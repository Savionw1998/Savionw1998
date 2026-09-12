package com.savion.skydivecompanion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/**
 * Programmatic view kit. Every visual decision in the app goes through here
 * so the screens stay consistent. Colours follow docs/WIREFRAMES.md.
 */
final class Ui {
    static final int BG = 0xFF080C12, CARD = 0xFF131A23, CARD2 = 0xFF18212C, BORDER = 0xFF1F2A37;
    static final int TEXT = 0xFFFFFFFF, MUTED = 0xFF9BAABC, ACCENT = 0xFF4BB1FF, ACCENT_DEEP = 0xFF1E7FD6;
    static final int GREEN = 0xFF47D394, AMBER = 0xFFFFC34D, RED = 0xFFFF6969, PURPLE = 0xFFB48CFF;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    final Context c; private final float density;
    Ui(Context c) { this.c = c; density = c.getResources().getDisplayMetrics().density; }

    int dp(float x) { return (int) (x * density + 0.5f); }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    static int phaseColor(String phase) {
        if (phase == null) return MUTED;
        switch (phase) {
            case "GROUND": return GREEN;
            case "AIRCRAFT": return AMBER;
            case "FREEFALL": return RED;
            case "CANOPY": return PURPLE;
            case "LANDED": return GREEN;
            default: return MUTED;
        }
    }

    // ---------------------------------------------------------------- text
    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    TextView title(String s) { TextView t = text(s, 26, TEXT, true); t.setLetterSpacing(-0.01f); return t; }
    TextView subtitle(String s) { TextView t = text(s, 14, MUTED, false); t.setLineSpacing(0, 1.15f); t.setPadding(0, dp(4), 0, dp(14)); return t; }
    TextView body(String s) { TextView t = text(s, 15, TEXT, false); t.setLineSpacing(0, 1.2f); return t; }
    TextView helper(String s) { TextView t = text(s, 13, MUTED, false); t.setLineSpacing(0, 1.25f); return t; }
    TextView label(String s) {
        TextView t = text(s.toUpperCase(Locale.US), 11, MUTED, true);
        t.setLetterSpacing(0.12f); t.setPadding(0, 0, 0, dp(8));
        return t;
    }
    TextView number(String s, float sp, int color) {
        TextView t = text(s, sp, color, true);
        t.setFontFeatureSettings("tnum"); t.setIncludeFontPadding(false);
        return t;
    }

