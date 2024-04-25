/*
 * SetUtils.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * some simple set utilities
 * Daniel HUson, 2018
 */
public class SetUtils {

	/**
	 * iterator over all elements contained in the intersection of the two collections
	 *
	 * @return intersection
	 */
	public static <T> Iterable<T> intersection(Collection<T> a, Collection<T> b) {
		return () -> new Iterator<>() {
			final Iterator<T> it = a.iterator();
			T v = null;

			{
				while (it.hasNext()) {
					v = it.next();
					if (b.contains(v))
						break;
					v = null;
				}
			}

			@Override
			public boolean hasNext() {
				return v != null;
			}

			@Override
			public T next() {
				final T result = v;
				if (result != null) {
					v = null;
					while (it.hasNext()) {
						v = it.next();
						if (b.contains(v))
							break;
					}
				}
				return result;
			}
		};
	}

	/**
	 * iterator over all elements contained in the union of the given sets
	 *
	 * @return intersection
	 */
	public static <T> Iterable<T> union(final Collection<T>... sets) {
		return () -> new Iterator<>() {
			int which = 0;
			Iterator<T> it = sets.length == 0 ? null : sets[0].iterator();
			final Set<T> seen = new HashSet<>();
			T next = null;

			{
				if (it != null) {
					while (which < sets.length && !it.hasNext()) {
						it = sets[which++].iterator();
					}
					if (it.hasNext())
						next = it.next();
				}
			}

			@Override
			public boolean hasNext() {
				return next != null;
			}

			@Override
			public T next() {
				var result = next;

				if (result != null) {
					next = null;
					while (next == null) {
						if (it.hasNext()) {
							var another = it.next();
							if (!seen.contains(another)) {
								seen.add(another);
								next = another;
							}
						} else if (which < sets.length) {
							it = sets[which++].iterator();
						} else
							break;
					}
				}
				return result;
			}
		};
	}

	/**
	 * iterator over all elements contained in the symmetric difference of two sets
	 *
	 * @return symmetric difference
	 */
	public static <T> Iterable<T> symmetricDifference(Collection<T> a, Collection<T> b) {
		return () -> new Iterator<>() {
			final Iterator<T> it = union(a, b).iterator();
			T v = null;

			{
				while (it.hasNext()) {
					var w = it.next();
					if (a.contains(w) != b.contains(w)) {
						v = w;
						break;
					}
				}
			}

			@Override
			public boolean hasNext() {
				return v != null;
			}

			@Override
			public T next() {
				final T result = v;
				if (result != null) {
					v = null;
					while (it.hasNext()) {
						v = it.next();
						if (a.contains(v) != b.contains(v))
							break;
					}
				}
				return result;
			}
		};
	}

	public static <T> boolean intersect(Collection<T> a, Collection<T> b) {
		return intersection(a, b).iterator().hasNext();
	}
}
