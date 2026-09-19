# YouTube experiment — upstream 2.3.x vs our rebuilt implementation

- **Branch:** `experiment/yt-upstream-2.3.1` (off the W4.1 tip)
- **Reference:** upstream `be4ab60..bf9526b` (`SwitchlyAccessibilityService.kt`, ~1800 changed lines)
- **Device:** AVD `HolyPixel` (Android 36, x86_64, Work Budget from W4.1)
- **YouTube:** official `com.google.android.youtube` 21.36.45
- **Rules under test:** Shorts, Subscriptions, You (Home allowed)
- **Date:** 2026-09-18

## Outcome summary

Our rebuilt YouTube implementation was already correct on every scenario we could
reproduce (baseline 9/9 before any port). Upstream's 2.3.x changes are largely
targeted at surfaces this fork removed (user-facing PiP/mini-player rules) or at
races our implementation already handles. Three upstream pieces were still worth
porting and are now on this branch; two upstream pieces were evaluated and left
out on purpose; one fork-side hazard (unsafe coordinate taps) was removed.

## Ported (commit `ad814d2`)

| Upstream piece | Why it helps us |
| --- | --- |
| `isYouTubeAccessibilityBinderRiskDevice()` + gated deep walks | Samsung Android 12 / Bigme ANR path; matches W4.1's work-budget goal. Applied in `youtubeSurfaceFromEventPosition` (1 vs 5 hops) and `isDirectYouTubeSubscriptionsEvent` (0 vs 3). |
| `maybeBlockYouTubeDirectNavigationBeforeDedupe()` + `isDirectYouTubeSubscriptionsEvent()` | Explicit Subscriptions/You bottom-nav click events now block immediately instead of waiting for the next root scan. Closes the tap-to-classify race and prevents a transient Shorts/player signal from stealing the Subscriptions tap. |
| Watch-ad position guard (`YT_WATCH_AD_DISMISS_LABELS`, `armYouTubeWatchAdPositionGuard`, `isYouTubeWatchAdPositionGuardActive`, `shouldProbeYouTubeWatchAdOverlayRoot`, `hasYouTubeWatchAdDismissEvent`, dismiss-aware `isYouTubeWatchAdSignal`) | Skip/close-ad controls sit in the same bottom band as the nav; geometry-only surface resolution is now suppressed for 2 s after ad signals, so watch-page ads cannot be misread as Subscriptions/You/Shorts taps. Armed from the Shorts fast path and the main classifier. |

## Removed (commit `7b21a85`)

Fork-side cleanup: the mini-player close chain could fall back to blind
coordinate taps/swipes (fixed-ratio tap chain, bounds-relative close taps,
swipe gestures, an x/y candidate tap loop). All coordinate-based strategies are
gone; node-based close/pause-axis/accessibility-action strategies remain.
Also removed the now-unused `"bounds"`/`"swipe"` strategy entries.

## Evaluated, not ported (on purpose)

| Upstream piece | Decision |
| --- | --- |
| `isYouTubePictureInPictureWindowVisible`, `maybeBlockYouTubePipOutsideYouTube`, `scheduleYouTubePipWindowProbe`, `enforceAsyncYouTubePipWindowEvidence` | The fork removed the user-facing PiP rule by design (plan §5). Porting them would re-introduce a surface the owner removed. Not applicable. |
| `scheduleYouTubeMiniPlayerRetryProbe` and the mini-player retry chain | Same: our user-facing mini-player rule was removed. The remaining cleanup helpers are shared with Shorts-in-PiP and were kept (now node-only). |
| `hasYouTubeShortsEventBurstSignal`, `isDirectYouTubeShortsCardEvent` | Adds a stateful event-burst heuristic with tree traversal. Our existing `isYouTubeShortsEntryEvent` / `isYouTubeHomeShortsShelfEvent` / `hasVisibleYouTubeReelPlayerContainer` passed every Shorts scenario on 21.36.45, so the extra traversal was not worth the ANR budget. Re-evaluate if a future YouTube build regresses. |
| `hasYouTubeNativeShortsLimitReachedSignal` | Our fork already has equivalent native-limit labels. |

## Already covered by our fork (no change)

