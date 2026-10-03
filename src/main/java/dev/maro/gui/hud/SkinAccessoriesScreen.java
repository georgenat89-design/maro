package dev.maro.gui.hud;

import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.Fonts;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Uses the real player feature renderer, including accessories, without changing player rotation. */
public final class SkinAccessoriesScreen extends Screen {
    private final Screen parent;
    private final SkinAccessories module;
    private final List<ButtonWidget> tiles = new ArrayList<>();
    private boolean parts, rotating = true;
    private int page;
    private float rotation = -25;
    private long previousFrame;
    private int x, y, w, h, previewWidth, controlsX, controlsWidth;
    private ButtonWidget tab, pager, toggle;
    private static final String[] PARTS = {"Head", "Wings", "Tail", "Halo", "Shoulders", "Back"};
    public SkinAccessoriesScreen(Screen parent, SkinAccessories module) {
        super(Text.literal("Skin Accessories")); this.parent = parent; this.module = module;
    }
    @Override protected void init() {
        w = Math.min(610, width - 20); h = Math.min(306, height - 20); x = (width - w) / 2; y = (height - h) / 2;
        previewWidth = Math.max(80, (int)(w * .43)); controlsX = x + previewWidth + 12; controlsWidth = w - previewWidth - 22;
        tab = button(parts ? "Show presets" : "Mix your own", b -> { parts = !parts; refresh(); }, controlsX, y + 31, controlsWidth, 20);
        int rowHeight = Math.min(36, Math.max(12, (h - 118) / 3));
        for (int i = 0; i < 6; i++) {
            int index = i;
            tiles.add(button("", b -> {
                if (parts) setting(PARTS[index]).cycle(1); else module.selectPreset(SkinAccessories.PRESETS[page * 6 + index]);
                refresh();
            }, controlsX + i % 2 * (controlsWidth / 2 + 2), y + 56 + i / 2 * (rowHeight + 3), controlsWidth / 2 - 2, rowHeight));
        }
        pager = button("More looks →", b -> { page = 1 - page; refresh(); }, controlsX, y + h - 51, controlsWidth, 20);
        toggle = button("", b -> { module.toggle(); refresh(); }, controlsX, y + h - 26, controlsWidth / 2 - 2, 20);
        button("Done", b -> close(), controlsX + controlsWidth / 2 + 2, y + h - 26, controlsWidth / 2 - 2, 20);
        int third = (previewWidth - 20) / 3;
        button("←", b -> { rotation -= 45; rotating = false; }, x + 8, y + h - 26, third, 20);
        button("Spin", b -> rotating = !rotating, x + 11 + third, y + h - 26, third, 20);
        button("→", b -> { rotation += 45; rotating = false; }, x + 14 + third * 2, y + h - 26, third, 20);
        refresh();
    }
    private ModeSetting setting(String name) { return (ModeSetting)module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    private void refresh() {
        tab.setMessage(Text.literal(parts ? "Show presets" : "Mix your own")); pager.visible = !parts;
        pager.setMessage(Text.literal(page == 0 ? "More looks →" : "← First looks"));
        toggle.setMessage(Text.literal(module.isEnabled() ? "Enabled" : "Enable"));
        for (int i = 0; i < tiles.size(); i++) {
            String text = parts ? PARTS[i] + ": " + setting(PARTS[i]).get() : SkinAccessories.PRESETS[page * 6 + i];
            tiles.get(i).setMessage(Text.literal(text));
        }
    }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float delta) { }
    @Override public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        if (previousFrame != 0 && rotating) rotation += Math.min(.05, (now - previousFrame) / 1e9) * 22;
        previousFrame = now; rotation %= 360;
        Render2D.rect(ctx, 0, 0, width, height, 0xDA10111A);
        Render2D.roundRect(ctx, x, y, w, h, 12, 0xFF191D2B);
        Render2D.roundRect(ctx, x + 6, y + 29, previewWidth, h - 60, 9, 0xFF101520);
        Fonts.drawV(ctx, "SKIN ACCESSORIES", x + 12, y + 16, 0xFFE4DCFF, true, .85f);
        Fonts.drawV(ctx, parts ? "Mix & match" : module.preset(), controlsX, y + 16, module.accentColor(), true, .8f);
        if (client.player != null) {
            var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
            var state = (PlayerEntityRenderState)renderer.getAndUpdateRenderState(client.player, client.getRenderTickCounter().getTickProgress(false));
            state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE; state.shadowPieces.clear(); state.outlineColor = 0;
            state.bodyYaw = 180 + rotation; state.relativeHeadYaw = 0; state.pitch = 0;
            state.width /= state.baseScale; state.height /= state.baseScale; state.baseScale = 1;
            float size = Math.min((previewWidth - 12) / (3.4f * Math.max(1, module.wingSize())), (h - 91) / (2.9f * Math.max(1, module.headSize() * .85f)));
            ctx.addEntity(state, size, new Vector3f(0, state.height / 2 + .08f, 0),
                new Quaternionf().rotateZ((float)Math.PI).rotateX(.10f), new Quaternionf().rotateX(.10f),
                x + 10, y + 35, x + previewWidth + 2, y + h - 45);
        } else Fonts.drawCentered(ctx, "Join a world", x + previewWidth / 2, y + h / 2, 0xFFBBC2D5, false, .75f);
        Fonts.drawCentered(ctx, "Client cosmetics", x + previewWidth / 2 + 6, y + h - 40, 0xFF8691AA, false, .65f);
        super.render(ctx, mx, my, delta);
    }
    @Override public void close() { client.setScreen(parent); }
    public void showAngle(float degrees) { rotation = degrees; rotating = false; }
    @Override public boolean shouldPause() { return false; }
    private ButtonWidget button(String label, ButtonWidget.PressAction action, int bx, int by, int bw, int bh) {
        return addDrawableChild(new AccessoryButton(label, action, bx, by, bw, bh));
    }
    private final class AccessoryButton extends ButtonWidget {
        AccessoryButton(String label, PressAction action, int bx, int by, int bw, int bh) {
            super(bx, by, bw, bh, net.minecraft.text.Text.literal(label), action, DEFAULT_NARRATION_SUPPLIER);
        }
            @Override protected void drawIcon(DrawContext ctx, int mx, int my, float delta) {
                boolean selected = !parts && getMessage().getString().equals(module.preset())
                    || this == toggle && module.isEnabled() || getMessage().getString().equals("Spin") && rotating;
                boolean hover = isHovered() || isFocused();
                int tint = module.accentColor() & 0xFFFFFF;
                Render2D.roundRect(ctx, getX(), getY(), getWidth(), getHeight(), 5,
                    selected ? 0x38000000 | tint : hover ? 0xFF303A51 : 0xFF232B3D);
                Render2D.roundOutline(ctx, getX(), getY(), getWidth(), getHeight(), 5, 1,
                    selected ? 0xFF000000 | tint : hover ? 0xFF65718D : 0xFF364057);
                Fonts.beginRaw();
                try {
                    String label = getMessage().getString(); float fontSize = .75f;
                    float measured = Fonts.width(label, selected, fontSize);
                    if (measured > getWidth() - 10) fontSize *= Math.max(.65f, (getWidth() - 10) / measured);
                    while (label.length() > 1 && Fonts.width(label, selected, fontSize) > getWidth() - 10) label = label.substring(0, label.length() - 1);
                    Fonts.drawCentered(ctx, label, getX() + getWidth() / 2f, getY() + getHeight() / 2f,
                        selected ? 0xFFF5EEFF : 0xFFC4CDE1, selected, fontSize);
                } finally { Fonts.endRaw(); }
            }
    }
}
