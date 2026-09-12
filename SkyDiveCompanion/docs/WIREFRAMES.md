# SkyDive Companion v2.2 — Wireframe Plan

Phone-first, one-hand, gloves-on. Dark UI stays (it is the right call for a
device that lives in a pocket and gets glanced at under a canopy). The v2.1
visual language is kept and tightened: same dark navy ground, same rounded
cards, same sky-blue accent. What changes is hierarchy, spacing, a stepper,
a hero altitude tile, stat tiles, status pills, and consistent buttons.

## Design tokens

| Token        | Value      | Use                                  |
|--------------|------------|--------------------------------------|
| BG           | #080C12    | window background                    |
| CARD         | #131A23    | default card                         |
| CARD_2       | #18212C    | secondary button / nested surface    |
| BORDER       | #1F2A37    | 1 dp hairline on every card          |
| TEXT         | #FFFFFF    | primary text                         |
| MUTED        | #9BAABC    | labels, helper copy                  |
| ACCENT       | #4BB1FF    | primary action, AGL number, stepper  |
| ACCENT_DEEP  | #1E7FD6    | gradient end for primary button/hero |
| GREEN        | #47D394    | verified / ready / GROUND            |
| AMBER        | #FFC34D    | calibrating / waiting / AIRCRAFT     |
| RED          | #FF6969    | blocked / FREEFALL / errors          |
| PURPLE       | #B48CFF    | CANOPY phase                         |

Type: system sans. Sizes 11 (labels, all-caps, letter-spaced), 13 (helper),
15 (body/buttons/checks), 20 (card titles), 26 (screen titles), 64 (hero AGL).
Numbers use tabular figures so the altitude does not jitter.

Radius: cards 18 dp, buttons 16 dp, pills 999 dp. Card padding 16/15.
Primary button 56 dp tall, gradient ACCENT → ACCENT_DEEP, bold white text.
Secondary button 52 dp, CARD_2 fill with BORDER outline.

## Shell (every screen)

