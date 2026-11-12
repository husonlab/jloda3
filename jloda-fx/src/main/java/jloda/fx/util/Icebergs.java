/*
 *  Icebergs.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.util;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.InputEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.fx.control.RichTextLabel;

import java.lang.ref.WeakReference;

/**
 * icebergs: an invisible, fat copy of a shape that lies below it and makes it easy to hit with a mouse or a finger.
 * <p>
 * An iceberg does not decide by itself what a press on it means, because the iceberg of one item may cover another
 * item. A press on an iceberg goes to the item that is closest to the pointer, among the items that are under the
 * pointer (a shape that has an iceberg, or a label; these have distance 0) and the shapes whose icebergs contain the
 * pointer. Of two items that are equally close, the one in front is taken. A circle counts as under the pointer
 * within CIRCLE_CORE of its center, so that a small node is not lost to the edges that end in it.
 * The drag, release, click and context-menu request that follow a press go to the same item.
 * Daniel Huson, 1.2024
 */
public class Icebergs {
	private static final Object SHAPE_KEY = "iceberg-shape"; // property of an iceberg: the shape that it stands for
	private static final Object HAS_ICEBERG_KEY = "has-iceberg"; // property of a shape that has an iceberg

	private static final double CIRCLE_CORE = 5; // a circle smaller than this is treated as if it had this radius

	private static boolean enabled = false;

	private static WeakReference<Shape> pressedIceberg = new WeakReference<>(null); // the iceberg last pressed
	private static WeakReference<Node> pressedTarget = new WeakReference<>(null); // and the item that the press went to

