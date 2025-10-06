/*
 * EdgeAttributes.java (updated & documented)
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
 *
 */

package jloda.graph.fmm.algorithm;

import jloda.graph.Edge;

/**
 * Per-edge attributes used by the multilevel stages of the FMMM implementation.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Store the ideal edge length used by force models.</li>
 *   <li>Track provenance to the original (finest) edge and linkage to a higher-level edge during coarsening.</li>
 *   <li>Flag special roles in coarsening (moon/extra edges).</li>
 * </ul>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>{@code originalEdge} stays bound to the finest-level edge.</li>
 *   <li>{@code subgraphEdge} is reused to reference the coarser/higher-level counterpart;
 *       accessor names {@code getHigherLevelEdge}/{@code setHigherLevelEdge} clarify this intent.</li>
 *   <li>{@code makeMoonEdge()} and {@code makeExtraEdge()} set boolean flags — they are one-way setters by design.</li>
 * </ul>
 */
public class EdgeAttributes {
    /**
     * Ideal edge length used by attractive force computations.
     */
    private double length;

    /** The original (finest-level) edge this attribute originated from (may be null on synthetic edges). */
    private Edge originalEdge;

    /**
     * The edge representing this edge on the next coarser (higher) level during multilevel coarsening.
     * Historically named "subgraphEdge" in this codebase; functionally a "higher-level edge" link.
     */
    private Edge subgraphEdge;

    /** Flag set for edges identified as moon edges during coarsening. */
    private boolean moonEdge;

    /** Flag set for edges considered "extra" at the higher level (used in sector/placement heuristics). */
    private boolean extraEdge;

    /** No-arg constructor with default values. */
    public EdgeAttributes() {
    }

    /** Construct with an ideal length. */
    public EdgeAttributes(double length) {
        setLength(length);
    }

    /** Construct with length and provenance. */
    public EdgeAttributes(double length, Edge originalEdge, Edge subgraphEdge) {
        this.length = length;              // keep double precision (no down-cast)
        this.originalEdge = originalEdge;
        this.subgraphEdge = subgraphEdge;
    }

    /** Bulk setter for length and provenance. */
    public void setAttributes(double length, Edge originalEdge, Edge subgraphEdge) {
        this.length = length;              // keep double precision (no down-cast)
        this.originalEdge = originalEdge;
        this.subgraphEdge = subgraphEdge;
    }

    /** Ideal edge length (double). */
    public double getLength() {
        return length;
    }

    /** Set the ideal edge length. */
    public void setLength(double length) {
        this.length = length;
    }

    /** Finest-level source edge (may be null for synthetic edges). */
    public Edge getOriginalEdge() {
        return originalEdge;
    }

    public void setOriginalEdge(Edge originalEdge) {
        this.originalEdge = originalEdge;
    }

    /**
     * Historical accessor. Prefer {@link #getHigherLevelEdge()}.
     *
     * @deprecated Use {@link #getHigherLevelEdge()}.
     */
    @Deprecated
    public Edge getSubgraphEdge() {
        return subgraphEdge;
    }

    /**
     * Historical mutator. Prefer {@link #setHigherLevelEdge(Edge)}.
     * @deprecated Use {@link #setHigherLevelEdge(Edge)}.
     */
    @Deprecated
    public void setSubgraphEdge(Edge subgraphEdge) {
        this.subgraphEdge = subgraphEdge;
    }

    /**
     * Legacy alias of {@link #getSubgraphEdge()} kept for source compatibility.
     * @deprecated Use {@link #getHigherLevelEdge()}.
     */
    @Deprecated
    public Edge getCopyEdge() {
        return subgraphEdge;
    }

    /**
     * Legacy alias of {@link #setSubgraphEdge(Edge)} kept for source compatibility.
     * @deprecated Use {@link #setHigherLevelEdge(Edge)}.
     */
    @Deprecated
    public void setCopyEdge(Edge copyEdge) {
        this.subgraphEdge = copyEdge;
    }

    /* ===== Multilevel link (preferred naming) ===== */

    /** The higher-level edge corresponding to this edge during coarsening. */
    public Edge getHigherLevelEdge() {
        return subgraphEdge;
    }

    /** Set the higher-level edge corresponding to this edge during coarsening. */
    public void setHigherLevelEdge(Edge higherLevelEdge) {
        this.subgraphEdge = higherLevelEdge;
    }

    /* ===== Role flags ===== */

    public boolean isMoonEdge() {
        return moonEdge;
    }

    /** Mark this as a moon edge (one-way setter). */
    public void makeMoonEdge() {
        this.moonEdge = true;
    }

    public boolean isExtraEdge() {
        return extraEdge;
    }

    /** Mark this as an extra edge (one-way setter). */
    public void makeExtraEdge() {
        this.extraEdge = true;
    }

    /**
     * Reset volatile multilevel state prior to (re)coarsening.
     * <ul>
     *   <li>Clears the higher-level edge link</li>
     *   <li>Resets moon/extra flags</li>
     * </ul>
     * Length and original provenance remain unchanged.
     */
    public void initMultiLevelValues() {
        subgraphEdge = null;
        moonEdge = false;
        extraEdge = false;
    }
}