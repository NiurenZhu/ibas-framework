package org.colorcoding.ibas.bobas.test.logic;

import java.math.BigDecimal;

import org.colorcoding.ibas.bobas.logic.IBusinessLogicContract;

/**
 * 物料订购数量逻辑契约
 * 
 * @author Niuren.Zhu
 *
 */
public interface IMaterialsOrderQuantityContract extends IBusinessLogicContract {
	/**
	 * 物料编码
	 * 
	 * @return
	 */
	String getItemCode();

	/**
	 * 数量
	 * 
	 * @return
	 */
	BigDecimal getQuantity();
}
