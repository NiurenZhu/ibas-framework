package org.colorcoding.ibas.bobas.data.measurement;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlType;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.data.Decimal;
import org.colorcoding.ibas.bobas.mapping.Value;

/**
 * 时间单位
 */
@XmlType(name = "emTimeUnit", namespace = MyConfiguration.NAMESPACE_BOBAS_DATA)
public enum emTimeUnit {
	/**
	 * 秒
	 */
	@Value("s")
	SECOND(0),
	/**
	 * 分钟
	 */
	@Value("m")
	MINUTE(1),
	/**
	 * 小时
	 */
	@Value("h")
	HOUR(2);

	private int intValue;
	private static java.util.HashMap<Integer, emTimeUnit> mappings;

	private synchronized static java.util.HashMap<Integer, emTimeUnit> getMappings() {
		if (mappings == null) {
			mappings = new java.util.HashMap<Integer, emTimeUnit>();
		}
		return mappings;
	}

	private emTimeUnit(int value) {
		intValue = value;
		emTimeUnit.getMappings().put(value, this);
	}

	public int getValue() {
		return intValue;
	}

	public static emTimeUnit forValue(int value) {
		return getMappings().get(value);
	}

	/**
	 * 基本转换率
	 */
	public final static int getRate(int level) {
		int rate = 1;
		for (int i = 0; i < Math.abs(level); i++) {
			// 每级别进率
			rate = rate * 60;
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
	public static double convert(emTimeUnit toUnit, double value, emTimeUnit fromUnit) {
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
	public static BigDecimal convert(emTimeUnit toUnit, BigDecimal value, emTimeUnit fromUnit) {
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
