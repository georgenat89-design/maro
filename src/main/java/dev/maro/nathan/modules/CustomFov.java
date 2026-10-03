package dev.maro.nathan.modules;

import dev.maro.runtime.events.render.GetFovEvent;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;

import dev.maro.runtime.event.EventHandler;
import dev.maro.runtime.event.EventPriority;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Your own field of view, past where the game's slider stops.
 *
 * <p><b>It changes the picture, not your options.</b> The game works out the
 * field of view for every frame, and Meteor lets a module see that answer and
 * change it on the way to the camera. That is all this does: it never writes to
 * the game's own FOV option. So there is nothing to put back. Turn it off and the
 * very next frame is worked out from your own setting, which was never touched,
 * however many times it has been turned on and off.
 *
 * <p><b>What is kept.</b> The game's answer is not only your setting: sprinting,
 * a speed potion, drawing a bow and being under water each widen or narrow it.
 * Those are kept. The answer is read as "your setting, times so much", and what
 * is handed on is "this setting, times the same" - so with this on you still get
 * the kick of a sprint, from the field of view you chose.
 *
 * <p><b>With Key Zoom.</b> Both listen to the same answer, and this one is
 * asked first, on purpose. By the time Key Zoom sees the field of view it is
 * already the custom one, so it zooms from there and, when the key is let go,
 * goes back to there - because "there" is worked out afresh every frame, not
 * remembered from before the zoom.
 *
 * <p><b>With F5, Freelook and Free Cam.</b> Those move the camera. This is how
 * wide the camera sees, which is the same question wherever it is, and is
 * answered the same way.
 *
 * <p><b>Why 155.</b> A flat picture cannot show 180 degrees: the edges run off
 * to infinity, and well before that the middle of the screen is a speck and the
 * rim is smeared out. 155 is the most that is still a picture. It is the most
 * the slider goes to, the most that can be typed, the most a saved value is
 * allowed to load as, and - since sprinting widens it further - the most that is
 * ever handed to the camera, whatever the sums come to.
 */
public class CustomFov extends Module {
    /** The most a field of view is ever allowed to be, by any route. */
    public static final int MOST = 155;

    private static final int LEAST = 10;

    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Integer> fov = sgMain.add(new IntSetting.Builder()
        .name("fov")
        .description("The field of view, in degrees. The game's own slider stops at 110; this goes to 155, and no further.")
        .defaultValue(100)
        .range(LEAST, MOST)
        .sliderRange(30, MOST)
        .build()
    );

    public CustomFov() {
        super(NameeProtectAddon.CATEGORY, "custom-fov", "Overrides the field of view, up to 155 degrees, without touching your game options.");
    }

    // Asked before Key Zoom, which listens at the ordinary priority, so that it
    // zooms from the custom field of view and back to it.
    @EventHandler(priority = EventPriority.HIGH)
    private void onGetFov(GetFovEvent event) {
        if (mc.options == null) return;

        double own = mc.options.getFov().getValue();
        if (own <= 0) return;

        // Whatever is in the setting, however it got there.
        int wanted = Math.max(LEAST, Math.min(MOST, fov.get()));

        // The game's answer as so many times your own setting - sprint, potions,
        // water and all - and the same number of times the custom one.
        double widened = wanted * (event.fov / own);

        event.fov = (float) Math.max(1, Math.min(MOST, widened));
    }
}
