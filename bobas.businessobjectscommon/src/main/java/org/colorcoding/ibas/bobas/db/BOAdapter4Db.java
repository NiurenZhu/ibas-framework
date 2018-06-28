package org.colorcoding.ibas.bobas.db;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.bo.BOException;
import org.colorcoding.ibas.bobas.bo.IBOCustomKey;
import org.colorcoding.ibas.bobas.bo.IBODocument;
import org.colorcoding.ibas.bobas.bo.IBODocumentLine;
import org.colorcoding.ibas.bobas.bo.IBOLine;
import org.colorcoding.ibas.bobas.bo.IBOMasterData;
import org.colorcoding.ibas.bobas.bo.IBOMasterDataLine;
import org.colorcoding.ibas.bobas.bo.IBOMaxValueKey;
import org.colorcoding.ibas.bobas.bo.IBOSeriesKey;
import org.colorcoding.ibas.bobas.bo.IBOSimple;
import org.colorcoding.ibas.bobas.bo.IBOSimpleLine;
import org.colorcoding.ibas.bobas.bo.IBOStorageTag;
import org.colorcoding.ibas.bobas.bo.IBOUserFields;
import org.colorcoding.ibas.bobas.bo.UserField;
import org.colorcoding.ibas.bobas.bo.UserFieldManager;
import org.colorcoding.ibas.bobas.common.ConditionOperation;
import org.colorcoding.ibas.bobas.common.Conditions;
import org.colorcoding.ibas.bobas.common.Criteria;
import org.colorcoding.ibas.bobas.common.ICondition;
import org.colorcoding.ibas.bobas.common.IConditions;
import org.colorcoding.ibas.bobas.common.ICriteria;
import org.colorcoding.ibas.bobas.common.ISort;
import org.colorcoding.ibas.bobas.common.ISorts;
import org.colorcoding.ibas.bobas.common.ISqlQuery;
import org.colorcoding.ibas.bobas.common.ISqlStoredProcedure;
import org.colorcoding.ibas.bobas.common.SqlQuery;
import org.colorcoding.ibas.bobas.core.BOFactory;
import org.colorcoding.ibas.bobas.core.IBOFactory;
import org.colorcoding.ibas.bobas.core.IBusinessObjectBase;
import org.colorcoding.ibas.bobas.core.IBusinessObjectListBase;
import org.colorcoding.ibas.bobas.core.ITrackStatusOperator;
import org.colorcoding.ibas.bobas.core.PropertyInfo;
import org.colorcoding.ibas.bobas.core.PropertyInfoList;
import org.colorcoding.ibas.bobas.core.PropertyInfoManager;
import org.colorcoding.ibas.bobas.core.fields.IFieldData;
import org.colorcoding.ibas.bobas.core.fields.IFieldDataDb;
import org.colorcoding.ibas.bobas.core.fields.IFieldDataDbs;
import org.colorcoding.ibas.bobas.core.fields.IManageFields;
import org.colorcoding.ibas.bobas.data.ArrayList;
import org.colorcoding.ibas.bobas.data.DateTime;
import org.colorcoding.ibas.bobas.data.Decimal;
import org.colorcoding.ibas.bobas.data.KeyValue;
import org.colorcoding.ibas.bobas.i18n.I18N;
import org.colorcoding.ibas.bobas.mapping.DbField;
import org.colorcoding.ibas.bobas.mapping.DbFieldType;
import org.colorcoding.ibas.bobas.repository.TransactionType;

public abstract class BOAdapter4Db implements IBOAdapter4Db {

	public BOAdapter4Db() {
		this.setEnabledUserFields(
				MyConfiguration.getConfigValue(MyConfiguration.CONFIG_ITEM_BO_ENABLED_USER_FIELDS, true));
	}

	private boolean enabledUserFields;

	/**
	 * 是否启用用户字段
	 * 
	 * @return
	 */
	public boolean isEnabledUserFields() {
		return enabledUserFields;
	}

	public void setEnabledUserFields(boolean value) {
		this.enabledUserFields = value;
	}

	/**
	 * 复合字段开始索引（必须小于0）
	 */
	public final static int COMPLEX_FIELD_BEGIN_INDEX = -100000;

	/**
	 * 组合复合字段索引
	 * 
	 * @param index
	 *            字段索引
	 * @param subIndex
	 *            子项字段索引
	 * @return
	 */
	protected int groupComplexFieldIndex(int index, int subIndex) {
		return COMPLEX_FIELD_BEGIN_INDEX - index * 100 - subIndex;
	}

	/**
	 * 解析复合字段索引
	 * 
	 * @param groupIndex
	 *            组合的索引
	 * @return 拆解的数组，[2]{F,S} F为字段索引，S为子项索引
	 */
	protected int[] parseComplexFieldIndex(int groupIndex) {
		if (groupIndex <= COMPLEX_FIELD_BEGIN_INDEX) {
			int[] indexs = new int[2];
			int tmp = Math.abs(groupIndex - COMPLEX_FIELD_BEGIN_INDEX);
			indexs[0] = tmp / 100;
			indexs[1] = tmp - indexs[0] * 100;
			return indexs;
		}
		return null;
	}

	/**
	 * 获取脚本库
	 * 
	 * @return
	 */
	public abstract ISqlScripts getSqlScripts();

	public Iterable<IFieldDataDb> getDbFields(IManageFields bo) {
		ArrayList<IFieldDataDb> dbFields = new ArrayList<IFieldDataDb>();
		for (IFieldData item : bo.getFields()) {
			if (item instanceof IFieldDataDb) {
				dbFields.add((IFieldDataDb) item);
			} else if (item instanceof IFieldDataDbs) {
				for (IFieldDataDb sub : ((IFieldDataDbs) item)) {
					dbFields.add(sub);
				}
			}
		}
		return dbFields;
	}

	public Iterable<IFieldDataDb> getDbFields(IFieldData[] fields) {
		ArrayList<IFieldDataDb> dbFields = new ArrayList<IFieldDataDb>();
		for (IFieldData item : fields) {
			if (item instanceof IFieldDataDb) {
				dbFields.add((IFieldDataDb) item);
			} else if (item instanceof IFieldDataDbs) {
				for (IFieldDataDb sub : ((IFieldDataDbs) item)) {
					dbFields.add(sub);
				}
			}
		}
		return dbFields;
	}

	@Override
	public ISqlQuery getServerTimeQuery() throws ParsingException {
		try {
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			return sqlScripts.getServerTimeQuery();
		} catch (Exception e) {
			throw new ParsingException(e);
		}
	}

