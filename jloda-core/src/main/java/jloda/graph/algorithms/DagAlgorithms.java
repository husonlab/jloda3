/*
 *  DagAlgorithms.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.graph.algorithms;

import jloda.graph.Graph;
import jloda.graph.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DagAlgorithms {

	/**
	 * Computes a topological order of a directed acyclic graph,
	 * i.e., a linear ordering of its vertices such that for every directed edge
	 * u \to v, vertex u appears before v in the ordering.
	 * Throws IllegalArgumentException if a cycle is detected.
	 */
	public static List<Node> topologicalOrder(Graph g) {

		var order = new ArrayList<Node>(g.getNumberOfNodes());
		var queue = new ArrayDeque<Node>();

		// copy indegrees (we must not destroy the graph)
		var indegree = new HashMap<Node, Integer>();

		for (var v : g.nodes()) {
			var d = v.getInDegree();
			indegree.put(v, d);
			if (d == 0)
				queue.add(v);
		}

		while (!queue.isEmpty()) {
			var v = queue.poll();
			order.add(v);

			for (var w : v.children()) {
				var newDeg = indegree.get(w) - 1;
				indegree.put(w, newDeg);
				if (newDeg == 0)
					queue.add(w);
			}
		}

		if (order.size() != g.getNumberOfNodes()) {
			throw new IllegalArgumentException("Graph is not acyclic");
		}

		return order;
	}
}