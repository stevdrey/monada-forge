package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Violation;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Manual task intake: title, description, ordered acceptance criteria, constraints and non-goals.
 *
 * <p>All rules come from core through {@link TaskIntakeForm}; this view only collects text and shows
 * the feedback. Entered text is displayed and stored as data and is never interpreted.
 */
final class TaskIntakeView extends VBox {
    private final TaskIntakeForm form;
    private final Label workspace = new Label();
    private final TextField title = new TextField();
    private final Label titleError = new Label();
    private final TextArea description = new TextArea();
    private final Label descriptionError = new Label();
    private final ItemListEditor criteria;
    private final ItemListEditor constraints;
    private final ItemListEditor nonGoals;
    private final Label outcome = new Label();
    private BiConsumer<StatusKind, String> onStatus = (kind, text) -> {};

    TaskIntakeView(TaskIntakeForm form, Runnable onChangeWorkspace) {
        this.form = Objects.requireNonNull(form, "form");
        Objects.requireNonNull(onChangeWorkspace, "onChangeWorkspace");
        getStyleClass().addAll("view", "task-form");

        var heading = new Label("Describe the task");
        heading.getStyleClass().add("view-title");
        workspace.getStyleClass().add("workspace-path");
        workspace.setWrapText(true);
        var intro = new Label("Enter the task, how it will be judged complete and what must stay out of scope. "
                + "What you type is kept as plain data and is never executed.");
        intro.getStyleClass().add("view-body");
        intro.setWrapText(true);

        title.setPromptText("Short summary of the task");
        title.setText(form.title());
        title.textProperty().addListener((observable, before, after) -> {
            form.setTitle(after);
            refresh();
        });
        description.setPromptText("Detailed description");
        description.setWrapText(true);
        description.setPrefRowCount(6);
        description.setText(form.description());
        description.textProperty().addListener((observable, before, after) -> {
            form.setDescription(after);
            refresh();
        });
        titleError.getStyleClass().add("field-error");
        titleError.setWrapText(true);
        descriptionError.getStyleClass().add("field-error");
        descriptionError.setWrapText(true);

        criteria = new ItemListEditor(
                Field.ACCEPTANCE_CRITERIA,
                "Acceptance criteria (required)",
                "Each item is one distinct, checkable outcome. Order is kept.",
                "criterion",
                form,
                this::refresh);
        constraints = new ItemListEditor(
                Field.CONSTRAINTS,
                "Constraints (optional)",
                "Rules the solution must respect.",
                "constraint",
                form,
                this::refresh);
        nonGoals = new ItemListEditor(
                Field.NON_GOALS,
                "Non-goals (optional)",
                "Work that is explicitly out of scope.",
                "non-goal",
                form,
                this::refresh);

        outcome.getStyleClass().add("view-body");
        outcome.setWrapText(true);
        var validate = new Button("Validate task");
        validate.getStyleClass().add("primary-button");
        validate.setOnAction(event -> {
            form.submit();
            refresh();
        });
        var change = new Button("Change workspace");
        change.setOnAction(event -> onChangeWorkspace.run());
        var actions = new HBox(validate, change);
        actions.getStyleClass().add("action-row");

        getChildren().addAll(
                heading,
                workspace,
                intro,
                field("Title (required)", title, titleError),
                field("Description (required)", description, descriptionError),
                criteria,
                constraints,
                nonGoals,
                outcome,
                actions);
        refresh();
    }

    /** Discards everything entered and shows the empty form. */
    void reset() {
        form.clear();
        title.setText("");
        description.setText("");
        criteria.rebuild();
        constraints.rebuild();
        nonGoals.rebuild();
        refresh();
    }

    boolean hasContent() {
        return form.hasContent();
    }

    void setWorkspace(Path path) {
        workspace.setText(Objects.requireNonNull(path, "path").toString());
    }

    void setOnStatus(BiConsumer<StatusKind, String> handler) {
        onStatus = Objects.requireNonNull(handler, "handler");
    }

    /** Re-emits the status for the current state, e.g. when this view is shown again. */
    void publishStatus() {
        var status = TaskIntakeMessages.status(form.result());
        onStatus.accept(status.kind(), status.text());
    }

    private static VBox field(String label, Control input, Label error) {
        var name = new Label(label);
        name.getStyleClass().add("form-label");
        var box = new VBox(name, input, error);
        box.getStyleClass().add("form-field");
        return box;
    }

    private void refresh() {
        showErrors(titleError, title, form.errors(Field.TITLE, Violation.NO_INDEX));
        showErrors(descriptionError, description, form.errors(Field.DESCRIPTION, Violation.NO_INDEX));
        criteria.refreshErrors();
        constraints.refreshErrors();
        nonGoals.refreshErrors();
        outcome.setText(TaskIntakeMessages.status(form.result()).text() + ".");
        publishStatus();
    }

    private static void showErrors(Label label, Control input, List<Violation> problems) {
        ItemListEditor.show(
                label,
                problems.isEmpty()
                        ? null
                        : problems.stream().map(TaskIntakeMessages::describe).collect(Collectors.joining(" ")));
        input.pseudoClassStateChanged(ItemListEditor.INVALID, !problems.isEmpty());
    }
}
