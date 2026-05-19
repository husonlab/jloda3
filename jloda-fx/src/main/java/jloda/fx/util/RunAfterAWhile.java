/*
 * RunAfterAWhile.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.util.ProgramExecutorService;

import java.util.*;

/**
 * executes a runnable with a delay after the last time a given key has been supplied
 * Daniel Huson, 11.2021
 */
public class RunAfterAWhile {
	private static final RunAfterAWhile instance;
	public static final long DELAY = 200L;

	static {
		instance = new RunAfterAWhile();
	}

	private final Map<Object, Job> keyJobMap;

	private RunAfterAWhile() {
		keyJobMap = new HashMap<>();

		var timer = new Timer(true);
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (keyJobMap) {
					var time = System.currentTimeMillis();
					var toDelete = new ArrayList<>();
					for (var entry : keyJobMap.entrySet()) {
						var entryTime = entry.getValue().time();
						if (time > entryTime + DELAY) {
							toDelete.add(entry.getKey());
							var entryJob = entry.getValue().runnable();
							if (entryJob != null)
								ProgramExecutorService.submit(entryJob);
						}
					}
					toDelete.forEach(keyJobMap.keySet()::remove);
				}
			}
		}, DELAY / 2, DELAY / 2);
	}

	/**
	 * The runnable will be executed after a delay, unless the same key is submitted again
	 *
	 * @param key      the key
	 * @param runnable the runnable
	 */
	public static void apply(Object key, Runnable runnable) {
		synchronized (instance.keyJobMap) {
			instance.keyJobMap.put(key, new Job(System.currentTimeMillis(), runnable));
		}
	}

	public static void apply(Object key, Runnable runnable, long waitingTimeMilliSeconds) {
		synchronized (instance.keyJobMap) {
			instance.keyJobMap.put(key, new Job(System.currentTimeMillis() + Math.max(DELAY, waitingTimeMilliSeconds) - DELAY, runnable));
		}
	}

	/**
	 * The runnable will be executed after a delay, unless the same key is waiting, in which case nothing is run
	 *
	 * @param key
	 * @param runnable
	 */
	public static void applyOrClearIfAlreadyWaiting(Object key, Runnable runnable) {
		synchronized (instance.keyJobMap) {
			var job = instance.keyJobMap.get(key);
			if (job != null)
				instance.keyJobMap.put(key, new Job(job.time(), null));
			else
				instance.keyJobMap.put(key, new Job(System.currentTimeMillis(), runnable));
		}
	}

	/**
	 * The runnable will be executed in the FX thread after a delay, unless the same key is submitted again
	 *
	 * @param key      the key
	 * @param runnables 0 or more runnables
	 */
	public static void applyInFXThread(Object key, Runnable... runnables) {

		apply(key, () -> Platform.runLater(() -> {
			for (var runnable : runnables)
				runnable.run();
		}));
	}

	public static void applyInFXThreadOrClearIfAlreadyWaiting(Object key, Runnable runnable) {
		applyOrClearIfAlreadyWaiting(key, () -> Platform.runLater(runnable));
	}

	private record Job(long time, Runnable runnable, Runnable excuteOnSuccess) {
		public Job(long time, Runnable runnable) {
			this(time, runnable, null);
		}
	}
}
