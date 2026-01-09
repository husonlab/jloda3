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

package jloda.phylogeny.layout;

import jloda.phylogeny.dolayout.NetworkDisplacementOptimization;
import jloda.phylogeny.utils.LsaUtils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import static jloda.phylogeny.utils.GraphUtils.postOrderTraversal;

/**
 * computes the layout of a rooted phylogenetic tree or network
 * Daniel Huson, 12.2021, 3.2025
 */
public class LayoutRootedPhylogeny {
	public enum Layout {Rectangular, Circular, Radial, Triangular}

	public enum Scaling {ToScale, EarlyBranching, LateBranching}

	/**
	 * compute coordinates for a rooted tree or network
	 *
	 * @param root                    the root node
	 * @param nodes                   all nodes
	 * @param edges                   all edges
	 * @param inEdges                 gets node in edges
	 * @param outEdges                gets node out edges
	 * @param source                  gets source of edge
	 * @param target                  gets target of edges
	 * @param weight                  gets weight of edge
	 * @param edgeType                gets type of edge
	 * @param layout                  desired layout
	 * @param scaling                 desired scaling
	 * @param averaging               desired averaging
	 * @param optimizeReticulateEdges should reticulate edges be displacement optimized?
	 * @param random                  random number source
	 * @param nodeAngleMap            returns node to angle map
	 * @param nodePointMap            returns node locations
	 * @param <Node>                  generic node type
	 * @param <Edge>                  generic edge type
	 */
	public static <Node, Edge> void apply(Node root, List<Node> nodes, List<Edge> edges,
										  Function<Node, List<Edge>> inEdges,
										  Function<Node, List<Edge>> outEdges,
										  Function<Edge, Node> source, Function<Edge, Node> target,
										  ToDoubleFunction<Edge> weight,
										  Function<Edge, EdgeType> edgeType,
										  Layout layout, Scaling scaling, Averaging averaging, boolean optimizeReticulateEdges, Random random,
										  Map<Node, Double> nodeAngleMap, Map<Node, Point2D> nodePointMap, Map<Node, List<Node>> lsaChildren) {

		if (layout == Layout.Radial && averaging == Averaging.ChildAverage) {
			averaging = Averaging.LeafAverage; // leaf averaging not valid for radial layout
		}

		var hasReticulations = nodes.stream().anyMatch(v -> inEdges.apply(v).size() > 1);


		// setup lsa map:
		if (lsaChildren.size() < nodes.size()) {
			if (hasReticulations) {
				var map = LsaUtils.computeLSAChildrenMap(root, nodes, inEdges, outEdges, source, target);
				lsaChildren.clear();
				lsaChildren.putAll(map);
			} else {
				lsaChildren.clear();
				for (var v : nodes) {
					lsaChildren.put(v, outEdges.apply(v).stream().map(target).toList());
				}
			}
		}

		HashMap<Edge, EdgeType> edgeTypeMap;
		if (edgeType == null) {
			edgeTypeMap = new HashMap<>();
			edgeType = edgeTypeMap::get;
		} else edgeTypeMap = null;


		if (optimizeReticulateEdges && hasReticulations) {
			var reticulateMap = new HashMap<Node, List<Node>>();
			for (var e : edges) {
				if (edgeTypeMap != null) {
					edgeTypeMap.put(e, inEdges.apply(target.apply(e)).size() <= 1 ? EdgeType.tree : EdgeType.combining);
				}
				var type = edgeType.apply(e);

				if (type == EdgeType.combining) {
					reticulateMap.computeIfAbsent(source.apply(e), k -> new ArrayList<>()).add(target.apply(e));
					reticulateMap.computeIfAbsent(target.apply(e), k -> new ArrayList<>()).add(source.apply(e));
				}
			}
			var circular = (layout != Layout.Rectangular);
			var result = NetworkDisplacementOptimization.apply(root, lsaChildren::get, reticulateMap::get, circular, random, () -> false);
			lsaChildren.clear();
			lsaChildren.putAll(result);
		}

		nodeAngleMap.clear();
		nodePointMap.clear();

		if (hasReticulations && layout == Layout.Triangular) {
			System.err.println("Triangular layout does not work well on reticulations");
		}
		if (layout == Layout.Triangular) {
			Function<Node, List<Node>> children = v -> outEdges.apply(v).stream().map(target).toList();
			TriangularTreeLayout.apply(root, children, lsaChildren::get, nodePointMap);
			return;
		}

		if (scaling != Scaling.ToScale) {
			LayoutRectangularCladogram.apply(root, nodes, edges, lsaChildren::get, outEdges, source, target, edgeType, averaging, nodePointMap);
		} else if (layout == Layout.Radial) {
			LayoutRadialPhylogram.apply(root, edges, outEdges, lsaChildren::get, source, target, weight, edgeType, averaging, nodeAngleMap, nodePointMap);
		} else {
			LayoutRectangularPhylogram.apply(root, nodes, edges, inEdges, outEdges, lsaChildren::get, source, target, weight, edgeType, averaging, nodePointMap);
		}

		if (scaling == Scaling.LateBranching) {
			modifyToLateBranching(root, inEdges, outEdges, target, edgeType, v -> nodePointMap.get(v).x(), (v, x) -> nodePointMap.put(v, new Point2D(x, nodePointMap.get(v).y())));
		}

		if (layout == Layout.Circular || (layout == Layout.Radial && scaling != Scaling.ToScale)) {
			nodeAngleMap.clear();
			var nodeRadiusMap = new HashMap<Node, Double>();
			var rootOffset = nodePointMap.get(root).x();
			for (var v : nodes) {
				nodeRadiusMap.put(v, nodePointMap.get(v).x() - rootOffset);
			}
			Function<Node, List<Node>> children = v -> outEdges.apply(v).stream().map(target).toList();
			HeightAndAngles.computeAngles(root, children, lsaChildren::get, nodeAngleMap, averaging, true);
			for (var v : nodes) {
				nodePointMap.put(v, Point2D.computeCartesian(nodeRadiusMap.get(v), nodeAngleMap.get(v)));
			}
		} else if (layout != Layout.Radial) {
			for (var v : nodes) {
				nodeAngleMap.put(v, 0.0);
			}
		}
	}

