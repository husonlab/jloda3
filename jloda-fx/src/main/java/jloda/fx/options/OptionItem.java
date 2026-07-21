/*
 *  OptionItem.java Copyright (C) 2026 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jloda.fx.options;

import javafx.beans.property.Property;
import jloda.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * a single option, that is, an annotated property together with the metadata declared for it
 * Daniel Huson, 7.2026
 */
public class OptionItem {
	private final String name;
	private final Property<Object> property;
	private final OptionValueType valueType;
	private final Class<?> valueClass;
	private final String description;
	private final List<String> aliases;
	private final double min;
	private final double max;
	private final List<String> legalValues;
	private final boolean persist;
	private final String defaultValue;
	private Predicate<Object> validator;

	@SuppressWarnings("unchecked")
	OptionItem(String name, Property<?> property, OptionValueType valueType, Class<?> valueClass, Option annotation) {
		this.name = name;
		this.property = (Property<Object>) property;
		this.valueType = valueType;
		this.valueClass = valueClass;
		this.description = (annotation.description().isBlank() ? descriptionFromName(name) : annotation.description());
		this.aliases = List.of(annotation.aliases());
		this.min = annotation.min();
		this.max = annotation.max();
		this.persist = annotation.persist();

		if (valueType == OptionValueType.Enum && valueClass != null)
			this.legalValues = Arrays.stream(valueClass.getEnumConstants()).map(Object::toString).toList();
		else
			this.legalValues = List.of(annotation.legalValues());

		this.defaultValue = getValueString();
	}

	public String getName() {
		return name;
	}

	public Property<Object> getProperty() {
		return property;
	}

	public OptionValueType getValueType() {
		return valueType;
	}

	public String getDescription() {
		return description;
	}

	public List<String> getAliases() {
		return aliases;
	}

	/**
	 * smallest acceptable value, NaN if unbounded
	 */
	public double getMin() {
		return min;
	}

	/**
	 * largest acceptable value, NaN if unbounded
	 */
	public double getMax() {
		return max;
	}

	/**
	 * acceptable values, empty if unrestricted
	 */
	public List<String> getLegalValues() {
		return legalValues;
	}

	public boolean isPersist() {
		return persist;
	}

	/**
	 * the value that this option had when first seen, that is, the program default
	 */
	public String getDefaultValue() {
		return defaultValue;
	}

	/**
	 * sets an additional check to be applied to any value read from a file, for cases in which the
	 * set of acceptable values is only known at runtime and so cannot be declared in the annotation
	 */
	public void setValidator(Predicate<Object> validator) {
		this.validator = validator;
	}

	/**
	 * the current value, as a string
	 */
	public String getValueString() {
		return valueType.toString(property.getValue());
	}

	/**
	 * sets the value from a string, checking type, range and legal values
	 *
	 * @return true, if the value was accepted and set
	 */
	public boolean setValueString(String text) {
		var value = valueType.parse(text, valueClass);
		if (value == null) {
			System.err.println("Option '" + name + "': not a valid " + valueType + ": '" + text + "', ignored");
			return false;
		}
		if (!legalValues.isEmpty() && legalValues.stream().noneMatch(s -> s.equalsIgnoreCase(text))) {
			System.err.println("Option '" + name + "': value '" + text + "' not in " + legalValues + ", ignored");
			return false;
		}
		if (validator != null && !validator.test(value)) {
			System.err.println("Option '" + name + "': value '" + text + "' not acceptable, ignored");
			return false;
		}
		if (value instanceof Number number) {
			var d = number.doubleValue();
			var clamped = d;
			if (!java.lang.Double.isNaN(min))
				clamped = Math.max(min, clamped);
			if (!java.lang.Double.isNaN(max))
				clamped = Math.min(max, clamped);
			if (clamped != d) {
				System.err.println("Option '" + name + "': value " + d + " out of range " + getRangeString() + ", using " + clamped);
				value = (valueType == OptionValueType.Integer ? (Object) (int) Math.round(clamped) : (Object) clamped);
			}
		}
		property.setValue(value);
		return true;
	}

	/**
	 * a readable description for an option that does not declare one, e.g. outline_width -&gt; Outline width
	 */
	private static String descriptionFromName(String name) {
		var text = name.replace('_', ' ');
		return (text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1));
	}

	/**
	 * the acceptable range, as a string, empty if unbounded
	 */
	public String getRangeString() {
		var hasMin = !java.lang.Double.isNaN(min);
		var hasMax = !java.lang.Double.isNaN(max);
		if (!legalValues.isEmpty())
			return StringUtils.toString(legalValues, "|");
		else if (hasMin && hasMax)
			return "[" + StringUtils.removeTrailingZerosAfterDot(min) + "," + StringUtils.removeTrailingZerosAfterDot(max) + "]";
		else if (hasMin)
			return "≥ " + StringUtils.removeTrailingZerosAfterDot(min);
		else if (hasMax)
			return "≤ " + StringUtils.removeTrailingZerosAfterDot(max);
		else
			return "";
	}

	@Override
	public String toString() {
		return "%s: %s = %s".formatted(name, valueType, getValueString());
	}
}
