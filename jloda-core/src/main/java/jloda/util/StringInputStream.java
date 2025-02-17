/*
 *  StringInputStream.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.util;

import java.io.InputStream;

/**
 * a string input stream
 * Daniel Huson, 2.2025
 */
public class StringInputStream extends InputStream {
	private final String data;
	private int position = 0;

	public StringInputStream(String data) {
		this.data = data;
	}

	@Override
	public int read() {
		if (position >= data.length()) {
			return -1;
		}
		return data.charAt(position++);
	}

	@Override
	public int available() {
		return data.length() - position;
	}
}