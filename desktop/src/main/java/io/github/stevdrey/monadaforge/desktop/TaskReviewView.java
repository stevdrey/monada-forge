package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskSpecification;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Review step: the workspace, task and scope shown together before anything else may happen.
 *
 * <p>The page is read-only apart from navigation and the confirm action. It shows the draft as core
 * holds it and asks for no input; confirming calls back into the application, which changes
 * in-memory state only. The standing notice states that confirmation authorizes nothing, so a later
 * phase can add its own approval boundary for side effects.
 */
final class TaskReviewView extends VBox {
    private final Supplier<TaskReviewModel> source;
    private final VBox details = new VBox();
    private final VBox blockers = new VBox();
    private final Label confirmation = new Label();
    private final Button confirm = new Button("Confirm task");
    private TaskReviewModel model;
    private Runnable onConfirm = () -> {};
    private Runnable onEditWorkspace = () -> {};
    private Runnable onEditTask = () -> {};
    private Runnable onEditScope = () -> {};
    private BiConsumer<StatusKind, String> onStatus = (kind, text) -> {};

    TaskReviewView(Supplier<TaskReviewModel> source) {
        this.source = Objects.requireNonNull(source, "source");
        getStyleClass().addAll("view", "task-form", "review-form");

        var heading = new Label("Review the task");
        heading.getStyleClass().add("view-title");
        var intro = new Label("Check everything below. Nothing on this page is read, changed or run.");
        intro.getStyleClass().add("view-body");
        intro.setWrapText(true);
        var notice = new Label(TaskReviewMessages.NOT_AUTHORIZATION);
        notice.getStyleClass().add("review-notice");
        notice.setWrapText(true);

        details.getStyleClass().add("review-details");
        blockers.getStyleClass().add("review-blockers");
        confirmation.getStyleClass().add("review-ready");
        confirmation.setWrapText(true);

        confirm.getStyleClass().add("primary-button");
        confirm.setOnAction(event -> onConfirm.run());
        var editTask = new Button("Edit task");
        editTask.setOnAction(event -> onEditTask.run());
        var editScope = new Button("Edit scope");
        editScope.setOnAction(event -> onEditScope.run());
        var editWorkspace = new Button("Change workspace");
        editWorkspace.setOnAction(event -> onEditWorkspace.run());
        var actions = new HBox(confirm, editTask, editScope, editWorkspace);
        actions.getStyleClass().add("action-row");

        getChildren().addAll(heading, intro, notice, details, blockers, confirmation, actions);
        refresh();
    }

    /** Rebuilds the page from the current draft; call it whenever the page is about to be shown. */
    void refresh() {
        model = source.get();
        details.getChildren().clear();
        blockers.getChildren().clear();

        details.getChildren().add(section("Workspace", model.workspace().map(Object::toString).orElse("Not selected"), true));
        TaskSpecification specification = model.specification().orElse(null);
        if (specification == null) {
            details.getChildren().add(section("Task", "Not defined", false));
        } else {
            details.getChildren().add(section("Title", specification.title(), false));
            details.getChildren().add(section("Description", specification.description(), false));
            details.getChildren().add(list("Acceptance criteria", specification.acceptanceCriteria(), true));
            details.getChildren().add(list("Constraints", specification.constraints(), false));
            details.getChildren().add(list("Non-goals", specification.nonGoals(), false));
        }
        details.getChildren().add(scopeSection());

        for (var blocker : model.blockers()) {
            var label = new Label(TaskReviewMessages.describe(blocker));
            label.getStyleClass().add("review-blocker");
            label.setWrapText(true);
            blockers.getChildren().add(label);
        }
        blockers.setVisible(!model.blockers().isEmpty());
        blockers.setManaged(!model.blockers().isEmpty());
        FeedbackLabels.show(confirmation, model.confirmed() ? TaskReviewMessages.CONFIRMED : null);
        confirm.setDisable(!model.canConfirm());
        publishStatus();
    }

    void setOnConfirm(Runnable handler) {
        onConfirm = Objects.requireNonNull(handler, "handler");
    }

    void setOnEditTask(Runnable handler) {
        onEditTask = Objects.requireNonNull(handler, "handler");
    }

    void setOnEditScope(Runnable handler) {
        onEditScope = Objects.requireNonNull(handler, "handler");
    }

    void setOnChangeWorkspace(Runnable handler) {
        onEditWorkspace = Objects.requireNonNull(handler, "handler");
    }

    void setOnStatus(BiConsumer<StatusKind, String> handler) {
        onStatus = Objects.requireNonNull(handler, "handler");
    }

    /** Emits the status for the current state; a view that is not displayed stays silent. */
    void publishStatus() {
        if (getParent() == null || model == null) {
            return;
        }
        var status = TaskReviewMessages.status(model);
        onStatus.accept(status.kind(), status.text());
    }

    private VBox scopeSection() {
        var section = new VBox();
        section.getStyleClass().addAll("review-section", "review-scope");
        section.getChildren().add(heading("Scope"));
        switch (model.scopeMode()) {
            case UNSET -> section.getChildren().add(text("Not defined", false));
            case ENTIRE_WORKSPACE -> {
                section.getChildren().add(text("The entire workspace, except the excluded paths", false));
                var warning = new Label("This is the broadest scope. Confirm only if the task really needs it.");
                warning.getStyleClass().add("scope-warning");
                warning.setWrapText(true);
                section.getChildren().add(warning);
            }
            case SPECIFIC_PATHS -> section.getChildren().add(text("Only the allowed paths", false));
        }
        if (model.scopeMode() == TaskReviewModel.ScopeMode.SPECIFIC_PATHS) {
            section.getChildren().add(list("Allowed paths", model.allowed(), false));
        }
        if (model.scopeMode() != TaskReviewModel.ScopeMode.UNSET) {
            section.getChildren().add(list("Excluded paths", model.excluded(), false));
        }
        return section;
    }

    private static VBox section(String title, String value, boolean path) {
        var section = new VBox(heading(title), text(value, path));
        section.getStyleClass().add("review-section");
        return section;
    }

    /** A titled list; ordered lists are numbered, and an empty list says so explicitly. */
    private static VBox list(String title, List<String> items, boolean numbered) {
        var section = new VBox(heading(title));
        section.getStyleClass().add("review-section");
        var rows = new VBox();
        rows.getStyleClass().add("review-list");
        if (items.isEmpty()) {
            rows.getChildren().add(text("None", false));
        }
        for (int i = 0; i < items.size(); i++) {
            rows.getChildren().add(text((numbered ? (i + 1) + ". " : "• ") + items.get(i), false));
        }
        section.getChildren().add(rows);
        return section;
    }

    private static Label heading(String title) {
        var label = new Label(title);
        label.getStyleClass().addAll("form-label", "review-heading");
        return label;
    }

    private static Label text(String value, boolean path) {
        var label = new Label(value);
        label.getStyleClass().add(path ? "workspace-path" : "view-body");
        label.setWrapText(true);
        return label;
    }
}
