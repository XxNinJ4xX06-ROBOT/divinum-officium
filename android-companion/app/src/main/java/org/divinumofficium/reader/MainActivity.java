package org.divinumofficium.reader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.Calendar;

public class MainActivity extends Activity {
    private int PAPER, INK, WINE, MUTED, LINE, SURFACE, SELECTED, PRIMARY;
    private boolean dark;
    private boolean archiveDark;
    private SharedPreferences prefs;
    private LinearLayout root, home, reader;
    private LinearLayout bottomNav, languageBar;
    private TextView toolbarSubtitle;
    private final Button[] navigation = new Button[4];
    private final Button[] languages = new Button[3];
    private final Calendar selectedDay = Calendar.getInstance();
    private int screen;
    private RubricCatalog rubrics;
    private BookCatalog books;
    private int librarySection;
    private String libraryQuery = "";
    private String officeVersion, massVersion;
    private boolean originalLayout;
    private boolean selectingRubric, selectingMass;
    private String rubricFilter = "";
    private String readerCss, readerScript;
    private ScrollView errorPanel;
    private TextView toolbarTitle, errorTitle, errorText;
    private ProgressBar progress;
    private WebView web;
    private Button previous, next, save;
    private boolean reading, local, loaded, failed, saving, clearHistory;
    private String section = "Ofício", retryUrl = UrlPolicy.OFFICE;

    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        dark = AppAppearance.isDark(this);
        setTheme(dark ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        AppAppearance.Palette colors = AppAppearance.palette(dark);
        PAPER = colors.paper; INK = colors.ink; WINE = colors.accent; MUTED = colors.muted;
        LINE = colors.line; SURFACE = colors.surface; SELECTED = colors.selected; PRIMARY = colors.primary;
        books = BookStore.catalog(this);
        librarySection = state == null ? 0 : state.getInt("librarySection", 0);
        libraryQuery = state == null ? "" : state.getString("libraryQuery", "");
        try { rubrics = new RubricCatalog(asset("rubrics.json")); }
        catch (JSONException e) { throw new IllegalStateException("Catálogo de rubricas inválido", e); }
        String legacyVersion = RubricCatalog.legacyValue(prefs.getInt("version", 0));
        officeVersion = rubrics.selected(false, prefs.getString("officeVersion", legacyVersion)).value;
        massVersion = rubrics.selected(true, prefs.getString("massVersion", legacyVersion)).value;
        if (state != null) screen = state.getInt("screen", 0);
        if (state != null) selectedDay.setTimeInMillis(state.getLong("day", selectedDay.getTimeInMillis()));
        readerCss = asset("reader.css");
        readerScript = asset("reader.js");
        buildUi();
        if (state != null && state.getBoolean("reading")) {
            local = state.getBoolean("local");
            archiveDark = state.getBoolean("archiveDark", false);
            section = state.getString("section", "Ofício");
            retryUrl = state.getString("retryUrl", UrlPolicy.OFFICE);
            configureSource(local);
            Bundle browserState = state.getBundle("web");
            if (browserState != null && web.restoreState(browserState) != null
                    && (UrlPolicy.isOfficial(web.getUrl()) || isSavedArchive(web.getUrl()))) {
                showReader();
                loaded = !state.getBoolean("failed");
                if (!loaded) showError("A página não foi carregada", "Toque em Tentar novamente para abrir o site.");
                updateControls();
            } else showHome();
        } else {
            showScreen(state == null ? 0 : state.getInt("screen", 0));
            if (state != null && state.getBoolean("selectingRubric")) {
                showRubrics(state.getBoolean("selectingMass"), state.getString("rubricFilter", ""));
            }
        }
        handleLaunch(getIntent());
    }

    private void handleLaunch(Intent intent) {
        if(intent.hasExtra("reminderHour")){
            int hour=intent.getIntExtra("reminderHour",-1);intent.removeExtra("reminderHour");
            if(hour>=0&&hour<8){selectedDay.setTimeInMillis(System.currentTimeMillis());showScreen(1);openDailyOrOnline(false,hour);}
        } else if(UrlPolicy.isOfficial(intent.getStringExtra("openUrl"))){
            String url=intent.getStringExtra("openUrl"),title=intent.getStringExtra("openTitle");intent.removeExtra("openUrl");openPage(url,title==null?"Leitura":title);
        }
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleLaunch(intent);}

