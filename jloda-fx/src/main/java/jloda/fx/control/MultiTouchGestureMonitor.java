package jloda.fx.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.Pane;
import jloda.fx.util.ProgramProperties;

import java.util.HashSet;

/**
 * setup multi-touch gesture monitoring
 * Daniel Huson, 1.2026
 */
public class MultiTouchGestureMonitor {
	/**
	 * create a boolean property that tracks whether a multi-touch gesture is currently in progress
	 *
	 * @param pane the pane
	 * @return boolean property
	 */
	public static BooleanProperty setup(ScrollPane scrollPane, Pane pane) {
		var suppressSingleTouchPanning = (ProgramProperties.isIOS() || ProgramProperties.isAndroid());
		var multiTouchGestureInProgress = new SimpleBooleanProperty(false);

		// this code ensures that panning requires at least two touch points on a mobile device:
		var activeTouches = new HashSet<Integer>();

		if (pane != null) {
			pane.addEventFilter(TouchEvent.TOUCH_PRESSED, e -> {
				activeTouches.add(e.getTouchPoint().getId());
				if (activeTouches.size() >= 2) {
					multiTouchGestureInProgress.set(true);
				}
				if (multiTouchGestureInProgress.get()) {
					e.consume();
				}
			});

			pane.addEventFilter(TouchEvent.TOUCH_MOVED, e -> {
				// TouchPoint ids are stable, but adding again is harmless
				activeTouches.add(e.getTouchPoint().getId());
				if (activeTouches.size() >= 2) {
					multiTouchGestureInProgress.set(true);
				}
				if (multiTouchGestureInProgress.get()) {
					e.consume();
				}
			});

			pane.addEventFilter(TouchEvent.TOUCH_RELEASED, e -> {
				activeTouches.remove(e.getTouchPoint().getId());
				// Keep consuming until all touches are gone
				boolean stillAnyTouchDown = !activeTouches.isEmpty();
				if (multiTouchGestureInProgress.get()) {
					e.consume();
					if (!stillAnyTouchDown) {
						multiTouchGestureInProgress.set(false);
					}
				}
			});
		}

		if (suppressSingleTouchPanning) {
			if (scrollPane != null) {
				multiTouchGestureInProgress.addListener((v, o, n) -> {
					scrollPane.setPannable(n);
				});
				scrollPane.addEventFilter(ScrollEvent.ANY, e -> {
					if (!multiTouchGestureInProgress.get())
						e.consume();
				});
				scrollPane.setOnMouseDragged(e -> {
					if (!multiTouchGestureInProgress.get())
						e.consume();
				});
			}
			if (pane != null) {
				pane.setOnMouseDragged(e -> {
					if (!multiTouchGestureInProgress.get())
						e.consume();
				});
			}
		}


		return multiTouchGestureInProgress;
	}

}
