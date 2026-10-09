package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Every block and item in a searchable grid; a click picks one and goes back. */
public final class ItemPickerScreen extends Screen {
    private static final int CELL = 22;

    private final Screen parent;
    private final String what;
    private final Consumer<Item> picked;
    private final List<Item> all = new ArrayList<>();
    private List<Item> shown = new ArrayList<>();
    private String query = "";
    private float scroll, scrollTarget;
    private float gx, gy, gw, gh;
    private int cols;

    public ItemPickerScreen(Screen parent, String what, Consumer<Item> picked) {
        super(Text.literal("Pick " + what));
        this.parent = parent;
        this.what = what;
        this.picked = picked;
        for (Item item : Registries.ITEM) if (item != Items.AIR) all.add(item);
        filter();
    }

    private void filter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (Item item : all) {
            if (q.isEmpty() || Registries.ITEM.getId(item).getPath().replace('_', ' ').contains(q)
                    || item.getName().getString().toLowerCase(Locale.ROOT).contains(q)) shown.add(item);
        }
        scroll = scrollTarget = 0;
    }

    /** Searches, as typing does; for tests. */
    public List<Item> search(String words) {
        query = words;
        filter();
        return shown;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        float pw = Math.min(520, width - 24), ph = Math.min(360, height - 24);
        float px = (width - pw) / 2f, py = (height - ph) / 2f;
        Render2D.rect(ctx, 0, 0, width, height, 0xA0000000);
        Render2D.roundRect(ctx, px, py, pw, ph, 12, 0xF5141720);
        Render2D.roundOutline(ctx, px, py, pw, ph, 12, 1, 0x1CFFFFFF);
        Render2D.roundRect(ctx, px, py, pw, 3, 1.5f, Theme.accent(0xC0));
        Fonts.beginRaw();
        try {
            Fonts.drawV(ctx, "Pick " + what, px + 14, py + 16, Theme.TEXT, true, 0.95f);
            float sx = px + 14, sy = py + 28, sw = pw - 28;
            Render2D.roundRect(ctx, sx, sy, sw, 20, 6, 0xFF0C0E14);
            Render2D.roundOutline(ctx, sx, sy, sw, 20, 6, 1, Theme.accent(0x90));
            Fonts.drawV(ctx, query.isEmpty() ? "Type to search…" : query, sx + 8, sy + 10, query.isEmpty() ? 0xFF545A6C : Theme.TEXT, false, 0.7f);
            Fonts.drawRight(ctx, shown.size() + " found", sx + sw - 8, sy + 10, Theme.TEXT_MUTED, false, 0.6f);

            gx = px + 14;
            gy = sy + 28;
            gw = pw - 28;
            gh = py + ph - 12 - gy;
            cols = Math.max(1, (int) (gw / CELL));
            int rows = (shown.size() + cols - 1) / cols;
            float maxScroll = Math.max(0, rows * CELL - gh);
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
            scroll += (scrollTarget - scroll) * 0.35f;
            Render2D.clip(ctx, (int) gx, (int) gy, (int) (gx + gw), (int) (gy + gh));
            Item hovered = null;
            for (int i = 0; i < shown.size(); i++) {
                float cx = gx + (i % cols) * CELL, cy = gy + (i / cols) * CELL - scroll;
                if (cy + CELL < gy || cy > gy + gh) continue;
                boolean hov = mx >= cx && my >= cy && mx < cx + CELL && my < cy + CELL && my >= gy && my < gy + gh;
                if (hov) hovered = shown.get(i);
                Render2D.roundRect(ctx, cx + 1, cy + 1, CELL - 2, CELL - 2, 4, hov ? Theme.accent(0x60) : 0xFF1A1E29);
                ctx.drawItem(new ItemStack(shown.get(i)), (int) cx + 3, (int) cy + 3);
            }
            Render2D.unclip(ctx);
            if (hovered != null) {
                String name = hovered.getName().getString();
                float tw = Fonts.width(name, false, 0.66f) + 10;
                float tx = Math.min(mx + 8, width - tw - 4), ty = my - 16;
                Render2D.roundRect(ctx, tx, ty, tw, 13, 4, 0xF0101218);
                Fonts.drawV(ctx, name, tx + 5, ty + 6.5f, Theme.TEXT, false, 0.66f);
            }
        } finally {
            Fonts.endRaw();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (mx >= gx && my >= gy && mx < gx + gw && my < gy + gh) {
            int col = (int) ((mx - gx) / CELL), row = (int) ((my - gy + scroll) / CELL);
            int i = row * cols + col;
            if (col < cols && i >= 0 && i < shown.size()) {
                picked.accept(shown.get(i));
                Sounds.click();
                close();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * CELL * 2;
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.codepoint() > Character.MAX_VALUE) return true;
        char c = (char) input.codepoint();
        if (c >= 32 && c != 127 && query.length() < 32) {
            query += c;
            filter();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty()) {
                query = "";
                filter();
            } else close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            filter();
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
