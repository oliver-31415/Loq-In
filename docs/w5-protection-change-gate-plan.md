# W5.2 C1 — Protection Change Gate + Delay Queue: Implementation Plan

**Status:** in progress — P0–P2 done 2026-09-20 (see `upstream231.md` progress log; commits `e11e9b4`, `6774077`, `b90e72a`, `c2b11ea`, `8b9b628`, `3e7af0f`)
**Date:** 2026-09-20
**Proposed branch:** `feature/upstream-w5-protection-gate` (off `feature/upstream-w6-polish`, which now includes W2/W3/W4.1/W4.2-YouTube/W6.1/A7)
**Upstream source:** `at.saltyy.switchly.util.ProtectionChangeGate` (782 LOC), `receiver/ProtectionChangeReceiver`, `util/ProtectionFeedback`, `feature/settings/AdvancedProtectionActivity` (page only)
**Decision record:** `upstream231.md` §8 #3 — owner chose to adopt C1 (this plan); W5.1/W5.3/W5.4 remain separate.

---

## 1. What this builds and why

Today, while protection is active, a protection-weakening edit (unblock an app, delete a website rule, raise a limit) is **denied** and the user is pushed toward fully disabling Loq In. This plan replaces that all-or-nothing model with a central gate:

- **Stricter / neutral edits** → apply immediately.
- **Weakening edits** → if a **Protection Change Delay** is configured, they are **queued** and applied by an alarm after the delay; if the delay is 0 (default), they are **denied** exactly like today.
- **Structural changes** (control mode, website/in-app rule mode) → always denied while protection is active, never queued.

The delay is the feature: you can legitimately change your mind from inside the app without disabling everything, but you never get an *instant* unblock in a moment of weakness.

### Scope

**In scope**
- Pure policy/queue core with JVM unit tests.
- Android shell: SharedPreferences persistence, AlarmManager, receiver, boot/post-update/app-start hooks.
- A new settings page: **Protection changes** (delay selector + pending changes list).
- Phase 1 call-site migration: app selection, in-app rules, website rules, app limits, control mode, profile structure, remove-blocked-app quick action.
- Removal of `ProtectionEditPolicy`.
- Backup exclusion for the pending queue; support-report lines.

**Out of scope (Phase 2, separate decision)**
- Protection toggles in `ToggleOptionsActivity`/`BlockingFeaturesActivity` (mixed channels/access, paired UIDs, notifications, lock warnings).
- Ignored/hidden apps, NFC pairing writes, schedule edits, website limits, backup import per-item.
- Any notification when a queued change applies.

**Explicitly never gated**
- Emergency PIN, App lock / strict protection, device-admin flows.
- Runtime automation (NFC tag actions, schedule firing, quick actions, widgets) — those are not edits.
- Backup import as a whole: restore already requires the fully-off path and force-disables Loq In afterwards (`LocalBackupPayload.forceDisableLoqInAfterRestore`).

---

## 2. Architecture

### 2.1 Split for testability

Our test infrastructure is JUnit-only (no Robolectric). Therefore the gate is split into a **pure core** (no Android imports, fully unit-testable) and an **Android shell**:

