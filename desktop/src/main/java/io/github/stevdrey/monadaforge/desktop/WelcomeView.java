package io.github.stevdrey.monadaforge.desktop;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Neutral placeholder hosted by the shell until workspace and task screens exist. */
public final class WelcomeView extends VBox {
    public WelcomeView() {
        getStyleClass().add("view");

        var heading = new Label("Welcome");
        heading.getStyleClass().add("view-title");

        var body = new Label("Task intake, agent connections and workflow execution "
                + "will arrive in future releases. No providers are connected.");
        body.getStyleClass().add("view-body");
        body.setWrapText(true);

        getChildren().addAll(heading, body);
    }
}
