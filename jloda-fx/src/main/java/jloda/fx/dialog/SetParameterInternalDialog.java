/*
 * SetParameterInternalDialog.java Copyright (C) 2026 Daniel H. Huson
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
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Minimalistic internal parameter dialog that can be embedded in an AnchorPane.
 * <p>
 * Behavior:
 * - Construct with (host, question, defaultEntry, applyConsumer)
 * - show() adds this pane to host (if needed) and focuses it
 * - OK calls applyConsumer with current text and then hides
 * - Cancel hides
 * - Losing focus hides
 * <p>
 * The class extends Pane so callers may set AnchorPane anchors before calling show().
 */
public class SetParameterInternalDialog extends Pane {
	private final AnchorPane host;
	private final Consumer<String> applyConsumer;

	private final VBox vBox;
	private final TextField textField;
	private final Button okButton;
	private final Button cancelButton;

	private boolean hiding = false;

	public SetParameterInternalDialog(AnchorPane host, String title, String prompt, String defaultEntry, Consumer<String> apply) {
		this.host = Objects.requireNonNull(host, "Host panel must not be null");
		this.applyConsumer = Objects.requireNonNull(apply, "Apply consumer must not be null");

		setPrefWidth(350);

		vBox = new VBox(5);
		vBox.setPadding(new Insets(5, 10, 5, 10));

		// Minimal card look (works with or without external CSS)
		vBox.setStyle("""
				-fx-background-color: -fx-control-inner-background;
				-fx-border-color: -fx-box-border;
				-fx-border-radius: 8;
				-fx-background-radius: 8;
				-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.20), 12, 0.2, 0, 2);
				""");

		if (title != null) {
			var titleLabel = new Label(title);
			titleLabel.setWrapText(true);
			titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
			vBox.getChildren().add(titleLabel);
		}

		textField = new TextField(defaultEntry != null ? defaultEntry : "");
		textField.setMaxWidth(Double.MAX_VALUE);

		if (prompt != null) {
			if (!prompt.endsWith(":"))
				prompt = prompt + ":";

			var questionLabel = new Label(prompt);
			questionLabel.setWrapText(true);

			var hBox = new HBox(10); // spacing between label and text field
			hBox.setAlignment(Pos.CENTER_LEFT); // vertical centering
			hBox.getChildren().addAll(questionLabel, textField);
			HBox.setHgrow(textField, Priority.ALWAYS);

			vBox.getChildren().add(hBox);
		} else {
			vBox.getChildren().add(textField);
		}

		cancelButton = new Button("Cancel");
		cancelButton.setCancelButton(true);
		MaterialIcons.setIcon(cancelButton, MaterialIcons.cancel);

		okButton = new Button("OK");
		okButton.setDefaultButton(true);
		MaterialIcons.setIcon(okButton, MaterialIcons.check);

		final var buttons = new HBox(10, cancelButton, okButton);
		buttons.setAlignment(Pos.CENTER_RIGHT);

		vBox.getChildren().add(buttons);

		getChildren().add(vBox);

		wireBehavior();

		setOnMousePressed(e -> {
			requestFocus();
			e.consume();
		});
	}

	/**
	 * Adds this dialog to the host (if necessary) and focuses it.
	 * Caller may set AnchorPane anchors on this instance before calling show().
	 */
	public void show() {
		if (!host.getChildren().contains(this)) {
			host.getChildren().add(this);
		}

		// If caller didn't set anchors, default them
		if (AnchorPane.getLeftAnchor(this) == null &&
			AnchorPane.getRightAnchor(this) == null &&
			AnchorPane.getTopAnchor(this) == null &&
			AnchorPane.getBottomAnchor(this) == null) {
			AnchorPane.setLeftAnchor(this, 50.0);
			AnchorPane.setTopAnchor(this, 100.0);
		}

		// Ensure we can receive focus so "focus lost" works reliably
		setFocusTraversable(true);

		Platform.runLater(() -> {
			// request focus into the text field and select all
			textField.requestFocus();
			textField.selectAll();
		});
	}

	/**
	 * Removes this dialog from its host.
	 */
	public void hide() {
		if (hiding) return;
		hiding = true;
		host.getChildren().remove(this);
		hiding = false;
	}

	/* ----------------------- internals ----------------------- */

	private void wireBehavior() {
		okButton.setOnAction(e -> {
			final var text = textField.getText();
			// Only call apply on OK:
			try {
				applyConsumer.accept(text);
			} finally {
				hide();
			}
		});

		cancelButton.setOnAction(e -> hide());

		// Enter in text field triggers OK
		textField.setOnAction(e -> okButton.fire());

		// Escape cancels
		addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
			if (ev.getCode() == KeyCode.ESCAPE) {
				ev.consume();
				hide();
			}
		});

		/*
		 * Hide when dialog loses focus:
		 * - We track focus within the dialog (this Pane or any child).
		 * - When focus moves outside of the dialog entirely, we hide.
		 */
		final var focusListener = (javafx.beans.value.ChangeListener<Boolean>) (v, o, n) -> {
			if (!n) {
				// Defer to allow focus to settle; then test if focus is outside this dialog.
				Platform.runLater(() -> {
					if (getScene() == null) return;
					final var owner = getScene().getFocusOwner();
					if (owner == null || !isDescendantOfThis(owner)) {
						hide();
					}
				});
			}
		};

		// Listen on this pane and key children to robustly detect focus leaving
		focusedProperty().addListener(focusListener);
		vBox.focusedProperty().addListener(focusListener);
		textField.focusedProperty().addListener(focusListener);
		okButton.focusedProperty().addListener(focusListener);
		cancelButton.focusedProperty().addListener(focusListener);
	}

	private boolean isDescendantOfThis(javafx.scene.Node node) {
		return node == this || BasicFX.getAllRecursively(this, n -> true).contains(node);
	}

	public TextField getTextField() {
		return textField;
	}
}