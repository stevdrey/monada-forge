package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.desktop.WorkspaceSelection.State;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSelectionTest {
    @TempDir
    Path temp;

    private final List<State> seen = new ArrayList<>();
    private final Executor direct = Runnable::run;

    private WorkspaceSelection selection(WorkspaceSelection.Validator validator, Executor background, Executor ui) {
        return new WorkspaceSelection(validator, background, ui, seen::add);
    }

    private WorkspaceSelection selection() {
        return selection(WorkspaceRoot::validate, direct, direct);
    }

    @Test
    void startsRequired() {
        assertInstanceOf(State.Required.class, selection().state());
    }

    @Test
    void validDirectoryIsSelectedWithCanonicalPath() throws IOException {
        var real = Files.createDirectory(temp.resolve("real"));
        var link = Files.createSymbolicLink(temp.resolve("link"), real);
        var selection = selection();
        selection.select(link);
        var selected = assertInstanceOf(State.Selected.class, selection.state());
        assertEquals(real.toRealPath(), selected.root().path());
    }

    @Test
    void missingPathAndRegularFileAreInvalid() throws IOException {
        var selection = selection();
        selection.select(temp.resolve("missing"));
        assertInstanceOf(State.Invalid.class, selection.state());
        var file = Files.createFile(temp.resolve("file.txt"));
        selection.select(file);
        var invalid = assertInstanceOf(State.Invalid.class, selection.state());
        assertEquals(file, invalid.requested());
        assertTrue(!invalid.message().isBlank());
    }

    @Test
    void unreadableDirectoryIsInvalid() throws IOException {
        var dir = Files.createDirectory(temp.resolve("locked"));
        try {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "POSIX permissions unavailable");
        }
        assumeTrue(!Files.isReadable(dir), "running with privileges that ignore permissions");
        try {
            var selection = selection();
            selection.select(dir);
            assertInstanceOf(State.Invalid.class, selection.state());
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void unexpectedIoFailureIsInvalidWithoutLeakingDetails() {
        var selection = selection(p -> { throw new IOException("secret detail"); }, direct, direct);
        selection.select(temp);
        var invalid = assertInstanceOf(State.Invalid.class, selection.state());
        assertEquals(WorkspaceMessages.UNEXPECTED_FAILURE, invalid.message());
    }

    @Test
    void cancellingChangesNothing() {
        var selection = selection();
        selection.select(temp);
        var before = selection.state();
        seen.clear();
        selection.select(null);
        assertEquals(before, selection.state());
        assertTrue(seen.isEmpty());
    }

    @Test
    void invalidSelectionIsRecoverable() {
        var selection = selection();
        selection.select(temp.resolve("missing"));
        assertInstanceOf(State.Invalid.class, selection.state());
        selection.select(temp);
        assertInstanceOf(State.Selected.class, selection.state());
    }

    @Test
    void staleResultIsIgnored() throws IOException {
        var first = Files.createDirectory(temp.resolve("first"));
        var second = Files.createDirectory(temp.resolve("second"));
        Queue<Runnable> backgroundQueue = new ArrayDeque<>();
        var selection = selection(WorkspaceRoot::validate, backgroundQueue::add, direct);
        selection.select(first);
        selection.select(second);
        assertInstanceOf(State.Validating.class, selection.state());
        backgroundQueue.remove().run(); // result for `first` arrives late
        assertInstanceOf(State.Validating.class, selection.state());
        backgroundQueue.remove().run();
        var selected = assertInstanceOf(State.Selected.class, selection.state());
        assertEquals(second.toRealPath(), selected.root().path());
    }

    @Test
    void validationRunsOnBackgroundAndResultsOnUiExecutor() {
        List<String> trail = new ArrayList<>();
        var selection = selection(
                p -> {
                    trail.add("validate");
                    return WorkspaceRoot.validate(p);
                },
                task -> { trail.add("background"); task.run(); },
                task -> { trail.add("ui"); task.run(); });
        selection.select(temp);
        assertEquals(List.of("background", "validate", "ui"), trail);
    }
}
