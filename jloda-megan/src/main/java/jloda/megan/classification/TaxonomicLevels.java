/*
 * TaxonomicLevels.java Copyright (C) 2026 Daniel H. Huson
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

import java.util.*;

/**
 * defines names and codes for taxonomic levels
 * Daniel Huson, 6.2007
 */
public class TaxonomicLevels {
	private final Map<Integer, String> id2name;
	private final Map<String, Integer> name2id;
	private final List<String> names = new LinkedList<>();

	public static final String Domain = "Domain";
	public static final String Kingdom = "Kingdom";
	public static final String Phylum = "Phylum";
	public static final String Class = "Class";
	public static final String Order = "Order";
	public static final String Family = "Family";
	private static final String Varietas = "Varietas";
	public static final String Genus = "Genus";
	private static final String Species_group = "Species_group";
	public static final String Species = "Species";
	private static final String Subspecies = "Subspecies";

	private final BitSet majorRanks = new BitSet();

	/**
	 * the ranks that make up an exported lineage, in order, with the one-letter code each is written as.
	 * <p>
	 * This is deliberately NOT {@link #majorRanks}, which is the list of ranks a user can <em>choose</em>
	 * (collapse at rank, select by rank, project to rank) and where Kingdom is a useful choice. As a level in a
	 * path Kingdom is not: NCBI carries it on only 22 nodes, and since it was extended to the prokaryotes
	 * (Bacillati, Thermoproteati, ...) it inserts a level between Domain and Phylum that most users do not expect;
	 * GTDB has no Kingdom at all, so including it also makes NCBI and GTDB paths differ in depth. The 2007 comment
	 * on {@code majorRanks.set(1)} said as much.
	 * <p>
	 * Domain is written {@code k}, not {@code d}: that is the Greengenes/QIIME convention that
	 * {@code megan8.util.ExportStamp} has always used, and downstream tools parse it.
	 */
	private static final int[] PATH_RANK_IDS = {127, 2, 3, 4, 5, 98, 100};
	private static final char[] PATH_RANK_LETTERS = {'k', 'p', 'c', 'o', 'f', 'g', 's'};

	/**
	 * is this one of the ranks that make up an exported lineage? Kingdom is not; see {@link #PATH_RANK_IDS}.
	 */
	public static boolean isPathRank(int rank) {
		for (var id : PATH_RANK_IDS) {
			if (id == rank)
				return true;
		}
		return false;
	}

	/**
	 * the one-letter code a path rank is written as ('k' for Domain), or 0 if the rank is not a path rank
	 */
	public static char getPathLetter(int rank) {
		for (var i = 0; i < PATH_RANK_IDS.length; i++) {
			if (PATH_RANK_IDS[i] == rank)
				return PATH_RANK_LETTERS[i];
		}
		return 0;
	}

	/**
	 * the path ranks in order, from Domain down to Species
	 */
	public static int[] getPathRanks() {
		return PATH_RANK_IDS.clone();
	}

	/**
	 * the one-letter codes of the path ranks, in the same order, uppercased &mdash; e.g. "KPCOFGS"
	 */
	public static String getPathLetters() {
		return new String(PATH_RANK_LETTERS).toUpperCase();
	}

	private static TaxonomicLevels instance;

	/**
	 * get instance
	 *
	 * @return instance
	 */
	private static TaxonomicLevels getInstance() {
		if (instance == null)
			instance = new TaxonomicLevels();
		return instance;
	}

	/**
	 * constructor
	 */
	private TaxonomicLevels() {
		name2id = new HashMap<>();
		id2name = new HashMap<>();
		// add all defined levels here:
		// addLevel((byte)0       ,"No rank");
		addLevel(127, Domain);
		majorRanks.set(127);

		addLevel(1, Kingdom);
		majorRanks.set(1);  // don't use this because it will cause kingdoms to be filled in

		addLevel(2, Phylum);
		majorRanks.set(2);

		addLevel(3, Class);
		majorRanks.set(3);

		addLevel(4, Order);
		majorRanks.set(4);

		addLevel(5, Family);
		majorRanks.set(5);

		addLevel(90, Varietas);

		addLevel(98, Genus);
		majorRanks.set(98);

		addLevel(99, Species_group);

		addLevel(100, Species);
		majorRanks.set(100);

		addLevel(101, Subspecies);
	}

	/**
	 * is this a major KPCOFGS rank?
	 *
	 * @return true, if major
	 */
	public static boolean isMajorRank(int rank) {
		return getInstance().majorRanks.get(rank);
	}

	/**
	 * get the next major rank
	 *
	 * @return next major rank
	 */
	public static int getNextRank(int rank) {
		if (rank == 100)
			return 0;
		if (rank == 127)
			return 1;
		int nextRank = getInstance().majorRanks.nextSetBit(rank + 1);
		return Math.max(nextRank, 0)
				;
	}

