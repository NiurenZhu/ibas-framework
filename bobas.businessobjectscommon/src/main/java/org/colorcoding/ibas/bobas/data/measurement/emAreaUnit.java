package org.colorcoding.ibas.bobas.data.measurement;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlType;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.data.Decimal;
import org.colorcoding.ibas.bobas.mapping.Value;

/**
 * 面积单位
 */
@XmlType(name = "emAreaUnit", namespace = MyConfiguration.NAMESPACE_BOBAS_DATA)
public enum emAreaUnit {
	/**
	 * 平方毫米
	 */
	@Value("mm²")
	SQUARE_MILLIMETER(0),
	/**
	 * 平方厘米
	 */
	@Value("cm²")
	SQUARE_CENTIMETER(1),
	/**
	 * 平方米
	 */
	@Value("m²")
	SQUARE_METRE(2),
	/**
	 * 平方千米
	 */
	@Value("km²")
	SQUARE_KILOMETER(5);

	private int intValue;
	private static java.util.HashMap<Integer, emAreaUnit> mappings;

	private synchronized static java.util.HashMap<Integer, emAreaUnit> getMappings() {
		if (mappings == null) {
			mappings = new java.util.HashMap<Integer, emAreaUnit>(4);
		}
		return mappings;
	}

	private emAreaUnit(int value) {
		intValue = value;
		emAreaUnit.getMappings().put(value, this);
	}

	public int getValue() {
		return intValue;
	}

	public static emAreaUnit valueOf(int value, boolean valueIndex) {
		if (valueIndex) {
			return getMappings().get(value);
		}
		return values()[value];
	}

	public static emAreaUnit valueOf(String value, boolean ignoreCase) {
		if (ignoreCase) {
			for (Object item : emAreaUnit.class.getEnumConstants()) {
				if (item.toString().equalsIgnoreCase(value)) {
					return (emAreaUnit) item;
				}
			}
		}
		return emAreaUnit.valueOf(value);
	}

	/**
	 * 基本转换率
	 */
	public final static int getRate(int level) {
		int rate = 1;
		for (int i = 0; i < Math.abs(level); i++) {
			// 每级别进率
			rate = rate * 100;
		}
		return rate;
	}

	/**
	 * 换算
	 * 
	 * @param toUnit   换算到单位
	 * @param value    值
	 * @param fromUnit 原始单位
	 * @return 目标单位的值
	 */
	public static double convert(emAreaUnit toUnit, double value, emAreaUnit fromUnit) {
		int level = toUnit.getValue() - fromUnit.getValue();
		if (level > 0) {
			// 目标单位大
			return value / getRate(level);
		} else if (level < 0) {
			// 目标单位小
			return value * getRate(level);
		}
		// 单位相同
		return value;
	}

	/**
	 * 换算
	 * 
	 * @param toUnit   换算到单位
	 * @param value    值
	 * @param fromUnit 原始单位
	 * @return 目标单位的值
	 */
	public static BigDecimal convert(emAreaUnit toUnit, BigDecimal value, emAreaUnit fromUnit) {
		int level = toUnit.getValue() - fromUnit.getValue();
		if (level > 0) {
			// 目标单位大
			return Decimal.divide(value, Decimal.valueOf(getRate(level)));
		} else if (level < 0) {
			// 目标单位小
			return Decimal.multiply(value, Decimal.valueOf(getRate(level)));
		}
		// 单位相同
		return value;
	}
}
