package org.divinumofficium.reader;

import org.junit.Test;
import static org.junit.Assert.*;

public class UrlPolicyTest {
    @Test public void officialLinksAreUpgradedWithoutChangingThePrayer() {
        assertTrue(UrlPolicy.isOfficial(UrlPolicy.OFFICE));
        assertTrue(UrlPolicy.isOfficial("http://divinumofficium.com:80/cgi-bin/horas/Pofficium.pl?date1=11-15-2026#hora"));
        assertEquals("https://divinumofficium.com/cgi-bin/horas/Pofficium.pl?date1=11-15-2026#hora",
                UrlPolicy.secure("http://divinumofficium.com:80/cgi-bin/horas/Pofficium.pl?date1=11-15-2026#hora"));
    }

    @Test public void lookalikesLocalFilesAndActiveSchemesStayOutOfTheEmbeddedBrowser() {
        String[] rejected = {"https://www.divinumofficium.com.attacker.test/", "https://attacker.test/divinumofficium.com",
                "https://divinumofficium.com@attacker.test/", "https://attacker@divinumofficium.com/",
                "https://divinumofficium.com:8443/", "file:///data/user/0/app/private", "content://test/file",
                "javascript:alert(1)", "intent://test", "data:text/html,test", "//divinumofficium.com/", "invalid url", null};
        for (String url : rejected) assertFalse(String.valueOf(url), UrlPolicy.isOfficial(url));
    }

    @Test public void externalNavigationRequiresAnHttpsAddress() {
        assertTrue(UrlPolicy.canOpenExternally("https://github.com/DivinumOfficium/divinum-officium"));
        assertFalse(UrlPolicy.canOpenExternally("http://example.org"));
        assertFalse(UrlPolicy.canOpenExternally("javascript:alert(1)"));
        assertFalse(UrlPolicy.canOpenExternally("https://user@example.org"));
    }
}
