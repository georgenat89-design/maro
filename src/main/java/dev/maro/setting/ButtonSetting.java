package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import java.util.function.BooleanSupplier;

/** A row with a button that runs an action. Holds no saved value. */
public class ButtonSetting extends Setting<Runnable> {
    private final String label;

    public ButtonSetting(String name, String description, String label, Runnable action) {
        super(name, description, action);
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public void press() {
        value.run();
    }

    public ButtonSetting visible(BooleanSupplier visibility) {
        setVisibility(visibility);
        return this;
    }

    @Override
    public JsonElement toJson() {
        return JsonNull.INSTANCE;
    }

    @Override
    public void fromJson(JsonElement json) {
    }
}
