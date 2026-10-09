package io.github.stevdrey.monadaforge.desktop;

import java.nio.file.Path;
import java.util.Objects;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Neutral placeholder hosted by the shell until task screens exist; lets the user change workspace. */
public final class WelcomeView extends VBox {
    public WelcomeView(Path workspace, Runnable onChangeWorkspace) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(onChangeWorkspace, "onChangeWorkspace");
        getStyleClass().add("view");

        var heading = new Label("Welcome");
        heading.getStyleClass().add("view-title");

        var workspaceLabel = new Label(workspace.toString());
        workspaceLabel.getStyleClass().add("workspace-path");
        workspaceLabel.setWrapText(true);

        var body = new Label("Task intake, agent connections and workflow execution "
                + "will arrive in future releases. No providers are connected.");
        body.getStyleClass().add("view-body");
        body.setWrapText(true);

        var change = new Button("Change workspace");
        change.setOnAction(event -> onChangeWorkspace.run());

        getChildren().addAll(heading, workspaceLabel, body, change);
    }
}
