package dev.maro.gametest;

import dev.maro.runtime.renderer.Renderer2D;
import dev.maro.runtime.utils.render.color.Color;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import javax.imageio.ImageIO;

/** Reusing a CPU mesh must not overwrite an earlier GUI batch awaiting submission. */
final class GuiMeshChecks {
    static void run(ClientGameTestContext context) {
        context.setScreen(Probe::new);
        try {
            context.waitTicks(3);
            var image = ImageIO.read(context.takeScreenshot("maro-gui-mesh-snapshots").toFile());
            int red = 0, blue = 0, green = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int p = image.getRGB(x, y), r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255;
                if (r > 240 && g < 10 && b < 10) red++;
                if (b > 240 && g < 10 && r < 10) blue++;
                if (g > 240 && r < 10 && b < 10) green++;
            }
            if (red < 500 || blue < 500 || green < 500)
                throw new AssertionError("Queued mesh snapshots, rotated quad or triangle lost: " + red + "/" + blue + "/" + green);
            System.out.println("[gui-mesh] PASS: independent snapshots, rotated quads and triangle winding");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        finally { context.setScreen(() -> null); }
    }
    private static final class Probe extends Screen {
        Probe() { super(Text.literal("Mesh snapshots")); }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF000000);
            var previous = Renderer2D.context();
            Renderer2D.context(context);
            try {
                Renderer2D.COLOR.begin();
                Renderer2D.COLOR.texQuad(40, 40, 80, 60, new Color(255, 0, 0, 255));
                Renderer2D.COLOR.render();
                Renderer2D.COLOR.begin();
                Renderer2D.COLOR.texQuad(200, 40, 80, 60, 90, 0, 0, 1, 1, new Color(0, 0, 255, 255));
                Renderer2D.COLOR.render();
                Renderer2D.COLOR.begin();
                var mesh = Renderer2D.COLOR.triangles;
                var green = new Color(0, 255, 0, 255);
                int a = mesh.vec2(400, 40).color(green).next();
                int b = mesh.vec2(480, 40).color(green).next();
                int c = mesh.vec2(440, 120).color(green).next();
                mesh.triangle(a, b, c);
                Renderer2D.COLOR.render();
            } finally { Renderer2D.context(previous); }
        }
    }
}
