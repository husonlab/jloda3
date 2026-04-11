/*
 * SetupWindowMenu.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCombination;
import jloda.fx.util.ProgramProperties;

import java.util.ArrayList;

/**
 * set up the window menu
 * Daniel Huson, 9.2019, 4.2026
 */
public class SetupWindowMenu {
	public static void apply(IMainWindow window, Menu windowMenu) {
		final ArrayList<MenuItem> originalWindowMenuItems = new ArrayList<>(windowMenu.getItems());

		final InvalidationListener invalidationListener = observable -> {
			var newMenuItems = new ArrayList<>(originalWindowMenuItems);
			var count = 0;
			for (var mainWindow : MainWindowManager.getInstance().getMainWindows()) {
				if (mainWindow.getStage() != null) {
					var title = mainWindow.getStage().getTitle();
					if (title != null) {
						var menuItem = new MenuItem(title.replaceAll("- " + jloda.fx.util.ProgramProperties.getProgramName(), ""));
						menuItem.setOnAction((e) -> mainWindow.getStage().toFront());
						if (count < 9)
							menuItem.setAccelerator(new KeyCharacterCombination("" + (++count), KeyCombination.SHORTCUT_DOWN));
						newMenuItems.add(menuItem);
					}
				}
				if (MainWindowManager.getInstance().getAuxiliaryWindows(mainWindow) != null) {
					for (var auxStage : MainWindowManager.getInstance().getAuxiliaryWindows(mainWindow)) {
						var title = auxStage.getTitle();
						if (title != null) {
							var menuItem = new MenuItem(title.replaceAll("- " + ProgramProperties.getProgramName(), ""));
							menuItem.setOnAction((e) -> auxStage.toFront());
							newMenuItems.add(menuItem);

						}
					}
				}
			}
			windowMenu.getItems().setAll(newMenuItems);
		};
		Platform.runLater(() -> {
			MainWindowManager.getInstance().changedProperty().addListener(invalidationListener);
			invalidationListener.invalidated(null);
			window.getStage().titleProperty().addListener(e -> MainWindowManager.getInstance().fireChanged());
		});
	}
}
