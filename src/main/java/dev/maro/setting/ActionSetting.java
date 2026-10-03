package dev.maro.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A module action, exposed alongside its settings without storing UI state. */
public final class ActionSetting extends Setting<String> {
    private final Runnable action;
    public ActionSetting(String name, String description, Runnable action) {
        super(name, description, "Open");
        this.action = action;
    }
    public void run() { action.run(); }
    @Override public JsonElement toJson() { return JsonNull.INSTANCE; }
    @Override public void fromJson(JsonElement json) { }
}
