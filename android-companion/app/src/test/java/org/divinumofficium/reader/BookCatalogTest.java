package org.divinumofficium.reader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class BookCatalogTest {
    @Test public void collectionIsCompleteAndOnlineIndexesAreNotClaimedAsDownloadedBooks() throws Exception {
        var app = RuntimeEnvironment.getApplication(); BookCatalog catalog = BookStore.catalog(app);
        assertEquals(16, catalog.all().length);
        int portuguese = 0, philosophy = 0, theology = 0;
        for (BookCatalog.Book book : catalog.all()) {
            if (book.language.equals("Português")) portuguese++;
            if (book.category.equals("filosofia")) philosophy++; else theology++;
            assertTrue(BookCatalog.https(book.url)); assertTrue(BookCatalog.https(book.sourceUrl));
        }
        assertEquals(10, portuguese); assertEquals(7, philosophy); assertEquals(9, theology);
        assertFalse(catalog.find("catecismo").offline); assertFalse(catalog.find("suma-teologica").offline);
        assertEquals("pdf", catalog.find("confissoes").format);
        assertNull(catalog.find("../../arbitrary-file"));
    }

    @Test public void downloadsCannotRedirectToLookalikeHostsOrArbitraryFiles() {
        BookCatalog catalog = BookStore.catalog(RuntimeEnvironment.getApplication());
        BookCatalog.Book book = catalog.find("compendio-catecismo");
        assertTrue(BookCatalog.allows(book, book.url));
        assertFalse(BookCatalog.allows(book, "https://www.vatican.va.attacker.test/file"));
        assertFalse(BookCatalog.allows(book, "https://attacker@www.vatican.va/file"));
        assertFalse(BookCatalog.allows(book, "file:///data/private"));
        assertFalse(BookCatalog.allows(book, "https://www.vatican.va:8443/file"));
        assertFalse(BookCatalog.allows(book, "javascript:alert(1)"));
        assertTrue(BookCatalog.allows(catalog.find("confissoes"), "https://drive.usercontent.google.com/download?id=test"));
        assertFalse(BookCatalog.allows(book, "https://drive.usercontent.google.com/download?id=test"));
    }

    @Test public void readerPresentationKeepsTheTextAndUsesTheRequestedPalette() {
        String raw = "<html><head><script>tracking()</script></head><body><h1>Fé &amp; razão</h1><p id='test'>Texto integral de prova.</p></body></html>";
        String css = "body{background:{{paper}};color:{{ink}}}";
        String dark = BookStore.present(raw, css, AppAppearance.palette(true));
        String light = BookStore.present(raw, css, AppAppearance.palette(false));
        assertTrue(dark.contains("Fé &amp; razão")); assertTrue(dark.contains("Texto integral de prova."));
        assertTrue(dark.contains("#151619")); assertTrue(light.contains("#f7f6f2"));
        assertFalse(dark.contains("<script>")); assertTrue(dark.contains("name='viewport'"));
    }
}
