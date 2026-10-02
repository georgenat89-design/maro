package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = Collections.unmodifiableList(Arrays.asList(modes));
        if (!this.modes.contains(defaultValue)) throw new IllegalArgumentException("Default mode '" + defaultValue + "' is not one of " + this.modes);
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean is(String mode) {
        return value.equalsIgnoreCase(mode);
    }

    public int index() {
        return Math.max(0, modes.indexOf(value));
    }

    public void cycle(int direction) {
        int i = Math.floorMod(index() + direction, modes.size());
        set(modes.get(i));
    }

    @Override
    protected String validate(String v) {
        for (String m : modes) if (m.equalsIgnoreCase(v)) return m;
        return null;
    }

    public ModeSetting visible(BooleanSupplier visibility) {
        setVisibility(visibility);
        return this;
    }

    public ModeSetting onChange(Consumer<String> listener) {
        setListener(listener);
        return this;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) set(json.getAsString());
    }
}
