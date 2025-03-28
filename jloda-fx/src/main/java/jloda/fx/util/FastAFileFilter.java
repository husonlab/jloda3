/*
 * FastAFileFilter.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.util;

import javafx.stage.FileChooser;

import java.io.File;

/**
 * FastA file filter
 * Daniel Huson, 3.2019
 */
public class FastAFileFilter {
	private static FileChooser.ExtensionFilter instance;

	public static FileChooser.ExtensionFilter getInstance() {
		if (instance == null)
			instance = new FileChooser.ExtensionFilter("FastA File", "*.fasta", "*.fna", "*.fa", "*.fasta.gz", "*.faa", "*.faa.gz", "*.fna.gz", "*.fa.gz");
		return instance;
	}

	public static boolean accepts(File selectedFile) {
		for (var ex : getInstance().getExtensions()) {
			if (ex.startsWith("*"))
				ex = ex.substring(1);
			if (selectedFile.getName().toLowerCase().endsWith(ex))
				return true;
		}
		return false;
	}
}
