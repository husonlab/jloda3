/*
 * SaveToSVG.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Chart;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.thirdparty.PngEncoderFX;
import jloda.fx.window.MainWindowManager;
import jloda.util.Basic;
import jloda.util.FileUtils;
import jloda.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;


/**
 * save a root node and all descendants to an SVG image.
 * This is very incomplete. It doesn't reproduce CSS styling and doesn't reproduce effects and non-color paints
 * Daniel Huson, 6.2023
 */
public class SaveToSVG {
	/**
	 * draws given root node to a file in SVG format
	 *
	 * @param root the root node to be saved
	 * @param file the file
	 * @throws IOException failed
	 */
	public static void apply(Node root, File file) throws IOException {
		apply(root, root.getBoundsInLocal(), file);
	}

	/**
	 * draws given root node to a file in SVG format
	 *
	 * @param root   the root node to be saved
	 * @param file   the file
	 * @param bounds the bounds to use
	 * @throws IOException failed
	 */
	public static void apply(Node root, Bounds bounds, File file) throws IOException {
		if (file.exists())
			Files.delete(file.toPath());

		var buf = new StringBuilder();

		var currentDateTime = LocalDateTime.now();
		var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		var formattedDateTime = currentDateTime.format(formatter);
		buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>%n<!-- Creator: %s -->%n<!-- Created: %s -->%n<!-- Software: JLODA2 https://github.com/husonlab/jloda2 -->%n".formatted(System.getProperty("user.name"), formattedDateTime));

		buf.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.1f\" height=\"%.1f\" viewBox=\"%.1f %.1f %.1f %.1f\">\n"
				.formatted(bounds.getWidth(), bounds.getHeight(), bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));
			/*
			buf.append("""
					<defs>
						<clipPath id="global-clip">
					    	<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" />
					 	</clipPath>
					</defs>%n""".formatted(svgMinX, svgMinY, svgWidth, svgHeight));

				then attach this to all items that should be clipped:
				<use xlink:href="#global-clip" />
			 */

		if (MainWindowManager.isUseDarkTheme()) {
			buf.append(createRect(-5, -5, bounds.getWidth() + 10, bounds.getHeight() + 10, "fill=\"%s\"".formatted(asSvgColor(Color.web("rgb(60, 63, 65)")))));
		}

