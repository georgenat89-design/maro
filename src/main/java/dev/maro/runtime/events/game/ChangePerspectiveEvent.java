package dev.maro.runtime.events.game;

import net.minecraft.client.option.Perspective;
public final class ChangePerspectiveEvent extends dev.maro.runtime.events.Cancellable { public final Perspective perspective; public ChangePerspectiveEvent(Perspective p) { perspective = p; } }