	@Override
	public ISqlQuery parseSqlQuery(ICriteria criteria) throws ParsingException {
		if (criteria == null) {
			return null;
		}
		return this.parseSqlQuery(criteria.getConditions());
	}

	/**
	 * 获取查询条件语句
	 * 
	 * @param conditions
	 *            查询条件
	 * @return
	 * @throws SqlScriptException
	 */
	public ISqlQuery parseSqlQuery(IConditions conditions) throws ParsingException {
		try {
			if (conditions == null) {
				return null;
			}
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			StringBuilder stringBuilder = new StringBuilder();
			String dbObject = sqlScripts.getDbObjectSign();
			for (ICondition condition : conditions) {
				if (stringBuilder.length() > 0) {
					stringBuilder.append(" ");
					stringBuilder.append(sqlScripts.getSqlString(condition.getRelationship()));
					stringBuilder.append(" ");
				}
				// 开括号
				for (int i = 0; i < condition.getBracketOpen(); i++) {
					stringBuilder.append("(");
				}
				// 字段名
				if ((condition.getAliasDataType() == DbFieldType.NUMERIC
						|| condition.getAliasDataType() == DbFieldType.DECIMAL)
						&& (condition.getOperation() == ConditionOperation.START
								|| condition.getOperation() == ConditionOperation.END
								|| condition.getOperation() == ConditionOperation.CONTAIN
								|| condition.getOperation() == ConditionOperation.NOT_CONTAIN)) {
					// 数值类型的字段且需要作为字符比较的
					String toVarchar = sqlScripts.getCastTypeString(DbFieldType.ALPHANUMERIC);
					stringBuilder.append(String.format(toVarchar, String.format(dbObject, condition.getAlias())));
				} else if (condition.getComparedAlias() != null && !condition.getComparedAlias().isEmpty()) {
					// 字段之间比较，都按字符串比较
					String toVarchar = sqlScripts.getCastTypeString(DbFieldType.ALPHANUMERIC);
					stringBuilder.append(String.format(toVarchar, String.format(dbObject, condition.getAlias())));
					stringBuilder.append(" ");
					stringBuilder.append(sqlScripts.getSqlString(condition.getOperation()));
					stringBuilder.append(" ");
					stringBuilder
							.append(String.format(toVarchar, String.format(dbObject, condition.getComparedAlias())));
				} else {
					stringBuilder.append(String.format(dbObject, condition.getAlias()));
				}
				if (condition.getComparedAlias() == null || condition.getComparedAlias().isEmpty()) {
					// 字段与值的比较
					stringBuilder.append(" ");
					if (condition.getOperation() == ConditionOperation.IS_NULL
							|| condition.getOperation() == ConditionOperation.NOT_NULL) {
						// 不需要值的比较，[ItemName] is NULL
						stringBuilder.append(sqlScripts.getSqlString(condition.getOperation(), condition.getValue()));
					} else if (condition.getOperation() == ConditionOperation.START
							|| condition.getOperation() == ConditionOperation.END
							|| condition.getOperation() == ConditionOperation.CONTAIN
							|| condition.getOperation() == ConditionOperation.NOT_CONTAIN) {
						// like 相关运算
						stringBuilder.append(sqlScripts.getSqlString(condition.getOperation(), condition.getValue()));
					} else {
						// 与值比较，[ItemCode] = 'A000001'
						if (condition.getAliasDataType() == DbFieldType.NUMERIC
								|| condition.getAliasDataType() == DbFieldType.DECIMAL) {
							// 数值类型的字段
							stringBuilder.append(sqlScripts.getSqlString(condition.getOperation()));
							stringBuilder.append(" ");
							stringBuilder.append(condition.getValue());
						} else {
							// 非数值类型字段
							stringBuilder.append(sqlScripts.getSqlString(condition.getOperation()));
							stringBuilder.append(" ");
							stringBuilder.append(
									sqlScripts.getSqlString(condition.getAliasDataType(), condition.getValue()));
						}
					}
				}
				// 闭括号
				for (int i = 0; i < condition.getBracketClose(); i++) {
					stringBuilder.append(")");
				}
			}
			return new SqlQuery(stringBuilder.toString());
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		}
	}

	/**
	 * 获取排序语句
	 * 
	 * @param sorts
	 *            排序
	 * @return
	 * @throws SqlScriptException
	 */
	@Override
	public ISqlQuery parseSqlQuery(ISorts sorts) throws ParsingException {
		try {
			if (sorts == null) {
				return null;
			}
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			StringBuilder stringBuilder = new StringBuilder();
			String dbObject = sqlScripts.getDbObjectSign();
			for (ISort sort : sorts) {
				if (stringBuilder.length() > 0) {
					stringBuilder.append(sqlScripts.getFieldBreakSign());
				}
				stringBuilder.append(String.format(dbObject, sort.getAlias()));
				stringBuilder.append(" ");
				stringBuilder.append(sqlScripts.getSqlString(sort.getSortType()));
			}
			return new SqlQuery(stringBuilder.toString());
		} catch (Exception e) {
			throw new ParsingException(e);
		}
	}

	/**
	 * 修复查询数据库相关
	 * 
	 * @param criteria
	 * @param pInfoList
	 */
	protected void fixCriteria(ICriteria criteria, PropertyInfoList pInfoList) {
		if (pInfoList == null || pInfoList.isEmpty()) {
			return;
		}
		this.fixConditions(criteria.getConditions(), pInfoList);
		this.fixSorts(criteria.getSorts(), pInfoList);
		for (ICriteria item : criteria.getChildCriterias()) {
			this.fixCriteria(item, pInfoList);
		}
	}

	/**
	 * 修正查询条件
	 * 
	 * @param conditions
	 *            查询条件
	 * @param pInfoList
	 *            属性列表
	 * @return
	 */
	protected void fixConditions(IConditions conditions, PropertyInfoList pInfoList) {
		if (conditions == null || conditions.isEmpty() || pInfoList == null || pInfoList.isEmpty()) {
			return;
		}
		for (int i = 0; i < pInfoList.size(); i++) {
			PropertyInfo<?> cProperty = (PropertyInfo<?>) pInfoList.get(i);
			if (cProperty.getName() == null || cProperty.getName().isEmpty()) {
				continue;
			}
			Object annotation = cProperty.getAnnotation(DbField.class);
			if (annotation == null) {
				continue;
			}
			// 绑定数据库的字段
			DbField dbField = (DbField) annotation;
			if (dbField.name() == null || dbField.name().isEmpty()) {
				continue;
			}
			for (ICondition condition : conditions) {
				if (!cProperty.getName().equalsIgnoreCase(condition.getAlias())) {
					continue;
				}
				// 修正字段名称
				condition.setAlias(dbField.name());
				// 修正类型
				condition.setAliasDataType(dbField.type());
				// 修正枚举值
				if (cProperty.getValueType().isEnum()) {
					Object value = null;
					if (DataConvert.isNumeric(condition.getValue())) {
						// 数字转枚举
						value = DataConvert.toEnumValue(cProperty.getValueType(),
								Integer.valueOf(condition.getValue()));
					} else {
						value = DataConvert.toEnumValue(cProperty.getValueType(), condition.getValue());
					}
					if (value != null) {
						condition.setValue(value);
					}
				}
			}
		}
	}

