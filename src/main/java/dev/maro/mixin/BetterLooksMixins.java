package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.module.impl.visuals.BetterLooks;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.client.render.fog.WaterFogModifier;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

/** The hooks behind {@link BetterLooks}; each changes only what is drawn, and only while its switch is on. */
public final class BetterLooksMixins {
    private BetterLooksMixins() {
    }

    /** No Sun & Moon, No Stars, No Sunrise Glow. */
    @Mixin(SkyRendering.class)
    public abstract static class Sky {
        @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
        private void maro$sun(CallbackInfo ci) {
            if (BetterLooks.noSunMoon()) ci.cancel();
        }

        @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
        private void maro$moon(CallbackInfo ci) {
            if (BetterLooks.noSunMoon()) ci.cancel();
        }

        @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
        private void maro$stars(CallbackInfo ci) {
            if (BetterLooks.noStars()) ci.cancel();
        }

        @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
        private void maro$sunrise(CallbackInfo ci) {
            if (BetterLooks.noSunrise()) ci.cancel();
        }
    }

    @Mixin(WorldRenderer.class)
    public abstract static class Clouds {
        @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
        private void maro$clouds(CallbackInfo ci) {
            if (BetterLooks.noClouds()) ci.cancel();
        }
    }

    /** No Rain Gloom: in the world you see, it is never raining hard enough to darken anything. */
    @Mixin(World.class)
    public abstract static class Rain {
        @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
        private void maro$rain(float delta, CallbackInfoReturnable<Float> cir) {
            if ((Object) this instanceof ClientWorld && BetterLooks.noRainGloom()) cir.setReturnValue(0f);
        }

        @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
        private void maro$thunder(float delta, CallbackInfoReturnable<Float> cir) {
            if ((Object) this instanceof ClientWorld && BetterLooks.noRainGloom()) cir.setReturnValue(0f);
        }
    }

    /** Bright Underwater: full sight underwater from the first moment. */
    @Mixin(ClientPlayerEntity.class)
    public abstract static class Underwater {
        @Inject(method = "getUnderwaterVisibility", at = @At("HEAD"), cancellable = true)
        private void maro$visibility(CallbackInfoReturnable<Float> cir) {
            if (BetterLooks.brightUnderwater()) cir.setReturnValue(1f);
        }
    }

    @Mixin(ItemStack.class)
    public abstract static class Glint {
        @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
        private void maro$glint(CallbackInfoReturnable<Boolean> cir) {
            Boolean glint = BetterLooks.glint();
            if (glint != null) cir.setReturnValue(glint);
        }
    }

    /** Transparent Inv BG and No Menu Blur. */
    @Mixin(Screen.class)
    public abstract static class Menus {
        @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
        private void maro$shade(DrawContext context, CallbackInfo ci) {
            if (BetterLooks.transparentInvBg()) ci.cancel();
        }

        @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true)
        private void maro$blur(DrawContext context, CallbackInfo ci) {
            if (BetterLooks.noMenuBlur()) ci.cancel();
        }
    }

    /** Clear Water: underwater, the fog starts as far off as on land. */
    @Mixin(WaterFogModifier.class)
    public abstract static class WaterFog {
        @Inject(method = "applyStartEndModifier", at = @At("TAIL"))
        private void maro$clearWater(FogData data, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
            if (!BetterLooks.clearWater()) return;
            data.environmentalEnd = Math.max(data.environmentalEnd, Math.max(96f, viewDistance));
            data.skyEnd = data.environmentalEnd;
            data.cloudEnd = data.environmentalEnd;
        }
    }

    /** Clear Lava: see through lava as far as with fire resistance and then some. */
    @Mixin(LavaFogModifier.class)
    public abstract static class LavaFog {
        @Inject(method = "applyStartEndModifier", at = @At("TAIL"))
        private void maro$clearLava(FogData data, Camera camera, ClientWorld world, float viewDistance, RenderTickCounter tickCounter, CallbackInfo ci) {
            if (!BetterLooks.clearLava()) return;
            data.environmentalStart = 0;
            data.environmentalEnd = Math.max(data.environmentalEnd, 24f);
            data.skyEnd = data.environmentalEnd;
            data.cloudEnd = data.environmentalEnd;
        }
    }

    /** No Blindness Fog: Blindness and Darkness leave the fog alone. */
    @Mixin(StatusEffectFogModifier.class)
    public abstract static class EffectFog {
        @Inject(method = "shouldApply", at = @At("HEAD"), cancellable = true)
        private void maro$noBlindFog(CameraSubmersionType submersion, Entity entity, CallbackInfoReturnable<Boolean> cir) {
            if (BetterLooks.noBlindFog()) cir.setReturnValue(false);
        }
    }

    /** No Fog: in the open air, far land is drawn with no haze over it. */
    @Mixin(FogRenderer.class)
    public abstract static class NoFog {
        @WrapOperation(method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
        private void maro$noFog(FogRenderer self, ByteBuffer buffer, int index, Vector4f color, float envStart, float envEnd, float rdStart, float rdEnd,
                                float skyEnd, float cloudEnd, Operation<Void> original) {
            if (BetterLooks.noFog() && MinecraftClient.getInstance().gameRenderer.getCamera().getSubmersionType() == CameraSubmersionType.NONE) {
                float far = 1.0e6f;
                envStart = far;
                envEnd = far;
                rdStart = far;
                rdEnd = far;
            }
            original.call(self, buffer, index, color, envStart, envEnd, rdStart, rdEnd, skyEnd, cloudEnd);
        }
    }

    /** No Entity Shadows: the shadow worked out for each entity is thrown away before it is drawn. */
    @Mixin(EntityRenderer.class)
    public abstract static class Shadows {
        @Inject(method = "updateShadow(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;)V", at = @At("TAIL"))
        private void maro$shadow(Entity entity, EntityRenderState state, CallbackInfo ci) {
            if (BetterLooks.noShadows()) clear(state);
        }

        @Inject(method = "updateShadow(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/world/World;)V", at = @At("TAIL"))
        private void maro$shadowPieces(EntityRenderState state, MinecraftClient client, World world, CallbackInfo ci) {
            if (BetterLooks.noShadows()) clear(state);
        }

        private static void clear(EntityRenderState state) {
            state.shadowRadius = 0;
            state.shadowPieces.clear();
        }
    }

    /** No Item Bobbing: the view still bobs as you walk, the hand drawn over it does not. */
    @Mixin(GameRenderer.class)
    public abstract static class HandBob {
        @WrapOperation(method = "renderHand", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/client/render/GameRenderer;bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V"))
        private void maro$noItemBob(GameRenderer self, MatrixStack matrices, float tickProgress, Operation<Void> original) {
            if (!BetterLooks.noItemBob()) original.call(self, matrices, tickProgress);
        }
    }

    @Mixin(ToastManager.class)
    public abstract static class Toasts {
        @Inject(method = "add", at = @At("HEAD"), cancellable = true)
        private void maro$toast(Toast toast, CallbackInfo ci) {
            if (BetterLooks.noToasts()) ci.cancel();
        }
    }
}
