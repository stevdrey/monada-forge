package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.task.TaskDraftService;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationDraft;
import io.github.stevdrey.monadaforge.desktop.TaskReviewModel.Blocker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskReviewMessagesTest {

    @TempDir
    Path temp;

    @Test
    void everyBlockerHasActionableText() {
        for (var blocker : Blocker.values()) {
            var text = TaskReviewMessages.describe(blocker);
            assertFalse(text.isBlank(), blocker.name());
            assertTrue(text.endsWith("."), blocker.name());
        }
    }

    @Test
    void theNoticeStatesThatConfirmationAuthorizesNothing() {
        assertTrue(TaskReviewMessages.NOT_AUTHORIZATION.contains("does not authorize"));
        assertTrue(TaskReviewMessages.NOT_AUTHORIZATION.contains("no agent has run"));
        assertTrue(TaskReviewMessages.CONFIRMED.contains("No agent has run"));
    }

    @Test
    void statusFollowsTheReviewState() throws IOException {
        var drafts = new TaskDraftService();
        var blocked = TaskReviewMessages.status(TaskReviewModel.of(drafts.current(), true, true));
        assertEquals(StatusKind.WARNING, blocked.kind());
        assertEquals("Resolve 3 problems before confirming", blocked.text());

        var ws = Files.createDirectory(temp.resolve("ws"));
        Files.createDirectory(ws.resolve("src"));
        drafts.selectWorkspace(ws);
        drafts.updateSpecification(new TaskSpecificationDraft("Secret title", "d", List.of("a"), null, null));
        drafts.selectPaths(List.of("src"), null);
        var one = TaskReviewMessages.status(TaskReviewModel.of(drafts.current(), false, true));
        assertEquals("Resolve 1 problem before confirming", one.text());

        var ready = TaskReviewMessages.status(TaskReviewModel.of(drafts.current(), true, true));
        assertEquals(StatusKind.INFO, ready.kind());
        assertFalse(ready.text().contains("Secret"));

        drafts.confirm();
        var confirmed = TaskReviewMessages.status(TaskReviewModel.of(drafts.current(), true, true));
        assertEquals(StatusKind.SUCCESS, confirmed.kind());
        assertEquals(TaskReviewMessages.CONFIRMED, confirmed.text());

        var withEdits = TaskReviewMessages.status(TaskReviewModel.of(drafts.current(), false, true));
        assertEquals(StatusKind.WARNING, withEdits.kind());
        assertEquals("Resolve 1 problem before confirming", withEdits.text());
        assertTrue(TaskReviewMessages.CONFIRMED_WITH_EDITS.contains("not part of it"));
    }
}
