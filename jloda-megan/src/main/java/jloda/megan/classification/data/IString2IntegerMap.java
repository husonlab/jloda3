/*
 * IString2IntegerMap.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.megan.classification.data;

import java.io.Closeable;
import java.io.IOException;

/**
 * String to integer map
 * Created by huson on 7/15/16.
 */
public interface IString2IntegerMap extends Closeable {
	/**
	 * get a value
	 *
	 * @return value or 0
	 */
	int get(String key) throws IOException;

	/**
	 * return the number of entries
	 *
	 * @return size
	 */
	int size();

	/**
	 * an instance that another thread may use, or this one if it is already safe to share.
	 * <p>
	 * In-memory and file-backed maps share fine and so return themselves, which is the default. A map backed
	 * by a SQLite connection must hand out its own: MEGAN runs one id parser per thread, and they would
	 * otherwise contend on a single connection.
	 *
	 * @return an instance for the caller's own use
	 */
	default IString2IntegerMap duplicate() throws IOException {
		return this;
	}
}
