/*
 * NewickIO.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package jloda.phylo;

import jloda.graph.Edge;
import jloda.graph.IllegalSelfEdgeException;
import jloda.graph.Node;
import jloda.graph.NodeIntArray;
import jloda.util.*;

import java.io.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * input and output of a tree or rooted network in extended rich Newick format
 * Daniel Huson, 2002-2023
 */
public class NewickIO {
	public static boolean WARN_HAS_MULTILABELS = true;
	public static final String COLLAPSED_NODE_SUFFIX = "{+}";

	public static boolean NUMBERS_ON_INTERNAL_NODES_ARE_CONFIDENCE_VALUES = true;
	public boolean allowMultiLabeledNodes = true;

	private final boolean cleanLabelsOnWrite;

	private boolean numbersOnInternalNodesAreConfidenceValues = NUMBERS_ON_INTERNAL_NODES_ARE_CONFIDENCE_VALUES;

	private boolean hideCollapsedSubTreeOnWrite = false;

	private int outputNodeNumber = 0;
	private int outputEdgeNumber = 0;
	private NodeIntArray outputNodeReticulationNumberMap;  // global number of the reticulate nodes
	private int outputReticulationNumber;

	private static final String punctuationCharacters = "),;:[";

	public NewickIO() {
		cleanLabelsOnWrite = ProgramProperties.get("cleanTreeLabelsOnWrite", false);
	}

	public String toBracketString(PhyloTree tree, boolean showWeights) {
		return toBracketString(tree, showWeights, null);
	}

	public String toBracketString(PhyloTree tree, OutputFormat format) {
		try (var sw = new StringWriter()) {
			write(tree, sw, format);
			return sw.toString();
		} catch (Exception ex) {
			Basic.caught(ex);
			return "();";
		}
	}

	public static String toString(PhyloTree tree, boolean showWeights) {
		return toString(tree, showWeights, false, false);
	}

	public static String toString(Collection<PhyloTree> trees, boolean showWeights) {
		var buf = new StringBuilder();
		for (var tree : trees) {
			buf.append(toString(tree, showWeights)).append(";\n");
		}
		return buf.toString();
	}

	public static String toString(PhyloTree tree, boolean showWeights, boolean showConfidences) {
		return toString(tree, showWeights, showConfidences, false);
	}

	public static String toString(PhyloTree tree, boolean showWeights, boolean showConfidences, boolean showProbabilities) {
		var format = new NewickIO.OutputFormat(showWeights, showConfidences, showConfidences, showProbabilities, false);
		return new NewickIO().toBracketString(tree, format);
	}

	/**
	 * Produces a string representation of the tree in bracket notation.
	 *
	 * @return a string representation of the tree in bracket notation
	 */
	public String toBracketString(PhyloTree tree, boolean showWeights, Map<String, String> translate) {
		try (var sw = new StringWriter()) {
			if (translate == null || translate.isEmpty()) {
				write(tree, sw, showWeights, false);
			} else {
				var tmpTree = new PhyloTree();
				tmpTree.copy(tree);
				for (var v : tmpTree.nodes()) {
					var key = tmpTree.getLabel(v);
					if (key != null) {
						var value = translate.get(key);
						if (value != null)
							tmpTree.setLabel(v, value);
					}
				}
				write(tmpTree, sw, showWeights, false);
			}
			return sw.toString();
		} catch (Exception ex) {
			Basic.caught(ex);
			return "()";
		}
	}

	/**
	 * writes a tree in bracket notation
	 */
	public void write(PhyloTree tree, Writer w, OutputFormat newickOutputFormat) throws IOException {
		write(tree, w, newickOutputFormat, null, null);
	}

	/**
	 * Writes a tree in bracket notation
	 *
	 * @param w           the writer
	 * @param showWeights write edge weights or not
	 */
	public void write(PhyloTree tree, Writer w, boolean showWeights, boolean writeEdgeLabelsAsComments) throws IOException {
		if (PhyloTree.SUPPORT_RICH_NEWICK) {
			write(tree, w, new OutputFormat(showWeights, false, tree.hasEdgeConfidences(), tree.hasEdgeProbabilities(), writeEdgeLabelsAsComments), null, null);
		} else {
			write(tree, w, new OutputFormat(showWeights, false, false, false, writeEdgeLabelsAsComments), null, null);
		}
	}