| Layer | File (new) | Responsibility |
|---|---|---|
| Pure policy | `app/src/main/java/com/oliver/loqin/util/ProtectionChangePolicy.kt` | `Direction`/`Decision`/`Result` enums, direction classification for every edit family, decision matrix, delta splitting for mixed edits |
| Pure queue model | `app/src/main/java/com/oliver/loqin/util/PendingChange.kt` | `PendingChange` data class, JSON encode/decode, dedupe keys, due filtering, apply-time from-value guard helpers, list operations (add/cancel/apply-due) |
| Android shell | `app/src/main/java/com/oliver/loqin/util/ProtectionChangeGate.kt` | Context APIs mirroring upstream: `getDelayMinutes`/`setDelayMinutes`/`canSetDelayMinutes`, `decision`, `requestAppSelection`, `requestInAppSelection`, `requestWebsiteRemoval`, `requestWebsiteEnabled`, `requestAppLimits`, `requestControlMode`, `pendingChanges`/`pendingCount`/`cancelPending`/`cancelAllPending`/`canApplyPendingNow`/`applyPendingNow`/`applyAllPendingNow`/`applyDueChanges`/`reschedulePending`, store apply functions |
| Feedback | `app/src/main/java/com/oliver/loqin/util/ProtectionFeedback.kt` | `showQueued()` (dialog) and `showInfo()` using our `showAccented` styling |
| Receiver | `app/src/main/java/com/oliver/loqin/receiver/ProtectionChangeReceiver.kt` | Alarm target: `goAsync()` + background thread → `applyDueChanges` → `BlockingRuntime.ensureRunning` |
| Settings page | `app/src/main/java/com/oliver/loqin/feature/settings/ProtectionChangesActivity.kt` | Delay selector, pending list, actions |

`EditingLockGuard` **stays** as the lock source (`isLocked()`), plus its dialog/suppress helpers. Only `ProtectionEditPolicy` is deleted (Phase P4).

### 2.2 Decision matrix (must match upstream)

```
not locked                        → APPLY_NOW
locked + STRICTER/NEUTRAL         → APPLY_NOW
locked + WEAKER + delay > 0       → QUEUE_DELAYED
locked + WEAKER + delay = 0       → DENY          (parity with today)
locked + PROTECTED_STRUCTURAL     → DENY
```

Lock source: our `EditingLockGuard.isLocked(ctx)` (`util/EditingLockGuard.kt:39`) = `SwitchModeStore.isEnabled || isBaseEnabled || hasActiveTemporaryOverride`.

Delay: `delayOptionsMinutes = [0, 15, 60, 360, 1440]`; custom 1–10080 minutes (see Open Decision 1). While locked, the delay may only stay the same or **increase** (`canSetDelayMinutes`).

### 2.3 Direction classification

| Family | STRICTER | WEAKER |
|---|---|---|
| App selection (block mode) | superset of blocked apps | removing any blocked app |
| App selection (allow mode) | removing allowed exceptions | adding an allowed exception |
| In-app surface (block mode) | selecting a rule | unselecting a rule |
| In-app surface (allow mode) | unselecting | selecting |
| Website rule removal | allow mode | block mode |
| Website rule enable/disable | block mode: enable; allow mode: disable | the inverse |
| Numeric limit | `0→>0`, lower value | `>0→0`, higher value |
| Limit reset mode | session→day | day→session |
| Control mode / rule modes / profile delete+switch | — | — → `PROTECTED_STRUCTURAL` |

### 2.4 Mixed edits are split, never queued wholesale

- **App selection:** stricter additions applied now; weaker removals queued as a delta (`removePackages` / `addPackages`), never a full snapshot.
- **Limits:** each component (time, attempts, per-visit, reset mode) classified independently; stricter components applied now; weaker components queued **with their old values** (`fromTime`/`toTime`, …).

### 2.5 Queue data model

Stored in default SharedPreferences (`loqin_prefs`) under `protection_pending_changes_json`:

```json
[
  {
    "id": "uuid",
    "type": "app_selection|in_app_selection|website_remove|website_enabled|app_limits",
    "executeAtMs": 1750000000000,
    "createdAtMs": 1750000000000,
    "data": { "...": "..." }
  }
]
```

Rules:
- `executeAtMs = now + delayMinutes * 60_000`.
- Dedupe keys: `app:<profile>:<allowMode>`, `inapp:<profile>:<baseKey>`, `web-remove:<profile>:<rule>`, `web-enabled:<profile>:<rule>`, `app-limits:<profile>:<package>`. Queueing the same key replaces the old entry and restarts its timer.
- Apply-time re-validation: profile must still exist; limit components only apply when the current value still equals the recorded `from` value (a stricter edit made during the delay wins).
- `canApplyPendingNow()` is true only when Loq In is genuinely fully off: `!SwitchModeStore.isBaseEnabled && !hasActiveTemporaryOverride && !EmergencyBypassStore.isActive && !isPaused`. Temporary pause and Emergency Unlock must not shortcut the delay.
- Pending queue is **excluded from backups**.

