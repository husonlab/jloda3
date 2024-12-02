/*
 * RootedNetworkProperties.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.phylo.algorithms;

import jloda.graph.*;
import jloda.graph.algorithms.BiconnectedComponents;
import jloda.graph.algorithms.CutPoints;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressSilent;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * computes network properties
 * Daniel Huson, 1.2020
 */
public class RootedNetworkProperties {
    /**
     * is this a non-empty forest?
     *
     * @return true, if non-empty forest
     */
    public static boolean isNonEmptyForest(Graph graph) {
        if (graph.getNumberOfNodes() == 0)
            return false;
        try (var visited = new NodeSet(graph)) {
            var queue = new LinkedList<>(findRoots(graph));
            while (!queue.isEmpty()) {
                var w = queue.remove();
                if (visited.contains(w))
                    return false;
                else
                    visited.add(w);
                queue.addAll(IteratorUtils.asList(w.children()));
            }
        }
        return true;
    }

    /**
     * is this a non-empty DAG?
     *
     * @return true, if non-empty DAG
     */
    public static boolean isNonEmptyDAG(Graph graph) {
        if (graph.getNumberOfNodes() == 0)
            return false;
        final var g = new Graph();
        g.copy(graph);

        while (g.getNumberOfNodes() > 0) {
            var found = false;
            for (var v : g.nodes()) {
                if (v.getOutDegree() == 0) {
                    g.deleteNode(v);
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
        }
        return true;
    }

    /**
     * are all leaves labeled?
     *
     * @return true, if all leaves are labeled
     */
    public static boolean isLeafLabeled(Graph graph) {
        return graph.nodeStream().noneMatch(v -> v.getOutDegree() == 0 && (graph.getLabel(v) == null || (graph.getLabel(v).isEmpty())));
    }

    /**
     * find all roots
     *
     * @return list of roots of this graph
     */
    public static List<Node> findRoots(Graph graph) {
        return graph.nodeStream().filter(v -> v.getInDegree() == 0).collect(Collectors.toList());
    }

    /**
     * compute label to node mapping
     *
     * @return label to node mapping
     */
    public static Map<String, Node> getLabel2Node(Graph graph) {
        var map = new TreeMap<String, Node>();
        for (var v : graph.nodes()) {
            var label = graph.getLabel(v);
            if (label != null)
                map.put(label, v);
        }
        return map;
    }

    /**
     * compute node to label mapping
     *
     * @return node to label mapping
     */
    public static NodeArray<String> getNode2Label(Graph graph) {
        var map = new NodeArray<String>(graph);
        for (var v : graph.nodes()) {
            var label = graph.getLabel(v);
            if (label != null)
                map.put(v, label);
        }
        return map;
    }

    /**
     * is this graph a tree-child network?
     *
     * @return true, if tree or tree-child network
     */
    public static boolean isTreeChild(PhyloTree graph) {
        for (var v : graph.nodes()) {
            if (v.getOutDegree() > 0) {
                var ok = false;
                for (var w : v.children()) {
                    if (w.getInDegree() == 1) {
                        ok = true;
                        break;
                    }
                }
                if (!ok)
                    return false;
            }
        }
        return true;
    }

    /**
     * compute all visible nodes
     *
     * @return set of visible nodes
     */
    public static NodeSet computeAllVisibleNodes(PhyloTree graph, Collection<Node> roots) {
        if (roots == null)
            roots = graph.nodeStream().filter(v -> v.getInDegree() == 0).collect(Collectors.toList());

        var result = graph.newNodeSet();

        for (var root : roots) {
            result.add(root);

            try (NodeArray<BitSet> leavesBelow = graph.newNodeArray()) {
                depthFirstDAG(root, v -> {
                    if (v.getOutDegree() == 0) {
                        leavesBelow.put(v, BitSetUtils.asBitSet(v.getId()));
                        result.add(v);
                    } else {
                        var set = new BitSet();
                        for (var w : v.children()) {
                            set.or(leavesBelow.get(w));
                        }
                        leavesBelow.put(v, set);
                    }
                });

                for (var nodeId : BitSetUtils.members(leavesBelow.get(root))) {
                    result.addAll(CutPoints.apply(graph, v -> leavesBelow.get(v).get(nodeId)));
                }
            }
        }
        return result;
    }

    /**
     * perform depth-first DAG traversal, visiting some nodes multiple times
     *
     * @param root        root node
     * @param calculation calculation to be performed
     */
    public static void depthFirstDAG(Node root, Consumer<Node> calculation) {
        for (var w : root.children()) {
            depthFirstDAG(w, calculation);
        }
        calculation.accept(root);
    }

    private static void computeAllVisibleNodesRec(Node v, NodeSet stableNodes, NodeSet result) {
        if (stableNodes.contains(v))
            result.add(v);

        for (var w : v.children()) {
            computeAllVisibleNodesRec(w, stableNodes, result);
            if (w.getInDegree() == 1 && (result.contains(w) || w.getOutDegree() == 0)) {
                result.add(v);
            }
        }
    }

    /**
     * determines all completely stable nodes, which are nodes that lie on all paths to all of their children
     */
    public static NodeSet computeAllCompletelyStableInternal(PhyloTree graph) {
        var result = graph.newNodeSet();

        if (isNonEmptyDAG(graph)) {
            for (var root : findRoots(graph))
                computeAllCompletelyStableInternalRec(root, new HashSet<>(), new HashSet<>(), result);
        }
        return result;
    }

    /**
     * recursively determines all stable nodes
     */
    private static void computeAllCompletelyStableInternalRec(Node v, Set<Node> below, Set<Node> parentsOfBelow, NodeSet result) {
        if (v.getOutDegree() == 0) {
            below.add(v);
            parentsOfBelow.addAll(IteratorUtils.asList(v.parents()));
        } else {
            var belowV = new HashSet<Node>();
            var parentsOfBelowV = new HashSet<Node>();

            for (var w : v.children()) {
                computeAllCompletelyStableInternalRec(w, belowV, parentsOfBelowV, result);
            }
            belowV.forEach(u -> parentsOfBelowV.addAll(IteratorUtils.asList(u.parents())));
            belowV.add(v);

            if (belowV.containsAll(parentsOfBelowV)) {
                result.add(v);
            }
            below.addAll(belowV);
            parentsOfBelow.addAll(parentsOfBelowV);
        }
    }

    public static NodeSet computeAllLowestStableAncestors(PhyloTree graph, Collection<Node> query) {
        var nodes = graph.newNodeSet();

        nodes.addAll(query.stream().filter(v -> v.getInDegree() == 1).map(Node::getParent).toList());
        var reticulateNodes = query.stream().filter(v -> v.getInDegree() > 1).toList();

        for (var component : BiconnectedComponents.apply(graph)) {
            if (CollectionUtils.intersects(component, reticulateNodes)) {
                component.stream().filter(v -> v.getInDegree() == 0 || !component.containsAll(IteratorUtils.asSet(v.parents()))).forEach(nodes::add);
            }
        }
        return nodes;
    }

    /**
     * is the tree a temporal network?
     *
     * @return true, if temporal
     */
    public static boolean isTemporal(PhyloTree tree) {
        try {
            var contractedGraph = new PhyloTree(tree);

            var reticulateEdges = contractedGraph.edgeStream().filter(e -> e.getTarget().getInDegree() > 1).collect(Collectors.toSet());

            if (reticulateEdges.isEmpty())
                return true;
            else {
                var selfEdgeEncountered = new Single<>(false);
                contractEdges(contractedGraph, reticulateEdges, selfEdgeEncountered);
                return !selfEdgeEncountered.get() && isNonEmptyDAG(contractedGraph);
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * is this rooted phylogenetic network tree-based?
     *
     * @return true, if tree-based
     */
    public static boolean isTreeBased(PhyloTree tree) {
        try (var match = OffspringGraphMatching.compute(tree, new ProgressSilent())) {
            return OffspringGraphMatching.isTreeBased(tree, match);
        } catch (CanceledException neverHappens) {
            return false;
        }
    }

    /**
     * compute an info string
     *
     * @return info string
     */
    public static String computeInfoString(PhyloTree phyloTree) {
        if (phyloTree == null)
            return "null";
        else if (phyloTree.isReticulated()) {
            var buf = new StringBuilder(String.format("nodes=%,d edges=%,d leaves=%,d h=%,d",
                    phyloTree.getNumberOfNodes(), phyloTree.getNumberOfEdges(),
                    phyloTree.nodeStream().filter(Node::isLeaf).count(),
                    phyloTree.nodeStream().filter(v -> v.getInDegree() > 1).mapToInt(v -> v.getInDegree() - 1).sum()));

            if (isTreeBased(phyloTree))
                buf.append(" tree-based");
            else if (isTreeChild(phyloTree))
                buf.append(" tree-child");
            if (isTemporal(phyloTree))
                buf.append(" temporal");
            buf.append(" network");
            return buf.toString();
        } else {
            return String.format("nodes=%d edges=%d leaves=%d",
                    phyloTree.getNumberOfNodes(), phyloTree.getNumberOfEdges(),
                    phyloTree.nodeStream().filter(Node::isLeaf).count());
        }
    }

    /**
     * contracts all edges below min length
     *
     * @return true, if anything contracted
     */
    public static boolean contractShortEdges(PhyloTree tree, double minLength) {
        return contractEdges(tree, tree.edgeStream().filter(e -> tree.getWeight(e) < minLength).collect(Collectors.toSet()), null);
    }

    /**
     * contracts a set of edges
     *
     * @return true, if anything contracted
     */
    public static boolean contractEdges(PhyloTree tree, Set<Edge> edgesToContract, Single<Boolean> selfEdgeEncountered) {
        boolean hasContractedOne = !edgesToContract.isEmpty();

        while (!edgesToContract.isEmpty()) {
            final var e = edgesToContract.iterator().next();
            edgesToContract.remove(e);

            final var v = e.getSource();
            final var w = e.getTarget();

            for (Edge f : v.adjacentEdges()) { // will remove e from edgesToContract here
                if (f != e) {
                    final var u = f.getOpposite(v);
                    final var needsContracting = edgesToContract.contains(f);
                    if (needsContracting)
                        edgesToContract.remove(f);

                    if (u != w) {
                        final Edge z;
                        if (u == f.getSource())
                            z = tree.newEdge(u, w);
                        else
                            z = tree.newEdge(w, u);
                        if (tree.hasEdgeWeights())
                            tree.setWeight(z, tree.getWeight(f));
                        if (tree.hasEdgeConfidences())
                            tree.setConfidence(z, tree.getConfidence(f));
                        if (tree.hasEdgeProbabilities())
                            tree.setProbability(z, tree.getProbability(f));

                        tree.setLabel(z, tree.getLabel(z));
                        if (needsContracting) {
                            edgesToContract.add(z);
                        }
                    } else if (selfEdgeEncountered != null)
                        selfEdgeEncountered.set(true);
                }
            }
            for (var taxon : tree.getTaxa(v))
                tree.addTaxon(w, taxon);

            if (tree.getRoot() == v)
                tree.setRoot(w);

            tree.deleteNode(v);
        }

        return hasContractedOne;
    }
}
