/*
 * Interval.java Copyright (C) 2024 Daniel H. Huson
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
 * Interval.java Copyright (c)
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

import java.util.Objects;

/**
 * The Interval class maintains an interval with some associated data
 *
 * @param <T> The type of data being stored
 * @author Kevin Dolan
 * Modified by Daniel Huson, 2.2017
 */
public class Interval<T> implements Comparable<Interval<T>> {
	private final int start;
	private final int end;
	private T data;

	/**
	 * constructor
	 *
	 * @param start (inclusive)
	 * @param end   (inclusive)
	 */
	public Interval(int start, int end, T data) {
		if (start <= end) {
			this.start = start;
			this.end = end;
		} else {
			this.end = start;
			this.start = end;
		}
		this.data = data;
	}

	/**
	 * get start
	 */
	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	/**
	 * get length
	 *
	 * @return length
	 */
	public int length() {
		return end - start + 1;
	}

	/**
	 * get data
	 *
	 * @return data
	 */
	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	/**
	 * @return true if this interval contains pos (inclusive)
	 */
	public boolean contains(int pos) {
		return pos <= end && pos >= start;
	}

	/**
	 * does this interval contain the other?
	 *
	 * @return true, if other contained in this
	 */
	public boolean contains(Interval<?> other) {
		return start <= other.start && end >= other.end;
	}

	/**
	 * @return return true if this interval intersects other
	 */
	public boolean intersects(Interval<?> other) {
		return other.getEnd() >= start && other.getStart() <= end;
	}

	/**
	 * Return -1 if this interval's start pos is less than the other, 1 if greater
	 * In the event of a tie, -1 if this interval's end pos is less than the other, 1 if greater, 0 if same
	 *
	 * @return 1 or -1
	 */
	public int compareTo(Interval<T> other) {
		if (start < other.getStart())
			return -1;
		else if (start > other.getStart())
			return 1;
		else return Integer.compare(end, other.getEnd());
	}

	/**
	 * Returns true if start and end are equals
	 *
	 * @return true, if start and end are equals
	 */
	public boolean equals(Interval<T> other) {
		return start == other.getStart() && end == other.end;
	}

	/**
	 * compute the overlap with other
	 *
	 * @return length of overlap
	 */
	public double overlap(Interval<T> other) {
		return Math.max(0, Math.min(end, other.end) - Math.max(start, other.start) + 1);
	}

	/**
	 * gets the length of the intersection with interval [a,b] or [b,a]
	 *
	 * @return intersection length
	 */
	public int intersectionLength(int a, int b) {
		return Math.min(end, Math.max(a, b)) - Math.max(start, Math.min(a, b));
	}


	/**
	 * are the intervals equal and have equal data?
	 *
	 * @param other the other
	 * @return true, if same intervals and equal data
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		return other instanceof Interval<?> interval && start == interval.start && end == interval.end &&
				(data == null && interval.data == null || data != null && data.equals(interval.data));
	}

	/**
	 * hash code for interval and data
	 *
	 * @return hash code
	 */
	@Override
	public int hashCode() {
		return Objects.hash(start, end, data);
	}
}