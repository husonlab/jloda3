/*
 * MyTableView.java Copyright (C) 2026 Daniel H. Huson
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

package jloda.fx.control.table;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.converter.DefaultStringConverter;
import jloda.fx.control.AMultipleSelectionModel;
import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.Triplet;

import java.util.*;
import java.util.function.Function;

/**
 * string table
 * Daniel Huson, 5.2019
 */
public class MyTableView extends Pane {
    private final ListView<String> rowHeaderView;
    private boolean rowHeaderSortDescending = false;
    private final TableView<MyTableRow> tableView;

    private final ObservableMap<String, Node> rowGraphicMap = FXCollections.observableHashMap();

    private final IntegerProperty rowCount = new SimpleIntegerProperty(0);
    private final IntegerProperty colCount = new SimpleIntegerProperty(0);

    private final IntegerProperty countSelectedRows = new SimpleIntegerProperty(0);
    private final IntegerProperty countSelectedCols = new SimpleIntegerProperty(0);
    private final BooleanProperty editable = new SimpleBooleanProperty(false);

    private final BooleanProperty allowRenameRow = new SimpleBooleanProperty(false);
    private final BooleanProperty allowDeleteRow = new SimpleBooleanProperty(false);
    private final BooleanProperty allAddRow = new SimpleBooleanProperty(false);
    private final BooleanProperty allowReorderRow = new SimpleBooleanProperty(false);

    private final BooleanProperty allowRenameCol = new SimpleBooleanProperty(false);
    private final BooleanProperty allowDeleteCol = new SimpleBooleanProperty(false);
    private final BooleanProperty allowAddCol = new SimpleBooleanProperty(false);

    private final ObservableSet<String> unrenameableCols = FXCollections.observableSet();
    private final ObservableSet<String> undeleteableCols = FXCollections.observableSet();

    private final StringProperty defaultNewCellValue = new SimpleStringProperty("?");

    private Function<Collection<String>, Collection<MenuItem>> additionRowHeaderMenuItems;
    private Function<String, Collection<MenuItem>> additionColHeaderMenuItems;

    private int updatePauseLevel = 0; // if level larger than 0 then updating is paused
    private final LongProperty update = new SimpleLongProperty(0);

    private static final String SELECTED_STYLE_CLASS = "selected";

    // the table's cell selection is the one source of truth; everything below is derived from it
    private final ObservableList<String> selectedRowNames = FXCollections.observableArrayList();
    private final ObservableList<String> unmodifiableSelectedRowNames = FXCollections.unmodifiableObservableList(selectedRowNames);
    private final LongProperty selectionUpdate = new SimpleLongProperty(0);
    private int selectionPauseLevel = 0; // while larger than 0, selection changes are collected rather than applied
    private boolean selectionUpdatePending = false;
    private int rowSelectionAnchor = -1; // for shift-click in the row header
    private String sortedColName = null; // which column the rows are in the order of, to mark its heading again after a reload
    private TableColumn.SortType sortedType = TableColumn.SortType.ASCENDING;

    private static final int MAX_UNDO = 20;
    private final Deque<TableState> undoStack = new ArrayDeque<>();
    private final Deque<TableState> redoStack = new ArrayDeque<>();
    private final BooleanProperty canUndo = new SimpleBooleanProperty(false);
    private final BooleanProperty canRedo = new SimpleBooleanProperty(false);
    private TableState currentState; // the table as it stood after the last change, i.e. what an undo goes back to
    private boolean applyingHistory = false;
    private boolean resetHistoryOnNextUpdate = false;

    private static final double MIN_ROW_HEADER_WIDTH = 60;
    private static final double MAX_ROW_HEADER_WIDTH = 400;
    private static final double MIN_COL_WIDTH = 60;
    private static final double MAX_COL_WIDTH = 300;
    private static final int MAX_ROWS_MEASURED_PER_COL = 200; // enough to size a column, cheap on a big table
    private static final double DEFAULT_COLUMN_HEADER_HEIGHT = 26; // only until the table has a skin to measure

    private final SplitPane splitPane;
    private final DoubleProperty rowHeaderWidth = new SimpleDoubleProperty(200);
    private boolean settingRowHeaderWidth = false; // true while we are the ones moving the divider
    private boolean rowHeaderWidthSetByUser = false; // once the divider has been dragged, leave it where it is

    private final Image dragImage;

