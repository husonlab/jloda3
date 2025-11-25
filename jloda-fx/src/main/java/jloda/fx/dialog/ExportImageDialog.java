/*
 * ExportImageDialog.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.dialog;

import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.SaveToPDF;
import jloda.fx.util.SaveToPNG;
import jloda.fx.util.SaveToSVG;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * save an image to a file
 * Daniel Huson, 4.2023
 */
public class ExportImageDialog {
	/**
	 * show a dialog for saving as an images in PNG, SVG or PDF format.
	 * Currently, the format is determined by the suffix that the user provides
	 *
	 * @param file     the document file name, suffix will be replaced, used for the initial file name
	 * @param stage    the main stage, used for positioning the dialog
	 * @param mainNode the main node to be exported
	 */
	public static void show(String file, Stage stage, Node mainNode) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Export Image");

		var previousFormat = ProgramProperties.get("SaveImageFormat", "png");
		var previousDir = new File(ProgramProperties.get("SaveImageDir", ""));
		if (previousDir.isDirectory()) {
			fileChooser.setInitialDirectory(previousDir);
		} else
			fileChooser.setInitialDirectory((new File(file).getParentFile()));
		fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPathOrSuffix(file) + "." + previousFormat);

		var supported = new String[]{"png", "pdf", "svg"};
		var formats = Arrays.stream(supported).map(f -> "*." + f).toArray(String[]::new);
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(String.format("Image Files (%s)", StringUtils.toString(supported, ", ")), formats));

		try {
			var selectedFile = fileChooser.showSaveDialog(stage);
			if (selectedFile != null) {
				var suffix = FileUtils.getFileSuffix(selectedFile.getName()).replaceAll("^.", "");
				var format = Arrays.stream(supported).filter(s -> s.equalsIgnoreCase(suffix)).findAny().orElse(null);
				if (format != null) {
					saveNodeAsImage(mainNode, format, selectedFile);
					ProgramProperties.put("SaveImageFormat", format);
					ProgramProperties.put("SaveImageDir", selectedFile.getParent());
				} else
					throw new IOException("Unknown image format: " + suffix);
			}
		} catch (IOException ex) {
			NotificationManager.showError("Save image failed: " + ex.getMessage());
		}
	}

	public static void saveNodeAsImage(Node node, String formatName, File file) throws IOException {
		var dark = MainWindowManager.isUseDarkTheme();
		try {
			if (dark)
				MainWindowManager.setUseDarkTheme(false);
		switch (formatName.toLowerCase()) {
			case "pdf" -> SaveToPDF.apply(node, file);
			case "svg" -> SaveToSVG.apply(node, file);
			case "png" -> SaveToPNG.apply(node, file);
			default -> throw new IOException("Write failed: format not supported: " + formatName);
		}
		} finally {
			if (dark)
				MainWindowManager.setUseDarkTheme(true);
		}
	}
}