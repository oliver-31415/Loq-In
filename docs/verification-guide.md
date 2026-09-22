# Verification Guide — do this, in order

This guide tells you exactly which buttons to press. Anything in **bold** is text you will see on screen.
Do §0 first. Then run the tests top to bottom; each one tells you its preconditions.

Devices: Pixel 10 Pro XL (main), emulator-5554. Build: `com.oliver.loqin.loqindev` (2.2.8).

---

## 0. One-time setup

### 0.1 Check protection is active
1. Unlock the phone and open **Loq In**.
2. On the home screen, scroll down until you see the big blue card with your profile name (e.g. **Default**).
3. At the bottom of that card there is a pill that says either **Active now · Xd Xh** or **Disabled**.
4. If it says **Disabled**, tap the **Enable** button on the card. Wait until it reads **Active now**.
5. **Do not continue until it says Active now** — most tests only work while protection is active.

### 0.2 Open the Settings screen
1. On the home screen, look at the top-right corner: three icons (camera, sliders, a person).
2. Tap the **middle icon (sliders)**.
3. A dialog appears: **Open Settings?** with the text "Loq In is active. Settings can still be viewed…".
4. Tap **Open Settings** (bottom-right of the dialog).

### 0.3 Set the delay to 1 minute
1. On Settings, find the **Controls** section (first one at the top). It has two rows: **Feature access** and **Protection changes**.
2. Tap **Protection changes**.
3. The **Protection changes** screen opens. You see:
   - **Change delay** (header) → a card with **Protection change delay** and a value on the right (e.g. **Off** or **15 minutes**).
   - **Pending changes** (header) → a card that says **No pending protection changes** when empty.
   - An **info icon (i)** in the top-right toolbar.
4. Tap the **Protection change delay** row.
5. A dialog opens with a big number at the top and three scrollable wheels: **days**, **hours**, **min**.
6. Flick the wheels until they read `0`, `0`, `1`. The big number at the top must read **1 minute**.
7. Tap **Save**.
8. The row now shows **1 minute** on the right.
9. **If the value is already bigger than 1 minute** (e.g. 15 minutes), you must first turn protection off: go Home → profile card → **Disable**, then set the delay, then **Enable** again.

### 0.4 Two shortcuts you will need often
- **Queue page** = Settings → Controls → **Protection changes** (as in 0.3).
- **Picker** (App rules) = Home → scroll to the blue profile card → tap the **pencil icon** (top-right corner of the card) → a sheet slides up from the bottom with rows **Apps / Websites / In-app rules / Schedules / Breaks / Delete profile** → tap **Apps** → a dialog **Open Rules?** appears → tap **Open Rules**.

### 0.5 Turn protection off / on (some tests need this)
1. **Off:** Home → blue profile card → tap **Disable**. The pill changes to **Disabled**.
2. **On:** Home → blue profile card → tap **Enable**. Wait for **Active now**.

### 0.6 Blocked vs limited — read this before the limit tests

These are two different states, and the second one always wins:

| State | Meaning | Example |
|---|---|---|
| **Blocked** | On the profile's block list and **no limit set** | Opening Calendar always shows the block screen |
| **Limited** | **Any** limit is set (screen time, opens/day, max per visit) | Calendar opens, but once the daily minutes are used up it blocks for the day |
| **Unrestricted** | Not on the block list and no limit | Calendar always opens |

Key consequences (this is what the gate now models):

- **Adding a limit to a blocked app makes it limited** — it changes from "always blocked" to "allowed until the limit". That is a **weakening**, so with protection active it **queues** instead of applying.
- **Removing the last limit from a blocked app** returns it to a hard block — a **strengthening**, applied immediately.
- **Adding a limit to an unrestricted app** restricts it — a strengthening, applied immediately.
- **Removing the last limit from an unrestricted app** frees it — a weakening, queues.
- Raising/lowering a limit on an already-limited app: lowering is stricter, raising is weaker (component by component, e.g. lowering screen time while raising opens).
- In **allow-selected** mode the same logic applies inverted: the app's own entry is the exception, so adding a limit to an allowed app restricts it, and adding a limit to a blocked app makes it limited instead.

