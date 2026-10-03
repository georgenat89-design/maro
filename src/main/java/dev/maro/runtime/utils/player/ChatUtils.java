package dev.maro.runtime.utils.player;

import net.minecraft.client.MinecraftClient;
public final class ChatUtils { public static void sendPlayerMsg(String message){var client=MinecraftClient.getInstance();if(client.getNetworkHandler()==null)return;if(message.startsWith("/"))client.getNetworkHandler().sendChatCommand(message.substring(1));else client.getNetworkHandler().sendChatMessage(message);} }
