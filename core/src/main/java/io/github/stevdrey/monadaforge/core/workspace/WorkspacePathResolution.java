package io.github.stevdrey.monadaforge.core.workspace;

import java.nio.file.Path;
import java.util.Objects;

/** Outcome of resolving a workspace-relative candidate path against a {@link WorkspaceRoot}. */
public sealed interface WorkspacePathResolution {

    /** Why a candidate path cannot be resolved inside the workspace. */
    enum Reason {
        /** The candidate is empty or not a syntactically valid path on the root's filesystem. */
        INVALID_PATH,
        /** The candidate is absolute or carries a root component. */
        ABSOLUTE,
        /** The candidate contains a {@code ..} segment. */
        TRAVERSAL,
        /** The candidate resolves, through symbolic links, outside the workspace root. */
        ESCAPES_WORKSPACE,
        /** Neither the candidate nor its parent directory exists. */
        PARENT_NOT_FOUND,
        /** Access to a path component was denied while resolving the candidate. */
        NOT_ACCESSIBLE,
        /** Containment could not be established (symlink loop, dangling link, non-directory parent). */
        UNRESOLVABLE
    }

    /**
     * The candidate resolves inside {@code root}.
     *
     * <p>{@code path} is absolute and canonical: the real path of an existing entry, or the real path
     * of its existing parent directory plus the leaf name when {@code existing} is {@code false}. The
     * result proves containment only at resolution time; callers performing I/O later must account
     * for the filesystem changing in between.
     */
    record Resolved(WorkspaceRoot root, Path path, boolean existing) implements WorkspacePathResolution {
        public Resolved {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(path, "path");
        }
    }

    /** The candidate was rejected; {@code requested} is the candidate exactly as supplied. */
    record Rejected(String requested, Reason reason) implements WorkspacePathResolution {
        public Rejected {
            Objects.requireNonNull(requested, "requested");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
