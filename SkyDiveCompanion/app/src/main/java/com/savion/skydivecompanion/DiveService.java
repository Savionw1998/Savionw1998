package com.savion.skydivecompanion;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Foreground tracking service: barometer + GPS -> AltitudeEngine ->
 * JumpStateMachine -> voice / notifications / status broadcasts / jump log.
 */
public class DiveService extends Service implements SensorEventListener, LocationListener, TextToSpeech.OnInitListener {

    public static final String ACTION_START = "START_TRACKING";
    public static final String ACTION_STOP = "STOP_TRACKING";
    public static final String ACTION_REZERO = "REZERO";
    public static final String ACTION_MARK_EXIT = "MARK_EXIT";
    public static final String ACTION_STATUS = "com.savion.skydivecompanion.STATUS";

    static final String CH_STATUS = "status_v2", CH_ALERT = "alerts_v2";
    static final int NOTE_ID = 701;

    /** Process-local mirror so the activity can reconnect after being recreated. */
    public static volatile boolean running = false;
    public static volatile Bundle lastStatus = null;

    private final AltitudeEngine engine = new AltitudeEngine();
    private final JumpStateMachine sm = new JumpStateMachine();
    private final Handler h = new Handler(Looper.getMainLooper());

    private SensorManager sensors; private Sensor baro, thermo;
    private LocationManager lm; private Location lastLoc;
    private TextToSpeech tts; private boolean ttsReady;
    private AudioManager audio; private PowerManager.WakeLock wake;

    private boolean tracking, voice, duck, hasBaro;
    private long startedMs, lastVoiceMs, lastNoteMs, lastRecordMs, lastStatusMs;
    private String lastNoteText = "";
    private int[] callouts = new int[0]; private double hardDeckFt;
    private final HashSet<Integer> spoken = new HashSet<>();
    private boolean hardDeckSaid, calibratedAnnounced;
    private final ArrayList<String> track = new ArrayList<>();
    private static final int MAX_TRACK = 7200;

