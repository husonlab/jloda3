/*
 * SaveToTikZ.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.print;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.util.Basic;
import jloda.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * saves a root node and all its descendants as a LaTeX {@code tikzpicture}, for inclusion in a LaTeX document.
 * <p>
 * The picture uses {@code [x=1pt, y=-1pt]}, so the exported coordinates are the same root-local coordinates
 * used by {@link SaveToSVG} (y running downwards, as on screen) and text is not mirrored. Lines, rectangles,
 * circles, ellipses, quadratic and cubic curves, polygons, polylines, general paths (arcs are sampled) and
 * text are reproduced as vector TikZ. Bitmap content (ImageView, Canvas, Chart, 3D and Arc nodes) and node
 * effects (drop shadows etc.) are not reproduced; use SVG or PDF export for those.
 * <p>
 * Daniel Huson, 9.2026
 */
public class SaveToTikZ {
	/**
	 * writes the given root node to a file as a tikzpicture
	 *
	 * @param root the root node to be saved
	 * @param file the file
	 * @throws IOException failed
	 */
	public static void apply(Node root, File file) throws IOException {
		apply(root, root.getBoundsInLocal(), file);
	}

	/**
	 * writes the given root node to a file as a tikzpicture, using the supplied bounds
	 *
	 * @param root   the root node to be saved
	 * @param bounds the bounds to use
	 * @param file   the file
	 * @throws IOException failed
	 */
	public static void apply(Node root, Bounds bounds, File file) throws IOException {
		if (file.exists())
			Files.delete(file.toPath());

		var buf = new StringBuilder();

		var formattedDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		buf.append("%% Creator: %s%n".formatted(System.getProperty("user.name")));
		buf.append("%% Created: %s%n".formatted(formattedDateTime));
		buf.append("%% Software: JLODA3 https://github.com/husonlab/jloda3%n".formatted());
		buf.append("%% Requires, in the document preamble: \\usepackage{tikz}%n".formatted());
		buf.append("%% Coordinates are in points (pt); the bounding box is %.1f x %.1f pt.%n".formatted(bounds.getWidth(), bounds.getHeight()));
		buf.append("\\begin{tikzpicture}[x=1pt,y=-1pt]%n".formatted());
		// fix the bounding box to the exported region, even if a stroke or shadow slightly overflows it
		buf.append("\\useasboundingbox (%.2f,%.2f) rectangle (%.2f,%.2f);%n"
				.formatted(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY()));

		writeNodeRecursively(root, root, buf);

		buf.append("\\end{tikzpicture}%n".formatted());

		try (var writer = FileUtils.getOutputWriterPossiblyZIPorGZIP(file.getPath())) {
			writer.write(buf.toString());
		}
	}

	/**
	 * recursively writes a node and its descendants
	 */
	private static void writeNodeRecursively(Node root, Node node, StringBuilder buf) {
		if (!node.isVisible() || "iceberg".equals(node.getId()))
			return;
		if (selfDrawable(node))
			buf.append(getTikZ(root, node));
		if (node instanceof Parent parent) {
			for (var child : parent.getChildrenUnmodifiable())
				writeNodeRecursively(root, child, buf);
		}
	}

	private static boolean selfDrawable(Node node) {
		if (node instanceof Shape shape)
			return (shape.getFill() != null && shape.getFill() != Color.TRANSPARENT) || (shape.getStroke() != null && shape.getStroke() != Color.TRANSPARENT);
		return true;
	}

