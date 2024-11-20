/*
 *  QRViewUtils.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyProperty;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ClipboardUtils;
import jloda.util.Basic;
import jloda.util.ProgramExecutorService;
import jloda.util.StringUtils;

import java.util.function.Supplier;

/**
 * utilities for setting up a QR view
 * Daniel Huson, 2.2024
 */
public class QRViewUtils {
	private static double mouseX;
	private static double mouseY;

	/**
	 * setup QR code view
	 *
	 * @param anchorPane          container
	 * @param updateProperty      triggers a call to the string supplier
	 * @param stringSupplier      supplies the string
	 * @param qrImageViewProperty this will contain the qr image view
	 * @param show                show or hide the image
	 */
	public static void setup(AnchorPane anchorPane, ReadOnlyProperty<?> updateProperty, Supplier<String> stringSupplier, ObjectProperty<ImageView> qrImageViewProperty, BooleanProperty show) {
		var qrImageView = new ImageView();
		qrImageViewProperty.set(qrImageView);

		qrImageView.setPreserveRatio(true);
		qrImageView.setFitHeight(256);

		var root = new StackPane(qrImageView);
		root.setId("qr");

		var copyMenuItem = new MenuItem("Copy");
		var smallMenuItem = new RadioMenuItem("Small");
		var mediumMenuItem = new RadioMenuItem("Medium");
		var largeMenuItem = new RadioMenuItem("Large");
		var closeMenuItem = new MenuItem("Close");
		closeMenuItem.setOnAction(e -> show.set(false));

		var group = new ToggleGroup();
		group.getToggles().addAll(smallMenuItem, mediumMenuItem, largeMenuItem);
		group.selectedToggleProperty().addListener((v, o, n) -> {
			if (n == smallMenuItem) {
				qrImageView.setScaleX(0.5);
				qrImageView.setScaleY(0.5);
			} else if (n == largeMenuItem) {
				qrImageView.setScaleX(1.5);
				qrImageView.setScaleY(1.5);
			} else {
				qrImageView.setScaleX(1);
				qrImageView.setScaleY(1);
			}
		});
		group.selectToggle(mediumMenuItem);

		var contextMenu = new ContextMenu(copyMenuItem, new SeparatorMenuItem(), smallMenuItem, mediumMenuItem, largeMenuItem, new SeparatorMenuItem(), closeMenuItem);

		qrImageView.setOnContextMenuRequested(e -> {
			var stringValue = stringSupplier.get();
				if (stringValue != null) {
					copyMenuItem.setOnAction(a -> ClipboardUtils.put(stringValue, qrImageView.getImage(), null));
					contextMenu.show(qrImageView, e.getScreenX(), e.getScreenY());
					ProgramExecutorService.submit(3000, () -> Platform.runLater(contextMenu::hide));
				}
		});

		AnchorPane.setBottomAnchor(root, 20.0);
		AnchorPane.setLeftAnchor(root, 20.0);

		qrImageView.setOnMousePressed(e -> {
			mouseX = e.getScreenX();
			mouseY = e.getScreenY();
		});

		qrImageView.setOnMouseDragged(e -> {
			var dx = e.getScreenX() - mouseX;
			var dy = e.getScreenY() - mouseY;

			if (AnchorPane.getLeftAnchor(root) + dx >= 16 && AnchorPane.getLeftAnchor(root) + dx + root.getWidth() <= anchorPane.getWidth() - 16)
				AnchorPane.setLeftAnchor(root, AnchorPane.getLeftAnchor(root) + dx);
			if (AnchorPane.getBottomAnchor(root) - dy >= 16 && AnchorPane.getBottomAnchor(root) - dy <= anchorPane.getHeight() - root.getHeight() - 16)
				AnchorPane.setBottomAnchor(root, AnchorPane.getBottomAnchor(root) - dy);
			mouseX = e.getScreenX();
			mouseY = e.getScreenY();
		});


		show.addListener((v, o, n) -> {
			anchorPane.getChildren().removeAll(BasicFX.findRecursively(anchorPane, a -> a.getId() != null && a.getId().equals("qr")));
			if (n) {
				var string = stringSupplier.get();
				if (string != null)
					Tooltip.install(qrImageView, new Tooltip(StringUtils.abbreviateDotDotDot(string, 100)));
				else Tooltip.install(qrImageView, null);
				qrImageView.setImage(createImage(string, 1024, 1024));
				anchorPane.getChildren().add(1, root);
			}
		});

		InvalidationListener listener = e -> {
			if (!show.get()) {
				qrImageView.setImage(null);
			}
			else if (anchorPane.getChildren().contains(root)) {
				var string = stringSupplier.get();
				if (string != null)
					Tooltip.install(qrImageView, new Tooltip(StringUtils.abbreviateDotDotDot(string, 100)));
				else Tooltip.install(qrImageView, null);
				qrImageView.setImage(createImage(string, 1024, 1024));
			}
		};
		updateProperty.addListener(listener);
		show.addListener(listener);
	}

	public static Image createImage(String text, int width, int height) {
		try {
			var image = new WritableImage(width, height);
			if (text != null && !text.isBlank()) {
				var qrCodeWriter = new QRCodeWriter();
				var bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
				var pixelWriter = image.getPixelWriter();

				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						pixelWriter.setColor(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
					}
				}
			}
			return image;
		} catch (Exception e) {
			Basic.caught(e);
		}
		return null;
	}

}
