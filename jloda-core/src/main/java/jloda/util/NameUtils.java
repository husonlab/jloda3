/*
 *  NameUtils.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.util;

import jloda.phylo.PhyloGraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * utilities for deriving file names from taxa in a document
 * Daniel Huson, 6.2026
 */
public class NameUtils {
	/**
	 * Derive a content-based document name from a phylogeny.
	 *
	 * @param phylo              the phylogeny
	 * @param otherDocumentNames existing names
	 * @return a short, human-readable title
	 */
	public static String deriveDocumentName(PhyloGraph phylo, Collection<String> otherDocumentNames) {
		Supplier<Collection<String>> taxaSupplier = () -> phylo.nodeStream().filter(v -> v.getOutDegree() == 0).map(phylo::getLabel).filter(s -> s != null && !s.isBlank()).toList();
		IntSupplier hSupplier = () -> phylo.nodeStream().filter(v -> v.getInDegree() > 1).mapToInt(v -> v.getInDegree() - 1).sum();
		var name = NameUtils.deriveDocumentName(taxaSupplier, hSupplier);
		return NameUtils.uniqueName(name, otherDocumentNames);
	}

	/**
	 * Derive a content-based document name from a phylogeny.
	 *
	 * @param taxaSupplier          supplies the current taxon (leaf) labels
	 * @param reticulationsSupplier supplies the current number of reticulations (0 means it is a tree)
	 * @return a short, human-readable title
	 */
	public static String deriveDocumentName(Supplier<Collection<String>> taxaSupplier, IntSupplier reticulationsSupplier) {
		var reticulations = reticulationsSupplier.getAsInt();
		var type = reticulations > 0 ? "net" : "tree";

		var raw = taxaSupplier.get();
		var taxa = (raw == null ? List.<String>of() : raw).stream()
				.filter(s -> s != null && !s.isBlank())
				.map(String::strip)
				.distinct()
				.sorted()
				.toList();

		if (taxa.isEmpty()) {
			if (reticulations <= 0)
				return "Untitled";
			return reticulations == 1 ? "Network (h=1)" : "Network (h=%d)".formatted(reticulations);
		}
		// synthetic placeholders (a, B, X1, t12, …) carry no content — name by size and type
		if (taxa.stream().allMatch(s -> s.matches("[A-Za-z]\\d*")))
			return "%s (%d taxa)".formatted(reticulations > 0 ? "Netw." : "Tree", taxa.size());

		if (taxa.size() == 1)
			return truncate(taxa.get(0).replace('_', ' '), 28);

		var genus = commonLeadingWord(taxa);
		if (genus != null)
			return "%s %s (%d taxa)".formatted(genus, type, taxa.size());
		return "%s +%d (%s)".formatted(truncate(taxa.get(0).replace('_', ' '), 24), taxa.size() - 1, type);
	}

	/**
	 * The first whitespace- or underscore-separated token shared by at least 60% of the labels (a genus,
	 * usually), or null if there is no clear common prefix. Underscores count as separators because Newick
	 * taxa often use them in place of spaces (Drosophila_melanogaster).
	 */
	private static String commonLeadingWord(List<String> taxa) {
		var counts = new HashMap<String, Integer>();
		for (var t : taxa) {
			var first = t.split("[\\s_]+", 2)[0];
			if (first.length() >= 3 && first.chars().anyMatch(Character::isLetter))
				counts.merge(first, 1, Integer::sum);
		}
		var best = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
		if (best == null)
			return null;
		return best.getValue() >= Math.ceil(0.6 * taxa.size()) ? best.getKey() : null;
	}

	private static String truncate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)).strip() + "…";
	}

	/**
	 * A filesystem-safe slug, e.g. "Drosophila network (8 taxa)" -> "drosophila-network-8-taxa".
	 */
	public static String toFilenameSlug(String name) {
		if (name == null || name.isBlank())
			return "untitled";
		var slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (slug.length() > 40)
			slug = slug.substring(0, 40).replaceAll("-+$", "");
		return slug.isEmpty() ? "untitled" : slug;
	}

	/**
	 * Returns name if free, else appends " (2)", " (3)", ... until it is not among existingNames.
	 */
	public static String uniqueName(String name, Collection<String> existingNames) {
		if (!existingNames.contains(name))
			return name;
		for (var i = 2; ; i++) {
			var candidate = name + " (" + i + ")";
			if (!existingNames.contains(candidate))
				return candidate;
		}
	}
}
