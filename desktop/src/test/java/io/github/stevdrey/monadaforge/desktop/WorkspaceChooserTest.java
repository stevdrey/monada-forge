package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceChooserTest {
    private final File picked = new File("/picked");

    @Test
    void usesTheRememberedDirectoryWhenThePlatformAcceptsIt() {
        List<File> initials = new ArrayList<>();
        var result = WorkspaceChooser.pick(Path.of("/last"), initial -> {
            initials.add(initial);
            return picked;
        });
        assertEquals(picked, result);
        assertEquals(List.of(new File("/last")), initials);
    }

    @Test
    void retriesWithTheDefaultWhenTheRememberedDirectoryIsRejected() {
        List<File> initials = new ArrayList<>();
        var result = WorkspaceChooser.pick(Path.of("/gone"), initial -> {
            initials.add(initial);
            if (initial != null) {
                throw new IllegalArgumentException("Folder not found");
            }
            return picked;
        });
        assertEquals(picked, result);
        assertEquals(2, initials.size());
        assertNull(initials.get(1));
    }

    @Test
    void startsWithTheDefaultWhenNothingIsRemembered() {
        List<File> initials = new ArrayList<>();
        WorkspaceChooser.pick(null, initial -> {
            initials.add(initial);
            return null;
        });
        assertEquals(1, initials.size());
        assertNull(initials.get(0));
    }

    @Test
    void cancellingReturnsNullWithoutRetrying() {
        int[] calls = {0};
        var result = WorkspaceChooser.pick(Path.of("/last"), initial -> {
            calls[0]++;
            return null;
        });
        assertNull(result);
        assertEquals(1, calls[0]);
    }

    @Test
    void otherFailuresPropagate() {
        assertThrows(IllegalStateException.class, () -> WorkspaceChooser.pick(Path.of("/last"), initial -> {
            throw new IllegalStateException("boom");
        }));
    }
}