### 2.6 Scheduling

- Arm `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, earliest.executeAtMs, pendingIntent)` targeting `ProtectionChangeReceiver` (inexact is fine; due time is a floor; no exact-alarm permission needed).
- Re-arm on every queue/cancel/apply.
- `applyDueChanges()` also runs from: app start (`SwitchlyApp`/`LoqInApp` startup path), `BootCompletedReceiver`, `PostUpdateReceiver` (alarms do not survive reboot).
- Receiver runs on a background thread and calls `BlockingRuntime.ensureRunning` after applying so enforcement picks up changes immediately.

---

## 3. File inventory

### New
| File | Notes |
|---|---|
| `app/src/main/java/com/oliver/loqin/util/ProtectionChangePolicy.kt` | pure |
| `app/src/main/java/com/oliver/loqin/util/PendingChange.kt` | pure |
| `app/src/main/java/com/oliver/loqin/util/ProtectionChangeGate.kt` | Android shell |
| `app/src/main/java/com/oliver/loqin/util/ProtectionFeedback.kt` | queued/info dialogs |
| `app/src/main/java/com/oliver/loqin/receiver/ProtectionChangeReceiver.kt` | alarm target |
| `app/src/main/java/com/oliver/loqin/feature/settings/ProtectionChangesActivity.kt` | settings page |
| `app/src/main/res/layout/activity_protection_changes.xml` | toolbar + scroll + cards |
| `app/src/main/res/values/strings_protection_changes.xml` + `values-de/…` | all copy |
| `app/src/test/java/com/oliver/loqin/util/ProtectionChangePolicyTest.kt` | unit tests |
| `app/src/test/java/com/oliver/loqin/util/PendingChangeQueueTest.kt` | unit tests |
| `/tmp/opencode/pcg_test.py` | emulator driver (not committed) |

### Modified
| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | register `ProtectionChangeReceiver` (`exported=false`, no filter); register `ProtectionChangesActivity` (`exported=false`, parent `SettingsActivity`) |
| `app/src/main/java/com/oliver/loqin/ui/LoqInApp.kt` (or `LoqInApp` equivalent) | `applyDueChanges` on startup |
| `app/src/main/java/com/oliver/loqin/platform/receiver/system/BootCompletedReceiver.kt` | `applyDueChanges` |
| `app/src/main/java/com/oliver/loqin/platform/receiver/system/PostUpdateReceiver.kt` | `applyDueChanges` |
| `app/src/main/java/com/oliver/loqin/feature/settings/SettingsActivity.kt` | new card wiring in `setupRootCards()` (~461–488), search index entry (~210–226), optional `restrictedCards` (~510–514) |
| `app/src/main/res/layout/activity_settings.xml` | new `cardSettingsProtectionChanges` in the Controls section (~44–110) |
| `app/src/main/java/com/oliver/loqin/data/sync/LocalBackupPayload.kt` | add `protection_pending_changes_json` to `backupExcludedExactKeys` (~60–64) |
| `app/src/main/java/com/oliver/loqin/data/sync/BackupSelection.kt` | route `protection_change_delay_minutes` to `CONTROL_SETTINGS` in `categoriesForDefaultPrefsKey()` (~372–459) |
| `app/src/main/java/com/oliver/loqin/feature/support/SupportActivity.kt` | delay + pending lines in diagnostics |
| Phase 1 call sites (table in §5) | gate every write |
| `app/src/main/java/com/oliver/loqin/util/ProtectionEditPolicy.kt` | **delete** in P4 |

---

## 4. Settings page — "Protection changes"

