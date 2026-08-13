/*
 * ClassificationsManifest.java Copyright (C) 2026 Daniel H. Huson
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * the catalog of classification databases available for download, fetched as JSON from a URL (the
 * {@code ClassificationsDatabaseManifestURL} property). The expected shape is:
 * <pre>
 * {
 *   "databases": [
 *     { "name": "megan-classification-r1", "release": "r1",
 *       "url": "https://.../megan-classification-r1.db", "size": 1234567, "sha256": "abc...",
 *       "date": "2026-01-15", "description": "NCBI, GTDB, GO, SEED, EGGNOG", "latest": true }
 *   ]
 * }
 * </pre>
 * This mirrors the manifest-based approach used by MEGAN's installer updater. A tiny self-contained JSON parser
 * is used so that the module keeps its minimal dependency footprint (jloda-core + sqlite only).
 * <p>
 * Daniel Huson, 7.2026
 */
public class ClassificationsManifest {
	private final List<Entry> databases = new ArrayList<>();

	/**
	 * the databases listed in the manifest, in the order given
	 */
	public List<Entry> getDatabases() {
		return databases;
	}

	/**
	 * the entry flagged as the latest, or the first entry, or null if the manifest is empty
	 */
	public Entry getLatest() {
		for (var entry : getDatabases())
			if (entry.isLatest())
				return entry;
		return getDatabases().isEmpty() ? null : getDatabases().get(0);
	}

