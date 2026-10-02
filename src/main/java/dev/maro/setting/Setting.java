package dev.maro.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visibility = () -> true;
    private Consumer<T> listener;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        T v = validate(value);
        if (v == null || v.equals(this.value)) return;
        this.value = v;
        if (listener != null) listener.accept(v);
    }

    public T getDefault() {
        return defaultValue;
    }

    public void reset() {
        set(defaultValue);
    }

    protected T validate(T value) {
        return value;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    protected void setVisibility(BooleanSupplier visibility) {
        this.visibility = visibility;
    }

    protected void setListener(Consumer<T> listener) {
        this.listener = listener;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement json);
}
