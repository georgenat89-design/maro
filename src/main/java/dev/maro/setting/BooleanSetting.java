package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public void toggle() {
        set(!value);
    }

    public BooleanSetting visible(BooleanSupplier visibility) {
        setVisibility(visibility);
        return this;
    }

    public BooleanSetting onChange(Consumer<Boolean> listener) {
        setListener(listener);
        return this;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) set(json.getAsBoolean());
    }
}
