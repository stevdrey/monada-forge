package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.task.TaskDraftService;
import io.github.stevdrey.monadaforge.core.task.TaskScope;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScopeValidationTest {
    /** Runs tasks only when told to, so tests control the order in which outcomes arrive. */
    private static final class Manual implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runNext() {
            tasks.remove().run();
        }
    }

    @TempDir
    Path temp;

    private TaskDraftService service;
    private TaskScopeForm form;
    private Manual background;
    private Manual ui;
    private int changes;
    private ScopeValidation validation;

    @BeforeEach
    void setUp() throws IOException {
        Path ws = Files.createDirectory(temp.resolve("ws"));
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve("docs"));
        service = new TaskDraftService();
        assertInstanceOf(WorkspaceRootValidation.Accepted.class, service.selectWorkspace(ws));
        form = new TaskScopeForm(
                (mode, allowed, excluded, stillWanted) -> service.selectPaths(allowed, excluded, stillWanted),
                service::clearScope);
        background = new Manual();
        ui = new Manual();
        validation = new ScopeValidation(form, background, ui, () -> changes++);
        form.setMode(TaskScopeForm.Mode.SPECIFIC_PATHS);
        form.addItem(Field.ALLOWED);
        form.setItem(Field.ALLOWED, 0, "src");
    }

    @Test
    void deliversTheResultToTheFormAndStopsBeingPending() {
        validation.start();
        assertTrue(validation.pending());

        background.runNext();
        ui.runNext();

        assertFalse(validation.pending());
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.result());
        assertInstanceOf(TaskScope.Paths.class, service.current().scope());
        assertEquals(2, changes); // once when started, once when delivered
    }

    @Test
    void supersededRequestNeverReachesTheFormNorTheNewerPendingState() {
        validation.start();
        background.runNext();
        form.setItem(Field.ALLOWED, 0, "docs");
        validation.supersede();
        validation.start(); // newer request, still waiting
        int before = changes;

        ui.runNext(); // late outcome of the first request

        assertTrue(validation.pending());
        assertEquals(before, changes);
        assertInstanceOf(TaskScopeForm.Result.NotValidated.class, form.result());
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void supersedingReEnablesANewValidationWhileTheOldOneIsStillStalled() {
        validation.start(); // never runs: a stalled file system
        validation.supersede();

        assertFalse(validation.pending());
        validation.start();
        background.tasks.remove(); // drop the stalled one
        background.runNext();
        ui.runNext();

        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.result());
    }

    @Test
    void lateObsoleteOutcomeDoesNotClearTheScopeANewerRequestAccepted() {
        validation.start();
        var first = background.tasks.remove();
        form.setItem(Field.ALLOWED, 0, "docs");
        validation.supersede();
        validation.start();
        background.runNext();
        ui.runNext();
        var accepted = service.current().scope();
        assertInstanceOf(TaskScope.Paths.class, accepted);

        first.run(); // the obsolete background task finally runs
        while (!ui.tasks.isEmpty()) {
            ui.runNext();
        }

        assertEquals(accepted, service.current().scope());
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.result());
    }

    @Test
    void rejectedExecutorEndsInFailedAndIsNotLeftPending() {
        Executor closed = task -> {
            throw new RejectedExecutionException("shut down");
        };
        var failing = new ScopeValidation(form, closed, ui, () -> {});

        failing.start();

        assertFalse(failing.pending());
        assertInstanceOf(TaskScopeForm.Result.Failed.class, form.result());
    }
}
