/*
 * IntervalNode.java Copyright (C) 2026 Daniel H. Huson
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

/*
 * IntervalNode.java  Copyright (c)
 * do What The F... you want to Public License
 *  Version 1.0, March 2000
 *  * Copyright (C) 2000 Banlu Kemiyatorn (]d).
 * 136 Nives 7 Jangwattana 14 Laksi Bangkok
 * Everyone is permitted to copy and distribute verbatim copies
 * of this license document, but changing it is not allowed.
 * Ok, the purpose of this license is simple and you just
 * DO WHAT THE F... YOU WANT TO.
 */
package jloda.util.interval;

import java.util.*;

/**
 * The Node class contains the interval tree information for one single node
 * <p>
 * The intervals stored at a node all contain the node's center. They are kept in two
 * parallel arrays, one sorted by start (ascending) and one sorted by end (descending).
 * Because every stored interval contains the center, a stabbing or intersection query
 * only needs to scan one of the two arrays and can stop as soon as an interval fails to
 * match, which makes queries substantially faster and allocation-free per node.
 * <p>
 * Extended by Daniel Huson, 2.2017, 7.2026  rewrite
 * Original author: Kevin Dolan
 */
public class IntervalNode<T> {
	private Interval<T>[] byStart; // intervals containing center, sorted by increasing start (ties: increasing end)
	private Interval<T>[] byEnd;   // the same intervals, sorted by decreasing end (ties: decreasing start)
	private final int center;
	private IntervalNode<T> leftNode;
	private IntervalNode<T> rightNode;

	/**
	 * create new empty node
	 */
	IntervalNode() {
		center = 0;
		byStart = newArray(0);
		byEnd = newArray(0);
		leftNode = null;
		rightNode = null;
	}

	/**
	 * create node for a collection of intervals
	 */
	IntervalNode(Collection<Interval<T>> intervals) {
		if (intervals.isEmpty()) {
			center = 0;
			byStart = newArray(0);
			byEnd = newArray(0);
			leftNode = null;
			rightNode = null;
		} else {
			// set center to median:
			if (intervals.size() == 1) {
				final var interval = intervals.iterator().next();
				center = (interval.getStart() + interval.getEnd()) >>> 1;
			} else {
				final var endPoints = new int[2 * intervals.size()];

				var z = 0;
				for (var interval : intervals) {
					endPoints[z++] = interval.getStart();
					endPoints[z++] = interval.getEnd();
				}
				Arrays.sort(endPoints);
				center = endPoints[endPoints.length >>> 1]; // median, not interpolated
			}

			final var left = new ArrayList<Interval<T>>();
			final var right = new ArrayList<Interval<T>>();
			final var here = new ArrayList<Interval<T>>();

			for (final var interval : intervals) {
				if (interval.getEnd() < center)
					left.add(interval);
				else if (interval.getStart() > center)
					right.add(interval);
				else
					here.add(interval);
			}

			byStart = here.toArray(newArray(here.size()));
			byEnd = here.toArray(newArray(here.size()));
			Arrays.sort(byStart, IntervalNode::compareByStart);
			Arrays.sort(byEnd, IntervalNode::compareByEnd);

			if (!left.isEmpty())
				leftNode = new IntervalNode<>(left);
			if (!right.isEmpty())
				rightNode = new IntervalNode<>(right);
		}
	}

	/**
	 * create node with single interval
	 */
	IntervalNode(Interval<T> interval) {
		center = (interval.getStart() + interval.getEnd()) / 2;
		byStart = newArray(1);
		byStart[0] = interval;
		byEnd = newArray(1);
		byEnd[0] = interval;
	}

	/**
	 * add a node to an existing tree
	 */
	void add(Interval<T> interval) {
		if (interval.getEnd() < center) {
			if (leftNode == null)
				leftNode = new IntervalNode<>(interval);
			else
				leftNode.add(interval);
		} else if (interval.getStart() > center) {
			if (rightNode == null)
				rightNode = new IntervalNode<>(interval);
			else
				rightNode.add(interval);
		} else {
			// this interval contains the center, so it belongs to this node
			byStart = insertSorted(byStart, interval, IntervalNode::compareByStart);
			byEnd = insertSorted(byEnd, interval, IntervalNode::compareByEnd);
		}
	}

	/**
	 * Perform a stabbing query on the node
	 *
	 * @param pos the pos to query at
	 * @return all intervals containing pos
	 */
	ArrayList<Interval<T>> stab(int pos) {
		final var result = new ArrayList<Interval<T>>();
		stab(pos, result);
		return result;
	}

