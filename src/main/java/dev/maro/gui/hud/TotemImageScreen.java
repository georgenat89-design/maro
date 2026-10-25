package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.module.impl.visuals.CustomTotem;
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

import java.nio.file.Path;
import java.util.List;

/** Pick a picture for {@link CustomTotem}: browse, paste a path or drag a file in. */
public final class TotemImageScreen extends Screen {
    private static final int PREVIEW = 112;

    private final Screen parent;
    private final CustomTotem module;
    private TextFieldWidget path;
    private String pickerError = "";

    public TotemImageScreen(Screen parent, CustomTotem module) {
        super(Text.literal("Totem Image"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        int w = Math.min(420, width - 32), x = (width - w) / 2, y = height / 2 + 20;
        String previousPath=path==null?"":path.getText();
        path = new TextFieldWidget(textRenderer, x, y + 8, w - 78, 20, Text.literal("Image file path"));
        path.setMaxLength(32767);
        path.setText(previousPath);
        addDrawableChild(path);
        setInitialFocus(path);
        addDrawableChild(ButtonWidget.builder(Text.literal("Browse"), button -> browse()).dimensions(x + w - 72, y + 8, 72, 20).build());
        int third = (w - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Use as totem"), button -> {
            try {
                use(Path.of(path.getText().strip()));
            } catch (RuntimeException e) {
                pickerError = "Choose an image file first";
            }
        }).dimensions(x, y + 35, third, 20).build());
        var shape = module.shapeSetting();
        addDrawableChild(ButtonWidget.builder(shapeLabel(), button -> {
            shape.cycle(1);
            button.setMessage(shapeLabel());
        }).dimensions(x + third + 4, y + 35, third, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions(x + w - third, y + 35, third, 20).build());
        module.loadStoredImage();
    }

    private Text shapeLabel() {
        return Text.literal("Shape: " + module.shapeSetting().get());
    }

    private void use(Path file) {
        pickerError = "";
        module.importImage(file);
    }

    private void browse() {
        if (module.imageBusy()) return;
        pickerError = "";
        client.setScreen(new dev.maro.gui.PngFilePickerScreen(this,Path.of(System.getProperty("user.home")),"Choose a totem image",file->{path.setText(file.toString());use(file);},"png","jpg","jpeg"));
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (paths.isEmpty()) return;
        path.setText(paths.getFirst().toString());
        use(paths.getFirst());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int w = Math.min(420, width - 32), y = height / 2 + 20;
        Render2D.rect(ctx, 0, 0, width, height, 0xC910121A);
        float px = width / 2f - PREVIEW / 2f, py = y - 30 - PREVIEW;
        Fonts.centeredTop(ctx, title.getString(), width / 2, (int) py - 14, width - 24, 0xFFEAF4FF);
        Render2D.roundRect(ctx, px - 4, py - 4, PREVIEW + 8, PREVIEW + 8, 8, 0xFF202532);
        drawPreview(ctx, px, py, PREVIEW);
        Fonts.centeredTop(ctx, "Browse, paste a path, or drag a PNG here", width / 2, y - 12, width - 24, 0xFFADB5C7);
        super.render(ctx, mx, my, delta);
        String status = !pickerError.isEmpty() ? pickerError : module.imageStatus();
        Fonts.centeredTop(ctx, Fonts.trim(status, w, false, .9f), width / 2, y + 63, width - 24, 0xFFADB5C7);
        String[] help = switch (module.shapeSetting().get()) {
            case CustomTotem.CUT_OUT -> new String[] {"Cut Out: a plain background is taken away,", "so your picture's own outline is the totem."};
            case CustomTotem.SQUARE -> new String[] {"Square: the whole picture, background and all;", "see-through parts of it stay see-through."};
            default -> new String[] {"Wrap: your picture covers the totem's own shape,", "with the totem showing through its see-through parts."};
        };
        Fonts.centeredTop(ctx, Fonts.trim(help[0], w, false, .9f), width / 2, y + 77, width - 24, 0xFF7C8599);
        Fonts.centeredTop(ctx, Fonts.trim(help[1], w, false, .9f), width / 2, y + 89, width - 24, 0xFF7C8599);
    }

    /** Your totem as the game will use it, in the shape chosen, pixel-sharp, on a checkerboard so see-through parts show. */
    private void drawPreview(DrawContext ctx, float x, float y, int size) {
        var texture = module.previewTexture();
        if (texture == null) {
            Fonts.centeredTop(ctx, "No image yet", (int) (x + size / 2f), (int) (y + size / 2f - 4), width - 24, 0xFF7C8599);
            return;
        }
        int cells = 8, cell = size / cells;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                Render2D.rect(ctx, x + cx * cell, y + cy * cell, cell, cell, (cx + cy) % 2 == 0 ? 0xFF2A3040 : 0xFF343B4D);
            }
        }
        float x2 = x + size, y2 = y + size;
        float[] vertices = {x, y, 0, 0, x, y2, 0, 1, x2, y2, 1, 1, x2, y, 1, 0};
        var pose = new Matrix3x2f(ctx.getMatrices());
        var bounds = new ScreenRect((int) Math.floor(x), (int) Math.floor(y), size, size).transformEachVertex(pose);
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
