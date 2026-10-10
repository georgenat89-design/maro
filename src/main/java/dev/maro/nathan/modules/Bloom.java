package dev.maro.nathan.modules;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.gui.widgets.WWidget;
import dev.maro.runtime.gui.widgets.containers.WHorizontalList;
import dev.maro.runtime.gui.widgets.pressable.WButton;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.KeybindSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;
import dev.maro.runtime.utils.misc.Keybind;

import dev.maro.runtime.event.EventHandler;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.mixin.PostChainAccessor;
import dev.maro.nathan.mixin.PostPassAccessor;
import dev.maro.nathan.render.PostEffect;

/**
 * Bloom: bright areas bleed light into what surrounds them. One slider.
 *
 * <p><b>How.</b> The finished world frame is run through a small pyramid. A
 * bright pass keeps only what is above a threshold and halves the resolution as
 * it goes. That image is blurred across and then down - a gaussian split into two
 * one-dimensional passes, which costs 2n samples instead of n squared for the
 * same result. Each further level reads the level above it at half the size
 * again, so the same small kernel reaches twice as far across the screen for a
 * quarter of the cost. The three levels are added back onto the frame.
 *
 * <p><b>The threshold has a soft knee.</b> A hard cut-off makes a pixel pop in
 * and out of the bloom as its brightness wobbles over the line, which flickers
 * badly on a moving camera. Instead the region either side of the threshold is a
 * quadratic ramp, so brightness fades into the bleed.
 *
 * <p><b>Everything but strength is fixed.</b> Threshold, radius, tap count and
 * the per-level weights below were tuned together against a real captured frame
 * rather than picked by eye, so that Intensity 1 is the value actually worth
 * using. They are constants, not settings: a bloom with six knobs is six ways to
 * make it look wrong.
 *
 * <p><b>Where.</b> At the one point in the frame where the world and the hand are
 * drawn and nothing 2D is - immediately before {@code GuiRenderer.render}, which
 * is the single call that rasterises the HUD, chat, any screen, the debug overlay
 * and Meteor's own GUI. Everything before that call only records what to draw. So
 * the interface is never blurred and never bloomed.
 *
 * <p><b>Precision.</b> The levels are 8 bits a channel, because that is all there
 * is: this version's texture formats are RGBA8, RED8, RED8I and DEPTH32, and
 * {@code PostChainConfig.InternalTarget} takes a width, a height, a persistent
 * flag and a clear colour - there is no format to ask for. Blurring at a quarter
 * and a sixteenth of the resolution averages away most of what that would cost.
 *
 * <p>Visual only. Nothing here touches an entity, a packet or the server.
 */
public class Bloom extends Module {
    /** Set from Better Looks' panel, so not listed on its own. */
    @Override
    public boolean hiddenInGui() {
        return true;
    }

    /** The pyramid. Index 0 is half the screen, 1 a quarter, 2 an eighth. */
    private static final Identifier[] LEVEL = {
        PostEffect.ours("bloom_0"), PostEffect.ours("bloom_1"), PostEffect.ours("bloom_2")
    };

    /** The other half of each level's ping-pong: a pass cannot read a target it writes. */
    private static final Identifier[] TEMP = {
        PostEffect.ours("bloom_0_tmp"), PostEffect.ours("bloom_1_tmp"), PostEffect.ours("bloom_2_tmp")
    };

    /** Where the composite lands before being blitted back over the frame. */
    private static final Identifier OUT = PostEffect.ours("bloom_out");

    private static final int LEVELS = 3;

