package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.render.accessories.CosmeticItems;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cosmetics: your character turning on the left (Front, Back, Reset; drag to turn it), and on the
 * right a tab for each kind of cosmetic, a list of every choice in it with the current one lit, a
 * size slider for that kind and Apply. Hats come from the Hats module; swords, pickaxes and
 * shovels are skins over your own items; the rest are the 3D accessories.
 */
public final class SkinAccessoriesScreen extends Screen {
    public static final String[] TABS = {"Hat", "Sword", "Pickaxe", "Shovel", "Wings", "Head", "Tail", "Halo", "Back", "Shoulders", "Looks", "Colors"};
    private static final float ROW = 18, ROW_GAP = 3;
    private static final int[] PALETTE = {0xFF48385E, 0xFFBA7BFF, 0xFFFFD98A, 0xFF55E6F5, 0xFFFF536D, 0xFFD77B3B, 0xFF8DE7B4,
            0xFFE9E5EF, 0xFF293447, 0xFFF2A4C3, 0xFF3391FC, 0xFF1E1B1F};

    private final Screen parent;
    private final SkinAccessories module;
    private String tab = "Sword";
    private float rotation = -25, scroll, scrollTarget;
    private boolean rotating = true, dragging, sizing;
    private double dragFromX;
    private float dragFromRotation;
    private long previousFrame;
    private float x, y, w, h, previewW, listX, listY, listW, listH, sliderX, sliderW;

    private interface Action {
        void run(double mx, double my, int button);
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    public SkinAccessoriesScreen(Screen parent, SkinAccessories module) {
        super(Text.literal("Cosmetics"));
        this.parent = parent;
        this.module = module;
    }

    // ---- for tests -------------------------------------------------------------------------------

    public void showAngle(float degrees) {
        rotation = degrees;
        rotating = false;
    }

    public void showTab(String name) {
        tab = name;
        scroll = scrollTarget = 0;
    }

    /** The preview's area on screen, in window pixels: {x1, y1, x2, y2}. */
    public int[] previewBounds() {
        double s = client == null ? 1 : client.getWindow().getScaleFactor();
        return new int[] {(int) ((x + 10) * s), (int) ((y + 34) * s), (int) ((x + 10 + previewW) * s), (int) ((y + h - 40) * s)};
    }

    // ---- what each tab holds ------------------------------------------------------------------------

