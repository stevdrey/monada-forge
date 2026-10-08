# Continuous integration

Monada Forge runs independent GitHub Actions workflows on pull requests and pushes to `main`.

| Workflow | Required verification | Scope |
| --- | --- | --- |
| Build | `./gradlew --no-daemon build :desktop:installDist` | Java 27 compilation, JUnit tests, Gradle checks, and desktop distribution |
| CodeQL | Java security-extended queries (buildless extraction) | Java source only; Kotlin DSL is excluded |
| Security Scans | Gitleaks and Trivy | Committed secrets, dependency vulnerabilities, and configuration |

CodeQL uses `build-mode: none` because compiler-traced extraction did not capture Java 27 builds. GitHub's currently published CodeQL support matrix only lists Java through version 26; confirm Java 27 extraction in Actions before treating it as fully supported. Buildless mode does not scan Kotlin build scripts.\n\nSecurity analysis also runs weekly and can be started manually. CI never launches a graphical JavaFX session, so successful builds do not certify visual behavior.

## Pull request expectations

1. Confirm that the Build, CodeQL, Gitleaks, and Trivy checks finish successfully.
2. Address failed checks before merging; do not ignore new findings without a documented justification.
3. Add JUnit tests for new core behaviors. Include a manual UI/UX verification summary for desktop-facing changes until automated desktop tests exist.
4. Prefer least-privilege workflow permissions and avoid untrusted privileged pull-request triggers.
5. Protect `main` by requiring successful checks in GitHub repository branch protection or rulesets. Adding a workflow alone does not enforce merge restrictions.

## Dependency scan boundaries

Trivy provides immediate filesystem scanning without introducing a Gradle plugin. It may not resolve every transitive Gradle dependency, so it is not a complete replacement for an OWASP Dependency-Check Gradle integration. Introduce OWASP in a separate change once plugin compatibility with the project's Java and Gradle versions, NVD API key handling, caching, and suppression policy have been validated.

## Local verification

```bash
./gradlew --no-daemon build :desktop:installDist
```

The security workflows run in GitHub Actions and require no application secrets.