	/**
	 * writes a tree
	 */
	public void write(PhyloTree tree, Writer writer, final boolean showWeights, final Function<Node, String> labeler) throws IOException {
		if (labeler == null) {
			write(tree, writer, showWeights, false);
		} else {
			var tmpTree = new PhyloTree();
			tmpTree.copy(tree);
			for (var v : tmpTree.nodes()) {
				var label = labeler.apply(v);
				if (label != null) {
					label = label.replaceAll("'", "_");
					if (StringUtils.intersects(label, " :(),"))
						tmpTree.setLabel(v, "'" + label + "'");
					else
						tmpTree.setLabel(v, label);
				}
			}
			write(tmpTree, writer, showWeights, false);
		}
	}

	/**
	 * Writes a tree in bracket notation. Uses extended bracket notation to write reticulate network
	 *
	 * @param tree          the tree
	 * @param w             the writer
	 * @param nodeId2Number if non-null, will contain node-id to number mapping after call
	 * @param edgeId2Number if non-null, will contain edge-id to number mapping after call
	 */
	public void write(PhyloTree tree, Writer w, OutputFormat format, Map<Integer, Integer> nodeId2Number, Map<Integer, Integer> edgeId2Number) throws IOException {
		outputNodeNumber = 0;
		outputEdgeNumber = 0;

		if (getNewickLeadingCommentSupplier() != null) {
			w.write(String.format("[%s]", getNewickLeadingCommentSupplier()));
		}

		if (tree.hasReticulateEdges()) {
			// following two lines enable us to write reticulate networks in Newick format
			outputNodeReticulationNumberMap = tree.newNodeIntArray();
			outputReticulationNumber = 0;
		}

		if (tree.getNumberOfEdges() > 0) {
			var root = tree.getRoot();
			if (root == null) {
				root = tree.getFirstNode();
				for (var v = root; v != null; v = v.getNext()) {
					if (v.getDegree() > root.getDegree())
						root = v;
				}
			}
			if (root != null)
				writeRec(tree, w, root, null, format, nodeId2Number, edgeId2Number, getLabelForWriting(root));
		} else if (tree.getNumberOfNodes() == 1) {
			if (getNewickNodeCommentSupplier() != null && !getNewickNodeCommentSupplier().apply(tree.getFirstNode()).isBlank()) {
				var comment = getNewickNodeCommentSupplier().apply(tree.getFirstNode()).trim();
				w.write("(" + getLabelForWriting(tree.getFirstNode()) + "[" + comment + "]);");
			} else
				w.write("(" + getLabelForWriting(tree.getFirstNode()) + ");");
			if (nodeId2Number != null)
				nodeId2Number.put(tree.getFirstNode().getId(), 1);
		} else
			w.write("();");

		if (outputNodeReticulationNumberMap != null)
			outputNodeReticulationNumberMap.clear();
	}

	/**
	 * Recursively writes a tree in bracket notation
	 */
	private void writeRec(PhyloTree tree, Writer writer, Node v, Edge e, OutputFormat format, Map<Integer, Integer> nodeId2Number, Map<Integer, Integer> edgeId2Number, String nodeLabel) throws IOException {
		if (nodeId2Number != null)
			nodeId2Number.put(v.getId(), ++outputNodeNumber);

		if (!isHideCollapsedSubTreeOnWrite() || tree.getLabel(v) == null || !tree.getLabel(v).endsWith(COLLAPSED_NODE_SUFFIX)) {
			if (v.getOutDegree() > 0) {
				writer.write("(");
				boolean first = true;
				for (Edge f : v.outEdges()) {
					if (edgeId2Number != null)
						edgeId2Number.put(f.getId(), ++outputEdgeNumber);

					if (first)
						first = false;
					else
						writer.write(",");

					final Node w = f.getTarget();

					if (tree.isReticulateEdge(f)) {
						boolean isAcceptorEdge = tree.isTransferAcceptorEdge(f);

						if (outputNodeReticulationNumberMap.get(w) == null) {
							outputNodeReticulationNumberMap.set(w, ++outputReticulationNumber);
							final String label;
							if (tree.getLabel(w) != null)
								label = getLabelForWriting(w) + PhyloTreeNetworkIOUtils.makeReticulateNodeLabel(isAcceptorEdge, outputNodeReticulationNumberMap.get(w));
							else
								label = PhyloTreeNetworkIOUtils.makeReticulateNodeLabel(isAcceptorEdge, outputNodeReticulationNumberMap.get(w));
							writeRec(tree, writer, w, f, format, nodeId2Number, edgeId2Number, label);
						} else {
							String label;
							if (tree.getLabel(w) != null)
								label = getLabelForWriting(w) + PhyloTreeNetworkIOUtils.makeReticulateNodeLabel(isAcceptorEdge, outputNodeReticulationNumberMap.get(w));
							else
								label = PhyloTreeNetworkIOUtils.makeReticulateNodeLabel(isAcceptorEdge, outputNodeReticulationNumberMap.get(w));

							writer.write(label);
							writer.write(getEdgeString(tree, format, f));
							if (getNewickNodeCommentSupplier() != null) {
								var comment = getNewickNodeCommentSupplier().apply(w);
								if (comment != null && !comment.trim().isEmpty()) {
									writer.write("[" + comment.trim() + "]");
								}
							}
						}
					} else
						writeRec(tree, writer, w, f, format, nodeId2Number, edgeId2Number, getLabelForWriting(w));
				}
				writer.write(")");
			}
			if (nodeLabel != null && !nodeLabel.isEmpty()) {
				writer.write(nodeLabel);
			}
		}
		if (e != null) {
			writer.write(getEdgeString(tree, format, e));
		}
		if (getNewickNodeCommentSupplier() != null) {
			var comment = getNewickNodeCommentSupplier().apply(v);
			if (comment != null && !comment.trim().isEmpty()) {
				writer.write("[" + comment.trim() + "]");
			}
		}
	}

