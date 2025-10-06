/*
 * NumericalStability.java  (updated)
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

import jloda.graph.fmm.geometry.DPoint;
import jloda.graph.fmm.geometry.DPointMutable;

import java.util.Random;

/**
 * Numerical safeguards for the fast multilayer method (no multipole variant).
 *
 * This version avoids astronomically large/small rescue vectors and instead
 * uses scale-aware, bounded random nudges when distances are effectively zero.
 * You can set a geometric scale (e.g., average ideal edge length) so that
 * epsilons adapt to the drawing size.
 *
 * Original C++ author: Stefan Hachul, original license: GPL
 * Reimplemented in Java by Daniel Huson, 3.2021; updated with the help of ChatGPT 2025-09
 */
public class NumericalStability {

	/* =======================
	 * Configuration (scale)
	 * ======================= */

	/**
	 * Geometric scale of the drawing (e.g., average ideal edge length k).
	 * Used to derive tolerances. Default 1.0 if not set.
	 */
	private static volatile double scale = 1.0;

	/**
	 * Set the geometric scale so epsilons are meaningful for your instance.
	 * A good choice is the average ideal edge length used elsewhere (k).
	 */
	public static void setGeometricScale(double s) {
		if (s > 0 && Double.isFinite(s)) {
			scale = s;
		}
	}

	/* =======================
	 * Epsilon/tolerance model
	 * ======================= */

	// Base factors (dimensionless). Final magnitudes depend on 'scale'.
	private static final double DIST_EPS_FACTOR = 1e-9;  // distances below ~scale*1e-9 treated as coincident
	private static final double PUSH_EPS_FACTOR = 1e-6;  // nudge magnitude ~scale*1e-6
	private static final double MAX_FORCE_FACTOR = 1e+3;  // upper bound for emergency vectors relative to scale

	/**
	 * Absolute distance threshold derived from scale.
	 */
	private static double distEps() {
		return Math.max(1e-15, DIST_EPS_FACTOR * scale);
	}

	/**
	 * Magnitude for small random nudges.
	 */
	private static double pushMag() {
		return Math.max(1e-12, PUSH_EPS_FACTOR * scale);
	}

	/**
	 * Clamp for any constructed emergency vector.
	 */
	private static double maxForceMag() {
		return Math.max(1.0, MAX_FORCE_FACTOR * scale);
	}

	/* =======================
	 * RNG / determinism
	 * ======================= */

	private static final Random random = new Random(0L); // deterministic by default

	/**
	 * Reseed to get reproducible or varied runs as desired.
	 */
	public static void reseed(long seed) {
		random.setSeed(seed);
	}

	/* =======================
	 * Public API (unchanged signatures)
	 * ======================= */

	/**
	 * Returns true if the distance is "too small" for stable repulsion math and writes a bounded random
	 * fallback vector to {@code force} (if non-null). No longer attempts to handle "too large" distances;
	 * far-field repulsion is naturally tiny and numerically safe.
	 */
	public static boolean repulsionNearMachinePrecision(double distance, DPointMutable force) {
		if (!(distance > 0) || distance < distEps()) {
			if (force != null) {
				setRandomUnitScaled(force, pushMag());
			}
			return true;
		}
		return false;
	}

	/**
	 * Returns true if the distance is effectively zero for general force math and writes a bounded random
	 * fallback vector to {@code force} (if non-null).
	 */
	public static boolean nearMachinePrecision(double distance, DPointMutable force) {
		if (!(distance > 0) || distance < distEps()) {
			if (force != null) {
				setRandomUnitScaled(force, pushMag());
			}
			return true;
		}
		return false;
	}

	/**
	 * Returns a nearby point distinct from {@code old_pos}, jittered by a small, scale-aware radius.
	 * Kept for API compatibility with existing callers.
	 */
	public static DPoint chooseDistinctRandomPointInRadiusEpsilon(DPoint old_pos) {
		final double r = pushMag(); // small, scale-aware jitter
		final double a = 2.0 * Math.PI * random.nextDouble();
		return new DPoint(old_pos.getX() + r * Math.cos(a), old_pos.getY() + r * Math.sin(a));
	}

	/* =======================
	 * Helpers
	 * ======================= */

	private static void setRandomUnitScaled(DPointMutable out, double magnitude) {
		// Draw a random direction uniformly on the circle, then scale.
		double theta = 2.0 * Math.PI * random.nextDouble();
		double x = Math.cos(theta);
		double y = Math.sin(theta);
		out.setX(x * magnitude);
		out.setY(y * magnitude);

		// Final clamp (very conservative) in case caller composes multiple emergency vectors.
		clampMagnitude(out, maxForceMag());
	}

	private static void clampMagnitude(DPointMutable v, double maxMag) {
		double x = v.getX();
		double y = v.getY();
		double m2 = x * x + y * y;
		double max2 = maxMag * maxMag;
		if (m2 > max2) {
			double s = maxMag / Math.sqrt(m2);
			v.setX(x * s);
			v.setY(y * s);
		}
	}
}