/*
 * DoNetworkLayout.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Compute a (reticulate) displacement optimized (DO) layout for a rooted network
 * If you use this code, then please cite: D.H. Huson, Sketch, Capture and Layout Phylogenies, submitted, 2025
 * Daniel Huson, 10.2025
 */
public class DoNetworkLayout {
	public static boolean verbose = false;

	/**
	 * Compute an optimized-reticulate-displacement layout for a rooted network
	 *
	 * @param root             the root node
	 * @param backboneChildren for each node, the list of its children in the backbone tree
	 * @param reticulateEdges  for each node, all nodes that it is connected to via a reticulate edge (excluding transfer-acceptor edges)
	 * @param circular         true, if we are optimizing for a circular layout
	 * @param canceled         returns true, if calculation has been canceled
	 * @param <Node>           the node of a tree
	 * @return an optimized LSA tree mapping
	 */
	public static <Node> Map<Node, List<Node>> apply(Node root, Function<Node, List<Node>> backboneChildren, Function<Node, List<Node>> reticulateEdges, boolean circular, BooleanSupplier canceled) {
		var random = new Random(666);
		return apply(root, backboneChildren, reticulateEdges, circular, random, canceled);

	}

	/**
	 * Compute an optimized-reticulate-displacement layout for a rooted network
	 *
	 * @param root             the root node
	 * @param backboneChildren for each node, the list of its children in the backbone tree
	 * @param reticulateEdges  for each node, all nodes that it is connected to via a reticulate edge (excluding transfer-acceptor edges)
	 * @param circular         true, if we are optimizing for a circular layout
	 * @param random           random number generator used in simulated annealing search
	 * @param canceled         returns true, if calculation has been canceled
	 * @param <Node>           the node of a tree
	 * @return an optimized LSA tree mapping
	 */
	public static <Node> Map<Node, List<Node>> apply(Node root, Function<Node, List<Node>> backboneChildren, Function<Node, List<Node>> reticulateEdges, boolean circular, Random random, BooleanSupplier canceled) {
		var childrenMap = new HashMap<Node, List<Node>>();

		{
			Common.postOrderTraversal(root, backboneChildren, u -> {
				childrenMap.put(u, backboneChildren.apply(u));
			});
		}

		var nodeHeightMap = Common.computeNodeHeightMap(root, childrenMap);
		Function<Node, Double> reticulateDisplacementFunction;

		if (circular) {
			var totalMin = Common.getMinHeight(root, childrenMap, nodeHeightMap);
			var totalMax = Common.getMaxHeight(root, childrenMap, nodeHeightMap);
			reticulateDisplacementFunction = v -> {
				var displacement = 0.0;
				var reticulateNeighbors = reticulateEdges.apply(v);
				if (reticulateNeighbors != null) {
					var hV = nodeHeightMap.get(v);
					for (var w : reticulateNeighbors) {
						var hW = nodeHeightMap.get(w);
						var min = Math.min(hV, hW);
						var max = Math.max(hV, hW);
						var diff = Math.min(max - min, (totalMax - max) + (min - totalMin) + 1);
						displacement += 0.5 * diff;
					}
				}
				return displacement;
			};
		} else {
			reticulateDisplacementFunction = v -> {
				var displacement = 0.0;
				var reticulateNeighbors = reticulateEdges.apply(v);
				if (reticulateNeighbors != null) {
					var hV = nodeHeightMap.get(v);
					for (var w : reticulateNeighbors) {
						var hW = nodeHeightMap.get(w);
						var diff = Math.abs(hV - hW);
						displacement += 0.5 * diff;
					}
					// todo: additional term that avoids lopsided layout
				}
				return displacement;
			};
		}
		var oldScore = (verbose ? computeCostBelow(childrenMap, root, reticulateDisplacementFunction) : -1);

		// pre- or post-order better?
		Common.preOrderTraversal(root, childrenMap::get, v -> optimizeOrdering(childrenMap, v, nodeHeightMap, reticulateDisplacementFunction, random, canceled));


		if (oldScore != -1) {
			var newScore = computeCostBelow(childrenMap, root, reticulateDisplacementFunction);
			System.err.printf("DoNetworkAlgorithm: %.1f -> %.1f%n", oldScore, newScore);
		}

		return childrenMap;
	}

