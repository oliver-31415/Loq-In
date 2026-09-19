# Upstream 2.2.9 → 2.3.1 hand-port plan for Loq In

Status: planning document. Written 2026-09-16.
Target: port the still-relevant parts of upstream Switchly v2.2.9, v2.3.0, v2.3.1 into Loq In.

## 0. How to use this document

- Every step is written so a new agent can execute it without reading this whole file first: goal, branch, files, exact changes, upstream reference, verification, risks, done criteria.
- Keep the checkboxes updated as you go (`[x]`).
- If a step is blocked or the plan turns out to be wrong, record the reason inline under the step instead of silently skipping it.
- Commits: match the existing convention used by Phase 0, e.g. `Upstream phase 0: bundle ML Kit barcode model for offline scanning`. For this plan use `Upstream 2.3.x: <short description>` or `YT experiment: <short description>`.
- Never commit to `main` directly. Integration branch for this work is `feature/upstream-work`.

### Upstream reference commands

```
git log --oneline be4ab60..bf9526b                 # the 5 upstream commits
git diff  be4ab60..bf9526b -- <path>               # full upstream delta for a file
git diff  61c5c58..2f8159f -- <path>               # just 2.3.0
git show  upstream/main:<path>                     # final upstream file
git show  2f8159f -- <path>                        # single commit
```

Upstream source paths are under `app/src/main/java/at/saltyy/switchly/...`; our paths are
`app/src/main/java/com/oliver/loqin/...`. Everything must be adapted, never copied verbatim.

## Progress log

| Date | Step | Branch | State | Notes |
|------|------|--------|-------|-------|
| 2026-09-16 | W1.1 A1 | `feature/upstream-w1-safety` | Done — `3916a71` | See "W1.1 implementation + test evidence" below |
| 2026-09-16 | W1.2 A7 | `feature/upstream-w1-safety` | Done — `ab14bb5` | See "W1.2 implementation + test evidence" below |
| 2026-09-17 | W1.3 C3 | `feature/upstream-w1-safety` | Done — `71280c1` | See "W1.3 implementation + test evidence" below |
| 2026-09-17 | W1.4 A6 | `feature/upstream-w1-safety` | Done — `f768d95` | See "W1.4 implementation + test evidence" below |
| 2026-09-17 | W1 merge | → `feature/upstream-work` | Done — `abc4051` | Fast-forward; WS1 complete |
| 2026-09-17 | W2.1 A4 | `feature/upstream-w2` | Done — `d10ceac` | Per-visit limits now enforced; unit tests added |
| 2026-09-17 | Limit editor redesign | `feature/upstream-w2` | Done — `fb01e65` | Not upstream work; owner request |
| 2026-09-17 | Picker tile limit overflow fix | `feature/upstream-w2` | Done — `05cddac` | Not upstream work; owner request |
| 2026-09-17 | Breaks editor redesign | `feature/upstream-w2` | Done — `1b0b64a` | Not upstream work; owner request; uses caps + usage + clear |
| 2026-09-17 | Compact limit dialogs + blank/blocked fixes | `feature/upstream-w2` | Done — `62440bd` | No-scroll, blank saves as no limit, fully-blocked sentence |
| 2026-09-17 | Limited badge in picker | `feature/upstream-w2` | Done — `6bf74c6` | Restricted apps no longer look fully blocked |
| 2026-09-17 | Change-count save notices | `feature/upstream-w2` | Done — `b2cfffa` | App rules + Hidden apps report changed apps, not totals |
| 2026-09-17 | W2.2 A5 | `feature/upstream-w2` | Done — `a8d8ec4` | Session limits survive pause/lock/profile switch; two branches still need a manual check |
| 2026-09-17 | Refill mode in limit editor | `feature/upstream-w2` | Done — `f827463` | Exposes Daily / Protection restart; renames "Single session cap" |
| 2026-09-17 | Refill control polish | `feature/upstream-w2` | Done — `52ee896` | Segmented track + session wording everywhere in the editor |
| 2026-09-17 | **WS2 complete** | `feature/upstream-w2` | A4 + A5 done | Remaining WS2 follow-ups: manual pause/off-on checks on a real device |
| 2026-09-17 | W3.1–W3.3 A3 | `feature/upstream-w2` | Done — `a6ef23f` | Website path rules with wildcards; store + service + UI |
| 2026-09-17 | Path-rule robustness | `feature/upstream-w2` | **Reverted** — `005b5a8` | Owner report: the change broke Firefox |
| 2026-09-17 | Firefox autocomplete false-block fix | `feature/upstream-w2` | Done — `b34ad98` | Root cause of the "broken Firefox" report |
| 2026-09-17 | Firefox internal-screen false blocks | `feature/upstream-w2` | Done — `67e5b09` | Verified side by side against upstream Switchly 2.2.9 on the emulator |
| 2026-09-18 | Firefox path rules + Chrome redirect research | `feature/upstream-w2` | Done | Firefox path rules verified working; Chrome NTP not reachable, about:blank stays |
| 2026-09-18 | Firefox 155 Compose toolbar path rules | `feature/upstream-w2` | Done — `182d711` | The user's Firefox 155 exposes the URL via a Compose test tag; path rules now work there too |
| 2026-09-18 | Firefox heavy-page detection | `feature/upstream-w2` | Done — `df1f217` | BFS toolbar scan; real pages (ABC News/Wikipedia) now detect |
| 2026-09-18 | Firefox 97–155 sweep | `feature/upstream-w2` | 16/16 pass | Only first-run promos masked the toolbar temporarily |
| 2026-09-18 | Samsung Internet + path backup + independence | `feature/upstream-w2` | Done — `778d38b` | Samsung host-only blocking; path rules export and survive host-rule deletion |
| 2026-09-18 | **W4.1 A2** | `feature/upstream-w4-a2` | Done — `7b6e5f9` | Budgeted root lookup + scan telemetry; no regressions on emulator |
| 2026-09-18 | **W4.2 B** experiment | `experiment/yt-upstream-2.3.1` | Done — `ad814d2`, `7b21a85`, `b5ae16d` | Upstream evidence port + coordinate-tap removal; see `docs/yt-experiment-results-2.3.1.md` |

### W1.1 implementation + test evidence (2026-09-16)

Changed files:
- `app/src/main/java/com/oliver/loqin/feature/blocker/BlockerActivity.kt` — guarded `WindowCompat.enableEdgeToEdge` with `runCatching`; moved `currentActivityRef` publication to after view init; `onNewIntent(intent: Intent)` now checks protection-enabled, finishing/destroyed and `areBlockerViewsInitialized()`; `applyFromIntent` no longer touches uninitialized views; added `reasonSummaryView`; info-button tint now set from `AccentColor` in code; `debugInfoButton` retyped `ImageButton` so `imageTintList` is available.
- `app/src/main/java/com/oliver/loqin/data/prefs/LastBlockReasonStore.kt` — added `Snapshot.userFacingSummary(): String?` (rule, else source, plus profile; returns null when nothing to show).
- `app/src/main/res/layout/activity_blocker.xml` — explicit colours (`loqin_bg`, `loqin_text_primary`, `loqin_text_secondary`), removed Material theme attrs and the `MaterialButton`/`LoqIn.Button` style, added `@id/blocker_reason_summary`, removed unused `blocker_title_row` id, transparent info-button background.
- `app/src/main/res/values/strings_home.xml` + `values-de/strings_home.xml` — added `block_reason_summary_fmt` ("Blocked by: %1$s").

Deviations from the plan (deliberate):
- `userFacingSummary()` returns `String?` and the blocker hides the line when null, instead of upstream's hardcoded English "protection rule" fallback.
- The close button is a plain `Button` without `@style/LoqIn.Button`; the blocker theme does not set `android:buttonStyle`, so this removes the Material3 style dependency. Visual note: the button is no longer the expressive pill (upstream made the same trade-off for crash safety).
- YouTube `bringYouTubeHomeToFront` from upstream 2.3.0 intentionally **not** ported here (belongs to W4.2).

Emulator test evidence (AVD `HolyPixel`, Android 36, x86_64, no root):
- Whole-app YouTube block: blocker shown → UI dump: `App blocked | YouTube | You can't use this app while Loq In is active. | Blocked by: Blocked app list • Default | OK`. Rotation landscape/portrait with blocker visible: no crash.
- Re-entry: the service issued repeated `BlockerActivity` starts while already visible (`LAUNCH_SINGLE_TASK`), exercising `onNewIntent` — no `lateinit` crash, content stayed consistent.
- In-app surfaces on official YouTube (`p_default_block_yt_shorts`, `p_default_block_yt_subscriptions`, `p_default_block_yt_you` all true):
  - Shorts tab → `Shorts is blocked! ... Blocked by: Shorts is blocked! • Default` (twice, before and after config reload).
  - Subscriptions tab → `Subscriptions is blocked! ... Blocked by: Subscriptions is blocked! • Default`.
  - You tab → `You is blocked! ... Blocked by: You is blocked! • Default`.
- No `FATAL EXCEPTION` in logcat for any run. Screenshots of the blocker are black because `BlockerActivity` sets `FLAG_SECURE` (expected); UI text was verified via `uiautomator dump`.
- Facebook Reels could **not** be tested on this emulator: Facebook is not installed, no Google account for Play Store, and the downloaded `com.facebook.lite` 529.0.0.7.105 APK is arm64-only (`INSTALL_FAILED_NO_MATCHING_ABIS` on x86_64). Needs an x86-compatible APK or a physical device to complete.

### W1.2 implementation + test evidence (2026-09-16)

Changed files:
- `app/src/main/java/com/oliver/loqin/util/AppBlockSafety.kt` — `isStrictModeEnabled` (dev-unlock pref) replaced by `isSettingsBlockingProtectionEnabled`: requires `AppLockStore.isStrictProtectionEnabled` **and** an active `DPMReceiver` admin / profile owner / device owner; `canAllowStrictModeBlocking` uses the new gate; removed the now-unused `APP_PREFS`/`KEY_DEV_UNLOCKED` constants.
- `app/src/main/java/com/oliver/loqin/feature/picker/AppPickerActivity.kt` — requirements dialog positive button now opens `AppLockSettingsActivity` ("Set up protection"), matching upstream 2.2.9.
- `app/src/main/res/values/strings_app_picker.xml` + `values-de/strings_app_picker.xml` — title changed to "Protection setup required" / "Schutz-Einrichtung erforderlich"; message names uninstall protection with Device Admin + emergency PIN; added `app_picker_settings_requirements_setup_action`.

**Correction to the earlier risk note:** the gate is consulted **only in the picker at selection time**. The accessibility service never re-checks it, so already-configured Settings rules keep being enforced after Device Admin is revoked. There is no silent unblocking of existing rules.

Emulator test evidence (AVD `HolyPixel`, Android 36, x86_64):
- Gate blocked: `pref_dev_unlocked=true` only (developer mode, no strict protection/admin) → selecting Settings in the picker shows `Protection setup required | To block Settings, uninstall protection with Device Admin must be active and Emergency Unlock with an Emergency PIN must already be configured. | Cancel | Set up protection`.
- Gate allowed: `dpm set-active-admin com.oliver.loqin.loqindev/com.oliver.loqin.receiver.DPMReceiver` + `pref_app_lock_strict_protection=true` + `pref_emergency_pin=1234` → selecting Settings proceeds through `Block Settings anyway?` → `Are you absolutely sure?` → `Block Settings`, tile becomes selected, `Save` writes `blocked_apps_Default = {com.android.settings}`.
- End-to-end: with accessibility enabled, launching system Settings shows `App blocked | Settings | ... | Blocked by: Blocked app list • Default`.

Testing notes (W1.2):
- The gate is **selection-time only**; revoking Device Admin afterwards does not unblock an existing rule (matches upstream).
- Prefs must be edited while the app process is dead (verify `pidof` is empty). `dpm set-active-admin` can start the app process to deliver `DEVICE_ADMIN_ENABLED`; if you edit prefs while that process is alive it will overwrite your file from its in-memory copy. Sequence: `am force-stop` → verify no pid → `dpm set-active-admin` → `am force-stop` again → edit/push prefs → launch.
- `uiautomator dump` fails with "could not get idle state" while LoqIn's home screen is open (a live-updating element prevents idle). Workaround used: `adb shell dumpsys activity top` gives the view hierarchy with absolute bounds; `tap_id.py`-style parsing was used for the pencil/hero edit button. Dumping works on the picker and dialogs.
- To tap dialog buttons by text, match the **exact** node text ("Open Rules" also matches the dialog title "Open Rules?").
- Settings navigation: gear → Settings → Controls (section header) → **Feature access** → e.g. "Change emergency PIN". Tapping a section header does nothing; protected sections are locked while protection is enabled.

### W1.3 implementation + test evidence (2026-09-17)

