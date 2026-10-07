package io.github.stevdrey.monadaforge.core.workspace;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A user-selected local directory validated as the filesystem boundary of a workspace.
 *
 * <p>Instances exist only for an existing, readable directory and are identified by its canonical
 * (real) path, so equivalent spellings and symlinks to the same directory yield equal roots.
 * Holding a root grants no access to its descendants; it only names the selected boundary.
 */
public final class WorkspaceRoot {

    private final Path path;

    private WorkspaceRoot(Path path) {
        this.path = path;
    }

    /**
     * Validates {@code candidate} as a workspace root using read-only metadata access.
     *
     * @return {@link WorkspaceRootValidation.Accepted} or a {@link WorkspaceRootValidation.Rejected}
     *     describing why the input is not usable
     * @throws IOException on unexpected I/O failures, as opposed to invalid input
     */
    public static WorkspaceRootValidation validate(Path candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        Path real;
        try {
            real = candidate.toRealPath();
        } catch (NoSuchFileException e) {
            return reject(candidate, WorkspaceRootValidation.Reason.NOT_FOUND);
        } catch (AccessDeniedException e) {
            return reject(candidate, WorkspaceRootValidation.Reason.NOT_READABLE);
        }
        if (!Files.isDirectory(real)) {
            return reject(candidate, WorkspaceRootValidation.Reason.NOT_A_DIRECTORY);
        }
        if (!Files.isReadable(real)) {
            return reject(candidate, WorkspaceRootValidation.Reason.NOT_READABLE);
        }
        return new WorkspaceRootValidation.Accepted(new WorkspaceRoot(real));
    }

    private static WorkspaceRootValidation reject(Path candidate, WorkspaceRootValidation.Reason reason) {
        return new WorkspaceRootValidation.Rejected(candidate, reason);
    }

    /** The canonical absolute path of this root. */
    public Path path() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkspaceRoot that && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return "WorkspaceRoot[" + path + "]";
    }
}
