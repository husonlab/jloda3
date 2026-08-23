/*
 * ClassificationsCompatibility.java Copyright (C) 2026 Daniel H. Huson
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

import java.util.Set;

/**
 * compares the classification database a meganized file was created with (as recorded in its data table) against
 * the classification database currently in use, and reports whether they are compatible.
 * <p>
 * This is pure logic (no UI), so it can be used both by the GUI (to warn on open) and, later, by the command-line
 * tools.
 * <p>
 * Daniel Huson, 7.2026
 */
public class ClassificationsCompatibility {
	/**
	 * the outcome of a compatibility comparison
	 */
	public enum Status {
		/** the file carries no classification stamp (e.g. an older, pre-feature meganized file) */
		UNKNOWN,
		/** the file was meganized with the database currently in use */
		MATCH,
		/** the databases differ, but the current one declares itself compatible with the file's */
		DECLARED_COMPATIBLE,
		/** the databases differ and no compatibility is declared */
		MISMATCH
	}

	/**
	 * the result of a compatibility comparison
	 *
	 * @param status          the outcome
	 * @param fileDatabase    the database base name recorded in the file (may be null)
	 * @param currentDatabase the base name of the database currently in use
	 * @param message         a human-readable message for the user, or null if no feedback is warranted
	 */
	public record Result(Status status, String fileDatabase, String currentDatabase, String message) {
		/**
		 * should this result be surfaced to the user as a warning?
		 */
		public boolean isWarning() {
			return status == Status.MISMATCH;
		}
	}

	/**
	 * compares the classification database recorded in a meganized file against the one currently in use
	 *
	 * @param fileDatabase the base name recorded in the file (from {@code DataTable.getClassificationsDatabase()}), may be null
	 * @param currentDb    the classification database currently in use, may be null (treated as the "unnamed" database)
	 * @return the comparison result
	 */
	public static Result check(String fileDatabase, IClassificationsDatabase currentDb) {
		final var currentName = (currentDb != null ? currentDb.getName() : IClassificationsDatabase.UNNAMED);

		if (fileDatabase == null || fileDatabase.isBlank())
			return new Result(Status.UNKNOWN, fileDatabase, currentName, null);

		if (fileDatabase.equals(currentName))
			return new Result(Status.MATCH, fileDatabase, currentName, null);

		if (currentDb != null && declaresCompatibility(currentDb.getCompatibleWith(), fileDatabase))
			return new Result(Status.DECLARED_COMPATIBLE, fileDatabase, currentName,
					"This file was meganized with classification database '" + fileDatabase
					+ "'; the current database '" + currentName + "' is compatible with it.");

		return new Result(Status.MISMATCH, fileDatabase, currentName,
				"This file was meganized with classification database '" + fileDatabase
				+ "', but the current database is '" + currentName + "'.\n"
				+ "Taxon and function names or ids may not resolve consistently.\n"
				+ "Consider switching to classification database '" + fileDatabase + "' before working with this file.");
	}

	/**
	 * does the current database declare compatibility with the given earlier database? An entry of the
	 * {@code compatible_with} column is either the full base name of that database
	 * ("megan8-classification-r1") or, as the released databases write it, just its release token ("r1").
	 *
	 * @param compatibleWith the entries declared by the current database
	 * @param fileDatabase   the base name recorded in the file
	 * @return true, if one of the entries names the file's database
	 */
	private static boolean declaresCompatibility(Set<String> compatibleWith, String fileDatabase) {
		for (var entry : compatibleWith) {
			if (entry.equals(fileDatabase) || fileDatabase.endsWith("-" + entry))
				return true;
		}
		return false;
	}
}
