/*
 * BoyerMoore.java Copyright (C) 2026 Daniel H. Huson
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
 * Copyright (C) 2002-2015 Robert Sedgewick and Kevin Wayne.
 */
package jloda.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The <tt>BoyerMoore</tt> class finds the first occurrence of a pattern string
 * in a text string.
 * <p>
 * This implementation uses the Boyer-Moore algorithm (with the bad-character
 * rule, but not the strong good suffix rule).
 * <p>
 * An instance operates in exactly one mode, determined by its constructor: a
 * {@link String} pattern is searched with {@link #search(String)}, a
 * {@code byte[]} pattern with {@link #search(byte[])} / {@link #search(byte[], int)}
 * and {@link #iterator(byte[])}. Calling a search method that does not match the
 * constructor used throws an {@link IllegalStateException}.
 * <p>
 * For additional documentation,
 * see <a href="http://algs4.cs.princeton.edu/53substring">Section 5.3</a> of
 * <i>Algorithms, 4th Edition</i> by Robert Sedgewick and Kevin Wayne.
 */
public class BoyerMoore {
	private final int R;     // the radix
	private final int[] right;     // the bad-character skip array

	private byte[] pattern;  // store the pattern as a byte array
	private String pat;      // or as a string

	/**
	 * Preprocesses the pattern string.
	 *
	 * @param pat the pattern string
	 */
	public BoyerMoore(String pat) {
		this.R = 256;
		this.pat = pat;

		// position of rightmost occurrence of c in the pattern
		right = new int[R];
		for (int c = 0; c < R; c++)
			right[c] = -1;
		for (int j = 0; j < pat.length(); j++)
			right[pat.charAt(j)] = j;
	}

	/**
	 * Preprocesses the pattern given as a sub-range of a byte array. The bad-character
	 * table always uses a radix of 256, so every possible (unsigned) byte value is covered.
	 *
	 * @param pattern the buffer containing the pattern
	 * @param offset  the start of the pattern within the buffer
	 * @param length  the length of the pattern
	 */
	public BoyerMoore(byte[] pattern, int offset, int length) {
		this.R = 256;
		this.pattern = new byte[length];
		System.arraycopy(pattern, offset, this.pattern, 0, length);

		// position of rightmost occurrence of c in the pattern
		right = new int[R];
		for (int c = 0; c < R; c++)
			right[c] = -1;
		for (int j = 0; j < this.pattern.length; j++)
			right[this.pattern[j] & 0xff] = j;
	}


	/**
	 * Returns the index of the first occurrence of the pattern string
	 * in the text string.
	 *
	 * @param txt the text string
	 * @return the index of the first occurrence of the pattern string
	 * in the text string; N (the text length) if no such match
	 */
	public int search(String txt) {
		if (pat == null)
			throw new IllegalStateException("search(String) requires a String pattern; this instance was built from a byte[]");
		int M = pat.length();
		int N = txt.length();
		int skip;
		for (int i = 0; i <= N - M; i += skip) {
			skip = 0;
			for (int j = M - 1; j >= 0; j--) {
				if (pat.charAt(j) != txt.charAt(i + j)) {
					skip = Math.max(1, j - right[txt.charAt(i + j)]);
					break;
				}
			}
			if (skip == 0) return i;    // found
		}
		return N;                       // not found
	}

	/**
	 * Returns the index of the first occurrence of the pattern in the text.
	 *
	 * @param text the text
	 * @return the index of the first occurrence of the pattern in the text;
	 * N (the text length) if no such match
	 */
	public int search(byte[] text) {
		return search(text, 0);
	}

	/**
	 * Returns the index of the first occurrence of the pattern in the text,
	 * at or after the given start position.
	 *
	 * @param text  the text
	 * @param start the first position to consider
	 * @return the index of the first occurrence of the pattern in the text;
	 * N (the text length) if no such match
	 */
	public int search(byte[] text, int start) {
		if (pattern == null)
			throw new IllegalStateException("search(byte[]) requires a byte[] pattern; this instance was built from a String");
		int M = pattern.length;
		int N = text.length;
		int skip;
		for (int i = start; i <= N - M; i += skip) {
			skip = 0;
			for (int j = M - 1; j >= 0; j--) {
				if (pattern[j] != text[i + j]) {
					skip = Math.max(1, j - right[text[i + j] & 0xff]);
					break;
				}
			}
			if (skip == 0) return i;    // found
		}
		return N;                       // not found
	}

	/**
	 * get an iterator over all (possibly overlapping) occurrences
	 *
	 * @return iterator
	 */
	public Iterator<Integer> iterator(final byte[] text) {
		return new Iterator<>() {
			private int next;

			{
				next = search(text, 0);
			}

			@Override
			public boolean hasNext() {
				return next < text.length;
			}

			@Override
			public Integer next() {
				if (!hasNext())
					throw new NoSuchElementException();
				int result = next;
				next = search(text, result + 1);
				return result;
			}

			@Override
			public void remove() {

			}
		};
	}

}
