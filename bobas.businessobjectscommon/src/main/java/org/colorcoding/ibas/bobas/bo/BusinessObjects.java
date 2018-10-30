package org.colorcoding.ibas.bobas.bo;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.common.ConditionOperation;
import org.colorcoding.ibas.bobas.common.Criteria;
import org.colorcoding.ibas.bobas.common.ICondition;
import org.colorcoding.ibas.bobas.common.ICriteria;
import org.colorcoding.ibas.bobas.common.ISort;
import org.colorcoding.ibas.bobas.common.SortType;
import org.colorcoding.ibas.bobas.core.BusinessObjectsBase;
import org.colorcoding.ibas.bobas.core.IBindableBase;
import org.colorcoding.ibas.bobas.core.IPropertyInfo;
import org.colorcoding.ibas.bobas.core.ITrackStatusOperator;
import org.colorcoding.ibas.bobas.core.fields.IFieldData;
import org.colorcoding.ibas.bobas.core.fields.IManagedFields;
import org.colorcoding.ibas.bobas.data.emBOStatus;
import org.colorcoding.ibas.bobas.data.emDocumentStatus;
import org.colorcoding.ibas.bobas.data.emYesNo;
import org.colorcoding.ibas.bobas.message.Logger;
import org.colorcoding.ibas.bobas.message.MessageLevel;
import org.colorcoding.ibas.bobas.rule.BusinessRuleCollection;
import org.colorcoding.ibas.bobas.rule.BusinessRuleException;
import org.colorcoding.ibas.bobas.rule.BusinessRulesFactory;
import org.colorcoding.ibas.bobas.rule.IBusinessRule;
import org.colorcoding.ibas.bobas.rule.IBusinessRules;

