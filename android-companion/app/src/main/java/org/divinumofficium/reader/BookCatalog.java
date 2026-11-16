package org.divinumofficium.reader;

import java.net.URI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BookCatalog {
    static final class Book {
        final String id, category, title, author, language, description, source, url, sourceUrl, format;
        final boolean offline;
        Book(JSONObject value) throws JSONException {
            id = value.getString("id"); category = value.getString("category");
            title = value.getString("title"); author = value.getString("author");
            language = value.getString("language"); description = value.getString("description");
            source = value.getString("source"); url = value.getString("url");
            sourceUrl = value.getString("sourceUrl"); format = value.getString("format");
            offline = value.getBoolean("offline");
            if (!id.matches("[a-z0-9-]+") || !("filosofia".equals(category) || "teologia".equals(category))
                    || !("html".equals(format) || "pdf".equals(format)) || !https(url) || !https(sourceUrl))
                throw new JSONException("Livro inválido");
        }
    }
    private final Book[] books;
    BookCatalog(String json) throws JSONException {
        JSONArray values = new JSONObject(json).getJSONArray("books");
        books = new Book[values.length()];
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < books.length; i++) {
            books[i] = new Book(values.getJSONObject(i));
            if (!ids.add(books[i].id)) throw new JSONException("Livro duplicado");
        }
    }
    Book[] all() { return books.clone(); }
    Book find(String id) {
        for (Book book : books) if (book.id.equals(id)) return book;
        return null;
    }
    static boolean https(String url) {
        try {
            URI u = new URI(url);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && u.getUserInfo() == null
                    && (u.getPort() == -1 || u.getPort() == 443);
        } catch (Exception e) { return false; }
    }
    static boolean allows(Book book, String url) {
        if (!https(url)) return false;
        String host = URI.create(url).getHost().toLowerCase(java.util.Locale.ROOT);
        String source = URI.create(book.url).getHost().toLowerCase(java.util.Locale.ROOT);
        if (host.replaceFirst("^www\\.", "").equals(source.replaceFirst("^www\\.", ""))) return true;
        return "pdf".equals(book.format) && "drive.google.com".equals(source)
                && ("drive.usercontent.google.com".equals(host) || host.endsWith(".googleusercontent.com"));
    }
}
