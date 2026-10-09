package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation.Reason;
import java.util.Objects;

/** Actionable, user-facing explanations for workspace validation failures. */
final class WorkspaceMessages {
    static final String UNEXPECTED_FAILURE =
            "The folder could not be checked because of an unexpected file system error. Try again or choose another folder.";

    private WorkspaceMessages() {}

    static String describe(Reason reason) {
        return switch (Objects.requireNonNull(reason, "reason")) {
            case NOT_FOUND -> "The folder does not exist or is no longer available. Choose another folder.";
            case NOT_A_DIRECTORY -> "The selection is not a folder. Choose a folder.";
            case NOT_READABLE -> "The folder cannot be read. Check its permissions or choose another folder.";
            case SYMLINK_LOOP -> "The folder path contains a circular symbolic link. Choose another folder.";
        };
    }
}
