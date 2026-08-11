/*
 * ClassificationsDatabaseManager.java Copyright (C) 2026 Daniel H. Huson
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
package jloda.megan.classification.db;

import jloda.megan.classification.Classification;
import jloda.megan.classification.ClassificationManager;
import jloda.megan.classification.LoadClassifications;
import jloda.util.FileUtils;
import jloda.util.ProgramProperties;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * orchestrates MEGAN's use of a classifications database: opening the current database, (re-)loading all
 * classifications from it, locating the on-disk data directory, and (later) discovering databases available
 * for download.
 * <p>
 * The single source of truth for the currently active database is {@link ClassificationManager}; this class
 * provides the higher-level operations around it.
 * <p>
 * Daniel Huson, 7.2026
 */
public class ClassificationsDatabaseManager {
	/**
	 * opens the temporary "unnamed" classifications database that serves the classifications bundled in the jar.
	 * This is used until real classification databases are available.
	 *
	 * @return the bundled ("unnamed") classifications database
	 */
	public static IClassificationsDatabase openBundled() {
		throw new UnsupportedOperationException("bundled classifications database is not available in jloda-metagenomics; open an explicit classification database file");
	}

	/**
	 * opens the classification database at the given file, verifying that it is a MEGAN classification database
	 *
	 * @param dbFile the SQLite classification database file
	 * @return the opened database
	 * @throws IOException if the file cannot be read or is not a valid MEGAN classification database
	 */
	public static IClassificationsDatabase open(File dbFile) throws IOException {
		if (dbFile == null || !dbFile.canRead())
			throw new IOException("File not found or not readable: " + dbFile);
		try {
			return new LoadClassifications(dbFile.getPath());
		} catch (SQLException e) {
			throw new IOException("Failed to open classification database '" + dbFile + "': " + e.getMessage(), e);
		}
	}

	/**
	 * gets the classifications database to use: the one configured via the {@code ClassificationsDatabaseFile}
	 * property if it exists and is a valid MEGAN classification database, otherwise the bundled ("unnamed") one
	 *
	 * @return the classifications database to use
	 */
	public static IClassificationsDatabase openConfiguredOrBundled() {
		final var file = getClassificationsDatabaseFile();
		if (file != null && !file.isBlank()) {
			final var dbFile = new File(file);
			if (dbFile.canRead()) {
				try {
					return open(dbFile);
				} catch (IOException e) {
					System.err.println("Warning: " + e.getMessage() + "; using bundled classifications");
				}
			} else {
				System.err.println("Warning: configured classification database not found: " + file + "; using bundled classifications");
			}
		}
		return openBundled();
	}

	/**
	 * gets the currently active classifications database, or null if none has been set
	 */
	public static IClassificationsDatabase getCurrent() {
		return ClassificationManager.getClassificationsDatabase();
	}

	/**
	 * gets the base name of the currently active classifications database, or {@link IClassificationsDatabase#UNNAMED}
	 * if none is set (i.e. the classifications bundled in the jar are in use). This is the value stamped into
	 * meganized files.
	 *
	 * @return current classifications database name
	 */
	public static String getCurrentName() {
		final var current = getCurrent();
		return current != null ? current.getName() : IClassificationsDatabase.UNNAMED;
	}

	/**
	 * sets the current classifications database, so that classifications it contains are loaded from it. Closes
	 * the previously current database (if any and different), releasing e.g. its connection.
	 */
	public static void setCurrent(IClassificationsDatabase db) {
		final var previous = ClassificationManager.getClassificationsDatabase();
		if (previous != null && previous != db) {
			try {
				previous.close();
			} catch (Exception ignored) {
			}
		}
		ClassificationManager.setClassificationsDatabase(db);
	}

