/*
 * SaveToPNG.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import jloda.fx.thirdparty.PngEncoderFX;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * save pane to PNG
 * This is quite incomplete, for example, doesn't draw effects or borders
 * Daniel Huson, 6.2023
 */
public class SaveToPNG {
	/**
	 * draws a given pane to a file in PNG format
	 *
	 * @param root the pane
	 * @param file the file
	 */
	public static void apply(Node root, File file) throws IOException {
		var bounds = root.getLayoutBounds();
		var image = createImage(root, 2 * bounds.getWidth(), 2 * bounds.getHeight(), true);
		apply(image, file);
	}

	/**
	 * draws a given image to a file in PNG format
	 *
	 * @param image the image
	 * @param file  the file
	 * @throws IOException
	 */
	public static void apply(Image image, File file) throws IOException {
		if (file.exists())
			Files.delete(file.toPath());
		var bytes = new PngEncoderFX(image, true, PngEncoderFX.FILTER_NONE, 9).pngEncode();
		try (var outs = new FileOutputStream(file)) {
			outs.write(bytes);
		}
	}

	/**
	 * create an image for a given region
	 *
	 * @param node         region
	 * @param targetWidth  the target width of the image
	 * @param targetHeight the target height of the image
	 * @return the image
	 */
	public static Image createImage(Node node, double targetWidth, double targetHeight, boolean preserveAspectRatio) {
		var bounds = node.getBoundsInLocal();
		var parameters = new SnapshotParameters();
		parameters.setFill(Color.TRANSPARENT);
		var xFactor = (targetWidth > 0 && bounds.getWidth() > 0 ? targetWidth / bounds.getWidth() : 1.0);
		var yFactor = (targetHeight > 0 && bounds.getHeight() > 0 ? targetHeight / bounds.getHeight() : 1.0);
		if (preserveAspectRatio)
			xFactor = yFactor = Math.min(xFactor, yFactor);
		parameters.setTransform(new Scale(xFactor, yFactor));
		parameters.setViewport(new Rectangle2D(0, 0, xFactor * bounds.getWidth(), yFactor * bounds.getHeight()));
		return node.snapshot(parameters, null);
	}
}

