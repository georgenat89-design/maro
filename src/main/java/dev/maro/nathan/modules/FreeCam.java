package dev.maro.nathan.modules;

import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.events.meteor.MouseScrollEvent;
import dev.maro.runtime.events.render.Render2DEvent;
import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Lets the camera leave your head and fly about, while you stay where you are.
 *
 * <p><b>What it is.</b> A camera and nothing else. Your player is not moved, is
 * never teleported, and is not what is being steered: while it is on, the
 * movement keys, jump, sneak, sprint and the mouse are taken for the camera and
 * do not reach the player at all, so you stand where you were, looking where
 * you were looking. Turn it off and the camera is back behind your eyes and the
 * keys are yours again. Nothing about it is sent anywhere.
 *
 * <p><b>What it cannot do.</b> The world on your screen is the part of it the
 * server has sent you, which is the part round your player. The camera can fly
 * out of that, and there is nothing there to see: no setting here can load
 * ground the server has not supplied. Render Distance only asks the game to
 * draw more of what it already has.
 *
 * <p><b>Smooth.</b> The camera is moved once a frame, by the clock, from where
 * the last frame left it - not once a tick and guessed at in between - so it
 * flies at the same speed and with the same glide at any frame rate. The mouse
 * turns it directly, as it turns you.
 *
 * <p>It is called "free-cam" and not "freecam" because Meteor has a module of
 * that name. A module added under a name that is taken replaces the one that
 * had it, and Meteor's own camera code asks for its Freecam by class and would
 * not find it. They are separate modules; use one or the other.
 */
