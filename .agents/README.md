# Canonical agent guidance

Use one workflow across coding assistants:

1. Establish the task outcome and inspect the current repository baseline.
2. Load the smallest sufficient context: affected files, relevant ADRs, security invariants and existing tests.
3. Define scope, non-goals, acceptance criteria and verification before editing.
4. Implement a coherent change within the owning module.
5. Run targeted verification, then the applicable build once after the last relevant changes.
6. Review the diff and publish a concise PR with evidence and remaining limitations.

Keep an explicit list of unresolved feedback items. Do not silently drop feedback, expand scope to optional refactors, or repeat successful checks without a relevant state change.

## Module guidance

| Scope | Guidance |
| --- | --- |
| Core | Plain Java; domain and application behavior; no JavaFX, UI state or provider implementation |
| Desktop | JavaFX scene graph and composition; keep blocking work off the JavaFX application thread; define presentation with CSS stylesheets wherever JavaFX CSS supports the intended styling. Avoid inline styles and styling properties in application code unless a genuinely dynamic or unsupported style requires it. |
| Integrations (future) | Narrow contracts only when needed; credentials and execution policies outside domain objects |
| Build | Wrapper, catalog and toolchain alignment; update CI and development instructions together |
| Documentation | Use normal relative Markdown links; no dependency on a published GitHub Wiki |

## Verification and completion

Add behavior-focused tests with the feature they protect. Reuse existing tests before introducing duplicate coverage. This scaffold has no domain behavior and no automated test cases yet; compilation is not a substitute for testing future behavior.

Stop when acceptance criteria are met, required evidence is available, and no correctness, security or data-integrity blocker remains. Label stylistic and speculative observations as optional.

Reusable skills may be added here when repeated tasks justify them. Do not generate provider-specific copies of the same instructions.