    public MyTableView() {
        rowHeaderView = new ListView<>();
        rowHeaderView.setMinWidth(0); // the divider decides the width, so let the user make it as narrow as they like
        rowHeaderView.setPrefWidth(200);
        rowHeaderView.setSelectionModel(new AMultipleSelectionModel<>());
        rowHeaderView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        rowHeaderView.setFocusTraversable(false);

        setupDragAndDrop();

        tableView = new TableView<>();
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getStylesheets().add(Objects.requireNonNull(MyTableView.class.getResource("mytable.css")).toExternalForm());

        rowCount.bind(Bindings.size(tableView.getItems()));
        colCount.bind(Bindings.size(tableView.getColumns()));

        rowHeaderView.getStylesheets().add(Objects.requireNonNull(MyTableView.class.getResource("mytable.css")).toExternalForm());
        rowHeaderView.setCellFactory(t -> {
            final ListCell<String> cell = new ListCell<>();
            cell.textProperty().bind(cell.itemProperty());
            rowGraphicMap.addListener((InvalidationListener) (e) -> cell.setGraphic(rowGraphicMap.get(cell.getText())));
            cell.textProperty().addListener((c, o, n) -> cell.setGraphic(rowGraphicMap.get(n)));

            // a name too long for the header is clipped, so show it in full on hover
            final Tooltip tooltip = new Tooltip();
            tooltip.textProperty().bind(cell.itemProperty());
            cell.itemProperty().addListener((c, o, n) -> cell.setTooltip(n == null ? null : tooltip));

            // clicking a row header selects the row itself: the header shows the table's selection, it does not hold one of its own
            cell.setOnMouseClicked((e) -> {
                if (e.getButton() == MouseButton.PRIMARY && !cell.isEmpty()) {
                    final int index = cell.getIndex();
                    if (e.isShiftDown() && rowSelectionAnchor >= 0) {
                        selectRowRange(rowSelectionAnchor, index);
                    } else if (e.isShortcutDown()) {
                        selectRow(index, !isRowSelected(index));
                        rowSelectionAnchor = index;
                    } else {
                        inOneSelectionStep(() -> {
                            tableView.getSelectionModel().clearSelection();
                            selectRow(index, true);
                        });
                        rowSelectionAnchor = index;
                    }
                    tableView.requestFocus();
                    e.consume();
                }
            });

            final MenuItem selectMenuItem = new MenuItem("Select All Values");
            selectMenuItem.setOnAction((e) -> {
                selectRows(new ArrayList<>(getSelectedRows()), true);
                tableView.requestFocus();
            });

            final MenuItem sortMenuItem = new MenuItem("Sort Rows");
            sortMenuItem.setOnAction((e) -> {
                if (rowHeaderSortDescending)
                    rowHeaderView.getItems().sort((a, b) -> -a.compareTo(b));
                else
                    rowHeaderView.getItems().sort(String::compareTo);
                rowHeaderSortDescending = !rowHeaderSortDescending;

                final Map<String, MyTableRow> map = new HashMap<>();
                for (String rowName : getRowNames()) {
                    map.put(rowName, getRow(rowName));
                }
                final ArrayList<MyTableRow> list = new ArrayList<>(map.size());
                for (String rowName : rowHeaderView.getItems()) {
                    list.add(map.get(rowName));
                }
                tableView.getItems().setAll(list);

                tableView.requestFocus();
            });

            final ContextMenu contextMenu = new ContextMenu(selectMenuItem, sortMenuItem, new SeparatorMenuItem());

            final MenuItem addRowMenuItem = new MenuItem("Add Row...");
            addRowMenuItem.setOnAction((e) -> {
                TextInputDialog dialog = new TextInputDialog("row");
                if (MainWindowManager.isUseDarkTheme()) {
                    dialog.getDialogPane().getScene().getWindow().getScene().getStylesheets().add("jloda/resources/css/dark.css");
                }

                dialog.setTitle("New row");
                dialog.setHeaderText("Enter row name:");

                final Optional<String> result = dialog.showAndWait();
                if (result.isPresent() && rowHeaderView.getSelectionModel().getSelectedItems().size() == 1) {
                    final String newName = StringUtils.getUniqueName(result.get().trim(), getRowNames());

                    final MyTableRow newRow = new MyTableRow(newName);
                    final int pos = getRowIndex(rowHeaderView.getSelectionModel().getSelectedItems().get(0));
                    if (pos >= 0 && pos < tableView.getItems().size())
                        tableView.getItems().add(pos, newRow);
                    else
                        tableView.getItems().add(newRow);
                }
            });
            addRowMenuItem.disableProperty().bind(Bindings.size(rowHeaderView.getSelectionModel().getSelectedItems()).isNotEqualTo(1));

            final MenuItem renameMenuItem = new MenuItem("Rename Row...");
            renameMenuItem.setOnAction((e) -> {
                final String oldName = cell.getText();
                TextInputDialog dialog = new TextInputDialog(oldName);
                if (MainWindowManager.isUseDarkTheme()) {
                    dialog.getDialogPane().getScene().getWindow().getScene().getStylesheets().add("jloda/resources/css/dark.css");
                }

                dialog.setTitle("New Row Name");
                dialog.setHeaderText("Enter new row name:");

                final Optional<String> result = dialog.showAndWait();
                if (result.isPresent() && rowHeaderView.getSelectionModel().getSelectedItems().size() == 1) {
                    if (!result.get().equals(oldName)) {
                        final String newName = StringUtils.getUniqueName(result.get().trim(), getRowNames());
                        renameRow(oldName, newName);
                    }
                }
            });
            renameMenuItem.disableProperty().bind(Bindings.size(rowHeaderView.getSelectionModel().getSelectedItems()).isNotEqualTo(1));

            final MenuItem deleteRowMenuItem = new MenuItem("Delete Row(s)");
            deleteRowMenuItem.setOnAction((e) -> deleteRows(new ArrayList<>(rowHeaderView.getSelectionModel().getSelectedItems())));
            deleteRowMenuItem.disableProperty().bind(Bindings.size(rowHeaderView.getSelectionModel().getSelectedItems()).isEqualTo(0));

            final ArrayList<MenuItem> originalMenuItems = new ArrayList<>(contextMenu.getItems());

            contextMenu.setOnShowing((e) -> {
                contextMenu.getItems().setAll(originalMenuItems);
                if (getAllAddRow())
                    contextMenu.getItems().add(addRowMenuItem);
                if (isAllowRenameRow())
                    contextMenu.getItems().add(renameMenuItem);
                if (isAllowDeleteRow())
                    contextMenu.getItems().add(deleteRowMenuItem);
                if (getAdditionRowHeaderMenuItems() != null) {
                    if (!(contextMenu.getItems().get(contextMenu.getItems().size() - 1) instanceof SeparatorMenuItem))
                        contextMenu.getItems().add(new SeparatorMenuItem());
                    contextMenu.getItems().addAll(getAdditionRowHeaderMenuItems().apply(new ArrayList<>(getSelectedRows())));
                }
            });

            cell.emptyProperty().addListener((c, o, n) -> {
                if (n) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(contextMenu);
                }
            });
            return cell;
        });

        VBox.setVgrow(rowHeaderView, Priority.ALWAYS);
        final ToolBar cornerSpacer = new ToolBar(); // sits above the row header, level with the column headers
        cornerSpacer.setMinHeight(DEFAULT_COLUMN_HEADER_HEIGHT);
        cornerSpacer.setPrefHeight(DEFAULT_COLUMN_HEADER_HEIGHT);
        cornerSpacer.setMaxHeight(DEFAULT_COLUMN_HEADER_HEIGHT);
        cornerSpacer.setOnMouseClicked((e) -> tableView.getSelectionModel().clearSelection());
        final VBox leftVBox = new VBox(cornerSpacer, rowHeaderView);
        leftVBox.setMinWidth(0);

        splitPane = new SplitPane(leftVBox, tableView);
        SplitPane.setResizableWithParent(leftVBox, false); // resizing the window widens the table, not the row header
        splitPane.prefWidthProperty().bind(widthProperty());
        splitPane.prefHeightProperty().bind(heightProperty());
        splitPane.widthProperty().addListener((c, o, n) -> {
            if (o.doubleValue() <= 0 && n.doubleValue() > 0)
                applyRowHeaderWidth(); // the first time we know how wide we are
        });
        // only an actual drag counts as the user choosing a width: the divider also moves while the pane lays itself out
        whenLaidOut(60, () -> {
            final Node divider = splitPane.lookup(".split-pane-divider");
            if (divider == null)
                return false;
            divider.addEventFilter(MouseEvent.MOUSE_RELEASED, (e) -> {
                if (!settingRowHeaderWidth) {
                    rowHeaderWidthSetByUser = true;
                    rowHeaderWidth.set(splitPane.getDividerPositions()[0] * splitPane.getWidth());
                }
            });
            return true;
        });
        this.getChildren().add(splitPane);

        tableView.skinProperty().addListener((c, o, n) -> whenLaidOut(60, () -> {
            final ScrollBar mainTableVerticalScrollBar = (ScrollBar) tableView.lookup(".scroll-bar:vertical");
            final ScrollBar rowHeaderScrollBar = (ScrollBar) rowHeaderView.lookup(".scroll-bar");
            final Node columnHeader = tableView.lookup(".column-header-background");
            final Node tableRow = tableView.lookup(".table-row-cell");
            if (mainTableVerticalScrollBar == null || rowHeaderScrollBar == null
                || !(columnHeader instanceof Region columnHeaderRegion) || !(tableRow instanceof Region tableRowRegion))
                return false;
            rowHeaderScrollBar.valueProperty().bindBidirectional(mainTableVerticalScrollBar.valueProperty());

            // both of these were guesses that only held for the default font, and the row header slid out of
            // register with the table as soon as it was not: take the numbers from the table itself
            cornerSpacer.minHeightProperty().bind(columnHeaderRegion.heightProperty());
            cornerSpacer.prefHeightProperty().bind(columnHeaderRegion.heightProperty());
            cornerSpacer.maxHeightProperty().bind(columnHeaderRegion.heightProperty());
            rowHeaderView.fixedCellSizeProperty().bind(tableRowRegion.heightProperty());
            return true;
        }));

