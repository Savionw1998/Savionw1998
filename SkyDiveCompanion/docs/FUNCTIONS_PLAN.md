# SkyDive Companion v2.2 — Functions Plan

Three goals, in priority order: altitude that is actually precise in feet,
watch challenge notifications that actually arrive, and a cleaner UI on the
same visual language. Everything below maps to a class and a method.

## 0. Root-cause findings in v2.1 (what is being fixed)

| # | Symptom you saw                        | Cause in v2.1                                                                                          |
|---|----------------------------------------|--------------------------------------------------------------------------------------------------------|
| 1 | Altitude numbers are wrong / too low   | `44330*(1-(p/p0)^0.1903)` returns **metres**, but it is displayed and thresholded as **feet** (3.28× low) |
| 2 | Vertical rate jumps around wildly      | ft/min is derived from two consecutive 20 ms samples of a smoothed value, so 0.1 m noise becomes 300 m/min |
| 3 | Baseline drifts / AGL reads ±30 ft on the ground | Baseline is a 1.2 s average taken once; weather drift, HVAC and hand heat are never compensated  |
| 4 | Watch challenge never appears          | `sendBroadcast(new Intent("…WATCH_TEST"))` is an **implicit** broadcast to a manifest receiver; Android 8+ drops it |
| 5 | If it did fire, the app would crash    | The receiver calls `startForegroundService` and the service never calls `startForeground` for that action |
| 6 | Live screen crashes on Android 14+     | `registerReceiver` without `RECEIVER_NOT_EXPORTED` throws a SecurityException at targetSdk 34+          |
| 7 | UI never shows a watch ack             | `WATCH_VERIFIED` is broadcast but nothing listens; the switch state is also not persisted              |
| 8 | Logbook is written but never shown     | `jumpLog` prefs are written by the service, no screen reads them                                        |
| 9 | Phone action can fake the watch check  | The ack action shows on the phone too, so tapping it on the phone "passes" the watch                    |

## 1. Altitude engine — `AltitudeEngine.java` (new, pure Java, unit-testable)

Inputs: raw pressure (hPa) at ~50 Hz, optional GPS fixes, optional ground
temperature and DZ elevation. Output: an `AltitudeEstimate` snapshot.

| Function | What it does |
|----------|--------------|
| `pressureToMetres(p, p0)` | Hypsometric ISA formula: `44330.77 × (1 − (p/p0)^0.190263)`. Returns **metres**. |
| `metresToFeet(m)` | `× 3.280839895`. The only place the conversion lives. |
| `temperatureCorrection(aglM, groundTempC)` | Scales AGL by `(273.15 + T_ground) / 288.15`. A hot Florida day (35 °C) is a +7 % correction: 700 ft at 10,000 ft. |
| `addPressure(hPa, tNanos)` | Median-of-5 spike filter → 2-state Kalman filter (altitude, vertical speed) with constant-velocity model. Process noise tuned so freefall accelerations are tracked within ~0.3 s, but ground noise is ±1–2 ft. |
| `calibrateGround(durationMs)` | Time-based baseline: collects up to 8 s of samples, drops the first 1.5 s (hand-heat settling), takes the **median** of the rest. Records sample noise σ as a quality metric. |
| `autoRezero()` | On the ground only (state GROUND, |vs| < 60 ft/min for > 20 s, AGL within ±40 ft): baseline tracks the filtered pressure with a 3-minute time constant. Compensates weather drift while waiting for the load. Frozen the moment ascent begins. |
| `setGps(alt, acc, vAcc, tMs)` | Stores fix. On the ground, averaged GPS MSL altitude (API 34 `getMslAltitudeMeters()` when available, else WGS84 altitude) seeds DZ elevation if the user has not set one. In flight, a GPS-vs-baro disagreement > 3σ is surfaced as a confidence drop, never blended blindly. |
| `verticalSpeedFpm()` | From the Kalman velocity state, not from differencing. |
| `confidence()` | 0–4 dots from baseline age, noise σ, temperature-correction availability and GPS agreement. |
| `snapshot()` | AGL ft (1 ft resolution), MSL ft, pressure altitude ft, vs ft/min, raw/filtered hPa, baseline hPa, GPS alt/acc, confidence. |

## 2. Jump state machine — `JumpStateMachine.java` (new)

Dwell-timed transitions so a gust or a door bump cannot flip the state.

