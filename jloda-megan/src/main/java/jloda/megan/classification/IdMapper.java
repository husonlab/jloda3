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

import java.util.function.Predicate;
import jloda.megan.classification.data.Accession2IdMapFactory;
import jloda.megan.classification.data.IString2IntegerMap;
import jloda.megan.classification.data.IString2IntegerMapFactory;
import jloda.megan.classification.data.String2IntegerMap;
import jloda.util.ProgramProperties;
import jloda.util.Basic;
import jloda.util.progress.ProgressListener;
import jloda.megan.classification.data.ClassificationFullTree;
import jloda.megan.classification.data.Name2IdMap;

import java.io.IOException;
import java.util.*;

/**
 * tracks mapping files for a named type of classification
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

	/**
	 * property naming the classifications that are taxonomic, and so are parsed with an LCA rather than by
	 * first hit. MEGAN's key and default, kept verbatim so a user's existing setting still applies; the value
	 * is read from the shared {@link ProgramProperties}, which MEGAN populates before loading anything.
	 */
	public static final String TAXONOMIC_CLASSIFICATIONS = "AdditionalTaxonomyViewers";
	public static final String[] TAXONOMIC_CLASSIFICATIONS_DEFAULT = {"Taxonomy", "GTDB"};

	public static IString2IntegerMapFactory accessionMapFactory = new Accession2IdMapFactory();

	/**
	 * opens a MEGAN mapping database ({@code megan-map*.db}) for one classification. The reader for those
	 * lives in MEGAN, not here, so MEGAN installs this at startup; a program that installs nothing simply
	 * cannot use {@link MapType#MeganMapDB}, which is the honest outcome rather than a link error.
	 */
	public interface MeganMapDBFactory {
		IString2IntegerMap open(String mappingDBFile, String cName) throws IOException;
	}

	public static MeganMapDBFactory meganMapDBFactory = null;

	/**
	 * whether a mapping-database file is one this program may open. MEGAN Community Edition installs a filter
	 * that rejects Ultimate Edition databases; the default accepts everything, since only MEGAN makes that
	 * distinction.
	 */
	public static Predicate<String> meganMapDBFileFilter = fileName -> true;

	public enum MapType {Accession, Synonyms, MeganMapDB}

	private final String cName;

	private final EnumMap<MapType, String> map2Filename = new EnumMap<>(MapType.class);

	private final EnumSet<MapType> loadedMaps = EnumSet.noneOf(MapType.class);

	private final EnumSet<MapType> activeMaps = EnumSet.noneOf(MapType.class);

	final ClassificationFullTree fullTree;
	private final Name2IdMap name2IdMap;

	private boolean useTextParsing;

	private final Set<Integer> disabledIds = new HashSet<>();

	private IString2IntegerMap accessionMap = null;
	private String2IntegerMap synonymsMap = null;

	private final IdParser.Algorithm algorithm;

	/**
	 * constructor
	 */
	public IdMapper(String name, ClassificationFullTree fullTree, Name2IdMap name2IdMap) {
		this.cName = name;
		this.fullTree = fullTree;
		this.name2IdMap = name2IdMap;

		algorithm = (Arrays.asList(ProgramProperties.get(TAXONOMIC_CLASSIFICATIONS, TAXONOMIC_CLASSIFICATIONS_DEFAULT)).contains(name) ? IdParser.Algorithm.LCA : IdParser.Algorithm.First_Hit);
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
	 * load the named file of the given map type
	 */
	public void loadMappingFile(String fileName, MapType mapType, boolean reload, ProgressListener progress) throws IOException {
		switch (mapType) {
			case Accession -> {
				if (accessionMap == null || reload) {
					if (accessionMap != null) {
						closeAccessionMap();
					}

					this.accessionMap = accessionMapFactory.create(name2IdMap, fileName, progress);
					loadedMaps.add(mapType);
					activeMaps.add(mapType);
					map2Filename.put(mapType, fileName);

				}
			}
			case Synonyms -> {
				if (synonymsMap == null || reload) {
					if (synonymsMap != null) {
						synonymsMap.close();
					}
					final String2IntegerMap synonymsMap = new String2IntegerMap();

					synonymsMap.loadFile(name2IdMap, fileName, progress);
					this.synonymsMap = synonymsMap;
					loadedMaps.add(mapType);
					activeMaps.add(mapType);
					map2Filename.put(mapType, fileName);

				}
			}
			case MeganMapDB -> {
				if (accessionMap == null || reload) {
					if (accessionMap != null) {
						closeAccessionMap();
					}
					if (meganMapDBFactory == null)
						throw new IOException("Mapping databases are not available in this program: no MeganMapDBFactory installed");
					try {
						this.accessionMap = meganMapDBFactory.open(fileName, cName);
						loadedMaps.add(mapType);
						activeMaps.add(mapType);
						map2Filename.put(mapType, fileName);
					} catch (Exception e) {
						throw new IOException(e);
					}
				}
			}
		}
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

	/**
	 * creates a new id parser for this mapper
	 */
	public IdParser createIdParser() {
		// one parser per thread, so a map that cannot be shared across threads - a SQLite mapping database -
		// hands out its own instance and gets its own mapper to hold it. Maps that are safe to share return
		// themselves from duplicate(), and the parser then works off this mapper directly.
		var mapper = this;
		if (accessionMap != null) {
			try {
				final var ownCopy = accessionMap.duplicate();
				if (ownCopy != accessionMap) {
					mapper = new IdMapper(cName, fullTree, name2IdMap);
					mapper.setUseTextParsing(isUseTextParsing());
					mapper.adoptAccessionMap(ownCopy, MapType.MeganMapDB, map2Filename.get(MapType.MeganMapDB));
				}
			} catch (IOException e) {
				Basic.caught(e);
			}
		}
		final IdParser idParser = new IdParser(mapper);
		idParser.setAlgorithm(algorithm);
		return idParser;
	}

	/**
	 * takes over an already-open accession map, recording it as loaded and active under the given type. Used
	 * by {@link #createIdParser()}, which has the map in hand and must not re-open it.
	 */
	private void adoptAccessionMap(IString2IntegerMap map, MapType mapType, String fileName) {
		this.accessionMap = map;
		loadedMaps.add(mapType);
		activeMaps.add(mapType);
		if (fileName != null)
			map2Filename.put(mapType, fileName);
	}

	/**
	 * get a id from an accession
	 *
	 * @return KO id or null
	 */
	public Integer getIdFromAccession(String accession) throws IOException {
		if (isLoaded(MapType.Accession) || isLoaded(MapType.MeganMapDB)) {
			return getAccessionMap().get(accession);
		}
		return null;
	}

	public String getMappingFile(MapType mapType) {
		return map2Filename.get(mapType);
	}

	public IString2IntegerMap getAccessionMap() {
		return accessionMap;
	}

	public String2IntegerMap getSynonymsMap() {
		return synonymsMap;
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

	private void closeAccessionMap() {
		if (accessionMap != null) {
			try {
				accessionMap.close();
			} catch (IOException e) {
				Basic.caught(e);
			}
		}
		accessionMap = null;
	}

	public boolean isDisabled(int id) {
		return disabledIds.size() > 0 && disabledIds.contains(id);
	}
}
