package dev.maro.module.impl.movement;

import dev.maro.setting.BooleanSetting;

/** Adapted from Anubis BoatFlyModule. */
public final class BoatFly extends BoatControl {
    private final BooleanSetting cancelPackets = add(new BooleanSetting("Cancel Server Packets", "Ignore vehicle setbacks while Boat Fly is on", false));
    public BoatFly() { super("Boat Fly", "Fly your boat or ridden vehicle; use with anti-cheat switched off"); }
    @Override public boolean cancelCorrection() { return cancelPackets.get(); }
}
