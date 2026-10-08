package io.github.stevdrey.monadaforge.core.task;

import java.util.List;

/**
 * Raw, unvalidated manual input for a {@link TaskSpecification}.
 *
 * <p>Any component may be {@code null}. A {@code null} {@code title}, {@code description} or
 * {@code acceptanceCriteria} is reported as a missing value, whereas a {@code null} optional list
 * ({@code constraints}, {@code nonGoals}) is accepted and treated as empty. All text is untrusted
 * data. The lists are read during {@link TaskSpecification#validate} and must not be modified
 * concurrently while it runs.
 */
public record TaskSpecificationDraft(
        String title,
        String description,
        List<String> acceptanceCriteria,
        List<String> constraints,
        List<String> nonGoals) {

    /** A structural summary only: unvalidated text never reaches logs or diagnostics. */
    @Override
    public String toString() {
        return "TaskSpecificationDraft[title=" + length(title)
                + ", description=" + length(description)
                + ", acceptanceCriteria=" + count(acceptanceCriteria)
                + ", constraints=" + count(constraints)
                + ", nonGoals=" + count(nonGoals) + "]";
    }

    private static String length(String text) {
        return text == null ? "null" : text.length() + " chars";
    }

    private static String count(List<String> items) {
        return items == null ? "null" : items.size() + " items";
    }
}
