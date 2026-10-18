package dev.maro.mixin;
import net.minecraft.client.realms.RealmsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(RealmsClient.class)
public interface AccountRealmsAccessor {
    @Accessor("instance") static void maro$setInstance(RealmsClient value){throw new AssertionError();}
}
