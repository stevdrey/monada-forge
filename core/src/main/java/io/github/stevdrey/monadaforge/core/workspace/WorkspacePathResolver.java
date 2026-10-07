package io.github.stevdrey.monadaforge.core.workspace;

import io.github.stevdrey.monadaforge.core.workspace.WorkspacePathResolution.Reason;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves untrusted workspace-relative paths to canonical paths proven to stay inside a {@link
 * WorkspaceRoot}.
 *
 * <p>Resolution fails closed: absolute paths and any {@code ..} segment are rejected outright, and
 * containment is checked component-wise on canonical paths after the filesystem has followed every
 * symbolic link. Only metadata is accessed; nothing is read, created or modified.
 */
public final class WorkspacePathResolver {

    private WorkspacePathResolver() {}

    /**
     * Resolves {@code candidate} relative to {@code root}.
     *
     * <p>An existing entry resolves to its real path. A missing leaf resolves when its parent
     * directory exists inside the root and the leaf is not a dangling symbolic link.
     *
     * @return {@link WorkspacePathResolution.Resolved} or a {@link WorkspacePathResolution.Rejected}
     *     describing why containment could not be established
     * @throws IOException on unexpected I/O failures, as opposed to invalid or escaping input
     */
    public static WorkspacePathResolution resolve(WorkspaceRoot root, String candidate) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.isEmpty()) {
            return reject(candidate, Reason.INVALID_PATH);
        }
        Path relative;
        try {
            relative = root.path().getFileSystem().getPath(candidate);
        } catch (InvalidPathException e) {
            return reject(candidate, Reason.INVALID_PATH);
        }
        if (relative.isAbsolute() || relative.getRoot() != null) {
            return reject(candidate, Reason.ABSOLUTE);
        }
        for (Path name : relative) {
            if (name.toString().equals("..")) {
                return reject(candidate, Reason.TRAVERSAL);
            }
        }
        Path target = root.path().resolve(relative.normalize());
        try {
            return contained(root, candidate, target.toRealPath(), true);
        } catch (NoSuchFileException e) {
            return resolveMissingLeaf(root, candidate, target);
        } catch (AccessDeniedException e) {
            return reject(candidate, Reason.NOT_ACCESSIBLE);
        } catch (FileSystemException e) {
            return reject(candidate, Reason.UNRESOLVABLE);
        }
    }

    private static WorkspacePathResolution resolveMissingLeaf(WorkspaceRoot root, String candidate, Path target)
            throws IOException {
        // The root itself vanished, or the leaf is a dangling link whose target cannot be proven.
        if (target.equals(root.path()) || Files.isSymbolicLink(target)) {
            return reject(candidate, Reason.UNRESOLVABLE);
        }
        Path realParent;
        try {
            realParent = target.getParent().toRealPath();
        } catch (NoSuchFileException e) {
            return reject(candidate, Reason.PARENT_NOT_FOUND);
        } catch (AccessDeniedException e) {
            return reject(candidate, Reason.NOT_ACCESSIBLE);
        } catch (FileSystemException e) {
            return reject(candidate, Reason.UNRESOLVABLE);
        }
        if (!Files.isDirectory(realParent, LinkOption.NOFOLLOW_LINKS)) {
            return reject(candidate, Reason.UNRESOLVABLE);
        }
        return contained(root, candidate, realParent.resolve(target.getFileName()), false);
    }

    private static WorkspacePathResolution contained(
            WorkspaceRoot root, String candidate, Path real, boolean existing) {
        if (!real.startsWith(root.path())) {
            return reject(candidate, Reason.ESCAPES_WORKSPACE);
        }
        return new WorkspacePathResolution.Resolved(root, real, existing);
    }

    private static WorkspacePathResolution reject(String candidate, Reason reason) {
        return new WorkspacePathResolution.Rejected(candidate, reason);
    }
}
