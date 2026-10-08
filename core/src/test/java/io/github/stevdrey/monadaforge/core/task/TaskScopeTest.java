package io.github.stevdrey.monadaforge.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.stevdrey.monadaforge.core.task.TaskScope.EntireWorkspace;
import io.github.stevdrey.monadaforge.core.task.TaskScope.Paths;
import io.github.stevdrey.monadaforge.core.task.TaskScope.ScopeEntry;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Reason;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Violation;
import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolution;
import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolver;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskScopeTest {

    @TempDir
    Path temp;

    private Path ws;
    private WorkspaceRoot root;

    @BeforeEach
    void setUp() throws IOException {
        ws = Files.createDirectory(temp.resolve("ws"));
        Files.createDirectories(ws.resolve("src/main"));
        Files.createDirectories(ws.resolve("src/test"));
        Files.createDirectories(ws.resolve("docs"));
        Files.createDirectories(ws.resolve("build"));
        root = accepted(WorkspaceRoot.validate(ws));
    }

    private static WorkspaceRoot accepted(WorkspaceRootValidation validation) {
        return assertInstanceOf(WorkspaceRootValidation.Accepted.class, validation).root();
    }

    private Paths paths(List<String> allowed, List<String> excluded) throws IOException {
        var validation = TaskScope.validatePaths(root, allowed, excluded);
        return assertInstanceOf(
                Paths.class, assertInstanceOf(TaskScopeValidation.Accepted.class, validation).scope());
    }

    private EntireWorkspace entire(List<String> excluded) throws IOException {
        var validation = TaskScope.validateEntireWorkspace(root, excluded);
        return assertInstanceOf(
                EntireWorkspace.class,
                assertInstanceOf(TaskScopeValidation.Accepted.class, validation).scope());
    }

    private static List<Violation> rejected(TaskScopeValidation validation) {
        return assertInstanceOf(TaskScopeValidation.Rejected.class, validation).violations();
    }

    private List<Violation> rejectedPaths(List<String> allowed, List<String> excluded) throws IOException {
        return rejected(TaskScope.validatePaths(root, allowed, excluded));
    }

    private WorkspacePathResolution.Resolved resolve(String candidate) throws IOException {
        return assertInstanceOf(
                WorkspacePathResolution.Resolved.class, WorkspacePathResolver.resolve(root, candidate));
    }

    private static List<String> relatives(List<ScopeEntry> entries) {
        return entries.stream().map(ScopeEntry::relative).toList();
    }

    private static void symlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }
    }

    @Test
    void unsetPermitsNothingAndIsNotTheEntireWorkspace() throws IOException {
        TaskScope unset = TaskScope.unset();

        assertSame(unset, TaskScope.unset());
        assertFalse(unset.permits(resolve("src")));
        assertFalse(unset.permits(resolve("src/main")));
        assertNotEquals(unset, entire(List.of()));
        assertTrue(entire(List.of()).permits(resolve("src")));
    }

    @Test
    void acceptsSinglePathAndKeepsStableNormalizedForm() throws IOException {
        Paths scope = paths(List.of("./src//main/"), null);

        assertEquals(List.of("src/main"), relatives(scope.allowed()));
        assertEquals(ws.toRealPath().resolve("src/main"), scope.allowed().getFirst().path());
        assertTrue(scope.excluded().isEmpty());
        assertEquals(root, scope.root());
    }

    @Test
    void keepsInputOrderAndAcceptsNestedAllowedEntries() throws IOException {
        Paths scope = paths(List.of("src/main", "docs", "src"), List.of());

        assertEquals(List.of("src/main", "docs", "src"), relatives(scope.allowed()));
        assertTrue(scope.permits(resolve("src/test")));
    }

    @Test
    void acceptsExclusionNestedInsideAllowedEntry() throws IOException {
        Paths scope = paths(List.of("src"), List.of("src/test"));

        assertEquals(List.of("src/test"), relatives(scope.excluded()));
        assertTrue(scope.permits(resolve("src/main")));
        assertTrue(scope.permits(resolve("src")));
        assertFalse(scope.permits(resolve("src/test")));
    }

    @Test
    void permitsChecksAllowedAreasAndRoot() throws IOException {
        Paths scope = paths(List.of("src/main", "docs"), null);

        assertTrue(scope.permits(resolve("docs")));
        assertTrue(scope.permits(resolve("src/main")));
        assertFalse(scope.permits(resolve("src")));
        assertFalse(scope.permits(resolve("build")));
        assertFalse(scope.permits(resolve(".")));

        Path otherWs = Files.createDirectory(temp.resolve("other"));
        Files.createDirectory(otherWs.resolve("docs"));
        WorkspaceRoot otherRoot = accepted(WorkspaceRoot.validate(otherWs));
        var foreign = assertInstanceOf(
                WorkspacePathResolution.Resolved.class, WorkspacePathResolver.resolve(otherRoot, "docs"));
        assertFalse(scope.permits(foreign));
        assertFalse(entire(List.of()).permits(foreign));
    }

    @Test
    void entireWorkspaceHonoursExclusions() throws IOException {
        EntireWorkspace scope = entire(List.of("build", "src/test"));

        assertEquals(List.of("build", "src/test"), relatives(scope.excluded()));
        assertTrue(scope.permits(resolve(".")));
        assertTrue(scope.permits(resolve("src/main")));
        assertFalse(scope.permits(resolve("build")));
        assertFalse(scope.permits(resolve("src/test")));
        assertTrue(entire(null).excluded().isEmpty());
    }

    @Test
    void scopeIsImmutableAndDetachedFromInput() throws IOException {
        List<String> allowed = new ArrayList<>(List.of("src"));
        List<String> excluded = new ArrayList<>(List.of("src/test"));
        Paths scope = paths(allowed, excluded);
        allowed.add("docs");
        excluded.clear();

        assertEquals(List.of("src"), relatives(scope.allowed()));
        assertEquals(List.of("src/test"), relatives(scope.excluded()));
        assertThrows(UnsupportedOperationException.class, () -> scope.allowed().clear());
        assertThrows(UnsupportedOperationException.class, () -> scope.excluded().clear());
        assertEquals(paths(List.of("src"), List.of("src/test")), scope);
        assertEquals(paths(List.of("src"), List.of("src/test")).hashCode(), scope.hashCode());
    }

    @Test
    void toStringDoesNotLeakPaths() throws IOException {
        Paths scope = paths(List.of("src"), List.of("src/test"));

        assertFalse(scope.toString().contains("src"));
        assertFalse(entire(List.of("build")).toString().contains("build"));
    }

    @Test
    void resolvesThroughInWorkspaceSymlinkToCanonicalPath() throws IOException {
        symlink(ws.resolve("alias"), ws.resolve("docs"));

        Paths scope = paths(List.of("alias"), null);

        assertEquals(List.of("docs"), relatives(scope.allowed()));
        assertTrue(scope.permits(resolve("docs")));
    }

    @Test
    void emptyOrMissingAllowedListIsRejectedNotReadAsEntireWorkspace() throws IOException {
        assertEquals(
                List.of(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.EMPTY_ALLOWED)),
                rejectedPaths(List.of(), List.of()));
        assertEquals(
                List.of(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.MISSING)),
                rejectedPaths(null, null));
    }

    @ParameterizedTest
    @CsvSource({
        "../outside,TRAVERSAL",
        "src/../../outside,TRAVERSAL",
        "/etc,ABSOLUTE",
        "'',INVALID_PATH",
        "missing/child,PARENT_NOT_FOUND"
    })
    void rejectsInvalidAllowedEntries(String entry, Reason reason) throws IOException {
        assertEquals(List.of(new Violation(Field.ALLOWED, 0, reason)), rejectedPaths(List.of(entry), null));
    }

    @Test
    void rejectsInvalidExcludedEntriesWithFieldAndIndex() throws IOException {
        assertEquals(
                List.of(
                        new Violation(Field.EXCLUDED, 0, Reason.TRAVERSAL),
                        new Violation(Field.EXCLUDED, 1, Reason.ABSOLUTE)),
                rejectedPaths(List.of("src"), List.of("../x", "/tmp")));
        assertEquals(
                List.of(new Violation(Field.EXCLUDED, 0, Reason.TRAVERSAL)),
                rejected(TaskScope.validateEntireWorkspace(root, List.of("../x"))));
    }

    @Test
    void rejectsSymlinkEscapingTheWorkspace() throws IOException {
        Path outside = Files.createDirectory(temp.resolve("outside"));
        symlink(ws.resolve("escape"), outside);

        assertEquals(
                List.of(new Violation(Field.ALLOWED, 1, Reason.ESCAPES_WORKSPACE)),
                rejectedPaths(List.of("src", "escape"), null));
    }

    @Test
    void rejectsNullEntries() throws IOException {
        assertEquals(
                List.of(
                        new Violation(Field.ALLOWED, 1, Reason.MISSING),
                        new Violation(Field.EXCLUDED, 0, Reason.MISSING)),
                rejectedPaths(Arrays.asList("src", null), Arrays.asList((String) null)));
    }

    @Test
    void rejectsWorkspaceRootEntries() throws IOException {
        assertEquals(
                List.of(new Violation(Field.ALLOWED, 0, Reason.WORKSPACE_ROOT)),
                rejectedPaths(List.of("."), null));
        assertEquals(
                List.of(new Violation(Field.EXCLUDED, 0, Reason.TRAVERSAL)),
                rejected(TaskScope.validateEntireWorkspace(root, List.of("src/.."))));
        assertEquals(
                List.of(new Violation(Field.EXCLUDED, 0, Reason.WORKSPACE_ROOT)),
                rejected(TaskScope.validateEntireWorkspace(root, List.of("./"))));
    }

    @Test
    void rejectsDuplicatesAcrossEquivalentSpellingsAndSymlinks() throws IOException {
        symlink(ws.resolve("alias"), ws.resolve("docs"));

        assertEquals(
                List.of(
                        new Violation(Field.ALLOWED, 1, Reason.DUPLICATE),
                        new Violation(Field.ALLOWED, 2, Reason.DUPLICATE)),
                rejectedPaths(List.of("docs", "./docs/", "alias"), null));
        assertEquals(
                List.of(new Violation(Field.EXCLUDED, 1, Reason.DUPLICATE)),
                rejected(TaskScope.validateEntireWorkspace(root, List.of("build", "build/"))));
    }

    @Test
    void rejectsEntryThatIsBothAllowedAndExcluded() throws IOException {
        assertEquals(
                List.of(new Violation(Field.ALLOWED, 0, Reason.ALLOWED_AND_EXCLUDED_CONTRADICT)),
                rejectedPaths(List.of("src"), List.of("src")));
    }

    @Test
    void rejectsAllowedEntryCoveredByAncestorExclusion() throws IOException {
        assertEquals(
                List.of(
                        new Violation(Field.ALLOWED, 0, Reason.ALLOWED_AND_EXCLUDED_CONTRADICT),
                        new Violation(Field.ALLOWED, 1, Reason.ALLOWED_AND_EXCLUDED_CONTRADICT)),
                rejectedPaths(List.of("src/main", "src/test", "docs"), List.of("src")));
    }

    @Test
    void rejectsExclusionOutsideEveryAllowedEntry() throws IOException {
        assertEquals(
                List.of(new Violation(Field.EXCLUDED, 1, Reason.ALLOWED_AND_EXCLUDED_CONTRADICT)),
                rejectedPaths(List.of("src"), List.of("src/test", "build")));
    }

    @Test
    void reportsEveryViolationGroupedByField() throws IOException {
        assertEquals(
                List.of(
                        new Violation(Field.ALLOWED, 0, Reason.TRAVERSAL),
                        new Violation(Field.ALLOWED, 2, Reason.DUPLICATE),
                        new Violation(Field.EXCLUDED, 0, Reason.ABSOLUTE)),
                rejectedPaths(List.of("../x", "src", "src"), List.of("/abs")));
    }

    @Test
    void boundsInspectedEntries() throws IOException {
        List<String> tooMany = java.util.Collections.nCopies(TaskScope.MAX_ENTRIES + 1, "docs");

        List<Violation> allowed = rejectedPaths(tooMany, null);
        assertTrue(allowed.contains(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.TOO_MANY)));
        assertEquals(
                TaskScope.MAX_ENTRIES - 1L,
                allowed.stream().filter(v -> v.reason() == Reason.DUPLICATE).count());

        List<Violation> excluded = rejected(TaskScope.validateEntireWorkspace(root, tooMany));
        assertTrue(excluded.contains(new Violation(Field.EXCLUDED, Violation.NO_INDEX, Reason.TOO_MANY)));
        assertTrue(excluded.stream().noneMatch(v -> v.index() >= TaskScope.MAX_ENTRIES));
    }

    @Test
    void rejectsNullRoot() {
        assertThrows(NullPointerException.class, () -> TaskScope.validatePaths(null, List.of("a"), null));
        assertThrows(NullPointerException.class, () -> TaskScope.validateEntireWorkspace(null, null));
    }
}