- Surface classification/selection (`resolveYouTubeSurfaceFromEvent`, `detectYouTubeSelectedSurface`) with fork-aware package checks (`app.revanced`/`app.morphe`), which upstream lacks.
- Ad-overlay negative evidence (`hasYouTubeWatchAdOverlaySignal` with Ad Center / sponsored / visit-site labels — upstream's own label lists were already present in our fork).
- Shorts quiet-session safety net, native Shorts limit detection, post-acknowledge cleanup flags, YouTube Home redirect/verification, mini-player cleanup (node-based), PiP window-root fallback helpers.

## Test matrix and results

### Baseline (pre-port, `feature/upstream-w4-a2` build)

| Scenario | Result |
| --- | --- |
| Home allowed | PASS |
| Shorts tab blocks + OK lands on Home | PASS |
| Subscriptions tab blocks + OK lands on Home | PASS |
| You tab blocks + OK lands on Home | PASS |
| Shorts deep link blocks | PASS |
| Watch page allowed | PASS |
| Rapid Shorts→Subs tap blocks | PASS |
| Home Shorts shelf blocks | PASS |

### Experiment build (`ad814d2` + `7b21a85`)

| Scenario | Result | Notes |
| --- | --- | --- |
| Home allowed | PASS | |
| Shorts / Subscriptions / You tabs | PASS | Block + correct labels + OK lands on Home |
| Rapid pairs: Shorts→Subs, Subs→You, You→Shorts, Home→Shorts | PASS | Correct label each time (Shorts, You, Shorts, Shorts) |
| Cold start → `https://www.youtube.com/shorts` | PASS 3/3 | First stress run missed while the emulator was locked; unlocked and repeated 3/3 |
| `https://www.youtube.com/feed/subscriptions` | PASS | Blocks as Subscriptions |
| `vnd.youtube://…watch?v=…` intent | PASS | Allowed |
| Live stream (`jfKfPfyJRdk`) 15 s | PASS | Allowed, no false floating-player/PiP block |
| Ad-supported video (`kJQP7kiw5Fk`) 15 s | PASS | Allowed, no false block during pre-roll |
| Home Shorts shelf | N/A | The emulator Home feed exposes no Shorts shelf (region/account); earlier "shelf" passes were actually the bottom-nav Shorts. Covered by the Shorts tab and the Shorts deep link. |
| Back-to-back Shorts re-entry | PASS | Blocks again immediately |
| Rotation to landscape on Shorts | PASS | Blocks in landscape too |
| Screen off/on while blocked | PASS | Block re-applied |
| Work budget (W4.1) | PASS | `roots/scans` counters keep incrementing; no ANRs/crashes in logcat |

## Limitations

- ReVanced/Morphe YouTube was not available on the emulator (the phone's
  wireless adb dropped during the run). Package handling for
  `app.revanced.android.youtube` / `app.morphe.android.youtube` is already
  fork-aware and was observed working on the owner's phone
  (`[in_app_detect] pkg=app.morphe.android.youtube` in the device log).
- Notification taps were not testable on the emulator without a signed-in
  account; the `vnd.youtube://` intent path is covered.
- The Home Shorts shelf is not present in this emulator's feed (region/account),
  so "open a Short from the Home shelf" could not be exercised; upstream's
  Shorts card/burst signals remain the fallback if a real device shows it
  evading detection.
- Premium/live-ad variants of the watch page and the slow-OEM stress matrix
  (Samsung A12 / Bigme) still need a manual pass.
- YouTube builds other than 21.36.45 were not exercised; the burst heuristics
  left out are the main candidate if a future build regresses.

## Mini-player and PiP re-enabled (2026-09-19, `7bbc996`)

Owner asked to make the floating-player rules usable again if they work. The keys
(`KEY_BLOCK_YT_MINI_PLAYER`, `KEY_BLOCK_YT_PIP`) already existed but were
hardcoded off and hidden from the In-App Rules screen.

Implemented:
- `maybeBlockYouTubeFloatingPlayer` reads both rule keys; the rules are inert
  unless enabled for the profile.
- In-App Rules screen: new "Mini player" and "Picture-in-picture" rows for the
  three YouTube packages (en/de strings), backup/profile handling included.
- Floating-player blocks show the standard blocker popup with the surface
  label (ported upstream `showYouTubeFloatingPlayerBlock`) instead of a silent
  kill.

Detection fixes found on the emulator (YouTube 21.36.45):
- The floating check now runs on content events too: the in-app mini-player
  appears via `WINDOW_CONTENT_CHANGED` (BACK from the watch page) and the
  transition-only call site never saw it.
- Mini-player detection requires an explicit label/view-id identity and
  `isVisibleToUser`. Geometry alone also matched the watch page's player
  control bar (false "Mini player is blocked" popups while scrolling), and a
  hidden mini-player container exists in the watch-page tree.
- Label lists learned YouTube's "Minimized player" wording.
- A visible blocker is not re-shown every second while a lingering mini-player
  sits behind it (`forceShow` bypasses the surface cooldown).

Test evidence:
- Rule off: play a video from the Home feed, BACK -> mini-player visible, **no
  blocker**. Rule on, same flow -> "Mini player is blocked!" popup, mini-player
  closed, OK does not re-block. Run 3x: 2/3 blocked as mini-player; the third
  was intercepted by the You rule because BACK landed on the You tab.
- Watch page scrolling: no floating-player block.
- Shorts/Subs/You/deeplink/watch/rapid/shelf suite still 9/9.

PiP limitation: this YouTube build never enters system PiP on the emulator —
its activities report `supportsEnterPipOnTaskSwitch: false` and Home/gesture
leave goes straight to the launcher. The PiP rule path is implemented and gated
(window detection + kill + popup) but could not be exercised end-to-end here;
it needs a device where YouTube actually enters PiP (or ReVanced/Morphe).

## Recommendation

1. Cherry-pick `ad814d2` (upstream evidence port) and `7b21a85` (coordinate-tap
   removal) onto the main line after a slow-OEM manual pass. They are isolated,
   emulator-verified, and improve race handling without re-adding removed
   surfaces.
2. Do not port upstream's PiP/mini-player enforcement: this fork intentionally
   has no user-facing rules for those surfaces.
3. Keep the fork's Shorts heuristics; revisit upstream's event-burst signals only
   if a real device shows Shorts evading the block.
