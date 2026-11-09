/*
 *  FruchtermanReingoldLayout.java Copyright (C) 2026 Daniel H. Huson
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
import jloda.graph.NodeArray;
import jloda.graph.NodeSet;
import jloda.util.APoint2D;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * force-directed graph layout following Fruchterman and Reingold (1991), "Graph drawing by force-directed placement",
 * Software: Practice and Experience 21(11):1129-1164.
 * <p>
 * Nodes repel each other with force k^2/d and adjacent nodes attract each other with force d^2/k, where k is the
 * ideal edge length sqrt(area/n). An optional gravity pulls all nodes toward the origin, which keeps disconnected
 * components and isolated nodes near the rest of the graph. The displacement of a node in one
 * iteration is capped by a temperature that decreases linearly to zero over the iterations. Nodes in the fixed set
 * never move. The repulsive forces, which dominate the
 * running time, are computed in parallel.
 * <p>
 * Daniel Huson, 11.2026
 */
public class FruchtermanReingoldLayout {
	private final Graph graph;
	private final Node[] nodes;
	private final NodeArray<Integer> node2index;
	private final BitSet fixed = new BitSet();
	private final double[] x;
	private final double[] y;

	private float area = 1_000_000f;
	private double gravity = 1.0;
	private double speed = 1.0;

	private final Random random = new Random(666);

	/**
	 * constructor
	 *
	 * @param graph the graph
	 */
	public FruchtermanReingoldLayout(Graph graph) {
		this(graph, null, null);
	}

	/**
	 * constructor
	 *
	 * @param graph      the graph
	 * @param fixedNodes nodes that keep their start position, may be null
	 */
	public FruchtermanReingoldLayout(Graph graph, NodeSet fixedNodes) {
		this(graph, fixedNodes, null);
	}

	/**
	 * constructor
	 *
	 * @param graph      the graph
	 * @param fixedNodes nodes that keep their start position, may be null
	 * @param node2start start positions; nodes without one are placed at random, may be null
	 */
	public FruchtermanReingoldLayout(Graph graph, NodeSet fixedNodes, NodeArray<APoint2D<?>> node2start) {
		this.graph = graph;
		nodes = new Node[graph.getNumberOfNodes()];
		node2index = graph.newNodeArray();
		x = new double[nodes.length];
		y = new double[nodes.length];

		var frame = Math.sqrt(area);
		var i = 0;
		for (var v : graph.nodes()) {
			nodes[i] = v;
			node2index.put(v, i);
			if (fixedNodes != null && fixedNodes.contains(v))
				fixed.set(i);
			var start = (node2start != null ? node2start.get(v) : null);
			if (start != null) {
				x[i] = start.getX();
				y[i] = start.getY();
			} else {
				// uniformly in a disk that fills the frame
				var angle = 2 * Math.PI * random.nextDouble();
				var radius = 0.5 * frame * Math.sqrt(random.nextDouble());
				x[i] = radius * Math.cos(angle);
				y[i] = radius * Math.sin(angle);
			}
			i++;
		}
	}

	/**
	 * run the layout
	 *
	 * @param numberOfIterations number of iterations, 50 to a few hundred are usual
	 * @return node to position
	 */
	public NodeArray<APoint2D<?>> apply(int numberOfIterations) {
		NodeArray<APoint2D<?>> result = graph.newNodeArray();
		apply(numberOfIterations, result);
		return result;
	}

	/**
	 * run the layout
	 *
	 * @param numberOfIterations number of iterations
	 * @param result             node to position
	 */
	public void apply(int numberOfIterations, NodeArray<APoint2D<?>> result) {
		try {
			apply(numberOfIterations, result, new ProgressSilent(), 1);
		} catch (CanceledException ignored) {
		}
	}