    /**
     * The look, fixed.
     *
     * <p>Tuned by simulating this exact pipeline over captured frames and
     * measuring what it did to them, rather than by taste.
     *
     * <p><b>How strong this looks depends on the world, and there is no setting
     * that can fix that.</b> Additive bloom over an 8 bit frame grows with how
     * much of the picture is above the threshold, and a lit daytime world has
     * several times more of that than a night one. Measured over the same frame at
     * rising exposure, Intensity 1 lifts the picture by roughly:
     *
     * <pre>
     * night          +3
     * dim daylight  +13
     * full daylight +20
     * </pre>
     *
     * <p>Raising the threshold does not flatten that curve, it only moves where it
     * starts - a threshold high enough to behave in daylight is dead at night
     * (measured: +0.2). Limiting the bleed by the headroom left in the pixel,
     * {@code bleed * (1 - base)}, lowers the whole curve but leaves the ratio
     * between the ends almost exactly as it was, 3.5x against 3.3x. So the base is
     * set for the bright case, where too much is actually unpleasant, and the
     * Intensity slider covers the rest: about 3 on a dark world, about 1 on a
     * bright one.
     *
     * <p><b>The base is deliberately gentle because the slider is long.</b> These
     * are what Intensity 1 gives; the slider runs to 10, and the base has to be
     * chosen so that the whole of that run is worth having. A base heavy enough to
     * look right at 1 turns the top of the slider into a white screen - at the
     * strength this was previously tuned to, Intensity 10 clipped 56% of the frame
     * to pure white and was useless. At this base it clips under a tenth of it and
     * the top end is still a picture.
     *
     * <p><b>Width comes from taps, not from radius.</b> RADIUS is the spacing
     * between samples and TAPS is how many are taken, so the blur reaches
     * radius * taps and is sampled every radius texels. Widening by radius alone
     * spreads the same handful of samples thinner and the gaussian starts to show
     * its individual taps as rings. Eleven taps at 1.65 covers exactly the same
     * distance as seven at 2.6 - the same sigma, to two decimal places - but takes
     * a sample every 1.65 texels instead of every 2.6, which is four and a half
     * samples per sigma rather than under three.
     *
     * <p>The threshold sets how much of the picture is allowed to bleed and the
     * weights set how hard; they are tuned together, because lowering one and
     * raising the other are not the same thing. A low threshold with light weights
     * hazes the whole frame evenly, which reads as fog. A higher threshold with
     * heavy weights leaves the midtones alone and puts the light where it belongs.
     *
     * <p>Where the grass stops looking like grass is somewhere around Intensity
     * 2.5 on this base. That is a look, not a fault, and the slider goes there on
     * purpose.
     *
     * <p>THRESHOLD is a luminance and the frame is 8 bit, so it cannot usefully go
     * near 1: at 1 nothing is brighter than the threshold and nothing blooms. That
     * is not a theoretical worry - it is exactly how this module spent its first
     * few versions doing nothing at all.
     */
    private static final float THRESHOLD = 0.45f;
    private static final float KNEE = THRESHOLD * 0.5f;
    private static final float RADIUS = 1.65f;
    private static final float TAPS = 11;

    /** The kernel grows a little at the coarser levels, for a longer falloff. */
    private static final float[] LEVEL_RADIUS = {1.0f, 1.15f, 1.3f};

    /** How much each level contributes. Wider levels are softer, so they carry less. */
    private static final float[] LEVEL_WEIGHT = {0.30f, 0.22f, 0.16f};

    private static final String BRIGHT_BLOCK = "BloomBrightConfig";
    private static final String BLUR_BLOCK = "BloomBlurConfig";
    private static final String COMPOSITE_BLOCK = "BloomCompositeConfig";

    /** Four floats each; std140 rounds a block up to a multiple of sixteen. */
    private static final int BLOCK_BYTES = 16;

    // ---- the debug dump's targets. Numbered so the files sort into pipeline order.

    private static final Identifier DBG_FRAME = PostEffect.ours("dbg_0_frame");
    private static final Identifier DBG_BRIGHT = PostEffect.ours("dbg_1_bright");

    private static final Identifier[] DBG_ACROSS = {
        PostEffect.ours("dbg_2_across_0"), PostEffect.ours("dbg_2_across_1"), PostEffect.ours("dbg_2_across_2")
    };

