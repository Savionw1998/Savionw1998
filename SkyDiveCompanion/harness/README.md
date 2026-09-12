# Device-free test harness

Runs the real `MainActivity`, `DiveService`, `WatchChallenge` and the altitude
engine inside Robolectric's JVM Android runtime, so the pre-flight flow, ground
calibration and the watch challenge can be exercised without a phone.

`AppFlowTest` covers:

| Test | What it pins down |
|------|-------------------|
| `groundTrackingStartsAndCalibrates` | START GROUND TRACKING starts the service, calibration completes, nothing is recorded by the crash reporter |
| `calibrationSurvivesAWrongSensorClock` | Regression for v2.2: calibration must finish even when `SensorEvent.timestamp` is on a clock unrelated to `SystemClock.elapsedRealtime` |
| `watchChallengeCarriesAStandardAction` | The PASSED action is a standard notification action (what Samsung's watch bridge forwards), not extender-only |
| `plainTestNotificationPosts` | The bridging self-test posts a plain, action-free notification |
| `expiredChallengeIsRejected` | A stale ack does not verify |
| `watchGateBlocksContinueUntilVerified` | Continue stays disabled until the watch acks |
| `crashReportSurfacesOnHome` | A recorded crash appears on the home screen and can be dismissed |

## Running it

Robolectric normally pulls two AndroidX artifacts from Google's Maven repo. If
that repo is unreachable, `androidx-stub/` holds minimal source-compatible
stand-ins for the 26 classes Robolectric actually touches; build them into
`build/androidx-test-stub.jar` first:

```
mkdir -p build/stub-classes build
javac -d build/stub-classes -cp <android-all jar> $(find androidx-stub -name '*.java')
(cd build/stub-classes && jar cf ../androidx-test-stub.jar .)
mvn test
```

With normal network access, delete the `androidx-test-stub` dependency from
`pom.xml` and the exclusions on the `robolectric` dependency, and `mvn test`
resolves everything itself.
