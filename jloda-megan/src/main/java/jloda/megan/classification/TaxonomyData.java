/*
 * TaxonomyData.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.graph.Node;
import jloda.megan.algorithms.LCAAddressing;
import jloda.megan.classification.Classification;
import jloda.megan.classification.ClassificationManager;
import jloda.megan.classification.IdMapper;
import jloda.megan.classification.data.ClassificationFullTree;
import jloda.megan.classification.data.Name2IdMap;

import java.util.*;

/**
 * maintains static access to the taxonomy
 * Daniel Huson, 4.2015
 */
public class TaxonomyData {
	public static final int ROOT_ID = 1;
	public static final int BACTERIA_ID = 2;
	public static final int VIRUSES_ID = 10239;

	private static Classification taxonomyClassification;

	/**
	 * explicitly load the taxonomy classification
	 */
	public static void load() {
		setTaxonomyClassification(ClassificationManager.get(Classification.Taxonomy, true));
	}

	/**
	 * set the taxonomy classification
	 */
	public static void setTaxonomyClassification(Classification taxonomyClassification) {
		TaxonomyData.taxonomyClassification = taxonomyClassification;
	}

	/**
	 * gets the taxonomy names to ids
	 *
	 * @return taxonomy names to ids
	 */
	public static Name2IdMap getName2IdMap() {
		return taxonomyClassification.getIdMapper().getName2IdMap();
	}

	/**
	 * gets the taxonomy tree
	 *
	 * @return tree
	 */
	public static ClassificationFullTree getTree() {
		return taxonomyClassification.getFullTree();
	}

	/**
	 * is taxonomy data available?
	 *
	 * @return true, if available
	 */
	public static boolean isAvailable() {
		return taxonomyClassification != null;
	}

	/**
	 * is this taxon, or one of its ancestors, disabled? Taxa that are disabled are ignored by LCA algorithm
	 *
	 * @return true, if disabled
	 */
	public static boolean isTaxonDisabled(String cName, Integer taxonId) {
		// cName is null when no particular classification is meant, in which case nothing is disabled
		return Classification.Taxonomy.equals(cName) && (taxonId == null || (taxonId > 0 && taxonomyClassification.getIdMapper().getDisabledIds().contains(taxonId)));
	}

	/**
	 * is this taxon, or one of its ancestors, disabled? Taxa that are disabled are ignored by LCA algorithm
	 *
	 * @return true, if disabled
	 */
	public static boolean isTaxonDisabled(Integer taxonId) {
		return taxonId == null || (taxonId > 0 && taxonomyClassification.getIdMapper().getDisabledIds().contains(taxonId));
	}


	/**
	 * get all currently disabled taxa. Note that any taxon below a disabled taxon is also considered disabled
	 *
	 * @return all disabled taxa
	 */
	public static Set<Integer> getDisabledTaxa() {
		if (taxonomyClassification == null)
			load();
		return taxonomyClassification.getIdMapper().getDisabledIds();
	}

	public static int getTaxonomicRank(Integer id) {
		return taxonomyClassification.getIdMapper().getName2IdMap().getRank(id);
	}

	public static void setTaxonomicRank(Integer id, byte rank) {
		taxonomyClassification.getIdMapper().getName2IdMap().setRank(id, rank);
	}

	/**
	 * gets the closest ancestor that has a major rank
	 *
	 * @return rank of this node, if is major, otherwise of closest ancestor
	 */
	public static int getLowestAncestorWithMajorRank(Integer id) {
		if (id <= 0)
			return id;

		while (true) {
			var rank = getTaxonomicRank(id);
			if (TaxonomicLevels.isMajorRank(rank))
				return id;
			var address = getAddress(id);
			if (address == null || address.isEmpty())
				return 1;
			address = address.substring(0, address.length() - 1);
			if (address.isEmpty())
				return 1;
			id = getAddress2Id(address);
			if (id <= 0)
				return 1;
		}

	}

