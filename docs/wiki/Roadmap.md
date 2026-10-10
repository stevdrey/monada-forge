# Roadmap

This is a sequencing proposal, not a delivery commitment.

| Phase | Outcome | Exit evidence |
| --- | --- | --- |
| 0 — Foundation | Desktop shell, modular build and documentation | Reproducible build and desktop startup verification |
| 1 — Task and workspace | Explicit local workspace selection and bounded task specification | Validation, path-boundary tests and usable intake flow. Implemented through the review and confirmation screen; automated evidence in [Phase 1 verification](../verification/phase-1-task-intake.md), manual desktop check pending |
| 2 — Context and plan | Traceable context collection and reviewable plan | Source provenance, scope limits and acceptance criteria |
| 3 — First agent adapter | One supported integration with explicit authorization and cancellation | Contract tests, secret handling and execution-policy tests |
| 4 — Delivery loop | Implementation, verification and feedback tracking | End-to-end evidence and deterministic stop conditions |
| 5 — Learning and evaluation | Optional Neuron/Resonance Store integration and cost/quality analysis | Reproducible comparisons, provenance and measured value |

Phases 0 and 1 are implemented here; Phases 2 onward are not. Inspect current `main` before turning any phase into an Issue. Use [Spec Context](../development/spec-context-template.md), including exact existing paths and explicit non-goals.
