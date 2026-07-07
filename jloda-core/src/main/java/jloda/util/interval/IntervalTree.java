/*
 * IntervalTree.java Copyright (C) 2026 Daniel H. Huson
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
 * IntervalTree.java  Copyright (c)
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
 * An Interval Tree is essentially a map from intervals to objects, which
 * can be queried for all data associated with a particular interval
 *
 * @param <T> the type of objects to associate
 * <p>
 * Extended by Daniel Huson, 2.2017, 7.2026  rewrite
 * Original author: Kevin Dolan
 */
public class IntervalTree<T> implements Iterable<Interval<T>> {
	private IntervalNode<T> head;
	private final ArrayList<Interval<T>> intervalList;
	private boolean inSync;
	private boolean sorted;

	private int covered;

	/**
	 * Instantiate a new interval tree with no intervals
	 */
	public IntervalTree() {
		this.head = new IntervalNode<>();
		this.intervalList = new ArrayList<>();
		this.inSync = true;
		this.sorted = true;
		covered = 0;
	}

	/**
	 * Instantiate and build an interval tree with a preset list of intervals
	 *
	 * @param intervalList the list of intervals to use
	 */
	public IntervalTree(Collection<Interval<T>> intervalList) {
		this.head = new IntervalNode<>(intervalList);
		this.intervalList = new ArrayList<>(intervalList);
		this.inSync = true;
		this.sorted = false;
		covered = -1;
	}

	/**
	 * clears the tree
	 */
	public void clear() {
		this.head = new IntervalNode<>();
		this.intervalList.clear();
		this.inSync = true;
		this.sorted = true;
		covered = 0;
	}

	/**
	 * @return the number of entries in the interval list
	 */
	public int size() {
		return intervalList.size();
	}

	/**
	 * is empty?
	 *
	 * @return true, if empty
	 */
	public boolean isEmpty() {
		return intervalList.isEmpty();
	}

	/**
	 * Perform a stabbing query, returning the associated data
	 * Will first rebuild the tree if out of sync
	 *
	 * @param pos the pos to stab
	 * @return the data associated with all intervals that contain pos
	 */
	public ArrayList<T> get(int pos) {
		buildTree();
		final var intervals = head.stab(pos);
		final var result = new ArrayList<T>(intervals.size());
		for (var interval : intervals)
			result.add(interval.getData());
		return result;
	}

	/**
	 * gets an interval that has highest coverage of the given interval, and among those, is longest
	 *
	 * @param minCoverageProportion amount of target of interval that must be covered
	 * @return best interval
	 */
	public Interval<T> getBestInterval(Interval<?> target, double minCoverageProportion) {
		buildTree();
		final var toCover = minCoverageProportion * target.length();
		final var intervals = head.query(target);
		Interval<T> result = null;
		var bestCoverage = 0;
		for (var interval : intervals) {
			final var coverage = interval.intersectionLength(target.getStart(), target.getEnd());
			if (coverage >= toCover && (result == null || coverage > bestCoverage || coverage == bestCoverage && interval.length() > result.length())) {
				result = interval;
				bestCoverage = coverage;
			}
		}
		return result;
	}

	/**
	 * Perform a stabbing query, returning the interval objects
	 * Will first rebuild the tree if out of sync
	 *
	 * @param pos the pos to stab
	 * @return all intervals that contain pos
	 */
	public ArrayList<Interval<T>> getIntervals(int pos) {
		buildTree();
		return head.stab(pos);
	}

	/**
	 * Perform an interval query, returning the associated data
	 * Will first rebuild the tree if out of sync
	 *
	 * @param start the start of the interval to check
	 * @param end   the end of the interval to check
	 * @return the data associated with all intervals that intersects target
	 */
	public ArrayList<T> get(int start, int end) {
		return get(new Interval<>(start, end, null));
	}

	/**
	 * Perform an interval query, returning the associated data
	 * Will first rebuild the tree if out of sync
	 *
	 * @param target the interval to check
	 * @return the data associated with all intervals that intersects target
	 */
	public ArrayList<T> get(Interval<T> target) {
		buildTree();
		final ArrayList<Interval<T>> intervals = head.query(target);
		final ArrayList<T> result = new ArrayList<>(intervals.size());
		for (Interval<T> interval : intervals)
			result.add(interval.getData());
		return result;
	}

	/**
	 * Perform an interval query, returning the interval objects
	 * Will first rebuild the tree if out of sync
	 *
	 * @param target the interval to check
	 * @return all intervals that intersect target
	 */
	public ArrayList<Interval<T>> getIntervals(Interval<T> target) {
		buildTree();
		return head.query(target);
	}

	/**
	 * Perform an interval query, returning the interval objects
	 * Will first rebuild the tree if out of sync
	 *
	 * @param start the start of the interval to check
	 * @param end   the end of the interval to check
	 * @return all intervals that intersects target
	 */
	public ArrayList<Interval<T>> getIntervals(int start, int end) {
		return getIntervals(new Interval<>(start, end, null));
	}

	/**
	 * Add an interval object to the interval tree's list.
	 * Where possible, use addAll() instead of add(), to avoid an unbalanced datastructure
	 * Interval is added directly, does not trigger a complete rebuild
	 *
	 * @param interval the interval object to add
	 */
	public void add(Interval<T> interval) {
		intervalList.add(interval);
		if (head != null)
			head.add(interval);
		else
			inSync = false;
		sorted = false;
		covered = -1;
	}

