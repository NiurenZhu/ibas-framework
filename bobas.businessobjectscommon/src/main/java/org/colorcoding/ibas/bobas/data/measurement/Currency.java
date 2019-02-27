package org.colorcoding.ibas.bobas.data.measurement;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.i18n.I18N;

/**
 * 货币
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlType(name = "Currency", namespace = MyConfiguration.NAMESPACE_BOBAS_DATA)
public class Currency extends MeasurementDecimal<String> {

	private static final long serialVersionUID = 1010546725622041464L;

	public Currency() {

	}

	public Currency(BigDecimal value, String unit) {
		super(value);
		this.setUnit(unit);
	}

	public Currency(double value, String unit) {
		super(value);
		this.setUnit(unit);
	}

	private String unit;

	@Override
	@XmlElement(name = "Unit")
	public String getUnit() {
		if (this.unit == null) {
			this.unit = "";
		}
		return this.unit;
	}

	@Override
	public void setUnit(Object value) {
		this.setUnit((String) value);
	}

	public void setUnit(String value) {
		String oldValue = this.unit;
		this.unit = value;
		this.firePropertyChange("Unit", oldValue, this.unit);
	}

	@Override
	public int compareTo(IMeasurement<BigDecimal, String> o) {
		if (!this.getUnit().equals(o.getUnit())) {
			throw new ClassCastException(I18N.prop("msg_bobas_measurement_unit_not_match"));
		}
		return this.getValue().compareTo(o.getValue());
	}

}
