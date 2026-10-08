package io.github.stevdrey.monadaforge.desktop;

import java.util.Arrays;
import java.util.List;

/** Semantic feedback states, each mapped to a CSS style class in {@code forge.css}. */
public enum StatusKind {
    INFO("status-info"),
    SUCCESS("status-success"),
    WARNING("status-warning"),
    ERROR("status-error");

    private static final List<String> ALL_STYLE_CLASSES =
            Arrays.stream(values()).map(StatusKind::styleClass).toList();

    private final String styleClass;

    StatusKind(String styleClass) {
        this.styleClass = styleClass;
    }

    String styleClass() {
        return styleClass;
    }

    /** Removes every status style class from {@code styleClasses} and adds this kind's class. */
    void applyTo(List<String> styleClasses) {
        styleClasses.removeAll(ALL_STYLE_CLASSES);
        styleClasses.add(styleClass);
    }
}
