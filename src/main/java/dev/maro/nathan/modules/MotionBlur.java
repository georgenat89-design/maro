package dev.maro.nathan.modules;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.maro.runtime.events.game.GameJoinedEvent;
import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.mixin.PostChainAccessor;
import dev.maro.nathan.mixin.PostPassAccessor;
import dev.maro.nathan.render.PostEffect;

/**
 * Motion blur. One slider.
 *
 * <p><b>How.</b> Two parts. First, each pixel is smeared along the way it has
 * moved on the screen since the last frame: its depth places it in the world,
 * and this frame's and last frame's camera matrices say where that point was
 * on each screen. A turn smears sideways, a fall smears down, and a wall you
 * walk past smears more the nearer it is. That is the smooth part - a spread
 * of samples along the motion, not one frame laid over another. Second, that
 * smeared frame is mixed a little with the frame shown before it, so the
 * trail carries on across frames and does not flicker.
 *
 * <p><b>By the clock, not by the frame.</b> How much of the old frame survives
 * is not a fixed fraction a frame. At 240 fps a fixed fraction would be applied
 * four times as often as at 60 and the blur would be four times as heavy, which
 * is the usual reason these look wrong. Instead the old frame decays like a
 * charge draining: with time constant {@code tau} seconds, after a frame that
 * took {@code dt} seconds a fraction {@code exp(-dt / tau)} of it is left, and
 * so the new frame is mixed in at
 *
 * <pre>
 * shutter = MAX_SHUTTER * strength / 50      how far back in time the smear reaches
 * smear   = motion over the last frame * shutter / dt
 * alpha   = 1 - exp(-dt / tau),   tau = shutter / 2
 * </pre>
 *
 * which makes the trail's length a length in seconds, the same at any frame
 * rate. {@code dt} is clamped: after a stall, a world load or a minute in a menu
 * one frame should not be treated as a whole second of motion.
 *
 * <p><b>Where.</b> Right after the world has been drawn and before the HUD, the
 * chat or any screen, which are therefore never blurred. The mixing is done by
 * the game's own post-processing machinery, in a target of the chain's own that
 * the game keeps from frame to frame - that is the history. It is sized with
 * the window, which is why a resize is a reset for free.
 *
 * <p><b>Resets.</b> The history is dropped - the next frame is shown as it is
 * and becomes the new history - on joining a world, changing dimension,
 * respawning, being teleported, whenever a screen was open or the game paused,
 * and on a resize. Otherwise the last thing seen before the cut would bleed
 * into the first thing seen after it.
 *
 * <p><b>Precision.</b> The history is kept in the game's standard 8 bits a
 * channel. This version of the game offers nothing wider for a post target -
 * its texture formats are RGBA8, RED8, RED8I and DEPTH32 - so instead the
 * shader dithers: a little ordered noise, under half a step, is added before
 * the result is stored, which turns the bands that repeated 8-bit rounding
 * would leave in dark areas into fine grain that the eye does not see.
 */
public class MotionBlur extends Module {
    /**
     * The shutter at full strength (50), in seconds: how far back in time a
     * pixel is smeared. A camera turning at 90 degrees a second, seen through
     * a 70 degree field of view, smears about 6% of the screen at 0.05 s -
     * plainly a blur, still a picture. 0.02 s at the default of 20. This is
     * the one number to retune; nothing else needs to change.
     */
    private static final double MAX_SHUTTER = 0.05;
    private static final int MAX_STRENGTH = 50;

    /** The smear is never longer than this fraction of the screen, however fast the turn. */
    private static final float MAX_SMEAR = 0.12f;

    /**
     * The most one frame is allowed to count for, in seconds. A frame longer
     * than this - a stall, a load - is treated as this long, so the blend
     * cannot leap to nearly 1 and then snap; and the history is dropped anyway
     * if it was very long, since what it holds is stale by then.
     */
    private static final double MAX_DT = 0.05;
    private static final double STALE_DT = 0.5;

    /** How far you have to have moved in one tick to count as having been teleported. */
    private static final double TELEPORT_BLOCKS = 12;

    /**
     * What the blend is driven by.
     *
     * <p>A camera blurs because it turned or changed speed, not because it was
     * moving. Flying straight and level at forty blocks a second is a sharp
     * picture; whipping round on the spot is not. So rotation carries most of the
     * weight, a change of velocity carries some, and raw speed is left a small
     * baseline so that running still reads as movement.
     *
     * <p>Each is measured against what counts as "a lot" of it, so all three come
     * out between 0 and 1 before they are weighted, and each is per second, so
     * none of it depends on the frame rate.
     */
    private static final double W_ROTATION = 0.70;
    private static final double W_ACCELERATION = 0.22;
    private static final double W_TRANSLATION = 0.08;

