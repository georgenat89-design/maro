package dev.maro.module.impl.player;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
/** Meteor's block break cooldown and optional protection against accidental instant breaks. */
public final class BreakDelay extends Module {
    public static volatile BreakDelay active;
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", "Ticks between broken blocks", 0, 0, 5, 1));
    private final BooleanSetting noInsta = add(new BooleanSetting("No Insta Break", "Prevent accidentally breaking an instant-mine block on the first click", false));
    private boolean freshClick;
    public BreakDelay() { super("Break Delay", "Change the delay between broken blocks; anti-cheat off only", Category.ANTI_CHEAT_OFF); }
    @Override public boolean persistEnabled() { return false; }
    @Override protected void onEnable() { freshClick = false; active = this; }
    @Override protected void onDisable() { if (active == this) active = null; freshClick = false; }
    public static int cooldown(int vanilla) {
        var m = active;
        if (m == null) return vanilla;
        if (m.freshClick) { m.freshClick = false; return 5; }
        return m.cooldown.getInt();
    }
    public static boolean preventInstant() { return active != null && active.noInsta.get(); }
    public static void clicked(int button, int action) {
        if (button == 0 && action == 1 && active != null && active.noInsta.get()) active.freshClick = true;
    }
}
