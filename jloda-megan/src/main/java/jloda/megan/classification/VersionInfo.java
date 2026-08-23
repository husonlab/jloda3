/*
 * VersionInfo.java Copyright (C) 2026 Daniel H. Huson
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
package jloda.megan.classification;

import java.util.HashMap;
import java.util.Map;

/**
 * where each loaded classification came from, keyed by "&lt;name&gt; tree" — the source and date recorded in the
 * classification database, shown by MEGAN's viewers and its "show versionInfo" command.
 * <p>
 * This used to be a static map inside {@code megan8.core.Document}, which is a Swing class; the map itself
 * never was one. It lives here because {@link Classification#loadFromDatabase} is what fills it, and that has
 * moved. MEGAN's {@code Document.getVersionInfo()} returns this same map, so its callers are unaffected.
 * <p>
 * Daniel Huson, 8.2026
 */
public class VersionInfo {
	private static final Map<String, String> name2versionInfo = new HashMap<>();

	/**
	 * records where the named item came from
	 */
	public static void put(String name, String info) {
		name2versionInfo.put(name, info);
	}

	/**
	 * the live table, so a caller can read or clear it
	 */
	public static Map<String, String> getAll() {
		return name2versionInfo;
	}
}
