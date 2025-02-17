/*
 * LSAUtils.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.phylo;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeSet;
import jloda.graph.algorithms.BiconnectedComponents;
import jloda.util.CollectionUtils;
import jloda.util.IteratorUtils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * some utilities for working with the LSA tree associated with a rooted network
 * Daniel Huson, 12.2021
 */
public class LSAUtils {
	/**
	 * extracts the LSA tree from a rooted network
	 *
	 * @param tree the network
	 * @return lsa tree
	 */
	public static PhyloTree getLSATree(PhyloTree tree) {
		if (tree.isReticulated()) {
			var lsaTree = new PhyloTree(tree);
			for (var v : lsaTree.nodes()) {
				var lsaChildren = IteratorUtils.asList(lsaTree.lsaChildren(v));
				var edges = new ArrayList<Edge>();
				for (var w : lsaChildren) {
					if (v.getEdgeTo(w) == null) {
						var e = lsaTree.newEdge(v, w);
						lsaTree.setWeight(e, 1);
						edges.add(e);
					} else
						edges.add(v.getEdgeTo(w));
				}
				var toDelete = v.outEdgesStream(false).filter(e -> !edges.contains(e)).toList();
				toDelete.forEach(lsaTree::deleteEdge);
				v.rearrangeAdjacentEdges(edges);
			}
			return lsaTree;
		} else
			return tree;
	}

	/**
	 * performs a pre-order traversal at node v using the LSA tree, if defined. Makes sure that a node is visited only once
	 * its parents both in the LSA and in the network have been visited.
	 * Assumes the lsa children mapping has been set inside tree
	 *
	 * @param tree   the tree or network (with embedded LSA tree)
	 * @param v      the root node
	 * @param method method to apply
	 */
	public static void preorderTraversalLSA(PhyloTree tree, Node v, Consumer<Node> method) {
		if (!tree.isReticulated())
			tree.preorderTraversal(v, method);
		else {
			preorderTraversalLSA(v, tree.getLSAChildrenMap(), method);
		}
	}

	/**
	 * performs a pre-order traversal at node v using the LSA children map. Makes sure that a node is visited only once
	 * its parents both in the LSA and in the network have been visited
	 *
	 * @param v           the root node
	 * @param lsaChildren the node to list of children in the LSA tree
	 * @param method      method to apply
	 */
	public static void preorderTraversalLSA(Node v, Map<Node, List<Node>> lsaChildren, Consumer<Node> method) {
		try (var visited = v.getOwner().newNodeSet()) {
			var queue = new LinkedList<Node>();
			queue.add(v);
			while (!queue.isEmpty()) {
				v = queue.pop();
				if (visited.containsAll(IteratorUtils.asList(v.parents()))) {
					method.accept(v);
					visited.add(v);
					queue.addAll(lsaChildren.get(v));
				} else
					queue.add(v);
			}
		}
	}

	/**
	 * performs a post-order traversal at node v, using the LSA tree, if defined
	 *
	 * @param tree   the tree or network
	 * @param v      the root node
	 * @param method method to apply
	 */
	public static void postorderTraversalLSA(PhyloTree tree, Node v, Consumer<Node> method) {
		if (!tree.isReticulated())
			tree.postorderTraversal(v, method);
		else {
			postorderTraversalLSA(v,tree.getLSAChildrenMap(), method);
		}
	}

	/**
	 * performs a post-order traversal at node v, using the LSA tree, if defined
	 *
	 * @param v           the root node
	 * @param lsaChildren the node to list of children in the LSA tree
	 * @param method      method to apply
	 */
	public static void postorderTraversalLSA(Node v, Map<Node, List<Node>> lsaChildren, Consumer<Node> method) {
		for (var w : lsaChildren.get(v)) {
			postorderTraversalLSA(w, lsaChildren, method);
		}
		method.accept(v);
	}


	public static void breathFirstTraversalLSA(PhyloTree tree, Node v, int level, BiConsumer<Integer, Node> method) {
		method.accept(level, v);
		for (var w : tree.lsaChildren(v)) {
			breathFirstTraversalLSA(tree, w, level + 1, method);
		}
	}

	/**
	 * given a reticulate network, returns a mapping of each LSA node to its children in the LSA tree
	 *
	 * @param tree             the tree
	 */
	public static NodeArray<List<Node>> computeLSAChildrenMap(PhyloTree tree) {
		try (NodeArray<Node> reticulation2LSA = tree.newNodeArray()) {
			return computeLSAChildrenMap(tree, reticulation2LSA);
		}
	}

	public static void setLSAChildrenMap(PhyloTree tree) {
		try (NodeArray<Node> reticulation2LSA = tree.newNodeArray();
			 var lsaChildrenMap = computeLSAChildrenMap(tree, reticulation2LSA)) {
			for (var v : tree.nodes()) {
				tree.getLSAChildrenMap().put(v, lsaChildrenMap.get(v));
			}
		}
	}

