package org.divinumofficium.reader;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Build;
import android.view.View;
import android.view.WindowInsetsController;

final class AppAppearance {
    static final String[] MODES = {"light", "dark", "system"};
    static final String[] LABELS = {"Claro", "Escuro", "Seguir o sistema"};

    static boolean isDark(Context context) {
        String mode = context.getSharedPreferences("reader", 0).getString("appearance", "system");
        return "dark".equals(mode) || ("system".equals(mode)
                && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }

    static int selected(Context context) {
        String mode = context.getSharedPreferences("reader", 0).getString("appearance", "system");
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return i;
        return 2;
    }

    static void save(Context context, int index) {
        if (index < 0 || index >= MODES.length) throw new IllegalArgumentException("Tema inválido");
        context.getSharedPreferences("reader", 0).edit().putString("appearance", MODES[index]).apply();
    }

    static Palette palette(boolean dark) { return new Palette(dark); }

    static void window(Activity activity, boolean dark) {
        Palette p = palette(dark);
        activity.getWindow().setStatusBarColor(p.paper);
        activity.getWindow().setNavigationBarColor(dark || Build.VERSION.SDK_INT >= 27 ? p.paper : Color.rgb(119, 46, 58));
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = activity.getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else activity.getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | (Build.VERSION.SDK_INT >= 27 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
    }

    static ColorMatrixColorFilter nightFilter() {
        return new ColorMatrixColorFilter(new ColorMatrix(new float[] {
                -.85f, 0, 0, 0, 240,
                0, -.85f, 0, 0, 240,
                0, 0, -.85f, 0, 240,
                0, 0, 0, 1, 0
        }));
    }

    static final class Palette {
        final int paper, surface, ink, muted, accent, line, selected, primary;
        Palette(boolean dark) {
            paper = Color.parseColor(dark ? "#151619" : "#F7F6F2");
            surface = Color.parseColor(dark ? "#222226" : "#FFFFFF");
            ink = Color.parseColor(dark ? "#F0EAE2" : "#2C2520");
            muted = Color.parseColor(dark ? "#B7B1AC" : "#6D6459");
            accent = Color.parseColor(dark ? "#E3A0B2" : "#772E3A");
            line = Color.parseColor(dark ? "#39343A" : "#E2D9CA");
            selected = Color.parseColor(dark ? "#422B35" : "#F2E8E8");
            primary = Color.parseColor(dark ? "#8F3C53" : "#772E3A");
        }
    }
}
