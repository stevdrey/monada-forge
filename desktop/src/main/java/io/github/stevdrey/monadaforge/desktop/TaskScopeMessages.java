package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskScope;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Violation;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Actionable, user-facing text for the scope editor; it never echoes what the user typed. */
final class TaskScopeMessages {
    static final String MODE_REQUIRED = "Choose what the task may touch before validating the scope";
    static final String WORKSPACE_CHANGED =
            "The workspace changed. Your entries were kept but are not trusted until you validate them again";

    private TaskScopeMessages() {}

    static String describe(Violation violation) {
        Objects.requireNonNull(violation, "violation");
        Field field = violation.field();
        boolean entry = violation.index() != Violation.NO_INDEX;
        String subject = entry ? capitalize(entryName(field)) + " " + (violation.index() + 1) : listName(field);
        return switch (violation.reason()) {
            case MISSING -> subject + " is missing. Enter a path or remove it.";
            case EMPTY_ALLOWED -> subject + " is empty. Add a path, or choose the entire workspace explicitly.";
            case TOO_MANY -> subject + " has too many entries. Use at most " + TaskScope.MAX_ENTRIES + ".";
            case INVALID_PATH -> subject + " is not a valid path. Enter a path relative to the workspace.";
            case ABSOLUTE -> subject + " is an absolute path. Enter a path relative to the workspace.";
            case TRAVERSAL -> subject + " contains \"..\". Paths cannot leave their directory.";
            case NOT_FOUND -> subject + " does not exist in the workspace.";
            case ESCAPES_WORKSPACE -> subject + " points outside the workspace and is not allowed.";
            case PARENT_NOT_FOUND -> subject + " does not exist, and neither does its parent directory.";
            case NOT_ACCESSIBLE -> subject + " cannot be accessed. Check its permissions.";
            case UNRESOLVABLE -> subject + " could not be checked against the workspace boundary.";
            case WORKSPACE_ROOT -> field == Field.EXCLUDED
                    ? subject + " is the workspace itself, so excluding it would leave nothing. Remove it or "
                            + "exclude a narrower path."
                    : subject + " is the workspace itself. To allow everything, choose the entire workspace "
                            + "explicitly.";
            case DUPLICATE -> subject + " repeats another entry in this list.";
            case ALLOWED_AND_EXCLUDED_CONTRADICT -> field == Field.ALLOWED
                    ? subject + " is completely excluded. Remove it or narrow the exclusion."
                    : subject + " lies outside every allowed path, so it has no effect. Remove it or adjust the allowed paths.";
        };
    }

    /** One text for all {@code violations}, or {@code null} when there are none. */
    static String describeAll(List<Violation> violations) {
        return violations.isEmpty()
                ? null
                : violations.stream().map(TaskScopeMessages::describe).collect(Collectors.joining(" "));
    }

    static StatusMessage status(TaskScopeForm.Result result, boolean needsRevalidation) {
        return switch (Objects.requireNonNull(result, "result")) {
            case TaskScopeForm.Result.NotValidated _ -> needsRevalidation
                    ? new StatusMessage(StatusKind.WARNING, WORKSPACE_CHANGED)
                    : new StatusMessage(StatusKind.INFO, "Define the task scope, then validate it");
            case TaskScopeForm.Result.ModeRequired _ ->
                    new StatusMessage(StatusKind.ERROR, MODE_REQUIRED);
            case TaskScopeForm.Result.Valid _ -> new StatusMessage(StatusKind.SUCCESS, "Scope is valid");
            case TaskScopeForm.Result.Invalid invalid -> new StatusMessage(
                    StatusKind.ERROR,
                    invalid.violations().size() == 1
                            ? "Fix 1 problem in the scope"
                            : "Fix " + invalid.violations().size() + " problems in the scope");
            case TaskScopeForm.Result.Failed _ ->
                    new StatusMessage(StatusKind.ERROR, "The scope could not be checked. Try again");
        };
    }

    private static String listName(Field field) {
        return switch (field) {
            case ALLOWED -> "Allowed paths";
            case EXCLUDED -> "Excluded paths";
        };
    }

    private static String entryName(Field field) {
        return switch (field) {
            case ALLOWED -> "allowed path";
            case EXCLUDED -> "excluded path";
        };
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