/**
 * 业务对象集合
 * 
 * @author Niuren.Zhu
 *
 * @param <E> 元素类型
 * @param <P> 父项类型
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlType(name = "BusinessObjects", namespace = MyConfiguration.NAMESPACE_BOBAS_BO)
public abstract class BusinessObjects<E extends IBusinessObject, P extends IBusinessObject>
		extends BusinessObjectsBase<E> implements IBusinessObjects<E, P> {

	private static final long serialVersionUID = 7360645136974073845L;

	public BusinessObjects() {
		this.setChangeElementStatus(true);
		this.setChangeParentStatus(true);
		// 监听自身改变事件
		this.registerListener(this.propertyListener);
	}

	public BusinessObjects(P parent) {
		this();
		this.setParent(parent);
	}

	private boolean changeElementStatus;

	/**
	 * 是否自动改变子项状态，父项变化
	 * 
	 * @return
	 */
	protected final boolean isChangeElementStatus() {
		return changeElementStatus;
	}

	protected final void setChangeElementStatus(boolean value) {
		this.changeElementStatus = value;
	}

	private boolean changeParentStatus;

	/**
	 * 是否自动改变父项状态，子项变化
	 * 
	 * @return
	 */
	protected final boolean isChangeParentStatus() {
		return changeParentStatus;
	}

	protected final void setChangeParentStatus(boolean value) {
		this.changeParentStatus = value;
	}

	private P parent = null;

	protected final P getParent() {
		return parent;
	}

	protected final void setParent(P parent) {
		if (this.parent instanceof IBindableBase) {
			// 移出监听属性改变
			IBindableBase bindable = (IBindableBase) this.parent;
			bindable.removeListener(this.propertyListener);
		}
		this.parent = parent;
		if (this.parent instanceof IBindableBase) {
			// 监听属性改变
			IBindableBase bindable = (IBindableBase) this.parent;
			bindable.registerListener(this.propertyListener);
		}
	}

	/**
	 * 属性监听实例（隐藏接口实现）
	 */
	private PropertyChangeListener propertyListener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (evt == null || evt.getPropertyName() == null || evt.getPropertyName().isEmpty()) {
				return;
			}
			if (evt.getSource() == BusinessObjects.this.getParent()) {
				// 父项属性改变
				BusinessObjects.this.onParentPropertyChanged(evt);
			} else if (BusinessObjects.this.contains(evt.getSource())) {
				// 此集合中的子项的属性改变
				if (evt.getPropertyName() != null && evt.getPropertyName().equals("isDirty")
						&& evt.getNewValue().equals(true)) {
					// 元素状态为Dirty时修改父项状态
					if (BusinessObjects.this.getParent() instanceof ITrackStatusOperator) {
						// 改变父项的状态跟踪
						ITrackStatusOperator statusOperator = (ITrackStatusOperator) BusinessObjects.this.getParent();
						statusOperator.markDirty();
					}
				} else {
					BusinessObjects.this.onElementPropertyChanged(evt);
				}
				// 集合数量发生变化，运行集合业务规则
				if (evt.getPropertyName().equals("isDeleted")) {
					this.runRules(null);
				} else {
					this.runRules(evt.getPropertyName());
				}
			} else if (evt.getSource() == BusinessObjects.this && evt.getPropertyName().equals(PROPERTY_NAME_SIZE)) {
				if (BusinessObjects.this.parent != null && !BusinessObjects.this.parent.isLoading()) {
					// 集合自身的属性改变事件
					if (BusinessObjects.this.getParent() instanceof ITrackStatusOperator) {
						// 改变父项的状态跟踪
						ITrackStatusOperator statusOperator = (ITrackStatusOperator) BusinessObjects.this.getParent();
						statusOperator.markDirty();
					}
				}
				// 集合数量发生变化，运行集合业务规则
				this.runRules(null);
			}
		}

		private volatile IBusinessRules myRules = null;

		public synchronized void runRules(String property) {
			if (!MyConfiguration.isLiveRules()) {
				return;
			}
			if (BusinessObjects.this.getParent() == null) {
				return;
			}
			if (BusinessObjects.this.getParent().isLoading()) {
				return;
			}
			Class<?> parentClass = BusinessObjects.this.getParent().getClass();
			if (this.myRules == null && parentClass != null) {
				this.myRules = BusinessRulesFactory.create().createManager().getRules(parentClass);
			}
			if (this.myRules == null) {
				return;
			}
			for (IBusinessRule rule : this.myRules) {
				if (!(rule instanceof BusinessRuleCollection)) {
					continue;
				}
				BusinessRuleCollection collectionRule = (BusinessRuleCollection) rule;
				if (!collectionRule.getCollection().getValueType().equals(BusinessObjects.this.getClass())) {
					continue;
				}
				if (property != null) {
					// 提供了属性名称，则只执行此属性相关规则
					boolean done = false;
					for (IPropertyInfo<?> item : rule.getInputProperties()) {
						if (item.getName().equals(property)) {
							done = true;
							break;
						}
					}
					if (!done) {
						continue;
					}
				}
				try {
					collectionRule.execute(BusinessObjects.this.getParent());
				} catch (BusinessRuleException e) {
					Logger.log(MessageLevel.DEBUG, e);
				}
			}
		}
	};

	@Override
	public final boolean add(E item) {
		return super.add(item);
	}

	@Override
	protected void afterAddItem(E item) {
		// 调用基类方法
		super.afterAddItem(item);
		// 额外逻辑
		if (item instanceof IBindableBase) {
			// 监听属性改变
			IBindableBase bindable = (IBindableBase) item;
			bindable.registerListener(this.propertyListener);
		}
		// 修正子项编号
		if (item instanceof IBOLine) {
			IBOLine line = (IBOLine) item;
			if (line.getLineId() <= 0) {
				int max = 0;
				for (E tmp : this) {
					if (tmp == item) {
						// 自身编号跳过
						continue;
					}
					if (tmp instanceof IBOLine) {
						IBOLine tmpLine = (IBOLine) tmp;
						if (tmpLine.getLineId() > max) {
							max = tmpLine.getLineId();
						}
					}
				}
				line.setLineId(max + 1);
			}
		}
		// 没父项，退出
		if (this.getParent() == null) {
			return;
		}
		// 添加子项即给子项主键赋值
		if (item instanceof IBODocumentLine) {
			IBODocumentLine child = (IBODocumentLine) item;
			if (this.getParent() instanceof IBODocument) {
				IBODocument parent = (IBODocument) this.getParent();
				child.setDocEntry(parent.getDocEntry());
			} else if (this.getParent() instanceof IBODocumentLine) {
				IBODocumentLine parent = (IBODocumentLine) this.getParent();
				child.setDocEntry(parent.getDocEntry());
			}
		} else if (item instanceof IBOMasterDataLine) {
			IBOMasterDataLine child = (IBOMasterDataLine) item;
			if (this.getParent() instanceof IBOMasterData) {
				IBOMasterData parent = (IBOMasterData) this.getParent();
				child.setCode(parent.getCode());
			} else if (this.getParent() instanceof IBOMasterDataLine) {
				IBOMasterDataLine parent = (IBOMasterDataLine) this.getParent();
				child.setCode(parent.getCode());
			}
		} else if (item instanceof IBOSimpleLine) {
			IBOSimpleLine child = (IBOSimpleLine) item;
			if (this.getParent() instanceof IBOSimple) {
				IBOSimple parent = (IBOSimple) this.getParent();
				child.setObjectKey(parent.getObjectKey());
			} else if (this.getParent() instanceof IBOSimpleLine) {
				IBOSimpleLine parent = (IBOSimpleLine) this.getParent();
				child.setObjectKey(parent.getObjectKey());
			}
		}
		// 父项读取数据中，退出
		if (this.getParent().isLoading()) {
			return;
		}
		// 处理单据状态
		if (item instanceof IBODocumentLine) {
			IBODocumentLine child = (IBODocumentLine) item;
			if (this.getParent() instanceof IBODocument) {
				IBODocument parent = (IBODocument) this.getParent();
				child.setLineStatus(parent.getDocumentStatus());
			} else if (this.getParent() instanceof IBODocumentLine) {
				IBODocumentLine parent = (IBODocumentLine) this.getParent();
				child.setLineStatus(parent.getLineStatus());
			}
		}
	}

	/**
	 * 当父项属性发生变化
	 * 
	 * @param evt
	 */
	protected void onParentPropertyChanged(PropertyChangeEvent evt) {
		// 父项空，退出
		if (this.getParent() == null) {
			return;
		}
		if (IBOSimple.MASTER_PRIMARY_KEY_NAME.equals(evt.getPropertyName())) {
			// 简单对象类
			if (evt.getSource() instanceof IBOSimple) {
				// 头
				IBOSimple parentItem = (IBOSimple) evt.getSource();
				for (E item : this) {
					if (item instanceof IBOSimpleLine) {
						IBOSimpleLine lineItem = (IBOSimpleLine) item;
						lineItem.setObjectKey(parentItem.getObjectKey());
					} else if (item instanceof IBOSimple) {
						IBOSimple lineItem = (IBOSimple) item;
						lineItem.setObjectKey(parentItem.getObjectKey());
					}
				}
			} else if (evt.getSource() instanceof IBOSimpleLine) {
				// 行
				IBOSimpleLine parentItem = (IBOSimpleLine) evt.getSource();
				for (E item : this) {
					if (item instanceof IBOSimpleLine) {
						IBOSimpleLine lineItem = (IBOSimpleLine) item;
						lineItem.setObjectKey(parentItem.getObjectKey());
					} else if (item instanceof IBOSimple) {
						IBOSimple lineItem = (IBOSimple) item;
						lineItem.setObjectKey(parentItem.getObjectKey());
					}
				}
			}

		} else if (IBOMasterData.MASTER_PRIMARY_KEY_NAME.equals(evt.getPropertyName())) {
			// 主数据类
			if (evt.getSource() instanceof IBOMasterData) {
				// 头
				IBOMasterData parentItem = (IBOMasterData) evt.getSource();
				for (E item : this) {
					if (item instanceof IBOMasterDataLine) {
						IBOMasterDataLine lineItem = (IBOMasterDataLine) item;
						lineItem.setCode(parentItem.getCode());
					} else if (item instanceof IBOMasterData) {
						IBOMasterData lineItem = (IBOMasterData) item;
						lineItem.setCode(parentItem.getCode());
					}
				}
			} else if (evt.getSource() instanceof IBOMasterDataLine) {
				// 行
				IBOMasterDataLine parentItem = (IBOMasterDataLine) evt.getSource();
				for (E item : this) {
					if (item instanceof IBOMasterDataLine) {
						IBOMasterDataLine lineItem = (IBOMasterDataLine) item;
						lineItem.setCode(parentItem.getCode());
					} else if (item instanceof IBOMasterData) {
						IBOMasterData lineItem = (IBOMasterData) item;
						lineItem.setCode(parentItem.getCode());
					}
				}
			}
		} else if (IBODocument.MASTER_PRIMARY_KEY_NAME.equals(evt.getPropertyName())) {
			// 单据类
			if (evt.getSource() instanceof IBODocument) {
				// 头
				IBODocument parentItem = (IBODocument) evt.getSource();
				for (E item : this) {
					if (item instanceof IBODocumentLine) {
						IBODocumentLine lineItem = (IBODocumentLine) item;
						lineItem.setDocEntry(parentItem.getDocEntry());
					} else if (item instanceof IBODocument) {
						IBODocument lineItem = (IBODocument) item;
						lineItem.setDocEntry(parentItem.getDocEntry());
					}
				}
			} else if (evt.getSource() instanceof IBODocumentLine) {
				// 行
				IBODocumentLine parentItem = (IBODocumentLine) evt.getSource();
				for (E item : this) {
					if (item instanceof IBODocumentLine) {
						IBODocumentLine lineItem = (IBODocumentLine) item;
						lineItem.setDocEntry(parentItem.getDocEntry());
					} else if (item instanceof IBODocument) {
						IBODocument lineItem = (IBODocument) item;
						lineItem.setDocEntry(parentItem.getDocEntry());
					}
				}
			}
		}
		// 加载中，退出
		if (this.getParent().isLoading()) {
			return;
		}
		// 状态变化
		if (this.isChangeElementStatus()) {
			this.changeElementStatus(evt);
		}
	}

	/**
	 * 当元素属性发生变化
	 * 
	 * @param evt
	 */
	protected void onElementPropertyChanged(PropertyChangeEvent evt) {
		// 加载中，退出
		if (this.getParent() == null || this.getParent().isLoading()) {
			return;
		}
		if (this.isChangeParentStatus()) {
			this.changeParentStatus(evt);
		}
	}

	/**
	 * 父项数据变化时触发改变元素数据
	 * 
	 * @param evt
	 */
	private void changeElementStatus(PropertyChangeEvent evt) {
		// 父项的属性改变
		// 可取消对象
		if (evt.getPropertyName().equals("Canceled")) {
			if (evt.getSource() instanceof IBOTagCanceled) {
				// 头
				IBOTagCanceled parentTag = (IBOTagCanceled) evt.getSource();
				for (E item : this) {
					if (!(item instanceof IBOTagCanceled)) {
						continue;
					}
					IBOTagCanceled lineTag = (IBOTagCanceled) item;
					lineTag.setCanceled(parentTag.getCanceled());
				}
			}
		}
		// 标记删除对象
		else if (evt.getPropertyName().equals("Deleted")) {
			if (evt.getSource() instanceof IBOTagDeleted) {
				// 头
				IBOTagDeleted parentTag = (IBOTagDeleted) evt.getSource();
				for (E item : this) {
					if (!(item instanceof IBOTagDeleted)) {
						continue;
					}
					IBOTagDeleted lineTag = (IBOTagDeleted) item;
					lineTag.setDeleted(parentTag.getDeleted());
				}
			}
		} else if (evt.getPropertyName().equals("LineStatus") || evt.getPropertyName().equals("DocumentStatus")
				|| evt.getPropertyName().equals("Status")) {
			// 单据类
			if (evt.getSource() instanceof IBODocument) {
				// 头
				IBODocument parentItem = (IBODocument) evt.getSource();
				for (E item : this) {
					if (item instanceof IBODocumentLine) {
						IBODocumentLine lineItem = (IBODocumentLine) item;
						if (evt.getPropertyName().equals("DocumentStatus")) {
							lineItem.setLineStatus(parentItem.getDocumentStatus());
						} else if (evt.getPropertyName().equals("Status")) {
							lineItem.setStatus(parentItem.getStatus());
						}
					} else if (item instanceof IBODocument) {
						IBODocument lineItem = (IBODocument) item;
						if (evt.getPropertyName().equals("DocumentStatus")) {
							lineItem.setDocumentStatus(parentItem.getDocumentStatus());
						} else if (evt.getPropertyName().equals("Status")) {
							lineItem.setStatus(parentItem.getStatus());
						}
					}
				}
			} else if (evt.getSource() instanceof IBODocumentLine) {
				// 行
				IBODocumentLine parentItem = (IBODocumentLine) evt.getSource();
				for (E item : this) {
					if (item instanceof IBODocumentLine) {
						IBODocumentLine lineItem = (IBODocumentLine) item;
						if (evt.getPropertyName().equals("DocumentStatus")) {
							lineItem.setLineStatus(parentItem.getLineStatus());
						} else if (evt.getPropertyName().equals("Status")) {
							lineItem.setStatus(parentItem.getStatus());
						}
					} else if (item instanceof IBODocument) {
						IBODocument lineItem = (IBODocument) item;
						if (evt.getPropertyName().equals("DocumentStatus")) {
							lineItem.setDocumentStatus(parentItem.getLineStatus());
						} else if (evt.getPropertyName().equals("Status")) {
							lineItem.setStatus(parentItem.getStatus());
						}
					}
				}
			}
		}
	}

	/**
	 * 元素数据变化时触发改变父项数据
	 * 
	 * @param evt
	 */
	private void changeParentStatus(PropertyChangeEvent evt) {
		// 不是关注的属性改变退出
		IFieldData parentField = null;
		// 被引用，子项被引用，父项被引用
		if (evt.getPropertyName().equals("Referenced")) {
			if (evt.getSource() instanceof IBOTagReferenced) {
				if (this.getParent() instanceof IBOTagReferenced && this.getParent() instanceof IManagedFields) {
					parentField = ((IManagedFields) this.getParent()).getField(evt.getPropertyName());
					if (parentField == null) {
						return;
					}
					// 子项全部为修改值，则父项也修改
					for (E item : this) {
						if (!(item instanceof IBOTagReferenced)) {
							continue;
						}
						IBOTagReferenced lineItem = (IBOTagReferenced) item;
						if (lineItem.getReferenced() == emYesNo.YES) {
							// 任意子项被引用，父项被引用
							parentField.setValue(emYesNo.YES);
							return;
						}
					}
				}
			}
		}
		// 可取消
		else if (evt.getPropertyName().equals("Canceled")) {
			if (evt.getSource() instanceof IBOTagCanceled) {
				if (this.getParent() instanceof IBOTagCanceled && this.getParent() instanceof IManagedFields) {
					parentField = ((IManagedFields) this.getParent()).getField(evt.getPropertyName());
					if (parentField == null) {
						return;
					}
					IBOTagCanceled lineItem = (IBOTagCanceled) evt.getSource();
					emYesNo boCanceled = lineItem.getCanceled();
					// 子项全部为修改值，则父项也修改
					for (E item : this) {
						if (!(item instanceof IBOTagCanceled)) {
							continue;
						}
						lineItem = (IBOTagCanceled) item;
						if (lineItem.getCanceled() != boCanceled) {
							// 子项有不同值，退出，优先不取消
							parentField.setValue(emYesNo.NO);
							return;
						}
					}
					parentField.setValue(boCanceled);
				}
			}
		}
		// 可删除
		else if (evt.getPropertyName().equals("Deleted")) {
			if (evt.getSource() instanceof IBOTagDeleted) {
				if (this.getParent() instanceof IBOTagDeleted && this.getParent() instanceof IManagedFields) {
					parentField = ((IManagedFields) this.getParent()).getField(evt.getPropertyName());
					if (parentField == null) {
						return;
					}
					IBOTagDeleted lineItem = (IBOTagDeleted) evt.getSource();
					emYesNo boDeleted = lineItem.getDeleted();
					// 子项全部为修改值，则父项也修改
					for (E item : this) {
						if (!(item instanceof IBOTagDeleted)) {
							continue;
						}
						lineItem = (IBOTagDeleted) item;
						if (lineItem.getDeleted() != boDeleted) {
							// 子项有不同值，退出，优先不删除
							parentField.setValue(emYesNo.NO);
							return;
						}
					}
					parentField.setValue(boDeleted);
				}
			}
		}
		// 单据对象
		else if (evt.getPropertyName().equals("LineStatus") || evt.getPropertyName().equals("Status")) {
			if (evt.getSource() instanceof IBODocumentLine && this.getParent() instanceof IManagedFields) {
				IBODocumentLine lineItem = (IBODocumentLine) evt.getSource();
				if (this.getParent() instanceof IBODocument) {
					// 父项是单据
					IBODocument parent = (IBODocument) this.getParent();
					if (evt.getPropertyName().equals("LineStatus")) {
						// 使用字段赋值避免触发事件
						parentField = ((IManagedFields) parent).getField("DocumentStatus");
						if (parentField == null) {
							return;
						}
						emDocumentStatus boLineStatus = lineItem.getLineStatus();
						// 子项全部为修改值，则父项也修改
						for (E item : this) {
							if (!(item instanceof IBODocumentLine)) {
								continue;
							}
							lineItem = (IBODocumentLine) item;
							if (lineItem.getLineStatus() != boLineStatus) {
								// 子项有不同值
								if (parent.getDocumentStatus() == emDocumentStatus.PLANNED) {
									// 父项计划状态
									if (boLineStatus.ordinal() > emDocumentStatus.PLANNED.ordinal()) {
										// 子项变为计划以上状态
										parentField.setValue(emDocumentStatus.RELEASED);
									}
								} else {
									if (parent.getDocumentStatus().ordinal() > boLineStatus.ordinal()) {
										// 父项高于修改状态，父项降低
										if (boLineStatus == emDocumentStatus.PLANNED)
											// 最低到Relase
											parentField.setValue(emDocumentStatus.RELEASED);
										else
											parentField.setValue(boLineStatus);
									}
									// 父项低于修改状态，等待全部修改
								}
								// 退出
								return;
							}
						}
						parentField.setValue(boLineStatus);
					} else if (evt.getPropertyName().equals("Status")) {
						// 使用字段赋值避免触发事件
						parentField = ((IManagedFields) parent).getField(evt.getPropertyName());
						if (parentField == null) {
							return;
						}
						emBOStatus boStatus = lineItem.getStatus();
						// 子项全部为修改值，则父项也修改
						for (E item : this) {
							if (!(item instanceof IBODocumentLine)) {
								continue;
							}
							lineItem = (IBODocumentLine) item;
							if (lineItem.getStatus() != boStatus) {
								// 子项有不同值，退出，优先不关闭
								parentField.setValue(emBOStatus.OPEN);
								return;
							}
						}
						parentField.setValue(boStatus);
					}
				} else if (this.getParent() instanceof IBODocumentLine) {
					// 父项是单据行
					IBODocumentLine parent = (IBODocumentLine) this.getParent();
					if (evt.getPropertyName().equals("LineStatus")) {
						// 使用字段赋值避免触发事件
						parentField = ((IManagedFields) parent).getField(evt.getPropertyName());
						if (parentField == null) {
							return;
						}
						emDocumentStatus boLineStatus = lineItem.getLineStatus();
						// 子项全部为修改值，则父项也修改
						for (E item : this) {
							if (!(item instanceof IBODocumentLine)) {
								continue;
							}
							lineItem = (IBODocumentLine) item;
							if (lineItem.getLineStatus() != boLineStatus) {
								if (parent.getLineStatus() == emDocumentStatus.PLANNED) {
									// 父项计划状态
									if (boLineStatus.ordinal() > emDocumentStatus.PLANNED.ordinal()) {
										// 子项变为计划以上状态
										parentField.setValue(emDocumentStatus.RELEASED);
									}
								} else {
									if (parent.getLineStatus().ordinal() > boLineStatus.ordinal()) {
										// 父项高于修改状态，父项降低
										if (boLineStatus == emDocumentStatus.PLANNED)
											// 最低到Relase
											parentField.setValue(emDocumentStatus.RELEASED);
										else
											parentField.setValue(boLineStatus);
									}
									// 父项低于修改状态，等待全部修改
								}
								// 退出
								return;
							}
						}
						parentField.setValue(boLineStatus);
					} else if (evt.getPropertyName().equals("Status")) {
						// 使用字段赋值避免触发事件
						parentField = ((IManagedFields) parent).getField(evt.getPropertyName());
						if (parentField == null) {
							return;
						}
						emBOStatus boStatus = lineItem.getStatus();
						// 子项全部为修改值，则父项也修改
						for (E item : this) {
							if (!(item instanceof IBODocumentLine)) {
								continue;
							}
							lineItem = (IBODocumentLine) item;
							if (lineItem.getStatus() != boStatus) {
								// 子项有不同值，退出，优先不关闭
								parentField.setValue(emBOStatus.OPEN);
								return;
							}
						}
						parentField.setValue(boStatus);
					}
				}
			}
		}
	}

	@Override
	protected void afterRemoveItem(E item) {
		// 调用基类方法
		super.afterRemoveItem(item);
		// 额外逻辑
		if (item instanceof IBindableBase) {
			// 移出监听属性改变
			IBindableBase bindable = (IBindableBase) item;
			bindable.removeListener(this.propertyListener);
		}
	}

	@Override
	public ICriteria getElementCriteria() {
		if (this.getParent() == null) {
			return null;
		}
		ICriteria criteria = null;
		if (this.getParent() instanceof IBOMasterData) {
			if (IBOMasterDataLine.class.isAssignableFrom(this.getElementType())) {
				criteria = new Criteria();
				ICondition condition = criteria.getConditions().create();
				condition.setAlias(IBOMasterDataLine.MASTER_PRIMARY_KEY_NAME);
				condition.setValue(((IBOMasterData) this.getParent()).getCode());
				condition.setOperation(ConditionOperation.EQUAL);
			}
		} else if (this.getParent() instanceof IBODocument) {
			if (IBODocumentLine.class.isAssignableFrom(this.getElementType())) {
				criteria = new Criteria();
				ICondition condition = criteria.getConditions().create();
				condition.setAlias(IBODocumentLine.MASTER_PRIMARY_KEY_NAME);
				condition.setValue(((IBODocument) this.getParent()).getDocEntry());
				condition.setOperation(ConditionOperation.EQUAL);
			}
		} else if (this.getParent() instanceof IBOSimple) {
			if (IBOSimpleLine.class.isAssignableFrom(this.getElementType())) {
				criteria = new Criteria();
				ICondition condition = criteria.getConditions().create();
				condition.setAlias(IBOSimpleLine.MASTER_PRIMARY_KEY_NAME);
				condition.setValue(((IBOSimple) this.getParent()).getObjectKey());
				condition.setOperation(ConditionOperation.EQUAL);
			}
		}
		if (criteria == null) {
			criteria = this.getParent().getCriteria();
		}
		if (IBOLine.class.isAssignableFrom(this.getElementType())) {
			// 元素类型是行类型，则添加排序字段
			ISort sort = criteria.getSorts().create();
			sort.setAlias(IBOLine.SECONDARY_PRIMARY_KEY_NAME);
			sort.setSortType(SortType.ASCENDING);
		}
		return criteria;
	}

	@Override
	public final void delete(E item) {
		if (this.contains(item)) {
			// 集合中的元素
			if (item.isNew()) {
				this.remove(item);
			} else {
				item.delete();
			}
		}
	}

	@Override
	public final void delete(int index) {
		E item = this.get(index);
		this.delete(item);
	}

	@Override
	public final void deleteAll() {
		for (int i = this.size() - 1; i >= 0; i--) {
			this.delete(i);
		}
	}
}
