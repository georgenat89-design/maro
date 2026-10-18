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
 * where you are marked with a dot. Click tiles to select destinations; Start stops at any selection.
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
            Fonts.drawV(ctx, "Click tiles to add or remove", px + 14 + Fonts.width("Auto Goliath", true, 0.95f) + 8, py + 17, Theme.TEXT_MUTED, false, 0.6f);

            // The tiles.
            int target = module.regionIndex();
            RegionGrid.Shard hovered = null;
            for (int i = 0; i < RegionGrid.count(); i++) {
                RegionGrid.Shard s = RegionGrid.shard(i);
                float x = mapX + s.col() * cell, y = mapY + s.row() * cell, w = s.width() * cell, h = s.height() * cell;
                boolean picked = module.isPicked(AutoGoliath.regionOf(s.locale()), s.number());
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
            if (ph >= 280) {
                for (int r = 0; r < AutoGoliath.REGIONS.length; r++) {
                    Render2D.roundRect(ctx, lx, ly - 4, 8, 8, 2, COLORS[r]);
                    Fonts.drawV(ctx, AutoGoliath.REGIONS[r] + "  (" + AutoGoliath.count(r) + ")", lx + 12, ly, r == target ? 0xFFFFFFFF : Theme.TEXT_DIM, r == target, 0.62f);
                    ly += 13;
                }
                ly += 8;
            }
            var selections = module.selectedTargets();
            Fonts.drawV(ctx, "Selected · " + selections.size(), lx, ly, Theme.TEXT_MUTED, false, 0.6f);
            int rows = Math.max(1, Math.min(4, (int) ((py + ph - 116 - ly) / 12)));
            for (int i = 0; i < Math.min(rows, selections.size()); i++) {
                Fonts.drawV(ctx, selections.get(i).name(), lx, ly + 12 + i * 12, Theme.accent(), true, 0.65f);
            }
            ly += 12 + Math.min(rows, selections.size()) * 12;
            if (selections.size() > rows) {
                Fonts.drawV(ctx, "+ " + (selections.size() - rows) + " more on map", lx, ly, Theme.TEXT_DIM, false, 0.6f);
                ly += 12;
            }
            if (here != null && ly + 6 < py + ph - 80) {
                Fonts.drawV(ctx, "You are in " + AutoGoliath.name(here), lx, ly + 6, Theme.TEXT_DIM, false, 0.6f);
            }
            Fonts.drawV(ctx, "Stops at any selection", lx, py + ph - 68, Theme.TEXT_MUTED, false, 0.6f);
            Fonts.drawV(ctx, "Right-click: select only one", mapX, py + ph - 6, Theme.TEXT_MUTED, false, 0.55f);
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
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) module.pick(r, s.number());
                else if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) module.togglePick(r, s.number());
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

    /** Tile center in the same layout used for drawing and hit testing. */
    public double[] tilePoint(int region, int number) {
        for (int i = 0; i < RegionGrid.count(); i++) {
            var shard = RegionGrid.shard(i);
            if (AutoGoliath.regionOf(shard.locale()) == region && shard.number() == number)
                return new double[] {mapX + (shard.col() + shard.width() * 0.5) * cell,
                        mapY + (shard.row() + shard.height() * 0.5) * cell};
        }
        throw new IllegalArgumentException("Unknown Goliath");
    }
}
