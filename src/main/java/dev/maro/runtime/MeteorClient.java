package dev.maro.runtime;

import java.io.File;
import net.minecraft.client.MinecraftClient;
import dev.maro.config.ConfigManager;
import dev.maro.runtime.event.EventBus;
/** Shared client services for the imported source; no Meteor Client dependency. */
public final class MeteorClient {
 public static final MinecraftClient mc = MinecraftClient.getInstance();
 public static final File FOLDER = ConfigManager.DIR.toFile();
 public static final EventBus EVENT_BUS = new EventBus();
}