### 4.1 Entry point
- Settings → **Controls** section → new card `cardSettingsProtectionChanges` (icon `security_24` or `schedule_24`, title "Protection changes", summary = delay state + pending count, refreshed in `onResume`).
- Wired with `openProtectedSettingsSection { startActivity(Intent(this, ProtectionChangesActivity::class.java)) }` (same pattern as `cardSettingsBlockingFeatures`, `SettingsActivity.kt:462–466`).
- Add to `showSettingsFinder()` so it is searchable (`settings_search_terms_protection_changes`).
- Manifest: `android:exported="false"`, `android:parentActivityName=".feature.settings.SettingsActivity"`.

### 4.2 Page layout (`activity_protection_changes.xml`)
Toolbar (`style="@style/LoqIn.TopBar"`, back arrow) + `NestedScrollView` with:
1. **Delay card** (`@style/LoqIn.Card`): title row + current value (`Off` / `15 minutes` / `1 hour` / … / `N minutes`), explanation (`protection_change_delay_summary_off` / `_on`), tap opens the delay dialog. Locked subtitle when protection is active.
2. **Pending changes card**: count line (plurals) + programmatic rows in a `LinearLayout` (each: label, relative due time, chevron → per-item actions) + footer actions (`Apply all now` only when `canApplyPendingNow()`, `Discard all`). Empty state uses `protection_pending_changes_none`.
3. **Info card**: explains stricter vs weaker, that stricter changes apply immediately, that early application requires Loq In fully off, and that the delay can only increase while active.

Use `EdgeToEdgeUtils.setupClassic(activity = this, toolbar = toolbar)` (pattern: `BlockingFeaturesActivity.kt:120–128`).

### 4.3 Delay dialog
`AlertDialog.Builder` with `setSingleChoiceItems` (presets + `Custom time…`) and `showAccented()`:
- Selecting a preset calls `ProtectionChangeGate.setDelayMinutes`; on failure show `protection_change_delay_locked`.
- Custom opens `showLoqInInputDialog` (numeric, 1–10080) → `setDelayMinutes`.
- Current value preselected; custom current value shown as selected custom entry.

### 4.4 Pending changes dialog (page card → per-item)
- Item list dialog: `setItems` of labels + relative time; neutral `Discard all`; positive `Apply all now` only when allowed.
- Per-item dialog: title `Apply this pending change now?` when allowed, else `Discard this pending change?`; neutral discard; positive apply.
- All actions refresh the page and show `ProtectionFeedback.showInfo` confirmations.

### 4.5 Strings (en + de, new file)
Port from upstream `strings_advanced_protection.xml` and adapt product name:
`protection_change_delay_title`, `_off`, `_15m`, `_1h`, `_6h`, `_24h`, `_summary_off`, `_summary_on`, `_locked`, `_custom`, `_custom_title`, `_custom_message`, `_custom_hint`, `_custom_invalid`, plurals `_minutes_value`/`_hours_value`/`_days_value`; `protection_pending_changes_title`, `_none`, plural `_count`, `_open`; item labels `protection_pending_item_app_rules`/`_in_app`/`_website`/`_app_limits`/`_unknown`; cancel strings `protection_pending_cancel_one_title/_action/_done`, `protection_pending_cancel_title/_message/_action/_cancelled`; apply strings `protection_pending_apply_one_title/_action/_done/_failed`, `protection_pending_apply_all_title/_message/_action/_done`; feedback `protection_change_queued` (from upstream `strings_settings.xml:84`). Add `settings_protection_changes_title/_summary` and `settings_search_terms_protection_changes`.

---

## 5. Call-site migration — Phase 1

All line numbers are current at plan time; re-verify before editing.

