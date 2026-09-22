# Verification Guide — W5.2 / W5.1 / W5.4 / W6

**Purpose:** recreate every test run for this workstream, with exact steps and expected results.
**Devices:** Pixel 10 Pro XL (wireless adb) · emulator-5554 (API 36) · Samsung A12 / Bigme A14 (only if available)
**Build:** debug `com.oliver.loqin.loqindev` (versionName 2.2.8)
**Tracker:** `upstream231.md` · **Plans:** `docs/upstream-finish-plan.md`, `docs/w5-protection-change-gate-plan.md`

---

## 0. Setup for every test

1. Protection must be **active** (home shows "Active now") unless a test says otherwise.
2. Set the delay to **1 minute**: Settings (gear) → Open Settings → **Controls → Protection changes → Change delay** → wheels `0 days / 0 hours / 1 min` → Save.
   - While protection is active the delay can only stay or increase. If it is already higher, fully disable Loq In first (dashboard → Disable), set the delay, then re-enable.
3. Keep the **Protection changes** page open in your head: it lists every queued change with a due time ("In 1 min."). The bottom pill is only feedback.
4. Where things live:
   - Queue page: Settings → Controls → Protection changes
   - App rules (picker): profile pencil → Apps → Open Rules
   - In-app rules: profile pencil → In-app rules → Open Rules
   - Websites: profile pencil → Websites → Open Rules
   - Limits (apps): in the picker, **long-press** an app tile (or tap its clock button)
   - Limits (websites): open a website's usage detail → Edit limits
   - Blocked apps list: home → Blocked apps row
   - Developer screen: Settings → Info → App info → **long-press the version row ~2.5s**
   - Support copy: Settings → Info → **Copy support info**

---

## 1. Queue mechanics

| # | Test | Steps | Expected |
|---|---|---|---|
| 1.1 | Parity (delay off) | Delay Off, protection active → unblock any app in the picker → Save | Refused with the locked dialog; rule unchanged |
| 1.2 | Queue + apply | Delay 1 min → unblock app A → Save | Pill "This protection-reducing change is queued…"; entry "Unblock A" with a due time; A stays blocked |
| 1.3 | Timer fires | Wait ~70s | Entry disappears; A opens normally |
| 1.4 | Stricter applies now | Block a new app → Save | Applied immediately, no entry, no pill |
| 1.5 | Per-target timers | Unblock A, then unblock B (separate saves) | **Two** entries ("Unblock A", "Unblock B") with independent due times; no timer reset |
| 1.6 | Same target re-queued | Unblock A, then repeat the same unblock before it fires | One entry; the original due time is kept (no extension) |
| 1.7 | Discard one | Protection changes → tap an entry → bottom sheet → Discard | Only that entry disappears; pill "Pending change discarded." |
| 1.8 | Discard all | Protection changes → Discard all → confirm | List empty |
| 1.9 | Apply now (fully off) | Queue a change → fully disable Loq In (not temporary pause, not emergency unlock) → open the page | "Apply all now" appears; per-item sheet shows "Apply now"; applying clears the queue and the change takes effect |
| 1.10 | No early apply | With temporary pause or Emergency Unlock active | "Apply now" hidden/refused |
| 1.11 | Reboot | Queue with a 15-min delay → reboot before due | Entry survives; alarm re-armed (applies after boot/when due) |
| 1.12 | Force-stop | Queue → force-stop Loq In from system settings → wait | Alarm wakes the app; change applies |
| 1.13 | Doze | Queue 1 min → `adb shell dumpsys deviceidle force-idle` → wait 95s | Still pending (deferred, by design) → `dumpsys deviceidle unforce` → applies within ~30s |
| 1.14 | Deleted profile | Queue a change for profile X → delete profile X | Entry dropped on next apply, no crash |
| 1.15 | Stricter wins (limits) | Queue a limit raise (e.g. 30→60) → before it fires set 20 (stricter) | Queued raise is skipped; value stays 20 |

---

## 2. Per-surface queue behaviour (delay 1 min, protection active)

| # | Surface | Steps | Expected |
|---|---|---|---|
| 2.1 | App unblock | Picker → untick app → Save | Queued (1.2) |
| 2.2 | App tile pending state | After queueing, look at the picker | Tile uses a **lighter accent shade** + faded check (no orange/clock) |
| 2.3 | Auto-block new apps | Picker checkbox: turn **on** then **off** | On applies immediately; off queues "Auto-block new apps · <profile>" |
| 2.4 | Auto-block cancel | While the disable is pending, turn it back on | Pending disable is cancelled |
| 2.5 | In-app rule | In-app rules → turn a surface off | Queues "In-app rule · <surface>"; switch previews the target state at reduced alpha |
| 2.6 | In-app whole-app offer | Turn a rule **on** for an app that is not whole-app blocked | Dialog "Also block <app> entirely?" → confirming blocks the app (or queues) |
| 2.7 | Website rule disable | Websites → toggle a rule off | Queues "Website rule · <rule>"; switch previews off, faded; meta "Pending — applies after the delay" |
| 2.8 | Website rule enable | Toggle a disabled rule on | Applies immediately |
| 2.9 | Website rule delete | Swipe a rule → Delete | Queues "Website rule · <rule>"; rule stays in the list |
| 2.10 | Website limit raise | Website usage detail → Edit limits → raise the daily minutes | Queues "Website limits · <domain>" |
| 2.11 | Website limit lower | Lower the minutes | Applies immediately |
| 2.12 | App limit raise | Picker → long-press an app with an existing limit → raise it | Queues "App limits · <app>" (the editor opens while protection is active) |
| 2.13 | App limit lower | Lower the value | Applies immediately |
| 2.14 | Remove blocked app | Home → Blocked apps → tap an app → Remove | Queues "Unblock <app>" (+ "App limits/rules · <app>" if it had limits) |
| 2.15 | Clear all | Picker → select apps → Clear all → Save | Queues "Unblock A, B, C" (or "Unblock N apps") |
| 2.16 | Still refused | Add/edit a website **rule**; switch rule mode; switch control mode; switch/delete a profile | Refused with the locked message (structural changes are never queued) |

