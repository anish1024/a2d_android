## Summary

<!-- What changed, and why? Link the issue/feature doc. -->

## Change type

- [ ] Bug fix
- [ ] Feature / enhancement
- [ ] Documentation only
- [ ] Refactor / tooling
- [ ] Release

## Verification

- [ ] `cd A2D && ./gradlew compileDebugKotlin` passes
- [ ] `cd A2D && ./gradlew test` passes (or a focused `--tests` run)
- [ ] `cd A2D && ./gradlew assembleDebug` passes
- [ ] Manual device check (if UI changed)
- [ ] Docs-only: Markdown links and code fences checked

## Documentation

- [ ] `README.md` updated if behavior/architecture/toolchain changed
- [ ] `AGENT.md` updated if file map/commands/rules changed
- [ ] Implementation-log entry appended; commit SHA added only after the green build/tests
- [ ] `CHANGELOG.md` updated for user-visible changes
- [ ] Release: `versionCode`/`versionName` bumped, versioned CHANGELOG section + user-facing note added
- [ ] Feature/ADR/known-issue doc updated if applicable

## Commit ID

- [ ] Verified commit SHA recorded after the build/tests passed
- [ ] Not green → logged as `pending` or `blocked` (never a SHA)

## Notes for reviewers

<!-- Room schema/migration impact, gate semantics changes, deployment impact, rollback plan. -->
