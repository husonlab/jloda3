/*
 * ProgramExecutorService.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * program executor service
 * all concurrent services should use this executor service
 * Daniel Huson, 12/11/16.
 */
public class ProgramExecutorService {
	private static ExecutorService instance;
	private static int numberOfCoresToUse = ProgramProperties.get("NumberOfCores", 8); //  number of threads to use by a parallel algorithm

	/**
	 * get the program-wide executor service
	 *
	 * @return executor service
	 */
	public static ExecutorService getInstance() {
		if (instance == null) {
			// Daemon threads: a shared worker pool must not be a reason for the JVM to stay
			// alive. A cached pool keeps idle workers for 60 seconds, and because they used
			// to be non-daemon, any process that had run a parallel algorithm sat for a full
			// minute after finishing before it could exit - measured at 60.4s for a
			// one-second consensus-network computation. The desktop application never
			// noticed because it exits through System.exit, and the command-line tools call
			// System.exit too, which is what hid this; a library cannot do either.
			// Callers await their own work (latches, invokeAll), so nothing here relies on
			// the pool holding the JVM open.
			instance = Executors.newCachedThreadPool(runnable -> {
				var thread = new Thread(runnable);
				thread.setDaemon(true);
				return thread;
			});
		}
		return instance;
	}

	public static void setNumberOfCoresToUse(int numberOfCoresToUse) {
		ProgramExecutorService.numberOfCoresToUse = (numberOfCoresToUse > 0 ? numberOfCoresToUse : Runtime.getRuntime().availableProcessors());
		ProgramProperties.put("NumberOfCores", ProgramExecutorService.numberOfCoresToUse);
	}

	public static int getNumberOfCoresToUse() {
		return numberOfCoresToUse;
	}

	public static void submit(Runnable runnable) {
		getInstance().submit(runnable);
	}

	public static void submit(long delayInMilliseconds, Runnable runnable) {
		getInstance().submit(() -> {
			try {
				Thread.sleep(delayInMilliseconds);
			} catch (InterruptedException ignored) {
			}
			runnable.run();
		});
	}
}