The limit editor now tells you which case you are in at the top, e.g. **"Fully blocked. Adding a limit allows it until the limit is reached."** or **"Limited. Removing all limits blocks the app completely."**

### 0.65 Auto-save (the picker has no Save button)

The app list saves as you go: every tile toggle is sent through the protection gate immediately
(debounced ~250 ms so Select all / Clear all count as one change). Stricter changes apply at once,
weakening ones queue and then re-baseline the list to the store, and a refusal shows the locked
message and reverts the tile.

- Toggle a tile → leave the screen → come back: the change is still there.
- A **queued** change keeps your intent on screen: the tile stays unchecked and shows the
  **hourglass** badge ("Queued — will apply after the delay") plus the lighter shade; the app is
  still blocked until the timer fires, and the entry appears in Protection changes.
- **Undo:** re-check the tile before the timer and the queued change is cancelled.
- With the delay Off, a weakening toggle is refused (locked message) and the tile reverts.
- In allow-selected mode, Clear all asks for confirmation before it would block every app.

### 0.7 Unselecting an app that has limits

An app with limits stays limited even after you remove it from the block list, so the picker asks what to do:

1. Picker → tap an app that is **checked** and has a **timer badge** (a limit).
2. **You should see:** **"Remove limits for <app>?"** with the message, a **Remember my choice** checkbox, and **Keep limits** / **Remove limits**.
3. **Keep limits** → the app is unselected but its limits still apply (it stays limited).
4. **Remove limits** → the app's limits are cleared and the tile updates immediately (the limit badge disappears). The unselect itself is a weakening change, so it queues (or is refused when the delay is Off).
5. Tick **Remember my choice** before choosing, and the question stops appearing; your answer becomes the default.
6. Change the default later in **Settings → Controls → Feature access → "Unselecting an app with limits"** (Ask every time / Remove limits / Keep limits).
7. **Allow-selected mode is inverted:** unselecting removes the app's exemption. Removing its limits then makes it hard-blocked (a strengthening, applied immediately), while keeping them leaves it limited.

---

## 1. Queue mechanics

### 1.1 Delay = Off means "refused" (the old behaviour)
**Preconditions:** protection active, delay **Off** (0.3 with `0/0/0`).
1. Open the **Picker** (0.4).
2. Find a tile with a **blue border and a checkmark** (that means blocked). Example: **Calendar**.
3. Tap that tile once. The checkmark disappears (this is only in the list so far).
4. Tap the big blue **Save** button at the very bottom.
5. **You should see:** a dialog telling you Loq In must be turned off to edit blocked apps. The tile returns to blocked.
6. **You should NOT see:** any row in Protection changes.
**Reset:** nothing to do.

### 1.2 Queue an unblock and watch it apply
**Preconditions:** protection active, delay **1 minute**.
1. Picker (0.4) → tap **Calendar** to remove its checkmark → tap **Save**.
2. **You should see:** a dark pill at the bottom: **"This protection-reducing change is queued and will apply after the configured delay."**
3. Tap anywhere to dismiss the pill. Notice Calendar is **checked again** in the list — that is correct: it stays blocked until the timer runs out.
4. Go to the **Queue page** (0.4).
5. **You should see:** a row **Unblock Calendar** with a subtitle like **In 1 min.** and a **>** chevron.
6. Leave the phone alone for about **70 seconds** (screen on or off both work).
7. Re-open the Queue page.
8. **You should see:** the row is gone.
9. Open Calendar from the home screen / app drawer.
10. **You should see:** it opens normally (no "blocked" screen).
**Reset:** Picker → tap Calendar (check it) → Save. Blocking applies immediately, no pill.

### 1.3 Two different apps = two separate timers (this was the bug)
**Preconditions:** delay 1 minute, both **Calendar** and **Chrome** blocked.
1. Picker → untick **Calendar** → **Save** → dismiss the pill.
2. Wait about 20 seconds.
3. Picker → untick **Chrome** → **Save** → dismiss the pill.
4. Open the **Queue page**.
5. **You should see TWO rows:**
   - **Unblock Calendar** — **In ~40 sec.** (or similar)
   - **Unblock Chrome** — **In 1 min.**
