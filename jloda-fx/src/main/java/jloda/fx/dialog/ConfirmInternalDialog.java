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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;

import java.util.Objects;

/**
 * Minimalistic internal confirmation dialog that can be embedded in an AnchorPane.
 * <p>
 * Behavior:
 * - Construct with (host, title, prompt, onOk)
 * - show() adds this pane to host (if needed) and focuses it
 * - OK calls onOk.run() and then hides
 * - Cancel hides
 * - Losing focus hides
 * <p>
 * The class extends Pane so callers may set AnchorPane anchors before calling show().
 */
public class ConfirmInternalDialog extends Pane {
	private final AnchorPane host;
	private final Runnable onOk;

	private final VBox vBox;
	private final Button okButton;
	private final Button cancelButton;

	private boolean hiding = false;

	public ConfirmInternalDialog(AnchorPane host, String title, String prompt, Runnable onOk) {
		this.host = Objects.requireNonNull(host, "Host panel must not be null");
		this.onOk = Objects.requireNonNull(onOk, "OK action must not be null");

		setPrefWidth(350);

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

		if (prompt != null) {
			var promptLabel = new Label(prompt);
			promptLabel.setWrapText(true);
			vBox.getChildren().add(promptLabel);
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
		okButton.setOnAction(e -> {
			try {
				onOk.run();
			} finally {
				hide();
			}
		});

		cancelButton.setOnAction(e -> hide());

		// Escape cancels
		addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
			if (ev.getCode() == KeyCode.ESCAPE) {
				ev.consume();
				hide();
			} else if (ev.getCode() == KeyCode.ENTER) {
				// Make ENTER behave like OK even if default button handling is impeded by focus
				ev.consume();
				okButton.fire();
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
		cancelButton.focusedProperty().addListener(focusListener);
	}

	private boolean isDescendantOfThis(javafx.scene.Node node) {
		return node == this || BasicFX.getAllRecursively(this, n -> true).contains(node);
	}
}