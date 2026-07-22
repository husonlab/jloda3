/*
 *  ExamplesManager.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.examples;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Loads examples that are bundled inside the application as classpath resources.
 *
 * <h2>Why every resource ends in .dat</h2>
 * iOS/GluonFX native images only bundle resources with certain extensions, and {@code .dat}
 * is the one we rely on. So all
 * files in the examples directory - <em>including the index itself</em> - must end in
 * {@code .dat}, and the real suffix has to be communicated separately. That is what the
 * index file does.
 *
 * <h2>Index format ({@code index.dat})</h2>
 * One entry per line; blank lines and lines starting with {@code #} are ignored:
 * <pre>
 *   &lt;resource&gt; [| &lt;display name&gt; [| &lt;real file name&gt;]]
 * </pre>
 * Two ways to give the real name, both supported:
 * <pre>
 *   # 1. Convention: append .dat to the real name, nothing else needed.
 *   formose.crs.dat        | Formose reaction
 *
 *   # 2. Explicit: for legacy resources whose name does not carry the real suffix.
 *   Bees.dat               | Bee data                 | Bees.nex
 * </pre>
 * If the display name is omitted it defaults to the base name of the real file name.
 *
 * <h2>The anchor class matters (JPMS)</h2>
 * Resources in a named module are encapsulated: another module cannot read
 * {@code /catrenet/examples/...} unless that package is opened, because {@code catrenet.examples}
 * is a valid package name. Passing a class <em>from the application's own module</em> as the
 * anchor sidesteps this entirely - a module can always read its own resources. So call this
 * with e.g. {@code CatReNet.class}, never with a jloda class.
 */
public class ExamplesManager {
	public static final String DEFAULT_INDEX_NAME = "index.dat";

	private final Class<?> anchorClass;
	private final String resourceDir;
	private final List<ExampleEntry> entries = new ArrayList<>();

	/**
	 * @param anchorClass a class from the same module/jar as the examples, e.g. {@code CatReNet.class}
	 * @param resourceDir absolute resource directory, e.g. {@code /catrenet/examples}
	 */
	public ExamplesManager(Class<?> anchorClass, String resourceDir) throws IOException {
		this(anchorClass, resourceDir, DEFAULT_INDEX_NAME);
	}

	public ExamplesManager(Class<?> anchorClass, String resourceDir, String indexName) throws IOException {
		this.anchorClass = Objects.requireNonNull(anchorClass, "anchorClass");

		var dir = Objects.requireNonNull(resourceDir, "resourceDir").trim();
		if (!dir.startsWith("/"))
			dir = "/" + dir;
		while (dir.endsWith("/"))
			dir = dir.substring(0, dir.length() - 1);
		this.resourceDir = dir;

		entries.addAll(parseIndex(indexName));
	}

	/**
	 * Creates a manager, or returns null if there are no bundled examples.
	 * Convenient when the examples directory is optional.
	 */
	public static ExamplesManager createOrNull(Class<?> anchorClass, String resourceDir) {
		try {
			var manager = new ExamplesManager(anchorClass, resourceDir);
			return manager.isEmpty() ? null : manager;
		} catch (IOException ex) {
			System.err.println("No bundled examples: " + ex.getMessage());
			return null;
		}
	}

	public List<ExampleEntry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * Opens the example for reading. The caller must close the stream.
	 */
	public InputStream open(ExampleEntry entry) throws IOException {
		var ins = anchorClass.getResourceAsStream(entry.resourcePath());
		if (ins == null)
			throw new FileNotFoundException("Resource not found: " + entry.resourcePath());
		return ins;
	}

	/**
	 * Reads the whole example as UTF-8 text. Preferred when the application can parse
	 * from a string or reader: nothing is written to disk and the document stays untitled.
	 */
	public String readText(ExampleEntry entry) throws IOException {
		try (var ins = open(entry)) {
			return new String(ins.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Writes the example into the given directory under its <em>real</em> file name,
	 * so existing open-by-path code and suffix-based format detection work unchanged.
	 *
	 * @return the written file
	 */
	public Path materialize(ExampleEntry entry, Path directory) throws IOException {
		Files.createDirectories(directory);
		var target = directory.resolve(entry.fileName());
		try (var ins = open(entry)) {
			Files.copy(ins, target, StandardCopyOption.REPLACE_EXISTING);
		}
		return target;
	}

	/**
	 * Writes the example to a temporary directory, deleted on exit. Use when the application
	 * can only open files by path.
	 */
	public Path materializeToTemp(ExampleEntry entry) throws IOException {
		var dir = Files.createTempDirectory("examples");
		dir.toFile().deleteOnExit();
		var file = materialize(entry, dir);
		file.toFile().deleteOnExit();
		return file;
	}

	private List<ExampleEntry> parseIndex(String indexName) throws IOException {
		var indexPath = resourceDir + "/" + indexName;
		var ins = anchorClass.getResourceAsStream(indexPath);
		if (ins == null)
			throw new FileNotFoundException("Examples index not found: " + indexPath
											+ " (anchor class: " + anchorClass.getName() + ")");

		var list = new ArrayList<ExampleEntry>();
		try (var reader = new BufferedReader(new InputStreamReader(ins, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				var tokens = line.split("\\|", -1);
				var resource = tokens[0].trim();
				if (resource.isEmpty())
					continue;

				var displayName = (tokens.length > 1) ? tokens[1].trim() : "";
				var fileName = (tokens.length > 2) ? tokens[2].trim() : "";

				// No explicit real name: recover it by stripping the trailing .dat
				if (fileName.isEmpty()) {
					fileName = resource.toLowerCase().endsWith(".dat")
							? resource.substring(0, resource.length() - 4)
							: resource;
				}

				var resourcePath = resourceDir + "/" + resource;
				if (anchorClass.getResource(resourcePath) == null) {
					System.err.println("Skipping missing example resource: " + resourcePath);
					continue;
				}

				var entry = new ExampleEntry(resourcePath, displayName, fileName);
				if (displayName.isEmpty())
					entry = new ExampleEntry(resourcePath, entry.baseName(), fileName);

				list.add(entry);
			}
		}
		return list;
	}

	public List<ExampleEntry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	/**
	 * copy the examples to a directory
	 *
	 * @param targetDirectory the target directory
	 * @throws IOException if something fails
	 */
	public void copyExamples(String targetDirectory) throws IOException {
		for (var entry : getEntries()) {
			try (var writer = new FileWriter(targetDirectory + File.separator + entry.fileName())) {
				writer.write(readText(entry));
			}
		}
	}
}
