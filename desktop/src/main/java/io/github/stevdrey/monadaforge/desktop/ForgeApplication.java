package io.github.stevdrey.monadaforge.desktop;

import java.util.Objects;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Desktop entry point. Workflow execution will be introduced in later increments. */
public final class ForgeApplication extends Application {
    @Override
    public void start(Stage stage) {
        var title = new Label("Monada Forge");
        title.getStyleClass().add("title");

        var subtitle = new Label("A workspace for agent-assisted software delivery.");
        subtitle.setWrapText(true);

        var status = new Label("Project foundation ready");
        status.getStyleClass().add("status");

        var description = new Label("Task intake, agent connections and workflow execution "
                + "will arrive in future releases. No providers are connected.");
        description.setWrapText(true);

        var root = new VBox(16, title, subtitle, status, description);
        root.setPadding(new Insets(40));
        var scene = new Scene(root, 880, 560);
        var stylesheet = Objects.requireNonNull(
                ForgeApplication.class.getResource("forge.css"), "Missing application stylesheet");
        scene.getStylesheets().add(stylesheet.toExternalForm());

        stage.setTitle("Monada Forge");
        stage.setMinWidth(520);
        stage.setMinHeight(360);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
