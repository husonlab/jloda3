/*
 * FileChooserManager.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.stage.FileChooser;
import javafx.stage.Window;
import jloda.util.ProgramProperties;

import java.io.File;
import java.util.List;

/**
 * shows JavaFX file choosers that remember, in ProgramProperties, the directory last used for a given
 * purpose (the "key"), so the next open/save/export starts in the same place, across program restarts.
 * <p>
 * Each show* method is a drop-in replacement for the matching FileChooser.show* method, with one extra
 * argument, the properties key. Before showing, it points the chooser at the remembered directory (a
 * remembered directory takes precedence over any fallback the caller set with setInitialDirectory);
 * after a file is chosen, it stores that file's directory back under the key. Different keys keep
 * different memories, so, e.g., "opened" and "exported" locations do not overwrite each other.
 * <p>
 * Daniel Huson, 8.2026
 */
public class FileChooserManager {
	/**
	 * show an open-file dialog that remembers its directory under the given key
	 *
	 * @return the chosen file, or null
	 */
	public static File showOpenDialog(Window owner, FileChooser fileChooser, String key) {
		applyInitialDirectory(fileChooser, key);
		return rememberDirectory(fileChooser.showOpenDialog(owner), key);
	}

	/**
	 * show an open-multiple-files dialog that remembers its directory under the given key
	 *
	 * @return the chosen files, or null
	 */
	public static List<File> showOpenMultipleDialog(Window owner, FileChooser fileChooser, String key) {
		applyInitialDirectory(fileChooser, key);
		var files = fileChooser.showOpenMultipleDialog(owner);
		if (files != null && !files.isEmpty())
			rememberDirectory(files.get(0), key);
		return files;
	}

	/**
	 * show a save-file dialog that remembers its directory under the given key
	 *
	 * @return the chosen file, or null
	 */
	public static File showSaveDialog(Window owner, FileChooser fileChooser, String key) {
		applyInitialDirectory(fileChooser, key);
		return rememberDirectory(fileChooser.showSaveDialog(owner), key);
	}

	/**
	 * point the chooser at the directory remembered under the key, if it is still a directory. A remembered
	 * directory overrides any fallback the caller has already set; if nothing is remembered, the caller's
	 * setting is left in place.
	 */
	public static void applyInitialDirectory(FileChooser fileChooser, String key) {
		var dir = new File(ProgramProperties.get(key, ""));
		if (dir.isDirectory())
			fileChooser.setInitialDirectory(dir);
	}

	/**
	 * store the given file's directory under the key, so the next dialog with that key starts there
	 *
	 * @return the file, unchanged (for chaining)
	 */
	public static File rememberDirectory(File file, String key) {
		if (file != null) {
			var parent = file.getParentFile();
			if (parent != null && parent.isDirectory())
				ProgramProperties.put(key, parent.getPath());
		}
		return file;
	}
}
