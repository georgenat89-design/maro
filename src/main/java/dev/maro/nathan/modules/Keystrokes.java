package dev.maro.nathan.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.maro.runtime.events.render.Render2DEvent;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.ColorSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.hud.HudRenderer;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.runtime.utils.render.color.SettingColor;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.render.CrispFont;
import dev.maro.nathan.render.RoundedBox;

/**
 * Draws the movement keys on screen and lights each one while it is held, with
 * the mouse buttons and the space bar underneath if you want them.
 *
 * <p>It reads the game's key bindings rather than the keyboard, so it follows
 * whatever you have movement bound to and labels each key with the name of the
 * key that is actually on it. It also means a key held <em>for</em> you lights
 * up too - Auto Walk's forward key shows as pressed, because as far as the game
 * is concerned it is.
 *
 * <p>A held key looks pressed in. Its face shrinks a few percent about its own
 * middle and sinks a pixel or two, the shadow under it pulls in, its colours
 * ease to the highlight, and a thin line of the highlight leaves its edge and
 * spreads into the gap round it, like pressure going out along the rim. Only
 * the face moves: the space a key takes up, and so every other key and the size
 * of the whole thing, stays exactly where it is. What is read from the game is
 * whether the key is down, which is immediate; only the look takes its time.
 *
 * <p>A mouse button says which button it is until you click it, and then says
 * how fast: the count takes the name's place on the button and hands it back
 * once the clicks stop. Each button counts its own, and one text gives way to
 * the other in turn, so the two are never on the button together.
 *
 * <p>The boxes go through Meteor's HUD renderer. Labels use the bundled Poppins
 * font, rasterised at their actual size in screen pixels.
 */
public class Keystrokes extends Module {
    /** Seconds for a key to reach its pressed colours, and to leave them, at an Animation Speed of 1. */
    private static final double FADE_IN = 0.08;
    private static final double FADE_OUT = 0.15;

    /** Seconds for the ripple a press sends out along a key's edge to spread and fade. */
    private static final double RIPPLE = 0.28;

    /**
     * Seconds for a mouse button to trade its name for its count, or its count
     * back for its name, at an Animation Speed of 1. The one leaves before the
     * other arrives, so each of them has half of it.
     */
    private static final double SWAP = 0.16;

    /** How much smaller a held key's face is, and how far it sinks as a fraction of the key size. */
    private static final double SQUEEZE = 0.03;
    private static final double SINK = 0.04;

    /** Which set of default colours the saved settings were made under. */
    private static final int LOOK = 2;

    /**
     * Width of the fading skin round each box, in pixels. About a pixel and a
     * half: less and the staircase shows through, more and the box looks blurred
     * rather than smooth.
     */
    private static final double FEATHER = 1.5;

    /** As wide as the counter gets in practice, so its size never depends on the count. */
    private static final String COUNTER_STAND_IN = "00 CPS";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Look");

