package org.divinumofficium.reader;
import android.app.*;
import android.content.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={26,35})
public class PersonalFeaturesTest {
    private Context c(){return RuntimeEnvironment.getApplication();}
    private JSONObject ref(){BookCatalog.Book b=BookStore.catalog(c()).find("compendio-catecismo");return PersonalStore.book(b,b.url,0,132);}
    @Test public void personalRecordsRoundTripWithoutExportingDownloadsOrEnablingReminders()throws Exception{
        PersonalStore.save(c(),ref(),"A fé e a razão","Minha nota",true,null);PersonalStore.complete(c(),"compendio-catecismo",true);
        PersonalStore.prefs(c()).edit().putString("appearance","dark").putInt("languageMode",2).putString("pages","private-path").commit();
        String backup=PersonalStore.exportData(c());assertFalse(backup.contains("private-path"));assertFalse(backup.contains("cookies"));
        PersonalStore.prefs(c()).edit().remove(PersonalStore.KEY).putString("appearance","light").commit();
        PersonalStore.importData(c(),backup);assertEquals(1,PersonalStore.items(c()).length());assertEquals("Minha nota",PersonalStore.items(c()).getJSONObject(0).getString("note"));
        assertTrue(PersonalStore.completed(c(),"compendio-catecismo"));assertEquals("dark",PersonalStore.prefs(c()).getString("appearance",""));
        assertEquals("private-path",PersonalStore.prefs(c()).getString("pages",""));assertFalse(ReminderPlanner.prefs(c()).getBoolean("enabled:1",false));
        PersonalStore.importData(c(),backup);assertEquals(1,PersonalStore.items(c()).length());
    }
    @Test public void invalidBackupDoesNotPartiallyApplyPreferencesOrRecords()throws Exception{
        PersonalStore.save(c(),ref(),"trecho","nota",true,null);String before=PersonalStore.exportData(c());
        JSONObject bad=new JSONObject(before);bad.getJSONObject("settings").put("appearance","dark").put("zoom",900);
        String old=PersonalStore.data(c()).toString();try{PersonalStore.importData(c(),bad.toString());fail();}catch(IOException expected){}
        assertEquals(old,PersonalStore.data(c()).toString());assertFalse(PersonalStore.prefs(c()).contains("appearance"));
        bad=new JSONObject(before);bad.getJSONObject("data").getJSONArray("items").getJSONObject(0).getJSONObject("ref").put("url","file:///data/private");
        try{PersonalStore.importData(c(),bad.toString());fail();}catch(IOException expected){}assertEquals(old,PersonalStore.data(c()).toString());
        byte[] tooBig=new byte[2*1024*1024+1];try{PersonalStore.readBackup(new ByteArrayInputStream(tooBig));fail();}catch(IOException expected){}
    }
    @Test public void notesEditRemoveAndNewestMergePreserveOtherRecords()throws Exception{
        PersonalStore.save(c(),ref(),"Um trecho","Antes",true,null);JSONObject item=PersonalStore.items(c()).getJSONObject(0);String backup=PersonalStore.exportData(c());
        PersonalStore.save(c(),ref(),"Um trecho","Depois",false,item.getString("id"));
        JSONObject older=new JSONObject(backup);older.getJSONObject("data").getJSONArray("items").getJSONObject(0).put("updated",0);
        PersonalStore.importData(c(),older.toString());assertEquals("Depois",PersonalStore.items(c()).getJSONObject(0).getString("note"));
        PersonalStore.save(c(),ref(),"Outro","",true,null);PersonalStore.remove(c(),item.getString("id"));assertEquals(1,PersonalStore.items(c()).length());assertEquals("Outro",PersonalStore.items(c()).getJSONObject(0).getString("quote"));
    }
    @Test public void bookmarksRestoreCoordinatesAndRequireCorrectDailyPackage()throws Exception{
        Intent book=PersonalStore.readingIntent(c(),ref());assertEquals(132,book.getIntExtra("scroll",0));assertTrue(book.getBooleanExtra("savedPosition",false));
        DailyOfficeStore.Spec spec=DailyOfficeStore.spec(c(),"2026-11-16",RubricCatalog.DEFAULT,false);JSONObject daily=PersonalStore.daily(spec,1,400);
        try{PersonalStore.readingIntent(c(),daily);fail();}catch(IOException expected){}
        DailyOfficeStore.put(c(),spec,1,DailyOfficeTest.snapshot(spec,1));Intent open=PersonalStore.readingIntent(c(),daily);assertEquals(400,open.getIntExtra("scroll",0));assertEquals(1,open.getIntExtra("hour",-1));
    }
    @Test public void studyPlanCoversCatalogExactlyAndProgressPersists()throws Exception{
        JSONArray plan=new JSONObject(BookStore.asset(c(),"study.json")).getJSONArray("items");HashSet<String> ids=new HashSet<>();
        for(int i=0;i<plan.length();i++){JSONObject row=plan.getJSONObject(i);assertTrue(ids.add(row.getString("book")));assertNotNull(BookStore.catalog(c()).find(row.getString("book")));assertTrue(Arrays.asList("Iniciante","Intermediário","Avançado").contains(row.getString("level")));assertFalse(row.getString("topic").isEmpty());}
        assertEquals(BookStore.catalog(c()).all().length,ids.size());PersonalStore.complete(c(),"confissoes",true);assertTrue(PersonalStore.completed(c(),"confissoes"));PersonalStore.complete(c(),"confissoes",false);assertFalse(PersonalStore.completed(c(),"confissoes"));
    }
    @Test public void todayHeadingUsesExactDateAndRubricIncludingLegacySnapshots()throws Exception{
        DailyOfficeStore.Spec spec=DailyOfficeStore.spec(c(),"2026-11-16",RubricCatalog.DEFAULT,false);assertEquals("",TodayInfo.heading(c(),spec));
        JSONObject snapshot=DailyOfficeTest.snapshot(spec,1);snapshot.put("fragment",snapshot.getString("fragment").replace("Dia litúrgico","S. Gertrudis Virginis ~ III classis<br>Tempore: Hebdomada XXVI"));
        DailyOfficeStore.put(c(),spec,1,snapshot);assertTrue(TodayInfo.heading(c(),spec).contains("S. Gertrudis"));assertTrue(TodayInfo.heading(c(),spec).contains("Tempore"));
        assertEquals("",TodayInfo.heading(c(),DailyOfficeStore.spec(c(),"2026-11-17",spec.version,false)));
        assertEquals("",TodayInfo.heading(c(),DailyOfficeStore.spec(c(),spec.day,"Divino Afflatu - 1954",false)));
    }
    @Test public void sevenDayDownloadCrossesYearAndSkipsExistingPages()throws Exception{
        DailyOfficeStore.Spec spec=DailyOfficeStore.spec(c(),"2026-12-31",RubricCatalog.DEFAULT,false),mass=DailyOfficeStore.spec(c(),spec.day,spec.version,true);
        for(int hour=0;hour<8;hour++)DailyOfficeStore.put(c(),spec,hour,DailyOfficeTest.snapshot(spec,hour));DailyOfficeStore.put(c(),mass,0,DailyOfficeTest.snapshot(mass,0));
        var controller=Robolectric.buildActivity(DailyDownloadActivity.class,DailyDownloadActivity.intent(c(),spec.day,spec.version,spec.version).putExtra("days",7)).setup();
        try{var web=DailyOfficeTest.web(controller.get().getWindow().getDecorView());assertEquals(DailyOfficeStore.request(DailyOfficeStore.spec(c(),"2027-01-01",spec.version,false),0),Shadows.shadowOf(web).getLastLoadedUrl());}
        finally{controller.pause().stop().destroy();}assertEquals(8,DailyOfficeStore.count(c(),spec));
        JSONObject pending=new JSONObject(PersonalStore.prefs(c()).getString("pendingBatch","{}"));assertEquals(7,pending.getInt("days"));assertEquals("2026-12-31",pending.getString("day"));
    }
    @Test public void offlineSearchFindsAccentsHtmlDailyAndPdfPagesWithoutNetwork()throws Exception{
        DailyOfficeStore.Spec spec=DailyOfficeStore.spec(c(),"2026-11-16",RubricCatalog.DEFAULT,false);DailyOfficeStore.put(c(),spec,1,DailyOfficeTest.snapshot(spec,1));
        BookCatalog.Book book=BookStore.catalog(c()).find("compendio-catecismo");File file=BookStore.file(c(),book);file.getParentFile().mkdirs();Files.write(file.toPath(),"<style>oculto</style><h1>Espírito Santo e caridade</h1>".getBytes(StandardCharsets.UTF_8));
        PDFBoxResourceLoader.init(c());File pdf=BookStore.file(c(),BookStore.catalog(c()).find("confissoes"));
        try(PDDocument doc=new PDDocument()){PDPage page=new PDPage();doc.addPage(page);try(PDPageContentStream content=new PDPageContentStream(doc,page)){content.beginText();content.setFont(PDType1Font.HELVETICA,12);content.newLineAtOffset(30,700);content.showText("Caridade e verdade no texto");content.endText();}doc.save(pdf);}
        OfflineSearch.Result found=OfflineSearch.search(c(),"espirito");assertEquals(2,found.hits.size());assertEquals(3,found.documents);
        OfflineSearch.Result pdfHit=OfflineSearch.search(c(),"verdade");assertEquals(1,pdfHit.hits.size());assertEquals(0,pdfHit.hits.get(0).ref.getInt("page"));assertEquals("confissoes",pdfHit.hits.get(0).ref.getString("book"));
        assertTrue(OfflineSearch.search(c(),"oculto").hits.isEmpty());assertEquals(1,OfflineSearch.pdfPages(c(),pdf,"confissoes").length());
        assertEquals(1,OfflineSearch.search(c(),"espirito","compendio-catecismo").hits.size());
    }
    @Test public void remindersAreOffUntilChosenAndCancelWhenDisabled(){
        for(int i=0;i<8;i++)assertFalse(ReminderPlanner.prefs(c()).getBoolean("enabled:"+i,false));
        if(android.os.Build.VERSION.SDK_INT>=33)Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        ReminderPlanner.all(c());var alarms=Shadows.shadowOf(c().getSystemService(AlarmManager.class));assertEquals(0,alarms.getScheduledAlarms().size());
        ReminderPlanner.prefs(c()).edit().putBoolean("enabled:1",true).putInt("minute:1",370).commit();ReminderPlanner.schedule(c(),1);assertEquals(1,alarms.getScheduledAlarms().size());
        ReminderPlanner.prefs(c()).edit().putBoolean("enabled:1",false).commit();ReminderPlanner.schedule(c(),1);assertEquals(0,alarms.getScheduledAlarms().size());
    }
    @Test public void remindersRecalculateLocalTimeAcrossMidnightAndDaylightSaving(){
        ZoneId brazil=ZoneId.of("America/Sao_Paulo");long now=ZonedDateTime.of(2026,11,16,23,50,0,0,brazil).toInstant().toEpochMilli();
        ZonedDateTime next=Instant.ofEpochMilli(ReminderPlanner.next(now,6*60,brazil)).atZone(brazil);assertEquals(LocalDate.of(2026,11,17),next.toLocalDate());assertEquals(6,next.getHour());
        ZoneId ny=ZoneId.of("America/New_York");long dst=ZonedDateTime.of(2026,3,7,23,0,0,0,ny).toInstant().toEpochMilli();assertTrue(ReminderPlanner.next(dst,150,ny)>dst);
    }
    @Test public void enabledReminderPostsLocallyAndBootOnlyReschedules(){
        if(android.os.Build.VERSION.SDK_INT>=33)Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        ReminderPlanner.prefs(c()).edit().putBoolean("enabled:1",true).commit();ReminderReceiver receiver=new ReminderReceiver();
        receiver.onReceive(c(),new Intent(Intent.ACTION_BOOT_COMPLETED));var notifications=Shadows.shadowOf(c().getSystemService(NotificationManager.class));assertNull(notifications.getNotification(1401));
        receiver.onReceive(c(),new Intent("org.divinumofficium.reader.REMIND").putExtra("hour",1));Notification n=notifications.getNotification(1401);assertNotNull(n);assertEquals("Laudes",n.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(1,Shadows.shadowOf(n.contentIntent).getSavedIntent().getIntExtra("reminderHour",-1));
        c().getSystemService(NotificationManager.class).cancelAll();ReminderPlanner.prefs(c()).edit().putBoolean("enabled:1",false).commit();
        receiver.onReceive(c(),new Intent("org.divinumofficium.reader.REMIND").putExtra("hour",1));assertNull(notifications.getNotification(1401));
    }
}
