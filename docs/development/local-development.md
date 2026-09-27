# Local development

## Prerequisites

- JDK 27 with `JAVA_HOME` and `PATH` configured.
- Network access for the first Wrapper and dependency downloads.
- A graphical session for JavaFX. On Linux, GTK 3 and X11/Wayland compatibility libraries are required by the JavaFX native runtime. Ubuntu commonly provides these through `libgtk-3-0t64` (or `libgtk-3-0` on older releases).

No API keys, database, Docker, Node.js, global Gradle installation or manually downloaded JavaFX SDK are required.

## Commands

```bash
java -version
./gradlew --version
./gradlew build
./gradlew :desktop:run
./gradlew :desktop:installDist
```

Use `gradlew.bat` on Windows. Import the repository root as a Gradle project in IntelliJ IDEA and select JDK 27 as the Gradle JVM.

`desktop/build/install/desktop/bin/desktop` launches the local distribution on Unix. On Windows use the corresponding `.bat` script. The distribution includes dependencies, not a JDK, and targets the platform on which it was built.

## Verification

`build` compiles both JPMS modules, processes resources and assembles the application distributions. There are no automated test cases in the initial scaffold; `test` tasks may report `NO-SOURCE`. Add behavior-focused tests with the first use case.

For desktop changes, launch the application and check the title, stylesheet, visible content, resizing and clean exit. Use a virtual display such as Xvfb only for headless verification; a successful compilation does not demonstrate GUI startup.

## Troubleshooting

- Wrong JDK: verify `JAVA_HOME`, `java -version` and the IDE Gradle JVM before changing the build.
- Missing display or GTK: use a graphical session and install the native runtime dependencies for the OS.
- Dependency download failure: diagnose the failing repository or proxy; do not disable TLS or checksum verification.
- Configuration cache: use `--no-configuration-cache` for diagnosis and report the offending task/plugin before changing the default.
