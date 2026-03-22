/*
 * DraggableLabel.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * maintains a draggable label
 * Daniel Huson, 5.2018
 */
public class DraggableLabel {
	private final Text text = new Text();
	private final AnchorPane anchorPane;

	private final BooleanProperty visible;

	/**
	 * constructor
	 */
	public DraggableLabel(AnchorPane anchorPane) {
		this.anchorPane = (anchorPane != null ? anchorPane : new AnchorPane());

		visible = text.visibleProperty();

		text.setFont(Font.font("Arial", 10));

		AnchorPane.setRightAnchor(text, 5.0);
		AnchorPane.setTopAnchor(text, 5.0);
		this.anchorPane.getChildren().add(text);

		DraggableUtils.makeDraggableInAnchorPane(text);
	}

	public String getText() {
		return text.getText();
	}

	public void setText(String text) {
		this.text.setText(text);
	}

	public Text get() {
		return text;
	}

	public boolean getVisible() {
		return visible.get();
	}

	public BooleanProperty visibleProperty() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible.set(visible);
	}

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	/**
	 * if node is contained in an anchor pane, makes it click-draggable
	 *
	 * @param node contained in anchor pane
	 * @deprecated use DraggableUtils.makeDraggableInAnchorPane(node);
	 *
	 */
	@Deprecated
	public static void makeDraggable(Node node) {
		DraggableUtils.makeDraggableInAnchorPane(node);
	}
}
