/*
 * SetupDropTarget.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SetupDropTarget {
	public static void apply(Node target,
							 Predicate<File> acceptableFile,
							 Consumer<List<File>> openFiles,
							 Predicate<String> acceptableText,
							 Consumer<String> openText) {

		Objects.requireNonNull(target);
		Objects.requireNonNull(acceptableFile);
		Objects.requireNonNull(openFiles);
		Objects.requireNonNull(acceptableText);
		Objects.requireNonNull(openText);

		target.addEventHandler(DragEvent.DRAG_ENTERED, event -> {
			if (isAcceptable(event.getDragboard(), acceptableFile, acceptableText))
				setDragAccept(target, true);
			event.consume();
		});

		target.addEventHandler(DragEvent.DRAG_OVER, event -> {
			if (isAcceptable(event.getDragboard(), acceptableFile, acceptableText))
				event.acceptTransferModes(TransferMode.COPY);
			event.consume();
		});

		target.addEventHandler(DragEvent.DRAG_EXITED, event -> {
			setDragAccept(target, false);
			event.consume();
		});

		target.addEventHandler(DragEvent.DRAG_DROPPED, event -> {
			var db = event.getDragboard();
			boolean success = false;

			setDragAccept(target, false);

			if (db.hasFiles()) {
				var files = db.getFiles().stream()
						.filter(acceptableFile)
						.toList();

				if (!files.isEmpty()) {
					openFiles.accept(files);
					success = true;
				}
			} else if (db.hasString()) {
				var text = db.getString();
				if (text != null && acceptableText.test(text)) {
					openText.accept(text);
					success = true;
				}
			}

			event.setDropCompleted(success);
			event.consume();
		});
	}

	private static boolean isAcceptable(Dragboard db,
										Predicate<File> acceptableFile,
										Predicate<String> acceptableText) {
		if (db.hasFiles())
			return !db.getFiles().isEmpty() && db.getFiles().stream().allMatch(acceptableFile);

		if (db.hasString()) {
			var text = db.getString();
			return text != null && acceptableText.test(text);
		}

		return false;
	}

	private static void setDragAccept(Node node, boolean accept) {
		if (accept) {
			node.setEffect(SelectionEffectBlue.getInstance());
		} else {
			node.setEffect(null);
		}
	}
}