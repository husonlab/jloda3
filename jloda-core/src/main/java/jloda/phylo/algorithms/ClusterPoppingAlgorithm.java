/*
 * ClusterPoppingAlgorithm.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;

import java.util.*;
import java.util.function.Function;

/**
 * runs the cluster popping algorithm to create a rooted tree or network from a set of clusters
 * Daniel Huson, 8.2022
 */
public class ClusterPoppingAlgorithm {
	/**
	 * runs the cluster popping algorithm to create a rooted tree or network from a set of clusters
	 *
	 * @param clusters input clusters
	 * @param network  the resulting network
	 */
	public static void apply(Collection<BitSet> clusters, PhyloTree network) {
		apply(clusters, null, null, network);
	}

	/**
	 * runs the cluster popping algorithm to create a rooted tree or network from a set of clusters
	 *
	 * @param clusters       input clusters
	 * @param weightFunction weights for clusters, may be null
	 * @param network        the resulting network
	 */
	public static void apply(Collection<BitSet> clusters, Function<BitSet, Double> weightFunction, PhyloTree network) {
		apply(clusters, weightFunction, null, network);
	}

	/**
	 * runs the cluster popping algorithm to create a rooted tree or network from a set of clusters
	 *
	 * @param clusters0          input clusters
	 * @param weightFunction     weights for clusters, may be null
	 * @param confidenceFunction confidences for clusters, may be null
	 * @param network            the resulting network
	 */
	public static void apply(Collection<BitSet> clusters0, Function<BitSet, Double> weightFunction, Function<BitSet, Double> confidenceFunction, PhyloTree network) {
		network.clear();

		if (!clusters0.isEmpty()) {
			var clusters = new ArrayList<>(clusters0);
			clusters.sort((a, b) -> -Integer.compare(a.cardinality(), b.cardinality())); // sorted by decreasing size

			var taxa = BitSetUtils.union(clusters);

			try (NodeArray<BitSet> nodeClusterMap = network.newNodeArray(); var visited = network.newNodeSet()) {
				network.setRoot(network.newNode());
				nodeClusterMap.put(network.getRoot(), taxa);

				if (clusters.get(0).cardinality() == taxa.cardinality()) {
					var missingTaxa = BitSetUtils.minus(taxa, BitSetUtils.union(clusters.subList(1, clusters.size())));
					for (var t : BitSetUtils.members(missingTaxa)) {
						clusters.add(BitSetUtils.asBitSet(t));
					}
				}

				for (var cluster : clusters) {
					var clusterNode = network.newNode();
					nodeClusterMap.put(clusterNode, cluster);
					visited.clear();

					if (network.getNumberOfNodes() > 1 || cluster.cardinality() < taxa.cardinality()) { // skip first cluster if it contains all taxa
						var stack = new Stack<Node>();
						stack.push(network.getRoot());
						while (!stack.isEmpty()) {
							var v = stack.pop();
							var isBelowAChild = false;
							for (var w : v.children()) {
								var clusterW = nodeClusterMap.get(w);
								if (BitSetUtils.contains(clusterW, cluster)) {
									isBelowAChild = true;
									if (!visited.contains(w))
										stack.push(w);
								}
							}
							if (!isBelowAChild && v != clusterNode)
								network.newEdge(v, clusterNode);
						}
					}
				}

				// make sure no node has indegree>1 and outdegree>1
				for (var v : network.nodeStream().filter(v -> v.getInDegree() > 1 && v.getOutDegree() > 1).toList()) {
					var above = network.newNode();
					for (var inEdge : IteratorUtils.asList(v.inEdges())) {
						network.newEdge(inEdge.getSource(), above);
						network.deleteEdge(inEdge);
					}
					network.newEdge(above, v);
				}

				// make sure we have all leaf edges:
				if (true)
					for (var v : IteratorUtils.asList(network.leaves())) {
						var cluster = nodeClusterMap.get(v);
						if (cluster.cardinality() == 1) {
							network.addTaxon(v, cluster.nextSetBit(1));
						} else {
							for (var t : BitSetUtils.members(cluster)) {
								var w = network.newNode();
								network.newEdge(v, w);
								network.addTaxon(w, t);
							}
						}
					}

				if (weightFunction != null)
					network.nodeStream().filter(v -> v.getInDegree() == 1)
							.forEach(v -> {
								var weight = -1d;
								var cluster = nodeClusterMap.get(v);
								if (cluster != null)
									weight = weightFunction.apply(cluster);
								if (weight != -1d)
									network.setWeight(v.getFirstInEdge(), weight);
							});
				if (confidenceFunction != null)
					network.nodeStream().filter(v -> v.getInDegree() == 1)
							.forEach(v -> {
								var confidence = -1d;
								var cluster = nodeClusterMap.get(v);
								if (cluster != null)
									confidence = confidenceFunction.apply(nodeClusterMap.get(v));
								if (confidence != -1)
									network.setConfidence(v.getFirstInEdge(), confidence);
							});

				network.edgeStream().filter(e -> e.getTarget().getInDegree() > 1).forEach(e -> network.setReticulate(e, true));
			}
		}
	}

	public static void main(String[] args) {
		var clusters = List.of(BitSetUtils.asBitSet(13, 14, 15), BitSetUtils.asBitSet(14, 15));
		var tree = new PhyloTree();
		apply(clusters, tree);
		tree.nodeStream().filter(v -> tree.getNumberOfTaxa(v) > 0).forEach(v -> tree.setLabel(v, "t" + tree.getTaxon(v)));

		{
			System.err.println("Clusters:");
			for (var cluster : clusters) {
				System.err.println(StringUtils.toString(cluster, ","));
			}
			System.err.println("Resulting tree: " + NewickIO.toString(tree, false));
		}
	}
}
