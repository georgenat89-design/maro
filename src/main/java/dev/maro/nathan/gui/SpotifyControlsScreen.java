package dev.maro.nathan.gui;

import dev.maro.nathan.modules.SpotifyHud;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Releases the mouse so the player can click the Spotify HUD without pausing the game. */
public final class SpotifyControlsScreen extends Screen {
    private final SpotifyHud player;

    public SpotifyControlsScreen(SpotifyHud player) {
        super(Text.literal("Spotify controls"));
        this.player = player;
    }

    @Override
    public void renderBackground(DrawContext graphics, int mouseX, int mouseY, float delta) {
        // Keep the HUD and world visible behind the controls.
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        graphics.drawCenteredTextWithShadow(client.textRenderer,
            "Drag song text to move  -  Click cover to open Spotify  -  Esc to return",
            width / 2, 12, 0xFFECEEF3);
        String hint = player.controlsHint();
        if (!hint.isBlank()) graphics.drawCenteredTextWithShadow(client.textRenderer, hint, width / 2, 24, 0xFF999EAB);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (event.button() == 0 && player.mousePressed(event.x() * client.getWindow().getScaleFactor(),
            event.y() * client.getWindow().getScaleFactor())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click event, double dragX, double dragY) {
        if (event.button() == 0 && player.mouseDragged(event.x() * client.getWindow().getScaleFactor(),
            event.y() * client.getWindow().getScaleFactor())) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(Click event) {
        if (event.button() == 0 && player.mouseReleased(event.x() * client.getWindow().getScaleFactor(),
            event.y() * client.getWindow().getScaleFactor())) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double scale = client.getWindow().getScaleFactor();
        return player.scrollVolume(mouseX * scale, mouseY * scale, vertical) || player.scrollLyrics(mouseX * scale, mouseY * scale, vertical)
            || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void removed() {
        player.cancelInteraction();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
