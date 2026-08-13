/*
 * GraphLayoutService.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.graph.layout;

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.fmm.FastMultiLayerMethodLayout;

import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

/**
 * A pluggable force-directed graph-layout service. jloda ships a pure-Java default
 * ({@code jloda-fmm}, wrapping {@link FastMultiLayerMethodLayout}); alternative implementations
 * (e.g. a native OGDF FM3 layout) are contributed as {@link java.util.ServiceLoader} providers and
 * discovered via {@link GraphLayouts}. The contract mirrors {@code FastMultiLayerMethodLayout.apply}
 * so callers can swap implementations without changing how they build the edge list or consume
 * coordinates. Provider-specific tuning (seed, quality, initial positions) is deliberately not part
 * of the contract; each implementation uses sensible defaults.
 * <p>
 * This interface lives in jloda-core and uses only jloda-core types, so it remains available on all
 * targets, including GraalVM/Gluon iOS builds, which fall back to the jloda default because no native
 * provider is present.
 * Daniel Huson, 8.2026
 */
public interface GraphLayoutService {
	/**
	 * a short, stable identifier for this layout, e.g. {@code "jloda-fmm"} or {@code "ogdf-fmm"};
	 * used to present and select among the available layouts.
	 */
	String getName();

	/**
	 * whether this layout is actually usable in the current run. The default is {@code true}; a provider
	 * backed by native code overrides this to return {@code false} when its native library cannot be loaded
	 * (e.g. on a platform for which it was not built, or on a GraalVM/iOS image), so that {@link GraphLayouts}
	 * skips it and falls back to another layout instead of failing when the layout is invoked.
	 */
	default boolean isAvailable() {
		return true;
	}

	/**
	 * computes a force-directed layout of the given graph.
	 *
	 * @param graph       the graph to embed (must be simple and connected)
	 * @param edgeWeights optional desired edge lengths (may be {@code null} for unit lengths)
	 * @param result      receives the computed coordinate for each node
	 * @return the bounding box of the computed layout
	 */
	FastMultiLayerMethodLayout.Rectangle apply(Graph graph, ToDoubleFunction<Edge> edgeWeights,
											   BiConsumer<Node, FastMultiLayerMethodLayout.Point> result);
}
