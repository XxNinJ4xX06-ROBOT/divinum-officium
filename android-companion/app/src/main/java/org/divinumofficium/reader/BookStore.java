package org.divinumofficium.reader;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BookStore {
    private BookStore() { }
    static String asset(Context context, String name) throws IOException {
        try (InputStream input = context.getAssets().open(name)) {
            java.util.Scanner scanner = new java.util.Scanner(input, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
    static BookCatalog catalog(Context context) {
        try { return new BookCatalog(asset(context, "books.json")); }
        catch (Exception e) { throw new IllegalStateException("Catálogo de livros inválido", e); }
    }
    static File file(Context context, BookCatalog.Book book) {
        return new File(new File(context.getFilesDir(), "books"), book.id + "." + book.format);
    }
    static boolean cached(Context context, BookCatalog.Book book) {
        return book.offline && file(context, book).isFile() && file(context, book).length() > 0;
    }

    static File load(Context context, BookCatalog.Book book, String address) throws IOException {
        boolean cacheRoot = book.offline && book.url.equals(address);
        File target = cacheRoot ? file(context, book) : new File(context.getCacheDir(), "book-" + java.util.UUID.randomUUID() + ".html");
        if (cacheRoot && cached(context, book)) return target;
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Não foi possível guardar a leitura.");
        File temporary = new File(parent, ".download-" + java.util.UUID.randomUUID());
        HttpURLConnection connection = null;
        try {
            String url = address;
            for (int redirects = 0; redirects <= 5; redirects++) {
                if (!BookCatalog.allows(book, url)) throw new IOException("Endereço fora da fonte do livro.");
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "DivinumLeitura/0.3 Android");
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirecionamento sem endereço.");
                    url = URI.create(url).resolve(location).toString();
                    connection.disconnect(); connection = null;
                    continue;
                }
                if (status != 200) throw new IOException("A fonte retornou erro " + status + ".");
                long limit = "pdf".equals(book.format) ? 48L * 1024 * 1024 : 8L * 1024 * 1024;
                if (connection.getContentLengthLong() > limit) throw new IOException("Arquivo maior que o limite desta leitura.");
                try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[16384]; long total = 0; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download interrompido.");
                        total += count;
                        if (total > limit) throw new IOException("Arquivo maior que o limite desta leitura.");
                        output.write(buffer, 0, count);
                    }
                }
                if ("pdf".equals(book.format)) {
                    try (InputStream input = new FileInputStream(temporary)) {
                        byte[] magic = new byte[5];
                        if (input.read(magic) != 5 || !"%PDF-".equals(new String(magic, StandardCharsets.US_ASCII)))
                            throw new IOException("A fonte não entregou o PDF. Use Abrir fonte para conferir.");
                    }
                } else {
                    byte[] bytes = Files.readAllBytes(temporary.toPath());
                    String type = connection.getContentType();
                    Matcher encoding = Pattern.compile("charset=[\"']?([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE).matcher(type == null ? "" : type);
                    Charset charset = StandardCharsets.UTF_8;
                    if (encoding.find()) {
                        try { charset = Charset.forName(encoding.group(1)); } catch (Exception ignored) { }
                    } else {
                        Matcher meta = Pattern.compile("charset=[\"']?([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE)
                                .matcher(new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.ISO_8859_1));
                        if (meta.find()) try { charset = Charset.forName(meta.group(1)); } catch (Exception ignored) { }
                    }
                    Files.write(temporary.toPath(), new String(bytes, charset).getBytes(StandardCharsets.UTF_8));
                }
                if (!temporary.renameTo(target)) throw new IOException("Não foi possível guardar o arquivo.");
                return target;
            }
            throw new IOException("A fonte redirecionou muitas vezes.");
        } finally {
            if (connection != null) connection.disconnect();
            if (temporary.exists()) temporary.delete();
        }
    }

    static String present(String raw, String css, AppAppearance.Palette palette) {
        String style = css.replace("{{paper}}", hex(palette.paper)).replace("{{ink}}", hex(palette.ink))
                .replace("{{accent}}", hex(palette.accent)).replace("{{line}}", hex(palette.line));
        String cleaned = raw.replaceAll("(?is)<script\\b[^>]*>.*?</script\\s*>", "")
                .replaceAll("(?is)<iframe\\b[^>]*>.*?</iframe\\s*>", "")
                .replaceAll("(?is)<base\\b[^>]*>", "")
                .replaceAll("(?is)<link\\b[^>]*>", "")
                .replaceAll("(?is)<meta\\b[^>]*>", "");
        String head = "<meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>" + style + "</style>";
        Matcher end = Pattern.compile("</head\\s*>", Pattern.CASE_INSENSITIVE).matcher(cleaned);
        if (end.find()) return cleaned.substring(0, end.start()) + head + cleaned.substring(end.start());
        return "<!doctype html><html><head>" + head + "</head><body>" + cleaned + "</body></html>";
    }
    private static String hex(int value) { return String.format(java.util.Locale.ROOT, "#%06x", value & 0xffffff); }
}
