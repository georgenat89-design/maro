package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.maro.util.KeyUtil;

import java.util.function.BooleanSupplier;

/** Stores a bind code, see {@link KeyUtil}. */
public class KeybindSetting extends Setting<Integer> {
    public KeybindSetting(String name, String description, int defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean isBound() {
        return value != KeyUtil.NONE;
    }

    public boolean matches(int code) {
        return isBound() && value == code;
    }

    public String getKeyName() {
        return KeyUtil.name(value);
    }

    public KeybindSetting visible(BooleanSupplier visibility) {
        setVisibility(visibility);
        return this;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) set(json.getAsInt());
    }
}
