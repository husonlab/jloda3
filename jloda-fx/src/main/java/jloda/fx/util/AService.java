/*
 * AService.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.util;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.scene.layout.Pane;
import jloda.fx.control.ProgressPane;
import jloda.fx.window.NotificationManager;
import jloda.util.ProgramExecutorService;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * a generic service
 * Daniel Huson, 1.2018
 *
 * @param <T>
 */
public class AService<T> extends Service<T> {
	private TaskWithProgressListener<T> task;
	private Callable<T> callable;
	private Pane progressParentPane;

	// Created on demand, never in the constructor: ProgressPane is a JavaFX Control, and loading Control
	// initialises the platform stylesheet, which throws "Toolkit not initialized" when no toolkit is running.
	// A service that never displays its progress - a command-line tool, a headless workflow run, a language
	// binding - must not be forced to start a toolkit just to compute. Only getProgressPane() may touch it.
	private ProgressPane progressPane;
	private boolean progressBarShowStopButton = true;

	private static boolean toolkitRunning = false;
	private ProgressListener inlineProgressListener;

	public AService() {
		this(null, null);
	}

	public AService(Callable<T> callable) {
		this(callable, null);
	}

	public AService(final Pane progressParentPane) {
		this(null, progressParentPane);
	}

	public AService(Callable<T> callable, final Pane progressParentPane) {
		super();
		setExecutor(ProgramExecutorService.getInstance());
		setCallable(callable);
		setProgressParentPane(progressParentPane);

		this.runningProperty().addListener((c, o, n) -> {
			if (getProgressParentPane() != null) {
				var pane = getProgressPane();
				RunAfterAWhile.apply(pane, () ->
						Platform.runLater(() -> {
							if (n) {
								if (!getProgressParentPane().getChildren().contains(pane))
									getProgressParentPane().getChildren().add(pane);
							} else {
								getProgressParentPane().getChildren().remove(pane);
							}
						}));
			}
		});
		setOnFailed(e -> NotificationManager.showError("Failed: "  // + Basic.getShortName(AService.this.getException().getClass())
													   + (AService.this.getException().getMessage() != null ? AService.this.getException().getMessage() : "")));
	}

	/**
	 * the progress pane, created on first use
	 *
	 * @return progress pane; requires a running JavaFX toolkit, so call this only when the progress is to be shown
	 */
	private ProgressPane getProgressPane() {
		if (progressPane == null) {
			progressPane = new ProgressPane(this);
			progressPane.setVisible(true);
			progressPane.showStopButtonProperty().set(progressBarShowStopButton);
		}
		return progressPane;
	}

	@Override
	protected TaskWithProgressListener<T> createTask() {
		task = new TaskWithProgressListener<>() {
			@Override
			public T call() throws Exception {
				return callable.call();
			}
		};
		return task;
	}

	public ProgressListener getProgressListener() {
		if (inlineProgressListener != null) // set only while runInline() is executing
			return inlineProgressListener;
		return (task != null ? task.getProgressListener() : null);
	}

	/**
	 * is the JavaFX toolkit running?
	 * <p>
	 * A javafx.concurrent.Service can only be started from the FX application thread, and there is no such
	 * thread until a toolkit has been started. Code that must work both in the application and headless - a
	 * command-line tool, a workflow run, a language binding - asks this and calls runInline() when it is false.
	 * Probing by calling Platform.runLater is the only reliable test: Platform.isFxApplicationThread() goes
	 * through Toolkit.getToolkit(), which is precisely what is not available.
	 *
	 * @return true if a toolkit is running
	 */
	public static boolean isToolkitRunning() {
		if (!toolkitRunning) { // once true it stays true; a toolkit cannot be shut down and restarted
			try {
				Platform.runLater(() -> {
				});
				toolkitRunning = true;
			} catch (IllegalStateException ignored) {
			}
		}
		return toolkitRunning;
	}

	/**
	 * runs the callable on the calling thread, bypassing the JavaFX service machinery
	 * <p>
	 * This is the headless path, for use when isToolkitRunning() is false. It is synchronous: when this
	 * returns, the computation has finished. getProgressListener() reports the given listener while it runs.
	 *
	 * @param progress progress listener to report, or null for a silent one
	 * @return the value computed by the callable, or null if there is none
	 */
	public T runInline(ProgressListener progress) throws Exception {
		inlineProgressListener = (progress != null ? progress : new ProgressSilent());
		try {
			return (callable != null ? callable.call() : null);
		} finally {
			inlineProgressListener = null;
		}
	}

	public void setCallable(Callable<T> callable) {
		this.callable = callable;
	}

	public Callable<T> getCallable() {
		return callable;
	}

	public Pane getProgressParentPane() {
		return progressParentPane;
	}

	public void setProgressParentPane(Pane progressParentPane) {
		if (this.progressParentPane != null && progressPane != null) // do not create the pane just to remove it
			this.progressParentPane.getChildren().remove(progressPane);
		this.progressParentPane = progressParentPane;
		//   if(progressParentPane!=null)
		//       progressParentPane.getChildren().add(progressPane);
	}

	public static <T> void run(Callable<T> callable, Consumer<T> runOnSucceeded, Consumer<Throwable> runOnFailed) {
		run(callable, runOnSucceeded, runOnFailed, null);
	}

	public static <T> void run(Callable<T> callable, Consumer<T> runOnSucceeded, Consumer<Throwable> runOnFailed, Pane progressParentPane) {
		var service = new AService<>(callable);
		if (progressParentPane != null)
			service.setProgressParentPane(progressParentPane);
		if (runOnSucceeded != null)
			service.setOnSucceeded(e -> runOnSucceeded.accept(service.getValue()));
		if (runOnFailed != null)
			service.setOnFailed(e -> runOnFailed.accept(service.getException()));
		service.start();
	}

	public void setProgressBarShowStopButton(boolean show) {
		progressBarShowStopButton = show; // remembered so it survives being set before the pane exists
		if (progressPane != null)
			progressPane.showStopButtonProperty().set(show);
	}
}