Changed files:
- `app/src/main/java/com/oliver/loqin/data/prefs/EmergencyPinStore.kt` — added `canResetWithoutCurrentPin` (requires base protection off, no temporary override, no active/paused Emergency Unlock) and `resetPinWhenFullyDisabled` (4–8 digits, re-checks the gate, clears all known keys, then writes); `setPin` now removes legacy keys; `removePin` reuses the shared cleanup.
- `app/src/main/java/com/oliver/loqin/ui/dialog/EmergencyPinDialog.kt` — `showEnterPin` gained optional neutral button params; `showChangePinFlow` offers "Reset PIN" only when `canResetWithoutCurrentPin`; new confirmation dialog; `showCreatePin` can persist via `resetPinWhenFullyDisabled` and reports success/failure pills.
- `app/src/main/java/com/oliver/loqin/feature/profiles/TempPauseDialogs.kt` — call site updated for the new parameter order (named `onSuccess`).
- `app/src/main/res/values/strings_settings_blocking.xml` + `values-de/…` — added `emergency_pin_reset_action`, `emergency_pin_reset_title`, `emergency_pin_reset_message`, `emergency_pin_reset_requires_disabled`, `emergency_pin_reset_done`.

Deliberately skipped: `SecureInputFields.kt` (our `PinEntryDialog` is the rebuilt UI), default-prefs PIN scanning (our reader only uses `loqin_prefs`), and any UI on the normal Emergency Unlock verify paths (reset is only offered in the change flow).

Emulator test evidence (AVD `HolyPixel`, Android 36, x86_64):
- Protection fully off → Settings → Controls → Feature access → Change emergency PIN: current-PIN dialog shows `Reset PIN` neutral.
- Reset path: `Reset PIN` → confirmation `Reset emergency PIN?` → `Reset PIN` → create dialog → entered `4321` + re-entered → `pref_emergency_pin` became `4321`.
- Old PIN rejected: entering `1234` in the verify dialog did not proceed; entering `4321` closed the dialog and opened the change flow (still requires the current PIN first).
- Negative case: with `emergency_bypass_paused=true`, `emergency_bypass_paused_remaining_ms=600000` and today's `emergency_last_used_epoch_day`, the current-PIN dialog shows only `Cancel` — no `Reset PIN`.
- Protection enabled: the Feature access screen is locked by `LoqInAppAccessGuard` (warn pill), so the change flow is unreachable while protection is active; `canResetWithoutCurrentPin` is a second layer.

### W1.4 implementation + test evidence (2026-09-17)

Changed files:
- `app/src/main/java/com/oliver/loqin/blocking/UsageAccessFallbackBlocking.kt` — `sync()` now evaluates `shouldRun` on a single-thread daemon executor, posts the start/stop decision to the main looper, drops stale requests via a generation counter, skips `startForegroundService` when `isRunning()` (fresh heartbeat), and logs scheduling failures. This avoids opening the FGS deadline while another lifecycle callback (e.g. `AccessibilityService.onDestroy`) still occupies main.
- `app/src/main/java/com/oliver/loqin/blocking/UsageAccessFallbackBlockingService.kt` — `onCreate` promotes to foreground **before** constructing `UsageEventsForegroundResolver`/`PowerManager`/`KeyguardManager`; `createChannelAndPromote` uses `ServiceCompat.startForeground(...)` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+.

Emulator test evidence (AVD `HolyPixel`, Android 36, x86_64):
- Enabled Android Advanced Protection Mode with `adb shell cmd advanced_protection set-protection-enabled true`, granted Usage Access (`appops set … android:get_usage_stats allow`), disabled Accessibility, kept protection on with Settings blocked.
- Fallback started and enforced: `limited_usage_fallback created mode=basic_app_only`; launching system Settings showed the blocker.
- When Accessibility was re-enabled, the fallback stopped: `limited_usage_fallback destroyed`.
- Stress toggling Accessibility off/on: no `FATAL`, no `ForegroundServiceDidNotStartInTimeException`; the fallback service started/stopped cleanly.
- Expected platform behavior observed: when Accessibility is disabled while the app is in the background, the start is denied by Android (`ForegroundServiceStartNotAllowedException`) and now logged as `limited_usage_fallback start failed … mAllowStartForeground false`. Previous code already swallowed this; it is not a regression, and the start succeeds when the app is foregrounded again.

Testing notes (W1.4):
- Android Advanced Protection Mode can be toggled on the emulator with `adb shell cmd advanced_protection set-protection-enabled true|false` (remember to turn it back off afterwards).
- The fallback only runs when all of: advanced protection on, Accessibility unavailable, protection enabled, profile has effective blocked apps, Usage Access granted.

Testing notes (W1.3):
- The PIN dialogs use a custom digit UI, so `adb shell input text` only works when the dialog has focus (it auto-focuses when freshly shown). If input stops registering, re-tap the digit boxes area and type again.
- Setting `emergency_bypass_paused` without `…_remaining_ms` and today's `emergency_last_used_epoch_day` is cleared by `EmergencyBypassStore` on read, so the paused negative test needs all three keys.
- Emulator state after the W1.3 run: protection enabled, accessibility enabled, emergency PIN `4321` (changed during the reset test).

Emulator gotchas for future sessions:
- `adb shell am force-stop <pkg>` **clears** `enabled_accessibility_services` on this image. Re-enable after every force-stop:
  `adb shell settings put secure enabled_accessibility_services com.oliver.loqin.loqindev/com.oliver.loqin.blocking.LoqInAccessibilityService && adb shell settings put secure accessibility_enabled 1`
- Debug builds use `applicationIdSuffix ".loqindev"`, so prefs live in `shared_prefs/loqin_prefs.xml` and `shared_prefs/com.oliver.loqin.loqindev_preferences.xml` (default prefs hold in-app rules as `p_default_<rule>`).
- `adb root` is not available on the Play Store image, so non-exported activities cannot be started directly.

## 1. Hard constraints

1. **Fully offline app.** No cloud, no accounts, no Firebase/Crashlytics, no billing/premium.
   - Never port: `CrashlyticsContext.kt`, any `CrashlyticsContext.syncAsync(...)` call, `AccountSignInFlow.kt`, `auth/`, `premium/`, `BillingDiagnosticsStore.kt`, `PremiumInfoActivity`, `PremiumRedeemActivity`, Google services build plugins, Firebase Crashlytics Gradle tasks.
   - Our diagnostics equivalent is `com.oliver.loqin.data.prefs.DiagnosticsTimelineStore` (already ported in Phase 0).
2. **Do not touch the build toolchain.** Stay on AGP 8.9.1 / Gradle 8.11.1 / compileSdk 36. Upstream's AGP 9 / compileSdk 37 / product-flavor migration is out of scope and not needed by any feature in these releases.
3. **Package/identifier rules.** Keep `com.oliver.loqin`, `loqin_prefs`, our resource names and our UI design language. Do not introduce `at.saltyy.switchly` or `switchly_*` identifiers.
4. **Preserve user data.** When porting a store, keep our existing preference keys. Do not rename existing keys.
5. **CLAUDE/agent rule:** no code comments unless they carry non-obvious intent. Upstream comments can be reworded and kept when they explain a real OEM/behavior trap.
6. **YouTube is special.** Loq In has its own rebuilt YouTube in-app blocking and has removed the user-facing mini-player and PiP rules. Any YouTube logic from upstream is only ever ported on a separate experiment branch (WS4.2) for an A/B comparison. Never mix YT logic ports into WS1–WS3.
7. **Emergency PIN has been rebuilt by us** (`PinEntryDialog` + `EmergencyPinDialog`, 4–8 digits, change requires current PIN). Do not port upstream's `SecureInputFields` UI or replace our dialog.

## 2. Current state audit (verified against the repo)

### Branches
- `main` is an ancestor of `feature/upstream-work`.
- `feature/upstream-work` is 32 commits ahead of `main` and is the integration branch. It contains Phase 0 plus UI work (control-mode gating, stats redesign, onboarding control-modes page, app picker grid, etc.). Working tree is currently clean.
- Phase 0 commits already in `feature/upstream-work`:
  - `32fe3e8` ML Kit bundled barcode model (`com.google.mlkit:barcode-scanning:17.3.0`) instead of play-services (E3).
  - `933ba86` Wi-Fi trigger FGS contract + background-location continuation (E4, E5).
  - `567e0de` `DiagnosticsTimelineStore`, `ProtectionStateProvider`, `SettingsSchemaMigration` (D1 partial, D2, D4).
  - `3b4fe80` `ControlModeGuidance` + silent foreign NFC tags (E1, E2).

### Phase 0 gaps (verified)
- E10 (edge-to-edge overloads) **not done**: `EdgeToEdgeUtils` still only has `setupClassic`.
- D1 is **partial**: timeline hooks exist only in `AppLogStore` and `NfcDiagnosticsStore`. Missing hooks: `SwitchModeStore`, `AutomationModeStore`, `LastBlockReasonStore`, `ProfileStore`.
- D5 (support report surfacing timeline/state) **not done**: `SupportActivity` is clipboard-only plain text.
- `LastBlockReasonStore.Snapshot` has no `userFacingSummary()`; the blocker has no "Blocked by:" line.
- No test source set and no junit dependency exists (`app/src/test` is absent).

### Verified facts that change the original plan
- **A1** (blocker): our `BlockerActivity.kt` still calls `WindowCompat.enableEdgeToEdge(window)` unguarded (L86–88), sets `currentActivityRef` before the early disabled-return (L91), has unguarded `onNewIntent(intent: Intent?)` (L152–156), and `activity_blocker.xml` still resolves `?attr/*` Material theme attributes. Real crash surface.
- **A4** (per-visit): confirmed broken promise. Our UI writes `SessionLimitStore` (picker, QuickLimitDialogs, MainActivity, stats) but `LoqInAccessibilityService` never references `SessionLimitStore` and `resolveAppBlockDecision` has no per-visit parameter.
- **A5** (session reset): our `SwitchModeStore` still calls `bumpLimitSessionGeneration(ctx)` from ~10 call sites (L221, 311, 378, 483, 562, 602, 626, 650, 678, 719), i.e. every effective state change bumps the generation. Our service also wipes session counters in `clearActiveLimitSession()` (service L1645) on screen-off/keyguard/disabled/bypass/profile-missing, which contradicts the upstream "session survives lock/pause" semantics. Service also already has `UsageLimitSessionRuntimeStore` restore-compatible `get()` (returns null on generation mismatch). A5 is therefore a **store + service atomic change**, not a store-only change.
- **A2** (work budget): `AccessibilityWorkBudget.kt` does not exist here. Our service calls `rootInActiveWindow` directly at L1403, 1451, 3558, 3575, 5443, 5908, 8095 and `windows` at L2957, 3590, 8633. There is no worker-thread root lookup and no `currentRoot(event.source)` preference.
- **A7** (settings gate): `AppBlockSafety.isStrictModeEnabled` reads `pref_dev_unlocked` (developer mode), not real Device Admin. Our `AppLockSettingsActivity` already treats strict protection as "pref + DeviceAdmin/owner active". The gate must move to that definition.
- **C3** (PIN): our app has **no reset-without-current-PIN path at all**. `removePin()` is only called from the verified change flow (`EmergencyPinDialog.kt:96`). Upstream's security fix is therefore mostly already satisfied by construction. What is still worth porting: `canResetWithoutCurrentPin` / `resetPinWhenFullyDisabled` guards for any future reset path, and legacy-key cleanup in `setPin`. `SecureInputFields` is a design conflict and must not be ported.
- **A6** (usage fallback): our `UsageAccessFallbackBlocking.sync()` (L68–86) is synchronous on the caller thread and always re-issues `startForegroundService`. It already has `isRunning`/`markRunning`/`markStopped`, so the upstream generation-token + executor + "skip start when already running" change is a clean port.
- **A3** (path rules): `DomainBlockStore.normalize()` strips paths today; `matches(host, rule)` is host-only; service extracts host-only domains (`domainFromText`, L3291). Callers of store matching: `DomainBlockStore.shouldBlockHost` (service L3830, 3968, 4049), `isRuleEnabledForHost`, `getEnabledDomains`, `isHostSelected`. No external caller passes a raw path yet.
- **B1** trap: our YT scan constants at L478–480 (`120 nodes / depth 32 / 25 ms`) carry a device-verified comment ("bottom nav at depth ~13; depth caps below that made every scan return after visiting only the first root chain"). Upstream's `32/6/10 ms` works only because upstream added `currentRoot(event.source)` and event-evidence paths. Do not tighten our YT budgets without the evidence paths and on-device validation.
- **B** removed surfaces: mini-player and PiP are removed as user-facing rules in our fork (service comment at L5288). Cleanup helpers still exist for Shorts-in-PiP. So the YT experiment must specifically re-test those paths before adopting upstream's mini-player/PiP changes.

## 3. Sequence overview

| WS | Name | Steps | Size | Branch |
|----|------|-------|------|--------|
| W0 | Test scaffolding | W0.1 | 0.5d | `feature/upstream-work` |
| W1 | Safety & correctness | W1.1 A1, W1.2 A7, W1.3 C3, W1.4 A6 | 2–3d | `feature/upstream-w1-safety` |
| W2 | Limits | W2.1 A4, W2.2 A5 | 3–4d | `feature/upstream-w2-limits` |
| W3 | Websites | W3.1 A3 store, W3.2 A3 service, W3.3 A3 UI/backup | 3–4d | `feature/upstream-w3-websites` |
| W4 | ANR & YouTube | W4.1 A2 infra, W4.2 YT experiment (B) | 1–2d + 4–7d | `feature/upstream-w4-a2`, `experiment/yt-upstream-2.3.1` |
| W5 | Protection model (optional) | W5.1 D3, W5.2 C1, W5.3 C2, W5.4 D5 | 1–2 weeks | `feature/upstream-w5-protection` |
| W6 | Polish | W6.1 E10, W6.2 E6, W6.3 E7, W6.4 E8, W6.5 E12 | 2–4d | `feature/upstream-w6-polish` |

