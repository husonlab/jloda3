/*
 * BiconnectedComponents.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.phylogeny.utils;

import java.util.*;
import java.util.function.Function;

/**
 * compute biconnected components
 * Daniel Huson, 11.2024
 */
public class BiconnectedComponents<Node, Edge> {

	private final List<Set<Node>> biconnectedComponents;
	private final Map<Node, Integer> discoveryTime;
	private final Map<Node, Integer> low;
	private final Map<Node, Node> parent;
	private final Stack<Node> stack;
	private int time;

	public BiconnectedComponents() {
		biconnectedComponents = new ArrayList<>();
		discoveryTime = new HashMap<>();
		low = new HashMap<>();
		parent = new HashMap<>();
		stack = new Stack<>();
		time = 0;
	}

	/**
	 * computes all biconnected components in this graph
	 *
	 * @param nodes    nodes
	 * @param inEdges  node in edges
	 * @param outEdges node out edges
	 * @param source   edge source
	 * @param target   edge taget
	 * @param <Node>   node
	 * @param <Edge>   edge
	 * @return set of nodes for each component
	 */
	public static <Node, Edge> List<Set<Node>> apply(Iterable<Node> nodes, Function<Node, List<Edge>> inEdges,
													 Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target) {
		return (new BiconnectedComponents<Node, Edge>()).findBiconnectedComponents(nodes, inEdges, outEdges, source, target);
	}

	public List<Set<Node>> findBiconnectedComponents(Iterable<Node> nodes, Function<Node, List<Edge>> inEdges,
													 Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target) {
		for (var node : nodes) {
			discoveryTime.put(node, -1);
			low.put(node, -1);
			parent.put(node, null);
		}

		// Apply DFS to each unvisited node
		for (var node : nodes) {
			if (discoveryTime.get(node) == -1) {
				dfs(node, inEdges, outEdges, source, target);
			}
		}

		return biconnectedComponents;
	}

	private void dfs(Node u, Function<Node, List<Edge>> inEdges, Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target) {
		discoveryTime.put(u, time);
		low.put(u, time);
		time++;

		int children = 0;  // Number of children in DFS tree for root check
		stack.push(u);     // Push the node onto the stack

		var adjacentEdges = new ArrayList<>(inEdges.apply(u));
		adjacentEdges.addAll(outEdges.apply(u));

		for (var e : adjacentEdges) {
			var v = (source.apply(e) == u ? target.apply(e) : source.apply(e));

			if (discoveryTime.get(v) == -1) {  // If v is not visited
				parent.put(v, u);
				children++;
				dfs(v, inEdges, outEdges, source, target);

				low.put(u, Math.min(low.get(u), low.get(v)));

				if ((parent.get(u) == null && children > 1) ||
					(parent.get(u) != null && low.get(v) >= discoveryTime.get(u))) {
					var component = new HashSet<Node>();
					Node node;
					do {
						node = stack.pop();
						component.add(node);
					} while (!node.equals(v));
					component.add(u);
					biconnectedComponents.add(component);
				}

			} else if (!v.equals(parent.get(u)) && discoveryTime.get(v) < discoveryTime.get(u)) {
				low.put(u, Math.min(low.get(u), discoveryTime.get(v)));
				if (!stack.contains(v)) {
					stack.push(v);
				}
			}
		}

		if (parent.get(u) == null && !stack.isEmpty()) {
			var component = new HashSet<Node>();
			while (!stack.isEmpty()) {
				component.add(stack.pop());
			}
			biconnectedComponents.add(component);
		}
	}
}