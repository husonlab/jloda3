/*
 * Legend.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.control;

import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import jloda.fx.util.ColorSchemeManager;
import jloda.fx.util.FuzzyBoolean;

import java.util.function.BiConsumer;

import static jloda.fx.util.FuzzyBoolean.False;
import static jloda.fx.util.FuzzyBoolean.True;

/**
 * simple color scheme legend and scale indicator
 * Daniel Huson, 4.2022
 */
public class Legend extends StackPane {
	public enum ScalingType {
		none, linear, sqrt, log;

		public double apply(double value) {
			return switch (this) {
				case none -> 0.0;
				case linear -> value;
				case sqrt -> Math.sqrt(value);
				case log -> Math.log(value);
			};
		}
	}

	public enum PatchShape {Circle, Square}

	private final StringProperty title = new SimpleStringProperty(this, "title", null);
	private final ObjectProperty<ScalingType> scalingType = new SimpleObjectProperty<>(this, "scalingType", ScalingType.none);
	private final ObjectProperty<PatchShape> patchShape = new SimpleObjectProperty<>(this, "colorPatchShape", PatchShape.Circle);
	private final DoubleProperty circleMinSize = new SimpleDoubleProperty(64);
	private final DoubleProperty unitRadius = new SimpleDoubleProperty(this, "unitRadius", 0);
	private final DoubleProperty scale = new SimpleDoubleProperty(this, "scaleProperty", 1.0);

	private final ObjectProperty<FuzzyBoolean> show = new SimpleObjectProperty<>(this, "show", True);

	private final StringProperty colorSchemeName = new SimpleStringProperty();
	private final ObservableList<String> labels;
	private final ObservableSet<String> active = FXCollections.observableSet();
	private final Pane pane;

	private BiConsumer<MouseEvent, String> clickOnLabel;

	public Legend(ObservableList<String> labels, String colorSchemeName, Orientation orientation) {
		this.labels = labels;
		setColorSchemeName(colorSchemeName);
		this.labels.addListener((InvalidationListener) e -> update());
		this.active.addListener((InvalidationListener) e -> update());
		this.show.addListener(e -> update());
		colorSchemeNameProperty().addListener(e -> update());
		this.unitRadius.addListener(e -> update());
		scale.addListener(e -> update());

		if (orientation == Orientation.HORIZONTAL) {
			var hbox = new HBox();
			hbox.setSpacing(5);
			pane = hbox;
		} else {
			var vbox = new VBox();
			vbox.setSpacing(6);
			pane = vbox;
			vbox.setAlignment(Pos.CENTER);
		}
		getChildren().setAll(pane);

		setOnMouseClicked(e -> {
			if (e.isStillSincePress() && getClickOnLabel() != null) {
				if (!e.isShiftDown()) {
					getClickOnLabel().accept(e, null);
					e.consume();
				}
			}
		});
	}

	private void update() {
		setVisible(getShow() != False);

		pane.getChildren().clear();

		if (getTitle() != null && !getTitle().isBlank()) {
			pane.getChildren().add(new HBox(new Label(getTitle())));
		}
		if (getShow() != False && getScalingType() != ScalingType.none && getUnitRadius() > 0) {
			pane.getChildren().add(createCircleScaleBox(getScalingType(), getUnitRadius(), getScale()));
		}

		if (getShow() == True && !getColorSchemeName().isBlank()) {
			var colorScheme = ColorSchemeManager.getInstance().getColorScheme(getColorSchemeName());
			for (var i = 0; i < labels.size(); i++) {
				if (active.contains(labels.get(i))) {
					var shape = getColorPatchShae() == PatchShape.Circle ? new Circle(8) : new Rectangle(16, 16);
					shape.setFill(colorScheme.get(i % colorScheme.size()));
					var label = new Label(labels.get(i));
					label.setOnMouseClicked(e -> {
						if (getClickOnLabel() != null) {
							getClickOnLabel().accept(e, label.getText());
							e.consume();
						}
					});
					shape.setOnMouseClicked(label.getOnMouseClicked());
					var hbox = new HBox(shape, label);
					hbox.setSpacing(3);
					pane.getChildren().add(hbox);
				}
			}
		}
	}