    private void buildUi() {
        root = column();
        root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(root);
        AppAppearance.window(this, dark);
        root.requestApplyInsets();

        LinearLayout toolbar = row();
        toolbar.setPadding(dp(8), dp(4), dp(8), dp(4));
        Button back = smallButton("‹", "Voltar");
        back.setTextSize(30);
        back.setOnClickListener(v -> { if (reading || selectingRubric) showScreen(screen); else showHome(); });
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titleGroup = column();
        toolbarTitle = text("Divinum", 21, INK);
        toolbarTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        toolbarSubtitle = text("Seu momento de oração", 12, MUTED);
        titleGroup.addView(toolbarTitle);
        titleGroup.addView(toolbarSubtitle);
        toolbar.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1));
        Button appearance = smallButton("◐", "Aparência: modo claro ou escuro");
        appearance.setTextSize(22);
        appearance.setOnClickListener(v -> showAppearance());
        toolbar.addView(appearance, new LinearLayout.LayoutParams(dp(44), dp(52)));
        Button menu = smallButton("⋮", "Mais opções");
        menu.setTextSize(27);
        menu.setOnClickListener(this::showMenu);
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(52)));
        root.addView(toolbar);
        root.addView(divider(), new LinearLayout.LayoutParams(-1, dp(1)));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        home = column();
        home.setFocusableInTouchMode(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(home);
        home.setPadding(dp(20), dp(20), dp(20), dp(24));
        content.addView(scroll);
        scroll.setTag("home");

        reader = column();
        reader.setVisibility(View.GONE);
        content.addView(reader, new FrameLayout.LayoutParams(-1, -1));
        languageBar = row();
        languageBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        String[] languageLabels = {"Português", "Latim", "Ambos"};
        for (int i = 0; i < languages.length; i++) {
            final int index = i;
            languages[i] = action(languageLabels[i], false);
            languages[i].setTextSize(13);
            languages[i].setOnClickListener(v -> {
                prefs.edit().putInt("languageMode", index).apply();
                updateLanguage();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            languageBar.addView(languages[i], lp);
        }
        reader.addView(languageBar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(WINE));
        reader.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        FrameLayout browser = new FrameLayout(this);
        reader.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        web = new WebView(this);
        configureWebView();
        browser.addView(web, new FrameLayout.LayoutParams(-1, -1));
        errorPanel = new ScrollView(this);
        errorPanel.setFillViewport(true);
        errorPanel.setBackgroundColor(PAPER);
        errorPanel.setVisibility(View.GONE);
        browser.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout errorContent = column();
        errorContent.setGravity(Gravity.CENTER);
        errorContent.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorPanel.addView(errorContent);
        errorTitle = text("", 25, WINE);
        errorTitle.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        errorText = text("", 16, MUTED);
        errorText.setPadding(0, dp(16), 0, dp(24));
        errorContent.addView(errorTitle);
        errorContent.addView(errorText);
        Button retry = action("Tentar novamente", true);
        retry.setOnClickListener(v -> {
            if (local) openArchive(web.getUrl(), section);
            else openPage(retryUrl, section);
        });
        errorContent.addView(retry, fullButton());
        Button browserButton = action("Abrir site no navegador", false);
        browserButton.setOnClickListener(v -> external(UrlPolicy.isOfficial(retryUrl) ? retryUrl : UrlPolicy.BASE));
        errorContent.addView(browserButton, fullButton());
        Button savedButton = action("Minhas leituras salvas", false);
        savedButton.setOnClickListener(v -> showSaved());
        errorContent.addView(savedButton, fullButton());

        reader.addView(divider(), new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout tools = row();
        tools.setPadding(dp(8), dp(4), dp(8), dp(4));
        previous = smallButton("‹", "Página anterior");
        previous.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        next = smallButton("›", "Próxima página");
        next.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        Button font = smallButton("Aa", "Ajustar tamanho do texto");
        font.setOnClickListener(v -> showFont());
        save = smallButton("Salvar", "Salvar esta página para ler sem internet");
        save.setOnClickListener(v -> savePage());
        for (Button button : new Button[] {previous, next, font, save}) {
            tools.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        reader.addView(tools);
        root.addView(divider(), new LinearLayout.LayoutParams(-1, dp(1)));
        bottomNav = row();
        bottomNav.setBackgroundColor(SURFACE);
        bottomNav.setPadding(dp(8), dp(6), dp(8), dp(6));
        String[] names = {"Início", "Ofício", "Missa", "Biblioteca"};
        int[] icons = {R.drawable.nav_home, R.drawable.nav_hours, R.drawable.nav_mass, R.drawable.nav_saved};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            Button button = smallButton(names[i], names[i]);
            button.setTextSize(11);
            button.setCompoundDrawablesWithIntrinsicBounds(0, icons[i], 0, 0);
            button.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(WINE));
            button.setCompoundDrawablePadding(dp(4));
            button.setPadding(dp(4), dp(8), dp(4), dp(4));
            button.setOnClickListener(v -> showScreen(index));
            navigation[i] = button;
            bottomNav.addView(button, new LinearLayout.LayoutParams(0, dp(64), 1));
        }
        root.addView(bottomNav);
    }

    private void createHome() {
        TextView dayTitle=text("Seu dia de oração",27,INK);dayTitle.setTypeface(Typeface.create("serif",Typeface.NORMAL));home.addView(dayTitle);
        DailyOfficeStore.Spec daily=dailySpec(false),massSpec=dailySpec(true);
        String dayHeading=TodayInfo.heading(this,daily);
        TextView celebration=text(dayHeading.isEmpty()?"Baixe as orações para ver a celebração e os dados litúrgicos informados pela fonte.":dayHeading,16,dayHeading.isEmpty()?MUTED:WINE);
        celebration.setPadding(0,dp(12),0,dp(12));home.addView(celebration);
        home.addView(text("Ofício: "+daily.label+"\n"+DailyOfficeStore.count(this,daily)+"/8 horas offline · Missa: "+DailyOfficeStore.count(this,massSpec)+"/1 offline",13,MUTED));
        try{
            JSONObject pending=new JSONObject(prefs.getString("pendingBatch","{}"));
            if(pending.has("day"))card("DOWNLOAD PENDENTE","Retomar download iniciado",pending.getString("day")+" · "+pending.optInt("days",1)+" dia(s)",()->
                startActivity(DailyDownloadActivity.intent(this,pending.optString("day"),pending.has("office")?pending.optString("office"):null,pending.has("mass")?pending.optString("mass"):null).putExtra("days",pending.optInt("days",1))));
        }catch(JSONException ignored){}
        LinearLayout prepare=row();Button day=action("Baixar este dia",true),days=action("Baixar vários dias",false);
        day.setOnClickListener(v->downloadDay());days.setOnClickListener(v->downloadDays());prepare.addView(day,new LinearLayout.LayoutParams(0,dp(56),1));prepare.addView(days,new LinearLayout.LayoutParams(0,dp(56),1));home.addView(prepare);
        LinearLayout access=row();Button laudes=action("Laudes",false),vespers=action("Vésperas",false),todayMass=action("Missa",false);
        laudes.setOnClickListener(v->openDailyOrOnline(false,1));vespers.setOnClickListener(v->openDailyOrOnline(false,6));todayMass.setOnClickListener(v->openDailyOrOnline(true,0));
        for(Button shortcut:new Button[]{laudes,vespers,todayMass})access.addView(shortcut,new LinearLayout.LayoutParams(0,dp(52),1));home.addView(access);
        sectionLabel("UM MOMENTO PARA REZAR");
        LinearLayout hero = column();
        hero.setPadding(dp(24), dp(24), dp(24), dp(24));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {Color.rgb(119, 46, 58), Color.rgb(72, 30, 44)});
        gradient.setCornerRadius(dp(24));
        hero.setBackground(gradient);
        TextView mark = text("✝   DIVINUM OFFICIUM", 11, Color.rgb(236, 213, 169));
        mark.setLetterSpacing(.12f);
        hero.addView(mark);
        TextView heading = text("Um tempo\npara o sagrado.", 33, Color.WHITE);
        heading.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        heading.setPadding(0, dp(20), 0, dp(12));
        hero.addView(heading);
        hero.addView(text("Recolha-se. Escolha uma hora\ne comece sua oração.", 15, Color.rgb(237, 222, 220)));
        Button start = action("Escolher uma hora  →", false);
        start.setBackground(background(Color.rgb(247, 235, 211), Color.TRANSPARENT, 12));
        start.setTextColor(Color.rgb(119, 46, 58));
        start.setOnClickListener(v -> showScreen(1));
        LinearLayout.LayoutParams startLp = fullButton();
        startLp.topMargin = dp(24);
        hero.addView(start, startLp);
        home.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        String last = prefs.getString("lastReading", "");
        if (UrlPolicy.isOfficial(last)) {
            sectionLabel("CONTINUAR LEITURA");
            card("", prefs.getString("lastTitle", "Sua última leitura"), "Retomar a última página carregada", () -> openPage(last, prefs.getString("lastTitle", "Leitura")));
        }
        sectionLabel("SEU DIA DE ORAÇÃO");
        LinearLayout shortcuts = row();
        Button office = action("Ofício Divino\nRezar as horas", false);
        Button mass = action("Santa Missa\nAcompanhar o missal", false);
        office.setOnClickListener(v -> showScreen(1));
        mass.setOnClickListener(v -> showScreen(2));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(92), 1);
        left.rightMargin = dp(6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(92), 1);
        right.leftMargin = dp(6);
        shortcuts.addView(office, left);
        shortcuts.addView(mass, right);
        home.addView(shortcuts);
        sectionLabel("LEVE SUA LEITURA COM VOCÊ");
        card("", "Sua biblioteca", "Guarde uma leitura e leve-a para onde for, mesmo sem internet.", this::showSaved);
        card("", "Lembretes de oração", "Escolha as horas e os horários dos seus avisos opcionais.", ()->startActivity(new Intent(this,ReminderActivity.class)));
        card("", "Favoritos e notas", "Seus trechos, pontos de leitura e backup dos dados pessoais.", ()->startActivity(new Intent(this,PersonalActivity.class)));
        home.addView(text("Leitor independente · Textos do Divinum Officium", 11, MUTED));
    }

    private void sectionLabel(String title) {
        TextView label = text(title, 11, MUTED);
        label.setLetterSpacing(.1f);
        label.setPadding(0, dp(28), 0, dp(14));
        home.addView(label);
    }

    void showScreen(int target) {
        screen = Math.max(0, Math.min(3, target));
        selectingRubric = false;
        rubricFilter = "";
        reading = false;
        web.stopLoading();
        reader.setVisibility(View.GONE);
        ((View) home.getParent()).setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
        toolbarTitle.setText(new String[] {DailyOfficeStore.day(selectedDay).equals(DailyOfficeStore.day(Calendar.getInstance()))?"Hoje":"Dia escolhido", "Ofício Divino", "Santa Missa", "Biblioteca"}[screen]);
        toolbarSubtitle.setText(new String[] {"Seu momento de oração", "Escolha a hora da sua oração", "O missal, sempre com você", "Filosofia, Teologia e suas leituras"}[screen] + " · v" + appVersion());
        for (int i = 0; i < navigation.length; i++) {
            navigation[i].setBackground(background(i == screen ? SELECTED : SURFACE, Color.TRANSPARENT, 14));
            navigation[i].setTypeface(Typeface.DEFAULT, i == screen ? Typeface.BOLD : Typeface.NORMAL);
            navigation[i].setSelected(i == screen);
        }
        home.removeAllViews();
        if (screen != 3) dateCard();
        switch (screen) {
            case 0: createHome(); break;
            case 1: createHours(); break;
            case 2: createMass(); break;
            case 3: createLibrary(); break;
            default: break;
        }
        scrollHomeToTop();
    }

    private void scrollHomeToTop() {
        ScrollView scroll = (ScrollView) home.getParent();
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (scroll.getViewTreeObserver().isAlive()) scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                scroll.scrollTo(0, 0);
            }
        });
        home.requestFocus();
        scroll.scrollTo(0, 0);
    }

    private void dateCard() {
        LinearLayout box = row();
        box.setPadding(dp(2), 0, 0, dp(18));
        LinearLayout dateText = column();
        dateText.addView(text("DATA DA LEITURA", 10, MUTED));
        TextView value = text(new java.text.SimpleDateFormat("EEE, d 'de' MMMM", new Locale("pt", "BR")).format(selectedDay.getTime()), 17, INK);
        value.setPadding(0, dp(5), 0, 0);
        dateText.addView(value);
        dateText.setOnClickListener(v -> chooseDate());
        dateText.setContentDescription("Escolher data da leitura");
        dateText.setFocusable(true);
        box.addView(dateText, new LinearLayout.LayoutParams(0, -2, 1));
        Button date = smallButton("Alterar", "Escolher data");
        date.setTextSize(13);
        date.setOnClickListener(v -> chooseDate());
        box.addView(date, new LinearLayout.LayoutParams(dp(68), dp(48)));
        Button today=smallButton("Hoje","Voltar para hoje");today.setTextSize(13);today.setOnClickListener(v->{selectedDay.setTimeInMillis(System.currentTimeMillis());showScreen(screen);});box.addView(today,new LinearLayout.LayoutParams(dp(52),dp(48)));
        home.addView(box);
    }

    private void chooseDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            selectedDay.set(year, month, day);
            showScreen(screen);
        }, selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH), selectedDay.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void versionButton() {
        boolean mass = screen == 2;
        String selected = mass ? massVersion : officeVersion;
        Button version = action(rubrics.selected(mass, selected).label + "   ▾", false);
        version.setMinimumHeight(dp(48));
        version.setContentDescription(mass ? "Escolher rubricas da Missa" : "Escolher rubricas do Ofício");
        version.setOnClickListener(v -> showRubrics(mass, ""));
        home.addView(version, new LinearLayout.LayoutParams(-1, -2));
    }

    void showRubrics(boolean mass, String filter) {
        selectingRubric = true;
        selectingMass = mass;
        rubricFilter = filter;
        home.removeAllViews();
        RubricCatalog.Option[] options = rubrics.options(mass);
        toolbarTitle.setText(mass ? "Rubricas da Missa" : "Rubricas do Ofício");
        toolbarSubtitle.setText(options.length + " opções disponíveis · v" + appVersion());
        TextView hint = text("Escolha uma versão", 26, INK);
        hint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        home.addView(hint);
        TextView instructions = text("Todas as opções estão nesta tela. Role a lista ou procure pelo nome.", 15, MUTED);
        instructions.setPadding(0, dp(10), 0, dp(18));
        home.addView(instructions);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Buscar rubrica, ano ou ordem…");
        search.setContentDescription("Buscar rubricas");
        search.setTextSize(16);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setPadding(dp(14), dp(12), dp(14), dp(12));
        search.setBackground(background(SURFACE, LINE, 12));
        search.setText(filter);
        home.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView count = text("", 12, WINE);
        count.setPadding(dp(2), dp(18), 0, dp(14));
        count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        home.addView(count);
        LinearLayout list = column();
        list.setTag("rubric-options");
        home.addView(list, new LinearLayout.LayoutParams(-1, -2));
        Runnable update = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            rubricFilter = search.getText().toString();
            list.removeAllViews();
            int shown = 0;
            for (RubricCatalog.Option option : options) {
                if (!option.label.toLowerCase(Locale.ROOT).contains(query)
                        && !option.value.toLowerCase(Locale.ROOT).contains(query)) continue;
                android.widget.RadioButton row = new android.widget.RadioButton(this);
                row.setText(option.label);
                row.setTextSize(16);
                row.setTextColor(INK);
                row.setChecked(option.value.equals(mass ? massVersion : officeVersion));
                row.setButtonTintList(android.content.res.ColorStateList.valueOf(WINE));
                row.setPadding(dp(12), dp(14), dp(14), dp(14));
                row.setMinimumHeight(dp(60));
                row.setBackground(background(SURFACE, LINE, 12));
                row.setOnClickListener(v -> {
                    if (mass) massVersion = option.value;
                    else officeVersion = option.value;
                    prefs.edit().putString(mass ? "massVersion" : "officeVersion", option.value).apply();
                    android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
                    showScreen(screen);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = dp(8);
                list.addView(row, lp);
                shown++;
            }
            count.setText(shown + " de " + options.length + " opções");
            if (shown == 0) list.addView(text("Nenhuma rubrica com esse nome. Apague a busca para ver todas.", 16, MUTED));
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        update.run();
        scrollHomeToTop();
    }

    private String appVersion() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) { return ""; }
    }

    private void createHours() {
        versionButton();
        dailyCard(false);
        sectionLabel("HORAS CANÔNICAS");
        for (int i = 0; i < ReadingRequest.HOURS.length; i += 2) {
            LinearLayout pair = row();
            pair.setBaselineAligned(false);
            for (int j = i; j < Math.min(i + 2, ReadingRequest.HOURS.length); j++) {
                final int hour = j;
                LinearLayout tile = column();
                tile.setPadding(dp(16), dp(16), dp(16), dp(16));
                tile.setMinimumHeight(dp(126));
                tile.setBackground(background(SURFACE, LINE, 18));
                tile.setContentDescription(ReadingRequest.LABELS[j] + ". " + ReadingRequest.DETAILS[j]);
                tile.setFocusable(true);
                tile.setOnClickListener(v -> openDailyOrOnline(false, hour));
                tile.addView(text(String.format(Locale.ROOT, "%02d", j + 1), 11, WINE));
                TextView name = text(ReadingRequest.LABELS[j] + "  ›", 19, INK);
                name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                name.setPadding(0, dp(10), 0, dp(6));
                tile.addView(name);
                boolean available = DailyOfficeStore.get(this, dailySpec(false), hour) != null;
                tile.addView(text(available ? "Disponível offline" : ReadingRequest.DETAILS[j], 12, available ? WINE : MUTED));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
                lp.setMargins(j == i ? 0 : dp(6), 0, j == i ? dp(6) : 0, 0);
                pair.addView(tile, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(12);
            home.addView(pair, lp);
        }
    }

    private void createMass() {
        versionButton();
        dailyCard(true);
        sectionLabel("SANTA MISSA");
        TextView title = text("O missal\nna palma da mão.", 34, INK);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        home.addView(title);
        TextView description = text("Acompanhe os textos da Missa para a data escolhida. Alterne entre português e latim durante a leitura.", 16, MUTED);
        description.setPadding(0, dp(18), 0, dp(24));
        description.setLineSpacing(dp(4), 1);
        home.addView(description);
        Button open = action("Abrir a Missa  →", true);
        open.setOnClickListener(v -> openDailyOrOnline(true, 0));
        home.addView(open, new LinearLayout.LayoutParams(-1, dp(56)));
        sectionLabel("UMA LEITURA MAIS TRANQUILA");
        home.addView(text("Texto em uma coluna, fonte ajustável e cópias salvas para consultar sem conexão.", 15, MUTED));
    }

    private void createLibrary() {
        TextView title = text("Uma biblioteca\npara a sua formação.", 29, INK);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        title.setPadding(0, dp(4), 0, dp(18)); home.addView(title);
        LinearLayout tabs = row();
        String[] names = {"Filosofia", "Teologia", "Salvos"};
        for (int i = 0; i < names.length; i++) {
            final int selected = i;
            Button tab = action(names[i], librarySection == i);
            tab.setTextSize(13); tab.setContentDescription("Biblioteca: " + names[i]);
            tab.setSelected(librarySection == i);
            tab.setOnClickListener(v -> { librarySection = selected; libraryQuery = ""; showScreen(3); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            lp.setMargins(dp(2), 0, dp(2), 0); tabs.addView(tab, lp);
        }
        home.addView(tabs);
        Button study=action("Roteiro de estudos  →",false);study.setOnClickListener(v->startActivity(new Intent(this,StudyActivity.class)));home.addView(study,fullButton());
        LinearLayout personal=row();Button offlineSearch=action("Buscar downloads",false),notes=action("Favoritos e notas",false);
        offlineSearch.setOnClickListener(v->startActivity(new Intent(this,OfflineSearchActivity.class)));notes.setOnClickListener(v->startActivity(new Intent(this,PersonalActivity.class)));
        personal.addView(offlineSearch,new LinearLayout.LayoutParams(0,dp(56),1));personal.addView(notes,new LinearLayout.LayoutParams(0,dp(56),1));home.addView(personal);
        if (librarySection == 2) { createSavedLibrary(); return; }
        TextView explanation = text(librarySection == 0
                ? "Percurso inicial de filosofia clássica e relação entre fé e razão. Português em destaque; idioma indicado em cada obra."
                : "Referências para o estudo da fé, da doutrina e da vida cristã. Comece pelo Compêndio e use o Catecismo para aprofundar.", 14, MUTED);
        explanation.setPadding(0, dp(20), 0, dp(18)); home.addView(explanation);
        EditText search = new EditText(this); search.setSingleLine(true);
        search.setHint("Buscar título, autor ou idioma…"); search.setTextSize(16);
        search.setTextColor(INK); search.setHintTextColor(MUTED);
        search.setContentDescription("Buscar livros"); search.setText(libraryQuery);
        search.setBackground(background(SURFACE, LINE, 12)); search.setPadding(dp(14), dp(12), dp(14), dp(12));
        home.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView count = text("", 12, WINE); count.setPadding(0, dp(16), 0, dp(16)); home.addView(count);
        LinearLayout results = column(); results.setTag("book-results"); home.addView(results);
        Runnable update = () -> {
            libraryQuery = search.getText().toString();
            String query = normalize(libraryQuery), category = librarySection == 0 ? "filosofia" : "teologia";
            results.removeAllViews(); int shown = 0;
            for (BookCatalog.Book book : books.all()) {
                if (!category.equals(book.category) || !normalize(book.title + " " + book.author + " " + book.language).contains(query)) continue;
                boolean downloaded = BookStore.cached(this, book);
                String badge = book.language.toUpperCase(new Locale("pt", "BR")) + " · "
                        + (downloaded ? "OFFLINE" : book.offline ? ("pdf".equals(book.format) ? "PDF PARA BAIXAR" : "TEXTO PARA BAIXAR") : "CONSULTA ONLINE");
                card(results, badge, book.title, book.author + "\n" + book.description, () -> showBook(book));
                if (downloaded) {
                    Button remove = smallButton("Remover download", "Remover download de " + book.title);
                    remove.setTextSize(12);
                    remove.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Remover a cópia deste livro?")
                            .setMessage("Você poderá baixar novamente quando estiver conectado.")
                            .setNegativeButton("Cancelar", null).setPositiveButton("Remover", (d, w) -> {
                                File file = BookStore.file(this, book);
                                if (!file.exists() || file.delete()) showScreen(3);
                                else toast("Não foi possível remover a cópia.");
                            }).show());
                    results.addView(remove, new LinearLayout.LayoutParams(-1, dp(44)));
                }
                shown++;
            }
            count.setText(shown + " obras · seleção inicial");
            if (shown == 0) results.addView(text("Nenhum livro com essa busca. Tente outro título ou autor.", 16, MUTED));
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        update.run();
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private void showBook(BookCatalog.Book book) {
        boolean cached = BookStore.cached(this, book);
        new AlertDialog.Builder(this).setTitle(book.title)
                .setMessage(book.author + "\nIdioma: " + book.language + "\nFonte: " + book.source + "\n\n" + book.description
                        + "\n\n" + (cached ? "A cópia está disponível neste aparelho sem internet."
                        : book.offline ? "O primeiro acesso baixa o texto ou PDF para a área privada do app. Imagens de textos online podem depender da fonte."
                        : "Esta obra é consultada por partes na fonte e precisa de internet."))
                .setPositiveButton(cached ? "Ler offline" : book.offline ? "Baixar e ler" : "Ler online",
                        (d, w) -> startActivity(new Intent(this, BookReaderActivity.class).putExtra("bookId", book.id)))
                .setNeutralButton("Ver fonte", (d, w) -> external(book.sourceUrl))
                .setNegativeButton("Fechar", null).show();
    }

    private void createSavedLibrary() {
        JSONArray pages = savedPages();
        java.util.List<DailyOfficeStore.Spec> packages = DailyOfficeStore.packages(this);
        if (!packages.isEmpty()) {
            sectionLabel("DIAS SALVOS PARA LER SEM INTERNET");
            for (DailyOfficeStore.Spec spec : packages) {
                int count = DailyOfficeStore.count(this, spec);
                card(count + "/" + spec.size() + " LEITURAS OFFLINE", (spec.mass ? "Missa · " : "Ofício · ") + spec.displayDay(), spec.label,
                        () -> showDailyPackage(spec));
                Button manage = smallButton("Gerenciar dia salvo", "Gerenciar " + spec.displayDay() + " · " + spec.label);
                manage.setTextSize(12); manage.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(spec.displayDay())
                        .setMessage(spec.label + "\n" + count + "/" + spec.size() + " leituras salvas.\nCalendário Geral · modo leigo · Latim/Português.")
                        .setPositiveButton("Baixar faltantes", (d, w) -> startActivity(DailyDownloadActivity.intent(this, spec.day, spec.mass ? null : spec.version, spec.mass ? spec.version : null)))
                        .setNeutralButton("Remover pacote", (d, w) -> new AlertDialog.Builder(this).setTitle("Remover dia salvo?")
                                .setMessage(spec.displayDay() + " · " + spec.label).setNegativeButton("Cancelar", null)
                                .setPositiveButton("Remover", (dialog, which) -> {
                                    if (!DailyOfficeStore.remove(this, spec)) toast("Não foi possível remover todos os arquivos. Tente novamente.");
                                    showScreen(3);
                                }).show()).setNegativeButton("Fechar", null).show());
                home.addView(manage, new LinearLayout.LayoutParams(-1, dp(44)));
            }
        }
        if (pages.length() == 0 && packages.isEmpty()) {
            TextView title = text("Suas orações,\nsempre por perto.", 33, INK);
            title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            title.setPadding(0, dp(32), 0, dp(20));
            home.addView(title);
            home.addView(text("Sua biblioteca está começando", 19, WINE));
            TextView hint = text("No Ofício ou na Missa, toque em Baixar orações do dia. As leituras ficarão aqui para consultar sem internet. Você também pode usar Salvar na tela de leitura.", 16, MUTED);
            hint.setPadding(0, dp(16), 0, dp(28));
            home.addView(hint);
            Button start = action("Escolher uma leitura", true);
            start.setOnClickListener(v -> showScreen(1));
            home.addView(start, new LinearLayout.LayoutParams(-1, dp(54)));
            return;
        }
        if (pages.length() > 0) sectionLabel(pages.length() + " PÁGINAS SALVAS INDIVIDUALMENTE");
        for (int i = pages.length() - 1; i >= 0; i--) {
            JSONObject page = pages.optJSONObject(i);
            if (page == null) continue;
            card("DISPONÍVEL OFFLINE", page.optString("title", "Leitura"), "Salva em " + DateFormat.getDateInstance(DateFormat.SHORT, new Locale("pt", "BR")).format(new Date(page.optLong("date"))),
                    () -> {
                        File file = new File(new File(getFilesDir(), "leituras"), page.optString("file"));
                        openArchive(Uri.fromFile(file).toString(), page.optString("title"));
                    });
            Button remove = smallButton("Excluir leitura", "Excluir " + page.optString("title"));
            remove.setTextSize(12);
            remove.setOnClickListener(v -> deletePage(page.optString("file")));
            home.addView(remove, new LinearLayout.LayoutParams(-1, dp(44)));
        }
    }

    private DailyOfficeStore.Spec dailySpec(boolean mass) {
        return new DailyOfficeStore.Spec(DailyOfficeStore.day(selectedDay), rubrics.selected(mass, mass ? massVersion : officeVersion));
    }

    private void dailyCard(boolean mass) {
        DailyOfficeStore.Spec spec = dailySpec(mass);
        int count = DailyOfficeStore.count(this, spec);
        TextView info = text(count + "/" + spec.size() + (mass ? " leitura da Missa salva" : " horas salvas") + " · " + spec.displayDay(), 14, WINE);
        info.setPadding(0, dp(16), 0, dp(6)); home.addView(info);
        home.addView(text("Baixe uma vez com internet e reze depois sem conexão. Calendário Geral · modo leigo · Latim/Português.", 13, MUTED));
        Button download = action("Baixar orações do dia", true);
        download.setOnClickListener(v -> downloadDay()); home.addView(download, fullButton());
        TextView note = text("Inclui as 8 horas do Ofício e a Missa, nas rubricas escolhidas em cada aba. Ao trocar a data ou a rubrica, baixe o novo pacote.", 12, MUTED);
        note.setPadding(0, dp(8), 0, dp(8)); home.addView(note);
    }

    private void downloadDay() {
        startActivity(DailyDownloadActivity.intent(this, DailyOfficeStore.day(selectedDay), officeVersion, massVersion));
    }
    private void downloadDays(){
        new AlertDialog.Builder(this).setTitle("Baixar a partir de "+dailySpec(false).displayDay())
            .setItems(new String[]{"1 dia · 9 leituras","3 dias · 27 leituras","7 dias · 63 leituras"},(d,w)->
                startActivity(DailyDownloadActivity.intent(this,DailyOfficeStore.day(selectedDay),officeVersion,massVersion).putExtra("days",new int[]{1,3,7}[w])))
            .setNegativeButton("Cancelar",null).show();
    }

    private void openDailyOrOnline(boolean mass, int hour) {
        DailyOfficeStore.Spec spec = dailySpec(mass);
        if (DailyOfficeStore.get(this, spec, hour) != null) startActivity(DailyReaderActivity.intent(this, spec, hour));
        else openPage(ReadingRequest.url(mass, hour, selectedDay, rubrics.selected(mass, mass ? massVersion : officeVersion)), spec.title(hour));
    }

    private void showDailyPackage(DailyOfficeStore.Spec spec) {
        String[] labels = new String[spec.size()];
        for (int hour = 0; hour < labels.length; hour++) labels[hour] = spec.title(hour) + (DailyOfficeStore.get(this, spec, hour) == null ? " · não baixada" : " · offline");
        new AlertDialog.Builder(this).setTitle((spec.mass ? "Missa · " : "Ofício · ") + spec.displayDay())
                .setItems(labels, (dialog, which) -> {
                    if (DailyOfficeStore.get(this, spec, which) == null) toast("Esta leitura ainda não foi baixada. Use Gerenciar dia salvo para baixar as que faltam.");
                    else startActivity(DailyReaderActivity.intent(this, spec, which));
                }).setNegativeButton("Fechar", null).show();
    }

    private void card(String label, String title, String detail, Runnable click) {
        card(home, label, title, detail, click);
    }

    private void card(LinearLayout parent, String label, String title, String detail, Runnable click) {
        LinearLayout card = column();
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(background(SURFACE, LINE, 18));
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(title + ". " + detail);
        card.setOnClickListener(v -> click.run());
        TextView small = text(label, 11, WINE);
        small.setLetterSpacing(.1f);
        if (!label.isEmpty()) card.addView(small);
        TextView name = text(title + "  ›", 24, INK);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setPadding(0, dp(10), 0, dp(6));
        card.addView(name);
        card.addView(text(detail, 14, MUTED));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(card, lp);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); // The original liturgical forms require JavaScript.
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(prefs.getInt("zoom", 110));
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setBackgroundColor(PAPER);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isSavedArchive(url) && local) return false;
                if (UrlPolicy.isOfficial(url)) {
                    if (local || !url.equals(UrlPolicy.secure(url))) {
                        // Never replay a POST as a GET on an HTTP redirect.
                        if (!"GET".equals(request.getMethod())) {
                            showError("Conexão segura necessária", "Volte ao início e abra novamente o Ofício ou a Missa.");
                        } else openPage(UrlPolicy.secure(url), section);
                        return true;
                    }
                    return false;
                }
                if (request.isForMainFrame() && UrlPolicy.canOpenExternally(url)) external(url);
                else toast("Este tipo de link não pode ser aberto no leitor.");
                return true;
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                loaded = false;
                failed = false;
                errorPanel.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
                web.setVisibility(View.INVISIBLE);
                if (UrlPolicy.isOfficial(url)) retryUrl = UrlPolicy.secure(url);
                updateControls();
            }

            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.INVISIBLE);
                loaded = !failed;
                if (clearHistory) { web.clearHistory(); clearHistory = false; }
                CookieManager.getInstance().flush();
                if (!failed && !local && UrlPolicy.isOfficial(url)) {
                    applyReaderStyle();
                    if (url.contains("command=pray")) prefs.edit().putString("lastReading", url).putString("lastTitle", section).apply();
                }
                if (local) web.setVisibility(View.VISIBLE);
                updateControls();
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError("Não foi possível abrir a página", local
                        ? "A leitura salva não pôde ser aberta. Você pode removê-la e salvar novamente quando estiver conectado."
                        : "Confira sua conexão e tente novamente. As leituras já salvas continuam disponíveis sem internet.");
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) showError("O site retornou erro " + response.getStatusCode(),
                        "O servidor pode estar indisponível ou restringindo o acesso. Tente novamente ou abra o site no navegador.");
            }
            // Certificate errors retain WebView's default cancellation behavior.
        });
    }

    private void configureSource(boolean archive) {
        web.getSettings().setAllowFileAccess(archive);
        web.getSettings().setJavaScriptEnabled(!archive);
        if (archive && dark != archiveDark) {
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColorFilter(AppAppearance.nightFilter());
            web.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
        } else web.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private void showHome() {
        showScreen(0);
    }

    private void showReader() {
        reading = true;
        ((View) home.getParent()).setVisibility(View.GONE);
        reader.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.GONE);
        languageBar.setVisibility(local || originalLayout ? View.GONE : View.VISIBLE);
        toolbarTitle.setText(local ? "Leitura salva" : section);
        toolbarSubtitle.setText(local ? section : "Texto litúrgico · Leitura online");
        updateLanguage();
    }

    void openPage(String url, String name) {
        if (!UrlPolicy.isOfficial(url)) return;
        clearHistory = local;
        local = false;
        originalLayout = false;
        loaded = false;
        section = name;
        retryUrl = UrlPolicy.secure(url);
        configureSource(false);
        showReader();
        errorPanel.setVisibility(View.GONE);
        web.loadUrl(retryUrl);
        updateControls();
    }

    private void openArchive(String url, String name) {
        if (!isSavedArchive(url)) { toast("Esta leitura não está mais disponível."); return; }
        clearHistory = !local;
        local = true;
        archiveDark = false;
        String filename = new File(Uri.parse(url).getPath()).getName();
        JSONArray copies = savedPages();
        for (int i = 0; i < copies.length(); i++) {
            JSONObject copy = copies.optJSONObject(i);
            if (copy != null && filename.equals(copy.optString("file"))) archiveDark = copy.optBoolean("dark", false);
        }
        loaded = false;
        section = name;
        configureSource(true);
        showReader();
        errorPanel.setVisibility(View.GONE);
        web.loadUrl(url);
        updateControls();
    }

    void showError(String title, String message) {
        failed = true;
        loaded = false;
        errorTitle.setText(title);
        errorText.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        progress.setVisibility(View.INVISIBLE);
        updateControls();
    }

    private void updateControls() {
        previous.setEnabled(!local && loaded && web.canGoBack());
        next.setEnabled(!local && loaded && web.canGoForward());
        save.setEnabled(loaded && !local && !saving && UrlPolicy.isOfficial(web.getUrl()));
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {"Início", "Ofício Divino", "Santa Missa", "Leituras salvas", "Tamanho do texto", "Recarregar página", "Abrir no navegador", "Sobre o aplicativo", originalLayout ? "Voltar ao visual do app" : "Opções avançadas / visual do site", "Aparência: claro ou escuro", "Baixar orações do dia", "Baixar vários dias", "Roteiro de estudos", "Buscar nos downloads", "Favoritos e notas / backup", "Lembretes de oração", "Sua leitura: favoritos e notas"};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]);
        menu.getMenu().findItem(5).setEnabled(reading && !local);
        menu.getMenu().findItem(6).setEnabled(reading && !local);
        menu.getMenu().findItem(8).setEnabled(reading && !local && loaded);
        menu.getMenu().findItem(16).setEnabled(reading && !local && loaded);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: showHome(); break;
                case 1: showScreen(1); break;
                case 2: showScreen(2); break;
                case 3: showSaved(); break;
                case 4: showFont(); break;
                case 5: web.reload(); break;
                case 6: external(retryUrl); break;
                case 7: showAbout(); break;
                case 8:
                    originalLayout = !originalLayout;
                    web.evaluateJavascript("window.divinumReader && window.divinumReader.setOriginal(" + originalLayout + ")", null);
                    languageBar.setVisibility(originalLayout ? View.GONE : View.VISIBLE);
                    break;
                case 9: showAppearance(); break;
                case 10: downloadDay(); break;
                case 11: downloadDays(); break;
                case 12: startActivity(new Intent(this,StudyActivity.class)); break;
                case 13: startActivity(new Intent(this,OfflineSearchActivity.class)); break;
                case 14: startActivity(new Intent(this,PersonalActivity.class)); break;
                case 15: startActivity(new Intent(this,ReminderActivity.class)); break;
                case 16:
                    try{ReadingTools.show(this,new JSONObject().put("kind","web").put("title",section).put("url",retryUrl).put("scroll",web.getScrollY()),web);}
                    catch(JSONException e){toast("Não foi possível preparar o registro.");}
                    break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }

    void showAppearance() {
        new AlertDialog.Builder(this).setTitle("Aparência")
                .setSingleChoiceItems(AppAppearance.LABELS, AppAppearance.selected(this), (dialog, which) -> {
                    AppAppearance.save(this, which);
                    dialog.dismiss();
                    recreate();
                }).setNegativeButton("Fechar", null).show();
    }

    private String asset(String path) {
        try (java.io.InputStream stream = getAssets().open(path)) {
            java.util.Scanner scanner = new java.util.Scanner(stream, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } catch (IOException e) { return ""; }
    }

    private void applyReaderStyle() {
        if (local || failed || !UrlPolicy.isOfficial(web.getUrl())) return;
        String script = "(function(){var s=document.getElementById('divinum-mobile-style');"
                + "if(!s){s=document.createElement('style');s.id='divinum-mobile-style';document.head.appendChild(s);}"
                + "s.textContent=" + JSONObject.quote(readerCss) + ";})();\n" + readerScript;
        web.evaluateJavascript(script, result -> {
            if (isDestroyed() || local) return;
            try {
                boolean bilingual = new JSONObject(result).optBoolean("bilingual");
                languageBar.setVisibility(!originalLayout && bilingual ? View.VISIBLE : View.GONE);
            } catch (JSONException | NullPointerException e) { languageBar.setVisibility(View.GONE); }
            updateLanguage();
            web.evaluateJavascript("window.divinumReader && window.divinumReader.setDark(" + dark + ")", null);
            web.evaluateJavascript("window.divinumReader && window.divinumReader.setOriginal(" + originalLayout + ")", null);
            web.setVisibility(View.VISIBLE);
            if(getIntent().getBooleanExtra("savedPosition",false)){
                int y=getIntent().getIntExtra("scroll",0);getIntent().removeExtra("savedPosition");web.post(()->web.scrollTo(0,y));
            }
            String highlight=getIntent().getStringExtra("highlight");if(highlight!=null&&!highlight.trim().isEmpty()){web.findAllAsync(highlight);getIntent().removeExtra("highlight");}
        });
    }

    private void updateLanguage() {
        int selected = Math.max(0, Math.min(2, prefs.getInt("languageMode", 0)));
        for (int i = 0; i < languages.length; i++) {
            languages[i].setBackground(background(i == selected ? PRIMARY : SURFACE, i == selected ? PRIMARY : LINE, 12));
            languages[i].setTextColor(i == selected ? Color.WHITE : MUTED);
            languages[i].setSelected(i == selected);
        }
        if (!local) web.evaluateJavascript("window.divinumReader && window.divinumReader.setMode('"
                + new String[] {"portugues", "latin", "both"}[selected] + "')", null);
    }

    void showFont() {
        LinearLayout panel = column();
        panel.setPadding(dp(24), dp(16), dp(24), dp(16));
        TextView label = text("", 18, INK);
        SeekBar slider = new SeekBar(this);
        slider.setMin(80);
        slider.setMax(200);
        slider.setProgress(prefs.getInt("zoom", 110));
        label.setText("Tamanho: " + slider.getProgress() + "%");
        slider.setContentDescription("Tamanho do texto, de 80 a 200 por cento");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                web.getSettings().setTextZoom(value);
                prefs.edit().putInt("zoom", value).apply();
                label.setText("Tamanho: " + value + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        panel.addView(label);
        panel.addView(slider, new LinearLayout.LayoutParams(-1, dp(60)));
        new AlertDialog.Builder(this).setTitle("Conforto de leitura").setView(panel)
                .setPositiveButton("Concluir", null)
                .setNeutralButton("Restaurar 110%", (d, w) -> {
                    prefs.edit().putInt("zoom", 110).apply();
                    web.getSettings().setTextZoom(110);
                }).show();
    }

    private void savePage() {
        if (!loaded || local || saving || !UrlPolicy.isOfficial(web.getUrl())) return;
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("Nome da leitura");
        name.setText(section + " · " + DateFormat.getDateInstance(DateFormat.SHORT, new Locale("pt", "BR")).format(new Date()));
        LinearLayout box = column();
        box.setPadding(dp(24), dp(8), dp(24), dp(8));
        box.addView(text("Será salva apenas a página aberta. Abra primeiro a hora ou a Missa que deseja guardar. Os controles do site não funcionarão na cópia salva.", 15, MUTED));
        box.addView(name);
        new AlertDialog.Builder(this).setTitle("Guardar para ler sem internet").setView(box)
                .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", (dialog, which) -> {
                    if (!loaded || local || !UrlPolicy.isOfficial(web.getUrl())) return;
                    File dir = new File(getFilesDir(), "leituras");
                    if (!dir.isDirectory() && !dir.mkdirs()) { toast("Não foi possível criar a biblioteca."); return; }
                    File file = new File(dir, UUID.randomUUID() + ".mht");
                    String source = web.getUrl();
                    String title = name.getText().toString().trim();
                    String savedTitle = title.isEmpty() ? section : title;
                    saving = true;
                    updateControls();
                    web.saveWebArchive(file.getAbsolutePath(), false, path -> {
                        saving = false;
                        if (path != null && file.isFile() && file.length() > 0) {
                            try {
                                JSONArray pages = savedPages();
                                pages.put(new JSONObject().put("file", file.getName()).put("title", savedTitle)
                                        .put("source", source).put("date", System.currentTimeMillis()).put("dark", dark));
                                prefs.edit().putString("pages", pages.toString()).apply();
                                toast("Leitura salva neste aparelho.");
                            } catch (JSONException e) { file.delete(); toast("Não foi possível salvar a leitura."); }
                        } else { file.delete(); toast("Não foi possível salvar. Aguarde a página carregar e tente novamente."); }
                        if (!isDestroyed()) updateControls();
                    });
                }).show();
    }

    private JSONArray savedPages() {
        try { return new JSONArray(prefs.getString("pages", "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private boolean isSavedArchive(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        if (!"file".equals(uri.getScheme()) || uri.getPath() == null) return false;
        try {
            File requested = new File(uri.getPath()).getCanonicalFile();
            JSONArray pages = savedPages();
            for (int i = 0; i < pages.length(); i++) {
                JSONObject page = pages.optJSONObject(i);
                if (page == null) continue;
                File saved = new File(new File(getFilesDir(), "leituras"), page.optString("file"));
                if (saved.getCanonicalFile().equals(requested) && saved.isFile()) return true;
            }
        } catch (IOException e) { return false; }
        return false;
    }

    void showSaved() {
        librarySection = 2;
        libraryQuery = "";
        showScreen(3);
    }

    private void deletePage(String filename) {
        new AlertDialog.Builder(this).setTitle("Excluir esta leitura?")
                .setMessage("Você poderá salvar uma nova cópia quando estiver conectado.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Excluir", (d, w) -> {
                    JSONArray pages = savedPages(), remaining = new JSONArray();
                    for (int i = 0; i < pages.length(); i++) {
                        JSONObject page = pages.optJSONObject(i);
                        if (!filename.equals(page.optString("file"))) remaining.put(page);
                    }
                    File file = new File(new File(getFilesDir(), "leituras"), filename);
                    if (file.exists() && !file.delete()) { toast("Não foi possível excluir esta leitura."); return; }
                    prefs.edit().putString("pages", remaining.toString()).apply();
                    if (local && web.getUrl() != null && Uri.fromFile(file).toString().equals(web.getUrl())) showHome();
                    if (!reading) showSaved();
                    toast("Leitura excluída.");
                }).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Divinum Leitura · " + appVersion())
                .setMessage("Leitor Android independente, sem vínculo oficial com o projeto Divinum Officium.\n\nO site fornece os textos e os cálculos litúrgicos. O app não altera esse conteúdo. As leituras salvas são cópias da página aberta, não um calendário litúrgico completo offline.\n\nA Biblioteca reúne clássicos, referências e documentos para formação. Cada edição conserva seus créditos e direitos; a licença MIT do Divinum Officium não se aplica automaticamente aos livros. As fontes são identificadas antes da leitura.\n\nO app não inclui anúncios nem ferramentas de análise. Os sites consultados recebem as solicitações online. Livros baixados, preferências e leituras salvas ficam na área privada do app e são apagados ao limpar seus dados ou desinstalá-lo.\n\nTextos litúrgicos e código de referência: Divinum Officium, licença MIT.")
                .setPositiveButton("Fechar", null)
                .setNeutralButton("Licença MIT", (d, w) -> {
                    try (java.io.InputStream stream = getAssets().open("LICENSE-DivinumOfficium.txt")) {
                        java.util.Scanner scanner = new java.util.Scanner(stream, "UTF-8").useDelimiter("\\A");
                        String license = scanner.hasNext() ? scanner.next() : "";
                        new AlertDialog.Builder(this).setTitle("Divinum Officium · MIT").setMessage(license).setPositiveButton("Fechar", null).show();
                    } catch (IOException e) { toast("A licença está incluída no código-fonte."); }
                }).show();
    }

    private void external(String url) {
        if (!UrlPolicy.canOpenExternally(url)) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException e) { toast("Nenhum navegador disponível neste aparelho."); }
    }

    @Override public void onBackPressed() {
        if (selectingRubric) showScreen(screen);
        else if (reading && !local && loaded && web.canGoBack()) web.goBack();
        else if (reading) showScreen(screen);
        else if (screen != 0) showHome();
        else super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("reading", reading);
        state.putInt("screen", screen);
        state.putInt("librarySection", librarySection);
        state.putString("libraryQuery", libraryQuery);
        state.putBoolean("selectingRubric", selectingRubric);
        state.putBoolean("selectingMass", selectingMass);
        state.putString("rubricFilter", rubricFilter);
        state.putLong("day", selectedDay.getTimeInMillis());
        state.putBoolean("local", local);
        state.putBoolean("archiveDark", archiveDark);
        state.putBoolean("failed", failed);
        state.putString("section", section);
        state.putString("retryUrl", retryUrl);
        Bundle browser = new Bundle();
        web.saveState(browser);
        state.putBundle("web", browser);
    }

    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() {
        super.onResume(); web.onResume();
        if (AppAppearance.isDark(this) != dark) { recreate(); return; }
        officeVersion=rubrics.selected(false,prefs.getString("officeVersion",officeVersion)).value;
        massVersion=rubrics.selected(true,prefs.getString("massVersion",massVersion)).value;
        web.getSettings().setTextZoom(prefs.getInt("zoom",110));
        if (!reading && !selectingRubric) showScreen(screen);
    }
    @Override protected void onDestroy() {
        web.stopLoading();
        ((android.view.ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private View divider() { View view = new View(this); view.setBackgroundColor(LINE); return view; }
    private GradientDrawable background(int color, int border, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); shape.setStroke(dp(1), border); return shape;
    }
    private Button smallButton(String title, String description) {
        Button button = new Button(this); button.setText(title); button.setContentDescription(description);
        button.setTextSize(17); button.setTextColor(WINE); button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0); button.setMinWidth(0); button.setMinimumWidth(0);
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        button.setBackgroundResource(value.resourceId); return button;
    }
    private Button action(String title, boolean primary) {
        Button button = new Button(this); button.setText(title); button.setTextSize(15); button.setAllCaps(false);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setTextColor(primary ? Color.WHITE : WINE);
        button.setBackground(background(primary ? PRIMARY : PAPER, primary ? PRIMARY : LINE, 8)); return button;
    }
    private LinearLayout.LayoutParams fullButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10); return lp;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
