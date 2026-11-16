package org.divinumofficium.reader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Full, separate version lists copied from the upstream dialog files at build time. */
final class RubricCatalog {
    static final String DEFAULT = "Rubrics 1960 - 1960";
    private final Option[] office;
    private final Option[] mass;

    static final class Option {
        final String label;
        final String value;
        final boolean mass;
        private Option(String label, String value, boolean mass) {
            this.label = label;
            this.value = value;
            this.mass = mass;
        }
    }

    RubricCatalog(String json) throws JSONException {
        JSONObject data = new JSONObject(json);
        office = parse(data.getJSONArray("office"), false);
        mass = parse(data.getJSONArray("mass"), true);
    }

    private static Option[] parse(JSONArray items, boolean mass) throws JSONException {
        if (items.length() == 0) throw new JSONException("Lista de rubricas vazia");
        Option[] options = new Option[items.length()];
        boolean hasDefault = false;
        java.util.HashSet<String> values = new java.util.HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String label = item.getString("label"), value = item.getString("value");
            if (label.trim().isEmpty() || value.trim().isEmpty() || !values.add(value)) throw new JSONException("Rubrica inválida");
            options[i] = new Option(label, value, mass);
            hasDefault |= DEFAULT.equals(value);
        }
        if (!hasDefault) throw new JSONException("Rubrica padrão ausente");
        return options;
    }

    Option[] options(boolean isMass) { return (isMass ? mass : office).clone(); }

    String[] labels(boolean isMass) {
        Option[] options = options(isMass);
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = options[i].label;
        return labels;
    }

    int index(boolean isMass, String value) {
        Option[] options = options(isMass);
        for (int i = 0; i < options.length; i++) if (options[i].value.equals(value)) return i;
        for (int i = 0; i < options.length; i++) if (DEFAULT.equals(options[i].value)) return i;
        throw new IllegalStateException("Rubrica padrão ausente");
    }

    Option selected(boolean isMass, String value) { return options(isMass)[index(isMass, value)]; }

    static String legacyValue(int oldIndex) {
        switch (oldIndex) {
            case 1: return "Divino Afflatu - 1954";
            case 2: return "Tridentine - 1570";
            default: return DEFAULT;
        }
    }
}
