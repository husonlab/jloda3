/*
 *  NotificationsController.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.notifications;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import jloda.fx.icons.MaterialIcons;

public class NotificationsController {

	@FXML
	private Button clearButton;

	@FXML
	private Button copyButton;

	@FXML
	private Button hideButton;

	@FXML
	private ListView<Notification> listVIew;

	@FXML
	private AnchorPane root;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(clearButton, "clear");
		MaterialIcons.setIcon(copyButton, "copy");
	}

	public Button getClearButton() {
		return clearButton;
	}

	public Button getCopyButton() {
		return copyButton;
	}

	public Button getHideButton() {
		return hideButton;
	}

	public ListView<Notification> getListVIew() {
		return listVIew;
	}

	public AnchorPane getRoot() {
		return root;
	}
}
