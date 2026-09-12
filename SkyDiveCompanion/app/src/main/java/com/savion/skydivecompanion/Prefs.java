package com.savion.skydivecompanion;

import android.content.Context;
import android.content.SharedPreferences;

/** All persisted settings and the keys the service and UI share. */
public final class Prefs {
    public static final String FILE = "SkyDiveCompanion";

    public static final String DZ_ELEV_FT = "dz_elev_ft";
    public static final String GROUND_TEMP_F = "ground_temp_f";
    public static final String AUTO_REZERO = "auto_rezero";
    public static final String GPS_CHECK = "gps_check";
    public static final String VOICE = "voice";
    public static final String DUCK = "duck";
    public static final String CALLOUTS = "callouts";
    public static final String HARD_DECK_FT = "hard_deck_ft";
    public static final String USE_WATCH = "use_watch";
    public static final String WATCH_VERIFIED_AT = "watch_verified_at";
    public static final String WATCH_NONCE = "watch_nonce";
    public static final String WATCH_EXPIRES_AT = "watch_expires_at";
    public static final String WATCH_LAST_SENT = "watch_last_sent";

    public static final String DEFAULT_CALLOUTS = "10000,9000,8000,7000,6000,5000,4000,3000,2000,1500,1000,500";
    public static final long WATCH_FRESH_MS = 6L * 60 * 60 * 1000;      // 6 h
    public static final long WATCH_CHALLENGE_MS = 90_000L;              // 90 s

    private Prefs() {}

    public static SharedPreferences of(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static double getDouble(Context c, String key, double def) {
        String s = of(c).getString(key, null);
        if (s == null || s.isEmpty()) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
    public static void putDouble(Context c, String key, double v) {
        if (Double.isNaN(v)) of(c).edit().remove(key).apply(); else of(c).edit().putString(key, String.valueOf(v)).apply();
    }
    public static boolean getBool(Context c, String key, boolean def) { return of(c).getBoolean(key, def); }
    public static void putBool(Context c, String key, boolean v) { of(c).edit().putBoolean(key, v).apply(); }
    public static long getLong(Context c, String key, long def) { return of(c).getLong(key, def); }
    public static void putLong(Context c, String key, long v) { of(c).edit().putLong(key, v).apply(); }
    public static String getString(Context c, String key, String def) { return of(c).getString(key, def); }
    public static void putString(Context c, String key, String v) { of(c).edit().putString(key, v).apply(); }

    public static int[] callouts(Context c) {
        String s = getString(c, CALLOUTS, DEFAULT_CALLOUTS);
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (String part : s.split("[,\\s]+")) {
            try { int v = Integer.parseInt(part.trim()); if (v > 0) out.add(v); } catch (NumberFormatException ignored) {}
        }
        java.util.Collections.sort(out, java.util.Collections.reverseOrder());
        int[] a = new int[out.size()]; for (int i = 0; i < a.length; i++) a[i] = out.get(i); return a;
    }

    public static boolean watchVerifiedFresh(Context c) {
        long at = getLong(c, WATCH_VERIFIED_AT, 0);
        return at > 0 && System.currentTimeMillis() - at < WATCH_FRESH_MS;
    }
    public static boolean watchChallengePending(Context c) {
        return getLong(c, WATCH_EXPIRES_AT, 0) > System.currentTimeMillis();
    }
}
