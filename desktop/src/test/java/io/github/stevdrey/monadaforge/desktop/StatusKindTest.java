package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the toolkit-independent style-class switching used by {@link StatusBar}. */
class StatusKindTest {
    @Test
    void applyToLeavesExactlyTheRequestedStatusClassAndKeepsOthers() {
        for (var kind : StatusKind.values()) {
            var classes = new ArrayList<>(List.of("app-status-bar"));
            for (var previous : StatusKind.values()) {
                previous.applyTo(classes);
                kind.applyTo(classes);
                assertEquals(List.of("app-status-bar", kind.styleClass()), classes,
                        previous + " -> " + kind);
            }
        }
    }

    @Test
    void styleClassesAreDistinctPerKind() {
        var distinct = Arrays.stream(StatusKind.values()).map(StatusKind::styleClass).distinct().count();
        assertEquals(StatusKind.values().length, distinct);
    }
}
