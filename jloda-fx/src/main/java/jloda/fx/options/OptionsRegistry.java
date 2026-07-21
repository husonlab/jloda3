/*
 *  OptionsRegistry.java Copyright (C) 2026 Daniel H. Huson
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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * all options declared by one or more option-carrying objects, collected by reflection
 * <p>
 * Any field annotated with {@link Option} and holding a JavaFX property is picked up, so
 * adding a new option to the program requires nothing beyond annotating its field.
 * <p>
 * Daniel Huson, 7.2026
 */
public class OptionsRegistry {
	private final Map<String, OptionItem> name2item = new LinkedHashMap<>();
	private final Map<String, OptionItem> alias2item = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * collects all annotated options declared by the given objects
	 */
	public static OptionsRegistry of(Object... carriers) {
		var registry = new OptionsRegistry();
		for (var carrier : carriers) {
			if (carrier != null)
				registry.scan(carrier);
		}
		return registry;
	}

	private void scan(Object carrier) {
		for (var clazz = carrier.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
			for (var field : clazz.getDeclaredFields()) {
				var annotation = field.getAnnotation(Option.class);
				if (annotation == null)
					continue;
				if (!Property.class.isAssignableFrom(field.getType()))
					throw new IllegalStateException("@Option on non-property field: " + clazz.getName() + "." + field.getName());
				try {
					field.setAccessible(true);
					var property = (Property<?>) field.get(carrier);
					if (property == null)
						throw new IllegalStateException("@Option field is null: " + clazz.getName() + "." + field.getName());
					var valueClass = determineValueClass(field, property);
					var valueType = OptionValueType.valueTypeOf(property, valueClass);
					if (valueType == null)
						throw new IllegalStateException("@Option of unsupported type: " + clazz.getName() + "." + field.getName());
					var name = (annotation.name().isBlank() ? defaultName(field.getName()) : annotation.name());
					var item = new OptionItem(name, property, valueType, valueClass, annotation);
					if (name2item.put(name, item) != null)
						throw new IllegalStateException("Duplicate option name: " + name);
					for (var alias : item.getAliases()) {
						alias2item.put(alias, item);
					}
				} catch (IllegalAccessException ex) {
					throw new IllegalStateException("Cannot access @Option field: " + clazz.getName() + "." + field.getName(), ex);
				}
			}
		}
	}

	/**
	 * determines the class of the property value, for object properties this is the type argument,
	 * e.g. TreeDiagramType for an ObjectProperty&lt;TreeDiagramType&gt;
	 */
	private static Class<?> determineValueClass(Field field, Property<?> property) {
		if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
			var arguments = parameterizedType.getActualTypeArguments();
			if (arguments.length == 1 && arguments[0] instanceof Class<?> valueClass)
				return valueClass;
		}
		return (property.getValue() != null ? property.getValue().getClass() : null);
	}

	/**
	 * the option name to use for a field, e.g. optionOutlineWidth -&gt; outline_width
	 */
	public static String defaultName(String fieldName) {
		var name = fieldName;
		if (name.length() > 6 && name.startsWith("option") && Character.isUpperCase(name.charAt(6)))
			name = name.substring(6);
		return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
	}

	/**
	 * all options, in declaration order
	 */
	public Collection<OptionItem> getItems() {
		return name2item.values();
	}

	/**
	 * gets the option of the given name, also accepting any name that the option was known by previously
	 *
	 * @return option, or null
	 */
	public OptionItem get(String name) {
		var item = name2item.get(name);
		return (item != null ? item : alias2item.get(name));
	}

	/**
	 * sets an option from its string representation, reporting, but tolerating, unknown names,
	 * so that files written by a newer version of the program can still be read
	 *
	 * @return true, if the value was accepted and set
	 */
	public boolean setValueString(String name, String value) {
		var item = get(name);
		if (item == null) {
			System.err.println("Unknown option: '" + name + "', ignored");
			return false;
		}
		return item.setValueString(value);
	}

	/**
	 * sets an additional check for the named option, see {@link OptionItem#setValidator}
	 */
	public OptionsRegistry setValidator(String name, java.util.function.Predicate<Object> validator) {
		var item = get(name);
		if (item == null)
			throw new IllegalArgumentException("Unknown option: " + name);
		item.setValidator(validator);
		return this;
	}

	/**
	 * a report of all options, their types, defaults, ranges and descriptions, for documentation
	 */
	public String report() {
		var buf = new StringBuilder();
		for (var item : getItems()) {
			buf.append("%-24s %-8s default=%-16s %-16s %s%n".formatted(item.getName(), item.getValueType(),
					item.getDefaultValue(), item.getRangeString(), item.getDescription()));
		}
		return buf.toString();
	}
}