	/**
	 * clears all existing classifications and (re-)loads them from the given database: registers every
	 * classification the database contains and rebuilds the global open-viewer commands accordingly, so that
	 * a viewer and menu item exists for each. The NCBI taxonomy (named {@link Classification#Taxonomy}) is
	 * eagerly loaded, as it backs the always-present MainViewer.
	 * <p>
	 * Note: menus and command managers of already-open windows are not yet rebuilt by this method; that
	 * runtime refresh is a follow-up. At startup this is not an issue, since it runs before any window is built.
	 *
	 * @param db the classifications database to load from
	 */
	public static void loadAndRegister(IClassificationsDatabase db) {
		registerFromDatabase(db);


		// the NCBI taxonomy backs the always-present MainViewer, so ensure it is loaded
		if (db.hasClassification(Classification.Taxonomy))
			ClassificationManager.ensureTreeIsLoaded(Classification.Taxonomy);
	}

	/**
	 * clears the existing classifications and registers those contained in the given database, setting it as the
	 * current database (so trees are loaded from it). Headless-safe: does not touch any Swing command/menu state,
	 * so it can be used by the command-line tools.
	 *
	 * @param db the classifications database to register
	 */
	public static void registerFromDatabase(IClassificationsDatabase db) {
		ClassificationManager.clear();
		setCurrent(db);

		for (var cName : db.getClassificationNames()) {
			if (cName.equals(Classification.Taxonomy)) {
				ClassificationManager.getAllSupportedClassifications().add(Classification.Taxonomy);
			} else {
				ClassificationManager.getAllSupportedClassifications().add(cName);
				ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy().add(cName);
			}
		}
	}

	/**
	 * opens the classification database at the given path and registers its classifications, making it the current
	 * database (so trees are loaded from it and meganized files are stamped with its name). A blank path is a no-op
	 * (the bundled "unnamed" classifications remain in use). This is the entry point for the command-line tools'
	 * {@code -cdb}/{@code --classificationDB} option.
	 *
	 * @param dbFile path to the classification database, or blank/null to keep the bundled classifications
	 * @throws IOException if the file cannot be read or is not a valid MEGAN classification database
	 */
	public static void applyClassificationDB(String dbFile) throws IOException {
		if (dbFile != null && !dbFile.isBlank())
			registerFromDatabase(open(new File(dbFile)));
	}

	/**
	 * chooses and registers the classification database for READING a meganized file, based on the classification
	 * database recorded in the file (its {@code @Classification} stamp):
	 * <ul>
	 *     <li>if {@code cdbOverride} is set, that database is used (an explicit -cdb wins);</li>
	 *     <li>otherwise, if the file names a classification database, it is located by base name in the MEGAN data
	 *     directory (or at the configured classification database file) and used;</li>
	 *     <li>otherwise the current / bundled classifications are kept.</li>
	 * </ul>
	 * In every case, a compatibility warning is printed to stderr if the database finally in use does not match the
	 * one recorded in the file.
	 *
	 * @param cdbOverride explicit classification database path from -cdb, or blank/null for auto-detection
	 * @param fileStamp   the classification database base name recorded in the file, or null
	 */
	public static void applyForReading(String cdbOverride, String fileStamp) throws IOException {
		if (cdbOverride != null && !cdbOverride.isBlank()) {
			registerIfDifferent(new File(cdbOverride));
		} else if (fileStamp != null && !fileStamp.isBlank() && !fileStamp.equals(IClassificationsDatabase.UNNAMED)) {
			final var resolved = locateDatabase(fileStamp);
			if (resolved != null)
				registerIfDifferent(resolved);
			// else: keep current; the compatibility check below reports the mismatch and suggests -cdb
		}

		final var result = ClassificationsCompatibility.check(fileStamp, getCurrent());
		if (result.isWarning())
			System.err.println("Warning: " + result.message().replace("\n", " "));
	}

	/**
	 * opens and registers the classification database at the given file, unless it is already the current one
	 * (avoids re-clearing and re-loading trees when processing many files that use the same database)
	 */
	private static void registerIfDifferent(File dbFile) throws IOException {
		if (FileUtils.replaceFileSuffix(dbFile.getName(), "").equals(getCurrentName()))
			return; // this database is already in use
		System.err.println("Using classification database: " + dbFile);
		registerFromDatabase(open(dbFile));
	}