Ordering rules:
- W1 before W2 (W1 touches `AppBlockSafety`, which the picker and service use; W2 touches the same service).
- W3.1 must land before W2.2? No hard dependency, but W3.1 changes `normalize()`; if W3 ships first, W2 session work must rebase. Prefer W2 then W3, or accept one rebase.
- W4.1 (A2) before W4.2 (YT) because the async root lookup is the foundation for the YT evidence paths.
- W5 only after W1–W3 are stable.

---

## W0 — Test scaffolding

### W0.1 Add JVM unit tests for pure blocking logic
- **Goal:** enable cheap regression tests for `resolveAppBlockDecision` and `DomainBlockStore` before W2/W3 modify them.
- **Branch:** `feature/upstream-work` (small, safe; can be its own commit).
- **Files:** `app/build.gradle.kts`, new `app/src/test/java/com/oliver/loqin/blocking/BlockDecisionTest.kt`, new `app/src/test/java/com/oliver/loqin/data/prefs/DomainBlockStoreTest.kt`.
- **Changes:**
  1. In the `dependencies { ... }` block (around line 149) add `add("testImplementation", "junit:junit:4.13.2")`.
  2. `BlockDecisionTest`: cover hard block (selected, no limit), allow mode (listed/unselected/essential), daily time limit reached, attempt limit only (`opensExceeded`), `force` does not block an unlimited unselected app, and (after W2.1) per-visit cases.
  3. `DomainBlockStoreTest`: `normalize()` host stripping (`www.`, ports, user-info, `*.`), path preservation (after W3.1), `matches()` subdomain behavior. `normalize`/`matches` do not touch `Context` so plain JUnit works. Android framework methods are not reached.
- **Upstream reference:** none (our own).
- **Verification:** `./gradlew :app:testDebugUnitTest`. If the build machine has no Maven cache access, resolve junit first (offline build ≠ offline dependency resolution) — if that's a problem, note it and defer W0.
- **Risks:** none at runtime; tests are dev-only.
- **Done when:** the two test classes exist and the Gradle task passes.

---

## W1 — Safety & correctness

Branch `feature/upstream-w1-safety` off `feature/upstream-work`. Merge back per step or as one PR after manual verification.

### W1.1 A1 — BlockerActivity crash hardening + block-reason summary — **DONE 2026-09-16**
> Implemented on `feature/upstream-w1-safety` (uncommitted). See the progress log above for exact files, deviations and emulator evidence. One open gap: Facebook Reels could not be tested on the x86_64 emulator (arm64-only APK); re-test on a physical device when convenient.

- **Goal:** the blocker screen must never crash the process/service on OEM theme or lifecycle edge cases, and must show *why* the app was blocked.
- **Depends:** none. Do first (safety critical).
- **Files:**
  - `app/src/main/java/com/oliver/loqin/feature/blocker/BlockerActivity.kt`
  - `app/src/main/res/layout/activity_blocker.xml`
  - `app/src/main/java/com/oliver/loqin/data/prefs/LastBlockReasonStore.kt`
  - `app/src/main/res/values/strings_home.xml`
- **Upstream reference:**
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/feature/blocker/BlockerActivity.kt`
  - `git diff be4ab60..bf9526b -- app/src/main/res/layout/activity_blocker.xml`
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/data/prefs/LastBlockReasonStore.kt`
- **Changes:**
  1. Wrap `WindowCompat.enableEdgeToEdge(window)` (L86–88) in `runCatching { }`.
  2. Move `currentActivityRef = WeakReference(this)` (L91) to after all views are resolved (after L137 `btnClose = findViewById(...)`, before `btnClose.backgroundTintList`). This prevents `clearVisibilityState()` from trying to finish a blocker whose views never initialized.
  3. `onNewIntent(intent: Intent)` (non-null signature):
     - `super.onNewIntent(intent)`, `setIntent(intent)`.
     - If `!SwitchModeStore.isEnabled(this)` → `clearVisibilityState("new_intent_while_disabled")`, `finish()`, return.
     - If `isFinishing || isDestroyed || !areBlockerViewsInitialized()` → return.
     - Then `applyFromIntent(intent)`.
  4. Add `private fun areBlockerViewsInitialized(): Boolean` checking `::titleView`, `::appNameView`, `::messageView`, `::reasonSummaryView`, `::debugInfoButton`, `::btnClose` with `isInitialized`.
  5. Add guard at the top of `applyFromIntent`: if views are not initialized, return.
  6. Reason summary:
     - Add `private lateinit var reasonSummaryView: TextView`, find `R.id.blocker_reason_summary`, include it in `areBlockerViewsInitialized()`.
     - In the snapshot branch of `applyFromIntent`: when snapshot is null → `reasonSummaryView.visibility = View.GONE`; else set `reasonSummaryView.text = getString(R.string.block_reason_summary_fmt, snapshot.userFacingSummary())` and `View.VISIBLE`.
     - `LastBlockReasonStore.Snapshot.userFacingSummary()`: `val reason = rule.ifBlank { source.ifBlank { "protection rule" } }; return if (profile.isNotBlank()) "$reason • $profile" else reason`. (Hardcoded English fallback is acceptable and matches upstream; better: pass no fallback and skip the line if blank — choose one and stay consistent.)
     - String `block_reason_summary_fmt` = `Blocked by: %1$s` in `strings_home.xml` next to the other `block_reason_*` strings.
  7. Layout `activity_blocker.xml`:
     - Add a `TextView` `@+id/blocker_reason_summary` between `@id/blocker_message` and `@id/btn_close`, styled like the message but `@color/loqin_text_secondary`, `12sp`, `visibility="gone"`.
     - Theme hardening (recommended, safety screen): replace `?attr/colorSurface` → `@color/loqin_bg`, `?attr/colorOnSurface` → `@color/loqin_text_primary`, the info icon `app:tint="?attr/colorPrimary"` → remove and set tint in code from `AccentColor.getActiveColor(this)`, and replace `com.google.android.material.button.MaterialButton` `@+id/btn_close` with a plain `Button` (its `backgroundTintList` is already set in code at L139). Keep `android:textColor="@android:color/white"` on the button.
  8. **Explicitly excluded:** upstream's `bringYouTubeHomeToFront` / post-ack YouTube redirect change (2.3.0). It belongs to the YT experiment (W4.2). Do not port it here; keep our current `postAckYoutubeHome` behavior unchanged.
- **Verification:**
  - `./gradlew :app:assembleDebug`.
  - Manual: trigger a block on a normally blocked app; confirm the reason line appears when a fresh `LastBlockReasonStore` snapshot exists and is hidden otherwise; tap the info button; rotate the device with the blocker visible; dismiss via button and back.
  - Lifecycle: while the blocker is visible, disable protection from the status notification → blocker must finish without crash. Re-trigger a block afterwards.
- **Risks / gotchas:**
  - Moving `currentActivityRef` changes `clearVisibilityState()` reach: an early-returning instance is no longer stored, which is intended.
  - Do not change the YT branches in `handleCloseAction`.
- **Done when:** build passes, manual checks pass, no `lateinit`/theme exceptions in logcat, reason line behaves.

### W1.2 A7 — Settings blocking requires real Device Admin — **DONE 2026-09-16**
> Implemented on `feature/upstream-w1-safety` (uncommitted); emulator-verified for both gate-blocked and gate-allowed paths. See the progress log for the important correction: the gate is selection-time only, so existing rules are never silently unblocked.

- **Goal:** blocking Settings/System UI is only allowed when uninstall protection is configured *and* backed by an active Device Admin/Managed owner, plus emergency recovery. Developer mode must no longer unlock this.
- **Depends:** none.
- **Files:**
  - `app/src/main/java/com/oliver/loqin/util/AppBlockSafety.kt`
  - `app/src/main/res/values/strings_app_picker.xml` (copy only; check `values-de/` if it exists)
- **Upstream reference:** `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/util/AppBlockSafety.kt`
- **Changes:**
  1. In `AppBlockSafety` replace `isStrictModeEnabled(context)` with `isSettingsBlockingProtectionEnabled(context)`:
     - `if (!AppLockStore.isStrictProtectionEnabled(context)) return false`
     - resolve `DevicePolicyManager`; return true if `isAdminActive(ComponentName(context, DPMReceiver::class.java))` or `isProfileOwnerApp(packageName)` or `isDeviceOwnerApp(packageName)`.
     - Imports: `android.app.admin.DevicePolicyManager`, `android.content.ComponentName`, `com.oliver.loqin.receiver.DPMReceiver`, `com.oliver.loqin.security.AppLockStore`.
  2. `canAllowStrictModeBlocking` uses `isSettingsBlockingProtectionEnabled(context) && hasEmergencyRecoveryConfigured(context)`.
  3. Delete the now-unused `KEY_DEV_UNLOCKED` / `APP_PREFS` constants in `AppBlockSafety` only if nothing else in the file uses them. (`SettingsFragment` has its own copy for the dev menu — leave it.)
  4. Update `app_picker_settings_requirements_message` (and its `values-de` twin) so it names the real requirement: "uninstall protection with Device Admin enabled and Emergency Unlock configured". Current copy says "advanced protection must be enabled", which is close but not the same gate.
- **Verification:**
  - Fresh install, dev mode unlocked, no Device Admin: try to select Settings in the picker → requirements dialog, app not selectable/blocked.
  - Set App lock PIN → enable strict protection (grants Device Admin) → configure Emergency PIN + emergency bypass → try Settings again → two-step warning then allowed.
  - Revoke Device Admin in system settings → verify `canAllowStrictModeBlocking` flips back to false (picker + service behavior).
  - Service path: with Settings blocked and admin revoked, verify the service no longer enforces (logcat/AppLog) and doesn't loop (service L2105 branch only adds a temporary allow when strict mode is required).
- **Risks / gotchas:**
  - **Behavior change:** verified that the gate is selection-time only. Existing Settings rules stay enforced after admin revocation (matching upstream); only new selections require the gate. Optional follow-up (open decision §8): warn when a Settings rule exists but the gate is no longer satisfied.
  - Keep the function name `canAllowStrictModeBlocking` so service/picker call sites compile unchanged.
- **Done when:** gate behaves per verification and copy is updated.

### W1.3 C3 — Emergency PIN security hardening (reduced scope) — **DONE 2026-09-17**
> Implemented on `feature/upstream-w1-safety` (uncommitted) with the store guards plus a "Reset PIN" path in the change flow when protection is fully off. See the progress log for evidence and the emulator test notes.
- **Goal:** guarantee that the Emergency PIN can never be reset while protection is partially active (temporary pause / emergency unlock), and clean legacy key copies on write.
- **Depends:** none.
- **Files:** `app/src/main/java/com/oliver/loqin/data/prefs/EmergencyPinStore.kt`, possibly a small guard test.
- **Upstream reference:** `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/data/prefs/EmergencyPinStore.kt`
- **Changes:**
  1. Add `canResetWithoutCurrentPin(ctx)`: true only when **all** of the following hold: `!SwitchModeStore.isBaseEnabled(ctx)`, `!SwitchModeStore.hasActiveTemporaryOverride(ctx)`, `!EmergencyBypassStore.isActive(ctx)`, `!EmergencyBypassStore.isPaused(ctx)`.
  2. Add `resetPinWhenFullyDisabled(ctx, newPin): Boolean`: trim; require length in `PinEntryDialog.MIN_LENGTH..PinEntryDialog.MAX_LENGTH` (4..8); require `canResetWithoutCurrentPin`; remove all known keys then `setPin`.
  3. `setPin` cleans legacy keys after writing the canonical key.
  4. **Do not port** `SecureInputFields.kt`, PreferenceManager default-prefs scans, or the Premium/dialog UI from upstream. Our `PinEntryDialog` flow (`EmergencyPinDialog.showChangePinFlow`) already requires the current PIN and re-entry.
  5. Grep to confirm there is still no path that calls `removePin()`/`resetPinWhenFullyDisabled()` without current-PIN verification or full disable.
- **Verification:**
  - Change flow: require current PIN, then create new PIN with re-entry, 4–8 digit enforcement, "Remove PIN" only on the verified path.
  - Temporarily pause protection and/or emergency-unlock, then confirm no UI path can reset the PIN (add guards even though no caller exists today).
  - Kill and relaunch app: PIN persists; legacy keys are gone (`adb shell run-as com.oliver.loqin cat shared_prefs/loqin_prefs.xml`).
- **Risks:** `setPin` is called from the verified create flow only, so cleanup is safe.
- **Done when:** functions exist, no unverified reset path, manual flow works.

### W1.4 A6 — Usage Access fallback start/stop hardening — **DONE 2026-09-17**
> Implemented on `feature/upstream-w1-safety` (`f768d95`), emulator-verified with Advanced Protection Mode enabled. See the progress log for evidence.
- **Goal:** stop the fallback service from being started on the main thread during event storms / service teardown, and promote the FGS before touching system services.
- **Depends:** none.
- **Files:**
  - `app/src/main/java/com/oliver/loqin/blocking/UsageAccessFallbackBlocking.kt`
  - `app/src/main/java/com/oliver/loqin/blocking/UsageAccessFallbackBlockingService.kt`
