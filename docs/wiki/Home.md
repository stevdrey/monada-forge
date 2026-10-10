# Monada Forge

Monada Forge aims to turn a software task into a traceable delivery workflow: gather relevant repository context, prepare a bounded plan, coordinate agents, verify behavior and retain review evidence.

The developer remains responsible for intent, policy and acceptance. Model output is advice, not execution authority.

## Current implementation

- A Java 27 / JavaFX 27 desktop application with workspace selection, manual task intake, scope definition and a review and confirmation step.
- A Gradle multi-project build with `desktop` and `core` JPMS modules.
- Repository-based documentation and contribution guidance.

No model connections, memory, issue ingestion, workflow execution, telemetry or credentials are configured.

## Guide

- [Product vision](Product-Vision.md)
- [Architecture](../architecture/system-context.md)
- [Security model](../security/threat-model.md)
- [Development model](../development/local-development.md)
- [Roadmap](Roadmap.md)
- [Full documentation index](../README.md)