    // ------------------------------------------------------------------ lifecycle
    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String action = i == null ? ACTION_START : i.getAction();
        createChannels();
        // Every path through startForegroundService() must reach startForeground() promptly.
        if (!promoteToForeground()) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_STOP.equals(action)) { finishJump(true); return START_NOT_STICKY; }
        if (ACTION_REZERO.equals(action)) { engine.rezero(SystemClock.elapsedRealtimeNanos()); alert("RE-ZEROED", "Ground baseline reset to current pressure.", true); return START_STICKY; }
        if (ACTION_MARK_EXIT.equals(action)) { markExit(); return START_STICKY; }
        startTracking();
        return START_STICKY;
    }

    private boolean promoteToForeground() {
        Notification n = statusNote(tracking ? lastNoteText : "Starting…");
        try {
            boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (Build.VERSION.SDK_INT >= 34) {
                if (!loc) throw new SecurityException("location permission required for a location foreground service");
                startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTE_ID, n, loc ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
            } else {
                startForeground(NOTE_ID, n);
            }
            return true;
        } catch (Exception e) {
            alert("CANNOT START TRACKING", "Grant Location permission (needed for the tracking service on this Android version).", false);
            return false;
        }
    }

    private void startTracking() {
        if (tracking) return;
        tracking = true; running = true;
        startedMs = System.currentTimeMillis();
        spoken.clear(); hardDeckSaid = false; calibratedAnnounced = false; track.clear();

        voice = Prefs.getBool(this, Prefs.VOICE, true);
        duck = Prefs.getBool(this, Prefs.DUCK, true);
        callouts = Prefs.callouts(this);
        hardDeckFt = Prefs.getDouble(this, Prefs.HARD_DECK_FT, 2500);
        engine.setAutoRezero(Prefs.getBool(this, Prefs.AUTO_REZERO, true));
        engine.setGpsCrossCheck(Prefs.getBool(this, Prefs.GPS_CHECK, true));
        engine.setDzElevationFt(Prefs.getDouble(this, Prefs.DZ_ELEV_FT, Double.NaN));
        double tempF = Prefs.getDouble(this, Prefs.GROUND_TEMP_F, Double.NaN);
        if (Double.isNaN(tempF)) engine.clearGroundTemperature(); else engine.setGroundTemperatureC(AltitudeEngine.fahrenheitToCelsius(tempF));
        engine.setGroundMode(true);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SkyDiveCompanion:track");
        wake.acquire(4 * 60 * 60 * 1000L);

        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        baro = sensors.getDefaultSensor(Sensor.TYPE_PRESSURE);
        thermo = sensors.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        hasBaro = baro != null;
        if (hasBaro) {
            sensors.registerListener(this, baro, SensorManager.SENSOR_DELAY_FASTEST);
            engine.setCalibrationDurationMs(8000);
            engine.startCalibration(SystemClock.elapsedRealtimeNanos());
        } else {
            alert("NO BAROMETER", "This phone has no pressure sensor. Only GPS altitude is available.", true);
        }
        if (thermo != null) sensors.registerListener(this, thermo, SensorManager.SENSOR_DELAY_NORMAL);

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, this, Looper.getMainLooper()); } catch (Exception ignored) {}
        }
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        tts = new TextToSpeech(this, this);

        h.removeCallbacks(tick); h.post(tick);
        updateStatusNote("GROUND • calibrating");
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!tracking) return;
            long nowNs = SystemClock.elapsedRealtimeNanos();
            AltitudeEngine.Estimate e = engine.snapshot(nowNs);
            if (e.baselineReady && !calibratedAnnounced) {
                calibratedAnnounced = true;
                alert("CALIBRATED", String.format(Locale.US, "Ground baseline %.1f hPa, sensor noise ±%.0f ft. AGL zeroed.", e.baselineHpa, Math.max(1, e.noiseFt)), true);
            }
            broadcast(e);
            h.postDelayed(this, 250);
        }
    };

    // ------------------------------------------------------------------ sensors
    @Override public void onSensorChanged(SensorEvent ev) {
        if (!tracking) return;
        if (ev.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            if (Double.isNaN(Prefs.getDouble(this, Prefs.GROUND_TEMP_F, Double.NaN)) && sm.phase() == JumpStateMachine.Phase.GROUND)
                engine.setGroundTemperatureC(ev.values[0]);
            return;
        }
        if (ev.sensor.getType() != Sensor.TYPE_PRESSURE) return;
        engine.addPressure(ev.values[0], ev.timestamp);
        AltitudeEngine.Estimate e = engine.snapshot(ev.timestamp);
        if (!e.baselineReady) return;

        long nowMs = System.currentTimeMillis();
        JumpStateMachine.Phase tr = sm.update(e.aglFt, e.verticalSpeedFpm, nowMs);
        if (tr != null) onPhase(tr, e);
        engine.setGroundMode(sm.phase() == JumpStateMachine.Phase.GROUND || sm.phase() == JumpStateMachine.Phase.LANDED);

        if (sm.phase() == JumpStateMachine.Phase.FREEFALL || sm.phase() == JumpStateMachine.Phase.CANOPY) altitudeAlerts(e.aglFt);
        if (nowMs - lastRecordMs >= 500) { lastRecordMs = nowMs; record(nowMs, e); }
    }
    @Override public void onAccuracyChanged(Sensor s, int a) {}

    @Override public void onLocationChanged(Location l) {
        lastLoc = l;
        double alt = l.hasAltitude() ? l.getAltitude() : Double.NaN;
        if (Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude()) alt = l.getMslAltitudeMeters();
        double acc = l.hasVerticalAccuracy() ? l.getVerticalAccuracyMeters() : (l.hasAccuracy() ? l.getAccuracy() * 1.5 : Double.NaN);
        double spd = l.hasSpeed() ? l.getSpeed() : Double.NaN;
        engine.addGps(alt, acc, spd, System.currentTimeMillis());
    }
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}
    @Override public void onStatusChanged(String p, int s, Bundle b) {}

    // ------------------------------------------------------------------ phases & alerts
    private void onPhase(JumpStateMachine.Phase p, AltitudeEngine.Estimate e) {
        switch (p) {
            case AIRCRAFT: alert("AIRCRAFT ASCENT", "Climb detected. Tracking the ride to altitude.", true); break;
            case FREEFALL: alert("EXIT DETECTED", String.format(Locale.US, "Exit at %,.0f ft AGL.", sm.exitAglFt), false); break;
            case CANOPY: alert("CANOPY", String.format(Locale.US, "Canopy at %,.0f feet.", sm.openAglFt), true); break;
            case LANDED:
                alert("LANDED", "Landing detected. Recording for two more minutes, then saving the jump.", true);
                h.postDelayed(() -> finishJump(false), 120_000);
                break;
            default: break;
        }
    }

    private void markExit() {
        AltitudeEngine.Estimate e = engine.snapshot(SystemClock.elapsedRealtimeNanos());
        JumpStateMachine.Phase tr = sm.forceFreefall(e.aglFt, System.currentTimeMillis());
        if (tr != null) { engine.setGroundMode(false); alert("EXIT MARKED", String.format(Locale.US, "Manual exit at %,.0f ft AGL.", e.aglFt), false); }
    }

    private void altitudeAlerts(double agl) {
        for (int b : callouts) {
            if (agl <= b && !spoken.contains(b)) {
                spoken.add(b);
                alert("ALTITUDE", String.format(Locale.US, "%,d feet", b), true);
                break;
            }
        }
        if (!hardDeckSaid && sm.phase() == JumpStateMachine.Phase.FREEFALL && hardDeckFt > 0 && agl <= hardDeckFt) {
            hardDeckSaid = true;
            lastVoiceMs = 0; // never rate-limit the hard deck
            alert("HARD DECK", "Hard deck. Pull. Pull. Pull.", true);
        }
    }

    private int nextCallout(double agl) {
        for (int b : callouts) if (b < agl && !spoken.contains(b)) return b;
        return -1;
    }

    // ------------------------------------------------------------------ notifications
    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH_STATUS) == null) {
            NotificationChannel a = new NotificationChannel(CH_STATUS, "Jump status", NotificationManager.IMPORTANCE_LOW);
            a.setSound(null, null); a.enableVibration(false); a.setShowBadge(false);
            nm.createNotificationChannel(a);
        }
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            NotificationChannel b = new NotificationChannel(CH_ALERT, "Jump alerts", NotificationManager.IMPORTANCE_HIGH);
            b.setDescription("Phase changes, altitude callouts and hard deck");
            b.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(b);
        }
        WatchChallenge.ensureChannel(this);
    }

    private Notification statusNote(String text) {
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, DiveService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CH_STATUS)
                .setContentTitle("SkyDive Companion")
                .setContentText(text)
                .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_notification))
                .setColor(0xFF4BB1FF)
                .setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notification), "STOP / SAVE", stopPi).build())
                .build();
    }
    private void updateStatusNote(String text) {
        long now = System.currentTimeMillis();
        if (text.equals(lastNoteText) || now - lastNoteMs < 1000) return;
        lastNoteText = text; lastNoteMs = now;
        getSystemService(NotificationManager.class).notify(NOTE_ID, statusNote(text));
    }

    private void alert(String title, String text, boolean speak) {
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_notification))
                .setColor(0xFF4BB1FF)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true).setOnlyAlertOnce(true)
                .extend(new Notification.WearableExtender())
                .build();
        getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() % 100000) + 1000, n);
        if (speak && voice) say(text);
    }

    private void say(String text) {
        if (tts == null || !ttsReady) return;
        long now = System.currentTimeMillis();
        if (now - lastVoiceMs < 1200) return;
        lastVoiceMs = now;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        tts.setAudioAttributes(attrs);
        if (duck && audio != null) {
            AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs).setWillPauseWhenDucked(false).build();
            audio.requestAudioFocus(req);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jump" + now);
            h.postDelayed(() -> audio.abandonAudioFocusRequest(req), Math.max(1600, text.length() * 60L));
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jump" + now);
        }
    }
    @Override public void onInit(int status) {
        ttsReady = status == TextToSpeech.SUCCESS;
        if (ttsReady) { tts.setLanguage(Locale.US); tts.setSpeechRate(1.05f); }
    }

    // ------------------------------------------------------------------ status & log
    private void broadcast(AltitudeEngine.Estimate e) {
        Bundle b = new Bundle();
        b.putDouble("agl", e.aglFt); b.putDouble("msl", e.mslFt); b.putDouble("pa", e.pressureAltFt);
        b.putDouble("fpm", e.verticalSpeedFpm); b.putDouble("raw", e.rawHpa); b.putDouble("filtered", e.filteredHpa);
        b.putDouble("baseline", e.baselineHpa); b.putDouble("noise", e.noiseFt); b.putDouble("baseAge", e.baselineAgeSec);
        b.putDouble("gpsAlt", e.gpsAltFt); b.putDouble("gpsAcc", e.gpsAccuracyFt); b.putDouble("gpsSpeed", e.gpsSpeedMph);
        b.putDouble("gpsDelta", e.gpsBaroDeltaFt); b.putDouble("dz", e.dzElevationFt); b.putString("dzSource", e.dzSource);
        b.putBoolean("ready", e.baselineReady); b.putBoolean("calibrating", e.calibrating); b.putDouble("calibProg", e.calibrationProgress);
        b.putInt("conf", e.confidence); b.putBoolean("tempCorr", e.temperatureCorrected); b.putBoolean("hasBaro", hasBaro);
        b.putString("phase", sm.phase().name()); b.putString("timer", elapsed());
        b.putDouble("maxAgl", sm.maxAglFt); b.putInt("nextCallout", nextCallout(e.aglFt));
        b.putDouble("exitAgl", sm.exitAglFt); b.putDouble("openAgl", sm.openAglFt);
        lastStatus = b;
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName()).putExtras(b));

        String note;
        if (!hasBaro) note = "NO BAROMETER • GPS only";
        else if (e.calibrating) note = String.format(Locale.US, "GROUND • calibrating %d%%", (int) (e.calibrationProgress * 100));
        else if (!e.baselineReady) note = "GROUND • waiting for barometer";
        else note = String.format(Locale.US, "%s • AGL %,.0f ft • %+,.0f ft/min", sm.phase().name(), e.aglFt, e.verticalSpeedFpm);
        updateStatusNote(note);
    }

    private String elapsed() {
        long s = Math.max(0, (System.currentTimeMillis() - startedMs) / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private void record(long nowMs, AltitudeEngine.Estimate e) {
        if (track.isEmpty()) track.add("time_ms,agl_ft,msl_ft,pressure_alt_ft,vs_fpm,pressure_hpa,phase,lat,lon,gps_alt_ft,gps_acc_ft");
        String loc = ",,,";
        if (lastLoc != null) loc = String.format(Locale.US, "%.7f,%.7f,%.0f,%.0f", lastLoc.getLatitude(), lastLoc.getLongitude(), e.gpsAltFt, e.gpsAccuracyFt);
        track.add(String.format(Locale.US, "%d,%.0f,%.0f,%.0f,%.0f,%.2f,%s,%s", nowMs, e.aglFt, e.mslFt, e.pressureAltFt, e.verticalSpeedFpm, e.filteredHpa, sm.phase().name(), loc));
        if (track.size() > MAX_TRACK) track.remove(1);
    }

    private void finishJump(boolean manual) {
        if (!tracking) { stopSelf(); return; }
        tracking = false; running = false;
        JumpLog.Summary s = new JumpLog.Summary();
        s.startedMs = startedMs; s.exitAglFt = sm.exitAglFt; s.openAglFt = sm.openAglFt; s.maxAglFt = sm.maxAglFt;
        s.minVsFpm = sm.minVsFpm; s.freefallSec = sm.freefallSeconds(); s.canopySec = sm.canopySeconds(); s.points = Math.max(0, track.size() - 1);
        JumpLog.save(this, s, android.text.TextUtils.join("\n", track));
        alert("JUMP SAVED", (manual ? "Stopped. " : "") + s.headline() + " · " + s.detail(), false);
        stopSelf();
    }

    @Override public void onDestroy() {
        tracking = false; running = false;
        h.removeCallbacksAndMessages(null);
        if (sensors != null) sensors.unregisterListener(this);
        if (lm != null) try { lm.removeUpdates(this); } catch (Exception ignored) {}
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (wake != null && wake.isHeld()) wake.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }

    /** Helper for the activity. */
    public static void start(Context c) {
        Intent i = new Intent(c, DiveService.class).setAction(ACTION_START);
        c.startForegroundService(i);
    }
    public static void send(Context c, String action) {
        if (!running) return;
        c.startForegroundService(new Intent(c, DiveService.class).setAction(action));
    }
}
