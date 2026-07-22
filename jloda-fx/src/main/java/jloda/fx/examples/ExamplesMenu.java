/*
 *  ExamplesMenu.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.examples;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;

import java.util.function.Consumer;

/**
 * Builds an "Open Example" menu from bundled examples.
 * <p>
 * Typical use, in the application's window presenter:
 * <pre>
 *   var manager = ExamplesManager.createOrNull(CatReNet.class, "/catrenet/examples");
 *   if (manager != null)
 *       controller.getFileMenu().getItems().add(1,
 *           ExamplesMenu.create("Open Example", manager, entry -&gt; openExample(manager, entry)));
 * </pre>
 * The handler decides what "open" means; loading the example as a new <em>untitled</em>
 * document is recommended, so that saving prompts for a location instead of writing back
 * over a bundled example.
 */
public class ExamplesMenu {

	/**
	 * Creates a new menu populated with the given examples.
	 */
	public static Menu create(String title, ExamplesManager manager, Consumer<ExampleEntry> handler) {
		var menu = new Menu(title);
		populate(menu, manager, handler);
		return menu;
	}

	/**
	 * Populates an existing menu, e.g. one declared in FXML. Any current items are replaced.
	 * The menu is disabled when there is nothing to show.
	 */
	public static void populate(Menu menu, ExamplesManager manager, Consumer<ExampleEntry> handler) {
		menu.getItems().clear();

		if (manager != null) {
			for (var entry : manager.entries()) {
				var item = new MenuItem(entry.displayName());
				item.setOnAction(e -> handler.accept(entry));
				menu.getItems().add(item);
			}
		}
		menu.setDisable(menu.getItems().isEmpty());
	}
}
