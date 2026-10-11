package dev.maro.gui.hud;

import dev.maro.gui.render.Render2D;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.module.impl.visuals.CustomSky;
import dev.maro.runtime.renderer.GuiMeshState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Pick a picture for {@link CustomSky}: browse, paste a path or drag a file in. */
public final class SkyImageScreen extends Screen {
    private static final int PREVIEW_W = 240, PREVIEW_H = 120;

    private final Screen parent;
    private final CustomSky module;
    private TextFieldWidget path;
    private boolean choosing;
    private String pickerError = "";

    public SkyImageScreen(Screen parent, CustomSky module) {
        super(Text.literal("Sky Image"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        int w = Math.min(420, width - 32), x = (width - w) / 2, y = height / 2 + 20;
        path = new TextFieldWidget(textRenderer, x, y + 8, w - 78, 20, Text.literal("Image file path"));
        path.setMaxLength(32767);
        addDrawableChild(path);
        setInitialFocus(path);
        addDrawableChild(ButtonWidget.builder(Text.literal("Browse"), button -> browse()).dimensions(x + w - 72, y + 8, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Use as sky"), button -> {
            try {
                module.importPicture(Path.of(path.getText().strip()));
            } catch (RuntimeException e) {
                pickerError = "Choose an image file first";
            }
        }).dimensions(x, y + 35, w / 2 - 4, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions(x + w / 2 + 4, y + 35, w / 2 - 4, 20).build());
        module.loadStoredPicture();
    }

    private void browse() {
        if (choosing || module.pictureBusy()) return;
        choosing = true;
        pickerError = "";
        CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                return TinyFileDialogs.tinyfd_openFileDialog("Choose a sky image", module.pictureFolder().toString() + java.io.File.separator,
                    stack.pointers(stack.UTF8("*.png"), stack.UTF8("*.jpg"), stack.UTF8("*.jpeg")), "PNG or JPG images", false);
            }
        }).whenComplete((file, error) -> client.execute(() -> {
            choosing = false;
            if (error != null) pickerError = "Paste the image's file path or drag the file into this window";
            else if (file != null) {
                path.setText(file);
                module.importPicture(Path.of(file));
            }
        }));
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (paths.isEmpty()) return;
        path.setText(paths.getFirst().toString());
        module.importPicture(paths.getFirst());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int w = Math.min(420, width - 32), y = height / 2 + 20;
        Render2D.rect(ctx, 0, 0, width, height, 0xC910121A);
        float px = width / 2f - PREVIEW_W / 2f, py = y - 30 - PREVIEW_H;
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, (int) py - 14, 0xFFEAF4FF);
        Render2D.roundRect(ctx, px - 4, py - 4, PREVIEW_W + 8, PREVIEW_H + 8, 8, 0xFF202532);
        drawPreview(ctx, px, py, PREVIEW_W, PREVIEW_H);
        ctx.drawCenteredTextWithShadow(textRenderer, "Browse, paste a path, or drag a PNG or JPG here", width / 2, y - 12, 0xFFADB5C7);
        super.render(ctx, mx, my, delta);
        String status = !pickerError.isEmpty() ? pickerError : choosing ? "Choosing a file…" : module.pictureStatus();
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(status, w), width / 2, y + 63, 0xFFADB5C7);
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth("2:1 images are used as 360° panoramas and 4:3 as skybox crosses;", w),
            width / 2, y + 77, 0xFF7C8599);
        ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth("any other picture wraps round the horizon.", w), width / 2, y + 89, 0xFF7C8599);
    }

    /** The loaded picture, fitted inside the box and keeping its shape. */
    private void drawPreview(DrawContext ctx, float x, float y, float w, float h) {
        var texture = module.picture().texture();
        var info = module.picture().image();
        if (texture == null || info == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No image yet", (int) (x + w / 2), (int) (y + h / 2 - 4), 0xFF7C8599);
            return;
        }
        float scale = Math.min(w / info.width(), h / info.height());
        float pw = info.width() * scale, ph = info.height() * scale;
        float x1 = x + (w - pw) / 2, y1 = y + (h - ph) / 2, x2 = x1 + pw, y2 = y1 + ph;
        float[] vertices = {x1, y1, 0, 0, x1, y2, 0, 1, x2, y2, 1, 1, x2, y1, 1, 0};
        var pose = new Matrix3x2f(ctx.getMatrices());
        var bounds = new ScreenRect((int) Math.floor(x1), (int) Math.floor(y1),
            Math.max(1, (int) Math.ceil(x2) - (int) Math.floor(x1)), Math.max(1, (int) Math.ceil(y2) - (int) Math.floor(y1))).transformEachVertex(pose);
        ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new GuiMeshState(pose, vertices,
            new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF}, RenderPipelines.GUI_TEXTURED,
            TextureSetup.of(texture.getGlTextureView(), texture.getSampler()), bounds));
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
