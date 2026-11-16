package org.divinumofficium.reader;

import android.content.*;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;

/** Saved text only: this reader cannot make network requests. */
public class DailyReaderActivity extends DailyActivity {
    private DailyOfficeStore.Spec spec;
    private int hour, language, zoom, restoreScroll;
    private WebView web;
    private TextView status;
    private SharedPreferences prefs;
    static Intent intent(Context context, DailyOfficeStore.Spec spec, int hour) {
        return new Intent(context, DailyReaderActivity.class).putExtra("day", spec.day).putExtra("version", spec.version)
                .putExtra("mass", spec.mass).putExtra("hour", hour);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            spec = DailyOfficeStore.spec(this, getIntent().getStringExtra("day"), getIntent().getStringExtra("version"), getIntent().getBooleanExtra("mass", false));
            hour = state == null ? getIntent().getIntExtra("hour", 0) : state.getInt("hour"); spec.id(hour);
        } catch (Exception e) { finish(); return; }
        prefs = getSharedPreferences("reader", 0); language = prefs.getInt("languageMode", 0); zoom = prefs.getInt("zoom", 110);
        restoreScroll = state == null ? prefs.getInt(positionKey(), 0) : state.getInt("scroll");
        if(state==null&&getIntent().getBooleanExtra("savedPosition",false))restoreScroll=getIntent().getIntExtra("scroll",0);
        buildRoot(spec.title(hour), spec.displayDay() + " · " + spec.label, true);
        status = paragraph("Cópia salva · Sem uso de internet", 12); status.setTextColor(colors.accent);
        LinearLayout languages = row();
        String[] names = {"Português", "Latim", "Ambos"};
        for (int i = 0; i < names.length; i++) {
            final int mode = i; Button b = button(names[i]);
            b.setOnClickListener(v -> { restoreScroll = web.getScrollY(); language = mode; prefs.edit().putInt("languageMode", mode).apply(); load(); });
            languages.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(languages);
        web = new WebView(this); web.setBackgroundColor(colors.paper);
        web.getSettings().setJavaScriptEnabled(false); web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false); web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setBlockNetworkLoads(true); web.getSettings().setBlockNetworkImage(true);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setBuiltInZoomControls(true); web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setTextZoom(zoom);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !url.startsWith("https://offline.invalid/reading#");
            }
            @Override public void onPageFinished(WebView view, String url) {
                int y = restoreScroll; restoreScroll = 0; view.post(() -> view.scrollTo(0, y));
                String highlight=getIntent().getStringExtra("highlight");if(highlight!=null&&!highlight.trim().isEmpty()){view.findAllAsync(highlight);getIntent().removeExtra("highlight");}
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = row();
        Button smaller = button("A−"); smaller.setContentDescription("Diminuir o texto"); smaller.setOnClickListener(v -> zoom(-10));
        controls.addView(smaller, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button notes=button("☆");notes.setContentDescription("Favoritos, notas e busca nesta leitura");notes.setOnClickListener(v->ReadingTools.show(this,PersonalStore.daily(spec,hour,web.getScrollY()),web));controls.addView(notes,new LinearLayout.LayoutParams(0,dp(48),1));
        Button choose = button("Outras horas"); choose.setEnabled(!spec.mass); choose.setOnClickListener(v -> choose());
        controls.addView(choose, new LinearLayout.LayoutParams(0, dp(48), 2));
        Button larger = button("A+"); larger.setContentDescription("Aumentar o texto"); larger.setOnClickListener(v -> zoom(10));
        controls.addView(larger, new LinearLayout.LayoutParams(0, dp(48), 1)); root.addView(controls); load();
    }
    private void load() {
        DailyOfficeStore.Entry entry = DailyOfficeStore.get(this, spec, hour);
        if (entry == null) {
            status.setText("Esta leitura não está disponível. Volte e baixe-a com internet.");
            web.loadDataWithBaseURL("https://offline.invalid/reading", "<meta name='viewport' content='width=device-width'>", "text/html", "UTF-8", null); return;
        }
        try {
            String note = !entry.snapshot.optBoolean("bilingual") ? " · Tradução em português não identificada" : "";
            if (entry.snapshot.optInt("missingImages") > 0) note += " · Imagem não incluída";
            status.setText("Cópia salva · Sem uso de internet" + note);
            web.loadDataWithBaseURL("https://offline.invalid/reading", DailyOfficeStore.document(this, entry, dark, language), "text/html", "UTF-8", null);
        } catch (Exception e) { status.setText("Não foi possível abrir a cópia. Volte e baixe-a novamente."); }
    }
    private void choose() {
        String[] labels = new String[spec.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = spec.title(i) + (DailyOfficeStore.get(this, spec, i) == null ? " · não baixada" : " · offline");
        new android.app.AlertDialog.Builder(this).setTitle("Horas de " + spec.displayDay()).setItems(labels, (dialog, which) -> {
            if (DailyOfficeStore.get(this, spec, which) == null) { Toast.makeText(this, "Baixe esta hora com internet primeiro.", Toast.LENGTH_LONG).show(); return; }
            savePosition(); startActivity(intent(this, spec, which)); finish();
        }).setNegativeButton("Fechar", null).show();
    }
    private String positionKey() { return "dailyScroll:" + spec.key + ":" + hour; }
    private void savePosition() { if (web != null) prefs.edit().putInt(positionKey(), web.getScrollY()).apply(); }
    private void zoom(int change) { zoom = Math.max(70, Math.min(220, zoom + change)); prefs.edit().putInt("zoom", zoom).apply(); web.getSettings().setTextZoom(zoom); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("hour", hour); state.putInt("scroll", web == null ? 0 : web.getScrollY()); }
    @Override protected void onPause() { savePosition(); super.onPause(); }
    @Override protected void onDestroy() { if (web != null) { web.stopLoading(); ((android.view.ViewGroup) web.getParent()).removeView(web); web.destroy(); } super.onDestroy(); }
}
