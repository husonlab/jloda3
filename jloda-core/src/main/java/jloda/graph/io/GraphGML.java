/*
 * GraphGML.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.graph.io;

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.StringUtils;
import jloda.util.TriConsumer;
import jloda.util.parse.NexusStreamParser;

import java.io.*;
import java.util.*;
import java.util.function.BiFunction;

import static java.io.StreamTokenizer.TT_EOF;

/**
 * read and write a graph in GML format
 * Daniel Huson, 2020
 */
public class GraphGML {
	/**
	 * write a graph in GraphGML
	 *
	 * @param graph             the graph
	 * @param w                 the writer
	 * @param labelNodeValueMap for given labels, provides a node value maps
	 * @param labelEdgeValueMap for given labels, provides node value maps
	 */
	public static void writeGML(Graph graph, String comment, String graphLabel, boolean directed, int graphId, Writer w,
								Map<String, Map<Node, String>> labelNodeValueMap, Map<String, Map<Edge, String>> labelEdgeValueMap) throws IOException {
		var nodeLabelNames = (labelNodeValueMap == null ? null : labelNodeValueMap.keySet());
		BiFunction<String, Node, String> labelNodeValueFunction = (labelNodeValueMap == null ? null : (n, v) -> {
			var map = labelNodeValueMap.get(n);
			return map == null ? null : map.get(v);
		});
		var edgeLabelNames = (labelEdgeValueMap == null ? null : labelEdgeValueMap.keySet());
		BiFunction<String, Edge, String> labelEdgeValueFunction = (labelEdgeValueMap == null ? null : (n, e) -> {
			var map = labelEdgeValueMap.get(n);
			return map == null ? null : map.get(e);
		});
		writeGML(graph, comment, graphLabel, directed, graphId, w, nodeLabelNames, labelNodeValueFunction, edgeLabelNames, labelEdgeValueFunction);
	}

	/**
	 * write a graph in GraphGML
	 *
	 * @param graph                  the graph
	 * @param w                      the writer
	 * @param labelNodeValueFunction for given labels, provides a node value maps
	 * @param labelEdgeValueFunction for given labels, provides node value maps
	 */
	public static void writeGML(Graph graph, String comment, String graphLabel, boolean directed, int graphId, Writer w,
								Collection<String> nodeLabelNames, BiFunction<String, Node, String> labelNodeValueFunction,
								Collection<String> edgeLabelNames, BiFunction<String, Edge, String> labelEdgeValueFunction) throws IOException {
		var gmlInfo = new GMLInfo(comment, directed, graphId, graphLabel);
		w.write("graph [\n");
		if (gmlInfo.comment() != null)
			w.write("\tcomment " + asQuotedString(gmlInfo.comment()) + "\n");
		w.write("\tdirected " + (gmlInfo.directed() ? 1 : 0) + "\n");
		w.write("\tid " + gmlInfo.id() + "\n");
		if (gmlInfo.label() != null)
			w.write("\tlabel " + asQuotedString(gmlInfo.label()) + "\n");

		for (var v : graph.nodes()) {
			w.write("\tnode [\n");
			w.write("\t\tid " + v.getId() + "\n");
			if (nodeLabelNames != null && labelNodeValueFunction != null) {
				for (var name : nodeLabelNames) {
					var value = labelNodeValueFunction.apply(name, v);
					if (value != null)
						w.write("\t\t" + name + " " + asQuotedString(value) + "\n");
				}
			}
			w.write("\t]\n");
		}

		for (var e : graph.edges()) {
			w.write("\tedge [\n");
			w.write("\t\tsource " + e.getSource().getId() + "\n");
			w.write("\t\ttarget " + e.getTarget().getId() + "\n");
			if (edgeLabelNames != null && labelNodeValueFunction != null) {
				for (var name : edgeLabelNames) {
					var value = labelEdgeValueFunction.apply(name, e);
					if (value != null)
						w.write("\t\t" + name + " " + asQuotedString(value) + "\n");
				}
			}
			w.write("\t]\n");
		}
		w.write("]\n");
		w.flush();
	}

