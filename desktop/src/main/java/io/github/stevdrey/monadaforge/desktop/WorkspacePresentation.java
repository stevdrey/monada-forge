package io.github.stevdrey.monadaforge.desktop;

import java.util.Objects;

/** Toolkit-independent description of what the workspace selection view shows for a state. */
record WorkspacePresentation(
        String requirementText,
        String pathText,
        String errorText,
        StatusKind statusKind,
        String statusText,
        boolean continueEnabled) {

    static WorkspacePresentation of(WorkspaceSelection.State state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case WorkspaceSelection.State.Required _ -> new WorkspacePresentation(
                    "A workspace is required before you can continue. "
                            + "Choose the local folder Monada Forge will work in.",
                    null,
                    null,
                    StatusKind.INFO,
                    "Select a workspace to begin",
                    false);
            case WorkspaceSelection.State.Validating _ -> new WorkspacePresentation(
                    "Checking the selected folder…", null, null, StatusKind.INFO, "Checking the selected folder…", false);
            case WorkspaceSelection.State.Selected selected -> new WorkspacePresentation(
                    "Workspace selected. You can choose a different folder at any time.",
                    selected.root().path().toString(),
                    null,
                    StatusKind.SUCCESS,
                    "Workspace: " + selected.root().path(),
                    true);
            case WorkspaceSelection.State.Invalid invalid -> new WorkspacePresentation(
                    "A valid workspace is required before you can continue.",
                    invalid.requested().toString(),
                    invalid.message(),
                    StatusKind.ERROR,
                    invalid.message(),
                    false);
        };
    }
}
