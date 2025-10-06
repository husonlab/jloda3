/*
 * MultiComponents.java (updated & documented)
 * Copyright (C) 2024 Daniel H. Huson
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

package jloda.graph.fmm;

import jloda.graph.*;
import jloda.graph.algorithms.Simple;
import jloda.graph.fmm.geometry.DPoint;
import jloda.graph.fmm.geometry.DRect;
import jloda.util.ProgramExecutorService;
import jloda.util.Single;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Layout for graphs with multiple connected components.
 *
 * <p>High-level strategy:
 * <ol>
 *     <li>If {@code graph0} is not simple, we build a simple copy and maintain a mapping back to {@code graph0} so
 *     we can emit results in terms of the original nodes.</li>
 *     <li>If the (possibly simplified) graph is connected, we call {@link FastMultiLayerMethodLayout#apply} directly.</li>
 *     <li>Otherwise, we extract all connected components as separate graphs, run {@code FastMultiLayerMethodLayout}
 *     on each component (in parallel), then pack the resulting component drawings into rows with horizontal/vertical gaps.
 *     The first (largest) component sets a global scale so its width becomes {@code maxWidth}. Subsequent components use
 *     the same scale and are placed left-to-right with line wraps at {@code maxLineWidth}.</li>
 * </ol>
 *
 * <p>Weights & initial positions:
 * <ul>
 *     <li>Edge weights are preserved: for each component edge, we look up the corresponding edge in the
 *     (possibly simplified) global graph and evaluate {@code edgeWeights} there.</li>
 *     <li>Initial positions are also preserved: for each component node, if a global {@code initialPosFunction}
 *     is provided, we map the component node back to its global node and query the initial position from that.</li>
 * </ul>
 *
 * <p>Output:
 * <ul>
 *     <li>All final coordinates are emitted via {@code result0} in terms of the original {@code graph0} nodes
 *     (even if simplification was performed).</li>
 *     <li>The returned {@link FastMultiLayerMethodLayout.Rectangle} is the bounding box of the packed layout
 *     in the final coordinate system.</li>
 * </ul>
 * <p>
 * Daniel Huson, 5.2021; updated using ChatGPT 2025-09
 */
