/*
 * MarchingAnts.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

import java.util.ArrayList;

/**
 * marching ants animation
 * Daniel Huson, 8.2023
 */
public class MarchingAnts {
	/**
	 * apply a marching ants animation to the stroke of a shape
	 *
	 * @param shape                the shape
	 * @param rate                 the rate of ants per second
	 * @param strokeDash0          the first stroke-dash value
	 * @param strokeDash1          the second stroke-dash value
	 * @param strokeDashAdditional optional additional stroke-dash values
	 * @return the timeline
	 */
	public static Timeline apply(Shape shape, DoubleProperty rate, double strokeDash0, double strokeDash1, double... strokeDashAdditional) {
		var list = new ArrayList<Double>();
		list.add(strokeDash0);
		list.add(strokeDash1);
		for (var d : strokeDashAdditional)
			list.add(d);
		shape.getStrokeDashArray().setAll(list);
		var total = list.stream().mapToDouble(d -> d).sum();

		var timeline = new Timeline();

		var changeListener = new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> v, Number o, Number n) {
				timeline.stop();
				var seconds = (n.doubleValue() <= 0 ? 0.0 : 1.0 / n.doubleValue());
				var startKeyFrame = new KeyFrame(Duration.seconds(seconds), new KeyValue(shape.strokeDashOffsetProperty(), 0.0));
				var endKeyFrame = new KeyFrame(Duration.seconds(0), new KeyValue(shape.strokeDashOffsetProperty(), total));
				timeline.getKeyFrames().setAll(startKeyFrame, endKeyFrame);
				timeline.setCycleCount(1000);

				if (seconds > 0)
					timeline.play();
			}
		};
		rate.addListener(changeListener);
		changeListener.changed(rate, null, rate.get());
		return timeline;
	}
}
