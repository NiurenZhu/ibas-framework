package org.colorcoding.ibas.bobas.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.data.ArrayList;

/**
 * 业务对象集合
 * 
 * 注意：仅add(E item)；remove(int index)；remove(Object item)触发属性改变事件
 * 
 * @author Niuren.Zhu
 *
 * @param <E>
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlType(name = "BusinessObjectsBase", namespace = MyConfiguration.NAMESPACE_BOBAS_CORE)
public abstract class BusinessObjectsBase<E extends IBusinessObjectBase> extends ArrayList<E>
		implements IBusinessObjectsBase<E>, IBindableBase {

	private static final long serialVersionUID = -5212226199781937273L;

	transient private PropertyChangeSupport listeners;

	@Override
	public final void registerListener(PropertyChangeListener listener) {
		if (this.listeners == null) {
			this.listeners = new PropertyChangeSupport(this);
		}
		this.listeners.addPropertyChangeListener(listener);
	}

	@Override
	public final void removeListener(PropertyChangeListener listener) {
		if (this.listeners == null) {
			return;
		}
		this.listeners.removePropertyChangeListener(listener);
	}

	protected void firePropertyChange(String name, Object oldValue, Object newValue) {
		if (this.listeners == null) {
			return;
		}
		this.listeners.firePropertyChange(name, oldValue, newValue);
	}

	public static final String PROPERTY_NAME_SIZE = "size";

	@Override
	public boolean add(E item) {
		int oldSize = this.size();
		boolean done = super.add(item);
		if (done) {
			this.firePropertyChange(PROPERTY_NAME_SIZE, oldSize, this.size());
			this.afterAddItem(item);
		}
		return done;
	}

	/**
	 * 集合添加项目后
	 * 
	 * @param item
	 *            添加的项目
	 */
	protected void afterAddItem(E item) {

	}

	@Override
	public E remove(int index) {
		int oldSize = this.size();
		E item = super.remove(index);
		this.firePropertyChange(PROPERTY_NAME_SIZE, oldSize, this.size());
		this.afterRemoveItem(item);
		return item;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean remove(Object item) {
		int oldSize = this.size();
		boolean done = super.remove(item);
		if (done) {
			this.firePropertyChange(PROPERTY_NAME_SIZE, oldSize, this.size());
			this.afterRemoveItem((E) item);
		}
		return done;
	}

	/**
	 * 集合移出项目后
	 * 
	 * @param item
	 *            项目
	 */
	protected void afterRemoveItem(E item) {

	}

}
