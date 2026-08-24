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

import jloda.megan.classification.db.IClassificationsDatabase;
import jloda.util.ProgramProperties;
import jloda.util.CanceledException;
import jloda.util.FileUtils;
import jloda.megan.classification.Classification;
import jloda.megan.classification.ClassificationManager;
import jloda.megan.classification.LoadClassifications;
import jloda.megan.classification.TaxonomicLevels;

import java.io.File;
import java.io.IOException;
import java.sql.DriverManager;
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
	 * the properties this class reads. MEGAN's keys, kept verbatim so existing settings still apply; MEGAN's
	 * MeganProperties now points at these rather than repeating the literals.
	 */
	public static final String CLASSIFICATIONS_DATABASE_FILE = "ClassificationsDatabaseFile";
	public static final String MEGAN_DATA_DIRECTORY = "MeganDataDirectory";
	public static final String CLASSIFICATIONS_DATABASE_MANIFEST_URL = "ClassificationsDatabaseManifestURL";

	/**
	 * run whenever the set of registered classifications changes, so that an application can refresh whatever
	 * it built from that set. MEGAN rebuilds its global "open viewer" commands, which are otherwise made once
	 * at startup and would go stale when the classification database is switched.
	 */
	public static Runnable afterClassificationsRegistered = null;

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
	 * property if it exists and is a valid MEGAN classification database, otherwise the newest default database
	 * found on disk, or null if none is available
	 *
	 * @return the classifications database to use, or null if none is available
	 */
	public static IClassificationsDatabase openConfiguredOrDefault() {
		final var file = getClassificationsDatabaseFile();
		if (file != null && !file.isBlank()) {
			final var dbFile = new File(file);
			if (dbFile.canRead()) {
				try {
					return open(dbFile);
				} catch (IOException e) {
					System.err.println("Warning: " + e.getMessage() + "; looking for a default classification database");
				}
			} else {
				System.err.println("Warning: configured classification database not found: " + file + "; looking for a default classification database");
			}
		}
		final var defaultDb = findDefaultDatabase();
		if (defaultDb != null) {
			try {
				return open(defaultDb);
			} catch (IOException e) {
				System.err.println("Warning: " + e.getMessage() + "; no classification database available");
			}
		}
		return null;
	}

	/**
	 * finds the default classification database to use when none is explicitly configured: the newest
	 * megan8-classification-r<N>.db in the MEGAN data directory, else the current working directory (where an
	 * installer-shipped or downloaded database is placed). Returns null if none is found.
	 */
	public static File findDefaultDatabase() {
		for (var dir : new File[]{getDataDirectory(), new File(System.getProperty("user.dir"))}) {
			final var best = newestClassificationDatabase(dir);
			if (best != null)
				return best;
		}
		return null;
	}

	private static File newestClassificationDatabase(File dir) {
		final var files = dir.listFiles((d, name) -> name.startsWith("megan8-classification-r") && name.endsWith(".db"));
		if (files == null)
			return null;
		File best = null;
		int bestRelease = -1;
		for (var file : files) {
			final var release = releaseNumber(file.getName());
			if (file.canRead() && release > bestRelease) {
				bestRelease = release;
				best = file;
			}
		}
		return best;
	}

	private static int releaseNumber(String fileName) {
		final var base = FileUtils.replaceFileSuffix(fileName, "");
		final var idx = base.lastIndexOf("-r");
		if (idx < 0)
			return -1;
		try {
			return Integer.parseInt(base.substring(idx + 2));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
	 * gets the currently active classifications database, or null if none has been set
	 */
	public static IClassificationsDatabase getCurrent() {
		return ClassificationManager.getClassificationsDatabase();
	}

	/**
	 * gets the base name of the currently active classifications database, or {@link IClassificationsDatabase#UNNAMED}
	 * if none is set (i.e. no classification database is configured). This is the value stamped into
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

		if (afterClassificationsRegistered != null)
			afterClassificationsRegistered.run();

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
		TaxonomicLevels.setFromDatabase(db.getRankNames());

		for (var cName : db.getClassificationNames()) {
			if (cName.equals(Classification.Taxonomy)) {
				ClassificationManager.getAllSupportedClassifications().add(Classification.Taxonomy);
			} else {
				ClassificationManager.getAllSupportedClassifications().add(cName);
				ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy().add(cName);
			}
		}

		report(db);
	}

	/**
	 * reports which classification database is in use and which version of each classification it provides, so
	 * that a run's log says what produced its numbers. Every way of choosing a database comes through
	 * {@link #registerFromDatabase}, so this covers the GUI at startup, the tools' {@code -cdb}, the classifications
	 * embedded in a combined mapping database, and the readers' auto-detection from a file's stamp.
	 *
	 * @param db the database just registered
	 */
	private static void report(IClassificationsDatabase db) {
		final var release = db.getDbRelease();
		System.err.printf("Classification database: %s%s%n", db.getName(),
				(release != null && !release.isBlank()) ? " (release " + release + ")" : "");

		final var buf = new StringBuilder();
		for (var cName : db.getClassificationNames()) {
			if (!buf.isEmpty())
				buf.append(", ");
			buf.append(cName);
			final var version = versionOf(db, cName);
			if (!version.isBlank())
				buf.append(" (").append(version).append(")");
		}
		if (!buf.isEmpty())
			System.err.println("Classifications: " + buf);
	}

	/**
	 * the source and version a database records for a classification, e.g. "GTDB v232", or "" if it records none
	 */
	private static String versionOf(IClassificationsDatabase db, String cName) {
		try {
			final var info = db.getClassificationInfo(cName);
			if (info == null)
				return "";
			return "%s %s".formatted(info.source() != null ? info.source() : "",
					info.sourceVersion() != null ? info.sourceVersion() : "").trim();
		} catch (IOException e) {
			return "";
		}
	}

	/**
	 * opens the classification database at the given path and registers its classifications, making it the current
	 * database (so trees are loaded from it and meganized files are stamped with its name). A blank path is a no-op
	 * (no classification database is applied). This is the entry point for the command-line tools'
	 * {@code -cdb}/{@code --classificationDB} option.
	 *
	 * @param dbFile path to the classification database, or blank/null for a no-op
	 * @throws IOException if the file cannot be read or is not a valid MEGAN classification database
	 */
	public static void applyClassificationDB(String dbFile) throws IOException {
		if (dbFile != null && !dbFile.isBlank())
			registerFromDatabase(open(new File(dbFile)));
	}

	/**
	 * opens and registers the classification database for a tool that has neither a meganized file nor a mapping
	 * database to take it from: the one named by {@code -cdb} if given, otherwise the configured or newest default
	 * database found on disk.
	 *
	 * @param dbFile path to the classification database from {@code -cdb}, or blank/null
	 * @return true, if a database was registered; false, if none was named and none could be found
	 * @throws IOException if the named file cannot be read or is not a valid MEGAN classification database
	 */
	public static boolean applyClassificationDBOrDefault(String dbFile) throws IOException {
		if (dbFile != null && !dbFile.isBlank()) {
			applyClassificationDB(dbFile);
			return true;
		}
		final var db = openConfiguredOrDefault();
		if (db == null)
			return false;
		registerFromDatabase(db);
		return true;
	}

	/**
	 * chooses and registers the classification database for MEGANIZING: an explicit {@code -cdb} wins; otherwise, if
	 * the mapping database is a combined file that embeds the classifications, those are used; otherwise it is an
	 * error, because meganization needs the classification trees.
	 *
	 * @param cdbFile   explicit classification database path from {@code -cdb}, or blank/null
	 * @param mapDbFile the mapping database path (a combined file may embed the classifications), or blank/null
	 * @throws IOException if no classification database is available
	 */
	public static void applyForMeganizing(String cdbFile, String mapDbFile) throws IOException {
		if (cdbFile != null && !cdbFile.isBlank()) {
			applyClassificationDB(cdbFile);                    // an explicit -cdb wins
		} else if (mappingDatabaseEmbedsClassifications(mapDbFile)) {
			applyClassificationDB(mapDbFile);                  // combined file: classifications embedded in the mapping database
		} else {
			throw new IOException("No classification database: supply --classificationDB (-cdb), or use a mapping database that embeds the classifications");
		}
	}

	/**
	 * does the given mapping database embed the classification tables (i.e. is it a combined mapping+classification
	 * file)? Checks for a {@code classifications} table; returns false if the file is missing or on any error.
	 *
	 * @param mapDbFile the mapping database path, or blank/null
	 * @return true if the mapping database also contains the classifications
	 */
	public static boolean mappingDatabaseEmbedsClassifications(String mapDbFile) {
		if (mapDbFile == null || mapDbFile.isBlank() || !new File(mapDbFile).canRead())
			return false;
		try (var connection = DriverManager.getConnection("jdbc:sqlite:" + mapDbFile);
			 var statement = connection.createStatement();
			 var rs = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='classifications';")) {
			return rs.next();
		} catch (SQLException e) {
			return false;
		}
	}

	/**
	 * chooses and registers the classification database for READING a meganized file, based on the classification
	 * database recorded in the file (its {@code @Classification} stamp):
	 * <ul>
	 *     <li>if {@code cdbOverride} is set, that database is used (an explicit -cdb wins);</li>
	 *     <li>otherwise, if the file names a classification database, it is located by base name in the MEGAN data
	 *     directory (or at the configured classification database file) and used;</li>
	 *     <li>otherwise the current classifications are kept.</li>
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

		if (getCurrent() == null) {
			final var defaultDb = findDefaultDatabase();
			if (defaultDb != null)
				registerIfDifferent(defaultDb);
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
	 * gets the dedicated directory into which downloaded classification (and map) databases are placed.
	 * Defaults to {@code ~/.megan}. The directory is not created here.
	 *
	 * @return data directory
	 */
	public static File getDataDirectory() {
		final var path = ProgramProperties.get(MEGAN_DATA_DIRECTORY,
				System.getProperty("user.home") + File.separator + ".megan");
		return new File(path);
	}

	/**
	 * gets the path of the active classification database file, or empty if no classification database is
	 * configured (the "unnamed" default is in use)
	 *
	 * @return classification database file path, or empty string
	 */
	public static String getClassificationsDatabaseFile() {
		return ProgramProperties.get(CLASSIFICATIONS_DATABASE_FILE, "");
	}

	/**
	 * gets the URL of the online catalog (JSON manifest) of classification databases, from the
	 * {@code ClassificationsDatabaseManifestURL} property, falling back to {@link #DEFAULT_MANIFEST_URL}
	 */
	public static String getManifestUrl() {
		return ProgramProperties.get(CLASSIFICATIONS_DATABASE_MANIFEST_URL, DEFAULT_MANIFEST_URL);
	}

	// Production catalog: a GitHub-releases manifest, like the installer updater uses.
	//
	// For local testing, serve a directory containing manifest.json and the .db files, e.g.
	//     cd ~/megan-catalog && python3 -m http.server 8000
	// and point MEGAN at it by setting the property, rather than by editing this line:
	//     ClassificationsDatabaseManifestURL=http://localhost:8000/manifest.json
	// (the property overrides this default at runtime)
	public static final String DEFAULT_MANIFEST_URL = "https://github.com/husonlab/megan8/releases/latest/download/megan-classification-manifest.json";

	/**
	 * fetches the online catalog of classification databases available for download
	 *
	 * @return the catalog
	 * @throws IOException if no catalog URL is configured, or it cannot be fetched or parsed
	 */
	public static ClassificationsManifest fetchManifest() throws IOException {
		return ClassificationsManifest.fetch(getManifestUrl());
	}

	/**
	 * downloads the given catalog entry into the MEGAN data directory (verifying its checksum), without changing
	 * the current database
	 *
	 * @param entry    the catalog entry to download
	 * @param progress progress handler, or null
	 * @return the downloaded file
	 */
	public static File downloadToDataDir(ClassificationsManifest.Entry entry, ClassificationsDatabaseDownloader.ProgressHandler progress) throws IOException, CanceledException {
		return ClassificationsDatabaseDownloader.download(entry, getDataDirectory(), progress);
	}

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
		ProgramProperties.put(CLASSIFICATIONS_DATABASE_FILE, dbFile.getPath());
		loadAndRegister(db);
	}

	/**
	 * has a real classification database (i.e. not the "unnamed" default) been configured, and does its file exist?
	 */
	public static boolean hasConfiguredDatabase() {
		final var file = getClassificationsDatabaseFile();
		return file != null && !file.isBlank() && new File(file).canRead();
	}
}
