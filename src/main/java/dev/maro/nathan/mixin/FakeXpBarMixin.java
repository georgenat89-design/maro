package dev.maro.nathan.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.maro.nathan.modules.FakeXp;
import net.minecraft.client.gui.hud.bar.ExperienceBar;

/**
 * How full the experience bar looks.
 *
 * <p><b>Why here.</b> In 1.21.11 the bar moved out of {@code Gui} into the
 * contextual bar renderers, and {@code ExperienceBarRenderer.render} is empty -
 * everything is drawn by {@code renderBackground}, which works the fill out as
 * {@code (int) (player.experienceProgress * 183.0f)}. Modifying that one field
 * read changes the width of the filled part and nothing else in the method.
 *
 * <p>The read is modified rather than the player being written to, so the real
 * {@code experienceProgress} is never touched and an incoming XP update cannot
 * disturb what is shown.
 */
@Mixin(ExperienceBar.class)
public class FakeXpBarMixin {
    @ModifyExpressionValue(
        method = "renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;experienceProgress:F",
            opcode = Opcodes.GETFIELD))
    private float nameeprotect$fakeProgress(float real) {
        return FakeXp.progressFor(real);
    }
}
