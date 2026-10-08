package io.github.stevdrey.monadaforge.core.task;

import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Reason;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Violation;
import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolution;
import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolver;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
 * <p>Every entry passes {@link WorkspacePathResolver} and is authorized by its canonical path, so a
 * symbolic link cannot widen the scope. Nested allowed entries are accepted and kept as supplied; an
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
        Objects.requireNonNull(root, "root");
        List<Violation> violations = new ArrayList<>();
        List<ScopeEntry> excludedEntries = resolve(root, excluded, Field.EXCLUDED, violations);
        if (!violations.isEmpty()) {
            return new TaskScopeValidation.Rejected(violations);
        }
        return new TaskScopeValidation.Accepted(new EntireWorkspace(root, excludedEntries));
    }

    /**
     * Validates a scope limited to {@code allowed} areas, minus optional {@code excluded} entries. A
     * {@code null} {@code excluded} list means no exclusions; a {@code null} or empty {@code allowed}
     * list is rejected rather than read as the whole workspace. Every violation is reported, grouped
     * by field. Only the first {@link #MAX_ENTRIES} entries of each list are inspected.
     *
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    static TaskScopeValidation validatePaths(WorkspaceRoot root, List<String> allowed, List<String> excluded)
            throws IOException {
        Objects.requireNonNull(root, "root");
        List<Violation> violations = new ArrayList<>();
        List<ScopeEntry> allowedEntries = resolve(root, allowed, Field.ALLOWED, violations);
        if (allowed == null) {
            violations.add(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.MISSING));
        } else if (allowedEntries.isEmpty() && violations.isEmpty()) {
            violations.add(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.EMPTY_ALLOWED));
        }
        List<ScopeEntry> excludedEntries = resolve(root, excluded, Field.EXCLUDED, violations);
        contradictions(allowedEntries, excludedEntries, violations);
        if (!violations.isEmpty()) {
            violations.sort(Comparator.comparing(Violation::field));
            return new TaskScopeValidation.Rejected(violations);
        }
        return new TaskScopeValidation.Accepted(new Paths(root, allowedEntries, excludedEntries));
    }

    /**
     * Whether {@code resolution} lies inside this scope: inside the workspace root, covered by an
     * allowed entry (or the entire workspace) and not covered by any exclusion. A pure path-prefix
     * check; the filesystem is not consulted.
     */
    boolean permits(WorkspacePathResolution.Resolved resolution);

    private static List<ScopeEntry> resolve(
            WorkspaceRoot root, List<String> raw, Field field, List<Violation> violations) throws IOException {
        // One bounded snapshot of the untrusted list: its size and elements are read exactly once.
        List<String> snapshot = raw == null ? List.of() : raw.stream().limit(MAX_ENTRIES + 1L).toList();
        if (snapshot.size() > MAX_ENTRIES) {
            violations.add(new Violation(field, Violation.NO_INDEX, Reason.TOO_MANY));
        }
        int inspected = Math.min(snapshot.size(), MAX_ENTRIES);
        List<ScopeEntry> entries = new ArrayList<>(inspected);
        Set<Path> seen = new HashSet<>();
        for (int i = 0; i < inspected; i++) {
            String candidate = snapshot.get(i);
            if (candidate == null) {
                violations.add(new Violation(field, i, Reason.MISSING));
                continue;
            }
            switch (WorkspacePathResolver.resolve(root, candidate)) {
                case WorkspacePathResolution.Rejected rejected ->
                    violations.add(new Violation(field, i, map(rejected.reason())));
                case WorkspacePathResolution.Resolved resolved -> {
                    if (resolved.path().equals(root.path())) {
                        violations.add(new Violation(field, i, Reason.WORKSPACE_ROOT));
                    } else if (!seen.add(resolved.path())) {
                        violations.add(new Violation(field, i, Reason.DUPLICATE));
                    } else {
                        entries.add(new ScopeEntry(i, relative(root, resolved.path()), resolved.path()));
                    }
                }
            }
        }
        return List.copyOf(entries);
    }

    private static void contradictions(
            List<ScopeEntry> allowed, List<ScopeEntry> excluded, List<Violation> violations) {
        for (ScopeEntry entry : allowed) {
            if (excluded.stream().anyMatch(exclusion -> entry.path().startsWith(exclusion.path()))) {
                violations.add(new Violation(Field.ALLOWED, entry.index(), Reason.ALLOWED_AND_EXCLUDED_CONTRADICT));
            }
        }
        for (ScopeEntry exclusion : excluded) {
            boolean related = allowed.stream()
                    .anyMatch(entry -> exclusion.path().startsWith(entry.path())
                            || entry.path().startsWith(exclusion.path()));
            if (!related) {
                violations.add(new Violation(Field.EXCLUDED, exclusion.index(), Reason.ALLOWED_AND_EXCLUDED_CONTRADICT));
            }
        }
    }

    private static Reason map(WorkspacePathResolution.Reason reason) {
        return switch (reason) {
            case INVALID_PATH -> Reason.INVALID_PATH;
            case ABSOLUTE -> Reason.ABSOLUTE;
            case TRAVERSAL -> Reason.TRAVERSAL;
            case ESCAPES_WORKSPACE -> Reason.ESCAPES_WORKSPACE;
            case PARENT_NOT_FOUND -> Reason.PARENT_NOT_FOUND;
            case NOT_ACCESSIBLE -> Reason.NOT_ACCESSIBLE;
            case UNRESOLVABLE -> Reason.UNRESOLVABLE;
        };
    }

    /** The stable user-visible form: root-relative, {@code /}-separated, never empty. */
    private static String relative(WorkspaceRoot root, Path real) {
        List<String> names = new ArrayList<>();
        for (Path name : root.path().relativize(real)) {
            names.add(name.toString());
        }
        return String.join("/", names);
    }

    private static boolean covered(List<ScopeEntry> entries, Path path) {
        return entries.stream().anyMatch(entry -> path.startsWith(entry.path()));
    }

    /**
     * One validated scope entry: its normalized workspace-relative form and the canonical path it
     * stands for. Only this package creates instances.
     */
    final class ScopeEntry {
        private final int index;
        private final String relative;
        private final Path path;

        private ScopeEntry(int index, String relative, Path path) {
            this.index = index;
            this.relative = relative;
            this.path = path;
        }

        /** Position of the entry in the supplied list, for reporting. */
        int index() {
            return index;
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
        private static final Unset INSTANCE = new Unset();

        private Unset() {}

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

        private EntireWorkspace(WorkspaceRoot root, List<ScopeEntry> excluded) {
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

        private Paths(WorkspaceRoot root, List<ScopeEntry> allowed, List<ScopeEntry> excluded) {
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
