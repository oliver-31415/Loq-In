# Release QA fixes (2.3.0)

Fixes from the pre-release QA pass on the emulator (API 36). Each section matches one commit on
`fix/release-qa-polish`: what was wrong, what changed, and how it was verified. Bug numbers refer
to the QA report's bug table.

## 1. Read the saved theme mode at startup

**Problem.** Appearance saves the Light/Dark choice as `pref_theme_mode`, but `LoqInApp` read the legacy `pref_theme` key on startup, so the choice was lost after every cold start (bug 8).

**Fix.**
`LoqInApp` reads `pref_theme_mode` and falls back to the legacy `pref_theme` for older installs.

**Verified.** Chose Light, force-stopped the app and relaunched: Light is kept.

## 2. Lock profile switching while protection is active

**Problem.** With Loq In active in QR (or any single-channel) mode, "Allow managing profiles" (on by default) let the user switch to, or create and switch to, an empty profile, which turned protection off without the unlock method. Deleting a profile from the Home sheet was not locked either (bug 1).

**Fix.**
- `AutomationModeStore.isProfileSwitchingAllowedWhileEnabled` only allows switching while the manual button could disable Loq In anyway (Mixed mode with manual controls).
- Creating a profile from the Home sheet is still allowed, but it only becomes current when switching is unlocked; otherwise a pill explains how to switch. Duplicate names now show "already exists" instead of failing silently.
- Deleting a profile from the Home sheet is refused while editing is locked, matching Manage profiles.
- The Feature access summary for the toggle states the new condition (EN/DE).

**Verified.** QR mode, active: switching to Work is refused, creating "Test" keeps Default current and Facebook stays blocked, Delete profile shows the pill.

## 3. Refuse hiding apps from blocking while protection is active

**Problem.** Settings > Hidden apps (Blocking / Protection) saved immediately while Loq In was active, so hiding a blocked app unblocked it at once (bug 2).

**Fix.**
Hiding an app from blocking is a weakening change: it is checked with `ProtectionChangeGate.decision(WEAKER)` on tap (the tile is reverted with an explanation) and again on Save in case protection turned on while the screen was open. Unhiding and the Screen time / Stats tab are unaffected.

**Verified.** QR mode, active: tapping Facebook shows the pill and the tile stays visible; Facebook remains blocked.

## 4. End Emergency Unlock when Loq In is explicitly enabled

**Problem.** Tapping Enable during an Emergency Unlock cleared the temporary disable but left the emergency window running. Home showed "Active now" while the accessibility service skipped every rule for the rest of the window (bug 3).

**Fix.**
`SwitchModeStore.setEnabled(true)` cancels an active emergency and logs it. Covers the Home button, tile, NFC/QR/barcode and widgets, which all go through `setEnabled`.

**Verified.** Started an emergency (Facebook opens), tapped Enable: Facebook is blocked and the tile shows "Already used today".

## 5. Prevent QR-mode lockouts

**Problem.** QR mode could be selected and enabled with no QR code. Disable was then refused, creating a code is blocked while active, schedules do not run in QR mode, and Emergency Unlock is once a day, so the user could be locked out. Barcode mode already had a fallback; QR did not (bug 4).

**Fix.**
- `AutomationModeStore.hasQrDisableCode` treats QR as set up once a disable-capable code was copied or shared from the generator, a QR scan was ever recorded (covers codes printed by older versions), or a managed QR code exists.
- `shouldAllowManualDisableForMissingScanSetup` (was barcode-only) keeps manual Disable available while the only disable channel is a scan channel with no code.
- The Blocking method sheet asks to create a QR code (or add a barcode) right after picking a scan-only mode without one.

**Verified.** QR mode with no code: Disable works as the fallback; picking QR mode shows the prompt; after copying a `loqin://toggle` code, Disable is refused again.

## 6. Stop the Home screen from redrawing every second

**Problem.** Home called the full `updateSwitchState()` every second: new drawables, spans and texts each tick, each firing accessibility events that the in-process accessibility service answered on the main thread. Home never went idle and the app ANR'd reproducibly when returning to it. The hero artwork also redrew at 60 fps (bug 6).

**Fix.**
- `tickSwitchState()` computes a cheap signature of the time-dependent values and only runs the full refresh when it changes (once a minute normally, once a second during countdowns). Explicit refreshes are unchanged.
- `HeroArtDrawable` throttles its invalidations to ~20 fps; its 26 s drift looks identical.

**Verified.** Inactive Home: 0 frames in 10 s (was ~1/s). Active: 198 frames in 10 s (was 599). `uiautomator dump` now succeeds on Home. The ANR steps (Account > Privacy & data > Back x3 > relaunch > Settings > search) no longer freeze, twice.

## 7. Make the block screen say what was blocked

**Problem.** Website blocks showed the browser name with "You can't use this app"; app limits showed the generic blocked text; in-app blocks repeated their title as the reason ("Blocked by: Shorts is blocked!"); titles mixed styles and the button said OK (bugs 9, 10, 18).

**Fix.**
- Website blocks name the site: "youtube.com is blocked while Loq In is active."
- App-limit blocks use "Limit reached" with the specific limit (opens, daily time, per visit), derived from the fresh block-reason snapshot.
- In-app blocks record "In-app rule" as the reason.
- Titles drop exclamation marks; the button reads "Close" (EN/DE).

**Verified.** Checked Facebook (app list), Chrome (open limit), youtube.com (website rule) and YouTube Shorts (in-app rule) block screens.

