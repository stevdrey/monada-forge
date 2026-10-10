package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskDraft;
import io.github.stevdrey.monadaforge.core.task.TaskScope;
import io.github.stevdrey.monadaforge.core.task.TaskSpecification;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the review screen shows, derived from one {@link TaskDraft} snapshot, plus the reasons
 * confirmation is not available yet. It holds no toolkit types so the gate can be unit-tested.
 *
 * <p>The draft is core's validated state. The two {@code InSync} flags say whether the editing forms
 * still match it: a form with unvalidated edits would otherwise let the user confirm a task that
 * differs from what is on screen elsewhere.
 */
final class TaskReviewModel {

    /** Why the draft cannot be confirmed yet. */
    enum Blocker {
        WORKSPACE_MISSING,
        SPECIFICATION_MISSING,
        SPECIFICATION_NOT_VALIDATED,
        SCOPE_MISSING,
        SCOPE_NOT_VALIDATED
    }

    /** How the scope was defined; {@link #UNSET} means nothing is permitted. */
    enum ScopeMode {
        UNSET,
        SPECIFIC_PATHS,
        ENTIRE_WORKSPACE
    }

    private final Optional<Path> workspace;
    private final Optional<TaskSpecification> specification;
    private final ScopeMode scopeMode;
    private final List<String> allowed;
    private final List<String> excluded;
    private final List<Blocker> blockers;
    private final boolean confirmed;

    private TaskReviewModel(
            TaskDraft draft, boolean specificationInSync, boolean scopeInSync) {
        this.workspace = draft.workspace().map(root -> root.path());
        this.specification = draft.specification();
        TaskScope scope = draft.scope();
        this.scopeMode = switch (scope) {
            case TaskScope.Paths _ -> ScopeMode.SPECIFIC_PATHS;
            case TaskScope.EntireWorkspace _ -> ScopeMode.ENTIRE_WORKSPACE;
            case TaskScope.Unset _ -> ScopeMode.UNSET;
        };
        this.allowed = scope instanceof TaskScope.Paths paths
                ? paths.allowed().stream().map(TaskScope.ScopeEntry::relative).toList()
                : List.of();
        this.excluded = switch (scope) {
            case TaskScope.Paths paths -> paths.excluded().stream().map(TaskScope.ScopeEntry::relative).toList();
            case TaskScope.EntireWorkspace entire ->
                    entire.excluded().stream().map(TaskScope.ScopeEntry::relative).toList();
            case TaskScope.Unset _ -> List.of();
        };
        var found = new ArrayList<Blocker>();
        if (workspace.isEmpty()) {
            found.add(Blocker.WORKSPACE_MISSING);
        }
        if (specification.isEmpty()) {
            found.add(Blocker.SPECIFICATION_MISSING);
        } else if (!specificationInSync) {
            found.add(Blocker.SPECIFICATION_NOT_VALIDATED);
        }
        if (scopeMode == ScopeMode.UNSET) {
            found.add(Blocker.SCOPE_MISSING);
        } else if (!scopeInSync) {
            found.add(Blocker.SCOPE_NOT_VALIDATED);
        }
        this.blockers = List.copyOf(found);
        // Core is the source of truth; unvalidated form edits show up as blockers, not as a withdrawn confirmation.
        this.confirmed = draft.status() == TaskDraft.Status.READY;
    }

    static TaskReviewModel of(TaskDraft draft, boolean specificationInSync, boolean scopeInSync) {
        return new TaskReviewModel(Objects.requireNonNull(draft, "draft"), specificationInSync, scopeInSync);
    }

    Optional<Path> workspace() {
        return workspace;
    }

    Optional<TaskSpecification> specification() {
        return specification;
    }

    ScopeMode scopeMode() {
        return scopeMode;
    }

    /** Allowed paths, relative to the workspace; empty unless the scope lists specific paths. */
    List<String> allowed() {
        return allowed;
    }

    /** Excluded paths, relative to the workspace, in the order validated by core. */
    List<String> excluded() {
        return excluded;
    }

    /** Whether the whole workspace is in scope, which deserves an explicit warning. */
    boolean wholeWorkspace() {
        return scopeMode == ScopeMode.ENTIRE_WORKSPACE;
    }

    List<Blocker> blockers() {
        return blockers;
    }

    /** Whether core holds this draft as confirmed, regardless of what the forms contain now. */
    boolean confirmed() {
        return confirmed;
    }

    /** Whether confirmation is offered: everything validated and not confirmed already. */
    boolean canConfirm() {
        return blockers.isEmpty() && !confirmed;
    }
}
