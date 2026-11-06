# Documentation Index

Entry point for every maintained document. Status values follow
[`manage-docs.md` §8.1](../manage-docs.md#8-status-metadata-and-freshness): **Current**, **Proposed**,
**Accepted**, **In progress**, **Implemented**, **Superseded**, **Rejected**, **Template**,
**Reference**, **Historical**, or **Archived**.

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-06
- Last verified against: code baseline `8d3b7f4`

## Project documents

| Document | Status | Purpose | Last updated |
| --- | --- | --- | --- |
| [README.md](../README.md) | Current | Architecture, current behavior, toolchain, repository map, deployment | 2026-11-06 |
| [AGENT.md](../AGENT.md) | Current | Short task routing, rules, commands, and troubleshooting for small local models | 2026-11-06 |
| [CHANGELOG.md](../CHANGELOG.md) | Current | Versioned release notes for every app version | 2026-11-06 |
| [manage-docs.md](../manage-docs.md) | Current | Repository-agnostic documentation rules, conventions, and checklists | 2026-11-06 |
| [docs/INDEX.md](INDEX.md) | Current | This index of every maintained document | 2026-11-06 |
| [.github/pull_request_template.md](../.github/pull_request_template.md) | Current | PR build/test/documentation checklist | 2026-11-06 |

## Operations

| Document | Status | Purpose | Last updated |
| --- | --- | --- | --- |
| [Deployment/a2d_build.md](../Deployment/a2d_build.md) | Current | Phone/USB debugging and device setup checklist | 2026-11-06 |
| [Deployment/a2d_deploy.sh](../Deployment/a2d_deploy.sh) | Current | `adb` discovery, debug APK install, app launch | 2026-11-06 |
| [docs/setup-adb.sh](setup-adb.sh) | Current | `adb` bootstrap helper | 2026-10-22 |

## History and decisions

| Document | Status | Purpose | Last updated |
| --- | --- | --- | --- |
| [docs/history/implementation-log.md](history/implementation-log.md) | Current | Historical + current implementation log with verification and commit IDs | 2026-11-06 |
| [docs/features/FEAT-0001-task-card-redesign.md](features/FEAT-0001-task-card-redesign.md) | Implemented | Delivered task-card redesign summary + original specification | 2026-11-06 |
| [docs/adr/0001-technology-stack.md](adr/0001-technology-stack.md) | Accepted | Technology stack decision and architectural rationale | 2026-11-06 |
| [docs/known-issues.md](known-issues.md) | Current | Active bugs, limitations, and workarounds | 2026-11-06 |
| [docs/adr/_TEMPLATE.md](adr/_TEMPLATE.md) | Template | Architecture Decision Record template | 2026-11-06 |
| [docs/features/_TEMPLATE.md](features/_TEMPLATE.md) | Template | Feature/enhancement specification template | 2026-11-06 |
| [docs/a2d_graph.yaml](a2d_graph.yaml) | Reference | Original project blueprint | 2026-10-22 |
| [docs/archive/](archive/) | Archived | Superseded/historical Markdown; never current guidance | 2026-11-06 |

## Adding a document

1. Copy the matching template in `docs/features/` or `docs/adr/`.
2. Add status metadata at the top.
3. Add the file to this index in the same change.
4. Link it from `README.md` or `AGENT.md` only if it changes current behavior or task routing.
