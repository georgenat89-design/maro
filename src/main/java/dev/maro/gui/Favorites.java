package dev.maro.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.maro.module.Module;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Modules starred in the menu, by name; saved in client.json. */
public final class Favorites {
    private static final Set<String> STARRED = new LinkedHashSet<>();

    private Favorites() {
    }

    private static String key(Module module) {
        return module.getName().toLowerCase(Locale.ROOT);
    }

    public static boolean starred(Module module) {
        return STARRED.contains(key(module));
    }

    public static void toggle(Module module) {
        if (!STARRED.remove(key(module))) STARRED.add(key(module));
    }

    public static JsonArray save() {
        JsonArray names = new JsonArray();
        STARRED.forEach(names::add);
        return names;
    }

    public static void load(JsonElement json) {
        STARRED.clear();
        if (json == null || !json.isJsonArray()) return;
        for (JsonElement name : json.getAsJsonArray())
            if (name.isJsonPrimitive()) STARRED.add(name.getAsString().toLowerCase(Locale.ROOT));
    }
}
