/*
 * IdMapper.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.megan.classification.data.ClassificationFullTree;
import jloda.megan.classification.data.Name2IdMap;
import jloda.util.ProgramProperties;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * tracks mapping files for a named type of classification
 * <p>
 * Read-side only: this copy (in jloda-metagenomics) keeps the classification's tree, name map, disabled
 * ids and active/loaded-map bookkeeping needed to read a meganized DAA. The accession/synonym mapping
 * machinery (loadMappingFile, createIdParser, getAccessionMap, ... which pull megan8.accessiondb) stays
 * in megan8, since a meganized DAA already carries per-read class ids.
 * <p>
 * Daniel Huson, 4.2015
 */
public class IdMapper {
	static public final int NOHITS_ID = -1;
	static public final String NOHITS_LABEL = "No hits";
	static public final int UNASSIGNED_ID = -2;
	static public final String UNASSIGNED_LABEL = "Not assigned";
	static public final int LOW_COMPLEXITY_ID = -3;
	static public final String LOW_COMPLEXITY_LABEL = "Low complexity";
	static public final int UNCLASSIFIED_ID = -4;
	static public final String UNCLASSIFIED_LABEL = "Unclassified";
	static public final int CONTAMINANTS_ID = -6; // -5 used by KEGG
	static public final String CONTAMINANTS_LABEL = "Contaminants";

	public enum MapType {Accession, Synonyms, MeganMapDB}

	private final String cName;

	private final EnumMap<MapType, String> map2Filename = new EnumMap<>(MapType.class);

	private final EnumSet<MapType> loadedMaps = EnumSet.noneOf(MapType.class);

	private final EnumSet<MapType> activeMaps = EnumSet.noneOf(MapType.class);

	final ClassificationFullTree fullTree;
	private final Name2IdMap name2IdMap;

	private boolean useTextParsing;

	private final Set<Integer> disabledIds = new HashSet<>();

	/**
	 * constructor
	 */
	public IdMapper(String name, ClassificationFullTree fullTree, Name2IdMap name2IdMap) {
		this.cName = name;
		this.fullTree = fullTree;
		this.name2IdMap = name2IdMap;
	}

	/**
	 * create tags for parsing header line
	 *
	 * @return short tag
	 */
	public static String[] createTags(String cName) {
		String shortTag = Classification.createShortTag(cName);
		String longTag = cName.toLowerCase() + "|";
		if (shortTag.equals(longTag))
			return new String[]{shortTag};
		else
			return new String[]{shortTag, longTag};
	}

	/**
	 * is the named parsing method loaded
	 *
	 * @return true, if loaded
	 */
	public boolean isLoaded(MapType mapType) {
		return loadedMaps.contains(mapType);
	}

	public boolean isActiveMap(MapType mapType) {
		return activeMaps.contains(mapType);
	}

	public void setActiveMap(MapType mapType, boolean state) {
		if (state)
			activeMaps.add(mapType);
		else
			activeMaps.remove(mapType);
	}

	public void setUseTextParsing(boolean useTextParsing) {
		this.useTextParsing = useTextParsing;
	}

	public boolean isUseTextParsing() {
		return useTextParsing;
	}

	public String getMappingFile(MapType mapType) {
		return map2Filename.get(mapType);
	}

	public boolean hasActiveAndLoaded() {
		return activeMaps.size() > 0 && loadedMaps.size() > 0;
	}

	public String getCName() {
		return cName;
	}

	public String[] getIdTags() {
		if (ProgramProperties.get(cName + "ParseIds", false)) {
			return ProgramProperties.get(cName + "Tags", createTags(cName)); // user can override tags using program property
		} else
			return new String[0];
	}

	public Name2IdMap getName2IdMap() {
		return name2IdMap;
	}

	public Set<Integer> getDisabledIds() {
		return disabledIds;
	}

	public boolean isDisabled(int id) {
		return disabledIds.size() > 0 && disabledIds.contains(id);
	}
}