	/**
	 * constructs the TikZ description for a single node (its own geometry, not its children)
	 *
	 * @param root the root node, used to resolve coordinates and scale
	 * @param node the node to report
	 * @return the TikZ string, or the empty string
	 */
	public static String getTikZ(Node root, Node node) {
		var scaleFactor = SaveToSVG.computeScaleFactor(root, node);
		var options = shapeOptions(node, scaleFactor);
		var buf = new StringBuilder();
		try {
			if (node instanceof Line line) {
				var p1 = local(root, line, line.getStartX(), line.getStartY());
				var p2 = local(root, line, line.getEndX(), line.getEndY());
				buf.append("\\path[%s] %s -- %s;%n".formatted(options, coord(p1), coord(p2)));
			} else if (node instanceof Rectangle rectangle) {
				// as a polygon, so that any rotation carried by the transform is preserved
				var points = new ArrayList<Point2D>();
				points.add(local(root, rectangle, rectangle.getX(), rectangle.getY()));
				points.add(local(root, rectangle, rectangle.getX() + rectangle.getWidth(), rectangle.getY()));
				points.add(local(root, rectangle, rectangle.getX() + rectangle.getWidth(), rectangle.getY() + rectangle.getHeight()));
				points.add(local(root, rectangle, rectangle.getX(), rectangle.getY() + rectangle.getHeight()));
				buf.append(polygonPath(points, options, true));
			} else if (node instanceof Circle circle) {
				var c = local(root, circle, circle.getCenterX(), circle.getCenterY());
				var e = local(root, circle, circle.getCenterX() + circle.getRadius(), circle.getCenterY());
				buf.append("\\path[%s] %s circle[radius=%.2fpt];%n".formatted(options, coord(c), c.distance(e)));
			} else if (node instanceof Ellipse ellipse) {
				var center = local(root, ellipse, ellipse.getCenterX(), ellipse.getCenterY());
				var xEdge = local(root, ellipse, ellipse.getCenterX() + ellipse.getRadiusX(), ellipse.getCenterY());
				var yEdge = local(root, ellipse, ellipse.getCenterX(), ellipse.getCenterY() + ellipse.getRadiusY());
				buf.append("\\path[%s] %s ellipse[x radius=%.2fpt, y radius=%.2fpt];%n".formatted(options, coord(center), center.distance(xEdge), center.distance(yEdge)));
			} else if (node instanceof QuadCurve curve) {
				var s = local(root, curve, curve.getStartX(), curve.getStartY());
				var c = local(root, curve, curve.getControlX(), curve.getControlY());
				var t = local(root, curve, curve.getEndX(), curve.getEndY());
				buf.append("\\path[%s] %s;%n".formatted(options, quadToTikZ(s, c, t)));
			} else if (node instanceof CubicCurve curve) {
				var s = local(root, curve, curve.getStartX(), curve.getStartY());
				var c1 = local(root, curve, curve.getControlX1(), curve.getControlY1());
				var c2 = local(root, curve, curve.getControlX2(), curve.getControlY2());
				var t = local(root, curve, curve.getEndX(), curve.getEndY());
				buf.append("\\path[%s] %s .. controls %s and %s .. %s;%n".formatted(options, coord(s), coord(c1), coord(c2), coord(t)));
			} else if (node instanceof Path path) {
				if (!containedInText(path)) {
					var d = pathToTikZ(path, root);
					if (!d.isBlank())
						buf.append("\\path[%s] %s;%n".formatted(options, d));
				}
			} else if (node instanceof Polygon polygon) {
				var points = new ArrayList<Point2D>();
				for (var i = 0; i < polygon.getPoints().size(); i += 2)
					points.add(local(root, polygon, polygon.getPoints().get(i), polygon.getPoints().get(i + 1)));
				buf.append(polygonPath(points, options, true));
			} else if (node instanceof Polyline polyline) {
				var points = new ArrayList<Point2D>();
				for (var i = 0; i < polyline.getPoints().size(); i += 2)
					points.add(local(root, polyline, polyline.getPoints().get(i), polyline.getPoints().get(i + 1)));
				buf.append(polygonPath(points, options, false));
			} else if (node instanceof Text text) {
				if (!text.getText().isBlank())
					buf.append(textToTikZ(root, text));
			}
			// ImageView, Canvas, Chart, Shape3D and Arc are intentionally not reproduced (use SVG/PDF for those)
		} catch (Exception ex) {
			Basic.caught(ex);
		}
		return buf.toString();
	}

