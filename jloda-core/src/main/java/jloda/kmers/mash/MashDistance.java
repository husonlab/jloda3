/*
 * MashDistance.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.kmers.mash;

import jloda.kmers.GenomeDistanceType;

/**
 * compute the distance between two mash sketches
 * Daniel Huson, 1.2020
 */
public class MashDistance {
	/**
	 * computes the mash distance
	 *
	 * @return mash distance
	 */
	public static double compute(MashSketch a, MashSketch b, GenomeDistanceType genomeDistanceType) {
		final double jaccardIndex = computeJaccardIndex(a, b);
		if (genomeDistanceType == GenomeDistanceType.Mash) {
			if (jaccardIndex == 0)
				return 0.75;
			else
				return Math.max(0f, -1.0 / a.getkSize() * Math.log(2.0 * jaccardIndex / (1 + jaccardIndex)));
		} else
			return 1 - jaccardIndex;
	}

	/**
	 * computes the mash distance
	 *
	 * @return mash distance or 0.75, if Jaccard Index is 0
	 */
	public static double compute(MashSketch a, MashSketch b) {
		return compute(computeJaccardIndex(a, b), a.getkSize());
	}

	public static double compute(double jaccardIndex, int k) {
		if (jaccardIndex == 0)
			return 0.75;
		else
			return Math.max(0f, -1.0 / k * Math.log(2.0 * jaccardIndex / (1 + jaccardIndex)));
	}

	public static int computeMinIntersectionSizeForMaxDistance(double maxDistance, int k, int s) {
		for (int j = (s + 1); j > 0; j--) {
			double d = compute((j - 1.0) / (double) s, k);
			if (d > maxDistance)
				return j;
		}
		return 1;
	}

	public static int computeIntersection(MashSketch sketch1, MashSketch sketch2) {
		final int sketchSize1 = sketch1.getSketchSize();
		final int sketchSize2 = sketch2.getSketchSize();

		int intersectionSize = 0;
		int mergeSize = 0;
		int i = 0;
		int j = 0;
		while (true) {
			var value1 = sketch1.getValue(i);
			if (value1 == null)
				break; // too few values
			var value2 = sketch2.getValue(j);
			if (value2 == null)
				break; // too few values

			if (value1 < value2) {
				if (++i == sketchSize1)
					break;
			} else if (value1 > value2) {
				if (++j == sketchSize2)
					break;
			} else {
				intersectionSize++;
				if (++i == sketchSize1)
					break;
				if (++j == sketchSize2)
					break;
			}
			if (++mergeSize == Math.min(sketchSize1, sketchSize2)) {
				break;
			}
		}
		return intersectionSize;
	}

	/**
	 * computes the Jaccard index for two sketches
	 *
	 * @return Jaccard index
	 */
	public static double computeJaccardIndex(MashSketch sketch1, MashSketch sketch2) {
		return (double) computeIntersection(sketch1, sketch2) / (double) Math.min(sketch1.getSketchSize(), sketch2.getSketchSize());
	}

	/*
	public static void main (String[] args) {
		var s=1000;
		var k=21;

		System.err.printf("# s=%d and k=%d%n", s,k);
		System.err.println("#diff <tab> shared <tab> distance");
		for(var diff=0;diff<=s;diff++) {
			System.err.printf("%d\t%d\t%s%n", diff,s-diff,compute((double)(s-diff)/s, k));
		}
	}
	 */
}
