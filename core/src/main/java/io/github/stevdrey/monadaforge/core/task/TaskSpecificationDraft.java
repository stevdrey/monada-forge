package io.github.stevdrey.monadaforge.core.task;

import java.util.List;

/**
 * Raw, unvalidated manual input for a {@link TaskSpecification}.
 *
 * <p>Any component may be {@code null}; validation reports that as a missing value. All text is
 * untrusted data.
 */
public record TaskSpecificationDraft(
        String title,
        String description,
        List<String> acceptanceCriteria,
        List<String> constraints,
        List<String> nonGoals) {}
