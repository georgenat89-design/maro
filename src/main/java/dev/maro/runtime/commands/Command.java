package dev.maro.runtime.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
public abstract class Command {
 public static final int SINGLE_SUCCESS=1;public final String name;
 protected Command(String name,String description){this.name=name;}
 public static LiteralArgumentBuilder<CommandSource> literal(String name){return LiteralArgumentBuilder.literal(name);}
 public abstract void build(LiteralArgumentBuilder<CommandSource> builder);
 public void error(String message){warning(message);}
 public void warning(String message){var player=net.minecraft.client.MinecraftClient.getInstance().player;if(player!=null)player.sendMessage(net.minecraft.text.Text.literal("[Maro] "+message),false);}
}