public class MultiComponents {
	/**
	 * Layout a graph that may contain multiple connected components.
	 *
	 * @param options            FMMM options (may be null for defaults).
	 * @param maxWidth           Target width for the largest component after scaling (first row).
	 * @param maxLineWidth       Maximum width for packing a row of components (triggers line wrap).
	 * @param hGap               Horizontal gap between components in the same row.
	 * @param vGap               Vertical gap between rows of components.
	 * @param graph0             Original graph (may be non-simple; this method will simplify it as needed).
	 * @param edgeWeights0       Edge weights defined on {@code graph0} (may be null → unit weights).
	 * @param initialPosFunction Optional initial positions defined on nodes of {@code graph0}.
	 * @param result0            Callback to receive final coordinates for nodes of {@code graph0}.
	 * @return The overall bounding rectangle of the packed layout.
	 */
	public static FastMultiLayerMethodLayout.Rectangle apply(
			FastMultiLayerMethodOptions options,
			double maxWidth,
			double maxLineWidth,
			double hGap,
			double vGap,
			Graph graph0,
			ToDoubleFunction<Edge> edgeWeights0,
			Function<Node, FastMultiLayerMethodLayout.Point> initialPosFunction,
			BiConsumer<Node, FastMultiLayerMethodLayout.Point> result0) throws Exception {

		// 1) Ensure a simple working graph "graph" and a mapping back to "graph0" for results & weights
		final Graph graph;
		ToDoubleFunction<Edge> edgeWeights; // weights defined on "graph"
		final BiConsumer<Node, FastMultiLayerMethodLayout.Point> result; // emits to original nodes
		if (graph0.isSimple()) {
			graph = graph0;
			edgeWeights = edgeWeights0;
			result = result0;
		} else {
			graph = new Graph();
			NodeArray<Node> tar2srcNode = graph.newNodeArray();
			EdgeArray<Edge> tar2srcEdge = graph.newEdgeArray();
			Simple.makeSimple(graph0, graph, tar2srcNode, tar2srcEdge);
			// Map results back to original nodes:
			result = (v, p) -> result0.accept(tar2srcNode.get(v), p);
			// Map weights back to original edges:
			edgeWeights = (e -> (edgeWeights0 == null ? 1.0 : edgeWeights0.applyAsDouble(tar2srcEdge.get(e))));
		}

		// 2) Connected graph case: run FMMM once and return
		if (graph.isConnected()) {
			return FastMultiLayerMethodLayout.apply(options, graph, edgeWeights, initialPosFunction, result);
		}

		// 3) Multi-component case: extract components and lay out each in parallel
		// src2tar maps nodes of "graph" -> corresponding node in its component graph
		NodeArray<Node> src2tar = graph.newNodeArray();
		var components = graph.extractAllConnectedComponents(src2tar);

		var service = Executors.newFixedThreadPool(ProgramExecutorService.getNumberOfCoresToUse());
		var exception = new Single<Exception>();
		var list = new ArrayList<Component>();

		for (var component : components) {
			service.submit(() -> {
				if (exception.isNull()) {
					try {
						// tar2src maps component node -> node in "graph"
						NodeArray<Node> tar2src = component.newNodeArray();
						for (var v : src2tar.keys()) {
							var w = src2tar.get(v); // node in some component graph
							if (w != null && w.getOwner() == component) {
								tar2src.put(w, v);
							}
						}

						// Per-component weight function: look up the corresponding edge in the global simple graph
						ToDoubleFunction<Edge> ewComp = e -> {
							if (edgeWeights == null) return 1.0;
							var gu = tar2src.get(e.getSource());
							var gv = tar2src.get(e.getTarget());
							if (gu == null || gv == null) return 1.0;
							var ge = graph.getCommonEdge(gu, gv); // graph is simple, so at most one exists
							if (ge == null) return 1.0;
							return edgeWeights.applyAsDouble(ge);
						};

						// Per-component initial positions: map comp node -> global node -> initialPosFunction
						Function<Node, FastMultiLayerMethodLayout.Point> initPosComp = null;
						if (initialPosFunction != null) {
							initPosComp = (n) -> {
								Node g = tar2src.get(n);
								return (g == null ? null : initialPosFunction.apply(g));
							};
						}

						// Run the layout on this component (results into "coords" on component nodes)
						NodeArray<FastMultiLayerMethodLayout.Point> points = component.newNodeArray();
						var rect = FastMultiLayerMethodLayout.apply(options, component, ewComp, initPosComp, points::put);
						synchronized (list) {
							list.add(new Component(component, rect, tar2src, points));
						}
					} catch (Exception e) {
						exception.setIfCurrentValueIsNull(e);
					}
				}
			});
		}

		service.shutdown();
		// generous upper bound; we want deterministic completion in long jobs as well
		service.awaitTermination(365, TimeUnit.DAYS);
		if (exception.isNotNull())
			throw exception.get();

		// Sort components by decreasing area so the largest sets the global scale first
		list.sort(Comparator.comparingDouble(Component::getArea).reversed());

		// 4) Pack components left-to-right, wrapping lines at maxLineWidth
		double scale = -1d;     // set by first (largest) component
		double cursorX = 0d;    // current x offset (left edge of the next component)
		double cursorY = 0d;    // current y offset (top of the current row)
		double lineMaxY = 0d;   // bottom of the tallest component in the current row (absolute Y)

		for (var comp : list) {
			double cWidth = comp.getRectangle().getWidth();
			// Establish a global scale so the largest component fits within maxWidth
			if (scale < 0d) {
				if (cWidth > 0) {
					scale = maxWidth / cWidth;
				} else {
					// Degenerate component (all points with same x?); pick neutral scale
					scale = 1.0;
				}
			}

			// Compute this component's scaled width
			double layoutWidth = scale * cWidth;

			// Line wrap if needed
			if (cursorX > 0 && cursorX + layoutWidth > maxLineWidth) {
				cursorX = 0;
				// Move down to the next row: place below the tallest component of the previous row + vGap
				cursorY = lineMaxY + vGap;
			}

			// Place the component at (cursorX, cursorY)
			var placedRect = comp.mapToRect(cursorX, cursorY, scale, result);

			// Advance cursorX and update row height
			cursorX = placedRect.getMaxX() + hGap;
			lineMaxY = Math.max(lineMaxY, placedRect.getMaxY());
		}

		// Overall bounding rectangle of the packed layout
		double finalWidth = Math.max(maxLineWidth, cursorX); // if last row didn't fill the line
		double finalHeight = lineMaxY;
		return new DRect(0, 0, finalWidth, finalHeight);
	}


