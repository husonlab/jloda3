/*
 *  Legend.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.control;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ColorSchemeManager;
import jloda.fx.util.FuzzyBoolean;
import jloda.fx.util.RunAfterAWhile;
import jloda.util.ProgramExecutorService;
import jloda.util.TriConsumer;

import java.util.HashMap;
import java.util.Map;
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
	private final DoubleProperty maxCircleRadius = new SimpleDoubleProperty(this, "maxCircleRadius", 32);

	private final DoubleProperty maxCount = new SimpleDoubleProperty(this, "maxCount", 0);

	private final IntegerProperty maxLabelsPerLine = new SimpleIntegerProperty(this, "maxLabelsPerLine", 20);

	private final ObjectProperty<FuzzyBoolean> show = new SimpleObjectProperty<>(this, "show", True);

	private final BooleanProperty editable = new SimpleBooleanProperty(this, "editable", false);

	private final ObservableMap<String, Color> colorMap = FXCollections.observableHashMap();
	private final StringProperty colorSchemeName = new SimpleStringProperty();
	private final ObservableList<String> labels;
	private final ObservableSet<String> active = FXCollections.observableSet();
	private final Pane pane;

	private BiConsumer<MouseEvent, String> clickOnLabel;

	private TriConsumer<String, Map<String, Color>, Map<String, Color>> updateColors;

	public Legend(ObservableList<String> labels, String colorSchemeName, Orientation orientation) {
		this.labels = labels;
		setColorSchemeName(colorSchemeName);

		this.labels.addListener((InvalidationListener) e -> update());
		this.active.addListener((InvalidationListener) e -> update());
		this.colorMap.addListener((InvalidationListener) e -> update());
		this.show.addListener(e -> update());
		colorSchemeNameProperty().addListener(e -> update());
		maxCircleRadius.addListener(e -> update());
		maxCount.addListener(e -> update());

		if (orientation == Orientation.HORIZONTAL) {
			var hbox = new HBox();
			hbox.setSpacing(5);
			pane = hbox;
		} else {
			var vbox = new VBox();
			vbox.setSpacing(5);
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
		RunAfterAWhile.applyInFXThread(this, () -> {
			setVisible(getShow() != False);

			pane.getChildren().clear();

			var unitRadius = (getMaxCount() <= 0 ? 0 : Math.sqrt(getMaxCircleRadius() * getMaxCircleRadius() / getMaxCount()));

			if (getTitle() != null && !getTitle().isBlank()) {
				pane.getChildren().add(new HBox(new Label(getTitle())));
			}
			if (getShow() != False && getScalingType() != ScalingType.none && unitRadius > 0) {
				pane.getChildren().add(createCircleScaleBox(getScalingType(), unitRadius, 1.0));
			}

			if (getShow() == True && !getColorSchemeName().isBlank()) {
				var orientation = (pane instanceof VBox ? Orientation.VERTICAL : Orientation.HORIZONTAL);
				Pane labelsPane;
				if (labels.size() <= getMaxLabelsPerLine()) {
					labelsPane = pane;
				} else {
					if (orientation == Orientation.VERTICAL) {
						labelsPane = new VBox();
						((VBox) labelsPane).setSpacing(5);
						var scrollPane = new ScrollPane(labelsPane);
						scrollPane.setFocusTraversable(false);
						scrollPane.setFitToWidth(true);
						scrollPane.setPrefHeight(20 * (5 + 25)); // 20 items x gap x height
						scrollPane.setMinHeight(ScrollPane.USE_PREF_SIZE);
						scrollPane.setMaxHeight(ScrollPane.USE_PREF_SIZE);
						scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
						scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
						scrollPane.setStyle("-fx-focus-color: transparent;-fx-faint-focus-color: transparent;-fx-control-inner-background: transparent;");
						pane.getChildren().add(scrollPane);
					} else {
						labelsPane = new HBox();
						((HBox) labelsPane).setSpacing(5);
						var scrollPane = new ScrollPane(labelsPane);
						scrollPane.setFocusTraversable(false);
						scrollPane.setFitToHeight(true);
						scrollPane.setPrefWidth(600);
						scrollPane.setMinWidth(ScrollPane.USE_PREF_SIZE);
						scrollPane.setMaxWidth(ScrollPane.USE_PREF_SIZE);
						scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
						scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
						scrollPane.setStyle("-fx-focus-color: transparent;-fx-faint-focus-color: transparent;-fx-control-inner-background: transparent;");
						pane.getChildren().add(scrollPane);
					}
				}

				for (var i = 0; i < labels.size(); i++) {
					var name = labels.get(i);
					if (active.contains(name)) {
						var shape = getColorPatchShae() == PatchShape.Circle ? new Circle(8) : new Rectangle(16, 16);
						var color = getColorForName(name);
						shape.setFill(color);
						var label = new Label(name);
						label.setOnMouseClicked(e -> {
							if (getClickOnLabel() != null) {
								getClickOnLabel().accept(e, label.getText());
							}
						});
						shape.setOnMouseClicked(label.getOnMouseClicked());
						var hbox = new HBox(shape, label);
						hbox.setSpacing(3);
						hbox.setPrefHeight(25);
						hbox.setMinHeight(HBox.USE_PREF_SIZE);
						hbox.setMaxHeight(HBox.USE_PREF_SIZE);

						if (isEditable()) {
							hbox.setOnMouseClicked(e -> {
								if (e.isStillSincePress()) {
									var oldColorPicker = BasicFX.findOneRecursively(hbox, ColorPicker.class);
									if (oldColorPicker != null) {
										hbox.getChildren().remove(oldColorPicker);
									} else {
										var colorPicker = new ColorPicker(color);
										hbox.getChildren().add(colorPicker);
										colorPicker.setOnAction(event -> {
											var selectedColor = colorPicker.getValue();
											hbox.getChildren().remove(colorPicker);
											if (!color.equals(selectedColor)) {
												var oldColor = colorMap.get(name);
												colorMap.put(name, selectedColor);
												if (updateColors != null) {
													var oldMap = new HashMap<>(colorMap);
													var newMap = new HashMap<>(colorMap);
													oldMap.put(name, oldColor);
													newMap.put(name, selectedColor);
													updateColors.accept(name, oldMap, newMap);
												}
											}
										});
										ProgramExecutorService.submit(10000, () -> Platform.runLater(() -> hbox.getChildren().remove(colorPicker)));
									}
								}
								e.consume();
							});
						}

						labelsPane.getChildren().add(hbox);
					}
				}
			}
		});
	}

	public Color getColorForName(String name) {
		var colorScheme = ColorSchemeManager.getInstance().getColorScheme(getColorSchemeName());
		if (colorMap.get(name) == null) {
			var i = labels.indexOf(name);
			if (i == -1)
				return Color.WHITE;
			else
				colorMap.put(name, colorScheme.get(i % colorScheme.size()));
		}
		return colorMap.get(name);
	}

	public ObservableSet<String> getActive() {
		return active;
	}

	public String getColorSchemeName() {
		return colorSchemeName.get();
	}

	public ObservableMap<String, Color> getColorMap() {
		return colorMap;
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

	public double getMaxCircleRadius() {
		return maxCircleRadius.get();
	}

	public DoubleProperty maxCircleRadiusProperty() {
		return maxCircleRadius;
	}

	public void setMaxCircleRadius(double maxCircleRadius) {
		this.maxCircleRadius.set(maxCircleRadius);
	}

	public double getMaxCount() {
		return maxCount.get();
	}

	public DoubleProperty maxCountProperty() {
		return maxCount;
	}

	public void setMaxCount(double maxCount) {
		this.maxCount.set(maxCount);
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

	public int getMaxLabelsPerLine() {
		return maxLabelsPerLine.get();
	}

	public IntegerProperty maxLabelsPerLineProperty() {
		return maxLabelsPerLine;
	}

	public void setMaxLabelsPerLine(int maxLabelsPerLine) {
		this.maxLabelsPerLine.set(maxLabelsPerLine);
	}

	private Node createCircleScaleBox(ScalingType scalingType, double unitRadius, double scale) {
		var pane = new Pane();
		for (var m = 1; m < 10000000; m *= 10) {
			for (var x = 1; x < 10; x = (m > 1 ? x + 1 : (x == 1 ? 5 : 10))) {
				var value = x * m;
				if (value > 1) {
					var radius = scalingType.apply(value) * unitRadius * scale;
					if (radius >= 0.5 * getMaxCircleRadius()) {
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

	public boolean isEditable() {
		return editable.get();
	}

	public BooleanProperty editableProperty() {
		return editable;
	}

	public void setEditable(boolean editable) {
		this.editable.set(editable);
	}

	public TriConsumer<String, Map<String, Color>, Map<String, Color>> getUpdateColors() {
		return updateColors;
	}

	public void setUpdateColors(TriConsumer<String, Map<String, Color>, Map<String, Color>> updateColors) {
		this.updateColors = updateColors;
	}
}

