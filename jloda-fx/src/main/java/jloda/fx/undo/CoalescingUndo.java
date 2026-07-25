/*
 * CoalescingUndo.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.undo;

import javafx.application.Platform;
import javafx.beans.property.Property;
import jloda.fx.util.RunAfterAWhile;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * records changes of a property as undoable items, coalescing a rapid burst of changes, such as
 * produced by holding down a spinner arrow, into a single undoable item that covers the whole burst
 * <p>
 * Daniel Huson, 7.2026
 */
public class CoalescingUndo {
	public static final long DELAY = 500L;

	/**
	 * records changes of the given property as undoable items, coalescing any changes that follow
	 * each other more closely than the default delay
	 */
	public static <T> void track(UndoManager undoManager, String name, Property<T> property) {
		track(undoManager, name, property, DELAY);
	}

	/**
	 * records changes of the given property as undoable items, coalescing any changes that follow
	 * each other more closely than the given delay. The item added once the changes have stopped
	 * runs from the value held before the first change to the value held after the last one
	 *
	 * @param delayMilliSeconds milliseconds of quiet required before the undoable item is added
	 */
	public static <T> void track(UndoManager undoManager, String name, Property<T> property, long delayMilliSeconds) {
		var burstActive = new AtomicBoolean(false);
		var valueBeforeBurst = new AtomicReference<T>();

		property.addListener((v, o, n) -> {
			// must be checked here, not when the item is added, as the undo will long be over by then:
			if (!undoManager.isRecordChanges() || undoManager.isPerformingUndoOrRedo())
				return;
			if (burstActive.compareAndSet(false, true))
				valueBeforeBurst.set(o);
			RunAfterAWhile.apply(property, () -> Platform.runLater(() -> {
				burstActive.set(false);
				var oldValue = valueBeforeBurst.get();
				var newValue = property.getValue();
				if (!Objects.equals(oldValue, newValue))
					undoManager.add(name, property, oldValue, newValue);
			}), delayMilliSeconds);
		});
	}
}
