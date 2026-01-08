/*
 * ScaleUtils.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.phylogeny.layout;


import java.util.Collection;
import java.util.Map;

/**
 * utilities for scaling stuff
 * Daniel Huson, 2.2025
 */
public class ScaleUtils {
	public static <N> void scaleToBox(Collection<N> nodes, Map<N, Double> xCoord, Map<N, Double> yCoord, double xMin, double xMax, double yMin, double yMax) {
		var pxMin = nodes.stream().mapToDouble(xCoord::get).min().orElse(0);
		var pxMax = nodes.stream().mapToDouble(xCoord::get).max().orElse(0);
		var pyMin = nodes.stream().mapToDouble(yCoord::get).min().orElse(0);
		var pyMax = nodes.stream().mapToDouble(yCoord::get).max().orElse(0);

		for (var v : nodes) {
			var px = xCoord.get(v);
			var py = yCoord.get(v);

			var x = xMin + (px - pxMin) * (xMax - xMin) / (pxMax - pxMin);

			var y = yMin + (py - pyMin) * (yMax - yMin) / (pyMax - pyMin);

			xCoord.put(v, x);
			yCoord.put(v, y);
		}
	}
}
