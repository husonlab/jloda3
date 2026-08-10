/*
 * LoadClassifications.java Copyright (C) 2026 Daniel H. Huson
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

package megan8.classification;

import jloda.graph.Node;
import jloda.util.FileUtils;
import jloda.util.progress.ProgressListener;
import megan8.classification.data.ClassificationFullTree;
import megan8.classification.data.Name2IdMap;
import megan8.classification.db.IClassificationsDatabase;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * a {@link IClassificationsDatabase} backed by a MEGAN classification database (an SQLite file with
 * application_id 'MEGC', e.g. megan-classification-r1.db).
 * <p>
 * Metadata (the {@code classifications} table and the shared {@code ranks} table) is read up front; each
 * classification's tree is read lazily, on demand, directly into MEGAN's {@link ClassificationFullTree} and
 * {@link Name2IdMap} (the NCBI taxonomy is large, so we do not build any classification until it is needed).
 * <p>
 * Both trees (one parent per node) and DAGs such as GO and EGGNOG (a node may have several parents, encoded as
 * several rows) are handled uniformly: exactly one node is created per {@code node_id} and one edge is added per
 * {@code parent_id}; for a DAG the edges entering a node of in-degree &gt; 1 are flagged as reticulate. The tree
 * table for each classification is the one named in {@code classifications.tree_table}.
 * <p>
 * The database uses {@code NCBI} for the NCBI taxonomy; MEGAN names it {@link Classification#Taxonomy}, so that
 * one name is mapped, all others are used as-is.
 * <p>
 * Daniel Huson, 2026
 */
public class LoadClassifications implements IClassificationsDatabase {
	// SQLite application_id marking a MEGAN classification database: the four bytes 'MEGC'
	public static final int MEGC_APPLICATION_ID = 0x4D454743;

	// a valid tree-table name: letters, digits and underscores only (guards the necessary string concatenation below)
	private static final Pattern SAFE_IDENTIFIER = Pattern.compile("\\w+");

	private final Connection connection;
	private final String name;
	private final Map<Integer, String> rankNames = new HashMap<>(); // merged across classifications (fallback)
	private final Map<String, Map<Integer, String>> classificationRankNames = new HashMap<>(); // per MEGAN classification name
	private final Set<String> compatibleWith = new HashSet<>(); // earlier database base names whose ids still resolve here
	private final Map<String, ClassificationMeta> name2meta = new LinkedHashMap<>(); // keyed by MEGAN classification name

