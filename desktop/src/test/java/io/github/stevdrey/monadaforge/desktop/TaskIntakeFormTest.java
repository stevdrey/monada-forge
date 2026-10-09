package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stevdrey.monadaforge.core.task.TaskDraftService;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Reason;
import io.github.stevdrey.monadaforge.core.task.TaskSpecificationValidation.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskIntakeFormTest {
    private final TaskDraftService service = new TaskDraftService();
    private final TaskIntakeForm form = new TaskIntakeForm(service::updateSpecification, service::clearSpecification);

    private void fillValid() {
        form.setTitle("Add export");
        form.setDescription("Let users export data.");
        form.setItem(Field.ACCEPTANCE_CRITERIA, 0, "Export produces a file");
    }

    @Test
    void startsIncompleteWithOneEmptyCriterion() {
        assertInstanceOf(TaskIntakeForm.Result.NotValidated.class, form.result());
        assertEquals(List.of(""), form.items(Field.ACCEPTANCE_CRITERIA));
        assertTrue(form.items(Field.CONSTRAINTS).isEmpty());
        assertTrue(form.items(Field.NON_GOALS).isEmpty());
        assertTrue(service.current().specification().isEmpty());
    }

    @Test
    void validInputIsAcceptedByCoreAndStoredInDraft() {
        fillValid();
        form.addItem(Field.CONSTRAINTS);
        form.setItem(Field.CONSTRAINTS, 0, "No new dependencies");
        form.addItem(Field.NON_GOALS);
        form.setItem(Field.NON_GOALS, 0, "No cloud upload");

        var valid = assertInstanceOf(TaskIntakeForm.Result.Valid.class, form.submit());

        assertEquals("Add export", valid.specification().title());
        assertEquals(List.of("No new dependencies"), valid.specification().constraints());
        assertEquals(List.of("No cloud upload"), valid.specification().nonGoals());
        assertEquals(valid.specification(), service.current().specification().orElseThrow());
    }

    @Test
    void emptyFormReportsMissingAndBlankValuesPerField() {
        var invalid = assertInstanceOf(TaskIntakeForm.Result.Invalid.class, form.submit());

        assertTrue(invalid.violations().contains(new Violation(Field.TITLE, Violation.NO_INDEX, Reason.BLANK)));
        assertTrue(invalid.violations().contains(new Violation(Field.DESCRIPTION, Violation.NO_INDEX, Reason.BLANK)));
        assertTrue(invalid.violations().contains(new Violation(Field.ACCEPTANCE_CRITERIA, 0, Reason.BLANK)));
        assertTrue(service.current().specification().isEmpty());
    }

    @Test
    void removingAllCriteriaIsReportedForTheWholeList() {
        fillValid();
        form.removeItem(Field.ACCEPTANCE_CRITERIA, 0);

        form.submit();

        assertEquals(
                List.of(new Violation(Field.ACCEPTANCE_CRITERIA, Violation.NO_INDEX, Reason.MISSING)), form.errors());
    }

    @Test
    void criteriaStayDistinctAndOrdered() {
        fillValid();
        form.addItem(Field.ACCEPTANCE_CRITERIA);
        form.setItem(Field.ACCEPTANCE_CRITERIA, 1, "Second");
        form.addItem(Field.ACCEPTANCE_CRITERIA);
        form.setItem(Field.ACCEPTANCE_CRITERIA, 2, "Third");

        assertTrue(form.moveUp(Field.ACCEPTANCE_CRITERIA, 2));
        assertFalse(form.moveUp(Field.ACCEPTANCE_CRITERIA, 0));
        assertFalse(form.moveDown(Field.ACCEPTANCE_CRITERIA, 2));

        var valid = assertInstanceOf(TaskIntakeForm.Result.Valid.class, form.submit());
        assertEquals(List.of("Export produces a file", "Third", "Second"), valid.specification().acceptanceCriteria());
    }

    @Test
    void removingAnItemKeepsTheOthers() {
        form.addItem(Field.CONSTRAINTS);
        form.addItem(Field.CONSTRAINTS);
        form.setItem(Field.CONSTRAINTS, 0, "a");
        form.setItem(Field.CONSTRAINTS, 1, "b");

        form.removeItem(Field.CONSTRAINTS, 0);

        assertEquals(List.of("b"), form.items(Field.CONSTRAINTS));
    }

    @Test
    void editingOneInvalidFieldKeepsOtherContentAndOtherErrors() {
        form.setDescription("Keep me");
        form.addItem(Field.NON_GOALS);
        form.setItem(Field.NON_GOALS, 0, "Keep me too");
        form.submit(); // title and criterion are blank

        form.setTitle("Now valid");

        assertEquals("Keep me", form.description());
        assertEquals(List.of("Keep me too"), form.items(Field.NON_GOALS));
        assertTrue(form.errors(Field.TITLE, Violation.NO_INDEX).isEmpty());
        assertFalse(form.errors(Field.ACCEPTANCE_CRITERIA, 0).isEmpty());
        assertInstanceOf(TaskIntakeForm.Result.NotValidated.class, form.result());
    }

    @Test
    void recoversFromErrorsAfterFixingEveryField() {
        form.submit();
        fillValid();

        assertInstanceOf(TaskIntakeForm.Result.Valid.class, form.submit());
        assertTrue(form.errors().isEmpty());
    }

    @Test
    void editAfterSuccessInvalidatesTheResult() {
        fillValid();
        form.submit();

        form.setTitle("Changed");

        assertInstanceOf(TaskIntakeForm.Result.NotValidated.class, form.result());
    }

    @Test
    void structuralChangeClearsOnlyThatListsPositionalErrors() {
        form.addItem(Field.CONSTRAINTS);
        form.submit();
        assertFalse(form.errors(Field.CONSTRAINTS, 0).isEmpty());

        form.addItem(Field.CONSTRAINTS);

        assertTrue(form.errors(Field.CONSTRAINTS, 0).isEmpty());
        assertFalse(form.errors(Field.TITLE, Violation.NO_INDEX).isEmpty());
    }

    @Test
    void titleWithLineBreakIsRejectedByCoreRules() {
        fillValid();
        form.setTitle("one\ntwo");

        form.submit();

        assertEquals(
                List.of(new Violation(Field.TITLE, Violation.NO_INDEX, Reason.CONTAINS_CONTROL_CHARACTERS)),
                form.errors());
    }

    @Test
    void scalarFieldsAreNotListFields() {
        assertThrows(IllegalArgumentException.class, () -> form.items(Field.TITLE));
    }

    @Test
    void editingAnAcceptedFormDropsTheStoredSpecification() {
        fillValid();
        form.submit();
        assertTrue(service.current().specification().isPresent());

        form.setTitle("Changed");

        assertTrue(service.current().specification().isEmpty());
    }

    @Test
    void clearingAnAcceptedFormDropsTheStoredSpecification() {
        fillValid();
        form.submit();

        form.clear();

        assertTrue(service.current().specification().isEmpty());
    }

    @Test
    void clearReturnsToTheEmptyIncompleteForm() {
        fillValid();
        form.addItem(Field.NON_GOALS);
        form.submit();
        assertTrue(form.hasContent());

        form.clear();

        assertFalse(form.hasContent());
        assertEquals("", form.title());
        assertEquals(List.of(""), form.items(Field.ACCEPTANCE_CRITERIA));
        assertTrue(form.items(Field.NON_GOALS).isEmpty());
        assertTrue(form.errors().isEmpty());
        assertInstanceOf(TaskIntakeForm.Result.NotValidated.class, form.result());
    }
}
