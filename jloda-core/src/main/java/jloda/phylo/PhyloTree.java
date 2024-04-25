/*
 * PhyloTree.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.*;
import jloda.util.Basic;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.Pair;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Phylogenetic tree, with support for rooted phylogenetic network
 * Daniel Huson, 2003
 */
public class PhyloTree extends PhyloSplitsGraph {
	public static boolean SUPPORT_RICH_NEWICK = false; // only SplitsTree6 should set this to true
	private Node root = null;

	private volatile EdgeSet reticulateEdges;
	private volatile NodeArray<List<Node>> lsaChildrenMap; // keep track of children in LSA tree in network
	private volatile EdgeSet transferAcceptorEdges;

	/**
	 * Construct a new empty phylogenetic tree.
	 */
	public PhyloTree() {
		super();
	}

	/**
	 * copy constructor
	 */
	public PhyloTree(PhyloTree src) {
		this();
		copy(src);
	}

	/**
	 * Clears the tree.
	 */
	public void clear() {
		super.clear();
		setRoot(null);
		reticulateEdges = null;
		transferAcceptorEdges = null;
		lsaChildrenMap = null;
	}

	public void clearReticulateEdges() {
		reticulateEdges = null;
	}

	public void clearTransferAcceptorEdges() {
		transferAcceptorEdges = null;
	}

	public void clearLsaChildrenMap() {
		lsaChildrenMap = null;
	}


	/**
	 * copies a phylogenetic tree
	 *
	 * @param src original tree
	 * @return old node to new node lap
	 */
	public NodeArray<Node> copy(PhyloTree src) {
		NodeArray<Node> oldNode2NewNode = src.newNodeArray();
		copy(src, oldNode2NewNode, null);
		return oldNode2NewNode;
	}

	/**
	 * copies a phylogenetic tree
	 */
	public void copy(PhyloTree src, NodeArray<Node> oldNode2NewNode, EdgeArray<Edge> oldEdge2NewEdge) {
		if (oldEdge2NewEdge == null)
			oldEdge2NewEdge = new EdgeArray<>(src);
		oldNode2NewNode = super.copy(src, oldNode2NewNode, oldEdge2NewEdge);
		if (src.getRoot() != null) {
			var root = src.getRoot();
			setRoot(oldNode2NewNode.get(root));
		}
		if (src.hasLSAChildrenMap()) {
			for (var v : src.nodes()) {
				var children = src.getLSAChildrenMap().get(v);
				if (children != null) {
					var newChildren = new ArrayList<Node>();
					for (var w : children) {
						newChildren.add(oldNode2NewNode.get(w));
					}
					getLSAChildrenMap().put(oldNode2NewNode.get(v), newChildren);
				}
			}
		}
		reticulateEdges = null;
		if (src.hasReticulateEdges()) {
			for (var e : src.getReticulateEdges()) {
				setReticulate(oldEdge2NewEdge.get(e), true);
			}
		}
		transferAcceptorEdges = null;
		if (src.hasTransferAcceptorEdges()) {
			for (var e : src.getTransferAcceptorEdges()) {
				setTransferAcceptor(oldEdge2NewEdge.get(e), true);
			}
		}
		setName(src.getName());
	}

	/**
	 * clones the current tree
	 *
	 * @return a clone of the current tree
	 */
	public Object clone() {
		var tree = new PhyloTree();
		tree.copy(this);
		return tree;
	}

	/**
	 * Produces a string representation of the tree in bracket notation.
	 *
	 * @return a string representation of the tree in bracket notation
	 */
	public String toBracketString() {
		return (new NewickIO()).toBracketString(this, true);
	}

	/**
	 * Produces a string representation of the tree in bracket notation.
	 *
	 * @return a string representation of the tree in bracket notation
	 */
	public String toBracketString(boolean showWeights) {
		return toBracketString(showWeights, null);

	}

