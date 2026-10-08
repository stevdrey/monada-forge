package io.github.stevdrey.monadaforge.core.workspace;

import java.nio.file.Path;
import java.util.Objects;

/** Outcome of resolving a workspace-relative candidate path against a {@link WorkspaceRoot}. */
public sealed interface WorkspacePathResolution {

    /** Why a candidate path cannot be resolved inside the workspace. */
    enum Reason {
        /**
         * The candidate is empty, not a syntactically valid path on the root's filesystem, or too long
         * for it.
         */
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
     *
     * <p>Only {@link WorkspacePathResolver} creates instances, so holding one implies the path was
     * checked; it is a class rather than a record because a record's constructor would be public.
     */
    final class Resolved implements WorkspacePathResolution {

        private final WorkspaceRoot root;
        private final Path path;
        private final boolean existing;

        Resolved(WorkspaceRoot root, Path path, boolean existing) {
            this.root = Objects.requireNonNull(root, "root");
            this.path = Objects.requireNonNull(path, "path");
            this.existing = existing;
        }

        /** The workspace root the path was resolved against. */
        public WorkspaceRoot root() {
            return root;
        }

        /** The canonical absolute path, inside {@link #root()}. */
        public Path path() {
            return path;
        }

        /** Whether the entry existed at resolution time. */
        public boolean existing() {
            return existing;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Resolved that
                    && root.equals(that.root)
                    && path.equals(that.path)
                    && existing == that.existing;
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, path, existing);
        }

        @Override
        public String toString() {
            return "Resolved[root=" + root + ", path=" + path + ", existing=" + existing + "]";
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
