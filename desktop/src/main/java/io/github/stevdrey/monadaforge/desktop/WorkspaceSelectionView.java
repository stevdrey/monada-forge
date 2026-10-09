package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/** Lets the user pick the local workspace and shows validation feedback before continuing. */
public final class WorkspaceSelectionView extends VBox {
    private final WorkspaceSelection selection;
    private final Label requirement = new Label();
    private final Label path = new Label();
    private final Label error = new Label();
    private final Button choose = new Button("Choose workspace…");
    private final Button proceed = new Button("Continue");
    private Path lastDirectory;
    private Consumer<WorkspaceRoot> onContinue = root -> {};

    WorkspaceSelectionView(WorkspaceSelection.Validator validator,
            java.util.concurrent.Executor background, java.util.concurrent.Executor ui) {
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

        choose.setOnAction(event -> chooseDirectory());
        proceed.setOnAction(event -> {
            if (selection.state() instanceof WorkspaceSelection.State.Selected selected) {
                onContinue.accept(selected.root());
            }
        });

        getChildren().addAll(heading, requirement, path, error, choose, proceed);
        render(selection.state());
    }

    public void setOnContinue(Consumer<WorkspaceRoot> handler) {
        onContinue = Objects.requireNonNull(handler, "handler");
    }

    private void chooseDirectory() {
        var chooser = new DirectoryChooser();
        chooser.setTitle("Choose workspace");
        if (lastDirectory != null) {
            chooser.setInitialDirectory(lastDirectory.toFile());
        }
        File picked = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
        selection.select(picked == null ? null : picked.toPath());
    }

    private void render(WorkspaceSelection.State state) {
        boolean validating = state instanceof WorkspaceSelection.State.Validating;
        choose.setDisable(validating);
        proceed.setDisable(!(state instanceof WorkspaceSelection.State.Selected));
        switch (state) {
            case WorkspaceSelection.State.Required required -> {
                requirement.setText("A workspace is required before you can continue. "
                        + "Choose the local folder Monada Forge will work in.");
                show(path, null);
                show(error, null);
            }
            case WorkspaceSelection.State.Validating validatingState -> {
                requirement.setText("Checking the selected folder…");
                show(path, null);
                show(error, null);
            }
            case WorkspaceSelection.State.Selected selected -> {
                requirement.setText("Workspace selected. You can choose a different folder at any time.");
                lastDirectory = selected.root().path();
                show(path, selected.root().path().toString());
                show(error, null);
            }
            case WorkspaceSelection.State.Invalid invalid -> {
                requirement.setText("A valid workspace is required before you can continue.");
                show(path, invalid.requested().toString());
                show(error, invalid.message());
            }
        }
    }

    private static void show(Label label, String text) {
        boolean visible = text != null;
        label.setText(visible ? text : "");
        label.setVisible(visible);
        label.setManaged(visible);
    }
}
