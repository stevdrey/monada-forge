package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.stevdrey.monadaforge.core.task.TaskDraftService;
import io.github.stevdrey.monadaforge.core.task.TaskScope;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Field;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Reason;
import io.github.stevdrey.monadaforge.core.task.TaskScopeValidation.Violation;
import io.github.stevdrey.monadaforge.core.workspace.WorkspaceRootValidation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskScopeFormTest {
    @TempDir
    Path temp;

    private Path ws;
    private TaskDraftService service;
    private TaskScopeForm form;

    @BeforeEach
    void setUp() throws IOException {
        ws = Files.createDirectory(temp.resolve("ws"));
        Files.createDirectories(ws.resolve("src/main"));
        Files.createDirectories(ws.resolve("docs"));
        Files.createDirectories(temp.resolve("outside"));
        service = new TaskDraftService();
        assertInstanceOf(WorkspaceRootValidation.Accepted.class, service.selectWorkspace(ws));
        form = new TaskScopeForm(
                (mode, allowed, excluded, stillWanted) -> mode == TaskScopeForm.Mode.ENTIRE_WORKSPACE
                        ? service.selectEntireWorkspace(excluded, stillWanted)
                        : service.selectPaths(allowed, excluded, stillWanted),
                service::clearScope);
    }

    private void allow(String... paths) {
        form.setMode(TaskScopeForm.Mode.SPECIFIC_PATHS);
        for (String path : paths) {
            form.addItem(Field.ALLOWED);
            form.setItem(Field.ALLOWED, form.items(Field.ALLOWED).size() - 1, path);
        }
    }

    private List<Violation> invalid() {
        return assertInstanceOf(TaskScopeForm.Result.Invalid.class, form.submit()).violations();
    }

    @Test
    void startsWithoutAnyChoice() {
        assertEquals(TaskScopeForm.Mode.NOT_CHOSEN, form.mode());
        assertInstanceOf(TaskScopeForm.Result.NotValidated.class, form.result());
        assertFalse(form.hasContent());
    }

    @Test
    void validatingWithoutAModeIsRefusedAndNeverReadAsWholeWorkspace() {
        assertInstanceOf(TaskScopeForm.Result.ModeRequired.class, form.submit());
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void emptyAllowedListIsRejectedByCoreInsteadOfMeaningTheWholeWorkspace() {
        form.setMode(TaskScopeForm.Mode.SPECIFIC_PATHS);

        assertEquals(List.of(new Violation(Field.ALLOWED, Violation.NO_INDEX, Reason.EMPTY_ALLOWED)), invalid());
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void nestedRelativePathIsAcceptedByCoreAndStoredInDraft() {
        allow("src/main");

        var valid = assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());

        assertInstanceOf(TaskScope.Paths.class, valid.scope());
        assertEquals(valid.scope(), service.current().scope());
    }

    @Test
    void isValidOnlyWhileTheAcceptedScopeIsUnedited() {
        assertFalse(form.isValid());
        allow("src/main");
        assertFalse(form.isValid());

        form.submit();
        assertTrue(form.isValid());

        form.setItem(Field.ALLOWED, 0, "docs");
        assertFalse(form.isValid());
    }

    @Test
    void entireWorkspaceIsAcceptedOnlyAfterTheExplicitChoice() {
        form.setMode(TaskScopeForm.Mode.ENTIRE_WORKSPACE);
        form.addItem(Field.EXCLUDED);
        form.setItem(Field.EXCLUDED, 0, "docs");

        var valid = assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());

        assertInstanceOf(TaskScope.EntireWorkspace.class, valid.scope());
    }

    @Test
    void traversalAbsoluteAndOutsidePathsAreRejectedPerEntry() throws IOException {
        allow("src/main", "../outside", temp.resolve("outside").toString(), "src/../../outside");

        var violations = invalid();

        assertTrue(violations.contains(new Violation(Field.ALLOWED, 1, Reason.TRAVERSAL)));
        assertTrue(violations.contains(new Violation(Field.ALLOWED, 2, Reason.ABSOLUTE)));
        assertTrue(violations.contains(new Violation(Field.ALLOWED, 3, Reason.TRAVERSAL)));
        assertFalse(violations.stream().anyMatch(v -> v.index() == 0));
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void symbolicLinkEscapingTheWorkspaceIsRejected() throws IOException {
        try {
            Files.createSymbolicLink(ws.resolve("link"), temp.resolve("outside"));
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links not supported here");
        }
        allow("link");

        assertEquals(List.of(new Violation(Field.ALLOWED, 0, Reason.ESCAPES_WORKSPACE)), invalid());
    }

    @Test
    void exclusionInsideAnAllowedPathIsAValidCarveOut() {
        allow("src");
        form.addItem(Field.EXCLUDED);
        form.setItem(Field.EXCLUDED, 0, "src/main");

        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());
    }

    @Test
    void removingAnInvalidEntryKeepsTheOthersAndAllowsValidation() {
        allow("src", "../outside");
        invalid();

        form.removeItem(Field.ALLOWED, 1);

        assertEquals(List.of("src"), form.items(Field.ALLOWED));
        assertTrue(form.errors().isEmpty());
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());
    }

    @Test
    void validationNeverChangesTheEnteredValues() {
        allow("../outside", "src");

        invalid();

        assertEquals(List.of("../outside", "src"), form.items(Field.ALLOWED));
    }

    @Test
    void editingClearsOnlyThatEntrysErrorsAndDropsTheAcceptedScope() {
        allow("src");
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());
        assertInstanceOf(TaskScope.Paths.class, service.current().scope());

        form.setItem(Field.ALLOWED, 0, "docs");

        assertInstanceOf(TaskScopeForm.Result.NotValidated.class, form.result());
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void switchingModeKeepsTypedEntriesButDropsTheAcceptedScope() {
        allow("src");
        form.submit();

        form.setMode(TaskScopeForm.Mode.ENTIRE_WORKSPACE);

        assertEquals(List.of("src"), form.items(Field.ALLOWED));
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void staleOutcomeIsDroppedAndNeverApplied() {
        allow("src");
        var request = form.request();
        var outcome = form.validate(request);
        assertInstanceOf(TaskScopeValidation.Accepted.class, outcome);

        form.setItem(Field.ALLOWED, 0, "docs"); // edited while validation was running
        var result = form.complete(request, outcome);

        assertInstanceOf(TaskScopeForm.Result.NotValidated.class, result);
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void validatorFailureIsReportedWithoutChangingEntries() {
        var failing = new TaskScopeForm(
                (mode, allowed, excluded, stillWanted) -> {
                    throw new IOException("boom");
                },
                () -> {});
        failing.setMode(TaskScopeForm.Mode.ENTIRE_WORKSPACE);

        assertInstanceOf(TaskScopeForm.Result.Failed.class, failing.submit());
        assertEquals(TaskScopeForm.Mode.ENTIRE_WORKSPACE, failing.mode());
    }

    @Test
    void changingToAnotherWorkspaceUnsetsTheDraftScopeAndRequiresRevalidationKeepingText() throws IOException {
        allow("src");
        form.submit();
        Path other = Files.createDirectory(temp.resolve("other"));

        assertInstanceOf(WorkspaceRootValidation.Accepted.class, service.selectWorkspace(other));
        form.workspaceChanged(true);

        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
        assertInstanceOf(TaskScopeForm.Result.NotValidated.class, form.result());
        assertTrue(form.needsRevalidation());
        assertEquals(List.of("src"), form.items(Field.ALLOWED));
        // "src" does not exist in the new root, so revalidation reports it instead of trusting the old result.
        assertEquals(List.of(new Violation(Field.ALLOWED, 0, Reason.NOT_FOUND)), invalid());
        assertFalse(form.needsRevalidation());
    }

    @Test
    void discardingOnWorkspaceChangeClearsTheForm() {
        allow("src");
        form.submit();

        form.workspaceChanged(false);

        assertEquals(TaskScopeForm.Mode.NOT_CHOSEN, form.mode());
        assertTrue(form.items(Field.ALLOWED).isEmpty());
        assertFalse(form.needsRevalidation());
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void editingEitherListClearsAContradictionReportedOnTheOther() {
        allow("src");
        form.addItem(Field.EXCLUDED);
        form.setItem(Field.EXCLUDED, 0, "src");
        assertEquals(
                List.of(new Violation(Field.ALLOWED, 0, Reason.ALLOWED_AND_EXCLUDED_CONTRADICT)), invalid());

        form.setItem(Field.EXCLUDED, 0, "src/main");

        assertTrue(form.errors(Field.ALLOWED, 0).isEmpty());
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());
    }

    @Test
    void structuralChangeClearsAContradictionOnTheOtherList() {
        allow("src");
        form.addItem(Field.EXCLUDED);
        form.setItem(Field.EXCLUDED, 0, "src");
        invalid();

        form.removeItem(Field.EXCLUDED, 0);

        assertTrue(form.errors().isEmpty());
    }

    @Test
    void revalidationRejectedLaterWithdrawsThePreviouslyAcceptedScope() throws IOException {
        allow("docs");
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());
        assertInstanceOf(TaskScope.Paths.class, service.current().scope());
        Files.delete(ws.resolve("docs"));

        assertEquals(List.of(new Violation(Field.ALLOWED, 0, Reason.NOT_FOUND)), invalid());

        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void failedRevalidationWithdrawsThePreviouslyAcceptedScope() {
        var withdrawn = new boolean[1];
        var flaky = new TaskScopeForm(
                (mode, allowed, excluded, stillWanted) -> {
                    if (withdrawn[0]) {
                        throw new IOException("boom");
                    }
                    return service.selectEntireWorkspace(excluded, stillWanted);
                },
                service::clearScope);
        flaky.setMode(TaskScopeForm.Mode.ENTIRE_WORKSPACE);
        assertInstanceOf(TaskScopeForm.Result.Valid.class, flaky.submit());
        withdrawn[0] = true;

        assertInstanceOf(TaskScopeForm.Result.Failed.class, flaky.submit());

        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void editingAPeerClearsAStaleDuplicateError() {
        allow("src", "src");
        assertEquals(List.of(new Violation(Field.ALLOWED, 1, Reason.DUPLICATE)), invalid());

        form.setItem(Field.ALLOWED, 0, "docs");

        assertTrue(form.errors().isEmpty());
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());
    }

    @Test
    void workspaceChangeWithNothingEnteredDoesNotAskForRevalidation() {
        form.workspaceChanged(true);

        assertFalse(form.needsRevalidation());
        assertEquals(TaskScopeForm.Mode.NOT_CHOSEN, form.mode());
    }

    @Test
    void editMadeWhileValidatingStopsTheRequestFromReinstallingItsScope() {
        allow("src");
        var request = form.request();

        form.setItem(Field.ALLOWED, 0, "docs"); // edit before the background validation applies its result
        assertFalse(form.isCurrent(request));
        var outcome = form.validate(request);

        assertNull(outcome); // the service refused the obsolete request instead of applying it
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }

    @Test
    void everyEditWithdrawsTheDraftScopeEvenWhenNothingWasAccepted() {
        var withdrawals = new int[1];
        var counting = new TaskScopeForm((mode, allowed, excluded, stillWanted) -> null, () -> withdrawals[0]++);

        counting.setMode(TaskScopeForm.Mode.SPECIFIC_PATHS);
        counting.addItem(Field.ALLOWED);

        assertEquals(2, withdrawals[0]);
    }

    @Test
    void completingAnObsoleteRequestLeavesANewerAcceptedScopeAlone() {
        allow("src");
        var old = form.request();
        var oldOutcome = form.validate(old);
        form.setItem(Field.ALLOWED, 0, "docs");
        assertInstanceOf(TaskScopeForm.Result.Valid.class, form.submit());
        var accepted = service.current().scope();

        var result = form.complete(old, oldOutcome);

        assertInstanceOf(TaskScopeForm.Result.Valid.class, result);
        assertEquals(accepted, service.current().scope());
    }

    @Test
    void staleRequestFromTheServiceIsDroppedQuietly() {
        var stale = new TaskScopeForm(
                (mode, allowed, excluded, stillWanted) -> service.selectEntireWorkspace(excluded, () -> false),
                () -> {});
        stale.setMode(TaskScopeForm.Mode.ENTIRE_WORKSPACE);

        assertNull(stale.validate(stale.request()));
        assertInstanceOf(TaskScope.Unset.class, service.current().scope());
    }
}