	public ObservableSet<String> getActive() {
		return active;
	}

	public String getColorSchemeName() {
		return colorSchemeName.get();
	}

	public StringProperty colorSchemeNameProperty() {
		return colorSchemeName;
	}

	public void setColorSchemeName(String colorSchemeName) {
		this.colorSchemeName.set(colorSchemeName);
	}

	public ObservableList<String> getLabels() {
		return labels;
	}

	public String getTitle() {
		return title.get();
	}

	public StringProperty titleProperty() {
		return title;
	}

	public void setTitle(String title) {
		this.title.set(title);
	}

	public ScalingType getScalingType() {
		return scalingType.get();
	}

	public ObjectProperty<ScalingType> scalingTypeProperty() {
		return scalingType;
	}

	public void setScalingType(ScalingType scalingType) {
		this.scalingType.set(scalingType);
	}

	public double getUnitRadius() {
		return unitRadius.get();
	}

	public DoubleProperty unitRadiusProperty() {
		return unitRadius;
	}

	public void setUnitRadius(double unitRadius) {
		this.unitRadius.set(unitRadius);
	}

	public double getScale() {
		return scale.get();
	}

	public DoubleProperty scaleProperty() {
		return scale;
	}

	public void setScale(double scale) {
		this.scale.set(scale);
	}

	public double getCircleMinSize() {
		return circleMinSize.get();
	}

	public DoubleProperty circleMinSizeProperty() {
		return circleMinSize;
	}

	public void setCircleMinSize(double circleMinSize) {
		this.circleMinSize.set(circleMinSize);
	}

	public PatchShape getColorPatchShae() {
		return patchShape.get();
	}

	public ObjectProperty<PatchShape> patchShapeProperty() {
		return patchShape;
	}

	public void setPatchShape(PatchShape patchShape) {
		this.patchShape.set(patchShape);
	}

	private Node createCircleScaleBox(ScalingType scalingType, double unitRadius, double scale) {
		var pane = new Pane();
		for (var m = 1; m < 10000000; m *= 10) {
			for (var x = 1; x < 10; x = (m > 1 ? x + 1 : (x == 1 ? 5 : 10))) {
				var value = x * m;
				if (value > 1) {
					var radius = scalingType.apply(value) * unitRadius * scale;
					if (radius >= 0.5 * getCircleMinSize()) {
						{
							var label = new Label(String.format("%,d", value));
							label.setStyle("-fx-font-family: Arial; -fx-font-size: 11 px;");
							var hbox = new HBox(label);
							hbox.setLayoutY(-12);
							hbox.setPrefWidth(2 * radius);
							hbox.setAlignment(Pos.CENTER);
							pane.getChildren().add(hbox);

							var oval = new Circle(radius, radius, radius);
							oval.getStyleClass().add("graph-edge");
							pane.getChildren().add(oval);
						}

						{
							var label = new Label("1");
							label.setStyle("-fx-font-family: Arial; -fx-font-size: 11 px;");
							var hbox = new HBox(label);
							hbox.setPrefWidth(2 * radius);
							hbox.setAlignment(Pos.CENTER);
							hbox.setLayoutY(2 * radius - 2 * unitRadius - 13);
							pane.getChildren().add(hbox);

							var shape = new Circle(radius, 2 * radius - unitRadius, unitRadius);
							shape.getStyleClass().add("graph-edge");
							pane.getChildren().add(shape);
						}
						return new StackPane(pane);
					}
				}
			}
		}
		return pane;
	}

	public FuzzyBoolean getShow() {
		return show.get();
	}

	public ObjectProperty<FuzzyBoolean> showProperty() {
		return show;
	}

	public void setShow(FuzzyBoolean show) {
		this.show.set(show);
	}

	public BiConsumer<MouseEvent, String> getClickOnLabel() {
		return clickOnLabel;
	}

	public void setClickOnLabel(BiConsumer<MouseEvent, String> clickOnLabel) {
		this.clickOnLabel = clickOnLabel;
	}
}

