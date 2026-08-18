/*
 * AlgorithmNode.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.workflow;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import jloda.fx.util.AService;
import jloda.util.Basic;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;

import java.util.stream.Collectors;

/**
 * a workflow node that contains an algorithm
 * Daniel Huson, 10.2021
 */
public class AlgorithmNode extends WorkflowNode {
	public static boolean verbose = false;
	private final AService<Boolean> service = new AService<>();
	private final ObjectProperty<Algorithm> algorithm = new SimpleObjectProperty<>(null);

	public AlgorithmNode(Workflow owner) {
		super(owner);

		service.setCallable(() -> {
			var algorithm = getAlgorithm();
			if (algorithm != null) {
				var inputData = getParents().stream().filter(d -> d instanceof DataNode).map(d -> (DataNode) d).map(DataNode::getDataBlock).collect(Collectors.toList());
				var outputData = getChildren().stream().filter(d -> d instanceof DataNode).map(d -> (DataNode) d).map(DataNode::getDataBlock).collect(Collectors.toList());
				algorithm.compute(service.getProgressListener(), inputData, outputData);
				return true;
			}
			return false;
		});
		service.stateProperty().addListener((v, o, n) -> {
			if (verbose)
				System.err.println("Service (" + getName() + ") " + o + " -> " + n);

			if (n.equals(Worker.State.SCHEDULED)) {
				getChildren().stream().filter(d -> d instanceof DataNode).map(d -> (DataNode) d).map(DataNode::getDataBlock).forEach(DataBlock::clear);
			}

			setValid(n.equals(Worker.State.SUCCEEDED));
		});

		validProperty().addListener((v, o, n) -> {
			if (!n && service.isRunning())
				service.cancel();
		});

		algorithm.addListener((v, o, n) -> {
			if (o != null) {
				nameProperty().unbindBidirectional(o.nameProperty());
				shortDescriptionProperty().unbindBidirectional(o.shortDescriptionProperty());
			}
			if (n != null) {
				nameProperty().bindBidirectional(n.nameProperty());
				shortDescriptionProperty().bind(n.shortDescriptionProperty());
			}
		});
	}

	@Override
	protected ChangeListener<Boolean> createParentValidListener() {
		return (v, o, n) -> {
			if (n && getParents().stream().allMatch(WorkflowNode::isValid)) {
				if (false)
					System.err.println(toReportString(false) + " restarting, n=" + n + " parents: " + (getParents().size()) + ", all=" + getParents().stream().allMatch(WorkflowNode::isValid));
				restart();
			} else
				setValid(false);
		};
	}

	public void addParent(WorkflowNode v) {
		if (!(v instanceof DataNode))
			throw new IllegalArgumentException("addParent(): must be DataNode");
		super.addParent(v);
	}

	public void addChild(WorkflowNode v) {
		if (!(v instanceof DataNode))
			throw new IllegalArgumentException("addChild(): must be DataNode");
		super.addChild(v);
	}

	public Algorithm getAlgorithm() {
		return algorithm.get();
	}

	public void setAlgorithm(Algorithm algorithm) {
		this.algorithm.set(algorithm);
	}

	public ReadOnlyObjectProperty<Algorithm> algorithmProperty() {
		return algorithm;
	}

	public AService<Boolean> getService() {
		return service;
	}

	public void restart() {
		try {
			if (service.getProgressListener() != null)
				service.getProgressListener().setTasks("Running", getName());
			if (!AService.isToolkitRunning())
				runInline();
			else
				service.restart();
		} catch (Exception ex) {
			Basic.caught(ex);
			throw ex;
		}
	}

	/**
	 * runs the algorithm on the calling thread, driving by hand the transitions that the service state
	 * listener drives when a toolkit is present: clear the outputs, invalidate, compute, validate
	 * <p>
	 * Used when there is no JavaFX toolkit, because a Service can only be started from the FX application
	 * thread. Note that this is synchronous and therefore recursive: setValid(true) validates the child data
	 * nodes, which restarts the algorithm nodes below them, so when this returns the whole subtree beneath
	 * the node has been computed. That is the property a headless run wants; it is also why a workflow with a
	 * pathologically long chain would use a correspondingly deep stack.
	 */
	private void runInline() {
		getChildren().stream().filter(d -> d instanceof DataNode).map(d -> (DataNode) d).map(DataNode::getDataBlock).forEach(DataBlock::clear);
		setValid(false);
		try {
			service.runInline(getHeadlessProgressListener());
			setValid(true);
		} catch (Exception ex) {
			Basic.caught(ex);
			setValid(false);
		}
	}

	/**
	 * the progress listener to use when running without a toolkit
	 *
	 * @return progress listener; silent unless the owning workflow supplies one
	 */
	private ProgressListener getHeadlessProgressListener() {
		var progress = getOwner().getHeadlessProgressListener();
		return (progress != null ? progress : new ProgressSilent());
	}

	@Override
	public String toReportString(boolean full) {
		if (full)
			return String.format("%s (%s); parents: %s children: %s",
					toReportString(false), isValid(),
					StringUtils.toString(getParents().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","),
					StringUtils.toString(getChildren().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","));
		else
			return String.format("%02d AlgorithmNode '%s'", getId(),
					(getAlgorithm() != null ? getAlgorithm().getName() : getName()));
	}
}
