package io.github.stevdrey.monadaforge.desktop;

/** Semantic feedback states, each mapped to a CSS style class in {@code forge.css}. */
public enum StatusKind {
    INFO("status-info"),
    SUCCESS("status-success"),
    WARNING("status-warning"),
    ERROR("status-error");

    private final String styleClass;

    StatusKind(String styleClass) {
        this.styleClass = styleClass;
    }

    String styleClass() {
        return styleClass;
    }
}
