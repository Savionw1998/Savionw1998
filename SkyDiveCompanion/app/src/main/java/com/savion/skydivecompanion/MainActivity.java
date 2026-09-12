package com.savion.skydivecompanion;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    enum Screen { HOME, GEAR, CHUTE, BOARD, LIVE, LOGBOOK, SETTINGS }

    /** Shown on the home screen so the installed build is never in doubt. */
    static final String VERSION = "v2.4 (build 6)";

    static final String[] GEAR_ITEMS = {
            "AAD is ON and showing the expected status",
            "Gear inspection complete (3-ring, closing loop, pin, handles)",
            "Landing area and landing pattern checked",
            "Phone secured and ready (pocket zipped)",
            "Jump plan / exit plan understood"};
    static final String[] CHUTE_ITEMS = {
            "Chest strap secured and routed correctly",
            "Leg straps secured",
            "Helmet on, chin strap secured",
            "Goggles ready / secured",
            "Altimeter on, zeroed and readable"};

    private Ui ui;
    private LinearLayout content, stepperHost;
    private Screen screen = Screen.HOME;
    private final boolean[] gearDone = new boolean[GEAR_ITEMS.length];
    private final boolean[] chuteDone = new boolean[CHUTE_ITEMS.length];
    private final ArrayList<CheckBox> checks = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());

    // live widgets
    private TextView liveAgl, livePhasePill, liveTimer, liveVs, liveBaseline, liveConf, liveCalib;
    private LinearLayout liveHero, liveDots, liveDotsHost, liveCalibBar, liveCalibHost;
    private View liveCalibFill, liveCalibRest;
    private LinearLayout rawHz, rawNow, rawBase, rawDelta, rawAgl;
    private Ui.Tile tMsl, tGps, tPress, tMax, tSpeed, tNext;
    private Button btnRezero, btnMark;
    private int heroTint = 0;

    // watch widgets
    private TextView watchPill, watchHelp, watchNotif;
    private Button watchSend, watchFix, chuteContinue;
    private Switch watchSwitch;

    // ------------------------------------------------------------------ lifecycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        buildShell();
        requestStartupPermissions();
        if (DiveService.running) showLive(); else showHome();
        handleIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleIntent(i); }
    private void handleIntent(Intent i) {
        if (i != null && i.getBooleanExtra(WatchChallenge.EXTRA_SHOW_GUIDE, false)) showGuideDialog();
    }

    private void requestStartupPermissions() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 44);
    }
    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { super.onRequestPermissionsResult(code, p, r); refresh(); }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(); f.addAction(DiveService.ACTION_STATUS); f.addAction(WatchChallenge.ACTION_VERIFIED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver, f);
        refresh();
    }
    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
        h.removeCallbacksAndMessages(null);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (DiveService.ACTION_STATUS.equals(i.getAction())) { if (screen == Screen.LIVE) applyStatus(i.getExtras()); }
            else if (WatchChallenge.ACTION_VERIFIED.equals(i.getAction())) {
                Toast.makeText(MainActivity.this, "Watch verified ✓", Toast.LENGTH_SHORT).show();
                if (screen == Screen.CHUTE) updateWatchCard(); else if (screen == Screen.BOARD || screen == Screen.HOME) refresh();
            }
        }
    };

    @Override public void onBackPressed() {
        switch (screen) {
            case GEAR: case LOGBOOK: case SETTINGS: showHome(); break;
            case CHUTE: showGear(); break;
            case BOARD: showChute(); break;
            case LIVE: moveTaskToBack(true); break;
            default: super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ shell
    private void buildShell() {
        ScrollView sc = new ScrollView(this); sc.setClipToPadding(false); sc.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(16), ui.dp(26), ui.dp(16), ui.dp(30)); root.setClipToPadding(false);
        sc.addView(root); setContentView(sc);

        LinearLayout head = ui.row();
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        TextView hTitle = ui.text("SKYDIVE COMPANION", 22, Ui.TEXT, true); hTitle.setLetterSpacing(0.04f); titles.addView(hTitle);
        titles.addView(ui.text("Preflight • jump tracking • logbook", 13, Ui.MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        Button gear = ui.ghost("⚙︎"); gear.setTextSize(22); gear.setMinWidth(ui.dp(44)); gear.setPadding(0, 0, 0, 0);
        gear.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48), ui.dp(44))); gear.setOnClickListener(v -> showSettings());
        head.addView(gear);
        root.addView(head);

        stepperHost = new LinearLayout(this); stepperHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP); sp.topMargin = ui.dp(16); root.addView(stepperHost, sp);

        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
    }
    private void begin(Screen s, int step, String stepTitle, String title, String subtitle) {
        screen = s; content.removeAllViews(); checks.clear(); stepperHost.removeAllViews(); h.removeCallbacksAndMessages(null);
        if (step > 0) stepperHost.addView(ui.stepper(step, 5, stepTitle));
        content.addView(ui.title(title));
        if (subtitle != null) content.addView(ui.subtitle(subtitle));
    }
    private void refresh() {
        switch (screen) {
            case HOME: showHome(); break;
            case GEAR: showGear(); break;
            case CHUTE: showChute(); break;
            case BOARD: showBoard(); break;
            case LIVE: if (!DiveService.running) showHome(); break;
            case LOGBOOK: showLogbook(); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ home
    private void showHome() {
        begin(Screen.HOME, 0, null, "Ready for the next jump", "One button. The phone stays pocketed while voice alerts and notifications do the work.");
        boolean live = DiveService.running;
        addDiagnosticsCard();

        LinearLayout c = ui.card(); c.addView(ui.label("Session"));
        LinearLayout r = ui.row(); r.addView(ui.pill(live ? "TRACKING" : "STANDBY", live ? Ui.GREEN : Ui.MUTED)); c.addView(r);
        TextView s = ui.helper(live ? "Ground tracking is running in the background." : "Nothing is tracking yet."); s.setPadding(0, ui.dp(8), 0, 0); c.addView(s);
        c.addView(ui.divider());
        boolean baro = ((SensorManager) getSystemService(SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_PRESSURE) != null;
        boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        String notifReason = WatchChallenge.blockedReason(this);
        c.addView(ui.kv("Barometer", baro ? "✓ present" : "✗ missing", baro ? Ui.GREEN : Ui.RED));
        c.addView(ui.kv("Location", loc ? "✓ granted" : "✗ not granted", loc ? Ui.GREEN : Ui.RED));
        c.addView(ui.kv("Notifications", notifReason == null ? "✓ allowed" : "✗ blocked", notifReason == null ? Ui.GREEN : Ui.RED));
        boolean useWatch = Prefs.getBool(this, Prefs.USE_WATCH, true);
        String wl = !useWatch ? "— not used" : WatchChallenge.verifiedLabel(this);
        c.addView(ui.kv("Galaxy Watch", wl, !useWatch ? Ui.MUTED : (Prefs.watchVerifiedFresh(this) ? Ui.GREEN : Ui.AMBER)));
        content.addView(c);

        if (live) { Button b = ui.primary("OPEN LIVE JUMP COMPUTER"); b.setOnClickListener(v -> showLive()); content.addView(b); }
        else { Button b = ui.primary("START PRE-FLIGHT"); b.setOnClickListener(v -> { java.util.Arrays.fill(gearDone, false); java.util.Arrays.fill(chuteDone, false); showGear(); }); content.addView(b); }

        JumpLog.Summary last = JumpLog.last(this);
        LinearLayout lj = ui.card(); lj.addView(ui.label("Last jump"));
        if (last == null) lj.addView(ui.helper("No jumps recorded yet. Tracked jumps land here automatically."));
        else { lj.addView(ui.body(last.date() + " · " + last.headline())); TextView d = ui.helper(last.detail()); d.setPadding(0, ui.dp(3), 0, 0); lj.addView(d); }
        Button open = ui.secondary("OPEN LOGBOOK"); open.setLayoutParams(ui.block(0)); ((LinearLayout.LayoutParams) open.getLayoutParams()).topMargin = ui.dp(12);
        open.setOnClickListener(v -> showLogbook()); lj.addView(open);
        content.addView(lj);

        LinearLayout info = ui.card(); info.addView(ui.label("What happens next"));
        info.addView(ui.numbered(1, "Gear checks before the parachute goes on", false));
        info.addView(ui.numbered(2, "Parachute-on checks", false));
        info.addView(ui.numbered(3, "Watch verification (optional)", false));
        info.addView(ui.numbered(4, "Start ground tracking before boarding", false));
        info.addView(ui.numbered(5, "Automatic altitude, GPS and jump-phase tracking", true));
        content.addView(info);

        LinearLayout safety = ui.card(); safety.addView(ui.label("Instrument status"));
        safety.addView(ui.helper("Certified altimeter, audible/AAD and drop-zone procedures remain primary. This app is a recording and awareness companion; a phone barometer is not a certified altimeter."));
        content.addView(safety);

        TextView ver = ui.helper("SkyDive Companion " + VERSION);
        ver.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        ver.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(ver);
    }

    /** Shows the last crash or failed start, with a one-tap way to send it. */
    private void addDiagnosticsCard() {
        if (!CrashReporter.has(this) && !DiveService.startFailed) return;
        LinearLayout c = ui.card();
        c.setBackground(ui.shape(Ui.alpha(Ui.RED, 0x14), 18, Ui.alpha(Ui.RED, 0x88)));
        TextView lab = ui.label("Problem report");
        lab.setTextColor(Ui.RED);
        c.addView(lab);
        String when = CrashReporter.when(this) > 0
                ? new java.text.SimpleDateFormat("MMM d, h:mm a", Locale.US).format(new java.util.Date(CrashReporter.when(this)))
                : "just now";
        c.addView(ui.body(DiveService.startFailed ? "Tracking could not start." : "The app stopped unexpectedly."));
        TextView sub = ui.helper(when + " \u00b7 " + CrashReporter.note(this));
        sub.setPadding(0, ui.dp(4), 0, 0);
        c.addView(sub);

        final TextView detail = ui.helper(CrashReporter.trace(this));
        detail.setTextSize(10);
        detail.setVisibility(View.GONE);
        detail.setPadding(0, ui.dp(10), 0, 0);
        detail.setTextIsSelectable(true);
        c.addView(detail);

        Button show = ui.ghost("Show details");
        show.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        show.setLayoutParams(ui.block(0));
        show.setOnClickListener(v -> {
            boolean vis = detail.getVisibility() == View.VISIBLE;
            detail.setVisibility(vis ? View.GONE : View.VISIBLE);
            show.setText(vis ? "Show details" : "Hide details");
        });
        c.addView(show);

        Button send = ui.secondary("SEND REPORT");
        send.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "SkyDive Companion problem report")
                    .putExtra(Intent.EXTRA_TEXT, CrashReporter.trace(this));
            startActivity(Intent.createChooser(i, "Send report"));
        });
        Button dismiss = ui.secondary("DISMISS");
        dismiss.setOnClickListener(v -> { CrashReporter.clear(this); DiveService.startFailed = false; showHome(); });
        LinearLayout pr = ui.pair(send, dismiss);
        ((LinearLayout.LayoutParams) pr.getLayoutParams()).topMargin = ui.dp(8);
        c.addView(pr);
        content.addView(c);
    }

    // ------------------------------------------------------------------ checklists
    private LinearLayout checklist(String[] items, boolean[] state, TextView progress, Runnable onChange) {
        LinearLayout c = ui.card(); c.setPadding(ui.dp(6), ui.dp(2), ui.dp(10), ui.dp(2));
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            CheckBox cb = ui.check(items[i], state[i], (b, on) -> { state[idx] = on; onChange.run(); });
            c.addView(cb); checks.add(cb);
            if (i < items.length - 1) { View d = ui.divider(); ((LinearLayout.LayoutParams) d.getLayoutParams()).setMargins(ui.dp(44), 0, 0, 0); c.addView(d); }
        }
        return c;
    }
    private static int count(boolean[] a) { int n = 0; for (boolean b : a) if (b) n++; return n; }
    private static boolean all(boolean[] a) { return count(a) == a.length; }

    private void showGear() {
        begin(Screen.GEAR, 1, "Gear check", "Gear check", "Complete these before putting the parachute on. Nothing is tracked yet.");
        TextView progress = ui.helper(""); progress.setPadding(ui.dp(4), 0, 0, ui.dp(10));
        Button cont = ui.primary("I'M DONE — CONTINUE");
        Runnable upd = () -> { int n = count(gearDone); progress.setText(n + " of " + gearDone.length + " complete"); progress.setTextColor(n == gearDone.length ? Ui.GREEN : Ui.MUTED); Ui.enable(cont, all(gearDone)); };
        content.addView(checklist(GEAR_ITEMS, gearDone, progress, upd));
        content.addView(progress); content.addView(cont); upd.run();
        cont.setOnClickListener(v -> showChute());
        Button back = ui.secondary("BACK"); back.setOnClickListener(v -> showHome()); content.addView(back);
    }

    private void showChute() {
        begin(Screen.CHUTE, 2, "Parachute on", "Parachute on", "Verify everything that goes on or gets checked after the rig is fitted.");
        TextView progress = ui.helper(""); progress.setPadding(ui.dp(4), 0, 0, ui.dp(10));
        chuteContinue = ui.primary("I'M READY — CONTINUE");
        Runnable upd = () -> { int n = count(chuteDone); progress.setText(n + " of " + chuteDone.length + " complete"); progress.setTextColor(n == chuteDone.length ? Ui.GREEN : Ui.MUTED); updateWatchCard(); };
        content.addView(checklist(CHUTE_ITEMS, chuteDone, progress, upd));
        content.addView(progress);

        // ---- Galaxy Watch card
        boolean useWatch = Prefs.getBool(this, Prefs.USE_WATCH, true);
        LinearLayout w = ui.card();
        LinearLayout head = ui.row();
        TextView lab = ui.label("Galaxy Watch"); lab.setPadding(0, 0, 0, 0); head.addView(lab, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        LinearLayout tg = ui.toggle("", useWatch, (b, on) -> { Prefs.putBool(this, Prefs.USE_WATCH, on); if (!on) WatchChallenge.cancel(this); updateWatchCard(); });
        watchSwitch = (Switch) tg.getTag(); tg.removeView(watchSwitch); head.addView(watchSwitch); w.addView(head);
        LinearLayout pr = ui.row(); pr.setPadding(0, ui.dp(10), 0, ui.dp(8)); watchPill = ui.pill("", Ui.MUTED); pr.addView(watchPill); w.addView(pr);
        watchHelp = ui.helper(""); w.addView(watchHelp);
        watchSend = ui.secondary("SEND WATCH CHALLENGE"); ((LinearLayout.LayoutParams) watchSend.getLayoutParams()).topMargin = ui.dp(12);
        watchSend.setOnClickListener(v -> sendChallenge()); w.addView(watchSend);
        Button plain = ui.secondary("SEND PLAIN TEST");
        plain.setOnClickListener(v -> {
            if (WatchChallenge.sendPlainTest(this))
                Toast.makeText(this, "Plain notification sent. If this one does not reach the watch, the Galaxy Wearable bridge is off.", Toast.LENGTH_LONG).show();
            else { Toast.makeText(this, "Notifications are blocked on the phone.", Toast.LENGTH_LONG).show(); openNotificationSettings(); }
        });
        w.addView(plain);
        LinearLayout nr = ui.row(); watchNotif = ui.helper(""); nr.addView(watchNotif, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        watchFix = ui.ghost("FIX"); watchFix.setLayoutParams(new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP)); watchFix.setOnClickListener(v -> openNotificationSettings()); nr.addView(watchFix); w.addView(nr);
        Button diag = ui.ghost("Notification diagnostics");
        diag.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); diag.setLayoutParams(ui.block(0));
        diag.setOnClickListener(v -> showDiagnosticsDialog()); w.addView(diag);
        Button guide = ui.ghost("Watch not showing it?  Open the guide"); guide.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); guide.setLayoutParams(ui.block(0)); guide.setOnClickListener(v -> showGuideDialog()); w.addView(guide);
        content.addView(w);

        content.addView(chuteContinue);
        chuteContinue.setOnClickListener(v -> showBoard());
        Button back = ui.secondary("BACK"); back.setOnClickListener(v -> showGear()); content.addView(back);
        upd.run();
    }

    private void updateWatchCard() {
        if (screen != Screen.CHUTE || watchPill == null) return;
        boolean useWatch = Prefs.getBool(this, Prefs.USE_WATCH, true);
        boolean pending = Prefs.watchChallengePending(this);
        boolean fresh = Prefs.watchVerifiedFresh(this);
        String reason = WatchChallenge.blockedReason(this);
        h.removeCallbacks(watchTick);
        if (!useWatch) {
            ui.setPill(watchPill, "NOT USED", Ui.MUTED);
            watchHelp.setText("Optional. Turn on to require a tap on the watch before boarding.");
            Ui.enable(watchSend, false);
        } else if (pending) {
            long left = Math.max(0, (Prefs.getLong(this, Prefs.WATCH_EXPIRES_AT, 0) - System.currentTimeMillis()) / 1000);
            ui.setPill(watchPill, String.format(Locale.US, "WAITING FOR WATCH  %d:%02d", left / 60, left % 60), Ui.AMBER);
            watchHelp.setText("Tap WATCH CHECK PASSED on the watch. The button only exists on the watch, not on the phone. If nothing shows, lock the phone for a few seconds: Galaxy Wearable may only forward while the phone screen is off.");
            watchSend.setText("SEND AGAIN"); Ui.enable(watchSend, true);
            h.postDelayed(watchTick, 1000);
        } else if (fresh) {
            ui.setPill(watchPill, WatchChallenge.verifiedLabel(this), Ui.GREEN);
            watchHelp.setText("The watch acknowledged the challenge. Verification stays valid for 6 hours.");
            watchSend.setText("SEND AGAIN"); Ui.enable(watchSend, true);
        } else {
            ui.setPill(watchPill, "NOT VERIFIED", Ui.RED);
            watchHelp.setText("Send a challenge, then look at the watch and tap WATCH CHECK PASSED.");
            watchSend.setText("SEND WATCH CHALLENGE"); Ui.enable(watchSend, true);
        }
        watchNotif.setText(reason == null ? "Notifications: ✓ allowed (Watch checks · High)" : "Notifications: ✗ " + reason);
        watchNotif.setTextColor(reason == null ? Ui.MUTED : Ui.RED);
        watchFix.setVisibility(reason == null ? View.GONE : View.VISIBLE);
        Ui.enable(chuteContinue, all(chuteDone) && (!useWatch || fresh));
    }
    private final Runnable watchTick = this::updateWatchCard;

    private void sendChallenge() {
        String reason = WatchChallenge.blockedReason(this);
        if (reason != null) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("The phone is blocking this")
                    .setMessage(reason + "\n\nNothing can reach the watch until the phone is allowed to post the notification.")
                    .setPositiveButton("Open notification settings", (d, w) -> openNotificationSettings())
                    .setNeutralButton("Diagnostics", (d, w) -> showDiagnosticsDialog())
                    .setNegativeButton("Cancel", null).show();
            updateWatchCard();
            return;
        }
        long nonce = WatchChallenge.send(this);
        if (nonce > 0) {
            boolean posted = NotificationDoctor.isPosted(this, WatchChallenge.NOTIFICATION_ID);
            Toast.makeText(this, posted
                    ? "Posted on the phone. Check the watch; lock the phone if it does not appear."
                    : "Sent, but the phone is not holding the notification. Open diagnostics.", Toast.LENGTH_LONG).show();
        } else {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("The notification did not post")
                    .setMessage(NotificationDoctor.report(this))
                    .setPositiveButton("Open notification settings", (d, w) -> openNotificationSettings())
                    .setNegativeButton("Close", null).show();
        }
        updateWatchCard();
    }

    private void showDiagnosticsDialog() {
        final String text = NotificationDoctor.report(this);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Notification diagnostics")
                .setMessage(text)
                .setPositiveButton("Send to developer", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "SkyDive Companion notification diagnostics")
                            .putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "Send diagnostics"));
                })
                .setNeutralButton("Notification settings", (d, w) -> openNotificationSettings())
                .setNegativeButton("Close", null).show();
    }
    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 45); return;
        }
        try { startActivity(WatchChallenge.settingsIntent(this)); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
    }
    private void showGuideDialog() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Galaxy Watch notifications")
                .setMessage(WatchChallenge.GUIDE)
                .setPositiveButton("Open notification settings", (d, w) -> openNotificationSettings())
                .setNegativeButton("Close", null).show();
    }

    // ------------------------------------------------------------------ board
    private void showBoard() {
        begin(Screen.BOARD, 4, "Ready to board", "Ready to board", "Live ground tracking starts here. Start it when you are under the tent or walking to the aircraft.");
        boolean useWatch = Prefs.getBool(this, Prefs.USE_WATCH, true);
        boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean baro = ((SensorManager) getSystemService(SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_PRESSURE) != null;

        LinearLayout sum = ui.card(); sum.addView(ui.label("Pre-flight summary"));
        sum.addView(ui.kv("Gear check", "✓ " + count(gearDone) + "/" + gearDone.length, Ui.GREEN));
        sum.addView(ui.kv("Parachute on", "✓ " + count(chuteDone) + "/" + chuteDone.length, Ui.GREEN));
        sum.addView(ui.kv("Watch", !useWatch ? "— not used" : WatchChallenge.verifiedLabel(this), !useWatch ? Ui.MUTED : (Prefs.watchVerifiedFresh(this) ? Ui.GREEN : Ui.RED)));
        sum.addView(ui.kv("Location", loc ? "✓ granted" : "✗ required", loc ? Ui.GREEN : Ui.RED));
        sum.addView(ui.kv("Barometer", baro ? "✓ present" : "✗ missing (GPS only)", baro ? Ui.GREEN : Ui.AMBER));
        if (!loc) { Button g = ui.ghost("GRANT LOCATION"); g.setLayoutParams(ui.block(0)); g.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 46)); sum.addView(g); }
        content.addView(sum);

        LinearLayout fs = ui.card(); fs.addView(ui.label("Field setup"));
        double dz = Prefs.getDouble(this, Prefs.DZ_ELEV_FT, Double.NaN), tf = Prefs.getDouble(this, Prefs.GROUND_TEMP_F, Double.NaN);
        Ui.Field fDz = ui.field("DZ elevation", Double.isNaN(dz) ? "" : String.valueOf(Math.round(dz)), "ft MSL", false); fs.addView(fDz.view);
        Ui.Field fT = ui.field("Ground temp", Double.isNaN(tf) ? "" : String.valueOf(Math.round(tf)), "°F", true); fs.addView(fT.view);
        TextView hint = ui.helper("Elevation gives you MSL; temperature corrects the barometer (a hot day reads low). Leave blank to auto-fill elevation from GPS."); hint.setPadding(0, ui.dp(6), 0, ui.dp(4)); fs.addView(hint);
        fs.addView(ui.toggle("Voice alerts", Prefs.getBool(this, Prefs.VOICE, true), (b, on) -> Prefs.putBool(this, Prefs.VOICE, on)));
        fs.addView(ui.toggle("Duck music / podcasts for callouts", Prefs.getBool(this, Prefs.DUCK, true), (b, on) -> Prefs.putBool(this, Prefs.DUCK, on)));
        content.addView(fs);

        Button start = ui.primary("START GROUND TRACKING");
        boolean canStart = loc || Build.VERSION.SDK_INT < 34;
        Ui.enable(start, canStart);
        if (!canStart) { TextView why = ui.helper("Android 14+ requires Location permission for the tracking service."); why.setTextColor(Ui.RED); why.setPadding(ui.dp(4), 0, 0, ui.dp(8)); content.addView(why); }
        start.setOnClickListener(v -> {
            Prefs.putDouble(this, Prefs.DZ_ELEV_FT, parse(fDz.edit.getText().toString()));
            Prefs.putDouble(this, Prefs.GROUND_TEMP_F, parse(fT.edit.getText().toString()));
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 46);
                Toast.makeText(this, "Grant location, then tap Start again.", Toast.LENGTH_LONG).show();
                return;
            }
            CrashReporter.clear(this);
            DiveService.startFailed = false;
            try { DiveService.start(this); }
            catch (Throwable t) {
                CrashReporter.note(this, t, "startForegroundService");
                Toast.makeText(this, "Could not start tracking. See the report on the home screen.", Toast.LENGTH_LONG).show();
                showHome();
                return;
            }
            showLive();
        });
        content.addView(start);
        Button back = ui.secondary("BACK"); back.setOnClickListener(v -> showChute()); content.addView(back);
    }
    private static double parse(String s) { try { return s.trim().isEmpty() ? Double.NaN : Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return Double.NaN; } }

    // ------------------------------------------------------------------ live
    private void showLive() {
        begin(Screen.LIVE, 5, "Live", "Live jump computer", null);
        heroTint = Ui.GREEN; liveHero = ui.hero(Ui.GREEN);
        LinearLayout top = ui.row(); livePhasePill = ui.pill("GROUND", Ui.GREEN); top.addView(livePhasePill); top.addView(ui.flex());
        liveTimer = ui.number("00:00", 14, Ui.MUTED); top.addView(liveTimer); liveHero.addView(top);
        liveAgl = ui.number("—", 64, Ui.TEXT); liveAgl.setPadding(0, ui.dp(14), 0, 0); liveAgl.setLetterSpacing(-0.03f); liveHero.addView(liveAgl);
        TextView aglLab = ui.label("feet AGL"); aglLab.setTextColor(Ui.ACCENT); liveHero.addView(aglLab);
        LinearLayout vsRow = ui.row(); liveVs = ui.number("▬ 0 ft/min", 17, Ui.TEXT); vsRow.addView(liveVs); vsRow.addView(ui.flex());
        liveBaseline = ui.number("QNH —", 13, Ui.MUTED); vsRow.addView(liveBaseline); liveHero.addView(vsRow);
        liveCalibHost = new LinearLayout(this); liveCalibHost.setOrientation(LinearLayout.VERTICAL); liveCalibHost.setPadding(0, ui.dp(12), 0, 0);
        liveCalib = ui.helper("Calibrating ground baseline… keep the phone still"); liveCalib.setTextColor(Ui.AMBER); liveCalibHost.addView(liveCalib);
        liveCalibBar = ui.progress(0, Ui.AMBER); ((LinearLayout.LayoutParams) liveCalibBar.getLayoutParams()).topMargin = ui.dp(6); liveCalibHost.addView(liveCalibBar);
        liveCalibFill = liveCalibBar.getChildAt(0); liveCalibRest = liveCalibBar.getChildAt(1);
        liveHero.addView(liveCalibHost);
        LinearLayout confRow = ui.row(); confRow.setPadding(0, ui.dp(12), 0, 0);
        liveDotsHost = new LinearLayout(this); liveDotsHost.setOrientation(LinearLayout.HORIZONTAL); liveDots = ui.dots(0, Ui.GREEN); liveDotsHost.addView(liveDots);
        TextView cl = ui.text("Confidence", 12, Ui.MUTED, true); cl.setPadding(0, 0, ui.dp(8), 0); confRow.addView(cl); confRow.addView(liveDotsHost); confRow.addView(ui.flex());
        liveConf = ui.text("baro ±— ft", 12, Ui.MUTED, false); confRow.addView(liveConf); liveHero.addView(confRow);
        content.addView(liveHero);

        tMsl = ui.tile("MSL", "—", "DZ —"); tGps = ui.tile("GPS alt", "—", "±— ft"); tPress = ui.tile("Pressure", "—", "raw —");
        content.addView(ui.tiles(tMsl, tGps, tPress));
        tMax = ui.tile("Max AGL", "—", "this session"); tSpeed = ui.tile("GPS speed", "—", "mph"); tNext = ui.tile("Next callout", "—", "ft");
        content.addView(ui.tiles(tMax, tSpeed, tNext));

        btnRezero = ui.secondary("RE-ZERO ON GROUND"); btnRezero.setOnClickListener(v -> DiveService.send(this, DiveService.ACTION_REZERO));
        btnMark = ui.secondary("MARK EXIT"); btnMark.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Mark exit now?").setMessage("Forces FREEFALL phase from this moment. Use only if automatic exit detection missed the exit.")
                .setPositiveButton("Mark exit", (d, w) -> DiveService.send(this, DiveService.ACTION_MARK_EXIT)).setNegativeButton("Cancel", null).show());
        content.addView(ui.pair(btnRezero, btnMark)); content.addView(ui.spacer(10));
        Button stop = ui.danger("STOP / SAVE JUMP");
        stop.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Stop tracking?").setMessage("The jump is saved to the logbook and tracking stops.")
                .setPositiveButton("Stop & save", (d, w) -> { DiveService.send(this, DiveService.ACTION_STOP); h.postDelayed(this::showHome, 400); })
                .setNegativeButton("Keep tracking", null).show());
        content.addView(stop);

        LinearLayout sens = ui.card(); sens.addView(ui.label("Barometer (raw)"));
        rawHz = ui.kv("Sample rate", "\u2014", Ui.TEXT); sens.addView(rawHz);
        rawNow = ui.kv("Pressure now", "\u2014", Ui.TEXT); sens.addView(rawNow);
        rawBase = ui.kv("Ground baseline", "\u2014", Ui.TEXT); sens.addView(rawBase);
        rawDelta = ui.kv("Change from baseline", "\u2014", Ui.TEXT); sens.addView(rawDelta);
        rawAgl = ui.kv("= height", "\u2014", Ui.ACCENT); sens.addView(rawAgl);
        sens.addView(ui.helper("1 hPa is about 27 ft. Lifting the phone by 3 ft should move the change by roughly 0.11 hPa; if it moves much more than that, the sensor reading itself is wrong, not the maths."));
        content.addView(sens);

        LinearLayout how = ui.card(); how.addView(ui.label("How altitude is computed"));
        TextView howT = ui.helper("Pressure is spike-filtered, converted with the ISA hypsometric formula, then smoothed by a two-state Kalman filter that also produces the vertical rate. AGL is measured against a median ground baseline taken over 8 s; while you are still on the ground it slowly tracks weather drift. Ground temperature scales the result (a pressure altimeter reads low on a hot day). MSL = DZ elevation + AGL. GPS altitude is shown for cross-checking and feeds the confidence dots; it is never blended into AGL.");
        howT.setVisibility(View.GONE); how.addView(howT);
        Button toggleHow = ui.ghost("Show"); toggleHow.setLayoutParams(ui.block(0)); toggleHow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        toggleHow.setOnClickListener(v -> { boolean vis = howT.getVisibility() == View.VISIBLE; howT.setVisibility(vis ? View.GONE : View.VISIBLE); toggleHow.setText(vis ? "Show" : "Hide"); });
        how.addView(toggleHow); content.addView(how);

        TextView note = ui.helper("Keep the phone secured. This screen is optional; voice alerts and notifications are the primary interface while jumping.");
        note.setPadding(ui.dp(4), 0, ui.dp(4), 0); content.addView(note);

        if (DiveService.lastStatus != null) applyStatus(DiveService.lastStatus);
    }

    private void applyStatus(Bundle b) {
        if (b == null || liveAgl == null) return;
        String phase = b.getString("phase", "GROUND"); int pc = Ui.phaseColor(phase);
        boolean ready = b.getBoolean("ready"), calibrating = b.getBoolean("calibrating"), hasBaro = b.getBoolean("hasBaro", true);
        double agl = b.getDouble("agl"), fpm = b.getDouble("fpm");

        ui.setPill(livePhasePill, phase, pc);
        if (heroTint != pc) { heroTint = pc; liveHero.setBackground(ui.heroBackground(pc)); }
        liveTimer.setText(b.getString("timer", "00:00"));
        liveAgl.setText(ready ? Ui.ft(agl) : "—");
        liveVs.setText(ready ? Ui.fpm(fpm) + " ft/min" : "—");
        liveVs.setTextColor(!ready || Math.abs(fpm) < 60 ? Ui.TEXT : (fpm > 0 ? Ui.AMBER : Ui.ACCENT));
        liveBaseline.setText(ready ? "QNH " + Ui.ft1(b.getDouble("baseline")) + " hPa" : "");

        if (!hasBaro) { liveCalibHost.setVisibility(View.VISIBLE); liveCalib.setText("No barometer on this phone. GPS altitude only."); liveCalib.setTextColor(Ui.RED); liveCalibBar.setVisibility(View.GONE); }
        else if (calibrating || !ready) {
            liveCalibHost.setVisibility(View.VISIBLE); liveCalibBar.setVisibility(View.VISIBLE);
            double p = b.getDouble("calibProg"); liveCalib.setText(String.format(Locale.US, "Calibrating ground baseline %d%% — keep the phone still", (int) (p * 100)));
            Ui.setProgress(liveCalibFill, liveCalibRest, p);
        } else liveCalibHost.setVisibility(View.GONE);

        int conf = b.getInt("conf"); liveDotsHost.removeAllViews(); liveDots = ui.dots(conf, conf >= 3 ? Ui.GREEN : (conf == 2 ? Ui.AMBER : Ui.RED)); liveDotsHost.addView(liveDots);
        double noise = b.getDouble("noise"); double delta = b.getDouble("gpsDelta");
        String confText = ready ? "baro ±" + Ui.ft(Math.max(1, noise)) + " ft" : "no baseline";
        if (!Double.isNaN(delta)) confText += " · GPS " + (delta >= 0 ? "+" : "") + Ui.ft(delta) + " ft";
        if (b.getBoolean("tempCorr")) confText += " · temp ✓";
        liveConf.setText(confText);

        double msl = b.getDouble("msl"), dz = b.getDouble("dz");
        tMsl.value.setText(Ui.ft(msl)); tMsl.sub.setText(Double.isNaN(dz) ? "set DZ elevation" : "DZ " + Ui.ft(dz) + " (" + b.getString("dzSource", "") + ")");
        double ga = b.getDouble("gpsAlt"), gacc = b.getDouble("gpsAcc");
        tGps.value.setText(Ui.ft(ga)); tGps.sub.setText(Double.isNaN(gacc) ? "waiting for fix" : "±" + Ui.ft(gacc) + " ft");
        tPress.value.setText(Ui.ft1(b.getDouble("filtered"))); tPress.sub.setText("raw " + Ui.ft1(b.getDouble("raw")) + " hPa");
        tMax.value.setText(Ui.ft(b.getDouble("maxAgl")));
        double spd = b.getDouble("gpsSpeed"); tSpeed.value.setText(Double.isNaN(spd) ? "—" : String.format(Locale.US, "%.0f", spd));
        int next = b.getInt("nextCallout", -1); tNext.value.setText(next > 0 ? Ui.ft(next) : "—");
        tNext.sub.setText(next > 0 ? "ft (freefall/canopy)" : "callouts done");

        double hz = b.getDouble("hz"), dHpa = b.getDouble("deltaHpa");
        setKv(rawHz, Double.isNaN(hz) || hz <= 0 ? "\u2014" : String.format(Locale.US, "%.0f Hz", hz));
        setKv(rawNow, Double.isNaN(b.getDouble("raw")) ? "\u2014" : String.format(Locale.US, "%.3f hPa", b.getDouble("raw")));
        setKv(rawBase, Double.isNaN(b.getDouble("baseline")) ? "\u2014" : String.format(Locale.US, "%.3f hPa", b.getDouble("baseline")));
        setKv(rawDelta, Double.isNaN(dHpa) ? "\u2014" : String.format(Locale.US, "%+.3f hPa", dHpa));
        setKv(rawAgl, ready ? Ui.ft(agl) + " ft" : "\u2014");

        Ui.enable(btnRezero, ready && "GROUND".equals(phase));
        Ui.enable(btnMark, "AIRCRAFT".equals(phase));
    }

    private static void setKv(LinearLayout row, String value) {
        if (row == null) return;
        Object t = row.getTag();
        if (t instanceof TextView) ((TextView) t).setText(value);
    }

    // ------------------------------------------------------------------ logbook
    private void showLogbook() {
        begin(Screen.LOGBOOK, 0, null, "Logbook", "Automatically recorded jumps. The full track of the most recent jump can be exported as CSV.");
        List<JumpLog.Summary> all = JumpLog.list(this);
        if (all.isEmpty()) { LinearLayout c = ui.card(); c.addView(ui.helper("No jumps yet.")); content.addView(c); }
        else {
            JumpLog.Summary last = all.get(0);
            LinearLayout c = ui.card(); c.addView(ui.label("Last jump"));
            c.addView(ui.body(last.date()));
            c.addView(ui.kv("Exit altitude", Ui.ft(last.exitAglFt) + " ft AGL", Ui.TEXT));
            c.addView(ui.kv("Opening altitude", Ui.ft(last.openAglFt) + " ft AGL", Ui.TEXT));
            c.addView(ui.kv("Freefall", last.freefallSec >= 0 ? last.freefallSec + " s" : "—", Ui.TEXT));
            c.addView(ui.kv("Canopy", last.canopySec >= 0 ? String.format(Locale.US, "%d:%02d", last.canopySec / 60, last.canopySec % 60) : "—", Ui.TEXT));
            c.addView(ui.kv("Max descent rate", Ui.ft(-last.minVsFpm) + " ft/min", Ui.TEXT));
            c.addView(ui.kv("Max AGL", Ui.ft(last.maxAglFt) + " ft", Ui.TEXT));
            c.addView(ui.kv("Track points", String.valueOf(last.points), Ui.TEXT));
            Button share = ui.secondary("SHARE CSV"); share.setOnClickListener(v -> shareCsv(last));
            Button save = ui.secondary("SAVE TO DOWNLOADS"); save.setOnClickListener(v -> saveCsv(last));
            LinearLayout pr = ui.pair(share, save); ((LinearLayout.LayoutParams) pr.getLayoutParams()).topMargin = ui.dp(12); c.addView(pr);
            content.addView(c);
            if (all.size() > 1) {
                LinearLayout p = ui.card(); p.addView(ui.label("Previous"));
                for (int i = 1; i < all.size(); i++) {
                    JumpLog.Summary s = all.get(i);
                    p.addView(ui.body(s.date() + " · " + s.headline())); TextView d = ui.helper(s.detail()); p.addView(d);
                    if (i < all.size() - 1) p.addView(ui.divider());
                }
                content.addView(p);
            }
            Button clear = ui.danger("CLEAR LOGBOOK");
            clear.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Clear logbook?")
                    .setMessage("Deletes all saved summaries and the last track.").setPositiveButton("Clear", (d, w) -> { JumpLog.clear(this); showLogbook(); }).setNegativeButton("Cancel", null).show());
            content.addView(clear);
        }
        Button back = ui.secondary("BACK"); back.setOnClickListener(v -> showHome()); content.addView(back);
    }
    private String csvName(JumpLog.Summary s) { return "skydive-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new java.util.Date(s.startedMs)) + ".csv"; }
    private void shareCsv(JumpLog.Summary s) {
        String csv = JumpLog.lastTrackCsv(this);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "SkyDive Companion " + s.date())
                .putExtra(Intent.EXTRA_TEXT, s.date() + " · " + s.headline() + " · " + s.detail() + "\n\n" + csv);
        startActivity(Intent.createChooser(i, "Share jump"));
    }
    private void saveCsv(JumpLog.Summary s) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, csvName(s)); v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SkyDiveCompanion");
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IllegalStateException("insert failed");
            try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(JumpLog.lastTrackCsv(this).getBytes("UTF-8")); }
            Toast.makeText(this, "Saved to Downloads/SkyDiveCompanion/" + csvName(s), Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    // ------------------------------------------------------------------ settings
    private void showSettings() {
        begin(Screen.SETTINGS, 0, null, "Settings", "Field defaults, alert bands and watch options. Saved on this phone only.");
        LinearLayout a = ui.card(); a.addView(ui.label("Altitude"));
        Ui.Field fDz = ui.field("DZ elevation", str(Prefs.getDouble(this, Prefs.DZ_ELEV_FT, Double.NaN)), "ft MSL", false); a.addView(fDz.view);
        Ui.Field fT = ui.field("Ground temperature", str(Prefs.getDouble(this, Prefs.GROUND_TEMP_F, Double.NaN)), "°F", true); a.addView(fT.view);
        a.addView(ui.toggle("Auto re-zero while still on the ground", Prefs.getBool(this, Prefs.AUTO_REZERO, true), (b, on) -> Prefs.putBool(this, Prefs.AUTO_REZERO, on)));
        a.addView(ui.toggle("GPS cross-check in confidence", Prefs.getBool(this, Prefs.GPS_CHECK, true), (b, on) -> Prefs.putBool(this, Prefs.GPS_CHECK, on)));
        content.addView(a);

        LinearLayout al = ui.card(); al.addView(ui.label("Alerts"));
        al.addView(ui.toggle("Voice alerts", Prefs.getBool(this, Prefs.VOICE, true), (b, on) -> Prefs.putBool(this, Prefs.VOICE, on)));
        al.addView(ui.toggle("Duck other audio", Prefs.getBool(this, Prefs.DUCK, true), (b, on) -> Prefs.putBool(this, Prefs.DUCK, on)));
        Ui.Field fC = ui.textField("Callouts, feet AGL (comma separated)", Prefs.getString(this, Prefs.CALLOUTS, Prefs.DEFAULT_CALLOUTS), Prefs.DEFAULT_CALLOUTS); al.addView(fC.view);
        al.addView(ui.spacer(8));
        Ui.Field fH = ui.field("Hard deck", str(Prefs.getDouble(this, Prefs.HARD_DECK_FT, 2500)), "ft AGL", false); al.addView(fH.view);
        content.addView(al);

        LinearLayout w = ui.card(); w.addView(ui.label("Galaxy Watch"));
        w.addView(ui.toggle("Use Galaxy Watch verification", Prefs.getBool(this, Prefs.USE_WATCH, true), (b, on) -> Prefs.putBool(this, Prefs.USE_WATCH, on)));
        Button ns = ui.secondary("OPEN NOTIFICATION SETTINGS"); ns.setOnClickListener(v -> openNotificationSettings()); w.addView(ns);
        Button gd = ui.secondary("GALAXY WEARABLE GUIDE"); gd.setOnClickListener(v -> showGuideDialog()); w.addView(gd);
        Button pt = ui.secondary("SEND PLAIN TEST NOTIFICATION"); pt.setLayoutParams(ui.block(0));
        pt.setOnClickListener(v -> {
            if (WatchChallenge.sendPlainTest(this)) Toast.makeText(this, "Sent. Check the watch.", Toast.LENGTH_LONG).show();
            else openNotificationSettings();
        });
        w.addView(pt);
        Button nd = ui.secondary("NOTIFICATION DIAGNOSTICS"); nd.setLayoutParams(ui.block(0));
        nd.setOnClickListener(v -> showDiagnosticsDialog()); w.addView(nd);
        content.addView(w);

        Button save = ui.primary("SAVE");
        save.setOnClickListener(v -> {
            Prefs.putDouble(this, Prefs.DZ_ELEV_FT, parse(fDz.edit.getText().toString()));
            Prefs.putDouble(this, Prefs.GROUND_TEMP_F, parse(fT.edit.getText().toString()));
            Prefs.putDouble(this, Prefs.HARD_DECK_FT, parse(fH.edit.getText().toString()));
            String c = fC.edit.getText().toString().trim(); Prefs.putString(this, Prefs.CALLOUTS, c.isEmpty() ? Prefs.DEFAULT_CALLOUTS : c);
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); showHome();
        });
        content.addView(save);
        Button back = ui.secondary("BACK"); back.setOnClickListener(v -> showHome()); content.addView(back);
    }
    private static String str(double v) { return Double.isNaN(v) ? "" : String.valueOf(Math.round(v)); }
}
