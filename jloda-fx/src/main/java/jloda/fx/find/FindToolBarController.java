/*
 * FindToolBarController.java Copyright (C) 2024 Daniel H. Huson
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

package jloda.fx.find;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import jloda.fx.icons.MaterialIcons;

public class FindToolBarController {
    @FXML
    private AnchorPane anchorPane;

    @FXML
    private ToolBar toolBar;

    @FXML
    private ComboBox<String> searchComboBox;

    @FXML
    private Button findButton;

    @FXML
    private Button nextButton;

    @FXML
    private Button allButton;

    @FXML
    private Button findFromFileButton;

    @FXML
    private Separator fromFileSeparator;

    @FXML
    private ToggleButton caseSensitiveCheckBox;

    @FXML
    private ToggleButton wholeWordsOnlyCheckBox;

    @FXML
    private ToggleButton regExCheckBox;

    @FXML
    Label label;

    @FXML
    private Button closeButton;

    @FXML
    private ToolBar replaceToolBar;

    @FXML
    private ComboBox<String> replaceComboBox;

    @FXML
    private Button ReplaceButton;

    @FXML
    private Button replaceAllButton;

    @FXML
    private ToggleButton inSelectionOnlyToggleButton;

    private static ObservableList<String> findList;
    private ListChangeListener<String> findListChangeListener;

    private static ObservableList<String> replaceList;
    private ListChangeListener<String> replaceListChangeListener;

    @FXML
    private void initialize() {
        MaterialIcons.setIcon(findButton, MaterialIcons.start, "-fx-font-size: 10;", false);
        MaterialIcons.setIcon(nextButton, MaterialIcons.navigate_next, "-fx-font-size: 10;", false);
        MaterialIcons.setIcon(allButton, MaterialIcons.select_all, "-fx-font-size: 10;", false);
        MaterialIcons.setIcon(findFromFileButton, MaterialIcons.file_open, "-fx-font-size: 10;", true);

        if (findList == null)
            findList = FXCollections.observableArrayList();
        findListChangeListener = e -> {
            while (e.next()) {
                for (var item : e.getAddedSubList()) {
                    if (!searchComboBox.getItems().contains(item))
                        searchComboBox.getItems().add(item);
                }
            }
        };
        findList.addListener(new WeakListChangeListener<>(findListChangeListener));
        searchComboBox.getItems().addListener((ListChangeListener<? super String>) e -> {
            while (e.next()) {
                for (var item : e.getAddedSubList()) {
                    if (!findList.contains(item))
                        findList.add(item);
                }
            }
        });
        searchComboBox.getItems().addAll(findList);

        if (replaceList == null)
            replaceList = FXCollections.observableArrayList();
        replaceListChangeListener = e -> {
            while (e.next()) {
                for (var item : e.getAddedSubList()) {
                    if (!replaceComboBox.getItems().contains(item))
                        replaceComboBox.getItems().add(item);
                    if (replaceComboBox.getItems().size() >= 100)
                        break;
                }
            }
        };
        replaceList.addListener(new WeakListChangeListener<>(replaceListChangeListener));
        replaceComboBox.getItems().addListener((ListChangeListener<? super String>) e -> {
            while (e.next()) {
                for (var item : e.getAddedSubList()) {
                    if (!replaceList.contains(item))
                        replaceList.add(item);
                    if (replaceList.size() >= 100)
                        break;
                }
            }
        });
        replaceComboBox.getItems().addAll(replaceList);
    }

    public AnchorPane getAnchorPane() {
        return anchorPane;
    }

    public ToolBar getToolBar() {
        return toolBar;
    }

    public ComboBox<String> getSearchComboBox() {
        return searchComboBox;
    }

    public Button getFindButton() {
        return findButton;
    }

    public Button getNextButton() {
        return nextButton;
    }

    public Button getAllButton() {
        return allButton;
    }

    public Button getFindFromFileButton() {
        return findFromFileButton;
    }

    public Separator getFromFileSeparator() {
        return fromFileSeparator;
    }

    public ToggleButton getCaseSensitiveCheckBox() {
        return caseSensitiveCheckBox;
    }

    public ToggleButton getWholeWordsOnlyCheckBox() {
        return wholeWordsOnlyCheckBox;
    }

    public ToggleButton getRegExCheckBox() {
        return regExCheckBox;
    }

    public Label getLabel() {
        return label;
    }

    public Button getCloseButton() {
        return closeButton;
    }

    public ToolBar getReplaceToolBar() {
        return replaceToolBar;
    }

    public ComboBox<String> getReplaceComboBox() {
        return replaceComboBox;
    }

    public Button getReplaceButton() {
        return ReplaceButton;
    }

    public Button getReplaceAllButton() {
        return replaceAllButton;
    }

    public ToggleButton getInSelectionOnlyCheckBox() {
        return inSelectionOnlyToggleButton;
    }
}
