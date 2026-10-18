package dev.maro.gametest.mixin;
import dev.maro.gametest.FakePayChecks;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(InGameHud.class)
public abstract class FakePaySidebarProbe {
    @ModifyArg(method="renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"),index=1)
    private Text maroTest$row(Text row) {
        if(FakePayChecks.observe)FakePayChecks.rendered.addIfAbsent(row.getString());
        return row;
    }
}
