# Documentation Index

Entry point for every maintained document. Status values follow
[`docs/manage-docs.md` §8.1](docs/manage-docs.md#8-status-metadata-and-freshness): **Current**,
**Proposed**, **Accepted**, **In progress**, **Implemented**, **Superseded**, **Rejected**,
**Template**, **Reference**, **Historical**, or **Archived**.

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-25
- Last verified against: code baseline `8d9563d`

## Project documents

| Document | Status | Purpose | Last updated |
| --- | --- | --- | --- |
| [docs/README.md](docs/README.md) | Current | Architecture, current behavior, toolchain, repository map, deployment | 2026-11-06 |
| [AGENTS.md](AGENTS.md) | Current | Short task routing, rules, commands, and troubleshooting for small local models | 2026-11-24 |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | Current | Versioned release notes for every app version | 2026-11-06 |
| [docs/manage-docs.md](docs/manage-docs.md) | Current | Repository-agnostic documentation rules, conventions, and checklists | 2026-11-06 |
| [README.md](README.md) | Current | This index of every maintained document (root entry point) | 2026-11-25 |
| [docs/pull_request_template.md](docs/pull_request_template.md) | Current | PR build/test/documentation checklist | 2026-11-06 |

## Operations

| Document | Status | Purpose | Last updated |
| --- | --- | --- | --- |
| [docs/a2d_build.md](docs/a2d_build.md) | Current | Phone/USB debugging and device setup checklist | 2026-11-06 |
| [Deployment/a2d_deploy.sh](Deployment/a2d_deploy.sh) | Current | `adb` discovery, debug APK install, app launch | 2026-11-06 |
| [docs/Deployment/a2d-cloud/README.md](docs/Deployment/a2d-cloud/README.md) | Current | Cloud backend deployment runbook: tunnel, deploy/update, invite, backup/restore, retention | 2026-11-16 |
| [docs/setup-adb.sh](docs/setup-adb.sh) | Current | `adb` bootstrap helper | 2026-10-22 |
| [docs/CLI_WALKTHROUGH.md](docs/CLI_WALKTHROUGH.md) | Current | On-device login/sync test script capture | 2026-11-24 |
| [docs/SUMMARY.md](docs/SUMMARY.md) | Current | Final diagnosis & action plan summary | 2026-11-24 |
| [docs/SOLUTION_SUMMARY.md](docs/SOLUTION_SUMMARY.md) | Implemented | Complete fix summary | 2026-11-24 |

## History and decisions

| Document | Status | Purpose | Last updated |
| --- | --- | --- | --- |
| [docs/history/implementation-log.md](docs/history/implementation-log.md) | Current | Historical + current implementation log with verification and commit IDs | 2026-11-06 |
| [docs/features/FEAT-0001-task-card-redesign.md](docs/features/FEAT-0001-task-card-redesign.md) | Implemented | Delivered task-card redesign summary + original specification | 2026-11-06 |
| [docs/features/FEAT-0002-cloud-sync.md](docs/features/FEAT-0002-cloud-sync.md) | Accepted | Cloud replica: two-watermark table-to-table row sync with row-count parity, per-user folders + tasks.db, FastAPI API, Cloudflare Tunnel, web UI, required import/export | 2026-11-16 |
| [docs/adr/0001-technology-stack.md](docs/adr/0001-technology-stack.md) | Accepted | Technology stack decision and architectural rationale | 2026-11-06 |
| [docs/adr/0002-cloud-sync-architecture.md](docs/adr/0002-cloud-sync-architecture.md) | Accepted | Cloud sync, data contract, Cloudflare Tunnel ingress, and cloud import/export decisions | 2026-11-16 |
| [docs/known-issues.md](docs/known-issues.md) | Current | Active bugs, limitations, and workarounds | 2026-11-06 |
| [docs/adr/_TEMPLATE.md](docs/adr/_TEMPLATE.md) | Template | Architecture Decision Record template | 2026-11-06 |
| [docs/features/_TEMPLATE.md](docs/features/_TEMPLATE.md) | Template | Feature/enhancement specification template | 2026-11-06 |
| [docs/a2d_graph.yaml](docs/a2d_graph.yaml) | Reference | Original project blueprint | 2026-10-22 |
| [docs/archive/](docs/archive/) | Archived | Superseded/historical Markdown; never current guidance | 2026-11-06 |

## Adding a document

1. Copy the matching template in `docs/features/` or `docs/adr/`.
2. Add status metadata at the top.
3. Add the file to this index in the same change.
4. Link it from `docs/README.md` or `AGENTS.md` only if it changes current behavior or task routing.