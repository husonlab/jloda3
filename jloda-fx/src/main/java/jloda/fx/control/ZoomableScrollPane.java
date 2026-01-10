/*
 * ZoomableScrollPane.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.control;

import javafx.beans.property.*;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/**
 * zoomable scroll pane that zooms to point under mouse
 * Adapted from: https://stackoverflow.com/questions/39827911/javafx-8-scaling-zooming-scrollpane-relative-to-mouse-position
 * Daniel Huson, 1.2018
 */
public class ZoomableScrollPane extends ScrollPane {
	private double mouseScrollZoomFactor = 1.01; // 1%
	private final BooleanProperty lockAspectRatio = new SimpleBooleanProperty(false);
	private final BooleanProperty allowZoom = new SimpleBooleanProperty(true);

	private final BooleanProperty requireShiftOrControlToZoom = new SimpleBooleanProperty(false);

	private Node content;
	private final Group zoomNode;
	private final StackPane outerNode;

	private final DoubleProperty zoomX = new SimpleDoubleProperty(this, "zoomX", 1.0);
	private final DoubleProperty zoomY = new SimpleDoubleProperty(this, "zoomY", 1.0);

	private double zoomFactorX = 1;
	private double zoomFactorY = 1;

	public static boolean zoomByScroll = true;

	private final ObjectProperty<Runnable> updateScaleMethod;

	/**
	 * constructor
	 */
	public ZoomableScrollPane(Node content) {
		super();
		this.content = content;
		zoomNode = new Group();
		if (content != null)
			zoomNode.getChildren().add(content);
		outerNode = createOuterNode();
		outerNode.setAlignment(javafx.geometry.Pos.TOP_LEFT);

		outerNode.getChildren().add(zoomNode);
		setContent(outerNode);

		updateScaleMethod = new SimpleObjectProperty<>(() -> {
			ZoomableScrollPane.this.content.setScaleX(getZoomX());
			ZoomableScrollPane.this.content.setScaleY(getZoomY());
		});

		// if setContent() is used to update content, then adjust accordingly:
		contentProperty().addListener((c, o, n) -> {
			if (n != outerNode) {
				ZoomableScrollPane.this.content = n;
				zoomNode.getChildren().clear();
				if (n != null)
					zoomNode.getChildren().add(n);
				setContent(outerNode); // scroll pane scrolls outer node
			}
		});
	}

	/**
	 * this returns the node that is zoomable. This method should be used in place of ScrollPane.getContent()
	 *
	 * @return zoomable content
	 */
	public Node getContentNode() {
		return content;
	}

	public Pane getOuterNode() {
		return outerNode;
	}

	public double getZoomFactorX() {
		return zoomFactorX;
	}

	public double getZoomFactorY() {
		return zoomFactorY;
	}

	public double getZoomX() {
		return zoomX.get();
	}

	public double getZoomY() {
		return zoomY.get();
	}

	public ReadOnlyDoubleProperty zoomXProperty() {
		return zoomX;
	}

	public ReadOnlyDoubleProperty zoomYProperty() {
		return zoomY;
	}

	private StackPane createOuterNode() {
		final StackPane outerNode = new StackPane();

		if (zoomByScroll)
			outerNode.setOnScroll(e -> {
				if (ZoomableScrollPane.this.isAllowZoom() && (!isRequireShiftOrControlToZoom() || e.isShiftDown() || e.isControlDown())) {
					e.consume();
					final double factorX;
					final double factorY;

					if ((Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY()))) {
						factorX = (e.getDeltaX() > 0 ? mouseScrollZoomFactor : 1 / mouseScrollZoomFactor);
						factorY = 1;
					} else {
						factorX = 1;
						factorY = (e.getDeltaY() > 0 ? mouseScrollZoomFactor : 1 / mouseScrollZoomFactor);
					}
					ZoomableScrollPane.this.doZoom(factorX, factorY, new Point2D(e.getX(), e.getY()));
				}
			});
		else { // zoom by zoom rather than by scroll
			outerNode.setOnZoom(e -> {
				if (ZoomableScrollPane.this.isAllowZoom())
					ZoomableScrollPane.this.doZoom(e.getZoomFactor(), e.getZoomFactor(), new Point2D(e.getX(), e.getY()));
			});
		}

