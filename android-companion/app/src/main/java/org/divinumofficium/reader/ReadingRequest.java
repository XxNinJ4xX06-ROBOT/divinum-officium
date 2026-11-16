package org.divinumofficium.reader;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Query values supported by the original Perl forms; no liturgical calculations here. */
final class ReadingRequest {
    static final String[] HOURS = {"Matutinum", "Laudes", "Prima", "Tertia", "Sexta", "Nona", "Vesperae", "Completorium"};
    static final String[] LABELS = {"Matinas", "Laudes", "Prima", "Terça", "Sexta", "Noa", "Vésperas", "Completas"};
    static final String[] DETAILS = {"Vigília e leituras", "Oração da manhã", "Primeira hora", "Terceira hora", "Sexta hora", "Nona hora", "Oração da tarde", "Oração da noite"};
    static String url(boolean mass, int hour, Calendar day, RubricCatalog.Option version) {
        if ((!mass && (hour < 0 || hour >= HOURS.length)) || version == null || version.mass != mass)
            throw new IllegalArgumentException("Opção inválida");
        String date = new SimpleDateFormat("MM-dd-yyyy", Locale.US).format(day.getTime());
        return UrlPolicy.BASE + (mass ? "/cgi-bin/missa/missa.pl?date=" : "/cgi-bin/horas/Pofficium.pl?date1=")
                + date + "&command=pray" + (mass ? "SanctaMissa" : HOURS[hour])
                + "&version=" + encode(version.value)
                + "&lang1=Latin&lang2=Portugues&interlinear=0&expand=tota";
    }

    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
}