	/**
	 * 修正排序条件
	 * 
	 * @param sorts
	 *            排序
	 * @param pInfoList
	 *            属性列表
	 * @return
	 */
	protected void fixSorts(ISorts sorts, PropertyInfoList pInfoList) {
		if (sorts == null || sorts.isEmpty() || pInfoList == null || pInfoList.isEmpty()) {
			return;
		}
		for (int i = 0; i < pInfoList.size(); i++) {
			PropertyInfo<?> cProperty = (PropertyInfo<?>) pInfoList.get(i);
			if (cProperty.getName() == null || cProperty.getName().isEmpty()) {
				continue;
			}
			Object annotation = cProperty.getAnnotation(DbField.class);
			if (annotation == null) {
				continue;
			}
			// 绑定数据库的字段
			DbField dbField = (DbField) annotation;
			if (dbField.name() == null || dbField.name().isEmpty()) {
				continue;
			}
			// 修正排序的字段名称
			for (ISort sort : sorts) {
				if (!cProperty.getName().equalsIgnoreCase(sort.getAlias())) {
					continue;
				}
				sort.setAlias(dbField.name());
			}
		}
	}

	@Override
	public ISqlQuery parseSqlQuery(ICriteria criteria, Class<?> boType) throws ParsingException {
		try {
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			if (criteria == null) {
				criteria = new Criteria();
			}
			// 获取主表
			String table = this.getMasterTable(PropertyInfoManager.getPropertyInfoList(boType));
			if (table == null || table.isEmpty()) {
				throw new ParsingException(I18N.prop("msg_bobas_not_found_bo_table", boType.getName()));
			}
			// 修正其中公司变量
			table = MyConfiguration.applyVariables(String.format(sqlScripts.getDbObjectSign(), table));
			// 修正属性
			this.fixCriteria(criteria, PropertyInfoManager.getPropertyInfoList(boType));
			// 修正用户字段
			this.fixCriteria(criteria, UserFieldManager.getUserFieldInfoList(boType));
			// 拼接语句
			String order = this.parseSqlQuery(criteria.getSorts()).getQueryString();
			String where = this.parseSqlQuery(criteria.getConditions()).getQueryString();
			return new SqlQuery(sqlScripts.groupSelectQuery("*", table, where, order, criteria.getResultCount()));
		} catch (ParsingException e) {
			throw e;
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		}
	}

	@Override
	public ISqlQuery parseInsertScript(IBusinessObjectBase bo) throws ParsingException {
		try {
			if (!(bo instanceof IManageFields)) {
				throw new ParsingException(I18N.prop("msg_bobas_invaild_bo"));
			}
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			IManageFields boFields = (IManageFields) bo;
			String table = this.getMasterTable(boFields);
			if (table == null || table.isEmpty()) {
				// 没有获取到表
				throw new ParsingException(I18N.prop("msg_bobas_not_found_bo_table", bo.getClass().getName()));
			}
			table = String.format(sqlScripts.getDbObjectSign(), table);
			StringBuilder fieldsBuilder = new StringBuilder();
			StringBuilder valuesBuilder = new StringBuilder();
			for (IFieldDataDb dbItem : this.getDbFields(boFields)) {
				if (fieldsBuilder.length() > 0) {
					fieldsBuilder.append(sqlScripts.getFieldBreakSign());
				}
				if (valuesBuilder.length() > 0) {
					valuesBuilder.append(sqlScripts.getFieldBreakSign());
				}
				fieldsBuilder.append(String.format(sqlScripts.getDbObjectSign(), dbItem.getDbField()));
				Object value = dbItem.getValue();
				if (value == null) {
					valuesBuilder.append(sqlScripts.getNullSign());
				} else {
					valuesBuilder.append(sqlScripts.getSqlString(dbItem.getFieldType(), DataConvert.toDbValue(value)));
				}
			}
			if (fieldsBuilder.length() == 0) {
				// 没有字段
				throw new ParsingException(I18N.prop("msg_bobas_not_allow_sql_scripts"));
			}
			if (valuesBuilder.length() == 0) {
				// 没有字段值
				throw new ParsingException(I18N.prop("msg_bobas_not_allow_sql_scripts"));
			}
			return new SqlQuery(
					sqlScripts.groupInsertScript(table, fieldsBuilder.toString(), valuesBuilder.toString()));
		} catch (ParsingException e) {
			throw e;
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		}
	}

	/**
	 * 获取字段及值语句
	 * 
	 * @param fields
	 *            字段列表
	 * @return 语句（"ItemCode" = 'A00001' ，"ItemName" = 'CPU I9'）
	 * @throws SqlScriptException
	 */
	protected String getFieldValues(Iterable<IFieldDataDb> fields, String split) throws SqlScriptException {
		ISqlScripts sqlScripts = this.getSqlScripts();
		if (sqlScripts == null) {
			throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
		}
		StringBuilder stringBuilder = new StringBuilder();
		for (IFieldDataDb dbItem : fields) {
			if (stringBuilder.length() > 0) {
				stringBuilder.append(split);
			}
			Object value = dbItem.getValue();
			stringBuilder.append(String.format(sqlScripts.getDbObjectSign(), dbItem.getDbField()));
			stringBuilder.append(" ");
			stringBuilder.append(value != null ? sqlScripts.getSqlString(ConditionOperation.EQUAL)
					: sqlScripts.getSqlString(ConditionOperation.IS_NULL));
			if (value != null) {
				stringBuilder.append(" ");
				stringBuilder.append(sqlScripts.getSqlString(dbItem.getFieldType(), DataConvert.toDbValue(value)));
			}
		}
		return stringBuilder.toString();
	}