	public String getEdgeString(PhyloTree tree, OutputFormat format, Edge e) {
		var buf = new StringBuilder();
		var colons = 0;
		if (format.weights() && tree.getWeight(e) != -1.0) {
			if (tree.getEdgeWeights().containsKey(e)) {
				buf.append(":").append(StringUtils.removeTrailingZerosAfterDot(String.format(format.weightFormat(), tree.getWeight(e))));
				colons++;
			}
		}
		if (format.confidenceUsingColon() && tree.hasEdgeConfidences() && tree.getEdgeConfidences().containsKey(e)) {
			while (colons < 2) {
				buf.append(":");
				colons++;
			}
			buf.append(StringUtils.removeTrailingZerosAfterDot(String.format(format.confidenceFormat(), tree.getConfidence(e))));
		}
		if (format.probabilityUsingColon() && tree.hasEdgeProbabilities() && tree.getEdgeProbabilities().containsKey(e)) {
			while (colons < 3) {
				buf.append(":");
				colons++;
			}
			buf.append(StringUtils.removeTrailingZerosAfterDot(String.format(format.probabilityFormat(), tree.getProbability(e))));
		}
		if (format.edgeLabelsAsComments() && tree.getLabel(e) != null) {
			buf.append("[").append(getLabelForWriting(e)).append("]");
		}
		return buf.toString();
	}

	/**
	 * get the label to be used for writing. Will have single quotes, if label contains punctuation character or white space
	 */
	private String getLabelForWriting(Node v) {
		var tree = (PhyloTree) v.getOwner();
		var label = cleanLabelsOnWrite ? StringUtils.getCleanLabelForNewick(tree.getLabel(v)) : tree.getLabel(v);
		if (label != null) {
			for (int i = 0; i < label.length(); i++) {
				if (punctuationCharacters.indexOf(label.charAt(i)) != -1 || Character.isWhitespace(label.charAt(i)))
					return "'" + label + "'";
			}
		}
		return label;
	}

	/**
	 * get the label to be used for writing. Will have single quotes, if label contains punctuation character or white space
	 */
	private String getLabelForWriting(Edge e) {
		var tree = (PhyloTree) e.getOwner();
		var label = cleanLabelsOnWrite ? StringUtils.getCleanLabelForNewick(tree.getLabel(e)) : tree.getLabel(e);
		if (label != null) {
			for (int i = 0; i < label.length(); i++) {
				if (punctuationCharacters.indexOf(label.charAt(i)) != -1 || Character.isWhitespace(label.charAt(i)))
					return "'" + label + "'";
			}
		}
		return label;
	}

	/**
	 * Given a string representation of a tree, returns the tree.
	 *
	 * @param str String
	 * @return tree PhyloTree
	 */
	static public PhyloTree valueOf(String str) throws IOException {
		var tree = new PhyloTree();
		tree.parseBracketNotation(str, true);
		return tree;
	}

	/**
	 * reads a line and then parses it as a rooted tree or network in Newick format
	 *
	 * @param r the reader
	 */
	public void read(PhyloTree tree, Reader r) throws IOException {
		final BufferedReader br;
		if (r instanceof BufferedReader)
			br = (BufferedReader) r;
		else
			br = new BufferedReader(r);
		parseBracketNotation(tree, br.readLine(), true, true);
	}

