/*
 * Separator.java Copyright (C) 2024 Daniel H. Huson
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

/**
 * separator
 * Daniel Huson, 1.2022
 */
public enum Separator {
	tab("\t"), csv(","), semicolon(";");
	private final String ch;

	Separator(String ch) {
		this.ch = ch;
	}

	public static char guessChar(String line) {
		var sep = guess(line);
		return sep == null ? 0 : sep.getChar().charAt(0);
	}

	public static Separator guess(String line) {
		for (var s : values()) {
			if (line.contains(s.getChar()))
				return s;
		}
		return null;
	}

	public String getChar() {
		return ch;
	}
}
