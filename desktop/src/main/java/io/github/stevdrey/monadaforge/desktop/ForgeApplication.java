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
            registerWorkspace(drafts, root, shell, intakeView);
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

    /** Registers the workspace with core off the UI thread (it touches the file system), then shows intake. */
    private void registerWorkspace(
            TaskDraftService drafts, WorkspaceRoot root, ApplicationShell shell, TaskIntakeView intakeView) {
        validation.execute(() -> {
            boolean accepted;
            try {
                accepted = drafts.selectWorkspace(root.path()) instanceof WorkspaceRootValidation.Accepted;
            } catch (IOException | RuntimeException e) {
                accepted = false;
            }
            boolean ok = accepted;
            Platform.runLater(() -> {
                if (ok) {
                    intakeView.setWorkspace(root.path());
                    shell.setContent(intakeView);
                    intakeView.publishStatus();
                } else {
                    shell.status().show(StatusKind.ERROR, WorkspaceMessages.UNEXPECTED_FAILURE);
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
