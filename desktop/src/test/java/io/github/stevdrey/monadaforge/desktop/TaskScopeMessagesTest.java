package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Reason;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskScopeMessagesTest {
    @Test
    void everyReasonHasAnActionableMessageForEntriesAndLists() {
        for (var field : Field.values()) {
            for (var reason : Reason.values()) {
                assertFalse(TaskScopeMessages.describe(new Violation(field, 0, reason)).isBlank());
                assertFalse(TaskScopeMessages.describe(new Violation(field, Violation.NO_INDEX, reason)).isBlank());
            }
        }
    }

    @Test
    void entriesAreIdentifiedByPositionNotByContent() {
        assertTrue(TaskScopeMessages.describe(new Violation(Field.ALLOWED, 1, Reason.TRAVERSAL))
                .startsWith("Allowed path 2 "));
    }

    @Test
    void noViolationsMeansNoText() {
        assertNull(TaskScopeMessages.describeAll(List.of()));
    }

    @Test
    void statusReflectsEachResult() {
        assertEquals(StatusKind.INFO, TaskScopeMessages.status(new TaskScopeForm.Result.NotValidated(), false).kind());
        assertEquals(StatusKind.WARNING, TaskScopeMessages.status(new TaskScopeForm.Result.NotValidated(), true).kind());
        assertEquals(StatusKind.ERROR, TaskScopeMessages.status(new TaskScopeForm.Result.ModeRequired(), false).kind());
        assertEquals(StatusKind.ERROR, TaskScopeMessages.status(new TaskScopeForm.Result.Failed(), false).kind());
        assertEquals(
                "Fix 2 problems in the scope",
                TaskScopeMessages.status(
                                new TaskScopeForm.Result.Invalid(List.of(
                                        new Violation(Field.ALLOWED, 0, Reason.TRAVERSAL),
                                        new Violation(Field.EXCLUDED, 0, Reason.NOT_FOUND))),
                                false)
                        .text());
    }
}
