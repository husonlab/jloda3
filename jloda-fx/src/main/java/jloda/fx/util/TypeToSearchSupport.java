/*
 * TypeToSearchSupport.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.input.KeyEvent;

/**
 * combobox and choice-box type to search support
 * Daniel Huson, 1.2023
 */
public class TypeToSearchSupport {
	/**
	 * install type to search
	 *
	 * @param comboBox the combobox
	 */
	public static <T> void install(ComboBox<T> comboBox) {
		var pos = new SimpleIntegerProperty(0);
		var search = new SimpleStringProperty("");
		var service = new AService<>(() -> {
			Thread.sleep(500);
			search.set("");
			return true;
		});
		search.addListener((v, o, n) -> {
			if (!n.isBlank()) {
				if (o.isBlank()) {
					pos.set(0);
				}
				while (pos.get() < comboBox.getItems().size() && String.valueOf(comboBox.getItems().get(pos.get())).toLowerCase().compareTo(n) < 0) {
					pos.set(pos.get() + 1);
				}
				if (pos.get() < comboBox.getItems().size())
					comboBox.setValue(comboBox.getItems().get(pos.get()));
			}
		});

		comboBox.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			search.set(search.get() + e.getText());
			service.restart();
		});
	}

	/**
	 * install type to search
	 *
	 * @param comboBox the combobox
	 */
	public static <T> void install(ChoiceBox<T> comboBox) {
		var pos = new SimpleIntegerProperty(0);
		var search = new SimpleStringProperty("");
		var service = new AService<>(() -> {
			Thread.sleep(500);
			search.set("");
			return true;
		});
		search.addListener((v, o, n) -> {
			if (!n.isBlank()) {
				if (o.isBlank()) {
					pos.set(0);
				}
				while (pos.get() < comboBox.getItems().size() && String.valueOf(comboBox.getItems().get(pos.get())).toLowerCase().compareTo(n) < 0) {
					pos.set(pos.get() + 1);
				}
				if (pos.get() < comboBox.getItems().size())
					comboBox.setValue(comboBox.getItems().get(pos.get()));
			}
		});

		comboBox.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
			search.set(search.get() + e.getText());
			service.restart();
		});
	}
}