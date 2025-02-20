/*
 *  DAGTraversals.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.graph;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * DAG traversals
 * Daniel Huson, 2.2025
 */
public class DAGTraversals {

	/**
	 * does a post order traversal of a rooted DAG
	 *
	 * @param v        the root node
	 * @param children provides the children
	 * @param consumer consumer for the current node
	 */
	public static void postOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		for (var w : children.apply(v)) {
			postOrderTraversal(w, children, consumer);
		}
		consumer.accept(v);
	}

	/**
	 * does a pre order traversal of a rooted DAG
	 *
	 * @param v        the root node
	 * @param children provides the children
	 * @param consumer consumer for the current node
	 */
	public static void preOrderTraversal(Node v, Function<Node, List<Node>> children, Consumer<Node> consumer) {
		consumer.accept(v);
		for (var w : children.apply(v)) {
			preOrderTraversal(w, children, consumer);
		}
	}

}
