package io.github.stevdrey.monadaforge.desktop;

import javafx.scene.control.Label;

/** Shared handling of feedback labels that occupy no space while they have nothing to say. */
final class FeedbackLabels {
    private FeedbackLabels() {}

    /** Shows {@code text}, or hides the label completely when {@code text} is {@code null}. */
    static void show(Label label, String text) {
        boolean visible = text != null;
        label.setText(visible ? text : "");
        label.setVisible(visible);
        label.setManaged(visible);
    }
}
