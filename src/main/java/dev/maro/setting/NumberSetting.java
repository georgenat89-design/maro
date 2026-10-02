package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class NumberSetting extends Setting<Double> {
    private final double min, max, step;
    private String suffix = "";

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    /** Fraction of the way between min and max, 0..1. */
    public double getPercent() {
        return max == min ? 0 : (value - min) / (max - min);
    }

    public void setPercent(double pct) {
        set(min + (max - min) * Math.max(0, Math.min(1, pct)));
    }

    public NumberSetting suffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public String format() {
        int decimals = Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString() + suffix;
    }

    @Override
    protected Double validate(Double v) {
        if (v == null || v.isNaN()) return null;
        double clamped = Math.max(min, Math.min(max, v));
        if (step > 0) clamped = min + Math.round((clamped - min) / step) * step;
        clamped = Math.max(min, Math.min(max, clamped));
        // trim floating point noise (0.30000000000000004 -> 0.3)
        return BigDecimal.valueOf(clamped).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    public NumberSetting visible(BooleanSupplier visibility) {
        setVisibility(visibility);
        return this;
    }

    public NumberSetting onChange(Consumer<Double> listener) {
        setListener(listener);
        return this;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json.isJsonPrimitive()) set(json.getAsDouble());
    }
}
