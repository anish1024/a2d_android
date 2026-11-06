# Implementation Log — Milestones 3–4 (2026-10)

- Date range: 2026-10 (historical; exact dates were not recorded)
- Topics: Compose theme, ViewModels, navigation, UI screens, USB deployment, release signing,
  app bundle, Play Store checklist
- Feature IDs: none (milestones 3.1–4.3)
- Entries: 6 milestones + overall summary
- Approx size: ~2.4k tokens
- Previous shard: [Milestones 0–2](2026-10-milestones-0-2.md)
- Index: [Implementation log](../implementation-log.md)
- Next shard: [Task card redesign](2026-10-redesign-01.md)

> Historical snapshot migrated from the former `README.md` Appendix A. Superseded files and older
> toolchain versions are kept for history; use [`README.md`](../../README.md) for current truth.
> Commit IDs were not recorded for these milestone entries.

## Entries

### Milestone 3.1 — Material 3 Theme Implementation ✅

**Status:** Complete

| File | Action |
| --- | --- |
| `ui/theme/Color.kt` | ✏️ rewritten to spec — ADHD-friendly palette: `CalmGreen #2E7D32`, `CalmTeal #00695C`, `SoftGrey #F5F5F5`, `DarkGrey #212121`, `AlertRed #C62828` (replaces template Purple/Pink colors) |
| `ui/theme/Theme.kt` | ✏️ rewritten to spec — fixed `LightColorScheme` (primary=CalmTeal, secondary=CalmGreen, background=SoftGrey, surface/onPrimary=White, error=AlertRed), `A2DTheme(content)` with no dark/dynamic-color branches |
| `ui/theme/Type.kt` | 🗑️ deleted — scaffold template file not in the spec's directory tree; its `Typography` reference was removed with the new `Theme.kt` |

---

### Milestone 3.2 — ViewModels with Gate Integration ✅

**Status:** Complete (2 files created verbatim from spec)

| File | Content |
| --- | --- |
| `ui/task/TaskViewModel.kt` | `@HiltViewModel` injecting `TaskRepositoryImpl`, `SerialAndGateProcessor`, `TaskListPipelineProcessor`. Exposes `tasks: StateFlow<List<Task>>` (`WhileSubscribed(5000)`) and `milestoneOutput: StateFlow<Int>`. `createSampleTask` saves a 2-action task; `toggleActionItem` updates the sub-item → Tier-1 `processActivityWithGate` → `saveTask` → Tier-2 `recalculatePipeline()`; `deleteTask` cascades then recalculates |
| `ui/logicgate/LogicGateViewModel.kt` | `@HiltViewModel` wrapping `SerialAndGateProcessor.processPulse`; exposes `lastResult`/`errorMessage` StateFlows, catching `IllegalArgumentException` for bad pin/pulse input |

---

### Milestone 3.3 — Compose Screens & Main Navigation ✅ (Sprint 3 verification: `./gradlew assembleDebug` → BUILD SUCCESSFUL)

**Status:** Complete

| File | Action |
| --- | --- |
| `ui/task/TaskScreen.kt` | ✅ created per spec — header + `Milestone Complete [GATE 1]` / `Milestone Incomplete [GATE 0]` status, `+ Task` button, `LazyColumn` of `ElevatedCard`s with per-action `Checkbox`es and `Delete` button |
| `ui/logicgate/LogicGateScreen.kt` | ✅ created per spec — arity `OutlinedTextField`, `Pulse HIGH` / `Pulse LOW (Reset)` buttons, error text, result `Card` (pin/pulse/latched/output/reset) |
| `ui/MainActivity.kt` | ✅ created at new package path `com.a2d.app.ui` — `@AndroidEntryPoint`, `A2DTheme`, `Scaffold` + `NavigationBar` with **Tasks** / **Logic Gate** tabs, `hiltViewModel()` wiring for both ViewModels |
| `AndroidManifest.xml` | ✏️ rewritten to spec — `.A2DApplication`, launcher activity now `.ui.MainActivity`, label `A2D Assistant`, system icon `@android:drawable/sym_def_app_icon`, theme `@android:style/Theme.Material.Light.NoActionBar` |
| `ui/MainActivity.kt` (old path) | 🗑️ deleted — scaffold activity at `com.a2d.app.MainActivity` |
| `ui/home/HelloWorldScreen.kt` | 🗑️ deleted — scaffold placeholder screen |

**Verification:**
```
$ ./gradlew assembleDebug
BUILD SUCCESSFUL in 5m 14s   (41 actionable tasks)
→ app/build/outputs/apk/debug/app-debug.apk (10,096,203 bytes, rebuilt Oct 7 21:36)
```

**Notes:**
- The app source tree now matches the spec's directory tree 1:1 (spec lists no `res/` folder; the
  scaffold's `res/drawable/ic_launcher.xml`, `res/values/strings.xml`, `res/values/themes.xml` are
  left in place but unreferenced — harmless, and removed automatically by resource shrinking in release).
- Build warning-free at the Kotlin level; Hilt deprecation note only in generated Java glue.

---

### Milestone 4.1 — USB Debugging Setup & Deployment ⏳ (blocked: no device attached)

**Status:** Build/deploy pipeline ready; physical-device step cannot be completed autonomously.

- `adb devices` → `List of devices attached` (empty). Re-checked after Sprint 4 builds — still none.
- Debug APK freshly built and ready: `A2D/app/build/outputs/apk/debug/app-debug.apk` (≈10.1 MB).
- adb works (daemon starts cleanly from `~/Library/Android/platform-tools`).

