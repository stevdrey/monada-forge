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
     * list order is preserved. Every violation is reported, in field order. For a list over
     * {@link #MAX_ITEMS}, only the first {@link #MAX_ITEMS} items are inspected, so the work stays
     * bounded regardless of the input size.
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
        // Input is untrusted: size work and allocation by the limit, never by the supplied list.
        int inspected = Math.min(raw.size(), MAX_ITEMS);
        List<String> items = new ArrayList<>(inspected);
        for (int i = 0; i < inspected; i++) {
            String item = raw.get(i);
            if (item == null) {
                violations.add(new Violation(field, i, Reason.MISSING));
            } else {
                items.add(text(item, field, i, MAX_ITEM_LENGTH, true, violations));
            }
        }
        return List.copyOf(items);
    }

    /**
     * Inspects {@code raw} in one pass without copying it, so an oversized untrusted string is
     * rejected before any normalized copy is allocated. The normalized form (edge whitespace
     * stripped, {@code \r\n} and {@code \r} as {@code \n}) is built only for accepted values.
     */
    private static String text(
            String raw, Field field, int index, int maxLength, boolean multiline, List<Violation> violations) {
        int start = 0;
        int end = raw.length();
        while (start < end && isBlank(raw.codePointAt(start))) {
            start += Character.charCount(raw.codePointAt(start));
        }
        while (end > start && isBlank(raw.codePointBefore(end))) {
            end -= Character.charCount(raw.codePointBefore(end));
        }
        if (start == end) {
            violations.add(new Violation(field, index, Reason.BLANK));
            return "";
        }

        int length = 0;
        boolean disallowed = false;
        for (int i = start; i < end; i++) {
            char c = raw.charAt(i);
            if (c == '\r' && i + 1 < end && raw.charAt(i + 1) == '\n') {
                continue; // the CRLF pair counts once, at its LF
            }
            length++;
            disallowed |= isDisallowed(c, multiline);
        }
        if (length > maxLength) {
            violations.add(new Violation(field, index, Reason.TOO_LONG));
        }
        if (disallowed) {
            violations.add(new Violation(field, index, Reason.CONTAINS_CONTROL_CHARACTERS));
        }
        if (length > maxLength || disallowed) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(length);
        for (int i = start; i < end; i++) {
            char c = raw.charAt(i);
            if (c == '\r') {
                if (i + 1 < end && raw.charAt(i + 1) == '\n') {
                    continue;
                }
                c = '\n';
            }
            normalized.append(c);
        }
        return normalized.toString();
    }

    /** Whitespace per Java plus Unicode space separators such as U+00A0, U+2007 and U+202F. */
    private static boolean isBlank(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /**
     * Control characters other than tab are disallowed; line breaks (LF, CR, U+2028, U+2029) are
     * allowed only in multiline fields.
     */
    private static boolean isDisallowed(char c, boolean multiline) {
        if (c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') {
            return !multiline;
        }
        return Character.isISOControl(c) && c != '\t';
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
        return "TaskSpecification[titleLength=" + title.length() + ", acceptanceCriteria=" + acceptanceCriteria.size()
                + ", constraints=" + constraints.size() + ", nonGoals=" + nonGoals.size()
                + ", source=" + source + "]";
    }
}
