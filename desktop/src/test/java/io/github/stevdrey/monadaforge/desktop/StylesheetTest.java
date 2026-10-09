package io.github.stevdrey.monadaforge.desktop;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Guards the contract between Java style-class names and the application stylesheet. */
class StylesheetTest {
    private static String stylesheet() throws IOException {
        try (var in = ApplicationShell.class.getResourceAsStream("forge.css")) {
            assertNotNull(in, "forge.css must sit next to ApplicationShell");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void definesEveryStatusKindClass() throws IOException {
        var css = stylesheet();
        for (var kind : StatusKind.values()) {
            assertTrue(css.contains("." + kind.styleClass()), "missing ." + kind.styleClass());
        }
    }

    @Test
    void definesWorkspaceSelectionClasses() throws IOException {
        var css = stylesheet();
        for (var styleClass : new String[] {".workspace-path", ".field-error", ".primary-button", ".primary-button:focused"}) {
            assertTrue(css.contains(styleClass), "missing " + styleClass);
        }
    }

    @Test
    void declaresPaletteOnShellInsteadOfSceneRoot() throws IOException {
        var css = stylesheet();
        assertTrue(css.contains("-forge-surface:"), "palette missing");
        assertTrue(!css.contains(".root"), "palette must not depend on the Scene root");
    }
}
