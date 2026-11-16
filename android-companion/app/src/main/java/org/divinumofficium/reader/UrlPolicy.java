package org.divinumofficium.reader;

import java.net.URI;
import java.net.URISyntaxException;

/** Keep the embedded browser on the official origin, always over TLS. */
final class UrlPolicy {
    static final String BASE = "https://www.divinumofficium.com";
    static final String OFFICE = BASE + "/cgi-bin/horas/Pofficium.pl?lang1=Latin&lang2=Portugues&interlinear=0";
    static final String MASS = BASE + "/cgi-bin/missa/missa.pl?lang1=Latin&lang2=Portugues&interlinear=0";

    static boolean isOfficial(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443 || uri.getPort() == 80)
                    && ("www.divinumofficium.com".equalsIgnoreCase(host)
                        || "divinumofficium.com".equalsIgnoreCase(host));
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }

    static String secure(String url) {
        if (!isOfficial(url)) throw new IllegalArgumentException("Endereço não permitido");
        return url.replaceFirst("(?i)^http:", "https:").replaceFirst("(?i):80(?=/|\\?|#|$)", "");
    }

    static boolean canOpenExternally(String url) {
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }
}
