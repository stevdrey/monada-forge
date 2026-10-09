package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskScope;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Violation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Toolkit-independent state of the task scope editor.
 *
 * <p>The form only holds the relative paths the user typed; every rule is decided by the core scope
 * validation passed to the constructor, so the form cannot widen or bypass containment checks. The
 * scope mode is never inferred: it starts {@link Mode#NOT_CHOSEN} and an empty allowed list is
 * rejected by core instead of being read as the whole workspace. Validation never changes the
 * entered values. The result is {@link Result.Valid} only for the exact input core accepted: any
 * later edit, or a workspace change, returns it to {@link Result.NotValidated} and runs {@code
 * onInvalidated} so the accepted scope is dropped from the draft. Validation touches the file
 * system, so it is split into {@link #request()} and {@link #validate} (callable off the UI
 * thread) and {@link #complete}, which ignores an outcome that no longer matches the form. All text
 * is untrusted data. Everything except {@link #validate} must be used on a single thread.
 */
final class TaskScopeForm {
    private static final System.Logger LOG = System.getLogger(TaskScopeForm.class.getName());

    enum Mode {
        /** No explicit choice yet; nothing can be validated. */
        NOT_CHOSEN,
        /** Only the allowed paths, minus the excluded ones. */
        SPECIFIC_PATHS,
        /** The whole workspace, chosen on purpose, minus the excluded paths. */
        ENTIRE_WORKSPACE
    }

    /** Throwing validator so unexpected I/O failures stay distinguishable from invalid input. */
    @FunctionalInterface
    interface Validator {
        TaskScopeValidation validate(Mode mode, List<String> allowed, List<String> excluded) throws IOException;
    }

    sealed interface Result {
        /** Nothing has been validated since the last edit. */
        record NotValidated() implements Result {}

        /** Validation was asked for before a scope mode was chosen. */
        record ModeRequired() implements Result {}

        record Valid(TaskScope scope) implements Result {}

        record Invalid(List<Violation> violations) implements Result {
            public Invalid {
                violations = List.copyOf(violations);
            }
        }

        /** Validation could not run (unexpected failure); the entered values are untouched. */
        record Failed() implements Result {}
    }

    /** An immutable copy of the input to validate, tied to the form version it was taken from. */
    record Request(long version, Mode mode, List<String> allowed, List<String> excluded) {}

    private final Validator validator;
    private final Runnable onInvalidated;
    private final List<String> allowed = new ArrayList<>();
    private final List<String> excluded = new ArrayList<>();
    private final List<Violation> errors = new ArrayList<>();
    private Mode mode = Mode.NOT_CHOSEN;
    private Result result = new Result.NotValidated();
    private boolean workspaceChanged;
    private long version;

    /**
     * @param onInvalidated run when an accepted result stops matching the form, so whoever stored the
     *     accepted scope can drop it
     */
    TaskScopeForm(Validator validator, Runnable onInvalidated) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.onInvalidated = Objects.requireNonNull(onInvalidated, "onInvalidated");
    }

    Mode mode() {
        return mode;
    }

    List<String> items(Field field) {
        return List.copyOf(list(field));
    }

    Result result() {
        return result;
    }

    /** Whether entries were kept across a workspace change and still await re-validation. */
    boolean needsRevalidation() {
        return workspaceChanged && result instanceof Result.NotValidated;
    }

    /** The errors from the last validation that the user has not edited away yet. */
    List<Violation> errors() {
        return List.copyOf(errors);
    }

    /** Errors for one entry ({@code index}) or for the whole list ({@link Violation#NO_INDEX}). */
    List<Violation> errors(Field field, int index) {
        return errors.stream()
                .filter(violation -> violation.field() == field && violation.index() == index)
                .toList();
    }

    /** Chooses the scope mode explicitly; the typed entries are kept when switching. */
    void setMode(Mode newMode) {
        Mode value = Objects.requireNonNull(newMode, "newMode");
        if (value != mode) {
            mode = value;
            errors.clear();
            invalidate();
        }
    }

    void setItem(Field field, int index, String text) {
        List<String> items = list(field);
        String value = Objects.requireNonNull(text, "text");
        if (!value.equals(items.get(index))) {
            items.set(index, value);
            errors.removeIf(violation -> violation.field() == field && violation.index() == index);
            dropContradictions();
            invalidate();
        }
    }

    void addItem(Field field) {
        list(field).add("");
        restructured(field);
    }

    void removeItem(Field field, int index) {
        list(field).remove(index);
        restructured(field);
    }

    /** Whether the user has chosen a mode or typed anything. */
    boolean hasContent() {
        return mode != Mode.NOT_CHOSEN
                || allowed.stream().anyMatch(item -> !item.isBlank())
                || excluded.stream().anyMatch(item -> !item.isBlank());
    }

    /**
     * The workspace was replaced. With {@code keep} the entries stay as typed but nothing is trusted
     * until validated again against the new root; otherwise the form returns to its initial state.
     */
    void workspaceChanged(boolean keep) {
        if (!keep) {
            clear();
            return;
        }
        errors.clear();
        invalidate();
        workspaceChanged = true;
    }

    /** Returns to the initial state: no mode, no entries, not validated. */
    void clear() {
        mode = Mode.NOT_CHOSEN;
        allowed.clear();
        excluded.clear();
        errors.clear();
        invalidate();
        workspaceChanged = false;
    }

    /** Copies the current input for {@link #validate}. */
    Request request() {
        return new Request(version, mode, List.copyOf(allowed), List.copyOf(excluded));
    }

    /**
     * Runs core validation on {@code request}. It only reads the request, so it may run off the UI
     * thread. {@code null} means validation could not run.
     */
    TaskScopeValidation validate(Request request) {
        if (request.mode() == Mode.NOT_CHOSEN) {
            return null;
        }
        try {
            return validator.validate(request.mode(), request.allowed(), request.excluded());
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Scope validation failed unexpectedly", e);
            return null;
        }
    }

    /**
     * Applies the outcome of {@link #validate} if the form is still as it was when {@code request}
     * was taken; otherwise the outcome is dropped, and an accepted scope is withdrawn from the draft.
     * Returns the resulting {@link #result()}.
     */
    Result complete(Request request, TaskScopeValidation outcome) {
        boolean current = request.version() == version;
        if (!current) {
            if (outcome instanceof TaskScopeValidation.Accepted) {
                onInvalidated.run();
            }
            return result;
        }
        errors.clear();
        workspaceChanged = false;
        if (!(outcome instanceof TaskScopeValidation.Accepted)) {
            // Core leaves an earlier accepted scope in the draft when it rejects new input.
            onInvalidated.run();
        }
        result = switch (outcome) {
            case TaskScopeValidation.Accepted accepted -> new Result.Valid(accepted.scope());
            case TaskScopeValidation.Rejected rejected -> {
                errors.addAll(rejected.violations());
                yield new Result.Invalid(rejected.violations());
            }
            case null -> mode == Mode.NOT_CHOSEN ? new Result.ModeRequired() : new Result.Failed();
        };
        return result;
    }

    /** Validates the current input in the calling thread; convenient where blocking is acceptable. */
    Result submit() {
        Request request = request();
        return complete(request, validate(request));
    }

    private List<String> list(Field field) {
        return switch (Objects.requireNonNull(field, "field")) {
            case ALLOWED -> allowed;
            case EXCLUDED -> excluded;
        };
    }

    private void restructured(Field field) {
        errors.removeIf(violation -> violation.field() == field);
        dropContradictions();
        invalidate();
    }

    /** A contradiction between the lists is reported on one of them but caused by either, so any edit clears it. */
    private void dropContradictions() {
        errors.removeIf(violation -> violation.reason() == TaskScopeValidation.Reason.ALLOWED_AND_EXCLUDED_CONTRADICT);
    }

    private void invalidate() {
        version++;
        if (result instanceof Result.Valid) {
            onInvalidated.run();
        }
        result = new Result.NotValidated();
    }
}
