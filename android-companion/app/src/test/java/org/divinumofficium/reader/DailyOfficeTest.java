package org.divinumofficium.reader;

import android.content.*;
import android.os.Looper;
import android.view.*;
import android.webkit.WebView;
import android.widget.TextView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Calendar;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class DailyOfficeTest {
    private Context context() { return RuntimeEnvironment.getApplication(); }
    private DailyOfficeStore.Spec office() { return DailyOfficeStore.spec(context(), "2026-11-16", RubricCatalog.DEFAULT, false); }
    static JSONObject snapshot(DailyOfficeStore.Spec spec, int hour) throws Exception {
        return new JSONObject().put("valid", true).put("source", DailyOfficeStore.request(spec, hour)).put("textLength", 300).put("bilingual", true)
                .put("fragment", "<p class='dl-day'>Dia litúrgico</p><h2 class='dl-notice'>Aviso do calendário</h2><table class='dl-prayer'><tbody><tr>"
                        + "<td class='dl-latin'>Deus, in adjutórium meum inténde. Dómine, ad adjuvándum me festína.</td>"
                        + "<td class='dl-portugues'>Vinde, ó Deus, em meu auxílio. Socorrei-me sem demora. Glória ao Pai e ao Filho e ao Espírito Santo.</td></tr>"
                        + "<tr><td class='dl-shared'>Rubrica comum às duas línguas.</td></tr></tbody></table>");
    }
    @Test public void persistsAllEightHoursAndMassWithoutMixingDatesOrRubrics() throws Exception {
        var spec = office();
        assertEquals(0, DailyOfficeStore.count(context(), spec));
        for (int hour = 0; hour < 8; hour++) {
            DailyOfficeStore.put(context(), spec, hour, snapshot(spec, hour));
            assertEquals(hour + 1, DailyOfficeStore.count(context(), spec));
        }
        var reconstructed = DailyOfficeStore.spec(context(), spec.day, spec.version, false);
        assertEquals(8, DailyOfficeStore.count(context(), reconstructed));
        assertEquals(0, DailyOfficeStore.count(context(), DailyOfficeStore.spec(context(), "2026-11-17", spec.version, false)));
        assertEquals(0, DailyOfficeStore.count(context(), DailyOfficeStore.spec(context(), spec.day, "Divino Afflatu - 1954", false)));
        var mass = DailyOfficeStore.spec(context(), spec.day, spec.version, true);
        assertEquals(0, DailyOfficeStore.count(context(), mass));
        DailyOfficeStore.put(context(), mass, 0, snapshot(mass, 0));
        assertEquals(1, DailyOfficeStore.count(context(), mass));
        assertEquals(2, DailyOfficeStore.packages(context()).size());
        assertTrue(DailyOfficeStore.remove(context(), spec));
        assertEquals(0, DailyOfficeStore.count(context(), spec));
        assertEquals(1, DailyOfficeStore.count(context(), mass));
    }
    @Test public void failedUpdateKeepsPreviousReadingAndRejectsWrongPageOrActiveContent() throws Exception {
        var spec = office(); JSONObject original = snapshot(spec, 0);
        DailyOfficeStore.put(context(), spec, 0, original);
        for (JSONObject bad : new JSONObject[] {
                snapshot(spec, 1), snapshot(spec, 0).put("source", DailyOfficeStore.request(DailyOfficeStore.spec(context(), "2026-11-17", spec.version, false), 0)),
                snapshot(spec, 0).put("valid", false), snapshot(spec, 0).put("textLength", 0),
                snapshot(spec, 0).put("fragment", "<table class='dl-prayer'><script>alert(1)</script></table>"),
                snapshot(spec, 0).put("fragment", "<table class='dl-prayer' onclick='alert(1)'></table>") }) {
            try { DailyOfficeStore.put(context(), spec, 0, bad); fail("Invalid page accepted"); } catch (java.io.IOException expected) { }
            assertEquals(original.toString(), DailyOfficeStore.get(context(), spec, 0).snapshot.toString());
            assertEquals(1, DailyOfficeStore.count(context(), spec));
        }
    }
    @Test public void corruptionIsReportedAsMissingInsteadOfOpeningWrongContent() throws Exception {
        var spec = office(); DailyOfficeStore.put(context(), spec, 3, snapshot(spec, 3));
        File dir = new File(context().getFilesDir(), "daily-offices/" + spec.key);
        File content = dir.listFiles((d, n) -> n.startsWith("reading-"))[0];
        Files.write(content.toPath(), "broken".getBytes(StandardCharsets.UTF_8));
        assertNull(DailyOfficeStore.get(context(), spec, 3)); assertEquals(0, DailyOfficeStore.count(context(), spec));
        DailyOfficeStore.put(context(), spec, 3, snapshot(spec, 3));
        assertNotNull(DailyOfficeStore.get(context(), spec, 3));
    }
    @Test public void everyRubricBuildsUnambiguousDailyRequests() {
        for (boolean mass : new boolean[] {false, true}) for (RubricCatalog.Option option : DailyOfficeStore.catalog(context()).options(mass)) {
            var spec = new DailyOfficeStore.Spec("2026-01-02", option);
            for (int i = 0; i < spec.size(); i++) {
                String url = DailyOfficeStore.request(spec, i);
                assertTrue(DailyOfficeStore.matches(spec, i, url));
                assertTrue(DailyOfficeStore.matches(spec, i, url.replace("01-02-2026", "1-2-2026")));
                assertFalse(DailyOfficeStore.matches(spec, i, url.replace("2026", "2025")));
                assertTrue(url.contains("expand=tota") && url.contains("priest=0") && url.contains("dioecesis=Generale") && url.contains("votive=Hodie"));
                assertFalse(url.contains("content="));
            }
        }
    }
    @Test public void nativeHoursPreferExactCachedReadingAndFallBackOnlineForMissingHours() throws Exception {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get(); Calendar today = Calendar.getInstance();
            var spec = DailyOfficeStore.spec(activity, DailyOfficeStore.day(today), RubricCatalog.DEFAULT, false);
            DailyOfficeStore.put(activity, spec, 1, snapshot(spec, 1)); activity.showScreen(1);
            ((View) text(activity.getWindow().getDecorView(), "Laudes  ›").getParent()).performClick();
            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(DailyReaderActivity.class.getName(), launched.getComponent().getClassName());
            assertEquals(spec.day, launched.getStringExtra("day")); assertEquals(1, launched.getIntExtra("hour", -1));
            ((View) text(activity.getWindow().getDecorView(), "Matinas  ›").getParent()).performClick();
            assertTrue(Shadows.shadowOf(web(activity.getWindow().getDecorView())).getLastLoadedUrl().contains("command=prayMatutinum"));
            activity.onBackPressed(); activity.showSaved();
            assertNotNull(text(activity.getWindow().getDecorView(), "Ofício · " + spec.displayDay() + "  ›"));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void offlineReaderBlocksNetworkScriptsAndFilesAndHonorsThemeAndLanguage() throws Exception {
        var spec = office(); DailyOfficeStore.put(context(), spec, 0, snapshot(spec, 0)); AppAppearance.save(context(), 1);
        var controller = Robolectric.buildActivity(DailyReaderActivity.class, DailyReaderActivity.intent(context(), spec, 0)).setup();
        try {
            var activity = controller.get(); WebView web = web(activity.getWindow().getDecorView());
            assertTrue(web.getSettings().getBlockNetworkLoads()); assertTrue(web.getSettings().getBlockNetworkImage());
            assertFalse(web.getSettings().getJavaScriptEnabled()); assertFalse(web.getSettings().getAllowFileAccess()); assertFalse(web.getSettings().getAllowContentAccess());
            String html = Shadows.shadowOf(web).getLastLoadDataWithBaseURL().data;
            assertTrue(html.contains("default-src 'none'")); assertTrue(html.contains("data-dl-theme='dark'")); assertTrue(html.contains("data-dl-mode='portugues'"));
            text(activity.getWindow().getDecorView(), "Latim").performClick();
            assertTrue(Shadows.shadowOf(web).getLastLoadDataWithBaseURL().data.contains("data-dl-mode='latin'"));
            controller.recreate(); assertEquals(1, context().getSharedPreferences("reader", 0).getInt("languageMode", -1));
            File previews = new File("build/previews"); previews.mkdirs();
            JSONObject captured;
            try (var stream = getClass().getResourceAsStream("/daily-captured.json")) {
                captured = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            DailyOfficeStore.put(context(), spec, 1, captured);
            for (boolean dark : new boolean[] {false, true}) for (int lang = 0; lang < 3; lang++)
                Files.write(new File(previews, "daily-" + dark + "-" + lang + ".html").toPath(), DailyOfficeStore.document(context(), DailyOfficeStore.get(context(), spec, 1), dark, lang).getBytes(StandardCharsets.UTF_8));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void downloadSkipsCompletedHoursKeepsPartialProgressAndResumesAfterRecreation() throws Exception {
        var spec = office(); DailyOfficeStore.put(context(), spec, 0, snapshot(spec, 0));
        var controller = Robolectric.buildActivity(DailyDownloadActivity.class, DailyDownloadActivity.intent(context(), spec.day, spec.version, null)).setup();
        try {
            WebView view = web(controller.get().getWindow().getDecorView());
            assertEquals(DailyOfficeStore.request(spec, 1), Shadows.shadowOf(view).getLastLoadedUrl());
            view.getWebViewClient().onPageFinished(view, DailyOfficeStore.request(spec, 1));
            Shadows.shadowOf(view).getLastEvaluatedJavascriptCallback().onReceiveValue("{}");
            Shadows.shadowOf(view).getLastEvaluatedJavascriptCallback().onReceiveValue(snapshot(spec, 1).toString());
            assertEquals(2, DailyOfficeStore.count(context(), spec));
            assertEquals(DailyOfficeStore.request(spec, 2), Shadows.shadowOf(view).getLastLoadedUrl());
            controller.recreate(); view = web(controller.get().getWindow().getDecorView());
            assertEquals(DailyOfficeStore.request(spec, 2), Shadows.shadowOf(view).getLastLoadedUrl());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46));
            assertEquals(2, DailyOfficeStore.count(context(), spec));
            assertEquals(DailyOfficeStore.request(spec, 3), Shadows.shadowOf(view).getLastLoadedUrl());
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void completedDownloadNeedsNoNetworkAndReportsAllNineSaved() throws Exception {
        var spec = office(); for (int i = 0; i < 8; i++) DailyOfficeStore.put(context(), spec, i, snapshot(spec, i));
        var mass = DailyOfficeStore.spec(context(), spec.day, spec.version, true); DailyOfficeStore.put(context(), mass, 0, snapshot(mass, 0));
        var controller = Robolectric.buildActivity(DailyDownloadActivity.class, DailyDownloadActivity.intent(context(), spec.day, spec.version, mass.version)).setup();
        try {
            assertNotNull(text(controller.get().getWindow().getDecorView(), "Pronto: 9/9 leituras disponíveis offline"));
            assertNull(Shadows.shadowOf(web(controller.get().getWindow().getDecorView())).getLastLoadedUrl());
            assertFalse(context().getSharedPreferences("reader",0).contains("pendingBatch"));
        } finally { controller.pause().stop().destroy(); }
    }
    static WebView web(View root) {
        if (root instanceof WebView) return (WebView) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) { WebView w = web(((ViewGroup) root).getChildAt(i)); if (w != null) return w; }
        return null;
    }
    static TextView text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) { TextView t = text(((ViewGroup) root).getChildAt(i), value); if (t != null) return t; }
        return null;
    }
}
