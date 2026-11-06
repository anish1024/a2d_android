# Documentation Management Policy

A repository-agnostic rulebook for creating, organizing, and maintaining project documentation.

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-06
- Scope: any repository; the defaults below are Markdown-based and can be renamed in the
  [repository profile](#4-repository-profile)

## Contents

1. [How to use this policy](#0-how-to-use-this-policy)
2. [Principles](#1-principles)
3. [Document model](#2-document-model)
4. [Standard layout](#3-standard-layout)
5. [Repository profile](#4-repository-profile)
6. [Update rules by change type](#5-update-rules-by-change-type)
7. [Release notes and versioning](#6-release-notes-and-versioning)
8. [Implementation log and commit IDs](#7-implementation-log-and-commit-ids)
9. [Status metadata and freshness](#8-status-metadata-and-freshness)
10. [Templates](#9-templates)
11. [Writing and growth rules](#10-writing-and-growth-rules)
12. [Enforcement](#11-enforcement)
13. [Adoption checklist](#12-adoption-checklist)
14. [Periodic audit](#13-periodic-audit)
15. [Definitions](#14-definitions)

---

## 0. How to use this policy

1. Copy this file to the repository root as `manage-docs.md` (or `docs/DOCUMENTATION.md`).
2. Fill in the [repository profile](#4-repository-profile) with the repository's real paths,
   commands, version source, and release targets.
3. Create the documents in [§2](#2-document-model) that the repository does not have yet.
4. Apply the [update rules](#5-update-rules-by-change-type) on every change.
5. Run the [adoption checklist](#12-adoption-checklist) when onboarding a repository and the
   [audit checklist](#13-periodic-audit) before a release.

**Rule priority.** `MUST` is mandatory; `SHOULD` is expected unless the repository documents a
reason; `MAY` is optional. Sections 0–14 are normative; the repository profile and the examples are
configuration and must be replaced per repository. If a path or command in this policy differs from
the repository profile, the profile wins, and the policy must be corrected in the same change.

---

## 1. Principles

1. **Docs change with the code.** A behavior change and its documentation land in the same
   commit/PR. A follow-up docs PR is allowed only when the code change is already merged and the
   delay is explicitly tracked.
2. **One document, one job.** Every maintained document has a single purpose and a defined update
   trigger (see §2).
3. **Current truth is separate from history.** The entry-point README describes what is true now.
   Logs, completed specifications, and superseded documents are archived or kept in history
   directories.
4. **Single source of truth.** Write each explanation once and link to it. Do not copy the same
   prose into README, an agent guide, and a feature document.
5. **Explicit freshness.** Every maintained document carries an owner and a freshness signal: a
   status line, or a last-verified line for entry-point documents.
6. **Archive, don't delete.** Superseded material is moved to the archive with an `Archived` banner
   so links and project history survive.
7. **Write for a reader with no context.** Prefer short sections, concrete commands, stable headings,
   and links over narrative. This also keeps the repository readable by small local models and
   coding agents.
8. **Keep process proportional.** A one-line fix does not need an ADR. A schema or security change
   does.

---

## 2. Document model

Every repository should have the following roles. A role may be omitted only if the repository is
too small to need it, and the omission must be recorded in the repository profile.

| Role | Default location | Purpose | Update trigger |
| --- | --- | --- | --- |
| **Entry point / current truth** | `README.md` | What the project does today: purpose, architecture, setup, current status, links | Behavior, architecture, toolchain, or current status changes |
| **Agent router** (optional) | `AGENT.md` | Fast task→file routing, rules, commands, and common failures for coding agents | File map, commands, architecture rules, or recurring failure patterns change |
| **Documentation index** | `docs/INDEX.md` | Every maintained document with status, purpose, and last-updated date | A document is added, moved, archived, or retired |
| **Decisions** | `docs/adr/NNNN-*.md` | Why a significant choice was made, including alternatives and consequences | Schema, dependency, architecture, security, or deployment decision |
| **Feature records** | `docs/features/FEAT-*.md` | Proposed, in-progress, and completed feature design | New feature, enhancement, or behavior change |
| **Operations** | `docs/operations/` or `Deployment/` | Setup, deployment, runbooks, device or environment prerequisites | Deployment flow, environment, or operations procedure changes |
| **Known issues** | `docs/known-issues.md` | Recurring bugs, limitations, and workarounds | A bug recurs or needs a workaround |
| **Release notes** | `CHANGELOG.md` | Versioned, user-facing and technical changes for every release | Every version bump and release |
| **History** | `docs/history/implementation-log.md` (index) + `docs/history/implementation-log/` (shards) | Internal task/milestone record with verification and commit IDs | Any code change or completed task |
| **Archive** | `docs/archive/` | Superseded or historical material, never current guidance | A document becomes obsolete or is replaced |

---

## 3. Standard layout

This is a default, not a mandate. Rename directories only when required, and record the mapping in
the repository profile.

```text
<repo>/
├── README.md                     # current truth and entry point
├── AGENT.md                      # optional agent routing guide
├── CHANGELOG.md                  # versioned release notes
├── manage-docs.md                # this policy
├── .github/
│   └── pull_request_template.md  # verification and documentation checklist
├── docs/
│   ├── INDEX.md                  # every maintained document
│   ├── adr/
│   │   ├── _TEMPLATE.md
│   │   └── NNNN-title.md
│   ├── features/
│   │   ├── _TEMPLATE.md
│   │   └── FEAT-XXXX-title.md
│   ├── operations/               # runbooks, deployment, environment setup
│   ├── known-issues.md
│   ├── history/
│   │   ├── implementation-log.md      # rules, index, recent entries (router)
│   │   └── implementation-log/        # dated shards (YYYY-MM.md, YYYY-QN.md)
│   └── archive/                  # superseded docs; never current guidance
└── ...                           # source tree
```

Rules:

- `NNNN` and `XXXX` are **zero-padded numeric sequence placeholders**, not literal filename text.
  Use four digits by default: `0001-technology-stack.md`, `FEAT-0001-task-card-redesign.md`,
  `FEAT-0002-offline-reminders.md`. Zero-padding keeps files sorted in creation order and avoids ID
  collisions; widen the padding consistently only if a repository exceeds the four-digit range.
- Do not keep two live copies of the same document in different directories.
- Keep the archive self-contained and clearly marked; never link to it as current guidance.
- Put project-specific long-form references (schemas, blueprints, generated inventories) under
  `docs/` or `docs/reference/` and list them in `docs/INDEX.md`.

---

## 4. Repository profile

Fill this in for each repository. The “Example” column shows how one Android repository fills it;
replace those values, do not treat them as defaults.

| Setting | What to record | Example (this repository) |
| --- | --- | --- |
| Version source | File and fields that define the released version | `A2D/app/build.gradle.kts` — `versionCode` / `versionName` |
| Test command | Command that runs the automated tests | `cd A2D && ./gradlew test` |
| Build command | Command that produces the shipped/debug artifact | `cd A2D && ./gradlew assembleDebug` |
| Release command | Command that produces the release artifact | `cd A2D && ./gradlew bundleRelease` |
| Artifact path | Where build output is written | `A2D/app/build/outputs/` |
| Deployment docs | Setup and install/launch documentation | `Deployment/a2d_build.md`, `Deployment/a2d_deploy.sh` |
| Platform / release target | Store, package format, or deployment channel | Android / Play Store (AAB) |
| Implementation log | Router + shard directory | `docs/history/implementation-log.md` + `docs/history/implementation-log/` |
| Known issues | Path to the issue register | `docs/known-issues.md` |
| Doc check command | Link/anchor/fence check, or `manual` | `manual` (run before docs commits) |
| Default branch | Branch releases are cut from | `main` |
| Agent guide size limit | Maximum lines for `AGENT.md`, if one exists | ~300 lines |

If a repository has no formal release process, set the release fields to `none` and keep the
changelog under `[Unreleased]` until a release exists.

---

## 5. Update rules by change type

| Change | Required documentation |
| --- | --- |
| Code change with no user-visible effect | Implementation-log entry |
| User-visible behavior or UI change | `CHANGELOG.md`; update `README.md` if current behavior changed |
| Bug fix | `CHANGELOG.md`; add to `docs/known-issues.md` if it has occurred before or has a workaround |
| Recurring bug | `docs/known-issues.md` **and** a regression test; add an agent troubleshooting note if it is easy to reintroduce |
| New feature / enhancement | New `docs/features/FEAT-XXXX.md` before implementation; after completion update `README.md` + `CHANGELOG.md`; set the feature status to **Implemented** |
| Version bump / release | `CHANGELOG.md` versioned section + the version source in the profile; record the release commit only after the build/tests pass |
| Architecture, domain, or public-contract change | `README.md` + tests + ADR if semantics or trade-offs changed |
| Data/storage schema change | ADR + migration notes + `README.md` repository map + agent boundaries |
| Dependency/toolchain upgrade | `README.md` current-state/toolchain section + ADR + agent commands/versions |
| Deployment/operations change | Operations document + agent guide + the deployment section of `README.md` |
| File/package/module move | Agent file map + `README.md` repository map + `docs/INDEX.md` |
| Document added, moved, or archived | `docs/INDEX.md`; fix all inbound links |
| Abandoned feature | Mark the feature record **Superseded** or **Rejected**, do not delete it |

### 5.1 Bug escalation rule

1. **First occurrence** → changelog entry only.
2. **Second occurrence or a workaround is needed** → `docs/known-issues.md`.
3. **Third occurrence or an architectural cause** → ADR + regression test + agent troubleshooting
   note.

### 5.2 Documentation-only changes

A docs-only change still gets an implementation-log entry and must pass the repository's link,
anchor, and code-fence checks. It does not require a code build unless the repository profile says
otherwise.

---

## 6. Release notes and versioning

`CHANGELOG.md` is the single source of truth for release notes. A release note is the versioned
section of the changelog; do not maintain a second hand-written release-note file, because the two
will drift.

### 6.1 Required versioned format

```markdown
# Changelog

All notable changes to this project.
Format: Keep a Changelog; versioning: Semantic Versioning where the project uses it.

## [Unreleased]
### Added
### Changed
### Fixed
### Removed
### Security

## [1.1.0] — YYYY-MM-DD — <version identifiers>
### Added
- User-visible feature in plain language.

### Fixed
- User-visible bug fix.

### Release notes (user-facing)
One or two short non-technical sentences for the store or release announcement.

### Build & verification
- Commit: `abc1234`
- Tests: `<test command>` ✅
- Build: `<build command>` ✅
- Artifact: `<path or URL>`
```

`<version identifiers>` means whatever uniquely identifies the release in that ecosystem, for example
`versionCode 2` on Android, `v1.1.0` for a Git tag, or a package version in a manifest.

### 6.2 Rules

1. Every version update gets exactly one versioned section. The heading must match the released
   version and include the repository's version identifiers.
2. Move relevant `Unreleased` items into the new section at release time. Do not rewrite or delete
   older released sections.
3. The user-facing summary is required for a public/store release; the technical `Added` / `Changed`
   / `Fixed` entries are the detailed record.
4. Record the commit SHA only after the release build and tests pass (same rule as §7).
5. If a release is built from several commits, record the final tagged release commit, not every
   intermediate commit.
6. If verification fails, leave `Commit:` as `pending` or `blocked` and do not publish or upload the
   release.
7. Patch releases still get their own section and their own version identifier.
8. `CHANGELOG.md` describes changes **per version**; the implementation log describes changes **per
   task/milestone**. They are complementary, not duplicates.

### 6.3 When a separate release document is justified

Only when the release needs material the changelog should not carry:

- store copy that differs substantially from the technical changelog;
- screenshots, graphics, or marketing assets;
- multiple artifacts with different notes;
- a long-form release announcement.

Even then, `CHANGELOG.md` remains the canonical version list and links to the richer release file.

### 6.4 Release checklist

- [ ] Version identifiers incremented in the repository's version source
- [ ] `CHANGELOG.md` has a matching versioned section
- [ ] User-facing release note written
- [ ] Repository test and build commands pass
- [ ] Release commit SHA recorded in `CHANGELOG.md` and the implementation log
- [ ] Artifact installed, published, or uploaded from that commit

---

## 7. Implementation log and commit IDs

Every code change gets one entry in `docs/history/implementation-log.md`. The entry is the internal
record: what changed, where, how it was verified, and which commit contains it.

`docs/history/implementation-log.md` is the **router**: rules, entry template, and index table. New
entries go into dated shards under `docs/history/implementation-log/` (see §10.1 for split rules and
budgets).

### 7.1 Entry fields

At minimum, an entry records:

- date and task/feature ID;
- status (`Complete`, `In progress`, or `Superseded`);
- files changed;
- behavior changed;
- verification: tests and build (or the docs-only check);
- commit ID;
- notes and follow-ups.

### 7.2 Commit-ID rule (mandatory)

**Record the git commit ID only if the code build and tests pass.**

| Situation | `Commit:` value |
| --- | --- |
| Not committed yet | `pending` |
| Committed but build/tests not run | `pending — verification not run` |
| Build/tests failed | `blocked — tests failed (see notes)` + failure summary |
| Build/tests passed on that commit | short SHA, e.g. `` `abc1234` `` |
| Docs-only change, docs checks pass | short SHA permitted with `Verification: docs-only (links/fences checked)` |

Additional rules:

1. Verification means the repository profile's compile, test, and build commands.
2. The SHA must identify the exact commit that was verified. If more commits are added afterward,
   re-run verification or record the newer verified SHA.
3. Never backfill a SHA into an entry whose verification failed. Fix the code, re-run, then update
   the entry.
4. For a multi-commit task, record the final verified commit (or a range such as
   `abc1234..def5678`) and note that the range is green.
5. Implementation-log entries are written when the task is done; they are not a pre-work plan (use a
   feature record for that).

---

## 8. Status metadata and freshness

Every maintained narrative document except `README.md` and `AGENT.md` carries explicit status
metadata at the top. Templates and the pull-request template use placeholders instead. `README.md`
and `AGENT.md` carry a last-verified line, because they are entry points and have no meaningful
`Status` beyond “current”.

```markdown
# FEAT-XXXX — Title

- Status: Proposed | Accepted | In progress | Implemented | Superseded | Rejected
- Owner: name or team
- Last updated: YYYY-MM-DD
- Related: links to issues, PRs, ADRs, or README sections
```

### 8.1 Status values

| Status | Meaning |
| --- | --- |
| **Current** | Maintained and accurate now |
| **Proposed** | Under consideration; not implemented |
| **Accepted** | Decision accepted (ADR) or feature approved for work |
| **In progress** | Being implemented |
| **Implemented** | Completed and reflected in current behavior |
| **Superseded** | Replaced by a newer document or decision |
| **Rejected** | Considered and declined |
| **Template** | A reusable skeleton, not a real record |
| **Reference** | Stable external/imported material used for lookup |
| **Historical** | Kept for history; may be outdated |
| **Archived** | Moved to the archive; never current guidance |

### 8.2 Freshness rules

- Use ISO 8601 dates (`YYYY-MM-DD`).
- `README.md`, `AGENT.md`, and `docs/INDEX.md` carry a last-verified line with a date and commit.
- Archived documents keep their original content but gain an `Archived` banner at the top, with the
  archive date and a link to the current entry point.
- When a document moves or is renumbered, re-check its status, date, and inbound links.

---

## 9. Templates

Templates are files, not copies pasted into this policy. Create them once in the repository and link
to them from `docs/INDEX.md`; copies drift.

| Document type | Default template | Required metadata | Required sections |
| --- | --- | --- | --- |
| Feature record | `docs/features/_TEMPLATE.md` | Status, owner, last updated, related, target version | Problem, proposed behavior, scope/non-goals, affected files, test plan, docs to update, risks |
| ADR | `docs/adr/_TEMPLATE.md` | Status, date, owner, supersedes/superseded by | Context, decision, consequences, alternatives considered |
| Known issue | `docs/known-issues.md` → issue template | Status, first seen, affected versions | Symptom, root cause, workaround, fix/regression test |
| Implementation log | `docs/history/implementation-log.md` → entry template | Status, files, behavior, verification, commit | See §7.1; see §10.1 for shard routing |
| Changelog | `CHANGELOG.md` → `[Unreleased]` | Version identifiers in each released heading | Added, changed, fixed, removed, security, release notes, build & verification |

If a repository uses a different template location, record it in the repository profile and update
this table in the same change.

---

## 10. Writing and growth rules

- **README:** current state only. Do not accumulate milestone logs, completed specifications, or
  decision rationale. Keep it navigable and link to depth.
- **Agent guide:** short and task-oriented. Update it only when the file map, commands, key rules, or
  common failure modes change. Keep it under a size the repository profile defines, or roughly 300
  lines if unset.
- **Do not duplicate:** link instead of copying the same explanation into multiple documents.
- **Archive, don’t delete:** move superseded docs to `docs/archive/` with an `Archived` banner.
- **Stable headings:** headings are link targets. Renaming one is a breaking change; update all
  anchors in the same commit.
- **Relative links:** use repository-relative links so the docs work on any host and in any checkout.
- **Fenced code:** always use a language tag and keep fences balanced.
- **No secrets or machine-specific paths:** never commit credentials, tokens, absolute home
  directories, or generated artifacts.
- **One H1 per document:** start with a single title, then the metadata block.
- **IDs:** use stable, zero-padded identifiers such as `ADR 0001`, `FEAT-0001`, `KI-001`, or
  `BUG-001`, and never renumber an existing record.
- **Dates:** use ISO 8601; never use locale-specific formats.

### 10.1 History scalability and agent retrieval

Line count is a poor proxy for context size. Budget history by **tokens or bytes** for the smallest
model that must read it.

| Target model context | Maximum per history file | Approximate practical size |
| --- | --- | --- |
| 8k tokens | 2,500–3,500 tokens | 150–250 lines / 15–25 KB |
| 16k tokens | 5,000–6,000 tokens | 300–400 lines / 30–40 KB |
| 32k+ tokens | 8,000–10,000 tokens | 500–650 lines / 45–60 KB |

- **Hard cap:** 6,000 tokens per history file if 8k-context models must work.
- `implementation-log.md` is a small router: rules, entry template, index, and recent entries only.
  Target ≤150 lines / 2,000 tokens.
- Split by **time bucket** (year/quarter/release), not by feature, under
  `docs/history/implementation-log/<YYYY-QN>.md`.
- Each shard starts with: date range, topics/feature IDs, entry count, approximate token count, a
  link back to the index, and previous/next shard links.
- Keep an index table in `implementation-log.md`:

  | Date range | Topics | Feature IDs | Shard | Approx tokens |
  | --- | --- | --- | --- | --- |
  | 2026-Q4 | Setup, core domain, redesign | FEAT-0001 | `implementation-log/2026-Q4.md` | ~3k |

- `docs/INDEX.md` links only the history index; the history index links the shards. Do not duplicate
  index rows at both levels.
- Feature-based navigation belongs in the index table (feature ID → shard + anchor), not in separate
  per-feature log files.
- Keep entries short: one task per entry, 10–30 lines, no code dumps; link to files and commits.
- Use relative Markdown links, not generated HTML, so agents and diffs can read them.

**Agent retrieval protocol** (mirrored in `AGENT.md`):

1. Read only the index table in `docs/history/implementation-log.md`.
2. Match by feature ID, date, or topic, and open **at most one** shard.
3. If the index is ambiguous, search first: `rg -n "FEAT-XXXX|YYYY-MM" docs/history/`.
4. Do not read all shards, and never treat `docs/archive/` as current guidance.
5. If the required shard is missing, report the gap instead of guessing.

---

## 11. Enforcement

- Add the documentation checklist to the pull-request template (or an equivalent merge checklist).
- Require the checklist to cover: build/tests, README/agent updates, the implementation-log SHA
  rule, the changelog entry, release metadata, feature/ADR/known-issue updates, and the docs
  link/fence check.
- `docs/INDEX.md` must link every maintained document with its status and last-updated date.
- Add a link/anchor/fence check to CI where possible. If no CI exists, run the check manually before
  committing docs, and record it in the implementation log.
- A reviewer may block a change whose docs are stale, missing, or link to archived material as if it
  were current.

---

## 12. Adoption checklist

Use this when applying the policy to a repository for the first time.

- [ ] Copy `manage-docs.md` to the repository root and fill in §4.
- [ ] Create or confirm `README.md` as the current-truth entry point.
- [ ] Create `CHANGELOG.md` with an `[Unreleased]` section.
- [ ] Create `docs/INDEX.md` and list every maintained document.
- [ ] Create `docs/history/implementation-log.md` with the entry template and commit-ID rule.
- [ ] Create shard files under `docs/history/implementation-log/` (e.g., `YYYY-MM.md`, `YYYY-QN.md`)
      and add the history index table to the log router when history exceeds the §10.1 budget.
- [ ] Create `docs/features/_TEMPLATE.md` and `docs/adr/_TEMPLATE.md`.
- [ ] Create `docs/known-issues.md` with the escalation rule and issue template.
- [ ] Create `docs/archive/` and move superseded documents there with `Archived` banners.
- [ ] Add `.github/pull_request_template.md` with the §11 checklist.
- [ ] Add status metadata and freshness lines to every maintained document.
- [ ] Record the version source, test command, build command, and release target in §4.
- [ ] Run the link/anchor/fence check and fix all broken references.
- [ ] Add the agent guide only if the repository is large enough to need task routing.

---

## 13. Periodic audit

Run this before a release, after a major refactor, or at a fixed interval (for example quarterly).

- [ ] `docs/INDEX.md` matches the files on disk; every row has a valid link and status.
- [ ] All local links, anchors, and code fences resolve.
- [ ] `README.md` is still current-state only; history and specs live in their own documents.
- [ ] `CHANGELOG.md` has a section matching every released version and the current version source.
- [ ] Implementation-log entries with a SHA have green verification for that exact commit.
- [ ] History shards are within the token budget (see §10.1) and the history index links each shard.
- [ ] Archived documents have banners and are not linked as current guidance.
- [ ] Status, owner, and last-updated values are accurate.
- [ ] Repository-profile commands and paths still exist and still work.
- [ ] No secrets, absolute home paths, or generated artifacts are committed in docs.
- [ ] Section numbers and cross-references still point at the intended headings.

Record the audit result in the implementation log. If the audit changes documentation only, follow
the docs-only verification rule in §5.2.

---

## 14. Definitions

| Term | Meaning |
| --- | --- |
| **ADR** | Architecture Decision Record: a short document recording a significant decision, its context, alternatives, and consequences. |
| **Feature record** | A `FEAT-XXXX` document that holds the problem, design, scope, test plan, and final status of a feature. |
| **Known issue** | A tracked bug or limitation, usually identified as `KI-###` or `BUG-###`, with symptom, cause, workaround, and fix. |
| **Implementation log** | The internal per-task history with files, behavior, verification, and commit IDs. |
| **Changelog** | The user-facing and technical record of changes grouped by released version. |
| **Current truth** | The documents that describe what the repository does now; they must not accumulate history. |
| **Archive** | The location for superseded material that is kept for history but is not current guidance. |
| **Repository profile** | The filled-in §4 table that maps this policy's defaults to a repository's real paths, commands, and release targets. |