	/**
	 * modify early-branching layout to late branching one, taking care not to disturb transfer edges
	 *
	 * @param root     root
	 * @param inEdges  node in edges
	 * @param outEdges node out edges
	 * @param target   edge target
	 * @param edgeType edge type
	 * @param xGetter  x getter
	 * @param xSetter  x setter
	 * @param <Node>   node
	 * @param <Edge>   edge
	 */
	public static <Node, Edge> void modifyToLateBranching(Node root, Function<Node, List<Edge>> inEdges, Function<Node, List<Edge>> outEdges,
														  Function<Edge, Node> target,
														  Function<Edge, EdgeType> edgeType,
														  ToDoubleFunction<Node> xGetter, BiConsumer<Node, Double> xSetter) {
		var aboveTransfer = new HashSet<Node>();

		postOrderTraversal(root, v -> outEdges.apply(v).stream().map(target).toList(),
				v -> {
					for (var f : outEdges.apply(v)) {
						var w = target.apply(f);
						if (aboveTransfer.contains(w)) {
							aboveTransfer.add(v);
							return;
						}
					}
					for (var e : inEdges.apply(v)) {
						if (edgeType.apply(e) == EdgeType.transfer) {
							aboveTransfer.add(v);
							return;
						}
					}
					for (var e : outEdges.apply(v)) {
						if (edgeType.apply(e) == EdgeType.transfer) {
							aboveTransfer.add(v);
							return;
						}
					}
				});

		postOrderTraversal(root,
				v -> outEdges.apply(v).stream().map(target).toList(),
				v -> {
					var outgoing = outEdges.apply(v);
					if (!outgoing.isEmpty() && !aboveTransfer.contains(v)) {
						var x = Double.MAX_VALUE;
						for (var e : outgoing) {
							var w = target.apply(e);
							var type = (edgeType == null ? EdgeType.tree : edgeType.apply(e));
							if (type == EdgeType.transfer) {
								x = Math.min(x, xGetter.applyAsDouble(w));
							} else if (type == EdgeType.combining) {
								x = Math.min(x, xGetter.applyAsDouble(w) - 0.5);
							} else {
								x = Math.min(x, xGetter.applyAsDouble(w) - 1);
							}
						}
						if (x < Double.MAX_VALUE)
							xSetter.accept(v, x);
					}
				});
	}
}

