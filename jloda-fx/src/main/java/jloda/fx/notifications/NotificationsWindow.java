/*
 *  NotificationsWindow.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.ProgramExecutorService;

public class NotificationsWindow {
	private static NotificationsWindow instance;

	private final Stage stage;
	private final NotificationsController controller;
	private final NotificationsPresenter presenter;

	private NotificationsWindow() {
		final ExtendedFXMLLoader<NotificationsController> extendedFXMLLoader = new ExtendedFXMLLoader<>(this.getClass());
		controller = extendedFXMLLoader.getController();

		stage = new Stage();
		stage.getIcons().setAll(ProgramProperties.getProgramIconsFX());
		stage.initStyle(StageStyle.UTILITY);
		// stage.setAlwaysOnTop(true);
		stage.initModality(Modality.WINDOW_MODAL);


		stage.setScene(new Scene(extendedFXMLLoader.getRoot()));
		stage.setTitle("Message Window - " + ProgramProperties.getProgramName());
		stage.setOnCloseRequest(e -> stage.hide());

		presenter = new NotificationsPresenter(this);

		ProgramExecutorService.submit(250, () -> Platform.runLater(() -> {
			stage.show();
			centerWindow(stage);
		}));
	}

	private static void centerWindow(Stage stage) {
		double x;
		double y;
		var other = MainWindowManager.getInstance().getLastFocusedMainWindow();
		if (other != null) {
			var otherStage = other.getStage();
			x = otherStage.getX() + (otherStage.getWidth() - stage.getWidth()) / 2;
			y = otherStage.getY() + (otherStage.getHeight() - stage.getHeight()) / 2;
			stage.setX(x);
			stage.setY(y);
		} else {
			var screenBounds = Screen.getPrimary().getVisualBounds();
			x = (screenBounds.getWidth() - stage.getWidth()) / 2;
			y = (screenBounds.getHeight() - stage.getHeight()) / 2;
		}
		stage.setX(x);
		stage.setY(y);
	}

	public NotificationsController getController() {
		return controller;
	}

	public Stage getStage() {
		return stage;
	}

	public static NotificationsWindow getInstance() {
		if (instance == null)
			instance = new NotificationsWindow();
		return instance;
	}

	public static void addMessage(NotificationManager.Mode mode, String message) {
		var controller = getInstance().getController();
		var listView = controller.getListVIew();
		var notification = new Notification(mode, message);
		listView.getItems().add(notification);
		Platform.runLater(() -> listView.scrollTo(notification));

		ProgramExecutorService.submit(250, () -> Platform.runLater(() -> {
			getInstance().getStage().show();
			getInstance().getStage().toFront();
		}));
	}
}
