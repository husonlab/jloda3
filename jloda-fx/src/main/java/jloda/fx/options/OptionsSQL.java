/*
 *  OptionsSQL.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.options;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * saves and loads all annotated options to and from an SQLite table
 * <p>
 * The table is self-describing: besides name, type and value, it also holds the description
 * and acceptable range declared in the annotation, so that a saved file can be inspected
 * with any SQLite browser.
 * <p>
 * Daniel Huson, 7.2026
 */
public class OptionsSQL {
	public static final String DEFAULT_TABLE = "parameters";

	/**
	 * creates the options table, if necessary, adding any columns missing in files written by an older version
	 */
	public static void ensureSchema(Connection conn, String table) throws SQLException {
		try (var stmt = conn.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS %s (
						name         TEXT PRIMARY KEY,
						type         TEXT NOT NULL,
						value        TEXT,
						description  TEXT,
						legal_range  TEXT
					);
					""".formatted(table));

			var columns = new ArrayList<String>();
			try (var rs = stmt.executeQuery("PRAGMA table_info(%s)".formatted(table))) {
				while (rs.next()) {
					columns.add(rs.getString("name"));
				}
			}
			for (var column : new String[]{"description", "legal_range"}) {
				if (!columns.contains(column))
					stmt.execute("ALTER TABLE %s ADD COLUMN %s TEXT".formatted(table, column));
			}
		}
	}

	/**
	 * writes all persistable options
	 */
	public static void save(Connection conn, String table, OptionsRegistry registry) throws SQLException {
		ensureSchema(conn, table);

		try (var stmt = conn.createStatement()) {
			stmt.executeUpdate("DELETE FROM " + table);
		}

		try (var ps = conn.prepareStatement(
				"INSERT INTO %s (name, type, value, description, legal_range) VALUES (?, ?, ?, ?, ?)".formatted(table))) {
			for (var item : registry.getItems()) {
				if (!item.isPersist())
					continue;
				ps.setString(1, item.getName());
				ps.setString(2, item.getValueType().toString().toLowerCase());
				ps.setString(3, item.getValueString());
				ps.setString(4, item.getDescription());
				ps.setString(5, item.getRangeString());
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	/**
	 * reads all options present in the table, leaving any option not mentioned at its current value
	 *
	 * @return number of options set
	 */
	public static int load(Connection conn, String table, OptionsRegistry registry) throws SQLException {
		var count = 0;
		try (var stmt = conn.createStatement();
			 var rs = stmt.executeQuery("SELECT name, value FROM %s".formatted(table))) {
			while (rs.next()) {
				var name = rs.getString("name");
				var value = rs.getString("value");
				if (name != null && value != null && registry.setValueString(name, value))
					count++;
			}
		}
		return count;
	}
}
