package dev.maro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maro.Maro;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Two kinds of files live in {@code .minecraft/maro/}:
 * <ul>
 *     <li>{@code client.json} - GUI/theme settings, friends and the active config name</li>
 *     <li>{@code configs/<name>.json} - module states, binds and settings</li>
 * </ul>
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIR = FabricLoader.getInstance().getGameDir().resolve(Maro.MOD_ID);
    public static final Path CONFIG_DIR = DIR.resolve("configs");
    private static final Path CLIENT_FILE = DIR.resolve("client.json");

    private static String current = "default";

    public record ConfigInfo(String name, long lastModified) {
    }

    private ConfigManager() {
    }

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            Maro.LOGGER.error("Could not create config directory", e);
        }
        loadClient();
        if (exists(current)) load(current);
    }

    public static String getCurrent() {
        return current;
    }

    public static void saveAll() {
        saveClient();
        save(current);
    }

    // ---- client.json --------------------------------------------------------------------

    public static void saveClient() {
        JsonObject root = new JsonObject();
        JsonObject settings = new JsonObject();
        for (SettingSection section : ClientSettings.ALL) writeSettings(settings, section.getSettings());
        root.add("settings", settings);
        JsonArray friends = new JsonArray();
        FriendManager.list().forEach(friends::add);
        root.add("friends", friends);
        root.addProperty("config", current);
        write(CLIENT_FILE, root);
    }

    public static void loadClient() {
        JsonObject root = read(CLIENT_FILE);
        if (root == null) return;
        if (root.has("settings") && root.get("settings").isJsonObject()) {
            JsonObject settings = root.getAsJsonObject("settings");
            for (SettingSection section : ClientSettings.ALL) readSettings(settings, section.getSettings());
        }
        if (root.has("friends") && root.get("friends").isJsonArray()) {
            FriendManager.clear();
            for (JsonElement e : root.getAsJsonArray("friends")) FriendManager.add(e.getAsString());
        }
        if (root.has("config")) {
            String name = sanitize(root.get("config").getAsString());
            if (!name.isEmpty()) current = name;
        }
    }

    // ---- module configs -----------------------------------------------------------------

    public static boolean save(String name) {
        name = sanitize(name);
        if (name.isEmpty()) return false;
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.add("bind", m.getBind().toJson());
            JsonObject settings = new JsonObject();
            writeSettings(settings, m.getSettings());
            o.add("settings", settings);
            modules.add(m.getName(), o);
        }
        root.add("modules", modules);
        boolean ok = write(CONFIG_DIR.resolve(name + ".json"), root);
        if (ok) current = name;
        return ok;
    }

    public static boolean load(String name) {
        name = sanitize(name);
        JsonObject root = read(CONFIG_DIR.resolve(name + ".json"));
        if (root == null) return false;
        JsonObject modules = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module m : ModuleManager.all()) {
            if (!modules.has(m.getName()) || !modules.get(m.getName()).isJsonObject()) {
                m.setEnabled(false);
                continue;
            }
            JsonObject o = modules.getAsJsonObject(m.getName());
            try {
                if (o.has("settings") && o.get("settings").isJsonObject()) readSettings(o.getAsJsonObject("settings"), m.getSettings());
                if (o.has("bind")) m.getBind().fromJson(o.get("bind"));
                if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
            } catch (Exception e) {
                Maro.LOGGER.warn("Failed to load module {} from config {}", m.getName(), name, e);
            }
        }
        current = name;
        return true;
    }

    public static boolean delete(String name) {
        try {
            boolean ok = Files.deleteIfExists(CONFIG_DIR.resolve(sanitize(name) + ".json"));
            if (ok && sanitize(name).equalsIgnoreCase(current)) current = "default";
            return ok;
        } catch (IOException e) {
            Maro.LOGGER.error("Failed to delete config {}", name, e);
            return false;
        }
    }

    public static boolean exists(String name) {
        return Files.isRegularFile(CONFIG_DIR.resolve(sanitize(name) + ".json"));
    }

    public static List<ConfigInfo> list() {
        List<ConfigInfo> list = new ArrayList<>();
        if (!Files.isDirectory(CONFIG_DIR)) return list;
        try (Stream<Path> files = Files.list(CONFIG_DIR)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String file = p.getFileName().toString();
                long modified = 0;
                try {
                    modified = Files.getLastModifiedTime(p).toMillis();
                } catch (IOException ignored) {
                }
                list.add(new ConfigInfo(file.substring(0, file.length() - 5), modified));
            });
        } catch (IOException e) {
            Maro.LOGGER.error("Failed to list configs", e);
        }
        list.sort(Comparator.comparingLong(ConfigInfo::lastModified).reversed());
        return list;
    }

    public static String sanitize(String name) {
        if (name == null) return "";
        String s = name.trim().replaceAll("[^A-Za-z0-9_\\- ]", "").replace(' ', '_');
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    // ---- helpers ------------------------------------------------------------------------

    private static void writeSettings(JsonObject target, List<Setting<?>> settings) {
        for (Setting<?> s : settings) target.add(s.getName(), s.toJson());
    }

    private static void readSettings(JsonObject source, List<Setting<?>> settings) {
        for (Setting<?> s : settings) {
            if (!source.has(s.getName())) continue;
            try {
                s.fromJson(source.get(s.getName()));
            } catch (Exception e) {
                Maro.LOGGER.warn("Bad value for setting {}", s.getName(), e);
            }
        }
    }

    private static boolean write(Path path, JsonObject json) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Maro.LOGGER.error("Failed to write {}", path, e);
            return false;
        }
    }

    private static JsonObject read(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            Maro.LOGGER.error("Failed to read {}", path, e);
            return null;
        }
    }
}
