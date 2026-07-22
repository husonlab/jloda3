/*
 *  ExampleEntry.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.examples;

/**
 * One bundled example.
 * <p>
 * The stored resource always ends in {@code .dat}, because that is what gets bundled
 * into iOS/GluonFX native images. The <em>real</em> name of the file, with the suffix
 * the application's parsers expect, is carried separately in {@link #fileName()}.
 *
 * @param resourcePath absolute classpath resource, e.g. {@code /catrenet/examples/formose.crs.dat}
 * @param displayName  label shown in the menu, e.g. {@code Formose reaction}
 * @param fileName     real file name with its true suffix, e.g. {@code formose.crs}
 */
public record ExampleEntry(String resourcePath, String displayName, String fileName) {

	/**
	 * The real suffix, lower case and without the dot, or "" if the file name has none.
	 * Use this to select a parser or importer.
	 */
	public String suffix() {
		var i = fileName.lastIndexOf('.');
		return (i > 0 && i < fileName.length() - 1) ? fileName.substring(i + 1).toLowerCase() : "";
	}

	/**
	 * The real file name without its suffix, e.g. {@code formose}.
	 */
	public String baseName() {
		var i = fileName.lastIndexOf('.');
		return (i > 0) ? fileName.substring(0, i) : fileName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
