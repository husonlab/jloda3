/*
 * FruchtermanReingold.java (updated)
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

package jloda.graph.fmm.algorithm;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.fmm.FastMultiLayerMethodOptions;
import jloda.graph.fmm.geometry.DPoint;
import jloda.graph.fmm.geometry.DPointMutable;
import jloda.graph.fmm.geometry.LayoutBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fruchterman-Reingold repulsive forces used within the Fast Multilayer Method (no multipole).
 * This version:
 *  - computes exact repulsion with a triangular loop (no double counting),
 *  - computes approximate repulsion on a uniform grid with 3x3 neighbor cells and no double counting,
 *  - handles coincident/near-coincident nodes via bounded, scale-aware nudges (see NumericalStability),
 *  - clamps grid indices and tolerates nodes slightly outside the box.
 *
 * Original C++ author: Stefan Hachul, original license: GPL
 * Reimplemented in Java by Daniel Huson, 3.2021; updated with the help of ChatGPT 2025-09
 */
public class FruchtermanReingold {

    /**
     * Calculate exact repulsive forces using a triangular loop over node pairs.
     * force(v) += f(u->v), force(u) -= f(u->v)
     */
    public static void calculateExactRepulsiveForces(Graph graph,
                                                     NodeArray<NodeAttributes> nodeAttributes,
                                                     NodeArray<DPoint> force) {
        // init
        for (var v : graph.nodes()) {
            force.put(v, new DPoint(0, 0));
        }

        // triangular pair loop to avoid double counting
        final List<Node> nodes = graph.getNodesAsList();
        final int n = nodes.size();

        for (int i = 0; i < n; i++) {
            final Node u = nodes.get(i);
            final DPoint pu0 = nodeAttributes.get(u).getPosition();
            for (int j = i + 1; j < n; j++) {
                final Node v = nodes.get(j);
                final DPoint pv0 = nodeAttributes.get(v).getPosition();

                DPoint delta = pv0.subtract(pu0);
                double dist = delta.norm();

                // Handle degenerate/near-degenerate distances with bounded rescue vector
                DPointMutable vec = new DPointMutable();
                if (NumericalStability.repulsionNearMachinePrecision(dist, vec)) {
                    // vec is already a small bounded repulsive vector in random direction
                } else {
                    // Regular FR repulsion: (k^2 / d) in direction delta, with k^2 applied later by caller.
                    // Here we produce (1/d^2) * delta; caller multiplies by k^2.
                    double s = repulsionScalar(dist) / dist; // 1/d^2
                    vec.setPosition(delta.scaleBy(s));
                }

                // Apply action-reaction
                force.put(v, force.get(v).add(vec));
                force.put(u, force.get(u).subtract(vec));
            }
        }
    }

    /**
     * Calculate approximate repulsive forces using a uniform grid.
     * We compute exact interactions within the same cell and its 8 neighbors,
     * but iterate cells in a way that avoids double counting:
     * - same-cell pairs: triangular loop
     * - neighbor cells: only process "upper/right half" to ensure each unordered pair once
     */
    public static void calculateApproxRepulsiveForces(FastMultiLayerMethodOptions options,
                                                      Graph graph,
                                                      LayoutBox layoutBox,
                                                      NodeArray<NodeAttributes> nodeAttributes,
                                                      NodeArray<DPoint> force) {
        // init
        for (var v : graph.nodes()) {
            force.put(v, new DPoint(0, 0));
        }

        final int n = graph.getNumberOfNodes();
        if (n <= 1) return;

        // Choose grid size; fallback to exact if too small
        int size = (int) (Math.sqrt(n) / Math.max(1e-9, options.getFrGridQuotient()));
        size = Math.max(2, Math.min(2048, size));

        if (size < 2) {
            calculateExactRepulsiveForces(graph, nodeAttributes, force);
            return;
        }

        final double left = layoutBox.getLeft();
        final double down = layoutBox.getDown();
        final double boxLen = Math.max(1e-12, layoutBox.getLength()); // avoid div-by-zero
        final double cell = boxLen / size;

        // Bin nodes into cells (with clamping)
        final Array2D<List<Node>> grid = new Array2D<>(size, size);
        for (var v : graph.nodes()) {
            var p = nodeAttributes.get(v).getPosition();
            int ix = (int) Math.floor((p.getX() - left) / cell);
            int iy = (int) Math.floor((p.getY() - down) / cell);
            if (ix < 0) ix = 0;
            else if (ix >= size) ix = size - 1;
            if (iy < 0) iy = 0;
            else if (iy >= size) iy = size - 1;
            grid.computeIfAbsent(ix, iy, (r, c) -> new ArrayList<>()).add(v);
        }

        // For each cell, compute:
        //  - same-cell interactions via triangular loop
        //  - neighbor-cell interactions for 8 neighbors, but only "upper/right half" to avoid double counting
        DPointMutable vec = new DPointMutable();

        for (int ix = 0; ix < size; ix++) {
            for (int iy = 0; iy < size; iy++) {
                final List<Node> here = grid.getOrDefault(ix, iy, Collections.emptyList());
                final int m = here.size();
                if (m == 0) continue;

                // Same-cell pairs: triangular
                for (int a = 0; a < m; a++) {
                    final Node u = here.get(a);
                    final DPoint pu0 = nodeAttributes.get(u).getPosition();
                    for (int b = a + 1; b < m; b++) {
                        final Node v = here.get(b);
                        final DPoint pv0 = nodeAttributes.get(v).getPosition();

                        DPoint delta = pv0.subtract(pu0);
                        double dist = delta.norm();

                        if (NumericalStability.repulsionNearMachinePrecision(dist, vec)) {
                            // vec set by rescue
                        } else {
                            double s = repulsionScalar(dist) / dist; // 1/d^2
                            vec.setPosition(delta.scaleBy(s));
                        }

                        force.put(v, force.get(v).add(vec));
                        force.put(u, force.get(u).subtract(vec));
                    }
                }

                // Neighbor cells: process only upper/right half to avoid double counting
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        // skip same cell (handled) and lower/left half to avoid double counting
                        if (dx < 0 || (dx == 0 && dy <= 0)) continue;

                        int jx = ix + dx, jy = iy + dy;
                        if (jx < 0 || jx >= size || jy < 0 || jy >= size) continue;

                        final List<Node> neigh = grid.getOrDefault(jx, jy, Collections.emptyList());
                        if (neigh.isEmpty()) continue;

                        for (var u : here) {
                            final DPoint pu0 = nodeAttributes.get(u).getPosition();
                            for (var v : neigh) {
                                if (u == v) continue; // defensive; should never happen

                                final DPoint pv0 = nodeAttributes.get(v).getPosition();
                                DPoint delta = pv0.subtract(pu0);
                                double dist = delta.norm();

                                if (NumericalStability.repulsionNearMachinePrecision(dist, vec)) {
                                    // vec set by rescue
                                } else {
                                    double s = repulsionScalar(dist) / dist; // 1/d^2
                                    vec.setPosition(delta.scaleBy(s));
                                }

                                force.put(v, force.get(v).add(vec));
                                force.put(u, force.get(u).subtract(vec));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * FR repulsion magnitude core: returns 1/d, so that after multiplying by 1/d (unit direction)
     * we get a 1/d^2 scaling. The FMMM caller later scales by k^2 (average ideal edge length squared).
     */
    private static double repulsionScalar(double d) {
        if (d > 0) return 1.0 / d;
        // Should not happen thanks to NumericalStability checks; provide safe fallback:
        return 0.0;
    }
}