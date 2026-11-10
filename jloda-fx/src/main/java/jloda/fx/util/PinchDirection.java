/*
 *  PinchDirection.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.scene.Node;
import javafx.scene.input.TouchEvent;
import javafx.scene.input.TouchPoint;
import javafx.scene.input.ZoomEvent;

/**
 * determines whether a pinch gesture on a touch screen is horizontal, vertical or diagonal.
 * A zoom event only reports a zoom factor, not the direction in which the two fingers move. This class watches the
 * touch points on a node and classifies the line between the first two fingers: if it is within 30 degrees of the
 * horizontal, the pinch is horizontal, if it is within 30 degrees of the vertical, the pinch is vertical, otherwise
 * it applies to both directions. The direction is fixed when a zoom gesture starts and kept until it finishes, so
 * that the fingers can wander during the gesture. Without touch information (for example a pinch on a trackpad),
 * the direction is {@link Direction#Both}.
 * Daniel Huson, 11.2026
 */
public class PinchDirection {
	public enum Direction {
		Horizontal, Vertical, Both;

		public boolean isHorizontal() {
			return this != Vertical;
		}

		public boolean isVertical() {
			return this != Horizontal;
		}
	}

	private Direction latest = Direction.Both; // from the most recent touch event with two fingers down
	private Direction locked = null; // direction of the zoom gesture in progress

	private PinchDirection() {
	}

	/**
	 * start watching touch and zoom events on a node
	 *
	 * @param node the node that receives the zoom events
	 * @return the pinch direction tracker
	 */
	public static PinchDirection install(Node node) {
		var pinchDirection = new PinchDirection();
		node.addEventFilter(TouchEvent.ANY, pinchDirection::update);
		node.addEventFilter(ZoomEvent.ZOOM_STARTED, e -> pinchDirection.locked = pinchDirection.latest);
		node.addEventFilter(ZoomEvent.ZOOM_FINISHED, e -> pinchDirection.locked = null);
		return pinchDirection;
	}

	private void update(TouchEvent event) {
		TouchPoint first = null;
		TouchPoint second = null;
		for (var point : event.getTouchPoints()) {
			if (point.getState() != TouchPoint.State.RELEASED) {
				if (first == null)
					first = point;
				else if (second == null)
					second = point;
			}
		}
		if (first != null && second != null) {
			var dx = Math.abs(first.getSceneX() - second.getSceneX());
			var dy = Math.abs(first.getSceneY() - second.getSceneY());
			var angle = Math.toDegrees(Math.atan2(dy, dx)); // 0: fingers side by side, 90: one above the other
			latest = (angle <= 30 ? Direction.Horizontal : (angle >= 60 ? Direction.Vertical : Direction.Both));
		} else {
			latest = Direction.Both;
			locked = null;
		}
	}

	/**
	 * the direction of the pinch gesture in progress
	 */
	public Direction getDirection() {
		return locked != null ? locked : latest;
	}
}
