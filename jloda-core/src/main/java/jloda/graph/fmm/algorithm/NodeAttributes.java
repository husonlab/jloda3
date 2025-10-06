/*
 * NodeAttributes.java (updated & documented)
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

import jloda.graph.Node;
import jloda.graph.fmm.FastMultiLayerMethodLayout;
import jloda.graph.fmm.geometry.DPoint;

import java.util.ArrayList;

/**
 * Per-node attributes used by the multilevel stages of the FMMM implementation.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Store geometric state (position, width/height).</li>
 *   <li>Track multilevel relations (lower/higher level representatives).</li>
 *   <li>Maintain solar-system classification (sun/planet/moon, distances, lambdas).</li>
 *   <li>Provide placement-sector angles and a placed-flag to prevent re-seeding.</li>
 * </ul>
 *
 * <p>Original C++ author: Stefan Hachul (GPL).
 * Reimplemented in Java by Daniel Huson, 3.2021. Updated with guards & docs.</p>
 */
public class NodeAttributes implements FastMultiLayerMethodLayout.Point {
	/* =========================
	 * Geometry
	 * ========================= */
	private DPoint position = new DPoint(); // immutable value object (DPointMutable is used elsewhere)
	private float width;
	private float height;

	/* =========================
	 * Multilevel links
	 * ========================= */
	private Node lowerLevelNode;   // the node represented by this node (when this is a higher-level rep)
	private Node higherLevelNode;  // the representative of this node on the next coarser level

	/* =========================
	 * Multilevel aggregation
	 * ========================= */
	private int mass; // number of fine-level nodes collapsed into this one

	public enum Type {Unspecified, Sun, Planet, PlanetWithMoons, Moon}

	private Type type = Type.Unspecified;

	// Solar-system assignment (at a given level)
	private Node dedicatedSunNode;        // the sun this node belongs to
	private double dedicatedSunDistance;  // ideal distance to the dedicated sun
	private Node dedicatedPMNode;         // for moons: their planet-with-moons (PM) attachment

	// Inter-solar links (for interpolation at refinement)
	private ArrayList<Double> lambdas;    // lambda ratios (in [0,1]) along edges to neighbor suns
	private ArrayList<Node> neighborSunNodes;

	// For PlanetWithMoons: list of attached moons
	private ArrayList<Node> moons;

	// Placement state
	private boolean placed = false;

	// Allowed placement sector around the sun
	private double angle1 = 0.0;          // radians, normalized to [0, 2π)
	private double angle2 = 2 * Math.PI;  // radians, normalized to [0, 2π); angle2 may be < angle1 modulo 2π

	public NodeAttributes() {
	}

	public NodeAttributes(float width, float height, DPoint position, Node vLow, Node vHigh) {
		this.width = width;
		this.height = height;
		this.position = position; // DPoint is immutable; reference assign is fine
		this.lowerLevelNode = vLow;
		this.higherLevelNode = vHigh;
	}

	/* =========================
	 * Geometry API (FastMultiLayerMethodLayout.Point)
	 * ========================= */

	/**
	 * A radius compatible with BoundingCircle edge-length measurement.
	 */
	public float getRadius() {
		return Math.max(width, height) / 2f;
	}

	public void setPosition(DPoint position) {
		this.position = position;
	}

	public void setPosition(double x, double y) {
		this.position = new DPoint(x, y);
	}

	public DPoint getPosition() {
		return position;
	}

	@Override
	public double getX() {
		return position.getX();
	}

	@Override
	public double getY() {
		return position.getY();
	}

	public float getWidth() {
		return width;
	}

	public void setWidth(float width) {
		this.width = width;
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		this.height = height;
	}

	/* =========================
	 * Multilevel links
	 * ========================= */

	/**
	 * @deprecated Use {@link #getLowerLevelNode()}.
	 */
	@Deprecated
	public Node getOriginalNode() {
		return lowerLevelNode;
	}

	/** @deprecated Use {@link #setLowerLevelNode(Node)}. */
	@Deprecated
	public void setOriginalNode(Node orig) {
		this.lowerLevelNode = orig;
	}

	/** @deprecated Use {@link #getHigherLevelNode()}. */
	@Deprecated
	public Node getCopyNode() {
		return higherLevelNode;
	}

	/** @deprecated Use {@link #setHigherLevelNode(Node)}. */
	@Deprecated
	public void setCopyNode(Node copy) {
		this.higherLevelNode = copy;
	}

	public Node getLowerLevelNode() {
		return lowerLevelNode;
	}

