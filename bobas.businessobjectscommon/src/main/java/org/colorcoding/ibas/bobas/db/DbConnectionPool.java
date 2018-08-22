package org.colorcoding.ibas.bobas.db;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.core.Daemon;
import org.colorcoding.ibas.bobas.core.IDaemonTask;
import org.colorcoding.ibas.bobas.core.InvalidDaemonTaskException;
import org.colorcoding.ibas.bobas.message.Logger;
import org.colorcoding.ibas.bobas.message.MessageLevel;

class DbConnectionPool implements IDbConnectionPool {

	protected static final String MSG_DB_POOL_USING_CONNECTION = "db pool: using connection [%s].";
	protected static final String MSG_DB_POOL_RECYCLED_CONNECTION = "db pool: recycled connection [%s].";
	static int POOLSIZE = -1;

	/**
	 * 获取-数据库连接缓存大小
	 * 
	 * @return
	 */
	static int getPoolSize() {
		if (POOLSIZE == -1) {
			synchronized (DbConnectionPool.class) {
				if (POOLSIZE == -1) {
					// 未被初始化，默认连接池关闭
					POOLSIZE = MyConfiguration.getConfigValue(MyConfiguration.CONFIG_ITEM_DB_CONNECTION_POOL_SIZE, 0);
				}
			}
		}
		return POOLSIZE;
	}

	static void setPoolSize(int value) {
		POOLSIZE = value;
	}

	/**
	 * 启用数据库连接池
	 * 
	 * @return
	 */
	public static boolean isEnabled() {
		if (DbConnectionPool.getPoolSize() > 0) {
			return true;
		}
		return false;
	}

	private volatile static IDbConnectionPool connectionPool = null;

	static final IDbConnectionPool getConnectionPool() {
		if (connectionPool == null) {
			synchronized (DbConnectionPool.class) {
				if (connectionPool == null) {
					connectionPool = new DbConnectionPool();

				}
			}
		}
		return connectionPool;
	}

	/**
	 * 还回资源
	 * 
	 * @param connection
	 *            数据库连接
	 * @throws DbException
	 */
	static boolean giveBack(IDbConnection connection) throws DbException {
		if (isEnabled() && getConnectionPool() != null) {
			// 启动了连接池并且有效
			boolean done = getConnectionPool().recycling(connection);
			if (done) {
				if (MyConfiguration.isDebugMode()) {
					Logger.log(MessageLevel.DEBUG, MSG_DB_POOL_RECYCLED_CONNECTION, connection.hashCode());
				}
			}
			return done;
		}
		return false;
	}

	/**
	 * 获取连接
	 * 
	 * @param sign
	 *            连接标记
	 * @return 数据库连接
	 */
	static IDbConnection getValid(String sign) {
		if (isEnabled() && getConnectionPool() != null) {
			// 启动了连接池并且有效
			IDbConnection connection = getConnectionPool().obtain(sign);
			if (connection != null) {
				if (connection instanceof DbConnection) {
					DbConnection dbConnection = (DbConnection) connection;
					dbConnection.setRecycled(false);
				}
				if (MyConfiguration.isDebugMode()) {
					Logger.log(MessageLevel.DEBUG, MSG_DB_POOL_USING_CONNECTION, connection.hashCode());
				}
			}
			return connection;
		}
		return null;
	}

