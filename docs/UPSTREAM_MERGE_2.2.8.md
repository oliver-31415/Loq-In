# Upstream merge plan — 2.2.4 → 2.2.8 (test → foqos-ui)

> **STATUS: DONE** (2026-09-04). Merged as `856abee`, post-fixes in `4f090d2`.
> Rollback point: tag `backup/foqos-ui-pre-2.2.8` (= `0c5e6c5`).
> Extra fix discovered during verification: upstream's offline-flavor
> `configure<CrashlyticsExtension>` assumed the (conditionally applied) Crashlytics
> plugin — now deferred via `pluginManager.withPlugin` so public builds configure.
> Also fixed during verification: hardcoded-white toolbar menu icons (light mode) and
> idle-hero white-on-light text (`applyHeroIdleContrast`).

Prepared 2026-09-04. Upstream `test` = `be4ab60` (2.2.8); our `main` = `2d5cd95` (2.2.4,
> already merged base). `foqos-ui` carries the restyle. Upstream ships the new work on
> `test` and merges `test → main` as release MRs; 2.2.5/2.2.7/2.2.8 are the three commits
> to integrate (`git log main..upstream/test`).

## 1. What upstream adds (2.2.5 → 2.2.8)

194 files, +7294/−1370. 181 files are **new or untouched by us → auto-merge**. Highlights:

| Area | Files | What it is |
|---|---|---|
| Scan unification | `feature/scan/UnifiedScanActivity.kt` (new), MainActivity header action | One scanner (QR/barcode/auto); header button now calls `openHeaderScanner()` |
| Usage-access blocking | `blocking/UsageAccessFallbackBlocking*.kt` (new), `AdvancedProtectionCompat` (new) | Blocking path without accessibility service (Android 15+ restricted settings) |
| OEM keep-alive | `blocking/OemAccessibilityKeepAlive*` (new), `BootCompletedReceiver` | Keeps accessibility alive on OEM kills |
| API-34 crash shield | `util/FrameworkApi34Compat.kt` (new), `EdgeToEdgeUtils`, `ThemeUtils`, `values/layout.xml`, themes | Crashlytics-driven guards for malformed Android 14 images (missing `setLineHeight(int,float)`, `systemOverlays()`, `ACTION_SCROLL_IN_DIRECTION`) + **edge-to-edge rework** (`WindowCompat.enableEdgeToEdge`, insets applied to `android.R.id.content`, `statusBarColor`/`navigationBarColor` removed from Blocker + old theme) |
| Most-used apps | `feature/usage/MostUsedAppsActivity.kt` (new, 662 ln), `UsageStatsRepo`, `AppLaunchCountStore` | New statistics screen |
| Quick limits rework | `QuickLimitDialogs`, `dialog_app_limits.xml` (new) | Old two-step "edit limits" option dialog replaced by one combined dialog; 5 strings removed from `strings_home.xml` |
| Profile management | `ManageProfilesActivity`, `ProtectionEditPolicy` (new) | Profile editing allowed while protection on (destructive ops locked inside) |
| Home logic | `MainActivity` (189 ln) | `openProfiles()` replaces `openProfilesIfUnlocked`; app-picker lock respects mixed-allow (`AutomationModeStore.isMixedAllowAppPicking`); setup card distinguishes "accessibility on but not connected" (`dashboard_accessibility_not_connected`, new string); quick-action tiles get dynamic equalized heights; blocked-list rebind via `notifyItemRangeChanged`; scanner header rework |
| Version | `build.gradle.kts` | versionCode 228 / versionName 2.2.8, offline-flavor Crashlytics mapping upload off |
| Misc | `SupportActivity`, `OnboardingActivity`, `PermissionsActivity`, `NfcWriteWaitingActivity`, `PlayStoreUpdatePrompt`, `UpdateReleaseInfo` (new), `SwitchlyOverviewActivity`, `AppPickerActivity`, changelog.json, many layouts (+1-line e2e tweaks), EN/DE strings | Feature polish; layouts get edge-to-edge compat tweaks |

**Upstream did NOT touch**: `BlockedTimeStore`, `FoqosHeatmapView`, `Dialogs.kt`,
`colors_foqos*`, `row_home_profile.xml`, `sheet_profile_edit.xml`, `menu_top_main.xml`
→ zero conflict with the hero/heatmap/tile/dialog work.

## 2. Conflict map — the 13 overlapping files