	/**
	 * computes the  reticulate node to LSA mapping
	 *
	 * @param lsaChildren
	 * @return mapping
	 */
	public static Map<Node, Node> computeReticulation2LSA(NodeArray<List<Node>> lsaChildren) {
		var tree = (PhyloTree) lsaChildren.getOwner();
		var lsaNodes = new HashSet<Node>();
		for (var v : lsaChildren.keySet()) {
			for (var w : tree.lsaChildren(v)) {
				if (!v.isChild(w))
					lsaNodes.add(v);
			}
		}
		var reticulation2LSA = new HashMap<Node, Node>();
		for (var r : tree.nodeStream().filter(v -> v.getInDegree() >= 2).toList()) {
			var p = r.getParent();
			while (p != null) {
				if (lsaNodes.contains(p)) {
					reticulation2LSA.put(r, p);
					break;
				} else p = p.getParent();
			}
		}
		if (false) {
			for (var r : tree.nodeStream().filter(v -> v.getInDegree() == 1).toList()) {
				reticulation2LSA.put(r, r.getParent());
			}
		}
		return reticulation2LSA;
	}

	/**
	 * given a reticulate network, returns a mapping of each LSA node to its children in the LSA tree
	 *
	 * @param tree             the tree
	 * @param reticulation2LSA is returned here
	 * @return node to children map
	 */
	public static NodeArray<List<Node>> computeLSAChildrenMap(PhyloTree tree, NodeArray<Node> reticulation2LSA) {
		final NodeArray<List<Node>> lsaChildrenMap = tree.newNodeArray();
		tree.getLSAChildrenMap().clear();

		if (tree.getRoot() != null) {
			// first we compute the reticulate node to lsa node mapping:
			computeReticulation2LSA(tree, reticulation2LSA);

			for (var v : tree.nodes()) {
				var children = v.outEdgesStream(false).map(Edge::getTarget)
						.filter(target -> target.getInDegree() == 1).collect(Collectors.toList());
				tree.getLSAChildrenMap().put(v, children);
				lsaChildrenMap.put(v,children);
			}
			for (var v : tree.nodes()) {
				var lsa = reticulation2LSA.get(v);
				if (lsa != null)
					lsaChildrenMap.get(lsa).add(v);
			}
		}
		return lsaChildrenMap;
	}

	/**
	 * compute the reticulation-to-lsa mapping
	 *
	 * @param tree             the rooted network
	 * @param reticulation2LSA the reticulation to LSA mapping
	 */
	public static void computeReticulation2LSA(PhyloTree tree, NodeArray<Node> reticulation2LSA) {
		reticulation2LSA.clear();

		var reticulateNodes = tree.nodeStream().filter(v -> v.getInDegree() >= 2).toList();
		try (var lsaNodes = computeAllLowestStableAncestors(tree, reticulateNodes)) {
			for (var v : reticulateNodes) {
				var stack = new Stack<Node>();
				for (var e : v.inEdges()) {
					stack.push(e.getSource());
				}
				while (!stack.isEmpty()) {
					var w = stack.pop();
					if (lsaNodes.contains(w)) {
						reticulation2LSA.put(v, w);
						break;
					} else {
						for (var e : w.inEdges()) {
							stack.push(e.getSource());
						}
					}
				}
			}
		}
	}

	public static NodeSet computeAllLowestStableAncestors(PhyloTree graph, Collection<Node> query) {
		if (query == null)
			query = graph.nodeStream().filter(v -> v.getInDegree() >= 2).toList();
		var nodes = graph.newNodeSet();

		nodes.addAll(query.stream().filter(v -> v.getInDegree() == 1).map(Node::getParent).toList());
		var reticulateNodes = query.stream().filter(v -> v.getInDegree() > 1).toList();

		for (var component : BiconnectedComponents.apply(graph)) {
			if (CollectionUtils.intersects(component, reticulateNodes)) {
				var top = component.stream().filter(v -> v.getInDegree() == 0 || !component.containsAll(IteratorUtils.asSet(v.parents()))).findFirst().orElse(graph.getRoot());
				for (var v : reticulateNodes) {
					var above = new HashSet<Node>();
					var stack = new Stack<Node>();
					stack.push(v);
					while (!stack.isEmpty()) {
						var w = stack.pop();
						above.add(w);
						if (w != top) {
							stack.addAll(IteratorUtils.asList(w.parents()));
						}
					}
					var w = top;
					while (w != v && CollectionUtils.intersection(above, IteratorUtils.asSet(w.children())).size() == 1) {
						w = CollectionUtils.intersection(above, IteratorUtils.asSet(w.children())).iterator().next();
					}
					nodes.add(w);
				}

				if (false)
					component.stream().filter(v -> v.getInDegree() == 0 || !component.containsAll(IteratorUtils.asSet(v.parents()))).forEach(nodes::add);
			}
		}
		return nodes;
	}
}
