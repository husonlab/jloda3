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

package jloda.fx.phylo.embed;

import javafx.geometry.Point2D;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * lays out a radial phylogram
 * todo: make independent of jloda and move to jloda-phylogeny
 * Daniel Huson, 8.2025
 */
public class LayoutRadialPhylogram {
	public static void apply(PhyloTree network, Averaging averaging, Map<Node, Double> nodeAngleMap, Map<Node, Point2D> nodePointMap) {
		var percentOffset = ProgramProperties.get("ReticulationOffsetPercent", 50.0);
		var averageWeight = network.edgeStream().filter(e -> network.isTransferAcceptorEdge(e) || network.isTreeEdge(e)).mapToDouble(network::getWeight).average().orElse(1);
		var smallOffsetForRecticulateEdge = (percentOffset / 100.0) * averageWeight;

		Function<Edge, Double> weightFunction = e -> {
			if (network.isTreeEdge(e) || network.isTransferAcceptorEdge(e))
				return network.getWeight(e);
			else return smallOffsetForRecticulateEdge;
		};

		HeightAndAngles.computeAngles(network, nodeAngleMap, averaging, true);

		try (var nodeDepthMap = network.newNodeDoubleArray()) {
			computeNodeDepthRec(network.getRoot(), null, weightFunction, nodeDepthMap);

			nodePointMap.clear();
			assignPointsRec(network.getLSAChildrenMap(), network.getRoot(), null, nodeDepthMap, nodeAngleMap, nodePointMap);
		}
	}

	/**
	 * compute the layout distance from the root to each node
	 *
	 * @param v              current node
	 * @param e              edge from parent
	 * @param weightFunction provides weights for edges
	 * @param nodeDepthMap   the node depth map
	 */
	private static void computeNodeDepthRec(Node v, Edge e, Function<Edge, Double> weightFunction, NodeDoubleArray nodeDepthMap) {
		if (e == null)
			nodeDepthMap.put(v, 0.0);
		else {
			var p = e.getSource();
			var newValue = nodeDepthMap.get(p) + weightFunction.apply(e);
			var value = nodeDepthMap.getOrDefault(v, 0.0);
			nodeDepthMap.put(v, Math.max(value, newValue));
		}
		for (var f : v.outEdges()) {
			computeNodeDepthRec(f.getTarget(), f, weightFunction, nodeDepthMap);
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
	private static void assignPointsRec(Map<Node, List<Node>> lsaChildrenMap, Node v, Node p, Map<Node, Double> nodeDepthMap, Map<Node, Double> nodeAngleMap, Map<Node, Point2D> nodePointMap) {
		if (p == null) {
			nodePointMap.put(v, new Point2D(0, 0));
		} else {
			var apt = nodePointMap.get(p);
			var dist = nodeDepthMap.get(v) - nodeDepthMap.get(p);
			var vpt = GeometryUtilsFX.translateByAngle(apt, nodeAngleMap.get(v), dist);
			nodePointMap.put(v, vpt);
		}
		for (var w : lsaChildrenMap.get(v)) {
			assignPointsRec(lsaChildrenMap, w, v, nodeDepthMap, nodeAngleMap, nodePointMap);
		}
	}
}
