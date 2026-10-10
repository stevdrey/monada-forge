package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Task scope step: which parts of the selected workspace are allowed or excluded.
 *
 * <p>All rules come from core through {@link TaskScopeForm}; this view only collects relative paths
 * and shows the feedback. Whole-workspace access is a separate, explicit choice and is never implied
 * by an empty list. Validation touches the file system, so it runs on the background executor and
 * its outcome is applied on the UI executor only if the form has not changed meanwhile. Nothing is
 * read, created or modified by this step beyond core's existence and containment checks.
 */
final class TaskScopeView extends VBox {
    private static final String ENTIRE_WORKSPACE_WARNING = "The whole workspace is in scope except the excluded "
            + "paths. Choose this only if the task really needs it.";
    private static final System.Logger LOG = System.getLogger(TaskScopeView.class.getName());

    private final TaskScopeForm form;
    private final Executor background;
    private final Executor ui;
    private final Label workspace = new Label();
    private final RadioButton specific = new RadioButton("Only the allowed paths");
    private final RadioButton entire = new RadioButton("The entire workspace");
    private final ToggleGroup modes = new ToggleGroup();
    private final Label warning = new Label();
    private final PathListEditor allowed;
    private final PathListEditor excluded;
    private final Label outcome = new Label();
    private final Button validate = new Button("Validate scope");
    private Runnable onChangeWorkspace = () -> {};
    private Runnable onBack = () -> {};
    private BiConsumer<StatusKind, String> onStatus = (kind, text) -> {};
    private boolean pending;
    private long requestId;

    TaskScopeView(TaskScopeForm form, Executor background, Executor ui) {
        this.form = Objects.requireNonNull(form, "form");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        getStyleClass().addAll("view", "task-form", "scope-form");

        var heading = new Label("Define the task scope");
        heading.getStyleClass().add("view-title");
        workspace.getStyleClass().add("workspace-path");
        workspace.setWrapText(true);
        var intro = new Label("Choose which parts of the workspace the task may touch. Paths are relative to "
                + "the workspace and are checked against its boundary. Nothing is read or changed by this step.");
        intro.getStyleClass().add("view-body");
        intro.setWrapText(true);

        specific.setToggleGroup(modes);
        entire.setToggleGroup(modes);
        specific.setOnAction(event -> choose(TaskScopeForm.Mode.SPECIFIC_PATHS));
        entire.setOnAction(event -> choose(TaskScopeForm.Mode.ENTIRE_WORKSPACE));
        warning.getStyleClass().add("scope-warning");
        warning.setWrapText(true);
        var mode = new VBox(label("Scope (required)"), specific, entire, warning);
        mode.getStyleClass().add("scope-mode");

        allowed = new PathListEditor(
                Field.ALLOWED,
                "Allowed paths",
                "Files or directories that exist in the workspace. At least one is required unless the "
                        + "entire workspace is chosen.",
                "allowed path",
                form,
                this::refresh);
        excluded = new PathListEditor(
                Field.EXCLUDED,
                "Excluded paths (optional)",
                "Parts inside the scope that stay off limits. Exclusions always win.",
                "excluded path",
                form,
                this::refresh);

        outcome.getStyleClass().add("view-body");
        outcome.setWrapText(true);
        validate.getStyleClass().add("primary-button");
        validate.setOnAction(event -> submit());
        var back = new Button("Back to task");
        back.setOnAction(event -> onBack.run());
        var change = new Button("Change workspace");
        change.setOnAction(event -> onChangeWorkspace.run());
        var actions = new HBox(validate, back, change);
        actions.getStyleClass().add("action-row");

        getChildren().addAll(heading, workspace, intro, mode, allowed, excluded, outcome, actions);
        syncFromForm();
    }

    /** Discards everything entered and shows the empty form. */
    void reset() {
        form.clear();
        syncFromForm();
    }

    /** Shows the form after it was changed from outside, e.g. by a workspace change. */
    void syncFromForm() {
        // A validation still running belongs to the previous state; its outcome is ignored.
        pending = false;
        requestId++;
        modes.selectToggle(
                switch (form.mode()) {
                    case NOT_CHOSEN -> null;
                    case SPECIFIC_PATHS -> specific;
                    case ENTIRE_WORKSPACE -> entire;
                });
        allowed.rebuild();
        excluded.rebuild();
        refresh();
    }

    boolean hasContent() {
        return form.hasContent();
    }

    void setWorkspace(Path path) {
        workspace.setText(Objects.requireNonNull(path, "path").toString());
    }

    void setOnChangeWorkspace(Runnable handler) {
        onChangeWorkspace = Objects.requireNonNull(handler, "handler");
    }

    void setOnBack(Runnable handler) {
        onBack = Objects.requireNonNull(handler, "handler");
    }

    void setOnStatus(BiConsumer<StatusKind, String> handler) {
        onStatus = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Emits the status for the current state, e.g. when this view is shown again. A view that is not
     * displayed stays silent, so a late validation result cannot overwrite another view's status.
     */
    void publishStatus() {
        if (getParent() == null) {
            return;
        }
        var status = TaskScopeMessages.status(form.result(), form.needsRevalidation());
        onStatus.accept(status.kind(), status.text());
    }

    private static Label label(String text) {
        var label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private void choose(TaskScopeForm.Mode mode) {
        form.setMode(mode);
        refresh();
    }

    private void submit() {
        var request = form.request();
        long id = ++requestId;
        pending = true;
        refresh();
        try {
            background.execute(() -> {
                var validation = form.validate(request);
                ui.execute(() -> finish(id, request, validation));
            });
        } catch (RejectedExecutionException e) {
            // E.g. the executor was shut down: never leave the view waiting for a result.
            LOG.log(System.Logger.Level.WARNING, "Scope validation was rejected", e);
            finish(id, request, null);
        }
    }

    private void finish(long id, TaskScopeForm.Request request, TaskScopeValidation validation) {
        if (id == requestId) {
            pending = false;
        }
        form.complete(request, validation);
        allowed.refreshErrors();
        excluded.refreshErrors();
        refresh();
    }

    private void refresh() {
        boolean wholeWorkspace = form.mode() == TaskScopeForm.Mode.ENTIRE_WORKSPACE;
        FeedbackLabels.show(warning, wholeWorkspace ? ENTIRE_WORKSPACE_WARNING : null);
        allowed.setVisible(!wholeWorkspace);
        allowed.setManaged(!wholeWorkspace);
        allowed.refreshErrors();
        excluded.refreshErrors();
        validate.setDisable(pending);
        var status = TaskScopeMessages.status(form.result(), form.needsRevalidation());
        outcome.setText(pending ? "Checking the scope…" : status.text() + ".");
        publishStatus();
    }
}