```
┌──────────────────────────────────────────┐
│ SKYDIVE COMPANION                 [⚙︎]    │  24 bold + settings glyph
│ Preflight • jump tracking • logbook      │  13 muted
│ ● ● ● ● ●   Step 2 of 5 · Parachute on   │  stepper, filled = done, ring = current
│                                          │
│  ┌── content ─────────────────────────┐  │
│  │                                    │  │
│  │  (screen body scrolls)             │  │
│  │                                    │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

The stepper is hidden on Home, Logbook and Settings.

## 1. Home

```
┌──────────────────────────────────────────┐
│ Ready for the next jump                  │  26 bold
│ One button. Phone stays pocketed; voice  │  14 muted
│ and notifications do the work.           │
│                                          │
│ ┌ SESSION ───────────────────────────┐   │
│ │ ◉ STANDBY                          │   │  pill: grey
│ │ Nothing is tracking yet.           │   │
│ │ ─────────────────────────────────  │   │
│ │ Barometer  ✓ present   Watch  ✓ 2h │   │  quick status row
│ │ Notifications ✓ on     GPS   ✓     │   │
│ └────────────────────────────────────┘   │
│                                          │
│ [        START PRE-FLIGHT        ]  56dp │  primary gradient
│                                          │
│ ┌ LAST JUMP ─────────────────────────┐   │
│ │ 2026-09-11 14:02 · Exit 13,480 ft  │   │
│ │ Freefall 61 s · Max 118 mph        │   │
│ │ [ OPEN LOGBOOK ]                   │   │  secondary
│ └────────────────────────────────────┘   │
│                                          │
│ ┌ WHAT HAPPENS NEXT ─────────────────┐   │
│ │ ① Gear checks                      │   │  numbered rows, accent circle
│ │ ② Parachute-on checks              │   │
│ │ ③ Watch verification (optional)    │   │
│ │ ④ Start ground tracking            │   │
│ │ ⑤ Automatic jump tracking          │   │
│ └────────────────────────────────────┘   │
│                                          │
│ ┌ INSTRUMENT STATUS ─────────────────┐   │
│ │ Certified altimeter, audible/AAD…  │   │  13 muted
│ └────────────────────────────────────┘   │
└──────────────────────────────────────────┘
```

## 2. Gear check (step 1)

```
┌──────────────────────────────────────────┐
│ ● ○ ○ ○ ○   Step 1 of 5 · Gear check     │
│ Gear check                               │
│ Complete these before the rig goes on.   │
│                                          │
│ ┌────────────────────────────────────┐   │
│ │ ☐  AAD is ON and shows expected    │   │  each row 56 dp, tap anywhere
│ │ ─────────────────────────────────  │   │
│ │ ☐  Gear inspection complete        │   │
│ │ ─────────────────────────────────  │   │
│ │ ☑  Landing area / pattern checked  │   │  checked: accent box, muted strike-free
│ │ ─────────────────────────────────  │   │
│ │ ☐  Phone secured and ready         │   │
│ │ ─────────────────────────────────  │   │
│ │ ☐  Jump / exit plan understood     │   │
│ └────────────────────────────────────┘   │
│ 3 of 5 complete                          │  progress line, turns green at 5/5
│ [        I'M DONE — CONTINUE      ]      │  disabled (40% alpha) until all checked
│ [ BACK ]                                 │
└──────────────────────────────────────────┘
```

## 3. Parachute on + Watch (step 2/3)

```
┌──────────────────────────────────────────┐
│ ● ● ○ ○ ○   Step 2 of 5 · Parachute on   │
│ Parachute on                             │
│ ┌────────────────────────────────────┐   │
│ │ ☐ Chest strap … ☐ Leg straps …     │   │  5 rows as above
│ └────────────────────────────────────┘   │
│                                          │
│ ┌ GALAXY WATCH ─────────────── [ON ●]┐   │  switch in header row
│ │ ◉ WAITING FOR WATCH  0:42          │   │  pill amber + countdown while a
│ │ Tap "WATCH CHECK PASSED" on the    │   │  challenge is live; green "VERIFIED
│ │ watch. The button only exists on   │   │  10:31" after ack; red "NOT VERIFIED"
│ │ the watch, not the phone.          │   │  when expired
│ │ [ SEND WATCH CHALLENGE ]           │   │  secondary; becomes "SEND AGAIN"
│ │ Notifications: ✓ allowed           │   │  red + [FIX] button if blocked
│ │ ▸ Watch not showing it? Open guide │   │  expands 4-line Galaxy Wearable how-to
│ └────────────────────────────────────┘   │
│                                          │
│ [        I'M READY — CONTINUE     ]      │  disabled until checks + (watch off or verified)
│ [ BACK ]                                 │
└──────────────────────────────────────────┘
```

## 4. Ready to board (step 4)

```
┌──────────────────────────────────────────┐
│ ● ● ● ○ ○   Step 4 of 5 · Ready to board │
│ Ready to board                           │
│ Start when you are under the tent /      │
│ walking to the aircraft.                 │
│                                          │
│ ┌ PRE-FLIGHT SUMMARY ────────────────┐   │
│ │ Gear check        ✓ 5/5            │   │
│ │ Parachute on      ✓ 5/5            │   │
│ │ Watch             ✓ VERIFIED 10:31 │   │  or "— not used" / red
│ │ Location          ✓ granted        │   │
│ │ Barometer         ✓ 0.9 Pa noise   │   │
│ └────────────────────────────────────┘   │
│                                          │
│ ┌ FIELD SETUP ───────────────────────┐   │
│ │ DZ elevation   [  128 ] ft MSL     │   │  prefilled from GPS/last used
│ │ Ground temp    [   84 ] °F         │   │  used for temperature correction
│ │ Voice alerts   [ON ●]  Duck audio [ON ●]│
│ └────────────────────────────────────┘   │
│                                          │
│ [      START GROUND TRACKING      ]      │
│ [ BACK ]                                 │
└──────────────────────────────────────────┘
```

## 5. Live jump computer (step 5)

```
┌──────────────────────────────────────────┐
│ ● ● ● ● ●   Step 5 of 5 · Live           │
│ ┌ HERO ─ gradient tinted by phase ───┐   │  GROUND green / AIRCRAFT amber /
│ │ ◉ AIRCRAFT           ⏱ 12:41       │   │  FREEFALL red / CANOPY purple
│ │                                    │   │
│ │        12,480 ft                   │   │  64 bold tabular, AGL
│ │           AGL                      │   │
│ │  ▲ 1,240 ft/min      QNH 1017.2    │   │  arrow + rate, baseline hPa
│ │ Confidence ●●●○  baro ±6 ft        │   │  fusion quality dots
│ └────────────────────────────────────┘   │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐   │  3 stat tiles
│ │ MSL      │ │ GPS ALT  │ │ PRESSURE │   │
│ │12,608 ft │ │12,590 ft │ │652.4 hPa │   │
│ │DZ +128   │ │ ±14 ft   │ │raw 652.7 │   │
│ └──────────┘ └──────────┘ └──────────┘   │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│ │ MAX AGL  │ │ GPS SPD  │ │ NEXT CALL│   │
│ │12,480 ft │ │  92 mph  │ │ 10,000   │   │
│ └──────────┘ └──────────┘ └──────────┘   │
│                                          │
│ [ RE-ZERO ON GROUND ]  [ MARK EXIT ]     │  two half-width secondaries
│ [        STOP / SAVE JUMP         ]      │  red-outlined secondary
│ ┌ HOW ALTITUDE IS COMPUTED ───────── ▾┐  │  collapsed by default
│ └────────────────────────────────────┘   │
└──────────────────────────────────────────┘
```

## 6. Logbook

```
┌──────────────────────────────────────────┐
│ Logbook                                  │
│ ┌ LAST JUMP ─────────────────────────┐   │
│ │ 2026-09-11 14:02                   │   │
│ │ Exit 13,480 ft · Open 4,120 ft     │   │
│ │ Freefall 61 s · Canopy 4:10        │   │
│ │ Max descent 10,900 ft/min          │   │
│ │ Track points 1,482                 │   │
│ │ [ SHARE CSV ]   [ CLEAR ]          │   │
│ └────────────────────────────────────┘   │
│ ┌ PREVIOUS ──────────────────────────┐   │
│ │ 2026-09-05 · 13,200 ft · 58 s      │   │  up to 20 summaries, newest first
│ │ 2026-09-05 · 13,050 ft · 60 s      │   │
│ └────────────────────────────────────┘   │
│ [ BACK ]                                 │
└──────────────────────────────────────────┘
```

## 7. Settings

```
┌──────────────────────────────────────────┐
│ Settings                                 │
│ ┌ ALTITUDE ──────────────────────────┐   │
│ │ DZ elevation (ft MSL)   [  128 ]   │   │
│ │ Ground temperature (°F) [   84 ]   │   │
│ │ Auto re-zero on ground  [ON ●]     │   │
│ │ GPS cross-check         [ON ●]     │   │
│ └────────────────────────────────────┘   │
│ ┌ ALERTS ────────────────────────────┐   │
│ │ Voice alerts            [ON ●]     │   │
│ │ Duck other audio        [ON ●]     │   │
│ │ Callouts (ft)  [10000,9000,…,500]  │   │
│ │ Hard deck (ft)          [ 2500 ]   │   │
│ └────────────────────────────────────┘   │
│ ┌ WATCH ─────────────────────────────┐   │
│ │ Use Galaxy Watch        [ON ●]     │   │
│ │ [ OPEN NOTIFICATION SETTINGS ]     │   │
│ │ [ GALAXY WEARABLE GUIDE ]          │   │
│ └────────────────────────────────────┘   │
│ [ SAVE ]  [ BACK ]                       │
└──────────────────────────────────────────┘
```

## Interaction rules

- Every primary button is disabled (not hidden) until its preconditions are
  met, and a one-line reason sits directly above it.
- Phase colour is used in exactly three places: the session pill, the hero
  gradient, and the ongoing notification text. Nothing else changes colour.
- Nothing on the live screen is required. Voice and notifications carry the
  jump; the screen is a diagnostic surface.
