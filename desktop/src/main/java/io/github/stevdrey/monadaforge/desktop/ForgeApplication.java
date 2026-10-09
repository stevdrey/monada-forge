package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskDraftService;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import java.nio.file.Path;
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
        var form = new TaskIntakeForm(drafts::updateSpecification, drafts::clearSpecification);
        var intakeView = new TaskIntakeView(form);
        intakeView.setOnStatus(shell.status()::show);
        intakeView.setOnChangeWorkspace(() -> {
            selectionView.offerKeepingTask(intakeView.hasContent());
            shell.setContent(selectionView);
            selectionView.publishStatus();
        });

        // Applies the draft and the view only for the latest confirmed workspace; see WorkspaceRegistration.
        var registration = new WorkspaceRegistration(
                WorkspaceRoot::validate, validation, Platform::runLater, new WorkspaceRegistration.Outcome() {
                    @Override
                    public void accepted(WorkspaceRoot fresh, boolean discardTask) {
                        if (discardTask) {
                            intakeView.reset();
                            drafts.clear();
                        }
                        drafts.selectWorkspace(fresh);
                        intakeView.setWorkspace(fresh.path());
                        shell.setContent(intakeView);
                        intakeView.publishStatus();
                    }

                    @Override
                    public void rejected(Path requested, String message) {
                        selectionView.reject(requested, message);
                    }
                });
        selectionView.setOnSelectionChange(registration::invalidate);
        // Discarding waits for a successful registration, so a rejected workspace loses nothing.
        selectionView.setOnContinue(root -> registration.register(root, !selectionView.keepTask()));
        shell.setContent(selectionView);
        selectionView.publishStatus();

        var scene = new Scene(shell, 880, 560);

        stage.setTitle(PRODUCT_NAME);
        stage.setMinWidth(520);
        stage.setMinHeight(360);
        stage.setScene(scene);
        stage.show();
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
