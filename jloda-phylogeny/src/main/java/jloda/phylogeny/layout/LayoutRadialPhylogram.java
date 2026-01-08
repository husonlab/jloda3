/*
 * LayoutRadialPhylogram.java Copyright (C) 2025 Daniel H. Huson
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
import java.util.function.ToDoubleFunction;

import static jloda.phylogeny.layout.LayoutRectangularPhylogram.computeAverageWeight;

/**
 * lays out a radial phylogram
 * todo: make independent of jloda and move to jloda-phylogeny
 * Daniel Huson, 8.2025
 */
public class LayoutRadialPhylogram {
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
	public static <Node, Edge> void apply(Node root, List<Node> nodes, List<Edge> edges,
										  Function<Node, List<Edge>> outEdges,
										  Function<Node, List<Node>> lsaChildren,
										  Function<Edge, Node> source, Function<Edge, Node> target,
										  ToDoubleFunction<Edge> weight,
										  Function<Edge, EdgeType> edgeType,
										  Averaging averaging, Map<Node, Point2D> nodePointMap) {


		var averageWeight = computeAverageWeight(edges, weight);
		var smallOffsetForRecticulateEdge = (reticulationOffsetPercent / 100.0) * averageWeight;

		ToDoubleFunction<Edge> weightFunction = e -> {
			var type = (edgeType == null ? EdgeType.tree : edgeType.apply(e));
			if (type == EdgeType.tree || type == EdgeType.transferAcceptor)
				return weight.applyAsDouble(e);
			else return smallOffsetForRecticulateEdge;
		};

		var nodeAngleMap = new HashMap<Node, Double>();

		HeightAndAngles.computeAngles(root, u -> outEdges.apply(u).stream().map(target).toList(), lsaChildren, nodeAngleMap, averaging, true);

		var nodeDepthMap = new HashMap<Node, Double>();
		computeNodeDepthRec(root, null, outEdges, source, target, weightFunction, nodeDepthMap);

		nodePointMap.clear();
		assignPointsRec(lsaChildren, root, null, nodeDepthMap, nodeAngleMap, nodePointMap);
	}

	/**
	 * compute the layout distance from the root to each node
	 *
	 * @param v            current node
	 * @param e            edge from parent
	 * @param weight       provides weights for edges
	 * @param nodeDepthMap the node depth map
	 */
	private static <Node, Edge> void computeNodeDepthRec(Node v, Edge e, Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target, ToDoubleFunction<Edge> weight, Map<Node, Double> nodeDepthMap) {
		if (e == null)
			nodeDepthMap.put(v, 0.0);
		else {
			var p = source.apply(e);
			var newValue = nodeDepthMap.get(p) + weight.applyAsDouble(e);
			var value = nodeDepthMap.getOrDefault(v, 0.0);
			nodeDepthMap.put(v, Math.max(value, newValue));
		}
		for (var f : outEdges.apply(v)) {
			computeNodeDepthRec(target.apply(f), f, outEdges, source, target, weight, nodeDepthMap);
		}
	}

	/**
	 * assign points to all nodes
	 *
	 * @param v            the current node
	 * @param p            its parent
	 * @param nodeDepthMap node depth map
	 * @param nodeAngleMap node angle map
	 * @param nodePointMap node point map (output)
	 */
	private static <Node> void assignPointsRec(Function<Node, List<Node>> lsaChildrenMap, Node v, Node p, Map<Node, Double> nodeDepthMap, Map<Node, Double> nodeAngleMap, Map<Node, Point2D> nodePointMap) {
		if (p == null) {
			nodePointMap.put(v, new Point2D(0, 0));
		} else {
			var apt = nodePointMap.get(p);
			var dist = nodeDepthMap.get(v) - nodeDepthMap.get(p);
			var vpt = translateByAngle(apt.x(), apt.y(), nodeAngleMap.get(v), dist);
			nodePointMap.put(v, vpt);
		}
		for (var w : lsaChildrenMap.apply(v)) {
			assignPointsRec(lsaChildrenMap, w, v, nodeDepthMap, nodeAngleMap, nodePointMap);
		}
	}

	private final static double DEG_TO_RAD_FACTOR = Math.PI / 180.0;

	/**
	 * Translate a point in the direction specified by an angle.
	 */
	public static Point2D translateByAngle(double aptX, double aptY, double alpha, double dist) {
		var dx = dist * Math.cos(DEG_TO_RAD_FACTOR * alpha);
		var dy = dist * Math.sin(DEG_TO_RAD_FACTOR * alpha);
		if (Math.abs(dx) < 0.000001)
			dx = 0;
		if (Math.abs(dy) < 0.000001)
			dy = 0;
		return new Point2D(aptX + dx, aptY + dy);
	}
}
