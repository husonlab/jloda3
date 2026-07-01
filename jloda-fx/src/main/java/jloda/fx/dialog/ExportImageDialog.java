/*
 * ExportImageDialog.java Copyright (C) 2026 Daniel H. Huson
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
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.print.SaveToPDF;
import jloda.fx.print.SaveToPNG;
import jloda.fx.print.SaveToSVG;
import jloda.fx.util.ProgramProperties;
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
	public static void show(String fileName, Stage stage, Node root, boolean autoCrop, ScrollPane scrollPane) {
		ScrollPane.ScrollBarPolicy hbar = null, vbar = null;
		if (scrollPane != null) {
			hbar = scrollPane.getHbarPolicy();
			vbar = scrollPane.getVbarPolicy();
			scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
			scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		}
		var useDarkTheme = MainWindowManager.isUseDarkTheme();
		if (useDarkTheme)
			MainWindowManager.setUseDarkTheme(false);

		try {
			show(fileName, stage, root);
		} finally {
			if (scrollPane != null) {
				scrollPane.setHbarPolicy(hbar);
				scrollPane.setVbarPolicy(vbar);
			}
			if (useDarkTheme)
				MainWindowManager.setUseDarkTheme(true);
		}
	}

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

	/**
	 * Export pixel density relative to on-screen 1:1. 2–4 is typical; higher = sharper + larger.
	 */
	private static final double EXPORT_PIXEL_SCALE = 3.0;

	/**
	 * Snapshots {@code root} at high resolution, independent of its current zoom/pan, and hands the
	 * resulting image to the standard export dialog. If {@code scrollPane} is non-null, its scrollbars
	 * are hidden for the duration of the snapshot and restored afterwards.
	 * ignores dark mode
	 *
	 * @param autoCrop automatically crop the image
	 */
	public static void show_ALT(String fileName, Stage stage, Node root, boolean autoCrop, ScrollPane scrollPane) {
		ScrollPane.ScrollBarPolicy hbar = null, vbar = null;
		if (scrollPane != null) {
			hbar = scrollPane.getHbarPolicy();
			vbar = scrollPane.getVbarPolicy();
			scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
			scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		}
		var useDarkTheme = MainWindowManager.isUseDarkTheme();
		if (useDarkTheme)
			MainWindowManager.setUseDarkTheme(false);

		Image image;
		try {
			image = createHighResSnapshot(root, EXPORT_PIXEL_SCALE);
			if (autoCrop)
				image = jloda.fx.print.ImageCropper.cropWhiteMargins(image, 20, 0.02, 0.1);
		} finally {
			if (scrollPane != null) {
				scrollPane.setHbarPolicy(hbar);
				scrollPane.setVbarPolicy(vbar);
			}
			if (useDarkTheme)
				MainWindowManager.setUseDarkTheme(true);
		}

		show(fileName, stage, new ImageView(image));
	}

	/**
	 * Renders {@code root} to an image at {@code pixelScale} density without altering the node: the
	 * snapshot transform cancels the node's live transform (zoom/pan) and re-applies a clean scale, so
	 * the export is 1:1 regardless of on-screen zoom and the scene graph is left untouched.
	 */
	private static Image createHighResSnapshot(Node root, double pixelScale) {
		var bounds = root.getBoundsInLocal();

		Transform inverse;
		try {
			inverse = root.getLocalToParentTransform().createInverse();
		} catch (NonInvertibleTransformException e) {
			inverse = new Affine(); // degenerate transform: fall back to identity
		}

		var transform = new Affine();
		transform.appendScale(pixelScale, pixelScale);
		transform.appendTranslation(-bounds.getMinX(), -bounds.getMinY());
		transform.append(inverse);

		var params = new SnapshotParameters();
		params.setFill(Color.WHITE);
		params.setTransform(transform);

		var image = new WritableImage(
				(int) Math.ceil(bounds.getWidth() * pixelScale),
				(int) Math.ceil(bounds.getHeight() * pixelScale));

		return root.snapshot(params, image);
	}
}