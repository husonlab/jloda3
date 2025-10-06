/*
 * MultiLevel.java (updated & documented)
 * Copyright (C) 2024 Daniel H. Huson
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

package jloda.graph.fmm.algorithm;

import jloda.graph.*;
import jloda.graph.fmm.FastMultiLayerMethodOptions;
import jloda.graph.fmm.geometry.DPoint;
import jloda.util.Counter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;

/**
 * Multilevel coarsening and initialization routines for the Fast Multilayer Method (FMMM),
 * following the "galaxy → solar-system → collapse" scheme.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Build a pyramid of coarser graphs (levels 0..L) while keeping per-level attributes.</li>
 *   <li>Select "suns" (coarse representatives), attach "planets" and "moons", then collapse.</li>
 *   <li>Provide initial positions for finer levels from already placed coarser levels.</li>
 * </ul>
 *
 * <p>Notable updates vs. original:</p>
 * <ul>
 *   <li>Randomness is now seeded from {@link FastMultiLayerMethodOptions#getRandSeed()} for determinism.</li>
 *   <li>Removed try-with-resources on per-level attribute arrays (they must not be auto-closed while still used later).</li>
 *   <li>Fixed the parallel-edge merge to use a single stable comparator (minId,maxId) and average lengths correctly.</li>
 *   <li>Corrected lambda index handling in PM-node initialization (no off-by-one / skipping last element).</li>
 *   <li>Clarified and tightened the sector selection logic; reduced double-increment of the loop index.</li>
 *   <li>Minor naming cleanup: "Mode" → "Moon".</li>
 * </ul>
 */
public class MultiLevel {
	/**
	 * Shared RNG seeded per run from options to keep deterministic behavior.
	 */
	private static final Random random = new Random();

	/**
	 * Create the multilevel representations (level 0 = original graph).
	 *
	 * @param options                      FMMM options
	 * @param graph                        level-0 graph
	 * @param nodeAttributes               level-0 node attributes
	 * @param edgeAttributes               level-0 edge attributes
	 * @param multiLevelGraph              output array: per-level graphs
	 * @param multiLevelNodeAttributes     output array: per-level node attributes
	 * @param multiLevelEdgeAttributes     output array: per-level edge attributes
	 * @return the top level index (L), i.e., number of levels - 1
	 */
	public static int createMultiLevelRepresentations(FastMultiLayerMethodOptions options,
													  Graph graph,
													  NodeArray<NodeAttributes> nodeAttributes,
													  EdgeArray<EdgeAttributes> edgeAttributes,
													  Graph[] multiLevelGraph,
													  NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
													  EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes) {

		// Seed RNG once per multilevel run for determinism
		random.setSeed(options.getRandSeed());

		multiLevelGraph[0] = graph;
		multiLevelNodeAttributes[0] = nodeAttributes;
		multiLevelEdgeAttributes[0] = edgeAttributes;

		var badEdgeNrCounter = new Counter(0);
		var activeLevel = 0;
		var activeGraph = multiLevelGraph[0];

		// Build higher levels until graph small enough or edge growth no longer near-linear
		while (activeGraph.getNumberOfNodes() > options.getMinGraphSize()
			   && edgeNumberSumOfAllLevelsIsLinear(multiLevelGraph, activeLevel, badEdgeNrCounter)) {

			var newGraph = new Graph();
			// DO NOT use try-with-resources here; these arrays live beyond this block.
			NodeArray<NodeAttributes> newNodeAttributes = newGraph.newNodeArray();
			EdgeArray<EdgeAttributes> newEdgeAttributes = newGraph.newEdgeArray();

			multiLevelGraph[activeLevel + 1] = newGraph;
			multiLevelNodeAttributes[activeLevel + 1] = newNodeAttributes;
			multiLevelEdgeAttributes[activeLevel + 1] = newEdgeAttributes;

			partitionGalaxyIntoSolarSystems(options, multiLevelGraph, multiLevelNodeAttributes, multiLevelEdgeAttributes, activeLevel);
			collapseSolarSystems(multiLevelGraph, multiLevelNodeAttributes, multiLevelEdgeAttributes, activeLevel);

			activeLevel++;
			activeGraph = multiLevelGraph[activeLevel];
		}
		return activeLevel;
	}

