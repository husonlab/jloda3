/*
 * CopyLineShape.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.scene.shape.*;

/**
 * method for computing a  copy of line shape, whose geometric properties are all bidirectionally bound to the source line shape
 * Daniel Huson, 8.203
 */
public class CopyLineShape {
	/**
	 * creates a copy of line shape, whose geometric properties are all bidirectionally bound to the source line shape
	 *
	 * @param lineShape the source line shape
	 * @return the copy
	 */
	public static Shape apply(Shape lineShape) {
		if (lineShape instanceof Line line) {
			var copy = new Line();
			copy.startXProperty().bindBidirectional(line.startXProperty());
			copy.startYProperty().bindBidirectional(line.startYProperty());
			copy.endXProperty().bindBidirectional(line.endXProperty());
			copy.endYProperty().bindBidirectional(line.endYProperty());
			return copy;
		} else if (lineShape instanceof QuadCurve curve) {
			var copy = new QuadCurve();
			copy.startXProperty().bindBidirectional(curve.startXProperty());
			copy.startYProperty().bindBidirectional(curve.startYProperty());
			copy.controlXProperty().bindBidirectional(curve.controlXProperty());
			copy.controlYProperty().bindBidirectional(curve.controlYProperty());
			copy.endXProperty().bindBidirectional(curve.endXProperty());
			copy.endYProperty().bindBidirectional(curve.endYProperty());
			return copy;
		} else if (lineShape instanceof CubicCurve curve) {
			var copy = new CubicCurve();
			copy.startXProperty().bindBidirectional(curve.startXProperty());
			copy.startYProperty().bindBidirectional(curve.startYProperty());
			copy.controlX1Property().bindBidirectional(curve.controlX1Property());
			copy.controlY1Property().bindBidirectional(curve.controlY1Property());
			copy.controlX2Property().bindBidirectional(curve.controlX2Property());
			copy.controlY2Property().bindBidirectional(curve.controlY2Property());
			copy.endXProperty().bindBidirectional(curve.endXProperty());
			copy.endYProperty().bindBidirectional(curve.endYProperty());
			return copy;
		} else if (lineShape instanceof Path path) {
			var copy = new Path();
			for (var element : path.getElements()) {
				if (element instanceof MoveTo src) {
					var elementCopy = new MoveTo();
					elementCopy.xProperty().bindBidirectional(src.xProperty());
					elementCopy.yProperty().bindBidirectional(src.yProperty());
					copy.getElements().add(elementCopy);
				} else if (element instanceof LineTo src) {
					var elementCopy = new LineTo();
					elementCopy.xProperty().bindBidirectional(src.xProperty());
					elementCopy.yProperty().bindBidirectional(src.yProperty());
					copy.getElements().add(elementCopy);
				} else if (element instanceof HLineTo src) {
					var elementCopy = new HLineTo();
					elementCopy.xProperty().bindBidirectional(src.xProperty());
					copy.getElements().add(elementCopy);
				} else if (element instanceof VLineTo src) {
					var elementCopy = new VLineTo();
					elementCopy.yProperty().bindBidirectional(src.yProperty());
					copy.getElements().add(elementCopy);
				} else if (element instanceof QuadCurveTo src) {
					var elementCopy = new QuadCurveTo();
					elementCopy.xProperty().bindBidirectional(src.xProperty());
					elementCopy.yProperty().bindBidirectional(src.yProperty());
					elementCopy.controlXProperty().bindBidirectional(src.controlXProperty());
					elementCopy.controlYProperty().bindBidirectional(src.controlYProperty());
					copy.getElements().add(elementCopy);
				} else if (element instanceof CubicCurveTo src) {
					var elementCopy = new CubicCurveTo();
					elementCopy.xProperty().bindBidirectional(src.xProperty());
					elementCopy.yProperty().bindBidirectional(src.yProperty());
					elementCopy.controlX1Property().bindBidirectional(src.controlX1Property());
					elementCopy.controlY1Property().bindBidirectional(src.controlY1Property());
					elementCopy.controlX2Property().bindBidirectional(src.controlX2Property());
					elementCopy.controlY2Property().bindBidirectional(src.controlY2Property());
					copy.getElements().add(elementCopy);
					copy.setFill(lineShape.getFill());
				} else if (element instanceof ArcTo src) {
					var elementCopy = new ArcTo();
					elementCopy.xProperty().bindBidirectional(src.xProperty());
					elementCopy.yProperty().bindBidirectional(src.yProperty());
					elementCopy.radiusXProperty().bindBidirectional(src.radiusXProperty());
					elementCopy.radiusYProperty().bindBidirectional(src.radiusYProperty());
					elementCopy.sweepFlagProperty().bindBidirectional(src.sweepFlagProperty());
					elementCopy.largeArcFlagProperty().bindBidirectional(src.largeArcFlagProperty());
					copy.getElements().add(elementCopy);
				} else if (element instanceof ClosePath) {
					copy.getElements().add(new ClosePath());
				}
			}
			return copy;
		} else return null;
	}
}