    // ---------------------------------------------------------------- containers
    LinearLayout.LayoutParams block(int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP); p.bottomMargin = dp(bottomDp); return p;
    }
    GradientDrawable shape(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }
    LinearLayout card() {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(15), dp(16), dp(15));
        l.setBackground(shape(CARD, 18, BORDER)); l.setLayoutParams(block(12));
        return l;
    }
    /** Gradient hero card tinted with the given colour. */
    GradientDrawable heroBackground(int tint) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{alpha(tint, 0x55), CARD});
        g.setCornerRadius(dp(22)); g.setStroke(dp(1), alpha(tint, 0x66));
        return g;
    }
    LinearLayout hero(int tint) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        l.setBackground(heroBackground(tint)); l.setLayoutParams(block(12));
        return l;
    }
    LinearLayout row() {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP)); return l;
    }
    View divider() {
        View v = new View(c); v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, dp(1)); p.topMargin = dp(10); p.bottomMargin = dp(10);
        v.setLayoutParams(p); return v;
    }
    View spacer(int dpH) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(dpH))); return v; }
    View flex() { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f)); return v; }

    // ---------------------------------------------------------------- components
    TextView pill(String s, int color) {
        TextView t = text("●  " + s, 12, color, true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(11), dp(6), dp(12), dp(6));
        t.setBackground(shape(alpha(color, 0x22), 999, alpha(color, 0x55)));
        return t;
    }
    void setPill(TextView t, String s, int color) {
        t.setText("●  " + s); t.setTextColor(color); t.setBackground(shape(alpha(color, 0x22), 999, alpha(color, 0x55)));
    }

    LinearLayout stepper(int current, int total, String stepTitle) {
        LinearLayout r = row(); r.setPadding(0, dp(2), 0, dp(14));
        for (int i = 1; i <= total; i++) {
            View d = new View(c);
            boolean done = i < current, now = i == current;
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
            g.setColor(done ? ACCENT : (now ? alpha(ACCENT, 0x33) : CARD2));
            g.setStroke(dp(now ? 2 : 1), done || now ? ACCENT : BORDER);
            d.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(now ? 14 : 10), dp(now ? 14 : 10)); p.rightMargin = dp(6);
            r.addView(d, p);
        }
        TextView t = text("Step " + current + " of " + total + " · " + stepTitle, 12, MUTED, true);
        t.setPadding(dp(6), 0, 0, 0); r.addView(t);
        return r;
    }

    private Button button(String s, GradientDrawable bg, int textColor, int minH) {
        Button b = new Button(c);
        b.setText(s); b.setTextSize(15); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(textColor); b.setLetterSpacing(0.03f);
        b.setMinHeight(dp(minH)); b.setMinimumHeight(dp(minH)); b.setPadding(dp(16), 0, dp(16), 0);
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
        b.setLayoutParams(block(10));
        return b;
    }
    Button primary(String s) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ACCENT, ACCENT_DEEP});
        g.setCornerRadius(dp(16));
        return button(s, g, 0xFF06101A, 56);
    }
    Button secondary(String s) { return button(s, shape(CARD2, 16, BORDER), TEXT, 52); }
    Button danger(String s) { return button(s, shape(alpha(RED, 0x14), 16, alpha(RED, 0x88)), RED, 52); }
    Button ghost(String s) { Button b = button(s, shape(Color.TRANSPARENT, 16, 0), ACCENT, 44); b.setTextSize(14); return b; }
    static void enable(View v, boolean on) { v.setEnabled(on); v.setAlpha(on ? 1f : 0.4f); }

    /** Half-width pair of buttons. */
    LinearLayout pair(Button a, Button b) {
        LinearLayout r = row(); r.setLayoutParams(block(0));
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, WRAP, 1f); pa.rightMargin = dp(5);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, WRAP, 1f); pb.leftMargin = dp(5);
        r.addView(a, pa); r.addView(b, pb); return r;
    }

    CheckBox check(String text, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        CheckBox cb = new CheckBox(c);
        cb.setText(text); cb.setTextColor(TEXT); cb.setTextSize(15); cb.setChecked(checked);
        cb.setMinHeight(dp(54)); cb.setMinimumHeight(dp(54)); cb.setPadding(dp(10), 0, 0, 0);
        cb.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, MUTED}));
        cb.setOnCheckedChangeListener(l);
        return cb;
    }

    static final class Tile { LinearLayout view; TextView value, sub; }
    Tile tile(String label, String value, String sub) {
        Tile t = new Tile();
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(12), dp(11), dp(12), dp(11)); l.setBackground(shape(CARD, 16, BORDER));
        TextView lab = label(label); lab.setPadding(0, 0, 0, dp(4)); lab.setTextSize(10); l.addView(lab);
        t.value = number(value, 18, TEXT); l.addView(t.value);
        t.sub = text(sub, 11, MUTED, false); t.sub.setPadding(0, dp(2), 0, 0); l.addView(t.sub);
        t.view = l; return t;
    }
    LinearLayout tiles(Tile... ts) {
        LinearLayout r = row(); r.setLayoutParams(block(10));
        for (int i = 0; i < ts.length; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, MATCH, 1f);
            if (i > 0) p.leftMargin = dp(5); if (i < ts.length - 1) p.rightMargin = dp(5);
            r.addView(ts[i].view, p);
        }
        return r;
    }

    LinearLayout toggle(String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = row(); r.setMinimumHeight(dp(44));
        TextView t = body(label); r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Switch s = new Switch(c); s.setChecked(checked); s.setOnCheckedChangeListener(l);
        s.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ACCENT, MUTED}));
        s.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{alpha(ACCENT, 0x66), CARD2}));
        r.addView(s); r.setTag(s);
        return r;
    }

    static final class Field { LinearLayout view; EditText edit; }
    Field field(String label, String value, String unit, boolean decimal) {
        Field f = new Field();
        LinearLayout r = row(); r.setMinimumHeight(dp(48));
        r.addView(body(label), new LinearLayout.LayoutParams(0, WRAP, 1f));
        EditText e = new EditText(c);
        e.setText(value); e.setTextColor(TEXT); e.setTextSize(16); e.setTypeface(Typeface.DEFAULT_BOLD);
        e.setFontFeatureSettings("tnum"); e.setGravity(Gravity.END);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        e.setBackground(shape(CARD2, 12, BORDER)); e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setHintTextColor(MUTED); e.setHint("—"); e.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(96), WRAP); r.addView(e, p);
        TextView u = text(unit, 13, MUTED, false); u.setPadding(dp(8), 0, 0, 0); u.setMinWidth(dp(44)); r.addView(u);
        f.view = r; f.edit = e; return f;
    }
    Field textField(String label, String value, String hint) {
        Field f = new Field();
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        col.addView(body(label));
        EditText e = new EditText(c);
        e.setText(value); e.setTextColor(TEXT); e.setTextSize(14); e.setHint(hint); e.setHintTextColor(MUTED);
        e.setInputType(InputType.TYPE_CLASS_TEXT); e.setSingleLine(true);
        e.setBackground(shape(CARD2, 12, BORDER)); e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP); p.topMargin = dp(6); col.addView(e, p);
        f.view = col; f.edit = e; return f;
    }

    LinearLayout kv(String key, String value, int valueColor) {
        LinearLayout r = row(); r.setMinimumHeight(dp(34));
        r.addView(text(key, 14, MUTED, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView v = number(value, 14, valueColor); r.addView(v); r.setTag(v);
        return r;
    }

    LinearLayout numbered(int n, String s, boolean last) {
        LinearLayout r = row(); r.setGravity(Gravity.TOP); r.setPadding(0, dp(4), 0, dp(last ? 0 : 6));
        TextView b = text(String.valueOf(n), 12, ACCENT, true); b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(alpha(ACCENT, 0x22)); g.setStroke(dp(1), alpha(ACCENT, 0x66));
        b.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(24), dp(24)); p.rightMargin = dp(12); p.topMargin = dp(1);
        r.addView(b, p);
        TextView t = body(s); t.setTextSize(14); r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return r;
    }

    /** Four confidence dots. */
    LinearLayout dots(int filled, int color) {
        LinearLayout r = row(); r.setLayoutParams(new LinearLayout.LayoutParams(WRAP, WRAP));
        for (int i = 0; i < 4; i++) {
            View d = new View(c); GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
            g.setColor(i < filled ? color : alpha(MUTED, 0x44)); d.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(8), dp(8)); p.rightMargin = dp(4); r.addView(d, p);
        }
        return r;
    }

    /** Updates an existing progress bar in place instead of rebuilding it. */
    static void setProgress(View fill, View rest, double fraction) {
        float f = (float) Math.max(0.02, Math.min(1, fraction));
        ((LinearLayout.LayoutParams) fill.getLayoutParams()).weight = f;
        ((LinearLayout.LayoutParams) rest.getLayoutParams()).weight = 1f - f;
        fill.requestLayout(); rest.requestLayout();
    }

    LinearLayout progress(double fraction, int color) {
        LinearLayout outer = new LinearLayout(c); outer.setBackground(shape(CARD2, 999, 0));
        outer.setLayoutParams(block(0));
        View bar = new View(c); bar.setBackground(shape(color, 999, 0));
        float f = (float) Math.max(0.02, Math.min(1, fraction));
        outer.addView(bar, new LinearLayout.LayoutParams(0, dp(6), f));
        outer.addView(new View(c), new LinearLayout.LayoutParams(0, dp(6), 1f - f));
        return outer;
    }

    static String ft(double v) { return Double.isNaN(v) ? "—" : String.format(Locale.US, "%,d", Math.round(v)); }
    static String ft1(double v) { return Double.isNaN(v) ? "—" : String.format(Locale.US, "%,.1f", v); }
    static String fpm(double v) {
        if (Double.isNaN(v)) return "—";
        String arrow = v > 60 ? "▲ " : (v < -60 ? "▼ " : "▬ ");
        return arrow + String.format(Locale.US, "%,d", Math.round(Math.abs(v)));
    }
}
