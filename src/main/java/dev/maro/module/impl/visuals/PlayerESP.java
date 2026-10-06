package dev.maro.module.impl.visuals;

import dev.maro.Maro;
import dev.maro.config.FriendManager;
import dev.maro.gui.hud.EspPreviewScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

/**
 * Shows players through walls as silhouettes that follow their actual model: an animated fill, a
 * smooth contour and an optional glow. Rendering lives in {@link dev.maro.render.esp.PlayerEspRenderer}
 * and {@code player_esp.fsh}; this class decides who is drawn and with what.
 */
public class PlayerESP extends Module {
    /** Order matches fillColor() in player_esp.fsh. */
    private static final String[] FILL_STYLES = {"Solid", "Player", "Gradient", "Rainbow", "Galaxy", "Aurora", "Plasma", "Lava", "Hologram"};
    /** Order matches the contour colour modes in player_esp.fsh. */
    private static final String[] LINE_COLORS = {"Custom", "Player", "Rainbow", "Fill"};
    /** Rainbow is painted in player_esp.fsh; the others are worked out per player here. */
    private static final String[] TRACER_COLORS = {"Player", "Distance", "Rainbow", "Custom"};

    private final ButtonSetting preview = add(new ButtonSetting("Preview", "See the ESP on yourself and flip through styles live", "Open",
            () -> mc.setScreen(new EspPreviewScreen(mc.currentScreen, this))));

    /** While the preview screen is open: draw yourself, even with the module or Self off. */
    private static boolean previewing;

    // ---- targets
    private final BooleanSetting self = add(new BooleanSetting("Self", "Draw yourself in third person", false));
    private final BooleanSetting friends = add(new BooleanSetting("Friends", "Draw players on your friends list", true));
    private final BooleanSetting friendColor = add(new BooleanSetting("Friend Color", "Give friends their own player color", true)
            .visible(friends::get));
    private final ColorSetting friendTint = add(new ColorSetting("Friend Tint", "Player color used for friends", 0xFF55FF88)
            .visible(() -> friends.get() && friendColor.get()));
    private final BooleanSetting healthColors = add(new BooleanSetting("Health Colors", "Player color goes from green to red as health drops", false));
    private final BooleanSetting spectators = add(new BooleanSetting("Spectators", "Draw players in spectator mode", false));
    private final NumberSetting range = add(new NumberSetting("Range", "Only draw players this close (0 = any distance)", 0, 0, 256, 4)
            .suffix(" blocks"));

    // ---- fill
    private final BooleanSetting fill = add(new BooleanSetting("Fill", "Fill the silhouette", true));
    private final ModeSetting fillStyle = add(new ModeSetting("Fill Style", "How the inside of the silhouette is painted", "Galaxy", FILL_STYLES)
            .visible(fill::get));
    private final ColorSetting colorA = add(new ColorSetting("Color", "Main color (Solid, Gradient, Galaxy, Hologram, and the player color)", 0xFF7B2CFF)
            .visible(() -> fill.get() && usesColorA()));
    private final ColorSetting colorB = add(new ColorSetting("Second Color", "Second color for Gradient and Galaxy", 0xFFFF5FD2)
            .visible(() -> fill.get() && (fillStyle.is("Gradient") || fillStyle.is("Galaxy"))));
    private final NumberSetting fillOpacity = add(new NumberSetting("Fill Opacity", "How solid the fill is", 55, 0, 100, 1)
            .suffix("%").visible(fill::get));
    private final NumberSetting edgeFade = add(new NumberSetting("Edge Fade", "Fade the middle of the fill so the edges stand out", 30, 0, 100, 1)
            .suffix("%").visible(fill::get));
    private final NumberSetting stars = add(new NumberSetting("Stars", "How many stars twinkle in the Galaxy fill", 40, 0, 100, 1)
            .suffix("%").visible(() -> fill.get() && fillStyle.is("Galaxy")));
    private final NumberSetting speed = add(new NumberSetting("Animation Speed", "How fast animated fills and colors move", 1, 0, 5, 0.05)
            .suffix("x"));
    private final NumberSetting scale = add(new NumberSetting("Pattern Scale", "Zoom of the fill pattern (higher = finer)", 1, 0.25, 4, 0.05)
            .suffix("x").visible(fill::get));

