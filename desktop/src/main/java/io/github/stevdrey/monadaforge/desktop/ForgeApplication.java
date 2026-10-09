package io.github.stevdrey.monadaforge.desktop;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Desktop entry point. Workflow execution will be introduced in later increments. */
public final class ForgeApplication extends Application {
    static final String PRODUCT_NAME = "Monada Forge";

    @Override
    public void start(Stage stage) {
        var shell = new ApplicationShell(PRODUCT_NAME, "A workspace for agent-assisted software delivery.");
        shell.setContent(new WelcomeView());
        shell.status().show(StatusKind.SUCCESS, "Project foundation ready");

        var scene = new Scene(shell, 880, 560);

        stage.setTitle(PRODUCT_NAME);
        stage.setMinWidth(520);
        stage.setMinHeight(360);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
