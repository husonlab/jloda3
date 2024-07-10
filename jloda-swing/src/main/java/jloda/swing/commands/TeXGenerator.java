/*
 * TeXGenerator.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.swing.commands;

import java.io.StringWriter;
import java.util.*;

/**
 * generate LaTeX description of menus
 * Daniel Huson, 11.2010
 */
public class TeXGenerator {
	/**
	 * Get LaTeX description of menus
	 * Format:
	 * Menu.menuLabel=name;item;item;...;item;  where  name is menu name
	 * and item is either the menuLabel of an action, | to indicate a separator
	 * or @menuLabel to indicate menuLabel name of a submenu
	 *
	 * @return menu description in LaTeX
	 */
	public static String getMenuLaTeX(CommandManager commandManager, String menuBarLayout, Hashtable<String, String> menusConfigurations) throws Exception {
		var w = new StringWriter();

		if (!menuBarLayout.startsWith("Menu."))
			menuBarLayout = "Menu." + menuBarLayout;
		String description = menusConfigurations.get(menuBarLayout);
		if (description == null)
			return null;
		var menuDescription = MenuCreator.getTokens(description);
		if (menuDescription.isEmpty())
			return null;
		var menuName = menuDescription.iterator().next();

		var labels = menuDescription.toArray(new String[0]);

		w.write("\\section{The " + menuName + " menu}\n");

		if (menuName.equals("Recent Files")) {
			w.write("The %s menu \\index{%s} contains a list of recently opened documents%n%n".formatted(menuName, menuName));
		} else if (labels.length > 1) {
			w.write("The %s menu \\index{%s} contains the following items:%n%n".formatted(menuName, menuName));
			w.write("\\begin{itemize}\n");

			for (var i = 1; i < labels.length; i++) {
				var name = labels[i];

				if (name.startsWith("@")) {
					w.write("\\item The %s → %s submenu. \\index{%s → %s submenu}%n".formatted(menuName, name.substring(1), menuName, name.substring(1)));
				} else if (name.equals("|")) {
					// separator
				} else {
					var command = commandManager.getCommand(name);
					if (command != null) {
						name = command.getName(); // label might have been altName...
						var notMac = name.equals("Quit") || name.equals("About") || name.equals("About...") || name.equals("Preferences") || name.equals("Preferences...");
						name = name.replaceAll("_", "-");
						var des = command.getDescription();
						w.write("\\item The " + menuName + " → " + name + " item: \\index{"
								+ menuName + " → " + name + "}"
								+ (des != null ? des.replaceAll("_", "-") : " NONE") +
								(notMac ? " (Windows and Linux only)" : "") + ".\n");
					}
				}
			}
			w.write("\\end{itemize}\n\n");
		}
		return w.toString();
	}

	/**
	 * get a laTeX description of a tool bar
	 *
	 * @return LaTeX
	 */
	public static String getToolBarLaTeX(String configuration, CommandManager commandManager) {
		StringWriter w = new StringWriter();

		if (configuration != null) {
			w.write("\\section{Toolbar}\n");

			w.write("The toolbar contains the following items:\n\n");
			w.write("\\begin{itemize}\n");

			String[] tokens = configuration.split(";");
			for (String name : tokens) {
				if (name.equals("|")) {
					// separator
				} else {
					var command = commandManager.getCommand(name);
					if (command != null) {
						name = command.getName();
						// label might have been altName...
						w.write("\\item The " + name + " toolbar item: \\index{" + name + " toolbar item} " + command.getDescription().replaceAll("_", "-") + ".\n");
					}
				}
			}
			w.write("\\end{itemize}\n\n");
		}
		return w.toString();
	}

	private static final Map<String, Integer> mainAdditionalPopupMenuCount = new HashMap<>();


	/**
	 * get a laTeX description of a popup menu
	 */
	public static String getPopupMenuLaTeX(String menuName, String configuration, CommandManager commandManager) {
		var w = new StringWriter();

		if (configuration != null) {
			var commands = Arrays.stream(configuration.split(";")).filter(s -> !s.equals("|")).map(commandManager::getCommand)
					.filter(Objects::nonNull).toList();
			if (!commands.isEmpty()) {
				{
					menuName = menuName.replace("Viewer J Table", "Table Viewer");
					menuName = menuName.replace("Viewer J Tree", "Tree Viewer");

					int count = mainAdditionalPopupMenuCount.computeIfAbsent(menuName, k -> 0);
					mainAdditionalPopupMenuCount.put(menuName, count + 1);
					if (count > 0)
						menuName += " additional (" + count + ")";
				}

				w.write("\\section{" + menuName + " popup menu} \\index{" + menuName + " popup menu}\n");

				w.write("The " + menuName + " popup menu contains the following items:\n\n");
				w.write("\\begin{itemize}\n");

				for (var command : commands) {
					var name = command.getName(); // label might have been altName...
					w.write("\\item The " + name + " item: \\index{ " + name + " popup menu item} "
							+ command.getDescription().replaceAll("_", "-") + ".\n");
				}
				w.write("\\end{itemize}\n\n");
			}
		}
		return w.toString();
	}
}
