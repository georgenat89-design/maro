package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.CrosshairPresetsScreen;
import dev.maro.gui.hud.CrosshairImageScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.CrosshairRenderer;
import dev.maro.gui.render.PngCrosshairImage;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CustomCrosshair extends Module {
    private final PngCrosshairImage pngImage = new PngCrosshairImage();
    private final ModeSetting source = add(new ModeSetting("Source", "Built-in shape or your own PNG", "Preset", "Preset", "PNG")
        .onChange(value -> { if (value.equals("PNG")) loadStoredPng(); }));
    private final ModeSetting preset = add(new ModeSetting("Preset", "Choose a crosshair shape", "Cross + Dot",
        CrosshairRenderer.PRESETS.toArray(String[]::new)).visible(() -> !pngMode()));
    private final ButtonSetting gallery = add(new ButtonSetting("Preset Gallery", "See all 24 shapes and click to select", "Browse",
        () -> mc.setScreen(new CrosshairPresetsScreen(mc.currentScreen, this))).visible(() -> !pngMode()));
    private final ButtonSetting importImage = add(new ButtonSetting("Import PNG", "Choose or drag in your own crosshair image", "Import",
        () -> mc.setScreen(new CrosshairImageScreen(mc.currentScreen, this))));
    private final NumberSetting pngSize = add(new NumberSetting("PNG Size", "Longest visible image dimension in GUI pixels", 20, 2, 128, 1).visible(this::pngMode));
    private final NumberSetting pngOpacity = add(new NumberSetting("PNG Opacity", "Image opacity", 100, 0, 100, 1).suffix("%").visible(this::pngMode));
    private final BooleanSetting trimPadding = add(new BooleanSetting("Trim Padding", "Center the visible artwork, ignoring transparent margins", true).visible(this::pngMode));
    private final BooleanSetting smoothPng = add(new BooleanSetting("Smooth PNG", "Smooth resized art; off keeps pixel art sharp", false)
        .visible(this::pngMode).onChange(value -> pngImage.setSmooth(value)));
    private final BooleanSetting tintPng = add(new BooleanSetting("Tint PNG", "Multiply the image by your crosshair color", false).visible(this::pngMode));
    private final NumberSetting size = add(new NumberSetting("Size", "Arm length or ring radius in GUI pixels", 5, 2, 24, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Gap", "Space between the center and crosshair arms", 2, 0, 12, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1.2, 0.5, 5, 0.1));
    private final NumberSetting dotSize = add(new NumberSetting("Dot Size", "Radius of dots in GUI pixels", 1.2, 0.5, 5, 0.1));
    private final BooleanSetting centerDot = add(new BooleanSetting("Center Dot", "Add a dot to any shape", false));
    private final ColorSetting color = add(new ColorSetting("Color", "Crosshair color and opacity", 0xFFEAF4FF, true)
        .visible(() -> !pngMode() || tintPng.get()));
    private final BooleanSetting targetHighlight = add(new BooleanSetting("Target Highlight", "Change color when aiming at a living entity", false));
    private final ColorSetting targetColor = add(new ColorSetting("Target Color", "Crosshair color on a living target", 0xFFFF718C, true)
        .visible(targetHighlight::get));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "Contrasting edges for visibility", true));
    private final NumberSetting outlineWidth = add(new NumberSetting("Outline Width", "Thickness around the crosshair", 0.8, 0.5, 3, 0.1)
        .visible(outline::get));
    private final ColorSetting outlineColor = add(new ColorSetting("Outline Color", "Outline color and opacity", 0xDD080B12, true)
        .visible(outline::get));
    private boolean pngBusy, closing;
    private String pngStatus = "Choose a PNG to import";

    public CustomCrosshair() {
        super("Custom Crosshair", "24 crisp crosshair styles with a visual preset gallery", Category.VISUALS);
        size.visible(() -> !pngMode()); gap.visible(() -> !pngMode()); thickness.visible(() -> !pngMode());
        dotSize.visible(() -> !pngMode()); centerDot.visible(() -> !pngMode()); outline.visible(() -> !pngMode());
        outlineWidth.visible(() -> !pngMode() && outline.get());
        outlineColor.visible(() -> !pngMode() && outline.get());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { closing = true; pngImage.close(); });
    }

    @Override public List<SettingSection> getSettingSections() {
        var styles = new SettingSection("Presets / PNG"); styles.add(source); styles.add(preset); styles.add(gallery); styles.add(importImage);
        var png = new SettingSection("PNG Image");
        png.add(pngSize); png.add(pngOpacity); png.add(trimPadding); png.add(smoothPng); png.add(tintPng);
        var shape = new SettingSection("Shape");
        shape.add(size); shape.add(gap); shape.add(thickness); shape.add(dotSize); shape.add(centerDot);
        var colors = new SettingSection("Colors"); colors.add(color); colors.add(targetHighlight); colors.add(targetColor);
        var edges = new SettingSection("Outline"); edges.add(outline); edges.add(outlineWidth); edges.add(outlineColor);
        return List.of(styles, png, shape, colors, edges);
    }

    public String preset() { return preset.get(); }
    public void selectPreset(String name) { preset.set(name); source.set("Preset"); }
    public boolean pngMode() { return source.is("PNG"); }
    public boolean pngBusy() { return pngBusy; }
    public String pngStatus() { return pngBusy ? "Loading PNG…" : pngStatus; }
    public Path pngFolder() { return mc.runDirectory.toPath().resolve("maro/crosshairs"); }
    @Override protected void onEnable() { if (pngMode()) loadStoredPng(); }

    public void loadStoredPng() {
        Path stored = pngFolder().resolve("custom.png");
        if (!pngImage.loaded() && !pngBusy && Files.isRegularFile(stored)) loadPng(stored, false);
    }
    public CompletableFuture<Boolean> importPng(Path path) { return loadPng(path, true); }
    private CompletableFuture<Boolean> loadPng(Path path, boolean importing) {
        if (pngBusy || closing) return CompletableFuture.completedFuture(false);
        pngBusy = true;
        var result = new CompletableFuture<Boolean>();
        CompletableFuture.supplyAsync(() -> {
            try {
                var decoded = PngCrosshairImage.decode(path);
                if (importing) {
                    Files.createDirectories(pngFolder());
                    Path temporary = Files.createTempFile(pngFolder(), "crosshair-", ".tmp");
                    try {
                        Files.write(temporary, decoded.file());
                        Files.move(temporary, pngFolder().resolve("custom.png"), StandardCopyOption.REPLACE_EXISTING);
                    } finally { Files.deleteIfExists(temporary); }
                }
                return decoded;
            } catch (IOException | RuntimeException e) { throw new java.util.concurrent.CompletionException(e); }
        }).whenComplete((decoded, error) -> mc.execute(() -> {
            pngBusy = false;
            if (closing) { result.complete(false); return; }
            try {
                if (error != null) throw new java.util.concurrent.CompletionException(error);
                pngImage.install(decoded, smoothPng.get());
                pngStatus = "Loaded " + decoded.width() + " × " + decoded.height() + " PNG";
                if (importing) {
                    source.set("PNG");
                    Notifications.push("Custom Crosshair", "PNG imported", Notifications.Type.INFO);
                }
                result.complete(true);
            } catch (RuntimeException e) {
                Throwable cause = e;
                while (cause.getCause() != null) cause = cause.getCause();
                pngStatus = cause.getMessage() == null ? "Could not load this PNG" : cause.getMessage();
                Notifications.push("Custom Crosshair", pngStatus, Notifications.Type.ERROR);
                result.complete(false);
            }
        }));
        return result;
    }

    /** GUI dimensions round up at odd window sizes; use the actual framebuffer center instead. */
    public static float screenCenter(int framebufferDimension, double guiScale) {
        return (float) (framebufferDimension / (guiScale * 2));
    }

    /** Called in vanilla's crosshair draw, so F1, perspective and spectator rules still apply. */
    public void renderCrosshair(DrawContext ctx) {
        int tint = targetHighlight.get() && mc.targetedEntity instanceof LivingEntity target && target.isAlive()
            ? targetColor.get() : color.get();
        float x = screenCenter(mc.getWindow().getFramebufferWidth(), mc.getWindow().getScaleFactor());
        float y = screenCenter(mc.getWindow().getFramebufferHeight(), mc.getWindow().getScaleFactor());
        if (pngMode() && pngImage.loaded()) {
            boolean target = targetHighlight.get() && mc.targetedEntity instanceof LivingEntity living && living.isAlive();
            int pngTint = target ? targetColor.get() : tintPng.get() ? color.get() : 0xFFFFFFFF;
            int alpha = Math.round((pngTint >>> 24) * pngOpacity.getFloat() / 100);
            pngImage.draw(ctx, x, y, pngSize.getFloat(), trimPadding.get(), (pngTint & 0xFFFFFF) | (alpha << 24));
            return;
        }
        draw(ctx, preset.get(), x, y,
            size.getFloat(), gap.getFloat(), thickness.getFloat(), dotSize.getFloat(), tint);
    }

    public void renderPngPreview(DrawContext ctx, float x, float y, float size) {
        if (pngImage.loaded()) pngImage.draw(ctx, x, y, size, trimPadding.get(), 0xFFFFFFFF);
        else renderPreview(ctx, preset.get(), x, y);
    }

    public void renderPreview(DrawContext ctx, String style, float x, float y) {
        draw(ctx, style, x, y, 5, 2, 1.2f, 1.2f, color.get());
    }

    private void draw(DrawContext ctx, String style, float x, float y, float s, float g, float t, float dot, int tint) {
        float oldAlpha = Render2D.getAlpha();
        ctx.getMatrices().pushMatrix();
        try {
            Render2D.setAlpha(1);
            ctx.getMatrices().translate(x, y);
            CrosshairRenderer.draw(ctx, style, s, g, t, dot, centerDot.get(), tint,
                outline.get() ? outlineWidth.getFloat() : 0, outlineColor.get());
        } finally {
            ctx.getMatrices().popMatrix();
            Render2D.setAlpha(oldAlpha);
        }
    }
}
