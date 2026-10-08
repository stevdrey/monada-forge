package io.github.stevdrey.monadaforge.core.task;

/**
 * Where a {@link TaskSpecification} came from.
 *
 * <p>Only manual intake exists today. The interface is sealed so future origins can be added
 * deliberately, without provider-specific types leaking into the model.
 */
public sealed interface TaskSource {

    /** The specification was entered manually by the developer. */
    enum Manual implements TaskSource {
        INSTANCE
    }
}
