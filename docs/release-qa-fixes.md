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

