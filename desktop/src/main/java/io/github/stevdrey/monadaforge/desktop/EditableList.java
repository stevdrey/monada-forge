package io.github.stevdrey.monadaforge.desktop;

import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation;
import java.util.List;
import java.util.Objects;

/** The list behind an {@link ItemListEditor}: its entries, edit operations and current feedback. */
interface EditableList {
    List<String> items();

    void setItem(int index, String text);

    void addItem();

    void removeItem(int index);

    /** Moves the item one place earlier; {@code false} when it cannot move. */
    default boolean moveUp(int index) {
        return false;
    }

    /** Moves the item one place later; {@code false} when it cannot move. */
    default boolean moveDown(int index) {
        return false;
    }

    /** User-facing error for one entry, or {@code null} when it has none. */
    String itemError(int index);

    /** User-facing error for the whole list, or {@code null} when it has none. */
    String listError();

    /** A reorderable list of one task specification field. */
    static EditableList of(TaskIntakeForm form, TaskSpecificationValidation.Field field) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(field, "field");
        return new EditableList() {
            @Override
            public List<String> items() {
                return form.items(field);
            }

            @Override
            public void setItem(int index, String text) {
                form.setItem(field, index, text);
            }

            @Override
            public void addItem() {
                form.addItem(field);
            }

            @Override
            public void removeItem(int index) {
                form.removeItem(field, index);
            }

            @Override
            public boolean moveUp(int index) {
                return form.moveUp(field, index);
            }

            @Override
            public boolean moveDown(int index) {
                return form.moveDown(field, index);
            }

            @Override
            public String itemError(int index) {
                return TaskIntakeMessages.describeAll(form.errors(field, index));
            }

            @Override
            public String listError() {
                return TaskIntakeMessages.describeAll(
                        form.errors(field, TaskSpecificationValidation.Violation.NO_INDEX));
            }
        };
    }

    /** An ordered list of workspace-relative paths of the scope form. */
    static EditableList of(TaskScopeForm form, TaskScopeValidation.Field field) {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(field, "field");
        return new EditableList() {
            @Override
            public List<String> items() {
                return form.items(field);
            }

            @Override
            public void setItem(int index, String text) {
                form.setItem(field, index, text);
            }

            @Override
            public void addItem() {
                form.addItem(field);
            }

            @Override
            public void removeItem(int index) {
                form.removeItem(field, index);
            }

            @Override
            public String itemError(int index) {
                return TaskScopeMessages.describeAll(form.errors(field, index));
            }

            @Override
            public String listError() {
                return TaskScopeMessages.describeAll(form.errors(field, TaskScopeValidation.Violation.NO_INDEX));
            }
        };
    }
}
