package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import io.github.stevdrey.monadaforge.desktop.WorkspaceSelection.State;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacePresentationTest {
    @TempDir
    Path temp;

    @Test
    void requiredAsksForAWorkspaceAndBlocksContinue() {
        var view = WorkspacePresentation.of(new State.Required());
        assertFalse(view.continueEnabled());
        assertNull(view.pathText());
        assertNull(view.errorText());
        assertEquals(StatusKind.INFO, view.statusKind());
        assertEquals("Select a workspace to begin", view.statusText());
    }

    @Test
    void validatingBlocksContinueAndShowsNoError() {
        var view = WorkspacePresentation.of(new State.Validating(temp));
        assertFalse(view.continueEnabled());
        assertNull(view.errorText());
        assertEquals(StatusKind.INFO, view.statusKind());
    }

    @Test
    void selectedShowsThePathEnablesContinueAndReportsSuccess() throws IOException {
        var accepted = (WorkspaceRootValidation.Accepted) WorkspaceRoot.validate(temp);
        var view = WorkspacePresentation.of(new State.Selected(accepted.root()));
        assertTrue(view.continueEnabled());
        assertEquals(accepted.root().path().toString(), view.pathText());
        assertNull(view.errorText());
        assertEquals(StatusKind.SUCCESS, view.statusKind());
        assertEquals("Workspace: " + accepted.root().path(), view.statusText());
    }

    @Test
    void invalidShowsTheErrorBlocksContinueAndReportsError() {
        var view = WorkspacePresentation.of(new State.Invalid(temp, "Not a folder"));
        assertFalse(view.continueEnabled());
        assertEquals("Not a folder", view.errorText());
        assertEquals(temp.toString(), view.pathText());
        assertEquals(StatusKind.ERROR, view.statusKind());
        assertEquals("Not a folder", view.statusText());
    }
}
