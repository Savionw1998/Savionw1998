package com.savion.skydivecompanion;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Last 20 jump summaries plus the most recent full track (CSV). */
public final class JumpLog {
    private static final String FILE = "jumpLog";
    private static final String KEY_SUMMARIES = "summaries";
    private static final String KEY_TRACK = "track";
    private static final int MAX = 20;

    public static final class Summary {
        public long startedMs;
        public double exitAglFt, openAglFt, maxAglFt, minVsFpm;
        public long freefallSec, canopySec;
        public int points;

        String encode() {
            return startedMs + "|" + r(exitAglFt) + "|" + r(openAglFt) + "|" + r(maxAglFt) + "|" + r(minVsFpm)
                    + "|" + freefallSec + "|" + canopySec + "|" + points;
        }
        static Summary decode(String s) {
            try {
                String[] p = s.split("\\|");
                Summary x = new Summary();
                x.startedMs = Long.parseLong(p[0]); x.exitAglFt = d(p[1]); x.openAglFt = d(p[2]); x.maxAglFt = d(p[3]);
                x.minVsFpm = d(p[4]); x.freefallSec = Long.parseLong(p[5]); x.canopySec = Long.parseLong(p[6]); x.points = Integer.parseInt(p[7]);
                return x;
            } catch (Exception e) { return null; }
        }
        private static String r(double v) { return Double.isNaN(v) ? "nan" : String.valueOf(Math.round(v)); }
        private static double d(String s) { return "nan".equals(s) ? Double.NaN : Double.parseDouble(s); }

        public String date() { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(startedMs)); }
        public String headline() {
            StringBuilder b = new StringBuilder();
            if (!Double.isNaN(exitAglFt) && exitAglFt > 0) b.append("Exit ").append(fmt(exitAglFt)).append(" ft");
            else b.append("Max ").append(fmt(maxAglFt)).append(" ft");
            if (!Double.isNaN(openAglFt) && openAglFt > 0) b.append(" · Open ").append(fmt(openAglFt)).append(" ft");
            return b.toString();
        }
        public String detail() {
            StringBuilder b = new StringBuilder();
            if (freefallSec >= 0) b.append("Freefall ").append(freefallSec).append(" s");
            if (canopySec >= 0) { if (b.length() > 0) b.append(" · "); b.append("Canopy ").append(canopySec / 60).append(":").append(String.format(Locale.US, "%02d", canopySec % 60)); }
            if (minVsFpm < 0) { if (b.length() > 0) b.append(" · "); b.append("Max ").append(fmt(-minVsFpm)).append(" ft/min"); }
            return b.length() == 0 ? "No jump phases detected" : b.toString();
        }
        static String fmt(double v) { return String.format(Locale.US, "%,d", Math.round(v)); }
    }

    private JumpLog() {}
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static void save(Context c, Summary s, String trackCsv) {
        List<Summary> all = list(c);
        all.add(0, s);
        while (all.size() > MAX) all.remove(all.size() - 1);
        StringBuilder b = new StringBuilder();
        for (Summary x : all) { if (b.length() > 0) b.append('\n'); b.append(x.encode()); }
        sp(c).edit().putString(KEY_SUMMARIES, b.toString()).putString(KEY_TRACK, trackCsv).apply();
    }
    public static List<Summary> list(Context c) {
        ArrayList<Summary> out = new ArrayList<>();
        String raw = sp(c).getString(KEY_SUMMARIES, "");
        if (raw.isEmpty()) return out;
        for (String line : raw.split("\n")) { Summary s = Summary.decode(line); if (s != null) out.add(s); }
        return out;
    }
    public static Summary last(Context c) { List<Summary> l = list(c); return l.isEmpty() ? null : l.get(0); }
    public static String lastTrackCsv(Context c) { return sp(c).getString(KEY_TRACK, ""); }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}
