package dev.maro.anubis.setting;

import java.util.function.BooleanSupplier;

/** Each source setting is backed by a normal Maro widget and JSON value. */
public interface Setting<T> {
    T get();
    dev.maro.setting.Setting<?> view();
    void visibleWhen(BooleanSupplier visible);
    default Setting<T> privateValue() { return this; }
}