	/**
	 * locates a classification database by its base name (e.g. megan-classification-r1), looking in the MEGAN data
	 * directory and, failing that, at the configured classification database file
	 *
	 * @param baseName the classification database base name
	 * @return the file, or null if not found
	 */
	public static File locateDatabase(String baseName) {
		final var inDataDir = new File(getDataDirectory(), baseName + ".db");
		if (inDataDir.canRead())
			return inDataDir;
		final var configured = getClassificationsDatabaseFile();
		if (configured != null && !configured.isBlank()) {
			final var file = new File(configured);
			if (file.canRead() && FileUtils.replaceFileSuffix(file.getName(), "").equals(baseName))
				return file;
		}
		return null;
	}

	/**
	 * removes the stale open-viewer commands from the global command list and adds one for each currently
	 * supported classification
	 */

	/**
	 * gets the dedicated directory into which downloaded classification (and map) databases are placed.
	 * Defaults to {@code ~/.megan}. The directory is not created here.
	 *
	 * @return data directory
	 */
	public static File getDataDirectory() {
		final var path = ProgramProperties.get("MeganDataDirectory",
				System.getProperty("user.home") + File.separator + ".megan");
		return new File(path);
	}

	/**
	 * gets the path of the active classification database file, or empty if the bundled ("unnamed")
	 * classifications should be used
	 *
	 * @return classification database file path, or empty string
	 */
	public static String getClassificationsDatabaseFile() {
		return ProgramProperties.get("ClassificationsDatabaseFile", "");
	}

	/**
	 * gets the URL of the online catalog (JSON manifest) of classification databases, from the
	 * {@code ClassificationsDatabaseManifestURL} property, falling back to {@link #DEFAULT_MANIFEST_URL}
	 */
	public static String getManifestUrl() {
		return ProgramProperties.get("ClassificationsDatabaseManifestURL", DEFAULT_MANIFEST_URL);
	}

	// Production catalog (once the databases are hosted), a GitHub-releases manifest like the installer updater uses:
	// public static final String DEFAULT_MANIFEST_URL = "https://github.com/husonlab/megan8/releases/latest/download/megan-classification-manifest.json";
	//
	// Local testing: serve a directory containing manifest.json and the .db files, e.g.
	//     cd ~/megan-catalog && python3 -m http.server 8000
	// (the property ClassificationsDatabaseManifestURL overrides this at runtime)
	public static final String DEFAULT_MANIFEST_URL = "http://localhost:8000/manifest.json";

	/**
	 * fetches the online catalog of classification databases available for download
	 *
	 * @return the catalog
	 * @throws IOException if no catalog URL is configured, or it cannot be fetched or parsed
	 */

	/**
	 * downloads the given catalog entry into the MEGAN data directory (verifying its checksum), without changing
	 * the current database
	 *
	 * @param entry    the catalog entry to download
	 * @param progress progress handler, or null
	 * @return the downloaded file
	 */

	/**
	 * makes the classification database at the given file the active one: records it in the
	 * {@code ClassificationsDatabaseFile} property and (re-)loads all classifications from it. Note: menus of
	 * already-open windows are not rebuilt here (a known limitation).
	 *
	 * @param dbFile the classification database file
	 * @throws IOException if the file is not a valid MEGAN classification database
	 */
	public static void useDatabaseFile(File dbFile) throws IOException {
		final var db = open(dbFile); // validates it is a MEGC database
		ProgramProperties.put("ClassificationsDatabaseFile", dbFile.getPath());
		loadAndRegister(db);
	}

	/**
	 * has a real classification database (i.e. not the bundled one) been configured, and does its file exist?
	 */
	public static boolean hasConfiguredDatabase() {
		final var file = getClassificationsDatabaseFile();
		return file != null && !file.isBlank() && new File(file).canRead();
	}
}
