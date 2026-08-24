/*
 * ClassificationManager.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.megan.classification.db.IClassificationsDatabase;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;

import java.io.IOException;
import java.util.*;

/**
 * manages classification data
 * Daniel Huson, 4.2015
 */
public class ClassificationManager {
	private static final Set<String> allSupportedClassifications = new TreeSet<>();
	private static final Set<String> allSupportedClassificationsExcludingNCBITaxonomy = new TreeSet<>();

	private static final Map<String, Classification> name2classification = new TreeMap<>();

	private static final ArrayList<String> defaultClassificationsList = new ArrayList<>();
	private static final ArrayList<String> defaultClassificationsListExcludingNCBITaxonomy = new ArrayList<>();


	private static String meganMapDBFile;
	private static boolean useFastAccessionMappingMode;

	private static IClassificationsDatabase classificationsDatabase;

	static {
		defaultClassificationsListExcludingNCBITaxonomy.add("GTDB");
		defaultClassificationsListExcludingNCBITaxonomy.add("EGGNOG");
		defaultClassificationsListExcludingNCBITaxonomy.add("SEED");
		defaultClassificationsListExcludingNCBITaxonomy.add("GO");
		defaultClassificationsListExcludingNCBITaxonomy.add("EC");
		allSupportedClassificationsExcludingNCBITaxonomy.addAll(defaultClassificationsListExcludingNCBITaxonomy);

		defaultClassificationsList.addAll(defaultClassificationsListExcludingNCBITaxonomy);
		defaultClassificationsList.add(Classification.Taxonomy);
		allSupportedClassifications.addAll(defaultClassificationsList);
	}

	/**
	 * gets the named classification, loading  the tree and mapping, if necessary
	 * There is one static classification object per name
	 *
	 * @param load - create standard file names and load the files
	 * @return classification
	 */
	public static Classification get(String name, boolean load) {
		Classification classification = name2classification.get(name);
		if (classification == null) {
			synchronized (name2classification) {
				classification = name2classification.get(name);
				if (classification == null) {
					if (load) {
						if (classificationsDatabase != null && classificationsDatabase.hasClassification(name)) {
							classification = loadFromDatabase(name, classificationsDatabase, new ProgressSilent());
						} else {
							System.err.println("Warning: classification '" + name + "' is not available in the current classification database");
							classification = new Classification(name);
						}
					} else {
						classification = new Classification(name);
					}
					name2classification.put(name, classification);
				}
			}
		}
		return classification;
	}


	/**
	 * loads the named classification from the given classifications database (if not already present)
	 *
	 * @return classification
	 */
	public static Classification loadFromDatabase(String name, IClassificationsDatabase db, ProgressListener progress) {
		synchronized (name2classification) {
			Classification classification = name2classification.get(name);
			if (classification == null) {
				classification = new Classification(name);
				name2classification.put(name, classification);
			}
			classification.loadFromDatabase(db, progress);
			return classification;
		}
	}

	/**
	 * clears all loaded classifications and the set of supported classifications, so that they can be
	 * re-registered from a classifications database. Does not change the current classifications database
	 * or the default classifications list (which determines the preferred menu order).
	 */
	public static void clear() {
		synchronized (name2classification) {
			name2classification.clear();
			allSupportedClassifications.clear();
			allSupportedClassificationsExcludingNCBITaxonomy.clear();
		}
	}

	/**
	 * gets the current classifications database, or null if classifications are loaded by file-name convention
	 */
	public static IClassificationsDatabase getClassificationsDatabase() {
		return classificationsDatabase;
	}

	/**
	 * sets the current classifications database. When set, any classification that it contains is loaded from it,
	 * rather than by file-name convention.
	 */
	public static void setClassificationsDatabase(IClassificationsDatabase classificationsDatabase) {
		ClassificationManager.classificationsDatabase = classificationsDatabase;
	}

	/**
	 * ensure that the tree and mapping for the named classification are loaded
	 */
	public static void ensureTreeIsLoaded(String name) {
		get(name, true);
	}

	public static Set<String> getAllSupportedClassifications() {
		return allSupportedClassifications;
	}

	public static Set<String> getAllSupportedClassificationsExcludingNCBITaxonomy() {
		return allSupportedClassificationsExcludingNCBITaxonomy;
	}

	public static ArrayList<String> getDefaultClassificationsList() {
		return defaultClassificationsList;
	}

	public static ArrayList<String> getDefaultClassificationsListExcludingNCBITaxonomy() {
		return defaultClassificationsListExcludingNCBITaxonomy;
	}

	public static String getIconFileName(String classificationName) {
		return StringUtils.capitalizeFirstLetter(classificationName.toLowerCase()) + "Viewer16.gif";
	}

	public static boolean isActiveMapper(String name, IdMapper.MapType mapType) {
		return name2classification.get(name) != null && get(name, true).getIdMapper().isActiveMap(mapType);
	}

	public static void setActiveMapper(String name, IdMapper.MapType mapType, boolean active) {
		if (active || name2classification.get(name) != null)
			get(name, true).getIdMapper().setActiveMap(mapType, active);
	}

	public static boolean hasTaxonomicRanks(String classificationName) {
		return name2classification.get(classificationName).getId2Rank().size() > 1;
	}

	/**
	 * is the named parsing method loaded
	 *
	 * @return true, if loaded
	 */
	/**
	 * is the tree for the named classification currently loaded in memory? This does NOT trigger a load (and does
	 * not create a placeholder): it returns true only if the classification has already been instantiated and its
	 * full tree has been populated (a freshly-created, unloaded classification holds a single placeholder node).
	 *
	 * @return true, if the full tree is loaded
	 */
	public static boolean isTreeLoaded(String name) {
		final var classification = name2classification.get(name);
		return classification != null && classification.getFullTree().getNumberOfNodes() > 1;
	}

	public static boolean isLoaded(String name, IdMapper.MapType mapType) {
		return name2classification.get(name) != null && get(name, true).getIdMapper().isLoaded(mapType);
	}


	public static String getMapFileKey(String name, IdMapper.MapType mapType) {
		return name + mapType.toString() + "FileLocation";
	}

	public static String getWindowGeometryKey(String name) {
		return name + "WindowGeometry";
	}

	public static boolean isTaxonomy(String name) {
		return hasTaxonomicRanks(name); // todo: need to enforce that all labels are unique
	}

	public static String getMeganMapDBFile() {
		return meganMapDBFile;
	}

	public static void setMeganMapDBFile(String meganMapDBFile) throws IOException {
		if (meganMapDBFile != null && !FileUtils.fileExistsAndIsNonEmpty(meganMapDBFile))
			throw new IOException("File not found or not readable: " + meganMapDBFile);
		if (!IdMapper.meganMapDBFileFilter.test(meganMapDBFile))
			throw new IOException("Mapping file " + FileUtils.getFileNameWithoutPath(meganMapDBFile) + " is intended for use with MEGAN Ultimate Edition, it is not compatible with MEGAN Community Edition");

		ClassificationManager.meganMapDBFile = meganMapDBFile;
		if (meganMapDBFile != null)
			setUseFastAccessionMappingMode(true);
	}

	public static boolean canUseMeganMapDBFile() {
		return getMeganMapDBFile() != null && isUseFastAccessionMappingMode();
	}

	public static boolean isUseFastAccessionMappingMode() {
		return useFastAccessionMappingMode;
	}

	public static void setUseFastAccessionMappingMode(boolean useFastAccessionMappingMode) {
		ClassificationManager.useFastAccessionMappingMode = useFastAccessionMappingMode;
	}
}