	/**
	 * run the layout
	 *
	 * @param numberOfIterations number of iterations
	 * @param result             node to position
	 * @param progress           progress listener, reports one step per iteration
	 * @param numberOfThreads    threads used for the repulsive forces
	 */
	public void apply(int numberOfIterations, NodeArray<APoint2D<?>> result, ProgressListener progress, int numberOfThreads) throws CanceledException {
		var n = nodes.length;
		if (n > 0) {
			numberOfThreads = Math.max(1, Math.min(numberOfThreads, n));
			var service = (numberOfThreads > 1 ? Executors.newFixedThreadPool(numberOfThreads) : null);
			try {
				var frame = Math.sqrt(area);
				var k = Math.sqrt(area / n); // ideal edge length
				var dispX = new double[n];
				var dispY = new double[n];

				progress.setMaximum(numberOfIterations);
				progress.setProgress(0);

				for (var iteration = 0; iteration < numberOfIterations; iteration++) {
					var temperature = 0.1 * frame * (1.0 - (double) iteration / numberOfIterations);

					computeRepulsion(service, numberOfThreads, k, dispX, dispY);

					// attraction along edges
					for (var e : graph.edges()) {
						var a = node2index.get(e.getSource());
						var b = node2index.get(e.getTarget());
						if (a.equals(b))
							continue;
						var dx = x[a] - x[b];
						var dy = y[a] - y[b];
						var d = Math.sqrt(dx * dx + dy * dy);
						if (d > 1e-9) {
							var f = d * d / k;
							dispX[a] -= dx / d * f;
							dispY[a] -= dy / d * f;
							dispX[b] += dx / d * f;
							dispY[b] += dy / d * f;
						}
					}

					// gravity toward the origin, proportional to the distance; the factor was chosen so that with the default
					// gravity a grid keeps uniform edge lengths while an isolated node settles about one component
					// diameter away from the rest
					if (gravity > 0) {
						var g = 4 * gravity * k / frame;
						for (var i = 0; i < n; i++) {
							dispX[i] -= x[i] * g;
							dispY[i] -= y[i] * g;
						}
					}

					// move, limited by the temperature
					for (var i = 0; i < n; i++) {
						if (!fixed.get(i)) {
							var d = Math.sqrt(dispX[i] * dispX[i] + dispY[i] * dispY[i]);
							if (d > 1e-9) {
								var step = Math.min(d, temperature) * speed;
								x[i] += dispX[i] / d * step;
								y[i] += dispY[i] / d * step;
							}
						}
					}
					progress.setProgress(iteration + 1);
				}
			} finally {
				if (service != null)
					service.shutdownNow();
			}
		}
		for (var i = 0; i < n; i++) {
			var v = nodes[i];
			result.put(v, new APoint2D<>(x[i], y[i], v));
		}
	}

	/**
	 * computes the repulsive displacements of all nodes, overwriting the displacement arrays. Each thread handles a
	 * range of nodes and only writes its own entries
	 */
	private void computeRepulsion(ExecutorService service, int numberOfThreads, double k, double[] dispX, double[] dispY) throws CanceledException {
		var n = nodes.length;
		if (service == null) {
			repulsion(0, n, k, dispX, dispY);
		} else {
			var futures = new ArrayList<Future<?>>();
			var chunk = (n + numberOfThreads - 1) / numberOfThreads;
			for (var start = 0; start < n; start += chunk) {
				var from = start;
				var to = Math.min(n, start + chunk);
				futures.add(service.submit(() -> repulsion(from, to, k, dispX, dispY)));
			}
			for (var future : futures) {
				try {
					future.get();
				} catch (InterruptedException ex) {
					throw new CanceledException();
				} catch (Exception ex) {
					Basic.caught(ex);
				}
			}
		}
	}

	private void repulsion(int from, int to, double k, double[] dispX, double[] dispY) {
		var k2 = k * k;
		var n = nodes.length;
		for (var i = from; i < to; i++) {
			var sumX = 0.0;
			var sumY = 0.0;
			for (var j = 0; j < n; j++) {
				if (j != i) {
					var dx = x[i] - x[j];
					var dy = y[i] - y[j];
					var d2 = dx * dx + dy * dy;
					if (d2 < 1e-12) { // coincident nodes: push in an arbitrary direction that depends on the pair
						var angle = 2 * Math.PI * ((i * 31 + j * 17) % 97) / 97.0;
						dx = 1e-6 * Math.cos(angle);
						dy = 1e-6 * Math.sin(angle);
						d2 = dx * dx + dy * dy;
					}
					// unit vector times k^2/d equals vector times k^2/d^2
					var f = k2 / d2;
					sumX += dx * f;
					sumY += dy * f;
				}
			}
			dispX[i] = sumX;
			dispY[i] = sumY;
		}
	}

	/**
	 * the area of the layout; the ideal edge length is sqrt(area/n)
	 */
	public float getArea() {
		return area;
	}

	public void setArea(float area) {
		this.area = area;
	}

	/**
	 * strength of the pull toward the origin, 0 switches it off
	 */
	public double getGravity() {
		return gravity;
	}

	public void setGravity(double gravity) {
		this.gravity = gravity;
	}

	/**
	 * scales the movement per iteration
	 */
	public double getSpeed() {
		return speed;
	}

	public void setSpeed(double speed) {
		this.speed = speed;
	}
}
