package io.github.stevdrey.monadaforge.desktop;

import java.io.File;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

/** Opens a directory dialog without letting a stale initial directory prevent selection. */
final class WorkspaceChooser {
    private WorkspaceChooser() {}

    /**
     * Shows the dialog starting at {@code lastDirectory}; if the platform rejects that folder
     * (renamed, deleted or unmounted since), retries once with the picker's default.
     *
     * <p>The remembered directory is deliberately not probed here: this runs on the JavaFX thread
     * and a stalled file system must not freeze it.
     *
     * @param dialog shows the dialog with the given initial directory ({@code null} for the default)
     *     and returns the picked directory, or {@code null} when cancelled
     * @return the picked directory, or {@code null} when cancelled
     */
    static File pick(Path lastDirectory, UnaryOperator<File> dialog) {
        if (lastDirectory != null) {
            try {
                return dialog.apply(lastDirectory.toFile());
            } catch (IllegalArgumentException staleDirectory) {
                // Fall through to the default location.
            }
        }
        return dialog.apply(null);
    }
}
