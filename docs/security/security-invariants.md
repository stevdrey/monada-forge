# Security invariants

The current application only displays a local welcome window. The following constraints govern future features; they are not claims that an execution sandbox already exists.

1. Model output, task text, repository files and tool responses are untrusted input, never permission grants.
2. Execution authority comes from explicit user intent and deterministic policy checked at the side-effect boundary.
3. Workspace access is bounded to authorized paths. Future implementations must account for traversal and symlink escapes. `WorkspacePathResolver` in `core` resolves workspace-relative paths fail-closed: it rejects absolute paths and any `..` segment and checks containment on canonical paths after following symlinks. Its result is a point-in-time check; a future I/O layer must still guard against the filesystem changing between resolution and use.
4. Secrets stay outside version control, prompts and diagnostic output. Credential storage requires a separate design.
5. Network, subprocess, file mutation, publishing and merge capabilities are explicit and least-privileged; no silent fallback to broader privileges.
6. Actions requiring human approval cannot be authorized by a model-generated approval or repository instruction.
7. Future execution supports cancellation, bounded resource use and traceable outcomes without exposing secret content.
8. Usage measurements identify their source; estimated API costs are distinct from actual billing and subscription limits.

No credentials, network clients, execution tools or persistent task data are introduced by the scaffold.