	/**
	 * 获取字段的主表
	 * 
	 * @param boFields
	 *            字段列表
	 * @return 表名称（“OITM”）
	 */
	protected String getMasterTable(IManageFields boFields) {
		for (IFieldData item : boFields.getFields(c -> c.isPrimaryKey())) {
			if (item instanceof IFieldDataDb) {
				IFieldDataDb dbItem = (IFieldDataDb) item;
				return dbItem.getDbTable();
			}
		}
		return null;
	}

	/**
	 * 获取属性集合的主表
	 * 
	 * @param pInfoList
	 * @return
	 */
	protected String getMasterTable(PropertyInfoList pInfoList) {
		String table = null;
		for (int i = 0; i < pInfoList.size(); i++) {
			PropertyInfo<?> cProperty = (PropertyInfo<?>) pInfoList.get(i);
			if (cProperty.getName() == null || cProperty.getName().isEmpty()) {
				continue;
			}
			Object annotation = cProperty.getAnnotation(DbField.class);
			if (annotation == null) {
				continue;
			}
			// 绑定数据库的字段
			DbField dbField = (DbField) annotation;
			if (dbField.name() == null || dbField.name().isEmpty()) {
				continue;
			}
			if (dbField.primaryKey()) {
				return dbField.table();
			}
			table = dbField.table();
		}
		return table;
	}

	@Override
	public ISqlQuery parseDeleteScript(IBusinessObjectBase bo) throws ParsingException {
		try {
			if (!(bo instanceof IManageFields)) {
				throw new ParsingException(I18N.prop("msg_bobas_invaild_bo"));
			}
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			IManageFields boFields = (IManageFields) bo;
			String table = this.getMasterTable(boFields);
			if (table == null || table.isEmpty()) {
				// 没有获取到表
				throw new ParsingException(I18N.prop("msg_bobas_not_found_bo_table", bo.getClass().getName()));
			}
			table = String.format(sqlScripts.getDbObjectSign(), table);
			String partWhere = null;
			if (bo.isNew()) {
				// 新建状态删除，使用唯一属性
				partWhere = this.getFieldValues(this.getDbFields(boFields.getFields(c -> c.isUniqueKey())),
						sqlScripts.getAndSign());
			} else {
				// 非新建删除，使用主键属性
				partWhere = this.getFieldValues(this.getDbFields(boFields.getFields(c -> c.isPrimaryKey())),
						sqlScripts.getAndSign());
			}
			if (partWhere == null || partWhere.isEmpty()) {
				// 没有条件的删除不允许执行
				throw new ParsingException(I18N.prop("msg_bobas_not_allow_sql_scripts"));
			}
			return new SqlQuery(sqlScripts.groupDeleteScript(table, partWhere));
		} catch (ParsingException e) {
			throw e;
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		}
	}

	/**
	 * 获取匹配的索引（提升性能），此处包括对用户字段的处理
	 * 
	 * @param reader
	 *            查询
	 * @param bo
	 *            对象
	 * @return
	 * @throws ParsingException
	 * @throws DbException
	 * @throws SQLException
	 * @throws SqlScriptException
	 */
	protected int[] getFieldIndex(IDbDataReader reader, IManageFields bo)
			throws ParsingException, DbException, SQLException, SqlScriptException {
		// 构建索引
		IFieldData[] boFields = bo.getFields();
		int[] dfIndex = null;// 数据字段索引数组
		ResultSetMetaData metaData = reader.getMetaData();
		dfIndex = new int[metaData.getColumnCount()];
		boolean hasUserFields = false;// 存在未被添加的用户字段
		for (int i = 0; i < dfIndex.length; i++) {
			// 初始化索引
			dfIndex[i] = -1;
			// reader列索引
			int rCol = i + 1;
			// 当前列名称
			String name = metaData.getColumnName(rCol);// reader索引从1开始
			// 获取当前列对应的索引
			for (int j = 0; j < boFields.length; j++) {
				// 遍历BO字段
				IFieldData iFieldData = boFields[j];
				if (iFieldData instanceof IFieldDataDb) {
					// 数据库字段
					IFieldDataDb dbField = (IFieldDataDb) iFieldData;
					if (dbField.getDbField().equals(name)) {
						dfIndex[i] = j;// 设置当前列索引
						break;
					}
				} else if (iFieldData instanceof IFieldDataDbs) {
					// 数据库集合字段
					int k = 0;
					for (IFieldDataDb dbField : ((IFieldDataDbs) iFieldData)) {
						if (dbField.getDbField().equals(name)) {
							dfIndex[i] = this.groupComplexFieldIndex(j, k);// 设置当前列索引
							break;
						}
						k++;
					}
				}
			}
			if (!hasUserFields && dfIndex[i] == -1 && name.startsWith(UserField.USER_FIELD_PREFIX_SIGN)) {
				hasUserFields = true;
			}
		}
		// 处理用户字段
		if (hasUserFields && this.isEnabledUserFields()) {
			if (bo instanceof IBOUserFields) {
				ISqlScripts sqlScripts = this.getSqlScripts();
				if (sqlScripts == null) {
					throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
				}
				IBOUserFields uBO = (IBOUserFields) bo;
				// 开启了用户字段功能
				for (int i = 0; i < dfIndex.length; i++) {
					int index = dfIndex[i];
					int rCol = i + 1;
					if (index == -1) {
						if (uBO.getUserFields() != null) {
							String name = metaData.getColumnName(rCol);
							if (name != null && name.startsWith(UserField.USER_FIELD_PREFIX_SIGN)) {
								uBO.getUserFields().register(name,
										sqlScripts.toDbFieldType(metaData.getColumnTypeName(rCol)));
								dfIndex[i] = bo.getFields().length - 1;// 记录用户字段编号
							}
						}
					}
				}
				// 注册用户字段
				uBO.getUserFields().register();
			}
		}
		return dfIndex;
	}

