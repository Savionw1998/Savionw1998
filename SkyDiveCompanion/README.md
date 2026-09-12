# SkyDive Companion v2.1

Phone-first skydiving companion for Android. Designed around a pocketed Galaxy S23 Ultra with voice alerts, notifications, GPS and barometric altitude. Galaxy Watch 8 can be used as an optional notification/verification surface.

## v2.1 workflow
1. **Start Pre-Flight** — no jump tracking yet.
2. **Gear check** — AAD, gear inspection, landing area/pattern, phone, jump plan.
3. **Parachute-on check** — chest strap, leg straps, helmet/chin strap, goggles, altimeter.
4. **Watch check** — optional. The app sends a notification with a Watch action. Tap **WATCH CHECK PASSED** on the Watch if it appears; the phone records the verification.
5. **Start Ground Tracking** — begin live GPS/barometer recording when heading under the tent / toward the aircraft.
6. Automatic ascent, freefall, canopy and landing detection continue through the jump. Post-landing recording remains active for about 2 minutes.

## Altitude/barometer changes
- Ground calibration now averages several seconds of pressure readings rather than using one instantaneous reading.
- Pressure is low-pass filtered to reduce sensor noise.
- AGL is calculated from the averaged ground pressure baseline.
- Small negative AGL values are clamped to zero near the ground.
- The live screen shows pressure in **hPa** (hectopascals), absolute pressure altitude, AGL and vertical rate.
- The vertical-rate number is **feet per minute**, so a negative value means descending; it is not altitude.
- The app records raw GPS and barometric data for the jump log.

### Important altitude limitation
A phone barometer is not a certified skydiving altimeter. Rapid weather/pressure changes, HVAC, indoor pressure gradients, movement, wind and sensor noise can affect readings. The app should not replace a certified altimeter, audible, AAD or drop-zone procedures.

## Build
Open the `SkyDiveCompanion` folder in Android Studio, allow Gradle sync, then build/install the debug APK. The project targets SDK 35 and min SDK 26.
