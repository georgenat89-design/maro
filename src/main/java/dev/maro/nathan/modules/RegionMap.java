package dev.maro.nathan.modules;

import java.util.Arrays;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;

import dev.maro.runtime.events.render.Render2DEvent;
import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.gui.widgets.WWidget;
import dev.maro.runtime.gui.widgets.containers.WHorizontalList;
import dev.maro.runtime.gui.widgets.containers.WVerticalList;
import dev.maro.runtime.gui.widgets.pressable.WButton;
import dev.maro.runtime.renderer.MeshBuilder;
import dev.maro.runtime.renderer.Renderer2D;
import dev.maro.runtime.renderer.Texture;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.ColorSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.runtime.utils.render.color.SettingColor;

import dev.maro.runtime.event.EventHandler;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.gui.RegionMapScreen;
import dev.maro.nathan.regionmap.RegionGrid;
import dev.maro.nathan.regionmap.RegionMapRaster;
import dev.maro.nathan.render.CrispFont;
import dev.maro.nathan.render.RoundedBox;

/**
 * The server's regions as a map on screen, with where you are on it.
 *
 * <p>The 36 by 36 source grid is drawn as differently sized numbered shards on a
 * dark glass card: a flowing band of the group colours along its top edge, the
 * region you are in written large over the map with its group and the way you
 * are facing, glossy tiles that dim every group but yours, a pulsing outline on
 * your own shard, a radar ping under the arrow, coordinate chips under the map
 * and the groups as a row of chips. Scale still grows every part together.
 *
 * <p>The cells and all 201 antialiased labels share one cached texture, rebuilt
 * when scale, colours, label settings or the group you are in change.
 *
 * <p>The supplied shard placements are kept apart in {@link RegionGrid}.
 */
public class RegionMap extends Module {
    // Every measurement here is in units: one interface pixel of the map at a
    // Scale of 1. The exception is the width of a fading edge, which is a
    // property of the screen rather than of the map.

    /** The pitch of one cell, and how much of that pitch is gutter. */
    private static final double CELL = 3;
    private static final double GUTTER = RegionMapRaster.GUTTER_UNITS;

    private static final double GRID = CELL * RegionGrid.SIDE;
    private static final double PAD = 6;
    private static final double WIDTH = GRID + PAD * 2;

    /** The bands over and under the grid, and the air between them. */
    private static final double HEADER = 10;
    private static final double FOOTER = 8;
    private static final double BAND_GAP = 5;

    /** The legend chips: their height, the air round them, the dot in each and the padding either side. */
    private static final double CHIP = 7;
    private static final double CHIP_GAP = 2;
    private static final double DOT = 2.6;
    private static final double CHIP_PAD = 2.4;

    private static final double PANEL_RADIUS = 5;
    private static final double CELL_RADIUS = RegionMapRaster.CORNER_UNITS;
    private static final double RULE = 0.6;

    /** Cap heights for the panel lettering. */
    private static final double NUMBER_CAPS = 7;
    private static final double LABEL_CAPS = 2.3;
    private static final double LOCALE_CAPS = 3.4;
    private static final double CHIP_CAPS = 3;
    private static final double LEGEND_CAPS = 2.4;

    /** Letter spacing for the lettering set in capitals. */
    private static final double TRACKING = 0.5;

    /** Width of the fading skin round each shape, in real pixels: as everywhere else here. */
    private static final double FEATHER = 1.5;

    /** How far Scale goes either way. The setting and the wheel are held to the same pair. */
    private static final double SCALE_MIN = 0.4;
    private static final double SCALE_MAX = 4;

    /** The arrow at a Marker Size of 1, from its middle to its point. */
    private static final double MARKER = 4.6;

    /** Seconds for the marker's heading to catch up with your own. */
    private static final double TURN = 0.07;

    /** Seconds for one pulse round your shard, and for one ping under the arrow. */
    private static final double PULSE = 1.6;
    private static final double PING = 2.2;

    /** Saturated colours that keep white numbers readable, in {@link RegionGrid.Locale} order. */
    private static final int[] NEON = { 0x3D6CFF, 0xEC4468, 0x17A862, 0xE58A0C, 0x965AFF, 0x0EA2B4, 0xDB4BB0 };

    /** Original six muted colours, plus one for the new Europe shards. */
    private static final int[] MUTED = { 0x3F67B2, 0xC45860, 0x3C7B13, 0xB88B1F, 0xA37CDF, 0x069A9D, 0xB065A8 };

    /** Original six signal colours, plus one for the new Europe shards. */
    private static final int[] SIGNAL = { 0x008BC4, 0xAA4A0F, 0x928905, 0xC24E90, 0x684DD4, 0x007555, 0xC063AF };

    /** Compass points by yaw in steps of 45 degrees: the game's yaw is 0 facing south. */
    private static final String[] HEADINGS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

    public enum Palette {
        Neon,
        Muted,
        Signal,
        Custom
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");
    private final SettingGroup sgColors = settings.createGroup("Map Colors", false);

    private final Setting<Integer> x = sgGeneral.add(new IntSetting.Builder()
        .name("x")
        .description("Distance from the left of the screen, in interface pixels. Easier set by dragging.")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 480)
        .build()
    );

