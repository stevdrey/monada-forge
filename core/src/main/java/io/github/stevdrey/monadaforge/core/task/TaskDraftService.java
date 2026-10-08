package io.github.stevdrey.monadaforge.core.task;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Holds the current {@link TaskDraft} in process memory and is the single place that changes it.
 *
 * <p>Each operation validates its input through the owning component contract and returns that
 * contract's validation result unchanged, so callers keep the full violation detail. Only accepted
 * input replaces state; a rejected input leaves the draft exactly as it was. Nothing is persisted,
 * and no file is created or modified. Changes are serialized; reads never wait for them, because a
 * snapshot is immutable and always consistent.
 */
public final class TaskDraftService {

    private volatile TaskDraft draft = TaskDraft.empty();

    /** The current draft; an immutable snapshot unaffected by later changes. */
    public TaskDraft current() {
        return draft;
    }

    /**
     * Validates {@code candidate} and, if accepted, makes it the workspace. The scope is reset to
     * {@linkplain TaskScope#unset() unset} when the root differs from the current one, because it
     * was validated against the previous root; re-selecting the same root keeps it. The
     * specification is always kept.
     *
     * @throws IOException on unexpected I/O failures, as opposed to an invalid candidate
     */
    public synchronized WorkspaceRootValidation selectWorkspace(Path candidate) throws IOException {
        WorkspaceRootValidation result = WorkspaceRoot.validate(Objects.requireNonNull(candidate, "candidate"));
        if (result instanceof WorkspaceRootValidation.Accepted accepted) {
            draft = draft.withWorkspace(accepted.root());
        }
        return result;
    }

    /** Validates {@code input} and, if accepted, replaces the specification. */
    public synchronized TaskSpecificationValidation updateSpecification(TaskSpecificationDraft input) {
        TaskSpecificationValidation result = TaskSpecification.validate(Objects.requireNonNull(input, "input"));
        if (result instanceof TaskSpecificationValidation.Accepted accepted) {
            draft = draft.withSpecification(accepted.specification());
        }
        return result;
    }

    /**
     * Validates an explicit whole-workspace scope against the selected workspace and, if accepted,
     * replaces the scope.
     *
     * @throws IllegalStateException if no workspace has been selected
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    public synchronized TaskScopeValidation selectEntireWorkspace(List<String> excluded) throws IOException {
        return apply(TaskScope.validateEntireWorkspace(requireWorkspace(), excluded));
    }

    /**
     * Validates a scope limited to {@code allowed} areas against the selected workspace and, if
     * accepted, replaces the scope.
     *
     * @throws IllegalStateException if no workspace has been selected
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    public synchronized TaskScopeValidation selectPaths(List<String> allowed, List<String> excluded)
            throws IOException {
        return apply(TaskScope.validatePaths(requireWorkspace(), allowed, excluded));
    }

    /** Discards the whole draft, returning to the empty, incomplete state. */
    public synchronized void clear() {
        draft = TaskDraft.empty();
    }

    private WorkspaceRoot requireWorkspace() {
        return draft.workspace()
                .orElseThrow(() -> new IllegalStateException("A workspace must be selected before a scope"));
    }

    private TaskScopeValidation apply(TaskScopeValidation result) {
        if (result instanceof TaskScopeValidation.Accepted accepted) {
            draft = draft.withScope(accepted.scope());
        }
        return result;
    }
}
