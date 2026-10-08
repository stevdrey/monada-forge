# Monada Forge

Monada Forge is a desktop workspace for agent-assisted software delivery: start from a task, assemble relevant context, coordinate implementation and review, and retain evidence about quality, feedback and cost.

**Status:** initial project scaffold. The application displays a welcome window. Task ingestion, agents, external integrations, execution and persistence are not implemented yet.

## Stack

| Component | Baseline |
| --- | --- |
| Java | 27, stable features only |
| JavaFX | 27 |
| Gradle Wrapper | 9.8.0 |
| Build scripts | Kotlin DSL |

Gradle 9.8.0 supports Java 27 for both toolchains and its runtime. See [the toolchain decision](docs/adr/0002-java-27-javafx-27-gradle.md).

## Quick start

Install JDK 27 and point `JAVA_HOME` to it. No global Gradle or separate JavaFX SDK installation is required.

```bash
java -version
./gradlew build
./gradlew :desktop:run
```

On Windows, use `gradlew.bat`. Running the application requires a graphical session and the native libraries listed in [local development](docs/development/local-development.md).

## Repository structure

| Path | Responsibility |
| --- | --- |
| `core/` | Framework-independent domain and application module; currently exposes the validated `WorkspaceRoot` boundary and contained workspace-relative path resolution (`WorkspacePathResolver`) |
| `desktop/` | JavaFX entry point, presentation and resources; depends on `core` |
| `docs/` | Product guide, architecture, ADRs, security, development and verification |
| `.agents/` | Provider-neutral coding-agent guidance |
| `.github/` | CI, dependency updates and contribution templates |
| `gradle/` | Version catalog and checked-in Wrapper |

This organization follows Dokene's documentation and agent-guidance conventions, adapted to a desktop application. It does not introduce a web backend, database or infrastructure before they are needed.

## Documentation

Start with the [documentation index](docs/README.md) or [product guide](docs/wiki/Home.md). All documentation is maintained in this repository; GitHub Wiki is not required.

Contributors and agents should read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).

## Licensing

Monada Forge is licensed under the [Apache License 2.0](LICENSE).

The license permits use, modification and distribution, including commercial use, subject to its terms. Trademark rights are not granted by the Apache License 2.0.