	/**
	 * gets the ancestor at the given rank, or 0
	 */
	public static int getAncestorAtGivenRank(Integer id, int rank) {
		while (id > 0) {
			if (getTaxonomicRank(id) == rank)
				return id;
			var address = getAddress(id);
			if (address == null || address.isEmpty())
				return 0;
			do {
				address = address.substring(0, address.length() - 1);
				if (address.isEmpty())
					return 0;
				id = getAddress2Id(address);
			}
			while (id == 0);
		}
		return 0;
	}

	public static String getAddress(Integer id) {
		return taxonomyClassification.getFullTree().getAddress(id);
	}

	public static Integer getAddress2Id(String address) {
		return taxonomyClassification.getFullTree().getAddress2Id(address);
	}

	public static boolean isAncestor(int higherTaxonId, int lowerTaxonId) {
		var higherAddress = getAddress(higherTaxonId);
		var lowerAddress = getAddress(lowerTaxonId);
		return higherAddress != null && (lowerAddress == null || lowerAddress.startsWith(higherAddress));
	}

	/**
	 * returns the LCA of a set of taxon ids
	 *
	 * @return id
	 */
	public static int getLCA(Set<Integer> taxonIds, boolean removeAncestors) {
		if (taxonIds.isEmpty())
			return IdMapper.NOHITS_ID;

		var addresses = new HashSet<String>();

		var countKnownTaxa = 0;

		// compute addresses of all hit taxa:
		for (var taxonId : taxonIds) {
			if (taxonId > 0 && !isTaxonDisabled(taxonId)) {
				var address = getAddress(taxonId);
				if (address != null) {
					addresses.add(address);
					countKnownTaxa++;
				}
			}
		}

		// compute LCA using addresses:
		if (countKnownTaxa > 0) {
			var address = LCAAddressing.getCommonPrefix(addresses, removeAncestors);
			return getAddress2Id(address);
		}

		// although we had some hits, couldn't make an assignment
		return IdMapper.UNASSIGNED_ID;
	}

	/**
	 * get the path string associated with a taxon
	 *
	 * @return path string or null
	 */
	public static String getPath(int taxId, boolean majorRanksOnly) {

		// the ranks a path is made of, and the letters they are written as: Domain..Species, Kingdom excluded
		// (TaxonomicLevels.PATH_RANK_IDS explains why), with Domain written 'K' by the Greengenes convention
		var expectedPath = TaxonomicLevels.getPathLetters();
		var expectedIndex = 0;

		final var v = taxonomyClassification.getFullTree().getANode(taxId);
		if (v != null) {
			var path = new LinkedList<Node>();
			{
				var w = v;
				while (true) {
					if (!majorRanksOnly || TaxonomicLevels.isPathRank(taxonomyClassification.getId2Rank().get((Integer) w.getInfo())))
						path.push(w);
					if (w.getInDegree() > 0)
						w = w.getFirstInEdge().getSource();
					else
						break;
				}
			}
			var buf = new StringBuilder();

			for (var w : path) {
				var id = (Integer) w.getInfo();
				if (id != null) {
					if (majorRanksOnly) {
						// the letter comes from the shared path definition, not from the rank's name: a database may
						// rename a rank (TaxonomicLevels.setFromDatabase), and Domain is written 'K' regardless
						var key = Character.toUpperCase(TaxonomicLevels.getPathLetter(taxonomyClassification.getId2Rank().get(id)));

						while (expectedIndex < expectedPath.length() && key != expectedPath.charAt(expectedIndex)) {
							if (!buf.isEmpty())
								buf.append(" ");
							buf.append("[").append(expectedPath.charAt(expectedIndex)).append("] unknown;");
							expectedIndex++;
						}
						expectedIndex++;

						if (!buf.isEmpty())
							buf.append(" ");
						buf.append("[").append(key).append("] ").append(taxonomyClassification.getName2IdMap().get(id)).append(";");
					} else {
						if (!buf.isEmpty())
							buf.append(" ");
						buf.append(taxonomyClassification.getName2IdMap().get(id)).append(";");
					}
				}
			}
			return buf.toString();
		} else
			return null;
	}

