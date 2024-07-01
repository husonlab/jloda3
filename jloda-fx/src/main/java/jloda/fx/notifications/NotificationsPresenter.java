/*
 *  NotificationsPresenter.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.ClipboardUtils;

import java.text.SimpleDateFormat;

public class NotificationsPresenter {

	public NotificationsPresenter(NotificationsWindow window) {
		var controller = window.getController();

		controller.getListVIew().setCellFactory(listView -> new NotificationListCell());

		controller.getClearButton().setOnAction(e -> controller.getListVIew().getItems().clear());

		controller.getCopyButton().setOnAction(e -> {
			var buf = new StringBuilder();
			for (var item : controller.getListVIew().getItems()) {
				buf.append(item.text()).append("\n");
			}
			ClipboardUtils.putString(buf.toString());
		});

		controller.getHideButton().setOnAction(e -> window.getStage().hide());
	}

	static class NotificationListCell extends ListCell<Notification> {
		public NotificationListCell() {
		}

		@Override
		protected void updateItem(Notification notification, boolean empty) {
			super.updateItem(notification, empty);

			if (empty || notification == null) {
				setText(null);
				setGraphic(null);
			} else {
				var hBox = new HBox(10); // spacing between icon and text
				var messageText = new Text(notification.text());
				var timestampText = new Text(formatTimestamp(notification.timestamp()));

				// Set small gray font for timestamp
				timestampText.setStyle("-fx-font-size: 10px; -fx-fill: gray;");

				var icon = switch (notification.mode()) {
					case confirmation -> MaterialIcons.graphic(MaterialIcons.task_alt, "-fx-font-size:  32;");
					case warning -> MaterialIcons.graphic(MaterialIcons.warning, "-fx-font-size:  32;");
					case information -> MaterialIcons.graphic(MaterialIcons.info, "-fx-font-size:  32;");
					case error -> MaterialIcons.graphic(MaterialIcons.error, "-fx-font-size:  32;");
				};

				var vBox = new VBox(messageText, timestampText);
				hBox.getChildren().addAll(icon, vBox);
				setGraphic(hBox);
			}
		}

		private String formatTimestamp(long timestamp) {
			var sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			return sdf.format(timestamp);
		}
	}
}