	/**
	 * get rank from one letter code
	 *
	 * @return level
	 */
	public static int getRankForOneLetterCode(String oneLetterLabel) {
		return switch (oneLetterLabel.toLowerCase()) {
			case "d", "k" -> getId(Domain); // 'k' is how a path writes Domain
			case "p" -> getId(Phylum);
			case "c" -> getId(Class);
			case "o" -> getId(Order);
			case "f" -> getId(Family);
			case "g" -> getId(Genus);
			case "s" -> getId(Species);
			default -> 0;
		};
	}

	/**
	 * get one letter code for rank, or null
	 *
	 * @return code or null
	 */
	public static String getOneLetterCodeFromRank(int rank) {
		final var letter = getPathLetter(rank);
		return letter == 0 ? null : String.valueOf(letter);
	}

	/* used to set up table
	 */

	private void addLevel(Integer level, String name) {
		name2id.put(name, level);
		id2name.put(level, name);
		names.add(name);
	}

	/**
	 * given a level name, returns the id
	 *
	 * @return level id     or null
	 */
	public static Integer getId(String name) {
		Integer value = TaxonomicLevels.getInstance().name2id.get(name);
		return value == null ? 0 : value;
	}

	/**
	 * given a level id, returns its name
	 *
	 * @return name
	 */
	public static String getName(int id) {
		return TaxonomicLevels.getInstance().id2name.get(id);
	}

	/**
	 * replaces the rank id -&gt; name mappings with those from a classification database, keeping the hardcoded
	 * defaults for any ids the database does not provide (the major-rank set is left unchanged). Rank names shown to
	 * the user are thus taken from the current classification database rather than being hard-wired.
	 * <p>
	 * The previous (hardcoded) name of an overridden rank is retained as an alias in the name-&gt;id map, so that
	 * code resolving a rank by its canonical MEGAN name ({@link #getGenusId()}, {@link #getRankForOneLetterCode},
	 * rank-collapse/-select commands, ...) keeps working even when the database uses a different display name for
	 * the same rank id.
	 * <p>
	 * Two cases are not treated as an override: rank id 0, which is MEGAN's 'no rank' sentinel rather than a
	 * level; and a name that differs from the hardcoded one only in spelling (the released databases write the raw
	 * NCBI {@code genus}, {@code species group}), where the canonical MEGAN name is kept and the database's
	 * spelling is added as a further alias.
	 */
	public static void setFromDatabase(Map<Integer, String> dbRankNames) {
		if (dbRankNames == null || dbRankNames.isEmpty())
			return;
		final var instance = getInstance();
		for (var entry : dbRankNames.entrySet()) {
			final var id = entry.getKey();
			final var name = entry.getValue();
			if (id == null || name == null || name.isBlank())
				continue;
			if (id == 0)
				continue; // 0 is MEGAN's 'no rank' sentinel, deliberately not one of the selectable levels
			final var previous = instance.id2name.get(id);
			if (name.equals(previous))
				continue;
			if (previous != null) {
				if (isSpellingVariant(name, previous)) {
					instance.name2id.put(name, id); // the database's spelling resolves, but the display name stays canonical
					continue;
				}
				final var idx = instance.names.indexOf(previous);
				if (idx >= 0)
					instance.names.set(idx, name);
				// keep 'previous' as an alias in name2id (do not remove it), so name-based lookups still resolve
			} else {
				instance.names.add(name);
			}
			instance.id2name.put(id, name);
			instance.name2id.put(name, id);
		}
	}

	/**
	 * do two names denote the same rank, differing only in spelling (upper/lower case, and '_' versus ' ')? A
	 * database that writes {@code genus} or {@code species group} is spelling MEGAN's rank differently, not
	 * renaming it, and the canonical name is kept: rank names are compared with {@code equals} elsewhere
	 * (e.g. {@code megan8.util.ExportStamp}), so a mere change of case would break those comparisons.
	 */
	private static boolean isSpellingVariant(String a, String b) {
		return a.replace('_', ' ').equalsIgnoreCase(b.replace('_', ' '));
	}

	/**
	 * get all names
	 *
	 * @return names
	 */
	public static List<String> getAllNames() {
		return TaxonomicLevels.getInstance().names;
	}

	/**
	 * get all names of major ranks
	 *
	 * @return names
	 */
	public static List<String> getAllMajorRanks() {
		ArrayList<String> list = new ArrayList<>();
		for (String name : TaxonomicLevels.getInstance().names) {
			if (isMajorRank(getId(name)))
				list.add(name);
		}
		return list;
	}

	public static int getSpeciesId() {
		return getId(Species);
	}

	public static int getSubspeciesId() {
		return getId(Subspecies);
	}

	public static int getGenusId() {
		return getId(Genus);
	}
}