	/**
	 * Parses a tree or network in Newick notation, and sets the root, if desired
	 */
	public void parseBracketNotation(PhyloTree tree, String str, boolean rooted) throws IOException {
		parseBracketNotation(tree, str, rooted, true);
	}

	/**
	 * Parses a tree or network in Newick notation, and sets the root, if desired
	 */
	public void parseBracketNotation(PhyloTree tree, String str, boolean rooted, Consumer<String> newickLeadingCommentConsumer, BiConsumer<Node, String> newickNodeCommentConsumer) throws IOException {
		setNewickLeadingCommentConsumer(newickLeadingCommentConsumer);
		setNewickNodeCommentConsumer(newickNodeCommentConsumer);
		parseBracketNotation(tree, str, rooted, true);
	}

	private boolean inputHasMultiLabels = false;

	/**
	 * Parses a tree or network in Newick notation, and sets the root, if desired
	 */
	public void parseBracketNotation(PhyloTree tree, String str, boolean rooted, boolean doClear) throws IOException {
		if (doClear)
			tree.clear();
		inputHasMultiLabels = false;

		str = str.trim();
		{
			while (str.startsWith("[")) {
				var next = str.indexOf("[", 1);
				var pos = str.indexOf("]", 1);
				if (pos == -1 || (next != -1 && next < pos))
					throw new IOException("Leading comment not properly terminated");
				if (getNewickLeadingCommentConsumer() != null)
					getNewickLeadingCommentConsumer().accept(str.substring(1, pos));
				str = str.substring(pos + 1).trim();
			}
		}

		var seen = new HashMap<String, Node>();

		try {
			parseBracketNotationRec(tree, seen, 0, null, 0, str);
		} catch (IOException ex) {
			//System.err.println(str);
			throw ex;
		}
		if (tree.getNumberOfNodes() > 0) {
			final var v = tree.getFirstNode();
			if (rooted) {
				tree.setRoot(v);
				/*
				if (!hasEdgeWeights() && isUnlabeledDiVertex(v)) {
					setWeight(v.getFirstAdjacentEdge(), 0.5);
					setWeight(v.getLastAdjacentEdge(), 0.5);
				}
				 */
			} else {
				if (tree.isUnlabeledDiVertex(v))
					tree.setRoot(tree.delDivertex(v).getSource());
				else tree.setRoot(v);
			}
		}

		// post process any reticulate nodes
		postProcessReticulate(tree);

		// if all internal nodes are labeled with numbers, then these are interpreted as confidence values and put on the edges
		// In dendroscope and splitstree5, these are left on the nodes
		if (PhyloTree.SUPPORT_RICH_NEWICK) {
			if (tree.nodeStream().filter(v -> !v.isLeaf() && v.getInDegree() == 1).allMatch(v -> NumberUtils.isDouble(tree.getLabel(v)))) {
				tree.nodeStream().filter(v -> !v.isLeaf() && v.getInDegree() == 1)
						.forEach(v -> {
							tree.setConfidence(v.getFirstInEdge(), NumberUtils.parseDouble(tree.getLabel(v)));
							tree.setLabel(v, null);
						});
				var maxValue = tree.edgeStream().filter(e -> !e.getTarget().isLeaf()).mapToDouble(tree::getConfidence).max();
				if (maxValue.isPresent()) {
					double leafValue = (maxValue.getAsDouble() > 1 && maxValue.getAsDouble() <= 100 ? 100 : 1);
					tree.nodeStream().filter(v -> v.isLeaf() && v.getInDegree() == 1)
							.forEach(v -> tree.setConfidence(v.getFirstInEdge(), leafValue));
				}
			}
		}

		// System.err.println("Multi-labeled nodes detected: " + isInputHasMultiLabels());

		if (false) {
			System.err.println("has reticulate edges: " + (tree.hasReticulateEdges() ? tree.getReticulateEdges().size() : 0));
			System.err.println("has acceptor edges: " + (tree.hasTransferAcceptorEdges() ? tree.getTransferAcceptorEdges().size() : 0));

			System.err.println("has edge weights: " + (tree.hasReticulateEdges() ? tree.getReticulateEdges().size() : 0));
			System.err.println("has edge confidences: " + (tree.hasEdgeConfidences() ? tree.getEdgeConfidences().size() : 0));
			System.err.println("has edge probabilities: " + (tree.hasEdgeProbabilities() ? tree.getEdgeProbabilities().size() : 0));
			System.err.println(toBracketString(tree, new OutputFormat(true, true, true, true, true)));
		}
	}


