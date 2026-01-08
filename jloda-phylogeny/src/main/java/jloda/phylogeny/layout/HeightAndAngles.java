/*
 *  HeightAndAngles.java Copyright (C) 2025 Daniel H. Huson
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


import java.util.*;
import java.util.function.Function;

import static jloda.phylogeny.utils.GraphUtils.postOrderTraversal;

/**
 * computes the y-coordinates for the rectangular left-to-right layout
 * Daniel Huson, 2025, 1.2026
 */
public class HeightAndAngles {


	/**
	 * compute the y-coordinates for a rectangular left-to-right view
	 *
	 * @param root                        the root node
	 * @param children                    maps each node to list of children
	 * @param lsaChildren                 maps each node to list of children in LSA tree (or null)
	 * @param nodeHeightMap               will map each node to its height
	 * @param averaging                   type of averaging to use
	 * @param fixSpacingBetweenTrueLeaves request that spacing between true leaves is fixed
	 * @param <Node>                      tree or rooted network node
	 */
	public static <Node> void apply(Node root, Function<Node, List<Node>> children, Function<Node, List<Node>> lsaChildren, Map<Node, Double> nodeHeightMap, Averaging averaging, boolean fixSpacingBetweenTrueLeaves) {
		var lsaChildrenFinal = (lsaChildren != null ? lsaChildren : children);

		var leafOrder = new ArrayList<Node>();
		computeYCoordinateOfLeavesRec(root, children, lsaChildrenFinal, 0, nodeHeightMap, leafOrder);
		if (fixSpacingBetweenTrueLeaves)
			fixSpacingBetweenTrueLeaves(children, leafOrder, nodeHeightMap);
		if (averaging == Averaging.ChildAverage) {
			computeHeightInternalNodesAsChildAverageRec(root, lsaChildrenFinal, nodeHeightMap);
		} else {
			record MinMax(double min, double max) {
			}
			var minMaxBelowMap = new HashMap<Node, MinMax>();
			postOrderTraversal(root, lsaChildrenFinal, v -> {
				var descendents = lsaChildrenFinal.apply(v);
				if (descendents.isEmpty()) { // leaf
					minMaxBelowMap.put(v, new MinMax(nodeHeightMap.get(v), nodeHeightMap.get(v)));
				} else {
					var min = minMaxBelowMap.get(descendents.get(0)).min();
					var max = minMaxBelowMap.get(descendents.get(descendents.size() - 1)).max();
					nodeHeightMap.put(v, 0.5 * (min + max));
					minMaxBelowMap.put(v, new MinMax(min, max));
				}
			});
		}
	}

	/**
	 * compute the angles for a radial (isCircular) view
	 *
	 * @param root                        the root node
	 * @param children                    maps each node to list of children
	 * @param lsaChildren                 maps each node to list of children in LSA tree (or null)
	 * @param nodeAngleMap                will map each node to it's angle
	 * @param averaging                   type of averaging to use
	 * @param fixSpacingBetweenTrueLeaves request that spacing between true leaves is fixed
	 * @param <Node>                      tree or rooted network node
	 */
	public static <Node> void computeAngles(Node root, Function<Node, List<Node>> children, Function<Node, List<Node>> lsaChildren, Map<Node, Double> nodeAngleMap, Averaging averaging, boolean fixSpacingBetweenTrueLeaves) {
		apply(root, children, lsaChildren, nodeAngleMap, averaging, fixSpacingBetweenTrueLeaves);
		var max = nodeAngleMap.values().stream().mapToDouble(a -> a).max().orElse(0);
		var factor = 360.0 / max;
		nodeAngleMap.replaceAll((v, value) -> value * factor);
	}

	/**
	 * recursively compute the y coordinate for a parallel or triangular diagram
	 *
	 * @return index of last leaf
	 */
	private static <Node> int computeYCoordinateOfLeavesRec(Node v, Function<Node, List<Node>> children, Function<Node, List<Node>> lsaChildren, int leafNumber, Map<Node, Double> yCoord, List<Node> nodeOrder) {

		if (children.apply(v).isEmpty() || lsaChildren.apply(v).isEmpty()) {
			// String taxonName = tree.getLabel(v);
			yCoord.put(v, (double) ++leafNumber);
			nodeOrder.add(v);
		} else {
			for (var w : lsaChildren.apply(v)) {
				leafNumber = computeYCoordinateOfLeavesRec(w, children, lsaChildren, leafNumber, yCoord, nodeOrder);
			}
		}
		return leafNumber;
	}

	/**
	 * recursively compute the y coordinate for the internal nodes of a parallel diagram
	 */
	private static <Node> void computeHeightInternalNodesAsChildAverageRec(Node v, Function<Node, List<Node>> lsaChildren, Map<Node, Double> nodeHeightMap) {
		var children = lsaChildren.apply(v);
		if (!children.isEmpty()) {
			double first = Double.NEGATIVE_INFINITY;
			double last = Double.NEGATIVE_INFINITY;
			for (var w : children) {
				var height = nodeHeightMap.get(w);
				if (height == null) {
					computeHeightInternalNodesAsChildAverageRec(w, lsaChildren, nodeHeightMap);
					height = nodeHeightMap.get(w);
				}
				last = height;
				if (first == Double.NEGATIVE_INFINITY)
					first = last;
			}
			nodeHeightMap.put(v, 0.5 * (last + first));

		}
	}

	/**
	 * fix spacing so that space between any two true leaves is 1
	 */
	private static <Node> void fixSpacingBetweenTrueLeaves(Function<Node, List<Node>> children, Collection<Node> lsaTreeLeafOrder, Map<Node, Double> nodeHeightMap) {
		var nodes = new ArrayList<>(lsaTreeLeafOrder);
		double leafPos = 0;
		for (int lastTrueLeaf = -1; lastTrueLeaf < nodes.size(); ) {
			int nextTrueLeaf = lastTrueLeaf + 1;
			while (nextTrueLeaf < nodes.size() && !children.apply(nodes.get(nextTrueLeaf)).isEmpty())
				nextTrueLeaf++;
			// assign fractional positions to intermediate nodes
			int count = (nextTrueLeaf - lastTrueLeaf) - 1;
			if (count > 0) {
				double add = 1.0 / (count + 1); // if odd, use +2 to avoid the middle
				double value = leafPos;
				for (int i = lastTrueLeaf + 1; i < nextTrueLeaf; i++) {
					value += add;
					nodeHeightMap.put(nodes.get(i), value);
				}
			}
			// assign whole positions to actual leaves:
			if (nextTrueLeaf < nodes.size()) {
				nodeHeightMap.put(nodes.get(nextTrueLeaf), ++leafPos);
			}
			lastTrueLeaf = nextTrueLeaf;
		}
	}
}
