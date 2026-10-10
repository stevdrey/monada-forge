package io.github.stevdrey.monadaforge.desktop;

import java.util.Objects;

/** User-facing text for the review step; it never echoes the task text or paths the user entered. */
final class TaskReviewMessages {
    static final String NOT_AUTHORIZATION =
            "Confirming only marks this draft as reviewed and ready for a later phase. It does not authorize "
                    + "any action, and no agent has run.";
    static final String CONFIRMED = "Confirmed and ready for the next phase. No agent has run yet";
    static final String CONFIRMED_WITH_EDITS =
            "This draft is confirmed, but edits you made since are not part of it. Validate them to update the draft";
    static final String READY = "Review the task, then confirm it";

    private TaskReviewMessages() {}

    static String describe(TaskReviewModel.Blocker blocker) {
        return switch (Objects.requireNonNull(blocker, "blocker")) {
            case WORKSPACE_MISSING -> "No workspace is selected. Select one before confirming.";
            case SPECIFICATION_MISSING -> "The task is not defined yet. Validate the task before confirming.";
            case SPECIFICATION_NOT_VALIDATED ->
                    "The task was edited after it was last validated. Validate it again before confirming.";
            case SCOPE_MISSING -> "No scope is defined yet. Validate the scope before confirming.";
            case SCOPE_NOT_VALIDATED ->
                    "The scope must be validated again before confirming, for example after a workspace change.";
        };
    }

    static StatusMessage status(TaskReviewModel model) {
        Objects.requireNonNull(model, "model");
        if (model.confirmed() && model.blockers().isEmpty()) {
            return new StatusMessage(StatusKind.SUCCESS, CONFIRMED);
        }
        if (!model.blockers().isEmpty()) {
            int count = model.blockers().size();
            return new StatusMessage(
                    StatusKind.WARNING,
                    count == 1 ? "Resolve 1 problem before confirming" : "Resolve " + count + " problems before confirming");
        }
        return new StatusMessage(StatusKind.INFO, READY);
    }
}
