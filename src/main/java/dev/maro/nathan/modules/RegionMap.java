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
 * <p>The 36 by 36 source grid is drawn as differently sized numbered shards.
 * This class keeps the addon's slim header, footer, palette, hover details,
 * directional marker and placement screen. Its map occupies the same panel width
 * as the former 9 by 9 version, and Scale still grows every part together.
 *
 * <p>The cells and all 201 antialiased labels share one cached texture, rebuilt
 * when scale, colours or label settings change. Labels can fit their shards and
 * all use the same Text Colour setting.
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
    private static final double HEADER = 7;
    private static final double FOOTER = 7;
    private static final double BAND_GAP = 4;

    /** The legend: two columns, a row this tall, and these gaps inside it. */
    private static final int LEGEND_COLUMNS = 2;
    private static final double LEGEND_ROW = 8;
    private static final double COLUMN_GAP = 10;
    private static final double SWATCH_GAP = 3;
    private static final double SWATCH = 5.5;

    private static final double PANEL_RADIUS = 3;
    private static final double CELL_RADIUS = RegionMapRaster.CORNER_UNITS;
    private static final double RULE = 0.75;

    /** Cap heights for the panel lettering. */
    private static final double TITLE_CAPS = 4;
    private static final double LINE_CAPS = 4.4;
    private static final double SMALL_CAPS = 4.2;

    /** Letter spacing for the one piece of text set in capitals. */
    private static final double TITLE_TRACKING = 0.9;

    /** Width of the fading skin round each shape, in real pixels: as everywhere else here. */
    private static final double FEATHER = 1.5;

    /** How far Scale goes either way. The setting and the wheel are held to the same pair. */
    private static final double SCALE_MIN = 0.4;
    private static final double SCALE_MAX = 4;

    /** The arrow at a Marker Size of 1, from its middle to its point. */
    private static final double MARKER = 4.6;

    /** Seconds for the marker's heading to catch up with your own. */
    private static final double TURN = 0.07;

    /** Original six muted colours, plus one for the new Europe shards. */
    private static final int[] MUTED = { 0x3F67B2, 0xC45860, 0x3C7B13, 0xB88B1F, 0xA37CDF, 0x069A9D, 0xB065A8 };

    /** Original six signal colours, plus one for the new Europe shards. */
    private static final int[] SIGNAL = { 0x008BC4, 0xAA4A0F, 0x928905, 0xC24E90, 0x684DD4, 0x007555, 0xC063AF };

    public enum Palette {
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
        .description("The slim line of a name over the map.")
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
        .description("The compact line under the map: the region you are standing in, and your x and z.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> legend = sgGeneral.add(new BoolSetting.Builder()
        .name("legend")
        .description("The seven groups named, in two columns centred under the map.")
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

    private final Setting<Integer> backgroundOpacity = sgLook.add(new IntSetting.Builder()
        .name("background-opacity")
        .description("How solid the panel is, as a percentage. The panel only: the cells, the rim and the lettering keep their own.")
        .defaultValue(80)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgLook.add(new ColorSetting.Builder()
        .name("background-color")
        .description("The panel behind the map. How solid it is is the setting over this one.")
        .defaultValue(new SettingColor(21, 23, 29))
        .build()
    );

    private final Setting<SettingColor> borderColor = sgLook.add(new ColorSetting.Builder()
        .name("border-color")
        .description("The rim round the panel, and the two hairlines inside it.")
        .defaultValue(new SettingColor(104, 114, 138, 150))
        .build()
    );

    private final Setting<SettingColor> accentColor = sgLook.add(new ColorSetting.Builder()
        .name("accent-color")
        .description("The thin rim round the cell you are standing in.")
        .defaultValue(new SettingColor(238, 244, 255, 225))
        .build()
    );

    private final Setting<SettingColor> markerColor = sgLook.add(new ColorSetting.Builder()
        .name("marker-color")
        .description("The arrow. Its outline is worked out from it, dark on a light arrow and light on a dark one.")
        .defaultValue(new SettingColor(245, 248, 255))
        .visible(marker::get)
        .build()
    );

    private final Setting<SettingColor> textColor = sgLook.add(new ColorSetting.Builder()
        .name("text-color")
        .description("One color for all region numbers and panel lettering. The name, legend and bounds use lower opacity.")
        .defaultValue(new SettingColor(233, 236, 244))
        .build()
    );

    private final Setting<Boolean> shadow = sgLook.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Shadow under the lettering on the panel. Cell numbers do not use a shadow.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Palette> palette = sgColors.add(new EnumSetting.Builder<Palette>()
        .name("palette")
        .description("Muted and Signal retain the original six colours and add Europe. Custom lets you change all seven.")
        .defaultValue(Palette.Muted)
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
    private int[] rasterFills;
    private int[] rasterInks;

    public RegionMap() {
        super(NameeProtectAddon.CATEGORY, "region-map", "The server's regions as a map on screen, with where you are on it.");
    }

    private ColorSetting.Builder group(String name, RegionGrid.Locale of) {
        int rgb = MUTED[of.ordinal()];

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

        int[] rgb = palette.get() == Palette.Signal ? SIGNAL : MUTED;
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

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null || mc.options == null || mc.options.hudHidden) return;
        if (!CrispFont.POPPINS_MEDIUM.ready() || !CrispFont.POPPINS_SEMIBOLD.ready()) return;

        // Measured from the clock and capped, so the marker turns at the same rate
        // at 40 frames a second as at 400.
        long nanos = System.nanoTime();
        double seconds = lastFrameAt == 0 ? 0 : Math.min((nanos - lastFrameAt) / 1.0e9, 0.05);
        lastFrameAt = nanos;

        double gui = mc.getWindow().getScaleFactor();
        double unit = gui * scale.get();
        double left = Math.round(x.get() * gui);
        double top = Math.round(y.get() * gui);

        CrispFont.Sized small = CrispFont.POPPINS_MEDIUM.forCaps(SMALL_CAPS * unit);

        double worldX = mc.player.getX();
        double worldZ = mc.player.getZ();
        double column = RegionGrid.column(worldX);
        double row = RegionGrid.row(worldZ);
        int here = RegionGrid.cell(column, row);

        // The two halves of the footer, and one size that fits them both side by
        // side with a gap between - fitted, so a long coordinate cannot push the
        // region off the end of the line.
        String said = here < 0 ? "off the map" : RegionGrid.region(here) + "  ·  " + RegionGrid.servedBy(here);
        String at = "X  " + (long) Math.floor(worldX) + "     Z  " + (long) Math.floor(worldZ);
        CrispFont.Sized line = fitted(said + "      " + at, LINE_CAPS * unit, (WIDTH - PAD * 2) * unit);

        // The bands, down the panel in units. The legend is measured from its own
        // longest name, so it is as wide as what is in it and no wider.
        int groups = RegionGrid.Locale.values().length;
        double entry = SWATCH + SWATCH_GAP + widestName(small, unit);
        int columns = LEGEND_COLUMNS;
        double block = columns * entry + (columns - 1) * COLUMN_GAP;

        if (block > GRID) {
            columns = 1;
            block = entry;
        }

        boolean showLegend = legend.get() && block <= GRID;
        int legendRows = showLegend ? (groups + columns - 1) / columns : 0;

        double gridTop = PAD + (header.get() ? HEADER + BAND_GAP : 0);
        double under = gridTop + GRID;
        double footTop = under + BAND_GAP;
        double legendTop = footTop + (footer.get() ? FOOTER + BAND_GAP : 0);
        double room = (WIDTH - PAD * 2) * unit;

        // What the footer can hold at this size: both halves with air between
        // them, the region on its own, or - smaller than that - neither.
        boolean bothHalves = footer.get() && line.width(said, 0) + line.width(at, 0) + 4 * unit <= room;
        boolean regionOnly = footer.get() && !bothHalves && line.width(said, 0) <= room;

        boolean showNumbers = numbers.get();

        panelUnits = legendTop + legendRows * LEGEND_ROW + PAD;

        // The whole block centred under the map, and every column the same width,
        // so the swatches line up down the legend and the legend sits on the
        // panel's own middle.
        double blockLeft = Math.max(PAD, (WIDTH - block) / 2);

        pointer(left, top, unit, gridTop);

        double panelWidth = WIDTH * unit;
        double panelHeight = panelUnits * unit;

        Color[] colors = colors();
        Color ink = textColor.get();
        Color rim = borderColor.get();
        Color base = backgroundColor.get();
        Color panel = new Color(base.r, base.g, base.b, (int) Math.round(255 * backgroundOpacity.get() / 100.0));

        updateGridTexture(unit, colors, ink, showNumbers);

        // ---- the shapes

        Renderer2D.COLOR.begin();

        RoundedBox.draw(left + panelWidth / 2, top + panelHeight / 2, panelWidth, panelHeight,
            PANEL_RADIUS * unit, Math.max(1, RULE * unit), FEATHER, 0, panel, rim);

        if (header.get()) hairline(left, top, unit, gridTop - BAND_GAP / 2, rim);
        if (footer.get() || showLegend) hairline(left, top, unit, under + BAND_GAP / 2, rim);

        Renderer2D.COLOR.render();

        Renderer2D.TEXTURE.begin();
        // The artwork already has antialiased edges. Match texels to physical
        // pixels at an integer origin instead of resampling it a second time.
        Renderer2D.TEXTURE.texQuad(Math.round(left + PAD * unit), Math.round(top + gridTop * unit), rasterSize, rasterSize,
            new Color(255, 255, 255));
        Renderer2D.TEXTURE.render(gridTexture.getGlTextureView(), gridTexture.getSampler());

        Renderer2D.COLOR.begin();

        // The cell you are in, then the one under the pointer: a rim and not a
        // wash, so the group's own colour still reads through it.
        if (here >= 0) {
            RegionGrid.Shard shard = RegionGrid.shard(here);
            double[] middle = cellMiddle(left, top, unit, gridTop, here);

            RoundedBox.drawCompact(middle[0], middle[1], shardWidth(shard, unit), shardHeight(shard, unit),
                CELL_RADIUS * unit, Math.max(1, 0.85 * unit), FEATHER, 0,
                CLEAR, accentColor.get());
        }

        if (hovered >= 0 && hovered != here) {
            RegionGrid.Shard shard = RegionGrid.shard(hovered);
            double[] middle = cellMiddle(left, top, unit, gridTop, hovered);

            RoundedBox.drawCompact(middle[0], middle[1], shardWidth(shard, unit), shardHeight(shard, unit),
                CELL_RADIUS * unit, Math.max(1, 0.7 * unit), FEATHER, 0,
                CLEAR, tinted(ink, 0.6));
        }

        if (showLegend) {
            for (int i = 0; i < groups; i++) {
                double[] spot = entryAt(left, top, unit, legendTop, blockLeft, entry, columns, i);

                RoundedBox.draw(spot[0] + SWATCH * unit / 2, spot[1] + SWATCH * unit / 2,
                    SWATCH * unit, SWATCH * unit, 1.25 * unit, 0, FEATHER, 0, colors[i], colors[i]);
            }
        }

        if (marker.get() && here >= 0) {
            double want = mc.player.getYaw();

            if (!turning) {
                shownYaw = want;
                turning = true;
            } else {
                // The short way round, so 359 to 1 is two degrees and not 358.
                double turn = ((want - shownYaw) % 360 + 540) % 360 - 180;

                shownYaw += turn * Math.min(1, seconds / TURN);
            }

            arrow(left + (PAD + column * CELL) * unit, top + (gridTop + row * CELL) * unit,
                MARKER * markerSize.get() * unit, shownYaw, markerColor.get());
        }

        Renderer2D.COLOR.render();

        // ---- the lettering

        CrispFont.begin(null);

        if (header.get()) {
            CrispFont.Sized title = CrispFont.POPPINS_SEMIBOLD.forCaps(TITLE_CAPS * unit);
            title.draw("REGION MAP", left + PAD * unit,
                top + (PAD + (HEADER - TITLE_CAPS) / 2) * unit - title.capsTop(),
                tinted(ink, 0.5), TITLE_TRACKING * unit, shadow.get());
        }

        if (bothHalves || regionOnly) {
            double capsTop = top + footTop * unit + (FOOTER * unit - line.caps()) / 2 - line.capsTop();

            line.draw(said, left + PAD * unit, capsTop, here < 0 ? tinted(ink, 0.5) : ink, 0, shadow.get());

            if (bothHalves) {
                line.draw(at, left + (WIDTH - PAD) * unit - line.width(at, 0), capsTop, tinted(ink, 0.78), 0, shadow.get());
            }
        }

        if (showLegend) {
            for (int i = 0; i < groups; i++) {
                double[] spot = entryAt(left, top, unit, legendTop, blockLeft, entry, columns, i);

                // On the swatch's own middle, so a swatch and its name sit level
                // however the two are sized.
                small.draw(RegionGrid.Locale.values()[i].toString(),
                    spot[0] + (SWATCH + SWATCH_GAP) * unit,
                    spot[1] + (SWATCH * unit - small.caps()) / 2 - small.capsTop(),
                    tinted(ink, 0.72), 0, shadow.get());
            }
        }

        CrispFont.end();

        if (hovered >= 0 && details.get()) tip(hovered, unit, colors, ink, rim, panel);
    }

    /** Nothing at all: the fill of a shape that is only a rim. */
    private static final Color CLEAR = new Color(0, 0, 0, 0);

    /** One hairline across the panel, inset from its rim, this many units down it. */
    private void hairline(double left, double top, double unit, double down, Color color) {
        double thick = Math.max(1, RULE * unit);

        RoundedBox.draw(left + WIDTH * unit / 2, top + down * unit, (WIDTH - PAD * 1.5) * unit, thick, thick / 2, 0, 1, 0,
            tinted(color, 0.5), tinted(color, 0.5));
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

    /** The top left of one legend entry's swatch, in real pixels. */
    private double[] entryAt(double left, double top, double unit, double legendTop, double blockLeft, double entry,
                             int columns, int i) {
        int column = i % columns;
        int row = i / columns;

        return new double[]{
            left + (blockLeft + column * (entry + COLUMN_GAP)) * unit,
            top + (legendTop + row * LEGEND_ROW + (LEGEND_ROW - SWATCH) / 2) * unit};
    }

    /** The longest group name, in units, so every legend column is that wide. */
    private double widestName(CrispFont.Sized font, double unit) {
        double widest = 0;

        for (RegionGrid.Locale of : RegionGrid.Locale.values()) widest = Math.max(widest, font.width(of.toString(), 0) / unit);

        return widest;
    }

    /**
     * The size a line is drawn at: the cap height it would like, held to the width
     * it has to fit in. The font rasterises at whatever whole height it is asked
     * for, so the height that fits is asked for rather than stepped down to.
     *
     * <p>There is a floor under it - no font is made smaller than six pixels,
     * because under that there is nothing to read - so at a small enough Scale a
     * line cannot be made to fit however it is sized. What cannot fit is not drawn:
     * the footer falls back to the region alone and the legend goes from two
     * columns to one and then away. The map itself,
     * which is the part that still says something at that size, stays.
     */
    private static CrispFont.Sized fitted(String text, double caps, double room) {
        CrispFont font = CrispFont.POPPINS_MEDIUM;

        return font.at((int) Math.min(Math.round(caps / font.capsRatio()), Math.floor(room / font.unitWidth(text))));
    }

    private void updateGridTexture(double unit, Color[] colors, Color ink, boolean showNumbers) {
        int size = Math.max(1, Math.min(4096, (int) Math.round(GRID * unit)));
        RegionMapRaster.NumberFont chosenFont = numberFont.get();
        double chosenSize = numberSize.get();
        boolean fit = fitNumbers.get();
        int[] fills = new int[colors.length];
        int[] inks = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            fills[i] = argb(colors[i]);
            inks[i] = argb(ink);
        }
        if (gridTexture != null && size == rasterSize && showNumbers == rasterNumbers
            && (!showNumbers || chosenFont == rasterFont && chosenSize == rasterNumberSize && fit == rasterFitNumbers)
            && Arrays.equals(fills, rasterFills) && Arrays.equals(inks, rasterInks)) return;

        var image = RegionMapRaster.create(size, fills, inks, showNumbers, chosenFont, chosenSize, fit).image();
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
     * The marker: a filled arrow with an outline of even width behind it.
     *
     * <p>A kite, laid out about its own middle so that Marker Size grows it where
     * it stands instead of sliding it off the spot it marks, pointing down the map
     * before it is turned - the game's yaw is already 0 for south, and south is
     * down the map, so there is no angle to work out. The outline is the same kite
     * grown a little, in whichever of black or white reads against the arrow, so
     * the arrow holds up over any of the group colours and over the panel between
     * them.
     */
    private void arrow(double px, double py, double size, double yaw, Color color) {
        // Point, one flank, tail, the other flank. Its middle is the origin, so
        // the point reaches as far forward as the tail does back.
        double[] shapeX = { 0, -0.55, 0, 0.55 };
        double[] shapeY = { 0.81, -0.49, -0.81, -0.49 };

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

        Color edge = luminance(color) > 0.3 ? new Color(10, 12, 16, color.a) : new Color(255, 255, 255, color.a);

        RoundedBox.polygon(xs, ys, 4, Math.max(0.9, size * 0.17), FEATHER, edge);
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
     * What a cell is, beside the pointer: its region, the group that serves it with
     * that group's colour beside the name, and the blocks it runs between in x and
     * in z. Its own small panel over everything else, turned back on itself near an
     * edge of the window so that it is never cut off.
     */
    private void tip(int cell, double unit, Color[] colors, Color ink, Color rim, Color panel) {
        CrispFont.Sized strong = CrispFont.POPPINS_SEMIBOLD.forCaps(4.6 * unit);
        CrispFont.Sized body = CrispFont.POPPINS_MEDIUM.forCaps(4.2 * unit);

        RegionGrid.Shard shard = RegionGrid.shard(cell);

        String head = "Region " + RegionGrid.region(cell);
        String group = RegionGrid.servedBy(cell).toString();
        String east = "X  " + RegionGrid.from(shard.col()) + "  to  " + RegionGrid.to(shard.col() + shard.width() - 1);
        String south = "Z  " + RegionGrid.from(shard.row()) + "  to  " + RegionGrid.to(shard.row() + shard.height() - 1);

        double pad = 4 * unit;
        double gap = 2.6 * unit;
        double swatch = 4 * unit;
        double wide = Math.max(
            Math.max(strong.width(head, 0), swatch + gap * 0.7 + body.width(group, 0)),
            Math.max(body.width(east, 0), body.width(south, 0)));

        double width = wide + pad * 2;
        double groupTop = pad + strong.caps() + gap;
        double eastTop = groupTop + body.caps() + gap;
        double southTop = eastTop + body.caps() + gap * 0.7;
        double height = southTop + body.caps() + pad;

        double px = mc.mouse.getX() + 12 * unit;
        double py = mc.mouse.getY() + 10 * unit;

        if (px + width > mc.getWindow().getFramebufferWidth()) px = mc.mouse.getX() - 12 * unit - width;
        if (py + height > mc.getWindow().getFramebufferHeight()) py = mc.mouse.getY() - 10 * unit - height;

        px = Math.round(Math.max(2, px));
        py = Math.round(Math.max(2, py));

        Renderer2D.COLOR.begin();

        // Readable whatever the map's own panel is set to: a tip you cannot read
        // through is the point of it.
        RoundedBox.draw(px + width / 2, py + height / 2, width, height, 2.5 * unit, Math.max(1, RULE * unit), FEATHER, 0,
            new Color(panel.r, panel.g, panel.b, Math.max(panel.a, 238)), rim);

        Color fill = colors[RegionGrid.servedBy(cell).ordinal()];

        RoundedBox.draw(px + pad + swatch / 2, py + groupTop + (body.caps() - swatch) / 2 + swatch / 2,
            swatch, swatch, unit, 0, FEATHER, 0, fill, fill);

        Renderer2D.COLOR.render();

        CrispFont.begin(null);

        strong.draw(head, px + pad, py + pad - strong.capsTop(), ink, 0, false);
        body.draw(group, px + pad + swatch + gap * 0.7, py + groupTop - body.capsTop(), tinted(ink, 0.85), 0, false);
        body.draw(east, px + pad, py + eastTop - body.capsTop(), tinted(ink, 0.62), 0, false);
        body.draw(south, px + pad, py + southTop - body.capsTop(), tinted(ink, 0.62), 0, false);

        CrispFont.end();
    }

    private static Color tinted(Color color, double alpha) {
        return new Color(color.r, color.g, color.b, (int) Math.round(color.a * Math.max(0, Math.min(1, alpha))));
    }
}
