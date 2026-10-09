package io.github.stevdrey.monadaforge.desktop;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Reusable window layout: product header, primary content region and status region. */
public final class ApplicationShell extends BorderPane {
    private final StackPane content = new StackPane();
    private final ScrollPane scroll = new ScrollPane(content);
    private final StatusBar status = new StatusBar();

    public ApplicationShell(String productName, String tagline) {
        getStyleClass().add("app-shell");
        getStylesheets().add(Objects.requireNonNull(
                ApplicationShell.class.getResource("forge.css"), "Missing application stylesheet")
                .toExternalForm());

        var title = new Label(Objects.requireNonNull(productName, "productName"));
        title.getStyleClass().add("app-title");
        var subtitle = new Label(Objects.requireNonNull(tagline, "tagline"));
        subtitle.getStyleClass().add("app-subtitle");
        subtitle.setWrapText(true);
        var header = new VBox(title, subtitle);
        header.getStyleClass().add("app-header");

        content.getStyleClass().add("app-content");
        scroll.getStyleClass().add("app-content-scroll");
        scroll.setFitToWidth(true);

        setTop(header);
        setCenter(scroll);
        setBottom(status);
    }

    /** Replaces the view hosted in the primary content region. */
    public void setContent(Node view) {
        content.getChildren().setAll(Objects.requireNonNull(view, "view"));
        scroll.setHvalue(0);
        scroll.setVvalue(0);
    }

    public StatusBar status() {
        return status;
    }
}
