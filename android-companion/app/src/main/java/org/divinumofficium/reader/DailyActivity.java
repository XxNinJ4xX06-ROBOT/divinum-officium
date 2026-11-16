package org.divinumofficium.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Shared native surfaces for daily downloads and their offline reader. */
abstract class DailyActivity extends Activity {
    boolean dark;
    AppAppearance.Palette colors;
    LinearLayout root;
    @Override public void onCreate(Bundle state) {
        dark = AppAppearance.isDark(this);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        colors = AppAppearance.palette(dark);
    }
    void buildRoot(String title, String subtitle, boolean appearance) {
        root = column(); root.setBackgroundColor(colors.paper);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(i.left, i.top, i.right, i.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); AppAppearance.window(this, dark); root.requestApplyInsets();
        LinearLayout header = row(); header.setPadding(dp(8), dp(8), dp(8), dp(8));
        Button back = button("‹"); back.setContentDescription("Voltar"); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        LinearLayout labels = column(); labels.addView(text(title, 20, colors.ink)); labels.addView(text(subtitle, 12, colors.muted));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (appearance) {
            Button theme = button("◐"); theme.setContentDescription("Aparência: modo claro ou escuro");
            theme.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Aparência")
                    .setSingleChoiceItems(AppAppearance.LABELS, AppAppearance.selected(this), (dialog, which) -> {
                        AppAppearance.save(this, which); dialog.dismiss(); recreate();
                    }).setNegativeButton("Fechar", null).show());
            header.addView(theme, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        root.addView(header);
    }
    int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    Button button(String value) { Button v = new Button(this); v.setText(value); v.setAllCaps(false); v.setTextColor(colors.accent); v.setMinWidth(0); v.setMinimumWidth(0); return v; }
    TextView paragraph(String value, int size) {
        TextView v = text(value, size, colors.muted); v.setPadding(dp(20), dp(8), dp(20), dp(8)); root.addView(v); return v;
    }
    @Override protected void onResume(){super.onResume();if(colors!=null&&AppAppearance.isDark(this)!=dark)recreate();}
}
