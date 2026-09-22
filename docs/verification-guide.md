# Verification Guide — step by step

**Devices:** Pixel 10 Pro XL (main) · emulator-5554 (API 36) · Samsung A12 / Bigme A14 (only if available)
**Build:** debug `com.oliver.loqin.loqindev` · versionName 2.2.8
Every test below is written as: **preconditions → numbered taps → what you should see → how to reset.**

---

## 0. Before you start

### 0.1 Check protection is on
1. Open Loq In. On the home screen the profile card says **"Active now · Xd Xh"**.
2. If it says "Disabled", tap **Disable/Enable** on the profile card until it is active.
3. The toolbar top-right has three icons: camera, sliders, account. The **gear is the middle icon** (Settings).
4. With protection active, tapping the gear shows **"Open Settings?"** → tap **Open Settings**.

### 0.2 Set the delay to 1 minute (do this first)
1. Settings → **Controls** section → **Protection changes**.
2. Tap the **Protection change delay** row.
3. Three wheels appear: **days / hours / minutes** with a big total above (e.g. "Off").
4. Set `0 days`, `0 hours`, `1 min`. The total must read **"1 minute"**.
5. Tap **Save**. The row now shows **"1 minute"** and the summary "Weakening changes wait 1 minute…".
6. Important: while protection is active the delay can only stay the same or grow. If it is already 15 minutes or more, fully disable Loq In first (home → profile card → Disable), set the delay, then re-enable.

### 0.3 How to read the queue (source of truth)
1. Settings → Controls → **Protection changes** → the **Pending changes** card.
2. Each row shows what is waiting and when: **"Unblock Calendar"**, **"In 1 min."**, with a chevron.
3. Bottom pill messages ("…is queued…", "Pending change discarded.") are only feedback and disappear; the list is the truth.

### 0.4 Where each screen lives
| Screen | Path |
|---|---|
| Queue + delay | Settings → Controls → Protection changes |
| App rules (picker) | Home → scroll to the **Profile** card → tap the **pencil** (top-right of the card) → bottom sheet → **Apps** → dialog **Open Rules** |
| In-app rules | same sheet → **In-app rules** → Open Rules |
| Websites | same sheet → **Websites** → Open Rules |
| App limit editor | in the picker, **long-press** an app tile (or tap the clock badge on the tile) |
| Website usage/limits | Home → **More insights** (next to "4 Week Activity") → switch to the websites view → tap a site → **Edit limits** |
| Blocked apps list | Home → **Blocked apps** row (expand it) |
| Developer screen | Settings → Info → **App info** → **long-press the "2.2.8 (228)" row for ~2.5 seconds** |
| Copy support info | Settings → Info → **Copy support info** |

---

## 1. Queue mechanics

### 1.1 Parity: delay Off means "refused"
**Preconditions:** protection active, delay **Off**.
1. Open the picker (0.4).
2. Tap a blocked app (blue check tile) to untick it.
3. Tap **Save** at the bottom.
**Expect:** the locked dialog ("Turn off Loq In to edit blocked apps" style) and the app stays blocked. No queue entry.
**Reset:** none.

### 1.2 Queue an unblock, then watch it apply
**Preconditions:** protection active, delay **1 minute**.
1. Picker → untick **Calendar** → **Save**.
**Expect:** bottom pill *"This protection-reducing change is queued and will apply after the configured delay."*
2. Open Protection changes.
**Expect:** a row **"Unblock Calendar"** with a due time ("In 1 min."). Calendar is still blocked (tile stays checked after Save).
3. Wait ~70 seconds (keep the phone unlocked or leave it; both work).
4. Re-open Protection changes.
**Expect:** the row is gone. Open Calendar → it opens (no block).
**Reset:** re-block Calendar in the picker → Save (applies immediately because blocking is stricter).