public class FreeCam extends Module {
    private static FreeCam instance;

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Double> speed = sgMain.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the camera flies, in blocks a second.")
        .defaultValue(16)
        .range(0.5, 200)
        .sliderRange(1, 64)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Double> lookSpeed = sgMain.add(new DoubleSetting.Builder()
        .name("look-speed")
        .description("How far the camera turns for a given move of the mouse. 0.15 is how the game turns you; your own mouse sensitivity applies on top, as it does for you.")
        .defaultValue(0.10)
        .range(0.01, 1)
        .sliderRange(0.01, 0.5)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Double> smoothing = sgMain.add(new DoubleSetting.Builder()
        .name("smoothing")
        .description("How much the camera glides into and out of a move. 0 starts and stops dead; nearer 1 is a longer, softer glide.")
        .defaultValue(0.98)
        .range(0, 0.995)
        .sliderRange(0, 0.99)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Integer> renderDistance = sgMain.add(new IntSetting.Builder()
        .name("render-distance")
        .description("How far the game is asked to draw while the camera is out, in blocks. It only ever raises your own render distance, never lowers it, and puts it back afterwards. It draws more of what the server has sent; it cannot load what it has not.")
        .defaultValue(64)
        .range(32, 512)
        .sliderRange(32, 512)
        .build()
    );

    private final Setting<Boolean> scrollSpeed = sgMain.add(new BoolSetting.Builder()
        .name("scroll-speed")
        .description("The mouse wheel changes the camera's speed while it is out, and does nothing else: it does not change your hotbar slot or zoom.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> scrollStep = sgMain.add(new DoubleSetting.Builder()
        .name("scroll-step")
        .description("How much one notch of the wheel changes the speed, as a share of it: 0.10 is a tenth faster or slower.")
        .defaultValue(0.10)
        .range(0.01, 1)
        .sliderRange(0.01, 0.5)
        .decimalPlaces(2)
        .visible(scrollSpeed::get)
        .build()
    );

    private final Setting<Double> sprintMultiplier = sgMain.add(new DoubleSetting.Builder()
        .name("sprint-multiplier")
        .description("How many times faster the camera flies while your sprint key is held.")
        .defaultValue(2)
        .range(1, 20)
        .sliderRange(1, 8)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Boolean> holdPosition = sgMain.add(new BoolSetting.Builder()
        .name("hold-position")
        .description("Let go of the movement keys and the camera stops where it is. Off, it drifts on and slows to a stop by Smoothing.")
        .defaultValue(true)
        .build()
    );

    // Where the camera is and where it is going, in the world. Moved once a frame.
    private boolean started;
    private double x;
    private double y;
    private double z;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private float yaw;
    private float pitch;
    private long lastAt;

    /** The world it was turned on in, so that a change of world turns it off. */
    private ClientWorld level;

    /** Your own render distance, in chunks, if it was raised; nothing if it was not. */
    private Integer ownRenderDistance;

    /** Whether chunk culling was on before the camera went out; nothing while it is not out. */
    private Boolean ownChunkCulling;

    public FreeCam() {
        super(NameeProtectAddon.CATEGORY, "free-cam", "Flies the camera about on its own while your player stays put. It never moves or teleports you.");

        instance = this;

        // Only until a saved binding is loaded over it: cleared, it stays cleared.
        keybind.set(true, GLFW.GLFW_KEY_RIGHT_BRACKET, 0);
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // One camera at a time. Freelook puts the perspective back as it goes,
        // so this starts from the view you really have.
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) freeLook.toggle();

        // Where the camera starts is read from the camera itself on the first
        // frame it is asked for, so it picks up exactly where the view was.
        started = false;
        level = mc.world;
        velocityX = 0;
        velocityY = 0;
        velocityZ = 0;

        int chunks = Math.min(32, (int) Math.ceil(renderDistance.get() / 16.0));
        int own = mc.options.getViewDistance().getValue();

        if (chunks > own) {
            ownRenderDistance = own;
            mc.options.getViewDistance().setValue(chunks);
        }

        // Occlusion culling works out what is visible by walking outward from the
        // section the camera is in. Once the camera flies into solid ground that walk
        // cannot get out, and the world falls apart into floating scraps over the void.
        // Vanilla switches it off for spectators inside blocks; do the same while the
        // camera is out, and put it back afterwards.
        ownChunkCulling = mc.chunkCullingEnabled;
        mc.chunkCullingEnabled = false;
        mc.worldRenderer.scheduleTerrainUpdate();
    }

    @Override
    public void onDeactivate() {
        started = false;
        level = null;

        if (ownRenderDistance != null && mc.options != null) {
            mc.options.getViewDistance().setValue(ownRenderDistance);
            ownRenderDistance = null;
        }

        if (ownChunkCulling != null) {
            mc.chunkCullingEnabled = ownChunkCulling;
            ownChunkCulling = null;
            if (mc.worldRenderer != null) mc.worldRenderer.scheduleTerrainUpdate();
        }
    }

    /**
     * Whether the camera is somewhere the client has no terrain for.
     *
     * <p>This is not a rendering fault and cannot be fixed by rendering. The
     * client only holds the chunks the server chose to send, which are the ones
     * around <em>you</em>; fly the camera past that and there is no data to draw.
     * Asking for it would mean pretending to be somewhere else, which is the one
     * thing this module will not do.
     *
     * <p>Checked with load = false, so asking the question cannot itself pull a
     * chunk in.
     */
    private boolean outsideLoadedTerrain() {
        if (!started || mc.world == null) return false;

        int chunkX = ChunkSectionPos.getSectionCoord(x);
        int chunkZ = ChunkSectionPos.getSectionCoord(z);

        return mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null;
    }

    /**
     * A quiet line under the crosshair when there is nothing out there to draw.
     *
     * <p>Deliberately small and grey. It is telling you about the server's limit,
     * not reporting an error, and a loud warning for something you cannot do
     * anything about is just noise.
     */
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!outsideLoadedTerrain()) return;

        String text = "Outside loaded terrain";
        int width = mc.textRenderer.getWidth(text);

        event.drawContext.drawText(mc.textRenderer, text,
            (event.screenWidth - width) / 2,
            event.screenHeight / 2 + 18,
            0x9AB4BCC8, false);
    }

    /** Dying, or ending up in another world, ends it: the camera belongs to the place it was turned on in. */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world != level || mc.player.isDead()) toggle();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (mc.currentScreen != null) return;

        // Taken whether or not it changes anything, so that it never also walks
        // along the hotbar while the camera is out.
        event.cancel();

        if (!scrollSpeed.get()) return;

