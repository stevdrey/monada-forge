package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Toolkit-independent coordination of one scope validation at a time.
 *
 * <p>Validation touches the file system, so it runs on the background executor and its outcome is
 * delivered through the UI executor. Every {@link #start} and every {@link #supersede} (an input
 * change, a workspace change) makes older requests obsolete: an obsolete outcome is ignored
 * completely, so it can neither reach the form nor re-enable the view for a newer request, and
 * {@link #pending()} never waits on a stalled operation that was superseded. All methods must be
 * called on the UI thread.
 */
final class ScopeValidation {
    private static final System.Logger LOG = System.getLogger(ScopeValidation.class.getName());

    private final TaskScopeForm form;
    private final Executor background;
    private final Executor ui;
    private final Runnable onChange;
    private boolean pending;
    private long requestId;

    /** @param onChange run when {@link #pending()} changes or a result was applied to the form */
    ScopeValidation(TaskScopeForm form, Executor background, Executor ui, Runnable onChange) {
        this.form = Objects.requireNonNull(form, "form");
        this.background = Objects.requireNonNull(background, "background");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    /** Whether the latest request has not delivered its outcome yet. */
    boolean pending() {
        return pending;
    }

    /** Validates the form's current input, superseding any earlier request. */
    void start() {
        var request = form.request();
        long id = ++requestId;
        pending = true;
        onChange.run();
        try {
            background.execute(() -> {
                TaskScopeValidation outcome = form.validate(request);
                ui.execute(() -> deliver(id, request, outcome));
            });
        } catch (RejectedExecutionException e) {
            // E.g. the executor was shut down: never leave the view waiting for a result.
            LOG.log(System.Logger.Level.WARNING, "Scope validation was rejected", e);
            deliver(id, request, null);
        }
    }

    /** Drops the pending request, if any, so a new one can start at once. */
    void supersede() {
        pending = false;
        requestId++;
    }

    private void deliver(long id, TaskScopeForm.Request request, TaskScopeValidation outcome) {
        if (id != requestId) {
            return;
        }
        pending = false;
        form.complete(request, outcome);
        onChange.run();
    }
}