	/**
	 * builds the TikZ draw/fill options for a shape: colors, opacities, line width and dashes
	 */
	private static String shapeOptions(Node node, double scaleFactor) {
		var options = new ArrayList<String>();
		if (node instanceof Shape shape) {
			if (shape.getStroke() instanceof Color color && color != Color.TRANSPARENT && color.getOpacity() > 0) {
				options.add("draw=" + tikzColor(color));
				if (color.getOpacity() < 1)
					options.add("draw opacity=%.3f".formatted(color.getOpacity()));
			}
			if (shape.getFill() instanceof Color color && color != Color.TRANSPARENT && color.getOpacity() > 0) {
				options.add("fill=" + tikzColor(color));
				if (color.getOpacity() < 1)
					options.add("fill opacity=%.3f".formatted(color.getOpacity()));
			}
			options.add("line width=%.2fpt".formatted(scaleFactor * shape.getStrokeWidth()));
			if (shape.getStrokeLineCap() == StrokeLineCap.ROUND)
				options.add("line cap=round");
			else if (shape.getStrokeLineCap() == StrokeLineCap.SQUARE)
				options.add("line cap=rect");
			var dashes = shape.getStrokeDashArray();
			if (!dashes.isEmpty()) {
				var sb = new StringBuilder("dash pattern=");
				for (var i = 0; i < dashes.size(); i++)
					sb.append(i % 2 == 0 ? "on " : "off ").append("%.2fpt ".formatted(scaleFactor * dashes.get(i)));
				options.add(sb.toString().trim());
			}
		}
		return String.join(", ", options);
	}

	/**
	 * a text node, placed with its baseline left at the transformed anchor and rotated by its on-screen angle
	 */
	private static String textToTikZ(Node root, Text text) {
		var screenAngle = SaveToSVG.getAngleOnScreen(text);
		var localBounds = text.getBoundsInLocal();
		var origX = localBounds.getMinX();
		var origY = localBounds.getMinY() + 0.87 * localBounds.getHeight();
		var anchor = local(root, text, origX, origY);
		if (SaveToSVG.isMirrored(text))
			screenAngle = 360 - screenAngle;
		var fontSize = SaveToSVG.computeFinalHeight(root, text, text.getFont().getSize());

		var options = new ArrayList<String>();
		options.add("anchor=base west");
		options.add("inner sep=0pt");
		options.add("outer sep=0pt");
		// TikZ rotate is counter-clockwise in paper space, the screen angle is clockwise
		if (screenAngle % 360.0 != 0)
			options.add("rotate=%.2f".formatted(-screenAngle));
		if (text.getFill() instanceof Color color && color != Color.TRANSPARENT)
			options.add("text=" + tikzColor(color));
		options.add("font=" + fontSpec(text.getFont(), fontSize));

		return "\\node[%s] at %s {%s};%n".formatted(String.join(", ", options), coord(anchor), escapeLaTeX(text.getText()));
	}

	/**
	 * builds a LaTeX font specification (size and, where recognizable, italic/bold) for a node's {@code font=} option
	 */
	private static String fontSpec(Font font, double sizePt) {
		var buf = new StringBuilder("{");
		buf.append("\\fontsize{%.1f}{%.1f}\\selectfont".formatted(sizePt, 1.2 * sizePt));
		var name = font.getName().toLowerCase();
		if (name.contains("italic") || name.contains("oblique"))
			buf.append("\\itshape");
		if (name.contains("bold"))
			buf.append("\\bfseries");
		buf.append("}");
		return buf.toString();
	}

	/**
	 * a quadratic Bézier, converted to the cubic form TikZ draws
	 */
	private static String quadToTikZ(Point2D s, Point2D c, Point2D t) {
		var c1 = new Point2D(s.getX() + 2.0 / 3.0 * (c.getX() - s.getX()), s.getY() + 2.0 / 3.0 * (c.getY() - s.getY()));
		var c2 = new Point2D(t.getX() + 2.0 / 3.0 * (c.getX() - t.getX()), t.getY() + 2.0 / 3.0 * (c.getY() - t.getY()));
		return "%s .. controls %s and %s .. %s".formatted(coord(s), coord(c1), coord(c2), coord(t));
	}

