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
    public static final ModeSetting menuStyle = INTERFACE.add(new ModeSetting("Menu Style",
            "Choose category dropdowns or the card-based Panel", "Panels", "Panels", "Panel") {
        @Override protected String validate(String value) {
            return super.validate("Window".equalsIgnoreCase(value)||"Laptop".equalsIgnoreCase(value)||"iPad".equalsIgnoreCase(value) ? "Panel" : value);
        }
    });
    public static final BooleanSetting backgroundBlur = INTERFACE.add(new BooleanSetting("Frosted Glass", "Blur the world behind either menu layout; text and buttons stay sharp", false));
    public static final BooleanSetting spatialMenu = INTERFACE.add(new BooleanSetting("Floating Window",
            "The menu floats on a glass window in the world: look around with the mouse, click with the crosshair,"
                    + " walk about, hold the bar under it to move it, the red dot closes it", false));
    public static final BooleanSetting spatialWalk = INTERFACE.add(new BooleanSetting("Walk With Menu Open",
            "Move, jump, sneak and sprint while the floating window is open (not while typing in a box)", true).visible(spatialMenu::get));
    public static final BooleanSetting spatialRemember = INTERFACE.add(new BooleanSetting("Remember Spot",
            "The floating window opens where you left it when that is nearby and in front of you", true).visible(spatialMenu::get));
    public static final NumberSetting guiSize = INTERFACE.add(new NumberSetting("GUI Size", "Size of the menu", 75, 50, 100, 1).suffix("%"));
    public static final BooleanSetting guiFullscreen = INTERFACE.add(new BooleanSetting("Fullscreen View", "Expand the Click GUI across most of your screen", false));
    public static final NumberSetting menuTextSize = INTERFACE.add(new NumberSetting("Menu Text Size", "Resize text, module rows and category navigation in both layouts", 90, 75, 110, 5).suffix("%"));
    public static final BooleanSetting screenGui = INTERFACE.add(new BooleanSetting("Screen GUI", "Place the menu in the world. Walk and look normally; aim your crosshair and click to configure it", false));
    public static final NumberSetting screenDistance = INTERFACE.add(new NumberSetting("Screen Distance", "How far in front of you the menu appears when opened", 3, 1.5, 6, .25).suffix("m").visible(screenGui::get));
    public static final BooleanSetting customFont = INTERFACE.add(new BooleanSetting("Custom Font", "Use a smooth font instead of the Minecraft font", true));
    public static final ModeSetting fontFace = INTERFACE.add(new ModeSetting("Font", "Sora uses a shorter, wider-looking bold style; Rubik is rounded and Inter is tighter",
            "Sora", "Sora", "Rubik", "Inter").visible(customFont::get));
    public static final BooleanSetting capsText = INTERFACE.add(new BooleanSetting("Caps Text", "Bold uppercase text across menus and HUDs", true));
    public static final NumberSetting animSpeed = INTERFACE.add(new NumberSetting("Animation Speed", "How fast menu animations play", 1.0, 0.3, 3.0, 0.1).suffix("x"));
    public static final BooleanSetting backgroundDim = INTERFACE.add(new BooleanSetting("Background Dim", "Darken the game behind the menu", false));
    public static final NumberSetting dimAmount = INTERFACE.add(new NumberSetting("Dim Strength", "How dark the game behind the menu gets", 55, 0, 100, 1).suffix("%")
            .visible(backgroundDim::get));
    public static final BooleanSetting tooltips = INTERFACE.add(new BooleanSetting("Tooltips", "Show descriptions when hovering info icons", true));
    public static final BooleanSetting typeToSearch = INTERFACE.add(new BooleanSetting("Type To Search", "Start typing anywhere to search modules", true));

    public static final SettingSection BEHAVIOUR = new SettingSection("Behaviour");
    public static final BooleanSetting uiSounds = BEHAVIOUR.add(new BooleanSetting("UI Sounds", "Play a click when toggling things", true));
    public static final ModeSetting uiSound = BEHAVIOUR.add(new ModeSetting("Click Sound", "The sound a click makes", "Click", "Click", "Soft", "Tick", "Pop")
            .visible(uiSounds::get));
    public static final NumberSetting uiVolume = BEHAVIOUR.add(new NumberSetting("Sound Volume", "How loud menu clicks are (the game's Master volume applies too)", 70, 5, 100, 5)
            .suffix("%").visible(uiSounds::get));
    public static final BooleanSetting notifications = BEHAVIOUR.add(new BooleanSetting("Notifications", "Show toast notifications", true));
    public static final BooleanSetting toggleNotifications = BEHAVIOUR.add(new BooleanSetting("Toggle Alerts", "Notify when a module is toggled with its keybind", true)
            .visible(notifications::get));
    public static final ModeSetting notificationPos = BEHAVIOUR.add(new ModeSetting("Toast Position", "Where notifications appear", "Bottom", "Top", "Bottom")
            .visible(notifications::get));
    public static final BooleanSetting autoSave = BEHAVIOUR.add(new BooleanSetting("Auto Save", "Save the active config whenever the menu closes", true));

    // ---- Theme page --------------------------------------------------------------------

    public static final SettingSection ACCENT = new SettingSection("Accent");
    public static final ColorSetting accent = ACCENT.add(new ColorSetting("Accent Color", "Main highlight colour", 0xFF3391FC));
    public static final BooleanSetting gradient = ACCENT.add(new BooleanSetting("Gradient", "Blend the accent into a second hue", false));
    public static final NumberSetting gradientShift = ACCENT.add(new NumberSetting("Gradient Shift",
            "Hue distance of the second colour: below 0 it turns one way round the wheel (Maro's blue towards cyan), above 0 the other",
            -0.07, -0.4, 0.4, 0.01)
            .visible(gradient::get));
    public static final BooleanSetting rainbow = ACCENT.add(new BooleanSetting("Rainbow", "Cycle the accent through every hue", false));
    public static final NumberSetting rainbowSpeed = ACCENT.add(new NumberSetting("Rainbow Speed", "Seconds per full cycle", 8, 2, 30, 1).suffix("s")
            .visible(rainbow::get));

    public static final SettingSection WINDOW = new SettingSection("Window");
    public static final ColorSetting background = WINDOW.add(new ColorSetting("Background Color", "Colour of the menu window", 0xFF04070D));
    public static final NumberSetting opacity = WINDOW.add(new NumberSetting("Background Opacity", "How see-through the menu window is", 94, 0, 100, 1).suffix("%"));
    public static final NumberSetting radius = WINDOW.add(new NumberSetting("Corner Radius", "Roundness of panels and cards", 6, 0, 10, 0.5));
    public static final BooleanSetting glow = WINDOW.add(new BooleanSetting("Accent Glow", "Soft glow around active elements", false));
    public static final BooleanSetting shadow = WINDOW.add(new BooleanSetting("Window Shadow", "Drop shadow behind the window", true));
    public static final BooleanSetting contourLines = WINDOW.add(new BooleanSetting("Contour Lines", "Faint map lines behind the menu, like the logo's", false));

    public static final SettingSection SCENE = new SettingSection("Main Menu Scene");
    public static final ModeSetting sceneBackdrop = SCENE.add(new ModeSetting("Scene Background", "Landscape or a floating island against a galaxy", "Landscape", "Landscape", "Galaxy"));
    public static final ModeSetting sceneTime = SCENE.add(new ModeSetting("Scene Time", "Lighting in the main menu landscape", "Sunset", "Dawn", "Day", "Sunset", "Night", "Auto"));
    public static final ModeSetting sceneTrees = SCENE.add(new ModeSetting("Scene Trees", "Trees growing beside the menu's river", "Mixed", "Oak", "Birch", "Cherry", "Spruce", "Mixed"));
    public static final ModeSetting sceneCamera = SCENE.add(new ModeSetting("Scene Camera", "Orbit the landscape, drift gently, or hold the current view", "Orbit", "Orbit", "Drift", "Paused"));
    public static final NumberSetting orbitSpeed = SCENE.add(new NumberSetting("Orbit Speed", "How quickly the menu camera circles the scene", 100, 10, 300, 10).suffix("%"));
    public static final NumberSetting sceneZoom = SCENE.add(new NumberSetting("Scene Zoom", "Move closer to the menu landscape or floating island", 100, 60, 150, 5).suffix("%"));
    public static final NumberSetting orbitAngle = SCENE.add(new NumberSetting("Orbit Angle", "View the scene from a low or high camera angle", 45, 20, 75, 5).suffix("°"));
    public static final ModeSetting orbitDirection = SCENE.add(new ModeSetting("Orbit Direction", "Which way the menu camera circles", "Clockwise", "Clockwise", "Counterclockwise"));
    public static final BooleanSetting transparentMenu = SCENE.add(new BooleanSetting("Transparent Menu", "See the scene through main-menu panels and navigation buttons", false));
    public static final NumberSetting mainMenuOpacity = SCENE.add(new NumberSetting("Main Menu Opacity", "Panel opacity while Transparent Menu is enabled", 55, 15, 90, 5).suffix("%").visible(transparentMenu::get));

    public static int menuSurfaceAlpha(){return transparentMenu.get()?Math.round(mainMenuOpacity.getFloat()*2.55f):255;}

    public static final List<SettingSection> GENERAL_PAGE = List.of(INTERFACE, BEHAVIOUR, SCENE);
    public static final List<SettingSection> THEME_PAGE = List.of(ACCENT, WINDOW);
    public static final List<SettingSection> ALL = List.of(INTERFACE, BEHAVIOUR, ACCENT, WINDOW, SCENE);

    public static float animationSpeed() {
        return animSpeed.getFloat();
    }
}
