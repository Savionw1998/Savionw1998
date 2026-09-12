package com.savion.skydivecompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Captures any uncaught exception to SharedPreferences so the next launch can
 * show the exact stack trace on screen. Without this, a crash on a sideloaded
 * build is invisible unless the phone is attached to a computer.
 */
public final class CrashReporter {
    private static final String FILE = "crash";
    private static final String KEY_TRACE = "trace";
    private static final String KEY_WHEN = "when";
    private static final String KEY_NOTE = "note";

    private CrashReporter() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static void install(final Context app) {
        final Thread.UncaughtExceptionHandler prior = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { save(app, error, "uncaught on thread " + thread.getName()); } catch (Throwable ignored) {}
            if (prior != null) prior.uncaughtException(thread, error);
        });
    }

    /** Record a caught problem that did not kill the process. */
    public static void note(Context c, Throwable t, String note) {
        try { save(c, t, note); } catch (Throwable ignored) {}
    }

    private static void save(Context c, Throwable t, String note) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("SkyDive Companion 2.4 (versionCode 6)");
        pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL + "  Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("When: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("Note: " + note);
        pw.println();
        t.printStackTrace(pw);
        pw.flush();
        sp(c).edit().putString(KEY_TRACE, sw.toString()).putLong(KEY_WHEN, System.currentTimeMillis()).putString(KEY_NOTE, note).apply();
    }

    public static boolean has(Context c) { return sp(c).getString(KEY_TRACE, null) != null; }
    public static String trace(Context c) { return sp(c).getString(KEY_TRACE, ""); }
    public static String note(Context c) { return sp(c).getString(KEY_NOTE, ""); }
    public static long when(Context c) { return sp(c).getLong(KEY_WHEN, 0); }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}