    private static final double ROTATION_FULL = 190;
    private static final double ACCELERATION_FULL = 22;
    private static final double SPEED_FULL = 26;

    /** Nothing ever smears the frame completely, whatever the motion. */
    private static final double CEILING = 0.85;

    /** In sustained flight it cannot reach what it can on the ground, even on a hard turn. */
    private static final double FLIGHT_CEILING = 0.55;

    /**
     * How the amount moves. It comes on almost at once, because a turn that
     * blurs late looks broken, and goes off slowly, so a straight line settles
     * over about half a second rather than snapping clear. Flight itself eases
     * over a quarter of a second, so entering and leaving it glides.
     */
    private static final double RISE_TAU = 0.05;
    private static final double FALL_TAU = 0.40;
    private static final double FLIGHT_TAU = 0.25;

    private static final Identifier HISTORY = PostEffect.ours("blur_history");
    private static final Identifier SWAP = PostEffect.ours("blur_swap");
    private static final String BLOCK = "MotionBlurConfig";
    /** Two mat4, a vec4, four floats: 64 + 64 + 16 + 16. */
    private static final int BLOCK_BYTES = 160;

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Integer> strength = sgMain.add(new IntSetting.Builder()
        .name("strength")
        .description("How long the trail is, 1 to 50.")
        .defaultValue(20)
        .range(1, MAX_STRENGTH)
        .sliderRange(1, MAX_STRENGTH)
        .build()
    );

    private final PostEffect effect = new PostEffect("motion-blur");

    /** The per-frame uniforms, in a buffer of our own that can be written to. */
    private GpuBuffer uniforms;

    /** Whether the history target holds a frame worth mixing with. */
    private boolean historyValid;

    private long lastFrameNanos;
    private Vec3d lastPos;
    private int lastDimension;

    /** How much blur the motion is asking for, eased, and how far into flight we are. */
    private double amount;
    private double flight;

    /** Last frame's camera aim and velocity, for the rotation and acceleration terms. */
    private double prevYaw = Double.NaN;
    private double prevPitch;
    private Vec3d prevVelocity;

    /** This frame's view-projection and camera position, from the level renderer, and last frame's. */
    private static final Matrix4f viewProj = new Matrix4f();
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static float cameraYaw;
    private static float cameraPitch;
    private static boolean haveMatrices;

    private final Matrix4f prevViewProj = new Matrix4f();
    private final Matrix4f invViewProj = new Matrix4f();
    private Vec3d prevCameraPos;

    /** From the level renderer, at the start of drawing the world: what it is drawn with. */
    public static void matrices(Matrix4f modelView, Matrix4f projection, Camera camera) {
        projection.mul(modelView, viewProj);
        cameraPos = camera.getCameraPos();

        // The camera's own aim, not the player's: this is what the frame was
        // actually drawn with, so Freelook and the free cameras are included.
        cameraYaw = camera.getYaw();
        cameraPitch = camera.getPitch();
        haveMatrices = true;
    }

    public MotionBlur() {
        super(NameeProtectAddon.CATEGORY, "motion-blur", "Blurs fast movement, the same at any frame rate. Not the HUD, not the hand.");
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void onActivate() {
        effect.reset();
        reset();
    }

    @Override
    public void onDeactivate() {
        // The chain, and with it the history target, given back. Ours too.
        effect.discard();
        freeUniforms();
        reset();
    }

    /**
     * Earlier versions of this module saved a dozen settings - blend mode,
     * frame caps, per-axis switches - and, briefly, a strength from 0 to 1.
     * Meteor matches saved settings to live ones by name and skips the rest,
     * and refuses a value that is not of the right kind or range, so an old
     * file loads and an old strength lands on the default.
     */
    @Override
    public Module fromTag(NbtCompound tag) {
        return super.fromTag(tag);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    /** Respawns, teleports and dimension changes, seen from the player's own position. */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            lastPos = null;
            return;
        }

        Vec3d pos = mc.player.getEntityPos();
        int dimension = mc.world.getRegistryKey().hashCode();

        if (lastPos == null || dimension != lastDimension || pos.squaredDistanceTo(lastPos) > TELEPORT_BLOCKS * TELEPORT_BLOCKS || mc.player.deathTime > 0) reset();

        lastPos = pos;
        lastDimension = dimension;
    }