**Pending user actions (per spec + `Deployment/a2d_build.md`):**
1. On the phone: **Settings → About phone → tap Build number 7×** → enable **USB debugging** in Developer options.
2. Connect via USB, accept the **"Allow USB debugging?"** RSA prompt (check *Always allow*).
3. Verify: `adb devices` shows `device` (not `unauthorized`/`offline`).
4. Install with either:
   - `./Deployment/a2d_deploy.sh` (repo script — resolves the APK relative to the script), or
   - `cd A2D && ./gradlew installDebug`
5. On-device acceptance checks from the spec:
   - `+ Task` inserts a task with 2 sub-actions
   - toggling **both** sub-actions flips the card to `COMPLETED`
   - header flips to `Milestone Complete [GATE 1]`
   - **Logic Gate** tab sends manual pulses to pin `2` (2× `Pulse HIGH` → `Gate Output: 1`; `Pulse LOW` → reset)

---

### Milestone 4.2 — Production Key Generation & App Bundle Build ✅

**Status:** Complete — signed `app-release.aab` produced

**1. Keystore generated** (spec command, run in `A2D/` project root, JDK 17 `keytool`):
```
keytool -genkey -v -keystore a2d-release-key.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias a2d-key-alias \
  -dname "CN=A2D Developer, OU=Mobile, O=A2D, L=San Francisco, S=CA, C=US" \
  -storepass a2dpassword -keypass a2dpassword
→ A2D/a2d-release-key.jks (2,758 bytes), 2048-bit RSA, valid 10,000 days
```
Verified with `keytool -list` → `a2d-key-alias, 7 Oct 2026, PrivateKeyEntry`, SHA-256 fingerprint `C6:C6:9E:65:...:CE`.

**2. `keystore.properties` verified** (already the audit-corrected form):
```properties
KEYSTORE_PATH=a2d-release-key.jks      # resolved via rootProject.file(...) from app/
KEYSTORE_PASSWORD=a2dpassword
KEY_ALIAS=a2d-key-alias
KEY_PASSWORD=a2dpassword
```

**3. Signed release bundle built:**
```
$ ./gradlew bundleRelease
BUILD SUCCESSFUL in 6m 46s   (52 actionable tasks)
→ app/build/outputs/bundle/release/app-release.aab (2,684,158 bytes, Oct 7 21:45)
```
Pipeline confirmed: `validateSigningRelease` → `minifyReleaseWithR8` → `shrinkBundleReleaseResources` → `signReleaseBundle` — i.e. release signing config, R8 obfuscation (with the spec's `-keep` rules for models/entities/relations) and resource shrinking all ran successfully.

**4. Git hygiene:** added `keystore.properties` and `*.jks` to `A2D/.gitignore` (spec marks them git-ignored).
⚠️ `A2D/keystore.properties` was **already tracked** in an earlier commit, so `.gitignore` alone won't hide it — run `git rm --cached A2D/keystore.properties` and commit if you want it removed from the index. No git commits were made by this run.

---

### Milestone 4.3 — Google Play Store Release Checklist ⏳ (manual/online steps — cannot be automated here)

**Status:** AAB is ready for upload; all remaining items require the Play Console and design assets.

| # | Spec step | State |
| --- | --- | --- |
| 1 | Create Google Play Console account + app **"A2D - Adult ADHD Assistant"** | ⏳ manual (needs paid Console account) |
| 2 | Store listing assets: 512×512 icon, 1024×500 feature graphic, ≥2× 1080×1920 screenshots | ⏳ not in repo — must be produced (screenshots can come from the device run in 4.1) |
| 3 | Content declarations: Privacy Policy (local SQLite, zero telemetry), 18+, Data Safety = no data collected/shared | ⏳ manual forms |
| 4 | Testing → Internal Testing → upload `A2D/app/build/outputs/bundle/release/app-release.aab` + tester emails | ⏳ blocked on steps 1–3 |
| 5 | Promote Release → Production → submit for review | ⏳ last step |

---

### Overall Summary

| Sprint | Milestones | Result |
| --- | --- | --- |
| 0 — Environment & scaffolding | 0.1, 0.2 | ✅ 2/2 (JDK 17, wrapper 8.9, SDK 34, adb verified; config files written; `./gradlew help` OK) |
| 1 — Domain, engines, tests | 1.1, 1.2, 1.3 | ✅ 3/3 — **`./gradlew test`: 8/8 tests pass** |
| 2 — Room, repository, DI | 2.1, 2.2, 2.3 | ✅ 3/3 — **`./gradlew compileDebugKotlin`: BUILD SUCCESSFUL** |
| 3 — Compose UI | 3.1, 3.2, 3.3 | ✅ 3/3 — **`./gradlew assembleDebug`: BUILD SUCCESSFUL** (app-debug.apk ≈10.1 MB) |
| 4 — Deploy & release | 4.1, 4.2, 4.3 | ⚠️ 1/3 — 4.2 ✅ signed `app-release.aab` (2.68 MB, R8 + shrinking); 4.1 & 4.3 blocked on physical device / Play Console (manual) |

**Final state:** all 31 spec source files implemented 1:1 under `A2D/`, all three verification commands green, debug APK + signed production AAB built. Remaining work is device onboarding (`./Deployment/a2d_deploy.sh`) and the Play Console submission.

---
