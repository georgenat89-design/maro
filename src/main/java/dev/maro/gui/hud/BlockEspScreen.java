package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.BlockESP;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Block ESP's block picker: every block in the game in a searchable grid on the left, click one to
 * add or remove it; the ones picked on the right, each with its own colour, a click away from a
 * colour picker. Presets along the bottom add spawners, storage or ores in one go. Typing anywhere
 * searches.
 */
public final class BlockEspScreen extends Screen {
    private static final float TILE = 26, GAP = 4, ROW = 26, PICKER = 74;
    private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
    /** Colours a click away under the picker. */
    private static final int[] QUICK = {0xFFFF3B6B, 0xFFFFA733, 0xFFFFD84A, 0xFF35F07A, 0xFF4DF0FF, 0xFF3D6BFF, 0xFFB45CFF, 0xFFFFFFFF};

    private final Screen parent;
    private final BlockESP module;
    private final List<Block> all = new ArrayList<>();
    private final Map<Block, String> names = new IdentityHashMap<>();
    /** Each block's picture: an item, and for some a small second item in the corner (a pot, a cake). */
    private record Icon(ItemStack item, ItemStack badge) {
    }

    private final Map<Block, Icon> icons = new IdentityHashMap<>();
    private List<Block> shown = new ArrayList<>();
    private String query = "";

    /** The picked block whose colour picker is open, and its colour as hue, saturation, value. */
    private Block expanded;
    private float[] hsv = {0, 0, 1};
    private float expandAnim;

    private float leftScroll, leftTarget, rightScroll, rightTarget;
    private Block hoveredTile;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    private interface Action {
        void run(double mx, double my);
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Action dragging;

    // This frame's layout.
    private float px, py, pw, ph, gridX, gridY, gridW, gridH, listX, listY, listW, listH;

    public BlockEspScreen(Screen parent, BlockESP module) {
        super(Text.literal(module.getName() + " blocks"));
        this.parent = parent;
        this.module = module;
        for (Block block : Registries.BLOCK) {
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
            // Only what this module finds: Block ESP leaves storage to Storage ESP, which has only storage.
            if (!module.allows(block)) continue;
            all.add(block);
            names.put(block, block.getName().getString());
        }
        all.sort(Comparator.comparing(names::get, String.CASE_INSENSITIVE_ORDER));
        refilter();
    }

    // ---- searching ------------------------------------------------------------------------------

    /** Shows only blocks whose name or id has {@code text} in it; for tests as well as typing. */
    public void search(String text) {
        query = text == null ? "" : text;
        refilter();
    }

    public List<Block> results() {
        return shown;
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            shown = all;
        } else {
            List<Block> out = new ArrayList<>();
            for (Block block : all) {
                if (names.get(block).toLowerCase(Locale.ROOT).contains(q) || Registries.BLOCK.getId(block).getPath().contains(q.replace(' ', '_'))) {
                    out.add(block);
                }
            }
            shown = out;
        }
        leftTarget = 0;
    }

    /** Adds the block, or takes it off if it is already picked. */
    public void toggle(Block block) {
        if (module.isPicked(block)) {
            module.unpick(block);
            if (expanded == block) expanded = null;
        } else {
            module.pick(block);
            // New picks go at the end of the list: bring it into view.
            rightTarget = Float.MAX_VALUE;
        }
        Sounds.click();
    }

    /** Opens the colour picker for a picked block (or closes it); for tests as well as clicks. */
    public void expand(Block block) {
        if (expanded == block) {
            expanded = null;
        } else {
            expanded = block;
            expandAnim = 0;
            hsv = ColorUtil.toHsv(module.colorOf(block));
        }
    }

    // ---- drawing --------------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        hits.clear();
        hoveredTile = null;
        Theme.update();
        layout();

        float open = Easing.outCubic(Math.min(1f, (now - openedAt) / 1e9f / 0.18f));
        Render2D.setAlpha(open);
        Render2D.rect(ctx, 0, 0, width, height, 0xA0000000);
        Render2D.shadow(ctx, px, py + 4, pw, ph, 14, 20, 0x90000000);
        Render2D.roundRect(ctx, px, py, pw, ph, 12, 0xF5141720);
        Render2D.roundOutline(ctx, px, py, pw, ph, 12, 1, 0x1CFFFFFF);

