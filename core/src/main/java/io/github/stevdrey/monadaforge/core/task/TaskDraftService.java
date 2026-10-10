package io.github.stevdrey.monadaforge.core.task;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Holds the current {@link TaskDraft} in process memory and is the single place that changes it.
 *
 * <p>Each operation validates its input through the owning component contract and returns that
 * contract's validation result unchanged, so callers keep the full violation detail. Only accepted
 * input replaces state; a rejected input leaves the draft exactly as it was. Nothing is persisted,
 * and no file is created or modified. Changes are serialized; reads never wait for them, because a
 * snapshot is immutable and always consistent. Scope validation touches the file system, so it runs
 * outside that serialization against the workspace snapshot and is applied afterwards only if that
 * workspace is still the selected one; a stalled file system therefore never blocks other changes.
 */
public final class TaskDraftService {

    /** The workspace a scope is validated against, and the epoch of the draft it was read from. */
    record Basis(WorkspaceRoot root, long epoch) {}

    private volatile TaskDraft draft = TaskDraft.empty();
    // Bumped whenever the workspace is replaced or the draft is cleared, so A, B, A cannot look unchanged.
    private long epoch;

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
            replaceWorkspace(accepted.root());
        }
        return result;
    }

    /**
     * Makes an already validated {@code root} the workspace, with the same scope rule as {@link
     * #selectWorkspace(Path)}. It performs no I/O, so a caller can validate off-thread and apply the
     * result only when it is still wanted. A {@link WorkspaceRoot} exists only for a validated
     * directory, so nothing unchecked can be applied.
     */
    public synchronized void selectWorkspace(WorkspaceRoot root) {
        replaceWorkspace(Objects.requireNonNull(root, "root"));
    }

    /** Validates {@code input} and, if accepted, replaces the specification. */
    public synchronized TaskSpecificationValidation updateSpecification(TaskSpecificationDraft input) {
        TaskSpecificationValidation result = TaskSpecification.validate(Objects.requireNonNull(input, "input"));
        if (result instanceof TaskSpecificationValidation.Accepted accepted) {
            draft = draft.withSpecification(accepted.specification());
        }
        return result;
    }

    /** Drops the specification, keeping the workspace and scope; a no-op when none is held. */
    public synchronized void clearSpecification() {
        draft = draft.withoutSpecification();
    }

    /**
     * Validates an explicit whole-workspace scope against the selected workspace and, if accepted,
     * replaces the scope.
     *
     * @throws IllegalStateException if no workspace has been selected, or it changed while validating
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    public TaskScopeValidation selectEntireWorkspace(List<String> excluded) throws IOException {
        return selectEntireWorkspace(excluded, () -> true);
    }

    /**
     * Like {@link #selectEntireWorkspace(List)}, but the result is applied only if {@code
     * stillWanted} is true at the moment of applying (checked under the draft's lock). When it is
     * false the draft is left untouched and the validation result is returned unapplied, so a caller
     * that has dropped the request cannot have it reinstate a scope.
     */
    public TaskScopeValidation selectEntireWorkspace(List<String> excluded, BooleanSupplier stillWanted)
            throws IOException {
        Basis basis = basis();
        return applyIfCurrent(
                basis, TaskScope.validateEntireWorkspace(basis.root(), excluded), Objects.requireNonNull(stillWanted));
    }

    /**
     * Validates a scope limited to {@code allowed} areas against the selected workspace and, if
     * accepted, replaces the scope.
     *
     * @throws IllegalStateException if no workspace has been selected, or it changed while validating
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    public TaskScopeValidation selectPaths(List<String> allowed, List<String> excluded) throws IOException {
        return selectPaths(allowed, excluded, () -> true);
    }

    /** Like {@link #selectPaths(List, List)} with the {@code stillWanted} guard of {@link #selectEntireWorkspace(List, BooleanSupplier)}. */
    public TaskScopeValidation selectPaths(List<String> allowed, List<String> excluded, BooleanSupplier stillWanted)
            throws IOException {
        Basis basis = basis();
        return applyIfCurrent(
                basis, TaskScope.validatePaths(basis.root(), allowed, excluded), Objects.requireNonNull(stillWanted));
    }

    /** Drops the scope back to {@linkplain TaskScope#unset() unset}, keeping workspace and specification; a no-op when unset. */
    public synchronized void clearScope() {
        draft = draft.withScope(TaskScope.unset());
    }

    /** Discards the whole draft, returning to the empty, incomplete state. */
    public synchronized void clear() {
        epoch++;
        draft = TaskDraft.empty();
    }

    private void replaceWorkspace(WorkspaceRoot root) {
        if (!root.equals(draft.workspace().orElse(null))) {
            epoch++;
        }
        draft = draft.withWorkspace(root);
    }

    /** The workspace and epoch a scope validation starts from. */
    synchronized Basis basis() {
        WorkspaceRoot root = draft.workspace()
                .orElseThrow(() -> new IllegalStateException("A workspace must be selected before a scope"));
        return new Basis(root, epoch);
    }

    /** Applies {@code result}, validated against {@code basis}, only while that exact draft state still holds. */
    synchronized TaskScopeValidation applyIfCurrent(Basis basis, TaskScopeValidation result) {
        return applyIfCurrent(basis, result, () -> true);
    }

    synchronized TaskScopeValidation applyIfCurrent(
            Basis basis, TaskScopeValidation result, BooleanSupplier stillWanted) {
        if (basis.epoch() != epoch || !basis.root().equals(draft.workspace().orElse(null))) {
            throw new IllegalStateException("The workspace changed during validation");
        }
        if (result instanceof TaskScopeValidation.Accepted accepted && stillWanted.getAsBoolean()) {
            draft = draft.withScope(accepted.scope());
        }
        return result;
    }
}