    private final Setting<Integer> y = sgGeneral.add(new IntSetting.Builder()
        .name("y")
        .description("Distance from the top of the screen, in interface pixels. Easier set by dragging.")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 270)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("How big the map is. Everything in it scales together, so it keeps its shape.")
        .defaultValue(1)
        .min(SCALE_MIN)
        .max(SCALE_MAX)
        .sliderRange(0.5, 2.5)
        .build()
    );

    private final Setting<Boolean> header = sgGeneral.add(new BoolSetting.Builder()
        .name("header")
        .description("Over the map: the region you are in, written large, its group, and the way you are facing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> numbers = sgGeneral.add(new BoolSetting.Builder()
        .name("numbers")
        .description("The region's number on its cell. All numbers use Text Color.")
        .defaultValue(true)
        .build()
    );

    private final Setting<RegionMapRaster.NumberFont> numberFont = sgGeneral.add(new EnumSetting.Builder<RegionMapRaster.NumberFont>()
        .name("number-font")
        .description("Rounded and Rounded Bold use normal-width digits. Condensed fits more text in narrow cells.")
        .defaultValue(RegionMapRaster.NumberFont.RoundedBold)
        .visible(numbers::get)
        .build()
    );

    private final Setting<Double> numberSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("number-size")
        .description("Size of the numbers, without resizing the map. Disable Fit Numbers to enlarge digits past a small cell's bounds.")
        .defaultValue(1.25)
        .range(0.5, 2.5)
        .sliderRange(0.5, 2.5)
        .visible(numbers::get)
        .build()
    );

    private final Setting<Boolean> fitNumbers = sgGeneral.add(new BoolSetting.Builder()
        .name("fit-numbers")
        .description("Shrink numbers to fit their cells. When off, Number Size is respected and labels may overlap neighbouring cells.")
        .defaultValue(true)
        .visible(numbers::get)
        .build()
    );

    private final Setting<Boolean> footer = sgGeneral.add(new BoolSetting.Builder()
        .name("footer")
        .description("Your x and z, in two chips under the map.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> legend = sgGeneral.add(new BoolSetting.Builder()
        .name("legend")
        .description("The seven groups as chips under the map, with yours lit up.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> marker = sgGeneral.add(new BoolSetting.Builder()
        .name("marker")
        .description("An arrow where you are, pointing the way you are looking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> markerSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("marker-size")
        .description("How big the arrow is. It grows about its own middle, so it stays on the spot it marks.")
        .defaultValue(1)
        .min(0.4)
        .max(2.5)
        .sliderRange(0.5, 2)
        .visible(marker::get)
        .build()
    );

    private final Setting<Boolean> details = sgGeneral.add(new BoolSetting.Builder()
        .name("hover-details")
        .description("While a screen is open - the placement screen included - the pointer on a cell says its region, its group and the blocks it runs between.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> glossy = sgLook.add(new BoolSetting.Builder()
        .name("glossy-tiles")
        .description("Tiles lit from above, with a bright top edge and a soft shadow under each number.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> spotlight = sgLook.add(new BoolSetting.Builder()
        .name("spotlight")
        .description("Dims every group but the one you are in, so yours stands out.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> spotlightStrength = sgLook.add(new IntSetting.Builder()
        .name("spotlight-strength")
        .description("How far the other groups are dimmed, as a percentage.")
        .defaultValue(40)
        .range(0, 85)
        .sliderRange(0, 85)
        .visible(spotlight::get)
        .build()
    );

    private final Setting<Boolean> animations = sgLook.add(new BoolSetting.Builder()
        .name("animations")
        .description("The pulse round your shard, the ping under the arrow and the colours flowing along the top edge.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> backgroundOpacity = sgLook.add(new IntSetting.Builder()
        .name("panel-opacity")
        .description("How solid the panel is, as a percentage. The panel only: the cells, the rim and the lettering keep their own.")
        .defaultValue(88)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgLook.add(new ColorSetting.Builder()
        .name("panel-color")
        .description("The panel behind the map. How solid it is is the setting over this one.")
        .defaultValue(new SettingColor(11, 13, 18))
        .build()
    );

    private final Setting<SettingColor> borderColor = sgLook.add(new ColorSetting.Builder()
        .name("rim-color")
        .description("The fine rim round the panel and round each chip.")
        .defaultValue(new SettingColor(255, 255, 255, 26))
        .build()
    );

    private final Setting<SettingColor> accentColor = sgLook.add(new ColorSetting.Builder()
        .name("glow-color")
        .description("The outline round the cell you are standing in. Its pulse takes your group's colour.")
        .defaultValue(new SettingColor(255, 255, 255, 240))
        .build()
    );

    private final Setting<SettingColor> markerColor = sgLook.add(new ColorSetting.Builder()
        .name("marker-color")
        .description("The arrow and its glow. Its outline is worked out from it, dark on a light arrow and light on a dark one.")
        .defaultValue(new SettingColor(245, 248, 255))
        .visible(marker::get)
        .build()
    );

    private final Setting<SettingColor> textColor = sgLook.add(new ColorSetting.Builder()
        .name("text-color")
        .description("One color for all region numbers and panel lettering. Labels use it at lower opacity.")
        .defaultValue(new SettingColor(240, 243, 250))
        .build()
    );

    private final Setting<Boolean> shadow = sgLook.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Shadow under the lettering on the panel. Cell numbers have their own with Glossy Tiles.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Palette> palette = sgColors.add(new EnumSetting.Builder<Palette>()
        .name("scheme")
        .description("Neon is bright and saturated. Muted and Signal are the original colours. Custom lets you change all seven.")
        .defaultValue(Palette.Neon)
        .build()
    );

    private final Setting<SettingColor> euCentralColor = sgColors.add(group("eu-central-color", RegionGrid.Locale.EuCentral).build());
    private final Setting<SettingColor> euWestColor = sgColors.add(group("eu-west-color", RegionGrid.Locale.EuWest).build());
    private final Setting<SettingColor> naEastColor = sgColors.add(group("na-east-color", RegionGrid.Locale.NaEast).build());
    private final Setting<SettingColor> naWestColor = sgColors.add(group("na-west-color", RegionGrid.Locale.NaWest).build());
    private final Setting<SettingColor> asiaColor = sgColors.add(group("asia-color", RegionGrid.Locale.Asia).build());
    private final Setting<SettingColor> oceaniaColor = sgColors.add(group("oceania-color", RegionGrid.Locale.Oceania).build());
    private final Setting<SettingColor> europeColor = sgColors.add(group("europe-color", RegionGrid.Locale.Europe).build());

    /** The heading the marker is drawn at, and whether it has been set yet. */
    private double shownYaw;
    private boolean turning;
    private long lastFrameAt;

    /** What the pointer was on, while a screen was open. */
    private int hovered = -1;

    /** The panel's height in units, for the placement screen. */
    private double panelUnits = GRID + PAD * 2;

    private Texture gridTexture;
    private int rasterSize;
    private boolean rasterNumbers;
    private RegionMapRaster.NumberFont rasterFont;
    private double rasterNumberSize;
    private boolean rasterFitNumbers;
    private boolean rasterGloss;
    private int rasterSpotlight;
    private double rasterDim;
    private int[] rasterFills;
    private int[] rasterInks;

    public RegionMap() {
        super(NameeProtectAddon.CATEGORY, "region-map", "The server's regions as a map on screen, with where you are on it.");
    }

    private ColorSetting.Builder group(String name, RegionGrid.Locale of) {
        int rgb = NEON[of.ordinal()];

        return new ColorSetting.Builder()
            .name(name)
            .description("Cells served from " + of + ".")
            .defaultValue(new SettingColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF))
            .visible(() -> palette.get() == Palette.Custom);
    }

    @Override
    public void onActivate() {
        turning = false;
        lastFrameAt = 0;
        hovered = -1;
    }

    @Override
    public void onDeactivate() {
        if (gridTexture != null) gridTexture.close();
        gridTexture = null;
        rasterFills = null;
        rasterInks = null;
    }

    /** The seven colours in use, in {@link RegionGrid.Locale} order. */
    private Color[] colors() {
        if (palette.get() == Palette.Custom) {
            return new Color[]{euCentralColor.get(), euWestColor.get(), naEastColor.get(),
                naWestColor.get(), asiaColor.get(), oceaniaColor.get(), europeColor.get()};
        }

        int[] rgb = switch (palette.get()) {
            case Muted -> MUTED;
            case Signal -> SIGNAL;
            default -> NEON;
        };
        Color[] out = new Color[rgb.length];

        for (int i = 0; i < rgb.length; i++) out[i] = new Color(rgb[i] >> 16 & 0xFF, rgb[i] >> 8 & 0xFF, rgb[i] & 0xFF);

        return out;
    }

    // -------------------------------------------------------------- placement

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WButton place = list.add(theme.button("Place the map")).expandX().widget();
        place.action = () -> mc.setScreen(new RegionMapScreen(mc.currentScreen, this));

        WHorizontalList resets = list.add(theme.horizontalList()).expandX().widget();

        WButton colours = resets.add(theme.button("Reset Colors")).expandX().widget();
        colours.action = () -> {
            for (Setting<?> setting : sgColors) setting.reset();
        };

        WButton layout = resets.add(theme.button("Reset Layout")).expandX().widget();
        layout.action = this::resetLayout;

        return list;
    }

    // Interface pixels, which is what a screen's mouse coordinates are in.

    public double mapLeft() {
        return x.get();
    }

    public double mapTop() {
        return y.get();
    }

    public double mapWidth() {
        return WIDTH * scale.get();
    }

    public double mapHeight() {
        return panelUnits * scale.get();
    }

    public double mapScale() {
        return scale.get();
    }

    public boolean mapContains(double interfaceX, double interfaceY) {
        return interfaceX >= mapLeft() && interfaceY >= mapTop()
            && interfaceX <= mapLeft() + mapWidth() && interfaceY <= mapTop() + mapHeight();
    }

    /** Called as the map is dragged, and kept on the screen. Saved with the rest of Meteor's settings. */
    public void moveMap(double left, double top) {
        double room = mc.getWindow().getScaledWidth() - mapWidth();
        double drop = mc.getWindow().getScaledHeight() - mapHeight();

        x.set((int) Math.round(Math.max(0, Math.min(Math.max(0, room), left))));
        y.set((int) Math.round(Math.max(0, Math.min(Math.max(0, drop), top))));
    }

    public void scaleMap(double by) {
        scale.set(Math.max(SCALE_MIN, Math.min(SCALE_MAX, Math.round((scale.get() + by) * 100) / 100.0)));
        moveMap(mapLeft(), mapTop());
    }

    public void resetLayout() {
        x.reset();
        y.reset();
        scale.reset();
    }

    // -------------------------------------------------------------- the frame

    /** Where each legend chip sits, in units from the panel's left, and on which row. */
    private record Chips(String[] labels, double[] x, int[] row, double[] width, int rows) {}

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || mc.options == null || mc.options.hudHidden) return;
        if (!CrispFont.POPPINS_MEDIUM.ready() || !CrispFont.POPPINS_SEMIBOLD.ready()) return;

        // Measured from the clock and capped, so the marker turns at the same rate
        // at 40 frames a second as at 400.
        long nanos = System.nanoTime();
        double seconds = lastFrameAt == 0 ? 0 : Math.min((nanos - lastFrameAt) / 1.0e9, 0.05);
        lastFrameAt = nanos;
        double clock = animations.get() ? nanos / 1.0e9 : 0;

        double gui = mc.getWindow().getScaleFactor();
        double unit = gui * scale.get();
        double left = Math.round(x.get() * gui);
        double top = Math.round(y.get() * gui);

        double worldX = mc.player.getX();
        double worldZ = mc.player.getZ();
        double column = RegionGrid.column(worldX);
        double row = RegionGrid.row(worldZ);
        int here = RegionGrid.cell(column, row);
        RegionGrid.Locale locale = here < 0 ? null : RegionGrid.servedBy(here);

        double want = mc.player.getYaw();

        if (!turning) {
            shownYaw = want;
            turning = true;
        } else {
            // The short way round, so 359 to 1 is two degrees and not 358.
            double turn = ((want - shownYaw) % 360 + 540) % 360 - 180;

            shownYaw += turn * Math.min(1, seconds / TURN);
        }

        Color[] colors = colors();
        Color ink = textColor.get();
        Color rim = borderColor.get();
        Color glow = accentColor.get();
        Color base = backgroundColor.get();
        double opacity = backgroundOpacity.get() / 100.0;
        Color panel = new Color(base.r, base.g, base.b, (int) Math.round(255 * opacity));
        Color own = locale == null ? ink : colors[locale.ordinal()];
        Color ownLight = mixed(own, WHITE, 0.35);

        // ---- the bands, down the panel in units

        CrispFont.Sized legendFont = CrispFont.POPPINS_SEMIBOLD.forCaps(LEGEND_CAPS * unit);
        Chips chips = legend.get() ? legendChips(legendFont, unit) : null;

        double gridTop = PAD + (header.get() ? HEADER + BAND_GAP : 0);
        double under = gridTop + GRID;
        double footTop = under + BAND_GAP;
        double legendTop = footTop + (footer.get() ? FOOTER + BAND_GAP : 0);

        panelUnits = (chips != null ? legendTop + chips.rows() * (CHIP + CHIP_GAP) - CHIP_GAP
            : footer.get() ? footTop + FOOTER : under) + PAD;

        pointer(left, top, unit, gridTop);

        double panelWidth = WIDTH * unit;
        double panelHeight = panelUnits * unit;

        int spot = spotlight.get() && locale != null ? locale.ordinal() : -1;
        updateGridTexture(unit, colors, ink, numbers.get(), spot);

        // The header, measured before anything is drawn so its chip can be laid
        // out round the text.
        String number = here < 0 ? "--" : Integer.toString(RegionGrid.region(here));
        String group = locale == null ? "OFF THE MAP" : upper(locale.toString());
        String heading = HEADINGS[(int) Math.round(((shownYaw % 360) + 360) % 360 / 45) % 8];

        CrispFont.Sized big = CrispFont.POPPINS_SEMIBOLD.forCaps(NUMBER_CAPS * unit);
        CrispFont.Sized label = CrispFont.POPPINS_SEMIBOLD.forCaps(LABEL_CAPS * unit);
        CrispFont.Sized groupFont = CrispFont.POPPINS_SEMIBOLD.forCaps(LOCALE_CAPS * unit);
        CrispFont.Sized chipFont = CrispFont.POPPINS_SEMIBOLD.forCaps(CHIP_CAPS * unit);

        double bandMiddle = top + (PAD + HEADER / 2) * unit;
        double numberCapsTop = Math.round(bandMiddle - big.caps() / 2);
        double compassWidth = (CHIP_PAD * 2 + 3.2 + 1.4) * unit + chipFont.width(heading, TRACKING * unit);
        double compassHeight = HEADER * 0.78 * unit;
        double compassLeft = left + (WIDTH - PAD) * unit - compassWidth;

        // The footer chips: one font for both, fitted so that a long coordinate
        // still sits inside its chip.
        String atX = Long.toString((long) Math.floor(worldX));
        String atZ = Long.toString((long) Math.floor(worldZ));
        double footChip = (GRID - CHIP_GAP) / 2;
        CrispFont.Sized footFont = fitted(CrispFont.POPPINS_SEMIBOLD, "X   " + (atX.length() > atZ.length() ? atX : atZ),
            CHIP_CAPS * unit, (footChip - CHIP_PAD * 2.5) * unit);

        // ---- the shapes

        Renderer2D.COLOR.begin();

        RoundedBox.shadow(left + panelWidth / 2, top + panelHeight / 2 + 2 * unit, panelWidth + 2 * unit, panelHeight + 2 * unit,
            (PANEL_RADIUS + 2) * unit, 12 * unit, 0, 0.55 * Math.max(0.25, opacity));
        RoundedBox.draw(left + panelWidth / 2, top + panelHeight / 2, panelWidth, panelHeight,
            PANEL_RADIUS * unit, Math.max(1, RULE * unit), FEATHER, 0, panel, rim);

        crown(left, top, unit, colors, clock);

        // A darker well the mosaic sits in, so its edge reads as a frame.
        double well = 1.4;
        RoundedBox.draw(left + WIDTH * unit / 2, top + (gridTop + GRID / 2) * unit, (GRID + well * 2) * unit, (GRID + well * 2) * unit,
            (CELL_RADIUS + well + 0.6) * unit, Math.max(1, RULE * unit), FEATHER, 0,
            new Color(0, 0, 0, (int) Math.round(120 * Math.max(0.35, opacity))), tinted(rim, 0.7));

        if (header.get()) {
            chip(compassLeft, bandMiddle - compassHeight / 2, compassWidth, compassHeight, unit, WHITE, rim, 0.06);
            kite(compassLeft + (CHIP_PAD + 1.6) * unit, bandMiddle, 1.7 * unit, shownYaw, ownLight, false);
        }

        if (footer.get()) {
            for (int i = 0; i < 2; i++) {
                chip(left + (PAD + i * (footChip + CHIP_GAP)) * unit, top + footTop * unit, footChip * unit, FOOTER * unit,
                    unit, WHITE, rim, 0.05);
            }
        }

        if (chips != null) {
            RegionGrid.Locale[] all = RegionGrid.Locale.values();

            for (int i = 0; i < all.length; i++) {
                double chipLeft = left + chips.x()[i] * unit;
                double chipTop = top + (legendTop + chips.row()[i] * (CHIP + CHIP_GAP)) * unit;
                boolean mine = all[i] == locale;

                if (mine) chip(chipLeft, chipTop, chips.width()[i] * unit, CHIP * unit, unit, colors[i], tinted(colors[i], 0.9), 0.24);
                else chip(chipLeft, chipTop, chips.width()[i] * unit, CHIP * unit, unit, WHITE, rim, 0.04);

                double dotX = chipLeft + (CHIP_PAD + DOT / 2) * unit;
                double dotY = chipTop + CHIP * unit / 2;

                if (mine) RoundedBox.draw(dotX, dotY, DOT * 1.8 * unit, DOT * 1.8 * unit, DOT * 0.9 * unit, 0, DOT * unit, 0,
                    tinted(colors[i], 0.55), tinted(colors[i], 0.55));
                RoundedBox.draw(dotX, dotY, DOT * unit, DOT * unit, DOT / 2 * unit, 0, FEATHER, 0, colors[i], colors[i]);
            }
        }

        Renderer2D.COLOR.render();

        Renderer2D.TEXTURE.begin();
        // The artwork already has antialiased edges. Match texels to physical
        // pixels at an integer origin instead of resampling it a second time.
        Renderer2D.TEXTURE.texQuad(Math.round(left + PAD * unit), Math.round(top + gridTop * unit), rasterSize, rasterSize,
            new Color(255, 255, 255));
        Renderer2D.TEXTURE.render(gridTexture.getGlTextureView(), gridTexture.getSampler());

        Renderer2D.COLOR.begin();

        // The cell you are in: a glow inside it, a bright outline, and a pulse in
        // your group's colour leaving it.
        if (here >= 0) {
            RegionGrid.Shard shard = RegionGrid.shard(here);
            double[] middle = cellMiddle(left, top, unit, gridTop, here);
            double w = shardWidth(shard, unit);
            double h = shardHeight(shard, unit);

            if (animations.get()) {
                double phase = (clock % PULSE) / PULSE;

                RoundedBox.ripple(middle[0], middle[1], w, h, CELL_RADIUS * unit, FEATHER, (0.4 + phase * 3.6) * unit,
                    1.3 * unit, tinted(ownLight, Math.pow(1 - phase, 1.6) * 0.9));
            }

            RoundedBox.innerGlow(middle[0], middle[1], w, h, CELL_RADIUS * unit, FEATHER, 0, 2.2 * unit, 0, tinted(glow, 0.4));
            RoundedBox.drawCompact(middle[0], middle[1], w, h, CELL_RADIUS * unit, Math.max(1, 0.8 * unit), FEATHER, 0,
                CLEAR, glow);
        }

        if (hovered >= 0 && hovered != here) {
            RegionGrid.Shard shard = RegionGrid.shard(hovered);
            double[] middle = cellMiddle(left, top, unit, gridTop, hovered);

            RoundedBox.drawCompact(middle[0], middle[1], shardWidth(shard, unit), shardHeight(shard, unit),
                CELL_RADIUS * unit, Math.max(1, 0.7 * unit), FEATHER, 0,
                tinted(WHITE, 0.12), tinted(ink, 0.75));
        }

        if (marker.get() && here >= 0) {
            double px = left + (PAD + column * CELL) * unit;
            double py = top + (gridTop + row * CELL) * unit;
            double size = MARKER * markerSize.get() * unit;
            Color color = markerColor.get();

            // A soft halo, then a ring that leaves it like a radar ping.
            double halo = size * 2.2;
            RoundedBox.draw(px, py, halo, halo, halo / 2, 0, halo * 0.5, 0, tinted(color, 0.3), tinted(color, 0.3));

            if (animations.get()) {
                double phase = (clock % PING) / PING;
                double ring = size * 1.1;

                RoundedBox.ripple(px, py, ring, ring, ring / 2, FEATHER, phase * 7 * unit * markerSize.get(), 1.1 * unit,
                    tinted(color, Math.pow(1 - phase, 2) * 0.85));
            }

            kite(px, py, size, shownYaw, color, true);
        }

        Renderer2D.COLOR.render();

        // ---- the lettering

        CrispFont.begin(null);

        if (header.get()) {
            double numberLeft = left + PAD * unit;
            big.draw(number, numberLeft, numberCapsTop - big.capsTop(), here < 0 ? tinted(ink, 0.5) : ink, 0, shadow.get());

            double stackLeft = numberLeft + big.width(number, 0) + 3.2 * unit;
            label.draw("REGION MAP", stackLeft, numberCapsTop - label.capsTop(), tinted(ink, 0.42), TRACKING * 1.6 * unit, shadow.get());

            double groupCapsTop = numberCapsTop + big.caps() - groupFont.caps();
            groupFont.draw(group, stackLeft, groupCapsTop - groupFont.capsTop(), locale == null ? tinted(ink, 0.5) : ownLight,
                TRACKING * unit, shadow.get());

            chipFont.draw(heading, compassLeft + (CHIP_PAD + 3.2 + 1.4) * unit,
                Math.round(bandMiddle - chipFont.caps() / 2) - chipFont.capsTop(), ink, TRACKING * unit, shadow.get());
        }

        if (footer.get()) {
            String[] values = { atX, atZ };
            String[] names = { "X", "Z" };

            for (int i = 0; i < 2; i++) {
                double chipLeft = left + (PAD + i * (footChip + CHIP_GAP)) * unit;
                double capsTop = Math.round(top + footTop * unit + (FOOTER * unit - footFont.caps()) / 2) - footFont.capsTop();

                footFont.draw(names[i], chipLeft + CHIP_PAD * unit, capsTop, ownLight, 0, shadow.get());
                footFont.draw(values[i], chipLeft + (footChip - CHIP_PAD) * unit - footFont.width(values[i], 0), capsTop, ink, 0, shadow.get());
            }
        }

        if (chips != null) {
            RegionGrid.Locale[] all = RegionGrid.Locale.values();

            for (int i = 0; i < all.length; i++) {
                double chipLeft = left + chips.x()[i] * unit;
                double chipTop = top + (legendTop + chips.row()[i] * (CHIP + CHIP_GAP)) * unit;
                boolean mine = all[i] == locale;

                legendFont.draw(chips.labels()[i], chipLeft + (CHIP_PAD + DOT + 1.6) * unit,
                    Math.round(chipTop + (CHIP * unit - legendFont.caps()) / 2) - legendFont.capsTop(),
                    mine ? ink : tinted(ink, 0.62), TRACKING * unit, shadow.get());
            }
        }

        CrispFont.end();

        if (hovered >= 0 && details.get()) tip(hovered, unit, colors, ink, rim, panel);
    }

    /** Nothing at all: the fill of a shape that is only a rim. */
    private static final Color CLEAR = new Color(0, 0, 0, 0);
    private static final Color WHITE = new Color(255, 255, 255);

    /** A pill: {@code fill} at {@code strength} of its own alpha, with a fine rim. */
    private static void chip(double left, double top, double width, double height, double unit, Color fill, Color rim, double strength) {
        RoundedBox.draw(left + width / 2, top + height / 2, width, height, Math.min(height / 2, 2.6 * unit),
            Math.max(1, RULE * unit), FEATHER, 0, tinted(fill, strength), rim);
    }

    /**
     * The flowing band along the top of the panel: the group colours one into the
     * next, faded out towards both corners, with a faint spill of the same light
     * down into the header. It drifts sideways while Animations is on.
     */
    private static void crown(double left, double top, double unit, Color[] colors, double clock) {
        double from = left + PANEL_RADIUS * unit;
        double span = (WIDTH - PANEL_RADIUS * 2) * unit;
        double thick = Math.max(1, 0.8 * unit);
        double spill = 8 * unit;
        int steps = 56;
        MeshBuilder mesh = Renderer2D.COLOR.triangles;

        for (int i = 0; i < steps; i++) {
            double a = (double) i / steps;
            double b = (double) (i + 1) / steps;
            double ea = Math.sqrt(Math.sin(Math.PI * a));
            double eb = Math.sqrt(Math.sin(Math.PI * b));
            Color ca = flow(colors, a * 0.6 + clock * 0.05);
            Color cb = flow(colors, b * 0.6 + clock * 0.05);
            double xa = from + span * a;
            double xb = from + span * b;

            gradient(mesh, xa, top, xb, top + thick, tinted(ca, ea), tinted(ca, ea), tinted(cb, eb), tinted(cb, eb));
            gradient(mesh, xa, top + thick, xb, top + thick + spill, tinted(ca, ea * 0.16), CLEARED, CLEARED, tinted(cb, eb * 0.16));
        }
    }

    private static final Color CLEARED = new Color(255, 255, 255, 0);

    /** One rectangle with a colour at each corner: top left, bottom left, bottom right, top right. */
    private static void gradient(MeshBuilder mesh, double x0, double y0, double x1, double y1,
                                 Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
        mesh.ensureQuadCapacity();
        mesh.quad(
            mesh.vec2(x0, y0).color(topLeft).next(),
            mesh.vec2(x0, y1).color(bottomLeft).next(),
            mesh.vec2(x1, y1).color(bottomRight).next(),
            mesh.vec2(x1, y0).color(topRight).next());
    }

    /** A point along the group colours laid end to end in a loop, {@code at} 1 being once round. */
    private static Color flow(Color[] colors, double at) {
        double along = ((at % 1) + 1) % 1 * colors.length;
        int i = (int) Math.floor(along) % colors.length;
        Color from = colors[i];
        Color to = colors[(i + 1) % colors.length];
        Color out = mixed(from, to, along - Math.floor(along));

        return new Color(out.r, out.g, out.b, 255);
    }

    /** The legend as chips, flowed into rows as wide as the map and each row centred; null if one will not fit. */
    private static Chips legendChips(CrispFont.Sized font, double unit) {
        RegionGrid.Locale[] all = RegionGrid.Locale.values();
        int count = all.length;
        String[] labels = new String[count];
        double[] width = new double[count];
        double[] x = new double[count];
        int[] row = new int[count];

        for (int i = 0; i < count; i++) {
            labels[i] = upper(all[i].toString());
            width[i] = CHIP_PAD + DOT + 1.6 + font.width(labels[i], TRACKING * unit) / unit + CHIP_PAD;

            if (width[i] > GRID) return null;
        }

        int rows = 0;
        int first = 0;
        double used = 0;

        for (int i = 0; i < count; i++) {
            double need = i == first ? width[i] : used + CHIP_GAP + width[i];

            if (need > GRID) {
                centre(x, width, first, i, used);
                rows++;
                first = i;
                used = width[i];
            } else {
                used = need;
            }

            row[i] = rows;
        }

        centre(x, width, first, count, used);

        return new Chips(labels, x, row, width, rows + 1);
    }

    private static void centre(double[] x, double[] width, int from, int to, double used) {
        double at = PAD + (GRID - used) / 2;

        for (int i = from; i < to; i++) {
            x[i] = at;
            at += width[i] + CHIP_GAP;
        }
    }

    private static String upper(String text) {
        return text.toUpperCase(java.util.Locale.ROOT);
    }

    private static double[] cellMiddle(double left, double top, double unit, double gridTop, int cell) {
        RegionGrid.Shard shard = RegionGrid.shard(cell);
        return new double[]{
            left + (PAD + (shard.col() + shard.width() / 2.0) * CELL) * unit,
            top + (gridTop + (shard.row() + shard.height() / 2.0) * CELL) * unit};
    }

    private static double shardWidth(RegionGrid.Shard shard, double unit) {
        return (shard.width() * CELL - GUTTER) * unit;
    }

    private static double shardHeight(RegionGrid.Shard shard, double unit) {
        return (shard.height() * CELL - GUTTER) * unit;
    }

    /**
     * The size a line is drawn at: the cap height it would like, held to the width
     * it has to fit in. The font rasterises at whatever whole height it is asked
     * for, so the height that fits is asked for rather than stepped down to.
     *
     * <p>There is a floor under it - no font is made smaller than six pixels,
     * because under that there is nothing to read.
     */
    private static CrispFont.Sized fitted(CrispFont font, String text, double caps, double room) {
        return font.at((int) Math.min(Math.round(caps / font.capsRatio()), Math.floor(room / font.unitWidth(text))));
    }

    private void updateGridTexture(double unit, Color[] colors, Color ink, boolean showNumbers, int spot) {
        int size = Math.max(1, Math.min(4096, (int) Math.round(GRID * unit)));
        RegionMapRaster.NumberFont chosenFont = numberFont.get();
        double chosenSize = numberSize.get();
        boolean fit = fitNumbers.get();
        boolean gloss = glossy.get();
        double dim = spot < 0 ? 0 : spotlightStrength.get() / 100.0;
        int[] fills = new int[colors.length];
        int[] inks = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            fills[i] = argb(colors[i]);
            inks[i] = argb(ink);
        }
        if (gridTexture != null && size == rasterSize && showNumbers == rasterNumbers
            && (!showNumbers || chosenFont == rasterFont && chosenSize == rasterNumberSize && fit == rasterFitNumbers)
            && gloss == rasterGloss && spot == rasterSpotlight && dim == rasterDim
            && Arrays.equals(fills, rasterFills) && Arrays.equals(inks, rasterInks)) return;

        var image = RegionMapRaster.create(size, fills, inks, showNumbers, chosenFont, chosenSize, fit, gloss, spot, dim).image();
        int[] pixels = image.getRGB(0, 0, size, size, null, 0, size);
        byte[] rgba = new byte[size * size * 4];
        for (int i = 0; i < pixels.length; i++) {
            rgba[i * 4] = (byte) (pixels[i] >> 16);
            rgba[i * 4 + 1] = (byte) (pixels[i] >> 8);
            rgba[i * 4 + 2] = (byte) pixels[i];
            rgba[i * 4 + 3] = (byte) (pixels[i] >>> 24);
        }
        Texture replacement = new Texture(size, size, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);
        replacement.upload(rgba);
        if (gridTexture != null) gridTexture.close();
        gridTexture = replacement;
        rasterSize = size;
        rasterNumbers = showNumbers;
        rasterFont = chosenFont;
        rasterNumberSize = chosenSize;
        rasterFitNumbers = fit;
        rasterGloss = gloss;
        rasterSpotlight = spot;
        rasterDim = dim;
        rasterFills = fills;
        rasterInks = inks;
    }

    private static int argb(Color color) {
        return color.a << 24 | color.r << 16 | color.g << 8 | color.b;
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b);
    }

    private static double channel(int value) {
        double v = value / 255.0;

        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    // ------------------------------------------------------------- the marker

    /**
     * A filled arrow, optionally with an outline of even width behind it.
     *
     * <p>A kite, laid out about its own middle so that Marker Size grows it where
     * it stands instead of sliding it off the spot it marks, pointing down the map
     * before it is turned - the game's yaw is already 0 for south, and south is
     * down the map, so there is no angle to work out. The outline is the same kite
     * grown a little, in whichever of black or white reads against the arrow, so
     * the arrow holds up over any of the group colours and over the panel between
     * them.
     */
    private static void kite(double px, double py, double size, double yaw, Color color, boolean outline) {
        // Point, one flank, tail, the other flank. Its middle is the origin, so
        // the point reaches as far forward as the tail does back.
        double[] shapeX = { 0, -0.6, 0, 0.6 };
        double[] shapeY = { 0.85, -0.5, -0.62, -0.5 };

        double[] xs = new double[4];
        double[] ys = new double[4];
        double cos = Math.cos(Math.toRadians(yaw));
        double sin = Math.sin(Math.toRadians(yaw));

        for (int i = 0; i < 4; i++) {
            double a = shapeX[i] * size;
            double b = shapeY[i] * size;

            xs[i] = px + a * cos - b * sin;
            ys[i] = py + a * sin + b * cos;
        }

        if (outline) {
            Color edge = luminance(color) > 0.3 ? new Color(10, 12, 16, color.a) : new Color(255, 255, 255, color.a);

            RoundedBox.polygon(xs, ys, 4, Math.max(0.9, size * 0.17), FEATHER, edge);
        }

        RoundedBox.polygon(xs, ys, 4, 0, FEATHER, color);
    }

    // --------------------------------------------------------------- pointing

    /**
     * What the pointer is on, which is only a question while a screen is open: in
     * play it is held in the middle of the window and is not pointing at anything.
     */
    private void pointer(double left, double top, double unit, double gridTop) {
        hovered = -1;

        if (mc.currentScreen == null || !details.get()) return;

        double across = (mc.mouse.getX() - left - PAD * unit) / (CELL * unit);
        double down = (mc.mouse.getY() - top - gridTop * unit) / (CELL * unit);

        hovered = RegionGrid.cell(across, down);
    }

    /**
     * What a cell is, beside the pointer: its region, the group that serves it in
     * that group's colour, and the blocks it runs between in x and in z. Its own
     * small card over everything else, with a stripe of the group's colour down
     * its side, turned back on itself near an edge of the window so that it is
     * never cut off.
     */
    private void tip(int cell, double unit, Color[] colors, Color ink, Color rim, Color panel) {
        CrispFont.Sized strong = CrispFont.POPPINS_SEMIBOLD.forCaps(4.6 * unit);
        CrispFont.Sized small = CrispFont.POPPINS_SEMIBOLD.forCaps(2.8 * unit);
        CrispFont.Sized body = CrispFont.POPPINS_MEDIUM.forCaps(3.8 * unit);

        RegionGrid.Shard shard = RegionGrid.shard(cell);

        String head = "Region " + RegionGrid.region(cell);
        String group = upper(RegionGrid.servedBy(cell).toString());
        String east = "X  " + RegionGrid.from(shard.col()) + "  to  " + RegionGrid.to(shard.col() + shard.width() - 1);
        String south = "Z  " + RegionGrid.from(shard.row()) + "  to  " + RegionGrid.to(shard.row() + shard.height() - 1);

        double pad = 4 * unit;
        double stripe = 1.2 * unit;
        double inset = pad + stripe + 2 * unit;
        double gap = 2.6 * unit;
        double wide = Math.max(
            Math.max(strong.width(head, 0), small.width(group, TRACKING * unit)),
            Math.max(body.width(east, 0), body.width(south, 0)));

        double width = wide + inset + pad;
        double groupTop = pad + strong.caps() + gap * 0.8;
        double eastTop = groupTop + small.caps() + gap * 1.2;
        double southTop = eastTop + body.caps() + gap * 0.7;
        double height = southTop + body.caps() + pad;

        double px = mc.mouse.getX() + 12 * unit;
        double py = mc.mouse.getY() + 10 * unit;

        if (px + width > mc.getWindow().getFramebufferWidth()) px = mc.mouse.getX() - 12 * unit - width;
        if (py + height > mc.getWindow().getFramebufferHeight()) py = mc.mouse.getY() - 10 * unit - height;

        px = Math.round(Math.max(2, px));
        py = Math.round(Math.max(2, py));

        Color fill = colors[RegionGrid.servedBy(cell).ordinal()];

        Renderer2D.COLOR.begin();

        // Readable whatever the map's own panel is set to: a tip you cannot read
        // through is the point of it.
        RoundedBox.shadow(px + width / 2, py + height / 2 + unit, width + unit, height + unit, 4 * unit, 8 * unit, 0, 0.5);
        RoundedBox.draw(px + width / 2, py + height / 2, width, height, 3 * unit, Math.max(1, RULE * unit), FEATHER, 0,
            new Color(panel.r, panel.g, panel.b, Math.max(panel.a, 242)), rim);
        RoundedBox.draw(px + pad + stripe / 2, py + height / 2, stripe, height - pad * 2, stripe / 2, 0, FEATHER, 0, fill, fill);

        Renderer2D.COLOR.render();

        CrispFont.begin(null);

        strong.draw(head, px + inset, py + pad - strong.capsTop(), ink, 0, false);
        small.draw(group, px + inset, py + groupTop - small.capsTop(), mixed(fill, WHITE, 0.35), TRACKING * unit, false);
        body.draw(east, px + inset, py + eastTop - body.capsTop(), tinted(ink, 0.66), 0, false);
        body.draw(south, px + inset, py + southTop - body.capsTop(), tinted(ink, 0.66), 0, false);

        CrispFont.end();
    }

    private static Color tinted(Color color, double alpha) {
        return new Color(color.r, color.g, color.b, (int) Math.round(color.a * Math.max(0, Math.min(1, alpha))));
    }

    /** {@code from} blended towards {@code to} by {@code amount}, keeping {@code from}'s alpha. */
    private static Color mixed(Color from, Color to, double amount) {
        return new Color(
            (int) Math.round(from.r + (to.r - from.r) * amount),
            (int) Math.round(from.g + (to.g - from.g) * amount),
            (int) Math.round(from.b + (to.b - from.b) * amount),
            from.a);
    }
}
