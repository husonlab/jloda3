/*
 *  PyTest.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.py;

import jloda.phylo.NewickIO;
import jloda.phylo.algorithms.RootedNetworkProperties;

import java.io.IOException;

/**
 * methods for testing Python connectivity
 * Daniel Huson, 1.2026
 */
public class PyTest {
	public static String getInfo(String newickString) throws IOException {
		var tree = NewickIO.valueOf(newickString);
		return RootedNetworkProperties.computeInfoString(tree);
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			args = new String[]{"((a,b),(c,d));"};
		}
		System.out.println(getInfo(args[0]));
	}
}