	/**
	 * Produces a string representation of the tree in bracket notation.
	 *
	 * @return a string representation of the tree in bracket notation
	 */
	public String toBracketString(boolean showWeights, Map<String, String> translate) {
		return (new NewickIO()).toBracketString(this, showWeights, translate);
	}


	/**
	 * Parses a tree or network in Newick notation, and sets the root, if desired
	 */
	public void parseBracketNotation(String str, boolean rooted) throws IOException {
		(new NewickIO()).parseBracketNotation(this, str, rooted, true);
	}

	/**
	 * is v an unlabeled node of degree 2?
	 *
	 * @return true, if v is an unlabeled node of degree 2
	 */
	public boolean isUnlabeledDiVertex(Node v) {
		return v.getDegree() == 2 && (getLabel(v) == null || getLabel(v).isBlank());
	}

	/**
	 * deletes divertex
	 *
	 * @param v Node
	 * @return the new edge
	 */
	public Edge delDivertex(Node v) {
		if (v.getDegree() != 2)
			throw new RuntimeException("v not di-vertex, degree is: " + v.getDegree());

		var e = getFirstAdjacentEdge(v);
		var f = getLastAdjacentEdge(v);

		var x = getOpposite(v, e);
		var y = getOpposite(v, f);

		Edge g = null;
		try {
			if (x == e.getSource())
				g = newEdge(x, y);
			else
				g = newEdge(y, x);
		} catch (IllegalSelfEdgeException e1) {
			Basic.caught(e1);
		}
		if (getWeight(e) != Double.NEGATIVE_INFINITY && getWeight(f) != Double.NEGATIVE_INFINITY)
			setWeight(g, getWeight(e) + getWeight(f));
		if (hasEdgeConfidences())
			setConfidence(g, Math.min(getConfidence(e), getConfidence(f)));
		if (hasEdgeProbabilities())
			setProbability(g, Math.min(getProbability(e), getProbability(f)));
		if (root == v)
			root = null;
		deleteNode(v);
		return g;
	}

	public Node contract(Edge e) {
		var reroot = (e.getSource() == getRoot());
		var result = super.contract(e);
		if (reroot)
			setRoot(result);
		return result;
	}

	/**
	 * gets the root node if set, or null
	 *
	 * @return root or null
	 */
	public Node getRoot() {
		return root;
	}

	/**
	 * sets the root node
	 */
	public void setRoot(Node root) {
		this.root = root;
	}

	/**
	 * sets the root node in the middle of this edge
	 */
	public void setRoot(Edge e, EdgeArray<String> edgeLabels) {
		setRoot(e, getWeight(e) * 0.5, getWeight(e) * 0.5, edgeLabels);
	}

	/**
	 * sets the root node in the middle of this edge
	 *
	 * @param weightToSource weight for new edge adjacent to source of e
	 * @param weightToTarget weight for new adjacent to target of e
	 */
	public void setRoot(Edge e, double weightToSource, double weightToTarget, EdgeArray<String> edgeLabels) {
		final var root = getRoot();
		if (root != null && root.getDegree() == 2 && (getTaxa(root) == null || getNumberOfTaxa(root) == 0)) {
			if (root == e.getSource()) {
				var f = (root.getFirstAdjacentEdge() != e ? root.getFirstAdjacentEdge() : root.getLastAdjacentEdge());
				setWeight(e, weightToTarget);
				setWeight(f, weightToSource);
				return; // root stays root
			} else if (root == e.getTarget()) {
				var f = (root.getFirstAdjacentEdge() != e ? root.getFirstAdjacentEdge() : root.getLastAdjacentEdge());
				setWeight(e, weightToSource);
				setWeight(f, weightToTarget);
				return; // root stays root
			}
			eraseRoot(edgeLabels);
		}
		var v = e.getSource();
		var w = e.getTarget();
		var u = newNode();
		var vu = newEdge(v, u);
		var uw = newEdge(u, w);
		setWeight(vu, weightToSource);
		setWeight(uw, weightToTarget);
		if (edgeLabels != null) {
			edgeLabels.put(vu, edgeLabels.get(e));
			edgeLabels.put(uw, edgeLabels.get(e));
		}
		if (hasEdgeConfidences() && getEdgeConfidences().containsKey(e)) {
			setConfidence(vu, getConfidence(e));
			setConfidence(uw, getConfidence(e));
		}
		if (hasEdgeProbabilities() && getEdgeProbabilities().containsKey(e)) {
			setProbability(vu, getProbability(e));
			setProbability(uw, getProbability(e));
		}

		deleteEdge(e);
		setRoot(u);
	}