### 1.3 Two apps, two timers (the reported bug)
**Preconditions:** delay 1 minute, Calendar and Chrome both blocked.
1. Picker → untick **Calendar** → Save → dismiss the pill.
2. Wait ~20 seconds.
3. Picker → untick **Chrome** → Save.
4. Open Protection changes.
**Expect:** **two** rows — "Unblock Calendar" (In ~40s) and "Unblock Chrome" (In 1 min.) — with **different** due times. The second action must not replace or reset the first.
**Reset:** Discard all, then re-block both.

### 1.4 Same app again: timer must not restart
1. Queue an unblock for Calendar with a **15-minute** delay (set the delay to 15 first).
2. Open Protection changes and note the due time ("In 14 min.").
3. Go back to the picker, untick Calendar again → Save.
4. Check the queue.
**Expect:** still one row, **same due time** as before (the original timer is kept).
**Reset:** Discard all; set the delay back to 1 minute.

### 1.5 Stricter changes apply immediately
1. Delay 1 minute, protection active.
2. Picker → tick a **new** app → Save.
**Expect:** applied instantly, no pill, no queue row.
**Reset:** untick it and discard the queued unblock.

### 1.6 Discard one / discard all
1. Queue two unblocks (1.3).
2. Protection changes → tap **"Unblock Calendar"**.
**Expect:** a **bottom sheet** (not a full-screen dialog) with the label, the due time and a **Discard** button (plus **Apply now** only when Loq In is fully off).
3. Tap **Discard**.
**Expect:** pill *"Pending change discarded."* and only "Unblock Chrome" remains.
4. Tap **Discard all** → confirm.
**Expect:** the list is empty.

### 1.7 Apply now (only when fully off)
1. Queue an unblock (delay 15 min).
2. Home → profile card → **Disable** Loq In completely (not "Take a break", not Emergency Unlock).
3. Open Protection changes.
**Expect:** **"Apply all now"** appears, and tapping a row shows **Apply now** in the sheet.
4. Tap **Apply all now** → confirm.
**Expect:** pill "Pending changes applied."; the queue empties and the app is unblocked immediately.
5. Re-enable protection.
**Expect:** with a temporary break or Emergency Unlock active, **Apply now is not offered**.

### 1.8 Reboot survival
1. Delay 15 minutes → queue an unblock.
2. Note the due time, then reboot the phone.
3. After boot, open Protection changes.
**Expect:** the row is still there with the same due time; it applies when due.
**Reset:** Discard all.

### 1.9 Doze deferral (emulator/phone with adb)
1. Delay 1 minute → queue an unblock.
2. `adb shell dumpsys deviceidle force-idle`
3. Wait 95 seconds, check the queue.
**Expect:** still pending (the alarm is deferred in Doze — by design, the due time is a floor).
4. `adb shell dumpsys deviceidle unforce`
5. Wait ~30 seconds.
**Expect:** the row disappears and the change applies.

### 1.10 Deleted profile
1. Queue a change for a **non-active profile** (switch profile, make the change, switch back) — or skip this one if you only use one profile.
2. Delete that profile (Manage profiles → swipe/menu → Delete).
3. Re-open the app.
**Expect:** the pending row is gone, no crash.

### 1.11 Stricter wins for limits
1. Delay 15 minutes → app limit: set 30 (applies immediately), then **raise to 60** → Save.
**Expect:** queued "App limits · <app>".
2. Before it fires, lower it to **20** → Save.
**Expect:** applies immediately; when the queued 60 fires it is **skipped** (value stays 20).

---

## 2. Per-surface behaviour (delay 1 minute, protection active)

### 2.1 Auto-block new apps
1. Picker → top checkbox **"Automatically block newly installed apps"**: turn it **on**.
**Expect:** applies immediately, no queue row (enabling is stricter).
2. Turn it **off**.
**Expect:** pill + queue row **"Auto-block new apps · <profile>"**; the checkbox stays on until the timer fires.
3. Turn it **on** again before the timer.
**Expect:** the pending disable is cancelled (queue row disappears).

### 2.2 In-app rule (and the pending preview)
1. Profile pencil → **In-app rules** → Open Rules.
2. Expand **YouTube** (tap the group row; it says "N rules active · Tap to expand").
3. Turn a rule **off** (e.g. Shorts).
**Expect:** pill + queue row **"In-app rule · Shorts"**; the switch shows the **off** position but faded (the target state, not the current one).

