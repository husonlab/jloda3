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
import jloda.graph.NodeDoubleArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.ProgramProperties;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static jloda.fx.phylo.embed.OptimizeLayout.computeCost;

/**
 * computes the layout of a rooted phylogenetic tree or network
 * Daniel Huson, 12.2021, 3.2025
 */
public enum LayoutRootedPhylogeny {
	Phylogram, CladogramEarly, CladogramLate,
	CircularPhylogram, CircularCladogramEarly, CircularCladogramLate;

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
	public static void apply(PhyloTree network, LayoutRootedPhylogeny layout, Averaging averaging, boolean optimize, Random random, Map<Node, Double> nodeAngleMap, Map<Node, Point2D> nodePointMap) {
		if (!network.hasLSAChildrenMap())
			LSAUtils.setLSAChildrenAndTransfersMap(network);
		applyRec(network, layout, averaging, optimize, random, nodeAngleMap, nodePointMap);

		if (layout.isCircular()) {
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

	/**
	 * recursively compute the layout of a rooted phylogenetic tree or network
	 * (only one level of recursion, after first running to optimize y coordinates, run again without optimizing y coordinates)
	 *
	 * @param network      the phylogeny
	 * @param layout       the desired embedding type
	 * @param averaging    parent averaging method
	 * @param random       random number generator for crossing optimization heuristic
	 * @param nodeAngleMap when a circular layout is requested, will return the angle associated with each node
	 * @param nodePointMap the node layout points
	 */
	private static void applyRec(PhyloTree network, LayoutRootedPhylogeny layout, Averaging averaging, boolean optimize, Random random, Map<Node, Double> nodeAngleMap, Map<Node, Point2D> nodePointMap) {
		OptimizeLayout.How how = (optimize ? (layout.isCircular() ? OptimizeLayout.How.Circular : OptimizeLayout.How.Rectangular) : OptimizeLayout.How.None);

		var originalScore = (how != OptimizeLayout.How.None ? computeCost(network, network.getLSAChildrenMap(), nodePointMap, how) : Integer.MAX_VALUE);

		nodePointMap.clear();

		nodePointMap.put(network.getRoot(), new Point2D(0, 0));
		try (var yCoord = network.newNodeDoubleArray()) {
			HeightAndAngles.apply(network, yCoord, averaging, how == OptimizeLayout.How.None);
			if (layout == Phylogram || layout == CircularPhylogram) {
				setCoordinatesPhylogram(network, yCoord, nodePointMap);
			} else {
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

				var max = longestPath.values().stream().mapToDouble(d -> d).max().orElse(0.0);
				for (var v : network.nodes()) {
					if (v.isLeaf())
						nodePointMap.put(v, new Point2D(max, yCoord.get(v)));
					else
						nodePointMap.put(v, new Point2D(longestPath.get(v), yCoord.get(v)));
				}
			}
			if (how != OptimizeLayout.How.None) {
				if (originalScore == Integer.MAX_VALUE) {
					originalScore = computeCost(network, network.getLSAChildrenMap(), nodePointMap, how);
				}
				if (originalScore > 0) {
					DAGTraversals.preOrderTraversal(network.getRoot(), network.getLSAChildrenMap(), v -> OptimizeLayout.optimizeOrdering(v, network.getLSAChildrenMap(), nodePointMap, random, how));
					var newScore = computeCost(network, network.getLSAChildrenMap(), nodePointMap, how);
					if (false)
						System.err.printf("Layout optimization: %d -> %d%n", originalScore, newScore);
				}
				applyRec(network, layout, averaging, false, new Random(666), nodeAngleMap, nodePointMap);
			}

			if (layout == CladogramLate || layout == CircularCladogramLate) {
				modifyToLateBranching(network, v -> nodePointMap.get(v).getX(), (v, x) -> nodePointMap.put(v, new Point2D(x, nodePointMap.get(v).getY())));
			}
		}
	}

	public static void computeLongestPathsRec(PhyloTree tree, Node v, HashMap<Node, Double> longestPath) {
		var vDist = longestPath.get(v);
		for (var f : v.outEdges()) {
			var w = f.getTarget();
			var wDist = (tree.isTreeEdge(f) || tree.isTransferAcceptorEdge(f) ? vDist + 1.0 : vDist);
			if (tree.isReticulateEdge(f) && !tree.isTransferEdge(f) && !tree.isTransferAcceptorEdge(f)) {
				wDist += 0.5;
			}
			if (!longestPath.containsKey(w) || wDist > longestPath.get(w))
				longestPath.put(w, wDist);
			computeLongestPathsRec(tree, w, longestPath);
		}
	}

	/**
	 * computes the coordinates of a phylogram
	 *
	 * @param network      the phylogeny
	 * @param yCoord       the y-coordinates
	 * @param nodePointMap the results are written to this map
	 */
	public static void setCoordinatesPhylogram(PhyloTree network, NodeDoubleArray yCoord, Map<Node, Point2D> nodePointMap) {
		var percentOffset = ProgramProperties.get("ReticulationOffsetPercent", 50.0);

		var averageWeight = network.edgeStream().mapToDouble(network::getWeight).average().orElse(1);
		var smallOffsetForReticulateEdge = (percentOffset / 100.0) * averageWeight;

		var rootHeight = yCoord.get(network.getRoot());

		try (var assigned = network.newNodeSet()) {
			// assign coordinates:
			var queue = new LinkedList<Node>();
			queue.add(network.getRoot());
			while (!queue.isEmpty()) // breath-first assignment
			{
				var w = queue.remove(0); // pop
				var ok = true;
				if (w.getInDegree() == 1) // has regular in edge
				{
					var e = w.getFirstInEdge();
					var v = e.getSource();
					var location = nodePointMap.get(v);

					if (!assigned.contains(v)) // can't process yet
					{
						ok = false;
					} else {
						var height = yCoord.get(e.getTarget());
						var u = e.getTarget();
						nodePointMap.put(u, new Point2D(location.getX() + network.getWeight(e), height));
						assigned.add(u);
					}
				} else if (w.getInDegree() > 1 && w.inEdgesStream(false).anyMatch(network::isTransferAcceptorEdge)) { // todo: just added this, might cause problems...
					var e = w.inEdgesStream(false).filter(network::isTransferAcceptorEdge).findAny().orElse(w.getFirstInEdge());
					var v = e.getSource();
					var location = nodePointMap.get(v);
					if (!assigned.contains(v)) // can't process yet
					{
						ok = false;
					} else {
						var height = yCoord.get(e.getTarget());
						var u = e.getTarget();
						double weight;
						if (network.hasEdgeWeights() && network.getEdgeWeights().containsKey(e))
							weight = network.getEdgeWeights().get(e);
						else if (network.isTreeEdge(e) || network.isTransferAcceptorEdge(e))
							weight = 1.0;
						else if (network.isTransferEdge(e))
							weight = 0.01;
						else weight = 0.1;
						nodePointMap.put(u, new Point2D(location.getX() + weight, height));
						assigned.add(u);
					}
				} else if (w.getInDegree() > 1) {
					var x = Double.NEGATIVE_INFINITY;
					for (var f : w.inEdges()) {
						var u = f.getSource();
						var location = nodePointMap.get(u);
						if (location == null) {
							ok = false;
						} else {
							x = Math.max(x, location.getX());
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
					queue.addAll(IteratorUtils.asList(w.children()));
				else  // process this node again later
					queue.add(w);
			}
		}
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

	public boolean isCircular() {
		return this == CircularPhylogram || this == CircularCladogramEarly || this == CircularCladogramLate;
	}
}

