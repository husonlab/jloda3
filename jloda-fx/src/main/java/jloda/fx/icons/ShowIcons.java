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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jloda.fx.util.AutoCompleteComboBox;
import jloda.fx.util.ClipboardUtils;

import java.util.HashMap;
import java.util.Map;

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

		ObservableList<String> list = FXCollections.observableArrayList();
		Map<String, Pane> namePaneMap = new HashMap<>();

		for (var icon : MaterialIcons.values()) {
			var vbox = new VBox(MaterialIcons.graphic(icon, ""), new Label(icon.name()));
			vbox.setOnMouseClicked(e -> ClipboardUtils.putString(icon.name()));
			Tooltip.install(vbox, new Tooltip(icon.name() + ": click to copy"));
			flowPane.getChildren().add(vbox);
			list.add(icon.name());
			namePaneMap.put(icon.name(), vbox);
		}
		var filteredItems = new FilteredList<>(list, p -> true);

		var comboBox = new ComboBox<>(filteredItems);
		AutoCompleteComboBox.install(comboBox);

		var doneButton = new Button("Done");
		doneButton.setOnAction(e -> Platform.exit());

		var root = new BorderPane();
		var scrollPane = new ScrollPane(flowPane);
		scrollPane.setFitToWidth(true);
		root.setCenter(scrollPane);
		root.setTop(new ToolBar(comboBox, new Separator(Orientation.VERTICAL), doneButton));
		stage.setScene(new Scene(root, 900, 800));
		stage.show();

		if (true)
			comboBox.valueProperty().addListener((v, o, n) -> {
				if (n != null && namePaneMap.containsKey(n)) {
					Platform.runLater(() -> scrollToPane(scrollPane, flowPane, namePaneMap.get(n)));
				}
			});
	}

	private static void scrollToPane(ScrollPane scrollPane, FlowPane flowPane, Pane targetPane) {
		var targetBounds = targetPane.getBoundsInParent();

		var flowBounds = flowPane.getLayoutBounds();

		double vPosition = (targetBounds.getMinY() - flowBounds.getMinY()) / (flowBounds.getHeight() - scrollPane.getViewportBounds().getHeight());
		double hPosition = (targetBounds.getMinX() - flowBounds.getMinX()) / (flowBounds.getWidth() - scrollPane.getViewportBounds().getWidth());

		vPosition = Math.max(0, Math.min(1, vPosition));
		hPosition = Math.max(0, Math.min(1, hPosition));

		scrollPane.setVvalue(vPosition);
		scrollPane.setHvalue(hPosition);
	}
}
