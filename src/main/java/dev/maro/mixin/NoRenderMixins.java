package dev.maro.mixin;

import dev.maro.module.impl.visuals.NoRender;
import dev.maro.module.impl.visuals.NoRender.Part;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The hooks behind {@link NoRender}: each one skips drawing a thing while its switch is on. */
public final class NoRenderMixins {
    private NoRenderMixins() {
    }

    /** Fire, underwater and in-wall overlays, drawn over the view of the world. */
    @Mixin(InGameOverlayRenderer.class)
    public abstract static class Overlays {
        @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
        private static void maro$fire(CallbackInfo ci) {
            if (NoRender.hides(Part.FIRE)) ci.cancel();
        }

        @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
        private static void maro$underwater(CallbackInfo ci) {
            if (NoRender.hides(Part.UNDERWATER)) ci.cancel();
        }

        @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
        private static void maro$inWall(CallbackInfo ci) {
            if (NoRender.hides(Part.IN_WALL)) ci.cancel();
        }
    }

    /** Screen overlays and HUD parts drawn by the in-game HUD. */
    @Mixin(InGameHud.class)
    public abstract static class Hud {
        @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
        private void maro$vignette(CallbackInfo ci) {
            if (NoRender.hides(Part.VIGNETTE)) ci.cancel();
        }

        @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
        private void maro$portal(CallbackInfo ci) {
            if (NoRender.hides(Part.PORTAL)) ci.cancel();
        }

        @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
        private void maro$nausea(CallbackInfo ci) {
            if (NoRender.hides(Part.NAUSEA)) ci.cancel();
        }

        @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
        private void maro$potionIcons(CallbackInfo ci) {
            if (NoRender.hides(Part.POTION_ICONS)) ci.cancel();
        }

        @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
                at = @At("HEAD"), cancellable = true)
        private void maro$scoreboard(CallbackInfo ci) {
            if (NoRender.hides(Part.SCOREBOARD)) ci.cancel();
        }

        /** The textured overlays: the pumpkin blur and the powder snow frost both come through here. */
        @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
        private void maro$texturedOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
            String path = texture.getPath();
            if (path.contains("pumpkinblur") && NoRender.hides(Part.PUMPKIN)) ci.cancel();
            else if (path.contains("powder_snow") && NoRender.hides(Part.POWDER_SNOW)) ci.cancel();
        }
    }

    @Mixin(BossBarHud.class)
    public abstract static class BossBars {
        @Inject(method = "render", at = @At("HEAD"), cancellable = true)
        private void maro$bossBar(CallbackInfo ci) {
            if (NoRender.hides(Part.BOSS_BAR)) ci.cancel();
        }
    }

    /** Camera shake when hurt, and the totem that fills the screen when one pops. */
    @Mixin(GameRenderer.class)
    public abstract static class Camera {
        @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
        private void maro$hurtCamera(CallbackInfo ci) {
            if (NoRender.hides(Part.HURT_CAMERA)) ci.cancel();
        }

        @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
        private void maro$totem(ItemStack stack, CallbackInfo ci) {
            if (stack.isOf(Items.TOTEM_OF_UNDYING) && NoRender.hides(Part.TOTEM)) ci.cancel();
        }
    }

    /** Rain and snow, and the splashes and sound that go with them. */
    @Mixin(WeatherRendering.class)
    public abstract static class Weather {
        @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true)
        private void maro$precipitation(CallbackInfo ci) {
            if (NoRender.hides(Part.WEATHER)) ci.cancel();
        }

        @Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true)
        private void maro$splashes(CallbackInfo ci) {
            if (NoRender.hides(Part.WEATHER)) ci.cancel();
        }
    }

    @Mixin(ParticleManager.class)
    public abstract static class Particles {
        @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
                at = @At("HEAD"), cancellable = true)
        private void maro$explosions(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz,
                                     CallbackInfoReturnable<Particle> cir) {
            if (NoRender.hides(Part.EXPLOSIONS)
                    && (effect.getType() == ParticleTypes.EXPLOSION || effect.getType() == ParticleTypes.EXPLOSION_EMITTER)) {
                cir.setReturnValue(null);
            }
        }
    }

    @Mixin(ClientWorld.class)
    public abstract static class MiningParticles {
        @Inject(method = "spawnBlockBreakingParticle", at = @At("HEAD"), cancellable = true)
        private void maro$miningParticles(CallbackInfo ci) {
            if (NoRender.hides(Part.MINING_PARTICLES)) ci.cancel();
        }
    }

    /** Whole kinds of entity, left out before they are drawn at all. */
    @Mixin(EntityRenderManager.class)
    public abstract static class Entities {
        @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
        private void maro$entities(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
            if (hidden(entity)) cir.setReturnValue(false);
        }

        private static boolean hidden(Entity entity) {
            if (entity instanceof ItemEntity) return NoRender.hides(Part.DROPPED_ITEMS);
            if (entity instanceof ExperienceOrbEntity) return NoRender.hides(Part.XP_ORBS);
            if (entity instanceof ArmorStandEntity) return NoRender.hides(Part.ARMOR_STANDS);
            if (entity instanceof FallingBlockEntity) return NoRender.hides(Part.FALLING_BLOCKS);
            if (entity instanceof FireworkRocketEntity) return NoRender.hides(Part.FIREWORKS);
            if (entity instanceof ItemFrameEntity) return NoRender.hides(Part.ITEM_FRAMES);
            return false;
        }
    }
}
