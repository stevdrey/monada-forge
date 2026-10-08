package io.github.stevdrey.monadaforge.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.task.TaskDraft.Missing;
import io.github.stevdrey.monadaforge.core.task.TaskDraft.Status;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskDraftServiceTest {

    @TempDir
    Path temp;

    private Path ws;
    private TaskDraftService service;

    @BeforeEach
    void setUp() throws IOException {
        ws = Files.createDirectory(temp.resolve("ws"));
        Files.createDirectories(ws.resolve("src"));
        Files.createDirectories(ws.resolve("docs"));
        service = new TaskDraftService();
    }

    private static TaskSpecificationDraft spec(String title) {
        return new TaskSpecificationDraft(title, "Secret description", List.of("Works"), List.of(), List.of());
    }

    private void selectWorkspace(Path path) throws IOException {
        assertInstanceOf(WorkspaceRootValidation.Accepted.class, service.selectWorkspace(path));
    }

    private void completeDraft() throws IOException {
        selectWorkspace(ws);
        assertInstanceOf(TaskSpecificationValidation.Accepted.class, service.updateSpecification(spec("Title")));
        assertInstanceOf(TaskScopeValidation.Accepted.class, service.selectPaths(List.of("src"), null));
    }

    @Test
    void newServiceHoldsAnIncompleteEmptyDraft() {
        TaskDraft draft = service.current();

        assertEquals(Status.INCOMPLETE, draft.status());
        assertEquals(List.of(Missing.WORKSPACE, Missing.SPECIFICATION, Missing.SCOPE), draft.missing());
        assertTrue(draft.workspace().isEmpty());
        assertTrue(draft.specification().isEmpty());
        assertInstanceOf(TaskScope.Unset.class, draft.scope());
    }

    @Test
    void assemblesReviewableDraftFromPathsScope() throws IOException {
        completeDraft();

        TaskDraft draft = service.current();
        assertEquals(Status.REVIEWABLE, draft.status());
        assertTrue(draft.missing().isEmpty());
        assertEquals("Title", draft.specification().orElseThrow().title());
        assertInstanceOf(TaskScope.Paths.class, draft.scope());
        assertEquals(draft.workspace().orElseThrow(), ((TaskScope.Paths) draft.scope()).root());
    }

    @Test
    void assemblesReviewableDraftFromEntireWorkspaceScope() throws IOException {
        selectWorkspace(ws);
        service.updateSpecification(spec("Title"));

        assertInstanceOf(TaskScopeValidation.Accepted.class, service.selectEntireWorkspace(List.of("docs")));

        assertEquals(Status.REVIEWABLE, service.current().status());
        assertInstanceOf(TaskScope.EntireWorkspace.class, service.current().scope());
    }

    @Test
    void missingScopeIsNeverInferred() throws IOException {
        selectWorkspace(ws);
        service.updateSpecification(spec("Title"));

        TaskDraft draft = service.current();
        assertEquals(Status.INCOMPLETE, draft.status());
        assertEquals(List.of(Missing.SCOPE), draft.missing());
        assertInstanceOf(TaskScope.Unset.class, draft.scope());
    }

    @Test
    void rejectedWorkspaceKeepsDraftAndReportsReason() throws IOException {
        completeDraft();
        TaskDraft before = service.current();

        var result = service.selectWorkspace(ws.resolve("missing"));

        var rejected = assertInstanceOf(WorkspaceRootValidation.Rejected.class, result);
        assertEquals(WorkspaceRootValidation.Reason.NOT_FOUND, rejected.reason());
        assertSame(before, service.current());
    }

    @Test
    void rejectedSpecificationKeepsDraftAndReportsViolations() throws IOException {
        completeDraft();
        TaskDraft before = service.current();

        var result = service.updateSpecification(new TaskSpecificationDraft(" ", "D", List.of("A"), null, null));

        var rejected = assertInstanceOf(TaskSpecificationValidation.Rejected.class, result);
        assertEquals(
                List.of(new TaskSpecificationValidation.Violation(
                        TaskSpecificationValidation.Field.TITLE,
                        TaskSpecificationValidation.Violation.NO_INDEX,
                        TaskSpecificationValidation.Reason.BLANK)),
                rejected.violations());
        assertSame(before, service.current());
    }

    @Test
    void rejectedScopeKeepsDraftAndReportsViolations() throws IOException {
        completeDraft();
        TaskDraft before = service.current();

        var result = service.selectPaths(List.of("../outside"), null);

        var rejected = assertInstanceOf(TaskScopeValidation.Rejected.class, result);
        assertFalse(rejected.violations().isEmpty());
        assertSame(before, service.current());
    }

    @Test
    void emptyAllowedListIsRejectedRatherThanReadAsWholeWorkspace() throws IOException {
        selectWorkspace(ws);

        assertInstanceOf(TaskScopeValidation.Rejected.class, service.selectPaths(List.of(), null));

        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void replacingSpecificationKeepsWorkspaceAndScope() throws IOException {
        completeDraft();
        TaskDraft before = service.current();

        service.updateSpecification(spec("Other"));

        TaskDraft after = service.current();
        assertEquals("Other", after.specification().orElseThrow().title());
        assertEquals(before.workspace(), after.workspace());
        assertEquals(before.scope(), after.scope());
        assertEquals(Status.REVIEWABLE, after.status());
    }

    @Test
    void replacingWorkspaceResetsScopeAndKeepsSpecification() throws IOException {
        completeDraft();
        Path other = Files.createDirectory(temp.resolve("other"));

        selectWorkspace(other);

        TaskDraft draft = service.current();
        assertEquals(Status.INCOMPLETE, draft.status());
        assertEquals(List.of(Missing.SCOPE), draft.missing());
        assertInstanceOf(TaskScope.Unset.class, draft.scope());
        assertEquals("Title", draft.specification().orElseThrow().title());
    }

    @Test
    void replacingScopeIsDeterministic() throws IOException {
        completeDraft();

        service.selectPaths(List.of("docs"), null);
        TaskDraft first = service.current();
        service.selectPaths(List.of("docs"), null);

        assertEquals(first, service.current());
        assertEquals(
                List.of("docs"),
                ((TaskScope.Paths) service.current().scope())
                        .allowed().stream().map(TaskScope.ScopeEntry::relative).toList());
    }

    @Test
    void reselectingSameWorkspaceKeepsScope() throws IOException {
        completeDraft();
        TaskDraft before = service.current();

        selectWorkspace(ws);

        assertEquals(before, service.current());
        assertEquals(Status.REVIEWABLE, service.current().status());
    }

    @Test
    void clearReturnsToEmptyDraftAndIsIdempotent() throws IOException {
        completeDraft();

        service.clear();
        TaskDraft cleared = service.current();
        service.clear();

        assertEquals(Status.INCOMPLETE, cleared.status());
        assertEquals(List.of(Missing.WORKSPACE, Missing.SPECIFICATION, Missing.SCOPE), cleared.missing());
        assertEquals(cleared, service.current());
    }

    @Test
    void snapshotsAreUnaffectedByLaterChanges() throws IOException {
        completeDraft();
        TaskDraft snapshot = service.current();

        service.clear();

        assertEquals(Status.REVIEWABLE, snapshot.status());
        assertEquals("Title", snapshot.specification().orElseThrow().title());
    }

    @Test
    void scopeRequiresSelectedWorkspace() {
        assertThrows(IllegalStateException.class, () -> service.selectPaths(List.of("src"), null));
        assertThrows(IllegalStateException.class, () -> service.selectEntireWorkspace(null));
        assertEquals(List.of(Missing.WORKSPACE, Missing.SPECIFICATION, Missing.SCOPE), service.current().missing());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> service.selectWorkspace(null));
        assertThrows(NullPointerException.class, () -> service.updateSpecification(null));
    }

    @Test
    void draftToStringDoesNotExposeTaskText() throws IOException {
        completeDraft();

        String text = service.current().toString();

        assertFalse(text.contains("Secret description"));
        assertFalse(text.contains("Title"));
    }
}
