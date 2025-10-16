/*
 * LayoutRectangularCladogram.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.fx.phylo.embed;

import javafx.geometry.Point2D;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.util.HashMap;
import java.util.Map;

/**
 * compute the coordinates for a (early branching) cladogram
 * todo: make independent of jloda and move to jloda-phylogeny
 * Daniel Huson, 8.2025
 */
public class LayoutRectangularCladogram {
	public static void apply(PhyloTree network, Averaging averaging, Map<Node, Point2D> nodePointMap) {
		var longestPath = new HashMap<Node, Double>();
		longestPath.put(network.getRoot(), 0.0);
		computeLongestPathsRec(network, network.getRoot(), longestPath);
		for (var i = 0; i < 5; i++) {
			var changed = false;
			for (var e : network.edges()) {
				if (network.isTransferEdge(e)) {
					var longestPathSource = longestPath.get(e.getSource());
					var longestPathTarget = longestPath.get(e.getTarget());
					if (longestPathTarget > longestPathSource) {
						longestPath.put(e.getSource(), longestPathTarget);
						changed = true;
					}
				}
			}
			if (changed) {
				computeLongestPathsRec(network, network.getRoot(), longestPath);
			} else
				break;
		}
		try (var yCoord = network.newNodeDoubleArray()) {
			HeightAndAngles.apply(network, yCoord, averaging, true);
			var max = longestPath.values().stream().mapToDouble(d -> d).max().orElse(0.0);
			for (var v : network.nodes()) {
				if (v.isLeaf())
					nodePointMap.put(v, new Point2D(max, yCoord.get(v)));
				else
					nodePointMap.put(v, new Point2D(longestPath.get(v), yCoord.get(v)));
			}
		}
	}

	/**
	 * recursively compute the longest path to each node in the phylogeny
	 *
	 * @param network     the phylogeny
	 * @param v           the current node
	 * @param longestPath the longest path map
	 */
	public static void computeLongestPathsRec(PhyloTree network, Node v, HashMap<Node, Double> longestPath) {
		var vDist = longestPath.get(v);
		for (var f : v.outEdges()) {
			var w = f.getTarget();
			var wDist = (network.isTreeEdge(f) || network.isTransferAcceptorEdge(f) ? vDist + 1.0 : vDist);
			if (network.isReticulateEdge(f) && !network.isTransferEdge(f) && !network.isTransferAcceptorEdge(f)) {
				wDist += 0.5;
			}
			if (!longestPath.containsKey(w) || wDist > longestPath.get(w))
				longestPath.put(w, wDist);
			computeLongestPathsRec(network, w, longestPath);
		}
	}
}
