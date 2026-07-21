/*
 * OptionControls.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package jloda.fx.options;

import javafx.beans.property.Property;
import javafx.scene.control.*;
import javafx.util.StringConverter;

/**
 * sets up JavaFX controls from the metadata declared in an {@link Option} annotation,
 * so that tooltips, ranges and legal values are never written out a second time by hand
 * <p>
 * Daniel Huson, 7.2026
 */
public class OptionControls {

	/**
	 * the tooltip text for an option, its description together with its range, if bounded
	 */
	public static String tooltipText(OptionItem item) {
		var range = item.getRangeString();
		return (range.isEmpty() ? item.getDescription() : item.getDescription() + " " + range);
	}

	/**
	 * sets the tooltip of a control from the description and range of an option
	 */
	public static void setupTooltip(Control control, OptionItem item) {
		control.setTooltip(new Tooltip(tooltipText(item)));
	}

	/**
	 * sets up a spinner for a numerical option: range and tooltip are taken from the annotation,
	 * the initial value from the property, and the two are then kept in sync
	 */
	public static void bindSpinner(Spinner<Double> spinner, OptionItem item, double step) {
		if (item.getValueType() != OptionValueType.Double && item.getValueType() != OptionValueType.Integer)
			throw new IllegalArgumentException("bindSpinner: option '" + item.getName() + "' is not numerical: " + item.getValueType());

		var property = numberProperty(item);
		var min = (Double.isNaN(item.getMin()) ? -Double.MAX_VALUE : item.getMin());
		var max = (Double.isNaN(item.getMax()) ? Double.MAX_VALUE : item.getMax());
		var initial = clamp(property.getValue().doubleValue(), min, max);

		spinner.setEditable(true);

		var valueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);

		valueFactory.setConverter(new StringConverter<>() {
			@Override
			public String toString(Double value) {
				if (value == null)
					return "";
				var d = (double) value;
				return (d == (long) d ? Long.toString((long) d) : Double.toString(d));
			}

			@Override
			public Double fromString(String text) {
				if (text == null || text.isBlank())
					return valueFactory.getValue();
				try {
					return clamp(Double.parseDouble(text.trim()), min, max);
				} catch (NumberFormatException ex) {
					return valueFactory.getValue();
				}
			}
		});

		spinner.setValueFactory(valueFactory);

		// commit on Enter and on focus loss
		spinner.getEditor().setOnAction(e -> commitEditorText(spinner));
		spinner.getEditor().focusedProperty().addListener((v, o, n) -> {
			if (!n)
				commitEditorText(spinner);
		});

		valueFactory.valueProperty().addListener((v, o, n) -> {
			if (n != null) {
				var value = clamp(n, min, max);
				if (Double.compare(value, property.getValue().doubleValue()) != 0)
					property.setValue(value);
			}
		});

		property.addListener((v, o, n) -> {
			var value = clamp(n.doubleValue(), min, max);
			var current = valueFactory.getValue();
			if (current == null || Double.compare(value, current) != 0)
				valueFactory.setValue(value);
		});

		setupTooltip(spinner, item);
	}

	/**
	 * sets up a text field for an option: the text is validated against the declared type and range
	 * when committed, and the field always shows the value that the option actually has
	 */
	public static void bindTextField(TextField textField, OptionItem item) {
		textField.setOnAction(e -> {
			item.setValueString(textField.getText());
			textField.setText(item.getValueString());
		});
		textField.focusedProperty().addListener((v, o, n) -> {
			if (!n) {
				item.setValueString(textField.getText());
				textField.setText(item.getValueString());
			}
		});
		textField.setText(item.getValueString());
		item.getProperty().addListener((v, o, n) -> textField.setText(item.getValueString()));
		setupTooltip(textField, item);
	}

	/**
	 * sets up a combo box for an option that has legal values, for example an enum option
	 */
	public static void bindComboBox(ComboBox<String> comboBox, OptionItem item) {
		if (!item.getLegalValues().isEmpty())
			comboBox.getItems().setAll(item.getLegalValues());
		comboBox.setValue(item.getValueString());
		comboBox.valueProperty().addListener((v, o, n) -> {
			if (n != null)
				item.setValueString(n);
		});
		item.getProperty().addListener((v, o, n) -> comboBox.setValue(item.getValueString()));
		setupTooltip(comboBox, item);
	}

	/**
	 * sets up a check box for a boolean option
	 */
	public static void bindCheckBox(CheckBox checkBox, OptionItem item) {
		if (item.getValueType() != OptionValueType.Boolean)
			throw new IllegalArgumentException("bindCheckBox: option '" + item.getName() + "' is not boolean: " + item.getValueType());
		@SuppressWarnings("unchecked") var property = (Property<Boolean>) (Property<?>) item.getProperty();
		checkBox.selectedProperty().bindBidirectional(property);
		setupTooltip(checkBox, item);
	}

	@SuppressWarnings("unchecked")
	private static Property<Number> numberProperty(OptionItem item) {
		return (Property<Number>) (Property<?>) item.getProperty();
	}

	private static void commitEditorText(Spinner<Double> spinner) {
		var valueFactory = spinner.getValueFactory();
		if (valueFactory == null)
			return;
		try {
			valueFactory.setValue(valueFactory.getConverter().fromString(spinner.getEditor().getText()));
		} catch (Exception ignored) {
			spinner.getEditor().setText(valueFactory.getConverter().toString(valueFactory.getValue()));
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