		for (var n : BasicFX.getAllRecursively(root, n -> true)) {
			// System.err.println("n: " + n.getClass().getSimpleName());
			if (SaveToPDF.isNodeVisible(n)) {
				buf.append(getSVG(root, n));
			}
		}
		buf.append("</svg>\n");
		try (var writer = FileUtils.getOutputWriterPossiblyZIPorGZIP(file.getPath())) {
			writer.write(buf.toString());
		}
	}

	/**
	 * constructs the SVG description for a node that represents a shape or image
	 *
	 * @param root the root node containing the node, used for determining the apparent size and angle
	 * @param node the node to report
	 * @return the SVG string or empty string
	 */
	public static String getSVG(Node root, Node node) {
		var scaleFactor = SaveToPDF.computeScaleFactor(root, node);

		var formatting = createFormattingAndTransformString(root, node, scaleFactor);

		var buf = new StringBuilder();
		try {
			if (node instanceof Pane pane) { // this might contain a background color
				if (pane.getBackground() != null && pane.getBackground().getFills().size() == 1) {
					var fill = pane.getBackground().getFills().get(0);
					if (fill.getFill() instanceof Color color) {
						var width = computeFinalWidth(root, pane, pane.getWidth());
						var height = computeFinalHeight(root, pane, pane.getHeight());
						var location = root.screenToLocal(pane.localToScreen(0, 0));
						var format = " fill=\"%s\"".formatted(asSvgColor(color));
						{
							double screenAngle = SaveToPDF.getAngleOnScreen(node);
							if ((screenAngle % 360.0) != 0) {
								format += (" transform=\"rotate(%.1f %.2f %.2f)\"".formatted(screenAngle, location.getX(), location.getY()));
							}
						}
						buf.append(createRect(location.getX(), location.getY(), width, height, format));
					}
				}
			} else if (node instanceof Line line) {
				var x1 = (root.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getX());
				var y1 = (root.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getY());
				var x2 = (root.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getX());
				var y2 = (root.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getY());
				buf.append(createLine(x1, y1, x2, y2, formatting));
			} else if (node instanceof Rectangle rectangle) {
				var width = computeFinalWidth(root, rectangle, rectangle.getWidth());
				var height = computeFinalHeight(root, rectangle, rectangle.getHeight());
				var screenBounds = rectangle.localToScreen(rectangle.getBoundsInLocal());
				var location = root.screenToLocal(new Point2D(screenBounds.getMinX(), screenBounds.getMinY()));
				buf.append(createRect(location.getX(), location.getY(), width, height, formatting));
			} else if (node instanceof Circle circle) {
				var bounds = root.sceneToLocal(circle.localToScene(circle.getBoundsInLocal()));
				var r = (0.5 * bounds.getHeight());
				var x = (bounds.getCenterX());
				var y = (bounds.getCenterY());
				buf.append(createCircle(x, y, r, formatting));
			} else if (node instanceof Ellipse ellipse) {
				var bounds = root.sceneToLocal(ellipse.localToScene(ellipse.getBoundsInLocal()));
				var rx = (ellipse.getRadiusX());
				var ry = (ellipse.getRadiusY());
				var x = (bounds.getCenterX());
				var y = (bounds.getCenterY());
				buf.append(createEllipse(x, y, rx, ry, formatting));
			} else if (node instanceof QuadCurve curve) {
				var sX = (root.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getX());
				var sY = (root.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getY());
				var cX = (root.sceneToLocal(curve.localToScene(curve.getControlX(), curve.getControlY())).getX());
				var cY = (root.sceneToLocal(curve.localToScene(curve.getControlX(), curve.getControlY())).getY());
				var tX = (root.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getX());
				var tY = (root.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getY());
				buf.append(createQuadCurve(sX, sY, cX, cY, tX, tY, formatting));
			} else if (node instanceof CubicCurve curve) {
				var sX = (root.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getX());
				var sY = (root.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getY());
				var c1X = (root.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getX());
				var c1Y = (root.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getY());
				var c2X = (root.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getX());
				var c2Y = (root.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getY());
				var tX = (root.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getX());
				var tY = (root.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getY());
				buf.append(createCubicCurve(sX, sY, c1X, c1Y, c2X, c2Y, tX, tY, formatting));
			} else if (node instanceof Path path) {
				if (!SaveToPDF.containedInText(path))
					buf.append(createPath(path, root, formatting));
			} else if (node instanceof Polygon polygon) {
				var points = new ArrayList<Point2D>();
				for (var i = 0; i < polygon.getPoints().size(); i += 2) {
					var x = (root.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getX());
					var y = (root.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getY());
					points.add(new Point2D(x, y));
				}
				buf.append(createPolygon(points, formatting));
			} else if (node instanceof Polyline polyline) {
				var points = new ArrayList<Point2D>();
				for (var i = 0; i < polyline.getPoints().size(); i += 2) {
					var x = (root.sceneToLocal(polyline.localToScene(polyline.getPoints().get(i), polyline.getPoints().get(i + 1))).getX());
					var y = (root.sceneToLocal(polyline.localToScene(polyline.getPoints().get(i), polyline.getPoints().get(i + 1))).getY());
					points.add(new Point2D(x, y));
				}
				buf.append(createPolyline(points, formatting));
			} else if (node instanceof Text text) {
				if (!text.getText().isBlank()) {
					double screenAngle = SaveToPDF.getAngleOnScreen(text);
					var localBounds = text.getBoundsInLocal();
					var origX = localBounds.getMinX();
					var origY = localBounds.getMinY() + 0.87f * localBounds.getHeight();
					var rotateAnchorX = root.sceneToLocal(text.localToScene(origX, origY)).getX();
					var rotateAnchorY = root.sceneToLocal(text.localToScene(origX, origY)).getY();
					if (SaveToPDF.isMirrored(text)) // todo: this is untested:
						screenAngle = 360 - screenAngle;
					var fontHeight = computeFinalHeight(root, text, text.getFont().getSize());
					buf.append(createText((rotateAnchorX), (rotateAnchorY), screenAngle, text.getText(), text.getFont(), fontHeight, text.getFill()));
				}
			} else if (node instanceof ImageView imageView) {
				var bounds = root.sceneToLocal(imageView.localToScene(imageView.getBoundsInLocal()));
				var x = (bounds.getMinX());
				var width = (bounds.getWidth());
				var y = (bounds.getMinY());
				var height = (bounds.getHeight());
				buf.append(createImage(x, y, width, height, imageView.getImage()));
			} else if (node instanceof Shape3D || node instanceof Canvas || node instanceof Chart || node instanceof Arc) {
				var layoutBounds = node.getLayoutBounds();
				var writableImage = new WritableImage((int) layoutBounds.getWidth() * 3, (int) layoutBounds.getHeight() * 3);
				var parameters = new SnapshotParameters();
				parameters.setFill(Color.TRANSPARENT);
				parameters.setTransform(javafx.scene.transform.Transform.scale(3, 3));
				var snapShot = node.snapshot(parameters, writableImage);
				var bounds = root.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
				var x = (bounds.getMinX());
				var width = (bounds.getWidth());
				var y = (bounds.getMinY());
				var height = (bounds.getHeight());
				buf.append(createImage(x, y, width, height, snapShot));
			}
		} catch (Exception ex) {
			Basic.caught(ex);
		}
		return buf.toString();
	}


	public static String createLine(double x1, double y1, double x2, double y2, String formatting) {
		return "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" %s/>%n".formatted(x1, y1, x2, y2, formatting);
	}

	public static String createRect(double x, double y, double width, double height, String formatting) {
		return ("<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" %s/>%n".formatted(x, y, width, height, formatting));
	}

	public static String createCircle(double x, double y, double radius, String formatting) {
		return "<circle cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\" %s/>%n".formatted(x, y, radius, formatting);
	}

	public static String createEllipse(double x, double y, double rx, double ry, String formatting) {
		return ("<ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\" %s/>%n".formatted(x, y, rx, ry, formatting));
	}

	public static String createQuadCurve(Double sX, Double sY, Double cX, Double cY, Double tX, Double tY, String formatting) {
		return ("<path d=\"M%.2f,%.2f Q%.2f,%.2f %.2f,%.2f\" %s/>%n".formatted(sX, sY, cX, cY, tX, tY, formatting));
	}

	public static String createCubicCurve(Double sX, Double sY, Double c1X, Double c1Y, Double c2X, Double c2Y, Double tX, Double tY, String formatting) {
		return ("<path d=\"M%.2f,%.2f C%.2f,%.2f %.2f,%.2f %.2f,%.2f\" %s/>%n".formatted(sX, sY, c1X, c1Y, c2X, c2Y, tX, tY, formatting));
	}

	private static String createPath(Path path, Node pane, String formatting) {
		var local = new Point2D(0, 0);
		var buf = new StringBuilder();
		buf.append("<path d=\"");
		try {
			for (var element : path.getElements()) {
				//System.err.println("Element: " + element);
				if (element instanceof MoveTo moveTo) {
					local = new Point2D(moveTo.getX(), moveTo.getY());
					var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
					buf.append(" M%.2f,%.2f".formatted((t.getX()), (t.getY())));
				} else if (element instanceof LineTo lineTo) {
					local = new Point2D(lineTo.getX(), lineTo.getY());
					var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
					buf.append(" L%.2f,%.2f".formatted((t.getX()), (t.getY())));
				} else if (element instanceof HLineTo lineTo) {
					local = new Point2D(lineTo.getX(), local.getY());
					var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
					buf.append(" L%.2f,%.2f".formatted((t.getX()), (t.getY())));
				} else if (element instanceof VLineTo lineTo) {
					local = new Point2D(local.getX(), lineTo.getY());
					var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
					buf.append(" L%.2f,%.2f".formatted((t.getX()), (t.getY())));
				} else if (element instanceof ArcTo arcTo) {
					local = new Point2D(arcTo.getX(), arcTo.getY());
					var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
					double radiusX = arcTo.getRadiusX();
					double radiusY = arcTo.getRadiusY();
					double xAxisRotation = arcTo.getXAxisRotation();
					boolean largeArcFlag = arcTo.isLargeArcFlag();
					boolean sweepFlag = arcTo.isSweepFlag();
					buf.append(" A%.2f,%.2f %.2f %d,%d %.2f,%.2f".formatted(radiusX, radiusY, xAxisRotation, (largeArcFlag ? 1 : 0), (sweepFlag ? 1 : 0), (t.getX()), (t.getY())));
				} else if (element instanceof QuadCurveTo curveTo) {
					var c = pane.sceneToLocal(path.localToScene(curveTo.getControlX(), curveTo.getControlY()));
					var t = pane.sceneToLocal(path.localToScene(curveTo.getX(), curveTo.getY()));
					buf.append(" Q%.2f,%.2f %.2f,%.2f".formatted((c.getX()), (c.getY()), (t.getX()), (t.getY())));
				} else if (element instanceof CubicCurveTo curveTo) {
					var c1 = pane.sceneToLocal(path.localToScene(curveTo.getControlX1(), curveTo.getControlY1()));
					var c2 = pane.sceneToLocal(path.localToScene(curveTo.getControlX2(), curveTo.getControlY2()));
					var t = pane.sceneToLocal(path.localToScene(curveTo.getX(), curveTo.getY()));
					buf.append(" C%.2f,%.2f %.2f,%.2f %.2f,%.2f".formatted((c1.getX()), (c1.getY()), (c2.getX()), (c2.getY()), (t.getX()), (t.getY())));
				}
			}
		} finally {
			buf.append("\" ").append(formatting).append("/>\n");
		}
		return buf.toString();
	}

	public static String createPolygon(ArrayList<Point2D> points, String formatting) {
		var buf = new StringBuilder();
		buf.append("<polygon points=\"");
		for (var point : points) {
			buf.append(" %.2f,%.2f".formatted(point.getX(), point.getY()));
		}
		buf.append("\" ").append(formatting).append("/>\n");
		return buf.toString();
	}

	public static String createPolyline(ArrayList<Point2D> points, String formatting) {
		var buf = new StringBuilder();
		if (!points.isEmpty()) {
			try {
				buf.append("<path d=\"");
				buf.append(" M%.2f,%.2f".formatted(points.get(0).getX(), points.get(0).getY()));

				for (var point : points) {
					buf.append(" L%.2f,%.2f".formatted(point.getX(), point.getY()));
				}
			} finally {
				buf.append("\" ").append(formatting).append("/>\n");
			}
		}
		return buf.toString();
	}

	public static String createText(Double x, Double y, double angle, String text, Font font, Double fontSize, Paint textFill) {
		var buf = new StringBuilder();
		buf.append("<text x=\"%.2f\" y=\"%.2f\"".formatted(x, y));
		buf.append(" font-family=\"%s\"".formatted(getSVGFontName(font.getFamily())));
		buf.append(" font-size=\"%.1f\"".formatted(fontSize));
		if (font.getName().contains(" Italic"))
			buf.append(" font-style=\"italic\"");
		if (font.getName().contains(" Bold"))
			buf.append(" font-weight=\"bold\"");
		if (textFill instanceof Color color && color != Color.TRANSPARENT)
			buf.append(" fill=\"%s\"".formatted(asSvgColor(color)));
		if ((angle % 360.0) != 0) {
			buf.append(" transform=\"rotate(%.1f %.2f %.2f)\"".formatted(angle, x, y));
		}
		buf.append("><![CDATA[");
		buf.append(text);
		buf.append("]]></text>\n");
		return buf.toString();
	}

	public static String createImage(double x, double y, double width, double height, Image image) {
		var buf = new StringBuilder();
		var encoder = new PngEncoderFX(image, true);
		var base64Data = Base64.getEncoder().encodeToString(encoder.pngEncode(true));
		buf.append("<image xlink:href=\"data:image/png;base64,").append(base64Data).append("\"");
		buf.append(" x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"/>\n".formatted(x, y, width, height));
		return buf.toString();
	}

	public static Object getSVGFontName(String fontFamily) {
		fontFamily = fontFamily.toLowerCase();
		if (fontFamily.startsWith("times"))
			return "Times New Roman";
		else if (fontFamily.startsWith("arial"))
			return "Arial";
		else if (fontFamily.startsWith("courier") || fontFamily.startsWith("monospaced"))
			return "Courier";
		else // if(fontFamily.startsWith("arial") || fontFamily.startsWith("helvetica") || fontFamily.startsWith("system"))
			return "Helvetica";
	}

	public static String asSvgColor(Color color) {
		var r = (int) (color.getRed() * 255);
		var g = (int) (color.getGreen() * 255);
		var b = (int) (color.getBlue() * 255);
		var alpha = color.getOpacity();

		// Convert RGB values to hexadecimal notation
		var hexColor = String.format("#%02X%02X%02X", r, g, b);

		// Append alpha value if it's different from fully opaque (1.0)
		if (alpha < 1.0) {
			String alphaHex = String.format("%02X", (int) (alpha * 255));
			hexColor += alphaHex;
		}
		return hexColor;
	}

	public static String createFormattingAndTransformString(Node root, Node node, double scaleFactor) {
		var buf = new StringBuilder();
		if (node instanceof Shape shape) {
			var stroke = shape.getStroke();
			if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
				buf.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
			else
				buf.append(" stroke=\"none\"");
			var fill = shape.getFill();
			if (fill instanceof Color color && color != Color.TRANSPARENT)
				buf.append(" fill=\"%s\"".formatted(asSvgColor(color)));
			else
				buf.append(" fill=\"none\"");
			buf.append(" stroke-width=\"%.2f\"".formatted(scaleFactor * shape.getStrokeWidth()));
			var strokeDashArray = shape.getStrokeDashArray().stream().map(v -> scaleFactor * v).toList();
			if (!strokeDashArray.isEmpty()) {
				buf.append(" stroke-dasharray=\"").append(StringUtils.toString(strokeDashArray, ",")).append("\"");
			}
		}

		if (node instanceof Circle || node instanceof Ellipse || node instanceof Rectangle || node instanceof Pane || node instanceof ImageView || node instanceof Chart) {
			double screenAngle = SaveToPDF.getAngleOnScreen(node);
			var localBounds = node.getBoundsInLocal();
			if ((screenAngle % 360.0) != 0) {
				var origX = localBounds.getMinX();
				var origY = localBounds.getMaxY();
				var rotateAnchorX = root.sceneToLocal(node.localToScene(origX, origY)).getX();
				var rotateAnchorY = root.sceneToLocal(node.localToScene(origX, origY)).getY();
				buf.append(" transform=\"rotate(%.1f %.2f %.2f)\"".formatted(screenAngle, rotateAnchorX, rotateAnchorY));
			}
		}
		return buf.toString();
	}

	public static double computeFinalWidth(Node root, Node pane, double originalWidth) {
		var widthInScreenCoordinates = pane.localToScreen(new Point2D(originalWidth, 0)).subtract(pane.localToScreen(new Point2D(0, 0))).magnitude();
		return root.sceneToLocal(new Point2D(widthInScreenCoordinates, 0)).subtract(root.sceneToLocal(new Point2D(0, 0))).magnitude();
	}

	public static double computeFinalHeight(Node root, Node pane, double originalHeight) {
		var heightInScreenCoordinates = pane.localToScreen(new Point2D(0, originalHeight)).subtract(pane.localToScreen(new Point2D(0, 0))).magnitude();
		return root.sceneToLocal(new Point2D(0, heightInScreenCoordinates)).subtract(root.sceneToLocal(new Point2D(0, 0))).magnitude();
	}
}
