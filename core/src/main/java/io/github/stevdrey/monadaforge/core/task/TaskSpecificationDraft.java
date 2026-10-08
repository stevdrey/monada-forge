package io.github.stevdrey.monadaforge.core.task;

import java.util.List;

/**
 * Raw, unvalidated manual input for a {@link TaskSpecification}.
 *
 * <p>Any component may be {@code null}. A {@code null} {@code title}, {@code description} or
 * {@code acceptanceCriteria} is reported as a missing value, whereas a {@code null} optional list
 * ({@code constraints}, {@code nonGoals}) is accepted and treated as empty. All text is untrusted
 * data.
 */
public record TaskSpecificationDraft(
        String title,
        String description,
        List<String> acceptanceCriteria,
        List<String> constraints,
        List<String> nonGoals) {}
