package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.EmoteWheelScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.emote.Emote;
import dev.maro.render.emote.EmoteRenderState;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.KeybindSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.KeyUtil;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Emotes: hold the wheel key, point at an emote and let go, and your player dances, waves, dabs or
 * sits. The camera swings round to watch while it plays (the front, by default) and comes back when
 * it ends; moving stops it. It is drawn on your own player model, so it is for you, your
 * screenshots and recordings: other players do not see it.
 */
public class Emotes extends Module {
    public static final String CAMERA_FRONT = "Front", CAMERA_BACK = "Back", CAMERA_KEEP = "Keep";
    /** Seconds to ease into an emote, and out of one that has finished. */
    private static final float EASE_IN = 0.18f, EASE_OUT = 0.28f;

    private static Emotes instance;

    private final KeybindSetting wheelKey = add(new KeybindSetting("Wheel Key", "Hold to open the emote wheel, let go over an emote to play it", GLFW.GLFW_KEY_B));
    private final ModeSetting camera = add(new ModeSetting("Camera", "Where the camera watches from while an emote plays", CAMERA_FRONT,
            CAMERA_FRONT, CAMERA_BACK, CAMERA_KEEP));
    private final BooleanSetting stopOnMove = add(new BooleanSetting("Stop When Moving", "Moving, jumping or sneaking ends the emote", true));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast emotes play", 1, 0.5, 2, 0.05).suffix("x"));

    private Emote playing;
    private long startedAt, stoppingAt = -1;
    private Perspective savedPerspective;
    private boolean keyWasDown;

    public Emotes() {
        super("Emotes", "An emote wheel: wave, dab, floss, dance and more on your own player", Category.VISUALS);
        instance = this;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Emotes", wheelKey, camera, stopOnMove, speed));
    }

    @Override
    protected void onDisable() {
        stopNow();
    }

    @Override
    public void onTick() {
        if (!inGame()) {
            stopNow();
            return;
        }
        // The wheel opens as the key goes down, outside any menu.
        boolean down = isDown(wheelKey.get());
        if (down && !keyWasDown && mc.currentScreen == null) mc.setScreen(new EmoteWheelScreen(this));
        keyWasDown = down;

        if (playing == null) return;
        if (stoppingAt < 0 && stopOnMove.get() && moving()) stop();
        if (stoppingAt < 0 && !playing.loops() && time() > playing.length()) stop();
        if (stoppingAt >= 0 && (System.nanoTime() - stoppingAt) / 1e9 > EASE_OUT) stopNow();
    }

    /** Whether the wheel key is held, for the wheel to know when it is let go. */
    public boolean wheelKeyDown() {
        return isDown(wheelKey.get());
    }

    private boolean isDown(int code) {
        if (code == KeyUtil.NONE) return false;
        long window = mc.getWindow().getHandle();
        if (KeyUtil.isMouse(code)) return GLFW.glfwGetMouseButton(window, code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }

    private boolean moving() {
        var o = mc.options;
        return o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed()
                || o.jumpKey.isPressed() || o.sneakKey.isPressed();
    }

    // ---- playing --------------------------------------------------------------------------

    public void play(Emote emote) {
        if (emote == null) return;
        if (playing == null && !camera.is(CAMERA_KEEP)) {
            savedPerspective = mc.options.getPerspective();
            mc.options.setPerspective(camera.is(CAMERA_BACK) ? Perspective.THIRD_PERSON_BACK : Perspective.THIRD_PERSON_FRONT);
        }
        playing = emote;
        startedAt = System.nanoTime();
        stoppingAt = -1;
    }

    /** Eases out of the emote that is playing. */
    public void stop() {
        if (playing != null && stoppingAt < 0) stoppingAt = System.nanoTime();
    }

    private void stopNow() {
        playing = null;
        stoppingAt = -1;
        if (savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
    }

    public Emote playing() {
        return playing;
    }

    /** Seconds into the emote, at the chosen speed. */
    private float time() {
        return (float) ((System.nanoTime() - startedAt) / 1e9 * speed.get());
    }

    /** How far the model is into the emote's pose: easing in at the start and out at the end. */
    private float blend() {
        float in = Math.min(1f, (float) ((System.nanoTime() - startedAt) / 1e9 / EASE_IN));
        float out = stoppingAt < 0 ? 1f : Math.max(0f, 1f - (float) ((System.nanoTime() - stoppingAt) / 1e9 / EASE_OUT));
        float b = Math.min(in, out);
        return b * b * (3 - 2 * b);
    }

    /**
     * Called as each entity's render state is filled in: your own player gets the emote that is
     * playing, everyone and everything else none.
     */
    public static void stamp(EntityRenderState state, Entity entity) {
        if (!(state instanceof EmoteRenderState emote)) return;
        Emotes m = instance;
        if (m != null && m.isEnabled() && m.playing != null && entity == mc.player) {
            float time = m.time();
            emote.maro$setEmote(m.playing, m.playing.loops() ? time : Math.min(time, m.playing.length()), m.blend());
        } else {
            emote.maro$setEmote(null, 0, 0);
        }
    }
}