### 2.3 Whole-app offer when enabling a rule
1. Same screen → turn a rule **on** for an app that is **not** whole-app blocked (e.g. YouTube).
**Expect:** dialog **"Also block YouTube entirely?"** with the app icon, **Block entire app** / **Not now**.
2. Tap **Block entire app**.
**Expect:** blocking the whole app is a **stricter** change, so it applies **immediately** (no queue row, no pill): YouTube becomes whole-app blocked and the in-app rule stays active. If you instead do this while the app can only be queued (never the case for a block) the gate would queue it.
**Note:** the offer never appears if the app is already whole-app blocked or is a protected app (keyboard/launcher/dialer).

### 2.4 Website rule: disable / enable / delete
1. Websites → toggle a rule **off**.
**Expect:** pill + queue row **"Website rule · <rule>"**; the switch previews **off** faded; the row meta reads **"Pending — applies after the delay"**.
2. Toggle it **on** again before it fires.
**Expect:** the pending disable is cancelled.
3. Swipe a rule left → **Delete**.
**Expect:** pill + queue row "Website rule · <rule>"; the rule stays in the list until the timer fires.
4. Tap a **disabled** rule's switch to enable it.
**Expect:** applies immediately.

### 2.5 Website limits
1. Home → **More insights** → **Websites** tab → tap a site → **Edit limits**.
2. Raise the daily minutes (e.g. 10 → 30) → confirm.
**Expect:** pill + queue row **"Website limits · <domain>"**.
3. Lower the minutes (30 → 5).
**Expect:** applies immediately, no queue row.

### 2.6 App limits
1. Picker → **long-press** an app tile.
**Expect:** the limit editor opens even while protection is active (this was previously refused).
2. Turn **Screen time** on, set a value (e.g. 30) → **Save limits**.
**Expect:** if the app had **no** limit, this applies immediately (adding a limit is stricter).
3. Long-press again → raise it to **60** → Save.
**Expect:** pill + queue row **"App limits · <app>"**; the editor still shows 30 until the timer fires.
4. Lower it to **20** → Save.
**Expect:** applies immediately.
**Caveat:** when driving this by adb, `input text` into the minutes field is unreliable (it produced "603" once). Type manually.

### 2.7 Remove a blocked app from the home list
1. Home → **Blocked apps** row → expand it.
2. Tap an app → quick actions → **Remove** (destructive) → confirm.
**Expect:** pill + queue row **"Unblock <app>"** (plus "App limits/rules · <app>" if it had limits). The app stays in the list until the timer.

### 2.8 Clear all
1. Picker → type something in **Search apps** (or tap a category chip like "Media").
**Expect:** the bulk bar appears with **Select all** / **Clear all** (it only shows while filtering).
2. Tap **Clear all** → **Save**.
**Expect:** pill + one queue row **"Unblock A, B, C"** (up to 3 names, otherwise "Unblock N apps").

### 2.9 What must still be refused (never queued)
With protection active, these show the locked message and change nothing:
- Adding or editing a **website rule** (the "+" button and tapping a rule row).
- Switching **Block selected / Allow selected** for websites or apps.
- Switching the **control mode** (Schedule/NFC/QR/Barcode/Mixed).
- Switching or deleting a **profile**.

---

## 3. UI checks

### 3.1 The queue page
1. Open Protection changes.
**Expect:** section headers ("Change delay", "Pending changes") above cards; the delay row shows the value on the right ("1 minute") with a chevron; pending rows have the **target's icon** (Calendar/YouTube app icons, globe for websites) and a due time; an **info icon** in the toolbar opens the explanation overlay.

### 3.2 The wheels
1. Tap the delay row.
**Expect:** days/hours/minutes wheels, big live total, **no** hint text and **no** Off switch; `0/0/0` reads "Off".
2. While protection is active, pick less than the current value.
**Expect:** the hint "While protection is active, the delay can only stay the same or be increased." and Save does nothing.

