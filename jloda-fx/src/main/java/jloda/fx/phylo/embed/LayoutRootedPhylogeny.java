/*
 *  RectangularPhylogenyLayout.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static jloda.fx.phylo.embed.OptimizeLayout.computeCost;

/**
 * computes the layout of a rooted phylogenetic tree or network
 * Daniel Huson, 12.2021, 3.2025
 */
public class LayoutRootedPhylogeny {
	public enum Layout {Rectangular, Circular, Radial}

	public enum Scaling {ToScale, EarlyBranching, LateBranching}

	/**
	 * computes the layout of a rooted phylogenetic tree or network	 *
	 *
	 * @param network      the phylogeny
	 * @param layout       the desired embedding type
	 * @param averaging    parent averaging method
	 * @param random       random number generator for crossing optimization heuristic
	 * @param nodeAngleMap when a circular layout is requested, will return the angle associated with each node
	 * @param nodePointMap the node layout points
	 */
	public static void apply(PhyloTree network, Layout layout, Scaling scaling, Averaging averaging, boolean optimizeReticulateEdges, Random random, Map<Node, Double> nodeAngleMap, Map<Node, Point2D> nodePointMap) {
		if (network.getRoot() != null && (!network.hasLSAChildrenMap() || network.getLSAChildrenMap().isEmpty() || network.getLSAChildrenMap().get(network.getRoot()).isEmpty())) {
			LSAUtils.setLSAChildrenAndTransfersMap(network);
		}

		if (layout == Layout.Radial && averaging == Averaging.ChildAverage) {
			averaging = Averaging.LeafAverage; // leaf averaging not valid for radial layout
		}


		if (optimizeReticulateEdges && network.hasReticulateEdges()) {
			nodeAngleMap.clear();
			nodePointMap.clear();

			LayoutRectangularCladogram.apply(network, averaging, nodePointMap);

			var optimizeHow = (layout == Layout.Rectangular ? OptimizeLayout.How.Rectangular : OptimizeLayout.How.Circular);
			var originalScore = computeCost(network, network.getLSAChildrenMap(), nodePointMap, optimizeHow);

			if (originalScore > 0) {
				DAGTraversals.preOrderTraversal(network.getRoot(), network.getLSAChildrenMap(), v -> OptimizeLayout.optimizeOrdering(v, network.getLSAChildrenMap(), nodePointMap, random, optimizeHow));
				if (true) {
					var ystep = computeLeafYStep(network, v -> nodePointMap.get(v).getY());
					var newScore = computeCost(network, network.getLSAChildrenMap(), nodePointMap, optimizeHow);
					System.err.printf("Layout displacement-optimization: %.1f -> %.1f%n", ((float) originalScore / ystep), ((float) newScore / ystep));
				}
			}
		}

		nodeAngleMap.clear();
		nodePointMap.clear();

		if (scaling != Scaling.ToScale) {
			LayoutRectangularCladogram.apply(network, averaging, nodePointMap);
		} else if (layout == Layout.Radial) {
			LayoutRadialPhylogram.apply(network, averaging, nodeAngleMap, nodePointMap);
		} else {
			LayoutRectangularPhylogram.apply(network, averaging, nodePointMap);
		}

		if (scaling == Scaling.LateBranching) {
			modifyToLateBranching(network, v -> nodePointMap.get(v).getX(), (v, x) -> nodePointMap.put(v, new Point2D(x, nodePointMap.get(v).getY())));
		}

		if (layout == Layout.Circular || (layout == Layout.Radial && scaling != Scaling.ToScale)) {
			nodeAngleMap.clear();
			try (var nodeRadiusMap = network.newNodeDoubleArray()) {
				var rootOffset = nodePointMap.get(network.getRoot()).getX();
				for (var v : network.nodes()) {
					nodeRadiusMap.put(v, nodePointMap.get(v).getX() - rootOffset);
				}
				HeightAndAngles.computeAngles(network, nodeAngleMap, averaging, true);
				for (var v : network.nodes()) {
					nodePointMap.put(v, GeometryUtilsFX.computeCartesian(nodeRadiusMap.get(v), nodeAngleMap.get(v)));
				}
			}
		} else {
			for (var v : network.nodes()) {
				nodeAngleMap.put(v, 0.0);
			}
		}
	}

	private static double computeLeafYStep(PhyloTree network, Function<Node, Double> yFunction) {
		var min = Double.MAX_VALUE;
		var max = Double.MIN_VALUE;
		var count = 0;
		for (var v : network.nodes()) {
			if (v.isLeaf()) {
				var value = yFunction.apply(v);
				min = Math.min(min, value);
				max = Math.max(max, value);
				count++;
			}
		}
		return count >= 2 ? (max - min) / (count - 1) : 1;
	}

	/**
	 * modify early-branching layout to late branching one, taking care not to disturb transfer edges
	 *
	 * @param network the network
	 * @param xGetter get the x-value of a node
	 * @param xSetter set the x-value of a node
	 */
	public static void modifyToLateBranching(PhyloTree network, Function<Node, Double> xGetter, BiConsumer<Node, Double> xSetter) {
		try (var aboveTransfer = network.newNodeSet()) {
			DAGTraversals.postOrderTraversal(network.getRoot(), v -> {
				for (var w : v.children()) {
					if (aboveTransfer.contains(w)) {
						aboveTransfer.add(v);
						return;
					}
				}
				for (var e : v.adjacentEdges()) {
					if (network.isTransferEdge(e)) {
						aboveTransfer.add(v);
						return;
					}
				}
			}, false);

			DAGTraversals.postOrderTraversal(network.getRoot(), v -> {
						if (!v.isLeaf() && !aboveTransfer.contains(v)) {
							var x = Double.MAX_VALUE;
							for (var e : v.outEdges()) {
								var w = e.getTarget();
								if (network.isTransferEdge(e)) {
									x = Math.min(x, xGetter.apply(w));
								} else if (network.isReticulateEdge(e) && !network.isTransferAcceptorEdge(e)) {
									x = Math.min(x, xGetter.apply(w) - 0.5);
								} else {
									x = Math.min(x, xGetter.apply(w) - 1);
								}
							}
							if (x < Double.MAX_VALUE)
								xSetter.accept(v, x);
						}
					},
					true);
		}
	}
}