    /** Drops the history. The next frame is shown as it is and becomes the new history. */
    public void reset() {
        historyValid = false;
        lastFrameNanos = 0;
        prevCameraPos = null;
        prevVelocity = null;
        prevYaw = Double.NaN;
        amount = 0;
        flight = 0;
    }

    // ----------------------------------------------------------------- frame

    /** From the game renderer, after the world and before the hand, while the world's depth is still in the buffer. */
    public static void applyTo(Framebuffer target, ObjectAllocator allocator) {
        Modules modules = Modules.get();
        if (modules == null) return;

        MotionBlur module = modules.get(MotionBlur.class);
        if (module == null || !module.isActive()) return;

        module.frame(target, allocator);
    }

    private void frame(Framebuffer target, ObjectAllocator allocator) {
        // Nothing to blur, or nothing that should be: no world, a screen up,
        // the game paused. The history is dropped so that whatever was on
        // screen before the pause does not bleed into what comes after it.
        if (mc.world == null || mc.player == null || mc.currentScreen != null || mc.isPaused()) {
            reset();
            return;
        }

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? MAX_DT : (now - lastFrameNanos) / 1e9;

        lastFrameNanos = now;

        if (dt > STALE_DT) historyValid = false;

        dt = Math.max(0.0005, Math.min(MAX_DT, dt));

        // The smear reaches back a shutter's worth of time. The motion the
        // shader has is over one frame, so it is scaled by shutter / dt to
        // reach that far whatever the frame rate. No last frame yet, or the
        // matrices missing: no smear this frame.
        double shutter = MAX_SHUTTER * strength.get() / MAX_STRENGTH;
        boolean canSmear = haveMatrices && prevCameraPos != null && historyValid;

        // How much blur this frame's motion is asking for, 0 to 1. Everything
        // below is scaled by it, so standing still is a clean frame and a
        // straight line settles to one.
        double motion = motion(dt);

        float scale = canSmear ? (float) (shutter / dt * motion) : 0;

        // alpha = 1 - exp(-dt / tau): the share of the NEW frame in the mix.
        // tau now falls with the motion, so at rest it is nothing and the whole
        // of the new frame is shown - which is what stops flight smearing for
        // ever. With no history yet it is all new frame, which seeds it.
        double tau = shutter / 2 * motion;
        float alpha = historyValid && tau > 1e-4 ? (float) (1 - Math.exp(-dt / tau)) : 1;

        // This frame's alpha goes into the uniform buffer before the chain
        // runs. The chain itself is built once and kept; only the buffer moves.
        if (!effect.prepare("v2", this::config)) {
            error("The motion blur shader would not run - see the log. Toggle the module to try again.");
            return;
        }

        try {
            write(alpha, scale, now);
        } catch (RuntimeException e) {
            NameeProtectAddon.LOG.warn("motion-blur could not write its uniforms", e);
        }

        if (effect.run(target, allocator, "v2", this::config)) historyValid = true;

        // This frame is next frame's last frame.
        if (haveMatrices) {
            prevViewProj.set(viewProj);
            prevCameraPos = cameraPos;
        }
    }

    /**
     * How much blur the camera's motion is asking for this frame, 0 to 1.
     *
     * <p>Three terms, each per second so none of them depends on the frame rate,
     * each normalised against what counts as a lot of it, and weighted: the turn
     * dominates, a change of velocity matters, and raw speed is a small baseline.
     * In flight that baseline is taken away entirely, which is what lets a
     * straight line settle to a sharp picture however fast it is going.
     *
     * <p>What comes out is eased rather than used raw: on almost at once, off
     * over about half a second, so a turn blurs immediately and a straight line
     * clears gently. Two ceilings sit over it - one absolute, so nothing ever
     * smears the frame completely, and a lower one in flight, so even a hard
     * elytra turn stays under what a spin on the ground reaches.
     */
    private double motion(double dt) {
        if (mc.player == null || !haveMatrices || prevCameraPos == null || Double.isNaN(prevYaw)) {
            remember(dt);

            return amount;
        }

        // Degrees a second. Yaw is wrapped, or passing north would read as a
        // three hundred and sixty degree spin.
        double turn = Math.hypot(MathHelper.wrapDegrees(cameraYaw - prevYaw), cameraPitch - prevPitch) / dt;

        Vec3d velocity = cameraPos.subtract(prevCameraPos).multiply(1 / dt);
        double speed = velocity.length();
        double change = prevVelocity == null ? 0 : velocity.subtract(prevVelocity).length() / dt;

        boolean flying = mc.player.isGliding()
            || mc.player.getAbilities().flying
            || mc.player.hasStatusEffect(StatusEffects.LEVITATION);

        flight += ((flying ? 1 : 0) - flight) * (1 - Math.exp(-dt / FLIGHT_TAU));

        double wanted = W_ROTATION * clamp(turn / ROTATION_FULL)
            + W_ACCELERATION * clamp(change / ACCELERATION_FULL)
            + W_TRANSLATION * clamp(speed / SPEED_FULL) * (1 - flight);

        // The absolute ceiling, and then the lower one flight brings with it.
        wanted = Math.min(wanted, CEILING);
        wanted = Math.min(wanted, CEILING + (FLIGHT_CEILING - CEILING) * flight);

        amount += (wanted - amount) * (1 - Math.exp(-dt / (wanted > amount ? RISE_TAU : FALL_TAU)));

        remember(dt);

        return amount;
    }

