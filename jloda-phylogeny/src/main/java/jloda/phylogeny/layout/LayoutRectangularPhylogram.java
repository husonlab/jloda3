/*
 * LayoutRectangularPhylogram.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * compute the coordinates for a phylogram
 * Daniel Huson, 8.2025, 1.2026
 */
public class LayoutRectangularPhylogram {
	public static double reticulationOffsetPercent = 10.0;

	/**
	 * compute coordinates for a rectangular phylogram
	 *
	 * @param root         the root node
	 * @param nodes        all nodes
	 * @param edges        all edges
	 * @param inEdges      node in edges
	 * @param outEdges     node out edges
	 * @param lsaChildren  children in LSA tree (or null, if phylogeny is tree)
	 * @param source       edge  source node
	 * @param target       edge target node
	 * @param weight       edge weight
	 * @param edgeType     edge type (or null, if phylogeny is tree)
	 * @param averaging    child averaging
	 * @param nodePointMap calculated coordinates
	 * @param <Node>       node
	 * @param <Edge>       edge
	 */
	public static <Node, Edge> void apply(Node root, Iterable<Node> nodes, Iterable<Edge> edges,
										  Function<Node, List<Edge>> inEdges,
										  Function<Node, List<Edge>> outEdges,
										  Function<Node, List<Node>> lsaChildren,
										  Function<Edge, Node> source, Function<Edge, Node> target,
										  ToDoubleFunction<Edge> weight,
										  Function<Edge, EdgeType> edgeType,
										  Averaging averaging, Map<Node, Point2D> nodePointMap) {
		var yCoord = new HashMap<Node, Double>();
		HeightAndAngles.apply(root, v -> outEdges.apply(v).stream().map(target).toList(), lsaChildren, yCoord, averaging, true);

		var averageWeight = computeAverageWeight(edges, weight);

		var smallOffsetForReticulateEdge = (reticulationOffsetPercent / 100.0) * averageWeight;

		var rootHeight = yCoord.get(root);

		var assigned = new HashSet<Node>();

		// assign coordinates:
		var queue = new LinkedList<Node>();
		queue.add(root);
		while (!queue.isEmpty()) // breath-first assignment
		{
			var w = queue.remove(0);// pop
			var incomingEdges = inEdges.apply(w);
			var ok = true;
			if (incomingEdges.size() == 1) // has regular in edge
			{
				var e = incomingEdges.get(0);
				var v = source.apply(e);
				var location = nodePointMap.get(v);

				if (!assigned.contains(v)) {  // can't process yet
					ok = false;
				} else {
					var u = target.apply(e);
					var height = yCoord.get(u);
					nodePointMap.put(u, new Point2D(location.x() + weight.applyAsDouble(e), height));
					assigned.add(u);
				}
			} else if (incomingEdges.size() > 1 && edgeType != null && incomingEdges.stream().anyMatch(f -> edgeType.apply(f) == EdgeType.transferAcceptor)) { // todo: just added this, might cause problems...
				var e = incomingEdges.stream().filter(f -> edgeType.apply(f) == EdgeType.transferAcceptor).findAny().orElse(incomingEdges.get(0));
				var v = source.apply(e);
				var location = nodePointMap.get(v);
				if (!assigned.contains(v)) // can't process yet
				{
					ok = false;
				} else {
					var u = target.apply(e);
					var height = yCoord.get(u);
					var type = edgeType.apply(e);
					double eWeight;
					if (type == EdgeType.tree || type == EdgeType.transferAcceptor)
						eWeight = weight.applyAsDouble(e);
					else if (type == EdgeType.transfer)
						eWeight = 0.01;
					else
						eWeight = 0.1;

					nodePointMap.put(u, new Point2D(location.x() + eWeight, height));
					assigned.add(u);
				}
			} else if (incomingEdges.size() > 1) {
				var x = Double.NEGATIVE_INFINITY;
				for (var f : incomingEdges) {
					var u = source.apply(f);
					var location = nodePointMap.get(u);
					if (location == null) {
						ok = false;
					} else {
						x = Math.max(x, location.x());
					}
				}
				if (ok && x > Double.NEGATIVE_INFINITY) {
					x += smallOffsetForReticulateEdge;
					nodePointMap.put(w, new Point2D(x, yCoord.get(w)));
					assigned.add(w);
				}
			} else  // is root node
			{
				nodePointMap.put(w, new Point2D(0, rootHeight));
				assigned.add(w);
			}

			if (ok)  // add children to end of queue:
			{
				for (var e : outEdges.apply(w)) {
					queue.add(target.apply(e));
				}
			} else  // process this node again later
				queue.add(w);
		}
	}

	public static <Edge> double computeAverageWeight(Iterable<Edge> edges, ToDoubleFunction<Edge> weight) {
		var averageWeight = 0.0;
		{
			var count = 0;
			for (var e : edges) {
				var eWeight = (weight == null ? 1.0 : weight.applyAsDouble(e));
				averageWeight += eWeight;
				count++;
			}
			if (count > 0) {
				averageWeight /= count;
			}
		}
		return averageWeight;
	}
}
