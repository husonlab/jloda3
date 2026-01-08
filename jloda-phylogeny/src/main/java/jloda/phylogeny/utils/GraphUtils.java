/*
 *  GraphUtils.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.phylogeny.utils;

import jloda.phylogeny.dolayout.Value;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * methods used by both tanglegram and network layout
 * Daniel Huson, 10.2025
 */
public class GraphUtils {

	public static <Node> void postOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		if (v != null) {
			var below = children.apply(v);
			if (below != null) {
				for (var u : below) {
					postOrderTraversal(u, children, consumer);
				}
			}
			consumer.accept(v);
		}
	}

	public static <Node> void preOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		if (v != null) {
			consumer.accept(v);
			var below = children.apply(v);
			if (below != null) {
				for (var u : below) {
					preOrderTraversal(u, children, consumer);
				}
			}
		}
	}

	public static <Node> double getMinHeight(Node u, Map<Node, List<Node>> childrenMap, Map<Node, Double> nodeHeightMap) {
		while (true) {
			var children = childrenMap.get(u);
			if (children.isEmpty())
				return nodeHeightMap.get(u);
			else u = children.get(0);
		}
	}

	public static <Node> double getMaxHeight(Node u, Map<Node, List<Node>> childrenMap, Map<Node, Double> nodeHeightMap) {
		while (true) {
			var children = childrenMap.get(u);
			if (children.isEmpty())
				return nodeHeightMap.get(u);
			else u = children.get(children.size() - 1);
		}
	}

	public static <Node> Map<Node, Double> computeNodeHeightMap(Node root, Map<Node, List<Node>> childrenMap) {
		var nodeHeightMap = new HashMap<Node, Double>();
		var min = new Value<>(0.0);
		var max = computeLeavesBelow(root, childrenMap).size() - 1;
		var bounds = computeNodeHeightMapRec(root, childrenMap, min, nodeHeightMap);
		if (bounds.min() != 0 || bounds.max() != max)
			throw new IllegalStateException("Bad bounds");
		return nodeHeightMap;
	}

	private static <Node> Collection<Node> computeLeavesBelow(Node u, Map<Node, List<Node>> childrenMap) {
		var list = new ArrayList<Node>();
		postOrderTraversal(u, childrenMap::get, v -> {
			if (childrenMap.get(v).isEmpty())
				list.add(v);
		});
		return list;
	}

	public static <Node> Bounds computeNodeHeightMapRec(Node v, Map<Node, List<Node>> childrenMap, Value<Double> next, Map<Node, Double> nodeHeightMap) {
		var children = childrenMap.get(v);
		if (children.isEmpty()) {

			var height = (double) next.get();
			next.set(next.get() + 1);
			nodeHeightMap.put(v, height);
			return new Bounds(height, height);
		} else {
			double min = Double.MAX_VALUE;
			double max = Double.MIN_VALUE;
			for (var w : children) {
				var minMax = computeNodeHeightMapRec(w, childrenMap, next, nodeHeightMap);
				min = Math.min(min, minMax.min());
				max = Math.max(max, minMax.max());
			}
			nodeHeightMap.put(v, 0.5 * (max + min));
			return new Bounds(min, max);
		}
	}

	public record Bounds(double min, double max) {
	}
}
