# FEAT-XXXX — Title

- Status: Proposed | Accepted | In progress | Implemented | Superseded
- Owner:
- Last updated:
- Related:
- Target version:

## Problem

What user or engineering problem does this solve? Include the current behavior.

## Proposed behavior

What should the app do after this change? Be specific about UI and domain behavior.

## Scope

In scope:

- ...

Non-goals:

- ...

## Affected files

- `A2D/app/src/main/java/com/a2d/app/...`
- Tests: ...

## Domain and gate impact

Does this change `Task.isCompleted`, `Task.achieved`, pin arity, latch/reset behavior, or the
two-tier pipeline? If yes, describe the new semantics and the tests that prove them.

## Test plan

- [ ] Unit tests: ...
- [ ] Manual device check: ...
- [ ] Regression test added for the bug/feature: ...

## Docs to update

- [ ] `README.md` (current behavior/architecture)
- [ ] `AGENT.md` (file map/rules/commands, if changed)
- [ ] `CHANGELOG.md` (user-visible change)
- [ ] `docs/history/implementation-log.md` (on completion, with a verified commit SHA)
- [ ] `docs/known-issues.md` (if a recurring bug/workaround)
- [ ] ADR (if a significant architectural decision)

## Rollback / risks

How can this be reverted? What could break? Does it require a Room migration?
