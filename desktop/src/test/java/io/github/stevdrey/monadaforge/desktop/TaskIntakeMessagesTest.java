package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Reason;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskIntakeMessagesTest {
    @Test
    void everyFieldAndReasonHasAMessage() {
        for (var field : Field.values()) {
            boolean list = field != Field.TITLE && field != Field.DESCRIPTION;
            for (var reason : Reason.values()) {
                assertFalse(TaskIntakeMessages.describe(new Violation(field, Violation.NO_INDEX, reason)).isBlank());
                if (list) {
                    assertFalse(TaskIntakeMessages.describe(new Violation(field, 0, reason)).isBlank());
                }
            }
        }
    }

    @Test
    void itemMessagesUseOneBasedPositions() {
        assertTrue(TaskIntakeMessages.describe(new Violation(Field.ACCEPTANCE_CRITERIA, 1, Reason.BLANK))
                .startsWith("Acceptance criterion 2"));
    }

    @Test
    void statusReflectsTheResult() {
        assertEquals(StatusKind.INFO, TaskIntakeMessages.status(new TaskIntakeForm.Result.NotValidated()).kind());
        var one = TaskIntakeMessages.status(new TaskIntakeForm.Result.Invalid(
                List.of(new Violation(Field.TITLE, Violation.NO_INDEX, Reason.BLANK))));
        assertEquals(StatusKind.ERROR, one.kind());
        assertTrue(one.text().contains("1 problem "));
    }

    @Test
    void describeAllJoinsMessagesAndIsNullWhenEmpty() {
        assertEquals(null, TaskIntakeMessages.describeAll(List.of()));
        var both = TaskIntakeMessages.describeAll(List.of(
                new Violation(Field.TITLE, Violation.NO_INDEX, Reason.BLANK),
                new Violation(Field.DESCRIPTION, Violation.NO_INDEX, Reason.BLANK)));
        assertTrue(both.startsWith("The title") && both.contains("The description"));
    }
}
