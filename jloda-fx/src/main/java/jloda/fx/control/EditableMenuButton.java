/*
 * EditableMenuButton.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.control;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Side;
import javafx.scene.control.*;
import jloda.util.NumberUtils;
import jloda.util.Single;
import jloda.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * an editable menu button, to replace combo-boxes when running on iOS
 * Daniel Huson, 1.2025
 */
public class EditableMenuButton {

	// Max number of suggestions in the autocomplete popup
	private static final int MAX_SUGGESTIONS = 10;

	/**
	 * Sets up a MenuButton that behaves like a (possibly editable) ComboBox<String>.
	 *
	 * @param button     the MenuButton to configure
	 * @param itemLabels selectable string items
	 * @param editable   whether the value can be edited directly
	 * @return StringProperty tracking the selected/edited value
	 */
	public static StringProperty setup(MenuButton button, List<String> itemLabels, boolean editable, DoubleProperty doubleProperty) {
		button.setStyle("""
								    -fx-padding: 0;
								    -fx-background-insets: 0;
								    -fx-background-radius: 4;
								    -fx-min-height: 28;
								    -fx-pref-height: 28;
								    -fx-border-color: rgba(0,0,0,0.25);
								    -fx-border-width: 0.75;
				    				-fx-font-size: 12;
				""");

		var value = new SimpleStringProperty("");

		var editor = new TextField();
		editor.setEditable(editable);
		editor.setPrefColumnCount(8);
		editor.setStyle("""
				    -fx-padding: 2 6 2 6;
				    -fx-background-radius: 2;
				    -fx-background-insets: 0;
							-fx-font-size: 12;
				""");

		// Autocomplete popup (only used if editable == true)
		final ContextMenu suggestionsPopup = new ContextMenu();
		suggestionsPopup.setAutoHide(true);

		// Commit on ENTER
		editor.setOnAction(e -> {
			if (doubleProperty == null || NumberUtils.isDouble(editor.getText())) {
				value.set(editor.getText());
			}
		});

		// Commit on focus loss; hide suggestions
		editor.focusedProperty().addListener((obs, o, n) -> {
			if (!n) {
				if (doubleProperty == null || NumberUtils.isDouble(editor.getText())) {
					value.set(editor.getText());
				}
				suggestionsPopup.hide();
			}
		});

		// Keep editor and value in sync; preserve your "insert new typed values into menu" behavior
		value.addListener((obs, o, n) -> {
			var s = (n == null ? "" : n);

			if (!editor.getText().equals(s)) {
				editor.setText(s);
				editor.positionCaret(s.length());
			}

			if (!s.isBlank() && button.getItems().stream().noneMatch(b -> s.equals(b.getText()))) {
				var menuItem = new MenuItem(s);
				menuItem.setOnAction(e -> value.set(s));
				button.getItems().add(0, menuItem);
			}
		});

		button.setGraphic(editor);
		button.setText(null);

		// --- Menu items ---
		button.getItems().clear();
		for (var label : itemLabels) {
			if (label != null && !label.isBlank()) {
				var menuItem = new MenuItem(label);
				menuItem.setOnAction(e -> value.set(label));
				button.getItems().add(menuItem);
			}
		}

		// When showing the full menu, hide autocomplete to avoid stacked popups
		button.showingProperty().addListener((obs, was, is) -> {
			if (is) suggestionsPopup.hide();
		});

		// --- Autocomplete wiring ---
		if (editable) {
			editor.textProperty().addListener((obs, oldText, newText) -> {
				// Only show suggestions while actively editing
				if (!editor.isFocused() || !editor.isEditable()) {
					return;
				}
				updateSuggestions(suggestionsPopup, editor, itemLabels, newText, value);
			});
		}

		// --- Initial value ---
		if (itemLabels != null && !itemLabels.isEmpty()) {
			var first = itemLabels.get(0);
			value.set(first == null ? "" : first);
		}

		if (doubleProperty != null) {
			var inUpdate = new Single<>(false);
			value.addListener((v, o, n) -> {
				if (!inUpdate.get()) {
					inUpdate.set(true);
					try {
						if (NumberUtils.isDouble(n))
							doubleProperty.set(NumberUtils.parseDouble(n));
					} finally {
						inUpdate.set(false);
					}
				}
			});
			doubleProperty.addListener((v, o, n) -> {
				if (!inUpdate.get()) {
					inUpdate.set(true);
					try {
						value.set(StringUtils.removeTrailingZerosAfterDot(n.doubleValue()));
					} finally {
						inUpdate.set(false);
					}
				}
			});
		}

		return value;
	}

	private static void updateSuggestions(ContextMenu popup, TextField editor, List<String> sourceItems, String typed, StringProperty value) {
		String q = typed == null ? "" : typed.trim();

		if (q.isEmpty()) {
			popup.hide();
			return;
		}

		final String qLower = q.toLowerCase(Locale.ROOT);

		// Build a de-duplicated list preserving order
		var unique = new LinkedHashSet<String>();
		if (sourceItems != null) {
			for (var s : sourceItems) {
				if (s != null && !s.isBlank()) unique.add(s);
			}
		}

		// Filter: contains; rank: startsWith first
		var starts = new ArrayList<String>();
		var contains = new ArrayList<String>();

		for (var s : unique) {
			var sl = s.toLowerCase(Locale.ROOT);
			if (!sl.contains(qLower)) continue;
			if (sl.startsWith(qLower)) starts.add(s);
			else contains.add(s);
		}

		// Sort within groups for stability
		starts.sort(String::compareToIgnoreCase);
		contains.sort(String::compareToIgnoreCase);

		var matches = new ArrayList<String>(Math.min(MAX_SUGGESTIONS, starts.size() + contains.size()));
		for (var s : starts) {
			if (matches.size() >= MAX_SUGGESTIONS) break;
			matches.add(s);
		}
		for (var s : contains) {
			if (matches.size() >= MAX_SUGGESTIONS) break;
			matches.add(s);
		}

		if (matches.isEmpty()) {
			popup.hide();
			return;
		}

		// Rebuild popup items
		popup.getItems().clear();
		for (var m : matches) {
			var label = new Label(m);
			var cmi = new CustomMenuItem(label, true);
			cmi.setOnAction(e -> {
				value.set(m);
				popup.hide();
				Platform.runLater(() -> editor.positionCaret(editor.getText().length()));
			});
			popup.getItems().add(cmi);
		}

		// Show below the editor; if already showing, keep it open (menu items updated)
		if (!popup.isShowing()) {
			popup.show(editor, Side.BOTTOM, 0, 0);
		}
	}
}