	/**
	 * erase the current root. If it has out-degree two and is not node-labeled, then two out edges will be replaced by single edge
	 *
	 * @param edgeLabels if non-null and root has two out edges, will try to copy one of the edge labels to the new edge
	 */
	public void eraseRoot(EdgeArray<String> edgeLabels) {
		final Node oldRoot = getRoot();
		setRoot(null);
		if (oldRoot != null) {
			if (getOutDegree(oldRoot) == 2 && getLabel(oldRoot) == null) {
				if (edgeLabels != null) {
					String label = null;
					for (Edge e = oldRoot.getFirstOutEdge(); e != null; e = oldRoot.getNextOutEdge(e)) {
						if (label == null && edgeLabels.get(e) != null)
							label = edgeLabels.get(e);
						edgeLabels.put(e, null);
					}
					final Edge e = delDivertex(oldRoot);
					edgeLabels.put(e, label);
				} else
					delDivertex(oldRoot);
			}
		}
	}


	/**
	 * returns true if string contains a bootstrap value
	 *
	 * @return true, if label contains a non-negative float
	 */
	public static boolean isBootstrapValue(String label) {
		try {
			return Float.parseFloat(label) >= 0;
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * is this bifurcating, do all nodes degree <=3?
	 *
	 * @return true, if binary
	 */
	public boolean isBifurcating() {
		return nodeStream().noneMatch(v -> v.getDegree() > 3);
	}

	/**
	 * is this a rooted network
	 *
	 * @return true, if is rooted network
	 */
	public boolean isReticulated() {
		return nodeStream().anyMatch(v -> v.getInDegree() >= 2);
	}

	/**
	 * compute weight of all edges
	 *
	 * @return sum of all none-negative edge weights
	 */
	public double computeTotalWeight() {
		return edgeStream().filter(e -> getWeight(e) > 0).mapToDouble(this::getWeight).sum();
	}

	/**
	 * given a rooted tree and a set of collapsed nodes, returns a tree that contains
	 * only the uncollapsed part of the tree
	 */
	public void extractTree(PhyloTree src, NodeSet collapsedNodes) {
		clear();
		if (src.getRoot() != null) {
			NodeArray<Node> oldNode2newNode = super.copy(src);

			if (getRoot() != null && oldNode2newNode != null) {
				setRoot(oldNode2newNode.get(src.getRoot()));
			}

			var toDelete = new NodeSet(this);
			toDelete.addAll();
			extractTreeRec(src.getRoot(), null, collapsedNodes, oldNode2newNode, toDelete);
			while (!toDelete.isEmpty()) {
				Node v = toDelete.getFirstElement();
				toDelete.remove(v);
				deleteNode(v);
			}
		}
	}

	/**
	 * recursively does the work
	 */
	private void extractTreeRec(Node v, Edge e, NodeSet collapsedNodes, NodeArray<Node> oldNode2newNode, NodeSet toDelete) {
		if (oldNode2newNode != null)
			toDelete.remove(oldNode2newNode.get(v));
		if (!collapsedNodes.contains(v)) {
			for (var f : v.adjacentEdges()) {
				if (f != e && this.okToDescendDownThisEdgeInTraversal(f, v)) {
					extractTreeRec(f.getOpposite(v), f, collapsedNodes, oldNode2newNode, toDelete);
				}
			}
		}
	}

	/**
	 * redirect edges away from root. Assumes that reticulate edges already point away from root
	 */
	public void redirectEdgesAwayFromRoot() {
		redirectEdgesAwayFromRootRec(getRoot(), null);

	}

	/**
	 * recursively does the work
	 */
	private void redirectEdgesAwayFromRootRec(Node v, Edge e) {
		if (e != null && v != e.getTarget() && !isReticulateEdge(e))
			e.reverse();
		for (var f : IteratorUtils.asList(v.adjacentEdges())) {
			if (f != e && this.okToDescendDownThisEdgeInTraversal(f, v))
				redirectEdgesAwayFromRootRec(f.getOpposite(v), f);
		}
	}

	/**
	 * gets the LSA-to-children map
	 *
	 * @return children of a node in the LSA tree
	 */
	public NodeArray<List<Node>> getLSAChildrenMap() {
		if (lsaChildrenMap == null) {
			synchronized (this) {
				if (lsaChildrenMap == null)
					lsaChildrenMap = newNodeArray();
			}
		}
		return lsaChildrenMap;
	}

	public boolean hasLSAChildrenMap() {
		return lsaChildrenMap != null;
	}

	/**
	 * iterable over all children, if tree, or all children in LSA tree, if network
	 *
	 * @param v node
	 * @return iterable
	 */
	public Iterable<Node> lsaChildren(Node v) {
		if (lsaChildrenMap != null && lsaChildrenMap.get(v) != null)
			return lsaChildrenMap.get(v);
		else
			return v.children();
	}

	public boolean isLeaf(Node v) {
		return v.getOutDegree() == 0;
	}

	/**
	 * determines whether this node is a leaf in tree, if tree, or in the LSA tree, if network
	 *
	 * @param v node
	 * @return true, if leaf in LSA tree
	 */
	public boolean isLsaLeaf(Node v) {
		return v.isLeaf() || lsaChildrenMap != null && lsaChildrenMap.get(v) != null && lsaChildrenMap.get(v).isEmpty();
	}

	/**
	 * gets the first out edge in the LSA tree
	 *
	 * @param v node
	 * @return first edge
	 */
	public Node getFirstChildLSA(Node v) {
		for (var first : lsaChildren(v))
			return first;
		return null;
	}

	/**
	 * gets the last out edge in the LSA tree
	 *
	 * @param v node
	 * @return last edge
	 */
	public Node getLastChildLSA(Node v) {
		Node last = null;
		for (Node node : lsaChildren(v))
			last = node;
		return last;
	}

	/**
	 * compute the cycle for this tree and then return it
	 *
	 * @return cycle for this tree
	 */
	public int[] getCycle(Node v) {
		computeCycleRec(v, null, 0);
		return getCycle();
	}

	/**
	 * recursively compute a cycle
	 */
	private int computeCycleRec(Node v, Edge e, int pos) {
		for (Integer t : getTaxa(v)) {
			setTaxon2Cycle(t, ++pos);
		}
		for (var f : v.adjacentEdges()) {
			if (f != e && this.okToDescendDownThisEdgeInTraversal(f, v))
				pos = computeCycleRec(f.getOpposite(v), f, pos);
		}
		return pos;
	}


	public boolean isTreeEdge(Edge e) {
		return !isReticulateEdge(e);
	}

	/**
	 * determines whether edge represents a transfer.
	 * This is the case if the edge is a reticulate edge and has non-positive weight
	 *
	 * @return true if transfer edge
	 */
	public boolean isTransferEdge(Edge e) {
		if (SUPPORT_RICH_NEWICK)
			return isReticulateEdge(e) && !isTransferAcceptorEdge(e) && e.getTarget().inEdgesStream(false).anyMatch(this::isTransferAcceptorEdge);
		else
			return isReticulateEdge(e) && getWeight(e) < 0.0;
	}

	/**
	 * applies method to all nodes in preorder traversal
	 *
	 * @param method method to apply
	 */
	public void preorderTraversal(Consumer<Node> method) {
		preorderTraversal(getRoot(), method);
	}

	/**
	 * performs a pre-order traversal at node v. If rooted network, will visit some nodes more than once
	 *
	 * @param v      the root node
	 * @param method method to apply
	 */
	public void preorderTraversal(Node v, Consumer<Node> method) {
		method.accept(v);
		for (var e : v.outEdges()) {
			preorderTraversal(e.getTarget(), method);
		}
	}

	/**
	 * performs a pre-order traversal at node v. If rooted network, will visit some nodes more than once
	 *
	 * @param v         the root node
	 * @param condition must evaluate to true for node to be visited
	 * @param method    method to apply
	 */
	public void preorderTraversal(Node v, Function<Node, Boolean> condition, Consumer<Node> method) {
		if (condition.apply(v)) {
			method.accept(v);
			for (var e : v.outEdges()) {
				preorderTraversal(e.getTarget(), condition, method);
			}
		}
	}

	/**
	 * applies method to all nodes in postorder traversal. If rooted network, will visit some nodes more than once
	 *
	 * @param method method to apply
	 */
	public void postorderTraversal(Consumer<Node> method) {
		postorderTraversal(getRoot(), method);
	}

	/**
	 * performs a post-order traversal at node v. If rooted network, will visit some nodes more than once
	 *
	 * @param v      the root node
	 * @param method method to apply
	 */
	public void postorderTraversal(Node v, Consumer<Node> method) {
		for (var e : v.outEdges()) {
			postorderTraversal(e.getTarget(), method);
		}
		method.accept(v);
	}

	/**
	 * performs a post-order traversal at node v. If rooted network, will visit some nodes more than once
	 *
	 * @param v         the root node
	 * @param condition must evaluate to true for node to be visited
	 * @param method    method to apply
	 */
	public void postorderTraversal(Node v, Function<Node, Boolean> condition, Consumer<Node> method) {
		if (condition.apply(v)) {
			for (var e : v.outEdges()) {
				postorderTraversal(e.getTarget(), condition, method);
			}
			method.accept(v);
		}
	}

	public void breathFirstTraversal(BiConsumer<Integer, Node> method) {
		breathFirstTraversal(getRoot(), 1, method);
	}

	public void breathFirstTraversal(Node v, int level, BiConsumer<Integer, Node> method) {
		method.accept(level, v);
		for (var e : v.outEdges()) {
			breathFirstTraversal(e.getTarget(), level + 1, method);
		}
	}

	/**
	 * is this a reticulated edge?
	 *
	 * @param e edge
	 * @return true, if marked as reticulate
	 */
	public boolean isReticulateEdge(Edge e) {
		return e != null && reticulateEdges != null && reticulateEdges.contains(e);
	}

	/**
	 * mark as reticulated or not
	 *
	 * @param e          edge
	 * @param reticulate is reticulate
	 */
	public void setReticulate(Edge e, boolean reticulate) {
		if (reticulate)
			getReticulateEdges().add(e);
		else if (reticulateEdges != null)
			getReticulateEdges().remove(e);
	}

	public EdgeSet getReticulateEdges() {
		if (reticulateEdges == null) {
			synchronized (this) {
				if (reticulateEdges == null) {
					reticulateEdges = newEdgeSet();
				}
			}
		}
		return reticulateEdges;
	}

	public boolean hasReticulateEdges() {
		return reticulateEdges != null && !reticulateEdges.isEmpty();
	}

	/**
	 * mark as acceptor or not
	 *
	 * @param e        edge
	 * @param acceptor is acceptor
	 */
	public void setTransferAcceptor(Edge e, boolean acceptor) {
		setReticulate(e, acceptor);
		if (acceptor)
			getTransferAcceptorEdges().add(e);
		else if (transferAcceptorEdges != null)
			getTransferAcceptorEdges().remove(e);
	}

	public boolean isTransferAcceptorEdge(Edge e) {
		if (SUPPORT_RICH_NEWICK)
			return transferAcceptorEdges != null && transferAcceptorEdges.contains(e);
		else
			return isReticulateEdge(e) && getWeight(e) > 0;
	}

	public EdgeSet getTransferAcceptorEdges() {
		if (transferAcceptorEdges == null) {
			synchronized (this) {
				if (transferAcceptorEdges == null) {
					transferAcceptorEdges = newEdgeSet();
				}
			}
		}
		return transferAcceptorEdges;
	}

	public boolean hasTransferAcceptorEdges() {
		return transferAcceptorEdges != null && !transferAcceptorEdges.isEmpty();
	}

	/**
	 * gets the number of reticulate edges
	 *
	 * @return number of reticulate edges
	 */
	public int getNumberReticulateEdges() {
		return reticulateEdges == null ? 0 : reticulateEdges.size();
	}

	/**
	 * iterable over all reticulate edges
	 */
	public Iterable<Edge> reticulateEdges() {
		return reticulateEdges != null ? reticulateEdges : Collections.emptySet();
	}

	/**
	 * determines whether it is ok to descend an edge in a recursive
	 * traverse of a tree. Use this to ensure that each node is visited only once
	 *
	 * @return true, if we should descend this edge, false else
	 */
	public boolean okToDescendDownThisEdgeInTraversal(Edge e, Node v) {
		if (!isReticulateEdge(e))
			return true;
		else {
			if (v != e.getSource())
				return false; // only go DOWN reticulate edges.
			return e == e.getTarget().inEdgesStream(false).filter(this::isReticulateEdge).findFirst().orElse(null);
		}
	}

	/**
	 * determines whether it is ok to descend an edge in a recursive
	 * traverse of a tree. Use this to ensure that each node is visited only once
	 */
	public boolean okToDescendDownThisEdgeInTraversal(Edge e) {
		if (!isReticulateEdge(e))
			return true;
		else {
			return e == e.getTarget().inEdgesStream(false).filter(this::isReticulateEdge).findFirst().orElse(null);
		}
	}

	/**
	 * get edge that corresponds to the split
	 *
	 * @param partA one side of split
	 * @param partB other slide of split
	 * @return separating edge, if it exists, otherwise null
	 */
	public Edge getEdgeForSplit(BitSet partA, BitSet partB) {
		var e = getEdgeForCluster(partA);
		if (e == null)
			e = getEdgeForCluster(partB);
		return e;
	}

	/**
	 * get the edge that separates the given cluster from other taxa
	 *
	 * @param cluster the taxa to be separated
	 * @return separating edge or null
	 */
	public Edge getEdgeForCluster(BitSet cluster) {
		var pair = getEdgeForTaxaRec(getRoot(), cluster);
		return pair == null ? null : pair.getFirst();
	}

	private Pair<Edge, BitSet> getEdgeForTaxaRec(Node v, BitSet cluster) {
		var here = new BitSet();
		for (var t : getTaxa(v)) {
			here.set(t);
		}
		if (!BitSetUtils.contains(cluster, here))
			return null;

		var hasBadChild = false;
		for (var w : v.children()) {
			var pair = getEdgeForTaxaRec(w, cluster);
			if (pair == null)
				hasBadChild = true;
			else if (pair.getFirst() != null) // result contains seeked edge
				return pair;
			else
				here.or(pair.getSecond());
		}
		if (hasBadChild)
			return null;
		else if (here.equals(cluster))
			return new Pair<>(v.getFirstInEdge(), here);
		else
			return new Pair<>(null, here);
	}
}

// EOF
