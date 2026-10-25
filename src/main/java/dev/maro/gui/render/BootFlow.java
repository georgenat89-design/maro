package dev.maro.gui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.util.Util;

/** Keeps connection, configuration, terrain and first-world frames in one visual session. */
public final class BootFlow {
    private static boolean active;
    private static long handoff = -1, fade = -1;
    private static String status = "Entering your world";
    private BootFlow() { }
    public static boolean isLoading(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof MessageScreen
                || screen instanceof ReconfiguringScreen || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen;
    }
    public static boolean isConnection(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof MessageScreen || screen instanceof ReconfiguringScreen;
    }
    public static void screenChanged(Screen screen) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        if (isLoading(screen)) {
            active = true; handoff = fade = -1;
        } else if (screen != null) {
            active = false; handoff = fade = -1;
        } else if (active && handoff < 0) handoff = Util.getMeasuringTimeMs();
    }
    public static void draw(DrawContext ctx, int w, int h, float fill, String message) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        active = true;
        status = message;
        BootScene.loading(ctx, w, h, fill < 0 ? -1 : Math.min(1, fill), message, 1, false);
    }
    public static void finishFrame(DrawContext ctx) {
        if (dev.maro.render.capture.CapturePass.active()) return;
        var client = MinecraftClient.getInstance();
        if (!active || client.currentScreen != null || client.getOverlay() != null) return;
        long now = Util.getMeasuringTimeMs();
        if (handoff < 0) handoff = now;
        boolean ready = client.world != null && client.player != null
                && (client.worldRenderer.getCompletedChunkCount() > 0 || now - handoff > 1000);
        // An empty pre-world frame stays covered. Errors and Cancel clear state in screenChanged.
        if (ready && fade < 0) fade = now;
        float opacity = fade < 0 ? 1 : Math.max(0, 1 - (now - fade) / 400f);
        opacity = opacity * opacity * (3 - 2 * opacity);
        if (opacity <= 0) { active = false; return; }
        ctx.createNewRootLayer();
        BootScene.loading(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), ready ? 1 : -1, status, opacity, false);
    }
}