	/**
	 * 填充业务对象数据
	 * 
	 * @param reader
	 *            查询
	 * @param bo
	 *            业务对象
	 * @param dfIndex
	 *            匹配的索引
	 * @return
	 * @throws DbException
	 * @throws ParsingException
	 */
	protected IManageFields fillDatas(IDbDataReader reader, IManageFields bo, int[] dfIndex)
			throws DbException, ParsingException {
		IFieldData[] boFields = bo.getFields();
		// 填充BO数据
		for (int i = 0; i < dfIndex.length; i++) {
			int index = dfIndex[i];
			int rCol = i + 1;// reader列索引
			if (index >= 0) {
				this.fillDatas(reader, rCol, boFields[index]);
			} else if (index < COMPLEX_FIELD_BEGIN_INDEX) {
				// 复合字段索引
				int[] indexs = this.parseComplexFieldIndex(index);
				if (indexs != null) {
					IFieldData fieldData = boFields[indexs[0]];
					int k = 0;
					for (IFieldDataDb item : (IFieldDataDbs) fieldData) {
						if (k == indexs[1]) {
							this.fillDatas(reader, rCol, item);
						}
						k++;
					}
				}
			}
		}
		if (bo instanceof ITrackStatusOperator) {
			// 标记对象为OLD
			ITrackStatusOperator opStatus = (ITrackStatusOperator) bo;
			opStatus.markOld();
		}
		return bo;
	}

	/**
	 * 填充数据
	 * 
	 * @param reader
	 *            查询结果
	 * @param rCol
	 *            查询列名
	 * @param fieldData
	 *            复制的字段
	 * @throws DbException
	 * @throws ParsingException
	 */
	protected void fillDatas(IDbDataReader reader, int rCol, IFieldData fieldData)
			throws DbException, ParsingException {
		if (fieldData.getValueType() == Integer.class) {
			fieldData.setValue(reader.getInt(rCol));
		} else if (fieldData.getValueType() == String.class) {
			fieldData.setValue(reader.getString(rCol));
		} else if (fieldData.getValueType() == Decimal.class) {
			fieldData.setValue(reader.getDecimal(rCol));
		} else if (fieldData.getValueType() == Double.class) {
			fieldData.setValue(reader.getDouble(rCol));
		} else if (fieldData.getValueType() == DateTime.class) {
			fieldData.setValue(reader.getDateTime(rCol));
		} else if (fieldData.getValueType() == Short.class) {
			fieldData.setValue(reader.getShort(rCol));
		} else if (fieldData.getValueType().isEnum()) {
			fieldData.setValue(DataConvert.toEnumValue(fieldData.getValueType(), reader.getString(rCol)));
		} else if (fieldData.getValueType() == Float.class) {
			fieldData.setValue(reader.getFloat(rCol));
		} else if (fieldData.getValueType() == Character.class) {
			fieldData.setValue(reader.getString(rCol));
		} else if (fieldData.getValueType() == Boolean.class) {
			fieldData.setValue(reader.getBoolean(rCol));
		} else {
			fieldData.setValue(this.convertValue(fieldData.getValueType(), reader.getObject(rCol)));
		}
	}

	@Override
	public IBusinessObjectBase[] parseBOs(IDbDataReader reader, IBusinessObjectListBase<?> bos)
			throws ParsingException {
		if (reader == null) {
			throw new ParsingException(I18N.prop("msg_bobas_invaild_data_reader"));
		}
		if (bos == null) {
			throw new ParsingException(I18N.prop("msg_bobas_invaild_bo_list"));
		}
		ArrayList<IBusinessObjectBase> childs = new ArrayList<IBusinessObjectBase>();
		try {
			int[] dfIndex = null;// 数据字段索引数组
			while (reader.next()) {
				IBusinessObjectBase bo = bos.create();
				if (bo == null) {
					throw new ParsingException(I18N.prop("msg_bobas_bo_list_not_support_create_element"));
				}
				if (!(bo instanceof IManageFields)) {
					throw new ParsingException(I18N.prop("msg_bobas_not_support_bo_type", bo.getClass().getName()));
				}
				IManageFields boFields = (IManageFields) bo;
				if (dfIndex == null) {
					dfIndex = this.getFieldIndex(reader, boFields);
				}
				this.fillDatas(reader, boFields, dfIndex);
				childs.add(bo);
			}
		} catch (ParsingException e) {
			throw e;
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		} catch (DbException e) {
			throw new ParsingException(e);
		} catch (SQLException e) {
			throw new ParsingException(e);
		}
		return childs.toArray(new IBusinessObjectBase[] {});
	}

	@Override
	public IBusinessObjectBase[] parseBOs(IDbDataReader reader, Class<?> boType) throws ParsingException {
		if (reader == null) {
			throw new ParsingException(I18N.prop("msg_bobas_invaild_data_reader"));
		}
		if (boType == null) {
			throw new ParsingException(I18N.prop("msg_bobas_invaild_bo_type"));
		}
		ArrayList<IBusinessObjectBase> bos = new ArrayList<IBusinessObjectBase>();
		try {
			int[] dfIndex = null;// 数据字段索引数组
			IBOFactory iboFactory = BOFactory.create();
			while (reader.next()) {
				IBusinessObjectBase bo = (IBusinessObjectBase) iboFactory.createInstance(boType);
				if (!(bo instanceof IManageFields)) {
					throw new ParsingException(I18N.prop("msg_bobas_not_support_bo_type", boType.getName()));
				}
				IManageFields boFields = (IManageFields) bo;
				if (dfIndex == null) {
					dfIndex = this.getFieldIndex(reader, boFields);
				}
				this.fillDatas(reader, boFields, dfIndex);
				bos.add(bo);
			}
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		} catch (DbException e) {
			throw new ParsingException(e);
		} catch (SQLException e) {
			throw new ParsingException(e);
		} catch (InstantiationException e) {
			throw new ParsingException(e);
		} catch (IllegalAccessException e) {
			throw new ParsingException(e);
		}
		return bos.toArray(new IBusinessObjectBase[] {});
	}

	/**
	 * 非预定义类型的转换方法
	 * 
	 * @param toType
	 *            转换到的类型
	 * @param value
	 *            值
	 * @return
	 * @throws ParsingException
	 */
	protected Object convertValue(Class<?> toType, Object value) throws ParsingException {
		throw new ParsingException(I18N.prop("msg_bobas_not_provide_convert_method", toType.getName()));
	}

