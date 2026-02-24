/*
 * PrintStreamForTextArea.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.message;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A PrintStream that appends to a JavaFX TextArea efficiently:
 *  - throttles FX updates (not debounced: continuous output still updates)
 *  - caps displayed lines
 *  - preserves newline semantics (only breaks lines on '\n')
 *  Daniel Huson, 2019, updated 2026
 */
public class PrintStreamForTextArea extends PrintStream {
	// ---- throttle implementation (leading + trailing) ----
	private static final ScheduledExecutorService SCHEDULER =
			Executors.newSingleThreadScheduledExecutor(r -> {
				var t = new Thread(r, "PrintStreamForTextArea-Throttler");
				t.setDaemon(true);
				return t;
			});

	private static final class ThrottleState {
		final long intervalMillis;
		volatile Runnable latest;
		final AtomicBoolean inWindow = new AtomicBoolean(false);
		final AtomicBoolean trailingRequested = new AtomicBoolean(false);
		volatile ScheduledFuture<?> windowEndFuture;

		ThrottleState(long intervalMillis) {
			this.intervalMillis = intervalMillis;
		}
	}

	private static final ConcurrentHashMap<Object, ThrottleState> THROTTLES = new ConcurrentHashMap<>();

	private static void runThrottled(Object key, long intervalMillis, Runnable runnable) {
		if (intervalMillis <= 0) {
			runOnFx(runnable);
			return;
		}

		var s = THROTTLES.compute(key, (k, existing) ->
				(existing == null || existing.intervalMillis != intervalMillis) ? new ThrottleState(intervalMillis) : existing
		);
		s.latest = runnable;

		if (s.inWindow.compareAndSet(false, true)) {
			// leading edge
			runOnFx(s.latest);

			s.windowEndFuture = SCHEDULER.schedule(() -> closeWindowAndMaybeRunTrailing(key, s),
					s.intervalMillis, TimeUnit.MILLISECONDS);
		} else {
			s.trailingRequested.set(true);
		}
	}

	private static void closeWindowAndMaybeRunTrailing(Object key, ThrottleState s) {
		s.inWindow.set(false);

		if (s.trailingRequested.getAndSet(false)) {
			if (s.inWindow.compareAndSet(false, true)) {
				runOnFx(s.latest);
				s.windowEndFuture = SCHEDULER.schedule(() -> closeWindowAndMaybeRunTrailing(key, s),
						s.intervalMillis, TimeUnit.MILLISECONDS);
			}
		} else {
			THROTTLES.remove(key, s);
		}
	}

	private static void runOnFx(Runnable r) {
		if (r == null) return;
		if (Platform.isFxApplicationThread()) r.run();
		else Platform.runLater(r);
	}

	// ---- logger state ----
	private final TextArea textArea;
	private final int maxLines;
	private final long throttleMillis;

	// incoming chunks (any thread) → buffered
	private final StringBuilder pending = new StringBuilder();
	private final Object pendingLock = new Object();

	// parsed lines (FX thread only)
	private final Deque<String> lines = new ArrayDeque<>();
	private final StringBuilder partialLine = new StringBuilder();

	// unique throttle key per instance
	private final Object throttleKey = new Object();

	// avoid charset surprises when bytes are written
	private final Charset charset = Charset.defaultCharset();

	public PrintStreamForTextArea(TextArea textArea) {
		this(textArea, 1000, 100);
	}

	public PrintStreamForTextArea(TextArea textArea, int maxLines, long throttleMillis) {
		super(System.out);
		this.textArea = Objects.requireNonNull(textArea, "textArea");
		this.maxLines = Math.max(1, maxLines);
		this.throttleMillis = Math.max(0, throttleMillis);
	}

	// ---- PrintStream API overrides ----

	@Override
	public void print(String s) {
		if (s == null) s = "null";
		enqueue(s);
	}

	@Override
	public void println(String s) {
		if (s == null) s = "null";
		enqueue(s);
		enqueue("\n");
	}

