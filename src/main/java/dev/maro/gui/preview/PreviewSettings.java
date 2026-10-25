package dev.maro.gui.preview;

import dev.maro.module.Module;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.setting.Setting;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Render-thread lookups cache setting references, never values, so edits stay immediate. */
public final class PreviewSettings {
    private static final Map<Module, Index> INDICES = new IdentityHashMap<>();
    private PreviewSettings() { }

    private static final class Index {
        final Map<String, Setting<?>> normalized = new HashMap<>();
        final Map<String, Setting<?>> requests = new HashMap<>();
        Index(Module module) {
            for (Setting<?> setting : module.getSettings()) normalized.put(key(setting.getName()), setting);
        }
        Setting<?> get(String name) {
            if (requests.containsKey(name)) return requests.get(name);
            Setting<?> result = normalized.get(key(name));
            requests.put(name, result);
            return result;
        }
    }
    private static String key(String value) { return value.replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT); }
    public static Setting<?> setting(Module module, String name) {
        return INDICES.computeIfAbsent(module, Index::new).get(name);
    }
    private static Object value(Setting<?> setting) {
        // Imported item/string adapters expose their source through JSON; ordinary widgets
        // expose live typed values and need no list building or JSON allocation while drawing.
        if (setting instanceof SettingAdapters.ValueSetting) {
            var json = setting.toJson();
            return json != null && json.isJsonPrimitive() ? json.getAsString() : null;
        }
        return setting == null ? null : setting.get();
    }
    public static double number(Module module, String name, double fallback) {
        Object value = value(setting(module, name));
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String string) try { return Double.parseDouble(string); } catch (NumberFormatException ignored) { }
        return fallback;
    }
    public static String mode(Module module, String name, String fallback) {
        Object value = value(setting(module, name));
        return value == null ? fallback : value.toString();
    }
    public static boolean flag(Module module, String name, boolean fallback) {
        Object value = value(setting(module, name));
        if (value instanceof Boolean flag) return flag;
        return value instanceof String string ? Boolean.parseBoolean(string) : fallback;
    }
    public static int color(Module module, String name, int fallback) {
        Setting<?> setting = setting(module, name);
        return setting instanceof dev.maro.setting.ColorSetting color ? color.get() : fallback;
    }
}
