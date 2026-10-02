package dev.maro.config;

import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.KeybindSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Client-wide options shown on the Settings and Theme pages. Saved to {@code maro/client.json}. */
public final class ClientSettings {
    private ClientSettings() {
    }

    // ---- Settings page -----------------------------------------------------------------

    public static final SettingSection INTERFACE = new SettingSection("Interface");
    public static final KeybindSetting guiBind = INTERFACE.add(new KeybindSetting("Menu Keybind", "Key that opens this menu", GLFW.GLFW_KEY_RIGHT_SHIFT));
    public static final BooleanSetting customFont = INTERFACE.add(new BooleanSetting("Custom Font", "Use the Inter font instead of the Minecraft font", true));
    public static final BooleanSetting capsText = INTERFACE.add(new BooleanSetting("Caps Text", "Small bold all-caps labels (off = normal sentence case)", true));
    public static final NumberSetting animSpeed = INTERFACE.add(new NumberSetting("Animation Speed", "How fast menu animations play", 1.0, 0.3, 3.0, 0.1).suffix("x"));
    public static final BooleanSetting backgroundBlur = INTERFACE.add(new BooleanSetting("Background Blur", "Frosted-glass blur behind the menu (strength = vanilla Menu Blur option)", true));
    public static final BooleanSetting backgroundDim = INTERFACE.add(new BooleanSetting("Background Dim", "Darken the game behind the menu", true));
    public static final NumberSetting dimAmount = INTERFACE.add(new NumberSetting("Dim Strength", "How dark the game behind the menu gets", 55, 0, 100, 1).suffix("%")
            .visible(backgroundDim::get));
    public static final BooleanSetting tooltips = INTERFACE.add(new BooleanSetting("Tooltips", "Show descriptions when hovering info icons", true));
    public static final BooleanSetting typeToSearch = INTERFACE.add(new BooleanSetting("Type To Search", "Start typing anywhere to search modules", true));

    public static final SettingSection BEHAVIOUR = new SettingSection("Behaviour");
    public static final BooleanSetting uiSounds = BEHAVIOUR.add(new BooleanSetting("UI Sounds", "Play a click when toggling things", true));
    public static final BooleanSetting notifications = BEHAVIOUR.add(new BooleanSetting("Notifications", "Show toast notifications", true));
    public static final BooleanSetting toggleNotifications = BEHAVIOUR.add(new BooleanSetting("Toggle Alerts", "Notify when a module is toggled with its keybind", true)
            .visible(notifications::get));
    public static final ModeSetting notificationPos = BEHAVIOUR.add(new ModeSetting("Toast Position", "Where notifications appear", "Bottom", "Top", "Bottom")
            .visible(notifications::get));
    public static final BooleanSetting pauseGame = BEHAVIOUR.add(new BooleanSetting("Pause Game", "Pause singleplayer while the menu is open", false));
    public static final BooleanSetting autoUpdate = BEHAVIOUR.add(new BooleanSetting("Auto Update", "Download new builds in the background and install them when the game closes", true));
    public static final BooleanSetting autoSave = BEHAVIOUR.add(new BooleanSetting("Auto Save", "Save the active config whenever the menu closes", true));

    // ---- Theme page --------------------------------------------------------------------

    public static final SettingSection ACCENT = new SettingSection("Accent");
    public static final ColorSetting accent = ACCENT.add(new ColorSetting("Accent Color", "Main highlight colour", 0xFF8B5CF6));
    public static final BooleanSetting gradient = ACCENT.add(new BooleanSetting("Gradient", "Blend the accent into a second hue", true));
    public static final NumberSetting gradientShift = ACCENT.add(new NumberSetting("Gradient Shift", "Hue distance of the second colour", 0.1, 0.02, 0.4, 0.01)
            .visible(gradient::get));
    public static final BooleanSetting rainbow = ACCENT.add(new BooleanSetting("Rainbow", "Cycle the accent through every hue", false));
    public static final NumberSetting rainbowSpeed = ACCENT.add(new NumberSetting("Rainbow Speed", "Seconds per full cycle", 8, 2, 30, 1).suffix("s")
            .visible(rainbow::get));

    public static final SettingSection WINDOW = new SettingSection("Window");
    public static final ColorSetting background = WINDOW.add(new ColorSetting("Background Color", "Colour of the menu window", 0xFF000000));
    public static final NumberSetting opacity = WINDOW.add(new NumberSetting("Background Opacity", "How see-through the menu window is", 94, 0, 100, 1).suffix("%"));
    public static final NumberSetting radius = WINDOW.add(new NumberSetting("Corner Radius", "Roundness of panels and cards", 6, 0, 10, 0.5));
    public static final BooleanSetting glow = WINDOW.add(new BooleanSetting("Accent Glow", "Soft glow around active elements", true));
    public static final BooleanSetting shadow = WINDOW.add(new BooleanSetting("Window Shadow", "Drop shadow behind the window", true));

    public static final List<SettingSection> GENERAL_PAGE = List.of(INTERFACE, BEHAVIOUR);
    public static final List<SettingSection> THEME_PAGE = List.of(ACCENT, WINDOW);
    public static final List<SettingSection> ALL = List.of(INTERFACE, BEHAVIOUR, ACCENT, WINDOW);

    public static float animationSpeed() {
        return animSpeed.getFloat();
    }
}
