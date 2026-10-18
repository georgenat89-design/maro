package dev.maro.gametest.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.*;

public final class PrivacyProbe {
    @Mixin(ClientPlayNetworkHandler.class)
    public interface Roster {@Accessor("playerListEntries")Map<UUID,PlayerListEntry> maro$entries();}
    @Mixin(ChatHud.class)
    public interface Chat {
        @Accessor("visibleMessages")List<ChatHudLine.Visible> maro$visible();
        @Accessor("messages")List<ChatHudLine> maro$messages();
    }
}
