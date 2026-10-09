package io.github.stevdrey.monadaforge.desktop;

import javafx.event.Event;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Makes Tab and Shift+Tab move focus out of a {@link TextArea} instead of typing a tab character. */
final class TabTraversal {
    private TabTraversal() {}

    static void install(TextArea area) {
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.TAB && !event.isControlDown() && !event.isAltDown()
                    && !event.isMetaDown()) {
                event.consume();
                // The text area's skin maps Ctrl+Tab to focus traversal.
                Event.fireEvent(area, new KeyEvent(
                        KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, event.isShiftDown(), true, false, false));
            }
        });
    }
}
