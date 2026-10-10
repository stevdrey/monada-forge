package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Edits one list of text entries: add, remove, per-item feedback and, for ordered lists, reorder.
 *
 * <p>The editor only shows and edits what its {@link EditableList} holds; rules and messages come
 * from the form behind it. Use {@link #forTask} for the task specification lists and {@link
 * #forPaths} for the scope path lists.
 */
final class ItemListEditor extends VBox {
    static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private record Row(TextInputControl input, Label error) {}

    private final EditableList list;
    private final String itemName;
    private final boolean reorderable;
    private final IntFunction<String> prompt;
    private final Function<String, TextInputControl> inputFactory;
    private final Runnable onChange;
    private final VBox rows = new VBox();
    private final Label sectionError = new Label();
    private final List<Row> rendered = new ArrayList<>();

    private ItemListEditor(
            String heading,
            String hint,
            String itemName,
            EditableList list,
            boolean reorderable,
            IntFunction<String> prompt,
            Function<String, TextInputControl> inputFactory,
            Runnable onChange) {
        this.list = Objects.requireNonNull(list, "list");
        this.itemName = Objects.requireNonNull(itemName, "itemName");
        this.reorderable = reorderable;
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.inputFactory = Objects.requireNonNull(inputFactory, "inputFactory");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        getStyleClass().add("item-list");

        var title = new Label(Objects.requireNonNull(heading, "heading"));
        title.getStyleClass().add("form-label");
        var help = new Label(Objects.requireNonNull(hint, "hint"));
        help.getStyleClass().add("form-hint");
        help.setWrapText(true);
        rows.getStyleClass().add("item-rows");
        sectionError.getStyleClass().add("field-error");
        sectionError.setWrapText(true);
        var add = new Button("Add " + itemName);
        add.setOnAction(event -> {
            list.addItem();
            rebuild();
            focusRow(rendered.size() - 1);
            onChange.run();
        });

        getChildren().addAll(title, help, rows, sectionError, add);
        rebuild();
    }

    /** Ordered multi-line items of one task specification field, with reorder buttons. */
    static ItemListEditor forTask(
            TaskSpecificationValidation.Field field,
            String heading,
            String hint,
            String itemName,
            TaskIntakeForm form,
            Runnable onChange) {
        return new ItemListEditor(
                heading,
                hint,
                itemName,
                EditableList.of(form, field),
                true,
                index -> Character.toUpperCase(itemName.charAt(0)) + itemName.substring(1) + " " + (index + 1),
                text -> {
                    var input = new TextArea(text);
                    input.setWrapText(true);
                    input.setPrefRowCount(2);
                    input.getStyleClass().add("item-input");
                    TabTraversal.install(input);
                    return input;
                },
                onChange);
    }

    /** Single-line workspace-relative paths of one scope list; entries can be added and removed. */
    static ItemListEditor forPaths(
            TaskScopeValidation.Field field,
            String heading,
            String hint,
            String itemName,
            TaskScopeForm form,
            Runnable onChange) {
        return new ItemListEditor(
                heading,
                hint,
                itemName,
                EditableList.of(form, field),
                false,
                index -> "Path relative to the workspace, e.g. src/main",
                TextField::new,
                onChange);
    }

    /** Rebuilds the rows from the list; used after structural or whole-form changes. */
    void rebuild() {
        rendered.clear();
        List<String> items = list.items();
        var nodes = new ArrayList<Node>(items.size());
        for (int i = 0; i < items.size(); i++) {
            nodes.add(row(i, items.get(i), items.size()));
        }
        rows.getChildren().setAll(nodes);
        refreshErrors();
    }

    /** Shows the current errors without touching the entered text. */
    void refreshErrors() {
        for (int i = 0; i < rendered.size(); i++) {
            var row = rendered.get(i);
            String problem = list.itemError(i);
            FeedbackLabels.show(row.error(), problem);
            row.input().pseudoClassStateChanged(INVALID, problem != null);
        }
        FeedbackLabels.show(sectionError, list.listError());
    }

    private HBox row(int index, String text, int count) {
        var input = inputFactory.apply(text);
        input.setPromptText(prompt.apply(index));
        input.setAccessibleText(Character.toUpperCase(itemName.charAt(0)) + itemName.substring(1) + " " + (index + 1));
        input.textProperty().addListener((observable, before, after) -> {
            list.setItem(index, after);
            onChange.run();
        });
        var error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        rendered.add(new Row(input, error));

        var controls = new HBox();
        controls.getStyleClass().add("action-row");
        if (reorderable) {
            controls.getChildren().addAll(
                    button("↑", "Move " + itemName + " " + (index + 1) + " up", index == 0, () -> move(index, true)),
                    button(
                            "↓",
                            "Move " + itemName + " " + (index + 1) + " down",
                            index == count - 1,
                            () -> move(index, false)));
        }
        controls.getChildren().add(button("Remove", "Remove " + itemName + " " + (index + 1), false, () -> {
            list.removeItem(index);
            rebuild();
            focusRow(Math.min(index, rendered.size() - 1));
            onChange.run();
        }));

        var column = new VBox();
        column.getStyleClass().add("item-column");
        if (reorderable) {
            column.getChildren().addAll(input, error, controls);
        } else {
            // Short single-line entries: the remove button sits beside the field.
            var line = new HBox(input, controls);
            line.getStyleClass().add("path-row");
            HBox.setHgrow(input, Priority.ALWAYS);
            column.getChildren().addAll(line, error);
        }
        HBox.setHgrow(column, Priority.ALWAYS);
        var row = new HBox();
        row.getStyleClass().add("item-row");
        if (reorderable) {
            var number = new Label(Integer.toString(index + 1));
            number.getStyleClass().add("item-index");
            row.getChildren().add(number);
        }
        row.getChildren().add(column);
        return row;
    }

    private Button button(String label, String description, boolean disabled, Runnable action) {
        var button = new Button(label);
        button.setAccessibleText(description);
        button.setDisable(disabled);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void move(int index, boolean up) {
        boolean moved = up ? list.moveUp(index) : list.moveDown(index);
        if (moved) {
            rebuild();
            focusRow(up ? index - 1 : index + 1);
            onChange.run();
        }
    }

    private void focusRow(int index) {
        if (index >= 0 && index < rendered.size()) {
            rendered.get(index).input().requestFocus();
        }
    }
}
