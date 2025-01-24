/*
 * StateToggleButton.java Copyright (C) 2025 Daniel H. Huson
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

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import jloda.fx.icons.MaterialIcons;

import java.util.List;
import java.util.function.Function;

/**
 * This is a toggle button that switches to the next item, if the user clicks on it, but also supplies
 * a drop-down menu to choose from
 *
 * @param <E> type of elements in the list
 */
public class StateToggleButton<E> {
	private final MenuButton button;

	/**
	 * constructor
	 *
	 * @param states            list of states to display in menu
	 * @param iconFunction      maps each state to an icon. If value is null, show result of toString() rather than icon
	 * @param showButtonText    show the text of state on menu button?
	 * @param showMenuItemsText show text on menu items?
	 * @param selectedState     the selected state
	 * @param button            menu button, if it exists
	 */
	public StateToggleButton(List<E> states, Function<E, MaterialIcons> iconFunction, boolean showButtonText, boolean showMenuItemsText,
							 ObjectProperty<E> selectedState, MenuButton button) {
		this.button = (button != null ? button : new MenuButton());
		updateButton(this.button, states, selectedState, iconFunction, showButtonText);

		selectedState.addListener(e -> updateButton(this.button, states, selectedState, iconFunction, showButtonText));

		for (var state : states) {
			var icon = iconFunction.apply(state);
			var menuItem = new MenuItem(icon == null || showMenuItemsText ? state.toString() : null, icon == null ? null : MaterialIcons.graphic(icon));
			menuItem.setOnAction(e -> selectedState.set(state));
			menuItem.setUserData(state);
			this.button.getItems().add(menuItem);
		}
	}

	/**
	 * update the button
	 */
	private void updateButton(MenuButton stateButton, List<E> states, ObjectProperty<E> currentState, Function<E, MaterialIcons> iconFunction, boolean showButtonText) {
		var label = new Label();
		if (currentState.get() == null) {
			label.setText("  -  ");
		} else {
			var icon = iconFunction.apply(currentState.get());
			if (showButtonText || icon == null) {
				label.setText(currentState.get().toString());
			}
			if (icon != null) {
				label.setGraphic(MaterialIcons.graphic(iconFunction.apply(currentState.get())));
			}
		}
		label.setOnMouseClicked(e -> {
			var next = states.get((states.indexOf(currentState.get()) + 1) % states.size());
			currentState.set(next);
			e.consume();
		});
		label.setOnMousePressed(Event::consume);
		label.setOnMouseReleased(Event::consume);
		var menuItem = getMenuItem(currentState.get());
		if (menuItem != null) {
			label.setDisable(menuItem.isDisable());
		}
		label.setUserData(currentState.get());
		stateButton.setGraphic(label);
	}

	/**
	 * get the menu item for the given state
	 *
	 * @param state the state
	 * @return its menu item
	 */
	public MenuItem getMenuItem(E state) {
		for (var item : button.getItems()) {
			if (item.getUserData().equals(state)) {
				return item;
			}
		}
		return null;
	}

	/**
	 * set up disable binding for state.
	 *
	 * @param state           the state
	 * @param disableProperty the disable property to bind to
	 */
	public void bindDisable(E state, BooleanProperty disableProperty) {
		var menuItem = getMenuItem(state);
		if (menuItem != null) {
			menuItem.disableProperty().bind(disableProperty);
			InvalidationListener listener = e -> {
				if (button.getGraphic() instanceof Label label && menuItem.getUserData().equals(label.getUserData())) {
					label.setDisable(menuItem.isDisable());
				}
			};
			menuItem.disableProperty().addListener(listener);
			listener.invalidated(null);
		}
	}

	public MenuButton getButton() {
		return button;
	}
}
