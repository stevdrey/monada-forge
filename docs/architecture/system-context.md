# System context

## Implemented structure

The product is one desktop application built from two Gradle subprojects and two JPMS modules.

| Module | Owns | Dependencies |
| --- | --- | --- |
| `core` | Domain types and application use cases; currently the validated `WorkspaceRoot` (existing, readable, canonical directory) and `WorkspacePathResolver` (workspace-relative paths proven to stay inside that root) | Java platform only |
| `desktop` | JavaFX lifecycle, workspace selection, welcome view, stylesheet and application composition | `core`, JavaFX Controls |

The core exposes only the `io.github.stevdrey.monadaforge.core.workspace` package, added for the workspace boundary; further API is added only when a real use case requires it. The desktop declares the future dependency direction without inventing an orchestration framework.

## Runtime

The entry point is `io.github.stevdrey.monadaforge.desktop.ForgeApplication`. JavaFX owns the UI thread. Future blocking repository, network or process operations must execute outside that thread and support cancellation. UI updates return to the JavaFX thread.

## Future boundaries

Task sources, repository access, model/agent providers, MCP tools and memory are integration candidates. Introduce narrow adapters only with a concrete use case. Do not make UI code responsible for authorization or let model-generated instructions establish execution authority.

Monada Neuron and Monada Resonance Store remain independent projects. Their transport or library integration, versions, licensing and lifecycle need separate decisions; no dependency is assumed here.

## Packaging

Gradle `installDist` produces launch scripts and runtime dependencies for the current operating system. A compatible JDK remains required. JavaFX native artifacts are platform-specific: distributions must be built and validated per target platform. Native installers and bundled runtimes are future work.
