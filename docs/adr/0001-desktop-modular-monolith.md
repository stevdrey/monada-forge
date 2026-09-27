# ADR 0001: Desktop modular monolith

- Status: Accepted
- Date: 2026-09-27

## Context

Monada Forge starts as a local desktop product. Dokene provides useful documentation and agent-guidance conventions, but its web backend, multi-tenancy and deployment infrastructure do not match this initial scope.

## Decision

Use one repository and one desktop application with two Gradle/JPMS modules: `desktop` depends on `core`. JavaFX belongs only to `desktop`. Keep `core` empty until the first domain use case. Add integration modules only when real dependencies justify them.

Maintain product guidance in `docs/wiki`, technical architecture in `docs/architecture`, decisions in `docs/adr`, and security guidance in `docs/security`. Use relative links and no Wiki publication dependency. Agent guidance is canonical under `.agents`, discovered through `AGENTS.md`.

## Consequences

The build enforces an initial boundary with low operational complexity. There is no server, database, DI framework or process runner. New persistence, execution or integration behavior requires explicit contracts, tests and security review appropriate to that change.