	/**
	 * gets the path, or id, if path not found
	 *
	 * @return path or id
	 */
	public static String getPathOrId(int taxId, boolean majorRanksOnly) {
		var path = getPath(taxId, majorRanksOnly);
		return path != null ? path : "" + taxId;
	}

	/**
	 * set total set of disabled nodes from a set of disabled nodes. This is used to set all disabled node from
	 * the representation saved in the preferrences file
	 */
	public static void setDisabledInternalTaxa(Set<Integer> internal) {
		var root = taxonomyClassification.getFullTree().getRoot();
		if (root != null)
			setDisabledInternalTaxaRec(root, internal, internal.contains((Integer) root.getInfo()));
	}

	/**
	 * recursively does the work
	 */
	private static void setDisabledInternalTaxaRec(final Node v, final Set<Integer> internalDisabledIds, boolean disable) {
		final var id = (int) v.getInfo();

		if (!disable && internalDisabledIds.contains(id))
			disable = true;

		if (disable)
			taxonomyClassification.getIdMapper().getDisabledIds().add(id);
		else
			taxonomyClassification.getIdMapper().getDisabledIds().remove(id);

		for (var e : v.outEdges()) {
			setDisabledInternalTaxaRec(e.getTarget(), internalDisabledIds, disable);
		}
	}

	/**
	 * gets the disabled internal nodes. This generates the set that is saved to the preferences file
	 *
	 * @return disabled internal nodes
	 */
	public static Set<Integer> getDisabledInternalTaxa() {
		var internalDisabledIds = new TreeSet<Integer>();
		var root = getTree().getRoot();
		if (root != null)
			getDisabledFromInternalTaxaRec(root, internalDisabledIds);
		return internalDisabledIds;
	}

	/**
	 * recursively does the work
	 */
	private static void getDisabledFromInternalTaxaRec(Node v, Set<Integer> internalDisabledIds) {
		var id = (int) v.getInfo();

		if (taxonomyClassification.getIdMapper().getDisabledIds().contains(id)) {
			internalDisabledIds.add(id);
		} else {
			for (var e : v.outEdges()) {
				getDisabledFromInternalTaxaRec(e.getTarget(), internalDisabledIds);
			}
		}
	}

	/**
	 * ensures that the disabled taxa have been initialized
	 */
	public static void ensureDisabledTaxaInitialized() {
		if (getDisabledTaxa().isEmpty() && !getDisabledInternalTaxa().isEmpty())
			TaxonomyData.setDisabledInternalTaxa(getDisabledInternalTaxa());
	}

	public static Collection<Integer> getNonProkaryotesToCollapse() {
		var root = taxonomyClassification.getFullTree().getRoot();
		var ids2collapse = new ArrayList<Integer>();
		for (var e : root.outEdges()) {
			var w = e.getTarget();
			var id = (Integer) w.getInfo();
			if (id != 131567 && id != 2 && id != 2157)// cellular organisms
				ids2collapse.add(id);
		}
		ids2collapse.add(2759); // eukaryotes
		return ids2collapse;
	}

	public static Collection<Integer> getNonEukaryotesToCollapse() {
		var root = taxonomyClassification.getFullTree().getRoot();
		var ids2collapse = new ArrayList<Integer>();
		for (var e : root.outEdges()) {
			var w = e.getTarget();
			var id = (Integer) w.getInfo();
			if (id != 131567)// cellular organisms
				ids2collapse.add(id);
		}
		ids2collapse.add(2); // bacteria
		ids2collapse.add(2157); // archaea
		return ids2collapse;
	}

	public static Collection<Integer> getNonVirusesToCollapse() {
		var root = taxonomyClassification.getFullTree().getRoot();
		var ids2collapse = new ArrayList<Integer>();
		for (var e : root.outEdges()) {
			var w = e.getTarget();
			var id = (Integer) w.getInfo();
			if (id != 10239 && id != 12884)// viruses  or viroids
				ids2collapse.add(id);
		}
		return ids2collapse;
	}
}
