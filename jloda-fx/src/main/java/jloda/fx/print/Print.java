/*
 *  Print.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.fx.print;

import javafx.application.Platform;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.stage.*;
import jloda.fx.window.MainWindowManager;

public class Print {
	private static PrinterJob lastJob = null;

	private Print() {
	}

	/**
	 * print the  node
	 *
	 * @param owner stage
	 * @param node  node
	 */
	public static void print(Stage owner, Node node) {
		if (node instanceof TextInputControl textInput) {
			printText(owner, textInput.getText());
		} else {
			printNode(owner, node);
		}
	}


	/**
	 * Print plain text with automatic pagination.
	 */
	public static void printText(Stage owner, String text) {
		if (text == null || text.isEmpty())
			return;

		var job = (lastJob != null ? lastJob : PrinterJob.createPrinterJob());
		if (job == null)
			return;

		if (!job.showPrintDialog(owner))
			return;

		var dark = MainWindowManager.isUseDarkTheme();
		MainWindowManager.setUseDarkTheme(false);
		try {
			var printer = job.getPrinter();
			var layout = printer.getDefaultPageLayout();

			var printableWidth = layout.getPrintableWidth();
			var printableHeight = layout.getPrintableHeight();

			var page = new VBox();
			page.setPrefWidth(printableWidth);

			Font font = Font.font("Monospaced", 11);

			Text measuringText = new Text("X");
			measuringText.setFont(font);
			var lineHeight = measuringText.getLayoutBounds().getHeight();

			var maxLinesPerPage = (int) (printableHeight / lineHeight) - 1;
			String[] lines = text.split("\n");

			var lineIndex = 0;
			while (lineIndex < lines.length) {
				page.getChildren().clear();

				for (var i = 0; i < maxLinesPerPage && lineIndex < lines.length; i++) {
					Text line = new Text(lines[lineIndex++]);
					line.setFont(font);
					page.getChildren().add(line);
				}

				var success = job.printPage(page);
				if (!success)
					break;
			}

			job.endJob();
		} finally {
			MainWindowManager.setUseDarkTheme(dark);
			restoreMenuBar(owner);
		}
	}

	public static void printNode(Stage owner, Node node) {
		if (node == null) return;
		var dark = MainWindowManager.isUseDarkTheme();
		MainWindowManager.setUseDarkTheme(false);
		WritableImage snapshot;
		try {
			snapshot = CroppedSnapshot.snapshotContentBBox(node, Color.WHITE, 300, 200);
			if (snapshot == null) return;
			snapshot = ImageCropper.cropWhiteMargins(snapshot, 20, 0.1, 0.1);
		} finally {
			MainWindowManager.setUseDarkTheme(dark);
		}
		printSnapshot(owner, snapshot, 4);

	}

	public static void printSnapshot(Stage owner, WritableImage snapshot, int requestedOversample) {
		try {
			if (snapshot == null) return;

			var job = (lastJob != null ? lastJob : PrinterJob.createPrinterJob());
			if (job == null) return;
			if (!job.showPrintDialog(owner)) return;

			PageLayout pl = job.getJobSettings().getPageLayout();
			double pw = pl.getPrintableWidth();
			double ph = pl.getPrintableHeight();

			// Conservative max dimension for internal raster/texture.
			final int MAX_DIM = 4096;

			// Account for Retina / output scaling
			double osx = outputScaleX(owner);
			double osy = outputScaleY(owner);

			// Cap oversample so (pw*O*osx) and (ph*O*osy) stay <= MAX_DIM
			int maxO1 = (int) Math.floor(MAX_DIM / (pw * osx));
			int maxO2 = (int) Math.floor(MAX_DIM / (ph * osy));
			int oversample = Math.max(1, Math.min(requestedOversample, Math.max(1, Math.min(maxO1, maxO2))));

			// Fit snapshot to printable area (preserve aspect ratio)
			double fit = Math.min(pw / snapshot.getWidth(), ph / snapshot.getHeight());
			double drawW = snapshot.getWidth() * fit;
			double drawH = snapshot.getHeight() * fit;
			double x = (pw - drawW) / 2.0;
			double y = (ph - drawH) / 2.0;

			Canvas canvas = new Canvas(pw * oversample, ph * oversample);
			GraphicsContext gc = canvas.getGraphicsContext2D();

			gc.setFill(Color.WHITE);
			gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
			gc.setImageSmoothing(true);

			gc.drawImage(snapshot,
					x * oversample, y * oversample,
					drawW * oversample, drawH * oversample);

			Group printable = new Group(canvas);
			printable.getTransforms().add(new Scale(1.0 / oversample, 1.0 / oversample, 0, 0));

			if (false) {
				System.err.println("oversample requested=" + requestedOversample
								   + " used=" + oversample
								   + " canvas=" + (int) canvas.getWidth() + "x" + (int) canvas.getHeight()
								   + " outputScale=" + osx + "x" + osy
								   + " effective=" + (int) Math.round(canvas.getWidth() * osx) + "x" + (int) Math.round(canvas.getHeight() * osy));
			}

			boolean ok = job.printPage(pl, printable);
			if (ok) job.endJob();

		} finally {
			Print.restoreMenuBar(owner);
		}
	}

	private static double outputScaleX(Stage owner) {
		if (owner != null) {
			try {
				return (double) Window.class.getMethod("getOutputScaleX").invoke(owner);
			} catch (Exception ignored) {
			}
		}
		return Screen.getPrimary().getOutputScaleX(); // JavaFX 21+
	}

	private static double outputScaleY(Stage owner) {
		if (owner != null) {
			try {
				return (double) Window.class.getMethod("getOutputScaleY").invoke(owner);
			} catch (Exception ignored) {
			}
		}
		return Screen.getPrimary().getOutputScaleY(); // JavaFX 21+
	}

	/**
	 * Show the page layout dialog for the current or a new printer job.
	 */
	public static void showPageLayout(Stage owner) {
		try {
			var job = PrinterJob.createPrinterJob();
			if (job == null)
				return;
			job.showPageSetupDialog(owner);
			lastJob = job;
		} finally {
			restoreMenuBar(owner);
		}
	}

	public static void restoreMenuBar(Stage owner) {
		if (owner == null) return;

		// need to work hard to ensure that the menubar reappears
		Platform.runLater(() -> attempt(owner, false));
		Platform.runLater(() -> attempt(owner, true));
		Platform.runLater(() -> attempt(owner, true));
	}

	private static void attempt(Stage owner, boolean withBounce) {
		if (!owner.isShowing()) return;

		owner.toFront();
		owner.requestFocus();

		boolean wasAOT = owner.isAlwaysOnTop();
		try {
			owner.setAlwaysOnTop(true);
			owner.toFront();
			owner.requestFocus();
		} finally {
			owner.setAlwaysOnTop(wasAOT);
		}

		if (!withBounce) return;

		Stage bounce = new Stage(StageStyle.TRANSPARENT);
		bounce.initOwner(owner);
		bounce.initModality(Modality.NONE);
		bounce.setAlwaysOnTop(true);
		bounce.setWidth(1);
		bounce.setHeight(1);
		bounce.setX(owner.getX()); // keep it "near" the app
		bounce.setY(owner.getY());

		Platform.runLater(() -> {
			try {
				bounce.show();
				bounce.toFront();
				bounce.requestFocus();
			} finally {
				// hide quickly, then bring owner back
				Platform.runLater(() -> {
					bounce.hide();
					owner.toFront();
					owner.requestFocus();
				});
			}
		});
	}
}
