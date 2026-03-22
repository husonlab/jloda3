/*
 *  RunThrottled.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.util;

import javafx.application.Platform;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Throttles FX-thread execution: at most one run per interval per key.
 * <p>
 * Semantics:
 * - First call runs immediately (leading edge).
 * - If further calls arrive within the interval, exactly one additional run
 * will happen after the interval ends (trailing edge), using the latest runnable.
 * <p>
 * Thread-safe: can be called from any thread.
 */
public final class RunThrottled {
	private RunThrottled() {
	}

	// Single scheduler thread for all throttles (daemon).
	private static final ScheduledExecutorService SCHEDULER =
			Executors.newSingleThreadScheduledExecutor(r -> {
				var t = new Thread(r, "RunThrottled");
				t.setDaemon(true);
				return t;
			});

	private static final class State {
		final long intervalMillis;

		// next runnable to execute (latest wins)
		volatile Runnable latest;

		// whether we're currently inside a throttle window
		final AtomicBoolean inWindow = new AtomicBoolean(false);

		// whether a trailing execution is needed
		final AtomicBoolean trailingRequested = new AtomicBoolean(false);

		// scheduled future for ending the current window
		volatile ScheduledFuture<?> windowEndFuture;

		State(long intervalMillis) {
			this.intervalMillis = intervalMillis;
		}
	}

	private static final Map<Object, State> STATES = new ConcurrentHashMap<>();

	public static void apply(Object key, Runnable runnable) {
		apply(key, runnable, 200);
	}

	/**
	 * Request execution of runnable on the FX thread, throttled by key.
	 *
	 * @param key            distinct channel (e.g. "log-flush")
	 * @param runnable       code to run on FX thread (latest runnable wins)
	 * @param intervalMillis throttle interval in milliseconds (e.g. 200)
	 */
	public static void apply(Object key, Runnable runnable, long intervalMillis) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(runnable, "runnable");
		if (intervalMillis <= 0) {
			runOnFx(runnable);
			return;
		}

		final State s = STATES.compute(key, (k, existing) ->
				(existing == null || existing.intervalMillis != intervalMillis) ?
						new State(intervalMillis) : existing);

		s.latest = runnable;

		// If we're not in a window, run immediately and open a window.
		if (s.inWindow.compareAndSet(false, true)) {
			// leading edge
			runOnFx(s.latest);

			// schedule end of window
			s.windowEndFuture = SCHEDULER.schedule(() -> closeWindowAndMaybeRunTrailing(key, s),
					s.intervalMillis, TimeUnit.MILLISECONDS);
		} else {
			// we are in a window; request trailing run
			s.trailingRequested.set(true);
		}
	}

	/**
	 * Cancel any pending trailing execution for this key and remove state.
	 */
	public static void cancel(String key) {
		State s = STATES.remove(key);
		if (s != null) {
			var f = s.windowEndFuture;
			if (f != null) f.cancel(false);
		}
	}

	/**
	 * Shut down scheduler (optional). Call on application exit if desired.
	 */
	public static void shutdown() {
		SCHEDULER.shutdownNow();
		STATES.clear();
	}

	private static void closeWindowAndMaybeRunTrailing(Object key, State s) {
		// close current window
		s.inWindow.set(false);

		// If a trailing run was requested during the window, run once and open new window.
		if (s.trailingRequested.getAndSet(false)) {
			// Re-enter window (if another thread already reopened, let it handle)
			if (s.inWindow.compareAndSet(false, true)) {
				runOnFx(s.latest);

				s.windowEndFuture = SCHEDULER.schedule(() -> closeWindowAndMaybeRunTrailing(key, s),
						s.intervalMillis, TimeUnit.MILLISECONDS);
			}
		} else {
			// No trailing work: optionally drop idle state to avoid map growth.
			// Only remove if still same instance mapped for this key.
			STATES.remove(key, s);
		}
	}

	private static void runOnFx(Runnable r) {
		if (r == null) return;
		if (Platform.isFxApplicationThread()) r.run();
			else Platform.runLater(r);
	}

	public static void main(String[] args) {
		var obj = new Object();
		for (var i = 0; i < 2_000_000_000; i++) {
			var id = i;
			RunThrottled.apply(obj, () -> System.err.println(id), 1000);
		}
	}
}