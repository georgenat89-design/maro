package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.misc.AutoGoliath;
import dev.maro.nathan.regionmap.RegionGrid;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The region map for Auto Goliath: every goliath as a tile in its region's colour with its number,
 * where you are marked with a dot. Click a tile to aim for it; Start begins teleporting.
 */
public final class GoliathPickerScreen extends Screen {
    private static final int[] COLORS = {0xFF3B82F6, 0xFF22C55E, 0xFFF59E0B, 0xFFEC4899, 0xFF14B8A6, 0xFFA855F7};

    private final Screen parent;
    private final AutoGoliath module;
    private float mapX, mapY, cell, px, py, pw, ph;

    public GoliathPickerScreen(Screen parent, AutoGoliath module) {
        super(Text.literal("Auto Goliath"));
        this.parent = parent;
        this.module = module;
    }

    private static int colorOf(RegionGrid.Locale locale) {
        int r = AutoGoliath.regionOf(locale);
        return r < 0 ? 0xFF4B5263 : COLORS[r];
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Fonts.beginRaw();
        try {
            float size = Math.min(height - 70, width - 200);
            cell = size / RegionGrid.SIDE;
            pw = size + 170;
            ph = size + 50;
            px = (width - pw) / 2f;
            py = (height - ph) / 2f;
            mapX = px + 14;
            mapY = py + 36;
            Render2D.rect(ctx, 0, 0, width, height, 0xA0000000);
            Render2D.roundRect(ctx, px, py, pw, ph, 12, 0xF5141720);
            Render2D.roundOutline(ctx, px, py, pw, ph, 12, 1, 0x1CFFFFFF);
            Render2D.roundRect(ctx, px, py, pw, 3, 1.5f, Theme.accent(0xC0));
            Fonts.drawV(ctx, "Auto Goliath", px + 14, py + 17, Theme.TEXT, true, 0.95f);
            Fonts.drawV(ctx, "Click the goliath to land in", px + 14 + Fonts.width("Auto Goliath", true, 0.95f) + 8, py + 17, Theme.TEXT_MUTED, false, 0.6f);

            // The tiles.
            int target = module.regionIndex();
            int number = module.goliathNumber();
            RegionGrid.Shard hovered = null;
            for (int i = 0; i < RegionGrid.count(); i++) {
                RegionGrid.Shard s = RegionGrid.shard(i);
                float x = mapX + s.col() * cell, y = mapY + s.row() * cell, w = s.width() * cell, h = s.height() * cell;
                boolean picked = AutoGoliath.regionOf(s.locale()) == target && s.number() == number;
                boolean hov = mx >= x && my >= y && mx < x + w && my < y + h;
                if (hov) hovered = s;
                int base = colorOf(s.locale());
                int fill = picked ? base : ColorUtil.withAlpha(base, hov ? 0xA0 : 0x58);
                Render2D.roundRect(ctx, x + 0.6f, y + 0.6f, w - 1.2f, h - 1.2f, Math.min(3f, cell * 0.3f), fill);
                if (picked) Render2D.roundOutline(ctx, x, y, w, h, Math.min(3f, cell * 0.3f), 1.5f, 0xFFFFFFFF);
                if (cell * Math.min(s.width(), s.height()) >= 9) {
                    float scale = Math.min(0.62f, cell * Math.min(s.width(), s.height()) / 18f);
                    Fonts.drawCentered(ctx, String.valueOf(s.number()), x + w / 2f, y + h / 2f, picked ? 0xFF000000 : 0xE0FFFFFF, picked, scale);
                }
            }
            // You.
            RegionGrid.Shard here = module.here();
            if (client != null && client.player != null && here != null) {
                float yx = mapX + (float) RegionGrid.column(client.player.getX()) * cell, yy = mapY + (float) RegionGrid.row(client.player.getZ()) * cell;
                Render2D.circle(ctx, yx, yy, 3.4f, 0xFF000000);
                Render2D.circle(ctx, yx, yy, 2.4f, 0xFFFFFFFF);
            }

            // The legend and the choice on the right.
            float lx = mapX + RegionGrid.SIDE * cell + 14, ly = mapY + 4;
            for (int r = 0; r < AutoGoliath.REGIONS.length; r++) {
                Render2D.roundRect(ctx, lx, ly - 4, 8, 8, 2, COLORS[r]);
                Fonts.drawV(ctx, AutoGoliath.REGIONS[r] + "  (" + AutoGoliath.count(r) + ")", lx + 12, ly, r == target ? 0xFFFFFFFF : Theme.TEXT_DIM, r == target, 0.62f);
                ly += 13;
            }
            ly += 8;
            Fonts.drawV(ctx, "Target", lx, ly, Theme.TEXT_MUTED, false, 0.6f);
            Fonts.drawV(ctx, AutoGoliath.REGIONS[target] + " " + number, lx, ly + 12, Theme.accent(), true, 0.85f);
            if (here != null) {
                Fonts.drawV(ctx, "You are in", lx, ly + 30, Theme.TEXT_MUTED, false, 0.6f);
                Fonts.drawV(ctx, AutoGoliath.name(here), lx, ly + 42, Theme.TEXT, false, 0.7f);
            }
            if (hovered != null) Fonts.drawV(ctx, AutoGoliath.name(hovered), lx, py + ph - 52, Theme.TEXT_DIM, false, 0.66f);

            // Start / Stop and Done.
            float bw = 136, bh = 18, by = py + ph - 40;
            boolean on = module.isEnabled();
            boolean hovStart = mx >= lx && my >= by && mx < lx + bw && my < by + bh;
            Render2D.roundRect(ctx, lx, by, bw, bh, 6, on ? (hovStart ? 0xFFE5484D : 0xC0E5484D) : (hovStart ? Theme.accent() : Theme.accent(0xD0)));
            Fonts.drawCentered(ctx, on ? "Stop" : "Start", lx + bw / 2f, by + bh / 2f, 0xFFFFFFFF, true, 0.68f);
            boolean hovDone = mx >= lx && my >= by + 22 && mx < lx + bw && my < by + 22 + bh;
            Render2D.roundRect(ctx, lx, by + 22, bw, bh, 6, hovDone ? 0xFF262B3A : 0xFF1A1E29);
            Fonts.drawCentered(ctx, "Done", lx + bw / 2f, by + 22 + bh / 2f, Theme.TEXT_DIM, false, 0.66f);
        } finally {
            Fonts.endRaw();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        float lx = mapX + RegionGrid.SIDE * cell + 14, bw = 136, bh = 18, by = py + ph - 40;
        if (mx >= lx && mx < lx + bw && my >= by && my < by + bh) {
            module.toggle();
            Sounds.toggle(module.isEnabled());
            if (module.isEnabled()) close();
            return true;
        }
        if (mx >= lx && mx < lx + bw && my >= by + 22 && my < by + 22 + bh) {
            close();
            return true;
        }
        int id = RegionGrid.cell((mx - mapX) / cell, (my - mapY) / cell);
        if (id >= 0) {
            RegionGrid.Shard s = RegionGrid.shard(id);
            int r = AutoGoliath.regionOf(s.locale());
            if (r >= 0) {
                module.pick(r, s.number());
                Sounds.click();
            }
            return true;
        }
        if (mx < px || mx > px + pw || my < py || my > py + ph) close();
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