	/**
	 * Add an interval object to the interval tree's list
	 * Interval is added directly, does not trigger a complete rebuild
	 *
	 * @param begin the beginning of the interval
	 * @param end   the end of the interval
	 * @param data  the data to associate
	 */
	public void add(int begin, int end, T data) {
		add(new Interval<>(begin, end, data));
	}

	/**
	 * adds a list of intervals
	 * Will not rebuild until the next query or call to build
	 */
	public void addAll(Collection<Interval<T>> intervals) {
		intervalList.addAll(intervals); // don't add one by one as this will lead to an unbalanced tree
		inSync = false;
		sorted = false;
		covered = -1;
	}

	/**
	 * sets a list of intervals
	 * Will not rebuild until the next query or call to build
	 */
	public void setAll(Collection<Interval<T>> intervals) {
		clear();
		intervalList.addAll(intervals);
		inSync = false;
		sorted = false;
		covered = -1;
	}

	/**
	 * remove an interval
	 * Will not rebuild until the next query or call to build
	 *
	 * @return true, if was contained
	 */
	public boolean remove(Interval<T> interval) {
		var removed = intervalList.remove(interval);
		if (removed) {
			inSync = false;
			covered = -1;
		}
		return removed;
	}

	/**
	 * remove an interval associated with the given data
	 * Will not rebuild until the next query or call to build
	 */
	public void remove(T data) {
		var interval = find(data);
		if (interval != null) {
			remove(interval);
		}
	}

	/**
	 * find an interval whose data equals the given data
	 *
	 * @param data (can be null)
	 * @return interval or null
	 */
	public Interval<T> find(T data) {
		sortList();
		for (var interval : intervalList) {
			if ((data == null && interval.getData() == null) || (interval.getData() != null && interval.getData().equals(data)))
				return interval;
		}
		return null;
	}

	/**
	 * remove a collection of intervals
	 * Will not rebuild until the next query or call to build
	 */
	public void removeAll(Collection<Interval<T>> intervals) {
		var removed = intervalList.removeAll(intervals);
		if (removed) {
			inSync = false;
			covered = -1;
		}
	}

	/**
	 * Build the interval tree to reflect the list of intervals, if not already in sync
	 */
	public void buildTree() {
		if (!inSync) {
			head = new IntervalNode<>(intervalList);
			inSync = true;
		}
	}

	/**
	 * gets the list of all intervals in order of the start coordinate
	 *
	 * @return intervals
	 */
	public List<Interval<T>> intervals() {
		return getAllIntervals(true);
	}

	/**
	 * gets all intervals
	 *
	 * @param sort first sort all intervals by their start coordinate
	 * @return all intervals
	 */
	public List<Interval<T>> getAllIntervals(boolean sort) {
		if (sort)
			sortList();
		return new AbstractList<>() { // wrap like this so interval list can't be changed
			@Override
			public Interval<T> get(int index) {
				return intervalList.get(index);
			}

			@Override
			public int size() {
				return intervalList.size();
			}
		};
	}

	/**
	 * gets the list of all data values in order of start coordinate
	 *
	 * @return values
	 */
	public List<T> values() {
		return getAllValues(true);
	}

	/**
	 * gets the list of all data values
	 *
	 * @param sort first sort all intervals by their start coordinate
	 * @return values
	 */
	public List<T> getAllValues(boolean sort) {
		if (sort)
			sortList();

		return new AbstractList<>() {
			@Override
			public T get(int index) {
				return intervalList.get(index).getData();
			}

			@Override
			public int size() {
				return intervalList.size();
			}
		};
	}

	/**
	 * iterates over all intervals, in order of start coordinate
	 *
	 * @return iterator
	 */
	public Iterator<Interval<T>> iterator() {
		return getAllIntervals(true).iterator();
	}

	/**
	 * sets the interval list returned by values() or iterator(), if not already sorted
	 */
	private void sortList() {
		if (!sorted) {
			intervalList.sort(Interval::compareCoordinatesTo);
			sorted = true;
		}
	}

	@Override
	public String toString() {
		return head.toStringRec(0);
	}

	/**
	 * get the number of positions covered
	 *
	 * @return covered
	 */
	public int getCovered() {
		if (intervalList.isEmpty())
			return 0;
		if (covered >= 0)
			return covered;
		else
			covered = 0;
		var start = Integer.MIN_VALUE;
		var end = Integer.MIN_VALUE;

		for (var interval : getAllIntervals(true)) {
			if (start == Integer.MIN_VALUE) {
				start = interval.getStart();
			} else if (interval.getStart() > end) {
				covered += (end - start) + 1;
				start = interval.getStart();
			}
			end = Math.max(end, interval.getEnd());
		}
		covered += (end - start) + 1;
		return covered;
	}

	/**
	 * get intervals sorted by decreasing amount of interval a,b covered
	 *
	 * @return list
	 */
	public Interval<T>[] getIntervalsSortedByDecreasingIntersectionLength(final int a, final int b) {
		final var intervals = getIntervals(a, b);
		final var n = intervals.size();
		@SuppressWarnings("unchecked") final Interval<T>[] array = intervals.toArray(new Interval[0]);
		// compute each intersection length exactly once (avoids O(n log n) recomputation in the comparator)
		final var length = new int[n];
		for (var i = 0; i < n; i++)
			length[i] = array[i].intersectionLength(a, b);
		final var order = new Integer[n];
		for (var i = 0; i < n; i++)
			order[i] = i;
		Arrays.sort(order, (i, j) -> Integer.compare(length[j], length[i]));
		@SuppressWarnings("unchecked") final Interval<T>[] result = new Interval[n];
		for (var i = 0; i < n; i++)
			result[i] = array[order[i]];
		return result;
	}
}