		return outerNode;
	}

	public void updateScale() {
		updateScaleMethod.get().run();
	}

	public Runnable getUpdateScaleMethod() {
		return updateScaleMethod.get();
	}

	public ObjectProperty<Runnable> updateScaleMethodProperty() {
		return updateScaleMethod;
	}

	public void setUpdateScaleMethod(Runnable updateScaleMethod) {
		this.updateScaleMethod.set(updateScaleMethod);
	}

	private void doZoom(double factorX, double factorY, Point2D mousePointInOuterLocal) {
		if (lockAspectRatio.get()) {
			if (factorX != 1.0)
				//noinspection SuspiciousNameCombination
				factorY = factorX;
			else
				//noinspection SuspiciousNameCombination
				factorX = factorY;
		}

		// Mouse in SCENE coords (this is the stable reference frame):
		final var mouseScene = outerNode.localToScene(mousePointInOuterLocal);

		// The anchor point in CONTENT local coords BEFORE scaling:
		final var anchorInContentLocal = content.sceneToLocal(mouseScene);

		// Current scroll offsets in pixels, using the ScrollPane's content node sizes.
		final var viewportBounds = getViewportBounds();
		final var contentBounds0 = outerNode.getLayoutBounds();

		final var maxX0 = contentBounds0.getWidth() - viewportBounds.getWidth();
		final var maxY0 = contentBounds0.getHeight() - viewportBounds.getHeight();

		final var pixelOffsetX0 = (maxX0 > 0 ? getHvalue() * maxX0 : 0);
		final var pixelOffsetY0 = (maxY0 > 0 ? getVvalue() * maxY0 : 0);

		// must do this for external scaling methods
		zoomFactorX = factorX;
		zoomFactorY = factorY;

		// Apply zoom:
		zoomX.set(getZoomX() * factorX);
		zoomY.set(getZoomY() * factorY);
		updateScale();

		// Force the ScrollPane/skin to recompute sizes/positions:
		applyCss();
		layout();

		// Where does the same anchor point end up AFTER scaling (in scene coords)?
		final var anchorSceneAfter = content.localToScene(anchorInContentLocal);

		// How far did it drift away from the mouse (in scene pixels)?
		final var driftScene = anchorSceneAfter.subtract(mouseScene);

		// Convert drift to scroll pixel adjustments:
		final var contentBounds1 = outerNode.getLayoutBounds();
		final var maxX1 = contentBounds1.getWidth() - viewportBounds.getWidth();
		final var maxY1 = contentBounds1.getHeight() - viewportBounds.getHeight();

		final var pixelOffsetX1 = pixelOffsetX0 + driftScene.getX();
		final var pixelOffsetY1 = pixelOffsetY0 + driftScene.getY();

		if (maxX1 > 0) setHvalue(pixelOffsetX1 / maxX1);
		if (maxY1 > 0) setVvalue(pixelOffsetY1 / maxY1);
	}

	public boolean isLockAspectRatio() {
		return lockAspectRatio.get();
	}

	public BooleanProperty lockAspectRatioProperty() {
		return lockAspectRatio;
	}

	public void setLockAspectRatio(boolean lockAspectRatio) {
		this.lockAspectRatio.set(lockAspectRatio);
	}

	public boolean isAllowZoom() {
		return allowZoom.get();
	}

	public BooleanProperty allowZoomProperty() {
		return allowZoom;
	}

	public void setAllowZoom(boolean allowZoom) {
		this.allowZoom.set(allowZoom);
	}

	public void zoomBy(double zoomFactorX, double zoomFactorY) {
		if (isAllowZoom()) {
			doZoom(zoomFactorX, zoomFactorY, new Point2D(0.5 * getWidth(), 0.5 * getHeight())); // zoom to center
			updateScale();
		}
	}

	public void resetZoom() {
		zoomFactorX = 1.0 / getZoomX();
		zoomFactorY = 1.0 / getZoomY();
		zoomX.set(1.0);
		zoomY.set(1.0);
		updateScale();
	}

	public Group getContentGroup() {
		return zoomNode;
	}

	/**
	 * ensure the node is showing
	 */
	public void ensureVisible(Node node) {
		if (node != null && getContent().getScene() != null) {
			final Bounds viewportBounds = getViewportBounds();
			final Bounds contentBounds = getContent().localToScene(getContent().getBoundsInLocal());
			Bounds nodeBounds = node.localToScene(node.getBoundsInLocal());

			// this adjusts for the fact that the scrollpane might not fill out the whole scene:
			final double offsetH = (getContent().getScene().getWidth() - viewportBounds.getWidth());
			final double offsetV = (getContent().getScene().getHeight() - viewportBounds.getHeight());
			nodeBounds = new BoundingBox(nodeBounds.getMinX() - offsetH, nodeBounds.getMinY() - offsetV, nodeBounds.getWidth(), nodeBounds.getHeight());

			if (nodeBounds.getMaxX() < 0) {
				final double hValueDelta = (nodeBounds.getMinX() - viewportBounds.getWidth()) / contentBounds.getWidth();
				setHvalue(getHvalue() + hValueDelta);
			} else if (nodeBounds.getMinX() > viewportBounds.getWidth()) {
				final double hValueDelta = (nodeBounds.getMinX() + viewportBounds.getWidth()) / contentBounds.getWidth();
				setHvalue(getHvalue() + hValueDelta);
			}

			if (nodeBounds.getMaxY() < 0) {
				final double vValueDelta = (nodeBounds.getMinY() - viewportBounds.getHeight()) / contentBounds.getHeight();
				setVvalue(getVvalue() + vValueDelta);
			} else if (nodeBounds.getMinY() > viewportBounds.getHeight()) {
				final double vValueDelta = (nodeBounds.getMinY() + viewportBounds.getHeight()) / contentBounds.getHeight();
				setVvalue(getVvalue() + vValueDelta);
			}
		}
	}

	public boolean isRequireShiftOrControlToZoom() {
		return requireShiftOrControlToZoom.get();
	}

	public BooleanProperty requireShiftOrControlToZoomProperty() {
		return requireShiftOrControlToZoom;
	}

	public void setRequireShiftOrControlToZoom(boolean requireShiftOrControlToZoom) {
		this.requireShiftOrControlToZoom.set(requireShiftOrControlToZoom);
	}

	public double getMouseScrollZoomFactor() {
		return mouseScrollZoomFactor;
	}

	public void setMouseScrollZoomFactor(double mouseScrollZoomFactor) {
		this.mouseScrollZoomFactor = mouseScrollZoomFactor;
	}

	public ScrollBar getHorizontalScrollBar() {
		for (var node : lookupAll(".scroll-bar")) {
			if (node instanceof ScrollBar) {
				var scrollBar = (ScrollBar) node;
				if (scrollBar.getOrientation() == Orientation.HORIZONTAL)
					return scrollBar;
			}
		}
		return null;
	}

	public ScrollBar getVerticalScrollBar() {
		for (var node : lookupAll(".scroll-bar")) {
			if (node instanceof ScrollBar) {
				var scrollBar = (ScrollBar) node;
				if (scrollBar.getOrientation() == Orientation.VERTICAL)
					return scrollBar;
			}
		}
		return null;
	}


}