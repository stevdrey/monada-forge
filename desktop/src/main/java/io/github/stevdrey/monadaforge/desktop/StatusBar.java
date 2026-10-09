package io.github.stevdrey.monadaforge.desktop;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Status and feedback region; presentation of each state is defined by CSS. */
public final class StatusBar extends HBox {
    private final Label message = new Label();

    public StatusBar() {
        getStyleClass().add("app-status-bar");
        message.getStyleClass().add("status-message");
        message.setWrapText(true);
        message.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(message, Priority.ALWAYS);
        getChildren().add(message);
        // Hidden until the first show(): an empty colored strip carries no feedback.
        setVisible(false);
        setManaged(false);
    }

    /** Shows {@code text} using the style class that corresponds to {@code kind}. */
    public void show(StatusKind kind, String text) {
        Objects.requireNonNull(kind, "kind");
        message.setText(Objects.requireNonNull(text, "text"));
        kind.applyTo(getStyleClass());
        setVisible(true);
        setManaged(true);
    }
}