        tableView.getItems().addListener((InvalidationListener) e -> {
            pausePostingUpdates();
            try {
                final ArrayList<String> order = new ArrayList<>(tableView.getItems().size());
                for (MyTableRow row : tableView.getItems())
                    order.add(row.getRowName());
                ((AMultipleSelectionModel<String>) rowHeaderView.getSelectionModel()).setItems(order);
                rowHeaderView.getItems().setAll(order);
            } finally {
                resumePostingUpdates();
            }
        });

        tableView.getColumns().addListener((InvalidationListener) e -> postUpdate());

        tableView.getSelectionModel().getSelectedCells().addListener((InvalidationListener) (e) -> {
            if (selectionPauseLevel > 0)
                selectionUpdatePending = true;
            else
                applySelection();
        });

        dragImage = createRectangleImage();


        // typing over a cell starts editing it, with what was typed - which was collected and then dropped
        tableView.setOnKeyPressed((KeyEvent t) -> {
            if (!t.isControlDown() && !t.isShortcutDown() && !t.isAltDown()
                && (t.getCode().isLetterKey() || t.getCode().isDigitKey())) {
                final var focused = tableView.getFocusModel().getFocusedCell();
                if (focused != null && focused.getTableColumn() != null) {
                    final String typed = t.getText();
                    tableView.edit(focused.getRow(), focused.getTableColumn());
                    Platform.runLater(() -> {
                        if (tableView.lookup(".text-field-table-cell .text-field") instanceof TextField editor) {
                            editor.setText(typed);
                            editor.positionCaret(typed.length());
                        }
                    });
                }
            }
        });

    }


    /**
     * runs something that needs the skin's own nodes, retrying until they exist or the attempts run out
     */
    private void whenLaidOut(int attemptsLeft, java.util.function.BooleanSupplier action) {
        if (!action.getAsBoolean() && attemptsLeft > 0)
            Platform.runLater(() -> whenLaidOut(attemptsLeft - 1, action));
    }

    /**
     * the width of the row header, in pixels
     */
    public double getRowHeaderWidth() {
        return rowHeaderWidth.get();
    }

    public DoubleProperty rowHeaderWidthProperty() {
        return rowHeaderWidth;
    }

    public void setRowHeaderWidth(double width) {
        rowHeaderWidth.set(Math.max(MIN_ROW_HEADER_WIDTH, Math.min(MAX_ROW_HEADER_WIDTH, width)));
        applyRowHeaderWidth();
    }

    /**
     * lets the row header go back to fitting its contents, undoing a drag of the divider
     */
    public void resetRowHeaderWidth() {
        rowHeaderWidthSetByUser = false;
        fitRowHeaderWidth();
    }

    private void applyRowHeaderWidth() {
        final double total = splitPane.getWidth();
        if (total > 0) {
            settingRowHeaderWidth = true;
            try {
                splitPane.setDividerPosition(0, Math.max(0.02, Math.min(0.9, rowHeaderWidth.get() / total)));
            } finally {
                settingRowHeaderWidth = false;
            }
        }
    }

    /**
     * widens or narrows the row header to fit the row names it holds
     */
    public void fitRowHeaderWidth() {
        if (rowHeaderWidthSetByUser)
            return; // they have put the divider where they want it
        final Font font = getRowHeaderFont();
        double required = 0;
        for (String rowName : rowHeaderView.getItems()) {
            if (rowName != null)
                required = Math.max(required, BasicFX.getTextDimension(rowName, font).getWidth());
        }
        setRowHeaderWidth(required + 56); // room for the row graphic, the cell padding and the scroll bar
    }

    /**
     * sizes each column to the widest of its heading and its values
     * <p>
     * On a long table only the first rows are measured - past a couple of hundred, later rows almost never
     * widen the column, and measuring them all is not worth the pass.
     */
    public void fitColumnWidths() {
        final Font font = getRowHeaderFont();
        for (var column : tableView.getColumns()) {
            // the heading also has to fit the sort arrow, and it turns bold when the column is selected
            double required = BasicFX.getTextDimension(column.getText(), font).getWidth() + 34;
            final int rowsToMeasure = Math.min(tableView.getItems().size(), MAX_ROWS_MEASURED_PER_COL);
            for (int row = 0; row < rowsToMeasure; row++) {
                final String value = tableView.getItems().get(row).getValue(column.getText());
                if (value != null)
                    required = Math.max(required, BasicFX.getTextDimension(value, font).getWidth() + 16);
            }
            column.setPrefWidth(Math.max(MIN_COL_WIDTH, Math.min(MAX_COL_WIDTH, required)));
        }
    }

    private Font getRowHeaderFont() {
        if (rowHeaderView.lookup(".list-cell") instanceof Labeled labeled && labeled.getFont() != null)
            return labeled.getFont();
        else
            return Font.getDefault();
    }

    public void pausePostingUpdates() {
        updatePauseLevel++;
    }

    public void resumePostingUpdates() {
        if (updatePauseLevel > 0) {
            updatePauseLevel--;
            if (updatePauseLevel == 0)
                postUpdate();
        }
    }

    private void postUpdate() {
        if (updatePauseLevel == 0) {
            recordHistory();
            update.set(update.get() + 1);
        }
    }

    /**
     * the table's contents, enough to put it back the way it was
     */
    public record TableState(ArrayList<String> rowNames, ArrayList<String> colNames, String[][] values) {
    }

    private TableState snapshotState() {
        final var rowNames = getRowNames();
        final var colNames = getColNames();
        final var values = new String[rowNames.size()][colNames.size()];
        for (int row = 0; row < rowNames.size(); row++) {
            final MyTableRow tableRow = tableView.getItems().get(row);
            for (int col = 0; col < colNames.size(); col++)
                values[row][col] = tableRow.getValue(colNames.get(col));
        }
        return new TableState(rowNames, colNames, values);
    }

    /**
     * remembers what the table looked like before the change that has just been made
     * <p>
     * A change that leaves the table exactly as it was records nothing. That matters because writing an edit
     * through to a document typically makes the document notify its viewers, which loads the table again -
     * an echo of the edit, not a second change, and the history has to survive it.
     */
    private void recordHistory() {
        final TableState newState = snapshotState();
        if (resetHistoryOnNextUpdate) {
            resetHistoryOnNextUpdate = false;
            undoStack.clear();
            redoStack.clear();
        } else if (!applyingHistory && currentState != null && !sameState(currentState, newState)) {
            undoStack.push(currentState);
            while (undoStack.size() > MAX_UNDO)
                undoStack.removeLast();
            redoStack.clear();
        }
        currentState = newState;
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
    }

    private static boolean sameState(TableState a, TableState b) {
        return a.rowNames().equals(b.rowNames()) && a.colNames().equals(b.colNames())
               && Arrays.deepEquals(a.values(), b.values());
    }

    public boolean isCanUndo() {
        return canUndo.get();
    }

    public ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo;
    }

    public boolean isCanRedo() {
        return canRedo.get();
    }

    public ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo;
    }

    /**
     * forgets the history, for when the table is reloaded rather than edited
     */
    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        canUndo.set(false);
        canRedo.set(false);
        currentState = snapshotState();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            if (currentState != null)
                redoStack.push(currentState);
            applyState(undoStack.pop());
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            if (currentState != null)
                undoStack.push(currentState);
            applyState(redoStack.pop());
        }
    }

    private void applyState(TableState state) {
        applyingHistory = true;
        pausePostingUpdates();
        try {
            final var selection = captureSelection();
            tableView.getSelectionModel().clearSelection();
            tableView.getItems().clear();
            tableView.getColumns().clear();
            for (String colName : state.colNames())
                tableView.getColumns().add(createTableCol(colName));
            for (String rowName : state.rowNames())
                tableView.getItems().add(new MyTableRow(rowName));
            for (int row = 0; row < state.rowNames().size(); row++) {
                final MyTableRow tableRow = tableView.getItems().get(row);
                for (int col = 0; col < state.colNames().size(); col++)
                    tableRow.setValue(state.colNames().get(col), state.values()[row][col]);
            }
            restoreSelection(selection);
        } finally {
            try {
                resumePostingUpdates(); // ticks the update, so the caller writes the restored table back
            } finally {
                applyingHistory = false;
                currentState = state;
                canUndo.set(!undoStack.isEmpty());
                canRedo.set(!redoStack.isEmpty());
            }
        }
        Platform.runLater(this::fitColumnWidths);
    }

    /**
     * collect selection changes rather than reacting to each one
     * <p>
     * Selecting a row or a column touches one cell at a time, so without this every one of those cells
     * would trigger a full recomputation of the selection state, which is quadratic in the size of the table.
     */
    private void pauseSelectionUpdates() {
        selectionPauseLevel++;
    }

    private void resumeSelectionUpdates() {
        if (selectionPauseLevel > 0) {
            selectionPauseLevel--;
            if (selectionPauseLevel == 0 && selectionUpdatePending)
                applySelection();
        }
    }

    /**
     * runs a bulk selection change, reacting to it once rather than once per cell
     */
    private void inOneSelectionStep(Runnable runnable) {
        pauseSelectionUpdates();
        try {
            runnable.run();
        } finally {
            resumeSelectionUpdates();
        }
    }

    /**
     * recomputes everything derived from the table's cell selection: the counts, the names of the selected
     * rows, the highlighting of the selected column headers and the selection shown in the row header
     */
    private void applySelection() {
        selectionUpdatePending = false;

        final BitSet selectedRows = new BitSet();
        final BitSet selectedCols = new BitSet();
        for (var pos : tableView.getSelectionModel().getSelectedCells()) {
            if (pos.getRow() >= 0)
                selectedRows.set(pos.getRow());
            if (pos.getColumn() >= 0)
                selectedCols.set(pos.getColumn());
        }

        // once per column, never once per cell: adding the style class per cell left hundreds of copies of it
        // on the column, each one re-running CSS, and one remove() per event was never enough to clear them
        for (int col = 0; col < tableView.getColumns().size(); col++) {
            final var styleClass = tableView.getColumns().get(col).getStyleClass();
            final boolean shouldBeSelected = selectedCols.get(col);
            if (shouldBeSelected != styleClass.contains(SELECTED_STYLE_CLASS)) {
                if (shouldBeSelected)
                    styleClass.add(SELECTED_STYLE_CLASS);
                else
                    styleClass.removeAll(List.of(SELECTED_STYLE_CLASS)); // removeAll(), to also drop any older duplicates
            }
        }

        final ArrayList<String> rowNames = new ArrayList<>(selectedRows.cardinality());
        for (int row = selectedRows.nextSetBit(0); row != -1; row = selectedRows.nextSetBit(row + 1)) {
            if (row < tableView.getItems().size())
                rowNames.add(getRowName(row));
        }
        if (!rowNames.equals(selectedRowNames))
            selectedRowNames.setAll(rowNames);

        countSelectedRows.set(selectedRows.cardinality());
        countSelectedCols.set(selectedCols.cardinality());

        syncRowHeaderSelection(selectedRows);

        selectionUpdate.set(selectionUpdate.get() + 1);
    }

    /**
     * brings the row header's own selection into line with the table's, changing only what differs
     */
    private void syncRowHeaderSelection(BitSet selectedRows) {
        final var model = (AMultipleSelectionModel<String>) rowHeaderView.getSelectionModel();
        final var items = rowHeaderView.getItems();
        final ArrayList<String> toSelect = new ArrayList<>();
        final ArrayList<String> toClear = new ArrayList<>();
        for (int row = 0; row < items.size(); row++) {
            final boolean shouldBeSelected = selectedRows.get(row);
            if (shouldBeSelected != model.isSelected(row))
                (shouldBeSelected ? toSelect : toClear).add(items.get(row));
        }
        if (!toClear.isEmpty())
            model.clearSelection(toClear);
        if (!toSelect.isEmpty())
            model.selectItems(toSelect);
    }

    public void renameRow(String oldName, String newName) {
        final int index = getRowIndex(oldName);
        final MyTableRow tableRow = getRow(oldName);
        if (index != -1 && tableRow != null) {
            newName = StringUtils.getUniqueName(newName.trim(), getRowNames());
            rowHeaderView.getItems().set(index, newName);
            tableRow.setRowName(newName);
            postUpdate();
        }
    }

    public void renameCol(String oldName, String newName) {
        final TableColumn col = getCol(oldName);
        if (col != null) {
            newName = StringUtils.getUniqueName(newName.trim(), getColNames());
            col.setText(newName);
            for (MyTableRow row : tableView.getItems()) {
                row.renameCol(oldName, newName);
            }
            postUpdate();
        }
    }


    public ArrayList<String> getColNames() {
        final ArrayList<String> list = new ArrayList<>();
        for (TableColumn column : tableView.getColumns()) {
            list.add(column.getText());
        }
        return list;
    }

    public ArrayList<String> getRowNames() {
        final ArrayList<String> list = new ArrayList<>();
        for (MyTableRow tableRow : tableView.getItems()) {
            list.add(tableRow.getRowName());
        }
        return list;
    }

    private boolean downKey = false;

    private TableColumn<MyTableRow, String> createTableCol(String colName) {
        final TableColumn<MyTableRow, String> tableColumn = new TableColumn<>(colName);

        tableColumn.setSortable(false); // sorting is on the heading's context menu; a click must not reorder the table

        tableColumn.setCellValueFactory(p -> p.getValue().valueProperty(tableColumn.getText()));

        tableColumn.setCellFactory(param -> {
            final MyTextFieldTableCell<MyTableRow, String> cell = new MyTextFieldTableCell<>(new DefaultStringConverter());
            cell.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.DOWN) {
                    downKey = true;
                    cell.commitEdit(cell.getConverter().fromString(cell.getTextField().getText()));
                }
            });
            cell.editableProperty().bind(editable);
            return cell;
        });

        tableColumn.setOnEditCommit(t -> {
                    final String oldValue = t.getTableView().getItems().get(t.getTablePosition().getRow()).getValue(tableColumn.getText());
                    final String newValue = t.getNewValue();
                    if (!newValue.equals(oldValue)) {
                        t.getTableView().getItems().get(t.getTablePosition().getRow()).valueProperty(tableColumn.getText()).set(newValue);
                        if (!downKey)
                            Platform.runLater(this::postUpdate);
                    }
                    Platform.runLater(() -> {
                        tableView.requestFocus();
                        final int row = t.getTablePosition().getRow();
                        tableView.getSelectionModel().clearAndSelect(row < tableView.getItems().size() ? row + 1 : row, t.getTableColumn());
                    });
                    downKey = false;
                }
        );

        final ContextMenu contextMenu = new ContextMenu();
        final MenuItem selectMenuItem = new MenuItem("Select All Values");
        selectMenuItem.setOnAction((e) -> selectCol(tableColumn, true));

        final MenuItem sortAscendingMenuItem = new MenuItem("Sort Ascending");
        sortAscendingMenuItem.setOnAction((e) -> sortByCol(tableColumn.getText(), TableColumn.SortType.ASCENDING));

        final MenuItem sortDescendingMenuItem = new MenuItem("Sort Descending");
        sortDescendingMenuItem.setOnAction((e) -> sortByCol(tableColumn.getText(), TableColumn.SortType.DESCENDING));

        contextMenu.getItems().addAll(selectMenuItem, sortAscendingMenuItem, sortDescendingMenuItem, new SeparatorMenuItem());

        final MenuItem addColumnMenuItem = new MenuItem("Add Column...");
        addColumnMenuItem.setOnAction((e) -> {
            TextInputDialog dialog = new TextInputDialog("col");
            dialog.setTitle("New Column");
            dialog.setHeaderText("Enter new column name:");

            final Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                final int pos = tableView.getColumns().indexOf(tableColumn);
                addCol(pos, result.get().trim());
            }
        });

        final MenuItem renameMenuItem = new MenuItem("Rename Column...");
        renameMenuItem.setOnAction((e) -> {
            TextInputDialog dialog = new TextInputDialog(tableColumn.getText());
            dialog.setTitle("Rename Column");
            dialog.setHeaderText("Enter new column name:");

            final Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                if (!result.get().equals(tableColumn.getText())) {
                    final String newName = StringUtils.getUniqueName(result.get().trim(), getColNames());
                    renameCol(tableColumn.getText(), newName);
                }
            }
        });

        final MenuItem deleteItem = new MenuItem(("Delete Column"));
        deleteItem.setOnAction((e) -> deleteCol(tableColumn.getText()));

        final ArrayList<MenuItem> originalMenuItems = new ArrayList<>(contextMenu.getItems());

        contextMenu.setOnShowing((e) -> {
            contextMenu.getItems().setAll(originalMenuItems);
            if (getAllowAddCol())
                contextMenu.getItems().add(addColumnMenuItem);
            if (isAllowRenameCol() && !getUnrenameableCols().contains(tableColumn.getText()))
                contextMenu.getItems().add(renameMenuItem);
            if (isAllowDeleteCol() && !getUndeleteableCols().contains(tableColumn.getText()))
                contextMenu.getItems().add(deleteItem);
            if (getAdditionColHeaderMenuItems() != null) {
                if (!(contextMenu.getItems().get(contextMenu.getItems().size() - 1) instanceof SeparatorMenuItem))
                    contextMenu.getItems().add(new SeparatorMenuItem());
                contextMenu.getItems().addAll(getAdditionColHeaderMenuItems().apply(tableColumn.getText()));
            }
        });

        tableColumn.setContextMenu(contextMenu);
        return tableColumn;
    }

    public void deleteRow(String rowName) {
        tableView.getItems().remove(getRow(rowName));
    }

    public void deleteRows(Collection<String> rowNames) {
        pausePostingUpdates();
        try {
            for (String row : rowNames) {
                tableView.getItems().remove(getRow(row));
            }
        } finally {
            resumePostingUpdates();
        }
    }

    public void deleteCol(String colName) {
        pausePostingUpdates();
        try {
            tableView.getColumns().remove(getCol(colName));
            for (MyTableRow row : tableView.getItems()) {
                row.colValueMap.remove(colName);
            }
        } finally {
            resumePostingUpdates();
        }
    }

    public void deleteCols(Collection<String> colNames) {
        pausePostingUpdates();
        try {
            for (String colName : colNames) {
                tableView.getColumns().remove(getCol(colName));
                for (MyTableRow row : tableView.getItems()) {
                    row.colValueMap.remove(colName);
                }
            }
        } finally {
            resumePostingUpdates();
        }
    }

    /**
     * sorts the rows by one column, and marks that column's heading with the direction
     */
    public void sortByCol(String colName, TableColumn.SortType sortType) {
        final TableColumn<MyTableRow, ?> tableColumn = getCol(colName);
        if (tableColumn != null) {
            tableColumn.setSortType(sortType);
            applySort(colName, sortType);
            sortedColName = colName;
            sortedType = sortType;
            showSortIndicator();
        }
    }

    /**
     * marks the sorted column's heading, since a heading that cannot be clicked draws no arrow of its own
     */
    private void showSortIndicator() {
        for (var column : tableView.getColumns()) {
            if (column.getText().equals(sortedColName)) {
                final Label indicator = new Label(sortedType == TableColumn.SortType.ASCENDING ? "\u25b2" : "\u25bc");
                indicator.getStyleClass().add("sort-indicator");
                column.setGraphic(indicator);
            } else
                column.setGraphic(null);
        }
    }

    /**
     * reorders the rows, keeping whatever was selected selected
     */
    private void applySort(String colName, TableColumn.SortType sortType) {
        pausePostingUpdates();
        try {
            final var selection = captureSelection();
            final ArrayList<MyTableRow> list = new ArrayList<>(tableView.getItems());
            list.sort(new ColumnComparator(colName, sortType, list));
            tableView.getItems().setAll(list);
            restoreSelection(selection);
            tableView.requestFocus();
        } finally {
            resumePostingUpdates();
        }
    }

    private record CellRef(String rowName, int col) {
    }

    private List<CellRef> captureSelection() {
        final var list = new ArrayList<CellRef>();
        for (var pos : tableView.getSelectionModel().getSelectedCells()) {
            if (pos.getRow() >= 0 && pos.getRow() < tableView.getItems().size() && pos.getColumn() >= 0)
                list.add(new CellRef(getRowName(pos.getRow()), pos.getColumn()));
        }
        return list;
    }

    private void restoreSelection(List<CellRef> cells) {
        if (!cells.isEmpty()) {
            final Map<String, Integer> rowIndex = new HashMap<>();
            for (int row = 0; row < tableView.getItems().size(); row++)
                rowIndex.put(getRowName(row), row);
            inOneSelectionStep(() -> {
                tableView.getSelectionModel().clearSelection();
                for (var cell : cells) {
                    final Integer row = rowIndex.get(cell.rowName());
                    if (row != null && cell.col() < getColCount())
                        selectCell(row, cell.col(), true);
                }
            });
        }
    }

    public TableColumn<MyTableRow, ?> getCol(String colName) {
        for (TableColumn<MyTableRow, ?> column : tableView.getColumns()) {
            if (column.getText().equals(colName))
                return column;
        }
        return null;
    }

    public TableColumn<MyTableRow, ?> getCol(int index) {
        return tableView.getColumns().get(index);
    }

    public String getColName(int index) {
        return tableView.getColumns().get(index).getText();
    }

    public int getColIndex(String name) {
        for (int index = 0; index < getColCount(); index++) {
            if (tableView.getColumns().get(index).getText().equalsIgnoreCase(name))
                return index;
        }
        return -1;
    }

    public String getRowName(int index) {
        return tableView.getItems().get(index).getRowName();
    }

    public int getRowIndex(String rowName) {
        int count = 0;
        for (MyTableRow row : tableView.getItems()) {
            if (row.getRowName().equals(rowName))
                return count;
            count++;
        }
        return -1;
    }

    private MyTableRow getRow(String rowName) {
        for (MyTableRow row : tableView.getItems()) {
            if (row.getRowName().equals(rowName))
                return row;
        }
        return null;
    }

    public void selectByValue(String colName, String value) {
        final int col = getColIndex(colName);
        if (col >= 0) {
            inOneSelectionStep(() -> {
                for (int row = 0; row < getRowCount(); row++) {
                    if (value.equals(getValue(row, col)))
                        selectCell(row, col, true);
                }
            });
        }
    }

    public void selectCol(TableColumn<MyTableRow, ?> column, boolean select) {
        if (column != null && !tableView.getItems().isEmpty()) {
            inOneSelectionStep(() -> {
                if (select)
                    tableView.getSelectionModel().selectRange(0, column, tableView.getItems().size() - 1, column);
                else {
                    for (int row = 0; row < tableView.getItems().size(); row++)
                        tableView.getSelectionModel().clearSelection(row, column);
                }
            });
        }
    }

    public void selectCol(String colName, boolean select) {
        selectCol(getCol(colName), select);
    }

    public void selectCols(Collection<String> colNames, boolean select) {
        inOneSelectionStep(() -> {
            for (String colName : colNames)
                selectCol(colName, select);
        });
    }

    public void selectRow(String rowName, boolean select) {
        selectRow(getRowIndex(rowName), select);
    }

    /**
     * the row header shows the table's selection, so selecting a row header means selecting the row
     */
    public void selectRowHeader(String rowName, boolean select) {
        selectRow(rowName, select);
    }

    public void selectRowHeaders(Collection<String> rowNames, boolean select) {
        selectRows(rowNames, select);
    }

    public void selectRow(int row, boolean select) {
        if (row >= 0 && row < tableView.getItems().size() && !tableView.getColumns().isEmpty()) {
            inOneSelectionStep(() -> {
                if (select)
                    tableView.getSelectionModel().selectRange(row, getCol(0), row, getCol(getColCount() - 1));
                else {
                    for (TableColumn<MyTableRow, ?> column : tableView.getColumns())
                        tableView.getSelectionModel().clearSelection(row, column);
                }
            });
        }
    }

    public void selectRows(Collection<String> rowNames, boolean select) {
        inOneSelectionStep(() -> {
            for (String row : rowNames)
                selectRow(row, select);
        });
    }

    /**
     * selects all rows from one index to another, inclusive, replacing the current selection
     */
    public void selectRowRange(int fromRow, int toRow) {
        inOneSelectionStep(() -> {
            tableView.getSelectionModel().clearSelection();
            for (int row = Math.min(fromRow, toRow); row <= Math.max(fromRow, toRow); row++)
                selectRow(row, true);
        });
    }

    public void selectCell(int rowId, int colId, boolean select) {
        if (select)
            tableView.getSelectionModel().select(rowId, tableView.getColumns().get(colId));
        else
            tableView.getSelectionModel().clearSelection(rowId, tableView.getColumns().get(colId));
    }

    public boolean isSelected(int rowId, int colId) {
        return tableView.getSelectionModel().isSelected(rowId, tableView.getColumns().get(colId));
    }

    public boolean isRowSelected(int row) {
        return row >= 0 && row < tableView.getItems().size() && selectedRowNames.contains(getRowName(row));
    }

    public String getASelectedCol() {
        final int col = getASelectedColIndex();
        if (col != -1)
            return getColName(col);
        else
            return null;
    }

    public int getASelectedColIndex() {
        if (!tableView.getSelectionModel().getSelectedCells().isEmpty())
            return tableView.getSelectionModel().getSelectedCells().get(0).getColumn();
        else
            return -1;
    }

    public void selectAll(boolean select) {
        inOneSelectionStep(() -> {
            if (select)
                tableView.getSelectionModel().selectAll();
            else
                tableView.getSelectionModel().clearSelection();
        });
    }

    /**
     * the names of the selected rows, in table order
     *
     * @return an unmodifiable list, updated in step with the table's cell selection
     */
    public ObservableList<String> getSelectedRows() {
        return unmodifiableSelectedRowNames;
    }

    /**
     * ticks once per selection change, however many cells that change touched
     */
    public ReadOnlyLongProperty selectionUpdateProperty() {
        return selectionUpdate;
    }

    public ArrayList<Integer> getSelectedRowIndices() {
        final ArrayList<Integer> list = new ArrayList<>();
        try {
            for (String rowName : getSelectedRows()) {
                list.add(getRowIndex(rowName));
            }
        } catch (ConcurrentModificationException ex) {
            // don't know why this happens
        }
        return list;
    }

    public ArrayList<String> getSelectedCols() {
        final BitSet cols = new BitSet();
        try {
            for (TablePosition position : getSelectedCells()) {
                cols.set(position.getColumn());
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
        final ArrayList<String> list = new ArrayList<>(cols.cardinality());
        for (String colName : getColNames()) {
            if (cols.get(getColIndex(colName)))
                list.add(colName);
        }
        return list;
    }

    public int getCountSelectedRows() {
        return countSelectedRows.get();
    }

    public ReadOnlyIntegerProperty countSelectedRowsProperty() {
        return countSelectedRows;
    }


    public int getCountSelectedCols() {
        return countSelectedCols.get();
    }

    public ReadOnlyIntegerProperty countSelectedColsProperty() {
        return countSelectedCols;
    }


    public TableView.TableViewSelectionModel<MyTableRow> getSelectionModel() {
        return tableView.getSelectionModel();
    }

    public ObservableList<TablePosition> getSelectedCells() {
        return tableView.getSelectionModel().getSelectedCells();
    }

    public String getValue(int rowId, int colId) {
        return tableView.getItems().get(rowId).getValue(getColName(colId));
    }

    public String getValue(String rowName, String colName) {
        return tableView.getItems().get(getRowIndex(rowName)).getValue(colName);
    }

    public void setValue(int rowId, int colId, String value) {
        tableView.getItems().get(rowId).valueProperty(getColNames().get((colId))).set(value);
        postUpdate();
    }

    public void setValue(String rowName, String colName, String value) {
        tableView.getItems().get(getRowIndex(rowName)).valueProperty(colName).set(value);
        postUpdate();
    }

    public boolean isAllowDeleteCol() {
        return allowDeleteCol.get();
    }

    public BooleanProperty allowDeleteColProperty() {
        return allowDeleteCol;
    }

    public void setAllowDeleteCol(boolean allowDeleteCol) {
        this.allowDeleteCol.set(allowDeleteCol);
    }

    public boolean getAllowAddCol() {
        return allowAddCol.get();
    }

    public BooleanProperty allowAddColProperty() {
        return allowAddCol;
    }

    public String getDefaultNewCellValue() {
        return defaultNewCellValue.get();
    }

    public StringProperty defaultNewCellValueProperty() {
        return defaultNewCellValue;
    }

    public void setDefaultNewCellValue(String defaultNewCellValue) {
        this.defaultNewCellValue.set(defaultNewCellValue);
    }

    public void setAllowAddCol(boolean allowAddCol) {
        this.allowAddCol.set(allowAddCol);
    }

    public boolean isAllowDeleteRow() {
        return allowDeleteRow.get();
    }

    public BooleanProperty allowDeleteRowProperty() {
        return allowDeleteRow;
    }

    public void setAllowDeleteRow(boolean allowDeleteRow) {
        this.allowDeleteRow.set(allowDeleteRow);
    }

    public boolean getAllAddRow() {
        return allAddRow.get();
    }

    public BooleanProperty allAddRowProperty() {
        return allAddRow;
    }

    public void setAllAddRow(boolean allAddRow) {
        this.allAddRow.set(allAddRow);
    }

    public boolean isAllowRenameRow() {
        return allowRenameRow.get();
    }

    public BooleanProperty allowRenameRowProperty() {
        return allowRenameRow;
    }

    public void setAllowRenameRow(boolean allowRenameRow) {
        this.allowRenameRow.set(allowRenameRow);
    }

    public boolean getAllowReorderRow() {
        return allowReorderRow.get();
    }

    public BooleanProperty allowReorderRowProperty() {
        return allowReorderRow;
    }

    public void setAllowReorderRow(boolean allowReorderRow) {
        this.allowReorderRow.set(allowReorderRow);
    }

    public boolean isAllowRenameCol() {
        return allowRenameCol.get();
    }

    public BooleanProperty allowRenameColProperty() {
        return allowRenameCol;
    }

    public void setAllowRenameCol(boolean allowRenameCol) {
        this.allowRenameCol.set(allowRenameCol);
    }

    public Function<Collection<String>, Collection<MenuItem>> getAdditionRowHeaderMenuItems() {
        return additionRowHeaderMenuItems;
    }

    /**
     * items to append to a row header's context menu, built afresh for each showing
     * <p>
     * The items must not belong to another menu: adding a {@link MenuItem} to a context menu removes it
     * from the one it was in.
     */
    public void setAdditionRowHeaderMenuItems(Function<Collection<String>, Collection<MenuItem>> additionRowHeaderMenuItems) {
        this.additionRowHeaderMenuItems = additionRowHeaderMenuItems;
    }

    public Function<String, Collection<MenuItem>> getAdditionColHeaderMenuItems() {
        return additionColHeaderMenuItems;
    }

    /**
     * items to append to a column heading's context menu, built afresh for each showing
     * <p>
     * The items must not belong to another menu: adding a {@link MenuItem} to a context menu removes it
     * from the one it was in.
     */
    public void setAdditionColHeaderMenuItems(Function<String, Collection<MenuItem>> additionColHeaderMenuItems) {
        this.additionColHeaderMenuItems = additionColHeaderMenuItems;
    }

    public int getRowCount() {
        return rowCount.get();
    }

    public ReadOnlyIntegerProperty rowCountProperty() {
        return rowCount;
    }

    public int getColCount() {
        return colCount.get();
    }

    public ReadOnlyIntegerProperty colCountProperty() {
        return colCount;
    }

    private void setupDragAndDrop() {
        rowHeaderView.setOnDragDetected(e -> {
            if (getAllowReorderRow()) {
                pausePostingUpdates();

                final PickResult pickResult = e.getPickResult();
                if (pickResult != null && (pickResult.getIntersectedNode() instanceof ListCell && ((ListCell<?>) pickResult.getIntersectedNode()).getItem() != null
                                           || pickResult.getIntersectedNode() instanceof Text)) {
                    final Dragboard dragboard = rowHeaderView.startDragAndDrop(TransferMode.MOVE);
                    final ClipboardContent content = new ClipboardContent();
                    content.put(DataFormat.PLAIN_TEXT, StringUtils.toString(new ArrayList<>(rowHeaderView.getSelectionModel().getSelectedItems()), "\n"));
                    dragboard.setDragView(dragImage);

                    content.putString(StringUtils.toString(rowHeaderView.getSelectionModel().getSelectedItems(), "\n"));
                    dragboard.setContent(content);

                    tableView.getSelectionModel().clearSelection();
                    for (String row : rowHeaderView.getSelectionModel().getSelectedItems())
                        selectRow(row, true);
                    tableView.requestFocus();
                }
                e.consume();
            }
        });

        rowHeaderView.setOnDragOver(e -> {
            if (getAllowReorderRow()) {
                e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            }
        });

        rowHeaderView.setOnDragDropped(e -> {
            if (getAllowReorderRow()) {
                final List<String> list = StringUtils.toList(e.getDragboard().getContent(DataFormat.PLAIN_TEXT).toString());

                String hitRowName = null;
                {
                    final PickResult pickResult = e.getPickResult();
                    if (pickResult != null && (pickResult.getIntersectedNode() instanceof ListCell && ((ListCell<?>) pickResult.getIntersectedNode()).getItem() != null
                                               || pickResult.getIntersectedNode() instanceof Text)) {
                        final String name;

                        if (pickResult.getIntersectedNode() instanceof ListCell)
                            name = ((ListCell<?>) pickResult.getIntersectedNode()).getText();
                        else
                            name = ((Text) pickResult.getIntersectedNode()).getText();

                        for (String item : rowHeaderView.getItems()) {
                            if (item.equals(name)) {
                                hitRowName = name;
                                break;
                            }
                        }
                    }
                }

                if (hitRowName != null && !list.contains(hitRowName)) {
                    rowHeaderView.getItems().removeAll(list);
                    rowHeaderView.getItems().addAll(rowHeaderView.getItems().indexOf(hitRowName), list);

                    final ArrayList<MyTableRow> sorted = new ArrayList<>(tableView.getItems().size());
                    for (String rowName : rowHeaderView.getItems()) {
                        sorted.add(getRow(rowName));
                    }
                    tableView.getItems().setAll(sorted);

                    tableView.getSelectionModel().clearSelection();
                    for (String row : list)
                        selectRow(row, true);
                    tableView.requestFocus();
                }

                e.setDropCompleted(true);
                e.consume();
            }
        });

        setOnDragDone((e) -> {
            if (getAllowReorderRow()) {
                resumePostingUpdates();
                e.consume();
            }
        });
    }

    public void setRowGraphic(String rowName, Node node) {
        rowGraphicMap.put(rowName, node);
    }

    public Node getRowGraph(String rowName) {
        return rowGraphicMap.get(rowName);
    }

    public void clearRowGraphic(String rowName) {
        rowGraphicMap.remove(rowName);
    }

    public void clearRowGraphics() {
        rowGraphicMap.clear();
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

    public ObservableSet<String> getUnrenameableCols() {
        return unrenameableCols;
    }

    public ObservableSet<String> getUndeleteableCols() {
        return undeleteableCols;
    }

    private static Image createRectangleImage() {
        final Rectangle rectangle = new Rectangle(64, 16);
        rectangle.setFill(Color.LIGHTGRAY);
        rectangle.setStroke(Color.BLACK);
        return rectangle.snapshot(null, null);
    }

    public void copyToClipboard() {
        final StringBuilder buf = new StringBuilder();
        buf.append("Table");
        for (String colName : getSelectedCols())
            buf.append("\t").append(colName);
        buf.append("\n");
        for (int row = 0; row < getRowCount(); row++) {
            boolean addedRowHeader = false;
            for (int col = 0; col < getColCount(); col++) {
                if (isSelected(row, col)) {
                    if (!addedRowHeader) {
                        buf.append(getRowName(row));
                        addedRowHeader = true;
                    }
                    buf.append("\t").append(getValue(row, col));
                }
            }
            if (addedRowHeader)
                buf.append("\n");
        }

        if (!buf.isEmpty()) {
            final ClipboardContent contents = new ClipboardContent();

            contents.put(DataFormat.PLAIN_TEXT, buf.toString());
            contents.putString(buf.toString());
            Clipboard.getSystemClipboard().setContent(contents);
        }
    }

    public void scrollToRow(String rowName) {
        rowHeaderView.scrollTo(rowName);
    }

    public void scrollToRow(int index) {
        rowHeaderView.scrollTo(index);
    }

    public void addCol(int pos, String colName) {
        pausePostingUpdates();
        try {
            setUserData(-1);
            final String newColName = StringUtils.getUniqueName(colName, getColNames());
            final TableColumn<MyTableRow, String> newColumn = createTableCol(newColName);
            if (pos >= 0 && pos < getColCount() - 1)
                tableView.getColumns().add(pos + 1, newColumn);
            else
                tableView.getColumns().add(newColumn);
            setUserData(0);
        } finally {
            resumePostingUpdates();
        }
    }

    public void addCol(String colName) {
        addCol(Integer.MAX_VALUE, colName);
    }

    public void addRow(int index, String rowName, Object... values) {
        pausePostingUpdates();
        try {
            final String newRowName = StringUtils.getUniqueName(rowName, getRowNames());
            final MyTableRow newRow = new MyTableRow(newRowName);
            if (index >= 0 && index < getRowCount() - 1)
                tableView.getItems().add(index, newRow);
            else
                tableView.getItems().add(newRow);

            final int top = Math.min(values.length, getColCount());
            for (int i = 0; i < top; i++) {
                if (values[i] != null)
                    newRow.setValue(getColName(i), values[i].toString());
            }
        } finally {
            resumePostingUpdates();
        }
    }

    public void addRow(String rowName, Object... values) {
        addRow(Integer.MAX_VALUE, rowName, values);
    }

    public void clear() {
        pausePostingUpdates();
        try {
            resetHistoryOnNextUpdate = true;
            sortedColName = null;
            tableView.getItems().clear();
            tableView.getColumns().clear();
        } finally {
            resumePostingUpdates();
        }
    }

    public void createRowsAndCols(ArrayList<String> rowNames, ArrayList<String> colNames) {
        pausePostingUpdates();
        try {
            tableView.getItems().clear();
            tableView.getColumns().clear();
            for (String colName : colNames) {
                tableView.getColumns().add(createTableCol(colName));
            }
            for (String rowName : rowNames) {
                tableView.getItems().add(new MyTableRow(rowName));
            }
        } finally {
            resumePostingUpdates();
        }
        showSortIndicator(); // the columns are new objects, but the rows are still in the order we left them in
        Platform.runLater(() -> {
            fitRowHeaderWidth();
            fitColumnWidths();
        });
    }

    public String toString() {
        final StringBuilder buf = new StringBuilder();

        buf.append("#table");
        for (String colName : getColNames()) {
            buf.append("\t").append(colName);
        }
        buf.append("\n");
        for (String rowName : getRowNames()) {
            buf.append(rowName);
            for (String colName : getColNames()) {
                buf.append("\t");
                buf.append(getValue(rowName, colName));
            }
            buf.append("\n");
        }
        return buf.toString();
    }

    public long getUpdate() {
        return update.get();
    }

    public ReadOnlyLongProperty updateProperty() {
        return update;
    }

    public Triplet<String, String, String> getSingleSelectedCell() {
        if (getSelectedCells().size() == 1) {
            final TablePosition tablePosition = getSelectedCells().get(0);
            return new Triplet<>(getRowName(tablePosition.getRow()), getColName(tablePosition.getColumn()), getValue(tablePosition.getRow(), tablePosition.getColumn()));
        } else
            return null;
    }

    public void swapRows(int pos1, int pos2) {
        if (pos1 != pos2 && pos1 >= 0 && pos1 < getRowCount() && pos2 >= 0 && pos2 < getRowCount()) {
            pausePostingUpdates();
            try {
                final int minPos = Math.min(pos1, pos2);
                final int maxPos = Math.max(pos1, pos2);
                System.err.println("Swapping: " + minPos + " " + maxPos);
                final MyTableRow minRow = tableView.getItems().get(minPos);
                final MyTableRow maxRow = tableView.getItems().remove(maxPos);
                tableView.getItems().set(minPos, maxRow);
                if (maxPos < getRowCount())
                    tableView.getItems().add(maxPos, minRow);
                else
                    tableView.getItems().add(minRow);
            } finally {
                resumePostingUpdates();
            }
        }
    }

    public ArrayList<String> sortInSameOrderAsRows(Collection<String> samples) {
        final ArrayList<String> sorted = new ArrayList<>(samples.size());
        for (String row : getRowNames()) {
            if (samples.contains(row))
                sorted.add(row);
        }
        return sorted;
    }

    /**
     * orders rows by one column
     * <p>
     * A missing value must not turn a numerical column into a textual one - in a metadata table most columns
     * have some - so missing values are left out of that decision and sorted to the end either way round.
     */
    private class ColumnComparator implements Comparator<MyTableRow> {
        private final String colName;
        private final int direction;
        private final boolean sortAsNumbers;

        public ColumnComparator(String colName, TableColumn.SortType sortType, Collection<MyTableRow> rows) {
            this.colName = colName;
            this.direction = (sortType == TableColumn.SortType.ASCENDING ? 1 : -1);

            boolean allPresentAreNumbers = true;
            boolean anyPresent = false;
            for (MyTableRow row : rows) {
                final String value = row.getValue(colName);
                if (!isMissing(value)) {
                    anyPresent = true;
                    if (!NumberUtils.isDouble(value)) {
                        allPresentAreNumbers = false;
                        break;
                    }
                }
            }
            sortAsNumbers = anyPresent && allPresentAreNumbers;
        }

        private boolean isMissing(String value) {
            return value == null || value.isBlank() || value.equals(getDefaultNewCellValue());
        }

        @Override
        public int compare(MyTableRow a, MyTableRow b) {
            final String valueA = a.getValue(colName);
            final String valueB = b.getValue(colName);
            if (isMissing(valueA))
                return isMissing(valueB) ? 0 : 1;
            else if (isMissing(valueB))
                return -1;
            else if (sortAsNumbers)
                return direction * Double.compare(NumberUtils.parseDouble(valueA), NumberUtils.parseDouble(valueB));
            else
                return direction * valueA.compareTo(valueB);
        }
    }

    public class MyTableRow {
        private String rowName;
        private final Map<String, StringProperty> colValueMap = new HashMap<>();

        MyTableRow(String rowName) {
            this.rowName = rowName;
        }

        public String getRowName() {
            return rowName;
        }

        void setRowName(String name) {
            rowName = name;
        }

        public StringProperty valueProperty(String colName) {
            StringProperty valueProperty = colValueMap.get(colName);
            if (valueProperty == null) {
                valueProperty = new SimpleStringProperty(getDefaultNewCellValue());
                colValueMap.put(colName, valueProperty);
            }
            return valueProperty;
        }

        public String getValue(String colName) {
            return valueProperty(colName).get();
        }

        public void setValue(String colName, String value) {
            valueProperty(colName).set(value);
        }

        void renameCol(String oldName, String newName) {
            if (!newName.equals(oldName)) {
                colValueMap.put(newName, colValueMap.get(oldName));
                colValueMap.remove(oldName);
            }
        }
    }
}