	/**
	 * a polygon (closed) or polyline (open) through the given already-transformed points
	 */
	private static String polygonPath(List<Point2D> points, String options, boolean close) {
		if (points.isEmpty())
			return "";
		var sb = new StringBuilder("\\path[%s] ".formatted(options));
		for (var i = 0; i < points.size(); i++)
			sb.append(i == 0 ? "" : " -- ").append(coord(points.get(i)));
		if (close)
			sb.append(" -- cycle");
		sb.append(";%n".formatted());
		return sb.toString();
	}

	/**
	 * converts a JavaFX Path into a TikZ path specification (in root-local coordinates). Arcs are sampled into
	 * short line segments, which keeps the result correct under any orientation.
	 */
	private static String pathToTikZ(Path path, Node root) {
		var buf = new StringBuilder();
		var local = new Point2D(0, 0); // current point, in path-local coordinates
		var first = true;
		for (var element : path.getElements()) {
			if (element instanceof MoveTo moveTo) {
				local = new Point2D(moveTo.getX(), moveTo.getY());
				buf.append(first ? "" : " ").append(coord(toRoot(root, path, local)));
				first = false;
			} else if (element instanceof LineTo lineTo) {
				local = new Point2D(lineTo.getX(), lineTo.getY());
				buf.append(" -- ").append(coord(toRoot(root, path, local)));
			} else if (element instanceof HLineTo lineTo) {
				local = new Point2D(lineTo.getX(), local.getY());
				buf.append(" -- ").append(coord(toRoot(root, path, local)));
			} else if (element instanceof VLineTo lineTo) {
				local = new Point2D(local.getX(), lineTo.getY());
				buf.append(" -- ").append(coord(toRoot(root, path, local)));
			} else if (element instanceof QuadCurveTo curveTo) {
				var s = toRoot(root, path, local);
				var c = toRoot(root, path, new Point2D(curveTo.getControlX(), curveTo.getControlY()));
				local = new Point2D(curveTo.getX(), curveTo.getY());
				var t = toRoot(root, path, local);
				buf.append(" .. controls ")
						.append(coord(new Point2D(s.getX() + 2.0 / 3.0 * (c.getX() - s.getX()), s.getY() + 2.0 / 3.0 * (c.getY() - s.getY()))))
						.append(" and ")
						.append(coord(new Point2D(t.getX() + 2.0 / 3.0 * (c.getX() - t.getX()), t.getY() + 2.0 / 3.0 * (c.getY() - t.getY()))))
						.append(" .. ").append(coord(t));
			} else if (element instanceof CubicCurveTo curveTo) {
				var c1 = toRoot(root, path, new Point2D(curveTo.getControlX1(), curveTo.getControlY1()));
				var c2 = toRoot(root, path, new Point2D(curveTo.getControlX2(), curveTo.getControlY2()));
				local = new Point2D(curveTo.getX(), curveTo.getY());
				buf.append(" .. controls ").append(coord(c1)).append(" and ").append(coord(c2)).append(" .. ").append(coord(toRoot(root, path, local)));
			} else if (element instanceof ArcTo arcTo) {
				var end = new Point2D(arcTo.getX(), arcTo.getY());
				for (var p : sampleArc(local, end, arcTo.getRadiusX(), arcTo.getRadiusY(), arcTo.getXAxisRotation(), arcTo.isLargeArcFlag(), arcTo.isSweepFlag()))
					buf.append(" -- ").append(coord(toRoot(root, path, p)));
				local = end;
			} else if (element instanceof ClosePath) {
				buf.append(" -- cycle");
			}
		}
		return buf.toString();
	}

