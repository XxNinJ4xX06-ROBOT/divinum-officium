package org.divinumofficium.reader;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Durable, per-date/per-rubric snapshots. A failed update never replaces a valid reading. */
final class DailyOfficeStore {
    private static final int SCHEMA = 1;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    static final class Spec {
        final boolean mass;
        final String day, version, label, key;
        Spec(String day, RubricCatalog.Option option) {
            LocalDate.parse(day);
            this.day = day; mass = option.mass; version = option.value; label = option.label;
            key = hash((SCHEMA + "|" + day + "|" + mass + "|" + version + "|Latin|Portugues|Generale|Hodie|lay").getBytes(StandardCharsets.UTF_8));
        }
        int size() { return mass ? 1 : ReadingRequest.HOURS.length; }
        String id(int hour) {
            if (hour < 0 || hour >= size()) throw new IllegalArgumentException("Hora inválida");
            return mass ? "SanctaMissa" : ReadingRequest.HOURS[hour];
        }
        String title(int hour) { id(hour); return mass ? "Santa Missa" : ReadingRequest.LABELS[hour]; }
        String displayDay() { return LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")); }
        Calendar calendar() {
            LocalDate d = LocalDate.parse(day);
            Calendar c = Calendar.getInstance(); c.clear(); c.set(d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth(), 12, 0); return c;
        }
    }
    static final class Entry {
        final Spec spec;
        final int hour;
        final JSONObject snapshot;
        final long savedAt;
        Entry(Spec spec, int hour, JSONObject snapshot, long savedAt) {
            this.spec = spec; this.hour = hour; this.snapshot = snapshot; this.savedAt = savedAt;
        }
    }

    static String day(Calendar day) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
    }
    static RubricCatalog catalog(Context context) {
        try { return new RubricCatalog(BookStore.asset(context, "rubrics.json")); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static Spec spec(Context context, String day, String version, boolean mass) {
        RubricCatalog.Option option = catalog(context).selected(mass, version);
        if (!option.value.equals(version)) throw new IllegalArgumentException("Rubrica inválida");
        return new Spec(day, option);
    }
    static String request(Spec spec, int hour) {
        spec.id(hour);
        String date = LocalDate.parse(spec.day).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.ROOT));
        try {
            return UrlPolicy.BASE + (spec.mass ? "/cgi-bin/missa/missa.pl?date=" : "/cgi-bin/horas/Pofficium.pl?date1=")
                    + date + "&command=pray" + spec.id(hour) + "&version=" + java.net.URLEncoder.encode(spec.version, "UTF-8")
                    + "&lang1=Latin&lang2=Portugues&interlinear=0&expand=tota&votive=Hodie&dioecesis=Generale"
                    + "&priest=0&psalmvar=0&oldhymns=0" + (spec.mass ? "&rubrics=1" : "");
        } catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
    static boolean matches(Spec spec, int hour, String url) {
        if (!UrlPolicy.isOfficial(url)) return false;
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (!uri.getPath().equals(spec.mass ? "/cgi-bin/missa/missa.pl" : "/cgi-bin/horas/Pofficium.pl")) return false;
            Map<String, String> params = new HashMap<>();
            for (String pair : uri.getRawQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) params.put(java.net.URLDecoder.decode(kv[0], "UTF-8"), java.net.URLDecoder.decode(kv[1], "UTF-8"));
            }
            String[] date = params.get(spec.mass ? "date" : "date1").split("-");
            LocalDate actual = LocalDate.of(Integer.parseInt(date[2]), Integer.parseInt(date[0]), Integer.parseInt(date[1]));
            return spec.day.equals(actual.toString()) && spec.version.equals(params.get("version"))
                    && ("pray" + spec.id(hour)).equals(params.get("command")) && "Latin".equals(params.get("lang1"))
                    && "Portugues".equals(params.get("lang2"));
        } catch (Exception e) { return false; }
    }
    private static File directory(Context context, Spec spec) {
        return new File(new File(context.getFilesDir(), "daily-offices"), spec.key);
    }
    private static JSONObject metadata(Context context, Spec spec) throws Exception {
        File file = new File(directory(context, spec), "manifest.json");
        try (InputStream input = new AtomicFile(file).openRead()) {
            String text = new java.util.Scanner(input, "UTF-8").useDelimiter("\\A").next();
            JSONObject data = new JSONObject(text);
            if (data.getInt("schema") != SCHEMA || !spec.key.equals(data.getString("key"))
                    || !spec.day.equals(data.getString("day")) || !spec.version.equals(data.getString("version"))
                    || data.getBoolean("mass") != spec.mass) throw new IOException("Pacote incompatível");
            return data;
        }
    }
    static Entry get(Context context, Spec spec, int hour) {
        try {
            JSONObject item = metadata(context, spec).getJSONObject("entries").getJSONObject(spec.id(hour));
            String digest = item.getString("sha256"), name = item.getString("file");
            if (!digest.matches("[0-9a-f]{64}") || !name.equals("reading-" + digest + ".json")) return null;
            File file = new File(directory(context, spec), name);
            if (!file.isFile() || file.length() == 0 || file.length() > MAX_BYTES) return null;
            byte[] data = Files.readAllBytes(file.toPath());
            if (!hash(data).equals(digest)) return null;
            JSONObject snapshot = new JSONObject(new String(data, StandardCharsets.UTF_8));
            validate(spec, hour, snapshot);
            return new Entry(spec, hour, snapshot, item.getLong("savedAt"));
        } catch (Exception e) { return null; }
    }
    static int count(Context context, Spec spec) {
        int count = 0; for (int hour = 0; hour < spec.size(); hour++) if (get(context, spec, hour) != null) count++; return count;
    }
    static void validate(Spec spec, int hour, JSONObject snapshot) throws Exception {
        String fragment = snapshot.getString("fragment");
        if (!snapshot.getBoolean("valid") || snapshot.getInt("textLength") < 80 || !fragment.contains("dl-prayer")
                || !matches(spec, hour, snapshot.getString("source"))
                || java.util.regex.Pattern.compile("(?is)<(?:script|iframe|object|embed|foreignObject)\\b|\\son[a-z]+\\s*=").matcher(fragment).find())
            throw new IOException("A página não contém uma leitura válida para esta data e rubrica.");
    }
    static synchronized void put(Context context, Spec spec, int hour, JSONObject snapshot) throws Exception {
        validate(spec, hour, snapshot);
        byte[] bytes = snapshot.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Leitura grande demais para este pacote.");
        File dir = directory(context, spec);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar o pacote.");
        JSONObject manifest;
        try { manifest = metadata(context, spec); }
        catch (Exception e) {
            manifest = new JSONObject().put("schema", SCHEMA).put("key", spec.key).put("day", spec.day)
                    .put("version", spec.version).put("label", spec.label).put("mass", spec.mass).put("entries", new JSONObject());
        }
        String digest = hash(bytes), name = "reading-" + digest + ".json";
        JSONObject entries = manifest.getJSONObject("entries");
        JSONObject old = entries.optJSONObject(spec.id(hour));
        // Publish the manifest only after the new, immutable content file is durable.
        atomic(new File(dir, name), bytes);
        entries.put(spec.id(hour), new JSONObject().put("file", name).put("sha256", digest).put("savedAt", System.currentTimeMillis()));
        atomic(new File(dir, "manifest.json"), manifest.toString().getBytes(StandardCharsets.UTF_8));
        if (old != null && !name.equals(old.optString("file")) && old.optString("file").matches("reading-[0-9a-f]{64}\\.json"))
            new File(dir, old.optString("file")).delete();
    }
    private static void atomic(File target, byte[] bytes) throws IOException {
        AtomicFile atomic = new AtomicFile(target); FileOutputStream output = null;
        try { output = atomic.startWrite(); output.write(bytes); atomic.finishWrite(output); }
        catch (IOException e) { if (output != null) atomic.failWrite(output); throw e; }
    }
    static List<Spec> packages(Context context) {
        ArrayList<Spec> result = new ArrayList<>();
        File[] dirs = new File(context.getFilesDir(), "daily-offices").listFiles();
        if (dirs == null) return result;
        for (File dir : dirs) {
            if (!dir.isDirectory() || !dir.getName().matches("[0-9a-f]{64}")) continue;
            try (InputStream input = new AtomicFile(new File(dir, "manifest.json")).openRead()) {
                JSONObject m = new JSONObject(new java.util.Scanner(input, "UTF-8").useDelimiter("\\A").next());
                Spec spec = spec(context, m.getString("day"), m.getString("version"), m.getBoolean("mass"));
                if (dir.getName().equals(spec.key) && count(context, spec) > 0) result.add(spec);
            } catch (Exception ignored) { }
        }
        result.sort((a, b) -> b.day.compareTo(a.day)); return result;
    }
    static synchronized boolean remove(Context context, Spec spec) {
        File dir = directory(context, spec); File[] files = dir.listFiles(); boolean okay = true;
        if (files != null) for (File file : files) okay &= file.isFile() && file.delete();
        return (!dir.exists() || dir.delete()) && okay;
    }
    static String document(Context context, Entry entry, boolean dark, int language) throws Exception {
        String mode = new String[] {"portugues", "latin", "both"}[Math.max(0, Math.min(2, language))];
        String css = entry.snapshot.optString("css").replaceAll("(?i)</style", "") + "\n" + BookStore.asset(context, "reader.css");
        return "<!doctype html><html lang='pt-BR' data-dl-theme='" + (dark ? "dark" : "light") + "'><head><meta charset='UTF-8'>"
                + "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:; base-uri 'none'; form-action 'none'\">"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><style>" + css + "</style></head>"
                + "<body class='dl-mobile dl-adapted' data-dl-mode='" + mode + "'><form>" + entry.snapshot.getString("fragment") + "</form></body></html>";
    }
    private static String hash(byte[] bytes) {
        try {
            StringBuilder value = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) value.append(String.format(Locale.ROOT, "%02x", b & 255));
            return value.toString();
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