	/**
	 * Perform a stabbing query, appending matches to the given accumulator.
	 * Every interval at this node contains the center, so an interval [s,e] contains pos iff:
	 * s &le; pos (when pos &le; center, because then pos &le; center &le; e) or
	 * e &ge; pos (when pos &gt; center, because then s &le; center &lt; pos). This lets us scan
	 * only the cheaper of the two arrays and stop as soon as an interval fails.
	 *
	 * @param pos    the pos to query at
	 * @param result accumulator that all matching intervals are appended to
	 */
	private void stab(int pos, ArrayList<Interval<T>> result) {
		if (pos <= center) {
			for (final var interval : byStart) {
				if (interval.getStart() <= pos)
					result.add(interval);
				else
					break; // sorted by increasing start
			}
		} else { // pos > center
			for (final var interval : byEnd) {
				if (interval.getEnd() >= pos)
					result.add(interval);
				else
					break; // sorted by decreasing end
			}
		}

		if (pos < center) {
			if (leftNode != null)
				leftNode.stab(pos, result);
		} else if (pos > center) {
			if (rightNode != null)
				rightNode.stab(pos, result);
		}
	}

	/**
	 * Perform an interval intersection query on the node
	 *
	 * @param target the interval to intersects
	 * @return all intervals containing time
	 */
	ArrayList<Interval<T>> query(Interval<?> target) {
		final var result = new ArrayList<Interval<T>>();
		query(target, result);
		return result;
	}

	/**
	 * Perform an interval intersection query, appending matches to the given accumulator.
	 * Every interval at this node contains the center, so relative to a target [qs,qe]:
	 * if the target straddles the center every stored interval intersects it;
	 * if qe &lt; center an interval intersects iff its start &le; qe;
	 * if qs &gt; center an interval intersects iff its end &ge; qs.
	 * Each case scans only one array and stops early.
	 *
	 * @param target the interval to intersect
	 * @param result accumulator that all matching intervals are appended to
	 */
	private void query(Interval<?> target, ArrayList<Interval<T>> result) {
		final var qs = target.getStart();
		final var qe = target.getEnd();

		if (qs <= center && qe >= center) {
			// target contains the center, and so does every stored interval => all intersect
			Collections.addAll(result, byStart);
		} else if (qe < center) {
			// then every stored end >= center > qe >= qs, so end >= qs always; intersect iff start <= qe
			for (final var interval : byStart) {
				if (interval.getStart() <= qe)
					result.add(interval);
				else
					break; // sorted by increasing start
			}
		} else { // qs > center
			// then every stored start <= center < qs <= qe, so start <= qe always; intersect iff end >= qs
			for (final var interval : byEnd) {
				if (interval.getEnd() >= qs)
					result.add(interval);
				else
					break; // sorted by decreasing end
			}
		}

		if (qs < center && leftNode != null)
			leftNode.query(target, result);
		if (qe > center && rightNode != null)
			rightNode.query(target, result);
	}

	/**
	 * compares two intervals by increasing start, then increasing end
	 */
	private static int compareByStart(Interval<?> a, Interval<?> b) {
		final var c = Integer.compare(a.getStart(), b.getStart());
		return c != 0 ? c : Integer.compare(a.getEnd(), b.getEnd());
	}

	/**
	 * compares two intervals by decreasing end, then decreasing start
	 */
	private static int compareByEnd(Interval<?> a, Interval<?> b) {
		final var c = Integer.compare(b.getEnd(), a.getEnd());
		return c != 0 ? c : Integer.compare(b.getStart(), a.getStart());
	}

	/**
	 * inserts an interval into a sorted array, returning the (new, longer) array.
	 * Equal elements are placed after existing ones, keeping insertion order stable.
	 */
	private static <T> Interval<T>[] insertSorted(Interval<T>[] array, Interval<T> interval, Comparator<Interval<?>> comparator) {
		var lo = 0;
		var hi = array.length;
		while (lo < hi) {
			final var mid = (lo + hi) >>> 1;
			if (comparator.compare(array[mid], interval) <= 0)
				lo = mid + 1;
			else
				hi = mid;
		}
		final var result = IntervalNode.<T>newArray(array.length + 1);
		System.arraycopy(array, 0, result, 0, lo);
		result[lo] = interval;
		System.arraycopy(array, lo, result, lo + 1, array.length - lo);
		return result;
	}

	/**
	 * creates a new typed interval array
	 */
	@SuppressWarnings("unchecked")
	private static <T> Interval<T>[] newArray(int size) {
		return (Interval<T>[]) new Interval[size];
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append(center).append(": ");
		for (final var interval : byStart) {
			sb.append("[").append(interval.getStart()).append(",").append(interval.getEnd()).append(",").append(interval.getData()).append("] ");
		}
		return sb.toString();
	}

	/**
	 * recursively creates string that describes this node and subtree below
	 *
	 * @return string
	 */
	public String toStringRec(int level) {
		final var sb = new StringBuilder();
		sb.append("\t".repeat(Math.max(0, level)));
		sb.append(this).append("\n");
		if (leftNode != null)
			sb.append(leftNode.toStringRec(level + 1));
		if (rightNode != null)
			sb.append(rightNode.toStringRec(level + 1));
		return sb.toString();
	}
}