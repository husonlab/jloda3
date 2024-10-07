/*
 * RecentFilesManager.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import jloda.util.FileUtils;
import jloda.util.ProgramProperties;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * manages recent files
 * Daniel Huson, 2.2018
 */
public class RecentFilesManager {
	public static boolean SHOW_PATH = false;

	private static RecentFilesManager instance;

	private final int maxNumberRecentFiles;
	private final ObservableList<String> recentFiles;
	private final ArrayList<WeakReference<Menu>> menuReferences = new ArrayList<>();

	private final ObjectProperty<Consumer<String>> fileOpener = new SimpleObjectProperty<>();

	private final BooleanProperty disable = new SimpleBooleanProperty(false);

	/**
	 * constructor
	 */
	private RecentFilesManager() {
		recentFiles = FXCollections.observableArrayList();

		maxNumberRecentFiles = ProgramProperties.get("MaxNumberRecentFiles", 40);

		for (var fileName : ProgramProperties.get("RecentFiles", new String[0])) {
			if (FileUtils.fileExistsAndIsNonEmpty(fileName) && recentFiles.size() < maxNumberRecentFiles && !recentFiles.contains(fileName))
				recentFiles.add(fileName);
		}

		recentFiles.addListener((ListChangeListener<String>) (c) -> Platform.runLater(() -> {

			var deadRefs = new HashSet<WeakReference<Menu>>();

			while (c.next()) {
				if (c.wasRemoved() && c.getRemoved() instanceof OpenFileMenuItem removed) {
					for (var ref : menuReferences) {
						var menu = ref.get();
						if (menu != null) {
							var toDelete = new ArrayList<OpenFileMenuItem>();
							for (var menuItem : menu.getItems()) {
								if (menuItem instanceof OpenFileMenuItem openFileMenuItem) {
									if (removed.getFile().equals(openFileMenuItem.getFile()))
										toDelete.add(openFileMenuItem);
								}
							}
							menu.getItems().removeAll(toDelete);
						} else
							deadRefs.add(ref);
					}
				}
				if (c.wasAdded()) {
					for (var ref : menuReferences) {
						var menu = ref.get();
						if (menu != null) {
							try {
								for (var fileName : c.getAddedSubList()) {
									// make sure not present:
									menu.getItems().removeAll(menu.getItems().stream().filter(item -> item instanceof OpenFileMenuItem openFileMenuItem && openFileMenuItem.getFile().getPath().equals((new File(fileName)).getPath())).toList());
									var openMenuItem = new OpenFileMenuItem(fileName);
									openMenuItem.setOnAction(e -> fileOpener.get().accept(fileName));
									openMenuItem.disableProperty().bind(disable);
									menu.getItems().add(0, openMenuItem);
								}
							} catch (Exception ignored) {
							}
						} else
							deadRefs.add(ref);
					}
				}
			}

			if (!deadRefs.isEmpty()) {
				menuReferences.removeAll(deadRefs); // purge anything that has been garbage collected
			}
			ProgramProperties.put("RecentFiles", recentFiles.toArray(new String[0]));
		}));
	}

	/**
	 * get the instance
	 *
	 * @return instance
	 */
	public static RecentFilesManager getInstance() {
		if (instance == null)
			instance = new RecentFilesManager();
		return instance;
	}

	/**
	 * create the recent files menu
	 */
	public void setupMenu(final Menu menu) {
		menuReferences.add(new WeakReference<>(menu));

		if (fileOpener.get() != null) {
			for (var fileName : recentFiles) {
				var openMenuItem = new OpenFileMenuItem(fileName);
				openMenuItem.setOnAction(e -> fileOpener.get().accept(fileName));
				openMenuItem.disableProperty().bind(disable);
				menu.getItems().add(openMenuItem);
			}
		}
	}

	private static class OpenFileMenuItem extends MenuItem {
		private final File file;

		public OpenFileMenuItem(String fileName) {
			this.file = new File(fileName);
			if (SHOW_PATH)
				setText(fileName);
			else
				setText(FileUtils.getFileNameWithoutPath(fileName));
		}

		public File getFile() {
			return file;
		}
	}

	/**
	 * get the list of recent files
	 *
	 * @return recent files
	 */
	public ReadOnlyListWrapper<String> getRecentFiles() {
		return new ReadOnlyListWrapper<>(recentFiles);
	}

	/**
	 * inserts a recent file to top of menu
	 */
	public void insertRecentFile(String fileName) {
		// remove if already present and then add, this will bring to top of list
		if (recentFiles.contains(fileName))
			removeRecentFile(fileName);
		recentFiles.add(0, fileName);
		if (recentFiles.size() >= maxNumberRecentFiles)
			recentFiles.remove(maxNumberRecentFiles - 1);
	}

	/**
	 * remove a recent file
	 */
	public void removeRecentFile(String fileName) {
		recentFiles.remove(fileName);
	}

	public Consumer<String> getFileOpener() {
		return fileOpener.get();
	}

	public ObjectProperty<Consumer<String>> fileOpenerProperty() {
		return fileOpener;
	}

	public void setFileOpener(Consumer<String> fileOpener) {
		this.fileOpener.set(fileOpener);
	}

	public BooleanProperty disableProperty() {
		return disable;
	}
}
