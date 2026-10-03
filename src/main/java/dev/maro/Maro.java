package dev.maro;

import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.theme.Theme;
import dev.maro.module.ModuleManager;
import dev.maro.util.KeyUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import dev.maro.module.impl.misc.ScreenHider;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Maro implements ClientModInitializer {
    public static final String MOD_ID = "maro";
    public static final String NAME = "maro.gg";
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        ConfigManager.init();
        dev.maro.runtime.commands.Commands.add(new dev.maro.nathan.commands.BloomCommand());

        ClientTickEvents.START_CLIENT_TICK.register(client -> dev.maro.runtime.RuntimeEvents.tickPre());
        ClientTickEvents.END_CLIENT_TICK.register(client -> { ModuleManager.onTick(); dev.maro.runtime.RuntimeEvents.tickPost(); });
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), (context, tickCounter) -> {
            Theme.update();
            ModuleManager.onRender2D(context, tickCounter.getTickProgress(false));
            dev.maro.runtime.RuntimeEvents.hud(context);
            if (!(MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen)) Notifications.render(context);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { ConfigManager.saveAll(); dev.maro.runtime.RuntimeEvents.shutdown(); });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> ScreenHider.renderOver(s, context)));

        LOGGER.info("{} {} loaded", NAME, VERSION);
    }

    /** Called from {@link dev.maro.mixin.KeyboardMixin}. */
    public static void onKey(int key, int action) {
        if (action != GLFW.GLFW_PRESS || key == GLFW.GLFW_KEY_UNKNOWN) return;
        handleBind(key);
    }

    /** Called from {@link dev.maro.mixin.MouseMixin}. */
    public static void onMouseButton(int button, int action) {
        if (action != GLFW.GLFW_PRESS) return;
        handleBind(KeyUtil.mouse(button));
    }

    private static void handleBind(int code) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || mc.player == null) return;
        if (ClientSettings.guiBind.matches(code)) {
            mc.setScreen(new ClickGuiScreen());
            return;
        }
        ModuleManager.onBind(code);
    }
}
