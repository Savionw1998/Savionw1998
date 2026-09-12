package com.savion.skydivecompanion;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;

/**
 * Galaxy Watch verification challenge.
 *
 * v2.1 sent an implicit broadcast to a manifest receiver, which Android 8+
 * drops, and then tried to start a foreground service that never called
 * startForeground(). Nothing ever reached the watch.
 *
 * v2.2 posts the notification directly from whoever asks (activity or
 * service). The "WATCH CHECK PASSED" action is attached only through the
 * WearableExtender, so it renders on the watch and NOT on the phone: the
 * only way to pass is to tap it on the wrist. Each challenge carries a
 * nonce and expires after 90 s.
 */
public final class WatchChallenge {
    public static final String CHANNEL = "watch_v2";
    public static final int NOTIFICATION_ID = 911;
    public static final String ACTION_ACK = "com.savion.skydivecompanion.WATCH_ACK";
    public static final String ACTION_VERIFIED = "com.savion.skydivecompanion.WATCH_VERIFIED";
    public static final String EXTRA_NONCE = "nonce";
    public static final String EXTRA_SHOW_GUIDE = "show_watch_guide";

    private WatchChallenge() {}

    public static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Watch checks", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Pre-flight watch verification challenge");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 250, 150, 250});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setShowBadge(true);
        nm.createNotificationChannel(ch);
    }

    /** @return the nonce of the challenge that was posted, or -1 when notifications are blocked. */
    public static long send(Context c) {
        ensureChannel(c);
        if (notificationsBlocked(c)) return -1;
        long nonce = System.currentTimeMillis();
        long expires = nonce + Prefs.WATCH_CHALLENGE_MS;
        Prefs.of(c).edit().putLong(Prefs.WATCH_NONCE, nonce).putLong(Prefs.WATCH_EXPIRES_AT, expires)
                .putLong(Prefs.WATCH_LAST_SENT, nonce).apply();

        Intent ack = new Intent(c, WatchTestReceiver.class).setAction(ACTION_ACK).putExtra(EXTRA_NONCE, nonce);
        PendingIntent ackPi = PendingIntent.getBroadcast(c, 911, ack,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 912, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent guide = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_SHOW_GUIDE, true);
        PendingIntent guidePi = PendingIntent.getActivity(c, 913, guide,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Icon icon = Icon.createWithResource(c, R.drawable.ic_notification);
        Icon check = Icon.createWithResource(c, R.drawable.ic_check);

        // The action must be a STANDARD action, not only a WearableExtender one.
        // Samsung's One UI Watch bridge forwards the notification's own action list
        // and drops extender-only actions, which is why v2.2 showed nothing tappable
        // on the watch. It is added to the extender as well for stock Wear OS.
        Notification.Action pass = new Notification.Action.Builder(check, "WATCH CHECK PASSED", ackPi).build();
        Notification.Action passWear = new Notification.Action.Builder(check, "WATCH CHECK PASSED", ackPi).build();
        Notification.Action help = new Notification.Action.Builder(icon, "NOT ON WATCH?", guidePi).build();

        String body = "Look at your Galaxy Watch and tap WATCH CHECK PASSED there. "
                + "Challenge expires in " + (Prefs.WATCH_CHALLENGE_MS / 1000) + " s.";

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setContentTitle("Watch check")
                .setContentText("Tap WATCH CHECK PASSED on your watch")
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(icon)
                .setColor(0xFF4BB1FF)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setLocalOnly(false)
                .setContentIntent(openPi)
                .setWhen(nonce)
                .setShowWhen(true)
                .addAction(pass)
                .addAction(help)
                .extend(new Notification.WearableExtender()
                        .addAction(passWear)
                        .setContentIntentAvailableOffline(true)
                        .setHintContentIntentLaunchesActivity(true));
        if (!NotificationDoctor.post(c, NOTIFICATION_ID, b.build())) return -1;
        return nonce;
    }

    public static final int PLAIN_TEST_ID = 915;

    /**
     * Dead-simple notification with no actions and no extender. If this one does
     * not reach the watch, the problem is Galaxy Wearable's notification settings
     * rather than anything about the challenge itself.
     */
    public static boolean sendPlainTest(Context c) {
        ensureChannel(c);
        if (notificationsBlocked(c)) return false;
        Notification n = new Notification.Builder(c, CHANNEL)
                .setContentTitle("SkyDive Companion test")
                .setContentText("If you can read this on your watch, bridging works.")
                .setSmallIcon(Icon.createWithResource(c, R.drawable.ic_notification))
                .setColor(0xFF4BB1FF)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setAutoCancel(true)
                .build();
        return NotificationDoctor.post(c, PLAIN_TEST_ID, n);
    }

    public static void cancel(Context c) {
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        Prefs.of(c).edit().remove(Prefs.WATCH_EXPIRES_AT).apply();
    }

    /** Called by the receiver. Validates nonce and expiry. */
    public static boolean acknowledge(Context c, long nonce) {
        long expected = Prefs.getLong(c, Prefs.WATCH_NONCE, -1);
        long expires = Prefs.getLong(c, Prefs.WATCH_EXPIRES_AT, 0);
        long now = System.currentTimeMillis();
        if (nonce != expected || now > expires) return false;
        Prefs.of(c).edit().putLong(Prefs.WATCH_VERIFIED_AT, now).remove(Prefs.WATCH_EXPIRES_AT).apply();
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        c.sendBroadcast(new Intent(ACTION_VERIFIED).setPackage(c.getPackageName()));
        return true;
    }

    /** Why notifications cannot reach the watch right now, or null when they can. */
    public static String blockedReason(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return "Notification permission not granted";
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) return "Notifications are turned off for this app";
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL);
        if (ch != null && ch.getImportance() == NotificationManager.IMPORTANCE_NONE) return "The Watch checks channel is blocked";
        if (ch != null && ch.getImportance() < NotificationManager.IMPORTANCE_DEFAULT) return "Watch checks channel importance is too low to bridge";
        return null;
    }
    public static boolean notificationsBlocked(Context c) { return blockedReason(c) != null; }

    /** Deep link to this app's notification settings (channel page when the channel exists). */
    public static Intent settingsIntent(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        Intent i;
        if (nm.getNotificationChannel(CHANNEL) != null) {
            i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL);
        } else {
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
        }
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static String verifiedLabel(Context c) {
        long at = Prefs.getLong(c, Prefs.WATCH_VERIFIED_AT, 0);
        if (at <= 0) return "NOT VERIFIED";
        String time = new java.text.SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date(at));
        return Prefs.watchVerifiedFresh(c) ? "VERIFIED " + time : "STALE (verified " + time + ")";
    }

    public static final String GUIDE =
            "If the challenge never shows on the watch:\n"
            + "1. Galaxy Wearable app → Watch settings → Notifications.\n"
            + "2. App notifications → turn SkyDive Companion ON.\n"
            + "3. Turn ON \"Show while phone in use\" (otherwise the watch only gets it while the phone screen is off).\n"
            + "4. Make sure the watch is connected (Bluetooth) and not in Do Not Disturb / Bedtime mode.\n"
            + "5. On the phone, Watch checks channel must be allowed with High importance (use the button above).\n"
            + "Tip: send the challenge, then lock the phone and put it in your pocket. It stays valid for 90 s.";
}
