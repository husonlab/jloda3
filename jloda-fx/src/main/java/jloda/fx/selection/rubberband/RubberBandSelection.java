/*
 * RubberBandSelection.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.selection.rubberband;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RubberBandSelection {

    private static ExecutorService service;

    @FunctionalInterface
    public interface Handler {
        /**
         * Called when a rubber-band selection completes.
         * @param rectangle selection rectangle in SCENE coordinates
         * @param extendSelection true if shift pressed
         * @param service executor you can use for non-FX work
         */
        void handle(Rectangle2D rectangle, boolean extendSelection, ExecutorService service);
    }

    private final Node area;          // the node you draw over (Canvas, Pane, etc.)
    private final Rectangle band;     // the visible rubber band
    private final Handler handler;

    private final BooleanProperty inDrag = new SimpleBooleanProperty(false);
    private final BooleanProperty inRubberBand = new SimpleBooleanProperty(false);

    private double anchorX, anchorY;

    public RubberBandSelection(Node area, Handler handler) {
        this.area = area;
        this.handler = handler;

        if (service == null) {
            service = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "rubberband-worker");
                t.setDaemon(true);
                return t;
            });
        }

        // The band is added as a child of the same parent as `area` so it sits above it.
        // If `area` is a Parent, you could alternatively add to an overlay group.
        band = new Rectangle(0, 0, 0, 0);
        band.setManaged(false);
        band.setMouseTransparent(true);
        band.setStroke(Color.web("#1a73e8"));          // outline
        band.setFill(Color.web("#1a73e8", 0.15));      // translucent fill
        band.setVisible(false);

        // Ensure band is present above the area
        Platform.runLater(() -> {
            var p = area.getParent();
            if (p != null) {
                if (!p.getChildrenUnmodifiable().contains(band) && p instanceof javafx.scene.layout.Pane pane) {
                    pane.getChildren().add(band);
                    // keep band above
                    band.toFront();
                }
            }
        });

        // Prevent scroll-wheel jitter while drawing (esp. on trackpads)
        area.addEventFilter(ScrollEvent.ANY, e -> {
            if (inDrag.get()) e.consume();
        });

        // Start
        area.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            inDrag.set(true);
            inRubberBand.set(false);

            // anchor in LOCAL coords of `area`, clamped into bounds
            Bounds b = getLocalBounds(area);
            anchorX = clamp(e.getX(), b.getMinX(), b.getMaxX());
            anchorY = clamp(e.getY(), b.getMinY(), b.getMaxY());

            // place band in PARENT coordinates (so it sits visually over the area)
            Point2D startInParent = area.localToParent(anchorX, anchorY);
            band.setX(startInParent.getX());
            band.setY(startInParent.getY());
            band.setWidth(0);
            band.setHeight(0);
            band.setVisible(true);
            band.toFront();
            area.setCursor(Cursor.CROSSHAIR);
            e.consume();
        });

        // Drag
        area.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!inDrag.get()) return;

            Bounds b = getLocalBounds(area);

            // clamp current point so we never go past the left/top (or right/bottom) edges
            double xLocal = clamp(e.getX(), b.getMinX(), b.getMaxX());
            double yLocal = clamp(e.getY(), b.getMinY(), b.getMaxY());

            // convert both anchor and current to parent space (band lives in parent)
            Point2D a = area.localToParent(anchorX, anchorY);
            Point2D c = area.localToParent(xLocal, yLocal);

            double minX = Math.min(a.getX(), c.getX());
            double minY = Math.min(a.getY(), c.getY());
            double maxX = Math.max(a.getX(), c.getX());
            double maxY = Math.max(a.getY(), c.getY());

            band.setX(minX);
            band.setY(minY);
            band.setWidth(maxX - minX);
            band.setHeight(maxY - minY);

            // activate only when visible size > 0
            inRubberBand.set(band.getWidth() > 0 && band.getHeight() > 0);
            e.consume();
        });

        // End
        area.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (!inDrag.get()) return;

            if (inRubberBand.get() && band.getWidth() > 0 && band.getHeight() > 0) {
                // Report in SCENE coordinates (as your existing handler expects)
                Point2D minScene = band.localToScene(0, 0);
                Point2D maxScene = band.localToScene(band.getWidth(), band.getHeight());
                Rectangle2D sceneRect = new Rectangle2D(
                        Math.min(minScene.getX(), maxScene.getX()),
                        Math.min(minScene.getY(), maxScene.getY()),
                        Math.abs(maxScene.getX() - minScene.getX()),
                        Math.abs(maxScene.getY() - minScene.getY())
                );
                if (handler != null) {
                    handler.handle(sceneRect, e.isShiftDown(), service);
                }
            }

            band.setVisible(false);
            band.setWidth(0);
            band.setHeight(0);
            inRubberBand.set(false);
            inDrag.set(false);
            area.setCursor(Cursor.DEFAULT);
            e.consume();
        });
    }

    // If the area is a Region (Pane, Canvas wrapper in a Pane, etc.), prefer its current width/height.
    // Otherwise fall back to its layout bounds.
    private static Bounds getLocalBounds(Node n) {
        if (n instanceof Region r) {
            double w = Math.max(0, r.getWidth());
            double h = Math.max(0, r.getHeight());
            // Regions report 0 until laid out; fallback if needed:
            if (w > 0 && h > 0) {
                return new javafx.geometry.BoundingBox(0, 0, w, h);
            }
        }
        return n.getLayoutBounds();
    }

    private static double clamp(double v, double min, double max) {
        return (v < min) ? min : Math.min(v, max);
    }

    public BooleanProperty inRubberBandProperty() {
        return inRubberBand;
    }
}