6. The two rows must have **different** times. The second action must not have replaced the first.
**Reset:** Queue page → **Discard all** → confirm. Then re-block both in the picker.

### 1.4 Queueing the same app again must not extend the timer
**Preconditions:** delay **15 minutes** (0.3 with `0/0/15`), Calendar blocked.
1. Picker → untick **Calendar** → Save → dismiss.
2. Queue page → note the time on the row, e.g. **In 14 min.**
3. Picker → untick **Calendar** again → Save → dismiss.
4. Queue page.
5. **You should see:** still **one** row, with the **same** time as step 2 (e.g. still "In 14 min.", not "In 15 min.").
**Reset:** Discard all, set delay back to 1 minute.

### 1.5 Stricter changes apply immediately
**Preconditions:** delay 1 minute, protection active.
1. Picker → tap an app that is **not** checked (e.g. **Camera**) → tap **Save**.
2. **You should see:** NO pill and NO queue row. Camera is now blocked.
**Reset:** untick Camera → Save → the unblock queues; discard it in the Queue page.

### 1.6 Discard one item / discard everything
**Preconditions:** two queued unblocks from 1.3.
1. Queue page → tap the row **Unblock Calendar**.
2. **You should see:** a **sheet sliding up from the bottom** (not a big centred dialog) with:
   - the title **Unblock Calendar**
   - the due time **In X min.**
   - a full-width **Discard** button
   - (only if protection is fully off) an **Apply now** button
3. Tap **Discard**.
4. **You should see:** the sheet closes, a pill **"Pending change discarded."**, and only **Unblock Chrome** remains.
5. Tap **Discard all** at the bottom of the card → confirm.
6. **You should see:** **No pending protection changes**.

### 1.7 Apply a queued change early (only when protection is fully off)
1. Queue an unblock with the delay set to **15 minutes**.
2. Go Home → profile card → tap **Disable**. The pill must say **Disabled** (this is different from "Take a break" and from "Emergency unlock").
3. Open the Queue page.
4. **You should see:** a **Apply all now** button at the bottom of the card.
5. Tap the pending row → the sheet now also shows **Apply now**.
6. Tap **Apply all now** → confirm.
7. **You should see:** a pill **"Pending changes applied."**, the queue is empty, and the app opens immediately.
8. Re-enable protection (profile card → **Enable**).
9. Repeat steps 1–3 but instead of Disable, use **Take a break** (or Emergency unlock).
10. **You should see:** **Apply now is not offered** (the card has only Discard all).

### 1.8 Surviving a reboot
1. Set the delay to **15 minutes** → queue an unblock (1.2 step 1).
2. Note the time on the queue row.
3. Reboot the phone (power menu → Restart).
4. After it boots, open the Queue page.
5. **You should see:** the same row with the same time; it applies when due.
**Reset:** Discard all.

### 1.9 Doze (phone/emulator with adb)
1. Delay 1 minute → queue an unblock.
2. Run on your computer: `adb shell dumpsys deviceidle force-idle`
3. Wait **95 seconds**, then open the Queue page.
4. **You should see:** the row is **still there** — Android defers alarms in Doze. This is expected.
5. Run: `adb shell dumpsys deviceidle unforce`
6. Wait ~30 seconds.
7. **You should see:** the row is gone and the app is unblocked.

### 1.10 Deleted profile
**Only if you have a second profile.**
1. Switch to profile **Second** (Home → **Switch profile**).
2. Queue an unblock in that profile (delay 1 minute).
3. Switch back to **Default**.
4. Manage profiles (Home → **Switch profile** → manage/edit) → delete **Second**.
5. Open the Queue page / restart the app.
6. **You should see:** no row for the deleted profile and no crash.

### 1.11 Stricter wins (limits)
1. Delay **15 minutes**.
2. Picker → long-press **Calendar** → in the editor turn **Screen time** on → type **30** → **Save limits**.
3. Long-press Calendar again → change 30 to **60** → **Save limits**.
4. **You should see:** a pill and a queue row **App limits · Calendar**.
5. Before the timer fires, open the editor again → change it to **20** → Save.
6. Wait for the timer to pass.
7. **You should see:** the limit is **20** — the queued 60 was skipped because you made a stricter edit in the meantime.