| # | Surface | File / function | Gate call | Result handling |
|---|---|---|---|---|
| 1 | App picker save (single + bulk + save button) | `AppPickerActivity.saveManagedApps` (963–1001), `saveCurrentModeSelection` (126–133); `original = originalManagedPackages` (107) | `requestAppSelection(profile, allowMode, original, requested)` | APPLIED → save + finish; QUEUED → `ProtectionFeedback.showQueued(afterDismiss = finish)`; DENIED → existing locked dialog |
| 2 | App picker row/tile toggles | `AppPickerActivity.canChangeSelection` (151–159) via adapter provider (291/354) | `canInteractWithAppSelection(allowMode, current, requested)` | deny → revert + locked pill (as today) |
| 3 | Bulk clear-all / clear-unavailable | `AppPickerActivity.setupBulkButtons` (850–894); `AppListAdapter.clearUnavailable/clearAllVisible` (130–181) | clear = weakening; route through the same delta gate before writing limits/rules | QUEUED → queue deltas; DENIED → locked dialog |
| 4 | Unavailable-tile uncheck (clears limits + in-app rules) | `AppListAdapter` (417–436) | gate selection delta + `requestAppLimits(0)` + in-app clears | same as above |
| 5 | Auto-block-new-apps checkbox | `AppPickerActivity.setupAutoBlockNewAppsCheckbox` (828–848) | enable = STRICTER (apply); disable = WEAKER (queue) — new `requestAutoBlockNewApps` | queued feedback |
| 6 | In-app surface toggle | `InAppRulesActivity.buildSurfaceRow` (770–855), `writeProfileBool` (990–992) | `requestInAppSelection(profile, pkg, baseKey, surfaceKey, allowMode, current, requested)` | APPLIED → update UI; QUEUED → feedback + revert switch; DENIED → locked pill |
| 7 | In-app render-time side-effect write | `InAppRulesActivity` (815–819) | must not write while locked/queued; only apply when `isChecked && !readOnly` and the gate says APPLY_NOW | — |
| 8 | In-app rule mode switch | `InAppRulesActivity.applyRuleMode` (206–279) | `requestControlMode`-style `PROTECTED_STRUCTURAL` (new `requestInAppRuleMode`) | DENIED while locked |
| 9 | Website add | `ManageBlockedWebsitesActivity.showRuleDialog` (675–735) | add = STRICTER → apply now (keep `canAddBlockedWebsite` semantics) | — |
| 10 | Website edit | same | split: removal (WEAKER) + addition (STRICTER) | queued removal + immediate addition |
| 11 | Website delete (single + bulk) | `removeRule`/`confirmDeleteSelected` (499–579), `WebsiteUsageDetailActivity` delete (746–778) | `requestWebsiteRemoval(profile, rule)` | APPLIED/QUEUED/DENIED feedback |
| 12 | Website enable/disable | `setRuleEnabled` (559–565) | `requestWebsiteEnabled(profile, rule, current, requested)` | same |
| 13 | Website rule mode | `setupWebsiteRuleMode` (184–230) | `PROTECTED_STRUCTURAL` | DENIED while locked |
| 14 | App limits | `QuickLimitDialogs.showAppLimitEditor.applyValues` (535–561); `AppUsageDetailActivity` (902) | `requestAppLimits(profile, pkg, time, attempts, perVisit, resetMode)` | APPLIED → refresh; QUEUED → feedback; DENIED → locked dialog |
| 15 | Control mode | `ToggleOptionsActivity.applyControlModeSelection` (656–720); `BlockingModeSheet.selectMode` (527–542) | `requestControlMode(mode)` | QUEUED/DENIED → locked dialog (parity) |
| 16 | Profile delete / switch | `ManageProfilesActivity.deleteProfile` (358–377), `setActiveProfile` (413–421) | `PROTECTED_STRUCTURAL` | DENIED while locked (already today) |
| 17 | Remove blocked app quick action | `MainActivity.removeBlockedApp` (4013–4037) | app-selection delta + limit clears via gate | feedback |
| 18 | Website limits | `QuickLimitDialogs.showForWebsite` (598–649), `WebsiteUsageDetailActivity` (735) | **keep locked** (denied) in Phase 1 | — |

Notes:
- The picker holds edits in memory until Save, so the gate must run **at save** for the authoritative decision and at row-toggle time for immediate feedback (`canInteract…`).
- After P4, `ProtectionEditPolicy` is deleted; its three call sites are replaced by the rows above.
- Keep the existing `LoqInAppAccessGuard.isControlSettingsLocked` recovery exception for screens that must stay reachable during Emergency Unlock / temporary pause (Open Decision 3); the gate's `decision()` still uses `EditingLockGuard.isLocked` for everything else.