	@Override
	public void print(Object obj) {
		enqueue(String.valueOf(obj));
	}

	@Override
	public void println(Object obj) {
		enqueue(String.valueOf(obj));
		enqueue("\n");
	}

	@Override
	public void print(boolean b) {
		enqueue(String.valueOf(b));
	}

	@Override
	public void println(boolean b) {
		enqueue(String.valueOf(b));
		enqueue("\n");
	}

	@Override
	public void print(int i) {
		enqueue(String.valueOf(i));
	}

	@Override
	public void println(int i) {
		enqueue(String.valueOf(i));
		enqueue("\n");
	}

	@Override
	public void print(long l) {
		enqueue(String.valueOf(l));
	}

	@Override
	public void println(long l) {
		enqueue(String.valueOf(l));
		enqueue("\n");
	}

	@Override
	public void print(float f) {
		enqueue(String.valueOf(f));
	}

	@Override
	public void println(float f) {
		enqueue(String.valueOf(f));
		enqueue("\n");
	}

	@Override
	public void print(double d) {
		enqueue(String.valueOf(d));
	}

	@Override
	public void println(double d) {
		enqueue(String.valueOf(d));
		enqueue("\n");
	}

	@Override
	public void print(char c) {
		enqueue(String.valueOf(c));
	}

	@Override
	public void println(char c) {
		enqueue(String.valueOf(c));
		enqueue("\n");
	}

	@Override
	public void print(char[] s) {
		enqueue(String.valueOf(s));
	}

	@Override
	public void println(char[] s) {
		enqueue(String.valueOf(s));
		enqueue("\n");
	}

	@Override
	public void write(byte[] buf, int off, int len) {
		if (buf == null) return;
		enqueue(new String(buf, off, len, charset));
	}

	@Override
	public void flush() {
		// ensure pending data is shown promptly
		requestFlush();
	}

	// ---- internals ----

	private void enqueue(String chunk) {
		if (chunk == null || chunk.isEmpty()) return;

		synchronized (pendingLock) {
			pending.append(chunk);
		}
		requestFlush();
	}

	private void requestFlush() {
		runThrottled(throttleKey, throttleMillis, this::flushToTextAreaOnFx);
	}

	/**
	 * Runs on FX thread. Drains pending buffer, parses lines, applies maxLines, updates TextArea once.
	 */
	private void flushToTextAreaOnFx() {
		final String chunk;
		synchronized (pendingLock) {
			if (pending.isEmpty()) return;
			chunk = pending.toString();
			pending.setLength(0);
		}

		// parse chunk into complete lines + partial (split on '\n')
		int start = 0;
		int idx;
		while ((idx = chunk.indexOf('\n', start)) >= 0) {
			partialLine.append(chunk, start, idx);
			lines.addLast(partialLine.toString());
			partialLine.setLength(0);
			start = idx + 1;
		}
		if (start < chunk.length()) {
			partialLine.append(chunk.substring(start));
		}

		// cap line count (drop from head)
		while (lines.size() > maxLines) {
			lines.removeFirst();
		}

		// rebuild visible text (bounded)
		var sb = new StringBuilder();
		for (var line : lines) {
			sb.append(line).append('\n');
		}
		// show current partial line without forcing a newline
		sb.append(partialLine);

		textArea.setText(sb.toString());
		textArea.positionCaret(textArea.getLength());
		textArea.setScrollTop(Double.MAX_VALUE);
	}

	// keep your old API surface if you rely on it
	public void setError() {
	}

	public boolean checkError() {
		return false;
	}

	/**
	 * Clears all buffered and displayed text immediately.
	 * Safe to call from any thread.
	 */
	public void clear() {
		synchronized (pendingLock) {
			pending.setLength(0);
		}

		// clear throttle state for this instance
		ThrottleState s = THROTTLES.remove(throttleKey);
		if (s != null && s.windowEndFuture != null) {
			s.windowEndFuture.cancel(false);
		}

		runOnFx(() -> {
			lines.clear();
			partialLine.setLength(0);
			textArea.clear();
		});
	}
}