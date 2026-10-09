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
- `AutomationModeStore.hasQrDisableCode` treats QR as set up once a disable-capable code was copied or shared from the generator, or a managed QR code exists. (Past scans no longer count since section 18: older codes stop working.)
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

## 8. Ask what "Remove limits" should do with the app

**Problem.** A limited app is stored in the profile's block list. Removing all its limits left that entry, which means "always blocked", so the app became fully blocked without saying so (bug 13).

**Fix.**
When the app is in the block list, "Remove limits" offers **Stop limiting** (remove limits and take it off the list; a weakening change, refused while protection is active) or **Block completely** (previous behaviour). Apps outside the list keep the simple confirmation.

**Verified.** Limited Chrome > Remove limits > Stop limiting: the tile becomes "not selected" and Chrome opens.

## 9. Explain that schedules are read-only while active

**Problem.** Opening Schedules while active showed "Open Rules? You can still add protection", but adding a schedule was then refused with "Turn off Loq In to manage schedules". The empty state also said "Tap +" when the + was hidden (bugs 11, 28).

**Fix.**
`openRulesDestination` takes optional title/message/action; Schedules uses "Open Schedules? You can view schedules, but adding or changing them is locked until Loq In is off." The empty state no longer refers to the + button (EN/DE).

**Verified.** Active: the Schedules row shows the new dialog and opens the list.

## 10. Use the time-format setting in schedules

**Problem.** The schedule list and the save preview always printed 24-hour times (21:31-21:34) even with a 12-hour device and Appearance set to Auto (bug 15).

**Fix.**
Both use `TimeFormatPrefs.formatMinutesOfDay`. The preview gets a `TIME` line kind that keeps the canonical 24 h text for tests and formats with the user's setting when rendered. Ranges use an en dash.

**Verified.** Schedule row shows "9:31 PM–9:34 PM"; `SchedulePreviewFormatterTest` updated and passing.

## 11. Count usage access on the Home setup card

**Problem.** Home said "3 missing" while the Permissions re-check said 4: Home did not count App usage access, which Permissions marks Required (bug 12).

**Fix.**
The Home setup card also lists App usage access when it is missing.

**Verified.** Revoked usage access: Home shows "1 missing · App usage access".

## 12. Fix "1 opens" plurals in limits

**Problem.** The limits dialog and tiles showed "Up to 1 opens per day" and "1 opens/day" (bug 20).

**Fix.**
The opens sentence and split hint are plurals; tiles reuse the existing `daily_attempt_limit_value_format` plural; the unused `limit_tile_attempts_fmt` is removed (EN/DE).

**Verified.** Limits dialog with 1 open: "Up to 1 open per day".

## 13. Disable Save in name dialogs while the name is empty

**Problem.** Saving a new profile with an empty name closed the dialog with no feedback (bug 21).

**Fix.**
`showLoqInInputDialog` keeps its positive button disabled while the trimmed input is blank.

**Verified.** New profile: Save is disabled until a name is typed.

## 14. Clarify the Emergency Unlock confirmation

**Problem.** The confirmation was titled "Emergency unlock feature" with an OK button, and its click handler named the button index `dialog`, so the intended pill anchor never resolved (bug 23).

**Fix.**
Home and Settings use "Start emergency unlock?" / "Loq In turns off for 15 minutes. You can use this once a day." / "Start". The dead anchor code is removed (the screen anchor was always used); the unused `emergency_action_start_15` string is dropped (EN/DE).

**Verified.** Home > Emergency > PIN shows the new dialog; starting shows the confirmation pill.

## 15. Accessibility labels and 48dp limit buttons

**Problem.** TalkBack read the hero pencil as "Switch profile", every website-tile icon as "Website rules", settings/account icons repeated their row titles, the limit +/- buttons had no label, and sheet close buttons were a bare "✕". The clock limit button on tiles was 28dp (bugs 24, 25).

**Fix.**
- Labels: hero pencil "Edit profile"; decorative icons in Settings, Account and website tiles use `@null`; limit +/- buttons get descriptive labels (EN/DE); sheet close buttons are labelled "Close" with the glyph hidden from TalkBack.
- The tile limit button is 48dp with 14dp padding and a -10dp overlay offset, so the glyph stays where it was.

**Verified.** UI dump shows the new labels; tiles look unchanged with a larger touch area.

## 16. Correct website path suggestions

**Problem.** The youtube.com suggestion offered `/reels/*`, which YouTube does not have; single Instagram/Facebook reels open at `/reel/<id>`, which `/reels/*` missed (bug 27).

**Fix.**
YouTube offers `/shorts/*` only; Instagram adds `/reel/*`; Facebook adds `/reel/*`.

**Verified.** Suggestion catalog reviewed; paths dialog shows the new options.

## 17. Clearer website rule dialog

**Problem.** Typing an invalid domain said "Please enter a domain."; the dialog button said OK for both adding and editing (bug 28).

**Fix.**
Invalid input says "Enter a valid domain, like example.com."; the button reads "Add" when adding and "Save" when editing (EN/DE).

**Verified.** Entered "not a url": the new error appears.

## 18. Require this install's secret on QR codes and loqin:// links

**Problem.** QR mode accepted any `loqin://` code, and the exported `ExternalQrActionActivity` accepted any `loqin://` link from a website or app (after one confirmation). Anyone could make a working "disable" code with a web QR generator, which removed the friction QR mode is meant to add (bug 7).

