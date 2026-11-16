package org.divinumofficium.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.*;

/** Reader for the catalog's own sources, with private downloads and no native JS bridge. */
public class BookReaderActivity extends Activity {
    private BookCatalog.Book book;
    private AppAppearance.Palette colors;
    private boolean dark;
    private volatile boolean closed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> task;
    private FrameLayout frame;
    private ProgressBar progress;
    private LinearLayout error;
    private TextView errorText, status, position;
    private WebView web;
    private ImageView image;
    private ScrollView pdfScroll;
    private HorizontalScrollView horizontal;
    private Button previous, next;
    private File pdf;
    private int page, pages, textZoom, restoreScroll;
    private float pdfZoom;
    private String address;
    private int generation, renderGeneration;
    private ArrayList<String> history = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        dark = AppAppearance.isDark(this);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        colors = AppAppearance.palette(dark);
        book = BookStore.catalog(this).find(getIntent().getStringExtra("bookId"));
        if (book == null) { finish(); return; }
        android.content.SharedPreferences prefs = getSharedPreferences("reader", 0);
        textZoom = prefs.getInt("bookZoom", 110);
        pdfZoom = prefs.getFloat("pdfZoom", 1f);
        page = state == null ? prefs.getInt("bookPage:" + book.id, 0) : state.getInt("page", 0);
        restoreScroll = state == null ? prefs.getInt("bookScroll:" + book.id, 0) : state.getInt("scroll", 0);
        address = state == null ? book.url : state.getString("address", book.url);
        if(state==null&&getIntent().getBooleanExtra("savedPosition",false)){
            page=getIntent().getIntExtra("page",0);restoreScroll=getIntent().getIntExtra("scroll",0);
            address=getIntent().getStringExtra("address");if(address==null)address=book.url;
        }
        if (!BookCatalog.allows(book, address)) address = book.url;
        if (state != null && state.getStringArrayList("history") != null) history = state.getStringArrayList("history");
        build();
        load(address, history.isEmpty());
    }

    private void build() {
        LinearLayout root = column(); root.setBackgroundColor(colors.paper);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(i.left, i.top, i.right, i.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); AppAppearance.window(this, dark); root.requestApplyInsets();
        LinearLayout header = row(); header.setPadding(dp(8), dp(6), dp(8), dp(6));
        Button back = button("‹", "Voltar à leitura anterior"); back.setOnClickListener(v -> onBackPressed());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(52)));
        LinearLayout heading = column();
        TextView title = text(book.title, 19, colors.ink); title.setMaxLines(2);
        heading.addView(title); heading.addView(text(book.author + " · " + book.language, 12, colors.muted));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button theme = button("◐", "Aparência: modo claro ou escuro");
        theme.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Aparência")
                .setSingleChoiceItems(AppAppearance.LABELS, AppAppearance.selected(this), (dialog, which) -> {
                    AppAppearance.save(this, which); dialog.dismiss(); recreate();
                }).setNegativeButton("Fechar", null).show());
        header.addView(theme, new LinearLayout.LayoutParams(dp(44), dp(52))); root.addView(header);
        status = text("Preparando leitura…", 12, colors.accent); status.setPadding(dp(20), dp(8), dp(20), dp(12));
        status.setOnClickListener(v -> source()); status.setContentDescription("Abrir fonte: " + book.source); root.addView(status);
        frame = new FrameLayout(this); root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        if ("pdf".equals(book.format)) {
            pdfScroll = new ScrollView(this); horizontal = new HorizontalScrollView(this);
            image = new ImageView(this); image.setContentDescription("Página do livro " + book.title);
            if (dark) image.setColorFilter(AppAppearance.nightFilter());
            horizontal.addView(image); pdfScroll.addView(horizontal); frame.addView(pdfScroll, new FrameLayout.LayoutParams(-1, -1));
        } else {
            web = new WebView(this); web.setBackgroundColor(colors.paper);
            web.getSettings().setJavaScriptEnabled(false); web.getSettings().setAllowFileAccess(false);
            web.getSettings().setAllowContentAccess(false); web.getSettings().setDomStorageEnabled(false);
            web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            web.getSettings().setBuiltInZoomControls(true); web.getSettings().setDisplayZoomControls(false);
            web.getSettings().setTextZoom(textZoom);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (!request.isForMainFrame()) return false;
                    String target = request.getUrl().toString().replaceFirst("(?i)^http:", "https:");
                    if (BookCatalog.allows(book, target)) {
                        if (target.split("#", 2)[0].equals(address.split("#", 2)[0])) return false;
                        restoreScroll = 0; load(target, true);
                    } else if (BookCatalog.https(target)) external(target);
                    return true;
                }
                @Override public void onPageFinished(WebView view, String url) {
                    if (restoreScroll > 0) { int y = restoreScroll; restoreScroll = 0; web.post(() -> web.scrollTo(0, y)); }
                    String highlight=getIntent().getStringExtra("highlight");if(highlight!=null&&!highlight.trim().isEmpty()){view.findAllAsync(highlight);getIntent().removeExtra("highlight");}
                }
            });
            frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        }
        progress = new ProgressBar(this);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(colors.accent));
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER); frame.addView(progress, center);
        error = column(); error.setPadding(dp(24), dp(24), dp(24), dp(24)); error.setGravity(Gravity.CENTER);
        error.setBackgroundColor(colors.paper); error.setVisibility(View.GONE);
        errorText = text("", 17, colors.ink); error.addView(errorText);
        Button retry = button("Tentar novamente", "Tentar abrir a leitura novamente"); retry.setOnClickListener(v -> load(address, false)); error.addView(retry);
        Button source = button("Abrir fonte no navegador", "Abrir fonte no navegador"); source.setOnClickListener(v -> source()); error.addView(source);
        frame.addView(error, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout controls = row(); controls.setPadding(dp(8), dp(4), dp(8), dp(4));
        previous = button("‹", "Página anterior"); previous.setOnClickListener(v -> { if (page > 0) { page--; renderPdf(); } });
        next = button("›", "Próxima página"); next.setOnClickListener(v -> { if (page + 1 < pages) { page++; renderPdf(); } });
        if (image != null) controls.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(48)));
        Button smaller = button("A−", "Diminuir o texto ou a página"); smaller.setOnClickListener(v -> zoom(-1));
        controls.addView(smaller, new LinearLayout.LayoutParams(dp(52), dp(48)));
        Button notes=button("☆","Favoritos, notas e busca nesta leitura");notes.setOnClickListener(v->ReadingTools.show(this,PersonalStore.book(book,address,page,web==null?0:web.getScrollY()),web));controls.addView(notes,new LinearLayout.LayoutParams(dp(48),dp(48)));
        position = text("", 13, colors.muted); position.setGravity(Gravity.CENTER);
        position.setContentDescription(image == null ? "Tamanho do texto" : "Ir para uma página do PDF");
        if (image != null) position.setOnClickListener(v -> {
            if (pages == 0) return;
            EditText number = new EditText(this); number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            number.setText(String.valueOf(page + 1)); number.setSelectAllOnFocus(true);
            new AlertDialog.Builder(this).setTitle("Ir para a página (1–" + pages + ")").setView(number)
                    .setNegativeButton("Cancelar", null).setPositiveButton("Ir", (dialog, which) -> {
                        try {
                            int target = Integer.parseInt(number.getText().toString());
                            if (target < 1 || target > pages) throw new NumberFormatException();
                            page = target - 1; renderPdf();
                        } catch (NumberFormatException e) { Toast.makeText(this, "Escolha uma página entre 1 e " + pages + ".", Toast.LENGTH_LONG).show(); }
                    }).show();
        });
        controls.addView(position, new LinearLayout.LayoutParams(0, -2, 1));
        Button larger = button("A+", "Aumentar o texto ou a página"); larger.setOnClickListener(v -> zoom(1));
        controls.addView(larger, new LinearLayout.LayoutParams(dp(52), dp(48)));
        if (image != null) controls.addView(next, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(controls); updatePosition();
    }

    private void load(String url, boolean push) {
        final int ticket = ++generation;
        address = url; error.setVisibility(View.GONE); progress.setVisibility(View.VISIBLE);
        status.setText(BookStore.cached(this, book) ? book.source + " · Cópia disponível offline" : book.source + " · Abrindo com internet…");
        if (task != null) task.cancel(true);
        task = worker.submit(() -> {
            try {
                File file = BookStore.load(this, book, url);
                String html = null;
                if (web != null) html = BookStore.present(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), BookStore.asset(this, "book-reader.css"), colors);
                String document = html;
                runOnUiThread(() -> {
                    if (closed || ticket != generation) return;
                    if (push && (history.isEmpty() || !history.get(history.size() - 1).equals(url))) history.add(url);
                    status.setText(book.source + (BookStore.cached(this, book) ? " · Disponível offline" : " · Consulta online"));
                    if (web != null) { web.getSettings().setBlockNetworkLoads(book.offline && book.url.equals(url) && BookStore.cached(this,book)); web.loadDataWithBaseURL(url, document, "text/html", "UTF-8", url); progress.setVisibility(View.GONE); }
                    else { pdf = file; renderPdf(); }
                });
            } catch (Exception e) { failure(e); }
        });
    }

    private void renderPdf() {
        if (pdf == null) return;
        final int ticket = ++renderGeneration;
        progress.setVisibility(View.VISIBLE); previous.setEnabled(false); next.setEnabled(false);
        final int requested = page;
        final int viewport = frame.getWidth() > 0 ? frame.getWidth() : getResources().getDisplayMetrics().widthPixels;
        final int width = Math.max(320, Math.round(viewport * pdfZoom));
        if (task != null) task.cancel(true);
        task = worker.submit(() -> {
            try (ParcelFileDescriptor file = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(file)) {
                int count = renderer.getPageCount(), current = Math.min(Math.max(0, requested), count - 1);
                try (PdfRenderer.Page p = renderer.openPage(current)) {
                    double ratio = (double) p.getHeight() / p.getWidth();
                    int renderWidth = Math.min(1600, width);
                    if (renderWidth * (renderWidth * ratio) > 4000000) renderWidth = (int) Math.sqrt(4000000 / ratio);
                    Bitmap bitmap = Bitmap.createBitmap(Math.max(1, renderWidth), Math.max(1, (int) (renderWidth * ratio)), Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE); p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    runOnUiThread(() -> {
                        if (closed || ticket != renderGeneration) { bitmap.recycle(); return; }
                        page = current; pages = count;
                        image.setImageBitmap(bitmap);
                        image.setLayoutParams(new android.widget.FrameLayout.LayoutParams(width, (int) (width * ratio)));
                        pdfScroll.scrollTo(0, 0); horizontal.scrollTo(0, 0); progress.setVisibility(View.GONE); updatePosition();
                    });
                }
            } catch (Exception e) { failure(e); }
        });
    }

    private void zoom(int direction) {
        if (web != null) {
            textZoom = Math.max(80, Math.min(220, textZoom + direction * 10));
            web.getSettings().setTextZoom(textZoom); getSharedPreferences("reader", 0).edit().putInt("bookZoom", textZoom).apply();
        } else {
            pdfZoom = Math.max(1, Math.min(3, pdfZoom + direction * .5f));
            getSharedPreferences("reader", 0).edit().putFloat("pdfZoom", pdfZoom).apply(); renderPdf();
        }
        updatePosition();
    }
    private void updatePosition() {
        if (position == null) return;
        position.setText(web != null ? textZoom + "%" : (pages == 0 ? "PDF" : (page + 1) + " / " + pages));
        previous.setEnabled(image != null && page > 0); next.setEnabled(image != null && page + 1 < pages);
    }
    private void failure(Exception e) {
        if (closed || Thread.currentThread().isInterrupted()) return;
        runOnUiThread(() -> {
            if (closed) return;
            progress.setVisibility(View.GONE); error.setVisibility(View.VISIBLE);
            errorText.setText("Não foi possível abrir a leitura. Confira a conexão e tente novamente.\n\n" + (e.getMessage() == null ? "A fonte pode estar indisponível." : e.getMessage()));
        });
    }
    private void source() { external(book.sourceUrl); }
    private void external(String url) {
        if (!BookCatalog.https(url)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (android.content.ActivityNotFoundException e) { Toast.makeText(this, "Nenhum navegador disponível.", Toast.LENGTH_LONG).show(); }
    }
    @Override public void onBackPressed() {
        if (web != null && history.size() > 1) { history.remove(history.size() - 1); restoreScroll = 0; load(history.get(history.size() - 1), false); }
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("address", address); state.putInt("page", page);
        state.putInt("scroll", web == null ? 0 : web.getScrollY()); state.putStringArrayList("history", history);
    }
    @Override protected void onPause() {
        if (book != null) getSharedPreferences("reader", 0).edit().putInt("bookPage:" + book.id, page)
                .putInt("bookScroll:" + book.id, web == null ? 0 : web.getScrollY()).apply();
        if (web != null) web.onPause(); super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (book != null && AppAppearance.isDark(this) != dark) { recreate(); return; }
        if (web != null) web.onResume();
    }
    @Override protected void onDestroy() {
        closed = true; if (task != null) task.cancel(true); worker.shutdownNow();
        if (web != null) { ((android.view.ViewGroup) web.getParent()).removeView(web); web.destroy(); }
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String text, int size, int color) { TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); return v; }
    private Button button(String label, String description) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(colors.accent);
        b.setContentDescription(description); b.setTextSize(16); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(4), 0, dp(4), 0);
        android.util.TypedValue value = new android.util.TypedValue(); getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        b.setBackgroundResource(value.resourceId); return b;
    }
}
