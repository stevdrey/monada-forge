package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Edits one ordered list of the task form: add, remove, reorder and per-item feedback. */
final class ItemListEditor extends VBox {
    static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private record Row(TextArea input, Label error) {}

    private final Field field;
    private final String itemName;
    private final TaskIntakeForm form;
    private final Runnable onChange;
    private final VBox rows = new VBox();
    private final Label sectionError = new Label();
    private final List<Row> rendered = new ArrayList<>();

    ItemListEditor(Field field, String heading, String hint, String itemName, TaskIntakeForm form, Runnable onChange) {
        this.field = Objects.requireNonNull(field, "field");
        this.itemName = Objects.requireNonNull(itemName, "itemName");
        this.form = Objects.requireNonNull(form, "form");
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
            form.addItem(field);
            rebuild();
            focusRow(rendered.size() - 1);
            onChange.run();
        });

        getChildren().addAll(title, help, rows, sectionError, add);
        rebuild();
    }

    /** Rebuilds the rows from the form; used after structural changes. */
    void rebuild() {
        rendered.clear();
        List<String> items = form.items(field);
        var nodes = new ArrayList<javafx.scene.Node>(items.size());
        for (int i = 0; i < items.size(); i++) {
            nodes.add(row(i, items.get(i), items.size()));
        }
        rows.getChildren().setAll(nodes);
        refreshErrors();
    }

    /** Shows the current form errors without touching the entered text. */
    void refreshErrors() {
        for (int i = 0; i < rendered.size(); i++) {
            var row = rendered.get(i);
            var problems = form.errors(field, i);
            FeedbackLabels.show(row.error(), TaskIntakeMessages.describeAll(problems));
            row.input().pseudoClassStateChanged(INVALID, !problems.isEmpty());
        }
        FeedbackLabels.show(sectionError, TaskIntakeMessages.describeAll(form.errors(field, Violation.NO_INDEX)));
    }

    private HBox row(int index, String text, int count) {
        var number = new Label(Integer.toString(index + 1));
        number.getStyleClass().add("item-index");

        var input = new TextArea(text);
        input.setPromptText(Character.toUpperCase(itemName.charAt(0)) + itemName.substring(1) + " " + (index + 1));
        input.setWrapText(true);
        input.setPrefRowCount(2);
        input.getStyleClass().add("item-input");
        TabTraversal.install(input);
        input.textProperty().addListener((observable, before, after) -> {
            form.setItem(field, index, after);
            onChange.run();
        });
        var error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        rendered.add(new Row(input, error));

        var up = button("↑", "Move " + itemName + " " + (index + 1) + " up", index == 0, () -> move(index, true));
        var down = button(
                "↓", "Move " + itemName + " " + (index + 1) + " down", index == count - 1, () -> move(index, false));
        var remove = button("Remove", "Remove " + itemName + " " + (index + 1), false, () -> {
            form.removeItem(field, index);
            rebuild();
            focusRow(Math.min(index, rendered.size() - 1));
            onChange.run();
        });
        var controls = new HBox(up, down, remove);
        controls.getStyleClass().add("action-row");

        var column = new VBox(input, error, controls);
        column.getStyleClass().add("item-column");
        HBox.setHgrow(column, Priority.ALWAYS);
        var row = new HBox(number, column);
        row.getStyleClass().add("item-row");
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
        boolean moved = up ? form.moveUp(field, index) : form.moveDown(field, index);
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
