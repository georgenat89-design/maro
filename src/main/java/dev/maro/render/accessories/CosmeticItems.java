package dev.maro.render.accessories;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The weapon and tool skins drawn by tools/cosmeticgen.py: each a name ("Phoenix Grace"), the kind
 * of item it dresses (sword, pickaxe, shovel) and the item model it is drawn with.
 */
public final class CosmeticItems {
    private CosmeticItems() {
    }

    public enum Kind {SWORD, PICKAXE, SHOVEL}

    public record Skin(Kind kind, String id, String name) {
        public Identifier model() {
            return Identifier.of("maro", "cosmetic/" + id);
        }
    }

    private static final Map<Kind, Map<String, Skin>> BY_NAME = new LinkedHashMap<>();

    static {
        for (Kind k : Kind.values()) BY_NAME.put(k, new LinkedHashMap<>());
        try (InputStream in = CosmeticItems.class.getResourceAsStream("/assets/maro/cosmetics.json")) {
            if (in != null) {
                for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    Kind kind = Kind.valueOf(o.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT));
                    Skin skin = new Skin(kind, o.get("id").getAsString(), o.get("name").getAsString());
                    BY_NAME.get(kind).put(skin.name(), skin);
                }
            }
        } catch (Exception e) {
            // No skins: the lists are just None.
        }
    }

    /** "None" and then every skin of a kind, for a setting's choices. */
    public static String[] choices(Kind kind) {
        List<String> out = new ArrayList<>();
        out.add("None");
        out.addAll(BY_NAME.get(kind).keySet());
        return out.toArray(String[]::new);
    }

    public static List<Skin> all(Kind kind) {
        return Collections.unmodifiableList(new ArrayList<>(BY_NAME.get(kind).values()));
    }

    /** The skin of that name, or null for None. */
    public static Skin byName(Kind kind, String name) {
        return BY_NAME.get(kind).get(name);
    }
}
