package com.savion.skydivecompanion;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;

/**
 * Reports what the platform itself says about our notifications, so a failure
 * on a real phone can be read off the screen instead of guessed at.
 */
public final class NotificationDoctor {

    private NotificationDoctor() {}

    /** True once we have proof the phone actually holds the notification. */
    public static boolean isPosted(Context c, int id) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            for (StatusBarNotification sbn : nm.getActiveNotifications()) if (sbn.getId() == id) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static String importanceName(int i) {
        switch (i) {
            case NotificationManager.IMPORTANCE_NONE: return "NONE (blocked)";
            case NotificationManager.IMPORTANCE_MIN: return "MIN";
            case NotificationManager.IMPORTANCE_LOW: return "LOW";
            case NotificationManager.IMPORTANCE_DEFAULT: return "DEFAULT";
            case NotificationManager.IMPORTANCE_HIGH: return "HIGH";
            case NotificationManager.IMPORTANCE_MAX: return "MAX";
            default: return "UNSPECIFIED(" + i + ")";
        }
    }

    /** Human-readable state of every gate between notify() and the watch. */
    public static String report(Context c) {
        StringBuilder b = new StringBuilder();
        NotificationManager nm = c.getSystemService(NotificationManager.class);

        b.append("App version: 2.4 (build 6)\n");
        b.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("  Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");

        boolean permission = Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        b.append("POST_NOTIFICATIONS permission: ").append(permission ? "GRANTED" : "DENIED").append('\n');
        b.append("Notifications enabled for app: ").append(nm.areNotificationsEnabled() ? "YES" : "NO").append('\n');

        NotificationChannel ch = nm.getNotificationChannel(WatchChallenge.CHANNEL);
        if (ch == null) b.append("Watch checks channel: NOT CREATED YET\n");
        else {
            b.append("Watch checks channel: ").append(importanceName(ch.getImportance())).append('\n');
            if (Build.VERSION.SDK_INT >= 28) {
                b.append("Channel blocked by group: ").append(ch.getGroup() != null && nm.getNotificationChannelGroup(ch.getGroup()) != null
                        && nm.getNotificationChannelGroup(ch.getGroup()).isBlocked() ? "YES" : "no").append('\n');
            }
        }

        int filter = nm.getCurrentInterruptionFilter();
        String f;
        switch (filter) {
            case NotificationManager.INTERRUPTION_FILTER_ALL: f = "all (normal)"; break;
            case NotificationManager.INTERRUPTION_FILTER_PRIORITY: f = "priority only (Do Not Disturb)"; break;
            case NotificationManager.INTERRUPTION_FILTER_ALARMS: f = "alarms only (Do Not Disturb)"; break;
            case NotificationManager.INTERRUPTION_FILTER_NONE: f = "none (Do Not Disturb)"; break;
            default: f = "unknown";
        }
        b.append("Do Not Disturb: ").append(f).append('\n');

        b.append("Challenge currently posted: ").append(isPosted(c, WatchChallenge.NOTIFICATION_ID) ? "YES" : "no").append('\n');
        b.append("Plain test currently posted: ").append(isPosted(c, WatchChallenge.PLAIN_TEST_ID) ? "YES" : "no").append('\n');

        String err = Prefs.getString(c, Prefs.LAST_NOTIFY_ERROR, null);
        b.append("Last notify() error: ").append(err == null ? "none" : err).append('\n');

        long sent = Prefs.getLong(c, Prefs.WATCH_LAST_SENT, 0);
        b.append("Last challenge sent: ").append(sent == 0 ? "never"
                : new java.text.SimpleDateFormat("MMM d, h:mm:ss a", java.util.Locale.US).format(new java.util.Date(sent))).append('\n');
        b.append("Watch verified: ").append(WatchChallenge.verifiedLabel(c)).append('\n');
        b.append('\n');
        b.append("If everything above is green but nothing reaches the watch, the\n");
        b.append("phone is posting correctly and the Galaxy Wearable bridge is the\n");
        b.append("blocker. Use the guide button.");
        return b.toString();
    }

    /** One-line verdict for the watch card. */
    public static String oneLine(Context c) {
        String reason = WatchChallenge.blockedReason(c);
        if (reason != null) return "BLOCKED: " + reason;
        if (isPosted(c, WatchChallenge.NOTIFICATION_ID)) return "Posted on phone ✓ — waiting for the watch";
        return "Ready to post";
    }

    /** Best-effort: notify() and remember anything it throws. */
    public static boolean post(Context c, int id, Notification n) {
        try {
            c.getSystemService(NotificationManager.class).notify(id, n);
            Prefs.of(c).edit().remove(Prefs.LAST_NOTIFY_ERROR).apply();
            return true;
        } catch (Throwable t) {
            Prefs.putString(c, Prefs.LAST_NOTIFY_ERROR, t.getClass().getSimpleName() + ": " + t.getMessage());
            CrashReporter.note(c, t, "notify id=" + id);
            return false;
        }
    }
}
