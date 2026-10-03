package dev.maro.runtime.commands;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
public final class Commands {
 @SuppressWarnings({"rawtypes","unchecked"}) public static void add(Command command){ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{var builder=ClientCommandManager.literal(command.name);command.build((com.mojang.brigadier.builder.LiteralArgumentBuilder)builder);dispatcher.register(builder);});}
}
