package io.github.stevdrey.monadaforge.core.task;

import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Reason;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Violation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, validated description of a task entered manually by a developer.
 *
 * <p>Instances exist only for input that passed {@link #validate}. All text is stored as untrusted
 * data: the model confers no execution authority and its content is never interpreted as commands or
 * approvals.
 */
public final class TaskSpecification {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 20_000;
    public static final int MAX_ITEM_LENGTH = 2_000;
    public static final int MAX_ITEMS = 50;

    private final String title;
    private final String description;
    private final List<String> acceptanceCriteria;
    private final List<String> constraints;
    private final List<String> nonGoals;
    private final TaskSource source;

    private TaskSpecification(
            String title,
            String description,
            List<String> acceptanceCriteria,
            List<String> constraints,
            List<String> nonGoals,
            TaskSource source) {
        this.title = title;
        this.description = description;
        this.acceptanceCriteria = acceptanceCriteria;
        this.constraints = constraints;
        this.nonGoals = nonGoals;
        this.source = source;
    }

    /**
     * Normalizes and validates {@code draft}. Text is stripped and line endings become {@code \n};
     * list order is preserved. Every violation is reported, in field order.
     */
    public static TaskSpecificationValidation validate(TaskSpecificationDraft draft) {
        Objects.requireNonNull(draft, "draft");
        List<Violation> violations = new ArrayList<>();

        String title = scalar(draft.title(), Field.TITLE, MAX_TITLE_LENGTH, false, violations);
        String description =
                scalar(draft.description(), Field.DESCRIPTION, MAX_DESCRIPTION_LENGTH, true, violations);
        List<String> criteria = list(draft.acceptanceCriteria(), Field.ACCEPTANCE_CRITERIA, true, violations);
        List<String> constraints = list(draft.constraints(), Field.CONSTRAINTS, false, violations);
        List<String> nonGoals = list(draft.nonGoals(), Field.NON_GOALS, false, violations);

        if (!violations.isEmpty()) {
            return new TaskSpecificationValidation.Rejected(violations);
        }
        return new TaskSpecificationValidation.Accepted(new TaskSpecification(
                title, description, criteria, constraints, nonGoals, TaskSource.Manual.INSTANCE));
    }

    private static String scalar(
            String raw, Field field, int maxLength, boolean multiline, List<Violation> violations) {
        if (raw == null) {
            violations.add(new Violation(field, Violation.NO_INDEX, Reason.MISSING));
            return null;
        }
        return text(raw, field, Violation.NO_INDEX, maxLength, multiline, violations);
    }

    private static List<String> list(
            List<String> raw, Field field, boolean required, List<Violation> violations) {
        if (raw == null || (required && raw.isEmpty())) {
            if (required) {
                violations.add(new Violation(field, Violation.NO_INDEX, Reason.MISSING));
            }
            return List.of();
        }
        if (raw.size() > MAX_ITEMS) {
            violations.add(new Violation(field, Violation.NO_INDEX, Reason.TOO_MANY));
        }
        List<String> items = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String item = raw.get(i);
            if (item == null) {
                violations.add(new Violation(field, i, Reason.MISSING));
            } else {
                items.add(text(item, field, i, MAX_ITEM_LENGTH, true, violations));
            }
        }
        return List.copyOf(items);
    }

    private static String text(
            String raw, Field field, int index, int maxLength, boolean multiline, List<Violation> violations) {
        String value = raw.strip().replace("\r\n", "\n").replace('\r', '\n');
        if (value.isEmpty()) {
            violations.add(new Violation(field, index, Reason.BLANK));
        } else if (value.length() > maxLength) {
            violations.add(new Violation(field, index, Reason.TOO_LONG));
        } else if (hasDisallowedControl(value, multiline)) {
            violations.add(new Violation(field, index, Reason.CONTAINS_CONTROL_CHARACTERS));
        }
        return value;
    }

    private static boolean hasDisallowedControl(String value, boolean multiline) {
        return value.chars().anyMatch(c -> Character.isISOControl(c) && !(c == '\t' || (multiline && c == '\n')));
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Acceptance criteria in input order; never empty. */
    public List<String> acceptanceCriteria() {
        return acceptanceCriteria;
    }

    /** Explicit constraints in input order; possibly empty. */
    public List<String> constraints() {
        return constraints;
    }

    /** Explicit non-goals in input order; possibly empty. */
    public List<String> nonGoals() {
        return nonGoals;
    }

    public TaskSource source() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TaskSpecification that
                && title.equals(that.title)
                && description.equals(that.description)
                && acceptanceCriteria.equals(that.acceptanceCriteria)
                && constraints.equals(that.constraints)
                && nonGoals.equals(that.nonGoals)
                && source.equals(that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, acceptanceCriteria, constraints, nonGoals, source);
    }

    @Override
    public String toString() {
        return "TaskSpecification[title=" + title + ", acceptanceCriteria=" + acceptanceCriteria.size()
                + ", constraints=" + constraints.size() + ", nonGoals=" + nonGoals.size()
                + ", source=" + source + "]";
    }
}
