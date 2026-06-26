/*
 * DraggableUtils.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;

/**
 * Utilities for making nodes draggable.
 * Daniel Huson, 4.2022
 */
public class DraggableUtils {
	private static double mouseX;
	private static double mouseY;

	private static final EventHandler<? super MouseEvent> mousePressedHandler;
	private static final EventHandler<? super MouseEvent> mouseDraggedHandlerTranslate;
	private static final EventHandler<? super MouseEvent> mouseDraggedHandlerLayout;

	static {
		mousePressedHandler = e -> {
			mouseX = e.getSceneX();
			mouseY = e.getSceneY();
		};

		mouseDraggedHandlerTranslate = e -> {
			if (e.getSource() instanceof Node node) {
				var dx = e.getSceneX() - mouseX;
				var dy = e.getSceneY() - mouseY;

				node.setTranslateX(node.getTranslateX() + dx);
				node.setTranslateY(node.getTranslateY() + dy);

				mouseX = e.getSceneX();
				mouseY = e.getSceneY();

				e.consume();
			}
		};

		mouseDraggedHandlerLayout = e -> {
			if (e.getSource() instanceof Node node) {
				var dx = e.getSceneX() - mouseX;
				var dy = e.getSceneY() - mouseY;

				node.setLayoutX(node.getLayoutX() + dx);
				node.setLayoutY(node.getLayoutY() + dy);

				mouseX = e.getSceneX();
				mouseY = e.getSceneY();

				e.consume();
			}
		};
	}

	public static void setupDragMouseTranslate(Node node) {
		setupDragMouseTranslate(node, null);
	}

	public static void setupDragMouseLayout(Node node) {
		setupDragMouseLayout(node, null);
	}

	public static void setupDragMouseTranslate(Node node, Runnable runOnPressed) {
		node.setOnMousePressed(e -> {
			mousePressedHandler.handle(e);
			if (runOnPressed != null)
				runOnPressed.run();
		});

		node.setOnMouseDragged(mouseDraggedHandlerTranslate);
	}

	public static void setupDragMouseLayout(Node node, Runnable runOnPressed) {
		node.setOnMousePressed(e -> {
			mousePressedHandler.handle(e);
			if (runOnPressed != null)
				runOnPressed.run();
		});

		node.setOnMouseDragged(mouseDraggedHandlerLayout);
	}

	/**
	 * If the node is contained in an AnchorPane, makes it draggable while keeping it
	 * inside the current pane bounds.
	 *
	 * The method supports nodes anchored on the left or right, and/or top or bottom.
	 * If both left and right anchors are set, horizontal dragging is disabled because
	 * the node is stretched horizontally. Likewise, if both top and bottom anchors are
	 * set, vertical dragging is disabled.
	 *
	 * @param node node contained in an AnchorPane
	 */
	public static void makeDraggableInAnchorPane(Node node) {
		if (!(node.getParent() instanceof AnchorPane))
			return;

		var leftAnchor = AnchorPane.getLeftAnchor(node);
		var rightAnchor = AnchorPane.getRightAnchor(node);
		var topAnchor = AnchorPane.getTopAnchor(node);
		var bottomAnchor = AnchorPane.getBottomAnchor(node);

		var canMoveHorizontally = !(leftAnchor != null && rightAnchor != null);
		var canMoveVertically = !(topAnchor != null && bottomAnchor != null);

		if (!canMoveHorizontally && !canMoveVertically)
			return;

		final double[] mouseDown = new double[2];

		node.setOnMousePressed(e -> {
			mouseDown[0] = e.getScreenX();
			mouseDown[1] = e.getScreenY();

			node.setCursor(Cursor.CLOSED_HAND);

			e.consume();
		});

		node.setOnMouseDragged(e -> {
			if (!(node.getParent() instanceof AnchorPane pane))
				return;

			var deltaX = e.getScreenX() - mouseDown[0];
			var deltaY = e.getScreenY() - mouseDown[1];

			var nodeWidth = node.getBoundsInParent().getWidth();
			var nodeHeight = node.getBoundsInParent().getHeight();

			var paneWidth = pane.getWidth();
			var paneHeight = pane.getHeight();

			if (canMoveHorizontally) {
				var left = AnchorPane.getLeftAnchor(node);
				var right = AnchorPane.getRightAnchor(node);

				if (left != null) {
					var maxLeft = Math.max(0, paneWidth - nodeWidth);
					var newLeft = clamp(left + deltaX, 0, maxLeft);
					AnchorPane.setLeftAnchor(node, newLeft);
				} else if (right != null) {
					var maxRight = Math.max(0, paneWidth - nodeWidth);
					var newRight = clamp(right - deltaX, 0, maxRight);
					AnchorPane.setRightAnchor(node, newRight);
				}
			}

			if (canMoveVertically) {
				var top = AnchorPane.getTopAnchor(node);
				var bottom = AnchorPane.getBottomAnchor(node);

				if (top != null) {
					var maxTop = Math.max(0, paneHeight - nodeHeight);
					var newTop = clamp(top + deltaY, 0, maxTop);
					AnchorPane.setTopAnchor(node, newTop);
				} else if (bottom != null) {
					var maxBottom = Math.max(0, paneHeight - nodeHeight);
					var newBottom = clamp(bottom - deltaY, 0, maxBottom);
					AnchorPane.setBottomAnchor(node, newBottom);
				}
			}

			mouseDown[0] = e.getScreenX();
			mouseDown[1] = e.getScreenY();

			e.consume();
		});

		node.setOnMouseReleased(e -> {
			node.setCursor(Cursor.DEFAULT);
			e.consume();
		});
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}