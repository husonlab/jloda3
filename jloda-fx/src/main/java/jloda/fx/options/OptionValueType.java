/*
 *  OptionValueType.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.beans.property.*;
import jloda.fx.util.ColorUtilsFX;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

/**
 * the value types supported for options, together with their string conversions
 * Daniel Huson, 7.2026
 */
public enum OptionValueType {
	Integer, Double, Boolean, String, Enum, Color;

	/**
	 * determines the value type of a property
	 *
	 * @return type, or null, if not supported
	 */
	public static OptionValueType valueTypeOf(Property<?> property, Class<?> valueClass) {
		if (property instanceof IntegerProperty)
			return Integer;
		else if (property instanceof DoubleProperty || property instanceof FloatProperty)
			return Double;
		else if (property instanceof BooleanProperty)
			return Boolean;
		else if (property instanceof StringProperty)
			return String;
		else if (valueClass != null && valueClass.isEnum())
			return Enum;
		else if (valueClass != null && javafx.scene.paint.Color.class.isAssignableFrom(valueClass))
			return Color;
		else
			return null;
	}

	/**
	 * can the given text be parsed as a value of this type?
	 */
	public boolean isValid(String text, Class<?> enumClass) {
		if (text == null)
			return false;
		return switch (this) {
			case Integer -> NumberUtils.isInteger(text);
			case Double -> NumberUtils.isDouble(text);
			case Boolean -> NumberUtils.isBoolean(text);
			case String -> true;
			case Color -> ColorUtilsFX.isColor(text);
			case Enum -> parseEnum(enumClass, text) != null;
		};
	}

	/**
	 * parses the text as a value of this type
	 *
	 * @return value, or null, if not parsable
	 */
	public Object parse(String text, Class<?> enumClass) {
		if (!isValid(text, enumClass))
			return null;
		return switch (this) {
			case Integer -> NumberUtils.parseInt(text);
			case Double -> NumberUtils.parseDouble(text);
			case Boolean -> NumberUtils.parseBoolean(text);
			case String -> text;
			case Color -> ColorUtilsFX.parseColor(text);
			case Enum -> parseEnum(enumClass, text);
		};
	}

	/**
	 * converts a value of this type to its string representation
	 */
	public String toString(Object value) {
		if (value == null)
			return null;
		return switch (this) {
			case Double -> StringUtils.trim("%.8f", ((Number) value).doubleValue());
			case Color -> ColorUtilsFX.toWeb((javafx.scene.paint.Color) value);
			default -> value.toString();
		};
	}

	private static Object parseEnum(Class<?> enumClass, String text) {
		if (enumClass == null || !enumClass.isEnum())
			return null;
		for (var value : enumClass.getEnumConstants()) {
			if (value.toString().equalsIgnoreCase(text))
				return value;
		}
		return null;
	}
}
