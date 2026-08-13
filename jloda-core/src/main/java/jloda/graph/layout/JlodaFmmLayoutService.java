/*
 * JlodaFmmLayoutService.java Copyright (C) 2026 Daniel H. Huson
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
 * The built-in, pure-Java graph-layout service: a thin wrapper around jloda's
 * {@link FastMultiLayerMethodLayout} (the Java reimplementation of the fast multilayer method).
 * This is the fallback used by {@link GraphLayouts} when no other provider is on the path, and it is
 * always available (no native code), including on Gluon/iOS builds.
 * Daniel Huson, 8.2026
 */
public class JlodaFmmLayoutService implements GraphLayoutService {
	/** the identifier of this layout */
	public static final String NAME = "jloda-fmm";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public FastMultiLayerMethodLayout.Rectangle apply(Graph graph, ToDoubleFunction<Edge> edgeWeights,
													  BiConsumer<Node, FastMultiLayerMethodLayout.Point> result) {
		// null options -> defaults; null initial-position function -> random start
		return FastMultiLayerMethodLayout.apply(null, graph, edgeWeights, null, result);
	}
}
