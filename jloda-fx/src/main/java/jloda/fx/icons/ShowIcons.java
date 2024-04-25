/*
 * ShowIcons.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.icons;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jloda.fx.util.ClipboardUtils;

/**
 * display all the material icons
 * Daniel Huson, 4.2024
 */
public class ShowIcons extends Application {
	@Override
	public void start(Stage stage) throws Exception {
		var flowPane = new FlowPane();
		flowPane.setHgap(10);
		flowPane.setVgap(10);
		for (var icon : MaterialIcons.values()) {
			var vbox = new VBox(MaterialIcons.graphic(icon, ""), new Label(icon.name()));
			vbox.setOnMouseClicked(e -> ClipboardUtils.putString(icon.name()));
			flowPane.getChildren().add(vbox);
		}
		var doneButton = new Button("Done");
		doneButton.setOnAction(e -> Platform.exit());

		var root = new BorderPane();
		var scrollPane = new ScrollPane(flowPane);
		scrollPane.setFitToWidth(true);
		root.setCenter(scrollPane);
		root.setTop(new ToolBar(doneButton));
		stage.setScene(new Scene(root, 900, 800));
		stage.show();
	}
}
