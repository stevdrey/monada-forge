# Contributing

1. Inspect the current `main` branch and the smallest relevant set of source, tests and documentation.
2. State the goal, acceptance criteria, affected files and explicit exclusions using the [Spec Context template](docs/development/spec-context-template.md).
3. Work on a focused branch. Keep all code, comments, Issues, PRs and documentation in English.
4. Use Java 27 features when they simplify the solution. Preview features require a separate decision; they are disabled by default.
5. Run the narrowest applicable checks, then `./gradlew build` before submitting. Do not claim tests passed when tasks report `NO-SOURCE`.
6. Include verification evidence and limitations in the PR. Update the relevant docs and ADR when a durable decision changes.

Define JavaFX presentation styling in CSS stylesheets whenever possible. Do not set inline CSS or style properties in Java code for static presentation; use code-driven styling only when the style is genuinely dynamic or JavaFX CSS cannot express it. Keep `core` free of JavaFX and provider SDKs. Avoid speculative extension points, generic execution engines or empty service modules. Introduce an adapter when a concrete integration requires it.

Use the checked-in Gradle Wrapper. Dependency versions belong in `gradle/libs.versions.toml`. Do not commit credentials, generated output, local workspaces or real task data.
