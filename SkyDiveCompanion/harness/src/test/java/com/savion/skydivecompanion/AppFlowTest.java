package com.savion.skydivecompanion;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSensor;
import org.robolectric.shadows.ShadowSensorManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AppFlowTest {

    Application app;
    ShadowSensorManager ssm;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        DiveService.running = false;
        DiveService.startFailed = false;
        DiveService.lastStatus = null;
        CrashReporter.clear(app);
        Prefs.of(app).edit().clear().commit();
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS);
        SensorManager sm = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        ssm = shadowOf(sm);
        ssm.addSensor(ShadowSensor.newInstance(Sensor.TYPE_PRESSURE));
    }

    static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out); }
    }
    static List<View> all(View root) { List<View> l = new ArrayList<>(); collect(root, l); return l; }
    static TextView findText(View root, String needle) {
        for (View v : all(root)) if (v instanceof TextView && ((TextView) v).getText() != null
                && ((TextView) v).getText().toString().toUpperCase().contains(needle.toUpperCase())) return (TextView) v;
        return null;
    }
    static void click(View root, String label) {
        TextView t = findText(root, label);
        assertNotNull("button not found: " + label, t);
        assertTrue("button disabled: " + label, t.isEnabled());
        assertTrue("click not handled: " + label, t.performClick());
        shadowOf(Looper.getMainLooper()).idle();
    }
    static void checkAll(View root) {
        for (View v : all(root)) if (v instanceof CheckBox) ((CheckBox) v).setChecked(true);
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Drives the service's barometer input. clockMode picks what SensorEvent.timestamp carries. */
    private void feed(ServiceController<DiveService> sc, int samples, double hPa, double noise, String clockMode) {
        Random rnd = new Random(7);
        SensorEvent ev = ShadowSensorManager.createSensorEvent(1, Sensor.TYPE_PRESSURE);
        for (int i = 0; i < samples; i++) {
            ev.values[0] = (float) (hPa + rnd.nextGaussian() * noise);
            switch (clockMode) {
                case "realtime": ev.timestamp = SystemClock.elapsedRealtimeNanos(); break;
                case "frozen":   ev.timestamp = 12345L; break;                       // Samsung-style wrong clock
                case "future":   ev.timestamp = SystemClock.elapsedRealtimeNanos() + 86_400_000_000_000L; break;
                default:         ev.timestamp = 0L;
            }
            ssm.sendSensorEventToListeners(ev);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
        }
    }

    private ServiceController<DiveService> startService() {
        Intent svc = shadowOf(app).getNextStartedService();
        assertNotNull("service was never started", svc);
        ServiceController<DiveService> sc = Robolectric.buildService(DiveService.class, svc).create().startCommand(0, 1);
        shadowOf(Looper.getMainLooper()).idle();
        return sc;
    }

    private View runPreflight() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        shadowOf(Looper.getMainLooper()).idle();
        click(root, "START PRE-FLIGHT");
        checkAll(root);
        click(root, "I'M DONE");
        checkAll(root);
        return root;
    }

    // ---------------------------------------------------------------- tests

    @Test public void groundTrackingStartsAndCalibrates() {
        Prefs.putBool(app, Prefs.USE_WATCH, false);
        View root = runPreflight();
        click(root, "I'M READY");
        click(root, "START GROUND TRACKING");
        ServiceController<DiveService> sc = startService();
        assertTrue("service should be tracking", DiveService.running);
        assertFalse("start must not have failed", DiveService.startFailed);

        feed(sc, 600, 1005.0, 0.04, "realtime");   // 12 s
        assertFalse("no crash should have been recorded: " + CrashReporter.trace(app), CrashReporter.has(app));
        TextView agl = findText(root, "feet AGL");
        assertNotNull("AGL label missing", agl);
        assertNotNull("calibrated baseline should be published", DiveService.lastStatus);
        assertTrue("baseline should be ready after 12 s", DiveService.lastStatus.getBoolean("ready"));
        assertFalse("calibration should be finished", DiveService.lastStatus.getBoolean("calibrating"));
        sc.destroy();
    }

    /**
     * Regression: several Samsung barometers report SensorEvent.timestamp on a clock
     * unrelated to SystemClock.elapsedRealtime. v2.2 mixed the two, so calibration
     * either never completed or completed with too few samples.
     */
    @Test public void calibrationSurvivesAWrongSensorClock() {
        for (String mode : new String[]{"frozen", "future", "zero"}) {
            DiveService.running = false; DiveService.lastStatus = null;
            Prefs.putBool(app, Prefs.USE_WATCH, false);
            View root = runPreflight();
            click(root, "I'M READY");
            click(root, "START GROUND TRACKING");
            ServiceController<DiveService> sc = startService();
            feed(sc, 600, 1005.0, 0.04, mode);
            assertNotNull("no status broadcast for clock mode " + mode, DiveService.lastStatus);
            assertTrue("baseline never became ready for clock mode " + mode,
                    DiveService.lastStatus.getBoolean("ready"));
            assertFalse("still calibrating for clock mode " + mode,
                    DiveService.lastStatus.getBoolean("calibrating"));
            sc.withIntent(new Intent(app, DiveService.class).setAction(DiveService.ACTION_STOP)).startCommand(0, 2);
            shadowOf(Looper.getMainLooper()).idle();
            sc.destroy();
        }
    }

    @Test public void watchChallengeCarriesAStandardAction() {
        long nonce = WatchChallenge.send(app);
        assertTrue("challenge not posted", nonce > 0);
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        Notification n = shadowOf(nm).getNotification(WatchChallenge.NOTIFICATION_ID);
        assertNotNull(n);

        // The Samsung watch bridge forwards standard actions, not extender-only ones.
        boolean standardPass = false;
        for (Notification.Action a : n.actions) if ("WATCH CHECK PASSED".contentEquals(a.title)) standardPass = true;
        assertTrue("PASSED must be a standard action so the Galaxy Watch bridge forwards it", standardPass);

        List<Notification.Action> wear = new Notification.WearableExtender(n).getActions();
        assertEquals("stock Wear OS copy should also be present", 1, wear.size());
        assertEquals("WATCH CHECK PASSED", wear.get(0).title.toString());
        assertFalse("must not be phone-local", (n.flags & Notification.FLAG_LOCAL_ONLY) != 0);

        try { n.actions[0].actionIntent.send(); } catch (Exception e) { throw new RuntimeException(e); }
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("tapping PASSED must verify", Prefs.watchVerifiedFresh(app));
        assertNull("notification should be cancelled", shadowOf(nm).getNotification(WatchChallenge.NOTIFICATION_ID));
    }

    @Test public void plainTestNotificationPosts() {
        assertTrue(WatchChallenge.sendPlainTest(app));
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        Notification n = shadowOf(nm).getNotification(WatchChallenge.PLAIN_TEST_ID);
        assertNotNull("plain bridging test not posted", n);
        assertEquals(0, n.actions == null ? 0 : n.actions.length);
    }

    @Test public void expiredChallengeIsRejected() {
        long nonce = WatchChallenge.send(app);
        Prefs.putLong(app, Prefs.WATCH_EXPIRES_AT, System.currentTimeMillis() - 1);
        assertFalse(WatchChallenge.acknowledge(app, nonce));
        assertFalse(Prefs.watchVerifiedFresh(app));
    }

    @Test public void watchGateBlocksContinueUntilVerified() {
        Prefs.putBool(app, Prefs.USE_WATCH, true);
        View root = runPreflight();
        TextView cont = findText(root, "I'M READY");
        assertNotNull(cont);
        assertFalse("continue must be disabled until the watch acks", cont.isEnabled());
        click(root, "SEND WATCH CHALLENGE");
        assertNotNull("waiting state should be shown", findText(root, "WAITING FOR WATCH"));
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        Notification n = shadowOf(nm).getNotification(WatchChallenge.NOTIFICATION_ID);
        try { n.actions[0].actionIntent.send(); } catch (Exception e) { throw new RuntimeException(e); }
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("continue should unlock after the ack", findText(root, "I'M READY").isEnabled());
    }

    @Test public void crashReportSurfacesOnHome() {
        CrashReporter.note(app, new IllegalStateException("synthetic"), "unit test");
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View root = a.getWindow().getDecorView();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull("problem report card missing", findText(root, "Problem report"));
        click(root, "DISMISS");
        assertFalse(CrashReporter.has(app));
    }
}