    // ---- contour
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "Draw a smooth contour around the silhouette", true));
    private final ModeSetting outlineColorMode = add(new ModeSetting("Outline Color", "Where the contour and glow take their color from", "Fill", LINE_COLORS)
            .visible(() -> outline.get() || this.glow.get()));
    private final ColorSetting outlineColor = add(new ColorSetting("Outline Custom", "Contour and glow color in Custom mode", 0xFFFFFFFF)
            .visible(() -> (outline.get() || this.glow.get()) && outlineColorMode.is("Custom")));
    private final NumberSetting outlineWidth = add(new NumberSetting("Outline Width", "Contour thickness at 1080p (scales with resolution)", 1.5, 0.5, 6, 0.25)
            .suffix("px").visible(outline::get));
    private final NumberSetting outlineOpacity = add(new NumberSetting("Outline Opacity", "How solid the contour is", 100, 0, 100, 1)
            .suffix("%").visible(outline::get));

    // ---- glow
    private final BooleanSetting glow = add(new BooleanSetting("Glow", "Soft glow around the silhouette, in the outline color", true));
    private final NumberSetting glowRadius = add(new NumberSetting("Glow Radius", "How far the glow reaches at 1080p", 8, 2, 16, 0.5)
            .suffix("px").visible(glow::get));
    private final NumberSetting glowStrength = add(new NumberSetting("Glow Strength", "How bright the glow is", 60, 0, 150, 1)
            .suffix("%").visible(glow::get));

    // ---- tracers
    private final BooleanSetting tracers = add(new BooleanSetting("Tracers", "Smooth lines from your crosshair to each player", true));
    private final ModeSetting tracerStart = add(new ModeSetting("Tracer Start", "Where the lines start", "Crosshair", "Crosshair", "Bottom")
            .visible(tracers::get));
    private final ModeSetting tracerColor = add(new ModeSetting("Tracer Color", "Player matches the ESP, Distance goes red as they get close",
            "Player", TRACER_COLORS).visible(tracers::get));
    private final ColorSetting tracerCustom = add(new ColorSetting("Tracer Custom", "Line color in Custom mode", 0xFFFFFFFF)
            .visible(() -> tracers.get() && tracerColor.is("Custom")));
    private final NumberSetting tracerWidth = add(new NumberSetting("Tracer Width", "Line thickness at 1080p (scales with resolution)", 1.5, 0.5, 5, 0.25)
            .suffix("px").visible(tracers::get));
    private final NumberSetting tracerOpacity = add(new NumberSetting("Tracer Opacity", "How solid the lines are", 80, 10, 100, 1)
            .suffix("%").visible(tracers::get));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Preview", preview),
            SettingSection.of("Targets", range, self, friends, friendColor, friendTint, healthColors, spectators),
            SettingSection.of("Fill", fill, fillStyle, colorA, colorB, fillOpacity, edgeFade, stars, scale, speed),
            SettingSection.of("Outline", outline, outlineColorMode, outlineColor, outlineWidth, outlineOpacity),
            SettingSection.of("Glow", glow, glowRadius, glowStrength),
            SettingSection.of("Tracers", tracers, tracerStart, tracerColor, tracerCustom, tracerWidth, tracerOpacity));

    private final long start = System.nanoTime();

    public PlayerESP() {
        super("Player ESP", "See players through walls as smooth silhouettes with galaxy, rainbow and other animated fills", Category.VISUALS);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    public static boolean previewing() {
        return previewing;
    }

    /** The main fill colour, used to tint the preview screen's controls. */
    public int accentColor() {
        return colorA.get();
    }

    public static void setPreviewing(boolean on) {
        previewing = on;
    }

    private boolean usesColorA() {
        return fillStyle.is("Solid") || fillStyle.is("Player") || fillStyle.is("Gradient")
                || fillStyle.is("Galaxy") || fillStyle.is("Hologram");
    }

    // ---- what is drawn ----------------------------------------------------------------------

    /** Called on the render thread for each entity rendered this frame. */
    public boolean shouldDraw(Entity entity) {
        if (!(entity instanceof PlayerEntity player) || mc.player == null) return false;
        if (player == mc.player) return (self.get() || previewing) && !mc.options.getPerspective().isFirstPerson();
        if (player.isSpectator() && !spectators.get()) return false;
        if (!friends.get() && FriendManager.isFriend(player.getName().getString())) return false;
        return range.get() <= 0 || mc.player.distanceTo(player) <= range.get();
    }

    /** The colour written into this player's silhouette: what the Player fill and outline modes show. */
    public int playerColor(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (friends.get() && friendColor.get() && player != mc.player && FriendManager.isFriend(player.getName().getString())) {
                return friendTint.get();
            }
            if (healthColors.get()) {
                float max = Math.max(1f, player.getMaxHealth());
                float health = Math.max(0f, Math.min(1f, (player.getHealth() + player.getAbsorptionAmount()) / max));
                return ColorUtil.hsv(health / 3f, 0.85f, 1f);
            }
        }
        return colorA.get();
    }

    // ---- shader inputs ----------------------------------------------------------------------

    /** Contour and glow sizes are given at 1080p and grow with the window, so they look the same. */
    private float pixelScale() {
        return Math.max(1f, mc.getWindow().getFramebufferHeight() / 1080f);
    }

    private float widthPx() {
        return outlineWidth.getFloat() * pixelScale();
    }

    private float glowPx() {
        return Math.min(24f, glowRadius.getFloat() * pixelScale());
    }

    /** How far past the silhouette the effect paints, in framebuffer pixels. */
    public float effectReach() {
        float reach = outline.get() ? widthPx() : 0;
        if (glow.get()) reach = Math.max(reach, glowPx());
        return reach + 2;
    }

    // ---- tracers ----------------------------------------------------------------------------

    public boolean tracersOn() {
        return tracers.get();
    }

    public boolean tracersFromBottom() {
        return tracerStart.is("Bottom");
    }

    public boolean rainbowTracers() {
        return tracerColor.is("Rainbow");
    }

    public float tracerWidthPx() {
        return tracerWidth.getFloat() * pixelScale();
    }

    /** ARGB for this player's tracer; alpha carries the opacity. Unused in Rainbow mode. */
    public int tracerColor(Entity entity) {
        int rgb;
        if (tracerColor.is("Custom")) {
            rgb = tracerCustom.get();
        } else if (tracerColor.is("Distance") && mc.player != null) {
            float near = Math.max(0f, Math.min(1f, mc.player.distanceTo(entity) / 64f));
            rgb = ColorUtil.hsv(near / 3f, 0.85f, 1f);
        } else {
            rgb = playerColor(entity);
        }
        int alpha = Math.round(tracerOpacity.getFloat() / 100f * 255f);
        return alpha << 24 | (rgb & 0xFFFFFF);
    }

    /** The EspData block of player_esp.fsh, as 28 floats in order; the renderer fills in the last (mask scale). */
    public float[] uniformValues() {
        int a = colorA.get(), b = colorB.get(), line = outlineColor.get();
        float seconds = (float) (((System.nanoTime() - start) / 1e9) % 3600.0);
        return new float[] {
                red(a), green(a), blue(a), 1,
                red(b), green(b), blue(b), 1,
                red(line), green(line), blue(line), 1,
                seconds, speed.getFloat(), scale.getFloat(), stars.getFloat() / 100f * 0.55f,
                fillStyle.index(), fillOpacity.getFloat() / 100f, edgeFade.getFloat() / 100f, fill.get() ? 1 : 0,
                outlineColorMode.index(), widthPx(), outlineOpacity.getFloat() / 100f, outline.get() ? 1 : 0,
                glow.get() ? 1 : 0, glowPx(), glowStrength.getFloat() / 100f, 0
        };
    }

    private static float red(int c) {
        return ColorUtil.red(c) / 255f;
    }

    private static float green(int c) {
        return ColorUtil.green(c) / 255f;
    }

    private static float blue(int c) {
        return ColorUtil.blue(c) / 255f;
    }

    /** The composite pass threw: report once and switch off rather than fail every frame. */
    public void renderFailed(RuntimeException e) {
        Maro.LOGGER.error("Player ESP could not render", e);
        Notifications.push(getName(), "Rendering failed - see the log. Turned off.", Notifications.Type.ERROR);
        setEnabled(false);
    }
}
