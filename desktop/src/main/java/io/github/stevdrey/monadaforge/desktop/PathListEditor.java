package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Edits one list of workspace-relative paths: add, remove and per-entry feedback. */
final class PathListEditor extends VBox {
    private record Row(TextField input, Label error) {}

    private final Field field;
    private final String entryName;
    private final TaskScopeForm form;
    private final Runnable onChange;
    private final VBox rows = new VBox();
    private final Label sectionError = new Label();
    private final List<Row> rendered = new ArrayList<>();
    private final Button add;

    PathListEditor(Field field, String heading, String hint, String entryName, TaskScopeForm form, Runnable onChange) {
        this.field = Objects.requireNonNull(field, "field");
        this.entryName = Objects.requireNonNull(entryName, "entryName");
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
        add = new Button("Add " + entryName);
        add.setOnAction(event -> {
            form.addItem(field);
            rebuild();
            focusRow(rendered.size() - 1);
            onChange.run();
        });

        getChildren().addAll(title, help, rows, sectionError, add);
        rebuild();
    }

    /** Rebuilds the rows from the form; used after structural or whole-form changes. */
    void rebuild() {
        rendered.clear();
        var nodes = new ArrayList<Node>();
        List<String> items = form.items(field);
        for (int i = 0; i < items.size(); i++) {
            nodes.add(row(i, items.get(i)));
        }
        rows.getChildren().setAll(nodes);
        refreshErrors();
    }

    /** Shows the current form errors without touching the entered text. */
    void refreshErrors() {
        for (int i = 0; i < rendered.size(); i++) {
            var row = rendered.get(i);
            var problems = form.errors(field, i);
            FeedbackLabels.show(row.error(), TaskScopeMessages.describeAll(problems));
            row.input().pseudoClassStateChanged(ItemListEditor.INVALID, !problems.isEmpty());
        }
        FeedbackLabels.show(sectionError, TaskScopeMessages.describeAll(form.errors(field, Violation.NO_INDEX)));
    }

    private VBox row(int index, String text) {
        var input = new TextField(text);
        input.setPromptText("Path relative to the workspace, e.g. src/main");
        input.setAccessibleText(Character.toUpperCase(entryName.charAt(0)) + entryName.substring(1) + " " + (index + 1));
        input.textProperty().addListener((observable, before, after) -> {
            form.setItem(field, index, after);
            onChange.run();
        });
        HBox.setHgrow(input, Priority.ALWAYS);
        var error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        rendered.add(new Row(input, error));

        var remove = new Button("Remove");
        remove.setAccessibleText("Remove " + entryName + " " + (index + 1));
        remove.setOnAction(event -> {
            form.removeItem(field, index);
            rebuild();
            focusRow(Math.min(index, rendered.size() - 1));
            onChange.run();
        });
        var line = new HBox(input, remove);
        line.getStyleClass().add("path-row");
        var column = new VBox(line, error);
        column.getStyleClass().add("item-column");
        return column;
    }

    private void focusRow(int index) {
        if (index >= 0 && index < rendered.size()) {
            rendered.get(index).input().requestFocus();
        }
    }
}
