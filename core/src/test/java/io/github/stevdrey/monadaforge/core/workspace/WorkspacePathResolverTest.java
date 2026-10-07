package io.github.stevdrey.monadaforge.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolution.Reason;
import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolution.Rejected;
import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolution.Resolved;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspacePathResolverTest {

    @TempDir
    Path temp;

    private Path ws;
    private Path outside;
    private WorkspaceRoot root;

    @BeforeEach
    void setUp() throws IOException {
        ws = Files.createDirectory(temp.resolve("ws"));
        outside = Files.createDirectory(temp.resolve("outside"));
        Files.createFile(outside.resolve("secret.txt"));
        root = assertInstanceOf(WorkspaceRootValidation.Accepted.class, WorkspaceRoot.validate(ws)).root();
    }

    private Resolved resolved(String candidate) throws IOException {
        Resolved resolved = assertInstanceOf(Resolved.class, WorkspacePathResolver.resolve(root, candidate));
        assertEquals(root, resolved.root());
        assertTrue(resolved.path().startsWith(root.path()));
        return resolved;
    }

    private void assertRejected(String candidate, Reason reason) throws IOException {
        Rejected rejected = assertInstanceOf(Rejected.class, WorkspacePathResolver.resolve(root, candidate));
        assertEquals(reason, rejected.reason());
        assertEquals(candidate, rejected.requested());
    }

    private static void symlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }
    }

    @Test
    void resolvesExistingNestedPath() throws IOException {
        Path file = Files.createFile(Files.createDirectories(ws.resolve("a/b")).resolve("file.txt"));

        Resolved resolved = resolved("a/b/file.txt");

        assertEquals(file.toRealPath(), resolved.path());
        assertTrue(resolved.existing());
    }

    @Test
    void resolvesMissingLeafUnderExistingParent() throws IOException {
        Path dir = Files.createDirectory(ws.resolve("a"));

        Resolved resolved = resolved("a/new.txt");

        assertEquals(dir.toRealPath().resolve("new.txt"), resolved.path());
        assertFalse(resolved.existing());
        assertFalse(Files.exists(resolved.path()));
    }

    @Test
    void resolvesDotSegments() throws IOException {
        Path dir = Files.createDirectory(ws.resolve("a"));

        assertEquals(root.path(), resolved(".").path());
        assertEquals(dir.toRealPath(), resolved("./a/.").path());
    }

    @Test
    void rejectsMissingParent() throws IOException {
        assertRejected("missing/new.txt", Reason.PARENT_NOT_FOUND);
    }

    @Test
    void rejectsAbsolutePaths() throws IOException {
        assertRejected(outside.toAbsolutePath().toString(), Reason.ABSOLUTE);
        assertRejected(ws.resolve("inside").toAbsolutePath().toString(), Reason.ABSOLUTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../outside", "../outside/secret.txt", "a/../../outside", "a/../b", "a/.."})
    void rejectsDotDotTraversal(String candidate) throws IOException {
        Files.createDirectory(ws.resolve("a"));
        Files.createDirectory(ws.resolve("b"));

        assertRejected(candidate, Reason.TRAVERSAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a\u0000b"})
    void rejectsInvalidSyntax(String candidate) throws IOException {
        assertRejected(candidate, Reason.INVALID_PATH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"link", "link/secret.txt", "link/new.txt"})
    void rejectsSymlinkEscape(String candidate) throws IOException {
        symlink(ws.resolve("link"), outside);

        assertRejected(candidate, Reason.ESCAPES_WORKSPACE);
    }

    @Test
    void rejectsSymlinkedFileOutsideWorkspace() throws IOException {
        symlink(ws.resolve("secret.txt"), outside.resolve("secret.txt"));

        assertRejected("secret.txt", Reason.ESCAPES_WORKSPACE);
    }

    @Test
    void resolvesSymlinkStayingInsideWorkspace() throws IOException {
        Path dir = Files.createDirectory(ws.resolve("a"));
        Files.createFile(dir.resolve("file.txt"));
        symlink(ws.resolve("alias"), dir);

        Resolved existing = resolved("alias/file.txt");
        Resolved missing = resolved("alias/new.txt");

        assertEquals(dir.toRealPath().resolve("file.txt"), existing.path());
        assertEquals(dir.toRealPath().resolve("new.txt"), missing.path());
    }

    @Test
    void rejectsDanglingSymlinkLeaf() throws IOException {
        symlink(ws.resolve("dangling"), outside.resolve("not-yet-created"));

        assertRejected("dangling", Reason.UNRESOLVABLE);
    }

    @Test
    void rejectsSymlinkLoop() throws IOException {
        symlink(ws.resolve("x"), ws.resolve("y"));
        symlink(ws.resolve("y"), ws.resolve("x"));

        assertRejected("x", Reason.UNRESOLVABLE);
        assertRejected("x/child", Reason.UNRESOLVABLE);
    }

    @Test
    void rejectsPathBelowRegularFile() throws IOException {
        Files.createFile(ws.resolve("file.txt"));

        assertRejected("file.txt/child", Reason.UNRESOLVABLE);
    }

    @Test
    void rejectsInaccessibleDirectory() throws IOException {
        Path dir = Files.createDirectory(ws.resolve("locked"));
        boolean posix = temp.getFileSystem().supportedFileAttributeViews().contains("posix");
        assumeTrue(posix, "POSIX permissions required");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("---------"));
        try {
            assumeTrue(!Files.isReadable(dir), "permissions not enforced (e.g. running as root)");

            assertRejected("locked/file.txt", Reason.NOT_ACCESSIBLE);
        } finally {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void rejectsWhenRootWasReplacedBySymlinkOutside() throws IOException {
        Files.delete(ws);
        symlink(ws, outside);

        assertRejected("secret.txt", Reason.ESCAPES_WORKSPACE);
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> WorkspacePathResolver.resolve(null, "a"));
        assertThrows(NullPointerException.class, () -> WorkspacePathResolver.resolve(root, null));
    }
}
