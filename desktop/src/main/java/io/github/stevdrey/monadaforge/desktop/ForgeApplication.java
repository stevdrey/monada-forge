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
    // Identifies the latest selection or Continue; older registration results are dropped (UI thread only).
    private long registration;

    @Override
    public void start(Stage stage) {
        validation = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("workspace-validation-", 0).factory());
        var shell = new ApplicationShell(PRODUCT_NAME, "A workspace for agent-assisted software delivery.");
        var selectionView = new WorkspaceSelectionView(WorkspaceRoot::validate, validation, Platform::runLater);
        selectionView.setOnStatus(shell.status()::show);
        selectionView.setOnSelectionChange(() -> registration++);
        // One form instance for the session, so entered text survives changing the workspace.
        var drafts = new TaskDraftService();
        TaskIntakeView[] intake = new TaskIntakeView[1];
        intake[0] = new TaskIntakeView(new TaskIntakeForm(drafts::updateSpecification, drafts::clearSpecification), () -> {
            selectionView.offerKeepingTask(intake[0].hasContent());
            shell.setContent(selectionView);
            selectionView.publishStatus();
        });
        var intakeView = intake[0];
        intakeView.setOnStatus(shell.status()::show);
        selectionView.setOnContinue(root -> {
            // Discarding waits for a successful registration, so a rejected workspace loses nothing.
            registerWorkspace(
                    drafts, root, ++registration, !selectionView.keepTask(), shell, selectionView, intakeView);
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
     * Revalidates the selected workspace off the UI thread (it touches the file system) and then
     * shows intake. Revalidation changes no shared state: the freshly validated root is applied to
     * the draft and displayed on the UI thread, and only if no selection change or newer Continue
     * happened since {@code token} was issued; the task is discarded (when {@code discardTask}) at that same point. A slow, obsolete request therefore cannot override a
     * newer choice (including A, B, A). A rejection is returned to the selection view with its reason.
     */
    private void registerWorkspace(
            TaskDraftService drafts,
            WorkspaceRoot root,
            long token,
            boolean discardTask,
            ApplicationShell shell,
            WorkspaceSelectionView selectionView,
            TaskIntakeView intakeView) {
        validation.execute(() -> {
            WorkspaceRoot fresh = null;
            String failure = null;
            try {
                switch (WorkspaceRoot.validate(root.path())) {
                    case WorkspaceRootValidation.Accepted accepted -> fresh = accepted.root();
                    case WorkspaceRootValidation.Rejected rejected ->
                            failure = WorkspaceMessages.describe(rejected.reason());
                }
            } catch (IOException | RuntimeException e) {
                failure = WorkspaceMessages.UNEXPECTED_FAILURE;
            }
            WorkspaceRoot validated = fresh;
            String message = failure;
            Platform.runLater(() -> {
                if (token != registration) {
                    return; // the user has since chosen or confirmed something else
                }
                if (validated != null) {
                    if (discardTask) {
                        intakeView.reset();
                        drafts.clear();
                    }
                    drafts.selectWorkspace(validated);
                    intakeView.setWorkspace(validated.path());
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