    private static final Identifier[] DBG_DOWN = {
        PostEffect.ours("dbg_3_down_0"), PostEffect.ours("dbg_3_down_1"), PostEffect.ours("dbg_3_down_2")
    };

    private static final Identifier DBG_COMPOSITE = PostEffect.ours("dbg_4_composite");

    /**
     * Frames the debug chain is kept alive after a dump is asked for.
     *
     * <p>The readback is asynchronous - the image arrives a frame or two later,
     * when the GPU has caught up - and the targets have to still exist when it
     * does. Reverting to the normal chain straight away would close them out from
     * under the callback.
     */
    private static final int DEBUG_HOLD = 8;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> intensity = sgGeneral.add(new DoubleSetting.Builder()
        .name("intensity")
        .description("How strong the glow is, 0 to 10. 1 is the tuned default; past about 3 it stops being a glow and becomes a wash. 0 turns the effect off entirely and costs nothing.")
        .defaultValue(1)
        .min(0)
        .max(10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("Turns the module on and off.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final PostEffect effect = new PostEffect("bloom");

    /**
     * The composite pass's uniform buffer.
     *
     * <p><b>Lent, not kept.</b> Once a buffer is put into a pass's custom uniform
     * map the chain owns it: {@code PostPass.close} walks that map and closes
     * every buffer in it. So when the chain goes - a resize, a shader that would
     * not build - this is already closed, and the reference here is dropped rather
     * than closed again. Closing it a second time is how this sort of code usually
     * ends up disabling itself after the first window resize.
     */
    private GpuBuffer uniforms;

    /** The chain the buffer was lent to, by identity, so a rebuild is noticed. */
    private PostEffectProcessor lastChain;

    /** One staging buffer, reused. Nothing here allocates while a frame is drawn. */
    private ByteBuffer staging;

    private final float[] compositeValues = new float[BLOCK_BYTES / 4];

    /** The size the chain was built for. A change means the window moved. */
    private int builtWidth;
    private int builtHeight;

    private boolean keyWasDown;

    /** Set by the button or the command; cleared once the dump has been asked for. */
    private boolean debugRequested;

    /** Counts down while the debug chain is held open for the readback. */
    private int debugHold;

    /** Whether the chain being built is the debug one. Read by config(). */
    private boolean debugChain;

    public Bloom() {
        super(NameeProtectAddon.CATEGORY, "bloom",
            "Bright areas bleed light into what surrounds them. The world only - the HUD, chat and menus stay sharp.");
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void onActivate() {
        effect.reset();

        // A shader pack owns the frame at this point and will either overwrite
        // this or fight it. Said once, on activation, rather than every frame.
        FabricLoader loader = FabricLoader.getInstance();

        if (loader.isModLoaded("iris") || loader.isModLoaded("oculus") || loader.isModLoaded("optifabric")) {
            info("A shader mod is installed. If a shader pack is loaded it draws the world itself, and this bloom will be overwritten or doubled up - most packs have their own.");
        }
    }

    @Override
    public void onDeactivate() {
        release();
    }

    /**
     * Everything given back: the chain and its targets, the uniform buffer the
     * chain closes with it, and the one off-heap staging buffer.
     */
    private void release() {
        // Closes every pass, and with them the uniform buffer we lent it.
        effect.discard();

        uniforms = null;
        lastChain = null;

        if (staging != null) {
            MemoryUtil.memFree(staging);
            staging = null;
        }

        builtWidth = 0;
        builtHeight = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean down = mc.currentScreen == null && keybind.get().isPressed();

        if (down && !keyWasDown) toggle();

        keyWasDown = down;
    }

    /**
     * A button for the dump, under the settings.
     *
     * <p>The command is Meteor's, so it wears Meteor's prefix - a full stop by
     * default, not a slash. That is easy to get wrong, and a button cannot be got
     * wrong, so the dump is reachable both ways.
     */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList list = theme.horizontalList();

        WButton dump = list.add(theme.button("Dump Debug Images")).expandX().widget();

        dump.action = () -> {
            if (!isActive()) {
                warning("Turn Bloom on first - the chain only exists while it is running.");
                return;
            }

            requestDebugDump();
        };

        return list;
    }

    /** From the command or the button: dump every stage on the next frame drawn. */
    public void requestDebugDump() {
        debugRequested = true;
    }

    // ----------------------------------------------------------------- frame

    /** From the game renderer, after the world and the hand and before anything 2D. */
    public static void applyTo(Framebuffer target, ObjectAllocator allocator) {
        Modules modules = Modules.get();
        if (modules == null) return;

        Bloom module = modules.get(Bloom.class);
        if (module == null || !module.isActive()) return;

        module.frame(target, allocator);
    }

    private void frame(Framebuffer target, ObjectAllocator allocator) {
        // Nothing asked for: no chain built, no targets allocated, no cost.
        if (intensity.get() <= 0) return;
        if (mc.world == null || target.textureWidth <= 0 || target.textureHeight <= 0) return;

        // The targets are a fixed size, so the key carries it: a resized window is
        // a different key, the chain is rebuilt, and the buffers with it.
        builtWidth = target.textureWidth;
        builtHeight = target.textureHeight;

        // The debug chain is a different chain - extra copies into targets that
        // survive the frame - so it gets its own key and is built on demand.
        debugChain = debugRequested || debugHold > 0;

        String key = builtWidth + "x" + builtHeight + (debugChain ? "|debug" : "");

        if (!effect.prepare(key, this::config)) {
            error("The bloom shader would not run - see the log. Toggle the module to try again.");
            return;
        }

        try {
            write();
        } catch (RuntimeException e) {
            NameeProtectAddon.LOG.warn("bloom could not write its uniforms", e);
        }

        if (!effect.run(target, allocator, key, this::config)) return;

        if (debugRequested) {
            debugRequested = false;
            debugHold = DEBUG_HOLD;
            dump();
        } else if (debugHold > 0) {
            debugHold--;
        }
    }

    // ----------------------------------------------------------------- chain

    private int levelWidth(int level) {
        return Math.max(1, builtWidth >> (level + 1));
    }

    private int levelHeight(int level) {
        return Math.max(1, builtHeight >> (level + 1));
    }

    private static PostEffectPipeline.Targets sized(int width, int height, boolean persistent) {
        return new PostEffectPipeline.Targets(Optional.of(width), Optional.of(height), persistent, 0);
    }

    private PostEffectPipeline config() {
        Map<Identifier, PostEffectPipeline.Targets> targets = new LinkedHashMap<>();

        for (int i = 0; i < LEVELS; i++) {
            targets.put(LEVEL[i], sized(levelWidth(i), levelHeight(i), false));
            targets.put(TEMP[i], sized(levelWidth(i), levelHeight(i), false));
        }

        // Full size: the composite reads the frame and so cannot write to it.
        targets.put(OUT, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0));

        if (debugChain) {
            // Persistent, so they are still there to be read after the frame, and
            // deliberately HALF SIZE - including the two that capture a full-screen
            // stage. A full-screen persistent target proved impossible to tell
            // apart from the main buffer in a dump: the frame and the composite
            // came back byte-identical and carrying a HUD, which cannot be in the
            // frame at the point this runs. Half size cannot be mistaken for the
            // main target, at the cost of a dump at half resolution - which, for
            // looking at, is no cost at all.
            targets.put(DBG_FRAME, sized(levelWidth(0), levelHeight(0), true));
            targets.put(DBG_COMPOSITE, sized(levelWidth(0), levelHeight(0), true));
            targets.put(DBG_BRIGHT, sized(levelWidth(0), levelHeight(0), true));

            for (int i = 0; i < LEVELS; i++) {
                targets.put(DBG_ACROSS[i], sized(levelWidth(i), levelHeight(i), true));
                targets.put(DBG_DOWN[i], sized(levelWidth(i), levelHeight(i), true));
            }
        }

        List<PostEffectPipeline.Pass> passes = new ArrayList<>();

        // The frame as it arrives, before anything is done to it: the control
        // image the rest of the dump is read against.
        if (debugChain) passes.add(PostEffect.copy(PostEffect.MAIN, DBG_FRAME));

        // The bright pass is also the first halving: four taps of the frame, each
        // thresholded, averaged into the half-size level.
        passes.add(PostEffect.pass("bloom_bright",
            List.of(new PostEffectPipeline.TargetSampler("In", PostEffect.MAIN, false, true)),
            LEVEL[0], BRIGHT_BLOCK,
            List.of(new UniformValue.FloatValue(THRESHOLD),
                    new UniformValue.FloatValue(KNEE),
                    new UniformValue.FloatValue(0),
                    new UniformValue.FloatValue(0))));

        if (debugChain) passes.add(PostEffect.copy(LEVEL[0], DBG_BRIGHT));

        for (int i = 0; i < LEVELS; i++) {
            // For levels past the first the across pass reads the level above at
            // twice the size, so it halves the resolution as it blurs and there is
            // no separate downsample pass to pay for.
            Identifier from = i == 0 ? LEVEL[0] : LEVEL[i - 1];
            float levelRadius = RADIUS * LEVEL_RADIUS[i];

            passes.add(PostEffect.pass("bloom_blur",
                List.of(new PostEffectPipeline.TargetSampler("In", from, false, true)),
                TEMP[i], BLUR_BLOCK, blur(1, 0, levelRadius)));

            if (debugChain) passes.add(PostEffect.copy(TEMP[i], DBG_ACROSS[i]));

            passes.add(PostEffect.pass("bloom_blur",
                List.of(new PostEffectPipeline.TargetSampler("In", TEMP[i], false, true)),
                LEVEL[i], BLUR_BLOCK, blur(0, 1, levelRadius)));

            if (debugChain) passes.add(PostEffect.copy(LEVEL[i], DBG_DOWN[i]));
        }

        List<PostEffectPipeline.Input> inputs = new ArrayList<>();

        inputs.add(PostEffect.input("In", PostEffect.MAIN));

        for (int i = 0; i < LEVELS; i++) {
            inputs.add(new PostEffectPipeline.TargetSampler("L" + i, LEVEL[i], false, true));
        }

        // Placeholders: every one is overwritten before the chain is first run.
        passes.add(PostEffect.pass("bloom_composite", inputs, OUT, COMPOSITE_BLOCK,
            List.of(new UniformValue.FloatValue(0), new UniformValue.FloatValue(0),
                    new UniformValue.FloatValue(0), new UniformValue.FloatValue(0))));

        if (debugChain) passes.add(PostEffect.copy(OUT, DBG_COMPOSITE));

        passes.add(PostEffect.copy(OUT, PostEffect.MAIN));

        return new PostEffectPipeline(targets, passes);
    }

    private static List<UniformValue> blur(float dirX, float dirY, float radius) {
        return List.of(new UniformValue.FloatValue(dirX),
                       new UniformValue.FloatValue(dirY),
                       new UniformValue.FloatValue(radius),
                       new UniformValue.FloatValue(TAPS));
    }

    // -------------------------------------------------------------- uniforms

    /**
     * The one block that changes while the module runs.
     *
     * <p>The bright and blur blocks are baked into the chain when it is built,
     * because nothing in them depends on anything that can move. Only the
     * composite's strength does, so only it is written per frame.
     *
     * <p>The pass is found by the block it carries rather than by its position:
     * the debug chain interleaves copy passes between these, so any index
     * arithmetic would be wrong the moment the dump is switched on.
     */
    private void write() {
        PostEffectProcessor chain = effect.chain();
        if (chain == null) return;

        if (chain != lastChain) {
            // A different chain, so the one before it closed the buffer we had
            // lent it. Dropped rather than closed - see the field.
            uniforms = null;
            lastChain = chain;
        }

        if (staging == null) staging = MemoryUtil.memAlloc(BLOCK_BYTES);

        compositeValues[0] = intensity.get().floatValue();
        compositeValues[1] = LEVEL_WEIGHT[0];
        compositeValues[2] = LEVEL_WEIGHT[1];
        compositeValues[3] = LEVEL_WEIGHT[2];

        for (PostEffectPass pass : ((PostChainAccessor) chain).getPasses()) {
            Map<String, GpuBuffer> custom = ((PostPassAccessor) pass).getUniformBuffers();

            if (!custom.containsKey(COMPOSITE_BLOCK)) continue;

            if (uniforms == null) {
                uniforms = RenderSystem.getDevice().createBuffer(
                    () -> "nameeprotect bloom uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLOCK_BYTES);
            }

            if (custom.get(COMPOSITE_BLOCK) != uniforms) {
                GpuBuffer old = custom.put(COMPOSITE_BLOCK, uniforms);

                if (old != null && old != uniforms) old.close();
            }

            // Absolute puts, so the position stays where it is set below.
            for (int i = 0; i < compositeValues.length; i++) staging.putFloat(i * 4, compositeValues[i]);

            staging.position(0);
            staging.limit(BLOCK_BYTES);

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(0, BLOCK_BYTES), staging);

            staging.clear();

            return;
        }
    }

    // ----------------------------------------------------------------- debug

    /**
     * Writes every stage of the chain to {@code .minecraft/bloom/debug} as PNGs.
     *
     * <p>The point is to make "it does not work" answerable by looking rather than
     * by guessing. Read the files in order: if {@code dbg_0_frame} has the world
     * in it but {@code dbg_1_bright} is black, the threshold is keeping nothing.
     * If the bright pass has content but {@code dbg_3_down_0} is black, the blur
     * is losing it. If every stage looks right and {@code dbg_4_composite} is
     * indistinguishable from the frame, the bleed is being added too weakly to
     * see.
     *
     * <p>The readback is asynchronous - the image is handed over a frame or two
     * later - which is why the debug chain is held open for a few frames after
     * this returns.
     */
    private void dump() {
        PostEffectProcessor chain = effect.chain();
        if (chain == null) return;

        Map<Identifier, Framebuffer> persistent = ((PostChainAccessor) chain).getFramebuffers();
        Path dir = mc.runDirectory.toPath().resolve("bloom").resolve("debug");

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            error("Could not make %s: %s", dir, e.getMessage());

            return;
        }

        List<Identifier> wanted = new ArrayList<>();

        wanted.add(DBG_FRAME);
        wanted.add(DBG_BRIGHT);

        for (int i = 0; i < LEVELS; i++) {
            wanted.add(DBG_ACROSS[i]);
            wanted.add(DBG_DOWN[i]);
        }

        wanted.add(DBG_COMPOSITE);

        int asked = 0;

        for (Identifier id : wanted) {
            Framebuffer stage = persistent.get(id);

            if (stage == null) {
                NameeProtectAddon.LOG.warn("bloom debug: no persistent target for {}", id);
                continue;
            }

            Path file = dir.resolve(id.getPath() + ".png");

            // The consumer owns the image - the game's own screenshot path closes
            // it in a try-with-resources, so this has to close it too.
            ScreenshotRecorder.takeScreenshot(stage, image -> {
                try {
                    image.writeTo(file.toFile());
                } catch (Exception e) {
                    NameeProtectAddon.LOG.warn("bloom debug: could not write {}", file, e);
                } finally {
                    image.close();
                }
            });

            asked++;
        }

        info("Dumping %d stages to %s. The readback lags the frame, so give it a moment.", asked, dir);
    }
}
