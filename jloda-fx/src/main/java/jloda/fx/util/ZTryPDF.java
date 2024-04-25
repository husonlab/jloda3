/*
 * ZTryPDF.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.window.MainWindowManager;
import jloda.util.Basic;

import java.io.File;
import java.io.IOException;

public class ZTryPDF extends Application {
	@Override
	public void start(Stage stage) throws Exception {
		var label = new RichTextLabel("Hello <b>world!");
		label.setRotate(45);
		label.setTranslateX(100);
		label.setTranslateY(100);

		var rectangle = new Rectangle(20, 20, 100, 100);
		rectangle.setFill(Color.LIGHTBLUE);
		rectangle.setStroke(Color.ORANGE);

		var button = new Button("WHAT?");

		button.setOnAction(e -> {
			MainWindowManager.setUseDarkTheme(!MainWindowManager.isUseDarkTheme());
			MainWindowManager.ensureDarkTheme(stage, MainWindowManager.isUseDarkTheme());
		});

		var listView = new ListView<Button>();
		listView.setTranslateX(150);
		listView.setTranslateY(150);
		listView.getItems().add(button);
		button.setTextFill(null);
		button.setStyle("-fx-text-fill: GREEN;");

		var pane = new Pane();
		pane.getChildren().add(rectangle);
		pane.getChildren().add(label);
		for (var i = 0; i < 10; i++) {
			var other = new RichTextLabel("text " + i);
			other.setTranslateX(10);
			other.setTranslateY(100 + 20 * i);
			pane.getChildren().add(other);
		}
		pane.getChildren().add(listView);

		var pdfButton = new Button("PDF");
		pdfButton.setOnAction(e -> {
			try {
				System.err.println("Saving to: try.pdf");
				SaveToPDF.apply(pane, new File("/Users/huson/Desktop/try.pdf"));
			} catch (IOException ex) {
				Basic.caught(ex);
			}
		});

		var root = new BorderPane();
		root.setTop(new ToolBar(pdfButton));
		root.setCenter(new StackPane(pane));

		stage.setScene(new Scene(root, 800, 800));
		stage.sizeToScene();
		stage.show();
	}
}
