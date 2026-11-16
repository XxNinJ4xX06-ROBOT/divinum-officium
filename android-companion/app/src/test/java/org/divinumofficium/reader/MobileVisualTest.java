package org.divinumofficium.reader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Renders the actual native layouts for visual review at a phone-sized viewport. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MobileVisualTest {
    @Test public void newNativeFeaturesRenderAtPhoneWidth()throws Exception{
        var context=org.robolectric.RuntimeEnvironment.getApplication();AppAppearance.save(context,0);
        var book=BookStore.catalog(context).find("compendio-catecismo");PersonalStore.save(context,PersonalStore.book(book,book.url,0,100),"A fé e a razão","Anotação pessoal para revisar depois.",true,null);
        for(Class<? extends android.app.Activity> type:new Class[]{StudyActivity.class,PersonalActivity.class,OfflineSearchActivity.class,ReminderActivity.class}){
            var controller=Robolectric.buildActivity(type).setup();try{capture(controller.get().getWindow().getDecorView(),type.getSimpleName());}finally{controller.pause().stop().destroy();}
        }
    }
    @Test public void completedDailyPackagesRenderInBothThemes() throws Exception {
        var context = org.robolectric.RuntimeEnvironment.getApplication();
        var spec = DailyOfficeStore.spec(context, "2026-11-16", RubricCatalog.DEFAULT, false);
        for (int i = 0; i < spec.size(); i++) DailyOfficeStore.put(context, spec, i, DailyOfficeTest.snapshot(spec, i));
        var mass = DailyOfficeStore.spec(context, spec.day, spec.version, true);
        DailyOfficeStore.put(context, mass, 0, DailyOfficeTest.snapshot(mass, 0));
        for (int theme : new int[] {0, 1}) {
            AppAppearance.save(context, theme);
            var main = Robolectric.buildActivity(MainActivity.class).setup();
            try { main.get().showSaved(); capture(main.get().getWindow().getDecorView(), "dias-salvos-" + theme); }
            finally { main.pause().stop().destroy(); }
            var download = Robolectric.buildActivity(DailyDownloadActivity.class, DailyDownloadActivity.intent(context, spec.day, spec.version, mass.version)).setup();
            try { capture(download.get().getWindow().getDecorView(), "dia-concluido-" + theme); }
            finally { download.pause().stop().destroy(); }
        }
    }
    @Test public void darkScreensRenderWithReadableNativeSurfaces() throws Exception {
        AppAppearance.save(org.robolectric.RuntimeEnvironment.getApplication(), 1);
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            capture(activity.getWindow().getDecorView(), "inicio-escuro");
            activity.showScreen(1); capture(activity.getWindow().getDecorView(), "oficio-escuro");
            activity.showScreen(3); capture(activity.getWindow().getDecorView(), "biblioteca-escuro");
            AppearanceAndLibraryTest.description(activity.getWindow().getDecorView(), "Biblioteca: Teologia").performClick();
            capture(activity.getWindow().getDecorView(), "teologia-escuro");
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void fullRubricSelectorAndSearchRenderAtPhoneWidth() throws Exception {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            activity.showScreen(1);
            activity.showRubrics(false, "");
            View decor = activity.getWindow().getDecorView();
            assertEquals(15, ((android.view.ViewGroup) decor.findViewWithTag("rubric-options")).getChildCount());
            capture(decor, "rubricas-oficio");
            activity.showRubrics(false, "Altovado");
            assertEquals(1, ((android.view.ViewGroup) decor.findViewWithTag("rubric-options")).getChildCount());
            capture(decor, "rubricas-busca");
            activity.showScreen(2);
            activity.showRubrics(true, "");
            assertEquals(8, ((android.view.ViewGroup) decor.findViewWithTag("rubric-options")).getChildCount());
            capture(decor, "rubricas-missa");
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void nativeScreensRenderAtPhoneWidth() throws Exception {
        var controller = Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity = controller.get();
            String[] names = {"inicio", "oficio", "missa", "biblioteca"};
            for (int i = 0; i < names.length; i++) {
                activity.showScreen(i);
                View decor = activity.getWindow().getDecorView();
                capture(decor, names[i]);
            }
        } finally { controller.pause().stop().destroy(); }
    }

    private static void capture(View decor, String name) throws Exception {
        decor.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 393, 852);
        decor.getViewTreeObserver().dispatchOnGlobalLayout();
        assertEquals(393, decor.getWidth());
        View scroll = decor.findViewWithTag("home");
        if (scroll != null) assertEquals(0, scroll.getScrollY());
        Bitmap bitmap = Bitmap.createBitmap(393, 852, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File file = new File("build/previews/" + name + ".png");
        file.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}
