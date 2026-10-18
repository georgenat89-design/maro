package dev.maro.mixin;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ConnectScreen.class)
public interface MaroConnectAccessor {
    @Accessor("status") Text maro$getStatus();
    @Accessor("lastNarrationTime") long maro$getLastNarrationTime();
    @Accessor("lastNarrationTime") void maro$setLastNarrationTime(long time);
}
