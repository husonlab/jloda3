/*
 * ComputeOrthogonalDisplacement.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.phylogeny.dolayout;


import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * computes the orthogonal displacement for a network
 */
public class ComputeOrthogonalDisplacement {

	public static <Node, Edge> double apply(Iterable<Node> nodes, Iterable<Edge> edges, Function<Edge, Node> getSource, Function<Edge, Node> getTarget, Predicate<Edge> useEdge, ToDoubleFunction<Node> nodeHeight) {
		var min = Double.MAX_VALUE;
		var max = Double.MIN_VALUE;
		for (var v : nodes) {
			min = Math.min(min, nodeHeight.applyAsDouble(v));
			max = Math.max(max, nodeHeight.applyAsDouble(v));
		}
		var height = max - min;
		var displacement = 0.0;
		for (var e : edges) {
			if (useEdge.test(e)) {
				displacement += Math.abs(nodeHeight.applyAsDouble(getSource.apply(e)) - nodeHeight.applyAsDouble(getTarget.apply(e)));
			}
		}
		return (height == 0 ? height : displacement / height);
	}
}