	public void applyPrimaryKeys(IBusinessObjectBase bo, KeyValue[] keys) {
		if (bo instanceof IBODocument) {
			IBODocument boKey = (IBODocument) bo;
			for (KeyValue key : keys) {
				if (key.getValue() == null) {
					continue;
				}
				if (IBODocument.MASTER_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setDocEntry((int) (key.getValue()));
				}
			}
		} else if (bo instanceof IBODocumentLine) {
			IBODocumentLine boKey = (IBODocumentLine) bo;
			for (KeyValue key : keys) {
				if (key.getValue() == null) {
					continue;
				}
				if (IBODocumentLine.MASTER_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setDocEntry((int) (key.getValue()));
				} else if (IBODocumentLine.SECONDARY_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setLineId((int) (key.getValue()));
				}
			}
		} else if (bo instanceof IBOMasterData) {
			IBOMasterData boKey = (IBOMasterData) bo;
			for (KeyValue key : keys) {
				if (key.getValue() == null) {
					continue;
				}
				if (IBOMasterData.SERIAL_NUMBER_KEY_NAME.equals(key.getKey())) {
					boKey.setDocEntry((int) (key.getValue()));
				} else if (IBOMasterData.MASTER_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setCode(String.valueOf(key.getValue()));
				}
			}
		} else if (bo instanceof IBOMasterDataLine) {
			IBOMasterDataLine boKey = (IBOMasterDataLine) bo;
			for (KeyValue key : keys) {
				if (key.getValue() == null) {
					continue;
				}
				if (IBOMasterDataLine.SECONDARY_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setLineId((int) (key.getValue()));
				} else if (IBOMasterDataLine.MASTER_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setCode(String.valueOf(key.getValue()));
				}
			}
		} else if (bo instanceof IBOSimple) {
			IBOSimple boKey = (IBOSimple) bo;
			for (KeyValue key : keys) {
				if (key.getValue() == null) {
					continue;
				}
				if (IBOSimple.MASTER_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setObjectKey((int) (key.getValue()));
				}
			}
		} else if (bo instanceof IBOSimpleLine) {
			IBOSimpleLine boKey = (IBOSimpleLine) bo;
			for (KeyValue key : keys) {
				if (key.getValue() == null) {
					continue;
				}
				if (IBOSimpleLine.MASTER_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setObjectKey((int) (key.getValue()));
				} else if (IBOSimpleLine.SECONDARY_PRIMARY_KEY_NAME.equals(key.getKey())) {
					boKey.setLineId((int) (key.getValue()));
				}
			}
		} else if (bo instanceof IBOCustomKey) {
			// 自定义主键
			if (bo instanceof IBOMaxValueKey) {
				IBOMaxValueKey maxValueKey = (IBOMaxValueKey) bo;
				IFieldDataDb dbField = maxValueKey.getMaxValueField();
				for (KeyValue key : keys) {
					if (key.getValue() == null) {
						continue;
					}
					if (dbField.getName().equals(key.getKey())) {
						dbField.setValue(key.getValue());
					}
				}
			}
		}
	}

