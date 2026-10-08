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

    /** Maximum lengths are measured in Unicode code points after normalization. */
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 20_000;
    public static final int MAX_ITEM_LENGTH = 2_000;
    /** Maximum number of entries in each list. */
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
        // One bounded snapshot of the untrusted list: its size and elements are read exactly once.
        List<String> snapshot = raw == null ? List.of() : raw.stream().limit(MAX_ITEMS + 1L).toList();
        if (snapshot.isEmpty()) {
            if (required) {
                violations.add(new Violation(field, Violation.NO_INDEX, Reason.MISSING));
            }
            return List.of();
        }
        if (snapshot.size() > MAX_ITEMS) {
            violations.add(new Violation(field, Violation.NO_INDEX, Reason.TOO_MANY));
        }
        int inspected = Math.min(snapshot.size(), MAX_ITEMS);
        List<String> items = new ArrayList<>(inspected);
        for (int i = 0; i < inspected; i++) {
            String item = snapshot.get(i);
            if (item == null) {
                violations.add(new Violation(field, i, Reason.MISSING));
            } else {
                items.add(text(item, field, i, MAX_ITEM_LENGTH, true, violations));
            }
        }
        return List.copyOf(items);
    }

    /**
     * Strips, inspects and normalizes {@code raw} in a single pass over its code points. The
     * normalized text is buffered only up to {@code maxLength}, so memory stays bounded however large
     * the untrusted input is; a rejected value yields {@code ""}.
     */
    private static String text(
            String raw, Field field, int index, int maxLength, boolean multiline, List<Violation> violations) {
        int start = 0;
        int end = raw.length();
        while (start < end) {
            int codePoint = raw.codePointAt(start);
            if (!isBlank(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = raw.codePointBefore(end);
            if (!isBlank(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        if (start == end) {
            violations.add(new Violation(field, index, Reason.BLANK));
            return "";
        }

        StringBuilder normalized = new StringBuilder(Math.min(end - start, maxLength));
        int length = 0;
        boolean disallowed = false;
        for (int i = start; i < end; ) {
            int codePoint = raw.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\r' && i < end && raw.charAt(i) == '\n') {
                continue; // a CRLF pair is one line break, counted at its LF
            }
            if (isLineBreak(codePoint)) {
                disallowed |= !multiline;
                codePoint = '\n';
            } else {
                disallowed |= isDisallowed(codePoint);
            }
            if (length < maxLength) {
                normalized.appendCodePoint(codePoint);
            }
            length++;
        }
        if (length > maxLength) {
            violations.add(new Violation(field, index, Reason.TOO_LONG));
        }
        if (disallowed) {
            violations.add(new Violation(field, index, Reason.CONTAINS_CONTROL_CHARACTERS));
        }
        return length > maxLength || disallowed ? "" : normalized.toString();
    }

    /** Line breaks, tab, Unicode space separators and zero-width spaces are stripped at the edges. */
    private static boolean isBlank(int codePoint) {
        if (isLineBreak(codePoint) || codePoint == '\t') {
            return true;
        }
        return !Character.isISOControl(codePoint)
                && (Character.isSpaceChar(codePoint)
                        || codePoint == 0x200B
                        || codePoint == 0x2060
                        || codePoint == 0xFEFF);
    }

    /** LF, CR, NEL, LINE SEPARATOR and PARAGRAPH SEPARATOR; all normalize to {@code \n}. */
    private static boolean isLineBreak(int codePoint) {
        return codePoint == '\n' || codePoint == '\r' || codePoint == 0x85
                || codePoint == 0x2028 || codePoint == 0x2029;
    }

    /** Control characters other than tab, bidirectional controls and unpaired surrogates. */
    private static boolean isDisallowed(int codePoint) {
        return (Character.isISOControl(codePoint) && codePoint != '\t')
                || codePoint == 0x061C
                || codePoint == 0x200E
                || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069)
                || (codePoint >= 0xD800 && codePoint <= 0xDFFF);
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