| From → To | Condition (all in feet, feet/min) | Dwell |
|-----------|-----------------------------------|-------|
| GROUND → AIRCRAFT | vs > +300 and AGL > 150 | 4 s |
| AIRCRAFT → FREEFALL | vs < −3,000 | 1.5 s |
| FREEFALL → CANOPY | vs > −2,500 and vs < +300 | 3 s |
| CANOPY → LANDED | AGL < 100 and |vs| < 300 | 5 s |
| LANDED → (stop) | 2 min post-landing recording | — |
| any → manual | "MARK EXIT" button forces FREEFALL | — |

Each transition emits an event (notification + optional voice) and records
exit altitude, opening altitude, freefall seconds, canopy seconds.

## 3. Alerts — inside `DiveService`

| Function | Behaviour |
|----------|-----------|
| `callouts(agl)` | Configurable descending bands (default 10k … 500). Spoken once per jump, reset on each new tracking session. |
| `hardDeck(agl)` | One-time urgent voice + high-importance notification below the configured hard deck while still in FREEFALL. |
| `say(text)` | Unchanged approach (TTS + transient duck), but the rate limit is per-band, not global, so a callout is never swallowed by an event alert. |

## 4. Watch challenge — `WatchChallenge.java` (new) + `WatchTestReceiver`

| Function | Behaviour |
|----------|-----------|
| `send(context)` | **Direct call**, no broadcast, no service. Creates the `watch` channel (IMPORTANCE_HIGH, vibrate), posts a notification with `Notification.WearableExtender`. The **"WATCH CHECK PASSED"** action is added only to the wearable extender, so it renders on the Galaxy Watch and not on the phone (fixes #9). Phone body says "Look at your watch". Each challenge carries a fresh nonce and a 90 s expiry. |
| `WatchTestReceiver.onReceive(WATCH_ACK)` | Explicit PendingIntent broadcast (allowed). Validates nonce + expiry, stores `watch_verified_at`, cancels the notification, sends a package-scoped `WATCH_VERIFIED` broadcast. |
| `MainActivity` receiver | Registered with `RECEIVER_NOT_EXPORTED` in `onStart`, unregistered in `onStop`. Flips the watch card to green "VERIFIED hh:mm" live, no re-navigation needed. |
| `notificationsBlocked(context)` | Checks POST_NOTIFICATIONS, `areNotificationsEnabled()`, and the watch channel importance. Shows a red status + **FIX** button that deep-links to the channel settings. |
| Help panel | Galaxy Wearable → Watch settings → Notifications → App notifications → SkyDive Companion ON; and "Show while phone in use" ON. The most common reason a Galaxy Watch shows nothing is that toggle. |
| Verification freshness | A verification older than 6 h is treated as stale on the next pre-flight. |

## 5. Service hardening — `DiveService`

- `onStartCommand` calls `startForeground` immediately for every action that
  arrives via `startForegroundService`; nothing else uses that path.
- Foreground service only starts when FINE_LOCATION is granted (Android 14
  throws otherwise). Without it the app still tracks the barometer and says so.
- Partial wake lock while tracking so the pocketed phone keeps sampling.
- All app-internal broadcasts are `setPackage(getPackageName())`.
- `SENSOR_DELAY_FASTEST` for the barometer; the Kalman filter does the smoothing.
- The jump log keeps the last 20 summaries plus the last full track (CSV).

## 6. UI — `MainActivity` + `Ui.java` (new view kit)

| Function | Result |
|----------|--------|
| `Ui.card / hero / tile / pill / stepper / checkRow / primary / secondary / toggleRow / numberField` | One place for every style. Cards get a hairline border; primary buttons a gradient; numbers use tabular figures. |
| Screens | Home, Gear, Parachute+Watch, Board, Live, Logbook, Settings (see WIREFRAMES.md). |
| State | Checklist progress, watch switch, DZ elevation, ground temp, callouts, hard deck, voice/duck all persisted in `SharedPreferences`. |
| Lifecycle | Receivers registered in `onStart`/`onStop`, never per screen. Live screen reconnects to a running service on relaunch. |
| Icon | Vector adaptive launcher icon and a proper monochrome notification icon (a canopy) instead of the system compass. |

## 7. Out of scope (deliberately)

- No AndroidX / Compose migration: the app builds with zero dependencies and
  that is a feature for a personal tool. Everything uses framework classes.
- No Wear OS companion app. Notification bridging via Galaxy Wearable is what
  the user already relies on; making it work reliably is the fix.
- No cloud sync. CSV share covers the logbook export.

## 8. Verification

- Sources compile against the Android 15 framework jar (`javac`, API 35).
- `AltitudeEngineTest` (plain `main`) drives synthetic pressure traces:
  ground noise, a 12,500 ft climb, freefall at 176 ft/s, canopy at 18 ft/s,
  and checks AGL error < 5 ft on the ground, < 1 % in flight, and correct
  state transitions with no false flips.
