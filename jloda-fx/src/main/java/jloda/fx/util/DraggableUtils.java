/*
 * DraggableUtils.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.util.Pair;

/**
 * utilities for making nodes draggable
 * Daniel Huson, 4.2022
 */
public class DraggableUtils {
	private static double mouseX;
	private static double mouseY;

	private static final EventHandler<? super MouseEvent> mousePressedHander;
	private static final EventHandler<? super MouseEvent> mouseDraggedHandlerTranslate;
	private static final EventHandler<? super MouseEvent> mouseDraggedHandlerLayout;

	static {
		mousePressedHander = e -> {
			mouseX = e.getSceneX();
			mouseY = e.getSceneY();
		};
		mouseDraggedHandlerTranslate = e -> {
			if (e.getSource() instanceof Node aNode) {
				var dx = e.getSceneX() - mouseX;
				var dy = e.getSceneY() - mouseY;
				aNode.setTranslateX(aNode.getTranslateX() + dx);
				aNode.setTranslateY(aNode.getTranslateY() + dy);
				mouseX = e.getSceneX();
				mouseY = e.getSceneY();
				e.consume();
			}
		};
		mouseDraggedHandlerLayout = e -> {
			if (e.getSource() instanceof Node aNode) {
				var dx = e.getSceneX() - mouseX;
				var dy = e.getSceneY() - mouseY;
				aNode.setLayoutX(aNode.getLayoutX() + dx);
				aNode.setLayoutY(aNode.getLayoutY() + dy);
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
		if (runOnPressed == null)
			node.setOnMousePressed(mousePressedHander);
		else {
			node.setOnMousePressed(e -> {
				mousePressedHander.handle(e);
				runOnPressed.run();
			});
		}
		node.setOnMouseDragged(mouseDraggedHandlerTranslate);
	}

	public static void setupDragMouseLayout(Node node, Runnable runOnPressed) {
		if (runOnPressed == null)
			node.setOnMousePressed(mousePressedHander);
		else {
			node.setOnMousePressed(e -> {
				mousePressedHander.handle(e);
				runOnPressed.run();
			});
		}
		node.setOnMouseDragged(mouseDraggedHandlerLayout);
	}

	/**
	 * if node is contained in an anchor pane, makes it press-draggable
	 *
	 * @param node contained in anchor pane
	 */
	public static void makeDraggableInAnchorPane(Node node) {
		var right = AnchorPane.getRightAnchor(node);
		var left = AnchorPane.getLeftAnchor(node);
		var top = AnchorPane.getTopAnchor(node);
		var bottom = AnchorPane.getBottomAnchor(node);

		if ((right == null || left == null) && (top == null || bottom == null)) {
			final var mouseDown = new Pair<Double, Double>();

			node.setOnMousePressed((e -> {
				mouseDown.set(e.getScreenX(), e.getScreenY());
				node.setCursor(Cursor.CLOSED_HAND);
				e.consume();
			}));

			node.setOnMouseDragged((e -> {
				double deltaX = e.getScreenX() - mouseDown.getFirst();
				double deltaY = e.getScreenY() - mouseDown.getSecond();
				if (right != null)
					AnchorPane.setRightAnchor(node, AnchorPane.getRightAnchor(node) - deltaX);
				if (left != null)
					AnchorPane.setLeftAnchor(node, AnchorPane.getLeftAnchor(node) + deltaX);
				if (top != null)
					AnchorPane.setTopAnchor(node, AnchorPane.getTopAnchor(node) + deltaY);
				if (bottom != null)
					AnchorPane.setBottomAnchor(node, AnchorPane.getBottomAnchor(node) - deltaY);
				mouseDown.set(e.getScreenX(), e.getScreenY());
				e.consume();
			}));

			node.setOnMouseReleased(e -> node.setCursor(Cursor.DEFAULT));
		}
	}
}
