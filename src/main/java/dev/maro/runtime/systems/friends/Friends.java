package dev.maro.runtime.systems.friends;

import dev.maro.config.FriendManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
public final class Friends { private static final Friends INSTANCE=new Friends();public static Friends get(){return INSTANCE;}public boolean isFriend(AbstractClientPlayerEntity player){return FriendManager.isFriend(player.getName().getString());} }
