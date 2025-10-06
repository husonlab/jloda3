/*
 * Array2D.java (updated & documented)
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

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Fixed-size 2D array with null-safe helpers.
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>Indices are zero-based; bounds are checked on access/mutation.</li>
 *   <li>Generic storage uses {@code Object[][]} internally; casts are localized.</li>
 *   <li>{@link #computeIfAbsent(int, int, BiFunction)} mirrors Map semantics.</li>
 * </ul>
 */
public class Array2D<T> {
    private final Object[][] table;

	/**
	 * Create a rows×cols matrix. Rows and cols must be &ge; 0.
	 */
    public Array2D(int numberOfRows, int numberOfCols) {
		if (numberOfRows < 0 || numberOfCols < 0) {
			throw new IllegalArgumentException("numberOfRows and numberOfCols must be >= 0");
		}
		this.table = new Object[numberOfRows][numberOfCols];
    }

	/**
	 * Get the value at (row,col).
	 */
	@SuppressWarnings("unchecked")
    public T get(int row, int col) {
		checkBounds(row, col);
        return (T) table[row][col];
	}

	/** Get the value at (row,col) or return {@code defaultValue} if null. */
    public T getOrDefault(int row, int col, T defaultValue) {
        T result = get(row, col);
		return (result == null ? defaultValue : result);
	}

	/**
	 * If (row,col) is null, compute a value and store it; return the current or computed value.
	 * If the mapping function returns null, the cell remains null.
     */
    public T computeIfAbsent(int row, int col, BiFunction<Integer, Integer, T> function) {
        T result = get(row, col);
        if (result == null) {
			T computed = function.apply(row, col);
			if (computed != null) {
				put(row, col, computed);
				return computed;
			}
			return null;
        }
        return result;
	}

	/** Set the value at (row,col). */
    public void put(int row, int col, T value) {
		checkBounds(row, col);
        table[row][col] = value;
	}

	/**
	 * Fill every cell with {@code value}.
	 */
	public void fill(T value) {
		for (int r = 0; r < table.length; r++) {
			Arrays.fill(table[r], value);
		}
	}

	/** Clear all cells (set to null). */
	public void clear() {
		fill(null);
	}

	/** @return number of rows. */
    public int getNumberOfRows() {
        return table.length;
	}

	/** @return number of columns (0 for empty matrix). */
    public int getNumberOfColumns() {
		return table.length == 0 ? 0 : table[0].length;
	}

	/**
	 * Iterate over all cells (row-major), passing (row, col, value) to the consumer.
	 * Null values are passed as null.
	 */
	@SuppressWarnings("unchecked")
	public void forEach(BiConsumer<Integer, Integer> indexConsumer, BiConsumer<Integer, Integer> valueIgnored) {
		// Kept for backward compatibility if someone expects a two-arg variant—intentionally no-op on value
		for (int r = 0; r < table.length; r++) {
			for (int c = 0; c < (table[r] == null ? 0 : table[r].length); c++) {
				indexConsumer.accept(r, c);
			}
		}
	}

	/**
	 * Iterate over all cells (row-major), passing (row, col, value) to the consumer.
	 */
	@SuppressWarnings("unchecked")
	public void forEach(BiConsumer<Integer, Integer> indexConsumer, TriConsumer<Integer, Integer, T> cellConsumer) {
		for (int r = 0; r < table.length; r++) {
			for (int c = 0; c < table[r].length; c++) {
				indexConsumer.accept(r, c);
				cellConsumer.accept(r, c, (T) table[r][c]);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
		if (!(o instanceof Array2D<?> that)) return false;
		return getNumberOfRows() == that.getNumberOfRows()
			   && getNumberOfColumns() == that.getNumberOfColumns()
			   && Arrays.deepEquals(this.table, that.table);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(table);
	}

	@Override
	public String toString() {
		return "Array2D[" + getNumberOfRows() + "x" + getNumberOfColumns() + "]";
	}

	/* ---------- helpers ---------- */

	private void checkBounds(int row, int col) {
		int rows = getNumberOfRows();
		int cols = getNumberOfColumns();
		if (row < 0 || row >= rows || col < 0 || col >= cols) {
			throw new IndexOutOfBoundsException(
					"Index (" + row + "," + col + ") out of bounds for " + rows + "x" + cols);
		}
	}

	/**
	 * Simple tri-consumer interface to avoid extra dependencies.
	 */
	@FunctionalInterface
	public interface TriConsumer<A, B, C> {
		void accept(A a, B b, C c);
    }
}