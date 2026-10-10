# Feature research and roadmap (October 2026)

Four research passes (run by Haiku agents on 2026-10-09) looked at soft-friction apps, strict modes and unlock methods, insights and habit features, and what users complain about in Android blockers. Sources were opened pages (vendor help centres, App Store listings, GitHub issue trackers of open-source blockers, reviews); Reddit and Play Store reviews were not reachable, so user sentiment comes from issue trackers and articles. Vendor efficacy figures (e.g. one sec's) are unverified; the one independent study found is the Danish Competition and Consumer Authority's 2025 field experiment (a 6-second wait cut teenagers' daily use by about a third).

## Shortlist

Effort: S = days, M = 1–2 weeks. "Have" = what Loq In already has to build on.

| # | Feature | Seen in | Effort | Have |
| --- | --- | --- | --- | --- |
| 1 | **Attempt counter on the block screen** ("Blocked 3 times today") | one sec | S | Per-app block counts — *implemented on `fix/release-qa-polish`* |
| 2 | **Pause mode**: a countdown/breathing screen before a flagged app opens, with Continue/Leave; optional wait that grows with each open today | one sec, ScreenZen, Opal Waiting Room, Android 17 Pause Point | S–M | Block overlay, opens-per-day counter |
| 3 | **Pause outcomes in insights** ("you backed out 12 of 20 times") | one sec, KFST study | S | Insights, block events |
| 4 | **Week-over-week comparison** on Home/insights | Opal, Digital Wellbeing | S | Daily totals |
| 5 | **Streak of days within limits** (with a grace day; no red "broken" state) | ScreenZen, ScreenStreak | S | Per-day limit hits |
| 6 | **Weekly summary notification** (local; after 7 days of data) | Opal Focus Report, Digital Wellbeing "Weekly wrap" | S | Daily totals |
| 7 | **Escalating emergency unlock** (each use waits longer / asks for more typing; weekly cap) | AppBlock, Opal, Freedom | S | Emergency unlock |
| 8 | **Random-text unlock** as a control method | Cold Turkey, Stay Focused | S | Control-mode framework |
| 9 | **60-second undo window** when a strict session starts | Freedom Locked Mode | S | — |
| 10 | **Intention prompt** for breaks ("Why am I opening this?"), logged in insights | Opal (requested), Pause Point, Minded | S–M | Take a break |
| 11 | **Daily total budget** with a progress ring | ScreenZen | S | Daily totals |
| 12 | **CSV export** of usage history | StayFree, YourHour | S | Local DB, SAF |
| 13 | **Home-screen stats widget** | ScreenZen, Device Watch | M | Widgets exist |
| 14 | **Offline partner approval** (challenge/response with a pre-shared secret) | AppBlock (server-based) | M | QR secret |
| 15 | **Charger-only unlock** (with fallback) | AppBlock | S | — |

Avoid: punitive streak "failure" states, an opaque single "focus score", peer comparison (needs a server), and per-app grayscale (needs `WRITE_SECURE_SETTINGS`).

## Reliability and bypass themes (from user complaints)

Ranked by how often they came up in issue trackers of other open-source blockers (Nudge, Curbox, Reef, DetoxDroid, digipaws, Shorts-Blocker) and vendor help pages:

1. **Blocker silently stops** after an app update, reboot or OEM kill, while the UI still shows it as on. → Verify the service reconnects after `MY_PACKAGE_REPLACED`/boot; show the real connected state; notify when protection silently stops.
2. **Protection removed** via the accessibility toggle, device admin, force-stop, clear data, uninstall, Safe Mode. → Test each against the protection gate; document Safe Mode as a known limit.
3. **System-surface escapes**: PiP, floating windows, split screen. → Test floating/split-screen; PiP Shorts is covered by fix #27 (needs a device check).
4. **Browser gaps**: unsupported browsers, incognito, in-app browsers, custom tabs/TWAs, tab left on the blocked page, back-button loops.
5. **Lockouts**: forgotten PIN, emergency exits that don't work, stuck overlays, banking apps that refuse to run while an accessibility service is on.
6. **False positives**: fullscreen video closing, keyboard opening re-triggering warnings.
7. **Limit accuracy** at hour boundaries and after reboot.

The full 28-step bypass checklist (reboot, update in place, force-stop, clear data, accessibility toggle, device admin, Safe Mode, PiP, floating window, split screen, recents, eight browsers, in-app browsers, TWA/custom tabs, URL variants, back behaviour, locale, clock changes, hour-boundary limits, emergency unlock, backup restore, QS tile, NFC cloning, QR on a second screen, dual apps/work profile, deep links, banking apps, overlay dismissal) should be run on a real device before each release.

## Notes

- Google Play's accessibility policy requires a prominent disclosure, consent and a declaration form for non-accessibility apps; GitHub builds aren't bound by it, but Android 13+ shows "restricted settings" for sideloaded apps requesting accessibility — document the "Allow restricted settings" step for GitHub installs.
- Android 17's Pause Point ships a system 10-second pause; Loq In's version should stand out by tying the pause to its per-app limits, schedules and profiles.
