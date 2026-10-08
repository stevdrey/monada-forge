package io.github.stevdrey.monadaforge.core.task;

import io.github.stevdrey.monadaforge.core.task.TaskScope.ScopeEntry;
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
import java.util.Set;

/** Builds {@link TaskScope} instances; the only code allowed to construct validated entries. */
final class TaskScopeValidator {

    /** A validated entry with its position in the supplied list, kept only for reporting. */
    private record Indexed(int index, ScopeEntry entry) {}

    private TaskScopeValidator() {}

    static TaskScopeValidation entireWorkspace(WorkspaceRoot root, List<String> excluded) throws IOException {
        List<Violation> violations = new ArrayList<>();
        List<Indexed> excludedEntries = resolve(root, excluded, Field.EXCLUDED, violations);
        if (!violations.isEmpty()) {
            return new TaskScopeValidation.Rejected(violations);
        }
        return new TaskScopeValidation.Accepted(new TaskScope.EntireWorkspace(root, entries(excludedEntries)));
    }

    static TaskScopeValidation paths(WorkspaceRoot root, List<String> allowed, List<String> excluded)
            throws IOException {
        List<Violation> violations = new ArrayList<>();
        List<Indexed> allowedEntries = resolve(root, allowed, Field.ALLOWED, violations);
        if (allowed == null) {
            violations.add(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.MISSING));
        } else if (allowedEntries.isEmpty() && violations.isEmpty()) {
            violations.add(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.EMPTY_ALLOWED));
        }
        // Against an invalid allowed list every exclusion would look unrelated, so skip the cross-check.
        boolean allowedValid = violations.isEmpty();
        List<Indexed> excludedEntries = resolve(root, excluded, Field.EXCLUDED, violations);
        if (allowedValid) {
            contradictions(allowedEntries, excludedEntries, violations);
        }
        if (!violations.isEmpty()) {
            violations.sort(Comparator.comparing(Violation::field));
            return new TaskScopeValidation.Rejected(violations);
        }
        return new TaskScopeValidation.Accepted(
                new TaskScope.Paths(root, entries(allowedEntries), entries(excludedEntries)));
    }

    private static List<ScopeEntry> entries(List<Indexed> indexed) {
        return indexed.stream().map(Indexed::entry).toList();
    }

    private static List<Indexed> resolve(
            WorkspaceRoot root, List<String> raw, Field field, List<Violation> violations) throws IOException {
        // One bounded snapshot of the untrusted list: its size and elements are read exactly once.
        List<String> snapshot = raw == null ? List.of() : raw.stream().limit(TaskScope.MAX_ENTRIES + 1L).toList();
        if (snapshot.size() > TaskScope.MAX_ENTRIES) {
            violations.add(new Violation(field, Violation.NO_INDEX, Reason.TOO_MANY));
        }
        int inspected = Math.min(snapshot.size(), TaskScope.MAX_ENTRIES);
        List<Indexed> entries = new ArrayList<>(inspected);
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
                    if (!resolved.existing()) {
                        violations.add(new Violation(field, i, Reason.NOT_FOUND));
                    } else if (resolved.path().equals(root.path())) {
                        violations.add(new Violation(field, i, Reason.WORKSPACE_ROOT));
                    } else if (!seen.add(resolved.path())) {
                        violations.add(new Violation(field, i, Reason.DUPLICATE));
                    } else {
                        entries.add(new Indexed(i, new ScopeEntry(relative(root, resolved.path()), resolved.path())));
                    }
                }
            }
        }
        return List.copyOf(entries);
    }

    private static void contradictions(List<Indexed> allowed, List<Indexed> excluded, List<Violation> violations) {
        for (Indexed entry : allowed) {
            if (excluded.stream().anyMatch(exclusion -> entry.entry().path().startsWith(exclusion.entry().path()))) {
                violations.add(contradiction(Field.ALLOWED, entry.index()));
            }
        }
        for (Indexed exclusion : excluded) {
            Path path = exclusion.entry().path();
            boolean related = allowed.stream()
                    .map(entry -> entry.entry().path())
                    .anyMatch(other -> path.startsWith(other) || other.startsWith(path));
            if (!related) {
                violations.add(contradiction(Field.EXCLUDED, exclusion.index()));
            }
        }
    }

    private static Violation contradiction(Field field, int index) {
        return new Violation(field, index, Reason.ALLOWED_AND_EXCLUDED_CONTRADICT);
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
}
