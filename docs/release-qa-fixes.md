# Release QA fixes (2.3.0)

Fixes from the pre-release QA pass on the emulator (API 36). Each section matches one commit on
`fix/release-qa-polish`: what was wrong, what changed, and how it was verified. Bug numbers refer
to the QA report's bug table.

## 1. Read the saved theme mode at startup

**Problem.** Appearance saves the Light/Dark choice as `pref_theme_mode`, but `LoqInApp` read the legacy `pref_theme` key on startup, so the choice was lost after every cold start (bug 8).

**Fix.**
`LoqInApp` reads `pref_theme_mode` and falls back to the legacy `pref_theme` for older installs.

**Verified.** Chose Light, force-stopped the app and relaunched: Light is kept.

