package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.task.TaskDraftService;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationDraft;
import io.github.stevdrey.monadaforge.desktop.TaskReviewModel.Blocker;
import io.github.stevdrey.monadaforge.desktop.TaskReviewModel.ScopeMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskReviewModelTest {

    @TempDir
    Path temp;

    private Path ws;
    private TaskDraftService drafts;

    @BeforeEach
    void setUp() throws IOException {
        ws = Files.createDirectory(temp.resolve("ws"));
        Files.createDirectories(ws.resolve("src/main"));
        Files.createDirectories(ws.resolve("docs"));
        drafts = new TaskDraftService();
    }

    private void specification() {
        drafts.updateSpecification(new TaskSpecificationDraft(
                "Add login", "Describe it", List.of("First", "Second"), List.of("No new deps"), List.of("No UI")));
    }

    private TaskReviewModel model(boolean specInSync, boolean scopeInSync) {
        return TaskReviewModel.of(drafts.current(), specInSync, scopeInSync);
    }

    @Test
    void showsEveryPartOfACompleteDraftWithSpecificPaths() throws IOException {
        drafts.selectWorkspace(ws);
        specification();
        drafts.selectPaths(List.of("src", "docs"), List.of("src/main"));

        var model = model(true, true);

        assertEquals(drafts.current().workspace().orElseThrow().path(), model.workspace().orElseThrow());
        var spec = model.specification().orElseThrow();
        assertEquals("Add login", spec.title());
        assertEquals(List.of("First", "Second"), spec.acceptanceCriteria());
        assertEquals(List.of("No new deps"), spec.constraints());
        assertEquals(List.of("No UI"), spec.nonGoals());
        assertEquals(ScopeMode.SPECIFIC_PATHS, model.scopeMode());
        assertEquals(List.of("src", "docs"), model.allowed());
        assertEquals(List.of("src/main"), model.excluded());
        assertFalse(model.wholeWorkspace());
        assertTrue(model.blockers().isEmpty());
        assertTrue(model.canConfirm());
        assertFalse(model.confirmed());
    }

    @Test
    void flagsTheWholeWorkspaceScope() throws IOException {
        drafts.selectWorkspace(ws);
        specification();
        drafts.selectEntireWorkspace(List.of("docs"));

        var model = model(true, true);

        assertEquals(ScopeMode.ENTIRE_WORKSPACE, model.scopeMode());
        assertTrue(model.wholeWorkspace());
        assertTrue(model.allowed().isEmpty());
        assertEquals(List.of("docs"), model.excluded());
        assertTrue(model.canConfirm());
    }

    @Test
    void anEmptyDraftReportsEveryMissingPartAndCannotBeConfirmed() {
        var model = model(true, true);

        assertEquals(
                List.of(Blocker.WORKSPACE_MISSING, Blocker.SPECIFICATION_MISSING, Blocker.SCOPE_MISSING),
                model.blockers());
        assertEquals(ScopeMode.UNSET, model.scopeMode());
        assertFalse(model.canConfirm());
    }

    @Test
    void aMissingScopeBlocksConfirmation() throws IOException {
        drafts.selectWorkspace(ws);
        specification();

        var model = model(true, true);

        assertEquals(List.of(Blocker.SCOPE_MISSING), model.blockers());
        assertFalse(model.canConfirm());
    }

    @Test
    void formsThatNoLongerMatchTheDraftBlockConfirmation() throws IOException {
        drafts.selectWorkspace(ws);
        specification();
        drafts.selectPaths(List.of("src"), null);

        assertEquals(List.of(Blocker.SPECIFICATION_NOT_VALIDATED), model(false, true).blockers());
        assertEquals(List.of(Blocker.SCOPE_NOT_VALIDATED), model(true, false).blockers());
        assertEquals(
                List.of(Blocker.SPECIFICATION_NOT_VALIDATED, Blocker.SCOPE_NOT_VALIDATED),
                model(false, false).blockers());
        assertFalse(model(false, false).canConfirm());
    }

    @Test
    void aChangedWorkspaceDropsTheScopeAndBlocksConfirmation() throws IOException {
        drafts.selectWorkspace(ws);
        specification();
        drafts.selectPaths(List.of("src"), null);
        drafts.confirm();
        drafts.selectWorkspace(Files.createDirectory(temp.resolve("other")));

        var model = model(true, true);

        assertEquals(List.of(Blocker.SCOPE_MISSING), model.blockers());
        assertFalse(model.confirmed());
        assertFalse(model.canConfirm());
    }

    @Test
    void aConfirmedDraftIsShownAsConfirmedAndCannotBeConfirmedAgain() throws IOException {
        drafts.selectWorkspace(ws);
        specification();
        drafts.selectPaths(List.of("src"), null);
        drafts.confirm();

        var model = model(true, true);

        assertTrue(model.confirmed());
        assertFalse(model.canConfirm());
    }

    @Test
    void anEditedFormKeepsCoresConfirmationVisibleButBlocksConfirming() throws IOException {
        drafts.selectWorkspace(ws);
        specification();
        drafts.selectPaths(List.of("src"), null);
        drafts.confirm();

        var model = model(false, true);

        assertTrue(model.confirmed());
        assertEquals(List.of(Blocker.SPECIFICATION_NOT_VALIDATED), model.blockers());
        assertFalse(model.canConfirm());
    }
}
