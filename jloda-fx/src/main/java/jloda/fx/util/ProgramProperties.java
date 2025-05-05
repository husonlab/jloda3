/*
 * ProgramProperties.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.util;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Spinner;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.Locale;
import java.util.function.Function;

/**
 * program properties for JavaFX program
 * Daniel Huson, 10/2022
 */
public class ProgramProperties extends jloda.util.ProgramProperties {

	static private javafx.scene.text.Font defaultFontFX = null;

	static {
		try {
			defaultFontFX = javafx.scene.text.Font.font("Arial", 12);
			Locale.setDefault(new Locale("en", "US"));
		} catch (Exception ignored) {
		}
	}

	static private final ObservableList<Image> programIconsFX = FXCollections.observableArrayList();

	public static ObservableList<Image> getProgramIconsFX() {
		return programIconsFX;
	}

	public static Color get(Object name, Color defaultColorFX) {
		String value = (String) props.get(name);
		if (value == null || value.equalsIgnoreCase("null"))
			return defaultColorFX;
		else
			return Color.valueOf(value);
	}

	public static void put(String key, Color colorFX) {
		if (colorFX == null)
			props.setProperty(key, "null");
		else
			props.setProperty(key, "" + colorFX);
	}

	public static javafx.scene.text.Font getDefaultFontFX() {
		return defaultFontFX;
	}

	public static void setDefaultFontFX(javafx.scene.text.Font defaultFontFX) {
		ProgramProperties.defaultFontFX = defaultFontFX;
	}

	public static void track(ObjectProperty<Color> property, Color defaultValue) {
		track((property.getBean() != null ? property.getBean().getClass().getName() : "") + property.getName(), property, defaultValue);
	}

	public static void track(String label, ObjectProperty<Color> property, Color defaultValue) {
		try {
			if (!property.isBound()) {
				property.set(get(label, defaultValue));
			}
			property.addListener((v, o, n) -> put(label, property.get()));
		} catch (Exception ignore) {
		}
	}


	public static void track(IntegerProperty property, Integer defaultValue) {
		try {
			var label = (property.getBean() != null ? property.getBean().getClass().getName() : "") + property.getName();
			track(label, property, defaultValue);
		} catch (Exception ignore) {
		}
	}

	public static void track(String label, IntegerProperty property, Integer defaultValue) {
		try {
			if (!property.isBound()) {
				property.set(get(label, defaultValue));
			}
			property.addListener((v, o, n) -> put(label, property.get()));
		} catch (Exception ignore) {
		}
	}

	public static <T> void track(String label, Spinner<T> spinner, Function<String, T> function, T defaultValue) {
		try {
			var string = ProgramProperties.get(label, defaultValue.toString());
			if (string != null) {
				spinner.getValueFactory().setValue(function.apply(string));
			}
			spinner.valueProperty().addListener((v, o, n) -> put(label, n == null ? null : n.toString()));

		} catch (Exception ignore) {
		}
	}


	public static void track(DoubleProperty property, Double defaultValue) {
		try {
			var label = (property.getBean() != null ? property.getBean().getClass().getName() : "") + property.getName();
			track(label, property, defaultValue);
		} catch (Exception ignore) {
		}
	}

	public static void track(String label, DoubleProperty property, Double defaultValue) {
		try {
			if (!property.isBound()) {
				property.set(get(label, defaultValue));
			}
			property.addListener((v, o, n) -> put(label, property.get()));
		} catch (Exception ignore) {
		}
	}

	public static void track(BooleanProperty property, Boolean defaultValue) {
		try {
			var label = (property.getBean() != null ? property.getBean().getClass().getName() : "") + property.getName();
			track(label, property, defaultValue);
		} catch (Exception ignore) {
		}
	}

	public static void track(String label, BooleanProperty property, Boolean defaultValue) {
		try {
			if (!property.isBound()) {
				property.set(get(label, defaultValue));
			}
			property.addListener((v, o, n) -> put(label, property.get()));
		} catch (Exception ignore) {
		}
	}

	public static void track(StringProperty property, String defaultValue) {
		try {
			var label = (property.getBean() != null ? property.getBean().getClass().getName() : "") + property.getName();
			track(label, property, defaultValue);
		} catch (Exception ignore) {
		}
	}

	public static void track(String label, StringProperty property, String defaultValue) {
		try {
			if (!property.isBound()) {
				property.set(get(label, defaultValue));
			}
			property.addListener((v, o, n) -> put(label, property.get()));
		} catch (Exception ignore) {
		}
	}

	public static <T> void track(ObjectProperty<T> property, Function<String, T> valueOf, T defaultValue) {
		track((property.getBean() != null ? property.getBean().getClass().getName() : "") + property.getName(), property, valueOf, defaultValue);
	}

	public static <T> void track(String label, ObjectProperty<T> property, Function<String, T> valueOf, T defaultValue) {
		try {
			if (!property.isBound()) {
				property.set(valueOf.apply(get(label, defaultValue.toString())));
			}
			property.addListener((v, o, n) -> put(label, property.get() != null ? property.get().toString() : ""));
		} catch (Exception ignore) {
		}
	}

}