        float ease = Math.min(1f, dt * 16f);
        leftScroll += (leftTarget - leftScroll) * ease;
        rightScroll += (rightTarget - rightScroll) * ease;
        expandAnim += ((expanded != null ? 1f : 0f) - expandAnim) * Math.min(1f, dt * 14f);

        header(ctx, mx, my, now);
        grid(ctx, mx, my);
        list(ctx, mx, my);
        footer(ctx, mx, my);
        if (hoveredTile != null) tooltip(ctx, hoveredTile, mx, my);
        Render2D.setAlpha(1f);
    }

    private void layout() {
        pw = Math.min(680, width - 24);
        ph = Math.min(410, height - 24);
        px = Math.round((width - pw) / 2f);
        py = Math.round((height - ph) / 2f);
        float inner = pw - 32;
        gridX = px + 16;
        gridY = py + 66;
        gridW = Math.round(inner * 0.58f);
        gridH = ph - 66 - 44;
        listX = gridX + gridW + 16;
        listY = gridY;
        listW = px + pw - 16 - listX;
        listH = gridH;
    }

    private void header(DrawContext ctx, int mx, int my, long now) {
        Fonts.drawV(ctx, "BLOCK ESP", px + 16, py + 20, Theme.accent(), true, 0.95f);
        int count = module.pickedBlocks().size();
        Fonts.beginRaw();
        try {
            Fonts.drawV(ctx, count + (count == 1 ? " block" : " blocks") + " picked  ·  click a block to add or remove it, type to search",
                    px + 16, py + 34, Theme.TEXT_MUTED, false, 0.62f);
        } finally {
            Fonts.endRaw();
        }

        // The search box: typing anywhere goes into it.
        float sw = Math.min(210, pw * 0.34f), sh = 20, sx = px + pw - 16 - sw, sy = py + 14;
        Render2D.roundRect(ctx, sx, sy, sw, sh, 6, 0xFF0C0E14);
        Render2D.roundOutline(ctx, sx, sy, sw, sh, 6, 1, query.isEmpty() ? 0x22FFFFFF : Theme.accent(0xA0));
        float gx = sx + 11, gy = sy + sh / 2f - 0.5f;
        Render2D.ring(ctx, gx, gy, 3.6f, 1.2f, Theme.TEXT_MUTED);
        Render2D.line(ctx, gx + 2.6f, gy + 2.6f, gx + 5f, gy + 5f, 1.3f, Theme.TEXT_MUTED);
        Fonts.beginRaw();
        try {
            float tx = sx + 21;
            if (query.isEmpty()) {
                Fonts.drawV(ctx, "Search blocks…", tx, sy + sh / 2f, 0xFF5E6478, false, 0.7f);
            } else {
                String shownText = query;
                while (shownText.length() > 1 && Fonts.width(shownText, false, 0.7f) > sw - 40) shownText = shownText.substring(1);
                Fonts.drawV(ctx, shownText, tx, sy + sh / 2f, Theme.TEXT, false, 0.7f);
                if ((now / 500_000_000L) % 2 == 0) {
                    float cx = tx + Fonts.width(shownText, false, 0.7f) + 1;
                    Render2D.rect(ctx, cx, sy + 5, 1, sh - 10, Theme.TEXT_DIM);
                }
                // A little cross to clear it.
                float cx = sx + sw - 10, cy = sy + sh / 2f;
                boolean hover = mx >= cx - 6 && mx <= cx + 6 && my >= sy && my <= sy + sh;
                int c = hover ? Theme.TEXT : Theme.TEXT_MUTED;
                Render2D.line(ctx, cx - 3, cy - 3, cx + 3, cy + 3, 1.2f, c);
                Render2D.line(ctx, cx - 3, cy + 3, cx + 3, cy - 3, 1.2f, c);
                hit(cx - 7, sy, 14, sh, (x, y) -> search(""));
            }
        } finally {
            Fonts.endRaw();
        }
    }

    private void grid(DrawContext ctx, int mx, int my) {
        Fonts.drawV(ctx, "ALL BLOCKS", gridX, gridY - 9, Theme.TEXT_DIM, true, 0.62f);
        Fonts.drawRight(ctx, Integer.toString(shown.size()), gridX + gridW, gridY - 9, Theme.TEXT_MUTED, false, 0.6f);

        int cols = Math.max(1, (int) ((gridW - 6 + GAP) / (TILE + GAP)));
        float used = cols * (TILE + GAP) - GAP;
        float left = gridX + (gridW - 6 - used) / 2f;
        int rows = (shown.size() + cols - 1) / cols;
        float content = rows * (TILE + GAP);
        leftTarget = clamp(leftTarget, 0, Math.max(0, content - gridH));
        leftScroll = clamp(leftScroll, 0, Math.max(0, content - gridH));

        if (shown.isEmpty()) {
            Fonts.beginRaw();
            try {
                Fonts.drawCentered(ctx, "No block called \"" + query + "\"", gridX + gridW / 2f, gridY + 40, Theme.TEXT_MUTED, false, 0.7f);
            } finally {
                Fonts.endRaw();
            }
            return;
        }

        boolean inGrid = mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH;
        Render2D.clip(ctx, Math.round(gridX - 2), Math.round(gridY), Math.round(gridX + gridW + 2), Math.round(gridY + gridH));
        int first = Math.max(0, (int) (leftScroll / (TILE + GAP)));
        int last = Math.min(rows - 1, (int) ((leftScroll + gridH) / (TILE + GAP)) + 1);
        for (int row = first; row <= last; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                if (index >= shown.size()) break;
                Block block = shown.get(index);
                float tx = left + col * (TILE + GAP), ty = gridY + row * (TILE + GAP) - leftScroll;
                boolean picked = module.isPicked(block);
                int color = module.colorOf(block);
                boolean hover = inGrid && mx >= tx && mx < tx + TILE && my >= ty && my < ty + TILE;
                int bg = picked ? ColorUtil.withAlpha(color, hover ? 0x48 : 0x30) : hover ? 0xFF262B3A : 0xFF1A1E29;
                Render2D.roundRect(ctx, tx, ty, TILE, TILE, 6, bg);
                if (picked) Render2D.roundOutline(ctx, tx, ty, TILE, TILE, 6, 1.2f, color);
                else if (hover) Render2D.roundOutline(ctx, tx, ty, TILE, TILE, 6, 1, 0x30FFFFFF);
                icon(ctx, block, tx + 5, ty + 5);
                if (picked) {
                    Render2D.circle(ctx, tx + TILE - 4.5f, ty + 4.5f, 3.2f, 0xFF141720);
                    Render2D.circle(ctx, tx + TILE - 4.5f, ty + 4.5f, 2.2f, color);
                }
                if (hover) hoveredTile = block;
                float top = Math.max(ty, gridY), bottom = Math.min(ty + TILE, gridY + gridH);
                if (bottom > top) hit(tx, top, TILE, bottom - top, (x, y) -> toggle(block));
            }
        }
        Render2D.unclip(ctx);
        scrollbar(ctx, gridX + gridW - 3, gridY, gridH, leftScroll, content);
    }

    private void list(DrawContext ctx, int mx, int my) {
        List<Block> picked = module.pickedBlocks();
        Fonts.drawV(ctx, "PICKED", listX, listY - 9, Theme.TEXT_DIM, true, 0.62f);
        if (!picked.isEmpty()) {
            String clear = "Clear all";
            float cw = Fonts.width(clear, false, 0.6f);
            float cx = listX + listW - cw;
            boolean hover = mx >= cx - 3 && mx <= listX + listW && my >= listY - 15 && my <= listY - 3;
            Fonts.drawV(ctx, clear, cx, listY - 9, hover ? Theme.RED : Theme.TEXT_MUTED, false, 0.6f);
            hit(cx - 3, listY - 15, cw + 6, 12, (x, y) -> {
                module.clearPicked();
                expanded = null;
                Sounds.click();
            });
        }

        Render2D.roundRect(ctx, listX, listY, listW, listH, 8, 0xFF10131A);
        if (picked.isEmpty()) {
            Fonts.beginRaw();
            try {
                Fonts.drawCentered(ctx, "Nothing picked yet", listX + listW / 2f, listY + listH / 2f - 6, Theme.TEXT_DIM, false, 0.72f);
                Fonts.drawCentered(ctx, "Click blocks on the left, or a preset below", listX + listW / 2f, listY + listH / 2f + 7, Theme.TEXT_MUTED, false, 0.6f);
            } finally {
                Fonts.endRaw();
            }
            return;
        }

        float content = picked.size() * (ROW + 3) + (expanded != null ? PICKER * expandAnim : 0) + 4;
        rightTarget = clamp(rightTarget, 0, Math.max(0, content - listH));
        rightScroll = clamp(rightScroll, 0, Math.max(0, content - listH));
        boolean inList = mx >= listX && mx < listX + listW && my >= listY && my < listY + listH;

        Render2D.clip(ctx, Math.round(listX), Math.round(listY), Math.round(listX + listW), Math.round(listY + listH));
        float y = listY + 4 - rightScroll;
        float rowX = listX + 4, rowW = listW - 8 - (content > listH ? 4 : 0);
        for (Block block : picked) {
            boolean open = block == expanded;
            float extra = open ? PICKER * expandAnim : 0;
            if (y + ROW + extra >= listY && y <= listY + listH) {
                int color = module.colorOf(block);
                boolean hover = inList && mx >= rowX && mx < rowX + rowW && my >= y && my < y + ROW;
                Render2D.roundRect(ctx, rowX, y, rowW, ROW + extra, 6, open ? 0xFF1E2331 : hover ? 0xFF1C2130 : 0xFF171B25);
                Render2D.roundRect(ctx, rowX, y + 5, 2.5f, ROW - 10, 1.2f, color);
                icon(ctx, block, rowX + 8, y + 5);

                // Right to left: remove, swatch, name.
                float xx = rowX + rowW - 12, cy = y + ROW / 2f;
                boolean overX = hover && mx >= xx - 7;
                int xc = overX ? Theme.RED : Theme.TEXT_MUTED;
                Render2D.line(ctx, xx - 3, cy - 3, xx + 3, cy + 3, 1.2f, xc);
                Render2D.line(ctx, xx - 3, cy + 3, xx + 3, cy - 3, 1.2f, xc);
                float sx = xx - 14 - 22, sy = cy - 6;
                Render2D.roundRect(ctx, sx, sy, 22, 12, 3, color);
                Render2D.roundOutline(ctx, sx, sy, 22, 12, 3, 1, open ? 0xC0FFFFFF : 0x40FFFFFF);
                Fonts.beginRaw();
                try {
                    float nameX = rowX + 29;
                    String name = Fonts.trim(names.getOrDefault(block, block.getName().getString()), sx - 8 - nameX, false, 0.7f);
                    Fonts.drawV(ctx, name, nameX, cy, Theme.TEXT, false, 0.7f);
                } finally {
                    Fonts.endRaw();
                }
                float top = Math.max(y, listY), bottom = Math.min(y + ROW, listY + listH);
                if (bottom > top) {
                    hit(rowX, top, rowW - 20, bottom - top, (x, yy) -> {
                        expand(block);
                        Sounds.click();
                    });
                    hit(xx - 8, top, 16, bottom - top, (x, yy) -> toggle(block));
                }
                if (open && expandAnim > 0.05f) picker(ctx, block, rowX + 8, y + ROW + 2, rowW - 16, extra - 8);
            }
            y += ROW + 3 + extra;
        }
        Render2D.unclip(ctx);
        scrollbar(ctx, listX + listW - 4, listY, listH, rightScroll, content);
    }

    /** Saturation and value in a square, hue in a bar beside it, and some quick colours under. */
    private void picker(DrawContext ctx, Block block, float x, float y, float w, float h) {
        if (h < 12) return;
        float quickH = 12, boxH = Math.max(8, h - quickH - 6), barW = 8;
        float boxW = w - barW - 6;
        int hueColor = ColorUtil.hsv(hsv[0], 1, 1);
        Render2D.roundRect(ctx, x, y, boxW, boxH, 4, 0xFFFFFFFF, hueColor, hueColor, 0xFFFFFFFF);
        Render2D.roundRect(ctx, x, y, boxW, boxH, 4, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
        Render2D.ring(ctx, x + hsv[1] * boxW, y + (1 - hsv[2]) * boxH, 3.2f, 1.2f, 0xFFFFFFFF);
        hitClipped(x, y, boxW, boxH, (mx, my) -> dragging = (dx, dy) -> {
            hsv[1] = clamp((float) ((dx - x) / boxW), 0, 1);
            hsv[2] = 1 - clamp((float) ((dy - y) / boxH), 0, 1);
            apply(block);
        });

        float bx = x + boxW + 6;
        float seg = boxH / 6f;
        for (int i = 0; i < 6; i++) {
            Render2D.rectGradient(ctx, bx, y + seg * i, barW, seg + 0.01f, HUES[i], HUES[i], HUES[i + 1], HUES[i + 1]);
        }
        Render2D.roundRect(ctx, bx - 1, y + hsv[0] * boxH - 1, barW + 2, 2, 1, 0xFFFFFFFF);
        hitClipped(bx - 2, y, barW + 4, boxH, (mx, my) -> dragging = (dx, dy) -> {
            hsv[0] = clamp((float) ((dy - y) / boxH), 0, 0.999f);
            apply(block);
        });

        float qy = y + boxH + 6;
        float step = Math.min(18, (boxW - 40) / QUICK.length);
        for (int i = 0; i < QUICK.length; i++) {
            int c = QUICK[i];
            float cx = x + 6 + i * step;
            Render2D.circle(ctx, cx, qy + quickH / 2f, 5, c);
            if ((module.colorOf(block) & 0xFFFFFF) == (c & 0xFFFFFF)) Render2D.ring(ctx, cx, qy + quickH / 2f, 6.5f, 1, 0xFFFFFFFF);
            hitClipped(cx - 6, qy, 12, quickH, (mx, my) -> {
                hsv = ColorUtil.toHsv(c);
                apply(block);
                Sounds.click();
            });
        }
        Fonts.drawRight(ctx, String.format(Locale.ROOT, "#%06X", module.colorOf(block) & 0xFFFFFF), x + boxW, qy + quickH / 2f,
                Theme.TEXT_MUTED, false, 0.6f);
    }

    private void apply(Block block) {
        module.setColor(block, ColorUtil.hsv(hsv[0], hsv[1], hsv[2]));
    }

    private void footer(DrawContext ctx, int mx, int my) {
        float y = py + ph - 32, h = 20;
        float x = px + 16;
        Fonts.drawV(ctx, "ADD", x, y + h / 2f, Theme.TEXT_MUTED, true, 0.6f);
        x += Fonts.width("ADD", true, 0.6f) + 8;
        for (Map.Entry<String, List<Block>> preset : module.presetList()) {
            String label = preset.getKey();
            boolean all = !preset.getValue().isEmpty() && preset.getValue().stream().allMatch(module::isPicked);
            float w = Fonts.width(label, false, 0.66f) + 22;
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
            Render2D.roundRect(ctx, x, y, w, h, 10, all ? Theme.accent(0x40) : hover ? 0xFF262B3A : 0xFF1A1E29);
            Render2D.roundOutline(ctx, x, y, w, h, 10, 1, all ? Theme.accent(0xC0) : 0x22FFFFFF);
            // A plus to add the lot, a tick once they are all in.
            float ix = x + 9, iy = y + h / 2f;
            if (all) {
                Render2D.line(ctx, ix - 3, iy, ix - 1, iy + 2.2f, 1.2f, Theme.TEXT);
                Render2D.line(ctx, ix - 1, iy + 2.2f, ix + 3, iy - 2.5f, 1.2f, Theme.TEXT);
            } else {
                Render2D.rect(ctx, ix - 3, iy - 0.6f, 6, 1.2f, Theme.TEXT_DIM);
                Render2D.rect(ctx, ix - 0.6f, iy - 3, 1.2f, 6, Theme.TEXT_DIM);
            }
            Fonts.drawV(ctx, label, x + 15, y + h / 2f, all ? Theme.TEXT : Theme.TEXT_DIM, false, 0.66f);
            List<Block> blocks = preset.getValue();
            hit(x, y, w, h, (mx2, my2) -> {
                // All in already: take them out again; otherwise add whichever are missing.
                if (all) blocks.forEach(module::unpick);
                else blocks.forEach(module::pick);
                Sounds.click();
            });
            x += w + 6;
        }

        float dw = 64, dx = px + pw - 16 - dw;
        boolean hover = mx >= dx && mx < dx + dw && my >= y && my < y + h;
        Render2D.roundRect(ctx, dx, y, dw, h, 10, hover ? Theme.accent() : Theme.accent(0xD0));
        Fonts.drawCentered(ctx, "Done", dx + dw / 2f, y + h / 2f, 0xFFFFFFFF, true, 0.68f);
        hit(dx, y, dw, h, (mx2, my2) -> close());
    }

    private void tooltip(DrawContext ctx, Block block, int mx, int my) {
        Fonts.beginRaw();
        try {
            String name = names.getOrDefault(block, block.getName().getString());
            String hint = module.isPicked(block) ? "Click to remove" : "Click to add";
            float w = Math.max(Fonts.width(name, true, 0.68f), Fonts.width(hint, false, 0.58f)) + 14;
            float h = 28, x = Math.min(mx + 10, width - w - 4), y = Math.min(my + 12, height - h - 4);
            Render2D.roundRect(ctx, x, y, w, h, 6, 0xF20B0D12);
            Render2D.roundOutline(ctx, x, y, w, h, 6, 1, ColorUtil.withAlpha(module.colorOf(block), 0x90));
            Fonts.drawV(ctx, name, x + 7, y + 9, Theme.TEXT, true, 0.68f);
            Fonts.drawV(ctx, hint, x + 7, y + 20, Theme.TEXT_MUTED, false, 0.58f);
        } finally {
            Fonts.endRaw();
        }
    }

    private void icon(DrawContext ctx, Block block, float x, float y) {
        Icon icon = icons.computeIfAbsent(block, BlockEspScreen::iconFor);
        if (!icon.item().isEmpty()) {
            ctx.drawItem(icon.item(), Math.round(x), Math.round(y));
            if (!icon.badge().isEmpty()) {
                // The pot or cake, half size, in the bottom corner.
                var matrices = ctx.getMatrices();
                matrices.pushMatrix();
                matrices.translate(Math.round(x) + 8.5f, Math.round(y) + 8.5f);
                matrices.scale(0.55f, 0.55f);
                ctx.drawItem(icon.badge(), 0, 0);
                matrices.popMatrix();
            }
            return;
        }
        // The very few with nothing to show: a tile in their colour with their initial.
        Render2D.roundRect(ctx, x, y, 16, 16, 4, ColorUtil.withAlpha(module.colorOf(block), 0xC0));
        String name = names.getOrDefault(block, "?");
        Fonts.drawCentered(ctx, name.isEmpty() ? "?" : name.substring(0, 1), x + 8, y + 8, 0xFF0B0D12, true, 0.7f);
    }

    /** Blocks with no item of their own, and the item that shows them best. */
    private static final Map<String, String> STAND_INS = Map.ofEntries(
            Map.entry("fire", "flint_and_steel"), Map.entry("soul_fire", "soul_soil"),
            Map.entry("water", "water_bucket"), Map.entry("lava", "lava_bucket"), Map.entry("bubble_column", "water_bucket"),
            Map.entry("powder_snow", "powder_snow_bucket"), Map.entry("nether_portal", "obsidian"),
            Map.entry("end_portal", "end_portal_frame"), Map.entry("end_gateway", "ender_pearl"),
            Map.entry("moving_piston", "piston"), Map.entry("piston_head", "piston"), Map.entry("tripwire", "string"),
            Map.entry("cocoa", "cocoa_beans"), Map.entry("sweet_berry_bush", "sweet_berries"), Map.entry("carrots", "carrot"),
            Map.entry("potatoes", "potato"), Map.entry("beetroots", "beetroot_seeds"), Map.entry("frosted_ice", "ice"),
            Map.entry("big_dripleaf_stem", "big_dripleaf"), Map.entry("bamboo_sapling", "bamboo"),
            Map.entry("pitcher_crop", "pitcher_pod"), Map.entry("torchflower_crop", "torchflower_seeds"),
            Map.entry("redstone_wire", "redstone"), Map.entry("cave_vines", "glow_berries"), Map.entry("cave_vines_plant", "glow_berries"),
            Map.entry("tall_seagrass", "seagrass"), Map.entry("melon_stem", "melon_seeds"), Map.entry("attached_melon_stem", "melon_seeds"),
            Map.entry("pumpkin_stem", "pumpkin_seeds"), Map.entry("attached_pumpkin_stem", "pumpkin_seeds"),
            Map.entry("wheat", "wheat_seeds"), Map.entry("candle_cake", "candle"));

    /**
     * The picture for a block: its own item if it has one, otherwise the item it comes from. Potted
     * plants are the plant with a pot in the corner, candle cakes the candle with a cake, wall signs,
     * heads, banners and coral fans their standing kind, plants' stems the plant, and the rest from
     * a short list (fire, portals, crops, liquids).
     */
    private static Icon iconFor(Block block) {
        ItemStack own = new ItemStack(block.asItem());
        if (!own.isEmpty()) return new Icon(own, ItemStack.EMPTY);
        String path = Registries.BLOCK.getId(block).getPath();
        ItemStack badge = ItemStack.EMPTY;
        String guess = STAND_INS.get(path);
        if (guess == null) {
            if (path.startsWith("potted_")) {
                guess = path.substring("potted_".length());
                badge = item("flower_pot");
            } else if (path.endsWith("_candle_cake")) {
                guess = path.substring(0, path.length() - "_cake".length());
                badge = item("cake");
            } else if (path.contains("_wall_")) {
                guess = path.replace("_wall_", "_");
            } else if (path.endsWith("_plant")) {
                guess = path.substring(0, path.length() - "_plant".length());
            } else if (path.endsWith("_crop")) {
                guess = path.substring(0, path.length() - "_crop".length());
            }
        } else if (path.equals("candle_cake")) {
            badge = item("cake");
        }
        ItemStack stand = guess == null ? ItemStack.EMPTY : item(guess);
        // A potted azalea is "potted_azalea_bush"; a potted cactus or bamboo is the plain one.
        if (stand.isEmpty() && guess != null && guess.endsWith("_bush")) stand = item(guess.substring(0, guess.length() - "_bush".length()));
        if (stand.isEmpty() && guess != null && guess.startsWith("flowering_azalea")) stand = item("flowering_azalea");
        return new Icon(stand, stand.isEmpty() ? ItemStack.EMPTY : badge);
    }

    private static ItemStack item(String path) {
        Identifier id = Identifier.ofVanilla(path);
        return Registries.ITEM.containsId(id) ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    private void scrollbar(DrawContext ctx, float x, float y, float h, float scroll, float content) {
        if (content <= h) return;
        float bar = Math.max(18, h * h / content);
        float at = y + (h - bar) * (scroll / (content - h));
        Render2D.roundRect(ctx, x, at, 3, bar, 1.5f, 0x40FFFFFF);
    }

    private void hit(float x, float y, float w, float h, Action action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    /** A hit inside the picked list, cut to the part of it that shows. */
    private void hitClipped(float x, float y, float w, float h, Action action) {
        float top = Math.max(y, listY), bottom = Math.min(y + h, listY + listH);
        if (bottom > top) hit(x, top, w, bottom - top, action);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                dragging = null;
                hit.action().run(click.x(), click.y());
                // A press on the colour picker starts a drag; move it there straight away.
                if (dragging != null) dragging.run(click.x(), click.y());
                return true;
            }
        }
        if (click.x() < px || click.x() > px + pw || click.y() < py || click.y() > py + ph) {
            close();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) {
            dragging.run(click.x(), click.y());
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) rightTarget -= (float) vertical * 30;
        else leftTarget -= (float) vertical * 36;
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.codepoint() > Character.MAX_VALUE) return true;
        char c = (char) input.codepoint();
        if ((Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == ':') && query.length() < 40) search(query + c);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            boolean word = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
            if (word || query.length() <= 1) search("");
            else search(query.substring(0, query.length() - 1));
            return true;
        }
        return true;
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
