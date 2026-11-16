package org.divinumofficium.reader;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.net.Uri;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before public void setup() {
        controller = Robolectric.buildActivity(MainActivity.class).setup();
        activity = controller.get();
    }
    @After public void cleanup() { controller.pause().stop().destroy(); }

    @Test public void opensOfficeWithPortugueseAndKeepsRemoteFileAccessDisabled() {
        activity.openPage(UrlPolicy.OFFICE, "Ofício");
        WebView web = findWeb(activity.getWindow().getDecorView());
        assertNotNull(web);
        assertEquals(UrlPolicy.OFFICE, org.robolectric.Shadows.shadowOf(web).getLastLoadedUrl());
        assertTrue(web.getSettings().getJavaScriptEnabled());
        assertFalse(web.getSettings().getAllowFileAccess());
        assertFalse(web.getSettings().getAllowContentAccess());
        assertTrue(visibleText(activity.getWindow().getDecorView(), "Ofício"));
    }

    @Test public void networkFailureOffersRecoveryAndKeepsSavingDisabled() {
        activity.openPage(UrlPolicy.MASS, "Missa");
        WebView web = findWeb(activity.getWindow().getDecorView());
        web.getWebViewClient().onReceivedHttpError(web, new WebResourceRequest() {
            public Uri getUrl() { return Uri.parse(UrlPolicy.MASS); }
            public boolean isForMainFrame() { return true; }
            public boolean isRedirect() { return false; }
            public boolean hasGesture() { return false; }
            public String getMethod() { return "GET"; }
            public java.util.Map<String, String> getRequestHeaders() { return java.util.Collections.emptyMap(); }
        }, new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", java.util.Collections.emptyMap(), null));
        View root = activity.getWindow().getDecorView();
        assertTrue(visibleText(root, "O site retornou erro 403"));
        assertTrue(visibleText(root, "Tentar novamente"));
        assertFalse(findText(root, "Salvar").isEnabled());
        activity.onBackPressed();
        assertTrue(visibleText(root, "Um tempo\npara o sagrado."));
    }

    @Test public void emptyOfflineLibraryExplainsHowToSaveAPage() {
        activity.showSaved();
        assertTrue(visibleText(activity.getWindow().getDecorView(), "Sua biblioteca está começando"));
    }

    @Test public void textPreferenceSurvivesRecreation() {
        activity.getSharedPreferences("reader", 0).edit().putInt("zoom", 150).commit();
        controller.recreate();
        activity = controller.get();
        assertEquals(150, findWeb(activity.getWindow().getDecorView()).getSettings().getTextZoom());
    }

    @Test public void savedReadingUsesOnlyItsRegisteredFileAndDisablesScripts() throws Exception {
        java.io.File directory = new java.io.File(activity.getFilesDir(), "leituras");
        directory.mkdirs();
        java.io.File archive = new java.io.File(directory, "test.mht");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(archive)) { output.write(1); }
        org.json.JSONArray pages = new org.json.JSONArray().put(new org.json.JSONObject()
                .put("file", "test.mht").put("title", "Completas").put("date", 1));
        activity.getSharedPreferences("reader", 0).edit().putString("pages", pages.toString()).commit();
        activity.showSaved();
        ((View) findText(activity.getWindow().getDecorView(), "Completas  ›").getParent()).performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        WebView web = findWeb(activity.getWindow().getDecorView());
        assertEquals(Uri.fromFile(archive).toString(), org.robolectric.Shadows.shadowOf(web).getLastLoadedUrl());
        assertFalse(web.getSettings().getJavaScriptEnabled());
        assertTrue(web.getSettings().getAllowFileAccess());
        activity.onBackPressed();
        assertTrue(visibleText(activity.getWindow().getDecorView(), "Biblioteca"));
        activity.openPage(UrlPolicy.OFFICE, "Ofício");
        assertFalse(web.getSettings().getAllowFileAccess());
        assertTrue(web.getSettings().getJavaScriptEnabled());
    }

    @Test public void hoursOpenDirectlyFromNativeCardsAndKeepNativeNavigationOnReturn() {
        activity.showScreen(1);
        View root = activity.getWindow().getDecorView();
        assertTrue(visibleText(root, "HORAS CANÔNICAS"));
        ((View) findText(root, "Laudes  ›").getParent()).performClick();
        WebView web = findWeb(root);
        String url = org.robolectric.Shadows.shadowOf(web).getLastLoadedUrl();
        assertTrue(url.contains("command=prayLaudes"));
        assertTrue(url.contains("expand=tota"));
        assertTrue(visibleText(root, "Português"));
        activity.onBackPressed();
        assertTrue(visibleText(root, "HORAS CANÔNICAS"));
    }

    @Test public void massScreenUsesTheMassCommandAndLanguagePreferencePersists() {
        activity.showScreen(2);
        View root = activity.getWindow().getDecorView();
        findText(root, "Abrir a Missa  →").performClick();
        assertTrue(org.robolectric.Shadows.shadowOf(findWeb(root)).getLastLoadedUrl().contains("command=praySanctaMissa"));
        findText(root, "Latim").performClick();
        assertEquals(1, activity.getSharedPreferences("reader", 0).getInt("languageMode", -1));
    }

    @Test public void allRubricsAreSelectableAndTheTwoSelectionsRemainIndependent() {
        activity.showScreen(1);
        View root = activity.getWindow().getDecorView();
        findDescription(root, "Escolher rubricas do Ofício").performClick();
        assertEquals(15, ((ViewGroup) root.findViewWithTag("rubric-options")).getChildCount());
        assertNotNull(findText(root, "Ordo Praedicatorum - 1962"));
        findText(root, "Ordo Cisterciensis - Abbatia B.M.V. de Altovado").performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        ((View) findText(root, "Laudes  ›").getParent()).performClick();
        assertTrue(org.robolectric.Shadows.shadowOf(findWeb(root)).getLastLoadedUrl().contains("version=Monastic+Tridentinum+Cisterciensis+Altovadensis"));

        activity.showScreen(2);
        findDescription(root, "Escolher rubricas da Missa").performClick();
        assertEquals(8, ((ViewGroup) root.findViewWithTag("rubric-options")).getChildCount());
        assertTrue(((android.widget.RadioButton) findText(root, "Rubrics 1960 - 1960")).isChecked());
        findText(root, "Rubrics 1960 - 2020 USA").performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        findText(root, "Abrir a Missa  →").performClick();
        assertTrue(org.robolectric.Shadows.shadowOf(findWeb(root)).getLastLoadedUrl().contains("version=Rubrics+1960+Newcalendar"));

        controller.recreate();
        activity = controller.get();
        root = activity.getWindow().getDecorView();
        activity.showScreen(1);
        findDescription(root, "Escolher rubricas do Ofício").performClick();
        assertTrue(((android.widget.RadioButton) findText(root, "Ordo Cisterciensis - Abbatia B.M.V. de Altovado")).isChecked());
        activity.showScreen(2);
        findDescription(root, "Escolher rubricas da Missa").performClick();
        assertTrue(((android.widget.RadioButton) findText(root, "Rubrics 1960 - 2020 USA")).isChecked());
    }

    @Test public void updatingFromThreeOptionsPreservesThePreviouslyChosenVersion() {
        activity.getSharedPreferences("reader", 0).edit().putInt("version", 1).commit();
        controller.recreate();
        activity = controller.get();
        View root = activity.getWindow().getDecorView();
        activity.showScreen(1);
        findDescription(root, "Escolher rubricas do Ofício").performClick();
        assertTrue(((android.widget.RadioButton) findText(root, "Divino Afflatu - 1954")).isChecked());
        activity.showScreen(2);
        findDescription(root, "Escolher rubricas da Missa").performClick();
        assertTrue(((android.widget.RadioButton) findText(root, "Divino Afflatu - 1954")).isChecked());
    }

    @Test public void rubricSearchShowsHiddenOptionsAndSurvivesRecreation() {
        activity.showScreen(1);
        View root = activity.getWindow().getDecorView();
        findDescription(root, "Escolher rubricas do Ofício").performClick();
        assertTrue(visibleText(root, "15 de 15 opções"));
        android.widget.EditText search = (android.widget.EditText) findDescription(root, "Buscar rubricas");
        search.setText("Altovado");
        assertTrue(visibleText(root, "1 de 15 opções"));
        assertEquals(1, ((ViewGroup) root.findViewWithTag("rubric-options")).getChildCount());
        assertNotNull(findText(root, "Ordo Cisterciensis - Abbatia B.M.V. de Altovado"));
        controller.recreate();
        activity = controller.get();
        root = activity.getWindow().getDecorView();
        assertTrue(visibleText(root, "1 de 15 opções"));
        search = (android.widget.EditText) findDescription(root, "Buscar rubricas");
        assertEquals("Altovado", search.getText().toString());
        search.setText("");
        assertEquals(15, ((ViewGroup) root.findViewWithTag("rubric-options")).getChildCount());
        activity.onBackPressed();
        assertTrue(visibleText(root, "HORAS CANÔNICAS"));
    }

    @Test public void savedDarkPagesAreNotInvertedTwiceAndLegacyPagesCanUseNightMode() throws Exception {
        AppAppearance.save(activity, 1); controller.recreate(); activity = controller.get();
        java.io.File directory = new java.io.File(activity.getFilesDir(), "leituras"); directory.mkdirs();
        java.io.File archive = new java.io.File(directory, "night.mht");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(archive)) { out.write(1); }
        org.json.JSONObject page = new org.json.JSONObject().put("file", "night.mht").put("title", "Noite").put("date", 1).put("dark", true);
        activity.getSharedPreferences("reader", 0).edit().putString("pages", new org.json.JSONArray().put(page).toString()).commit();
        activity.showSaved(); View root = activity.getWindow().getDecorView();
        ((View) findText(root, "Noite  ›").getParent()).performClick();
        assertEquals(View.LAYER_TYPE_NONE, findWeb(root).getLayerType());
        page.put("dark", false);
        activity.getSharedPreferences("reader", 0).edit().putString("pages", new org.json.JSONArray().put(page).toString()).commit();
        activity.showSaved(); ((View) findText(root, "Noite  ›").getParent()).performClick();
        assertEquals(View.LAYER_TYPE_HARDWARE, findWeb(root).getLayerType());
        assertFalse(findWeb(root).getSettings().getJavaScriptEnabled());
    }

    private static View findDescription(View view, String description) {
        if (view.getContentDescription() != null && description.contentEquals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static WebView findWeb(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView result = findWeb(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findText(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }
    private static boolean visibleText(View root, String text) {
        View view = findText(root, text);
        return view != null && view.isShown();
    }
}