	/**
	 * recursively do the work
	 *
	 * @param seen  set of seen labels
	 * @param depth distance from root
	 * @param v     parent node
	 * @param pos   current position in string
	 * @param str   string
	 * @return new current position
	 */
	private int parseBracketNotationRec(PhyloTree tree, Map<String, Node> seen, int depth, Node v, int pos, String str) throws IOException {
		for (pos = StringUtils.skipSpaces(str, pos); pos < str.length(); pos = StringUtils.skipSpaces(str, pos + 1)) {
			var w = tree.newNode();
			String label = null;
			var confidenceValue = new Single<Double>();

			if (str.charAt(pos) == '(') {
				pos = parseBracketNotationRec(tree, seen, depth + 1, w, pos + 1, str);
				if (str.charAt(pos) != ')')
					throw new IOException("Expected ')' at position " + pos);
				pos = StringUtils.skipSpaces(str, pos + 1);
				while (pos < str.length() && punctuationCharacters.indexOf(str.charAt(pos)) == -1) {
					var pos0 = pos;
					var buf = new StringBuilder();
					var inQuotes = false;
					while (pos < str.length() && (inQuotes || punctuationCharacters.indexOf(str.charAt(pos)) == -1)) {
						if (str.charAt(pos) == '\'')
							inQuotes = !inQuotes;
						else
							buf.append(str.charAt(pos));
						pos++;
					}
					label = buf.toString().trim();

					if (!label.isEmpty()) {
						if (isNumbersOnInternalNodesAreConfidenceValues() && NumberUtils.isDouble(label)) {
							confidenceValue.set(NumberUtils.parseDouble(label));
						} else {
							if (!isAllowMultiLabeledNodes() && seen.containsKey(label) && PhyloTreeNetworkIOUtils.findReticulateLabel(label) == null)
							// if label already used, make unique, unless this is a reticulate node
							{
								if (label.startsWith("'") && label.endsWith("'") && label.length() > 1)
									label = label.substring(1, label.length() - 1);
								// give first occurrence of this label the suffix .1
								final Node old = seen.get(label);
								if (old != null) // change label of node
								{
									tree.setLabel(old, label + ".1");
									seen.put(label, null); // keep label in, but null indicates has changed
									seen.put(label + ".1", old);
									inputHasMultiLabels = true;
								}

								var t = 1;
								String labelt;
								do {
									labelt = label + "." + (++t);
								} while (seen.containsKey(labelt));
								label = labelt;
							}
							seen.put(label, w);
						}
					}
					tree.setLabel(w, label);
					if (label.isEmpty())
						throw new IOException("Expected label at position " + pos0);
				}
			} else // everything to next ) : or , is considered a label:
			{
				if (tree.getNumberOfNodes() == 1)
					throw new IOException("Expected '(' at position " + pos);
				var pos0 = pos;
				final var buf = new StringBuilder();
				boolean inQuotes = false;
				while (pos < str.length() && (inQuotes || punctuationCharacters.indexOf(str.charAt(pos)) == -1)) {
					if (str.charAt(pos) == '\'')
						inQuotes = !inQuotes;
					else
						buf.append(str.charAt(pos));
					pos++;
				}
				label = buf.toString().trim();

				if (label.startsWith("'") && label.endsWith("'") && label.length() > 1)
					label = label.substring(1, label.length() - 1).trim();

				if (!label.isEmpty()) {
					if (!isAllowMultiLabeledNodes() && seen.containsKey(label) && PhyloTreeNetworkIOUtils.findReticulateLabel(label) == null) {
						// give first occurrence of this label the suffix .1
						var old = seen.get(label);
						if (old != null) // change label of node
						{
							tree.setLabel(old, label + ".1");
							seen.put(label, null); // keep label in, but null indicates has changed
							seen.put(label + ".1", old);
							inputHasMultiLabels = true;
							if (WARN_HAS_MULTILABELS)
								System.err.println("multi-label: " + label);
						}

						var t = 1;
						String labelt;
						do {
							labelt = label + "." + (++t);
						} while (seen.containsKey(labelt));
						label = labelt;
					}
					seen.put(label, w);
				}
				tree.setLabel(w, label);
				if (label.isEmpty())
					throw new IOException("Expected label at position " + pos0);
			}
			Edge e = null;
			if (v != null)
				e = tree.newEdge(v, w);

			if (confidenceValue.isNotNull())
				tree.setConfidence(e, confidenceValue.get());

			// detect and read embedded bootstrap values:
			pos = StringUtils.skipSpaces(str, pos);

			// read edge weights
			var didReadWeight = false;

			for (var which = 0; which < 3; which++) {
				if (pos < str.length() && str.charAt(pos) == ':') { // edge weight is following
					pos = StringUtils.skipSpaces(str, pos + 1);
					if (pos < str.length() && str.charAt(pos) == ':') {
						continue;
					}
					var pos0 = pos;
					var numberStr = StringUtils.getStringUptoDelimiter(str, pos0, punctuationCharacters);
					if (!NumberUtils.isDouble(numberStr))
						throw new IOException("Expected number at position " + pos0 + " (got: '" + numberStr + "')");
					pos = pos0 + numberStr.length();
					var value = Math.max(0, Double.parseDouble(numberStr));
					switch (which) {
						case 0 -> {
							if (e != null) {
								tree.setWeight(e, value);
								didReadWeight = true;
							}
						}
						case 1 -> {
							if (e != null) {
								tree.setConfidence(e, value);
							}
						}
						case 2 -> {
							if (e != null) {
								tree.setProbability(e, value);
							}
						}
					}
				}
				if (!PhyloTree.SUPPORT_RICH_NEWICK)
					break; // don't allow confidence or probability
			}

			// adjust edge weights for reticulate edges
			if (e != null) {
				if (PhyloTree.SUPPORT_RICH_NEWICK) {
					if (label != null && PhyloTreeNetworkIOUtils.isReticulateNode(label)) {
						if (PhyloTreeNetworkIOUtils.isReticulateAcceptorEdge(label)) {
							tree.setTransferAcceptor(e, true);
						} else {
							tree.setReticulate(e, true);
						}
					}
				} else {
					try {
						if (label != null && PhyloTreeNetworkIOUtils.isReticulateNode(label)) {
							// if an instance of a reticulate node is marked ##, then we will set the weight of the edge to the node to a number >0
							// to indicate that edge should be drawn as a tree edge
							if (PhyloTreeNetworkIOUtils.isReticulateAcceptorEdge(label)) {
								if (!didReadWeight || tree.getWeight(e) <= 0) {
									tree.setWeight(e, 0.000001);
								}
							} else {
								if (tree.getWeight(e) > 0)
									tree.setWeight(e, 0.0);
							}
						}
					} catch (IllegalSelfEdgeException e1) {
						Basic.caught(e1);
					}
				}
			}

			// now i should be pointing to a ',', a ')' or '[' (for a label)
			if (pos >= str.length()) {
				if (depth == 0)
					return pos; // finished parsing tree
				else
					throw new IOException("Unexpected end of line");
			}
			if (str.charAt(pos) == '[') // edge label
			{
				int x = str.indexOf('[', pos + 1);
				int j = str.indexOf(']', pos + 1);
				if (j == -1 || (x != -1 && x < j))
					throw new IOException("Error in edge label at position: " + pos);
				if (getNewickNodeCommentConsumer() != null)
					getNewickNodeCommentConsumer().accept(v, str.substring(pos + 1, j));
				pos = j + 1;
			}
			if (str.charAt(pos) == ';' && depth == 0)

				return pos; // finished parsing tree
			else if (str.charAt(pos) == ')')
				return pos;
			else if (str.charAt(pos) != ',')
				throw new IOException("Unexpected '" + str.charAt(pos) + "' at position " + pos);
		}
		return -1;
	}

