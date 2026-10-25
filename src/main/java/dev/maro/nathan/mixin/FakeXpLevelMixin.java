package dev.maro.nathan.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.maro.nathan.modules.FakeXp;
import net.minecraft.client.gui.hud.InGameHud;

/**
 * The level number over the bar.
 *
 * <p><b>Why here.</b> {@code Gui.renderHotbarAndDecorations} is the only method
 * in {@code Gui} that reads {@code LocalPlayer.experienceLevel}, and it reads it
 * twice: once to decide whether to draw a number at all, and once for the number
 * itself. No ordinal is given, so both reads are replaced - which is what keeps
 * the two consistent. A fake level of 0 therefore draws nothing, exactly as
 * vanilla does at level 0, rather than drawing a "0" the gate would have skipped.
 *
 * <p><b>Centring.</b> Left to vanilla. It positions the text at
 * {@code (width - font.width(text)) / 2}, measured from the string it is about to
 * draw, so a ten digit level centres on the same rule a one digit level does and
 * simply occupies more room. Nothing clips it: the level is drawn with
 * {@code drawString}, which has no scissor of its own.
 */
@Mixin(InGameHud.class)
public class FakeXpLevelMixin {
    @WrapOperation(method="renderMainHud(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V"))
    private void maro$clientLevel(net.minecraft.client.gui.DrawContext context,net.minecraft.client.font.TextRenderer renderer,int level,Operation<Void> original){
        var module=dev.maro.module.ModuleManager.get(FakeXp.class);
        if(dev.maro.gui.render.OwnedHudText.enabled()&&module!=null&&module.isEnabled())
            dev.maro.gui.render.OwnedHudText.experienceLevel(context,level,context.getScaledWindowWidth()/2f,context.getScaledWindowHeight()-35);
        else original.call(context,renderer,level);
    }
    @ModifyExpressionValue(
        method = "renderMainHud(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;experienceLevel:I",
            opcode = Opcodes.GETFIELD))
    private int nameeprotect$fakeLevel(int real) {
        return FakeXp.levelFor(real);
    }
}
