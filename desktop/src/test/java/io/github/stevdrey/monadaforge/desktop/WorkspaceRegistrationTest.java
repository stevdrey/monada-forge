package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceRegistrationTest {
    @TempDir
    Path temp;

    private final Executor direct = Runnable::run;
    private final Queue<Runnable> queued = new ArrayDeque<>();
    private final List<String> events = new ArrayList<>();
    private final WorkspaceRegistration.Outcome outcome = new WorkspaceRegistration.Outcome() {
        @Override
        public void accepted(WorkspaceRoot fresh, boolean discardTask) {
            events.add("accepted " + fresh.path().getFileName() + " discard=" + discardTask);
        }

        @Override
        public void rejected(Path requested, String message) {
            events.add("rejected " + requested.getFileName() + ": " + message);
        }
    };
    private WorkspaceRoot a;
    private WorkspaceRoot b;

    @BeforeEach
    void setUp() throws IOException {
        a = root(Files.createDirectory(temp.resolve("a")));
        b = root(Files.createDirectory(temp.resolve("b")));
    }

    private static WorkspaceRoot root(Path path) throws IOException {
        return ((WorkspaceRootValidation.Accepted) WorkspaceRoot.validate(path)).root();
    }

    private WorkspaceRegistration registration(WorkspaceSelection.Validator validator, Executor ui) {
        return new WorkspaceRegistration(validator, direct, ui, outcome);
    }

    @Test
    void deliversTheFreshlyValidatedRootAndTheDiscardChoice() throws IOException {
        // Revalidation resolves to another directory, as if the path had been swapped for a link.
        var registration = registration(candidate -> new WorkspaceRootValidation.Accepted(b), direct);

        registration.register(a, true);

        assertEquals(List.of("accepted b discard=true"), events);
    }

    @Test
    void rejectionCarriesTheActionableReason() {
        var registration = registration(WorkspaceRoot::validate, direct);
        a.path().toFile().delete();

        registration.register(a, false);

        assertEquals(1, events.size());
        assertTrue(events.getFirst().startsWith("rejected a: " + WorkspaceMessages.describe(
                WorkspaceRootValidation.Reason.NOT_FOUND)), events.toString());
    }

    @Test
    void unexpectedFailuresAreReportedWithoutDetails() {
        registration(candidate -> {
                    throw new IOException("secret detail");
                }, direct)
                .register(a, false);
        registration(candidate -> {
                    throw new IllegalStateException("secret detail");
                }, direct)
                .register(b, false);

        assertEquals(
                List.of(
                        "rejected a: " + WorkspaceMessages.UNEXPECTED_FAILURE,
                        "rejected b: " + WorkspaceMessages.UNEXPECTED_FAILURE),
                events);
    }

    @Test
    void aNewerRegistrationDropsTheOlderResult() {
        var registration = registration(WorkspaceRoot::validate, queued::add);

        registration.register(a, false);
        registration.register(b, false);
        queued.forEach(Runnable::run);

        assertEquals(List.of("accepted b discard=false"), events);
    }

    @Test
    void aSelectionChangeDropsAPendingResultEvenForTheSameRoot() {
        var registration = registration(WorkspaceRoot::validate, queued::add);

        registration.register(a, false); // A, slow
        registration.invalidate(); // user picks B
        registration.invalidate(); // and then A again
        queued.forEach(Runnable::run);

        assertTrue(events.isEmpty(), events.toString());
    }

    @Test
    void aRejectedExecutorNeverLeavesTheViewWaiting() {
        Executor shutDown = task -> {
            throw new RejectedExecutionException();
        };
        new WorkspaceRegistration(WorkspaceRoot::validate, shutDown, direct, outcome).register(a, false);

        assertEquals(List.of("rejected a: " + WorkspaceMessages.UNEXPECTED_FAILURE), events);
    }
}
