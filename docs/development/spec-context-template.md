# Spec Context task template

Use this structure for implementation Issues. Inspect current `main` first; do not create tasks from stale assumptions.

## Goal and user outcome

Describe the observable outcome and why it matters.

## Baseline evidence

- Inspected `main` commit SHA and date:
- Current relevant behavior:
- Existing source and test paths:
- Applicable ADRs, security invariants and agent guidance:

## Scope and boundaries

- In scope:
- Explicitly out of scope:
- Allowed/expected files and modules:
- Existing contracts to preserve:
- Dependencies that must not be added or changed:

## Proposed behavior

Specify inputs, outputs, error cases, concurrency/cancellation needs and side effects. Distinguish requirements from optional implementation suggestions.

## Security and data

Identify trust boundaries, authorization, secrets, external data sharing and retention. State whether the change performs filesystem, network, process or publishing actions.

## Acceptance criteria

- [ ] Observable success behavior is defined.
- [ ] Relevant failure and boundary cases are defined.
- [ ] Existing tests to reuse and new cases to add are identified.
- [ ] Documentation and ADR changes are identified when needed.

## Verification and completion

- Exact targeted commands:
- Applicable full build:
- Manual checks and evidence:
- Known environment limitations:
- Stop condition and unresolved dependencies:

Do not fabricate file paths or claim planned features already exist. Explain any necessary scope expansion before implementing it.
