/*
 * SaveToPDF.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.fx.thirdparty.PngEncoderFX;
import jloda.fx.window.MainWindowManager;
import jloda.util.Basic;
import jloda.util.StringUtils;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.function.Function;

import static jloda.fx.util.SaveToSVG.computeFinalHeight;
import static jloda.fx.util.SaveToSVG.computeFinalWidth;


/**
 * save root node to PDF, trying to draw all descendants as objects
 * This is quite incomplete, for example, doesn't draw effects or borders
 * Daniel Huson, 6.2023
 */
public class SaveToPDF {
	static {
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	/**
	 * draws given pane to a file in PDF format
	 *
	 * @param root the root node
	 * @param file the file
	 * @throws IOException failed
	 */
	public static void apply(Node root, File file) throws IOException {
		apply(root, root.getBoundsInLocal(), file);
	}

	/**
	 * draws given pane to a file in PDF format
	 *
	 * @param root       the root node
	 * @param file       the file
	 * @param rootBounds the bounds to use
	 * @throws IOException failed
	 *                                                             todo: implement rotation of elements
	 */
	public static void apply(Node root, Bounds rootBounds, File file) throws IOException {
		if (file.exists())
			Files.delete(file.toPath());

		var document = new PDDocument();
		//var page = new PDPage(computeBoundingBox(root));

		var boundingBox = new PDRectangle((float) rootBounds.getMinX(), (float) rootBounds.getMinY(), (float) rootBounds.getWidth(), (float) rootBounds.getHeight());
		var page = new PDPage(boundingBox);
		document.addPage(page);

		{
			// Set the metadata
			var documentInformation = document.getDocumentInformation();
			documentInformation.setAuthor(System.getProperty("user.name"));
			documentInformation.setTitle(file.getName());
			documentInformation.setSubject("PDF image of " + file.getName());
			documentInformation.setCreator(ProgramProperties.getProgramName());
			var calendar = new GregorianCalendar(TimeZone.getDefault());
			documentInformation.setCreationDate(calendar);
			documentInformation.setModificationDate(calendar);
		}

		var contents = new PDPageContentStream(document, page);

		Function<Double, Float> py = s -> boundingBox.getUpperRightY() - s.floatValue();

		if (MainWindowManager.isUseDarkTheme()) {
			contents.setNonStrokingColor(pdfNonStrokingColor(Color.web("rgb(60, 63, 65)")));
			contents.addRect(boundingBox.getLowerLeftX(), boundingBox.getLowerLeftY(), boundingBox.getWidth(), boundingBox.getHeight());
			contents.fill();
		}

		var skippedClasses = new HashSet<String>();

		for (var node : BasicFX.getAllRecursively(root, n -> true)) {
			// System.err.println("n: " + n.getClass().getSimpleName());
			if (isNodeVisible(node)) {
				contents.saveGraphicsState();
				try {
					var scaleFactor = (float) computeScaleFactor(root, node);

					if (node instanceof Shape shape) {
						var strokeWidth = (float) (scaleFactor * shape.getStrokeWidth());
						var strokeDashArray = strokeDashArray(scaleFactor, shape);
						contents.setLineWidth(strokeWidth);
						contents.setLineDashPattern(strokeDashArray, 0);
					}

					if (node instanceof Circle || node instanceof Ellipse || node instanceof Rectangle || node instanceof Pane || node instanceof ImageView || node instanceof Chart) {
						var screenAngle = SaveToPDF.getAngleOnScreen(node);
						if ((screenAngle % 360.0) != 0) {
							var localBounds = node.getBoundsInLocal();
							var origX = localBounds.getMinX();
							var origY = localBounds.getMaxY();
							var rotateAnchorX = (float) root.sceneToLocal(node.localToScene(origX, origY)).getX();
							var rotateAnchorY = py.apply(root.sceneToLocal(node.localToScene(origX, origY)).getY());
							contents.transform(Matrix.getTranslateInstance(rotateAnchorX, rotateAnchorY));
							contents.transform(Matrix.getRotateInstance(Math.toRadians(-screenAngle), 0, 0));
							contents.transform(Matrix.getTranslateInstance(-rotateAnchorX, -rotateAnchorY));
						}
					}

					try {
						if (node instanceof Pane pane) { // this might contain a background color
							if (pane.getBackground() != null && pane.getBackground().getFills().size() == 1) {
								var fill = pane.getBackground().getFills().get(0);
								if (fill.getFill() instanceof Color color && !color.equals(Color.WHITE) && color.getOpacity() > 0) {
									var width = computeFinalWidth(root, pane, pane.getWidth());
									var height = computeFinalHeight(root, pane, pane.getHeight());
									var location = root.screenToLocal(pane.localToScreen(0, pane.getHeight()));
									contents.addRect((float) (location.getX()), py.apply(location.getY()), (float) (width), (float) (height));
									doFillStroke(contents, null, color);
								}
							}
						} else if (node instanceof Line line) {
							var x1 = (float) (root.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getX());
							var y1 = py.apply(root.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getY());
							var x2 = (float) (root.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getX());
							var y2 = py.apply(root.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getY());
							contents.moveTo(x1, y1);
							contents.lineTo(x2, y2);
							doFillStroke(contents, line.getStroke(), line.getFill());
						} else if (node instanceof Rectangle rectangle) {
							var width = computeFinalWidth(root, rectangle, rectangle.getWidth());
							var height = computeFinalHeight(root, rectangle, rectangle.getHeight());
							var screenBounds = rectangle.localToScreen(rectangle.getBoundsInLocal());
							var location = root.screenToLocal(new Point2D(screenBounds.getMinX(), screenBounds.getMinY()));

							contents.addRect((float) location.getX(), py.apply(location.getY() + height), (float) width, (float) height);
							doFillStroke(contents, rectangle.getStroke(), rectangle.getFill());
						} else if (node instanceof Ellipse ellipse) {
							// todo: this is broken
							var bounds = root.sceneToLocal(ellipse.localToScene(ellipse.getBoundsInLocal()));
							var rx = (float) (0.5 * bounds.getHeight());
							var ry = py.apply(0.5 * bounds.getWidth());
							var x = (float) (bounds.getCenterX() - rx);
							var y = py.apply(bounds.getCenterY());
							addEllipse(contents, x, y, rx, ry);
							doFillStroke(contents, ellipse.getStroke(), ellipse.getFill());
						} else if (node instanceof Circle circle) {
							var bounds = root.sceneToLocal(circle.localToScene(circle.getBoundsInLocal()));
							var r = (float) (0.5 * Math.min(bounds.getHeight(), bounds.getWidth()));
							var x = (float) (bounds.getCenterX());
							var y = py.apply(bounds.getCenterY());
							addCircle(contents, x, y, r);
							doFillStroke(contents, circle.getStroke(), circle.getFill());
						} else if (node instanceof QuadCurve || node instanceof CubicCurve) {
							var curve = (node instanceof QuadCurve ? convertQuadCurveToCubicCurve((QuadCurve) node) : (CubicCurve) node);
							var sX = (float) (root.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getX());
							var sY = py.apply(root.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getY());
							var c1X = (float) (root.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getX());
							var c1Y = py.apply(root.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getY());
							var c2X = (float) (root.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getX());
							var c2Y = py.apply(root.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getY());
							var tX = (float) (root.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getX());
							var tY = py.apply(root.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getY());
							contents.moveTo(sX, sY);
							contents.curveTo(c1X, c1Y, c2X, c2Y, tX, tY);
							doFillStroke(contents, curve.getStroke(), curve.getFill());
						} else if (node instanceof Path path) {
							if (containedInText(path))
								continue; // don't draw caret
							var local = new Point2D(0, 0);
							for (var element : path.getElements()) {
								if (element instanceof MoveTo moveTo) {
									local = new Point2D(moveTo.getX(), moveTo.getY());
									var t = root.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									contents.moveTo((float) (t.getX()), py.apply(t.getY()));
								} else if (element instanceof LineTo lineTo) {
									local = new Point2D(lineTo.getX(), lineTo.getY());
									var t = root.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									contents.lineTo((float) (t.getX()), py.apply(t.getY()));
								} else if (element instanceof HLineTo lineTo) {
									local = new Point2D(lineTo.getX(), local.getY());
									var t = root.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									contents.lineTo((float) (t.getX()), py.apply(t.getY()));
								} else if (element instanceof VLineTo lineTo) {
									local = new Point2D(local.getX(), lineTo.getY());
									var t = root.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									contents.lineTo((float) (t.getX()), py.apply(t.getY()));
								} else if (element instanceof ArcTo arcTo) {
									local = new Point2D(arcTo.getX(), arcTo.getY());
									System.err.println("arcTo: not implemented");
								} else if (element instanceof QuadCurveTo || element instanceof CubicCurveTo) {
									var curveTo = (element instanceof QuadCurveTo ? convertQuadToCubicCurveTo(local.getX(), local.getY(), (QuadCurveTo) element) : (CubicCurveTo) element);
									var t = root.sceneToLocal(path.localToScene(curveTo.getX(), curveTo.getY()));
									var c1 = root.sceneToLocal(path.localToScene(curveTo.getControlX1(), curveTo.getControlY1()));
									var c2 = root.sceneToLocal(path.localToScene(curveTo.getControlX2(), curveTo.getControlY2()));
									contents.curveTo((float) (c1.getX()), py.apply(c1.getY()), (float) (c2.getX()), py.apply(c2.getY()), (float) (t.getX()), py.apply(t.getY()));
								}
							}
							doFillStroke(contents, path.getStroke(), path.getFill());
						} else if (node instanceof Polygon polygon) {
							var points = polygon.getPoints();
							if (!points.isEmpty()) {
								var sX = (float) (root.sceneToLocal(polygon.localToScene(polygon.getPoints().get(0), polygon.getPoints().get(1))).getX());
								var sY = py.apply(root.sceneToLocal(polygon.localToScene(polygon.getPoints().get(0), polygon.getPoints().get(1))).getY());

								contents.moveTo(sX, sY);
								for (var i = 2; i < points.size(); i += 2) {
									var x = (float) (root.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getX());
									var y = py.apply(root.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getY());
									contents.lineTo(x, y);
								}
								contents.closePath();
								doFillStroke(contents, polygon.getStroke(), polygon.getFill());
							}
						} else if (node instanceof Polyline polyline) {
							var points = polyline.getPoints();
							if (!points.isEmpty()) {
								var sX = (float) (root.sceneToLocal(polyline.localToScene(polyline.getPoints().get(0), polyline.getPoints().get(1))).getX());
								var sY = py.apply(root.sceneToLocal(polyline.localToScene(polyline.getPoints().get(0), polyline.getPoints().get(1))).getY());
								contents.moveTo(sX, sY);
								for (var i = 0; i < points.size(); i += 2) {
									var x = (float) (root.sceneToLocal(polyline.localToScene(polyline.getPoints().get(i), polyline.getPoints().get(i + 1))).getX());
									var y = py.apply(root.sceneToLocal(polyline.localToScene(polyline.getPoints().get(i), polyline.getPoints().get(i + 1))).getY());
									contents.lineTo(x, y);
								}
								doFillStroke(contents, polyline.getStroke(), polyline.getFill());
							}
						} else if (node instanceof Text text) {
							if (!text.getText().isBlank()) {
								double screenAngle = 360 - getAngleOnScreen(text); // because y axis points upward in PDF
								var localBounds = text.getBoundsInLocal();
								var origX = localBounds.getMinX();
								var origY = localBounds.getMinY() + 0.87f * localBounds.getHeight();
								var rotateAnchorX = root.sceneToLocal(text.localToScene(origX, origY)).getX();
								var rotateAnchorY = root.sceneToLocal(text.localToScene(origX, origY)).getY();
								contents.beginText();
								if (isMirrored(text)) // todo: this is untested:
									screenAngle = 360 - screenAngle;
								contents.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(screenAngle), (float) (rotateAnchorX), py.apply(rotateAnchorY)));
								contents.setNonStrokingColor(pdfNonStrokingColor(text.getFill()));
								var fontHeight = (float) computeFinalHeight(root, text, text.getFont().getSize());
								setFont(contents, text, fontHeight);
								contents.showText(useOnlyUsualCharacters(text.getText()));
								contents.endText();
							}
						} else if (node instanceof ImageView imageView) {
							// todo: need to rotate
							var encoder = new PngEncoderFX(imageView.getImage());
							try (var ins = new ByteArrayInputStream(encoder.pngEncode(true))) {
								var image = LosslessFactory.createFromImage(document, ImageIO.read(ins));
								var bounds = root.sceneToLocal(imageView.localToScene(imageView.getBoundsInLocal()));
								var x = (float) (bounds.getMinX());
								var width = (float) (bounds.getWidth());
								var y = (float) (bounds.getMaxY());
								var height = (float) (bounds.getHeight());
								contents.drawImage(image, x, y, width, height);
							}
						} else if (node instanceof Shape3D || node instanceof Canvas || node instanceof Chart || node instanceof Arc) {
							var bounds = root.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
							var writableImage = new WritableImage((int) bounds.getWidth() * 3, (int) bounds.getHeight() * 3);
							var parameters = new SnapshotParameters();
							parameters.setFill(Color.TRANSPARENT);
							parameters.setTransform(javafx.scene.transform.Transform.scale(3, 3));
							var snapShot = node.snapshot(parameters, writableImage);
							var encoder = new PngEncoderFX(snapShot, true);
							try (var ins = new ByteArrayInputStream(encoder.pngEncode(true))) {
								var image = LosslessFactory.createFromImage(document, ImageIO.read(ins));
								var x = (float) (bounds.getMinX());
								var width = (float) (bounds.getWidth());
								var y = py.apply(bounds.getMaxY());
								var height = (float) (bounds.getHeight());
								contents.drawImage(image, x, y, width, height);
							}
						} else if (Basic.getDebugMode()) {
							var name = node.getClass().getSimpleName();
							if (!skippedClasses.contains(name)) {
								System.err.println("SaveToPDF: Skipping: " + name);
								skippedClasses.add(name);
							}
						}
					} catch (IOException ex) {
						Basic.caught(ex);
					}
				} finally {
					contents.restoreGraphicsState();
				}
			}
		}
		contents.close();
		document.save(file);
		document.close();
	}

	private static String useOnlyUsualCharacters(String text) {
		var buf = new StringBuilder();
		for (var i = 0; i < text.length(); i++) {
			var c = text.charAt(i);
			var isUsualCharacter = (c >= 32 && c <= 126) || (c >= 161 && c <= 255) || (c >= 256 && c <= 383) || (c >= 8192 && c <= 8303) || (c >= 8592 && c <= 8683);
			if (isUsualCharacter)
				buf.append(c);
			else
				buf.append("_");
		}
		return buf.toString();
	}

	private static void setFont(PDPageContentStream contentStream, Text text, float size) throws IOException {
		//System.err.println(text.getFont().getFamily()+" size: "+size);
		contentStream.setFont(convertToPDFBoxFont(text.getFont()), size);
	}

	public static PDType1Font convertToPDFBoxFont(Font javafxFont) {
		var pdfboxFontFamily = "";
		{
			var fontFamily = javafxFont.getFamily().toLowerCase();
			if (fontFamily.startsWith("times") || fontFamily.startsWith("arial"))
				pdfboxFontFamily = Standard14Fonts.FontName.TIMES_ROMAN.getName();
			else if (fontFamily.startsWith("courier") || fontFamily.startsWith("monospaced"))
				pdfboxFontFamily = Standard14Fonts.FontName.COURIER.getName();
			else if (fontFamily.startsWith("symbol"))
				pdfboxFontFamily = Standard14Fonts.FontName.SYMBOL.getName();
			else if (fontFamily.startsWith("zapf_dingbats"))
				pdfboxFontFamily = Standard14Fonts.FontName.ZAPF_DINGBATS.getName();
			else // if(fontFamily.startsWith("arial") || fontFamily.startsWith("helvetica") || fontFamily.startsWith("system"))
				pdfboxFontFamily = Standard14Fonts.FontName.HELVETICA.getName();
		}

		// Map JavaFX font weight and style to PDFBox font
		var bold = javafxFont.getName().contains(" Bold");
		var italic = javafxFont.getName().contains(" Italic");
		var pdfboxFontStyle = "";
		if (bold) {
			if (italic)
				pdfboxFontStyle = "_BoldItalic";
			else
				pdfboxFontStyle = "_Bold";
		} else if (italic)
			pdfboxFontStyle = "_Italic";
		var pdfboxFontFullName = pdfboxFontFamily + pdfboxFontStyle;
		var font = StringUtils.valueOfIgnoreCase(Standard14Fonts.FontName.class, pdfboxFontFullName);
		if (font == null) {
			pdfboxFontFullName = pdfboxFontFullName.replaceAll("Italic", "Oblique");
			font = StringUtils.valueOfIgnoreCase(Standard14Fonts.FontName.class, pdfboxFontFullName);
		}
		if (font == null) {
			font = StringUtils.valueOfIgnoreCase(Standard14Fonts.FontName.class, pdfboxFontFamily);
		}
		if (font == null)
			font = Standard14Fonts.FontName.HELVETICA;
		return new PDType1Font(font);
	}

	private static void doFillStroke(PDPageContentStream contentStream, Paint stroke, Paint fill) throws IOException {
		var pdfStroke = pdfStrokingColor(stroke);
		var pdfFill = pdfNonStrokingColor(fill);
		if (pdfStroke != null && pdfFill != null) {
			contentStream.setStrokingColor(pdfStroke);
			contentStream.setNonStrokingColor(pdfFill);
			contentStream.fillAndStroke();
		} else if (pdfStroke != null) {
			contentStream.setStrokingColor(pdfStroke);
			contentStream.stroke();
		} else if (pdfFill != null) {
			contentStream.setNonStrokingColor(pdfFill);
			contentStream.fill();
		}
	}

	private static void addCircle(PDPageContentStream contentStream, float cx, float cy, float r) throws IOException {
		addEllipse(contentStream, cx, cy, r, r);
	}

	private static void addEllipse(PDPageContentStream contentStream, float cx, float cy, float rx, float ry) throws IOException {
		final float k = 0.552284749831f;
		contentStream.moveTo(cx - rx, cy);
		contentStream.curveTo(cx - rx, cy + k * ry, cx - k * rx, cy + ry, cx, cy + ry);
		contentStream.curveTo(cx + k * rx, cy + ry, cx + rx, cy + k * ry, cx + rx, cy);
		contentStream.curveTo(cx + rx, cy - k * ry, cx + k * rx, cy - ry, cx, cy - ry);
		contentStream.curveTo(cx - k * rx, cy - ry, cx - rx, cy - k * ry, cx - rx, cy);
	}

	private static PDColor pdfNonStrokingColor(Paint paint) {
		if (paint == null || paint instanceof Color color && color.getOpacity() == 0.0)
			return null;
		if (!(paint instanceof Color))
			paint = MainWindowManager.isUseDarkTheme() ? Color.BLACK : Color.WHITE;

		var color = (Color) paint;
		if (color.equals(Color.TRANSPARENT))
			return null;
		else
			return new PDColor(new float[]{(float) color.getRed(), (float) color.getGreen(), (float) color.getBlue()}, PDDeviceRGB.INSTANCE);
	}

	private static PDColor pdfStrokingColor(Paint paint) {
		if (paint == null || paint instanceof Color color && color.getOpacity() == 0.0)
			return null;
		if (!(paint instanceof Color))
			paint = MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK;

		var color = (Color) paint;
		if (color.equals(Color.TRANSPARENT))
			return null;
		else
			return new PDColor(new float[]{(float) color.getRed(), (float) color.getGreen(), (float) color.getBlue()}, PDDeviceRGB.INSTANCE);
	}

	/**
	 * gets the angle of a node on screen
	 *
	 * @param node the node
	 * @return angle in degrees
	 */
	public static double getAngleOnScreen(Node node) {
		var localOrig = new Point2D(0, 0);
		var localX1000 = new Point2D(1000, 0);
		var orig = node.localToScreen(localOrig);
		if (orig != null) {
			var x1000 = node.localToScreen(localX1000).subtract(orig);
			if (x1000 != null) {
				return GeometryUtilsFX.computeAngle(x1000);
			}
		}
		return 0.0;
	}

	/**
	 * does this pane appear as a mirrored image on the screen?
	 *
	 * @param node the pane
	 * @return true, if mirror image, false if direct image
	 */
	public static boolean isMirrored(Node node) {
		var orig = node.localToScreen(0, 0);
		if (orig != null) {
			var x1000 = node.localToScreen(1000, 0);
			var y1000 = node.localToScreen(0, 1000);
			var p1 = x1000.subtract(orig);
			var p2 = y1000.subtract(orig);
			var determinant = p1.getX() * p2.getY() - p1.getY() * p2.getX();
			return (determinant < 0);
		} else
			return false;
	}

	private static CubicCurve convertQuadCurveToCubicCurve(QuadCurve quadCurve) {
		var cubicCurve = new CubicCurve();
		cubicCurve.setStartX(quadCurve.getStartX());
		cubicCurve.setStartY(quadCurve.getStartY());
		cubicCurve.setEndX(quadCurve.getEndX());
		cubicCurve.setEndY(quadCurve.getEndY());
		var c1x = (2.0 / 3.0) * quadCurve.getControlX() + (1.0 / 3.0) * quadCurve.getStartX();
		var c1y = (2.0 / 3.0) * quadCurve.getControlY() + (1.0 / 3.0) * quadCurve.getStartY();
		var c2x = (2.0 / 3.0) * quadCurve.getControlX() + (1.0 / 3.0) * quadCurve.getEndX();
		var c2y = (2.0 / 3.0) * quadCurve.getControlY() + (1.0 / 3.0) * quadCurve.getEndY();
		cubicCurve.setControlX1(c1x);
		cubicCurve.setControlY1(c1y);
		cubicCurve.setControlX2(c2x);
		cubicCurve.setControlY2(c2y);
		return cubicCurve;
	}

	private static CubicCurveTo convertQuadToCubicCurveTo(double startX, double startY, QuadCurveTo quadCurveTo) {
		var cubicCurveTo = new CubicCurveTo();
		cubicCurveTo.setX(quadCurveTo.getX());
		cubicCurveTo.setY(quadCurveTo.getY());
		var c1x = (2.0 / 3.0) * quadCurveTo.getControlX() + (1.0 / 3.0) * startX;
		var c1y = (2.0 / 3.0) * quadCurveTo.getControlY() + (1.0 / 3.0) * startY;
		var c2x = (2.0 / 3.0) * quadCurveTo.getControlX() + (1.0 / 3.0) * quadCurveTo.getX();
		var c2y = (2.0 / 3.0) * quadCurveTo.getControlY() + (1.0 / 3.0) * quadCurveTo.getY();
		cubicCurveTo.setControlX1(c1x);
		cubicCurveTo.setControlY1(c1y);
		cubicCurveTo.setControlX2(c2x);
		cubicCurveTo.setControlY2(c2y);
		return cubicCurveTo;
	}

	public static PDRectangle computeBoundingBox(Node pane) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = Double.MIN_VALUE;
		double maxY = Double.MIN_VALUE;

		for (var node : BasicFX.getAllRecursively(pane, n -> true)) {
			if (node instanceof Shape) {
				var bounds = pane.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
				minX = Math.min(minX, bounds.getMinX());
				minY = Math.min(minY, bounds.getMinY());
				maxX = Math.max(maxX, bounds.getMaxX());
				maxY = Math.max(maxY, bounds.getMaxY());
			}
		}
		{ // this restricts to the root node bounds
			minX = Math.max(minX, pane.getBoundsInLocal().getMinX());
			minY = Math.max(minY, pane.getBoundsInLocal().getMinY());

			maxX = Math.min(maxX, (pane.getBoundsInLocal().getMaxX()));
			maxY = Math.min(maxY, (pane.getBoundsInLocal().getMaxY()));
		}

		return new PDRectangle(new BoundingBox((float) minX, (float) minY, (float) maxX, (float) maxY));
	}

	public static boolean isNodeVisible(Node node) {
		if (!node.isVisible() || "iceberg".equals(node.getId())) {
			return false;
		}

		var parent = node.getParent();
		while (parent != null) {
			if (!parent.isVisible()) {
				return false;
			}
			parent = parent.getParent();
		}
		if (node instanceof Shape shape) {
			return (shape.getFill() != null && shape.getFill() != Color.TRANSPARENT) || (shape.getStroke() != null && shape.getStroke() != Color.TRANSPARENT);
		} else
			return true;
	}

	public static boolean containedInText(Node node) {
		while (node != null) {
			if (node instanceof Text || node instanceof RichTextLabel || node instanceof Labeled || node instanceof TextInputControl) {
				return true;
			} else
				node = node.getParent();
		}
		return false;
	}

	public static float[] strokeDashArray(float scaleFactor, Shape shape) {
		if (!shape.getStrokeDashArray().isEmpty()) {
			var array = new float[shape.getStrokeDashArray().size()];
			for (var i = 0; i < shape.getStrokeDashArray().size(); i++) {
				array[i] = scaleFactor * shape.getStrokeDashArray().get(i).floatValue(); // need to scale
			}
			return array;
		} else
			return new float[0];
	}

	public static double computeScaleFactor(Node root, Node node) {
		root.applyCss();
		node.applyCss();
		var scaleX = root.sceneToLocal(node.localToScene(1.0, 0.0)).getX() - root.sceneToLocal(node.localToScene(0.0, 0.0)).getX();
		var scaleY = root.sceneToLocal(node.localToScene(0.0, 1.0)).getY() - root.sceneToLocal(node.localToScene(0.0, 0.0)).getY();
		var value = Math.min(scaleX, scaleY); // todo: could also try average here?
		return value <= 0 ? 1.0 : value;
	}
}
