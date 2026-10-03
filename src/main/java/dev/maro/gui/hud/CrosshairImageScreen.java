package dev.maro.gui.hud;

import dev.maro.gui.render.Render2D;
import dev.maro.module.impl.visuals.CustomCrosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CrosshairImageScreen extends Screen {
    private final Screen parent;
    private final CustomCrosshair module;
    private TextFieldWidget path;
    private boolean choosing;
    private String pickerError = "";

    public CrosshairImageScreen(Screen parent, CustomCrosshair module) {
        super(Text.literal("PNG Crosshair")); this.parent = parent; this.module = module;
    }
    @Override protected void init() {
        int w = Math.min(420, width - 32), x = (width - w) / 2, y = height / 2;
        path = new TextFieldWidget(textRenderer, x, y + 8, w - 78, 20, Text.literal("PNG file path"));
        path.setMaxLength(32767); addDrawableChild(path); setInitialFocus(path);
        addDrawableChild(ButtonWidget.builder(Text.literal("Browse"), button -> browse()).dimensions(x + w - 72, y + 8, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Import PNG"), button -> {
            try { module.importPng(Path.of(path.getText().strip())); } catch (RuntimeException e) { pickerError = "Choose a PNG file first"; }
        }).dimensions(x, y + 35, w / 2 - 4, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions(x + w / 2 + 4, y + 35, w / 2 - 4, 20).build());
        module.loadStoredPng();
    }
    private void browse() {
        if (choosing || module.pngBusy()) return;
        choosing = true; pickerError = "";
        CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                return TinyFileDialogs.tinyfd_openFileDialog("Choose a PNG crosshair", module.pngFolder().toString() + java.io.File.separator,
                    stack.pointers(stack.UTF8("*.png")), "PNG images", false);
            }
        }).whenComplete((file, error) -> client.execute(() -> {
            choosing = false;
            if (error != null) pickerError = "Paste the PNG file path or drag the file into this window";
            else if (file != null) { path.setText(file); module.importPng(Path.of(file)); }
        }));
    }
    @Override public void onFilesDropped(List<Path> paths) {
        if (!paths.isEmpty()) { path.setText(paths.getFirst().toString()); module.importPng(paths.getFirst()); }
    }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) { }
    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        int w = Math.min(420, width - 32), x = (width - w) / 2, y = height / 2;
        Render2D.rect(ctx, 0, 0, width, height, 0xC910121A);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, y - 83, 0xFFEAF4FF);
        Render2D.roundRect(ctx, width / 2f - 25, y - 62, 50, 50, 8, 0xFF202532);
        module.renderPngPreview(ctx, width / 2f, y - 37, 32);
        ctx.drawCenteredTextWithShadow(textRenderer, "Browse, paste a path, or drag a PNG here", width / 2, y - 5, 0xFFADB5C7);
        super.render(ctx, mx, my, delta);
        String status = !pickerError.isEmpty() ? pickerError : choosing ? "Choosing a file…" : module.pngStatus();
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(status, w), width / 2, y + 63, 0xFFADB5C7);
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth("Transparent PNGs work best. Original colors are preserved.", w), width / 2, y + 77, 0xFF7C8599);
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