### 3.3 Pills vs dialogs
1. Queue anything / discard anything.
**Expect:** always a **bottom pill**; reviewing an item is a **bottom sheet**, never a full-screen dialog.

### 3.4 German + light mode
Switch language in Appearance and the system to light; open the page and the two new dialogs (3.1, W6.3, W6.4).
**Expect:** all copy translated, no clipped text.

---

## 4. Other workstreams

### 4.1 Feature flags (W5.1)
1. Settings → Info → **App info** → long-press **"2.2.8 (228)"** for ~2.5 s.
**Expect:** the **Developer Tools** screen opens with a **Feature flags** section:
   - "Diagnostic timeline · On"
   - "Schedule save preview · On"
   - "Automation engine v2 · Off · scaffold"
   - "Continuous usage trigger · Off · scaffold"
2. Tap **Diagnostic timeline**.
**Expect:** the subtitle flips to "Off" and the value is stored (`loqin_feature_flags.xml`, key `diagnostic_timeline`). Tap again to restore.

### 4.2 Copy support info (W5.4)
1. Settings → Info → **Copy support info**.
**Expect:** the Support screen opens briefly, a pill confirms the copy and it returns. Paste anywhere to see the report — it contains **Protection state / Base enabled / Blocking expected**, **Settings schema**, every **Feature flag**, and the **Diagnostics timeline** entries.

### 4.3 Map picker (W6.2)
1. Schedules → add/edit → choose **Location schedule** → **Open map picker**.
**Expect with no Maps API key (current builds):** "Map picker is unavailable in this build. Using location search instead." and the text search stays usable.
**Expect with a key configured:** the map opens and loads; the "map unavailable" message must **not** appear while the fragment is still initialising (only on a real failure or the 15 s timeout).

### 4.4 Schedule save preview (W6.3)
1. Schedules → **Add first schedule** (or +) → choose **Time schedule** → leave defaults → **Create**.
**Expect:** row-style dialog **"Save this schedule?"** with **Profile / Action / When / Time** (and Note if set) and a **Save schedule** button. Cancel discards.
2. Toggle a schedule's switch, delete one, reorder — **no** preview for those.

### 4.5 Whole-app offer (W6.4)
See 2.3.

---

## 5. Emulator reproduction (for the automated checks)

```bash
P=com.oliver.loqin.loqindev

# Read the queue
adb -s emulator-5554 shell run-as $P cat /data/data/$P/shared_prefs/${P}_preferences.xml

# Inject a delay (force-stop first, then restore)
adb shell am force-stop $P
# edit the XML copy locally, then:
adb shell "run-as $P sh -c 'cat > /data/data/$P/shared_prefs/${P}_preferences.xml'" < /tmp/state.xml

# Armed alarm
adb shell dumpsys alarm | grep -A2 ProtectionChangeReceiver

# Doze
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce
```
UI automation used `uiautomator dump` + `input tap`; the prefs files are the pass/fail source of truth.
Known limits: the limit editor's text field and clipboard reads (Android blocks adb clipboard access).

---

## 6. Still unverified — do these four

1. **App-limit raise → queue** (2.6 step 3).
2. **Website-limit raise → queue** (2.5 step 2).
3. **Clear all → Save → queue** (2.8).
4. **Remove blocked app → queue** (2.7).
5. **OEM alarm delivery** (only with the Samsung/Bigme): delay 15 min → queue → lock the phone 20–30 min → it applies (possibly late); repeat with the app in the OEM's sleeping-apps list.

---

## 7. Regression spot checks

1. **Websites still block:** add `example.com/blocked/*`, open `example.com/blocked/test` in Chrome → blocked; `example.com/allowed` opens.
2. **YouTube rules still block:** with a rule on, trigger it → blocked; rule off → not blocked.
3. **Scanner + NFC screens:** QR shortcut → camera fills the screen; NFC write waiting screen → close button below the status bar.
4. **A7 warning:** with a Settings rule and Device Admin revoked → the picker shows the warning pill.
5. **Schedules still fire** and app blocking still blocks/allows.