	/**
	 * opens the given MEGAN classification database (read-only) and reads its metadata
	 *
	 * @param dbFile path to the SQLite classification database
	 */
	public LoadClassifications(String dbFile) throws SQLException {
		connection = connect(dbFile, true);
		verifyApplicationId(connection);
		name = FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(dbFile), "");
		loadRanks(connection);
		for (var meta : loadClassificationMetadata(connection)) {
			name2meta.put(meta.megaName(), meta);
		}
	}

	// --- IClassificationsDatabase ---

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Collection<String> getClassificationNames() {
		return name2meta.keySet();
	}

	@Override
	public boolean hasClassification(String cName) {
		return name2meta.containsKey(cName);
	}

	@Override
	public void loadClassification(String cName, ClassificationFullTree fullTree, Name2IdMap name2IdMap, ProgressListener progress) throws IOException {
		final var meta = name2meta.get(cName);
		if (meta == null)
			throw new IOException("Classification not found in database: " + cName);
		try {
			buildTree(meta, fullTree, name2IdMap, progress);
		} catch (SQLException e) {
			throw new IOException("Failed to load classification '" + cName + "' from database '" + name + "': " + e.getMessage(), e);
		}
	}

	@Override
	public IClassificationsDatabase.ClassificationInfo getClassificationInfo(String cName) {
		final var meta = name2meta.get(cName);
		if (meta == null)
			return null;
		return new IClassificationsDatabase.ClassificationInfo(meta.megaName(), meta.type(), meta.source(), meta.sourceVersion(), meta.doi(), meta.nodeCount(), meta.isDag());
	}

	@Override
	public String getInfo(String cName) {
		final var meta = name2meta.get(cName);
		if (meta == null)
			return null;
		final var buf = new StringBuilder();
		buf.append(meta.displayName());
		if (meta.source() != null)
			buf.append("\nsource: ").append(meta.source());
		if (meta.sourceVersion() != null)
			buf.append("\nversion: ").append(meta.sourceVersion());
		if (meta.doi() != null)
			buf.append("\nCite: ").append(meta.doi());
		buf.append("\n(from ").append(name).append(")");
		return buf.toString();
	}

	@Override
	public void close() {
		try {
			if (connection != null && !connection.isClosed())
				connection.close();
		} catch (SQLException ignored) {
		}
	}

	// --- database reading ---

	/**
	 * verifies that the database is a MEGAN classification database, by checking its application_id
	 */
	private static void verifyApplicationId(Connection connection) throws SQLException {
		try (var statement = connection.createStatement();
			 var rs = statement.executeQuery("PRAGMA application_id;")) {
			if (rs.next()) {
				final var applicationId = rs.getInt(1);
				if (applicationId != MEGC_APPLICATION_ID)
					throw new SQLException("Not a MEGAN classification database (application_id=0x%08X, expected 0x%08X 'MEGC')"
							.formatted(applicationId, MEGC_APPLICATION_ID));
			}
		}
	}

	/**
	 * reads the ranks table (rank id -&gt; rank name). As of schema v1 the ranks are per classification (columns
	 * {@code classifications, id, name}); an older flat {@code id, name} table is still handled. Rank names are kept
	 * both per classification and in a merged map.
	 */
	private void loadRanks(Connection connection) throws SQLException {
		try (var statement = connection.createStatement();
			 var rs = statement.executeQuery("SELECT * FROM ranks")) {
			final var columns = columnNames(rs.getMetaData());
			final var hasClassification = columns.contains("classifications");
			while (rs.next()) {
				final var id = rs.getInt("id");
				final var rankName = rs.getString("name");
				rankNames.put(id, rankName);
				if (hasClassification) {
					final var cName = megaName(rs.getString("classifications"));
					classificationRankNames.computeIfAbsent(cName, k -> new HashMap<>()).put(id, rankName);
				}
			}
		} catch (SQLException ignored) {
			// a database without a ranks table simply has no rank names to resolve
		}
	}

	/**
	 * reads the classifications table into metadata records, reading the result set fully before any per-tree
	 * statement is opened. Optional columns (source, doi, ...) are read only if present.
	 */
	private java.util.List<ClassificationMeta> loadClassificationMetadata(Connection connection) throws SQLException {
		final var list = new java.util.ArrayList<ClassificationMeta>();
		try (var statement = connection.createStatement();
			 var rs = statement.executeQuery("SELECT * FROM classifications")) {
			final var columns = columnNames(rs.getMetaData());
			while (rs.next()) {
				final var dbName = rs.getString("name");
				final var displayName = str(rs, columns, "display_name");
				final var type = str(rs, columns, "type");
				final var treeTable = str(rs, columns, "tree_table");
				final var rootId = intOrNull(rs, columns, "root_id");
				final var nodeCount = columns.contains("node_count") ? rs.getInt("node_count") : 0;
				final var isDag = columns.contains("is_dag") && rs.getInt("is_dag") != 0;
				final var source = str(rs, columns, "source");
				final var sourceVersion = str(rs, columns, "source_version");
				final var doi = str(rs, columns, "doi");
				final var compatible = str(rs, columns, "compatible_with");
				if (compatible != null) {
					for (var token : compatible.split(","))
						if (!token.isBlank())
							compatibleWith.add(token.trim());
				}
				list.add(new ClassificationMeta(megaName(dbName), dbName, (displayName != null && !displayName.isBlank()) ? displayName : dbName,
						type, treeTable, rootId, nodeCount, isDag, source, sourceVersion, doi));
			}
		}
		return list;
	}

	/**
	 * builds the classification's tree directly into MEGAN's structures: one node per {@code node_id}, one edge
	 * per {@code parent_id}, the id-to-name and (if present) rank mappings, then MEGAN's ancillary nodes and LCA
	 * addresses via {@link ClassificationFullTree#finishLoadingFromDatabase()}
	 */
	private void buildTree(ClassificationMeta meta, ClassificationFullTree fullTree, Name2IdMap name2IdMap, ProgressListener progress) throws SQLException {
		if (meta.treeTable() == null || !SAFE_IDENTIFIER.matcher(meta.treeTable()).matches())
			throw new SQLException("Illegal tree table name for classification '%s': %s".formatted(meta.name(), meta.treeTable()));

		if (progress != null)
			progress.setSubtask("Loading " + meta.megaName());

		fullTree.clear();
		System.err.print("Loading " + meta.megaName() + " from database: ");

		// the table name comes from the trusted schema (classifications.tree_table) and is validated above; a
		// table name can not be passed as a prepared-statement parameter in any case
		try (var statement = connection.createStatement();
			 var rs = statement.executeQuery("SELECT * FROM " + meta.treeTable())) {
			final var columns = new Columns(rs.getMetaData());

			// a node may be listed under more than one parent; the schema allows this only for leaves. Such a node is
			// represented NOT as one node with several parents, but as several equivalent leaves that share the same
			// id and label. childSeen tracks the node ids already turned into a node (only needed for DAGs).
			final var childSeen = meta.isDag() ? new HashSet<Integer>() : null;

			while (rs.next()) {
				final var nodeId = rs.getInt(columns.nodeId());
				final Node v;
				if (childSeen != null && !childSeen.add(nodeId)) {
					// already seen as a child: this is a further parent of a leaf -> add a separate, equivalent leaf
					v = fullTree.newNode(nodeId);
					fullTree.setLabel(v, String.valueOf(nodeId));
					fullTree.addId2Node(nodeId, v);
				} else {
					v = getOrCreate(fullTree, nodeId);
				}

				if (columns.name() != 0) {
					final var label = rs.getString(columns.name());
					if (label != null)
						name2IdMap.put(label, nodeId);
				}

				if (columns.rankId() != 0) {
					final var rankId = rs.getInt(columns.rankId());
					if (!rs.wasNull())
						name2IdMap.setRank(nodeId, rankId);
				}

				final var parentId = rs.getInt(columns.parentId());
				if (!rs.wasNull() && parentId != nodeId) { // the root points at itself, skip that self loop
					final var parent = getOrCreate(fullTree, parentId);
					fullTree.newEdge(parent, v);
				}
			}
		}

		var root = (meta.rootId() != null) ? fullTree.getANode(meta.rootId()) : null;
		if (root == null)
			root = findRoot(fullTree);
		fullTree.setRoot(root);

		fullTree.finishLoadingFromDatabase();
		System.err.printf("%,9d%n", fullTree.getNumberOfNodes());
	}

	/**
	 * returns the node for the given id, creating it (with the id as its info and label, matching the Newick
	 * loading path) on first encounter
	 */
	private static Node getOrCreate(ClassificationFullTree tree, int id) {
		var v = tree.getANode(id);
		if (v == null) {
			v = tree.newNode(id);
			tree.setLabel(v, String.valueOf(id));
			tree.addId2Node(id, v);
		}
		return v;
	}

	/**
	 * fallback root determination: the (first) node with no parent
	 */
	private static Node findRoot(ClassificationFullTree tree) {
		for (var v : tree.nodes()) {
			if (v.getInDegree() == 0)
				return v;
		}
		return tree.getNumberOfNodes() > 0 ? tree.nodes().iterator().next() : null;
	}

	// --- accessors ---

	/**
	 * resolves a rank id to its name using the merged rank table, or null
	 */
	public String getRankName(Integer rankId) {
		return rankId == null ? null : rankNames.get(rankId);
	}

	/**
	 * resolves a rank id to its name within the given classification (falling back to the merged table), or null
	 */
	public String getRankName(String classification, Integer rankId) {
		if (rankId == null)
			return null;
		final var perClassification = classificationRankNames.get(classification);
		final var rankName = (perClassification != null ? perClassification.get(rankId) : null);
		return rankName != null ? rankName : rankNames.get(rankId);
	}

	/**
	 * gets the merged rank id -&gt; rank name map
	 */
	public Map<Integer, String> getRankNames() {
		return rankNames;
	}

	/**
	 * gets the rank id -&gt; rank name map for the given classification (falling back to the merged map)
	 */
	public Map<Integer, String> getRankNames(String classification) {
		return classificationRankNames.getOrDefault(classification, rankNames);
	}

	@Override
	public Set<String> getCompatibleWith() {
		return compatibleWith;
	}

	/**
	 * maps a database classification name to the name MEGAN uses: the NCBI taxonomy is named
	 * {@link Classification#Taxonomy}, every other classification keeps its database name
	 */
	private static String megaName(String dbName) {
		return "NCBI".equals(dbName) ? Classification.Taxonomy : dbName;
	}

	// --- helpers ---

	private static Set<String> columnNames(ResultSetMetaData md) throws SQLException {
		final var set = new HashSet<String>();
		for (var i = 1; i <= md.getColumnCount(); i++)
			set.add(md.getColumnName(i).toLowerCase());
		return set;
	}

	private static String str(ResultSet rs, Set<String> columns, String columnName) throws SQLException {
		return columns.contains(columnName) ? rs.getString(columnName) : null;
	}

	private static Integer intOrNull(ResultSet rs, Set<String> columns, String columnName) throws SQLException {
		if (!columns.contains(columnName))
			return null;
		final var value = rs.getInt(columnName);
		return rs.wasNull() ? null : value;
	}

	/**
	 * one row of the classifications table, with the database name mapped to the MEGAN name
	 */
	private record ClassificationMeta(String megaName, String name, String displayName, String type, String treeTable,
									  Integer rootId, int nodeCount, boolean isDag, String source, String sourceVersion,
									  String doi) {
	}

	/**
	 * the 1-based column indices of a tree table, resolved by name so column order and the presence or absence of
	 * the optional rank_id column do not matter; an index of 0 means the column is absent
	 */
	private record Columns(int nodeId, int parentId, int name, int rankId) {
		Columns(ResultSetMetaData md) throws SQLException {
			this(indexOf(md, "node_id"), indexOf(md, "parent_id"), indexOf(md, "name"), indexOf(md, "rank_id"));
		}

		private static int indexOf(ResultSetMetaData md, String columnName) throws SQLException {
			for (var i = 1; i <= md.getColumnCount(); i++) {
				if (columnName.equalsIgnoreCase(md.getColumnName(i)))
					return i;
			}
			return 0;
		}
	}

	/**
	 * opens an SQLite connection to the given file
	 */
	public static Connection connect(String fileName, boolean readOnly) throws SQLException {
		final var config = new SQLiteConfig();
		config.setCacheSize(10000);
		config.setReadOnly(readOnly);
		return config.createConnection("jdbc:sqlite:" + fileName);
	}
}
