package io.github.stevdrey.monadaforge.desktop;

import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Desktop entry point. Workflow execution will be introduced in later increments. */
public final class ForgeApplication extends Application {
    @Override
    public void start(Stage stage) {
        var shell = new ApplicationShell("Monada Forge", "A workspace for agent-assisted software delivery.");
        shell.setContent(new WelcomeView());
        shell.status().show(StatusKind.SUCCESS, "Project foundation ready");

        var scene = new Scene(shell, 880, 560);
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