	/**
	 * post processes a tree that really describes a reticulate network
	 */
	private void postProcessReticulate(PhyloTree tree) throws IOException {
		// determine all the groups of reticulate nodes
		final var reticulateNumber2Nodes = new HashMap<String, List<Node>>(); // maps each reticulate-node prefix to the set of all nodes that have it

		for (var v : tree.nodes()) {
			var label = tree.getLabel(v);
			if (label != null && !label.isEmpty()) {
				var reticulateLabel = PhyloTreeNetworkIOUtils.findReticulateLabel(label);
				if (reticulateLabel != null) {
					tree.setLabel(v, PhyloTreeNetworkIOUtils.removeReticulateNodeSuffix(label));
					var list = reticulateNumber2Nodes.computeIfAbsent(reticulateLabel, k -> new ArrayList<>());
					list.add(v);
				}
			}
		}

		// collapse all instances of a reticulate node into one node
		for (var reticulateNumber : reticulateNumber2Nodes.keySet()) {
			final var list = reticulateNumber2Nodes.get(reticulateNumber);
			if (list.size() == 1)
				throw new IOException("Unmatched reticulate node: " + reticulateNumber);
			else if (list.size() > 1) {
				Node u = null;

				for (var v : list) {
					if (u == null) {
						u = v;
					} else {
						if (tree.getLabel(v) != null) {
							if (tree.getLabel(u) == null)
								tree.setLabel(u, tree.getLabel(v));
							else if (!tree.getLabel(u).equals(tree.getLabel(v)))
								tree.setLabel(u, tree.getLabel(u) + "," + tree.getLabel(v));
						}

						for (var e : v.adjacentEdges()) {
							final Edge f;
							if (e.getSource() == v) { /// attach child of v below u
								f = tree.newEdge(u, e.getTarget());
							} else { // attach parent of v above u
								f = tree.newEdge(e.getSource(), u);
							}
							if (tree.hasEdgeWeights() && tree.getEdgeWeights().containsKey(e))
								tree.setWeight(f, tree.getWeight(e));
							if (tree.hasEdgeConfidences() && tree.getEdgeConfidences().containsKey(e))
								tree.setConfidence(f, tree.getConfidence(e));
							if (tree.hasEdgeProbabilities() && tree.getEdgeProbabilities().containsKey(e))
								tree.setProbability(f, tree.getProbability(e));
							if (tree.isTransferAcceptorEdge(e)) {
								tree.setTransferAcceptor(f, true);
							}
							if (tree.isReticulateEdge(e))
								tree.setReticulate(f, true);
							tree.setLabel(f, tree.getLabel(e));
						}
						tree.deleteNode(v);
					}
				}

				if (!PhyloTree.SUPPORT_RICH_NEWICK) {
					var transferAcceptorEdge = new Single<Edge>();
					for (var e : u.inEdges()) {
						tree.setReticulate(e, true);
						if (tree.getWeight(e) > 0) {
							if (transferAcceptorEdge.isNull())
								transferAcceptorEdge.set(e);
							else {
								tree.setWeight(e, 0.0);
								System.err.println("Warning: node has more than one transfer-acceptor edge, will only use first");
							}
						}
					}
					if (transferAcceptorEdge.isNotNull()) {
						u.inEdgesStream(false).filter(e -> e != transferAcceptorEdge.get()).forEach(e -> tree.setWeight(e, -1.0));
					}
				}
			}
		}
	}

