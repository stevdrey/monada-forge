package io.github.stevdrey.monadaforge.core.workspace;

import java.nio.file.Path;
import java.util.Objects;

/** Outcome of validating a candidate path as a {@link WorkspaceRoot}. */
public sealed interface WorkspaceRootValidation {

    /** Why a candidate path cannot be a workspace root. */
    enum Reason {
        NOT_FOUND,
        NOT_A_DIRECTORY,
        NOT_READABLE
    }

    /** The candidate is a valid workspace root. */
    record Accepted(WorkspaceRoot root) implements WorkspaceRootValidation {
        public Accepted {
            Objects.requireNonNull(root, "root");
        }
    }

    /** The candidate was rejected; {@code requested} is the path exactly as supplied. */
    record Rejected(Path requested, Reason reason) implements WorkspaceRootValidation {
        public Rejected {
            Objects.requireNonNull(requested, "requested");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
