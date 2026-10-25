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

    /** v4: blue theme; v5: translucent menus, normal-case text and the new canvas layout. */
    private static final int CLIENT_VERSION = 5;
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
        else for (Module m : ModuleManager.all()) if (m.enabledByDefault()) m.setEnabled(true);
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
        root.addProperty("spatialMenu", true);
        root.addProperty("lookRevision", 8);
        root.addProperty("version", CLIENT_VERSION);
        write(CLIENT_FILE, root);
    }

    public static void loadClient() {
        JsonObject root = read(CLIENT_FILE);
        if (root == null) return;
        if (root.has("settings") && root.get("settings").isJsonObject()) {
            JsonObject settings = root.getAsJsonObject("settings");
            if(!settings.has("Frosted Glass")&&settings.has("Background Blur"))settings.add("Frosted Glass",settings.get("Background Blur"));
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            for (SettingSection section : ClientSettings.ALL) {
                // files from before the redesign keep their behaviour settings but get the new look
                if (version < 4 && ClientSettings.THEME_PAGE.contains(section)) continue;
                readSettings(settings, section.getSettings());
            }
            if (version < 5) {
                // Apply the new menu presentation once, keeping binds, behaviour and accent colours.
                ClientSettings.capsText.reset();
                ClientSettings.backgroundBlur.reset();
                ClientSettings.backgroundDim.reset();
                ClientSettings.opacity.reset();
                ClientSettings.radius.reset();
                ClientSettings.glow.reset();
                ClientSettings.shadow.reset();
                ClientSettings.contourLines.reset();
            }
            // The old Spatial menu style is the floating window now.
            JsonElement style = settings.get("Menu Style");
            if (style != null && style.isJsonPrimitive() && style.getAsString().equals("Spatial")) ClientSettings.spatialMenu.set(true);
        }
        if (root.has("friends") && root.get("friends").isJsonArray()) {
            FriendManager.clear();
            for (JsonElement e : root.getAsJsonArray("friends")) FriendManager.add(e.getAsString());
        }
        if (root.has("config")) {
            String name = sanitize(root.get("config").getAsString());
            if (!name.isEmpty()) current = name;
        }
        // Adopt the floating window once, retaining an existing Screen GUI setup.
        if (!root.has("spatialMenu") && !ClientSettings.screenGui.get()) ClientSettings.spatialMenu.set(true);
        // Once: the plainer look, sentence case in Rubik without glows, two-tone gradients or
        // contour lines (each can be turned back on under Settings and Theme).
        if (!root.has("lookRevision") || root.get("lookRevision").getAsInt() < 2) {
            ClientSettings.capsText.set(false);
            ClientSettings.gradient.set(false);
            ClientSettings.glow.set(false);
            ClientSettings.contourLines.set(false);
            ClientSettings.customFont.set(true);
            ClientSettings.fontFace.set("Rubik");
        }
        if (!root.has("lookRevision") || root.get("lookRevision").getAsInt() < 3) {
            ClientSettings.capsText.set(true);
            ClientSettings.customFont.set(true);
            ClientSettings.fontFace.set("Rubik");
        }
        // Compact the previous default once. Keep deliberate custom size choices.
        if ((!root.has("lookRevision") || root.get("lookRevision").getAsInt() < 4)
                && ClientSettings.guiSize.getFloat() == 85) ClientSettings.guiSize.set(75d);
        // Adopt the requested open, bold typeface once; later font choices remain saved.
        if (!root.has("lookRevision") || root.get("lookRevision").getAsInt() < 5) {
            ClientSettings.fontFace.set("Sora");
            ClientSettings.customFont.set(true);
            ClientSettings.capsText.set(true);
        }
        // The requested reference design is adopted once. Later personal theme choices persist.
        if (!root.has("lookRevision") || root.get("lookRevision").getAsInt() < 6) {
            if (ClientSettings.guiSize.getInt() == 75) ClientSettings.guiSize.set(97d);
            ClientSettings.fontFace.set("Inter");
            ClientSettings.customFont.set(true);
            ClientSettings.capsText.set(false);
            ClientSettings.menuTextSize.reset();
            ClientSettings.accent.reset();
            ClientSettings.background.reset();
            ClientSettings.opacity.set(100d);
            ClientSettings.radius.set(10d);
            ClientSettings.gradient.set(false);
            ClientSettings.glow.set(false);
            ClientSettings.contourLines.set(false);
        }
        if (!root.has("lookRevision") || root.get("lookRevision").getAsInt() < 7) {
            ClientSettings.fontFace.set("Barlow Condensed");
            ClientSettings.customFont.set(true);
            ClientSettings.unifiedTypography.set(true);
            ClientSettings.capsText.set(true);
        }
        // Raise only the prior default once; keep deliberate text-size choices.
        if ((!root.has("lookRevision") || root.get("lookRevision").getAsInt() < 8)
                && ClientSettings.menuTextSize.getInt() == 100) ClientSettings.menuTextSize.reset();

    }

    // ---- module configs -----------------------------------------------------------------

    public static boolean save(String name) {
        name = sanitize(name);
        if (name.isEmpty()) return false;
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.persistEnabled() && dev.maro.render.capture.StreamproofScenePolicy.configuredEnabled(m, m.isEnabled()));
            o.add("bind", m.getBind().toJson());
            JsonObject settings = new JsonObject();
            writeSettings(settings, m.getSettings());
            o.add("settings", settings);
            o.add("extra", m.saveExtra());
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
        try { dev.maro.textures.GameplayTextureMigration.apply(modules); }
        catch(RuntimeException e) { Maro.LOGGER.warn("Could not migrate legacy texture modules in config {}",name,e); }
        try { dev.maro.render.pickaxe.PickaxeMigration.apply(modules); }
        catch(RuntimeException e) { Maro.LOGGER.warn("Could not migrate legacy pickaxe settings in config {}",name,e); }
        for (Module m : ModuleManager.all()) {
            if (!modules.has(m.getName()) || !modules.get(m.getName()).isJsonObject()) {
                // Never saved in this config: as it comes (on, for one that starts on).
                m.setEnabled(m.enabledByDefault());
                continue;
            }
            JsonObject o = modules.getAsJsonObject(m.getName());
            try {
                if (o.has("settings") && o.get("settings").isJsonObject()) readSettings(o.getAsJsonObject("settings"), m.getSettings());
                if (o.has("bind")) m.getBind().fromJson(o.get("bind"));
                if (o.has("extra") && o.get("extra").isJsonObject()) m.loadExtra(o.getAsJsonObject("extra"));
                else if (m instanceof dev.maro.module.impl.visuals.ViewModel) m.loadExtra(new JsonObject());
                if (o.has("enabled")) m.setEnabled(m.persistEnabled() && o.get("enabled").getAsBoolean());
            } catch (Exception e) {
                Maro.LOGGER.warn("Failed to load module {} from config {}", m.getName(), name, e);
            }
        }
        dev.maro.module.impl.visuals.NoRender nr = dev.maro.module.impl.visuals.NoRender.get();
        if (nr != null) nr.carryOver();
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
