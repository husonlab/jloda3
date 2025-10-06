/*
 * NodeSetWithGetRandomNode.java (updated & documented)
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

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeIntArray;

import java.util.Random;

/**
 * A mutable node set supporting:
 * <ul>
 *   <li>O(1) deletion by node</li>
 *   <li>O(1) uniform sampling without replacement</li>
 *   <li>Biased sampling by star mass (min/max over a fixed number of random tries)</li>
 * </ul>
 *
 * <p>Internally stores nodes in an array; the active range is {@code [0..lastSelectableIndexOfNode]}.
 * Deletion swaps a node with the tail element and shrinks the active range.</p>
 *
 * <p>Determinism: call {@link #setSeed(int)} before any sampling.</p>
 *
 * <p>Original C++ author: Stefan Hachul (GPL). Reimplemented in Java by Daniel Huson, 3.2021.
 * This version adds guards and documentation.</p>
 */
public class NodeSetWithGetRandomNode {
	private final Node[] array;
	private final NodeIntArray positionInArray;
	private final NodeIntArray massOfStar;
	private int lastSelectableIndexOfNode;
	private final Random random = new Random();

	/**
	 * Build a node set with unit masses.
	 */
	public NodeSetWithGetRandomNode(Graph graph) {
		array = new Node[graph.getNumberOfNodes()];
		positionInArray = graph.newNodeIntArray();
		massOfStar = graph.newNodeIntArray();

		int i = 0;
		for (var v : graph.nodes()) {
			array[i] = v;
			positionInArray.put(v, i);
			massOfStar.put(v, 1);
			i++;
		}
		lastSelectableIndexOfNode = array.length - 1;
	}

	/**
	 * Build a node set with masses taken from {@code nodeAttributes.get(v).getMass()}.
	 */
	public NodeSetWithGetRandomNode(Graph graph, NodeArray<NodeAttributes> nodeAttributes) {
		array = new Node[graph.getNumberOfNodes()];
		positionInArray = graph.newNodeIntArray();
		massOfStar = graph.newNodeIntArray();

		int i = 0;
		for (var v : graph.nodes()) {
			array[i] = v;
			positionInArray.put(v, i);
			massOfStar.put(v, nodeAttributes.get(v).getMass());
			i++;
		}
		lastSelectableIndexOfNode = array.length - 1;
	}

	/** @return {@code true} if no selectable nodes remain. */
	public boolean isEmpty() {
		return lastSelectableIndexOfNode < 0;
	}

	/** @return {@code true} if {@code v} has been deleted (or was never in the active range). */
	public boolean isDeleted(Node v) {
		return positionInArray.get(v) > lastSelectableIndexOfNode;
	}

	/**
	 * Delete {@code v} from the active set if present (O(1)).
	 * No-op if already deleted.
	 */
	public void delete(Node v) {
		if (!isDeleted(v)) {
			int pos = positionInArray.get(v);
			Node tail = array[lastSelectableIndexOfNode];
			array[pos] = tail;
			positionInArray.put(tail, pos);
			array[lastSelectableIndexOfNode] = v;
			positionInArray.put(v, lastSelectableIndexOfNode);
			lastSelectableIndexOfNode--;
		}
	}

	/**
	 * Uniform random node from the active set (without replacement).
	 * @throws IllegalStateException if the set is empty.
	 */
	public Node getRandomNode() {
		if (isEmpty()) {
			throw new IllegalStateException("NodeSetWithGetRandomNode is empty");
		}
		Node v = array[random.nextInt(lastSelectableIndexOfNode + 1)];
		delete(v);
		return v;
	}

	/**
	 * Random-biased selection preferring the lowest star mass among up to {@code numberRandomTries} random candidates.
	 * Each try samples uniformly without replacement from the active range by swapping with the tail segment used for tries.
	 * Falls back to uniform if no candidate was found (e.g., zero tries).
	 *
	 * @throws IllegalStateException if the set is empty.
	 */
	public Node getRandomNodeWithLowestStarMass(int numberRandomTries) {
		if (isEmpty()) {
			throw new IllegalStateException("NodeSetWithGetRandomNode is empty");
		}
		if (numberRandomTries <= 0) {
			return getRandomNode();
		}

		int minMass = Integer.MAX_VALUE;
		Node chosen = null;

		int lastTryIdx = lastSelectableIndexOfNode;
		int tries = 0;

		while (tries < numberRandomTries && lastTryIdx >= 0) {
			// sample uniformly from [0..lastTryIdx], then move that sampled element to lastTryIdx (try-reservoir)
			int rndIdx = random.nextInt(lastTryIdx + 1);
			Node sampled = array[rndIdx];
			Node tail = array[lastTryIdx];
			array[rndIdx] = tail;
			array[lastTryIdx] = sampled;
			positionInArray.put(tail, rndIdx);
			positionInArray.put(sampled, lastTryIdx);

			int mass = massOfStar.get(sampled);
			if (mass < minMass) {
				minMass = mass;
				chosen = sampled;
			}
			tries++;
			lastTryIdx--;
		}

		if (chosen == null) {
			// Should not happen unless zero tries, but be defensive
			return getRandomNode();
		}
		delete(chosen);
		return chosen;
	}

	/**
	 * Random-biased selection preferring the highest star mass among up to {@code numberRandomTries} random candidates.
	 * Each try samples uniformly without replacement from the active range by swapping with the tail segment used for tries.
	 * Falls back to uniform if no candidate was found (e.g., zero tries).
	 *
	 * @throws IllegalStateException if the set is empty.
	 */
	public Node getRandomNodeWithHighestStarMass(int numberRandomTries) {
		if (isEmpty()) {
			throw new IllegalStateException("NodeSetWithGetRandomNode is empty");
		}
		if (numberRandomTries <= 0) {
			return getRandomNode();
		}

		int maxMass = Integer.MIN_VALUE;
		Node chosen = null;

		int lastTryIdx = lastSelectableIndexOfNode;
		int tries = 0;

		while (tries < numberRandomTries && lastTryIdx >= 0) {
			// sample uniformly from [0..lastTryIdx], then move that sampled element to lastTryIdx (try-reservoir)
			int rndIdx = random.nextInt(lastTryIdx + 1);
			Node sampled = array[rndIdx];
			Node tail = array[lastTryIdx];
			array[rndIdx] = tail;
			array[lastTryIdx] = sampled;
			positionInArray.put(tail, rndIdx);
			positionInArray.put(sampled, lastTryIdx);

			int mass = massOfStar.get(sampled);
			if (mass > maxMass) {
				maxMass = mass;
				chosen = sampled;
			}
			tries++;
			lastTryIdx--;
		}

		if (chosen == null) {
			// Should not happen unless zero tries, but be defensive
			return getRandomNode();
		}
		delete(chosen);
		return chosen;
	}

	/**
	 * Seed the RNG for deterministic selection.
	 * Call before any sampling.
	 */
	public void setSeed(int seed) {
		random.setSeed(seed);
	}

	/**
	 * Total number of nodes the set was initialized with.
	 */
	public int size() {
		return array.length;
	}

	/**
	 * Number of nodes still selectable (not deleted).
	 */
	public int remainingCount() {
		return lastSelectableIndexOfNode + 1;
	}
}