	/**
	 * hide collapsed subtrees on write?
	 *
	 * @return true, if hidden
	 */
	public boolean isHideCollapsedSubTreeOnWrite() {
		return hideCollapsedSubTreeOnWrite;
	}

	/**
	 * hide collapsed subtrees on write?
	 */
	public void setHideCollapsedSubTreeOnWrite(boolean hideCollapsedSubTreeOnWrite) {
		this.hideCollapsedSubTreeOnWrite = hideCollapsedSubTreeOnWrite;
	}

	public boolean isAllowMultiLabeledNodes() {
		return allowMultiLabeledNodes;
	}

	public void setAllowMultiLabeledNodes(boolean allowMultiLabeledNodes) {
		this.allowMultiLabeledNodes = allowMultiLabeledNodes;
	}

	public boolean isCleanLabelsOnWrite() {
		return cleanLabelsOnWrite;
	}

	public boolean isInputHasMultiLabels() {
		return inputHasMultiLabels;
	}

	private Consumer<String> newickLeadingCommentConsumer;
	private BiConsumer<Node, String> newickNodeCommentConsumer;
	private Supplier<String> newickLeadingCommentSupplier;
	private Function<Node, String> newickNodeCommentSupplier;

	public Consumer<String> getNewickLeadingCommentConsumer() {
		return newickLeadingCommentConsumer;
	}

	public void setNewickLeadingCommentConsumer(Consumer<String> newickLeadingCommentConsumer) {
		this.newickLeadingCommentConsumer = newickLeadingCommentConsumer;
	}

	public BiConsumer<Node, String> getNewickNodeCommentConsumer() {
		return newickNodeCommentConsumer;
	}

	public void setNewickNodeCommentConsumer(BiConsumer<Node, String> newickNodeCommentConsumer) {
		this.newickNodeCommentConsumer = newickNodeCommentConsumer;
	}

	public Supplier<String> getNewickLeadingCommentSupplier() {
		return newickLeadingCommentSupplier;
	}

	public void setNewickLeadingCommentSupplier(Supplier<String> newickLeadingCommentSupplier) {
		this.newickLeadingCommentSupplier = newickLeadingCommentSupplier;
	}

	public Function<Node, String> getNewickNodeCommentSupplier() {
		return newickNodeCommentSupplier;
	}

	public void setNewickNodeCommentSupplier(Function<Node, String> newickNodeCommentSupplier) {
		this.newickNodeCommentSupplier = newickNodeCommentSupplier;
	}