	public DbConnectionPool() {
		this.availableConnections = new ConnectionWrapping[getPoolSize()];
		// 设置已回收的连接保持时间
		this.setHoldingTime(
				MyConfiguration.getConfigValue(MyConfiguration.CONFIG_ITEM_DB_CONNECTION_HOLDING_TIME, 180));
		if (this.getHoldingTime() > 0) {
			// 设置了连接持有时间
			// 开始释放连接任务
			try {
				Daemon.register(new IDaemonTask() {

					@Override
					public void run() {
						if (availableConnections != null) {
							// 锁住可用连接集合
							synchronized (availableConnections) {
								for (int i = 0; i < availableConnections.length; i++) {
									ConnectionWrapping wrapping = availableConnections[i];
									if (wrapping == null) {
										continue;
									}
									IDbConnection connection = wrapping.getConnection();
									if (connection == null) {
										availableConnections[i] = null;
										continue;
									}
									if ((System.currentTimeMillis() - wrapping.getStowedTime()) >= (getHoldingTime()
											* 1000)) {
										// 超出持有时间，关闭连接
										connection.dispose();
										availableConnections[i] = null;
									}
								}
							}
						}
					}

					@Override
					public String getName() {
						return "idle db connection dispose";
					}

					@Override
					public long getInterval() {
						return getHoldingTime();
					}

					@Override
					public boolean isActivated() {
						if (availableConnections == null)
							return false;
						if (availableConnections.length == 0)
							return false;
						// 不是全空的才执行释放
						boolean empty = true;
						for (int i = 0; i < availableConnections.length; i++) {
							if (availableConnections[i] != null) {
								empty = false;
							}
						}
						return !empty;
					}

				});
			} catch (InvalidDaemonTaskException e) {
				Logger.log(e);
			}
		}
	}

	private ConnectionWrapping[] availableConnections = null;

	@Override
	public IDbConnection obtain(String sign) {
		if (sign == null) {
			return null;
		}
		if (this.availableConnections != null) {
			synchronized (this.availableConnections) {
				for (int i = 0; i < this.availableConnections.length; i++) {
					try {
						ConnectionWrapping wrapping = this.availableConnections[i];
						if (wrapping == null) {
							continue;
						}
						IDbConnection connection = wrapping.getConnection();
						if (connection == null) {
							this.availableConnections[i] = null;
							continue;
						}
						if (connection instanceof DbConnection) {
							DbConnection dbConnection = (DbConnection) connection;
							if (!dbConnection.isValid()) {
								// 不可用，移出可用列表
								this.availableConnections[i] = null;
								// 释放引用资源
								dbConnection.dispose();
								continue;
							}
							if (sign.equals(dbConnection.getConnectionSign())) {
								// 匹配的，移出可用列表并返回
								this.availableConnections[i] = null;
								return connection;
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return null;
	}

	@Override
	public boolean recycling(IDbConnection connection) {
		try {
			if (connection == null) {
				return false;
			}

			if (!(connection instanceof DbConnection)) {
				return false;
			}
			DbConnection dbConnection = (DbConnection) connection;
			if (!dbConnection.isValid()) {
				// 已关闭，无效的
				return false;
			}
			if (this.availableConnections != null) {
				synchronized (this.availableConnections) {
					for (int i = 0; i < this.availableConnections.length; i++) {
						if (this.availableConnections[i] == null) {
							this.availableConnections[i] = new ConnectionWrapping(connection);
							return true;
						}
					}
				}
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public int getSize() {
		return getPoolSize();
	}

	@Override
	public void setSize(int value) {
		setPoolSize(value);
	}

	/**
	 * 链接的持有时间，超过这个时间即释放
	 */
	private long holdingTime;

	public long getHoldingTime() {
		return holdingTime;
	}

	public void setHoldingTime(long holdingTime) {
		this.holdingTime = holdingTime;
	}

	/**
	 * 连接的收纳盒
	 * 
	 * @author Niuren.Zhu
	 *
	 */
	private class ConnectionWrapping {
		public ConnectionWrapping(IDbConnection connection) {
			this.setConnection(connection);
		}

		IDbConnection connection;

		/**
		 * 连接
		 * 
		 * @return
		 */
		public IDbConnection getConnection() {
			return connection;
		}

		public void setConnection(IDbConnection connection) {
			this.connection = connection;
			if (this.connection != null)
				this.setStowedTime(System.currentTimeMillis());
			else
				this.setStowedTime(-1);
		}

		long stowedTime;

		/**
		 * 收纳时间
		 * 
		 * @return
		 */
		public long getStowedTime() {
			return stowedTime;
		}

		public void setStowedTime(long stowedTime) {
			this.stowedTime = stowedTime;
		}

	}
}
