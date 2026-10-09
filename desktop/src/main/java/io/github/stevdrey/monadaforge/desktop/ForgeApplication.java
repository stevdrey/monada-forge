package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskDraftService;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Desktop entry point. Workflow execution will be introduced in later increments. */
public final class ForgeApplication extends Application {
    static final String PRODUCT_NAME = "Monada Forge";

    // One cheap virtual thread per validation: a newer selection must not queue behind a stalled one.
    private ExecutorService validation;

    @Override
    public void start(Stage stage) {
        validation = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("workspace-validation-", 0).factory());
        var shell = new ApplicationShell(PRODUCT_NAME, "A workspace for agent-assisted software delivery.");
        var selectionView = new WorkspaceSelectionView(WorkspaceRoot::validate, validation, Platform::runLater);
        selectionView.setOnStatus(shell.status()::show);
        // One form instance for the session, so entered text survives changing the workspace.
        var drafts = new TaskDraftService();
        TaskIntakeView[] intake = new TaskIntakeView[1];
        intake[0] = new TaskIntakeView(new TaskIntakeForm(drafts::updateSpecification), () -> {
            selectionView.offerKeepingTask(intake[0].hasContent());
            shell.setContent(selectionView);
            selectionView.publishStatus();
        });
        var intakeView = intake[0];
        intakeView.setOnStatus(shell.status()::show);
        selectionView.setOnContinue(root -> {
            if (!selectionView.keepTask()) {
                intakeView.reset();
                drafts.clear();
            }
            registerWorkspace(drafts, root, shell, selectionView, intakeView);
        });
        shell.setContent(selectionView);
        selectionView.publishStatus();

        var scene = new Scene(shell, 880, 560);

        stage.setTitle(PRODUCT_NAME);
        stage.setMinWidth(520);
        stage.setMinHeight(360);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Registers the selected workspace with core off the UI thread (it touches the file system), then
     * shows intake. The result is applied only while {@code root} is still the selected workspace, so
     * a slow registration cannot override a newer choice. A rejection is returned to the selection
     * view with its reason.
     */
    private void registerWorkspace(
            TaskDraftService drafts,
            WorkspaceRoot root,
            ApplicationShell shell,
            WorkspaceSelectionView selectionView,
            TaskIntakeView intakeView) {
        validation.execute(() -> {
            String failure;
            try {
                failure = switch (drafts.selectWorkspace(root.path())) {
                    case WorkspaceRootValidation.Accepted _ -> null;
                    case WorkspaceRootValidation.Rejected rejected -> WorkspaceMessages.describe(rejected.reason());
                };
            } catch (IOException | RuntimeException e) {
                failure = WorkspaceMessages.UNEXPECTED_FAILURE;
            }
            String message = failure;
            Platform.runLater(() -> {
                if (selectionView.selectedRoot() == null || !selectionView.selectedRoot().path().equals(root.path())) {
                    return; // the user has since chosen something else
                }
                if (message == null) {
                    intakeView.setWorkspace(root.path());
                    shell.setContent(intakeView);
                    intakeView.publishStatus();
                } else {
                    selectionView.reject(root.path(), message);
                }
            });
        });
    }

    @Override
    public void stop() {
        if (validation != null) {
            validation.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
