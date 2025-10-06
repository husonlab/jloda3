/*
 * Dijkstra.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.graph.algorithms;


import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;

import java.util.*;
import java.util.function.Function;

/**
 * Dijkstras algorithm for single source shortest path, non-negative edge lengths
 *
 * @author huson
 * Date: 11-Dec-2004, 9.2025
 */
public class Dijkstra {
    /**
     * Dijkstra’s algorithm (single-source shortest path).
     *
     * @param graph the graph
     * @param source start node
     * @param sink target node
     * @param weights function mapping an edge to a weight (non-negative)
     * @param undirected if true, treat edges as undirected (use both outEdges and inEdges)
     * @return list of nodes forming the shortest path [source ... sink]
     */
    public static List<Node> compute(final Graph graph, Node source, Node sink,
                                     Function<Edge, Number> weights, boolean undirected) {
        try (NodeArray<Node> predecessor = graph.newNodeArray();
             var dist = graph.newNodeDoubleArray()) {

            for (var v : graph.nodes()) {
                dist.put(v, Double.POSITIVE_INFINITY);
                predecessor.put(v, null);
            }
            dist.put(source, 0.0);

            // map node id → node
            var nodes = new HashMap<Integer, Node>();
            graph.nodes().forEach(v -> nodes.put(v.getId(), v));

            var pq = new PriorityQueue<double[]>(Comparator.comparingDouble(a -> a[0]));
            pq.add(new double[]{0.0, source.getId()});

            while (!pq.isEmpty()) {
                var top = pq.poll();
                var d = top[0];
                var uId = (int) top[1];
                var u = nodes.get(uId);
                if (d > dist.get(u)) continue; // stale

                // gather neighbors
                Iterable<Edge> edges = u.outEdges();
                if (undirected) {
                    // concat outEdges + inEdges
                    edges = concat(u.outEdges(), u.inEdges());
                }

                for (var e : edges) {
                    var w = weights.apply(e).doubleValue();
                    if (w < 0)
                        throw new IllegalArgumentException("Dijkstra requires non-negative weights");
                    var v = e.getOpposite(u);
                    var nd = dist.get(u) + w;
                    if (nd < dist.get(v)) {
                        dist.put(v, nd);
                        predecessor.put(v, u);
                        pq.add(new double[]{nd, v.getId()});
                    }
                }
            }

            // reconstruct full path
            var path = new ArrayList<Node>();
            var cur = sink;
            if (Double.isInfinite(dist.get(cur)))
                throw new RuntimeException("No path from source to sink");
            while (cur != null) {
                path.add(0, cur);
                if (cur == source) break;
                cur = predecessor.get(cur);
            }
            return path;
        }
    }

    // Simple helper to concatenate two Iterables
    private static <T> Iterable<T> concat(Iterable<T> a, Iterable<T> b) {
        return () -> new Iterator<T>() {
            final Iterator<T> itA = a.iterator();
            final Iterator<T> itB = b.iterator();

            public boolean hasNext() {
                return itA.hasNext() || itB.hasNext();
            }

            public T next() {
                return itA.hasNext() ? itA.next() : itB.next();
            }
        };
    }
}