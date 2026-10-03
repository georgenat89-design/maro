package dev.maro.runtime.settings;

import com.google.gson.*;
import dev.maro.runtime.utils.misc.Keybind;
import dev.maro.runtime.utils.render.color.SettingColor;
import dev.maro.setting.*;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.*;

/** Each imported setting gets a live Maro widget and Maro JSON serialization. */
public final class SettingAdapters {
    private static final Map<Setting<?>, dev.maro.setting.Setting<?>> VIEWS = new WeakHashMap<>();
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static dev.maro.setting.Setting<?> adapt(Setting<?> source) {
        var existing = VIEWS.get(source);
        if (existing != null) return existing;
        String name = source.name.replace('-', ' ');
        Object initial = source.get();
        dev.maro.setting.Setting<?> view;
        if (initial instanceof Boolean b) {
            Setting<Boolean> setting = (Setting<Boolean>) source;
            var widget = new BooleanSetting(name, source.description, b).visible(source.visible).onChange(setting::set);
            setting.observe(widget::set); view = widget;
        } else if (initial instanceof Number n) {
            Setting setting = source;
            var widget = new NumberSetting(name, source.description, n.doubleValue(), source.min, source.max, source.step)
                .visible(source.visible).onChange(v -> setting.set(initial instanceof Integer ? (Object)v.intValue() : v));
            setting.observe(v -> widget.set(((Number)v).doubleValue())); view = widget;
        } else if (initial instanceof Enum<?> e) {
            Setting setting = source;
            Enum<?>[] values = e.getDeclaringClass().getEnumConstants();
            var widget = new ModeSetting(name, source.description, e.name(), Arrays.stream(values).map(Enum::name).toArray(String[]::new))
                .visible(source.visible).onChange(v -> setting.set(Enum.valueOf((Class)e.getDeclaringClass(), v)));
            setting.observe(v -> widget.set(((Enum<?>)v).name())); view = widget;
        } else if (initial instanceof String s && source.choices != null) {
            Setting<String> setting = (Setting<String>)source;
            var choices = new LinkedHashSet<>(List.of(source.choices.get())); choices.add(s);
            var widget = new ModeSetting(name, source.description, s, choices.toArray(String[]::new)).visible(source.visible).onChange(setting::set);
            setting.observe(widget::set); view = widget;
        } else if (initial instanceof SettingColor c) {
            Setting<SettingColor> setting = (Setting<SettingColor>)source;
            var widget = new dev.maro.setting.ColorSetting(name, source.description, c.getPacked(), true).visible(source.visible)
                .onChange(v -> setting.set(new SettingColor(new dev.maro.runtime.utils.render.color.Color(v))));
            setting.observe(v -> widget.set(v.getPacked())); view = widget;
        } else if (initial instanceof Keybind k) {
            Setting<Keybind> setting = (Setting<Keybind>)source;
            var widget = new dev.maro.setting.KeybindSetting(name, source.description, k.code()) {
                @Override public void set(Integer code) { super.set(code); setting.set(Keybind.fromCode(get())); }
            }.visible(source.visible);
            setting.observe(v -> widget.set(v.code())); view = widget;
        } else {
            view = new ValueSetting(source);
        }
        VIEWS.put(source, view);
        return view;
    }

    /** Text and lists use an editor; item lists store registry IDs, never class names. */
    public static final class ValueSetting extends dev.maro.setting.Setting<String> {
        private final Setting<?> source;
        ValueSetting(Setting<?> source) { super(source.name.replace('-', ' '), source.description, ""); this.source=source; setVisibility(source.visible); }
        public String editText() {
            Object value=source.get();
            if(value instanceof List<?>) return toJson().toString();
            return text(value);
        }
        private static String text(Object value){return value instanceof Item item?Registries.ITEM.getId(item).toString():String.valueOf(value);}
        @SuppressWarnings({"unchecked", "rawtypes"}) public void apply(String text){
            Object original=source.get(); Object next=text;
            if(original instanceof Item) next=item(text.trim());
            else if(original instanceof List<?> list){
                boolean items=source.defaultValue instanceof List<?> defaults && !defaults.isEmpty() && defaults.getFirst() instanceof Item;
                if (text.trim().startsWith("[")) {
                    next = readList(JsonParser.parseString(text).getAsJsonArray(), items);
                } else {
                    next=Arrays.stream(text.split(";|\\r?\\n")).map(String::trim).filter(s->!s.isEmpty()).map(s->items?item(s):(Object)s).toList();
                }
            }
            ((Setting)source).set(next);
        }
        private static Item item(String text){Identifier id=Identifier.tryParse(text);if(id==null || !Registries.ITEM.containsId(id))throw new IllegalArgumentException("Unknown item: "+text);return Registries.ITEM.get(id);}
        private static List<Object> readList(JsonArray json, boolean items) {
            List<Object> result = new ArrayList<>();
            json.forEach(v -> result.add(items ? item(v.getAsString()) : v.getAsString()));
            return result;
        }
        @Override public JsonElement toJson(){Object value=source.get();if(value instanceof List<?> list){JsonArray result=new JsonArray();list.forEach(v->result.add(text(v)));return result;}return new JsonPrimitive(text(value));}
        @Override public void fromJson(JsonElement json){if(json.isJsonArray())apply(json.toString());else if(json.isJsonPrimitive())apply(json.getAsString());}
    }
}
