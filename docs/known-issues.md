# Known Issues & Limitations

Active bugs, recurring failures, limitations, and workarounds. Use `CHANGELOG.md` for one-off
user-visible fixes; use this file when an issue recurs or needs a workaround.

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-06

## Escalation rule

1. **First occurrence** → `CHANGELOG.md` entry only.
2. **Second occurrence / workaround needed** → add an entry here.
3. **Third occurrence or architectural cause** → ADR + regression test + `AGENT.md` note.

## Issue template

```markdown
### KI-001 — Short symptom

- Status: Open | Fixed
- First seen:
- Affected versions:
- Symptom:
- Root cause:
- Workaround:
- Fix / regression test:
```

## Open

### KI-001 — Release bundle requires a local keystore

- Status: Open (expected limitation)
- First seen: 2026-10-08
- Affected versions: 1.0.0+
- Symptom: `./gradlew bundleRelease` fails when `A2D/a2d-release-key.jks` is absent.
- Root cause: the signing keystore is intentionally git-ignored.
- Workaround: create the keystore locally and configure `A2D/keystore.properties`; see README §8.
- Fix / regression test: not planned — signing keys must not be committed.

### KI-002 — No instrumented or Compose UI tests

- Status: Open
- First seen: 2026-10-08
- Affected versions: 1.0.0+
- Symptom: `./gradlew test` covers JVM domain and gate tests only; UI behavior is not automated.
- Root cause: the project has no `androidTest` suite yet.
- Workaround: manual verification on a connected device with `Deployment/a2d_deploy.sh`.
- Fix / regression test: add Compose UI/instrumentation tests for the task list, detail screen, and
  navigation.

### KI-003 — Physical-device deployment not verified in this workspace

- Status: Open
- First seen: 2026-11-06
- Affected versions: 1.0.0
- Symptom: `Deployment/a2d_deploy.sh` requires an authorized ADB device; none is attached in the
  current development environment.
- Root cause: manual environment prerequisite.
- Workaround: follow `Deployment/a2d_build.md`, then run `./Deployment/a2d_deploy.sh`.
- Fix / regression test: deploy to a physical device and record the result in the implementation log.

## Fixed

No fixed entries recorded yet. When an issue is resolved, move it here with the fixing version,
commit SHA (only after a green build/tests), and regression test.