	/**
	 * attempt to optimize ordering of children below v, updating LSA children, nodeRankMap nodeHeightMap appropriately
	 *
	 * @param v             the node
	 * @param nodeHeightMap maps v to its height, and to the min and max height of v and all descendants
	 * @param costFunction  the cost function
	 * @param random        random number generator used in simulated annealing
	 */
	public static <Node> void optimizeOrdering(Map<Node, List<Node>> childrenMap, Node v, Map<Node, Double> nodeHeightMap, Function<Node, Double> costFunction, Random random, BooleanSupplier canceled) {
		if (canceled.getAsBoolean())
			return;

		if (childrenMap.get(v).size() < 2)
			return;

		var originalCost = computeCostBelow(childrenMap, v, costFunction);
		var originalOrdering = new ArrayList<>(childrenMap.get(v));
		var originalHeightBelowMap = copyHeightBelowMap(v, childrenMap, nodeHeightMap);

		if (originalOrdering.size() <= 8) {
			var bestCost = new Value<>(originalCost);
			var bestOrdering = new Value<>(originalOrdering);

			for (var permuted : Permutations.generateAllPermutations(originalOrdering)) {
				childrenMap.put(v, originalOrdering);
				try {
					changeOrderOfChildren(childrenMap, v, permuted, nodeHeightMap);
					var cost = computeCostBelow(childrenMap, v, costFunction);
					if (cost < bestCost.get()) {
						bestCost.set(cost);
						bestOrdering.set(new ArrayList<>(permuted));
						if (bestCost.get() == 0)
							break;
					}
					if (canceled.getAsBoolean())
						return;
				} finally {
					childrenMap.put(v, originalOrdering);
					nodeHeightMap.putAll(originalHeightBelowMap);
				}
			}
			if (bestCost.get() < originalCost) {
				changeOrderOfChildren(childrenMap, v, bestOrdering.get(), nodeHeightMap);
			}
		} else {
			var simulatedAnnealing = new SimulatedAnnealingMinLA<Node>();
			var pair = simulatedAnnealing.apply(originalOrdering, random, (permuted) -> {
				if (canceled.getAsBoolean())
					return 0.0;
				try {
					childrenMap.put(v, originalOrdering);
					changeOrderOfChildren(childrenMap, v, permuted, nodeHeightMap);
					return computeCostBelow(childrenMap, v, costFunction);
				} finally {
					childrenMap.put(v, originalOrdering);
					nodeHeightMap.putAll(originalHeightBelowMap);
				}
			});
			if (pair.score() < originalCost) {
				changeOrderOfChildren(childrenMap, v, pair.list(), nodeHeightMap);
			}
		}
	}

	private static <Node> Map<Node, Double> copyHeightBelowMap(Node v, Map<Node, List<Node>> childrenMap, Map<Node, Double> heightMap) {
		var heightMapBelow = new HashMap<Node, Double>();
		Common.postOrderTraversal(v, childrenMap::get, u -> heightMapBelow.put(u, heightMap.get(u)));
		return heightMapBelow;
	}

	/**
	 * change the order of the children of v, both in LSA map and also in the nodeHeightMap
	 *
	 * @param v             the node
	 * @param newOrder      the new order of the children of v in the LSA map
	 * @param nodeHeightMap the node min-height-below, height and max height below values, these are also changed for v and all descendants
	 */
	private static <Node> void changeOrderOfChildren(Map<Node, List<Node>> childrenMap, Node v, List<Node> newOrder, Map<Node, Double> nodeHeightMap) {
		var oldOrder = childrenMap.get(v);
		if (oldOrder.size() > 1 && !oldOrder.equals(newOrder)) {
			var next = new Value<>(Common.getMinHeight(oldOrder.get(0), childrenMap, nodeHeightMap));
			for (var w : newOrder) {
				Common.computeNodeHeightMapRec(w, childrenMap, next, nodeHeightMap);
			}
			childrenMap.put(v, newOrder);
		}
	}

	private static <Node> double computeCostBelow(Map<Node, List<Node>> childrenMap, Node v, Function<Node, Double> costFunction) {
		var cost = new double[]{0.0};
		Common.postOrderTraversal(v, childrenMap::get, u -> cost[0] += costFunction.apply(u));
		return cost[0];
	}
}
