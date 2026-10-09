package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation.Reason;
import org.junit.jupiter.api.Test;

class WorkspaceMessagesTest {
    @Test
    void everyReasonHasAnActionableMessage() {
        for (var reason : Reason.values()) {
            assertFalse(WorkspaceMessages.describe(reason).isBlank(), reason.name());
        }
    }
}
