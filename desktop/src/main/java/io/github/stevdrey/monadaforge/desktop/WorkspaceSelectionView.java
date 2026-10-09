package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/** Lets the user pick the local workspace and shows validation feedback before continuing. */
final class WorkspaceSelectionView extends VBox {
    private final WorkspaceSelection selection;
    private final Label requirement = new Label();
    private final Label path = new Label();
    private final Label error = new Label();
    private final Button choose = new Button("Choose workspace…");
    private final CheckBox keepTask = new CheckBox("Keep the task I already entered");
    private final Button proceed = new Button("Continue");
    private Path lastDirectory;
    private Consumer<WorkspaceRoot> onContinue = root -> {};
    private Runnable onSelectionChange = () -> {};
    private BiConsumer<StatusKind, String> onStatus = (kind, text) -> {};

    WorkspaceSelectionView(WorkspaceSelection.Validator validator, Executor background, Executor ui) {
        getStyleClass().add("view");
        selection = new WorkspaceSelection(
                Objects.requireNonNull(validator, "validator"), background, ui, this::render);

        var heading = new Label("Select a workspace");
        heading.getStyleClass().add("view-title");
        requirement.getStyleClass().add("view-body");
        requirement.setWrapText(true);
        path.getStyleClass().add("workspace-path");
        path.setWrapText(true);
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        proceed.getStyleClass().add("primary-button");
        var actions = new HBox(choose, proceed);
        actions.getStyleClass().add("action-row");

        choose.setOnAction(event -> chooseDirectory());
        proceed.setOnAction(event -> {
            if (selection.state() instanceof WorkspaceSelection.State.Selected selected) {
                onContinue.accept(selected.root());
            }
        });

        keepTask.setSelected(true);
        keepTask.setVisible(false);
        keepTask.setManaged(false);

        getChildren().addAll(heading, requirement, path, error, keepTask, actions);
        render(selection.state());
    }

    /** Called whenever the selection state changes, e.g. a new choice, a result or a rejection. */
    void setOnSelectionChange(Runnable handler) {
        onSelectionChange = Objects.requireNonNull(handler, "handler");
    }

    /** Shows {@code message} as the reason {@code path} cannot be used, disabling Continue. */
    void reject(Path path, String message) {
        selection.reject(path, message);
    }

    /** Offers to keep or discard task text entered earlier; only shown when there is some. */
    void offerKeepingTask(boolean visible) {
        keepTask.setVisible(visible);
        keepTask.setManaged(visible);
        keepTask.setSelected(true);
    }

    /** Whether previously entered task text should survive the workspace change. */
    boolean keepTask() {
        return !keepTask.isVisible() || keepTask.isSelected();
    }

    void setOnContinue(Consumer<WorkspaceRoot> handler) {
        onContinue = Objects.requireNonNull(handler, "handler");
    }

    /** Receives the status the shell should show for the current selection state. */
    void setOnStatus(BiConsumer<StatusKind, String> handler) {
        onStatus = Objects.requireNonNull(handler, "handler");
    }

    /** Re-emits the status for the current state, e.g. when this view is shown again. */
    void publishStatus() {
        var presentation = WorkspacePresentation.of(selection.state());
        onStatus.accept(presentation.statusKind(), presentation.statusText());
    }

    private void chooseDirectory() {
        var owner = getScene() == null ? null : getScene().getWindow();
        File picked = WorkspaceChooser.pick(lastDirectory, initial -> {
            var chooser = new DirectoryChooser();
            chooser.setTitle("Choose workspace");
            if (initial != null) {
                chooser.setInitialDirectory(initial);
            }
            return chooser.showDialog(owner);
        });
        selection.select(picked == null ? null : picked.toPath());
    }

    private void render(WorkspaceSelection.State state) {
        // The chooser stays enabled while validating so a stalled file system can be abandoned.
        var presentation = WorkspacePresentation.of(state);
        requirement.setText(presentation.requirementText());
        FeedbackLabels.show(path, presentation.pathText());
        FeedbackLabels.show(error, presentation.errorText());
        proceed.setDisable(!presentation.continueEnabled());
        onSelectionChange.run();
        if (state instanceof WorkspaceSelection.State.Selected selected) {
            // Staleness of this directory is handled by WorkspaceChooser.pick, so Invalid keeps it.
            lastDirectory = selected.root().path();
        }
        publishStatus();
    }
}
