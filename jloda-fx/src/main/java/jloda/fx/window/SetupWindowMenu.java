/*
 * SetupWindowMenu.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.window;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;

import java.util.ArrayList;

/**
 * set up the window menu
 * Daniel Huson, 9.2019, 4.2026
 */
public class SetupWindowMenu {
	public static void apply(IMainWindow window, Menu windowMenu) {
		final ArrayList<MenuItem> originalWindowMenuItems = new ArrayList<>(windowMenu.getItems());

		final InvalidationListener invalidationListener = observable -> {
			synchronized (originalWindowMenuItems) {
				// Keep the static items in place and rebuild only the dynamic per-window tail. Replacing the
				// whole list (setAll) on a live macOS system menu can throw IndexOutOfBoundsException in
				// com.sun.glass.ui.Menu.insert, so we remove/add only the entries that actually change.
				if (windowMenu.getItems().size() > originalWindowMenuItems.size())
					windowMenu.getItems().remove(originalWindowMenuItems.size(), windowMenu.getItems().size());
				if (window.getStage().isFocused()) {
					var newMenuItems = new ArrayList<MenuItem>();
					var count = 0;
					for (var otherWindow : MainWindowManager.getInstance().getMainWindows()) {
						if (otherWindow.getStage() != null) {
							var title = otherWindow.getStage().getTitle();
							if (title != null) {
								var menuItem = new MenuItem(title.replaceAll(" - .*", ""));
								menuItem.setOnAction((e) -> otherWindow.getStage().toFront());
								if (count < 9)
									menuItem.setAccelerator(new KeyCharacterCombination("" + (++count), KeyCombination.SHORTCUT_DOWN));
								newMenuItems.add(menuItem);
							}
						}
						if (MainWindowManager.getInstance().getAuxiliaryWindows(otherWindow) != null) {
							for (var auxStage : MainWindowManager.getInstance().getAuxiliaryWindows(otherWindow)) {
								var title = auxStage.getTitle();
								if (title != null) {
									var menuItem = new MenuItem(title.replaceAll(" - .*", ""));
									menuItem.setOnAction((e) -> auxStage.toFront());
									newMenuItems.add(menuItem);

								}
							}
						}
					}
					windowMenu.getItems().addAll(newMenuItems);
				}
			}
		};
		MainWindowManager.getInstance().changedProperty().addListener(new WeakInvalidationListener(invalidationListener));
		window.getStage().titleProperty().addListener(e -> MainWindowManager.getInstance().fireChanged());
		window.getStage().focusedProperty().addListener(invalidationListener);
	}


}