| File | Upstream change | Resolution |
|---|---|---|
| `build.gradle.kts` | version 228, Crashlytics offline config, import | **Union** — keep our `applicationIdSuffix`, take all of theirs. Likely auto-merges. |
| `SwitchlyAccessibilityService.kt` | ~40 hunks (YouTube probe rework right above `usageTick`, usage-fallback hooks, OEM keep-alive, in-app enforcement) | Ours = 3 small additions (field, `trackProtectionTime()`, 1 call at top of `usageTick()`). **Keep their hunks, re-add our function + call.** Then **move the `ensureProtectionTodayAtLeast(activeDurationMs)` reconciliation into the service tick** so the session floor survives without Home open (see §4 Risks). |
| `MainActivity.kt` | 189 ln: scanner, lock policy, quick-action heights, limits dialog, setup card, rebind | **Union, region by region** — take all their behavioral changes; keep every restyle addition (heatmap/tiles/hero bindings, `showSwitchlyInputDialog` flows, `styleHeroToggle`, `styleQuickTile`, `HeroArtDrawable`, `retintUnthemedPrimaryIcons` caller). Watch the import block (they drop Qr/Barcode imports — we still use them in `openQrScannerDirectly`/`openBarcodeScannerDirectly`, keep those imports). |
| `ThemeUtils.kt` | +`FrameworkApi34Compat.applyThemeWorkaround(activity)` call | **Union** — add their 2 lines to our version (which has the icon-retint fallback). |
| `res/values/themes.xml` | Their 19 ln edit targets the OLD MaterialComponents theme (typography attrs + `statusBarColor` removal) | **Keep ours wholesale.** Their typography items already exist in our self-contained `Theme.Switchly.Base` (crash-fix token set), and the API-34 overlay overrides typography only on broken frameworks. Evaluate `statusBarColor` removal separately (§4 e2e). |
| `res/values-night/themes.xml` | Same, on their night theme | **Keep ours** (thin status-bar override), same reasoning. |
| `res/values/layout.xml` | +`SwitchlyApi34FrameworkCompatOverlay` + `Switchly.Api34SafeText.*` (pure addition at top), Blocker variant drops status/nav bar colors, `Switchly.TextInputLayout` textAppearance → `?attr/textAppearanceBody1`, DropdownHint drops lineHeight | **Take the whole new block**, take Blocker color removal + hint change; for `Switchly.TextInputLayout` keep ours (`enforceTextAppearance=false`, Pitfall #1) and add their `?attr/textAppearanceBody1` only if it doesn't reintroduce the ThemeEnforcement path — verify on device in night mode. |
| `res/layout/activity_main.xml` | Comment-only change | **Keep ours.** Nothing to port. |
| `res/layout/activity_settings.xml` | Comment-only changes | **Keep ours.** |
| `res/values/colors.xml` (+night) | +`switchly_text_primary`/`switchly_text_secondary` | **Union** — additive, keep our `switchly_bg` values. |
| `res/values/strings_home.xml` (+de) | −5 limit strings (moved into `dialog_app_limits.xml` world), +`dashboard_accessibility_not_connected` | **Union**, then `grep -r` the 5 removed names — only the OLD `showBlockedAppLimitActions` used them, which upstream replaces; drop them for real. Keep all our `tile_*`/hero strings. |
| — | — | `EdgeToEdgeUtils.kt`, `AndroidManifest.xml`, `SwitchlyApp.kt`, all other 168 files: **auto-merge** (we never touched them). |

## 3. Procedure

```bash
# 0) commit the current restyle work (13 modified files) — merge needs a clean tree
git checkout foqos-ui && git add -A && git commit   # "feat - duration cells, live day total, accent fallbacks"
git branch backup/foqos-ui-pre-2.2.8                # rollback point

# 1) sync main (fast-forward; main has no local edits)
git checkout main && git merge upstream/test        # ff to be4ab60
git push origin main

# 2) merge into the restyle branch
git checkout foqos-ui && git merge main             # resolve per §2

# 3) post-merge sweep
grep -rn "dashboard_blocked_app_limits_title\|dashboard_blocked_app_action_time_limit\|dashboard_blocked_app_action_open_limit" app/src/main/java || true
sh gradlew assembleOfflineDebug && sh gradlew lintOfflineDebug   # lint: expect only the known pre-existing themes.xml DuplicateDefinition errors
```

## 4. Risks & follow-ups

1. **Edge-to-edge rework touches every screen** (forced `enableEdgeToEdge`, content-view
   insets, Blocker/system-bar colors removed). Our home already handles insets (scroll
   padding, status-bar inset on scroll) — but every OTHER screen's look must be
   re-checked in light+dark, especially BlockerActivity and the dock-less home.
   `Theme.Switchly.Base` still sets `android:statusBarColor ?attr/colorSurface` —
   upstream removed theirs; if any screen double-pads or shows a colored bar, remove it
   there too.
2. **Protection-time tracking vs the new usage-access blocking path**: our counter lives
   in the accessibility service. If a device blocks via
   `UsageAccessFallbackBlockingService` instead (no accessibility), protection time
   under-counts. Follow-up: mirror the accrual/`ensure` there, or accrue in
   `BlockingRuntime` where both paths meet.
3. **`MainActivity` merge is the delicate one** — resolve hunk by hunk, rebuild after,
   and smoke-test: hero art + toggle, heatmap cells/durations, tiles, edit sheet,
   rename/create/delete dialogs, blocked list rebind, scanner header, setup card.
4. **New upstream UI keeps the old theme system** (`dialog_app_limits.xml`,
   `MostUsedAppsActivity`, `UnifiedScanActivity`, `SupportActivity` additions use
   `?attr`/MaterialComponents styles). They'll render but may show non-foqos styling —
   restyle pass AFTER the merge compiles and runs, one screen at a time.
5. `git merge main` will also drag in `docs/`, `changelog.json`, manifest entries —
   all additive.

## 5. Verification checklist (after merge, on device)

- [ ] `assembleOfflineDebug` + install + no `FATAL EXCEPTION` in light AND dark mode (Pitfall #1 rule: reach every touched screen in both modes)
- [ ] Home fits one screen; hero blob art + Enable/Disable pill states; "Active now" timer
- [ ] Heatmap: day numbers above cells, durations inside, today accumulates across two sessions, cell never below "Active now"
- [ ] Edit-profile sheet: accent icons + Rename label, dark rename/create dialogs, delete confirm red
- [ ] Settings: pink/accent icons; Appearance switch still accent
- [ ] New: header scanner opens UnifiedScan; Most-used apps screen opens from stats; blocked-list "Edit limits" opens the new combined dialog
- [ ] BlockerActivity still full-bleed after system-bar color removal
- [ ] Accent switch (pink → orange → custom) propagates on Settings + Home
