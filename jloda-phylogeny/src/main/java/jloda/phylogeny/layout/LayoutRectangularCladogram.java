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

package jloda.phylogeny.layout;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * compute the coordinates for a (early branching) cladogram
 * Daniel Huson, 8.2025
 */
public class LayoutRectangularCladogram {

	/**
	 * compute coordinates for a rectangular cladogram
	 *
	 * @param root         the root node
	 * @param nodes        all nodes
	 * @param edges        all edges
	 * @param lsaChildren  children in LSA tree (or null, if phylogeny is tree)
	 * @param outEdges     out edges
	 * @param source       source node
	 * @param target       target node
	 * @param edgeType     edge type (or null, if phylogeny is tree)
	 * @param averaging    child averaging
	 * @param nodePointMap calculated coordinates
	 * @param <Node>       node
	 * @param <Edge>       edge
	 */
	public static <Node, Edge> void apply(Node root, List<Node> nodes, List<Edge> edges,
										  Function<Node, List<Node>> lsaChildren,
										  Function<Node, List<Edge>> outEdges,
										  Function<Edge, Node> source, Function<Edge, Node> target, Function<Edge, EdgeType> edgeType,
										  Averaging averaging, Map<Node, Point2D> nodePointMap) {
		var longestPath = new HashMap<Node, Double>();
		longestPath.put(root, 0.0);
		computeLongestPathsRec(root, outEdges, target, edgeType, longestPath);
		for (var i = 0; i < 5; i++) {
			var changed = false;
			for (var e : edges) {
				var type = (edgeType == null ? EdgeType.tree : edgeType.apply(e));
				if (type == EdgeType.transfer) {
					var longestPathSource = longestPath.get(source.apply(e));
					var longestPathTarget = longestPath.get(target.apply(e));
					if (longestPathTarget > longestPathSource) {
						longestPath.put(source.apply(e), longestPathTarget);
						changed = true;
					}
				}
			}
			if (changed) {
				computeLongestPathsRec(root, outEdges, target, edgeType, longestPath);
			} else
				break;
		}
		var yCoord = new HashMap<Node, Double>();

		Function<Node, List<Node>> children = v -> outEdges.apply(v).stream().map(target).toList();
		HeightAndAngles.apply(root, children, lsaChildren, yCoord, averaging, true);
		var max = longestPath.values().stream().mapToDouble(d -> d).max().orElse(0.0);
		for (var v : nodes) {
			if (outEdges.apply(v).isEmpty())
				nodePointMap.put(v, new Point2D(max, yCoord.get(v)));
			else
				nodePointMap.put(v, new Point2D(longestPath.get(v), yCoord.get(v)));
		}
	}

	/**
	 * recursively compute the longest path to each node in the phylogeny
	 *
	 * @param v           current node
	 * @param outEdges    out edges for v
	 * @param target      target node of edge
	 * @param edgeType    type of edge
	 * @param longestPath maps each node to its longest path
	 * @param <Node>      nodes
	 * @param <Edge>      edges
	 */
	public static <Node, Edge> void computeLongestPathsRec(Node v, Function<Node, List<Edge>> outEdges, Function<Edge, Node> target, Function<Edge, EdgeType> edgeType, HashMap<Node, Double> longestPath) {
		var vDist = longestPath.get(v);
		for (var f : outEdges.apply(v)) {
			var type = (edgeType == null ? EdgeType.tree : edgeType.apply(f));
			var w = target.apply(f);
			var wDist = (type == EdgeType.tree || type == EdgeType.transferAcceptor ? vDist + 1.0 : vDist);
			if (type == EdgeType.combining) {
				wDist += 0.5;
			}
			if (!longestPath.containsKey(w) || wDist > longestPath.get(w))
				longestPath.put(w, wDist);
			computeLongestPathsRec(w, outEdges, target, edgeType, longestPath);
		}
	}
}