---

## 3. UI behaviour

| # | Test | Steps | Expected |
|---|---|---|---|
| 3.1 | Page layout | Open Protection changes | Section headers + cards; delay row with trailing value; pending list rows with the target app icon (globe for websites); info icon in the toolbar opens the explanation overlay |
| 3.2 | Delay picker | Tap the delay row | Days/hours/minutes wheels, live total, no hint text, no Off switch; `0/0/0` = Off |
| 3.3 | Locked delay | While active, pick less than the current delay | Save is blocked and the locked hint appears; picking more saves |
| 3.4 | Pending icons | Queue entries for an app, an in-app rule, a website and a limit | Calendar/YouTube/globe/Chrome icons respectively |
| 3.5 | Pills vs dialogs | Queue anything / discard anything | Bottom pill, never a full-screen dialog; per-item review is a bottom sheet |
| 3.6 | Languages/theme | German, light mode | All new copy translated; layout fine |

---

## 4. Other workstreams

| # | Test | Steps | Expected |
|---|---|---|---|
| 4.1 | W5.1 flags | Long-press the version row ~2.5s | Developer screen with a **Feature flags** section: Diagnostic timeline (On), Schedule save preview (On), Automation engine v2 (Off · scaffold), Continuous usage trigger (Off · scaffold). Tapping toggles and persists (`loqin_feature_flags.xml`) |
| 4.2 | W5.4 copy | Settings → Info → Copy support info | Copies the report; the Android clipboard preview shows it. Contains: protection state / base enabled / blocking expected, settings schema, every feature flag, diagnostics timeline |
| 4.3 | W6.2 map picker | Schedules → create → Location → Open map picker | With no `MAPS_API_KEY`: "Map picker is unavailable in this build" and the text search stays usable. With a key: the map opens; no premature "map unavailable" message |
| 4.4 | W6.3 schedule preview | Schedules → create/edit → Save | Row-style dialog "Save this schedule?" (Profile / Action / When / Time / Note) + Save schedule. Toggling/deleting/reordering does **not** show it |
| 4.5 | W6.4 whole-app offer | See 2.6 | Offer only for apps that are not already whole-app blocked; never for protected/always-excluded apps |

---

## 5. Emulator reproduction (how the automated checks were run)

The emulator runs a debug build, so `run-as` can read/write prefs. Helper patterns used:

```bash
# Read the pending queue
adb -s emulator-5554 shell run-as com.oliver.loqin.loqindev \
  cat /data/data/com.oliver.loqin.loqindev/shared_prefs/com.oliver.loqin.loqindev_preferences.xml

# Set the delay directly (bypasses the "can only increase" rule; force-stop first)
adb shell am force-stop com.oliver.loqin.loqindev
python3 - <<'PY'
x=open('/tmp/state.xml').read().replace('value="0" />','value="1" />')
open('/tmp/state1.xml','w').write(x)
PY
adb shell "run-as com.oliver.loqin.loqindev sh -c 'cat > /data/data/com.oliver.loqin.loqindev/shared_prefs/com.oliver.loqin.loqindev_preferences.xml'" < /tmp/state1.xml

# Inspect the armed alarm
adb shell dumpsys alarm | grep -A2 ProtectionChangeReceiver

# Doze
adb shell dumpsys deviceidle force-idle   # unplugged; applies are deferred
adb shell dumpsys deviceidle unforce      # pending changes apply shortly after
```

UI checks used `uiautomator dump` + `input tap` with coordinates from the dump; the
pref files above are the source of truth for pass/fail (queue content, flags, limits).

**Known automation limits:** the limit editor's value field is unreliable when driven by
`input text` (it produced "603" once); prefer manual entry for limit tests. Clipboard content
cannot be read from adb (Android restricts it) — verify by pasting.

---

## 6. Not yet verified (do these next)

| Item | Steps | Expected |
|---|---|---|
| App-limit **raise** | Picker → long-press an app with a limit → raise the minutes → Save | Pill + "App limits · <app>" entry; value unchanged until due |
| Website-limit **raise** | Website usage detail → Edit limits → raise minutes | Pill + "Website limits · <domain>" entry |
| **Clear all** | Picker → select several apps (bulk bar appears) → Clear all → Save | Pill + one "Unblock …" entry listing the apps |
| **Remove blocked app** | Home → Blocked apps → tap app → Remove | Pill + "Unblock <app>" entry |
| OEM alarms | Samsung A12 / Bigme: delay 15 min → queue → lock the phone 20–30 min | Applies (possibly late); also test with the app in the OEM's sleeping-apps list |

---

## 7. Regression checks (should be unaffected)

- Website blocking still works (path rules, Firefox/Chrome) — `ff_matrix` style spot check.
- YouTube in-app rules still block (mini-player, Shorts) and PiP still behaves as before.
- W6.1 screens: scanner full-bleed, NFC waiting insets.
- A7: Settings rule without Device Admin shows the picker warning pill.
- App blocking still blocks and allows correctly; schedules still fire.
