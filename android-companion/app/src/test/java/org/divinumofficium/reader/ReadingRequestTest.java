package org.divinumofficium.reader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Calendar;
import java.util.GregorianCalendar;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class ReadingRequestTest {
    private RubricCatalog catalog() throws Exception {
        try (java.io.InputStream input = RuntimeEnvironment.getApplication().getAssets().open("rubrics.json")) {
            return new RubricCatalog(new java.util.Scanner(input, "UTF-8").useDelimiter("\\A").next());
        }
    }

    @Test public void officeUsesTheUpstreamMonthDayDateAndCanonicalHourName() throws Exception {
        String url = ReadingRequest.url(false, 6, new GregorianCalendar(2026, Calendar.NOVEMBER, 16), catalog().selected(false, RubricCatalog.DEFAULT));
        assertTrue(UrlPolicy.isOfficial(url));
        assertTrue(url.contains("date1=11-16-2026"));
        assertTrue(url.contains("command=prayVesperae"));
        assertTrue(url.contains("version=Rubrics+1960+-+1960"));
        assertTrue(url.contains("lang1=Latin&lang2=Portugues"));
    }
    @Test public void massUsesItsOwnDateParameterAndRubricalVersion() throws Exception {
        String url = ReadingRequest.url(true, 0, new GregorianCalendar(2026, Calendar.DECEMBER, 25), catalog().selected(true, "Divino Afflatu - 1954"));
        assertTrue(url.contains("missa.pl?date=12-25-2026"));
        assertTrue(url.contains("command=praySanctaMissa"));
        assertTrue(url.contains("version=Divino+Afflatu+-+1954"));
        assertFalse(url.contains("date1="));
    }
    @Test(expected = IllegalArgumentException.class) public void invalidHourCannotCreateAnUnexpectedCommand() throws Exception {
        ReadingRequest.url(false, -1, Calendar.getInstance(), catalog().selected(false, RubricCatalog.DEFAULT));
    }

    @Test public void everyBundledVersionCanBeRequestedUsingItsCanonicalValue() throws Exception {
        RubricCatalog catalog = catalog();
        assertEquals(15, catalog.options(false).length);
        assertEquals(8, catalog.options(true).length);
        for (boolean mass : new boolean[] {false, true}) {
            for (RubricCatalog.Option version : catalog.options(mass)) {
                String url = ReadingRequest.url(mass, 1, Calendar.getInstance(), version);
                String query = new java.net.URI(url).getRawQuery();
                String encoded = java.util.Arrays.stream(query.split("&")).filter(p -> p.startsWith("version=")).findFirst().get().substring(8);
                assertEquals(version.value, java.net.URLDecoder.decode(encoded, "UTF-8"));
            }
        }
        assertEquals("Monastic Tridentinum Cisterciensis Altovadensis", catalog.options(false)[13].value);
        assertEquals("Rubrics 1960 Newcalendar", catalog.options(true)[7].value);
    }

    @Test(expected = IllegalArgumentException.class) public void officeOnlyOptionCannotLeakIntoAMassRequest() throws Exception {
        ReadingRequest.url(true, 0, Calendar.getInstance(), catalog().options(false)[13]);
    }

    @Test public void stalePreferencesAndLegacyIndicesHaveAStableDefault() throws Exception {
        assertEquals("Divino Afflatu - 1954", RubricCatalog.legacyValue(1));
        assertEquals("Tridentine - 1570", RubricCatalog.legacyValue(2));
        assertEquals(RubricCatalog.DEFAULT, RubricCatalog.legacyValue(100));
        assertEquals(RubricCatalog.DEFAULT, catalog().selected(false, "removed version").value);
        assertEquals(RubricCatalog.DEFAULT, catalog().selected(true, "Monastic - 1963").value);
    }
}
