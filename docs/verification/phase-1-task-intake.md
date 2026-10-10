# Phase 1 task intake verification

- Date: 2026-10-09
- Baseline `main` commit: `2e6c0d3` (change under verification is on branch `14-add-phase-1-task-review-and-confirmation-screen`)
- Platform: Linux x64
- JDK: OpenJDK 27
- Gradle: 9.8.0

Scope: the bounded intake flow from workspace selection through task and scope entry to the review and confirmation screen ([Issue 14](https://github.com/stevdrey/monada-forge/issues/14)). Confirmation is an in-memory state change only. It is not execution authorization, and no context collection or agent action exists in this phase.

## Results

| Check | Result |
| --- | --- |
| `./gradlew build` | Passed |
| Core tests (`:core:test`) | 131 tests, 0 failures, 0 skipped |
| Desktop tests (`:desktop:test`) | 106 tests, 0 failures, 0 skipped |
| Draft confirmation state transitions (`TaskDraftServiceTest`) | Passed: complete draft becomes `READY`; incomplete drafts are rejected unchanged; any change to workspace, task or scope (and clearing) withdraws confirmation; rejected input keeps it; re-applying equal data keeps it; confirming twice is idempotent |
| No side effects on confirm (`TaskDraftServiceTest`) | Passed: the workspace tree is identical before and after `confirm()`; `toString` leaks no task text |
| Review gate (`TaskReviewModelTest`) | Passed: all draft parts exposed in order for specific-path and whole-workspace scopes; missing or unvalidated workspace, task or scope blocks confirmation; a changed workspace blocks it; a confirmed draft with unvalidated form edits stays confirmed in core but blocks re-confirming |
| Review text (`TaskReviewMessagesTest`) | Passed: blockers are actionable; the notice states that confirmation authorizes nothing and that no agent has run |
| Review stylesheet classes (`StylesheetTest`) | Passed |
| Manual valid flow, whole-workspace scope (`:desktop:run`) | Observed in screenshots supplied by the maintainer: the review shows workspace, task and scope with the broad-scope warning and the no-authorization notice; "Confirm task" shows the ready state and disables itself. Filesystem, process and network activity were not inspected |
| Other manual flows | Not run (see below) |

## Manual desktop check still required

The JavaFX views are not covered by automated tests. Only the valid whole-workspace flow above was observed; the remaining flows below are still unchecked. Run `./gradlew :desktop:run` in a graphical session and check:

1. Valid flow: select a workspace, validate a task, validate a scope, open "Review task". Every part is shown, "Confirm task" is enabled, confirming shows the ready state, and no file, process or network activity occurs.
2. Edit/back flow: from the review use "Edit task" and "Edit scope". Entered data is preserved; changing anything and returning shows confirmation withdrawn.
3. Invalid-scope flow: with an invalid or edited scope, "Review task" stays disabled.
4. Workspace change: changing the workspace without keeping the task, or keeping it and not re-validating the scope, leaves confirmation unavailable.
5. Whole-workspace scope shows its warning on the review screen.