    private void remember(double dt) {
        if (haveMatrices) {
            if (prevCameraPos != null) prevVelocity = cameraPos.subtract(prevCameraPos).multiply(1 / dt);

            prevYaw = cameraYaw;
            prevPitch = cameraPitch;
        }
    }

    private static double clamp(double value) {
        return value < 0 ? 0 : Math.min(1, value);
    }

    private PostEffectPipeline config() {
        Map<Identifier, PostEffectPipeline.Targets> targets = Map.of(
            // Persistent: kept by the chain between frames, and remade - blank - when the window changes size.
            HISTORY, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), true, 0xFF000000),
            SWAP, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0));

        List<PostEffectPipeline.Pass> passes = List.of(
            // The mix, into a scratch target: a pass cannot read a target it writes.
            PostEffect.pass("motion_blur",
                List.of(
                    PostEffect.input("Current", PostEffect.MAIN),
                    // The same target's depth, which is how far away each pixel is.
                    new PostEffectPipeline.TargetSampler("Depth", PostEffect.MAIN, true, false),
                    PostEffect.input("History", HISTORY)),
                SWAP, BLOCK,
                List.of(
                    new UniformValue.Matrix4fValue(new Matrix4f()), // InvViewProj
                    new UniformValue.Matrix4fValue(new Matrix4f()), // PrevViewProj
                    new UniformValue.Vec4fValue(new Vector4f()),      // CameraDelta
                    new UniformValue.FloatValue(1),                  // Alpha
                    new UniformValue.FloatValue(0),                  // Scale
                    new UniformValue.FloatValue(0),                  // Seed
                    new UniformValue.FloatValue(MAX_SMEAR))),        // MaxLength
            // The mix becomes the history for next frame, and the frame that is shown.
            PostEffect.copy(SWAP, HISTORY),
            PostEffect.copy(SWAP, PostEffect.MAIN));

        return new PostEffectPipeline(targets, passes);
    }

    /**
     * The uniforms for this run. The buffer the chain made for them is not
     * writable once made, so it is swapped, once, for one of ours that is.
     */
    private void write(float alpha, float scale, long now) {
        PostEffectProcessor chain = effect.chain();
        if (chain == null) return;

        List<PostEffectPass> passes = ((PostChainAccessor) chain).getPasses();
        if (passes.isEmpty()) return;

        Map<String, GpuBuffer> custom = ((PostPassAccessor) passes.getFirst()).getUniformBuffers();

        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "nameeprotect motion blur uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLOCK_BYTES);
        }

        if (custom.get(BLOCK) != uniforms) {
            GpuBuffer old = custom.put(BLOCK, uniforms);
            if (old != null && old != uniforms) old.close();
        }

        ByteBuffer data = MemoryUtil.memAlloc(BLOCK_BYTES);

        try {
            // std140: two mat4 (column-major), a vec4, then four floats.
            viewProj.invert(invViewProj).get(0, data);
            prevViewProj.get(64, data);

            Vec3d prev = prevCameraPos == null ? cameraPos : prevCameraPos;

            data.putFloat(128, (float) (cameraPos.x - prev.x));
            data.putFloat(132, (float) (cameraPos.y - prev.y));
            data.putFloat(136, (float) (cameraPos.z - prev.z));
            data.putFloat(140, 0);
            data.putFloat(144, alpha);
            data.putFloat(148, scale);
            // A new dither phase every frame, so the grain does not sit still.
            data.putFloat(152, (now / 1_000_000L % 1000) / 1000f);
            data.putFloat(156, MAX_SMEAR);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(0, BLOCK_BYTES), data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private void freeUniforms() {
        if (uniforms == null) return;

        try {
            uniforms.close();
        } catch (RuntimeException ignored) {
            // Already gone with the chain that held it.
        }

        uniforms = null;
    }
}
