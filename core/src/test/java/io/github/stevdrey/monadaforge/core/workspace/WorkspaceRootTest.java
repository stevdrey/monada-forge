package io.github.stevdrey.monadaforge.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation.Accepted;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation.Reason;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation.Rejected;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceRootTest {

    @TempDir
    Path temp;

    private static WorkspaceRoot accepted(Path path) throws IOException {
        return assertInstanceOf(Accepted.class, WorkspaceRoot.validate(path)).root();
    }

    private static void assertRejected(Path path, Reason reason) throws IOException {
        Rejected rejected = assertInstanceOf(Rejected.class, WorkspaceRoot.validate(path));
        assertEquals(reason, rejected.reason());
        assertEquals(path, rejected.requested());
    }

    @Test
    void acceptsReadableDirectoryAsCanonicalRoot() throws IOException {
        Path dir = Files.createDirectory(temp.resolve("ws"));

        assertEquals(dir.toRealPath(), accepted(dir).path());
    }

    @Test
    void equivalentSpellingsYieldEqualRoots() throws IOException {
        Path dir = Files.createDirectory(temp.resolve("ws"));
        Path sub = Files.createDirectory(dir.resolve("sub"));

        WorkspaceRoot expected = accepted(dir);
        WorkspaceRoot viaParent = accepted(sub.resolve(".."));
        WorkspaceRoot viaDot = accepted(dir.resolve("."));

        assertEquals(expected, viaParent);
        assertEquals(expected, viaDot);
        assertEquals(expected.hashCode(), viaParent.hashCode());
    }

    @Test
    void symlinkToDirectoryResolvesToTarget() throws IOException {
        Path dir = Files.createDirectory(temp.resolve("ws"));
        Path link = temp.resolve("link");
        try {
            Files.createSymbolicLink(link, dir);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }

        assertEquals(accepted(dir), accepted(link));
    }

    @Test
    void rejectsMissingPath() throws IOException {
        assertRejected(temp.resolve("missing"), Reason.NOT_FOUND);
    }

    @Test
    void rejectsDanglingSymlink() throws IOException {
        Path link = temp.resolve("dangling");
        try {
            Files.createSymbolicLink(link, temp.resolve("gone"));
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }

        assertRejected(link, Reason.NOT_FOUND);
    }

    @Test
    void rejectsRegularFile() throws IOException {
        Path file = Files.createFile(temp.resolve("file.txt"));

        assertRejected(file, Reason.NOT_A_DIRECTORY);
    }

    @Test
    void rejectsPathBelowRegularFile() throws IOException {
        Path file = Files.createFile(temp.resolve("file.txt"));

        assertRejected(file.resolve("child"), Reason.NOT_A_DIRECTORY);
    }

    @Test
    void rejectsUnreadableDirectory() throws IOException {
        Path dir = Files.createDirectory(temp.resolve("locked"));
        boolean posix = temp.getFileSystem().supportedFileAttributeViews().contains("posix");
        assumeTrue(posix, "POSIX permissions required");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("---------"));
        try {
            assumeTrue(!Files.isReadable(dir), "permissions not enforced (e.g. running as root)");

            assertRejected(dir, Reason.NOT_READABLE);
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void rejectsNullCandidate() {
        assertThrows(NullPointerException.class, () -> WorkspaceRoot.validate(null));
    }
}
