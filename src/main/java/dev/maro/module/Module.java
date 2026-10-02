package dev.maro.module;

import dev.maro.setting.KeybindSetting;
import dev.maro.setting.Setting;
import dev.maro.util.KeyUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for every module. Extend it, add settings with {@link #add(Setting)} in the
 * constructor and register the instance in {@link ModuleManager#init()}.
 */
public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeybindSetting bind = new KeybindSetting("Keybind", "Press this key in-game to toggle the module", KeyUtil.NONE);
    private boolean enabled;
    private boolean experimental;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /** Shows a red warning marker next to the module name in the GUI. */
    protected void setExperimental(boolean experimental) {
        this.experimental = experimental;
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public KeybindSetting getBind() {
        return bind;
    }

    public boolean isExperimental() {
        return experimental;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    /** True when there is a world and player - most module logic needs this. */
    protected static boolean inGame() {
        return mc.player != null && mc.world != null;
    }

    // ---- hooks ---------------------------------------------------------------------------

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called at the end of every client tick while enabled. */
    public void onTick() {
    }

    /** Called every frame while enabled, after the vanilla HUD. */
    public void onRender2D(DrawContext context, float tickDelta) {
    }
}