    private Setting<?> setting(String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static Module hats() {
        return ModuleManager.getByName("Hats");
    }

    @SuppressWarnings("unchecked")
    private static dev.maro.runtime.settings.Setting<Object> runtimeSetting(Module m, String name) {
        if (!(m instanceof dev.maro.runtime.systems.modules.Module rm)) return null;
        for (var group : rm.settings) for (var s : group) if (s.name.equals(name)) return (dev.maro.runtime.settings.Setting<Object>) s;
        return null;
    }

    /** The choices in the tab, "None" first where there is one. */
    private List<String> choices() {
        List<String> out = new ArrayList<>();
        switch (tab) {
            case "Hat" -> {
                out.add("None");
                for (String id : dev.maro.nathan.hats.HatCatalogue.ids()) out.add(pretty(id));
            }
            case "Sword", "Pickaxe", "Shovel", "Wings", "Head", "Tail", "Halo", "Back", "Shoulders" ->
                    out.addAll(((ModeSetting) setting(tab)).getModes());
            case "Looks" -> out.addAll(List.of(SkinAccessories.PRESETS));
            default -> {
            }
        }
        return out;
    }

    private String current() {
        return switch (tab) {
            case "Hat" -> {
                Module h = hats();
                var s = runtimeSetting(h, "hat");
                yield h == null || !h.isEnabled() || s == null ? "None" : pretty(String.valueOf(s.get()));
            }
            case "Looks" -> module.preset();
            case "Colors" -> "";
            default -> ((ModeSetting) setting(tab)).get();
        };
    }

    private void choose(String choice) {
        switch (tab) {
            case "Hat" -> {
                Module h = hats();
                if (h == null) return;
                if (choice.equals("None")) {
                    h.setEnabled(false);
                    return;
                }
                var s = runtimeSetting(h, "hat");
                if (s != null) s.set(choice.toLowerCase(Locale.ROOT).replace(' ', '_'));
                h.setEnabled(true);
            }
            case "Looks" -> module.selectPreset(choice);
            default -> ((ModeSetting) setting(tab)).set(choice);
        }
    }

    /** The slider under the list for this tab, if it has one. */
    private NumberSetting size() {
        return switch (tab) {
            case "Sword", "Pickaxe", "Shovel" -> (NumberSetting) setting("Item Size");
            case "Wings" -> (NumberSetting) setting("Wing Size");
            case "Head" -> (NumberSetting) setting("Head Size");
            case "Tail" -> (NumberSetting) setting("Tail Length");
            case "Halo" -> (NumberSetting) setting("Halo Height");
            default -> null;
        };
    }

    private static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    /** What the player holds in the preview: on a weapon tab, an item of that kind with its skin. */
    private ItemStack previewItem() {
        Item item = switch (tab) {
            case "Sword" -> Items.DIAMOND_SWORD;
            case "Pickaxe" -> Items.DIAMOND_PICKAXE;
            case "Shovel" -> Items.DIAMOND_SHOVEL;
            default -> null;
        };
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** An icon for a weapon skin: the base item drawn with the skin's model. */
    private ItemStack icon(String choice) {
        CosmeticItems.Kind kind = switch (tab) {
            case "Sword" -> CosmeticItems.Kind.SWORD;
            case "Pickaxe" -> CosmeticItems.Kind.PICKAXE;
            case "Shovel" -> CosmeticItems.Kind.SHOVEL;
            default -> null;
        };
        if (kind == null) return null;
        ItemStack stack = previewItem().copy();
        CosmeticItems.Skin skin = CosmeticItems.byName(kind, choice);
        if (skin != null) stack.set(DataComponentTypes.ITEM_MODEL, skin.model());
        return stack;
    }

    // ---- drawing ------------------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hits.clear();
        long now = System.nanoTime();
        if (previousFrame != 0 && rotating && !dragging) rotation += Math.min(.05f, (now - previousFrame) / 1e9f) * 22;
        previousFrame = now;
        rotation %= 360;
        Theme.update();

        w = Math.min(660, width - 20);
        h = Math.min(340, height - 20);
        x = Math.round((width - w) / 2f);
        y = Math.round((height - h) / 2f);
        previewW = Math.round(w * 0.42f);

        Fonts.beginRaw();
        try {
            Render2D.rect(ctx, 0, 0, width, height, 0xC00A0C12);
            Render2D.shadow(ctx, x, y + 4, w, h, 14, 20, 0x90000000);
            Render2D.roundRect(ctx, x, y, w, h, 12, 0xF5141720);
            Render2D.roundOutline(ctx, x, y, w, h, 12, 1, 0x1CFFFFFF);
            Render2D.roundRect(ctx, x, y, w, 3, 1.5f, Theme.accent(0xC0));
            Fonts.drawV(ctx, "Cosmetics", x + 14, y + 17, Theme.TEXT, true, 1.05f);

            // On/off and Done.
            float bw = 58, bh = 18, by = y + 8;
            button(ctx, "Done", x + w - 12 - bw, by, bw, bh, true, mx, my, this::close);
            boolean on = module.isEnabled();
            float tx = x + w - 12 - bw * 2 - 6;
            button(ctx, on ? "Enabled" : "Disabled", tx, by, bw, bh, false, mx, my, () -> {
                module.toggle();
                Sounds.toggle(module.isEnabled());
            });
            if (on) Render2D.circle(ctx, tx + 8, by + bh / 2f, 2.4f, Theme.GREEN);

            preview(ctx, mx, my);
            tabs(ctx, mx, my);
            list(ctx, mx, my);
            footer(ctx, mx, my);
        } finally {
            Fonts.endRaw();
        }
    }

    private void preview(DrawContext ctx, int mx, int my) {
        float px = x + 10, py = y + 34, pw = previewW, ph = h - 74;
        Render2D.roundRect(ctx, px, py, pw, ph, 9, 0xFF101622, 0xFF101622, 0xFF1A2436, 0xFF1A2436);
        Render2D.roundOutline(ctx, px, py, pw, ph, 9, 1, 0x18FFFFFF);
        if (client != null && client.player != null) {
            var renderer = client.getEntityRenderDispatcher().getRenderer(client.player);
            var state = (PlayerEntityRenderState) renderer.getAndUpdateRenderState(client.player, client.getRenderTickCounter().getTickProgress(false));
            state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            state.bodyYaw = 180 + rotation;
            state.relativeHeadYaw = 0;
            state.pitch = 0;
            state.width /= state.baseScale;
            state.height /= state.baseScale;
            state.baseScale = 1;
            dev.maro.render.accessories.CosmeticPreview.hold(state, previewItem(), client.player);
            float size = Math.min((pw - 12) / (3.4f * Math.max(1, module.wingSize())), (ph - 20) / (2.9f * Math.max(1, module.headSize() * .85f)));
            ctx.addEntity(state, size, new Vector3f(0, state.height / 2 + .08f, 0),
                    new Quaternionf().rotateZ((float) Math.PI).rotateX(.10f), new Quaternionf().rotateX(.10f),
                    Math.round(px + 2), Math.round(py + 4), Math.round(px + pw - 2), Math.round(py + ph - 4));
        } else {
            Fonts.drawCentered(ctx, "Join a world to see yourself", px + pw / 2f, py + ph / 2f, Theme.TEXT_MUTED, false, 0.7f);
        }
        // On a weapon tab, the chosen skin large in the corner, at its Item Size.
        String chosen = current();
        ItemStack big = chosen.equals("None") ? null : icon(chosen);
        if (big != null) {
            float s = Math.min(Math.min(3.4f, pw / 70f) * module.itemSize(), (pw - 24) / 32f);
            float bx = px + pw - 16 * s - 8, byy = py + ph - 16 * s - 8;
            Render2D.roundRect(ctx, bx - 4, byy - 4, 16 * s + 8, 16 * s + 8, 8, 0x50000000);
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(bx, byy);
            m.scale(s, s);
            ctx.drawItem(big, 0, 0);
            m.popMatrix();
            Fonts.drawRight(ctx, chosen, px + pw - 8, byy - 10, Theme.TEXT_DIM, false, 0.62f);
        }
        // Drag across the preview to turn it.
        hit(px, py, pw, ph, (hx, hy, b) -> {
            dragging = true;
            rotating = false;
            dragFromX = hx;
            dragFromRotation = rotation;
        });
        float third = (pw - 8) / 3f, by = py + ph + 6;
        button(ctx, "Front", px, by, third, 18, false, mx, my, () -> showAngle(0));
        button(ctx, "Back", px + third + 4, by, third, 18, false, mx, my, () -> showAngle(180));
        button(ctx, "Reset", px + (third + 4) * 2, by, third, 18, false, mx, my, () -> {
            rotation = -25;
            rotating = true;
        });
    }

    private void tabs(DrawContext ctx, int mx, int my) {
        float tx = x + 10 + previewW + 10, tw = w - previewW - 30, ty = y + 34;
        int perRow = 6;
        float cw = (tw - (perRow - 1) * 4) / perRow;
        for (int i = 0; i < TABS.length; i++) {
            String name = TABS[i];
            float bx = tx + (i % perRow) * (cw + 4), by = ty + (i / perRow) * 22;
            boolean sel = tab.equals(name);
            boolean hov = inside(mx, my, bx, by, cw, 18);
            Render2D.roundRect(ctx, bx, by, cw, 18, 5, sel ? Theme.accent(0xD0) : hov ? 0xFF262B3A : 0xFF1A1E29);
            Render2D.roundOutline(ctx, bx, by, cw, 18, 5, 1, sel ? Theme.accent() : 0x1CFFFFFF);
            Fonts.drawCentered(ctx, Fonts.trim(name, cw - 6, false, 0.64f), bx + cw / 2f, by + 9, sel ? 0xFFFFFFFF : Theme.TEXT_DIM, false, 0.64f);
            hit(bx, by, cw, 18, (hx, hy, b) -> {
                showTab(name);
                Sounds.click();
            });
        }
        listX = tx;
        listY = ty + 48;
        listW = tw;
        listH = y + h - 40 - listY - 24;
    }

    private void list(DrawContext ctx, int mx, int my) {
        Render2D.roundRect(ctx, listX, listY, listW, listH, 8, 0xFF0F1219);
        if (tab.equals("Colors")) {
            colors(ctx, mx, my);
            return;
        }
        List<String> choices = choices();
        String now = current();
        float content = choices.size() * (ROW + ROW_GAP) + 6;
        float maxScroll = Math.max(0, content - listH);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * 0.35f;
        Render2D.clip(ctx, Math.round(listX), Math.round(listY + 2), Math.round(listX + listW), Math.round(listY + listH - 2));
        float ry = listY + 4 - scroll;
        for (String choice : choices) {
            if (ry + ROW >= listY && ry <= listY + listH) {
                boolean sel = choice.equals(now);
                boolean hov = inside(mx, my, listX + 4, ry, listW - 12, ROW) && my >= listY && my < listY + listH;
                Render2D.roundRect(ctx, listX + 4, ry, listW - 12, ROW, 5, sel ? Theme.accent(0x60) : hov ? 0xFF232838 : 0xFF181C26);
                Render2D.roundOutline(ctx, listX + 4, ry, listW - 12, ROW, 5, 1, sel ? Theme.accent() : hov ? 0x30FFFFFF : 0x12FFFFFF);
                ItemStack icon = choice.equals("None") ? null : icon(choice);
                float textX = listX + listW / 2f;
                if (icon != null) {
                    var m = ctx.getMatrices();
                    m.pushMatrix();
                    m.translate(listX + 8, ry + 1);
                    m.scale(1f, 1f);
                    ctx.drawItem(icon, 0, 0);
                    m.popMatrix();
                }
                Fonts.drawCentered(ctx, choice, textX, ry + ROW / 2f, sel ? 0xFFFFFFFF : Theme.TEXT, sel, 0.7f);
                float top = Math.max(ry, listY), bottom = Math.min(ry + ROW, listY + listH);
                if (bottom > top) hit(listX + 4, top, listW - 12, bottom - top, (hx, hy, b) -> {
                    choose(choice);
                    Sounds.click();
                });
            }
            ry += ROW + ROW_GAP;
        }
        Render2D.unclip(ctx);
        if (maxScroll > 0) {
            float bar = Math.max(16, listH * listH / content);
            Render2D.roundRect(ctx, listX + listW - 5, listY + (listH - bar) * (scroll / maxScroll), 3, bar, 1.5f, Theme.accent(0xA0));
        }
    }

    /** The Colors tab: the three colours (click to step through a palette) and the motion switches. */
    private void colors(DrawContext ctx, int mx, int my) {
        float ry = listY + 6;
        for (String name : new String[] {"Primary Color", "Accent Color", "Halo Color"}) {
            ColorSetting c = (ColorSetting) setting(name);
            boolean hov = inside(mx, my, listX + 4, ry, listW - 12, ROW);
            Render2D.roundRect(ctx, listX + 4, ry, listW - 12, ROW, 5, hov ? 0xFF232838 : 0xFF181C26);
            Fonts.drawV(ctx, name, listX + 12, ry + ROW / 2f, Theme.TEXT, false, 0.7f);
            Render2D.roundRect(ctx, listX + listW - 40, ry + 4, 26, ROW - 8, 3, c.get());
            hit(listX + 4, ry, listW - 12, ROW, (hx, hy, b) -> {
                int i = 0;
                for (int k = 0; k < PALETTE.length; k++) if ((PALETTE[k] & 0xFFFFFF) == (c.get() & 0xFFFFFF)) i = k;
                c.set(PALETTE[(i + (b == 1 ? PALETTE.length - 1 : 1)) % PALETTE.length]);
                Sounds.click();
            });
            ry += ROW + ROW_GAP;
        }
        for (String name : new String[] {"Rainbow", "Glowing Accents", "Animate", "React To Movement"}) {
            BooleanSetting b = (BooleanSetting) setting(name);
            boolean hov = inside(mx, my, listX + 4, ry, listW - 12, ROW);
            Render2D.roundRect(ctx, listX + 4, ry, listW - 12, ROW, 5, hov ? 0xFF232838 : 0xFF181C26);
            Fonts.drawV(ctx, name, listX + 12, ry + ROW / 2f, Theme.TEXT, false, 0.7f);
            float sx = listX + listW - 40;
            Render2D.roundRect(ctx, sx, ry + 4, 22, ROW - 8, (ROW - 8) / 2f, b.get() ? Theme.accent() : 0xFF2A2F3D);
            Render2D.circle(ctx, b.get() ? sx + 22 - (ROW - 8) / 2f : sx + (ROW - 8) / 2f, ry + ROW / 2f, (ROW - 8) / 2f - 1.5f, 0xFFFFFFFF);
            hit(listX + 4, ry, listW - 12, ROW, (hx, hy, bt) -> {
                b.toggle();
                Sounds.toggle(b.get());
            });
            ry += ROW + ROW_GAP;
        }
    }

    private void footer(DrawContext ctx, int mx, int my) {
        NumberSetting size = size();
        float fy = listY + listH + 6;
        if (size != null) {
            Fonts.drawV(ctx, "Size", listX + 2, fy + 4, Theme.TEXT_DIM, false, 0.66f);
            // Sizes read as a share of normal size (100% is as made); the halo's height in pixels.
            String value = size.getName().equals("Halo Height") ? size.format() : Math.round(size.getFloat() * 100) + "%";
            Fonts.drawRight(ctx, value, listX + listW - 2, fy + 4, Theme.TEXT_DIM, false, 0.66f);
            sliderX = listX + 2;
            sliderW = listW - 4;
            float sy = fy + 14;
            float pct = (float) size.getPercent();
            Render2D.roundRect(ctx, sliderX, sy - 2, sliderW, 4, 2, 0xFF2A2F3D);
            Render2D.roundGradientH(ctx, sliderX, sy - 2, sliderW * pct, 4, 2, Theme.accent2(), Theme.accent());
            Render2D.circle(ctx, sliderX + sliderW * pct, sy, 4.2f, 0xFFFFFFFF);
            hit(sliderX - 4, sy - 6, sliderW + 8, 12, (hx, hy, b) -> {
                if (b == 1) {
                    size.reset();
                    return;
                }
                sizing = true;
                size.setPercent((hx - sliderX) / sliderW);
            });
        }
        float ay = y + h - 30;
        button(ctx, "Apply", listX, ay, listW, 20, true, mx, my, () -> {
            module.setEnabled(true);
            Sounds.toggle(true);
            close();
        });
    }

    private void button(DrawContext ctx, String label, float bx, float by, float bw, float bh, boolean accent, int mx, int my, Runnable action) {
        boolean hover = inside(mx, my, bx, by, bw, bh);
        if (accent) Render2D.roundRect(ctx, bx, by, bw, bh, 6, hover ? Theme.accent() : Theme.accent(0xD0));
        else {
            Render2D.roundRect(ctx, bx, by, bw, bh, 6, hover ? 0xFF262B3A : 0xFF1A1E29);
            Render2D.roundOutline(ctx, bx, by, bw, bh, 6, 1, hover ? 0x40FFFFFF : 0x1EFFFFFF);
        }
        Fonts.drawCentered(ctx, label, bx + bw / 2f, by + bh / 2f, accent || hover ? 0xFFFFFFFF : Theme.TEXT_DIM, false, 0.68f);
        hit(bx, by, bw, bh, (hx, hy, b) -> {
            Sounds.click();
            action.run();
        });
    }

    private void hit(float hx, float hy, float hw, float hh, Action action) {
        hits.add(new Hit(hx, hy, hw, hh, action));
    }

    private static boolean inside(double mx, double my, float bx, float by, float bw, float bh) {
        return mx >= bx && my >= by && mx < bx + bw && my < by + bh;
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                hit.action().run(click.x(), click.y(), click.button());
                return true;
            }
        }
        if (click.x() < x || click.x() > x + w || click.y() < y || click.y() > y + h) close();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging) {
            rotation = dragFromRotation + (float) (click.x() - dragFromX) * 1.6f;
            return true;
        }
        if (sizing && size() != null) {
            size().setPercent((click.x() - sliderX) / sliderW);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        sizing = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 30;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
