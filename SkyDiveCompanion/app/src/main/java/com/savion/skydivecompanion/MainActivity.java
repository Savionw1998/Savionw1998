package com.savion.skydivecompanion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, content;
    TextView status, phase, alt, agl, vs, pressure, timer, watchStatus;
    Button primary;
    final int BG=Color.rgb(8,12,18), CARD=Color.rgb(19,26,35), CARD2=Color.rgb(24,33,44), TEXT=Color.WHITE, MUTED=Color.rgb(155,170,188), ACCENT=Color.rgb(75,177,255), GREEN=Color.rgb(71,211,148), RED=Color.rgb(255,105,105);
    final ArrayList<CheckBox> checks=new ArrayList<>();
    int stage=0;
    long started;

    int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,float sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(TEXT);return t;}
    TextView label(String s){TextView t=tv(s.toUpperCase(Locale.US),11);t.setTextColor(MUTED);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(dp(2),dp(3),dp(2),dp(5));return t;}
    GradientDrawable bg(int color,float r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;}
    LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(15),dp(16),dp(15));l.setBackground(bg(CARD,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(12));l.setLayoutParams(p);return l;}
    Button btn(String s,boolean primaryStyle){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(TEXT);b.setMinHeight(dp(54));b.setPadding(dp(16),0,dp(16),0);b.setBackground(bg(primaryStyle?ACCENT:CARD2,16));return b;}

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);buildShell();
        if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.ACCESS_FINE_LOCATION},44); else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},44);
        showHome();
    }

    void buildShell(){
        ScrollView sc=new ScrollView(this);sc.setClipToPadding(false);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(28),dp(16),dp(30));root.setClipToPadding(false);root.setBackgroundColor(BG);sc.addView(root);setContentView(sc);
        TextView h=tv("SKYDIVE COMPANION",24);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(h);
        TextView sub=tv("Preflight • jump tracking • logbook",13);sub.setTextColor(MUTED);root.addView(sub,new LinearLayout.LayoutParams(-1,-2));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(18);root.addView(content,cp);
    }

    void clear(){content.removeAllViews();checks.clear();}
    TextView title(String s,String sub){TextView t=tv(s,25);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);content.addView(t);TextView x=tv(sub,14);x.setTextColor(MUTED);x.setPadding(0,dp(5),0,dp(16));content.addView(x);return t;}

    void showHome(){stage=0;clear();title("Ready for the next jump","One-button workflow. The phone stays in your pocket while voice alerts and notifications do the work.");
        LinearLayout c=card();
        c.addView(label("Session"));phase=tv("STANDBY",20);phase.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(phase);status=tv("Nothing is tracking yet.",13);status.setTextColor(MUTED);c.addView(status);content.addView(c);
        primary=btn("START PRE-FLIGHT",true);content.addView(primary);primary.setOnClickListener(v->showGearChecklist());
        LinearLayout info=card();info.addView(label("What happens next"));info.addView(tv("1. Gear checks before the parachute goes on\n2. Parachute-on checks\n3. Watch/audio verification\n4. Start ground tracking before boarding\n5. Automatic altitude, GPS and jump-state tracking",14));content.addView(info);
        LinearLayout safety=card();safety.addView(label("Instrument status"));safety.addView(tv("Certified altimeter, audible/AAD and drop-zone procedures remain primary. This app is a recording/awareness companion.",13));content.addView(safety);
    }

    void showGearChecklist(){stage=1;clear();title("Gear check","Complete these before putting the parachute on. Nothing is tracked yet.");
        addCheck("AAD is ON and showing the expected status");
        addCheck("I completed my gear inspection");
        addCheck("I checked the landing area / landing pattern");
        addCheck("My phone is secured and ready");
        addCheck("My jump plan / exit plan is understood");
        Button cont=btn("I'M DONE — CONTINUE",true);content.addView(cont);cont.setOnClickListener(v->{if(allChecked())showParachuteChecklist();else remind();});
    }

    void showParachuteChecklist(){stage=2;clear();title("Parachute on","Now verify the equipment that goes on or gets checked after the rig is fitted.");
        addCheck("Chest strap is secured");addCheck("Leg straps are secured");addCheck("Helmet is on and chin strap secured");addCheck("Goggles are ready / secured");addCheck("Altimeter is on and readable");
        LinearLayout watch=card();watch.addView(label("Galaxy Watch"));watchStatus=tv("Optional — watch use is OFF",14);watchStatus.setTextColor(MUTED);watch.addView(watchStatus);Switch sw=new Switch(this);sw.setText("Use Galaxy Watch");sw.setTextColor(TEXT);sw.setChecked(true);watch.addView(sw);Button test=btn("SEND WATCH TEST",false);watch.addView(test);test.setOnClickListener(v->sendWatchTest());sw.setOnCheckedChangeListener((b,on)->{watchStatus.setText(on?"Watch verification enabled":"Optional — watch use is OFF");test.setEnabled(on);if(!on)getSharedPreferences("SkyDiveCompanion",0).edit().putBoolean("watch_ok",false).apply();});content.addView(watch);
        Button cont=btn("I'M READY — CONTINUE",true);content.addView(cont);cont.setOnClickListener(v->{if(allChecked() && (!sw.isChecked() || getSharedPreferences("SkyDiveCompanion",0).getBoolean("watch_ok",false)))showBoardingStart(sw.isChecked());else remind();});
    }

    void showBoardingStart(boolean useWatch){stage=3;clear();title("Ready to board","This is where live ground tracking begins. Start it when you are under the tent / moving toward the aircraft.");
        LinearLayout c=card();c.addView(label("Watch verification"));TextView w=tv(useWatch?(getSharedPreferences("SkyDiveCompanion",0).getBoolean("watch_ok",false)?"WATCH VERIFIED":"WATCH TEST REQUIRED"):"WATCH NOT USED",16);w.setTypeface(Typeface.DEFAULT,Typeface.BOLD);w.setTextColor(useWatch&&getSharedPreferences("SkyDiveCompanion",0).getBoolean("watch_ok",false)?GREEN:(useWatch?RED:MUTED));c.addView(w);content.addView(c);
        primary=btn("START GROUND TRACKING",true);content.addView(primary);primary.setOnClickListener(v->startTracking());
        Button back=btn("BACK",false);content.addView(back);back.setOnClickListener(v->showParachuteChecklist());
    }

    void startTracking(){started=System.currentTimeMillis();Intent i=new Intent(this,DiveService.class);i.setAction("START_TRACKING");i.putExtra("voice",true);i.putExtra("duck",true);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);showLive();}

    void showLive(){stage=4;clear();title("Live jump computer","Keep the phone secured. This screen is optional; voice alerts and notifications are the primary interface while jumping.");
        LinearLayout dash=card();dash.addView(label("REAL-TIME ALTITUDE"));alt=tv("— ft",36);alt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);dash.addView(alt);agl=tv("AGL  — ft",18);agl.setTextColor(ACCENT);dash.addView(agl);vs=tv("Vertical  — ft/min",14);vs.setTextColor(MUTED);dash.addView(vs);pressure=tv("Pressure  — hPa",12);pressure.setTextColor(MUTED);dash.addView(pressure);phase=tv("PRE-JUMP",18);phase.setTypeface(Typeface.DEFAULT,Typeface.BOLD);dash.addView(phase);timer=tv("00:00",12);timer.setTextColor(MUTED);dash.addView(timer);content.addView(dash);
        LinearLayout diag=card();diag.addView(label("Barometer diagnostics"));diag.addView(tv("Ground calibration uses an averaged pressure baseline. hPa is atmospheric pressure; ~1013 hPa is a common reference value, while local pressure can be around 1000–1030 hPa. The app converts pressure change into feet using the measured baseline.",12));content.addView(diag);
        Button stop=btn("STOP / SAVE JUMP",false);content.addView(stop);stop.setOnClickListener(v->{stopService(new Intent(this,DiveService.class));showHome();});
        registerReceiver(updateReceiver,new IntentFilter("com.savion.skydivecompanion.STATUS"));
    }

    final BroadcastReceiver updateReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(stage!=4)return;alt.setText(String.format(Locale.US,"%.0f ft",i.getDoubleExtra("alt",0)));agl.setText(String.format(Locale.US,"AGL  %.0f ft",i.getDoubleExtra("agl",0)));vs.setText(String.format(Locale.US,"Vertical  %.0f ft/min",i.getDoubleExtra("fpm",0)));pressure.setText(String.format(Locale.US,"Pressure  %.2f hPa",i.getDoubleExtra("pressure",0)));phase.setText(i.getStringExtra("state"));timer.setText(i.getStringExtra("timer"));}};

    void addCheck(String text){CheckBox cb=new CheckBox(this);cb.setText(text);cb.setTextColor(TEXT);cb.setTextSize(15);cb.setMinHeight(dp(52));cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));content.addView(cb);checks.add(cb);}
    boolean allChecked(){for(CheckBox c:checks)if(!c.isChecked())return false;return true;}
    void remind(){Toast.makeText(this,"Complete every required check before continuing.",Toast.LENGTH_SHORT).show();}
    void sendWatchTest(){Intent x=new Intent("com.savion.skydivecompanion.WATCH_TEST");sendBroadcast(x);Toast.makeText(this,"Watch test notification sent. Tap the action on the Watch if it appears.",Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){super.onDestroy();try{unregisterReceiver(updateReceiver);}catch(Exception ignored){}}
}
