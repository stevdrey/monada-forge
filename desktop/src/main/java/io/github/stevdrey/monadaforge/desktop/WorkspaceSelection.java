package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Toolkit-independent workspace selection state.
 *
 * <p>Validation may touch the file system, so it runs on the background executor; results are
 * delivered to the listener through the UI executor. All methods must be called on the UI thread.
 */
final class WorkspaceSelection {
    private static final System.Logger LOG = System.getLogger(WorkspaceSelection.class.getName());

    /** Throwing validator so unexpected I/O failures stay distinguishable from invalid input. */
    @FunctionalInterface
    interface Validator {
        WorkspaceRootValidation validate(Path candidate) throws IOException;
    }

    sealed interface State {
        /** Nothing selected yet; a workspace is required. */
        record Required() implements State {}

        record Validating(Path requested) implements State {}

        record Selected(WorkspaceRoot root) implements State {}

        record Invalid(Path requested, String message) implements State {}
    }

    private final Validator validator;
    private final Executor background;
    private final Executor ui;
    private final Consumer<State> listener;
    private State state = new State.Required();
    private long generation;

    WorkspaceSelection(Validator validator, Executor background, Executor ui, Consumer<State> listener) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    State state() {
        return state;
    }

    /** Validates {@code candidate}; {@code null} means the chooser was cancelled and changes nothing. */
    void select(Path candidate) {
        if (candidate == null) {
            return;
        }
        long mine = ++generation;
        publish(new State.Validating(candidate));
        try {
            background.execute(() -> {
                State result = validate(candidate);
                ui.execute(() -> {
                    if (mine == generation) {
                        publish(result);
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            // E.g. the executor was shut down: never leave the view waiting for a result.
            LOG.log(System.Logger.Level.WARNING, "Workspace validation was rejected", e);
            publish(new State.Invalid(candidate, WorkspaceMessages.UNEXPECTED_FAILURE));
        }
    }

    private State validate(Path candidate) {
        try {
            return switch (validator.validate(candidate)) {
                case WorkspaceRootValidation.Accepted accepted -> new State.Selected(accepted.root());
                case WorkspaceRootValidation.Rejected rejected ->
                        new State.Invalid(candidate, WorkspaceMessages.describe(rejected.reason()));
            };
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Workspace validation failed unexpectedly", e);
            return new State.Invalid(candidate, WorkspaceMessages.UNEXPECTED_FAILURE);
        }
    }

    private void publish(State next) {
        state = next;
        listener.accept(next);
    }
}