---

## 6. Platform integration details

| Item | Detail |
|---|---|
| Manifest receiver | `<receiver android:name=".receiver.ProtectionChangeReceiver" android:exported="false" />` (targeted PendingIntent only) |
| Boot/update/start | `applyDueChanges()` in `BootCompletedReceiver` (after `SwitchModeStore.ensureInit`), `PostUpdateReceiver`, and app startup (after blocking runtime init) |
| Alarm | `setAndAllowWhileIdle(RTC_WAKEUP, …)`, request code constant, `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` |
| Diagnostics | `DiagnosticsTimelineStore.record(ctx, "Protection", "Change queued/applied/denied", details)`; `AppLogStore.appendRateLimited` for failures |
| Backup | pending JSON excluded (`backupExcludedExactKeys`); delay key in `CONTROL_SETTINGS` category |
| Support report | add `Protection Change Delay: Off/N min` and `Pending protection changes: N` to the diagnostics block |
| Strings | en + de mirrored files; no hardcoded English |

---

## 7. Test plan

### 7.1 JVM unit tests (`./gradlew :app:testDebugUnitTest`)

`ProtectionChangePolicyTest` (pure):
1. Selection block mode: superset → STRICTER; removal → WEAKER; equal → NEUTRAL.
2. Selection allow mode: removal → STRICTER; addition → WEAKER.
3. In-app directions both modes.
4. Website removal: block mode WEAKER, allow mode STRICTER.
5. Website enable/disable directions both modes.
6. Numeric limits: `0→10` STRICTER, `10→0` WEAKER, `10→5` STRICTER, `5→10` WEAKER, equal NEUTRAL.
7. Reset mode: session→day STRICTER, day→session WEAKER, no time limit NEUTRAL.
8. Decision matrix: unlocked → APPLY_NOW; locked stricter/neutral → APPLY_NOW; locked weaker delay 0 → DENY; delay > 0 → QUEUE_DELAYED; structural → DENY.
9. Delta split: mixed selection produces immediate stricter set + queued weaker list.
10. Delta split: mixed limits produce immediate stricter components + queued weaker components with `from` values.
11. `canSetDelayMinutes`: unlocked any valid; locked only same/longer; invalid rejected.

`PendingChangeQueueTest` (pure):
12. JSON encode/decode round trip (all types, unknown fields ignored).
13. Dedupe: same key replaces entry and resets `executeAtMs`.
14. Different keys coexist.
15. Due filtering by `nowMs`.
16. Apply-time limit guard: `from` matches → apply; `from` differs (stricter edit since) → skip.
17. Profile-missing entry is dropped.
18. Cancel single / cancel all.
19. Sorted-by-due ordering.
20. `canApplyPendingNow` inputs (as pure predicate).

### 7.2 Emulator scenarios (`emulator-5554`, API 36, package `com.oliver.loqin.loqindev`)

Harness: `/tmp/opencode/pcg_test.py` — force-stop, inject `protection_change_delay_minutes` via `run-as` (same pattern as `yt_mini_pair.py`), drive UI with `tap_text.py`/`tap_exact.py`, read `loqin_prefs.xml` and `loqin_last_block_reason.xml` for assertions.