	/**
	 * fetches and parses the manifest from the given URL
	 *
	 * @param url the manifest URL
	 * @return the parsed manifest
	 * @throws IOException if the manifest cannot be fetched or parsed
	 */
	public static ClassificationsManifest fetch(String url) throws IOException {
		if (url == null || url.isBlank())
			throw new IOException("No classification-database catalog URL configured");
		try {
			final var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
			final var request = HttpRequest.newBuilder(URI.create(url)).GET().build();
			final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300)
				throw new IOException("Failed to fetch catalog: HTTP " + response.statusCode() + " from " + url);
			return parse(response.body());
		} catch (IOException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IOException("Failed to fetch catalog from " + url + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * parses a manifest from a JSON string
	 */
	public static ClassificationsManifest parse(String json) throws IOException {
		try {
			final var manifest = new ClassificationsManifest();
			if (JsonParser.parse(json) instanceof Map<?, ?> root && root.get("databases") instanceof List<?> databases) {
				for (final var item : databases) {
					if (item instanceof Map<?, ?> node) {
						final var entry = new Entry();
						entry.name = str(node, "name");
						entry.release = str(node, "release");
						entry.url = str(node, "url");
						entry.size = longVal(node, "size");
						entry.sha256 = str(node, "sha256");
						entry.date = str(node, "date");
						entry.description = str(node, "description");
						entry.latest = boolVal(node, "latest");
						manifest.databases.add(entry);
					}
				}
			}
			return manifest;
		} catch (Exception ex) {
			throw new IOException("Failed to parse classification-database catalog: " + ex.getMessage(), ex);
		}
	}

	private static String str(Map<?, ?> node, String field) {
		final var value = node.get(field);
		return value != null ? value.toString() : null;
	}

	private static long longVal(Map<?, ?> node, String field) {
		return (node.get(field) instanceof Number number) ? number.longValue() : 0L;
	}

	private static boolean boolVal(Map<?, ?> node, String field) {
		return (node.get(field) instanceof Boolean b) && b;
	}

	/**
	 * one classification database available for download
	 */
	public static class Entry {
		private String name;
		private String release;
		private String url;
		private long size;
		private String sha256;
		private String date;
		private String description;
		private boolean latest;

		public String getName() {
			return name;
		}

		public String getRelease() {
			return release;
		}

		public String getUrl() {
			return url;
		}

		public long getSize() {
			return size;
		}

		public String getSha256() {
			return sha256;
		}

		public String getDate() {
			return date;
		}

		public String getDescription() {
			return description;
		}

		public boolean isLatest() {
			return latest;
		}

		@Override
		public String toString() {
			final var buf = new StringBuilder(name != null ? name : "?");
			if (date != null && !date.isBlank())
				buf.append("  (").append(date).append(")");
			if (size > 0)
				buf.append("  ").append(formatSize(size));
			if (description != null && !description.isBlank())
				buf.append("  — ").append(description);
			if (latest)
				buf.append("  [latest]");
			return buf.toString();
		}
	}

	/**
	 * formats a byte count as a human-readable size
	 */
	public static String formatSize(long bytes) {
		if (bytes < 1024)
			return bytes + " B";
		final var units = new String[]{"KB", "MB", "GB", "TB"};
		double value = bytes;
		int unit = -1;
		do {
			value /= 1024.0;
			unit++;
		} while (value >= 1024.0 && unit < units.length - 1);
		return String.format("%.1f %s", value, units[unit]);
	}

	/**
	 * a tiny recursive-descent JSON parser returning Map/List/String/Double/Boolean/null; enough to read the
	 * classification-database catalog without pulling in a JSON library
	 */
	private static final class JsonParser {
		private final String s;
		private int pos;

		private JsonParser(String s) {
			this.s = s;
		}

		static Object parse(String s) {
			final var parser = new JsonParser(s);
			final var value = parser.readValue();
			parser.skipWhitespace();
			if (parser.pos < parser.s.length())
				throw new IllegalArgumentException("Trailing content at position " + parser.pos);
			return value;
		}

		private Object readValue() {
			skipWhitespace();
			if (pos >= s.length())
				throw new IllegalArgumentException("Unexpected end of input");
			return switch (s.charAt(pos)) {
				case '{' -> readObject();
				case '[' -> readArray();
				case '"' -> readString();
				case 't', 'f' -> readBoolean();
				case 'n' -> readNull();
				default -> readNumber();
			};
		}

		private Map<String, Object> readObject() {
			final var map = new LinkedHashMap<String, Object>();
			pos++; // consume '{'
			skipWhitespace();
			if (peek() == '}') {
				pos++;
				return map;
			}
			while (true) {
				skipWhitespace();
				final var key = readString();
				skipWhitespace();
				expect(':');
				map.put(key, readValue());
				skipWhitespace();
				final var c = next();
				if (c == ',')
					continue;
				if (c == '}')
					break;
				throw new IllegalArgumentException("Expected ',' or '}' at position " + (pos - 1));
			}
			return map;
		}

		private List<Object> readArray() {
			final var list = new ArrayList<>();
			pos++; // consume '['
			skipWhitespace();
			if (peek() == ']') {
				pos++;
				return list;
			}
			while (true) {
				list.add(readValue());
				skipWhitespace();
				final var c = next();
				if (c == ',')
					continue;
				if (c == ']')
					break;
				throw new IllegalArgumentException("Expected ',' or ']' at position " + (pos - 1));
			}
			return list;
		}

		private String readString() {
			expect('"');
			final var buf = new StringBuilder();
			while (true) {
				if (pos >= s.length())
					throw new IllegalArgumentException("Unterminated string");
				final var c = s.charAt(pos++);
				if (c == '"')
					break;
				if (c == '\\') {
					final var e = s.charAt(pos++);
					switch (e) {
						case '"' -> buf.append('"');
						case '\\' -> buf.append('\\');
						case '/' -> buf.append('/');
						case 'b' -> buf.append('\b');
						case 'f' -> buf.append('\f');
						case 'n' -> buf.append('\n');
						case 'r' -> buf.append('\r');
						case 't' -> buf.append('\t');
						case 'u' -> {
							buf.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
							pos += 4;
						}
						default -> throw new IllegalArgumentException("Invalid escape '\\" + e + "'");
					}
				} else {
					buf.append(c);
				}
			}
			return buf.toString();
		}

		private Object readNumber() {
			final var start = pos;
			while (pos < s.length() && "+-.eE0123456789".indexOf(s.charAt(pos)) >= 0)
				pos++;
			if (pos == start)
				throw new IllegalArgumentException("Invalid value at position " + start);
			return Double.parseDouble(s.substring(start, pos));
		}

		private Object readBoolean() {
			if (s.startsWith("true", pos)) {
				pos += 4;
				return Boolean.TRUE;
			}
			if (s.startsWith("false", pos)) {
				pos += 5;
				return Boolean.FALSE;
			}
			throw new IllegalArgumentException("Invalid literal at position " + pos);
		}

		private Object readNull() {
			if (s.startsWith("null", pos)) {
				pos += 4;
				return null;
			}
			throw new IllegalArgumentException("Invalid literal at position " + pos);
		}

		private char peek() {
			skipWhitespace();
			return pos < s.length() ? s.charAt(pos) : '\0';
		}

		private char next() {
			skipWhitespace();
			return s.charAt(pos++);
		}

		private void expect(char c) {
			skipWhitespace();
			if (pos >= s.length() || s.charAt(pos) != c)
				throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
			pos++;
		}

		private void skipWhitespace() {
			while (pos < s.length() && Character.isWhitespace(s.charAt(pos)))
				pos++;
		}
	}
}