	/**
	 * read a graph in GraphGML for that was previously saved using writeGML. This is not a general parser.
	 *
	 * @param r                 the reader
	 * @param graph             the graph to write to
	 * @param labelNodeValueMap a label-node-value map
	 * @param labelEdgeValueMap a label-edge-value map
	 * @return a GML info item
	 * @throws IOException if parsing fails
	 */
	public static GMLInfo readGML(Reader r, Graph graph, Map<String, Map<Node, String>> labelNodeValueMap, Map<String, Map<Edge, String>> labelEdgeValueMap) throws IOException {
		TriConsumer<String, Node, String> labelNodeValueConsumer = (label, node, value) -> {
			if (labelNodeValueMap != null)
				labelNodeValueMap.computeIfAbsent(label, k -> new HashMap<>()).put(node, value);
		};
		TriConsumer<String, Edge, String> labelEdgeValueConsumer = (label, edge, value) -> {
			if (labelEdgeValueMap != null)
				labelEdgeValueMap.computeIfAbsent(label, k -> new HashMap<>()).put(edge, value);
		};
		return readGML(r, graph, labelNodeValueConsumer, labelEdgeValueConsumer);
	}

	/**
	 * read a graph in GraphGML for that was previously saved using writeGML. This is not a general parser.
	 *
	 * @param r                      the reader
	 * @param graph                  the graph to write to
	 * @param labelNodeValueConsumer a label-node-value consumer
	 * @param labelEdgeValueConsumer a label-edge-value consumer
	 * @return a GML info item
	 * @throws IOException if parsing fails
	 */
	public static GMLInfo readGML(Reader r, Graph graph, TriConsumer<String, Node, String> labelNodeValueConsumer, TriConsumer<String, Edge, String> labelEdgeValueConsumer) throws IOException {
		if (labelNodeValueConsumer == null) {
			labelNodeValueConsumer = (s, node, s2) -> {
			};
		}
		if (labelEdgeValueConsumer == null) {
			labelEdgeValueConsumer = (s, edge, s2) -> {
			};
		}

		final var np = new NexusStreamParser(r);
		np.setSyntaxNoQuote();
		np.setSquareBracketsSurroundComments(false);
		np.setPunctuationCharacters("(),;:=\"{}`");

		graph.clear();

		np.matchIgnoreCase("graph [");
		String graphComment;
		if (np.peekMatchIgnoreCase("comment")) {
			np.matchIgnoreCase("comment");
			graphComment = asUnquotedString(np);
		} else
			graphComment = null;

		boolean graphDirected;
		if (np.peekMatchIgnoreCase("directed")) {
			np.matchIgnoreCase("directed");
			graphDirected = (np.getInt(0, 1) == 1);
		} else
			graphDirected = false;

		np.matchIgnoreCase("id");
		var graphId = np.getInt();

		String graphLabel;

		if (np.peekMatchIgnoreCase("label")) {
			np.matchIgnoreCase("label");
			graphLabel = asUnquotedString(np);
		} else
			graphLabel = null;

		var id2node = new HashMap<Integer, Node>();
		while (np.peekMatchIgnoreCase("node")) {
			np.matchIgnoreCase("node [");
			np.matchIgnoreCase("id");
			var id = np.getInt();
			var v = graph.newNode(null);
			id2node.put(id, v);
			while (!np.peekMatchIgnoreCase("]")) {
				var label = np.getLabelRespectCase();
				var value = asUnquotedString(np);
				labelNodeValueConsumer.accept(label, v, value);
			}
			np.matchIgnoreCase("]");
		}
		while (np.peekMatchIgnoreCase("edge")) {
			np.matchIgnoreCase("edge [");
			np.matchIgnoreCase("source");
			var sourceId = np.getInt();
			np.matchIgnoreCase("target");
			int targetId = np.getInt();
			if (!id2node.containsKey(sourceId))
				throw new IOException("Undefined node id: " + sourceId);
			if (!id2node.containsKey(targetId))
				throw new IOException("Undefined node id: " + targetId);
			var e = graph.newEdge(id2node.get(sourceId), id2node.get(targetId), null);
			while (!np.peekMatchIgnoreCase("]")) {
				var label = np.getLabelRespectCase();
				var value = asUnquotedString(np);
				labelEdgeValueConsumer.accept(label, e, value);
			}
			np.matchIgnoreCase("]");
		}
		np.matchIgnoreCase("]");
		return new GMLInfo(graphComment, graphDirected, graphId, graphLabel);
	}

