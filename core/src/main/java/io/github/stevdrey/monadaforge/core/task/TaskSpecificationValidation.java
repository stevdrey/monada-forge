package io.github.stevdrey.monadaforge.core.task;

import java.util.List;
import java.util.Objects;

/** Outcome of validating a {@link TaskSpecificationDraft}. */
public sealed interface TaskSpecificationValidation {

    /** The input field a {@link Violation} refers to. */
    enum Field {
        TITLE,
        DESCRIPTION,
        ACCEPTANCE_CRITERIA,
        CONSTRAINTS,
        NON_GOALS
    }

    /** Why a field value was rejected. */
    enum Reason {
        /** The value, or the whole required list, is absent. */
        MISSING,
        /** The value is empty or whitespace-only after normalization. */
        BLANK,
        /** The value exceeds the maximum length. */
        TOO_LONG,
        /** The list exceeds the maximum number of items. */
        TOO_MANY,
        /** The value contains a disallowed control character. */
        CONTAINS_CONTROL_CHARACTERS
    }

    /**
     * A single validation problem. {@code index} is the zero-based position of the offending list
     * item, or {@link #NO_INDEX} for scalar fields and whole-list problems.
     */
    record Violation(Field field, int index, Reason reason) {
        public static final int NO_INDEX = -1;

        public Violation {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(reason, "reason");
            if (index < NO_INDEX) {
                throw new IllegalArgumentException("index must be >= " + NO_INDEX);
            }
        }
    }

    /** The draft is a valid specification. */
    record Accepted(TaskSpecification specification) implements TaskSpecificationValidation {
        public Accepted {
            Objects.requireNonNull(specification, "specification");
        }
    }

    /** The draft was rejected; {@code violations} is non-empty, immutable and in field order. */
    record Rejected(List<Violation> violations) implements TaskSpecificationValidation {
        public Rejected {
            violations = List.copyOf(violations);
            if (violations.isEmpty()) {
                throw new IllegalArgumentException("violations must not be empty");
            }
        }
    }
}
