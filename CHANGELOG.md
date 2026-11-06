# Changelog

All notable changes to A2D (Adult ADHD Assistant) are recorded here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/).

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-06
- Last verified against: code baseline `8d3b7f4`

## [Unreleased]

### Added

### Changed

### Fixed

### Removed

### Security

## [1.0.0] — 2026-11-06 — versionCode 1

Initial Android release. The app version is defined in `A2D/app/build.gradle.kts`.

### Added

- Task list with per-task cards, activity checkboxes, and delete confirmations.
- Task detail screen with completion badge and activity management.
- Hierarchical two-tier AND-gate completion engine:
  - Tier 1 latches each task's activities.
  - Tier 2 evaluates the task list into a milestone output.
- Room persistence with `TaskEntity` / `ActionItemEntity` cascade relationships.
- Hilt dependency injection for the database, DAO, repository, and gate processors.
- Jetpack Compose + Material 3 UI with task list, task detail, and logic-gate screens.
- Standalone logic-gate playground for manual pulse testing.

### Security

- Release signing uses the local `A2D/a2d-release-key.jks` referenced by `A2D/keystore.properties`;
  the keystore is not committed.

### Release notes (user-facing)

Initial release of A2D: create tasks, break them into activities, and track completion with a
hierarchical AND-gate progress system. Includes a logic-gate playground for testing gate behavior.

### Build & verification

- Code baseline: `8d3b7f4`
- Commit: `pending` — no release commit/tag has been created
- Tests: `./gradlew test` → 11 tests (recorded baseline in README §6)
- Build: `./gradlew assembleDebug` → `A2D/app/build/outputs/apk/debug/app-debug.apk`
- Release status: Play Store submission pending