	public boolean isNumbersOnInternalNodesAreConfidenceValues() {
		return numbersOnInternalNodesAreConfidenceValues;
	}

	public void setNumbersOnInternalNodesAreConfidenceValues(boolean numbersOnInternalNodesAreConfidenceValues) {
		this.numbersOnInternalNodesAreConfidenceValues = numbersOnInternalNodesAreConfidenceValues;
	}

	public static class OutputFormat {
		private boolean weights;

		private String weightFormat = "%.8f";
		private boolean confidenceAsNodeLabel;
		private boolean confidenceUsingColon;

		private String confidenceFormat = "%.8f";

		private boolean probabilityUsingColon;

		private String probabilityFormat = "%.8f";

		private boolean edgeLabelsAsComments;

		public OutputFormat(boolean weights, boolean confidenceAsNodeLabel, boolean confidenceUsingColon,
							boolean probabilityUsingColon, boolean edgeLabelsAsComments) {
			this.weights = weights;
			this.confidenceAsNodeLabel = confidenceAsNodeLabel;
			this.confidenceUsingColon = confidenceUsingColon;
			this.probabilityUsingColon = probabilityUsingColon;
			this.edgeLabelsAsComments = edgeLabelsAsComments;
		}

		public boolean weights() {
			return weights;
		}

		public boolean confidenceAsNodeLabel() {
			return confidenceAsNodeLabel;
		}

		public boolean confidenceUsingColon() {
			return confidenceUsingColon;
		}

		public boolean probabilityUsingColon() {
			return probabilityUsingColon;
		}

		public boolean edgeLabelsAsComments() {
			return edgeLabelsAsComments;
		}

		public void setWeights(boolean weights) {
			this.weights = weights;
		}

		public void setConfidenceAsNodeLabel(boolean confidenceAsNodeLabel) {
			this.confidenceAsNodeLabel = confidenceAsNodeLabel;
		}

		public void setConfidenceUsingColon(boolean confidenceUsingColon) {
			this.confidenceUsingColon = confidenceUsingColon;
		}

		public void setProbabilityUsingColon(boolean probabilityUsingColon) {
			this.probabilityUsingColon = probabilityUsingColon;
		}

		public void setEdgeLabelsAsComments(boolean edgeLabelsAsComments) {
			this.edgeLabelsAsComments = edgeLabelsAsComments;
		}

		public String weightFormat() {
			return weightFormat;
		}

		public void setWeightFormat(String weightFormat) {
			this.weightFormat = weightFormat;
		}

		public String confidenceFormat() {
			return confidenceFormat;
		}

		public void setConfidenceFormat(String confidenceFormat) {
			this.confidenceFormat = confidenceFormat;
		}

		public String probabilityFormat() {
			return probabilityFormat;
		}

		public void setProbabilityFormat(String probabilityFormat) {
			this.probabilityFormat = probabilityFormat;
		}
	}

	public static void main(String[] args) throws IOException {
		var newick = "(a40:0.0899376429,((a47:0.1357332613,((tei_1:0.0000021732,tei_2:0.0000021732)100:0.1172693229,uk6:0.0808904571)73:0.0221078288)62:0.0179176982,(((bal:0.1407598263,(dec_1:0.1259715854,(dec_2:0.0642686547,van:0.0607203380)100:0.0606318912)93:0.0321244472)100:0.0887287403,((ker:0.0077780118,nog:0.0025525455)100:0.1687566009,(ris:0.0522666677,risA:0.0548213374)100:0.1431814732)70:0.0279369285)73:0.0183299149,(((cor:1.0478535960,gp6:0.7479808257)99:0.2441475379,isocom:0.3338813419)66:0.0959063961,(mis:0.3248559905,rim:0.3091183550)85:0.1240669481)100:0.2858317671)81:0.0321174501)100:0.0997399456,a50:0.0588594162);";

		var newickIO = new NewickIO();
		newickIO.setAllowMultiLabeledNodes(false);
		var tree = new PhyloTree();
		newickIO.setNewickNodeCommentConsumer((v, c) -> {
			if (c.startsWith("&&NHX:GN="))
				tree.setName(c.substring(c.indexOf("=") + 1));
		});
		newickIO.parseBracketNotation(tree, newick, true, true);
		System.err.println("isInputHasMultiLabels: " + newickIO.isInputHasMultiLabels());
		System.err.println("hasEdgeWeights: " + tree.hasEdgeWeights());
		System.err.println("hasEdgeConfidences: " + tree.hasEdgeConfidences());
		System.err.println(tree.getName());
		System.err.println(newickIO.toBracketString(tree, true));
	}
}
