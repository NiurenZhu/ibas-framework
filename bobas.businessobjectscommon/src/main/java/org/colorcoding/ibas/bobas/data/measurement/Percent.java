package org.colorcoding.ibas.bobas.data.measurement;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

import org.colorcoding.ibas.bobas.MyConfiguration;
import org.colorcoding.ibas.bobas.data.KeyText;
import org.colorcoding.ibas.bobas.i18n.I18N;

/**
 * 百分百
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlType(name = "Percent", namespace = MyConfiguration.NAMESPACE_BOBAS_DATA)
public class Percent extends MeasurementDecimal<emPercentUnit> {

	private static final long serialVersionUID = -2434264026253052566L;

	static KeyText[] unitCharacters = null;

	public Percent() {

	}

	public Percent(BigDecimal value, emPercentUnit unit) {
		super(value);
		this.setUnit(unit);
	}

	public Percent(double value, emPercentUnit unit) {
		super(value);
		this.setUnit(unit);
	}

	@Override
	protected KeyText[] getUnitCharacters() {
		if (unitCharacters == null) {
			unitCharacters = parseUnitCharacters(this.getUnit().getClass());
		}
		return unitCharacters;
	}

	private emPercentUnit unit;

	@Override
	@XmlElement(name = "Unit")
	public emPercentUnit getUnit() {
		if (this.unit == null) {
			this.unit = emPercentUnit.HUNDRED_PERCENT;
		}
		return this.unit;
	}

	@Override
	public void setUnit(Object value) {
		this.setUnit((emPercentUnit) value);
	}

	public void setUnit(emPercentUnit value) {
		emPercentUnit oldValue = this.unit;
		this.unit = value;
		this.firePropertyChange("Unit", oldValue, this.unit);
	}

	@Override
	public int compareTo(IMeasurement<BigDecimal, emPercentUnit> o) {
		if (!this.getUnit().equals(o.getUnit())) {
			throw new ClassCastException(I18N.prop("msg_bobas_measurement_unit_not_match"));
		}
		return this.getValue().compareTo(o.getValue());
	}

}
