package io.github.stevdrey.monadaforge.core.task;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable snapshot of the task being prepared in the current session.
 *
 * <p>A draft combines a validated {@link WorkspaceRoot}, a validated {@link TaskSpecification} and
 * a validated {@link TaskScope}. It is {@link Status#REVIEWABLE} only when all three were supplied
 * explicitly; nothing is inferred, so an {@linkplain TaskScope#unset() unset} scope is reported as
 * missing rather than read as the whole workspace. A reviewable draft becomes {@link Status#READY}
 * once it is explicitly confirmed; any later change to a component withdraws that confirmation.
 * Confirmation only records that the draft was reviewed: it is not an authorization to act. Task
 * text stays untrusted data and the draft grants no authority beyond the validated scope. Instances
 * come only from {@link TaskDraftService}.
 */
public final class TaskDraft {

    /** Whether the draft holds everything a reviewer needs. */
    public enum Status {
        /** At least one component is still {@linkplain #missing() missing}. */
        INCOMPLETE,
        /** Workspace, specification and scope were all supplied and validated, but not yet confirmed. */
        REVIEWABLE,
        /**
         * A reviewable draft that was explicitly confirmed and has not changed since. It is ready for a
         * future phase; this grants no permission to read, change or run anything.
         */
        READY
    }

    /** A component that has not been supplied yet. */
    public enum Missing {
        WORKSPACE,
        SPECIFICATION,
        SCOPE
    }

    private static final TaskDraft EMPTY = new TaskDraft(null, null, TaskScope.unset(), false);

    private final WorkspaceRoot workspace;
    private final TaskSpecification specification;
    private final TaskScope scope;
    private final boolean confirmed;

    private TaskDraft(WorkspaceRoot workspace, TaskSpecification specification, TaskScope scope, boolean confirmed) {
        this.workspace = workspace;
        this.specification = specification;
        this.scope = scope;
        this.confirmed = confirmed;
    }

    static TaskDraft empty() {
        return EMPTY;
    }

    TaskDraft withWorkspace(WorkspaceRoot newWorkspace) {
        Objects.requireNonNull(newWorkspace, "workspace");
        // A scope is bound to the root it was validated against, so it never survives a different root.
        TaskScope keptScope = newWorkspace.equals(workspace) ? scope : TaskScope.unset();
        return new TaskDraft(newWorkspace, specification, keptScope, false);
    }

    TaskDraft withSpecification(TaskSpecification newSpecification) {
        return new TaskDraft(workspace, Objects.requireNonNull(newSpecification, "specification"), scope, false);
    }

    TaskDraft withoutSpecification() {
        return new TaskDraft(workspace, null, scope, false);
    }

    TaskDraft withScope(TaskScope newScope) {
        return new TaskDraft(workspace, specification, Objects.requireNonNull(newScope, "scope"), false);
    }

    /** A copy marked as confirmed; only a draft with nothing {@linkplain #missing() missing} can be. */
    TaskDraft confirmed() {
        if (!missing().isEmpty()) {
            throw new IllegalStateException("An incomplete draft cannot be confirmed");
        }
        return confirmed ? this : new TaskDraft(workspace, specification, scope, true);
    }

    public Optional<WorkspaceRoot> workspace() {
        return Optional.ofNullable(workspace);
    }

    public Optional<TaskSpecification> specification() {
        return Optional.ofNullable(specification);
    }

    /** The scope; {@link TaskScope#unset()} (which permits nothing) until one is accepted. */
    public TaskScope scope() {
        return scope;
    }

    /** The components still to be supplied, in the order workspace, specification, scope. */
    public List<Missing> missing() {
        List<Missing> missing = new ArrayList<>(3);
        if (workspace == null) {
            missing.add(Missing.WORKSPACE);
        }
        if (specification == null) {
            missing.add(Missing.SPECIFICATION);
        }
        if (scope instanceof TaskScope.Unset) {
            missing.add(Missing.SCOPE);
        }
        return List.copyOf(missing);
    }

    public Status status() {
        if (!missing().isEmpty()) {
            return Status.INCOMPLETE;
        }
        return confirmed ? Status.READY : Status.REVIEWABLE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TaskDraft that
                && Objects.equals(workspace, that.workspace)
                && Objects.equals(specification, that.specification)
                && scope.equals(that.scope)
                && confirmed == that.confirmed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspace, specification, scope, confirmed);
    }

    /** A structural summary only: task text never reaches logs or diagnostics. */
    @Override
    public String toString() {
        return "TaskDraft[status=" + status() + ", missing=" + missing() + "]";
    }
}
