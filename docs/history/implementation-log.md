# Implementation Log

Internal task history: what changed, how it was verified, and which commit contains it. This file is
the **rules and index**; entries live in dated shards under
[`implementation-log/`](implementation-log/).

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-06
- Commit-ID rule: record the SHA only when the code build and tests pass (see below)

---

## 1. Rule

Every entry records what changed, where, how it was verified, and which commit contains it.

**Record the git commit ID only if the code build and tests pass.**

| Situation | `Commit:` value |
| --- | --- |
| Not committed yet | `pending` |
| Committed but build/tests not run | `pending — verification not run` |
| Build/tests failed | `blocked — tests failed (see notes)` + failure summary |
| Build/tests passed on that commit | short SHA, e.g. `` `abc1234` `` |
| Docs-only change, docs checks pass | short SHA permitted with `Verification: docs-only (links/fences checked)` |

Additional rules:

1. Verification means `./gradlew compileDebugKotlin` at minimum, plus `./gradlew test` and
   `./gradlew assembleDebug` for a normal code change.
2. The SHA must identify the exact commit that was verified. If more commits are added afterward,
   re-run verification or record the newer verified SHA.
3. Never backfill a SHA into an entry whose verification failed. Fix the code, re-run, then update
   the entry.
4. For a multi-commit task, record the final verified commit (or a range such as
   `abc1234..def5678`) and note that the range is green.
5. Entries are written when the task is done; they are not a pre-work plan (use `docs/features/` for
   that).

---

## 2. Entry template

```markdown
### YYYY-MM-DD — TASK-ID: Title (Status)
- Status: Complete | In progress | Superseded
- Files: `...`
- Behavior: ...
- Tests: `./gradlew test` ✅
- Build: `./gradlew assembleDebug` ✅
- Commit: `abc1234`
- Notes: ...
```

`TASK-ID` is a stable identifier such as `DOCS-0001`, `FEAT-0001/T3`, or `KI-002`. Use `—` (not an
ID) only for a small fix that has no tracked record.

---

## 3. History index

Read this table first. Open **at most one** shard.

| Date range | Topics | Feature IDs | Shard | Approx tokens |
| --- | --- | --- | --- | --- |
| 2026-10 | Environment, project config, domain, gate engines, tests, Room, repository, DI | — | [Milestones 0–2](implementation-log/2026-10-milestones-0-2.md) | ~2.9k |
| 2026-10 | Compose theme, ViewModels, screens, USB deployment, signing, release bundle, Play checklist | — | [Milestones 3–4](implementation-log/2026-10-milestones-3-4.md) | ~2.3k |
| 2026-10 | Task card redesign T1–T7 and verification | FEAT-0001 | [Task card redesign](implementation-log/2026-10-redesign-01.md) | ~3k |
| 2026-11 | Documentation restructuring, agent routing, deployment docs, history split | DOCS-0001 | [2026-11](implementation-log/2026-11.md) | ~0.7k |

Shards are chronological. Each shard links back here and to its previous/next shard.

---

## 4. Adding an entry

1. Use the **current month shard**, for example `implementation-log/2026-11.md`.
2. If the month changed, create `implementation-log/YYYY-MM.md` with this header:

   ```markdown
   # Implementation Log — YYYY-MM

   - Date range: YYYY-MM
   - Topics: ...
   - Feature IDs: ...
   - Entries: N tasks
   - Approx size: ~Nk tokens
   - Previous shard: ...
   - Index: [Implementation log](../implementation-log.md)

   ## Entries
   ```

3. Keep each entry to 10–30 lines: one task, no code dumps, link to files and commits.
4. Add or update the shard's row in [§3](#3-history-index).
5. Do **not** create one log file per feature. Feature navigation belongs in the index row above;
   the design record belongs in `docs/features/`.

### Size budget

| Target model context | Maximum per shard |
| --- | --- |
| 8k tokens | 2,500–3,500 tokens |
| 16k tokens | 5,000–6,000 tokens |
| 32k+ tokens | 8,000–10,000 tokens |

Hard cap: **6,000 tokens** per shard if 8k-context models must work. Split by time bucket, never by
feature. See [`manage-docs.md` §10.1](../../manage-docs.md#101-history-scalability-and-agent-retrieval)
for the full policy.

---

## 5. Retrieval protocol

1. Read only the index table in [§3](#3-history-index).
2. Match by feature ID, date, or topic, then open **one** shard.
3. If the index is ambiguous, search before reading:
   `rg -n "FEAT-XXXX|DOCS-XXXX|YYYY-MM" docs/history/`.
4. Do not load every shard. Do not treat `docs/archive/` as current guidance.
5. If no shard covers the task, report the gap instead of guessing.

Use relative Markdown links, never generated HTML, so agents and diffs can read the history.
