/*
 *  LsaUtils.java Copyright (C) 2026 Daniel H. Huson
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


import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LsaUtils {
	/**
	 * given a rooted network, returns a mapping of each LSA node to its children in the LSA tree
	 *
	 * @param root     the root
	 * @param nodes    the nodes
	 * @param inEdges  node in edge
	 * @param outEdges node out edge
	 * @param source   edge source
	 * @param target   edge target
	 * @param <Node>   nodes
	 * @param <Edge>   edges
	 * @return the LSA children map
	 */
	public static <Node, Edge> Map<Node, List<Node>> computeLSAChildrenMap(Node root, Collection<Node> nodes, Function<Node, List<Edge>> inEdges,
																		   Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target) {
		var lsaChildrenMap = new HashMap<Node, List<Node>>();
		computeLSAChildrenMap(root, nodes, inEdges, outEdges, source, target, lsaChildrenMap, new HashMap<>());
		return lsaChildrenMap;
	}

	/**
	 * given a rooted network, returns a mapping of each LSA node to its children in the LSA tree
	 *
	 * @param root             the root
	 * @param nodes            the nodes
	 * @param inEdges          node in edge
	 * @param outEdges         node out edge
	 * @param source           edge source
	 * @param target           edge target
	 * @param lsaChildrenMap   the node to LSA-tree children map
	 * @param reticulation2LSA the reticulation node to LSA node mapping
	 */
	public static <Node, Edge> void computeLSAChildrenMap(Node root, Collection<Node> nodes, Function<Node, List<Edge>> inEdges,
														  Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target,
														  Map<Node, List<Node>> lsaChildrenMap, Map<Node, Node> reticulation2LSA) {
		lsaChildrenMap.clear();
		reticulation2LSA.clear();

		// first we compute the reticulate node to lsa node mapping:
		computeReticulation2LSA(root, nodes, inEdges, outEdges, source, target, reticulation2LSA);

		for (var v : nodes) {
			var children = new ArrayList<>(outEdges.apply(v).stream().map(target).filter(u -> inEdges.apply(u).size() == 1).toList());
			lsaChildrenMap.put(v, children);
		}
		for (var v : nodes) {
			var lsa = reticulation2LSA.get(v);
			if (lsa != null)
				lsaChildrenMap.get(lsa).add(v);
		}
	}

	/**
	 * compute the reticulation-to-lsa mapping
	 *
	 * @param root             the root
	 * @param nodes            the nodes
	 * @param inEdges          node in edge
	 * @param outEdges         node out edge
	 * @param source           edge source
	 * @param target           edge target
	 * @param reticulation2LSA the reticulation to LSA mapping
	 */
	public static <Node, Edge> void computeReticulation2LSA(Node root, Collection<Node> nodes, Function<Node, List<Edge>> inEdges,
															Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target, Map<Node, Node> reticulation2LSA) {
		reticulation2LSA.clear();

		var reticulateNodes = nodes.stream().filter(v -> inEdges.apply(v).size() >= 2).toList();
		var lsaNodes = computeAllLowestStableAncestors(root, nodes, inEdges, outEdges, source, target, reticulateNodes);
		for (var v : reticulateNodes) {
			var stack = new Stack<Node>();
			for (var e : inEdges.apply(v)) {
				stack.push(source.apply(e));
			}
			while (!stack.isEmpty()) {
				var w = stack.pop();
				if (lsaNodes.contains(w)) {
					reticulation2LSA.put(v, w);
					break;
				} else {
					for (var e : inEdges.apply(w)) {
						stack.push(source.apply(e));
					}
				}
			}
		}
	}

	/**
	 * compute the reticulation-to-lsa mapping for a query set
	 *
	 * @param root     the root
	 * @param nodes    the nodes
	 * @param inEdges  node in edge
	 * @param outEdges node out edge
	 * @param source   edge source
	 * @param target   edge target
	 * @param query    the set of query nodes
	 * @return reticulation2LSA the reticulation to LSA mapping
	 */
	public static <Node, Edge> Set<Node> computeAllLowestStableAncestors(Node root, Collection<Node> nodes, Function<Node, List<Edge>> inEdges,
																		 Function<Node, List<Edge>> outEdges, Function<Edge, Node> source, Function<Edge, Node> target, Collection<Node> query) {
		if (query == null) {
			query = nodes.stream().filter(v -> inEdges.apply(v).size() >= 2).toList();
		}


		Function<Node, List<Node>> parents = v -> inEdges.apply(v).stream().map(source).collect(Collectors.toList());
		Function<Node, List<Node>> children = v -> outEdges.apply(v).stream().map(target).collect(Collectors.toList());

		var nodeSet = new HashSet<>(query.stream().filter(v -> inEdges.apply(v).size() == 1).map(v -> parents.apply(v).get(0)).toList());
		var reticulateNodes = query.stream().filter(v -> inEdges.apply(v).size() > 1).toList();

		for (var component : BiconnectedComponents.apply(nodes, inEdges, outEdges, source, target)) {
			if (intersects(component, reticulateNodes)) {
				var top = component.stream().filter(v -> inEdges.apply(v).isEmpty() || !component.containsAll(parents.apply(v))).findFirst().orElse(root);
				for (var v : reticulateNodes) {
					var above = new HashSet<Node>();
					var stack = new Stack<Node>();
					stack.push(v);
					while (!stack.isEmpty()) {
						var w = stack.pop();
						above.add(w);
						if (w != top) {
							stack.addAll(parents.apply(w));
						}
					}
					var w = top;
					while (w != v && intersection(above, children.apply(w)).size() == 1) {
						w = intersection(above, children.apply(w)).get(0);
					}
					nodeSet.add(w);
				}
			}
		}
		return nodeSet;
	}

	public static <T> List<T> intersection(Collection<T> collection1, Collection<T> collection2) {
		var result = new ArrayList<T>();
		if (!collection1.isEmpty() && !collection2.isEmpty()) {
			for (var a : collection1) {
				if (collection2.contains(a)) {
					result.add(a);
				}
			}
		}
		return result;
	}

	public static <T> boolean intersects(Collection<T> collection1, Collection<T> collection2) {
		if (!collection1.isEmpty() && !collection2.isEmpty()) {
			for (var a : collection1) {
				if (collection2.contains(a)) {
					return true;
				}
			}
		}
		return false;
	}
}
