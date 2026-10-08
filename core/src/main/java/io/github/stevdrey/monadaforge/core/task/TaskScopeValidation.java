package io.github.stevdrey.monadaforge.core.task;

import java.util.List;
import java.util.Objects;

/** Outcome of validating the entries of a {@link TaskScope}. */
public sealed interface TaskScopeValidation {

    /** The list a {@link Violation} refers to. */
    enum Field {
        ALLOWED,
        EXCLUDED
    }

    /** Why a scope entry, or a whole list, was rejected. */
    enum Reason {
        /** An entry, or the whole allowed list of a paths scope, is {@code null}. */
        MISSING,
        /** A paths scope has no allowed entries; use the entire-workspace scope to allow everything. */
        EMPTY_ALLOWED,
        /** The list exceeds {@link TaskScope#MAX_ENTRIES}. */
        TOO_MANY,
        /** The entry is empty, not a valid path, or too long for the filesystem. */
        INVALID_PATH,
        /** The entry is absolute or carries a root component. */
        ABSOLUTE,
        /** The entry contains a {@code ..} segment. */
        TRAVERSAL,
        /** The entry does not exist; scope entries must name existing files or directories. */
        NOT_FOUND,
        /** The entry resolves, through symbolic links, outside the workspace root. */
        ESCAPES_WORKSPACE,
        /** Neither the entry nor its parent directory exists. */
        PARENT_NOT_FOUND,
        /** Access to a path component was denied while resolving the entry. */
        NOT_ACCESSIBLE,
        /** Containment of the entry could not be established. */
        UNRESOLVABLE,
        /**
         * The entry is the workspace root itself. Whole-workspace access must be selected explicitly,
         * and excluding the root would leave nothing.
         */
        WORKSPACE_ROOT,
        /** Another entry of the same list resolves to the same path. */
        DUPLICATE,
        /**
         * An allowed entry is entirely excluded, or an exclusion lies outside every allowed entry and
         * so has no effect.
         */
        ALLOWED_AND_EXCLUDED_CONTRADICT
    }

    /**
     * A single validation problem. {@code index} is the zero-based position of the offending entry,
     * or {@link #NO_INDEX} for whole-list problems.
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

    /** The entries form a valid scope. */
    record Accepted(TaskScope scope) implements TaskScopeValidation {
        public Accepted {
            Objects.requireNonNull(scope, "scope");
        }
    }

    /** The entries were rejected; {@code violations} is non-empty, immutable and grouped by field. */
    record Rejected(List<Violation> violations) implements TaskScopeValidation {
        public Rejected {
            violations = List.copyOf(violations);
            if (violations.isEmpty()) {
                throw new IllegalArgumentException("violations must not be empty");
            }
        }
    }
}
