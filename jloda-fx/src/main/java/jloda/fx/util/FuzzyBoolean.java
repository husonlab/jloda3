/*
 *  FuzzyBoolean.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.util;

import javafx.beans.property.ObjectProperty;
import javafx.scene.control.CheckBox;

/**
 * three state boolean property
 * Daniel Huson, 3.2024
 */
public enum FuzzyBoolean {
	True, False, Indeterminant;

	private static boolean changing;

	public static void setupCheckBox(CheckBox checkBox, ObjectProperty<FuzzyBoolean> fuzzyBoolean) {
		checkBox.setAllowIndeterminate(true);
		if (fuzzyBoolean.get() != null) {
			switch (fuzzyBoolean.get()) {
				case True -> checkBox.setSelected(true);
				case Indeterminant -> checkBox.setIndeterminate(true);
				case False -> {
					checkBox.setSelected(false);
					checkBox.setIndeterminate(false);
				}
			}
		}

		fuzzyBoolean.addListener((v, o, n) -> {
			if (!changing) {
				changing = true;
				try {
					switch (n) {
						case True -> checkBox.setSelected(true);
						case Indeterminant -> checkBox.setIndeterminate(true);
						case False -> {
							checkBox.setSelected(false);
							checkBox.setIndeterminate(false);
						}
					}
				} finally {
					changing = false;
				}
			}
		});

		checkBox.indeterminateProperty().addListener((v, o, n) -> {
			if (!changing) {
				changing = true;
				try {
					if (n)
						fuzzyBoolean.set(Indeterminant);
				} finally {
					changing = false;
				}
			}
		});

		checkBox.selectedProperty().addListener((v, o, n) -> {
			if (!changing) {
				changing = true;
				try {
					fuzzyBoolean.set(n ? True : False);
				} finally {
					changing = false;
				}
			}
		});
	}
}
