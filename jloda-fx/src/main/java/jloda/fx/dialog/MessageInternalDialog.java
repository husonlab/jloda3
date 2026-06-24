/*
 * ConfirmInternalDialog.java Copyright (C) 2026 Daniel H. Huson
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
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;

import java.util.Objects;

/**
 * Minimalistic internal message dialog that can be embedded in an AnchorPane.
 * <p>
 * Behavior:
 * - Construct with (host, title, prompt)
 * - show() adds this pane to host (if needed) and focuses it
 * - OK calls onOk.run() and then hides
 * - Cancel hides
 * - Losing focus hides
 * <p>
 * The class extends Pane so callers may set AnchorPane anchors before calling show().
 */
public class MessageInternalDialog extends Pane {
	private final AnchorPane host;

	private final VBox vBox;
	private final Button okButton;

	private boolean hiding = false;

	public MessageInternalDialog(AnchorPane host, String title, String message) {
		this.host = Objects.requireNonNull(host, "Host panel must not be null");

		try {
			getStylesheets().add(Objects.requireNonNull(getClass().getResource("message-dialog.css")).toExternalForm());
		} catch (Exception ignored) {
		}

		setPrefWidth(350);
		setPrefHeight(Pane.USE_COMPUTED_SIZE);

		vBox = new VBox(8);
		vBox.setPadding(new Insets(5, 10, 5, 10));

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

		if (message != null) {
				var textArea = new TextArea(message);
				textArea.getStyleClass().add("copyable-label");
				textArea.setEditable(false);
				textArea.setWrapText(true);
				textArea.setFocusTraversable(false);

				textArea.setPrefRowCount(Math.max(1, message.split("\\R", -1).length));
				textArea.setMinHeight(Region.USE_PREF_SIZE);
				textArea.setMaxHeight(Region.USE_PREF_SIZE);

				vBox.getChildren().add(textArea);
		}

		okButton = new Button("OK");
		okButton.setDefaultButton(true);
		MaterialIcons.setIcon(okButton, MaterialIcons.check);

		final var buttons = new HBox(10, okButton);
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

		setFocusTraversable(true);

		Platform.runLater(okButton::requestFocus);
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
		// Escape cancels
		addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
			if (ev.getCode() == KeyCode.ESCAPE) {
				ev.consume();
				hide();
			} else if (ev.getCode() == KeyCode.ENTER) {
				// Make ENTER behave like OK even if default button handling is impeded by focus
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
				Platform.runLater(() -> {
					if (getScene() == null) return;
					final var owner = getScene().getFocusOwner();
					if (owner == null || !isDescendantOfThis(owner)) {
						hide();
					}
				});
			}
		};

		focusedProperty().addListener(focusListener);
		vBox.focusedProperty().addListener(focusListener);
		okButton.focusedProperty().addListener(focusListener);
		okButton.setOnAction(e -> hide());
	}

	private boolean isDescendantOfThis(javafx.scene.Node node) {
		return node == this || BasicFX.getAllRecursively(this, n -> true).contains(node);
	}
}