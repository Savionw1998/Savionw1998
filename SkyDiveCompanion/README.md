# SkyDive Companion v2.3

Phone-first skydiving companion for Android (Galaxy S23 Ultra in a pocket,
Galaxy Watch 8 as an optional verification surface). Zero dependencies:
plain framework Java, builds with Android Studio, min SDK 26, target SDK 35.

Plans that drove this version:

- [`docs/WIREFRAMES.md`](docs/WIREFRAMES.md) — every screen, tokens, interaction rules
- [`docs/FUNCTIONS_PLAN.md`](docs/FUNCTIONS_PLAN.md) — root causes found in v2.1 and the function-by-function fix list

## What changed from v2.1

**Altitude (precise feet)**
- Fixed the unit bug: v2.1 computed metres and displayed them as feet, so every reading was 3.28× low.
- New `AltitudeEngine`: median spike filter → ISA hypsometric conversion → 2-state Kalman filter (altitude + vertical speed) → AGL against a median 8 s ground baseline → optional ground-temperature correction → feet.
- Vertical rate comes from the filter state, not from differencing two 20 ms samples.
- Slow auto re-zero while genuinely still on the ground compensates weather drift; frozen the moment the climb starts. Manual RE-ZERO button too.
- DZ elevation (user-entered or GPS-seeded) gives MSL. GPS altitude is shown for cross-checking and feeds a 4-dot confidence indicator; it is never blended into AGL.
- Dwell-timed `JumpStateMachine` (GROUND → AIRCRAFT → FREEFALL → CANOPY → LANDED) with correct ft/min thresholds; manual MARK EXIT override.
- Synthetic full-jump test (`app/src/test/.../AltitudeEngineTest.java`): AGL error < 1 % in flight, < 3 ft on the ground, all phases detected.

**Watch challenge notifications**
- v2.1 sent an implicit broadcast to a manifest receiver; Android 8+ drops those silently, so the challenge never posted. The fallback path also started a foreground service that never called `startForeground()`.
- v2.2 posts the notification directly. The **WATCH CHECK PASSED** action is added via `Notification.WearableExtender`, so it renders on the Galaxy Watch and not on the phone. Each challenge carries a nonce and expires after 90 s.
- The app now listens for the ack and flips the card to green live, shows a countdown, checks that notifications are actually allowed (with a FIX button to the channel settings), and ships a Galaxy Wearable guide. The most common cause of "nothing on the watch" is Galaxy Wearable's *Show while phone in use* toggle being off.

**Stability**
- `registerReceiver` uses `RECEIVER_NOT_EXPORTED` (the v2.1 live screen crashed on Android 14+).
- Foreground service starts with the location type only when permission is granted; partial wake lock while tracking; all internal broadcasts package-scoped.

**UI**
- Same dark language, tightened: stepper, hero altitude card tinted by phase, stat tiles, status pills, gradient primary button, hairline card borders, tabular numbers, disabled-until-ready buttons with a reason line.
- New Logbook (last 20 jumps, CSV share / save to Downloads) and Settings (DZ elevation, ground temp, callouts, hard deck, watch options).
- Proper adaptive launcher icon and monochrome notification icon.

## Workflow
1. **Start Pre-Flight** → gear checks.
2. **Parachute on** checks + optional **watch challenge** (tap on the watch).
3. **Ready to board**: confirm summary, enter DZ elevation / ground temp, **Start ground tracking** when walking to the aircraft. Keep the phone still for the 8 s calibration.
4. Automatic phase detection, voice callouts (default 10,000 → 500 ft), hard deck warning, landing detection, 2 min post-landing recording, auto-save to the logbook.

## Build
Open the `SkyDiveCompanion` folder in Android Studio (Ladybug or newer), let Gradle sync, build and install the debug APK. No extra dependencies.

To run the engine test without Android Studio:
```
javac -d /tmp/out app/src/main/java/com/savion/skydivecompanion/AltitudeEngine.java \
      app/src/main/java/com/savion/skydivecompanion/JumpStateMachine.java \
      app/src/test/java/com/savion/skydivecompanion/AltitudeEngineTest.java
java -cp /tmp/out com.savion.skydivecompanion.AltitudeEngineTest
```

## Altitude limitation
A phone barometer is not a certified skydiving altimeter. Weather changes, HVAC, a hand over the sensor port, wind and sensor noise all affect it. The certified altimeter, audible, AAD and drop-zone procedures remain primary; this app is a recording and awareness companion.

## v2.3 fixes (reported from a real device)

**Ground calibration hung or the app died when tracking started**
- `SensorEvent.timestamp` is not `SystemClock.elapsedRealtime` on every device, and several Samsung barometers report a different clock entirely. v2.2 mixed the two, so the 8 s calibration window either never elapsed or elapsed instantly. Everything now runs on one clock. `harness/` has a regression test that fails on the old code and passes on the new.
- Android 14+ kills the process when `startForegroundService()` is not followed by a successful `startForeground()`. A location-typed foreground service is rejected outright without the location runtime permission, and v2.2 swallowed that rejection. The service now falls back through `specialUse` and then the untyped form, so promotion always succeeds, and the Start button asks for location first instead of failing inside the service.
- Sensor callbacks, text-to-speech, the wake lock and location updates can no longer take the app down; each failure is recorded instead.

**The watch challenge never reached the watch**
- The PASSED button was attached only to `Notification.WearableExtender`. Samsung's One UI Watch bridge forwards a notification's standard action list and drops extender-only actions, so nothing tappable arrived. The action is now a standard action *and* an extender copy for stock Wear OS.
- `setTimeoutAfter` is gone; some bridges drop a notification that carries it before relaying.
- New **SEND PLAIN TEST** button posts an action-free notification. If that does not reach the watch either, the problem is Galaxy Wearable's settings, not the app. The in-app guide now walks through them in order.

**Crash reporting**
- Any uncaught exception is captured and shown on the home screen next launch, with the full stack trace and a SEND REPORT button. No computer or cable needed to find out what went wrong.

## Prebuilt APK
`release/SkyDiveCompanion-v2.3-debug.apk` is a signed debug build (debug keystore, v2+v3 signatures, zipaligned). On the phone, open the file and allow installs from that app when asked. It will not install over a Play-signed or differently-signed build of the same package; uninstall v2.1 first if the installer refuses.

`scripts/build-apk.sh` reproduces it without Android Studio using aapt2, D8 and uber-apk-signer downloaded as plain files.

## Tests
- `app/src/test/.../AltitudeEngineTest.java` drives synthetic pressure traces through a full jump (plain `main`, no JUnit).
- `harness/` runs the real Activity and Service in Robolectric's JVM Android runtime: pre-flight flow, calibration, the wrong-sensor-clock regression, watch challenge actions and the crash banner. See `harness/README.md`.
