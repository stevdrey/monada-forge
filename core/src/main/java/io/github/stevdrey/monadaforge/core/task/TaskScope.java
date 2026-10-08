package io.github.stevdrey.monadaforge.core.task;

import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolution;
import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolver;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, validated filesystem scope for a task: the workspace areas later stages may touch.
 *
 * <p>A scope is authorization data. It has exactly three shapes, so that "nothing selected yet" can
 * never be mistaken for "everything allowed":
 *
 * <ul>
 *   <li>{@link Unset}: no scope has been supplied; it permits nothing.
 *   <li>{@link EntireWorkspace}: the whole workspace was selected on purpose, minus any exclusions.
 *   <li>{@link Paths}: a non-empty list of allowed areas, minus any exclusions.
 * </ul>
 *
 * <p>Every entry must exist, passes {@link WorkspacePathResolver} and is authorized by its canonical
 * path, so a symbolic link cannot widen the scope. Nested allowed entries are accepted and kept as supplied; an
 * exclusion nested inside an allowed entry is a valid carve-out and always wins over inclusion.
 * Instances come only from the {@code validate*} factories and {@link #unset()}. Nothing is read,
 * created or modified, and a scope can only be replaced, never widened in place.
 */
public sealed interface TaskScope {

    /** Maximum number of entries in each list. */
    int MAX_ENTRIES = 50;

    /** The scope that has not been supplied yet. */
    static TaskScope unset() {
        return Unset.INSTANCE;
    }

    /**
     * Validates an explicit whole-workspace scope with optional {@code excluded} entries; a
     * {@code null} list means no exclusions.
     *
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    static TaskScopeValidation validateEntireWorkspace(WorkspaceRoot root, List<String> excluded)
            throws IOException {
        return TaskScopeValidator.entireWorkspace(Objects.requireNonNull(root, "root"), excluded);
    }

    /**
     * Validates a scope limited to {@code allowed} areas, minus optional {@code excluded} entries.
     * Entries must name existing files or directories. A {@code null} {@code excluded} list means no
     * exclusions; a {@code null} or empty {@code allowed} list is rejected rather than read as the
     * whole workspace. Every violation is reported, grouped by field; the allowed/excluded
     * contradiction check runs only once the allowed list is otherwise valid. Only the first {@link
     * #MAX_ENTRIES} entries of each list are inspected.
     *
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    static TaskScopeValidation validatePaths(WorkspaceRoot root, List<String> allowed, List<String> excluded)
            throws IOException {
        return TaskScopeValidator.paths(Objects.requireNonNull(root, "root"), allowed, excluded);
    }

    /**
     * Whether {@code resolution} lies inside this scope: inside the workspace root, covered by an
     * allowed entry (or the entire workspace) and not covered by any exclusion. A pure path-prefix
     * check; the filesystem is not consulted.
     */
    boolean permits(WorkspacePathResolution.Resolved resolution);

    private static boolean covered(List<ScopeEntry> entries, Path path) {
        return entries.stream().anyMatch(entry -> path.startsWith(entry.path()));
    }

    /**
     * One validated scope entry: its normalized workspace-relative form and the canonical path it
     * stands for. Only this package creates instances.
     */
    final class ScopeEntry {
        private final String relative;
        private final Path path;

        ScopeEntry(String relative, Path path) {
            this.relative = relative;
            this.path = path;
        }

        /** Normalized workspace-relative path, {@code /}-separated; never empty. */
        public String relative() {
            return relative;
        }

        /** Canonical absolute path, strictly inside the workspace root. */
        public Path path() {
            return path;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ScopeEntry that && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public String toString() {
            return "ScopeEntry[" + relative + "]";
        }
    }

    /** No scope has been supplied; permits nothing. */
    final class Unset implements TaskScope {
        static final Unset INSTANCE = new Unset();

        Unset() {}

        @Override
        public boolean permits(WorkspacePathResolution.Resolved resolution) {
            Objects.requireNonNull(resolution, "resolution");
            return false;
        }

        @Override
        public String toString() {
            return "TaskScope.Unset";
        }
    }

    /** The whole workspace was selected explicitly, minus {@link #excluded()}. */
    final class EntireWorkspace implements TaskScope {
        private final WorkspaceRoot root;
        private final List<ScopeEntry> excluded;

        EntireWorkspace(WorkspaceRoot root, List<ScopeEntry> excluded) {
            this.root = root;
            this.excluded = excluded;
        }

        public WorkspaceRoot root() {
            return root;
        }

        /** Exclusions in input order; possibly empty. */
        public List<ScopeEntry> excluded() {
            return excluded;
        }

        @Override
        public boolean permits(WorkspacePathResolution.Resolved resolution) {
            return root.equals(resolution.root()) && !covered(excluded, resolution.path());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EntireWorkspace that && root.equals(that.root) && excluded.equals(that.excluded);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, excluded);
        }

        @Override
        public String toString() {
            return "TaskScope.EntireWorkspace[excluded=" + excluded.size() + "]";
        }
    }

    /** Only the {@link #allowed()} areas were selected, minus {@link #excluded()}. */
    final class Paths implements TaskScope {
        private final WorkspaceRoot root;
        private final List<ScopeEntry> allowed;
        private final List<ScopeEntry> excluded;

        Paths(WorkspaceRoot root, List<ScopeEntry> allowed, List<ScopeEntry> excluded) {
            this.root = root;
            this.allowed = allowed;
            this.excluded = excluded;
        }

        public WorkspaceRoot root() {
            return root;
        }

        /** Allowed areas in input order; never empty. */
        public List<ScopeEntry> allowed() {
            return allowed;
        }

        /** Exclusions in input order; possibly empty. */
        public List<ScopeEntry> excluded() {
            return excluded;
        }

        @Override
        public boolean permits(WorkspacePathResolution.Resolved resolution) {
            return root.equals(resolution.root())
                    && covered(allowed, resolution.path())
                    && !covered(excluded, resolution.path());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Paths that
                    && root.equals(that.root)
                    && allowed.equals(that.allowed)
                    && excluded.equals(that.excluded);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, allowed, excluded);
        }

        @Override
        public String toString() {
            return "TaskScope.Paths[allowed=" + allowed.size() + ", excluded=" + excluded.size() + "]";
        }
    }
}
