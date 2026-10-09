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

    /** Leaves {@code styleClasses} with this kind's status class and no other status class. */
    void applyTo(List<String> styleClasses) {
        for (var other : ALL_STYLE_CLASSES) {
            if (!other.equals(styleClass)) {
                styleClasses.remove(other);
            }
        }
        if (!styleClasses.contains(styleClass)) {
            styleClasses.add(styleClass);
        }
    }
}