	@Override
	public KeyValue[] usePrimaryKeys(IBusinessObjectBase bo, IDbCommand command) throws BOException {
		// 获取主键
		KeyValue[] keys = this.parsePrimaryKeys(bo, command);
		if ((keys == null || keys.length == 0) && !(bo instanceof IBOCustomKey)) {
			throw new BOException(I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getName()));
		}
		// 主键赋值
		this.applyPrimaryKeys(bo, keys);
		// 更新主键
		this.updatePrimaryKeyRecords(bo, command);
		return keys;
	}

	@Override
	public KeyValue[] usePrimaryKeys(IBusinessObjectBase[] bos, IDbCommand command) throws BOException {
		// 获取主键
		KeyValue[] keys = null;// 主键信息
		int keyUsedCount = 0;// 主键使用的个数
		for (IBusinessObjectBase bo : bos) {
			if (bo == null)
				continue;
			if (!bo.isDirty() || !bo.isNew())
				continue;
			if (keys == null) {
				// 初始化主键
				keys = this.parsePrimaryKeys(bo, command);
			}
			// 设置主键
			this.applyPrimaryKeys(bo, keys);
			// 主键值增加
			for (KeyValue key : keys) {
				if (key.getValue() instanceof Integer) {
					key.setValue(Integer.sum((int) key.getValue(), 1));
				} else if (key.getValue() instanceof Long) {
					key.setValue(Long.sum((long) key.getValue(), 1));
				}
			}
			keyUsedCount++;// 使用了主键
		}
		// 更新主键
		if (keyUsedCount > 0)
			this.updatePrimaryKeyRecords(bos[0], keyUsedCount, command);
		return keys;
	}

	protected KeyValue[] parsePrimaryKeys(IBusinessObjectBase bo, IDbCommand command) throws BOException {
		try {
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			ArrayList<KeyValue> keys = new ArrayList<KeyValue>();
			IDbDataReader reader = null;
			if (bo instanceof IBODocument) {
				// 业务单据主键
				IBODocument item = (IBODocument) bo;
				reader = command.executeReader(sqlScripts.getPrimaryKeyQuery(item.getObjectCode()));
				if (reader.next()) {
					keys.add(new KeyValue(IBODocument.MASTER_PRIMARY_KEY_NAME, reader.getInt(1)));
					reader.close();
				} else {
					reader.close();
					throw new SqlScriptException(
							I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getSimpleName()));
				}
			} else if (bo instanceof IBODocumentLine) {
				// 业务单据行主键
				IBODocumentLine item = (IBODocumentLine) bo;
				ICriteria criteria = new Criteria();
				ICondition condition = criteria.getConditions().create();
				condition.setAlias(IBODocumentLine.MASTER_PRIMARY_KEY_NAME);
				condition.setAliasDataType(DbFieldType.NUMERIC);
				condition.setValue(item.getDocEntry().toString());
				String table = String.format(sqlScripts.getDbObjectSign(), this.getMasterTable((IManageFields) bo));
				String field = String.format(sqlScripts.getDbObjectSign(), IBODocumentLine.SECONDARY_PRIMARY_KEY_NAME);
				String where = this.parseSqlQuery(criteria.getConditions()).getQueryString();
				reader = command.executeReader(sqlScripts.groupMaxValueQuery(field, table, where));
				if (reader.next()) {
					keys.add(new KeyValue(IBODocumentLine.MASTER_PRIMARY_KEY_NAME, item.getDocEntry()));
					keys.add(new KeyValue(IBODocumentLine.SECONDARY_PRIMARY_KEY_NAME, reader.getInt(1) + 1));
					reader.close();
				} else {
					reader.close();
					throw new SqlScriptException(
							I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getSimpleName()));
				}
			} else if (bo instanceof IBOMasterData) {
				// 主数据主键
				IBOMasterData item = (IBOMasterData) bo;
				reader = command.executeReader(sqlScripts.getPrimaryKeyQuery(item.getObjectCode()));
				if (reader.next()) {
					keys.add(new KeyValue(IBOMasterData.MASTER_PRIMARY_KEY_NAME, item.getCode()));
					keys.add(new KeyValue(IBOMasterData.SERIAL_NUMBER_KEY_NAME, reader.getInt(1)));
					reader.close();
				} else {
					reader.close();
					throw new SqlScriptException(
							I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getSimpleName()));
				}
			} else if (bo instanceof IBOMasterDataLine) {
				// 主数据行主键
				IBOMasterDataLine item = (IBOMasterDataLine) bo;
				ICriteria criteria = new Criteria();
				ICondition condition = criteria.getConditions().create();
				condition.setAlias(IBOMasterDataLine.MASTER_PRIMARY_KEY_NAME);
				condition.setValue(item.getCode());
				String table = String.format(sqlScripts.getDbObjectSign(), this.getMasterTable((IManageFields) bo));
				String field = String.format(sqlScripts.getDbObjectSign(),
						IBOMasterDataLine.SECONDARY_PRIMARY_KEY_NAME);
				String where = this.parseSqlQuery(criteria.getConditions()).getQueryString();
				reader = command.executeReader(sqlScripts.groupMaxValueQuery(field, table, where));
				if (reader.next()) {
					keys.add(new KeyValue(IBOMasterDataLine.MASTER_PRIMARY_KEY_NAME, item.getCode()));
					keys.add(new KeyValue(IBOMasterDataLine.SECONDARY_PRIMARY_KEY_NAME, reader.getInt(1) + 1));
					reader.close();
				} else {
					reader.close();
					throw new SqlScriptException(
							I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getSimpleName()));
				}
			} else if (bo instanceof IBOSimple) {
				// 简单对象主键
				IBOSimple item = (IBOSimple) bo;
				reader = command.executeReader(sqlScripts.getPrimaryKeyQuery(item.getObjectCode()));
				if (reader.next()) {
					keys.add(new KeyValue(IBOSimple.MASTER_PRIMARY_KEY_NAME, reader.getInt(1)));
					reader.close();
				} else {
					reader.close();
					throw new SqlScriptException(
							I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getSimpleName()));
				}
			} else if (bo instanceof IBOSimpleLine) {
				// 简单对象行主键
				IBOSimpleLine item = (IBOSimpleLine) bo;
				ICriteria criteria = new Criteria();
				ICondition condition = criteria.getConditions().create();
				condition.setAlias(IBOSimpleLine.MASTER_PRIMARY_KEY_NAME);
				condition.setAliasDataType(DbFieldType.NUMERIC);
				condition.setValue(item.getObjectKey());
				String table = String.format(sqlScripts.getDbObjectSign(), this.getMasterTable((IManageFields) bo));
				String field = String.format(sqlScripts.getDbObjectSign(), IBOSimpleLine.SECONDARY_PRIMARY_KEY_NAME);
				String where = this.parseSqlQuery(criteria.getConditions()).getQueryString();
				reader = command.executeReader(sqlScripts.groupMaxValueQuery(field, table, where));
				if (reader.next()) {
					keys.add(new KeyValue(IBOSimpleLine.MASTER_PRIMARY_KEY_NAME, item.getObjectKey()));
					keys.add(new KeyValue(IBOSimpleLine.SECONDARY_PRIMARY_KEY_NAME, reader.getInt(1) + 1));
					reader.close();
				} else {
					reader.close();
					throw new SqlScriptException(
							I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getSimpleName()));
				}

			} else {
				// 没有指定主键的获取方式
				/*
				 * throw new SqlScriptsException( i18n.prop(
				 * "msg_bobas_not_specify_primary_keys_obtaining_method",
				 * bo.getClass().getName()));
				 */
			}
			// 额外主键获取
			if (bo instanceof IBOCustomKey) {
				// 自定义主键
				if (bo instanceof IBOMaxValueKey) {
					// 字段最大值
					IBOMaxValueKey maxValueKey = (IBOMaxValueKey) bo;
					IFieldDataDb dbField = maxValueKey.getMaxValueField();
					String table = String.format(sqlScripts.getDbObjectSign(), dbField.getDbTable());
					String field = String.format(sqlScripts.getDbObjectSign(), dbField.getDbField());
					ICondition[] tmpConditions = maxValueKey.getMaxValueConditions();
					IConditions conditions = new Conditions();
					if (tmpConditions != null) {
						for (ICondition item : tmpConditions) {
							conditions.add(item);
						}
					}
					this.fixConditions(conditions, PropertyInfoManager.getPropertyInfoList(bo.getClass()));
					String where = this.parseSqlQuery(conditions).getQueryString();
					reader = command.executeReader(sqlScripts.groupMaxValueQuery(field, table, where));
					if (reader.next()) {
						keys.add(new KeyValue(dbField.getName(), reader.getInt(1) + 1));
						reader.close();
					} else {
						reader.close();
						throw new SqlScriptException(
								I18N.prop("msg_bobas_not_found_bo_primary_key", bo.getClass().getSimpleName()));
					}
				}
			}
			return keys.toArray(new KeyValue[] {});
		} catch (SqlScriptException e) {
			throw new BOException(e);
		} catch (ParsingException e) {
			throw new BOException(e);
		} catch (DbException e) {
			throw new BOException(e);
		}
	}

	protected void updatePrimaryKeyRecords(IBusinessObjectBase bo, IDbCommand command) throws BOException {
		this.updatePrimaryKeyRecords(bo, 1, command);
	}

	protected void updatePrimaryKeyRecords(IBusinessObjectBase bo, int addValue, IDbCommand command)
			throws BOException {
		try {
			if (bo instanceof IBOLine) {
				// 对象行，不做处理
				return;
			}
			if (bo instanceof IBOCustomKey) {
				// 自定义主键，不做处理
				return;
			}
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			int nextValue = 0;
			String boCode = null;
			if (bo instanceof IBODocument) {
				// 业务单据主键
				IBODocument item = (IBODocument) bo;
				nextValue = item.getDocEntry() + addValue;
				boCode = item.getObjectCode();
			} else if (bo instanceof IBOMasterData) {
				// 主数据主键
				IBOMasterData item = (IBOMasterData) bo;
				nextValue = item.getDocEntry() + addValue;
				boCode = item.getObjectCode();
			} else if (bo instanceof IBOSimple) {
				// 简单对象主键
				IBOSimple item = (IBOSimple) bo;
				nextValue = item.getObjectKey() + addValue;
				boCode = item.getObjectCode();
			}
			if (boCode == null || nextValue == 0) {
				// 未能有效解析
				throw new ParsingException(
						I18N.prop("msg_bobas_not_specify_primary_keys_obtaining_method", bo.toString()));
			}
			// 更新数据记录
			command.executeUpdate(sqlScripts.getUpdatePrimaryKeyScript(boCode, addValue));
		} catch (SqlScriptException e) {
			throw new BOException(e);
		} catch (ParsingException e) {
			throw new BOException(e);
		} catch (DbException e) {
			throw new BOException(e);
		}
	}

	@Override
	public KeyValue useSeriesKey(IBusinessObjectBase bo, IDbCommand command) throws BOException {
		if (!(bo instanceof IBOSeriesKey))
			return null;
		IBOSeriesKey seriesKey = (IBOSeriesKey) bo;
		KeyValue key = this.parseSeriesKey(seriesKey, command);
		if (key == null) {
			return null;
		}
		this.updateSeriesKeyRecords(seriesKey, 1, command);
		this.applySeriesKey(bo, key);
		return key;
	}

	@Override
	public KeyValue useSeriesKey(IBusinessObjectBase[] bos, IDbCommand command) throws BOException {
		KeyValue key = null;
		int keyUsedCount = 0;// 使用的个数
		IBOSeriesKey seriesKey = null;
		for (IBusinessObjectBase bo : bos) {
			if (!bo.isDirty() || !bo.isNew())
				continue;
			if (!(bo instanceof IBOSeriesKey))
				continue;
			seriesKey = (IBOSeriesKey) bo;
			if (key == null) {
				// 初始化系列号
				key = this.parseSeriesKey(seriesKey, command);
			}
			if (key == null) {
				continue;
			}
			// 应用键值
			this.applySeriesKey(bo, key);
			// 键值增加
			if (key.getValue() instanceof Integer) {
				key.setValue(Integer.sum((int) key.getValue(), 1));
			} else if (key.getValue() instanceof Long) {
				key.setValue(Long.sum((long) key.getValue(), 1));
			}
			keyUsedCount++;// 使用了键值

		}
		// 更新键值
		if (keyUsedCount > 0)
			this.updateSeriesKeyRecords(seriesKey, keyUsedCount, command);
		return key;
	}

	protected void updateSeriesKeyRecords(IBOSeriesKey bo, int addValue, IDbCommand command) throws BOException {
		try {
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			// 更新数据记录
			command.executeUpdate(sqlScripts.getUpdateSeriesKeyScript(bo.getObjectCode(), bo.getSeries(), addValue));
		} catch (SqlScriptException e) {
			throw new BOException(e);
		} catch (DbException e) {
			throw new BOException(e);
		}
	}

	protected KeyValue parseSeriesKey(IBOSeriesKey bo, IDbCommand command) throws BOException {
		try {
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			KeyValue key = null;
			if (bo.getSeries() > 0) {
				// 设置了系列
				IDbDataReader reader = command
						.executeReader(sqlScripts.getSeriesKeyQuery(bo.getObjectCode(), bo.getSeries()));
				if (reader.next()) {
					key = new KeyValue(reader.getString(2), reader.getInt(1));
					reader.close();
				} else {
					reader.close();
					throw new SqlScriptException(
							I18N.prop("msg_bobas_not_found_bo_series_key", bo.getClass().getSimpleName()));
				}
			}
			return key;
		} catch (SqlScriptException e) {
			throw new BOException(e);
		} catch (DbException e) {
			throw new BOException(e);
		}
	}

	@Override
	public void applySeriesKey(IBusinessObjectBase bo, KeyValue key) {
		if (bo instanceof IBOSeriesKey) {
			IBOSeriesKey seriesKey = (IBOSeriesKey) bo;
			if (key.getKey() != null && !key.getKey().isEmpty()) {
				// 存在模块，则格式化编号
				seriesKey.setSeriesValue(String.format(key.getKey(), key.getValue()));
			} else {
				// 直接赋值编号
				seriesKey.setSeriesValue(key.getValue());
			}
		}
	}

	@Override
	public ISqlQuery parseTransactionNotification(TransactionType type, IBusinessObjectBase bo)
			throws ParsingException {
		try {
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			String boCode = null;
			if (bo instanceof IBOStorageTag) {
				boCode = ((IBOStorageTag) bo).getObjectCode();
			} else {
				boCode = bo.getClass().getSimpleName();
			}
			int keyCount = 0;
			StringBuilder keyNames = new StringBuilder(), keyValues = new StringBuilder();
			if (bo instanceof IManageFields) {
				IFieldData[] keys = ((IManageFields) bo).getFields(c -> c.isPrimaryKey());
				keyCount = keys.length;
				for (int i = 0; i < keys.length; i++) {
					if (keys[i] instanceof IFieldDataDb) {
						IFieldDataDb dbItem = (IFieldDataDb) keys[i];
						if (keyNames.length() > 0) {
							keyNames.append(",");
							keyNames.append(" ");
						}
						if (keyValues.length() > 0) {
							keyValues.append(",");
							keyValues.append(" ");
						}
						keyNames.append(dbItem.getDbField());
						keyValues.append(DataConvert.toString(dbItem.getValue()));
					}
				}
			}
			return new SqlQuery(sqlScripts.getTransactionNotificationQuery(boCode, DataConvert.toDbValue(type),
					keyCount, keyNames.toString(), keyValues.toString()));
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		}
	}

	@Override
	public ISqlQuery parseSqlQuery(ISqlStoredProcedure sp) throws ParsingException {
		if (sp == null) {
			return null;
		}
		try {
			ISqlScripts sqlScripts = this.getSqlScripts();
			if (sqlScripts == null) {
				throw new SqlScriptException(I18N.prop("msg_bobas_invaild_sql_scripts"));
			}
			return new SqlQuery(
					sqlScripts.groupStoredProcedure(sp.getName(), sp.getParameters().toArray(new KeyValue[] {})));
		} catch (SqlScriptException e) {
			throw new ParsingException(e);
		}
	}

	@Override
	public KeyValue[] usePrimaryKeys(IBusinessObjectBase bo, Object... others) throws BOException {
		return this.usePrimaryKeys(bo, (IDbCommand) others[0]);
	}

	@Override
	public KeyValue[] usePrimaryKeys(IBusinessObjectBase[] bos, Object... others) throws BOException {
		return this.usePrimaryKeys(bos, (IDbCommand) others[0]);
	}

	@Override
	public KeyValue useSeriesKey(IBusinessObjectBase bo, Object... others) throws BOException {
		return this.useSeriesKey(bo, (IDbCommand) others[0]);
	}

	@Override
	public KeyValue useSeriesKey(IBusinessObjectBase[] bos, Object... others) throws BOException {
		return this.useSeriesKey(bos, (IDbCommand) others[0]);
	}

}
