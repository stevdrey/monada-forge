# Initial scaffold verification

- Date: 2026-09-27
- Starting `main` commit: `0da9f03e1281a9a9c3c8513d3261dbd62315f9e8`
- Platform: Linux x64
- JDK: Eclipse Temurin 27+35
- Gradle: 9.8.0

## Results

| Check | Result |
| --- | --- |
| Gradle distribution and JDK archive checksums | Matched publisher-provided SHA-256 values |
| Official Gradle Wrapper generation | Passed using the verified Gradle distribution |
| `build :desktop:installDist` | Passed; both JPMS modules compiled and application distributions assembled |
| Installed distribution module graph (`java --module-path ... --validate-modules`) | Passed |
| Packaged stylesheet and generated launch script | Present, including the scoped JavaFX native-access option |
| Wrapper shell syntax | Passed (`bash -n gradlew`) |
| Automated behavioral tests | Not present; Gradle test tasks report `NO-SOURCE` |
| Visual startup and interaction | Not run; this environment has no graphical display or Xvfb |

## Environment qualification

The Java/Gradle process could not reach the environment's network proxy (`Network is unreachable`). Dependencies were downloaded from the official Gradle Plugin Portal and Maven Central using the available HTTPS client and staged in a temporary local Maven repository. The successful build used Gradle's offline mode and a temporary init script selecting that repository. Neither the mirror nor the init script is committed; project repositories remain the Plugin Portal and Maven Central.

Wrapper generation used the already-verified distribution with URL validation skipped only for that generation command because of the network limitation. The committed Wrapper properties enable distribution URL validation and pin the distribution SHA-256.

This proves local build configuration, compilation and packaging with the stated versions. It does not prove a clean online dependency resolution or GUI startup. GitHub Actions is configured to perform a clean Wrapper-based build on JDK 27.

## Manual desktop check still required

Run `./gradlew :desktop:run` in a graphical session. Confirm the Monada Forge window opens, the stylesheet loads, text remains readable when resized, and closing the window terminates the application. No providers or execution controls should be exposed by this initial shell.
