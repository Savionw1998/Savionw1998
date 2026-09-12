package com.savion.skydivecompanion;

import android.app.*;import android.content.*;import android.os.*;

public class WatchTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        if("com.savion.skydivecompanion.WATCH_TEST".equals(i.getAction())){
            Intent svc=new Intent(c,DiveService.class);svc.setAction("WATCH_TEST_NOTIFICATION");if(Build.VERSION.SDK_INT>=26)c.startForegroundService(svc);else c.startService(svc);
        } else if("com.savion.skydivecompanion.WATCH_ACK".equals(i.getAction())){
            c.getSharedPreferences("watch",0).edit().putBoolean("ok",true).putLong("ack",System.currentTimeMillis()).apply();
            c.getSharedPreferences("SkyDiveCompanion",0).edit().putBoolean("watch_ok",true).apply();
            Intent x=new Intent("com.savion.skydivecompanion.WATCH_VERIFIED");c.sendBroadcast(x);
        }
    }
}