    private final Setting<Boolean> mouse = sgGeneral.add(new BoolSetting.Builder()
        .name("mouse")
        .description("Show the left and right mouse buttons under the keys.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cps = sgGeneral.add(new BoolSetting.Builder()
        .name("cps")
        .description("While you are clicking a mouse button, show how fast in place of its name.")
        .defaultValue(true)
        .visible(mouse::get)
        .build()
    );

    private final Setting<Boolean> spaceBar = sgGeneral.add(new BoolSetting.Builder()
        .name("space-bar")
        .description("Show the jump key along the bottom.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> x = sgGeneral.add(new IntSetting.Builder()
        .name("x")
        .description("Distance from the left of the window, in pixels.")
        .defaultValue(410)
        .min(0)
        .sliderRange(0, 1920)
        .build()
    );

    private final Setting<Integer> y = sgGeneral.add(new IntSetting.Builder()
        .name("y")
        .description("Distance from the top of the window, in pixels.")
        .defaultValue(170)
        .min(0)
        .sliderRange(0, 1080)
        .build()
    );

    private final Setting<Integer> keySize = sgGeneral.add(new IntSetting.Builder()
        .name("key-size")
        .description("Width and height of one key, in pixels. Everything else scales from it.")
        .defaultValue(44)
        .range(24, 50)
        .sliderRange(24, 50)
        .build()
    );

    private final Setting<Boolean> shadow = sgLook.add(new BoolSetting.Builder()
        .name("text-shadow")
        .description("Shadow under the labels. Worth having with no background, where the letters sit straight on the world.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> animation = sgLook.add(new BoolSetting.Builder()
        .name("animation")
        .description("Ease into the pressed look and back out of it, rather than flipping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> animationSpeed = sgLook.add(new DoubleSetting.Builder()
        .name("animation-speed")
        .description("How quick a press is. At 1 a key goes down in 80 ms and comes back up in 150.")
        .defaultValue(1)
        .min(0.25)
        .max(4)
        .sliderRange(0.25, 3)
        .visible(animation::get)
        .build()
    );

    private final Setting<SettingColor> highlight = sgLook.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("The theme a held key takes on: a soft wash of it behind the key, a rim of it, and a faint glow of it inside.")
        .defaultValue(new SettingColor(255, 176, 64))
        .build()
    );

    private final Setting<Boolean> pressEffect = sgLook.add(new BoolSetting.Builder()
        .name("press-effect")
        .description("A held key's face shrinks a little and sinks, its shadow pulls in, and a thin ripple of the highlight leaves its edge. Off, a press is a change of colour only.")
        .defaultValue(true)
        .visible(() -> this.background.get())
        .build()
    );

    private final Setting<Boolean> glow = sgLook.add(new BoolSetting.Builder()
        .name("pressed-glow")
        .description("A faint glow of the highlight colour round the inside of a held key.")
        .defaultValue(true)
        .visible(() -> this.background.get())
        .build()
    );

    private final Setting<Boolean> customPressed = sgLook.add(new BoolSetting.Builder()
        .name("custom-pressed-colors")
        .description("Choose a held key's background, rim and text yourself instead of taking them from the highlight colour.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> background = sgLook.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a box behind each key. Off leaves only the letters, which change colour when pressed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> roundness = sgLook.add(new IntSetting.Builder()
        .name("roundness")
        .description("How round the corners are. 0 is square, 100 is as round as a key allows. Every box gets the same corner, and the space bar is always a pill.")
        .defaultValue(45)
        .range(0, 100)
        .sliderRange(0, 100)
        .visible(background::get)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgLook.add(new ColorSetting.Builder()
        .name("background-color")
        .description("The box behind a key that is not held.")
        .defaultValue(new SettingColor(28, 29, 34, 225))
        .visible(background::get)
        .build()
    );

    private final Setting<SettingColor> pressedBackgroundColor = sgLook.add(new ColorSetting.Builder()
        .name("pressed-background-color")
        .description("The box behind a key that is held.")
        .defaultValue(new SettingColor(70, 73, 84, 235))
        .visible(() -> background.get() && customPressed.get())
        .build()
    );

    private final Setting<Boolean> border = sgLook.add(new BoolSetting.Builder()
        .name("border")
        .description("A thin rim round each box. It is what keeps a dark box readable over a dark part of the world, and a pale one over the sky.")
        .defaultValue(true)
        .visible(background::get)
        .build()
    );

    private final Setting<SettingColor> borderColor = sgLook.add(new ColorSetting.Builder()
        .name("border-color")
        .description("The rim round each box.")
        .defaultValue(new SettingColor(122, 126, 138, 150))
        .visible(() -> background.get() && border.get())
        .build()
    );

    private final Setting<SettingColor> pressedBorderColor = sgLook.add(new ColorSetting.Builder()
        .name("pressed-border-color")
        .description("The rim round a key that is held.")
        .defaultValue(new SettingColor(214, 218, 228, 230))
        .visible(() -> background.get() && border.get() && customPressed.get())
        .build()
    );

    private final Setting<SettingColor> textColor = sgLook.add(new ColorSetting.Builder()
        .name("text-color")
        .description("The label on a key that is not held.")
        .defaultValue(new SettingColor(226, 228, 234))
        .build()
    );

    private final Setting<SettingColor> pressedTextColor = sgLook.add(new ColorSetting.Builder()
        .name("pressed-text-color")
        .description("The label on a key that is held. With no background, this is the only thing that shows a press.")
        .defaultValue(new SettingColor(255, 255, 255))
        .visible(customPressed::get)
        .build()
    );

    // Never shown. It records which defaults the saved colours were chosen
    // against, so that a look saved under the old defaults - a white flash on a
    // see-through box - is replaced once by the new one and never again.
    private final Setting<Integer> lookVersion = sgLook.add(new IntSetting.Builder()
        .name("look-version")
        .description("Which set of default colours these settings were saved under.")
        .defaultValue(0)
        .visible(() -> false)
        .build()
    );

    /** One fade per key, by where it sits rather than what is bound there. */
    private final Map<String, Fade> fades = new HashMap<>();

    /** One swap per counting button: how far it has gone from its name to its count. */
    private final Map<String, Swap> swaps = new HashMap<>();

    private long lastFrameAt;
    private double frameSeconds;

    /** Text waiting to be drawn once every box is down, since it has to go on top of all of them. */
    private final List<Line> lines = new ArrayList<>();


    private final Clicks leftClicks = new Clicks();
    private final Clicks rightClicks = new Clicks();

    public Keystrokes() {
        super(NameeProtectAddon.CATEGORY, "keystrokes", "Shows your movement keys and mouse buttons on screen as you press them.");

        // Set, not defaulted, so that it differs from its default and is written
        // out with the rest: its being there is what is looked for.
        lookVersion.set(LOOK);
    }

    /**
     * Settings saved before the keys went charcoal are moved to the new look,
     * once. Only the colours and the two things that go with them: sizes,
     * position, font and what is shown are left exactly as they were.
     */
    @Override
    public Module fromTag(NbtCompound tag) {
        Module loaded = super.fromTag(tag);

        if (!saved(tag, "look-version")) {
            for (Setting<?> setting : List.of(shadow, roundness, backgroundColor, pressedBackgroundColor,
                borderColor, pressedBorderColor, textColor, pressedTextColor)) {
                setting.reset();
            }
        }

        lookVersion.set(LOOK);
        return loaded;
    }

    private static boolean saved(NbtCompound module, String name) {
        for (NbtElement groupTag : module.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(groupTag instanceof NbtCompound group)) continue;

            for (NbtElement settingTag : group.getListOrEmpty("settings")) {
                if (settingTag instanceof NbtCompound setting && name.equals(setting.getString("name", ""))) return true;
            }
        }

        return false;
    }

    @Override
    public void onActivate() {
        leftClicks.clear();
        rightClicks.clear();
        fades.clear();
        swaps.clear();
        lastFrameAt = 0;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.options == null || mc.options.hudHidden) return;

        // Measured here rather than taken from the event, and capped, so that a
        // fade is timed by the clock and takes as long at 40 frames a second as
        // it does at 400.
        long nanos = System.nanoTime();
        frameSeconds = lastFrameAt == 0 ? 0 : Math.min((nanos - lastFrameAt) / 1.0e9, 0.05);
        lastFrameAt = nanos;

        long now = System.currentTimeMillis();
        leftClicks.update(mc.options.attackKey.isPressed(), now);
        rightClicks.update(mc.options.useKey.isPressed(), now);

        double size = keySize.get();
        double gap = Math.max(2, Math.round(size / 11));
        double left = x.get();
        double top = y.get();
        double width = size * 3 + gap * 2;

        HudRenderer renderer = HudRenderer.INSTANCE;
        renderer.begin(event.drawContext);
        lines.clear();

        key("up", mc.options.forwardKey, left + size + gap, top, size, size);

        top += size + gap;
        key("left", mc.options.leftKey, left, top, size, size);
        key("down", mc.options.backKey, left + size + gap, top, size, size);
        key("right", mc.options.rightKey, left + (size + gap) * 2, top, size, size);

        if (mouse.get()) {
            top += size + gap;
            double half = (width - gap) / 2;

            // Wider than they are tall, and the same height whether or not they
            // count: a count takes the place of its button's name rather than a
            // line of its own, so there is only ever one line to make room for.
            double tall = Math.round(size * 0.7);

            box("lmb", "LMB", cps.get() ? leftClicks.perSecond() : -1, mc.options.attackKey.isPressed(), left, top, half, tall, false);
            box("rmb", "RMB", cps.get() ? rightClicks.perSecond() : -1, mc.options.useKey.isPressed(), left + half + gap, top, half, tall, false);

            top += tall - size;
        }

        if (spaceBar.get()) {
            top += size + gap;
            box("jump", "", -1, mc.options.jumpKey.isPressed(), left, top, width, Math.round(size * 0.42), true);
        }

        renderer.end();

        // Through the game's own interface renderer, which draws what it is
        // handed after everything drawn directly: on top of the boxes.
        drawLabels();
    }

    private void key(String id, KeyBinding mapping, double left, double top, double width, double height) {
        box(id, label(mapping), -1, mapping.isPressed(), left, top, width, height, false);
    }

    /**
     * One key: the box if there is one and its label - or, on a button that
     * counts clicks, its count in the label's place while they are coming. A
     * {@code bar} is the space bar: a pill, with a short line where a label
     * would be.
     */
    private void box(String id, String label, int count, boolean down,
                     double left, double top, double width, double height, boolean bar) {
        Fade fade = fades.computeIfAbsent(id, key -> new Fade());
        double mix = animation.get() ? fade.step(down, frameSeconds, animationSpeed.get()) : fade.snap(down);

        double size = keySize.get();

        // Whole pixels, so every box's edges fall the same way on the grid and
        // no two keys are softened differently.
        left = Math.round(left);
        top = Math.round(top);
        width = Math.round(width);

        // Where the face is within the key's own, fixed, space. It shrinks about
        // its middle and sinks; the space does not change, so nothing else moves.
        boolean pressing = pressEffect.get() && background.get();
        double squeeze = pressing ? 1 - SQUEEZE * mix : 1;
        double sink = pressing ? Math.max(1, size * SINK) * mix : 0;
        double faceX = left + width / 2;
        double faceY = top + height / 2 + sink;
        double faceWidth = width * squeeze;
        double faceHeight = height * squeeze;

        if (background.get()) {
            // One corner for every box, taken from the key size and not from the
            // box, so a mouse button's corners match a movement key's.
            double radius = (bar ? height / 2 : size / 2 * roundness.get() / 100.0) * squeeze;
            double rim = border.get() ? Math.max(1, size / 32.0) : 0;
            double gutter = Math.max(2, Math.round(size / 11));

            if (pressing) {
                // The shadow is what says how far off the surface a key stands:
                // long under one that is up, nearly gone under one that is down.
                // It stays inside the gap, so it never lies across the key below.
                double stands = 1 - 0.7 * mix;

                RoundedBox.shadow(left + width / 2, top + height / 2 + gutter * 0.45 * stands + sink * 0.5, faceWidth, faceHeight,
                    radius, gutter * 0.75, 0, 0.30 * stands * backgroundColor.get().a / 255.0);
            }

            RoundedBox.draw(faceX, faceY, faceWidth, faceHeight, radius, rim, FEATHER, 0,
                blend(backgroundColor.get(), heldBackground(), mix),
                blend(borderColor.get(), heldBorder(), mix));

            // Faint, inside the rim, and gone a fifth of the way in: enough to
            // warm the key, not enough to read as a light behind it.
            if (glow.get() && mix > 0.01) {
                Color lit = highlight.get();

                RoundedBox.innerGlow(faceX, faceY, faceWidth, faceHeight, radius, FEATHER, rim + 1,
                    Math.min(width, height) * 0.22, 0, new Color(lit.r, lit.g, lit.b, (int) Math.round(62 * mix)));
            }

            if (pressing) {
                ripple(fade.ripple, faceX, faceY, faceWidth, faceHeight, radius, gutter);
                ripple(fade.earlier, faceX, faceY, faceWidth, faceHeight, radius, gutter);
            }
        }

        // The label rides on the face. It is moved and not shrunk: three percent
        // is nothing to a letter, and a size that changed every frame would be a
        // font made afresh every frame.
        top += sink;

        Color color = blend(textColor.get(), heldText(), mix);

        if (bar) {
            // The mark on a space bar: a short line, round at the ends, in the
            // middle of the pill. Drawn after the pill, so on top of it.
            double markWidth = Math.max(12, Math.round(width * 0.2));
            double markHeight = Math.max(2, Math.round(size / 16));

            RoundedBox.draw(left + width / 2, top + height / 2, markWidth + 1, markHeight + 1, markHeight, 0, 1, 0, color, color);
            return;
        }

        if (!CrispFont.POPPINS_SEMIBOLD.ready() || !CrispFont.POPPINS_MEDIUM.ready()) return;

        CrispFont.Sized title = fit(CrispFont.POPPINS_SEMIBOLD, label, height * 0.40, width * 0.8);
        label = trim(label, title, width * 0.8);

        double middle = left + width / 2;

        if (count < 0) {
            lines.add(new Line(label, middle, capsTop(top, height, title), color, title));
            return;
        }

        // A counting button says its name until you click it and how fast once
        // you have, in the one place: the middle of the button, which is where
        // both of them are centred. The name is gone before the count arrives
        // and the count before the name comes back, so the two are never on the
        // button together; what changes on the way is how much of the text there
        // is, and never where it sits or how big it is.
        Swap swap = swaps.computeIfAbsent(id, key -> new Swap());
        double shown = animation.get() ? swap.step(count > 0, frameSeconds, animationSpeed.get()) : swap.snap(count > 0);

        // The counter is fitted to a stand-in, never to the number it is
        // showing: fitted to the live text, a change of digit could change its
        // size, and as the count ran down it would flicker between sizes. The
        // name's size is as far up as it goes, so a button never says its count
        // larger than its name.
        CrispFont.Sized counter = fit(CrispFont.POPPINS_MEDIUM, COUNTER_STAND_IN,
            height * 0.40, width * 0.86);
        if (counter.caps() > title.caps()) {
            counter = CrispFont.POPPINS_MEDIUM.forCaps(title.caps());
        }

        // The whole of it - "1 CPS", "12 CPS" - as one piece, and measured every
        // frame from the text as it is that frame, so it is re-centred the moment
        // a digit changes or one is added.
        if (shown < 0.5) lines.add(new Line(label, middle, capsTop(top, height, title), softer(color, 1 - shown * 2), title));
        if (shown > 0.5) lines.add(new Line(count + " CPS", middle, capsTop(top, height, counter), softer(color, shown * 2 - 1), counter));
    }

    /** The top of a line of capitals centred in a box of this height. */
    private static double capsTop(double top, double height, CrispFont.Sized font) {
        return top + (height - font.caps()) / 2;
    }

    /** The same colour with this much of its opacity: how a name leaves a button and a count arrives on it. */
    private static Color softer(Color color, double amount) {
        return new Color(color.r, color.g, color.b, (int) Math.round(color.a * amount));
    }

    /**
     * One ripple: a thin line of the highlight that leaves the key's edge when
     * it is pressed, spreads as far as the gap round the key and fades as it
     * goes. It starts fast and slows, the way a push does, and it is gone by
     * the time it reaches the next key, so it never draws over one. What is left
     * behind on a held key is the rim itself, steady in the same colour.
     */
    private void ripple(double age, double x, double y, double w, double h, double radius, double gutter) {
        if (age >= 1) return;

        double spread = 1 - (1 - age) * (1 - age);
        double strength = (1 - age) * (1 - age);
        Color lit = highlight.get();

        RoundedBox.ripple(x, y, w, h, radius, FEATHER, spread * gutter * 0.85, Math.max(1.25, gutter * 0.3),
            new Color(lit.r, lit.g, lit.b, (int) Math.round(150 * strength)));
    }

    // What a held key looks like. Taken from the one highlight colour unless
    // told otherwise, so changing the theme is changing one setting.

    private Color heldBackground() {
        if (customPressed.get()) return pressedBackgroundColor.get();

        // A third of the way from the key's own colour to the highlight: a wash
        // of it, not a block of it. Never more see-through than the key was.
        Color base = backgroundColor.get();
        Color washed = blend(base, highlight.get(), 0.32);

        return new Color(washed.r, washed.g, washed.b, Math.max(base.a, 235));
    }

    private Color heldBorder() {
        if (customPressed.get()) return pressedBorderColor.get();

        Color lit = highlight.get();

        return new Color(lit.r, lit.g, lit.b, 235);
    }

    private Color heldText() {
        if (customPressed.get()) return pressedTextColor.get();

        // On a box the label only has to brighten. With no box it is the one
        // thing that shows a press, so it takes the highlight itself.
        return background.get() ? blend(textColor.get(), new Color(255, 255, 255, 255), 0.85) : highlight.get();
    }

    // ---------------------------------------------------------------- text

    /** One centred line, sized from Poppins' actual capital height. */
    private record Line(String text, double centre, double capsTop, Color color, CrispFont.Sized font) {
    }

    /** Choose a real pixel size that keeps the complete label inside its key. */
    private static CrispFont.Sized fit(CrispFont face, String text, double caps, double maxWidth) {
        int pixels = (int) Math.floor(Math.min(caps / face.capsRatio(),
            maxWidth / Math.max(0.01, face.unitWidth(text))));
        CrispFont.Sized sized = face.at(pixels);

        while (sized.width(text, 0) > maxWidth && pixels > 6) sized = face.at(--pixels);
        return sized;
    }

    private static String trim(String text, CrispFont.Sized font, double maxWidth) {
        while (text.length() > 1 && font.width(text, 0) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void drawLabels() {
        if (lines.isEmpty()) return;

        CrispFont.begin(null);
        try {
            for (Line line : lines) {
                line.font.centred(line.text, line.centre, line.capsTop, line.color, 0, shadow.get());
            }
        } finally {
            CrispFont.end();
        }
    }

    /**
     * What is actually on the key, so a rebound layout reads correctly. A name
     * too long for its key is stepped down a size, and cut only when there is
     * no smaller size to step to.
     */
    private String label(KeyBinding mapping) {
        return mapping.getBoundKeyLocalizedText().getString().toUpperCase(Locale.ROOT);
    }

    // --------------------------------------------------------------- boxes

    private static Color blend(Color from, Color to, double amount) {
        return new Color(
            (int) Math.round(from.r + (to.r - from.r) * amount),
            (int) Math.round(from.g + (to.g - from.g) * amount),
            (int) Math.round(from.b + (to.b - from.b) * amount),
            (int) Math.round(from.a + (to.a - from.a) * amount));
    }

    /**
     * How lit a key is, from 0 to 1.
     *
     * <p>It is a position that is walked towards wherever it should be at a
     * fixed pace - 80 ms down, 150 back up - and never
     * a timer that is started. A timer would have to be restarted, and its start
     * blended with wherever the last one had got to, every time a key was tapped
     * faster than the fade; a position just turns round from where it is. So a
     * run of fast taps ripples about the middle instead of flashing, and a held
     * key arrives at 1 and stays there, steady, because there is nowhere further
     * for it to go. It cannot overshoot, so nothing bounces.
     *
     * <p>What comes out is eased at both ends, so the colour leaves gently and
     * lands gently instead of starting and stopping dead.
     */
    private static final class Fade {
        private double position;
        private boolean wasDown;

        /** Age of the ripple the last press sent out, 0 to 1, and of the one before it: taps can come faster than a ripple fades. */
        double ripple = 1;
        double earlier = 1;

        double step(boolean down, double seconds, double speed) {
            double move = seconds * speed / (down ? FADE_IN : FADE_OUT);

            position = down ? Math.min(1, position + move) : Math.max(0, position - move);

            // A new press sends a new ripple and lets the last one finish,
            // rather than cutting it off part way out.
            if (down && !wasDown) {
                earlier = ripple;
                ripple = 0;
            }

            wasDown = down;
            ripple = Math.min(1, ripple + seconds * speed / RIPPLE);
            earlier = Math.min(1, earlier + seconds * speed / RIPPLE);

            return position * position * (3 - 2 * position);
        }

        double snap(boolean down) {
            position = down ? 1 : 0;
            wasDown = down;
            ripple = 1;
            earlier = 1;

            return position;
        }
    }

    /**
     * How far a button has gone from its name to its count, from 0 to 1.
     *
     * <p>Like a {@link Fade}, it is a position walked towards where it should be
     * at a fixed pace rather than a timer that is started, so a swap that turns
     * round part way - one click, or a click as the count runs down - carries on
     * from where it had got to instead of jumping. It is read as two halves:
     * under a half the name is on its way out, over it the count is on its way
     * in, and at neither end is there any of the other to be seen.
     */
    private static final class Swap {
        private double position;

        double step(boolean counting, double seconds, double speed) {
            double move = seconds * speed / SWAP;

            position = counting ? Math.min(1, position + move) : Math.max(0, position - move);

            return position * position * (3 - 2 * position);
        }

        double snap(boolean counting) {
            position = counting ? 1 : 0;

            return position;
        }
    }

    /** Presses in the last second, counted from the button going down, not from it being down. */
    private static final class Clicks {
        private final ArrayDeque<Long> times = new ArrayDeque<>();
        private boolean wasDown;

        void update(boolean down, long now) {
            if (down && !wasDown) times.addLast(now);
            wasDown = down;

            while (!times.isEmpty() && now - times.peekFirst() > 1000) times.removeFirst();
        }

        int perSecond() {
            return times.size();
        }

        void clear() {
            times.clear();
            wasDown = false;
        }
    }
}