1. **Parity (delay 0):** locked, unblock an app in the picker → locked dialog, rule unchanged.
2. **Queue:** delay 1 min, locked, unblock app → queued dialog; pending JSON has 1 entry; app still blocked; after due → entry gone, app unblocked, blocking updated.
3. **Stricter immediate:** locked, block a new app → applies instantly, no queue entry.
4. **Mixed:** delay 1 min, in one save block A + unblock B → A blocked now, B queued.
5. **Cancel:** queue then discard → rule unchanged after due.
6. **Apply now:** queue, fully disable Loq In, apply now → immediate; repeat while temporary pause/emergency unlock active → refused.
7. **Dedupe:** queue unblock B twice with different values → single entry, timer reset.
8. **Reboot:** queue with delay 15 min, `adb reboot`, verify entry survives and alarm is re-armed (`dumpsys alarm`).
9. **Process death:** `am force-stop` after queueing → alarm still delivers (`setAndAllowWhileIdle`) and applies.
10. **Doze:** `dumpsys deviceidle force-idle`, verify deferred until idle exit/maintenance, then applied.
11. **Limit guard:** queue a limit raise, then lower the limit stricter before due → queued raise skipped.
12. **Profile deleted:** queue, delete profile → entry dropped without crash.
13. **Delay locked rule:** while locked, opening the delay dialog and choosing a shorter value → locked message; longer value applies.
14. **Website / in-app / control mode:** delete website rule (queued), toggle in-app rule (queued), change control mode (denied), toggle in-app rule mode (denied).
15. **Backup:** export with a pending entry → JSON does not contain `protection_pending_changes_json`; delay value is included under CONTROL_SETTINGS.
16. **Page UI:** en + de, light + dark, gesture + 3-button nav, locked + unlocked, empty + populated pending list.

### 7.3 Phone / real-device checklist (Pixel 10 Pro XL, plus Samsung A12 / Bigme if available)

- Install, set 15-minute delay, queue an unblock, verify it applies after 15 minutes with the screen off.
- Reboot before due; verify application after boot.
- Battery optimization on/off; verify alarm still fires (Doze/OEM).
- Emergency Unlock and temporary pause cannot apply pending changes early.
- Locked-state UX: pending list visible, discard works, delay can only increase.
- Verify no regression in existing blocking (website matrix `ff_matrix.py`, YT smoke `yt_test.py`, W6.1 scanner/NFC screens, A7 picker warning).

### 7.4 Device matrix (from `upstream231.md` §7)

| Area | Devices |
|---|---|
| Gate + delay | emulator-5554 (API 36), Pixel 10 Pro XL |
| Alarm/OEM behavior | Samsung A12 (A12), Bigme (A14) |
| Locked/unlocked + recovery | any, plus Emergency Unlock / temporary pause paths |

---

## 8. Phased implementation

| Phase | Work | Commit(s) | Gate before next phase | Est. |
|---|---|---|---|---|
| **P0** | Pure `ProtectionChangePolicy` + `PendingChange` + both unit test classes | 2 commits | `./gradlew :app:testDebugUnitTest` green | 1 d |
| **P1** | `ProtectionChangeGate` shell + receiver + manifest + boot/update/start hooks + `ProtectionFeedback` + diagnostics; wire one pilot call site (website delete) for end-to-end proof | 2–3 commits | Emulator scenarios 1–3, 8, 14 (website) | 1.5 d |
| **P2** | Settings page + layout + strings (en/de) + settings card + search + manifest | 2 commits | Page UI scenarios 13, 16 | 1.5–2 d |
| **P3** | Phase 1 call sites table §5 rows 1–17 | 3–4 commits grouped by surface | Emulator scenarios 2–7, 9–12, 14; existing test suites | 2–3 d |
| **P4** | Delete `ProtectionEditPolicy`; consolidate lock dialogs; queued feedback polish | 1 commit | Regression smoke (picker, websites, in-app, limits, profiles, control mode) | 0.5–1 d |
| **P5** | Backup exclusion + category, support report, docs (`upstream231.md`) | 1–2 commits | Backup round-trip scenario 15 | 0.5 d |
| **P6** | Full matrix execution (emulator + phone + OEM), fixes, final review, merge decision | 1–3 commits | All §7 scenarios pass; owner sign-off | 1.5–2 d |

Total estimate: **~9–11 working days**.

---

