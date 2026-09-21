# Finishing the Upstream Port — Comprehensive Plan

**Status:** active plan
**Date:** 2026-09-21
**Current branch:** `feature/upstream-w6-polish` (contains W1→W2→W3→W4.1→W6.1/A7→W4.2-YouTube→W5.2)
**Tracker:** `upstream231.md` · **W5.2 detail:** `docs/w5-protection-change-gate-plan.md`

---

## 0. Where we are

| Workstream | State |
|---|---|
| W1 safety (A1/A7/C3/A6) | Done, merged to `origin/main` (PR #10) |
| W2 limits (A4/A5) | Done on the stack; manual pause/off-on checks pending |
| W3 websites (A3) | Done on the stack; backup round-trip check pending |
| W4.1 work budget (A2) | Done; slow-OEM stress pending |
| W4.2 YouTube (B) | Done + merged into the line; PiP device pass pending |
| W5.2 protection change gate | **Complete** (P0–P6), merged into the line |
| W6.1 edge-to-edge, A7 follow-up | Done |
| W5.1 / W5.3 / W5.4 | **Not started** (this plan) |
| W6.2–W6.6 | Not started (this plan) |
| Phase 2 (gate coverage extension) | Not started — and not upstream; see §5 |
| Push / PR of the stack | Not started — only W1 is on `origin/main` |

Tests: 71/71 (`./gradlew :app:testDebugUnitTest`). Both test devices run the current build (Pixel 10 Pro XL + emulator-5554).

---

## 1. Definition of done

The upstream port is finished when:

1. W5.1, W5.3, W5.4 are implemented, documented in `upstream231.md`, and unit/emulator verified.
2. W6.2–W6.5 are either implemented or explicitly skipped with a recorded reason (W6.6 is already recommended-skip).
3. The W5.2 manual verification backlog is closed (or explicitly accepted).
4. The whole stack is pushed and merged to `main` (or PRs are open and reviewed).
5. `upstream231.md` has no open decision that blocks a shipped feature; remaining decisions are explicitly deferred with a note.

---

## 2. W5.2 leftovers (nearly nothing)

Code is complete; nothing structural remains. Two things:

1. **Verification backlog** (phone, ~15 min total):
   - app-limit raise → queued entry
   - website-limit raise via a website's usage detail → queued entry
   - Clear all in the picker → Save queues the unblocks
   - remove blocked app from the home list → queued
   - OEM alarm delivery on Samsung A12 / Bigme (only if those devices are actually available — see §9)
2. **Deferred fork feature (not upstream):** website per-visit / opens limits with the app-style dialog. Upstream has only `DomainLimitStore` (daily minutes per host), so this is our own feature. Build it after upstreaming (its own branch, its own plan; it touches the accessibility service).

---

## 3. W5.1 — FeatureFlagStore + dev tiles

**Goal:** stage experimental behavior behind switches, upstream-style.

**Deliverables**
- `data/prefs/FeatureFlagStore.kt` (our package): flags `DIAGNOSTIC_TIMELINE` (default on), `AUTOMATION_SAVE_PREVIEW` (on), `AUTOMATION_ENGINE_V2` (off, scaffold), `CONTINUOUS_USAGE_TRIGGER` (off, scaffold); `isEnabled`, `setEnabled` (records a timeline event), `snapshot`.
- Dev tiles in `AdvancedModeActivity` to flip each flag (title + summary + current state), reusing our `TilesInfoActivity.Tile`.
- Consumers wired:
  - `AppLogStore`/`DiagnosticsTimelineStore` recording gated on `DIAGNOSTIC_TIMELINE` (default on, so behavior is unchanged; the gate's protection events must keep recording).
  - `AUTOMATION_SAVE_PREVIEW` reserved for W6.3.
  - The two scaffolds get tiles but no behavior (mark them "scaffold" in the subtitle).
- en/de strings.

**Files:** new store; `AppLogStore.kt`; `AdvancedModeActivity.kt`; `strings_advanced_mode.xml` (+de).

**Verification:** toggle each flag, restart, value persists; timeline records the flag change; with the timeline flag off, `AppLogStore` stops feeding the timeline but the support report still works; tests green.

**Effort:** 0.5–1 day. **Depends on:** nothing. **Unlocks:** W6.3.

---

## 4. W5.3 — Advanced Protection hub — **SKIPPED (owner decision 2026-09-21)**

> Removed from the plan. The owner does not want a separate hub screen; the W5.2 "Protection changes" page stays as-is, and the support report reads protection state directly (no shared provider). Nothing else depended on the hub.

**Goal:** one canonical view of protection state, and a single place that gathers protection status, the change delay/queue, and privileged setup.

---

## 4b. W5.4 — Support-report surfacing — **DONE 2026-09-21**

Implemented in `SupportActivity`: canonical **Protection state** (+ base enabled, blocking expected) in the Loq In state section, **Settings schema** + **Feature flag** lines in Build configuration, and a **Diagnostics timeline** section (last 20 events). Also fixed the entry point: the Info screen's Support card opened the raw log viewer; it now opens the report, which links to the logs itself.

Remaining: open Settings → Info → Support once to eyeball the new sections.

---

## 5. Phase 2 — explained (and re-scoped)

**What it was in my earlier framing:** extend the protection change gate beyond the Phase-1 surfaces to the remaining places that can weaken protection:
- protection toggles in Feature access (mixed channels/access, paired UIDs, auto-pair, block notifications, session-missed notifications, lock-warning suppression, emergency visibility),
- hidden/ignored apps,
- NFC pairing writes,
- schedule edits,
- website limits,
- backup import per-item.

**Important correction:** this is **not upstream parity**. Upstream's `ProtectionChangeGate` covers exactly what we already ported (app selection, in-app rules, website rules, app limits, control mode, profile structure). Upstream does **not** gate the items above, and it has no per-visit/opens limits for websites either. So, given your instruction "I want whatever is in the upstream":

- **Upstream parity is already complete** once W5.1/W5.3/W5.4 and W6 are done.
- Phase 2 is optional **fork hardening**, not part of the upstream port.

**Recommendation:** drop Phase 2 from the finish plan. Revisit after real use of the queue; if you then want more, the highest-value subset is protection toggles + hidden apps (both are genuine weakening surfaces). Website per-visit/opens limits stay as a separate fork feature (§2.2).

---

## 6. W6 — remaining polish

| Item | What | Notes | Effort |
|---|---|---|---|
| **W6.2 E6** | Defer `SupportMapFragment` creation in `feature/schedule/LocationMapPickerActivity.kt` (post to the container, reuse existing fragment, `runOnCommit`, unavailable message only on failure) | Self-contained; low risk | 0.5 d |
| **W6.3 E7** | Port `SchedulePreviewFormatter.kt` + save-preview dialog | **Depends on W5.1** (`AUTOMATION_SAVE_PREVIEW` flag); our `ScheduleStore.Action` has 2 extra values — extend the `when` | 1 d |
| **W6.4 E8** | Pinned in-app rule → offer "Block entire app" | Route the whole-app block through `ProtectionChangeGate.requestAppSelection` so it queues correctly | 0.5–1 d |
| **W6.5 E12** | Bottom-nav runtime menu + `keep_bottom_navigation.xml` | Only if it matches our redesigned home; low priority — recommend skip if it conflicts | 0.5 d or skip |
| **W6.6 E9** | Onboarding reorder | Recommended **skip** (our onboarding is redesigned; upstream's adapter reorder conflicts). Needs your confirmation (decision §8 #4) | 0 |

Order: W6.2 → W6.3 (after W5.1) → W6.4 → W6.5/skip.

---

## 7. Order of work, dependencies, effort

```
A. W5.1 flags            (0.5–1 d)  ──► W6.3
B. W5.3 hub + state      (1–2 d)    ──► W5.4
C. W5.4 support report   (0.5–1 d)
D. W6.2                  (0.5 d)
E. W6.3                  (1 d)      (needs A)
F. W6.4                  (0.5–1 d)  (needs W5.2 gate — done)
G. W6.5 or skip          (0–0.5 d)
H. Verification + phone pass (1 d)
I. Push/PR + docs        (0.5 d)
J. Website per-visit/opens limits (fork feature, 2–3 d) — AFTER upstreaming
```

**Total upstream-only:** ~5–8 working days plus verification.

Suggested branches:
- `feature/upstream-w5-rest` for A+B+C (one branch, three commit groups).
- `feature/upstream-w6-rest` for D–G.
- Merge each into `feature/upstream-w6-polish` (or the current integration tip) when verified.

---

## 8. Verification & device matrix

**Per workstream:** unit tests where logic exists; emulator checks (light/dark, en/de, locked/unlocked); no accessibility-service changes in W5.1/W5.3/W5.4, so risk is UI-level.

**Backlog to close before calling the port finished** (existing rows in `upstream231.md` §7):
- W2: session-limit pause/off-on persistence on a real device.
- W3: backup export → clear data → import round trip (rules + limits).
- W4.1: slow-OEM stress (Samsung A12 / Bigme) for the work budget.
- W4.2: PiP device pass (never verified on a real device; the emulator cannot enter PiP).
- W5.2: the four phone checks from §2.1.
- OEM alarm delivery for the queue.

**Device availability is a dependency:** the matrix references a Samsung A12 and a Bigme A14. If those are not available, mark those rows as "not run — device unavailable" rather than leaving them open forever.

---

## 9. Shipping the stack (currently unpushed)

`origin/main` has only W1 (PR #10). Everything else is local.

Options:
- **One PR** `feature/upstream-w6-polish` → `main` (all workstreams at once; large diff).
- **Chained PRs** (`w2` → `w4-a2` → `w6-polish`) for reviewability, matching how W1 was handled.

Either way: push each branch, open the PR(s), link them to this thread, and keep `upstream231.md` as the review index (it already references commit hashes per step). Do this **before or after** the remaining W6 work — recommend after W5.1–W5.4 so the PR contains the complete upstream port minus the optional items.

---

## 10. Open decisions (with defaults if you don't answer)

| # | Decision | Default |
|---|---|---|
| 1 | Phase 2: drop, reduce, or full? | **Drop** (not upstream; revisit later) |
| 2 | W5.3 hub role: overview-only vs move toggles in | **Overview-only** |
| 3 | W6.5 bottom-nav menu: port or skip | **Skip if it conflicts with the redesign** |
| 4 | W6.6 onboarding reorder (E9): confirm skip | **Skip** |
| 5 | D5 `.md` attachment in the support report | **Clipboard only** |
| 6 | W3.1: deleting a host rule also deletes its path rules? | **Keep current behavior** (path rules survive) |
| 7 | PR shape: single vs chained | **Chained** |
| 8 | Website per-visit/opens limits: do after upstream | **Yes, as its own branch/plan** |

---

## 11. Risks

| Risk | Mitigation |
|---|---|
| W5.1's timeline flag could silently disable the gate's timeline events | Default on; gate events keep recording; test both states |
| W5.3 duplicates existing screens and confuses navigation | Overview-only role; links to existing editors; one Settings entry |
| W6.3's formatter misses our extra `ScheduleStore.Action` values | Exhaustive `when`; unit test every action |
| W6.4's "block entire app" bypasses the queue | Route through `requestAppSelection` |
| Device matrix rows never run (no OEM devices) | Mark "not run — device unavailable"; don't block the finish line |
| Stack grows further without being pushed | Push after W5.1–W5.4 and open PRs (step I) |

---

## 12. What you may have missed

1. **Nothing past W1 is pushed.** The port is finished in code but not in the repo's history that others can see. Plan for push/PR explicitly (§9) or it stays local forever.
2. **Phase 2 is not upstream.** If "whatever upstream has" is the goal, Phase 2 is optional and can be dropped; only the website per-visit/opens feature you asked for remains as a fork feature.
3. **W6.3 depends on W5.1.** Doing W6 first would mean porting the preview without its flag; order matters.
4. **The verification backlog is real work**, not just formality: W2 session limits, W3 backup round trip, W4.1 slow-OEM, W4.2 PiP, the W5.2 phone checks, OEM alarms. ~1–2 days if the OEM devices exist.
5. **OEM devices may not exist.** The Samsung A12/Bigme rows have never been run in this project; if the devices aren't available, say so and we'll close those rows as "not run".
6. **Open decisions block specific items:** E9 skip (W6.6), D5 attachment (W5.4), host-rule deletion (W3 follow-up). Answering the defaults in §10 unblocks everything.
7. **The hub and the W5.2 page overlap.** Decide now (default: hub links to the page) or you'll have two homes for "protection changes".
8. **`feature/upstream-w5-protection-gate` is now a duplicate** of work merged into the line. Keep it as a reference or delete it; don't build on it.
9. **Release/version:** the app is 2.2.8; after the port lands you may want a version bump/changelog. Not in scope here, but decide before shipping to anyone else.
10. **Website per-visit/opens limits are a service-level feature** (new stores, tracking, enforcement). They should not be squeezed into the W6 polish window; give them their own plan after the port.

---

## 13. Immediate next steps

1. Confirm the §10 defaults (or correct them).
2. Create `feature/upstream-w5-rest` off the current integration tip and implement **W5.1** (smallest, unblocks W6.3).
3. Then **W5.3** (state provider + hub) and **W5.4** (support report).
4. Push the stack and open the chained PRs.
5. W6.2 → W6.3 → W6.4 (+ W6.5/skip), then the verification backlog, then the website limits feature.
