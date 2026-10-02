package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.maro.util.ColorUtil;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** ARGB colour. Hue/saturation are cached so they survive black/white/grey values while editing. */
public class ColorSetting extends Setting<Integer> {
    private final boolean allowAlpha;
    private float hue, saturation, brightness;

    public ColorSetting(String name, String description, int defaultValue, boolean allowAlpha) {
        super(name, description, allowAlpha ? defaultValue : (defaultValue | 0xFF000000));
        this.allowAlpha = allowAlpha;
        syncHsv();
    }

    public ColorSetting(String name, String description, int defaultValue) {
        this(name, description, defaultValue, false);
    }

    public boolean allowsAlpha() {
        return allowAlpha;
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getBrightness() {
        return brightness;
    }

    public int getAlpha() {
        return ColorUtil.alpha(value);
    }

    /** Sets the colour from HSV components without losing hue when s/v hit zero. */
    public void setHsv(float h, float s, float v, int alpha) {
        hue = h;
        saturation = s;
        brightness = v;
        super.set(ColorUtil.withAlpha(ColorUtil.hsv(h, s, v), allowAlpha ? alpha : 255));
    }

    @Override
    public void set(Integer value) {
        super.set(value);
        syncHsv();
    }

    @Override
    protected Integer validate(Integer v) {
        if (v == null) return null;
        return allowAlpha ? v : (v | 0xFF000000);
    }

    private void syncHsv() {
        float[] hsv = ColorUtil.toHsv(value);
        if (hsv[2] > 0 && hsv[1] > 0) hue = hsv[0];
        if (hsv[2] > 0) saturation = hsv[1];
        brightness = hsv[2];
    }

    public ColorSetting visible(BooleanSupplier visibility) {
        setVisibility(visibility);
        return this;
    }

    public ColorSetting onChange(Consumer<Integer> listener) {
        setListener(listener);
        return this;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(ColorUtil.toHex(value, true));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (!json.isJsonPrimitive()) return;
        try {
            String s = json.getAsString().replace("#", "");
            set((int) Long.parseLong(s.length() == 6 ? "FF" + s : s, 16));
        } catch (NumberFormatException ignored) {
        }
    }
}
