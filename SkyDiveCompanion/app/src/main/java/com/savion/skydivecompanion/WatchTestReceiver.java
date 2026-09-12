package com.savion.skydivecompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Receives the explicit PendingIntent fired by the watch-only notification
 * action. Explicit broadcasts to a manifest receiver are still delivered on
 * Android 8+, unlike the implicit one v2.1 relied on.
 */
public class WatchTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null || !WatchChallenge.ACTION_ACK.equals(i.getAction())) return;
        long nonce = i.getLongExtra(WatchChallenge.EXTRA_NONCE, -1);
        boolean ok = WatchChallenge.acknowledge(c, nonce);
        if (!ok) {
            Toast.makeText(c, "Watch challenge expired. Send a new one from the app.", Toast.LENGTH_LONG).show();
            WatchChallenge.cancel(c);
        }
    }
}
