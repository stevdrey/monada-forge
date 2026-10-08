package io.github.stevdrey.monadaforge.desktop;

import java.util.Arrays;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** Status and feedback region; presentation of each state is defined by CSS. */
public final class StatusBar extends HBox {
    private final Label message = new Label();

    public StatusBar() {
        getStyleClass().add("app-status-bar");
        message.getStyleClass().add("status-message");
        message.setWrapText(true);
        getChildren().add(message);
    }

    /** Shows {@code text} using the style class that corresponds to {@code kind}. */
    public void show(StatusKind kind, String text) {
        Objects.requireNonNull(kind, "kind");
        message.setText(Objects.requireNonNull(text, "text"));
        Arrays.stream(StatusKind.values()).forEach(k -> getStyleClass().remove(k.styleClass()));
        getStyleClass().add(kind.styleClass());
    }
}
