# Product vision

## Problem

Coordinating coding agents requires repeated context gathering, task decomposition, verification and feedback tracking. A developer needs to understand what an agent changed, which requirements are still unresolved, and what evidence supports completion.

## Intended workflow

1. Select a task from a supported source, initially to be defined.
2. Gather bounded repository context and explicit constraints.
3. Produce a reviewable plan with acceptance criteria.
4. Execute authorized implementation and verification steps.
5. Track review findings until resolved or explicitly deferred.
6. Present the resulting change and evidence for acceptance.

## Future differentiators

- Context reuse based on similar prior work.
- Routing decisions informed by task characteristics and measured outcomes.
- Comparison of maintainability, unnecessary complexity, review feedback and validation results.
- Token usage and hypothetical API-cost estimates with provider, model, price date and assumptions recorded. Subscription activity must not be presented as actual API billing.
- Potential integration with Monada Neuron and Monada Resonance Store through explicit boundaries.

These are product directions, not implemented behavior or validated performance claims. No integration dependency is added until its API, lifecycle and concrete use case are established.

## Initial non-goals

No unrestricted agent execution, automatic merges, multi-tenant SaaS, microservices, provider-specific IDE replacement or universal graph engine in the scaffold.
