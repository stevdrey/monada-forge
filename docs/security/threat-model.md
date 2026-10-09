# Initial threat model

## Current surface

Runtime: local JavaFX window and packaged stylesheet. Build time: Gradle distribution, plugins, Maven artifacts and GitHub Actions. Manual task intake exists: the user types the task title, description, acceptance criteria, constraints and non-goals, which core validates and holds in memory only. That text is untrusted data, displayed and stored but never interpreted, executed or logged. No external task source, provider, subprocess execution or persistence exists yet.

## Future risks and required controls

| Boundary | Threat | Required design before enabling it |
| --- | --- | --- |
| Task/repository content to agent | Prompt injection and scope manipulation | Data/instruction separation, provenance and deterministic authorization |
| Agent to tools | Unapproved commands, writes or publication | Capability restrictions, human approval where required and auditable outcomes |
| Workspace filesystem | Traversal, symlink escape and accidental overwrite | Validated canonical boundaries and isolated workspaces |
| Provider connection | Credential or source-code disclosure | Explicit data-sharing scope, protected credentials and redacted logging |
| Memory/telemetry | Sensitive data retention or misleading evaluation | Provenance, retention controls and reproducible metrics |
| Dependencies and CI | Supply-chain compromise | Pinned versions, Wrapper checksum, immutable action refs and reviewed updates |

This is a living design document. Each new trust boundary must add concrete mitigations and adversarial tests; a policy document alone is not enforcement.
