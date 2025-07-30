/*
 *  CircularPhylogenyLayout.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.fx.phylo.embed;

import javafx.geometry.Point2D;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.DAGTraversals;
import jloda.graph.Node;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.CollectionUtils;
import jloda.util.IteratorUtils;
import jloda.util.Single;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * compute a circular layout
 * Daniel Huson, 12.2021, 3.2025
 */
public class CircularPhylogenyLayout {

	/**
	 * compute layout for a circular cladogram
	 */
	public static void apply(PhyloTree tree, boolean toScale, Averaging averaging, boolean optimize, Map<Node, Double> nodeAngleMap, Map<Node, Point2D> points) {
		if (!tree.hasLSAChildrenMap())
			LSAUtils.setLSAChildrenAndTransfersMap(tree);

		if (optimize) {
			RectangularPhylogenyLayout.apply(tree, toScale, averaging, OptimizeLayout.How.Circular, new Random(666), points);
		}

		points.clear();

		// compute radius:
		try (var nodeRadiusMap = tree.newNodeDoubleArray()) {
			if (!toScale) {
				final var maxDepth = computeMaxDepth(tree);
				DAGTraversals.postOrderTraversal(tree.getRoot(), v -> IteratorUtils.asList(v.children()), v -> {
					if (v.isLeaf()) {
						nodeRadiusMap.put(v, (double) maxDepth);
					} else {
						var min = Double.MAX_VALUE;
						for (var e : v.outEdges()) {
							var w = e.getTarget();
							if (!tree.isTreeEdge(e) && tree.hasTransferAcceptorEdges() && !tree.isTransferAcceptorEdge(e) && CollectionUtils.intersects(tree.getTransferAcceptorEdges(), IteratorUtils.asSet(w.inEdges())))
								min = Math.min(nodeRadiusMap.get(w), min);
							else
								min = Math.min(nodeRadiusMap.get(w) - 1, min);
						}
						nodeRadiusMap.put(v, min);
					}
				});

				var excess = nodeRadiusMap.get(tree.getRoot());
				if (excess > 0) {
					nodeRadiusMap.entrySet().forEach(entry -> entry.setValue(entry.getValue() - excess));
				}
			} else {
				var percentOffset = 50.0;

				var averageWeight = tree.edgeStream().mapToDouble(tree::getWeight).average().orElse(1);
				var smallOffsetForReticulateEdge = (percentOffset / 100.0) * averageWeight;

				nodeRadiusMap.put(tree.getRoot(), 0.0);
				DAGTraversals.preOrderTraversal(tree.getRoot(), v -> IteratorUtils.asList(v.children()), v -> {
					var max = 0.0;
					for (var e : v.inEdges()) {
						var w = e.getSource();
						var wRadius = nodeRadiusMap.get(w);
						if (wRadius != null) {
							if (tree.isReticulateEdge(e) && !tree.isTransferAcceptorEdge(e)) {
								max = Math.max(max, wRadius + smallOffsetForReticulateEdge);
							} else
								max = Math.max(max, wRadius + tree.getWeight(e));
						}
						var vRadius = nodeRadiusMap.get(v);
						if (vRadius == null || max > vRadius)
							nodeRadiusMap.put(v, max);
					}
				});
			}

			if (nodeAngleMap == null)
				nodeAngleMap = new HashMap<>();

			HeightAndAngles.computeAngles(tree, nodeAngleMap, averaging, true);
			for (var v : tree.nodes()) {
				points.put(v, GeometryUtilsFX.computeCartesian(nodeRadiusMap.get(v), nodeAngleMap.get(v)));
			}
		}
	}

	/**
	 * compute the maximum number of edges from the root to a leaf
	 *
	 * @param tree the tree
	 * @return length of longest path
	 */
	public static int computeMaxDepth(PhyloTree tree) {
		var max = new Single<>(0);
		tree.breathFirstTraversal(tree.getRoot(), 0, (level, v) -> max.set(Math.max(max.get(), level)));
		return max.get();
	}
}
