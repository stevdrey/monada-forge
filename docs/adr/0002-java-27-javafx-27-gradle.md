# ADR 0002: Java 27, JavaFX 27 and Gradle 9.8.0

- Status: Accepted
- Date: 2026-09-27

## Context

Use Java 27 if the latest stable Gradle supports it; otherwise fall back to Java 26. The project is a JavaFX desktop application.

## Decision

Pin Java toolchains to 27, JavaFX to 27, and the Gradle Wrapper to 9.8.0. Use stable language features; no preview flags. The catalog centralizes Java and dependency/plugin versions. Kotlin DSL describes the build; application source remains Java.

Gradle 9.8.0 was released on September 24, 2026 and lists Java 27 support for both toolchains and running Gradle. JavaFX 27 requires JDK 25 or newer, so this combination does not require the Java 26 fallback.

Use the official OpenJFX Gradle plugin 0.1.0 for native dependency selection. Enable native access only for `javafx.graphics` in application launch scripts.

## Consequences

Developers and CI use JDK 27. Build from each target platform rather than copying a Linux JavaFX distribution to another OS. The initial automated build targets Linux x64; other platforms require validation before claiming support. Future upgrades update the catalog, Wrapper, CI, docs and verification evidence together.

## Sources checked on 2026-09-27

- [Gradle releases](https://gradle.org/releases/)
- [Gradle Java compatibility](https://docs.gradle.org/9.8.0/userguide/compatibility.html)
- [JavaFX 27 highlights](https://openjfx.io/highlights/27/)
- [OpenJFX Gradle plugin](https://plugins.gradle.org/plugin/org.openjfx.javafxplugin)
