/*
 * InlineTextPrompt.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.fx.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import jloda.fx.icons.MaterialIcons;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class InlineTextPrompt extends HBox {

	private final Label label = new Label();
	private final TextField textField = new TextField();
	private final Button cancelButton = new Button("x");

	public InlineTextPrompt(String promptText, String initialText) {
		super(8); // spacing

		setAlignment(Pos.CENTER_LEFT);
		setPadding(new Insets(4));
		getStyleClass().add("viewer-background");
		setStyle("-fx-border-color: gray; -fx-border-width: 1;");

		label.setText(promptText);
		textField.setText(initialText);
		cancelButton.setFocusTraversable(false);
		MaterialIcons.setIcon(cancelButton, MaterialIcons.cancel);

		getChildren().addAll(label, textField, cancelButton);
	}

	/**
	 * Shows this prompt inside the given parent pane and returns a future
	 * that completes when the user either presses Enter (OK) or cancels.
	 * <p>
	 * OK  -> Optional.of(text)
	 * Cancel / ESC -> Optional.empty()
	 */
	public CompletableFuture<Optional<String>> showAndWait(Pane parent) {
		CompletableFuture<Optional<String>> future = new CompletableFuture<>();

		if (!parent.getChildren().contains(this)) {
			parent.getChildren().add(this);
		}

		Runnable completeCancel = () -> {
			if (!future.isDone()) {
				future.complete(Optional.empty());
			}
		};

		Runnable completeOk = () -> {
			if (!future.isDone()) {
				future.complete(Optional.ofNullable(textField.getText()));
			}
		};

		// Enter in text field -> OK
		textField.setOnAction(e -> completeOk.run());

		// ESC -> cancel
		textField.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				completeCancel.run();
			}
		});

		// x button -> cancel
		cancelButton.setOnAction(e -> completeCancel.run());

		textField.focusedProperty().addListener((v, o, n) -> {
			if (!n)
				completeCancel.run();
		});

		// Focus the text field and select all when shown
		Platform.runLater(() -> {
			textField.requestFocus();
			textField.selectAll();
		});

		return future;
	}

	public TextField getTextField() {
		return textField;
	}

	public Label getLabel() {
		return label;
	}

	public Button getCancelButton() {
		return cancelButton;
	}
}