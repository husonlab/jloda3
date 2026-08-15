/*
 * IClassificationsDatabase.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.util.progress.ProgressListener;
import jloda.megan.classification.data.ClassificationFullTree;
import jloda.megan.classification.data.Name2IdMap;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * a source of classifications (trees and id-mappings).
 * <p>
 * This is the abstraction that decouples MEGAN's classifications from any particular storage:
 * {@link ClassificationsDatabaseFromResources} serves the classifications bundled in the
 * {@code megan8/resources/files} branch of the jar, whereas {@link jloda.megan.classification.LoadClassifications}
 * serves them from an SQLite classification database (e.g. megan-classification-r1.db).
 * <p>
 * The name of a classifications database (its {@link #getName()}) is what gets recorded in meganized
 * DAA/RMA6/.megan files so that compatibility can later be checked. The bundled jar-resources source
 * uses the reserved name {@link #UNNAMED}.
 * <p>
 * Rather than exposing storage-specific data (file streams work for Newick trees but cannot represent the GO
 * and EGGNOG DAGs), an implementation is asked to populate MEGAN's own structures directly via
 * {@link #loadClassification}. This is the only contract that both a file-backed and a relational backend can
 * satisfy.
 * <p>
 * Daniel Huson, 7.2026
 */
public interface IClassificationsDatabase extends Closeable {
	/**
	 * reserved name/version used by the temporary jar-resources source, i.e. before any real
	 * classification database has been made available
	 */
	String UNNAMED = "unnamed";

	/**
	 * the base name / version of this classifications database, e.g. "unnamed" or "megan-classification-r1"
	 *
	 * @return name
	 */
	String getName();

	/**
	 * the names of the classifications contained in this database, using MEGAN's classification names,
	 * e.g. Taxonomy, GTDB, EGGNOG, SEED, GO, KEGG (note: the NCBI taxonomy is named {@code Taxonomy})
	 *
	 * @return classification names
	 */
	Collection<String> getClassificationNames();

	/**
	 * does this database contain the named classification?
	 *
	 * @param cName classification name
	 * @return true, if present
	 */
	boolean hasClassification(String cName);

	/**
	 * populates the given full tree and id-mapping for the named classification. Implementations are responsible
	 * for adding MEGAN's ancillary nodes (no-hits, unassigned, ...) and computing LCA addresses, as
	 * {@link ClassificationFullTree#loadFromReader} and {@link ClassificationFullTree#finishLoadingFromDatabase}
	 * do.
	 *
	 * @param cName       classification name
	 * @param fullTree    the full tree to populate
	 * @param name2IdMap  the id-mapping to populate
	 * @param progress    progress listener
	 * @throws IOException if the classification is not present or cannot be read
	 */
	void loadClassification(String cName, ClassificationFullTree fullTree, Name2IdMap name2IdMap, ProgressListener progress) throws IOException;

	/**
	 * gets the version-info text for the named classification, or null if none is available
	 *
	 * @param cName classification name
	 * @return version info or null
	 */
	String getInfo(String cName) throws IOException;

	/**
	 * lightweight metadata about a classification, as recorded by the database, obtained WITHOUT building the tree
	 * (so it is safe to query for a properties display). Any field may be null/absent; {@code nodeCount} is
	 * {@code -1} when the size is not recorded.
	 *
	 * @param name          MEGAN classification name (e.g. Taxonomy, GTDB, GO)
	 * @param type          "taxonomy" or "function" (or null if unknown)
	 * @param source        upstream source name (e.g. NCBI, GTDB), or null
	 * @param sourceVersion source version / snapshot date, or null
	 * @param doi           citation / DOI, or null
	 * @param nodeCount     number of nodes recorded for the classification, or -1 if unknown
	 * @param isDag         whether the classification is a DAG (a leaf may sit under several parents)
	 */
	record ClassificationInfo(String name, String type, String source, String sourceVersion, String doi, int nodeCount, boolean isDag) {
	}

	/**
	 * gets lightweight, database-recorded metadata for the named classification, WITHOUT building its tree, or
	 * {@code null} if this database records no such metadata. Used e.g. by the properties display to report the
	 * classifications currently held without incurring the cost of loading them.
	 *
	 * @param cName classification name
	 * @return metadata, or null
	 */
	default ClassificationInfo getClassificationInfo(String cName) throws IOException {
		return null;
	}

	/**
	 * gets the base names of earlier classification databases whose ids still resolve in this one, i.e. earlier
	 * releases that this database is backward-compatible with (aggregated from the {@code compatible_with} column).
	 * A file meganized with one of these can be opened against this database without a compatibility warning.
	 *
	 * @return set of compatible earlier database base names (empty if none / not applicable)
	 */
	default Set<String> getCompatibleWith() {
		return Set.of();
	}

	/**
	 * closes any resources (e.g. a database connection) held by this source. The default is a no-op.
	 */
	@Override
	default void close() {
	}
}
