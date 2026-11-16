package org.divinumofficium.reader;

import android.annotation.SuppressLint;
import android.content.*;
import android.os.*;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Foreground, resumable package preparation using the site's normal WebView session. */
public class DailyDownloadActivity extends DailyActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Job> jobs = new ArrayList<>();
    private WebView web;
    private TextView status, details, instructions;
    private ScrollView detailsScroll;
    private ProgressBar progress;
    private Button retry;
    private int current = -1, generation;
    private boolean running, closed, capturing;
    private String adapt, capture;
    private String batch;
    private int consecutiveFailures;
    private static class Job {
        final DailyOfficeStore.Spec spec; final int hour; String failure;
        Job(DailyOfficeStore.Spec spec, int hour) { this.spec = spec; this.hour = hour; }
    }
    static Intent intent(Context context, String day, String office, String mass) {
        Intent i = new Intent(context, DailyDownloadActivity.class).putExtra("day", day);
        if (office != null) i.putExtra("office", office);
        if (mass != null) i.putExtra("mass", mass);
        return i;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            String day = getIntent().getStringExtra("day");
            int days=getIntent().getIntExtra("days",1);
            if(days<1||days>7)throw new IllegalArgumentException();
            for(int offset=0;offset<days;offset++) {
            String currentDay=java.time.LocalDate.parse(day).plusDays(offset).toString();
            for (boolean mass : new boolean[] {false, true}) {
                String version = getIntent().getStringExtra(mass ? "mass" : "office");
                if (version == null) continue;
                DailyOfficeStore.Spec spec = DailyOfficeStore.spec(this, currentDay, version, mass);
                for (int hour = 0; hour < spec.size(); hour++) jobs.add(new Job(spec, hour));
            }
            }
            if (jobs.isEmpty()) throw new IllegalArgumentException();
            JSONObject pending=new JSONObject().put("day",day).put("days",days);
            for(String kind:new String[]{"office","mass"})if(getIntent().hasExtra(kind))pending.put(kind,getIntent().getStringExtra(kind));
            batch=pending.toString();
            adapt = BookStore.asset(this, "reader.js"); capture = BookStore.asset(this, "daily-capture.js");
        } catch (Exception e) { finish(); return; }
        build(); start();
    }
    @SuppressLint("SetJavaScriptEnabled") private void build() {
        buildRoot("Orações offline", jobs.get(0).spec.displayDay() + (getIntent().getIntExtra("days",1)>1?" · "+getIntent().getIntExtra("days",1)+" dias":""), false);
        instructions = paragraph("Mantenha esta tela aberta até concluir. Se sair, as leituras concluídas ficam guardadas e você poderá baixar as que faltarem depois.", 14);
        status = paragraph("Preparando…", 17); status.setTextColor(colors.accent); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(jobs.size()); root.addView(progress);
        detailsScroll = new ScrollView(this); details = text("", 13, colors.muted); details.setPadding(dp(20), dp(8), dp(20), dp(8)); detailsScroll.addView(details);
        root.addView(detailsScroll, new LinearLayout.LayoutParams(-1, dp(152)));
        web = new WebView(this); web.setBackgroundColor(colors.paper);
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setUseWideViewPort(true); web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !UrlPolicy.isOfficial(url) || !url.startsWith("https://");
            }
            @Override public void onPageFinished(WebView view, String url) { inspect(generation); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && isCurrent(request.getUrl().toString())) fail("Sem conexão ou site indisponível");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && isCurrent(request.getUrl().toString())) fail("Site retornou erro " + response.getStatusCode());
            }
            // TLS errors retain WebView's default cancellation.
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = row(); retry = button("Tentar faltantes"); retry.setOnClickListener(v -> start());
        actions.addView(retry, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button done = button("Voltar"); done.setOnClickListener(v -> finish()); actions.addView(done, new LinearLayout.LayoutParams(0, dp(52), 1)); root.addView(actions);
    }
    private boolean isCurrent(String url) {
        return running && current >= 0 && current < jobs.size() && DailyOfficeStore.matches(jobs.get(current).spec, jobs.get(current).hour, url);
    }
    private void start() {
        if (running) return;
        for (Job job : jobs) job.failure = null;
        consecutiveFailures=0;
        getSharedPreferences("reader",0).edit().putString("pendingBatch",batch).apply();
        detailsScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(152)));
        current = -1; running = true; retry.setEnabled(false); web.setVisibility(View.VISIBLE); next();
    }
    private void next() {
        handler.removeCallbacksAndMessages(null); generation++; capturing = false;
        if (closed) return;
        while (++current < jobs.size()) {
            Job job = jobs.get(current);
            if (DailyOfficeStore.get(this, job.spec, job.hour) != null) continue;
            update(); status.setText("Baixando " + job.spec.title(job.hour) + " · " + job.spec.displayDay() + "…");
            final int ticket = generation;
            web.loadUrl(DailyOfficeStore.request(job.spec, job.hour));
            handler.postDelayed(() -> { if (running && generation == ticket) fail("Tempo esgotado; tente novamente"); }, 45000);
            return;
        }
        running = false; web.stopLoading(); web.setVisibility(View.GONE);
        detailsScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        instructions.setText("Volte e abra as horas normalmente. As cópias disponíveis também ficam em Biblioteca → Salvos, mesmo depois de fechar o app.");
        CookieManager.getInstance().flush(); update();
    }
    private void inspect(int ticket) {
        if (closed || !running || capturing || ticket != generation || !isCurrent(web.getUrl())) return;
        capturing = true;
        web.evaluateJavascript(adapt, ignored -> {
            if (closed || !running || ticket != generation) return;
            web.evaluateJavascript(capture, result -> {
                if (closed || !running || ticket != generation) return;
                capturing = false;
                try {
                    JSONObject snapshot = new JSONObject(result);
                    if (!snapshot.optBoolean("valid")) {
                        handler.postDelayed(() -> inspect(ticket), 1500); return;
                    }
                    Job job = jobs.get(current); DailyOfficeStore.put(this, job.spec, job.hour, snapshot); consecutiveFailures=0; next();
                } catch (Exception e) { fail("Não foi possível guardar esta leitura; tente novamente"); }
            });
        });
    }
    private void fail(String reason) {
        if (closed || !running || current < 0 || current >= jobs.size()) return;
        jobs.get(current).failure = reason; generation++; web.stopLoading(); handler.post(this::next);
        if(++consecutiveFailures>=3)current=jobs.size();
    }
    private void update() {
        int saved = 0; StringBuilder lines = new StringBuilder();
        DailyOfficeStore.Spec previous = null;
        for (Job job : jobs) {
            if (previous != job.spec) { if (previous != null) lines.append('\n'); lines.append(job.spec.displayDay()).append(" · ").append(job.spec.mass ? "MISSA · " : "OFÍCIO · ").append(job.spec.label).append('\n'); previous = job.spec; }
            DailyOfficeStore.Entry entry = DailyOfficeStore.get(this, job.spec, job.hour);
            boolean okay = entry != null; if (okay) saved++;
            lines.append(okay ? "✓ " : job.failure != null ? "! " : "○ ").append(job.spec.title(job.hour));
            if (okay) {
                if (!entry.snapshot.optBoolean("bilingual")) lines.append(" · tradução em português não identificada");
                if (entry.snapshot.optInt("missingImages") > 0) lines.append(" · imagem não incluída");
            } else if (job.failure != null) lines.append(" · ").append(job.failure);
            lines.append('\n');
        }
        details.setText(lines.toString()); progress.setProgress(saved);
        if(!running&&saved==jobs.size()&&batch.equals(getSharedPreferences("reader",0).getString("pendingBatch","")))getSharedPreferences("reader",0).edit().remove("pendingBatch").apply();
        if (!running) status.setText(saved == jobs.size() ? "Pronto: " + saved + "/" + jobs.size() + " leituras disponíveis offline" : saved + "/" + jobs.size() + " salvas. Tente novamente para baixar as que faltam.");
        retry.setEnabled(!running && saved < jobs.size());
    }
    @Override protected void onDestroy() {
        closed = true; generation++; handler.removeCallbacksAndMessages(null);
        if (web != null) { web.stopLoading(); ((android.view.ViewGroup) web.getParent()).removeView(web); web.destroy(); }
        super.onDestroy();
    }
}