	/**
	 * creates a shape that represents the "bottom of the iceberg", which is used in a mobile app to make it possible
	 * to click on the shape
	 *
	 * @param shape                     original shape
	 * @param delegateMouseInteractions if true, delegates mouse pressed, -clicked, -dragged and -released events and
	 *                                  context-menu requests to shape; if false, they are left to the iceberg, from which
	 *                                  they reach the handlers of its parent. In either case, an event goes to another
	 *                                  item instead, if that item is under the pointer or closer to it.
	 *                                  (Mouse entered and exited are always delegated and any attempt to set handlers for these
	 *                                  events on the iceberg are ignored)
	 * @return bottom of the iceberg or null, if shape not supported
	 */
	public static Shape create(Shape shape, boolean delegateMouseInteractions) {
		if ("iceberg".equals(shape.getId()))
			throw new RuntimeException("create(): not valid on iceberg");
		else {
			Shape iceberg;
			var hasVolume = false;
			if (shape instanceof Line line) {
				var line1 = new Line();
				line.setPickOnBounds(false);
				line1.startXProperty().bind(line.startXProperty());
				line1.startYProperty().bind(line.startYProperty());
				line1.endXProperty().bind(line.endXProperty());
				line1.endYProperty().bind(line.endYProperty());
				iceberg = line1;
			} else if (shape instanceof Path path) {
				var path1 = new Path();
				path1.setPickOnBounds(false);
				InvalidationListener updatePath = e -> {
					path1.getElements().clear();
					for (var element : path.getElements()) {
						if (element instanceof MoveTo moveTo) {
							var moveTo1 = new MoveTo();
							moveTo1.xProperty().bind(moveTo.xProperty());
							moveTo1.yProperty().bind(moveTo.yProperty());
							path1.getElements().add(moveTo1);
						} else if (element instanceof LineTo lineTo) {
							var lineTo1 = new LineTo();
							lineTo1.xProperty().bind(lineTo.xProperty());
							lineTo1.yProperty().bind(lineTo.yProperty());
							path1.getElements().add(lineTo1);
						} else if (element instanceof HLineTo hLineTo) {
							var hLineTo1 = new HLineTo();
							hLineTo1.xProperty().bind(hLineTo.xProperty());
							path1.getElements().add(hLineTo1);
						} else if (element instanceof VLineTo vLineTo) {
							var vLineTo1 = new VLineTo();
							vLineTo1.yProperty().bind(vLineTo.yProperty());
							path1.getElements().add(vLineTo1);
						} else if (element instanceof QuadCurveTo quadCurveTo) {
							var quadCurveTo1 = new QuadCurveTo();
							quadCurveTo1.xProperty().bind(quadCurveTo.xProperty());
							quadCurveTo1.yProperty().bind(quadCurveTo.yProperty());
							quadCurveTo1.controlXProperty().bind(quadCurveTo.controlXProperty());
							quadCurveTo1.controlYProperty().bind(quadCurveTo.controlYProperty());
							path1.getElements().add(quadCurveTo1);
						} else if (element instanceof CubicCurveTo cubicCurveTo) {
							var cubicCurveTo1 = new CubicCurveTo();
							cubicCurveTo1.xProperty().bind(cubicCurveTo.xProperty());
							cubicCurveTo1.yProperty().bind(cubicCurveTo.yProperty());
							cubicCurveTo1.controlX1Property().bind(cubicCurveTo.controlX1Property());
							cubicCurveTo1.controlY1Property().bind(cubicCurveTo.controlY1Property());
							cubicCurveTo1.controlX2Property().bind(cubicCurveTo.controlX2Property());
							cubicCurveTo1.controlY2Property().bind(cubicCurveTo.controlY2Property());
							path1.getElements().add(cubicCurveTo1);
						} else if (element instanceof ArcTo arcTo) {
							var arcTo1 = new ArcTo();
							arcTo1.xProperty().bind(arcTo.xProperty());
							arcTo1.yProperty().bind(arcTo.yProperty());
							arcTo1.largeArcFlagProperty().bind(arcTo.largeArcFlagProperty());
							arcTo1.sweepFlagProperty().bind(arcTo.sweepFlagProperty());
							arcTo1.radiusXProperty().bind(arcTo.radiusXProperty());
							arcTo1.radiusYProperty().bind(arcTo.radiusYProperty());
							path1.getElements().add(arcTo1);
						}
					}
				};
				updatePath.invalidated(null);
				path.getElements().addListener((updatePath));
				iceberg = path1;
			} else if (shape instanceof Circle circle) {
				var circle1 = new Circle();
				circle1.radiusProperty().bind(Bindings.createDoubleBinding(() -> Math.max(10, circle.getRadius()), circle.radiusProperty()));
				iceberg = circle1;
				hasVolume = true;
			} else if (shape instanceof Rectangle rectangle) {
				var rectangle1 = new Rectangle();
				rectangle1.widthProperty().bind(rectangle.widthProperty());
				rectangle1.heightProperty().bind(rectangle.heightProperty());
				iceberg = rectangle1;
				hasVolume = true;
			} else if (shape instanceof Polygon polygon) {
				var polygon1 = new Polygon();
				polygon1.getPoints().addAll(polygon.getPoints());
				iceberg = polygon1;
				hasVolume = true;
			} else if (shape instanceof Polyline polyline) {
				var polyline1 = new Polyline();
				polyline1.setPickOnBounds(false);
				polyline1.getPoints().addAll(polyline.getPoints());
				iceberg = polyline1;
			} else {
				var rectangle = new Rectangle();
				rectangle.widthProperty().bind(Bindings.createDoubleBinding(() -> Math.max(10, 1.1 * shape.getBoundsInLocal().getWidth()), shape.boundsInLocalProperty()));
				rectangle.heightProperty().bind(Bindings.createDoubleBinding(() -> Math.max(10, 1.1 * shape.getBoundsInLocal().getHeight()), shape.boundsInLocalProperty()));
				rectangle.xProperty().bind(rectangle.widthProperty().multiply(-0.5));
				rectangle.yProperty().bind(rectangle.heightProperty().multiply(-0.5));
				iceberg = rectangle;
				hasVolume = true;
			} // todo: add polylines and polygons

			iceberg.setUserData(shape.getUserData());
			iceberg.scaleXProperty().bind(shape.scaleXProperty());
			iceberg.scaleYProperty().bind(shape.scaleYProperty());
			iceberg.translateXProperty().bind(shape.translateXProperty());
			iceberg.translateYProperty().bind(shape.translateYProperty());
			iceberg.layoutXProperty().bind(shape.layoutXProperty());
			iceberg.layoutYProperty().bind(shape.layoutYProperty());

			iceberg.getProperties().put(SHAPE_KEY, shape);
			shape.getProperties().put(HAS_ICEBERG_KEY, true);

			// filters, so that the choice of the item is made before any handler of the iceberg or its parents runs
			EventHandler<MouseEvent> mouseFilter = e -> redirect(iceberg, shape, delegateMouseInteractions, e, e.getSceneX(), e.getSceneY());
			iceberg.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseFilter);
			iceberg.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseFilter);
			iceberg.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseFilter);
			iceberg.addEventFilter(MouseEvent.MOUSE_CLICKED, mouseFilter);
			iceberg.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> redirect(iceberg, shape, delegateMouseInteractions, e, e.getSceneX(), e.getSceneY()));

			EventHandler<MouseEvent> handler = shape::fireEvent;
			iceberg.setOnMouseEntered(handler);
			iceberg.setOnMouseExited(handler);
			iceberg.onMouseEnteredProperty().addListener((v, o, n) -> {
				if (n != handler)
					Platform.runLater(() -> iceberg.setOnMouseEntered(handler));
			});
			iceberg.onMouseExitedProperty().addListener((v, o, n) -> {
				if (n != handler)
					Platform.runLater(() -> iceberg.setOnMouseExited(handler));
			});
			iceberg.setFill(Color.TRANSPARENT);
			iceberg.setStroke(Color.TRANSPARENT);
			if (hasVolume) {
				iceberg.setStrokeWidth(5);
				iceberg.setStyle("-fx-stroke-width: 5;-fx-stroke: transparent;-fx-fill: transparent;");
			} else {
				iceberg.setStrokeWidth(20);
				iceberg.setStyle("-fx-stroke-width: 20;-fx-stroke: transparent;-fx-fill: transparent;");
			}
			iceberg.setId("iceberg");
			return iceberg;
		}
	}

	/**
	 * passes an event that has arrived at an iceberg on to the item that it is meant for
	 *
	 * @param iceberg  the iceberg
	 * @param shape    the shape of the iceberg
	 * @param delegate does the iceberg delegate its events to its shape?
	 */
	private static void redirect(Shape iceberg, Shape shape, boolean delegate, InputEvent e, double sceneX, double sceneY) {
		Node target;
		if (e.getEventType() == MouseEvent.MOUSE_PRESSED || pressedIceberg.get() != iceberg) {
			target = pick(iceberg, shape, sceneX, sceneY);
			if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
				pressedIceberg = new WeakReference<>(iceberg);
				pressedTarget = new WeakReference<>(target);
			}
		} else {
			target = pressedTarget.get();
			if (target == null || target.getScene() == null)
				target = shape;
		}
		if (delegate || target != shape) {
			e.consume();
			target.fireEvent(e);
		}
	}

	/**
	 * determines the item that a press at the given location is meant for
	 *
	 * @param iceberg the iceberg that was hit
	 * @param shape   the shape of that iceberg
	 * @return the item closest to the pointer
	 */
	private static Node pick(Shape iceberg, Shape shape, double sceneX, double sceneY) {
		// the drawing that the iceberg is part of: everything below the closest ancestor that is not a group
		Node drawing = iceberg;
		while (drawing.getParent() instanceof Group group && !(group instanceof RichTextLabel))
			drawing = group;
		if (drawing.getParent() != null)
			drawing = drawing.getParent();

		var pick = new Pick();
		visit(drawing, sceneX, sceneY, pick);
		return pick.item != null ? pick.item : shape;
	}

	private static final class Pick {
		Node item; // the closest item so far
		double distance = Double.MAX_VALUE;
	}

	/**
	 * visits the nodes from front to back, keeping the item closest to the pointer
	 *
	 * @return true, once an item under the pointer has been found
	 */
	private static boolean visit(Node node, double sceneX, double sceneY, Pick pick) {
		if (!node.isVisible() || node.isMouseTransparent() || node.isDisabled())
			return false;
		if (node instanceof Shape shape) {
			if (shape.getProperties().get(SHAPE_KEY) instanceof Shape original) {
				if (contains(shape, sceneX, sceneY)) {
					var distance = distance(original, sceneX, sceneY);
					if (distance < pick.distance) { // of two that are equally close, the one in front is kept
						pick.distance = distance;
						pick.item = original;
					}
				}
			} else if (shape.getProperties().containsKey(HAS_ICEBERG_KEY) && contains(shape, sceneX, sceneY)) {
				pick.distance = 0;
				pick.item = shape;
			}
		} else if (node instanceof RichTextLabel || node instanceof Control) {
			var local = node.sceneToLocal(sceneX, sceneY);
			if (local != null && node.getBoundsInLocal().contains(local)) {
				pick.distance = 0;
				pick.item = node;
			}
		} else if (node instanceof Parent parent) {
			var children = parent.getChildrenUnmodifiable();
			for (var i = children.size() - 1; i >= 0; i--) {
				if (visit(children.get(i), sceneX, sceneY, pick))
					return true;
			}
		}
		return pick.distance == 0;
	}

	private static boolean contains(Shape shape, double sceneX, double sceneY) {
		var local = shape.sceneToLocal(sceneX, sceneY);
		return local != null && shape.contains(local);
	}

	/**
	 * the distance from a location in the scene to a shape, in scene coordinates. Exact for lines, circles and paths
	 * (curves and arcs are sampled); for other shapes, the distance to the bounding box. A circle has a radius of
	 * CIRCLE_CORE at least
	 */
	private static double distance(Shape shape, double sceneX, double sceneY) {
		var p = shape.sceneToLocal(sceneX, sceneY);
		if (p == null)
			return Double.MAX_VALUE;
		Point2D q; // the point of the shape that is closest to p
		if (shape instanceof Line line) {
			q = closestOnSegment(p, line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
		} else if (shape instanceof Circle circle) {
			var center = new Point2D(circle.getCenterX(), circle.getCenterY());
			var radius = Math.max(CIRCLE_CORE, circle.getRadius());
			var d = p.distance(center);
			q = (d <= radius ? p : center.add(p.subtract(center).multiply(radius / d)));
		} else if (shape instanceof Path path) {
			q = closestOnPath(p, path);
		} else {
			var bounds = shape.getBoundsInLocal();
			q = new Point2D(Math.max(bounds.getMinX(), Math.min(bounds.getMaxX(), p.getX())), Math.max(bounds.getMinY(), Math.min(bounds.getMaxY(), p.getY())));
		}
		if (q == null)
			return Double.MAX_VALUE;
		var inScene = shape.localToScene(q);
		return Math.hypot(inScene.getX() - sceneX, inScene.getY() - sceneY);
	}

	private static Point2D closestOnSegment(Point2D p, double x1, double y1, double x2, double y2) {
		var dx = x2 - x1;
		var dy = y2 - y1;
		var lengthSquared = dx * dx + dy * dy;
		var t = (lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((p.getX() - x1) * dx + (p.getY() - y1) * dy) / lengthSquared)));
		return new Point2D(x1 + t * dx, y1 + t * dy);
	}

	/**
	 * the point of a path that is closest to p; null, if the path is empty
	 */
	private static Point2D closestOnPath(Point2D p, Path path) {
		final var samples = 16; // number of straight pieces that a curve is cut into
		Point2D best = null;
		double x = 0, y = 0; // current point
		double startX = 0, startY = 0; // start of the current subpath
		for (var element : path.getElements()) {
			var offsetX = element.isAbsolute() ? 0 : x;
			var offsetY = element.isAbsolute() ? 0 : y;
			var points = new java.util.ArrayList<Point2D>(); // the points that follow the current one
			if (element instanceof MoveTo moveTo) {
				x = startX = offsetX + moveTo.getX();
				y = startY = offsetY + moveTo.getY();
				continue;
			} else if (element instanceof LineTo lineTo) {
				points.add(new Point2D(offsetX + lineTo.getX(), offsetY + lineTo.getY()));
			} else if (element instanceof HLineTo hLineTo) {
				points.add(new Point2D(offsetX + hLineTo.getX(), y));
			} else if (element instanceof VLineTo vLineTo) {
				points.add(new Point2D(x, offsetY + vLineTo.getY()));
			} else if (element instanceof ClosePath) {
				points.add(new Point2D(startX, startY));
			} else if (element instanceof QuadCurveTo quad) {
				var cx = offsetX + quad.getControlX();
				var cy = offsetY + quad.getControlY();
				var ex = offsetX + quad.getX();
				var ey = offsetY + quad.getY();
				for (var i = 1; i <= samples; i++) {
					var t = (double) i / samples;
					var s = 1 - t;
					points.add(new Point2D(s * s * x + 2 * s * t * cx + t * t * ex, s * s * y + 2 * s * t * cy + t * t * ey));
				}
			} else if (element instanceof CubicCurveTo cubic) {
				var c1x = offsetX + cubic.getControlX1();
				var c1y = offsetY + cubic.getControlY1();
				var c2x = offsetX + cubic.getControlX2();
				var c2y = offsetY + cubic.getControlY2();
				var ex = offsetX + cubic.getX();
				var ey = offsetY + cubic.getY();
				for (var i = 1; i <= samples; i++) {
					var t = (double) i / samples;
					var s = 1 - t;
					points.add(new Point2D(s * s * s * x + 3 * s * s * t * c1x + 3 * s * t * t * c2x + t * t * t * ex,
							s * s * s * y + 3 * s * s * t * c1y + 3 * s * t * t * c2y + t * t * t * ey));
				}
			} else if (element instanceof ArcTo arcTo) {
				sampleArc(x, y, arcTo, offsetX + arcTo.getX(), offsetY + arcTo.getY(), samples, points);
			}
			for (var point : points) {
				var q = closestOnSegment(p, x, y, point.getX(), point.getY());
				if (best == null || p.distance(q) < p.distance(best))
					best = q;
				x = point.getX();
				y = point.getY();
			}
		}
		return best;
	}

	/**
	 * cuts the elliptical arc from (x0,y0) to (x,y) into straight pieces and adds their end points to the list. The
	 * center and the angles are computed from the end points as described in the SVG specification (implementation
	 * notes, conversion from endpoint to center parameterization)
	 */
	private static void sampleArc(double x0, double y0, ArcTo arcTo, double x, double y, int samples, java.util.List<Point2D> points) {
		var rx = Math.abs(arcTo.getRadiusX());
		var ry = Math.abs(arcTo.getRadiusY());
		if (rx == 0 || ry == 0 || (x0 == x && y0 == y)) {
			points.add(new Point2D(x, y));
			return;
		}
		var phi = Math.toRadians(arcTo.getXAxisRotation());
		var cos = Math.cos(phi);
		var sin = Math.sin(phi);
		var x1 = cos * (x0 - x) / 2 + sin * (y0 - y) / 2;
		var y1 = -sin * (x0 - x) / 2 + cos * (y0 - y) / 2;
		var lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
		if (lambda > 1) { // radii too small to reach: scale them up
			rx *= Math.sqrt(lambda);
			ry *= Math.sqrt(lambda);
		}
		var denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
		var factor = Math.sqrt(Math.max(0, (rx * rx * ry * ry - denominator) / denominator));
		if (arcTo.isLargeArcFlag() == arcTo.isSweepFlag())
			factor = -factor;
		var cx1 = factor * rx * y1 / ry;
		var cy1 = -factor * ry * x1 / rx;
		var cx = cos * cx1 - sin * cy1 + (x0 + x) / 2;
		var cy = sin * cx1 + cos * cy1 + (y0 + y) / 2;
		var ux = (x1 - cx1) / rx;
		var uy = (y1 - cy1) / ry;
		var vx = (-x1 - cx1) / rx;
		var vy = (-y1 - cy1) / ry;
		var start = Math.atan2(uy, ux);
		var extent = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
		if (!arcTo.isSweepFlag() && extent > 0)
			extent -= 2 * Math.PI;
		else if (arcTo.isSweepFlag() && extent < 0)
			extent += 2 * Math.PI;
		for (var i = 1; i < samples; i++) {
			var angle = start + extent * i / samples;
			points.add(new Point2D(cx + rx * cos * Math.cos(angle) - ry * sin * Math.sin(angle), cy + rx * sin * Math.cos(angle) + ry * cos * Math.sin(angle)));
		}
		points.add(new Point2D(x, y));
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean enabled) {
		Icebergs.enabled = enabled;
	}
}