**Fix.**
- `LoqInCodeSecret` keeps a random per-install secret (`qr_code_secret` in `loqin_prefs`, backed up with Keys and codes). The QR generator appends it as `k=<secret>`.
- `NfcEntryActivity` rejects unmanaged QR/barcode dispatches whose URI does not carry the secret ("Not a code from this Loq In. Create a new one in My keys and codes."). Codes linked in My keys and codes stay trusted; NFC tags are unchanged.
- `ExternalQrActionActivity` rejects unsigned links before showing its confirmation.
- `hasQrDisableCode` no longer counts past scans, and its flag moved to `qr_disable_code_ready` (Keys backup category), so the lockout fallback stays available until a new signed code is kept.

**Upgrade note (strict, by decision).** QR codes created by earlier versions stop working and must be created again. Users in QR mode keep manual Disable as a fallback until they copy or share a new code. Mention this in the 2.3.0 release notes.

**Verified.** Unsigned `loqin://toggle` link: rejected with the toast, no dialog. Wrong secret: rejected. Signed link from the generator: confirmation shown, Loq In toggled on. `LoqInCodeSecretTest` covers the comparison.

## 19. Theme-aware status bar icons

**Problem.** Home and eight other screens forced light status/navigation bar icons (a leftover from the dark-only design), so in the Light theme the clock, battery and notification icons were white on a light background.

**Fix.** `EdgeToEdgeUtils.applyThemedSystemBars` picks dark icons on light surfaces and light icons in night mode; every screen that hard-coded light icons now calls it. The blocker keeps its own background-based logic.

**Verified.** Light theme: Home and Keys & codes show dark status bar icons.

## 20. Locked Home controls point to the way off

**Problem.** In QR or barcode mode the Disable button and the Take a break tile looked available but only answered with a toast ("Manual buttons can only enable Loq In right now…"); the tile still said "Choose duration" (bug 22).

**Fix.** When Disable is refused and a scan channel is the way off, Home says "Scan your code to turn Loq In off." and opens the scanner directly. The Take a break tile reads "Locked in <mode>" while the manual channel can't pause protection (EN/DE).

**Verified.** QR mode, active, code kept: the tile shows "Locked in QR mode"; Disable opens the scanner (after the camera permission prompt) and Loq In stays on.

## 21. Camera permission denial offers Android settings

**Problem.** Denying camera access showed a toast cut off at two lines that said "Enable it in Permissions" — but Loq In's Permissions page has no camera row and is locked while protection is active (bug 19).

**Fix.** All three scanners call `ScanFeedback.cameraPermissionDenied`, a dialog ("Camera access needed") with **Open settings**, which opens Android's app details page; the scanner closes when the dialog does. Messages updated (EN/DE).

**Verified.** QR mode, active: Disable > scanner > Don't allow shows the dialog; Open settings opens the app page; Back returns to Home; no crash.

## 22. Home counts limited apps separately

**Problem.** The Home profile card's "Apps" number counted every selected app, so adding a limit to an app raised the "blocked" count even though the app was only limited (bug 13).

**Fix.** In Block selected mode the number shows fully blocked apps only; when some selected apps have limits the label reads "Apps · N limited" (EN/DE). Allow selected mode is unchanged.

**Verified (Haiku agent).** Facebook blocked: "1 / Apps". After adding a 5-opens limit to Chrome: "1 / Apps · 1 limited". After Remove limits > Stop limiting: back to "1 / Apps". No crashes.

## 23. Limits dialog no longer jumps while editing

**Problem.** The limits dialog was vertically centred: turning on a limit expanded its section, the dialog re-centred, and every control (including the switch just tapped) moved under the finger. The summary line above also changed between one and two lines. Even the test agent's taps missed because of it.

**Fix.** `pinLoqInDialogToTop` pins the app and website limit editors to the top of the screen so content only grows downward (no offset: the window manager drops one once the content needs the full height), and the summary sentence reserves two lines.

**Verified (Haiku agent).** Turning on Screen time then App opens: the title and both switches keep their exact positions; the dialog stays below the status bar and Save/Cancel remain visible; no crashes.

## 24. Translate the German Home screen and fit long labels

**Problem.** 150 strings in `values-de/strings_home.xml` (Home, profile sheet, block reasons, quick actions, tiles) were still English, so the most visible screen was half-English for German users; the Switch profile pill read "Manage". With German text, the activity header, the 28-day blocks label and both bottom tiles were cut off (bug 26 / localisation).

**Fix.** Translated the Home strings using the terms already used elsewhere in the German UI (App-Regeln, Notfall-Entsperrung, Ausgewählte blockieren…). The activity title and the two bottom tiles' titles/subtitles autosize down to fit (helps English at large font sizes too); the German header is "Aktivität" and the blocks label "Sperren · 28 T.".

**Verified (Haiku agent).** German: no English left on Home, the profile sheet or the switch-profile sheet; header, stats row and both tiles fully visible. English Home unchanged and unclipped. No crashes.

## 25. Bottom sheets clear the gesture bar

**Problem.** Bottom sheets draw edge-to-edge, so their last row sat under the gesture/navigation bar ("Delete profile" on the profile sheet); the profile sheet also opened at half height, hiding the row until scrolled (bug 26).

**Fix.** `BottomSheetDialog.padForNavigationBar()` adds the navigation bar inset to the sheet content's bottom padding once attached; all eight bottom sheets call it before `show()`. The profile and Blocking method sheets open fully expanded.

**Verified (Haiku agent).** Profile, switch-profile, Blocking method and Take a break sheets: the last row sits ~150 px above the home indicator at open, tops stay below the status bar, no crashes.

