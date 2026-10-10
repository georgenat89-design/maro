package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A short line of text the user types in, up to {@link #getMaxLength()} characters. */
public class TextSetting extends Setting<String> {
    private final int maxLength;
    private final String placeholder;

    public TextSetting(String name, String description, String defaultValue, int maxLength, String placeholder) {
        super(name, description, defaultValue);
        this.maxLength = maxLength;
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    /** Whether a typed character may go in: no control characters, and no § formatting codes. */
    public static boolean allowed(int c) {
        return c >= 32 && c != 127 && c != '§';
    }

    @Override
    protected String validate(String v) {
        if (v == null) return null;
        StringBuilder ok = new StringBuilder();
        for (int i = 0; i < v.length() && ok.length() < maxLength; i++) {
            char c = v.charAt(i);
            if (allowed(c)) ok.append(c);
        }
        return ok.toString();
    }

    public TextSetting visible(BooleanSupplier visibility) {
        setVisibility(visibility);
        return this;
    }

    public TextSetting onChange(Consumer<String> listener) {
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