	public static String asQuotedString(String text) {
		if (text.contains("\""))
			return "`" + text + "`";
		else return "\"" + text + "\"";
	}

	public static String asUnquotedString(NexusStreamParser np) throws IOExceptionWithLineNumber {
		if (np.peekMatchIgnoreCase("`")) {
			np.setPunctuationCharacters("(),;:={}`");
			try {
				np.matchIgnoreCase("`");
				var words = new ArrayList<String>();
				while (!np.peekMatchIgnoreCase("`") && np.ttype != TT_EOF) {

					words.add(np.getWordRespectCase());
				}
				np.matchIgnoreCase("`");
				return StringUtils.toString(words, " ");
			} finally {
				np.setPunctuationCharacters("(),;:=\"{}`");
			}
		} else if (np.peekMatchIgnoreCase("\"")) {
			np.matchIgnoreCase("\"");
			var words = new ArrayList<String>();
			while (!np.peekMatchIgnoreCase("\"") && np.ttype != TT_EOF) {
				words.add(np.getWordRespectCase());
			}
			np.matchIgnoreCase("\"");
			return StringUtils.toString(words, " ");
		}
		return np.getWordRespectCase();
	}

	public static final class GMLInfo {
		private final String comment;
		private final boolean directed;
		private final int id;
		private final String label;

		public GMLInfo(String comment, boolean directed, int id, String label) {
			this.comment = comment;
			this.directed = directed;
			this.id = id;
			this.label = label;
		}

		public String comment() {
			return comment;
		}

		public boolean directed() {
			return directed;
		}

		public int id() {
			return id;
		}

		public String label() {
			return label;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (obj == null || obj.getClass() != this.getClass()) return false;
			var that = (GMLInfo) obj;
			return Objects.equals(this.comment, that.comment()) &&
				   this.directed == that.directed() &&
				   this.id == that.id() &&
				   Objects.equals(this.label, that.label());
		}

		@Override
		public int hashCode() {
			return Objects.hash(comment, directed, id, label);
		}

		@Override
		public String toString() {
			return "GMLInfo[" +
				   "comment=" + comment + ", " +
				   "directed=" + directed + ", " +
				   "id=" + id + ", " +
				   "label=" + label + ']';
		}

	}

	public static void main(String[] args) throws IOException {
		var input = """
				graph [
				comment "example graph"
				directed 1
				id 42
				label "Graph"
					node [
						id 1
						label "A"
						sequence "ACGTTGTCGTTG"
					]
					node [
						id 2
						label "B"
						sequence "TCGTTGGCGTTG"
					]
					node [
						id 3
						label `C "x"`
						sequence "GCGTTGACGTTG"
					]
					edge [
						source 1
						target 2
						overlap "6"
					]
					edge [
						source 2
						target 3
						overlap "7"
					]
					edge [
						source 3
						target 1
						overlap "8"
					]
				]
				""";

		var graph = new Graph();
		var labelNodeValueMap = new HashMap<String, Map<Node, String>>();
		var labelEdgeValueMap = new HashMap<String, Map<Edge, String>>();
		var gmlInfo = readGML(new StringReader(input), graph, labelNodeValueMap, labelEdgeValueMap);

		try (var w = new StringWriter()) {
			writeGML(graph, gmlInfo.comment(), gmlInfo.label(), gmlInfo.directed(), gmlInfo.id(), w, labelNodeValueMap, labelEdgeValueMap);
			System.out.println(w);
		}
	}
}