	public void setLowerLevelNode(Node lowerLevelNode) {
		this.lowerLevelNode = lowerLevelNode;
	}

	public Node getHigherLevelNode() {
		return higherLevelNode;
	}

	public void setHigherLevelNode(Node higherLevelNode) {
		this.higherLevelNode = higherLevelNode;
	}

	/* =========================
	 * Aggregation & classification
	 * ========================= */

	public int getMass() {
		return mass;
	}

	public void setMass(int mass) {
		this.mass = Math.max(0, mass);
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = (type == null ? Type.Unspecified : type);
	}

	public Node getDedicatedSunNode() {
		return dedicatedSunNode;
	}

	public void setDedicatedSunNode(Node dedicatedSunNode) {
		this.dedicatedSunNode = dedicatedSunNode;
	}

	public double getDedicatedSunDistance() {
		return dedicatedSunDistance;
	}

	public void setDedicatedSunDistance(double dedicatedSunDistance) {
		this.dedicatedSunDistance = dedicatedSunDistance;
	}

	public Node getDedicatedPMNode() {
		return dedicatedPMNode;
	}

	public void setDedicatedPMNode(Node dedicatedPMNode) {
		this.dedicatedPMNode = dedicatedPMNode;
	}

	/* =========================
	 * Neighboring solar info
	 * ========================= */

	/** Lazily created list of lambda values (each typically in [0,1]). */
	public ArrayList<Double> getLambdas() {
		if (lambdas == null) {
			lambdas = new ArrayList<>();
		}
		return lambdas;
	}

	/** Lazily created list of neighbor sun nodes (parallel to {@link #getLambdas()}). */
	public ArrayList<Node> getNeighborSunNodes() {
		if (neighborSunNodes == null) {
			neighborSunNodes = new ArrayList<>();
		}
		return neighborSunNodes;
	}

	/** Lazily created list of attached moons (only meaningful when {@code type == PlanetWithMoons}). */
	public ArrayList<Node> getMoons() {
		if (moons == null) {
			moons = new ArrayList<>();
		}
		return moons;
	}

	/* =========================
	 * Placement state
	 * ========================= */

	public boolean isPlaced() {
		return placed;
	}

	/** Mark this node as already placed (prevents re-seeding during refinement). */
	public void placed() {
		this.placed = true;
	}

	/**
	 * Explicit setter (paired with {@link #isPlaced()}); retains compatibility with {@link #placed()}.
	 */
	public void setPlaced(boolean placed) {
		this.placed = placed;
	}

	/* =========================
	 * Sector angles
	 * ========================= */

	/** Start angle (radians), normalized to [0, 2π). */
	public double getAngle1() {
		return angle1;
	}

	/**
	 * Set start angle (radians). Value is normalized to [0, 2π).
	 */
	public void setAngle1(double angle1) {
		this.angle1 = normalizeAngle(angle1);
		ensureNonDegenerateSector();
	}

	/** End angle (radians), normalized to [0, 2π). */
	public double getAngle2() {
		return angle2;
	}

	/**
	 * Set end angle (radians). Value is normalized to [0, 2π).
	 */
	public void setAngle2(double angle2) {
		this.angle2 = normalizeAngle(angle2);
		ensureNonDegenerateSector();
	}

	/**
	 * Reset multilevel-specific volatile fields before (re-)coarsening or refinement.
	 * Leaves geometry and inter-level links intact unless explicitly reset by the caller.
	 */
	public void initMultiLevelValues() {
		type = Type.Unspecified;
		dedicatedSunNode = null;
		dedicatedSunDistance = 0.0;
		dedicatedPMNode = null;
		lambdas = null;
		neighborSunNodes = null;
		moons = null;
		placed = false;
		angle1 = 0.0;
		angle2 = 2 * Math.PI;
	}

	/* =========================
	 * Helpers
	 * ========================= */

	private static double normalizeAngle(double a) {
		double twoPi = 2 * Math.PI;
		// Java's % can be negative; make result in [0, 2π)
		double r = a % twoPi;
		return (r < 0) ? r + twoPi : r;
	}

	/**
	 * Ensure the [angle1, angle2] sector is non-degenerate. If equal after normalization,
	 * expand to a half-circle to avoid zero measure sectors during placement sampling.
	 */
	private void ensureNonDegenerateSector() {
		if (Double.doubleToLongBits(angle1) == Double.doubleToLongBits(angle2)) {
			angle2 = normalizeAngle(angle1 + Math.PI);
		}
	}
}