        speed.set(Math.max(0.5, Math.min(200, speed.get() * Math.pow(1 + scrollStep.get(), event.value))));
    }

    // ------------------------------------------------------ for the mixins

    /** Whether the camera is out, and so whether the player's controls are the camera's. */
    public static boolean active() {
        FreeCam module = instance;

        return module != null && module.isActive();
    }

    /** Puts the camera somewhere directly (it must already be out), e.g. for tests or commands. */
    public static void placeCamera(double x, double y, double z, float yaw, float pitch) {
        FreeCam module = instance;
        if (module == null || !module.isActive()) return;

        module.x = x;
        module.y = y;
        module.z = z;
        module.yaw = yaw;
        module.pitch = pitch;
        module.velocityX = 0;
        module.velocityY = 0;
        module.velocityZ = 0;
        module.lastAt = System.nanoTime();
        module.started = true;
    }

    /** The mouse, which while the camera is out turns the camera and not you. */
    public static void look(double byX, double byY) {
        FreeCam module = instance;
        if (module == null) return;

        float turn = (float) (double) module.lookSpeed.get();

        module.yaw += (float) byX * turn;
        module.pitch = Math.max(-90, Math.min(90, module.pitch + (float) byY * turn));
    }

    /**
     * Called once a frame, after the game has put the camera where it thinks it
     * belongs: moves this camera on by however long the frame took, and says
     * where it now is. Null while it is off.
     */
    public static double[] frame(Camera camera) {
        FreeCam module = instance;

        if (module == null || !module.isActive()) return null;

        return module.advance(camera);
    }

    private double[] advance(Camera camera) {
        long now = System.nanoTime();

        if (!started) {
            Vec3d at = camera.getCameraPos();

            x = at.x;
            y = at.y;
            z = at.z;
            yaw = camera.getYaw();
            pitch = camera.getPitch();
            lastAt = now;
            started = true;
        }

        double seconds = Math.min((now - lastAt) / 1.0e9, 0.1);

        lastAt = now;

        // The keys themselves, since the player no longer gets them. With a
        // screen open they are being typed into it, not flown with.
        boolean flying = mc.currentScreen == null;

        double ahead = flying ? axis(mc.options.forwardKey.isPressed(), mc.options.backKey.isPressed()) : 0;
        double across = flying ? axis(mc.options.rightKey.isPressed(), mc.options.leftKey.isPressed()) : 0;
        double up = flying ? axis(mc.options.jumpKey.isPressed(), mc.options.sneakKey.isPressed()) : 0;

        // Along the ground the way the camera faces, and straight up and down:
        // looking at your feet and pressing forward does not dig you in.
        double facing = Math.toRadians(yaw);
        double wishX = -Math.sin(facing) * ahead - Math.cos(facing) * across;
        double wishZ = Math.cos(facing) * ahead - Math.sin(facing) * across;
        double length = Math.sqrt(wishX * wishX + up * up + wishZ * wishZ);

        if (length > 0) {
            double pace = speed.get() * (flying && mc.options.sprintKey.isPressed() ? sprintMultiplier.get() : 1) / length;

            // Into a move the glide is a third as long as out of one: a camera
            // that takes a second to get going feels broken, one that takes a
            // second to settle feels smooth. Both are by the clock.
            double kept = Math.pow(smoothing.get(), seconds * 180);

            velocityX = wishX * pace + (velocityX - wishX * pace) * kept;
            velocityY = up * pace + (velocityY - up * pace) * kept;
            velocityZ = wishZ * pace + (velocityZ - wishZ * pace) * kept;
        } else if (holdPosition.get()) {
            velocityX = 0;
            velocityY = 0;
            velocityZ = 0;
        } else {
            double kept = Math.pow(smoothing.get(), seconds * 60);

            velocityX *= kept;
            velocityY *= kept;
            velocityZ *= kept;
        }

        x += velocityX * seconds;
        y += velocityY * seconds;
        z += velocityZ * seconds;

        return new double[] {x, y, z, yaw, pitch};
    }

    private static double axis(boolean more, boolean less) {
        return (more ? 1 : 0) - (less ? 1 : 0);
    }
}
