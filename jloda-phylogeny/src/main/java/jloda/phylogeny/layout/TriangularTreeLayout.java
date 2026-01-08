/*
 *  TriangularTreeLayout.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.phylogeny.layout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static jloda.phylogeny.utils.GraphUtils.postOrderTraversal;

/**
 * computes a triangular layout for a tree
 * Daniel Huson, 12.2021, 1.2026
 */
public class TriangularTreeLayout {
	/**
	 * applies algorithm
	 */
	public static <Node> void apply(Node root, Function<Node, List<Node>> children, Function<Node, List<Node>> lsaChildren, Map<Node, Point2D> nodePointMap) {
		record FirstLast<Node>(Node first, Node last) {
		}

		var firstLastLeafBelowMap = new HashMap<Node, FirstLast<Node>>();
		nodePointMap.put(root, new Point2D(0.0, 0.0));
		// compute all y-coordinates:
		{
			var counter = new int[]{0};

			postOrderTraversal(root, lsaChildren, v -> {
				var below = children.apply(v);
				var lsaBelow = lsaChildren.apply(v);
				if (below.isEmpty() || lsaBelow.isEmpty()) {
					nodePointMap.put(v, new Point2D(0.0, ++counter[0]));
					firstLastLeafBelowMap.put(v, new FirstLast<>(v, v));
				} else {
					var min = Double.MAX_VALUE;
					var max = Double.MIN_VALUE;
					Node firstLeafBelow = null;
					Node lastLeafBelow = null;
					for (var w : lsaBelow) {
						{
							var fLeaf = firstLastLeafBelowMap.get(w).first();
							var y = nodePointMap.get(fLeaf).y();
							if (y < min) {
								min = y;
								firstLeafBelow = fLeaf;
							}
						}
						{
							var lLeaf = firstLastLeafBelowMap.get(w).last();
							var y = nodePointMap.get(lLeaf).y();
							if (y > max) {
								max = y;
								lastLeafBelow = lLeaf;
							}
						}
					}
					var y = 0.5 * (min + max);
					var x = -(max - min);
					nodePointMap.put(v, new Point2D(x, y));
					firstLastLeafBelowMap.put(v, new FirstLast<>(firstLeafBelow, lastLeafBelow));
				}
			});
		}
	}
}