## 9. Risks & mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Alarm not delivered on aggressive OEMs | Change never applies | `setAndAllowWhileIdle` + `applyDueChanges` on app start/boot/update; OEM test matrix; surface pending list so the user can see it |
| Delta logic bugs overwrite stricter edits | Protection weakened unintentionally | Pure-core unit tests for delta splitting + apply-time `from`-value guards; never persist snapshots |
| Picker in-memory state diverges from store | Wrong delta computed | Use `originalManagedPackages` captured at load; re-read store in `requestAppSelection`; unit tests |
| Behavior change at delay 0 breaks current UX | Regression reports | Decision matrix parity; P1 pilot + P3 staged migration; keep `ProtectionEditPolicy` until P4 verified |
| Recovery paths blocked by the gate (Emergency Unlock / temporary pause) | User locked out of control settings | Preserve `LoqInAppAccessGuard.isControlSettingsLocked` exception (Open Decision 3); dedicated scenarios |
| Pending queue leaks into backups | Stale queue restored on another device | Exclude key; scenario 15 |
| User confusion: "my change didn't apply" | Support load | Queued dialog + pending page + relative due times + support report line |
| Duplicate lock dialogs (gate + screen) | Bad UX | One feedback path per result (APPLIED/QUEUED/DENIED); P4 consolidation |

---

## 10. Rollback

- The feature is additive with delay default 0, so the gate at delay 0 reproduces current behavior; shipping without the page would already be safe.
- Keep `ProtectionEditPolicy` until P4; if P3 regresses, revert the call-site commits and the pilot, leaving the core + page dormant.
- `git revert` per phase (commits are grouped by surface); the queue key is ignored by older builds.
- Optional debug override: force `getDelayMinutes` to 0 from Advanced mode for a kill switch (Open Decision 6).

---

## 11. Open decisions

1. **Custom delay availability:** upstream's custom delay is premium. Recommendation: allow custom 1–10080 minutes for everyone (no premium in this fork; also simplifies testing). Alternative: presets only in release + debug-only custom.
2. **Auto-block-new-apps disable:** upstream does not gate it. Recommendation: gate the disable as WEAKER (enable applies now).
3. **Recovery exception:** keep `LoqInAppAccessGuard.isControlSettingsLocked` for control-settings screens during Emergency Unlock / temporary pause. Recommendation: yes.
4. **Page placement:** Settings → Controls → "Protection changes". Recommendation: yes; W5.3's Advanced Protection hub can later absorb it.
5. **Phase 2 scope:** protection toggles, ignored/hidden apps, NFC pairing writes, schedule edits, website limits, backup import. Recommendation: decide after P6 with real usage.
6. **Apply notification / kill switch:** no notification in v1; add an Advanced-mode "force delay = 0" debug override. Recommendation: yes.

---

## Appendix A — Upstream references to port

- `util/ProtectionChangeGate.kt` — decision/direction/delta/queue/apply logic (port semantics, not code shape; strip `PremiumManager` custom-delay branch and `CrashlyticsContext`).
- `receiver/ProtectionChangeReceiver.kt` — alarm receiver (33 LOC).
- `util/ProtectionFeedback.kt` — queued/info dialogs.
- `feature/settings/AdvancedProtectionActivity.kt` — delay dialog (228–305), pending dialog (310–430); we port only the change-delay/pending parts.
- Strings: `values/strings_advanced_protection.xml:56–122` (delay + pending), `values/strings_settings.xml:84` (`protection_change_queued`).

## Appendix B — Deliberate differences from upstream

| Topic | Upstream | Ours |
|---|---|---|
| Custom delay | premium-gated | allowed for everyone (Open Decision 1) |
| Edits during temporary pause | `TemporaryPauseProtectionEditStore` weakens the model | omitted |
| Page | part of a larger Advanced Protection hub | dedicated "Protection changes" page (hub is W5.3) |
| Lock source | upstream `EditingLockGuard.isLocked` | our `EditingLockGuard.isLocked` (SwitchModeStore semantics) |
| Scheduling | `setAndAllowWhileIdle` | same (no exact-alarm dependency) |
| Website limits | not gated by C1 | stay locked in Phase 1 |