	/**
	 * Heuristic guard: keep building levels while summed edge counts shrink roughly linearly.
	 */
	private static boolean edgeNumberSumOfAllLevelsIsLinear(Graph[] multiLevelGraph, int activeLevel, Counter badEdgeNrCounter) {
		if (activeLevel == 0) {
			return true;
		} else if (multiLevelGraph[activeLevel].getNumberOfEdges()
				   <= 0.8 * (multiLevelGraph[activeLevel - 1].getNumberOfEdges())) {
			return true;
		} else if (badEdgeNrCounter.get() < 5) {
			badEdgeNrCounter.increment();
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Partition level-{@code level} galaxy into solar systems and label suns/planets/moons.
	 */
	private static void partitionGalaxyIntoSolarSystems(FastMultiLayerMethodOptions options,
														Graph[] multiLevelGraph,
														NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
														EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
														int level) {
		createSunsAndPlanets(options, multiLevelGraph, multiLevelNodeAttributes, multiLevelEdgeAttributes, level);
		createMoonNodesAndPMNodes(multiLevelGraph, multiLevelNodeAttributes, multiLevelEdgeAttributes, level);
	}

	/**
	 * Select suns and planets, create higher-level representatives, and set core attributes.
	 */
	private static void createSunsAndPlanets(FastMultiLayerMethodOptions options,
											 Graph[] multiLevelGraph,
											 NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
											 EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
											 int level) {
		if (level == 0) {
			multiLevelNodeAttributes[level].values().forEach(na -> na.setMass(1));
		}

		final NodeSetWithGetRandomNode nodeSet = (options.getGalaxyChoice() == FastMultiLayerMethodOptions.GalaxyChoice.UniformProb)
				? new NodeSetWithGetRandomNode(multiLevelGraph[level])
				: new NodeSetWithGetRandomNode(multiLevelGraph[level], multiLevelNodeAttributes[level]);
		nodeSet.setSeed(options.getRandSeed());

		var sunNodes = new ArrayList<Node>();

		while (!nodeSet.isEmpty()) {
			// 1) Pick a sun
			Node sunNode = switch (options.getGalaxyChoice()) {
				default -> nodeSet.getRandomNode();
				case NonUniformProbLowerMass -> nodeSet.getRandomNodeWithLowestStarMass(options.getNumberRandomTries());
				case NonUniformProbHigherMass ->
						nodeSet.getRandomNodeWithHighestStarMass(options.getNumberRandomTries());
			};
			sunNodes.add(sunNode);

			// 2) Create the representative at level+1
			var newNode = multiLevelGraph[level + 1].newNode();
			var newNa = new NodeAttributes();
			newNa.initMultiLevelValues();
			multiLevelNodeAttributes[level + 1].put(newNode, newNa);

			// 3) Label the sun
			var sa = multiLevelNodeAttributes[level].get(sunNode);
			sa.setHigherLevelNode(newNode);
			sa.setType(NodeAttributes.Type.Sun);
			sa.setDedicatedSunNode(sunNode);
			sa.setDedicatedSunDistance(0);

			// 4) Label planets (neighbors of the sun)
			var planetNodes = new ArrayList<Node>();
			for (var sunEdge : sunNode.adjacentEdges()) {
				double distanceToSun = multiLevelEdgeAttributes[level].get(sunEdge).getLength();
				final Node planetNode = sunEdge.getOpposite(sunNode);
				var na = multiLevelNodeAttributes[level].get(planetNode);
				na.setType(NodeAttributes.Type.Planet);
				na.setDedicatedSunNode(sunNode);
				na.setDedicatedSunDistance(distanceToSun);
				planetNodes.add(planetNode);
			}

			// 5) Remove planets from candidate set
			for (var v : planetNodes) {
				if (!nodeSet.isDeleted(v)) nodeSet.delete(v);
			}

			// 6) Pre-claim neighbors of planets as potential moons (remove unclassified neighbors)
			for (var v : planetNodes) {
				for (var e : v.adjacentEdges()) {
					var possibleMoonNode = e.getOpposite(v);
					var na = multiLevelNodeAttributes[level].get(possibleMoonNode);
					if (na.getType() == NodeAttributes.Type.Unspecified) {
						nodeSet.delete(possibleMoonNode);
					}
				}
			}
		}

		// Copy visual properties from suns to their reps at level+1 and reset masses (recomputed later)
		for (var sunNode : sunNodes) {
			var sna = multiLevelNodeAttributes[level].get(sunNode);
			var rep = sna.getHigherLevelNode();
			var repAttr = new NodeAttributes(sna.getWidth(), sna.getHeight(), sna.getPosition(), sunNode, null);
			repAttr.setMass(0);
			multiLevelNodeAttributes[level + 1].put(rep, repAttr);
		}
	}

	/**
	 * Identify moon nodes and attach them to planet-with-moons (PM) nodes.
	 */
	private static void createMoonNodesAndPMNodes(Graph[] multiLevelGraph,
												  NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
												  EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
												  int level) {
		for (var v : multiLevelGraph[level].nodes()) {
			if (multiLevelNodeAttributes[level].get(v).getType() == NodeAttributes.Type.Unspecified) {
				double bestDist = 0;
				Node nearestPlanet = null;
				Edge moonEdge = null;

				for (var e : v.adjacentEdges()) {
					var neighbor = e.getOpposite(v);
					var neighborType = multiLevelNodeAttributes[level].get(neighbor).getType();
					if (neighborType == NodeAttributes.Type.Planet || neighborType == NodeAttributes.Type.PlanetWithMoons) {
						var ea = multiLevelEdgeAttributes[level].get(e);
						if (moonEdge == null || bestDist > ea.getLength()) {
							moonEdge = e;
							bestDist = ea.getLength();
							nearestPlanet = neighbor;
						}
					}
				}
				if (moonEdge != null) {
					multiLevelEdgeAttributes[level].get(moonEdge).makeMoonEdge();
				}
				if (nearestPlanet != null) {
					var pa = multiLevelNodeAttributes[level].get(nearestPlanet);
					pa.setType(NodeAttributes.Type.PlanetWithMoons);
					pa.getMoons().add(v);

					var va = multiLevelNodeAttributes[level].get(v);
					va.setType(NodeAttributes.Type.Moon);
					va.setDedicatedSunNode(pa.getDedicatedSunNode());
					va.setDedicatedSunDistance(pa.getDedicatedSunDistance());
					va.setDedicatedPMNode(nearestPlanet);
				}
			}
		}
	}

	/**
	 * Collapse solar systems from level→level+1 and update edge lengths/attributes.
	 */
	private static void collapseSolarSystems(Graph[] multiLevelGraph,
											 NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
											 EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
											 int level) {
		calculateMassOfCollapsedNodes(multiLevelGraph, multiLevelNodeAttributes, level);
		var newEdgeLengths = createEdgesEdgeDistancesAndLambdaLists(multiLevelGraph, multiLevelNodeAttributes, multiLevelEdgeAttributes, level);
		deleteParallelEdgesAndUpdateEdgeLength(multiLevelGraph, multiLevelEdgeAttributes, newEdgeLengths, level);
	}

	/**
	 * Sum masses into representatives at level+1.
	 */
	private static void calculateMassOfCollapsedNodes(Graph[] multiLevelGraph,
													  NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
													  int level) {
		for (var v : multiLevelGraph[level].nodes()) {
			var dedicatedSun = multiLevelNodeAttributes[level].get(v).getDedicatedSunNode();
			var rep = multiLevelNodeAttributes[level].get(dedicatedSun).getHigherLevelNode();
			var repAttr = multiLevelNodeAttributes[level + 1].get(rep);
			repAttr.setMass(repAttr.getMass() + 1);
		}
	}

	/**
	 * Create inter-solar-system edges on level+1; compute composed edge lengths newLength = sDist + eLen + tDist;
	 * propagate lambda-splits for finer-level interpolation.
	 */
	private static EdgeDoubleArray createEdgesEdgeDistancesAndLambdaLists(Graph[] multiLevelGraph,
																		  NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
																		  EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
																		  int level) {
		var interSolar = new ArrayList<Edge>();

		// Ensure attributes array for level+1 exists (created earlier in createMultiLevelRepresentations)
		if (multiLevelEdgeAttributes[level + 1] == null) {
			multiLevelEdgeAttributes[level + 1] = multiLevelGraph[level + 1].newEdgeArray();
		}
		var newEdgeLengths = multiLevelGraph[level + 1].newEdgeDoubleArray();

		// Build edges between different suns at level+1
		for (var e : multiLevelGraph[level].edges()) {
			var sSun = multiLevelNodeAttributes[level].get(e.getSource()).getDedicatedSunNode();
			var tSun = multiLevelNodeAttributes[level].get(e.getTarget()).getDedicatedSunNode();
			if (sSun != tSun) {
				var hs = multiLevelNodeAttributes[level].get(sSun).getHigherLevelNode();
				var ht = multiLevelNodeAttributes[level].get(tSun).getHigherLevelNode();

				var newE = multiLevelGraph[level + 1].newEdge(hs, ht);
				var ea = new EdgeAttributes();
				ea.initMultiLevelValues();
				multiLevelEdgeAttributes[level + 1].put(newE, ea);

				multiLevelEdgeAttributes[level].get(e).setHigherLevelEdge(newE);
				interSolar.add(e);
			}
		}

		// Compute new composed lengths and record lambdas/neighbor suns on the fine level
		for (var e : interSolar) {
			var sSun = multiLevelNodeAttributes[level].get(e.getSource()).getDedicatedSunNode();
			var tSun = multiLevelNodeAttributes[level].get(e.getTarget()).getDedicatedSunNode();

			double eLen = multiLevelEdgeAttributes[level].get(e).getLength();
			double sDist = multiLevelNodeAttributes[level].get(e.getSource()).getDedicatedSunDistance();
			double tDist = multiLevelNodeAttributes[level].get(e.getTarget()).getDedicatedSunDistance();
			double newLen = sDist + eLen + tDist;

			var eNew = multiLevelEdgeAttributes[level].get(e).getHigherLevelEdge();
			newEdgeLengths.put(eNew, newLen);

			// Store lambdas for later interpolation
			var sAttr = multiLevelNodeAttributes[level].get(e.getSource());
			var tAttr = multiLevelNodeAttributes[level].get(e.getTarget());
			if (newLen > 0) {
				sAttr.getLambdas().add(sDist / newLen);
				tAttr.getLambdas().add(tDist / newLen);
			} else {
				// Degenerate; fall back to equal split
				sAttr.getLambdas().add(0.5);
				tAttr.getLambdas().add(0.5);
			}

			sAttr.getNeighborSunNodes().add(tSun);
			tAttr.getNeighborSunNodes().add(sSun);
		}
		return newEdgeLengths;
	}

	/**
	 * Merge parallel edges (same unordered endpoints) on level+1; average their composed lengths.
	 */
	private static void deleteParallelEdgesAndUpdateEdgeLength(Graph[] multiLevelGraph,
															   EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
															   EdgeDoubleArray newEdgeLengths,
															   int level) {
		var nextGraph = multiLevelGraph[level + 1];

		var sorted = nextGraph.getEdgesAsList();
		// Sort by (minId, maxId) in a single stable comparator
		sorted.sort(Comparator
				.comparingInt((Edge e) -> Math.min(e.getSource().getId(), e.getTarget().getId()))
				.thenComparingInt(e -> Math.max(e.getSource().getId(), e.getTarget().getId())));

		Edge groupHead = null;
		int count = 0;
		for (var e : sorted) {
			if (groupHead == null) {
				groupHead = e;
				count = 1;
			} else {
				boolean samePair =
						(e.getSource() == groupHead.getSource() && e.getTarget() == groupHead.getTarget()) ||
						(e.getSource() == groupHead.getTarget() && e.getTarget() == groupHead.getSource());
				if (samePair) {
					// accumulate into groupHead
					newEdgeLengths.put(groupHead, newEdgeLengths.get(groupHead) + newEdgeLengths.get(e));
					nextGraph.deleteEdge(e);
					count++;
				} else {
					// finish previous group
					if (count > 1) {
						newEdgeLengths.put(groupHead, newEdgeLengths.get(groupHead) / count);
					}
					groupHead = e;
					count = 1;
				}
			}
		}
		// finish last group
		if (groupHead != null && count > 1) {
			newEdgeLengths.put(groupHead, newEdgeLengths.get(groupHead) / count);
		}

		// Write final lengths into the level+1 edge attributes
		for (var e : nextGraph.edges()) {
			multiLevelEdgeAttributes[level + 1].get(e).setLength(newEdgeLengths.get(e));
		}
	}

	/**
	 * Lift placement from level+1 to level, setting initial positions of sun nodes,
	 * then planets/moons (including PM-nodes), using lambdas/angles/neighbor suns.
	 */
	public static void findInitialPlacementForLevel(int level,
													FastMultiLayerMethodOptions options,
													Graph[] multiLevelGraph,
													NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
													EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes) {
		setInitialPositionsOfSunNodes(level, multiLevelGraph, multiLevelNodeAttributes);

		var pmNodes = new ArrayList<Node>();
		setInitialPositionsOfPlanetAndMoonNodes(level, options, multiLevelGraph, multiLevelNodeAttributes, multiLevelEdgeAttributes, pmNodes);
		setInitialPositionsOfPMNodes(level, options, multiLevelNodeAttributes, multiLevelEdgeAttributes, pmNodes);
	}

	/**
	 * Copy rep node positions (level+1) down to their corresponding sun nodes (level).
	 */
	private static void setInitialPositionsOfSunNodes(int level,
													  Graph[] multiLevelGraph,
													  NodeArray<NodeAttributes>[] multiLevelNodeAttributes) {
		for (var vHigh : multiLevelGraph[level + 1].nodes()) {
			var naHigh = multiLevelNodeAttributes[level + 1].get(vHigh);
			var vLow = naHigh.getLowerLevelNode();
			multiLevelNodeAttributes[level].get(vLow).setPosition(naHigh.getPosition());
			multiLevelNodeAttributes[level].get(vLow).placed();
		}
	}

	/**
	 * Position planets and moons (except PM-nodes which are handled later).
	 * Uses sector constraints (angles) and neighbor information if available.
	 */
	private static void setInitialPositionsOfPlanetAndMoonNodes(int level,
																FastMultiLayerMethodOptions options,
																Graph[] multiLevelGraph,
																NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
																EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
																ArrayList<Node> pmNodes) {
		final var candidates = new ArrayList<DPoint>();

		createAllPlacementSectors(multiLevelGraph, multiLevelNodeAttributes, multiLevelEdgeAttributes, level);

		for (var v : multiLevelGraph[level].nodes()) {
			var va = multiLevelNodeAttributes[level].get(v);
			var type = va.getType();

			if (type == NodeAttributes.Type.PlanetWithMoons) {
				pmNodes.add(v);
				continue;
			}

			candidates.clear();
			var sunPos = multiLevelNodeAttributes[level].get(va.getDedicatedSunNode()).getPosition();

			// Use already placed neighbors within the same solar system to triangulate a starting position
			if (options.getInitialPlacementMult() == FastMultiLayerMethodOptions.InitialPlacementMultiLayer.Advanced) {
				for (var e : v.adjacentEdges()) {
					var adj = e.getOpposite(v);
					var aa = multiLevelNodeAttributes[level].get(adj);

					if (va.getDedicatedSunNode() == aa.getDedicatedSunNode()
						&& aa.getType() != NodeAttributes.Type.Sun
						&& aa.isPlaced()) {
						var newPos = calculatePosition(sunPos, aa.getPosition(),
								va.getDedicatedSunDistance(), multiLevelEdgeAttributes[level].get(e).getLength());
						candidates.add(newPos);
					}
				}
			}

			// If no lambda info: choose a random point within allowed sector at the right radius
			if (va.getLambdas().isEmpty()) {
				if (candidates.isEmpty()) {
					var newPos = createRandomPosition(sunPos, va.getDedicatedSunDistance(), va.getAngle1(), va.getAngle2());
					candidates.add(newPos);
				}
			} else {
				// Use lambdas to place between this sun and neighbor suns
				int lambdaIdx = 0;
				for (var adjSun : va.getNeighborSunNodes()) {
					var lambda = va.getLambdas().get(lambdaIdx);
					var adjSunPos = multiLevelNodeAttributes[level].get(adjSun).getPosition();
					var newPos = getWaggledInbetweenPosition(sunPos, adjSunPos, lambda);
					candidates.add(newPos);
					lambdaIdx = (lambdaIdx + 1 < va.getLambdas().size() ? lambdaIdx + 1 : 0);
				}
			}

			va.setPosition(DPoint.computeBarycenter(candidates));
			va.placed();
		}
	}

	/**
	 * Compute sector angles for all suns at level using neighbor geometry from level+1.
	 */
	private static void createAllPlacementSectors(Graph[] multiLevelGraph,
												  NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
												  EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
												  int level) {
		var adjPositions = new ArrayList<DPoint>();

		for (var vHigh : multiLevelGraph[level + 1].nodes()) {
			var vha = multiLevelNodeAttributes[level + 1].get(vHigh);
			adjPositions.clear();

			var vHighPosition = vha.getPosition();
			for (var eHigh : vHigh.adjacentEdges()) {
				if (multiLevelEdgeAttributes[level + 1].get(eHigh).isExtraEdge()) {
					var wHigh = eHigh.getOpposite(vHigh);
					var wPos = multiLevelNodeAttributes[level + 1].get(wHigh).getPosition();
					adjPositions.add(new DPoint(wPos.getX(), wPos.getY()));
				}
			}

			double angle1, angle2;
			if (adjPositions.isEmpty()) {
				angle1 = 0;
				angle2 = 2 * Math.PI;
			} else if (adjPositions.size() == 1) {
				// Opposite half-plane to the only neighbor
				var start = adjPositions.get(0);
				var xAxis = new DPoint(vHighPosition.getX() + 1, vHighPosition.getY());
				angle1 = DPoint.angle(vHighPosition, xAxis, start);
				angle2 = angle1 + Math.PI;
			} else {
				// Choose the largest angular gap between neighbors (up to MAX random samples)
				final int MAX = 10;
				angle1 = 0;
				angle2 = 0;

				int samples = Math.min(MAX, adjPositions.size());
				for (int s = 0; s < samples; s++) {
					var start = adjPositions.get(s);
					var xAxis = new DPoint(vHighPosition.getX() + 1, vHighPosition.getY());
					double a1 = DPoint.angle(vHighPosition, xAxis, start);

					boolean first = true;
					double minDelta = 0;
					for (int j = 0; j < adjPositions.size(); j++) {
						if (j == s) continue;
						double delta = DPoint.angle(vHighPosition, start, adjPositions.get(j));
						if (first || delta < minDelta) {
							minDelta = delta;
							first = false;
						}
					}
					double a2 = a1 + minDelta;
					if (s == 0 || (a2 - a1) > (angle2 - angle1)) {
						angle1 = a1;
						angle2 = a2;
					}
				}
				if (angle1 == angle2) {
					angle2 = angle1 + Math.PI;
				}
			}

			// write sector to the corresponding sun at level
			var sunAtLevel = vha.getLowerLevelNode();
			multiLevelNodeAttributes[level].get(sunAtLevel).setAngle1(angle1);
			multiLevelNodeAttributes[level].get(sunAtLevel).setAngle2(angle2);
		}

		// import the sector of each node from its dedicated sun
		for (var v : multiLevelGraph[level].nodes()) {
			var va = multiLevelNodeAttributes[level].get(v);
			var sun = va.getDedicatedSunNode();
			va.setAngle1(multiLevelNodeAttributes[level].get(sun).getAngle1());
			va.setAngle2(multiLevelNodeAttributes[level].get(sun).getAngle2());
		}
	}

	/** Place a node at distance {@code dist_s} from s and {@code dist_t} from t along the line st, with slight waggle. */
	private static DPoint calculatePosition(DPoint s, DPoint t, double dist_s, double dist_t) {
		var dist_st = s.distance(t);
		double lambda = (dist_s + (dist_st - dist_s - dist_t) / 2.0) / (dist_st == 0 ? 1.0 : dist_st);
		if (Double.isNaN(lambda)) {
			// extremely degenerate case; fall back to mid
			lambda = 0.5;
		}
		return getWaggledInbetweenPosition(s, t, lambda);
	}

	/** Interpolate between s and t using lambda, then add a small random orthogonal waggle. */
	private static DPoint getWaggledInbetweenPosition(DPoint s, DPoint t, double lambda) {
		final double WAGGLE = 0.05;
		var inbetween = new DPoint(
				s.getX() + lambda * (t.getX() - s.getX()),
				s.getY() + lambda * (t.getY() - s.getY()));
		var dist = s.distance(t);
		var radius = WAGGLE * dist;
		var randRadius = radius * random.nextDouble();
		return createRandomPosition(inbetween, randRadius, 0, 2 * Math.PI);
	}

	/** Uniformly sample a point on a circular arc sector around {@code center}. */
	private static DPoint createRandomPosition(DPoint center, double radius, double angle1, double angle2) {
		var rnd = angle1 + (angle2 - angle1) * random.nextDouble();
		var dx = Math.cos(rnd) * radius;
		var dy = Math.sin(rnd) * radius;
		return new DPoint(center.getX() + dx, center.getY() + dy);
	}

	/**
	 * Place PM-nodes using advanced heuristics and their attached moons.
	 */
	private static void setInitialPositionsOfPMNodes(int level,
													 FastMultiLayerMethodOptions options,
													 NodeArray<NodeAttributes>[] multiLevelNodeAttributes,
													 EdgeArray<EdgeAttributes>[] multiLevelEdgeAttributes,
													 ArrayList<Node> pmNodes) {
		var candidates = new ArrayList<DPoint>();

		for (var v : pmNodes) {
			candidates.clear();
			var va = multiLevelNodeAttributes[level].get(v);

			var sun = va.getDedicatedSunNode();
			var sunDist = va.getDedicatedSunDistance();
			var sunPos = multiLevelNodeAttributes[level].get(sun).getPosition();

			// Triangulate using placed non-moon neighbors within the same solar system
			if (options.getInitialPlacementMult() == FastMultiLayerMethodOptions.InitialPlacementMultiLayer.Advanced) {
				for (var e : v.adjacentEdges()) {
					var adj = e.getOpposite(v);
					var aa = multiLevelNodeAttributes[level].get(adj);
					var ea = multiLevelEdgeAttributes[level].get(e);

					if (!ea.isMoonEdge()
						&& va.getDedicatedSunNode() == aa.getDedicatedSunNode()
						&& aa.getType() != NodeAttributes.Type.Sun
						&& aa.isPlaced()) {
						var newPos = calculatePosition(sunPos, aa.getPosition(), sunDist, ea.getLength());
						candidates.add(newPos);
					}
				}
			}

			// Use moons to position PM between its sun and moon(s)
			for (var moon : va.getMoons()) {
				var ma = multiLevelNodeAttributes[level].get(moon);
				var moonPos = ma.getPosition();
				var moonDist = ma.getDedicatedSunDistance();
				if (moonDist > 0) {
					double lambda = sunDist / moonDist;
					candidates.add(getWaggledInbetweenPosition(sunPos, moonPos, lambda));
				}
			}

			// Use neighbor-sun lambdas if present
			if (!va.getLambdas().isEmpty()) {
				int i = 0;
				for (var adjSun : va.getNeighborSunNodes()) {
					var lambda = va.getLambdas().get(i);
					var adjSunPos = multiLevelNodeAttributes[level].get(adjSun).getPosition();
					candidates.add(getWaggledInbetweenPosition(sunPos, adjSunPos, lambda));
					i = (i + 1 < va.getLambdas().size() ? i + 1 : 0);
				}
			}

			va.setPosition(DPoint.computeBarycenter(candidates));
			va.placed();
		}
	}
}