---

## 2. Each screen that can queue something

### 2.1 The auto-block checkbox
**Preconditions:** delay 1 minute, protection active.
1. Picker → at the top there is a checkbox **Automatically block newly installed apps** (currently off).
2. Tap it to turn it **on**.
3. **You should see:** it stays on, no pill, no queue row (turning protection on is stricter).
4. Tap it again to turn it **off**.
5. **You should see:** a pill, and in the Queue page a row **Auto-block new apps · Default**. The checkbox flips back on by itself until the timer fires.
6. Before the timer fires, turn the checkbox **on** again.
7. **You should see:** the pending row disappears (your stricter change cancelled it).

### 2.2 In-app rules
**Preconditions:** delay 1 minute, protection active.
1. Home → profile pencil → sheet → **In-app rules** → **Open Rules**.
2. You see groups: **YouTube**, **Instagram**, **X/Twitter**, **Snapchat**, **Facebook** (some may say **Not installed**).
3. Tap **YouTube**. It expands and shows rows with switches: **Shorts**, **Subscriptions**, **You**, **Mini player**, **Picture-in-picture**.
4. Tap the switch for **Shorts** to turn it **off**.
5. **You should see:** a pill and a queue row **In-app rule · Shorts**. The switch shows the **off** position but **faded** (lighter than a normal off switch).
**Reset:** Discard all.

### 2.3 "Also block the whole app?" when you turn a rule on
**Preconditions:** protection active, YouTube is **not** whole-app blocked.
1. In-app rules → YouTube → turn **Shorts** (or any rule) **on**.
2. **You should see:** a dialog **"Also block YouTube entirely?"** with the app icon, the text about the in-app rule staying active, and buttons **Not now** / **Block entire app**.
3. Tap **Block entire app**.
4. **You should see:** YouTube is now whole-app blocked (open the Picker and YouTube has a checkmark). No queue row — blocking is stricter and applies instantly.
5. Try the same for an app that is **already** whole-app blocked.
6. **You should see:** no dialog at all.