	/**
	 * samples an SVG-style elliptical arc (from p0 to p1) into a list of points in the same local coordinate
	 * system, excluding the start point and including the end point. Implements the endpoint-to-center
	 * parameterization from the SVG specification (appendix F.6).
	 */
	private static List<Point2D> sampleArc(Point2D p0, Point2D p1, double rx, double ry, double xAxisRotationDeg, boolean largeArc, boolean sweep) {
		var points = new ArrayList<Point2D>();
		rx = Math.abs(rx);
		ry = Math.abs(ry);
		if (rx == 0 || ry == 0 || p0.equals(p1)) {
			points.add(p1);
			return points;
		}
		var phi = Math.toRadians(xAxisRotationDeg % 360.0);
		var cosPhi = Math.cos(phi);
		var sinPhi = Math.sin(phi);

		var dx2 = (p0.getX() - p1.getX()) / 2.0;
		var dy2 = (p0.getY() - p1.getY()) / 2.0;
		var x1p = cosPhi * dx2 + sinPhi * dy2;
		var y1p = -sinPhi * dx2 + cosPhi * dy2;

		// correct out-of-range radii
		var lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
		if (lambda > 1) {
			var s = Math.sqrt(lambda);
			rx *= s;
			ry *= s;
		}

		var sign = (largeArc != sweep) ? 1.0 : -1.0;
		var num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
		var den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
		var co = sign * Math.sqrt(Math.max(0.0, num / den));
		var cxp = co * (rx * y1p / ry);
		var cyp = co * (-ry * x1p / rx);

		var cx = cosPhi * cxp - sinPhi * cyp + (p0.getX() + p1.getX()) / 2.0;
		var cy = sinPhi * cxp + cosPhi * cyp + (p0.getY() + p1.getY()) / 2.0;

		var theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
		var dTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
		if (!sweep && dTheta > 0)
			dTheta -= 2 * Math.PI;
		else if (sweep && dTheta < 0)
			dTheta += 2 * Math.PI;

		var steps = Math.max(2, (int) Math.ceil(Math.abs(dTheta) / (Math.PI / 16.0)));
		for (var i = 1; i <= steps; i++) {
			var theta = theta1 + dTheta * i / steps;
			var x = cx + rx * Math.cos(theta) * cosPhi - ry * Math.sin(theta) * sinPhi;
			var y = cy + rx * Math.cos(theta) * sinPhi + ry * Math.sin(theta) * cosPhi;
			points.add(new Point2D(x, y));
		}
		return points;
	}

	private static double angle(double ux, double uy, double vx, double vy) {
		var dot = ux * vx + uy * vy;
		var len = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
		var a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
		return (ux * vy - uy * vx < 0) ? -a : a;
	}

	/**
	 * a shape-local point, mapped into root-local coordinates (the coordinate system of the exported picture)
	 */
	private static Point2D local(Node root, Node node, double x, double y) {
		return root.sceneToLocal(node.localToScene(x, y));
	}

	private static Point2D toRoot(Node root, Node node, Point2D local) {
		return root.sceneToLocal(node.localToScene(local.getX(), local.getY()));
	}

	private static String coord(Point2D p) {
		return "(%.2f,%.2f)".formatted(p.getX(), p.getY());
	}

	/**
	 * an inline TikZ/xcolor color specification, using the 0..255 rgb model so that no colors need to be predefined
	 */
	public static String tikzColor(Paint paint) {
		if (paint instanceof Color color)
			return "{rgb,255:red,%d;green,%d;blue,%d}".formatted((int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
		else
			return "black";
	}

	/**
	 * escapes the LaTeX special characters in a piece of text
	 */
	public static String escapeLaTeX(String text) {
		var buf = new StringBuilder();
		for (var i = 0; i < text.length(); i++) {
			var ch = text.charAt(i);
			switch (ch) {
				case '\\' -> buf.append("\\textbackslash{}");
				case '{' -> buf.append("\\{");
				case '}' -> buf.append("\\}");
				case '$' -> buf.append("\\$");
				case '&' -> buf.append("\\&");
				case '#' -> buf.append("\\#");
				case '^' -> buf.append("\\textasciicircum{}");
				case '_' -> buf.append("\\_");
				case '%' -> buf.append("\\%");
				case '~' -> buf.append("\\textasciitilde{}");
				default -> buf.append(ch);
			}
		}
		return buf.toString();
	}

	private static boolean containedInText(Node node) {
		while (node != null) {
			if (node instanceof Text || node instanceof RichTextLabel || node instanceof Labeled || node instanceof TextInputControl)
				return true;
			else
				node = node.getParent();
		}
		return false;
	}
}
