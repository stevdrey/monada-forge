package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRoot;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Toolkit-independent confirmation of a selected workspace.
 *
 * <p>The selected root is revalidated on the background executor and changes no shared state; the
 * result is delivered through the UI executor, and only if it is still the latest request. Every
 * {@link #register} and every {@link #invalidate} (a selection change) supersedes older requests, so
 * a slow, obsolete result can never override a newer choice, including the A, B, A sequence. All
 * methods must be called on the UI thread.
 */
final class WorkspaceRegistration {
    private static final System.Logger LOG = System.getLogger(WorkspaceRegistration.class.getName());

    /** Receives the result of the latest request, on the UI executor. */
    interface Outcome {
        /** {@code fresh} is the root as validated now, which may differ from the one requested. */
        void accepted(WorkspaceRoot fresh, boolean discardTask);

        void rejected(Path requested, String message);
    }

    private record Result(WorkspaceRoot fresh, String message) {}

    private final WorkspaceSelection.Validator validator;
    private final Executor background;
    private final Executor ui;
    private final Outcome outcome;
    private long generation;

    WorkspaceRegistration(
            WorkspaceSelection.Validator validator, Executor background, Executor ui, Outcome outcome) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
    }

    /** Drops any registration still in flight, e.g. because the selection changed. */
    void invalidate() {
        generation++;
    }

    void register(WorkspaceRoot root, boolean discardTask) {
        Objects.requireNonNull(root, "root");
        long mine = ++generation;
        try {
            background.execute(() -> {
                Result result = validate(root.path());
                ui.execute(() -> {
                    if (mine == generation) {
                        deliver(root.path(), result, discardTask);
                    }
                });
            });
        } catch (RejectedExecutionException e) {
            // E.g. the executor was shut down: never leave the view waiting for a result.
            LOG.log(System.Logger.Level.WARNING, "Workspace registration was rejected", e);
            outcome.rejected(root.path(), WorkspaceMessages.UNEXPECTED_FAILURE);
        }
    }

    private void deliver(Path requested, Result result, boolean discardTask) {
        if (result.fresh() != null) {
            outcome.accepted(result.fresh(), discardTask);
        } else {
            outcome.rejected(requested, result.message());
        }
    }

    private Result validate(Path candidate) {
        try {
            return switch (validator.validate(candidate)) {
                case WorkspaceRootValidation.Accepted accepted -> new Result(accepted.root(), null);
                case WorkspaceRootValidation.Rejected rejected ->
                        new Result(null, WorkspaceMessages.describe(rejected.reason()));
            };
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Workspace registration failed unexpectedly", e);
            return new Result(null, WorkspaceMessages.UNEXPECTED_FAILURE);
        }
    }
}