### 2.4 Website rules
**Preconditions:** delay 1 minute, protection active, at least one rule exists (e.g. `example.com/blocked/*`).
1. Home → profile pencil → sheet → **Websites** → **Open Rules**.
2. You see the rule list with a globe icon, the rule text and a **Blocked** subtitle; each row has a **clock button** and a **switch**.
3. Tap the **switch** of a rule to turn it **off**.
4. **You should see:** a pill; the row subtitle changes to **Pending — applies after the delay**; the switch shows the off position but faded; a queue row **Website rule · example.com/blocked/*** appears.
5. Turn the switch **on** again before the timer.
6. **You should see:** the pending row disappears.
7. **Swipe a rule from right to left** → a **Delete** dialog appears → tap **Delete**.
8. **You should see:** a pill and a queue row **Website rule · <the rule>**; the rule stays in the list until the timer fires.
9. Tap a rule's switch to turn a **disabled** rule back on.
10. **You should see:** it applies immediately (no queue row).
**Note:** tapping the **clock button** opens the *rule editor* (domain, mode, daily limit) which is still locked while protection is active — that is intended. The rule's limit editor lives in the usage screen (2.5).

### 2.5 Website limits
**Preconditions:** delay 1 minute, protection active, a website with usage.
1. Home → **More insights** (the button next to "4 Week Activity").
2. Switch to the **websites** view (tabs at the top of that screen).
3. Tap a website row to open its detail page. You see **Today** usage and buttons including **Edit limits**.
4. Tap **Edit limits**.
5. Raise the daily minutes (e.g. **10 → 30**) → confirm/save.
6. **You should see:** a pill and a queue row **Website limits · <domain>**.
7. Lower it again (e.g. **30 → 5**).
8. **You should see:** it applies immediately, no queue row.

### 2.6 App limits
**Preconditions:** delay 1 minute, protection active.
1. Picker → **press and hold** an app tile for about a second.
2. **You should see:** the limit editor opens (this used to be refused). It shows the app name, **Profile: Default**, and rows: **Screen time / Total minutes per day**, **App opens / How often the app can be opened per day**, **Max per visit / Longest single visit**, plus **Cancel** / **Save limits**.
3. Turn **Screen time** on, type **30** in the minutes field, tap **Save limits**.
4. **You should see:** no pill and no queue row (the app had no limit, so adding one is stricter).
5. Long-press the tile again → change **30** to **60** → **Save limits**.
6. **You should see:** a pill and a queue row **App limits · <app>**. Re-open the editor: it still shows **30**.
7. Change it to **20** → Save.
8. **You should see:** it applies immediately.
**Tip:** type the numbers by hand; automation tools are unreliable in that field.

### 2.7 Removing a blocked app from the home list
**Preconditions:** delay 1 minute, protection active, at least one blocked app.
1. Home → scroll to the **Blocked apps** row (below the profile card area) → tap it to expand the list.
2. Tap a blocked app row.
3. **You should see:** a quick-actions sheet/dialog with options including **Remove** (in red).
4. Tap **Remove** → confirm.
5. **You should see:** a pill and a queue row **Unblock <app>** (plus **App limits/rules · <app>** if that app had limits). The app stays in the list until the timer.

### 2.8 Clear all (bulk)
**Preconditions:** delay 1 minute, protection active, several apps blocked.
1. Picker → tap into the **Search apps** field and type a letter (e.g. `c`) or tap a category chip such as **Media**.
2. **You should see:** a bulk bar appears with **Select all** and **Clear all** (it only appears while you are filtering).
3. Tap **Clear all**.
4. **You should see:** the visible tiles lose their checkmarks.
5. Tap **Save**.
6. **You should see:** a pill and **one** queue row **Unblock A, B, C** (up to three names; more shows "Unblock N apps").
**Reset:** Discard all, then re-block what you need.

### 2.9 Things that must still be refused (nothing queued)
With protection active, do each of these and check that you get the "turn Loq In off" message and **no** queue row:
1. Websites screen → tap **+** and try to add a rule.
2. Websites screen → tap a rule row (opens the rule editor).
3. Picker → tap **Allow selected** in the top toggle.
4. Settings → Controls → **Feature access** → try to change the **control mode** (Schedule/NFC/QR/Barcode/Mixed).
5. Home → **Switch profile** → try to switch to another profile; or try to delete one.

---

## 3. How the screens should look

### 3.05 Picker badges

- A **queued** change shows an **hourglass** badge on the tile (plus the lighter shade).
- Blocked **and** limited apps show the plain **check**; a limit is stated by the subtitle ("60 min/day").
  There is no stopwatch badge — it read as a delay timer.
- The small dot marks apps that have a limit (shown together with the "N min/day" subtitle).

### 3.1 Queue page
1. Settings → Controls → **Protection changes**.
2. **You should see:**
   - Header **Change delay**, card with **Protection change delay**, a value on the right (**Off** / **1 minute** / **15 minutes**), and a **>**.
   - Header **Pending changes**, then one row per queued change with the **target's icon**:
     - an app's real icon for app rules and app limits (Calendar, Chrome, YouTube…),
     - a **globe** for website rules,
     - a **clock** for anything else (auto-block).
   - Each row has a bold title (**Unblock Calendar**, **In-app rule · Shorts**, **Website rule · …**) and a time (**In 1 min.**).
   - An **info icon (i)** in the toolbar; tapping it opens the "How this works" overlay.
   - When something is queued: a hint line and a **Discard all** button.

### 3.2 The delay wheels
1. Tap the **Protection change delay** row.
2. **You should see:** the big total at the top, three wheels with the labels **days / hours / min**, and **Cancel** / **Save**.
3. There must be **no** extra hint paragraph and **no** On/Off switch. `0/0/0` reads **Off**.
4. While protection is active, drag the wheels to a value **smaller** than the current one.
5. **You should see:** the text **"While protection is active, the delay can only stay the same or be increased."** and Save does nothing.

### 3.3 Pills vs dialogs
1. Queue anything and discard anything (1.2, 1.6).
2. **You should see:** bottom pills for feedback, and a **bottom sheet** when you tap a pending row. You should never get a full-screen dialog for these.

### 3.4 Language and theme
1. Settings → Personalization → **Appearance** → set language to **German**.
2. Open the Queue page, the wheels, and the two new dialogs.
3. **You should see:** German text everywhere, nothing cut off. Switch back to English.

---

## 4. The other workstreams

### 4.1 Feature flags
1. Settings → Info → **App info**.
2. Press and hold the row that shows **2.2.8 (228)** for about **2.5 seconds**.
3. **You should see:** the **Developer Tools** screen opens. Scroll to the bottom.
4. **You should see a Feature flags section:**
   - **Diagnostic timeline · On**
   - **Schedule save preview · On**
   - **Automation engine v2 · Off · scaffold**
   - **Continuous usage trigger · Off · scaffold**
5. Tap **Diagnostic timeline**.
6. **You should see:** its subtitle flips to **Off**. Tap again to turn it back On.

### 4.2 Copy support info
1. Settings → Info → tap **Copy support info**.
2. **You should see:** the Support screen opens briefly, a pill confirms the copy, and the screen closes.
3. Open any text field (e.g. a note) and **paste**.
4. **You should see** in the pasted text: **Protection state**, **Base enabled**, **Blocking expected**, **Settings schema**, **Feature flag: …** lines, and a **Diagnostics timeline** list.

### 4.3 Map picker
**First turn protection off (0.5)** — schedule editing is locked while active.
1. Home → profile pencil → **Schedules** → **Open Rules**.
2. Tap **Add first schedule** (or **+** in the toolbar).
3. Tap **Location schedule**, then **Open map picker**.
4. **You should see (current builds, no Maps API key):** the message **"Map picker is unavailable in this build. Using location search instead."** and the text field stays usable. That is expected — there is no API key configured.
5. If a key is configured, the map should open and load without a premature "map unavailable" message.

### 4.4 Schedule save preview
**Protection must be off (0.5).**
1. Home → profile pencil → **Schedules** → **Open Rules**.
2. Tap **Add first schedule** (or **+**).
3. Tap **Time schedule**.
4. Leave everything at its default (profile Default, action Enable profile, time 08:00) and tap **Create** at the bottom.
5. **You should see:** a dialog **"Save this schedule?"** with rows **Profile: Default**, **Action: Enable profile**, **When: Weekly**, **Time: 08:00**, and a **Save schedule** button. **Cancel** throws it away.
6. Turn a schedule's switch on/off, delete one, or reorder — **no** preview dialog for those.
7. Re-enable protection (0.5).

---

## 5. The four tests that were not finished

Do these in order; each says what to look for.

### 5.1 App limit raise → queue
Follow **2.6** steps 1–3 (set 30, applies), then step 5 (raise to 60). **Expect** a pill and a queue row **App limits · <app>**.

### 5.2 Website limit raise → queue
Follow **2.5**. **Expect** a queue row **Website limits · <domain>**.

### 5.3 Clear all → queue
Follow **2.8**. **Expect** one queue row listing the unblocked apps.

### 5.4 Remove blocked app → queue
Follow **2.7**. **Expect** a queue row **Unblock <app>**.

### 5.5 OEM alarm delivery (Samsung/Bigme only)
1. Set the delay to 15 minutes and queue an unblock.
2. Lock the phone and leave it unplugged for 20–30 minutes.
3. **Expect:** the change has applied (open the app / check the queue). It may apply a few minutes late.
4. Repeat with Loq In added to the phone's "Sleeping apps"/"Deep sleeping apps" list.
5. **Worst case:** it applies the next time you open Loq In — note if that happens.

---

## 6. Regression spot checks

1. **Website blocking:** Websites → add `example.com/blocked/*` → open `example.com/blocked/test` in Chrome → it is blocked; `example.com/allowed` opens.
2. **YouTube rule:** turn a rule on → trigger it → it blocks; turn it off → it does not.
3. **Scanner:** use the QR shortcut → the camera fills the whole screen (no black bars).
4. **A7 warning:** with a Settings rule and Device Admin revoked → the picker shows the warning pill.
5. **Schedules still fire** and app blocking still blocks/allows normally.