	/**
	 * Convenience overload without initial positions.
	 */
	public static FastMultiLayerMethodLayout.Rectangle apply(FastMultiLayerMethodOptions options, double maxWidth, double maxLineWidth, double hGap, double vGap, Graph graph0, ToDoubleFunction<Edge> edgeWeights0, BiConsumer<Node, FastMultiLayerMethodLayout.Point> result0) throws Exception {
		return apply(options, maxWidth, maxLineWidth, hGap, vGap, graph0, edgeWeights0, null, result0);
	}


	/**
	 * Internal representation of one laid-out component, along with mappings to/from the global (simple) graph.
	 */
	private static class Component {
		private final Graph graph; // the component graph
		private final FastMultiLayerMethodLayout.Rectangle rectangle; // bbox in component's own coordinates
		private final NodeArray<Node> tar2src; // comp node -> global graph node (of "graph")
		private final NodeArray<FastMultiLayerMethodLayout.Point> points; // comp node -> final point

		public Component(Graph graph, FastMultiLayerMethodLayout.Rectangle rectangle, NodeArray<Node> tar2src, NodeArray<FastMultiLayerMethodLayout.Point> points) {
			this.graph = graph;
			this.rectangle = rectangle;
			this.tar2src = tar2src;
			this.points = points;
		}

		public Graph getGraph() {
			return graph;
		}

		public FastMultiLayerMethodLayout.Rectangle getRectangle() {
			return rectangle;
		}

		public NodeArray<Node> getTar2src() {
			return tar2src;
		}

		public NodeArray<FastMultiLayerMethodLayout.Point> getPoints() {
			return points;
		}

		public double getArea() {
			return rectangle.getWidth() * rectangle.getHeight();
		}

		/**
		 * Map this component's local coordinates into the global packed canvas and emit results for original nodes.
		 *
		 * @param dx     left offset of where to place this component
		 * @param dy     top offset of where to place this component
		 * @param scale  global uniform scaling to apply
		 * @param result callback that consumes (originalGraphNode, mappedPoint)
		 * @return the mapped bounding box in global coordinates
		 */
		public DRect mapToRect(double dx, double dy, double scale, BiConsumer<Node, FastMultiLayerMethodLayout.Point> result) {
			// map functions (shift by component min, then scale, then translate by (dx,dy))
			Function<Double, Double> mapX = x -> (x - rectangle.getMinX()) * scale + dx;
			Function<Double, Double> mapY = y -> (y - rectangle.getMinY()) * scale + dy;

			double minX = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;

			for (var a : graph.nodes()) {
				var p = points.get(a);
				if (p == null) continue; // defensive; should not happen
				double x = mapX.apply(p.getX());
				double y = mapY.apply(p.getY());

				if (x < minX) minX = x;
				if (x > maxX) maxX = x;
				if (y < minY) minY = y;
				if (y > maxY) maxY = y;

				// Emit in terms of original nodes (tar2src maps comp node -> original/simple graph node;
				// the caller's "result" already maps from simple to original if needed).
				Node original = tar2src.get(a);
				if (original != null) {
					result.accept(original, new DPoint(x, y));
				}
			}
			// If the component was empty (shouldn't happen), avoid NaN bbox
			if (!Double.isFinite(minX)) {
				minX = dx;
				maxX = dx;
				minY = dy;
				maxY = dy;
			}
			return new DRect(minX, minY, maxX, maxY);
		}
	}
}