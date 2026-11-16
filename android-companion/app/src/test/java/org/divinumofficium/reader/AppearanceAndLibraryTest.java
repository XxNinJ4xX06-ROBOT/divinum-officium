package org.divinumofficium.reader;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class AppearanceAndLibraryTest {
    @Test public void appearanceChoiceSurvivesRecreationAndRestoresLightColors() {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            AppAppearance.save(activity, 1); controller.recreate(); activity = controller.get();
            assertTrue(AppAppearance.isDark(activity));
            View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            assertEquals(Color.parseColor("#151619"), ((ColorDrawable) root.getBackground()).getColor());
            AppAppearance.save(activity, 0); controller.recreate(); activity = controller.get();
            assertFalse(AppAppearance.isDark(activity));
            root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            assertEquals(Color.parseColor("#F7F6F2"), ((ColorDrawable) root.getBackground()).getColor());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test @Config(qualifiers = "night") public void explicitPreferenceOverridesTheSystemAndAutoFollowsIt() {
        var context = RuntimeEnvironment.getApplication();
        AppAppearance.save(context, 2); assertTrue(AppAppearance.isDark(context));
        AppAppearance.save(context, 0); assertFalse(AppAppearance.isDark(context));
        AppAppearance.save(context, 1); assertTrue(AppAppearance.isDark(context));
    }

    @Test public void libraryOffersBothCategoriesSearchAndTheExistingSavedReadings() {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get(); activity.showScreen(3);
            View root = activity.getWindow().getDecorView();
            assertNotNull(find(root, "Aeterni Patris  ›"));
            EditText search = (EditText) description(root, "Buscar livros"); search.setText("tomas");
            assertNotNull(find(root, "O ente e a essência  ›"));
            assertNotNull(find(root, "Os princípios da natureza  ›"));
            assertNull(find(root, "Apologia de Sócrates  ›"));
            description(root, "Biblioteca: Teologia").performClick();
            assertNotNull(find(root, "Catecismo da Igreja Católica  ›"));
            assertNotNull(find(root, "Confissões  ›"));
            description(root, "Biblioteca: Salvos").performClick();
            assertNotNull(find(root, "Sua biblioteca está começando"));
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void openingABookUsesItsCatalogIdAndExplainsTheDownloadFirst() {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get(); activity.showScreen(3);
            ((View) find(activity.getWindow().getDecorView(), "Aeterni Patris  ›").getParent()).performClick();
            AlertDialog detail = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("Baixar e ler", detail.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            detail.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            Intent intent = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(BookReaderActivity.class.getName(), intent.getComponent().getClassName());
            assertEquals("aeterni-patris", intent.getStringExtra("bookId"));
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void aPreviouslyDownloadedBookCanBeReadWithoutAnotherRequest() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        BookCatalog.Book book = BookStore.catalog(app).find("aeterni-patris");
        java.io.File file = BookStore.file(app, book); file.getParentFile().mkdirs();
        java.nio.file.Files.write(file.toPath(), "<html><head></head><body><h1>Fé e razão</h1><p>Texto de prova.</p></body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(file, BookStore.load(app, book, book.url));
        AppAppearance.save(app, 1);
        var controller = Robolectric.buildActivity(BookReaderActivity.class, new Intent(app, BookReaderActivity.class).putExtra("bookId", book.id)).setup();
        try {
            BookReaderActivity activity = controller.get();
            var taskField = BookReaderActivity.class.getDeclaredField("task"); taskField.setAccessible(true);
            ((java.util.concurrent.Future<?>) taskField.get(activity)).get(5, java.util.concurrent.TimeUnit.SECONDS);
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            WebView web = findWeb(activity.getWindow().getDecorView());
            assertFalse(web.getSettings().getJavaScriptEnabled());
            assertFalse(web.getSettings().getAllowFileAccess());
            var data = org.robolectric.Shadows.shadowOf(web).getLastLoadDataWithBaseURL();
            assertEquals(book.url, data.baseUrl); assertTrue(data.data.contains("Fé e razão"));
            assertTrue(data.data.contains("#151619"));
        } finally { controller.pause().stop().destroy(); }
    }

    static View find(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    static View description(View view, String value) {
        if (value.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = description(((ViewGroup) view).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    static WebView findWeb(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            WebView found = findWeb(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
}
