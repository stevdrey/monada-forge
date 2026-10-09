package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskSpecification;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationDraft;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Violation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Toolkit-independent state of the manual task intake form.
 *
 * <p>The form only holds what the user typed; every rule is decided by the core validation passed to
 * the constructor. Validation never changes the entered values, so fixing one field cannot erase
 * another. The result is {@link Result.Valid} only for the exact text that core accepted: any later
 * edit returns it to {@link Result.NotValidated}. An edit clears just the errors of the field it
 * touched, and a structural list change (add, remove, reorder) clears that list's errors because
 * they refer to item positions. All text is untrusted data. Must be used on a single thread.
 */
final class TaskIntakeForm {
    sealed interface Result {
        /** Nothing has been validated since the last edit. */
        record NotValidated() implements Result {}

        record Valid(TaskSpecification specification) implements Result {}

        record Invalid(List<Violation> violations) implements Result {
            public Invalid {
                violations = List.copyOf(violations);
            }
        }
    }

    private final Function<TaskSpecificationDraft, TaskSpecificationValidation> validator;
    private final Map<Field, List<String>> lists = new EnumMap<>(Field.class);
    private final List<Violation> errors = new ArrayList<>();
    private String title = "";
    private String description = "";
    private final Runnable onInvalidated;
    private Result result = new Result.NotValidated();

    TaskIntakeForm(Function<TaskSpecificationDraft, TaskSpecificationValidation> validator) {
        this(validator, () -> {});
    }

    /**
     * @param onInvalidated run when an accepted result stops matching the form, so whoever stored the
     *     accepted specification can drop it
     */
    TaskIntakeForm(Function<TaskSpecificationDraft, TaskSpecificationValidation> validator, Runnable onInvalidated) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.onInvalidated = Objects.requireNonNull(onInvalidated, "onInvalidated");
        lists.put(Field.ACCEPTANCE_CRITERIA, new ArrayList<>(List.of("")));
        lists.put(Field.CONSTRAINTS, new ArrayList<>());
        lists.put(Field.NON_GOALS, new ArrayList<>());
    }

    String title() {
        return title;
    }

    String description() {
        return description;
    }

    List<String> items(Field field) {
        return List.copyOf(list(field));
    }

    Result result() {
        return result;
    }

    /** The errors from the last validation that the user has not edited away yet. */
    List<Violation> errors() {
        return List.copyOf(errors);
    }

    /** Errors for one scalar field, or for one list item ({@code index}), or the whole list ({@link Violation#NO_INDEX}). */
    List<Violation> errors(Field field, int index) {
        return errors.stream()
                .filter(violation -> violation.field() == field && violation.index() == index)
                .toList();
    }

    void setTitle(String text) {
        String value = Objects.requireNonNull(text, "text");
        if (!value.equals(title)) {
            title = value;
            edited(Field.TITLE, Violation.NO_INDEX);
        }
    }

    void setDescription(String text) {
        String value = Objects.requireNonNull(text, "text");
        if (!value.equals(description)) {
            description = value;
            edited(Field.DESCRIPTION, Violation.NO_INDEX);
        }
    }

    void setItem(Field field, int index, String text) {
        List<String> items = list(field);
        String value = Objects.requireNonNull(text, "text");
        if (!value.equals(items.get(index))) {
            items.set(index, value);
            edited(field, index); // whole-list errors (such as too many items) stay until the list changes
        }
    }

    void addItem(Field field) {
        list(field).add("");
        restructured(field);
    }

    void removeItem(Field field, int index) {
        list(field).remove(index);
        restructured(field);
    }

    /** Moves the item one place earlier; returns {@code false} when it is already first. */
    boolean moveUp(Field field, int index) {
        return swap(field, index, index - 1);
    }

    /** Moves the item one place later; returns {@code false} when it is already last. */
    boolean moveDown(Field field, int index) {
        return swap(field, index, index + 1);
    }

    /** Whether the user has typed anything, ignoring whitespace. */
    boolean hasContent() {
        return !title.isBlank()
                || !description.isBlank()
                || lists.values().stream().flatMap(List::stream).anyMatch(item -> !item.isBlank());
    }

    /** Returns to the initial, empty and not-validated form. */
    void clear() {
        title = "";
        description = "";
        lists.get(Field.ACCEPTANCE_CRITERIA).clear();
        lists.get(Field.ACCEPTANCE_CRITERIA).add("");
        lists.get(Field.CONSTRAINTS).clear();
        lists.get(Field.NON_GOALS).clear();
        errors.clear();
        invalidate();
    }

    /** Validates the current text through core; the entered values are left untouched. */
    Result submit() {
        var draft = new TaskSpecificationDraft(
                title,
                description,
                List.copyOf(list(Field.ACCEPTANCE_CRITERIA)),
                List.copyOf(list(Field.CONSTRAINTS)),
                List.copyOf(list(Field.NON_GOALS)));
        errors.clear();
        result = switch (validator.apply(draft)) {
            case TaskSpecificationValidation.Accepted accepted -> new Result.Valid(accepted.specification());
            case TaskSpecificationValidation.Rejected rejected -> {
                errors.addAll(rejected.violations());
                yield new Result.Invalid(rejected.violations());
            }
        };
        return result;
    }

    private boolean swap(Field field, int index, int other) {
        List<String> items = list(field);
        Objects.checkIndex(index, items.size());
        if (other < 0 || other >= items.size()) {
            return false;
        }
        String moved = items.get(index);
        items.set(index, items.get(other));
        items.set(other, moved);
        restructured(field);
        return true;
    }

    private List<String> list(Field field) {
        List<String> items = lists.get(Objects.requireNonNull(field, "field"));
        if (items == null) {
            throw new IllegalArgumentException(field + " is not a list field");
        }
        return items;
    }

    private void edited(Field field, int index) {
        errors.removeIf(violation -> violation.field() == field && violation.index() == index);
        invalidate();
    }

    private void restructured(Field field) {
        errors.removeIf(violation -> violation.field() == field);
        invalidate();
    }

    private void invalidate() {
        if (result instanceof Result.Valid) {
            onInvalidated.run();
        }
        result = new Result.NotValidated();
    }
}