- **Upstream reference:**
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/blocking/UsageAccessFallbackBlocking.kt`
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/blocking/UsageAccessFallbackBlockingService.kt`
- **Changes:**
  1. `UsageAccessFallbackBlocking`: add `mainHandler`/`syncExecutor`/`syncGeneration` exactly as upstream; rewrite `sync()` to evaluate `shouldRun` on the executor, post back on main, drop stale generations, skip start when `isRunning(ctx)`, log scheduling failures. Keep our `isRunning` heartbeat semantics (ours already checks heartbeat staleness; upstream's `isRunning` does the same).
  2. `UsageAccessFallbackBlockingService.onCreate`: call `createChannelAndPromote()` **before** constructing `UsageEventsForegroundResolver` and resolving `PowerManager`/`KeyguardManager`; bail out (and `markStopped`) on promotion failure before touching those services.
  3. `createChannelAndPromote()`: use `ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)` with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+, else 0. Follow the upstream shape; keep our notification channel/text.
- **Verification:**
  - Inspect logcat while toggling Accessibility off/on and enabling Advanced Protection: no `startForegroundService` related ANR, no `ForegroundServiceDidNotStartInTimeException`.
  - Check the fallback runs (`markRunning` heartbeat) when Accessibility is unavailable, and stops (`markStopped`) when it returns.
- **Risks:** service start timing changes could delay the fallback by a few ms; acceptable.
- **Done when:** build passes, no FGS exceptions, fallback still starts/stops correctly.

---

## W2 — Limits

Branch `feature/upstream-w2-limits` off `feature/upstream-work` (after W1 merged).

### Limit dialog fit, blank fields and blocked/limited states (2026-09-17, `62440bd`, `6bf74c6`)

Owner feedback after trying the redesigned dialogs on a phone:
- **Fitting on one page:** inactive sections now collapse to their header row instead of showing dimmed empty controls, the redundant `AlertDialog` title was dropped (the custom app header is the title), and paddings/steppers/pills were compacted in both the app limit editor and the breaks editor. Header sizes reduced (icon 38 dp, section icons 20 dp, steppers 42 dp, pills 32 dp). Verified on the Pixel: the editor opens fully visible and fits with all three sections enabled.
- **Blank fields:** text watchers no longer flip the switch off when the box is cleared, so a section stays open while typing. On save, a blank field means "no limit for that section" (applies as 0 and collapses on the next open). Verified on the emulator: clearing the Daily limit field kept Screen time expanded, Save removed `usage_limit_min__…` and the reopened editor showed the section collapsed.
- **Fully blocked apps:** the editor summary now reads "This app is fully blocked by this profile. Add a limit to allow restricted use." when the app is selected with no limits, instead of claiming it can be opened without restrictions. Works for block mode and allow mode (allow mode: listed but not allowed and not essential).
- **Restricted apps in the picker:** a selected app that also has limits now shows the timer icon + "Limited" instead of the check circle (accessibility description "%1$s, limited"), so restricted apps no longer look fully blocked. The Home blocked-apps list already distinguishes them: rule text shows the limits, and the status chip only appears when the app is actually blocked ("Limit reached", "Open limit reached", "Blocked by profile").

### Change-count save notices (2026-09-17, `b2cfffa`)

Owner request: saving should report how many apps actually changed, not the total number of selected apps, in both the App rules picker and the Hidden apps page.
- `AppPickerActivity.saveManagedApps`: counts the symmetric difference between the pre-edit baseline (`originalManagedPackages`) and the saved set, then re-baselines. The notice is now "Saved %d change(s)."; saving without changes shows "No changes to save.".
- `IgnoredUsageAppsActivity`: keeps the initial usage/app-picker selections, counts the symmetric difference across both lists on save, re-baselines, and shows the same notice pattern (new `hidden_apps_saved_notice` plurals).
- New shared string `save_no_changes`; `app_picker_saved_notice` plurals reworded to changes (EN + DE).
- Verified on the emulator: picker reported `Saved 2 changes.` then `No changes to save.`, and a single toggle reported `Saved 1 change.`; Hidden apps reported `Saved 1 change.` then `No changes to save.`.

### Refill mode in the app limit editor (2026-09-17, `f827463`)

Owner request: the redesigned editor dropped the reset-mode picker, so "Session" limits (A5) were unreachable for new users.
- Screen time card now has a **Refills** segmented control (`Every day` / `Protection restart`) inside the collapsible controls, shown only when the daily limit is on; a hint line explains the selected rule.
- `QuickLimitDialogs` reads `UsageLimitResetStore.getMode`, styles the toggle with the accent, updates the live summary (`Allow up to 60 min per protection session` vs `…min/day`), and persists the choice through `UsageLimitResetStore.setMode` on save (clears it when the daily limit is removed).
- Renamed the third section to **Max per visit** ("Longest single visit") to stop colliding with the reset-mode wording. New EN/DE strings for the toggle and hint.
- Emulator-verified: daily default selected with the midnight hint; a seeded Session limit loads as `Protection restart` with the session hint and the session summary; Save keeps `usage_limit_reset=session`. Both labels fit the button width after shortening `On protection restart` → `Protection restart`.

### Refill control polish (2026-09-17, `52ee896`)

Owner feedback: the dialog still said "per day" in places when Protection restart was selected, and the toggle looked unpolished.
- The Screen time subtitle and the value-field hint now follow the selection: "Total minutes per day" / "Daily limit" ↔ "Total minutes per protection session" / "Limit per session"; the visit-vs-limit warning also switches to a session variant.
- The control is now a segmented track (`bg_segmented_track.xml`, `foqos_surface` rounded 14 dp) with inset, fully rounded buttons: the selected option is accent-filled with on-accent text, the other is transparent with accent text (no more outline seam).
- Emulator-verified with a seeded session limit: summary `Allow up to 60 min per protection session`, subtitle/hint session wording, and the new segmented look.

### W3.1–W3.3 A3 — Website path rules (2026-09-17, `a6ef23f`)

Port of the upstream 2.2.9/2.3.x path-rule support, adapted to our service and UI.
- `DomainBlockStore`: `normalize` now preserves paths (with `*` and `?` wildcards; fragments/whitespace tails dropped; queries intentionally preserved in stored rules), added `hostPart`/`pathPart`/`isPathRule`, `matches(target, rule)` with a glob path matcher, disabled-rule checks are host-only, disabled-set filtering is exact membership, and path rules are excluded in allow mode.
- `LoqInAccessibilityService`: new `tryExtractWebsiteTargetFromBrowserUrlViews` (full host+path from browser URL views); `domainFromText` derives the host from `websiteTargetFromText` (path capture stops at `?`/`#`); all three match sites (`maybeBlockWebsite`, visible-site recheck, post-ack recheck) now pass the target only when its host equals the already-detected host, falling back to the host otherwise; the block reason stores the matched target.
- `ManageBlockedWebsitesActivity`: the add dialog shows a "You can add a path, e.g. youtube.com/shorts/*" helper in block mode, and entering a path in allow mode is rejected with an explanation. Rule rows already render the raw rule string, so path rules display correctly.
- `DomainLimitStore.clearForProfile` already existed in our fork; limits remain host-scoped (path rules only affect matching).

Verification:
- Unit tests (`DomainBlockStoreTest`, 8 tests) cover host normalization, path preservation/truncation, host/path parts, subdomain matching, path globs (`*`, `?`) and the bare-host/path-rule mismatch. `./gradlew :app:testDebugUnitTest` passes.
- Emulator: seeded `example.com/blocked/*`; Chrome blocked on `example.com/blocked/test` (reason "Website is blocked!"), while `example.com/allowed/page` opened normally. UI: the rule list renders `example.com/blocked/*`, adding `youtube.com/shorts/*` via the dialog works with the path helper visible, and in Allow-selected mode the dialog shows "Path rules only work in Block selected mode.".
- Still manual: Firefox host-only fallback and the backup export/import round trip (rules are plain strings, so risk is low).

### Firefox autocomplete false-block fix (2026-09-17, `b34ad98`)

Root cause of the owner's "Firefox is broken/blocked" report (confirmed by installing real Firefox 125.3.0 on the emulator and reproducing):
- On Firefox's new-tab/address-bar edit state, the **address bar's edit field holds the autocomplete suggestion** (e.g. `instagram.com`), and it is focused. Our Firefox detection parsed that suggestion as the current page and hard-blocked it, so opening a new tab or typing while a blocked site was suggested looked like Firefox itself was blocked.
- Fixes in `LoqInAccessibilityService`:
  - Firefox address-bar editing is now detected **before any URL extraction** (`isFirefoxAddressBarActive`): explicit edit-view ids (`mozac_browser_toolbar_edit_url_view`), or a focused editable node inside the toolbar container (covers Firefox's id drift, e.g. `mozac_browser_toolbar_container` in 125.x).
  - The Firefox URL-node fallback no longer accepts toolbar nodes whose id contains `edit` / `autocomplete` / `suggestion`, so typed or suggested URLs can never become the "current page".
- Emulator verification with Firefox 125.3.0 and an `instagram.com` rule:
  - Tapping the address bar and typing `insta` → edit view shows the `instagram.com` suggestion, focused → **no block** (previously blocked).
  - Pressing Enter to actually load Instagram → **blocked** (`Website is blocked!`, log `host=instagram.com hardBlocked=true`).
  - Loading a non-blocked site (`example.com`) → detected (`trusted=true`) and not blocked.
- Note: the previously reverted robustness commit (`01ef0b6`) was not the cause; it stays reverted.

### Firefox internal-screen false blocks (2026-09-18, `67e5b09`)

Owner follow-up after still seeing blocks while Firefox was not on a blocked page ("recommended websites taken as open websites"). Because upstream Switchly does not have this problem, upstream 2.2.9 was **built from source (`at.saltyy.switchly`, offline flavor) and run on the same emulator** with the same Firefox 125.3.0 and the same `example.com` rule, side by side with our build:

| Scenario (rule `example.com`) | Upstream 2.2.9 | Our build before the fix | Our build after the fix |
| --- | --- | --- | --- |
| Firefox home with "Example Domain" in Recently visited | no block (`website_detect result=no_host`, repeated probes) | blocked | no block (probes log `no_host`) |
| History screen listing `https://example.com/` | no block (`result=no_host`) | blocked (`trusted=true`, stale URL view) | no block (`result=no_host`) |
| Address bar focused, typing `exa` with the `example.com` suggestion | guarded (`address_editing`) | fixed by `b34ad98` | no block (`website_skip reason=address_editing editingNow=true`) |
| Enter/navigate to `example.com` | blocked | blocked | blocked (`trusted=true`) |
| Chrome navigation to `example.com` | blocked | blocked | blocked (`trusted=true`, redirected) |

Root cause of the remaining false positives: on internal Firefox screens (home, History, Bookmarks, Settings) the accessibility tree still contains the **previous page's URL view nodes** and the screens themselves contain domains/URLs as tiles, list rows and page text. Our extraction treated those as trusted current-page signals.
- `LoqInAccessibilityService` changes:
  - `tryExtractDomainFromBrowserUrlViews`, `findBrowserUrlNode` (id lookup and the Firefox toolbar fallback) and `findEditableUrlText` now require `isVisibleToUser`, so stale/hidden toolbar nodes on internal screens are ignored.
  - New guard in `maybeBlockWebsite`: for Firefox, only a trusted address-bar signal may drive enforcement. Untrusted signals (`hostSignal.second == false` from `firefoxEventDomainSignal`) and text inference hits are logged as `web-firefox-untrusted` and dropped; the pending/cached fallbacks are intentionally not used in that state, because they would resurrect a stale host on internal screens. Real navigation still blocks via the trusted URL-bar signal (verified above).
- Upstream comparison notes (for future divergence checks): upstream's `tryExtractDomainFromBrowserUrlViews` has no visibility check, but its text inference does not reach the internal-screen items within its node budget on Firefox 125.x, so it returns `no_host` there. Its `firefoxDomainAliases` also matches bare brand substrings, which is why upstream is not a safe model for text matching; our stricter domain-shaped alias matching stays.


### Path-rule robustness (2026-09-17, `01ef0b6`) — **REVERTED in `005b5a8`**

> **Owner reported that this change broke Firefox on their device and asked for a revert.** The commit was reverted in full (`005b5a8`); path rules from `a6ef23f` remain, but the remember/retry/broadcast behaviour is gone. The likely culprit is the null-event retry probes interacting with the Firefox address-editing/domain state, but this is unconfirmed because Firefox is not installed on the emulator. If path rules are revisited, keep Firefox on the existing host-only path and avoid null-event probes for `isFirefoxFamily` packages.

Owner report: `abc.net.au/news/*` did not block a news article on a real device.
- Investigation on the emulator: the rule itself works (article blocked, home page allowed), but the first decision after a page load could see a host-only URL (the URL bar sometimes commits before the path) and a same-host navigation does not restart the candidate cycle — so the path rule could be missed.
- Fixes in `LoqInAccessibilityService`:
  - Remember the last full host+path per browser (`WEBSITE_TARGET_TTL_MS` 90 s) and reuse it when the fresh extraction has no path, so a late/missing path still matches.
  - When an enabled path rule exists for the detected host but the current target has no path, schedule two short re-probes (450 ms / 1.1 s, throttled to 2 s) that re-run the website check for that package.
  - New `recheckWebsiteRulesNow` receiver (`BlockingRuntime.ACTION_WEBSITE_RULES_CHANGED`, app-internal): adding, removing, enabling or disabling a rule, or changing the rule mode, makes the running service re-check the visible browser page. `ManageBlockedWebsitesActivity` fires the signal, `BlockingRuntime.notifyWebsiteRulesChanged` sends it.
  - Added a throttled `[website_target]` diagnostic log (host / fresh / remembered / used target) to make future path-rule reports debuggable.
- Emulator verification: `abc.net.au/` allowed; same-tab navigation to `abc.net.au/news/…` blocked; fresh navigation to a matching article blocked and to `example.com/allowed/page` allowed.
- Caveat for users: path rules need a browser that exposes the path to accessibility. **Firefox works** (see update below); Samsung Internet is untested.

### Firefox path rules + Chrome redirect research (2026-09-18)

Owner asked how upstream blocks without a blank page and whether Firefox supports path rules. Both questions were answered by testing on the emulator (Firefox 125.3.0, Chrome, rule `example.com/blocked/*`):

- **Firefox path rules work.** Firefox's `mozac_browser_toolbar_url_view` accessibility text exposes the full path and query (e.g. `google.com/search?q=test&sei=…`), and `tryExtractWebsiteTargetFromBrowserUrlViews` reads it.
  - `https://example.com/allowed/page` → detected `host=example.com matched=false` → allowed.
  - `https://example.com/blocked/test` → **blocked**, and the stored reason shows `matched=example.com/blocked/test`.
  - The earlier "Firefox falls back to host-only" note was a guess from before Firefox was available on the emulator; it is corrected here.
- **Chrome redirect / blank page.** There is no public way for another app to open Chrome's New Tab Page: `chrome-native://newtab` and `chrome://newtab` are unresolvable from an external `ACTION_VIEW` intent, and Chrome ignores `about:home` (the intent resolves but the page does not change). `about:blank` is the only reliable safe target for Chrome, and that is what upstream and our build already use. The blocker dialog is shown over it, so the user sees the block reason instead of the blank page; pressing OK reveals the cleared blank tab.
  - Firefox cannot resolve `about:blank` or `about:home` from an external intent at all, so `redirected=false` there and the existing single-BACK fallback runs (no blank page in Firefox).
  - The remaining alternative for Chrome would be to skip the redirect and use the single-BACK fallback like Firefox, which pops the blocked tab but can exit Chrome entirely when it was the last tab. Kept upstream's redirect behaviour. No code change was made.

### Firefox 155 Compose toolbar — path rules actually work on the owner's version (2026-09-18, `182d711`)

The `2026-09-18` note above was measured on Firefox 125.3.0. The owner's phone runs **Firefox 155.0.1**, where the toolbar was rewritten in Compose and the old `mozac_browser_toolbar_*` views are gone. Host detection survived through the toolbar fallback, but `tryExtractWebsiteTargetFromBrowserUrlViews` only read the old ids, so the path was lost and `abc.net.au/news/*` never matched. That is the real cause of the owner's original "path rule does not block the article" report.

Accessibility shape on Firefox 155 (verified with UI dumps on the emulator, same 155.0.1 APK from Mozilla's archive):
- Display state: `ADDRESSBAR_URL_BOX` (View), `content-desc` = `" example.com/blocked/test. Search or enter address"` (full host+path+query, a literal `. ` separator before the hint).
- Edit state: `ADDRESSBAR_EDIT_MODE` + focused `ADDRESSBAR_SEARCH_BOX` (EditText, text = the full URL); the URL display node disappears.
- These Compose test tags are exposed as **bare resource ids** (no `package:` prefix), so `findAccessibilityNodeInfosByViewId` cannot resolve them — a traversal is required.

Implementation (`182d711`):
- `LoqInAccessibilityService`:
  - New `findFirefoxUrlNode()`: visible, non-editable, non-focused nodes with toolbar/address-bar ids (`mozac`, `toolbar`, `origin`, `omnibox`, `display_url`, or the Compose `ADDRESSBAR_URL_BOX` tag). History rows (`org.mozilla.firefox:id/url`) and internal-screen leftovers are excluded.
  - `tryExtractWebsiteTargetFromBrowserUrlViews()` falls back to that node for Firefox, so path rules get host+path; `findBrowserUrlNode()` reuses the same helper.
  - `isFirefoxAddressBarActive()` detects the 155 Compose edit mode; the Compose tags are appended to `firefoxEditingViewIds()` so the event guards (`eventLooksLikeBrowserAddressEditing`, `isFirefoxAddressBarInputEvent`) cover 155 as well.
  - `websiteTargetFromText()` strips the Firefox `". <hint>"` separator period and URL ellipsis truncation, so the stored matched target is exact (`example.com/blocked/test`, not `example.com/blocked/test.`).
- `DomainBlockStore.matches()`: a `prefix/*` rule now also matches the same path without the trailing slash, because browsers trim it in the displayed URL (Firefox shows `/news/` as `/news`). Unit test updated accordingly.
- `softBlockSurface()` no longer overwrites the website-rule block reason with the generic in-app reason; the blocker screen shows `Blocked by: Website rule` for website and path-rule blocks.

Verification (AVD `HolyPixel`, Android 36, x86_64, rule `example.com/blocked/*`) — scenario driver with a force-stop between cases so post-block suppression cannot mask results, 10/10:
- Firefox 155.0.1: blocked path, allowed path, same-tab navigation, query string (`?x=1`), subdomain (`www.`), trailing-slash-only (`/blocked/`), long deep path → all blocked with the exact `matched` target; bare host (`example.com/`) allowed.
- Chrome: blocked path blocked, allowed path allowed (regression).
- Firefox 125.3.0 (old toolbar, clean install): the same 10/10 pass, so the legacy path is intact.
- Owner's real rule `abc.net.au/news/*`: `https://www.abc.net.au/news/2026-09-18/firefox-path-rule-test` → blocked (`matched=abc.net.au/news/2026-09-18/firefox-path-rule-test`); `https://www.abc.net.au/` → allowed.
- No false positives on Firefox 155: address-bar typing with suggestions (`address_editing` / `addressBarInput`), the home screen (its search bar is in Compose edit mode by default), the History screen listing `example.com/blocked/test` (scrolled), and a Google results page whose text mentions the blocked URL — all unblocked.
- `./gradlew :app:testDebugUnitTest` passes (including the updated `DomainBlockStoreTest`).

### Firefox version compatibility sweep (2026-09-18)

Owner asked whether path blocking works across Firefox versions. 16 x86_64 release APKs from Mozilla's archive (`ftp.mozilla.org/pub/fenix/releases/`) were installed one by one on the AVD and tested with the `example.com/blocked/*` rule (blocked path must block with the exact matched target, allowed path must not):

| Firefox | Result | Toolbar family (URL view) |
| --- | --- | --- |
| 97.1.0 | PASS | legacy mozac |
| 105.1.0 | PASS | legacy mozac |
| 113.2.0 | PASS | legacy mozac (`mozac_browser_toolbar_url_view` text has the full path) |
| 116.2.0 | PASS | legacy mozac |
| 119.0.1 | PASS | legacy mozac |
| 120.1.1 | PASS | legacy mozac |
| 122.0 | PASS | legacy mozac |
| 125.3.0 | PASS | legacy mozac |
| 130.0 | PASS | legacy mozac |
| 136.0.1 | PASS | legacy mozac |
| 140.0 | PASS | legacy mozac |
| 145.0.1 | PASS | legacy mozac |
| 150.0.3 | PASS | Compose `ADDRESSBAR_URL_BOX` |
| 152.0.6 | PASS | Compose `ADDRESSBAR_URL_BOX` |
| 154.0.1 | PASS | Compose `ADDRESSBAR_URL_BOX` |
| 155.0.1 | PASS | Compose `ADDRESSBAR_URL_BOX` |

Findings:
- Both toolbar families expose the full host+path in the accessibility text (`example.com/allowed/page` verified on 113 and 150), so the existing legacy-id path and the Compose fallback cover every release from 97 to 155.
- The Compose toolbar (`ADDRESSBAR_URL_BOX`) first appears in the 150.0.3 build in this set; 145.0.1 still uses the mozac views.
- Every apparent failure in the automated sweep was a **one-time first-run Firefox promo** (e.g. "Total Cookie Protection") that covers the screen and masks the accessibility tree on the first session after a fresh install/first run. Re-running the navigation after the promo disappears (relaunch or dismiss) blocks correctly on 113, 116, 119 and 120. Real-world impact: the very first page load on a freshly installed Firefox may not be blocked while such a promo is up; afterwards blocking is unaffected. No code change was needed.

### Samsung Internet, path-rule backup and host-rule independence (2026-09-18, `778d38b`)

**Samsung Internet** (`com.sec.android.app.sbrowser` 30.0.2.61, installed on the AVD from the arm64 APK; the emulator runs it through `libndk_translation`):
- Host rules work: the URL is read from `com.sec.android.app.sbrowser:id/location_bar_edit_text` (an EditText whose id contains "location"), e.g. `example.com`. Verified: `example.com` rule blocks `https://example.com/` and `https://example.com/allowed/page`.
- Path rules cannot match on this build: Samsung's accessibility URL text is **host-only** (`example.com`, with a leading U+200E LTR mark) and the path appears nowhere else in the accessibility tree (searched the full dump for the path text). Path-rule coverage therefore stays with Chrome/Brave/Firefox; Samsung Internet falls back to host-only blocking. No code change.
- Note: installing the arm64 APK requires `adb install --abi arm64-v8a` on the x86_64 AVD (the mixed arm64+armv7 APK otherwise fails with `INSTALL_FAILED_INTERNAL_ERROR` because armeabi-v7a is not in the device ABI list).

**Path rules in backups** (verified by code + new unit tests, `BackupPathRulesTest`):
- Path rules live in the same per-profile string set as host rules (`domain_block_domains__p__<profile>`), the Website rules backup category maps that key, and the payload copies sets to lists and restores them as string sets. Path rules are therefore exported and restored unchanged.
- Tests cover: inclusion when the Website rules category is selected, exclusion when it is not, and the set -> list -> set round trip preserving the path.

**Path rules are independent of the host rule** (`778d38b`):
- Removing a rule removes exactly that string; deleting `example.com` already left `example.com/blocked/<path>` in place.
- The one coupling was limits: deleting a host rule cleared the host's limits even when path rules for the same host remained. `ManageBlockedWebsitesActivity` now clears the host limit only when no rule (selected, allowed or disabled) for that host is left.
- Rules cannot be edited while protection is active (existing `websiteEditingLocked()` policy); the emulator's lock made a full UI delete-flow run impractical, so the change is covered by code review plus the unit-tested store/backup semantics.

### Firefox website blocks kick out of the browser (2026-09-18, `1621976`)

Owner report: with a tab already open before the rule/blocking was active, selecting that tab could leave them on the blocked site ("doesn't kick me out correctly").

Root cause: Firefox cannot be redirected to a safe page (`about:blank` is rejected), so the block flow used `backCount=1` + deferred navigation. On OK the service brought Firefox back to the front and pressed BACK once; when the blocked page was the tab's first page, BACK did nothing (or landed on another page of the same site), and the follow-up `enforceWebsiteBlockIfStillVisible` gave up whenever no host was visible (scrolled page = toolbar disposed from the accessibility tree).

Fix (`LoqInAccessibilityService.maybeBlockWebsite`): when the browser could not be redirected (`!redirected`), the surface now posts HOME while the popup is shown (`prePopupPhoneHome=true`) and OK does not return to the browser (`returnToPackageOnClose=false`, no deferred BACK). Chrome keeps the existing safe-page redirect path unchanged.

Emulator evidence (Firefox 155, rule `example.com/blocked/*`):
- Blocked page: activity order behind the blocker is `BlockerActivity > launcher > Firefox`; OK leaves the launcher on screen.
- Chrome: block still redirects (`about:blank`) and OK returns to Chrome.
- Full Firefox/Chrome path-rule matrix still 10/10.

### W2.2 A5 — Session limit reset semantics (2026-09-17, `a8d8ec4`)

Owner-approved upstream port; the highest-risk change in the plan.
- `SwitchModeStore`: added `KEY_LIMIT_SESSION_STARTED_AT` + `getLimitSessionStartedAt`; `bumpLimitSessionGeneration` replaced by `startNewLimitSession` (also records `startedAt`) and `ensureLimitSessionStartedAt` (called from `ensureInit`); a new generation starts **only** when the base protection flag transitions false → true in `setEnabled`, `setEnabledBySchedule` and `setTemporarilyEnabled`; the other seven effective-state changes (temp pause, temp enable, clear/cancel temp, schedule/profile changes) no longer bump it. Added the "Protection enabled/disabled" timeline record (D1 hook).
- `LoqInAccessibilityService`: `ensureActiveLimitSession` now keeps the observed generation/startedAt and only wipes counters when a *new* generation is observed while the service is alive (startedAt comes from the store); `clearActiveLimitSession` only clears the profile pointer; `getEnforcedLimitUsageMs` restores session counters from `UsageLimitSessionRuntimeStore` after service/process recreation (stale generations rejected by the store); `publishSessionLimitState` falls back to the stored `startedAt`.

Emulator evidence (AVD `HolyPixel`, Android 36, x86_64) — Chrome with a 1-minute Session-mode daily limit:
- Baseline: blocked after ~61 s (`Blocked by: Daily time limit • Default`); runtime store holds generation 1 with `used_ms ≈ 60789`, `reached=true`.
- Screen lock/unlock: still blocked (allowance survived; previously it reset).
- Accessibility service/process restart: still blocked (restored from `UsageLimitSessionRuntimeStore`).
- Full device reboot: still blocked (generation + runtime store persisted).
- Profile switch away to a second profile (Chrome unblocked there) and back: still blocked on Default.
- Not verified on-device (emulator input stopped delivering taps to the app, and QS tile injection was ignored): the generation bump on protection off→on (the reset) and the temporary-pause resume path. Both mirror upstream exactly; **manual check recommended**: disable protection, re-enable, confirm the Session limit starts a fresh allowance, and pause/resume to confirm the allowance is kept.

### Picker tile limit summary fix (2026-09-17, `05cddac`)

Owner report: on apps with several limits the summary under the app name no longer fit inside the picker tile.
- `grid_app_tile.xml`: `tvSub` now allows 2 lines, icon 44 → 42 dp (label margins unchanged).
- `AppListAdapter`: the tile now uses compact labels (`%d min/day`, `%d min/visit`, `%d opens/day`) instead of the long "Daily limit: …"/"Session limit: …" strings used by other screens; `%d min/session` was already compact.
- Verified on the emulator: Chrome with time + visit + opens shows `60 min/day · 15 min/visit · 5 opens/day` across two lines inside the tile.

### Breaks editor redesign (2026-09-17, `1b0b64a`)

Owner request: give the "Breaks" (temporary pause) editor the same design as the app limit editor, including usage today and clearing usage.
- `dialog_temp_pause.xml` rebuilt with the card language: summary card (live caps sentence + "Used today: …"), and three cards — Breaks per day / Length per break / Daily break time — each with icon, titles, accent switch, stepper and a centred field with `breaks`/`min` suffix.
- `TempPauseDialogs.show` rewritten: switches replace the old "0 = unlimited" text fields (off = unlimited), defaults 2/15/30 when enabled, steppers ±1/±5/±15, same accent tinting/switches/summary tinting as the limit editor, and validation keeps 1..100 breaks / 1..1440 minutes.
- "Reset today" is now a red text button in the bottom action row; the existing emergency-PIN verification and the lock warning are preserved. The button hides when usage is 0 and the usage line refreshes after a reset.
- Locked state unchanged at the entry point: `MainActivity` still dims the Breaks row and shows the lock pill while protection is active.
- Verified on the emulator: defaults (2/15/30) saved to `loqin_temp_pause.xml`; seeding 2 breaks / 24 min showed `Used today: 2 breaks · 24 min`; the reset flow through the emergency PIN cleared both counters and the profile sheet summary refreshed.

### W2.1 A4 — Enforce Minutes per visit (SessionLimitStore) — **DONE 2026-09-17** (`d10ceac`)
> Implemented and emulator-verified. Test infrastructure from W0.1 was added along with it (`testImplementation junit`, `BlockDecisionTest`); `./gradlew :app:testDebugUnitTest` passes. See "W2.1 implementation + test evidence" in the progress log.
> Out-of-plan addition by owner request: the app limit editor dialog was redesigned (`fb01e65`, see below).

### W2.1 implementation + test evidence (2026-09-17)

Changed files:
- `app/src/main/java/com/oliver/loqin/blocking/BlockDecision.kt` — `perVisitLimitMinutes`/`perVisitUsageMs` inputs; a per-visit-limited app counts as limited (never hard-blocked); blocks with `immediate = true` once the visit is exhausted.
- `app/src/main/java/com/oliver/loqin/blocking/LoqInAccessibilityService.kt` — cached `SessionLimitStore` reader (`getSessionLimitCached` + cache invalidation on profile change), per-visit session state (`activePerVisitProfile/Pkg/StartedAt`), `ensurePerVisitSession` / `clearPerVisitSession` / `getPerVisitUsageMs`, wiring in `usageTick` (cleared on screen-off/keyguard/protection-off/bypass/no-profile/temp-allow; started per observed foreground package), an enforcement check before the daily-limit path, and per-visit fields in the decision call site, logs, `hardBlocked`/`managed` math and the block-reason `when`.
- `app/src/main/res/values/strings_home.xml` + `values-de/…` — `block_reason_rule_per_visit_limit`.
- `app/build.gradle.kts` — `testImplementation junit:junit:4.13.2`.
- new `app/src/test/java/com/oliver/loqin/blocking/BlockDecisionTest.kt` — 11 cases covering hard blocks, daily limits, attempts, per-visit, combinations, force and allow-mode.

Emulator test evidence (AVD `HolyPixel`, Android 36, x86_64):
- Seeded `session_limit_min__Default__com.android.chrome = 1` while the app was dead; launched Chrome with accessibility enabled.
- After ~61 s of Chrome foreground the blocker appeared: `App blocked | Chrome | … | Blocked by: Minutes per visit limit • Default`.
- Dismissing the blocker returned to the launcher and re-opening Chrome started a fresh visit (not blocked immediately); the visit expired again after ~60 s (log: `app_per_visit_limit_reached … perVisitMin=1 usageMs=60890/60958 limitMs=60000`).
- Screen lock/unlock also started a fresh visit (re-opening Chrome was not blocked).
- Unit tests: `./gradlew :app:testDebugUnitTest` passes.

Testing notes (W2.1):
- Per-visit enforcement is wall-clock from the first foreground tick; it is independent of the daily-limit "Session" reset mode (A5) and never persists across processes.
- The visit resets whenever another package (including the launcher after blocker dismissal) is observed as foreground, so blocker → home → app is a new visit.

### Limit editor dialog redesign (2026-09-17, `fb01e65`)

Owner request: make the app limit editor (`dialog_app_limits.xml`, opened from picker tiles / usage screens) match the rest of the app.
- Each limit is now a rounded `foqos_surface_variant` card (Screen time / App opens / Single session cap) with its icon, title, subtitle and switch; controls dim and disable when the section is off instead of sitting half-visible.
- Text fields use accent stroke/hint, centred values and unit suffixes (`min`, `opens`); quick time pills keep their place inside the Screen time card.
- The live summary card is tinted with the active accent (`ColorUtils.compositeColors`), and the sentence now uses the app's `·` separators (also fixed the lost leading space by using `\u0020` escapes).
- Switches use `CustomAccentApplier.tintSwitch`; "Remove limits" moved into the bottom action row (left, red) next to Cancel / Save limits.
- Verified on the emulator: disabled sections dim, enabling Screen time defaults to 60 min and updates the sentence, Save persists (`usage_limit_min__Default__com.android.chrome = 60`) and the picker tile shows `Daily limit: 60 min/day · Session limit: 1 min`.
- **Goal:** the "minutes per visit" value users can already set must actually block. It currently does nothing.
- **Depends:** none, but land before W2.2 (both touch limit enforcement).
- **Files:**
  - `app/src/main/java/com/oliver/loqin/blocking/BlockDecision.kt`
  - `app/src/main/java/com/oliver/loqin/blocking/LoqInAccessibilityService.kt`
  - `app/src/main/res/values/strings_home.xml`
  - `app/src/test/java/com/oliver/loqin/blocking/BlockDecisionTest.kt` (from W0)
- **Upstream reference:**
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/blocking/BlockDecision.kt`
  - `git show upstream/main:app/src/main/java/at/saltyy/switchly/blocking/SwitchlyAccessibilityService.kt` (search `getSessionLimitCached`, `ensurePerVisitSession`, `clearPerVisitSession`, `getPerVisitUsageMs`)
- **Changes:**
  1. `resolveAppBlockDecision`: add `perVisitLimitMinutes: Int = 0`, `perVisitUsageMs: Long = 0L`; `hasLimit = limitMinutes > 0 || attemptLimit > 0 || perVisitLimitMinutes > 0`; add `perVisitLimitReached`; include it in `shouldBlockNow` and `immediate`. Add tests.
  2. Service state: `activePerVisitProfile`, `activePerVisitPkg`, `activePerVisitStartedAt`; helpers `ensurePerVisitSession`, `clearPerVisitSession`, `getPerVisitUsageMs` (wall clock from first observed foreground tick for that package).
  3. Add a cached reader for `SessionLimitStore.getLimitMinutes(this, profile, pkg)` mirroring our `getUsageLimitCached` cache structure (`cachedSessionLimitAt/Profile/ByPkg`).
  4. In `usageTick()` (L1441):
     - Mirror every existing `clearActiveLimitSession()` early return (screen off L~1508, keyguard, `!SwitchModeStore.isEnabled`, `EmergencyBypassStore.isActive`, no profile) with `clearPerVisitSession()`.
     - After `ensureActiveLimitSession(profile, nowForCache)` call `ensurePerVisitSession(profile, pkg, nowForCache)`. When `TempAllowStore.isAllowed` → `clearPerVisitSession()` and return.
  5. In the limit section (L1565+): compute `perVisitLimitMin`, `perVisitUsageMs`, and on `perVisitUsageMs >= perVisitLimitMin * 60_000` log + `markLimitReached` is **not** required for per-visit (upstream does not persist per-visit reached) + `maybeBlockNow(pkg, force = true)`.
  6. Pass the two new params at the `resolveAppBlockDecision(...)` call (L1989). Extend the `hardBlocked`/`managed` calculations at L1956+ and L2036+ so an app with a per-visit limit is **limited**, never hard-blocked: add `perVisitLimitMin > 0` to the `hasLimit` side of both expressions and to the `managed` union.
  7. Extend the `appRule` `when` with `perVisitLimitMin > 0 && perVisitUsageMs >= perVisitLimitMin * 60_000L -> getString(R.string.block_reason_rule_per_visit_limit)`. Add the string to `strings_home.xml` next to the other `block_reason_rule_*` values. Add per-visit fields to the decision log messages.
- **Semantics (upstream):** the visit clock starts when the package is first observed in the foreground and resets when the package changes (new visit), screen turns off, keyguard locks, protection is disabled/paused/bypassed, temp-allowed, or profile is missing. This is wall clock, not measured usage.
- **Verification:**
  - Set "minutes per visit" = 1 on a test app (no daily limit): open it, wait past 1 minute with the screen on → blocker. Dismiss and reopen → the visit resets and it is usable again for a minute.
  - Daily limit + per-visit combined: either condition triggers blocking; removing both restores normal behavior.
  - Hard-block an app with no limits: still blocked immediately (no regression).
  - Lock the screen for >1 minute: reopening starts a new visit.
  - Unit tests pass.
- **Risks / gotchas:**
  - `usageTick` derives `pkg` from `rootInActiveWindow`/`currentTopPkg` (L1451); if `rootPkg` is null for >1 tick the timer may start late. Acceptable parity with upstream, but note it.
  - Do not mark per-visit as `LimitReachedStore` — Home/Stats uses `UsageLimitSessionRuntimeStore` for overall session state; per-visit state is deliberately ephemeral.
- **Done when:** per-visit blocks, resets as specified, tests pass, no hard-block regressions.

### W2.2 A5 — Overall "Session" limit reset semantics — **DONE 2026-09-17** (`a8d8ec4`)
> Implemented and mostly emulator-verified; see the progress log entry "W2.2 A5 — Session limit reset semantics" for evidence and the two branches that still need a manual check.
- **Goal:** an overall limit configured with reset mode = Session must survive profile switches, temporary pauses, screen locks and service/process recreation, and reset only when a genuinely new protection session starts (base protection off → on).
- **Depends:** W2.1 (do not conflict).
- **Files:**
  - `app/src/main/java/com/oliver/loqin/data/prefs/SwitchModeStore.kt`
  - `app/src/main/java/com/oliver/loqin/blocking/LoqInAccessibilityService.kt`
  - `app/src/main/java/com/oliver/loqin/data/prefs/UsageLimitSessionRuntimeStore.kt` (probably no change needed; verify restore path)
  - `app/src/main/java/com/oliver/loqin/data/prefs/LastBlockReasonStore.kt` (no; only if D1 hook bundled)
- **Upstream reference:**
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/data/prefs/SwitchModeStore.kt`
  - `git show upstream/main:.../SwitchlyAccessibilityService.kt` lines around `ensureActiveLimitSession` / `clearActiveLimitSession` / `getEnforcedLimitUsageMs` / `ensureLimitSessionStartedAt`
- **Changes:**
  1. `SwitchModeStore`:
     - Add `KEY_LIMIT_SESSION_STARTED_AT`, `getLimitSessionStartedAt(ctx)`.
     - Replace `bumpLimitSessionGeneration` with `startNewLimitSession(ctx)` (bumps generation **and** writes startedAt), plus `ensureLimitSessionStartedAt(ctx)` called from init when base protection is enabled.
     - Start a new session only when the **base** pref transitions false → true and the effective state is enabled after the call, in each public enable/toggle path that upstream touched (setEnabled, setEnabledQuiet, temp-enable, emergency path, etc.). Mirror upstream's added `val baseBefore = sp.getBoolean(KEY_ENABLED, false)` checks exactly.
     - Remove the `bumpLimitSessionGeneration` calls from all other effective-state changes (schedules, profile switches, NFC lock, temp pause end, etc.). There are ~10 sites today; after this change none should bump.
  2. Service:
     - `ensureActiveLimitSession(profile, now)`: if same generation and `startedAt > 0` → update profile pointer and return. If the observed generation changed while this service instance was alive → clear in-memory counters (`sessionLimitUsageMsByKey`, `sessionLimitReachedKeys`) and restore using `SwitchModeStore.getLimitSessionStartedAt(this)`.
     - `clearActiveLimitSession()`: only clear the profile pointer; **keep** generation, startedAt, and counters so lock/pause/bypass cannot reset the allowance.
     - `getEnforcedLimitUsageMs(profile, pkg)`: for session mode, return the in-memory counter if present, else restore from `UsageLimitSessionRuntimeStore.get(this, profile, pkg)` (it rejects stale generations already) and re-add `reached` keys. Publish state via `publishSessionLimitState` as today.
  3. Verify `UsageLimitSessionRuntimeStore.get` is not called/cleared from UI paths that would defeat persistence (`clearAll` outside a genuine new session). Grep `UsageLimitSessionRuntimeStore.clearAll` and fix call sites if needed.
  4. D1 hook (from §2 gap): add `DiagnosticsTimelineStore.record(ctx, "Protection", ...)` in `SwitchModeStore.recordEffectiveStateChange` and in `ProfileStore.setCurrent` / `AutomationModeStore.setMode`/`putBool` when these files are already being touched. Keep it minimal.
- **Verification (manual matrix):**
  | Action | Expected |
  |---|---|
  | Session limit reached, switch profile and back | still reached, no fresh allowance |
  | Temporary disable (pause) then expiry | still reached |
  | Emergency Unlock | still reached |
  | Screen off/on, keyguard | still reached |
  | Kill/restart Accessibility service (or process) | still reached |
  | Disable protection (base off), re-enable | allowance reset, new session |
  | Restart phone with protection on | allowance persisted |
  | Daily mode limit | unchanged day-based behavior |
  - Also confirm Home/Stats "session limit" progress is consistent before/after (no stale 0% after process restart).
- **Risks / gotchas:**
  - This is the highest-risk W2 change: a wrong base-transition check can either reset allowances on pause (user exploit) or never reset them (support burden).
  - Our `usageTick` calls `clearActiveLimitSession()` in several places; those calls stay but the new implementation must not wipe counters.
  - UI reads `UsageLimitSessionRuntimeStore`; ensure the generation it compares against is the same one written by the service.
- **Done when:** full matrix passes, `DiagnosticsTimelineStore` records protection/session events, unit/manual tests green.

---

## W3 — Website path wildcards

Branch `feature/upstream-w3-websites` off the W2 tip.

### W3.1 A3 store — normalize/matches with path and wildcard rules — **DONE 2026-09-17** (`a6ef23f`, covers W3.1–W3.3)
> Implemented and emulator-verified; see the progress log entry "W3.1–W3.3 A3 — Website path rules".
- **Goal:** rules like `youtube.com/shorts/*` can be stored, matched and displayed, without breaking existing host rules.
- **Files:** `app/src/main/java/com/oliver/loqin/data/prefs/DomainBlockStore.kt`, `app/src/test/java/com/oliver/loqin/data/prefs/DomainBlockStoreTest.kt`.
- **Upstream reference:** `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/data/prefs/DomainBlockStore.kt`
- **Changes (adapt upstream to our file):**
  1. `normalize(raw)`: stop stripping `/`; keep `?` as the single-character path wildcard; strip only whitespace tails and `#fragment`; parse user-info only before the first `/`; strip `www.`, ports, duplicate dots as today; keep IDN + length + charset validation for the host; then append a normalized path: collapse `//`, reject whitespace or >1024 chars in the path, return host-only when path is empty or `/`.
  2. Add `hostPart()`, `pathPart()`, `isPathRule()`.
  3. `matches(target, rule)`: normalize both; require host equality or subdomain suffix; if the rule has no path → host match; if it has a path → target must have a path and `globPathMatches(targetPath, rulePath)` with `*` → `.*`, `?` → `.`, escaped regex metacharacters, case-insensitive.
  4. `isRuleEnabledForHost`: only compare host rules against the disabled set (`!isPathRule(rule) && matches(host, rule)`).
  5. `getEnabledDomains`: exact disabled-set membership (`it in disabled`), not `matches`.
  6. `shouldBlockHost`: exact membership filtering, plus `filterNot { allowMode && isPathRule(it) }` (path rules are not supported in allow mode).
  7. Tests: `normalize` cases (`https://www.YouTube.com/shorts/abc?x=1#f` → `youtube.com/shorts/abc`? confirm: upstream strips nothing but whitespace/fragment, so query is preserved in stored rules; test against actual implementation), subdomain matching, path glob (`*`, `?`), allow-mode exclusion.
- **Verification:** tests pass; existing host-only rules still block subdomains; a path rule does not match the bare host; `removeDomain` exact-match still removes the rule.
- **Risks:** `isDomainEnabledForProfile` uses exact keys (fine). `removeDomainForProfile` only removes exact strings — deleting a host does not delete its path rules; decide whether to clean up (upstream did not) and document the decision.
- **Done when:** tests pass and no compile fallout in UI that calls `normalize`.

### W3.2 A3 service — extract website target (host + path) from browser UI
- **Goal:** the service must supply `host/path` targets to `shouldBlockHost` so path rules can match, with no false blocks while typing in the address bar.
- **Files:** `app/src/main/java/com/oliver/loqin/blocking/LoqInAccessibilityService.kt`, `app/src/main/java/com/oliver/loqin/blocking/BrowserWebsiteState.kt`.
- **Upstream reference:**
  - `git show 61c5c58 -- app/src/main/java/at/saltyy/switchly/blocking/SwitchlyAccessibilityService.kt` (look for `tryExtractWebsiteTargetFromBrowserUrlViews`, `websiteTargetFromText`, `domainFromText`)
- **Changes:**
  1. Add a `websiteTargetFromText(raw)` that reuses our `domainFromText` regex but keeps the path when present (URL-looking text), and a `tryExtractWebsiteTargetFromBrowserUrlViews(root, pkg)` that looks specifically at omnibox/URL view nodes rather than arbitrary text.
  2. Store the full target (not just host) in `BrowserWebsiteState` (`updateCurrentDomain`, `confirmCurrentDomain`, `currentTrackedDomain`, `currentConfirmedDomain`) or add sibling target fields. Keep host-only accessors for limit/stat lookups (`WebUsageStore`, `DomainLimitStore`).
  3. Update match call sites to pass the full target: `maybeBlockWebsite` L3830, the visible-site recheck at L3968, the post-ack recheck at L4049. Keep `DomainBlockStore.isRuleEnabledForHost` on the host part (per W3.1 semantics).
  4. Do not block while the address bar is being edited (existing guards at L3684+ must remain first). In-app browser surfaces without a visible URL must stay host-only.
- **Verification:**
  - Add rule `youtube.com/shorts/*` in block mode; in Chrome open `youtube.com/shorts/x` → blocked; open `youtube.com/` → allowed.
  - Typing `youtube.com/shorts/*` into the address bar must not block before navigation.
  - Firefox: verify path extraction works or gracefully falls back to host-only (log signal).
  - Allow-mode profile: path rules are ignored (no unexpected blocks).
- **Risks / gotchas:**
  - Text-node regexes can capture junk (e.g. `example.com/path` inside page content). Prefer URL-view nodes and require a scheme-less `domain.tld/path` shape; keep extraction throttled.
  - Firefox has a separate inference path (`inferFirefoxDomainFromTexts`) — keep it host-only unless the loaded-URL signal gives a path.
- **Done when:** path rules block/release correctly in Chrome/Brave/Firefox and the address-bar edit guard still works.

### W3.3 A3 UI, limits store and backup
- **Goal:** users can see and manage path rules; path rules survive backup/restore.
- **Files:**
  - `app/src/main/java/com/oliver/loqin/feature/settings/ManageBlockedWebsitesActivity.kt` (our rewrite)
  - `app/src/main/java/com/oliver/loqin/data/prefs/DomainLimitStore.kt`
  - `app/src/main/java/com/oliver/loqin/data/sync/BackupSelection.kt`
  - pickers that call `DomainBlockStore.normalize` (verify with grep)
- **Upstream reference:**
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/data/prefs/DomainLimitStore.kt`
  - `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/feature/settings/ManageBlockedWebsitesActivity.kt`
- **Changes:**
  1. Port `DomainLimitStore.clear(ctx, domain)` delegating to `clearForProfile(ctx, ProfileStore.getCurrent(ctx) ?: "default", domain)` and keep `sanitizeProfile` behavior. Confirm limit lookups still key by normalized target as needed.
  2. ManageBlockedWebsites UI: render `host/path` rules (host emphasized, path secondary); keep validation via `normalize`; in allow mode, either hide path-entry or warn that path rules only apply to block mode.
  3. Backup: confirm the domain sets (including path rules) are included in the selected categories; run an export/import round trip.
- **Verification:** add/edit/disable/delete host and path rules; export backup, clear app data (dev build), import, confirm rules and their enabled state.
- **Risks:** UI code is ours, not upstream's; port intent, not layout.
- **Done when:** round trip works and the UI is clear about allow-mode limitations.

---

## W4 — ANR work budget and the YouTube experiment

### W4.1 A2 — Accessibility work budget + async root lookup (shared infra) — **DONE 2026-09-18** (`7b6e5f9`)

> Ported to our rebuilt service on branch `feature/upstream-w4-a2`. New `AccessibilityWorkBudget` (in-memory counters persisted every 25 ops or on slow work; no node text), a `loqin-accessibility-binder` HandlerThread, `activeRootWithBudget()` (in-flight guard, 12 ms main-thread wait, returns null on timeout), `currentRoot()` now prefers `event.source` then the budgeted active root, and telemetry on `collectNodeTextBlob`, `collectNodeIdBlob`, `findAnyNode` and `findFirefoxUrlNode`. Generic root lookups migrated (`usage_tick`, `facebook_surface`); YouTube lookups intentionally left for W4.2.
>
> Emulator evidence (AVD HolyPixel): website path matrix 10/10 unchanged (Firefox 155 + Chrome), app blocking unchanged (Calendar), counters `roots=832 slow=198 scans=2962 overruns=46 nodeLimitHits=0 lastRoot=usage_tick/1ms`, no ANRs. Support screen shows the budget line. Manual stress on a slow OEM device (Samsung A12/Bigme) still pending.
- **Goal:** eliminate multi-second `rootInActiveWindow`/`windows` Binder stalls on the Accessibility main thread and add bounded scan telemetry.
- **Branch:** `feature/upstream-w4-a2` off the W3 tip (or off `feature/upstream-work` if W3 is not merged; avoid conflicts by doing W4.1 last in the W1–W4 run).
- **Files:**
  - new `app/src/main/java/com/oliver/loqin/blocking/AccessibilityWorkBudget.kt`
  - `app/src/main/java/com/oliver/loqin/blocking/LoqInAccessibilityService.kt`
- **Upstream reference:**
  - `git show upstream/main:app/src/main/java/at/saltyy/switchly/blocking/AccessibilityWorkBudget.kt`
  - `git show 2f8159f -- app/src/main/java/at/saltyy/switchly/blocking/SwitchlyAccessibilityService.kt` (search `activeRootWithBudget`, `currentRoot`, `accessibilityBinderWorker`)
- **Changes:**
  1. Port `AccessibilityWorkBudget.kt` with our package/name style; it stores counters in its own prefs and batches writes (persist every 25 ops or on overrun). No text is stored.
  2. Service: create a dedicated `accessibilityBinderWorker` `HandlerThread` in `onServiceConnected`; add `activeRootWithBudget(reason)` with an in-flight guard, `CountDownLatch`, 12 ms wait constant, `AccessibilityWorkBudget.recordRootLookup`; return `null` on timeout. Never call it from the worker thread itself.
  3. Add `currentRoot(event)` that prefers `event.source` before falling back to `activeRootWithBudget("current_root")`, and migrate the root call sites progressively:
     - Usage tick root (L1451) → `activeRootWithBudget("usage_tick")`.
     - The other direct `rootInActiveWindow` uses (L1403, 3558, 3575, 5443, 5908, 8095) → route through `currentRoot`/`activeRootWithBudget` only where an `AccessibilityEvent` is available; otherwise keep the synchronous call but record it via `AccessibilityWorkBudget.recordRootLookup`.
  4. Instrument our existing `walkNodes` node collector (L4112) with `AccessibilityWorkBudget.recordScan(context, name, visited, maxNodes, durationMs)`.
  5. **Do not change YT scan constants here.** Keep 120/32/25 ms in this step.
  6. Surface counters in `SupportActivity` debug output if trivial (optional; D5 does it properly later).
- **Verification:**
  - Build + install; perform the usual navigation; verify blocking behavior unchanged for app/website limits.
  - Check `AccessibilityWorkBudget.snapshot` values via logs or a debug screen; no stalls > 12 ms on the main thread.
  - Stress: Samsung A12-class device, open/close a heavy app repeatedly, screen on/off; watch for `root_timeout` counters.
- **Risks:** timeouts return null and can skip enforcement for one event; existing call sites are `runCatching`-based and null-tolerant. Add a log category for timeouts so W4.2 can tune the wait constant.
- **Done when:** no direct root lookups outside the budget helper, counters observable, no blocking regressions.

### W4.2 B — YouTube experiment (separate branch, A/B comparison) — **RUN 2026-09-18** (`experiment/yt-upstream-2.3.1`: `ad814d2`, `7b21a85`, `b5ae16d`)

> Experiment outcome: our rebuilt implementation already passed every reproducible scenario (baseline 9/9). Ported the three upstream pieces that still add value (Binder-risk gating, direct Subscriptions/You bottom-nav pre-dedupe, watch-ad position guard) and removed the fork's unsafe coordinate mini-player taps/swipes. Evaluated and left out upstream's PiP and mini-player enforcement (this fork removed those user-facing rules by design) and the Shorts event-burst/card heuristics (our Shorts detection passed everything; revisit only on a real regression). Full matrix, coverage comparison and recommendation: `docs/yt-experiment-results-2.3.1.md`.

- **Goal:** decide empirically whether upstream's 2.3.x YouTube logic beats our rebuilt implementation. **Nothing from this step merges without the experiment outcome.**
- **Branch:** `experiment/yt-upstream-2.3.1` off the W4.1 tip.
- **Context:** our service (`LoqInAccessibilityService.kt`, ~8.7k lines) is a parallel implementation. Our user-facing mini-player and PiP rules were removed; cleanup helpers remain for Shorts-in-PiP. Upstream's changes target those surfaces, so they must be re-tested rather than assumed better.
- **Upstream reference:** `git diff be4ab60..bf9526b -- app/src/main/java/at/saltyy/switchly/blocking/SwitchlyAccessibilityService.kt` (~1800 changed lines; functions named in §5).
- **Port list (adapt names/paths to our service):**
  1. `isYouTubeAccessibilityBinderRiskDevice()` + gating of risky deep walks (0/1 max hops on risky devices instead of 3/5).
  2. `currentRoot(event.source)` preference (should already exist from W4.1).
  3. `maybeBlockYouTubeDirectNavigationBeforeDedupe` + `isDirectYouTubeSubscriptionsEvent` + `hasYouTubeShortsEventBurstSignal` + `isDirectYouTubeShortsCardEvent` (Shorts/Subscriptions ordering on 21.35.x).
  4. Watch-page: `armYouTubeWatchAdPositionGuard` / `shouldProbeYouTubeWatchAdOverlayRoot` / `hasYouTubeWatchAdDismissEvent` (+ `YT_WATCH_AD_POSITION_GUARD_MS`) so Premium/live/watch are not misread as floating PiP.
  5. PiP: `isYouTubePictureInPictureWindowVisible`, `maybeBlockYouTubePipOutsideYouTube`, `scheduleYouTubePipWindowProbe`, `enforceAsyncYouTubePipWindowEvidence`, `PIP_KILL_COOLDOWN_MS`.
  6. Mini-player: `scheduleYouTubeMiniPlayerRetryProbe` and the post-ack retries (only as far as relevant to our remaining cleanup paths).
  7. **Remove unsafe coordinate taps/swipes** in our fork (mini-player close fallback around L4823) as its own commit so the A/B can attribute the improvement.
- **Do not port:** Instagram/TikTok/Threads surfaces (deleted in our fork), the BlockerActivity YouTube-home redirect (W1.1 excluded it; if the experiment needs it, it lives only on this branch).
- **A/B protocol:**
  1. Dev builds can coexist thanks to `applicationIdSuffix = ".loqindev"` (app/build.gradle.kts L88). Install the current implementation as dev and the experiment as another dev suffix (temporarily add `.loqindev.yt` or rename) — or use two test devices.
  2. Same test script on each build, same device(s), same YouTube version (official + ReVanced/Morphe):
     - Shorts: open Shorts from Home, from Subscriptions, from a channel; swipe between Shorts; block must fire reliably, dismiss must land on Home.
     - Subscriptions tab and Shorts tab detection are not swapped; fast navigation does not misreport.
     - Watch page: normal video, live stream, Premium account, ad break — no false "floating player" block.
     - PiP: gesture PiP from watch and from Shorts; background YouTube; return to app; mini-player appearing after leaving Shorts.
     - Direct navigation: `vnd.youtube://` intents, notification taps, external links while blocked.
     - Cold start, process death, rotation, screen lock/unlock, ReVanced spoofed versions.
  3. Record per scenario: pass/fail, ANR, battery/jank observations, logcat category. Write results to `docs/yt-experiment-results-2.3.1.md` (create the file as part of this step).
  4. Decision: if upstream wins, port the winning commits into `feature/upstream-work` behind a single revertable commit; if ours wins, close the experiment branch and cherry-pick only clearly-safe pieces (e.g. watch-ad guard) after re-testing.
- **Risks:** running two implementations doubles QA; timebox the experiment (suggest 3 sessions on 2 devices) and stop.
- **Done when:** results documented and a merge/close decision is recorded.

---

## W5 — Protection change model (optional; own branch)

Do not start until W1–W3 are merged and stable. Decide first (see §8): adopt C1 as the single gate and remove `ProtectionEditPolicy`, or keep our policy and only take D3/D5/C2 pieces.

### W5.1 D3 — FeatureFlagStore + dev tiles
- Port `FeatureFlagStore.kt` (52 LOC) and the `AdvancedModeActivity` tile entries. Adapt to our Advanced-mode screen; no Firebase.

### W5.2 C1 — ProtectionChangeGate core
- Upstream: `util/ProtectionChangeGate.kt` (782 LOC), `receiver/ProtectionChangeReceiver.kt`, `util/ProtectionFeedback.kt`, `data/prefs/TemporaryPauseProtectionEditStore.kt`.
- Adaptations required:
  - Strip `PremiumManager` (the custom-delay branch is premium; omit it) and `CrashlyticsContext`.
  - Use our `DiagnosticsTimelineStore` for events.
  - Replace `EditingLockGuard`/`ProtectionEditPolicy` usage deliberately: gate every protection-sensitive edit through one path.
  - Pending queue: exclude from backup (`BackupSelection`); reschedule on `BootCompletedReceiver` and `PostUpdateReceiver`; register the receiver in the manifest.
- Call sites are diverged: adapt intent for `AppPickerActivity`, `ManageBlockedWebsitesActivity`, `ToggleOptionsActivity`, `InAppRulesActivity`, `QuickLimitDialogs`, `AppUsageDetailActivity`, `ManageProfilesActivity`. Do not apply upstream diffs directly.
- Omit the "allow edits during temporary pause" toggle (weakens the model).

### W5.3 C2 — Advanced Protection hub
- Port `AdvancedProtectionActivity.kt` + settings card + `strings_advanced_protection.xml` (en/de), adapted to our design language and without premium branches.
- Needs `TilesInfoActivity.Tile.rowAlpha` (we only have an inline `rowAlpha` local in `BlockingFeaturesActivity` — implement a proper property) and our dialog styles instead of `styleSwitchlyDialogButtons`.

### W5.4 D5 — Support report surfacing
- Extend `SupportActivity` with markdown-style sections, `ProtectionStateProvider` lines, timeline (D1), schema version, feature flags. Keep clipboard export; only add a `.md` file attachment via `FileProvider` if the user wants it (not required for the offline app).

---

## W6 — Polish

### W6.1 E10 — Edge-to-edge helpers (Phase 0 gap)
- `ui/EdgeToEdgeUtils.kt`: add `setupStandalone(activity, root)` and `enableEdgeToEdgeOnly(activity)` overloads for `AppCompatActivity` and plain `Activity`, mirroring upstream, using `FrameworkApi34Compat` guards first. Optionally switch `WindowCompat.enableEdgeToEdge(activity.window)` → `activity.enableEdgeToEdge()` (equivalent; only do it consistently).
- Call from camera/NFC entry screens (`BarcodeScanActivity`, `UnifiedScanActivity`, `QrScanActivity`, `ExternalQrActionActivity`, `ScanLauncherActivity`, `NfcEntryActivity`, `NfcWriteWaitingActivity`) — these were not covered by `setupClassic`.
- Verify insets on gesture-nav and 3-button-nav devices; camera preview must not draw under the status bar incorrectly.

### W6.2 E6 — Defer Maps fragment creation
- `LocationMapPickerActivity`: instantiate `SupportMapFragment` from a `post {}` on the map container, reuse an existing fragment, attach via `runOnCommit`, and only show the unavailable message on failure. Port upstream's structure.

### W6.3 E7 — Schedule save preview
- Port `SchedulePreviewFormatter.kt` and the save-preview dialog. Our `ScheduleStore.Action` has 2 extra values vs upstream, so the formatter's `when` must be extended; check every branch compiles/exhausts.

### W6.4 E8 — Pinned in-app rule → "Block entire app" dialog
- In our picker/in-app rules screen, when a user blocks a pinned in-app surface, offer blocking the whole app. Can be implemented without C1; if W5.2 lands, route through the gate.

### W6.5 E12 — Bottom-nav runtime menu + keep rules (low priority)
- `MainActivity` runtime menu and `res/raw/keep_bottom_navigation.xml`. Port only if the menu structure matches our redesign.

### W6.6 E9 — Onboarding reorder: **skip or defer**
- Our onboarding was redesigned (Control modes page, Permissions redesign). Upstream's 1.5k-line adapter reorder conflicts. Decision: skip unless a specific missing step is identified.

---

## 5. Upstream functions per area (quick index)

- Service YT: `isYouTubeAccessibilityBinderRiskDevice`, `findAnyYouTubeNode`, `isRootDirectlyFromPackage`, `maybeBlockYouTubePipOutsideYouTube`, `maybeBlockYouTubeDirectNavigationBeforeDedupe`, `showYouTubeFloatingPlayerBlock`, `isDirectYouTubeSubscriptionsEvent`, `hasYouTubeShortsEventBurstSignal`, `isDirectYouTubeShortsCardEvent`, `armYouTubeWatchAdPositionGuard`, `shouldProbeYouTubeWatchAdOverlayRoot`, `hasYouTubeWatchAdDismissEvent`, `isYouTubeWatchAdSignal`, `isYouTubePictureInPictureWindowVisible`, `scheduleYouTubePipWindowProbe`, `enforceAsyncYouTubePipWindowEvidence`, `scheduleYouTubeMiniPlayerRetryProbe`.
- Service infra: `activeRootWithBudget`, `currentRoot`, `accessibilityBinderWorker`, `AccessibilityWorkBudget.recordScan/recordRootLookup`.
- Limits: `getSessionLimitCached`, `ensurePerVisitSession`, `clearPerVisitSession`, `getPerVisitUsageMs`, `ensureActiveLimitSession`, `clearActiveLimitSession`, persisted restore in `getEnforcedLimitUsageMs`.
- Websites: `tryExtractWebsiteTargetFromBrowserUrlViews`, `websiteTargetFromText`, `domainFromText`, `DomainBlockStore.matches/globPathMatches`.

## 6. Explicit skip list

- `auth/AccountSignInFlow.kt`, all Google account auth.
- `premium/**`, `feature/premium/**`, `BillingDiagnosticsStore.kt`, premium custom-delay branches in `ProtectionChangeGate`.
- `util/CrashlyticsContext.kt` and every `CrashlyticsContext.syncAsync(...)` call.
- `app/build.gradle.kts` toolchain/flavor changes (AGP 9, Gradle 9.7.1, compileSdk 37, product flavors, Firebase plugins, Crashlytics mapping).
- `devices_24` drawable deletion (we still reference it in `DeviceInfoActivity.kt:45`).
- Instagram/TikTok/Threads/Reels surfaces.
- Upstream `SecureInputFields.kt` (conflicts with our rebuilt PIN dialog).
- Upstream `AppListAdapter` / `AppPickerActivity` diffs in bulk (ours is rewritten; port only specific behaviors listed in W1.2/W6.4).

## 7. Device / verification matrix

| Area | Devices | Notes |
|---|---|---|
| Blocker (W1.1) | Samsung A12 (A12), Pixel-class (A14/A15), Bigme (A14) | theme/binder edge cases |
| Settings gate (W1.2) | any, plus DeviceAdmin revoke flow | also verify uninstall protection UI |
| PIN (W1.3) | any | plus data wipe of legacy prefs |
| Fallback (W1.4) | any with Usage Access | Accessibility off scenario |
| Limits (W2) | any; include process kill and screen lock | session persistence matrix |
| Websites (W3) | Chrome, Brave, Firefox, Samsung Internet | path rules, address bar |
| A2 (W4.1) | Samsung A12, Bigme A14 | ANR/stall measurement |
| YT (W4.2) | official YT + ReVanced/Morphe, 21.35.x | mini-player/PiP, watch/live/premium |

## 8. Open decisions (ask the owner before starting the relevant step)

1. **A7 follow-up (current implementation = upstream behavior):** the gate is selection-time only, so an existing Settings rule keeps being enforced even if Device Admin is later revoked. Do we want an additional in-app warning when a Settings rule exists but the gate is no longer satisfied? Recommendation: add a non-blocking warning later; no rule clearing.
2. **W4.2 timebox:** suggested 3 test sessions on 2 devices; confirm before starting.
3. **W5 go/no-go:** adopt `ProtectionChangeGate` and remove `ProtectionEditPolicy`/`EditingLockGuard` from edit paths, or keep our simpler policy and only port D3/D5/C2?
4. **E9 onboarding:** confirm skip.
5. **D5 attachment:** clipboard-only (current) or add a `.md` share attachment via `FileProvider`?
6. **W3.1 follow-up:** should deleting a host rule also delete its path rules?
