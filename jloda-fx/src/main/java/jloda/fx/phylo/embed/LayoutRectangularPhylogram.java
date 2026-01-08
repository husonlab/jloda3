/*
 * LayoutRectangularPhylogram.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.fx.phylo.embed;

import javafx.geometry.Point2D;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.ProgramProperties;

import java.util.LinkedList;
import java.util.Map;

/**
 * compute the coordinates for a phylogram
 * todo: make independent of jloda and move to jloda-phylogeny
 * Daniel Huson, 8.2025
 */
@Deprecated
public class LayoutRectangularPhylogram {
	/**
	 * computes the coordinates of a phylogram
	 *
	 * @param network      the phylogeny
	 * @param averaging    parent averaging method
	 * @param nodePointMap the results are written to this map
	 */
	public static void apply(PhyloTree network, Averaging averaging, Map<Node, Point2D> nodePointMap) {
		try (var yCoord = network.newNodeDoubleArray()) {
			HeightAndAngles.apply(network, yCoord, averaging, true);

			var percentOffset = ProgramProperties.get("ReticulationOffsetPercent", 10.0);

			var averageWeight = network.edgeStream().mapToDouble(network::getWeight).average().orElse(1);
			var smallOffsetForReticulateEdge = (percentOffset / 100.0) * averageWeight;

			var rootHeight = yCoord.get(network.getRoot());

			try (var assigned = network.newNodeSet()) {
				// assign coordinates:
				var queue = new LinkedList<Node>();
				queue.add(network.getRoot());
				while (!queue.isEmpty()) // breath-first assignment
				{
					var w = queue.remove(0); // pop
					var ok = true;
					if (w.getInDegree() == 1) // has regular in edge
					{
						var e = w.getFirstInEdge();
						var v = e.getSource();
						var location = nodePointMap.get(v);

						if (!assigned.contains(v)) // can't process yet
						{
							ok = false;
						} else {
							var height = yCoord.get(e.getTarget());
							var u = e.getTarget();
							nodePointMap.put(u, new Point2D(location.getX() + network.getWeight(e), height));
							assigned.add(u);
						}
					} else if (w.getInDegree() > 1 && w.inEdgesStream(false).anyMatch(network::isTransferAcceptorEdge)) { // todo: just added this, might cause problems...
						var e = w.inEdgesStream(false).filter(network::isTransferAcceptorEdge).findAny().orElse(w.getFirstInEdge());
						var v = e.getSource();
						var location = nodePointMap.get(v);
						if (!assigned.contains(v)) // can't process yet
						{
							ok = false;
						} else {
							var height = yCoord.get(e.getTarget());
							var u = e.getTarget();
							double weight;
							if (network.hasEdgeWeights() && network.getEdgeWeights().containsKey(e))
								weight = network.getEdgeWeights().get(e);
							else if (network.isTreeEdge(e) || network.isTransferAcceptorEdge(e))
								weight = 1.0;
							else if (network.isTransferEdge(e))
								weight = 0.01;
							else weight = 0.1;
							nodePointMap.put(u, new Point2D(location.getX() + weight, height));
							assigned.add(u);
						}
					} else if (w.getInDegree() > 1) {
						var x = Double.NEGATIVE_INFINITY;
						for (var f : w.inEdges()) {
							var u = f.getSource();
							var location = nodePointMap.get(u);
							if (location == null) {
								ok = false;
							} else {
								x = Math.max(x, location.getX());
							}
						}
						if (ok && x > Double.NEGATIVE_INFINITY) {
							x += smallOffsetForReticulateEdge;
							nodePointMap.put(w, new Point2D(x, yCoord.get(w)));
							assigned.add(w);
						}
					} else  // is root node
					{
						nodePointMap.put(w, new Point2D(0, rootHeight));
						assigned.add(w);
					}

					if (ok)  // add children to end of queue:
						queue.addAll(IteratorUtils.asList(w.children()));
					else  // process this node again later
						queue.add(w);
				}
			}
		}
	}
}
