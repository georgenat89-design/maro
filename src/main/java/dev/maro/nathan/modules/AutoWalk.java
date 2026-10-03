package dev.maro.nathan.modules;

import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;

import dev.maro.runtime.event.EventHandler;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Holds the forward key down for you, and the sprint key with it.
 *
 * <p>Both are pressed the way the keyboard presses them, rather than by setting
 * the sprinting flag on the player directly. That leaves every one of the
 * game's own rules in place - it will not sprint you on an empty stomach, out
 * of water, or while you are sneaking or eating - so what the server is told
 * about your movement is exactly what it would be told if you were holding the
 * keys yourself.
 *
 * <p>It sits in this addon's own category rather than Meteor's Movement one,
 * where it was a single line among thirty of Meteor's and directly beneath
 * Meteor's own Auto Walk. The name is spelt the way this addon spells
 * {@code nameeprotect}, which also keeps it clear of that module: Meteor
 * replaces any module whose name matches one of its own, so {@code auto-walk}
 * would have quietly removed it.
 */
public class AutoWalk extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> sprint = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint")
        .description("Hold sprint as well, so you run rather than walk.")
        .defaultValue(true)
        .build()
    );

    /**
     * Whether the sprint key is down because this module put it there, and so
     * is this module's to let go of. Without it, turning Sprint off - or
     * turning the module off - would release a sprint key you were holding
     * yourself, and you would have to press it again.
     */
    private boolean holdingSprint;

    public AutoWalk() {
        super(NameeProtectAddon.CATEGORY, "autoowalk", "Holds forward for you, and sprint with it.");
    }

    @Override
    public void onDeactivate() {
        release();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Leaving a world with the keys still held would carry that state into
        // the next one, where nothing is pressing them any more.
        if (mc.player == null) {
            release();
            return;
        }

        mc.options.forwardKey.setPressed(true);

        if (sprint.get()) {
            mc.options.sprintKey.setPressed(true);
            holdingSprint = true;
        } else {
            releaseSprint();
        }
    }

    private void release() {
        mc.options.forwardKey.setPressed(false);
        releaseSprint();
    }

    private void releaseSprint() {
        if (!holdingSprint) return;

        mc.options.sprintKey.setPressed(false);
        holdingSprint = false;

        // Letting go of the key is not enough on its own. The game ends a
        // sprint when the forward impulse stops, when you cannot sprint any
        // more, or when you hit something - never because the key came up. This
        // module holds forward down, so none of those ever happen and the
        // sprint would simply carry on after the setting was turned off.
        if (mc.player != null) mc.player.setSprinting(false);
    }
}
