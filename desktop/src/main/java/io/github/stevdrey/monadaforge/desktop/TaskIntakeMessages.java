package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskSpecification;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Violation;
import java.util.Objects;

/** Actionable, user-facing text for task intake; it never echoes what the user typed. */
final class TaskIntakeMessages {
    /** What the status region should show. */
    record Status(StatusKind kind, String text) {}

    private TaskIntakeMessages() {}

    static String describe(Violation violation) {
        Objects.requireNonNull(violation, "violation");
        Field field = violation.field();
        boolean item = violation.index() != Violation.NO_INDEX;
        String subject = item ? capitalize(itemName(field)) + " " + (violation.index() + 1) : sectionName(field);
        return switch (violation.reason()) {
            case MISSING -> item
                    ? subject + " is missing. Enter text or remove it."
                    : subject + " is required. " + (isList(field) ? "Add at least one item." : "Enter a value.");
            case BLANK -> subject + " is empty. Enter text" + (item ? " or remove it." : ".");
            case TOO_LONG -> subject + " is too long. Use at most " + maxLength(field) + " characters.";
            case TOO_MANY -> subject + " has too many items. Use at most " + TaskSpecification.MAX_ITEMS + ".";
            case CONTAINS_CONTROL_CHARACTERS -> subject
                    + " contains control or direction-changing characters. Remove them"
                    + (item || field == Field.DESCRIPTION ? "." : " (line breaks are not allowed here).");
        };
    }

    static Status status(TaskIntakeForm.Result result) {
        return switch (Objects.requireNonNull(result, "result")) {
            case TaskIntakeForm.Result.NotValidated _ ->
                    new Status(StatusKind.INFO, "Describe the task, then validate it");
            case TaskIntakeForm.Result.Valid _ -> new Status(StatusKind.SUCCESS, "Task specification is valid");
            case TaskIntakeForm.Result.Invalid invalid -> new Status(
                    StatusKind.ERROR,
                    invalid.violations().size() == 1
                            ? "Fix 1 problem in the task specification"
                            : "Fix " + invalid.violations().size() + " problems in the task specification");
        };
    }

    private static boolean isList(Field field) {
        return field != Field.TITLE && field != Field.DESCRIPTION;
    }

    private static int maxLength(Field field) {
        return switch (field) {
            case TITLE -> TaskSpecification.MAX_TITLE_LENGTH;
            case DESCRIPTION -> TaskSpecification.MAX_DESCRIPTION_LENGTH;
            case ACCEPTANCE_CRITERIA, CONSTRAINTS, NON_GOALS -> TaskSpecification.MAX_ITEM_LENGTH;
        };
    }

    static String sectionName(Field field) {
        return switch (field) {
            case TITLE -> "The title";
            case DESCRIPTION -> "The description";
            case ACCEPTANCE_CRITERIA -> "Acceptance criteria";
            case CONSTRAINTS -> "Constraints";
            case NON_GOALS -> "Non-goals";
        };
    }

    private static String itemName(Field field) {
        return switch (field) {
            case ACCEPTANCE_CRITERIA -> "acceptance criterion";
            case CONSTRAINTS -> "constraint";
            case NON_GOALS -> "non-goal";
            case TITLE, DESCRIPTION -> throw new IllegalArgumentException(field + " has no items");
        };
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
