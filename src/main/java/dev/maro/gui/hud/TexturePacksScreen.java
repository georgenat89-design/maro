package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.BetterTextures;
import dev.maro.module.impl.visuals.TexturePacks;
import dev.maro.textures.PackIcons;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Texture Packs: your packs down the left, each with its icon, what it is and whether it is made
 * for this Minecraft; the ones that will be on down the right, top first, to put in order. Changes
 * wait for Apply (or closing the screen) so the game reloads once, not on every click.
 */
public final class TexturePacksScreen extends Screen {
    private static final float CARD_H = 54, GAP = 6, ROW_H = 26;
    private static final String[] FILTERS = {"All", "On", "Off"};

    private final Screen parent;
    private final TexturePacks module;

    private final List<TexturePacks.Pack> packs = new ArrayList<>();
    /** What will be on once applied, top first. */
    private final List<String> draft = new ArrayList<>();
    /** Whether the picks were changed here since they were last applied. */
    private boolean touched;

    private String query = "";
    private boolean typing;
    private int filter;
    private String flash;
    private long flashUntil;

    private float scroll, scrollTarget, maxScroll;
    private float sideScroll, sideTarget, sideMax;
    private float sideX, sideY, sideW, sideH;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    private interface Action {
        void run();
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private float px, py, pw, ph;

    public TexturePacksScreen(Screen parent, TexturePacks module) {
        super(Text.literal("Texture Packs"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        reload();
    }

    /** Looks for packs again and, unless changed here, starts the picks from the module's. */
    public void reload() {
        module.opened();
        packs.clear();
        packs.addAll(module.scan());
        if (!touched) {
            draft.clear();
            draft.addAll(module.picked());
        }
    }

    // ---- picking --------------------------------------------------------------------------------

    public List<TexturePacks.Pack> packs() {
        return packs;
    }

    /** What will be on once applied, top first. */
    public List<String> draft() {
        return draft;
    }

    /** Switches a pack on (over the others) or off; for tests as well as clicks. */
    public void toggle(String id) {
        if (!draft.remove(id)) draft.addFirst(id);
        touched = true;
    }

    public void moveUp(String id) {
        int i = draft.indexOf(id);
        if (i > 0) {
            draft.remove(i);
            draft.add(i - 1, id);
            touched = true;
        }
    }

    public void moveDown(String id) {
        int i = draft.indexOf(id);
        if (i >= 0 && i < draft.size() - 1) {
            draft.remove(i);
            draft.add(i + 1, id);
            touched = true;
        }
    }

    public void allOff() {
        draft.clear();
        touched = true;
    }

    /** Whether Apply would change anything. */
    public boolean pending() {
        return module.isEnabled() ? !draft.equals(module.picked()) : touched;
    }

    /** Puts the picks into the game (turning the module on if it is off). */
    public void apply() {
        module.setPicked(draft);
        touched = false;
        say("Applied");
    }

    private void say(String message) {
        flash = message;
        flashUntil = System.currentTimeMillis() + 2600;
    }

    private TexturePacks.Pack pack(String id) {
        for (TexturePacks.Pack pack : packs) if (pack.id().equals(id)) return pack;
        return null;
    }

    private List<TexturePacks.Pack> onList() {
        List<TexturePacks.Pack> out = new ArrayList<>();
        for (String id : draft) {
            TexturePacks.Pack pack = pack(id);
            if (pack != null) out.add(pack);
        }
        return out;
    }

    private List<TexturePacks.Pack> shown() {
        String words = query.trim().toLowerCase(Locale.ROOT);
        List<TexturePacks.Pack> out = new ArrayList<>();
        for (TexturePacks.Pack pack : packs) {
            boolean on = draft.contains(pack.id());
            if (filter == 1 && !on || filter == 2 && on) continue;
            if (!words.isEmpty() && !pack.name().toLowerCase(Locale.ROOT).contains(words)
                    && !pack.description().toLowerCase(Locale.ROOT).contains(words)) continue;
            out.add(pack);
        }
        return out;
    }

    /** The pack's icon once read, for tests. */
    public boolean iconReady(String id) {
        TexturePacks.Pack pack = pack(id);
        return pack != null && icon(pack) != null;
    }

    private static Identifier icon(TexturePacks.Pack pack) {
        return PackIcons.local("local/" + pack.id(), () -> TexturePacks.readIcon(pack.profile()));
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
        Theme.update();
        module.pump();

        pw = Math.min(800, width - 24);
        ph = Math.min(520, height - 20);
        px = Math.round((width - pw) / 2f);
        py = Math.round((height - ph) / 2f);

        float open = Easing.outCubic(Math.min(1f, (now - openedAt) / 1e9f / 0.18f));
        Render2D.setAlpha(open);
        Fonts.beginRaw();
        try {
            Render2D.rect(ctx, 0, 0, width, height, 0xA0000000);
            Render2D.shadow(ctx, px, py + 4, pw, ph, 14, 20, 0x90000000);
            Render2D.roundRect(ctx, px, py, pw, ph, 12, 0xF5141720);
            Render2D.roundOutline(ctx, px, py, pw, ph, 12, 1, 0x1CFFFFFF);
            Render2D.roundRect(ctx, px, py, pw, 3, 1.5f, Theme.accent(0xC0));

            header(ctx, mx, my);
            sideW = Math.min(230, pw * 0.32f);
            float leftW = pw - 32 - sideW - 10;
            float rowY = py + 46;
            float filterW = Math.min(180, leftW * 0.4f);
            searchBox(ctx, px + 16, rowY, leftW - filterW - 8, 24, mx, my, now);
            filters(ctx, px + 16 + leftW - filterW, rowY, filterW, 24, mx, my);

            float infoY = rowY + 24 + 9;
            int onCount = onList().size();
            if (!module.isEnabled()) {
                Fonts.drawV(ctx, Fonts.trim("Texture Packs is off, so the game is on your usual packs. Apply puts these on.", leftW, false, 0.6f),
                        px + 17, infoY, Theme.YELLOW, false, 0.6f);
            } else {
                Fonts.drawV(ctx, packs.size() + (packs.size() == 1 ? " pack" : " packs") + "  ·  " + onCount + " on", px + 17, infoY,
                        Theme.TEXT_MUTED, false, 0.6f);
            }

            float gy = infoY + 8, gh = py + ph - 40 - gy;
            grid(ctx, px + 16, gy, leftW, gh, mx, my, dt);
            side(ctx, px + pw - 16 - sideW, rowY, sideW, py + ph - 40 - rowY, mx, my, dt);
            footer(ctx, py + ph - 30, mx, my);
        } finally {
            Fonts.endRaw();
            Render2D.setAlpha(1f);
        }
    }

    private void header(DrawContext ctx, int mx, int my) {
        Fonts.drawV(ctx, "Texture Packs", px + 16, py + 19, Theme.TEXT, true, 1.05f);
        Fonts.drawV(ctx, "Pick from the packs you have, and which one goes on top", px + 16, py + 33, Theme.TEXT_MUTED, false, 0.6f);

        float bw = 62, bh = 20, by = py + 12;
        float cx = px + pw - 16 - bw;
        button(ctx, "Close", cx, by, bw, bh, true, mx, my, this::close);
        button(ctx, "Refresh", cx - 6 - bw, by, bw, bh, false, mx, my, () -> {
            for (TexturePacks.Pack pack : packs) PackIcons.forget("local/" + pack.id());
            reload();
            say("Looked for packs again");
        });
        button(ctx, "Get More", cx - 2 * (6 + bw) - 8, by, bw + 8, bh, false, mx, my, () -> {
            if (client == null) return;
            if (pending()) apply();
            if (parent instanceof BetterTexturesScreen) client.setScreen(parent);
            else client.setScreen(new BetterTexturesScreen(this, BetterTextures.get()));
        });
    }

    private void button(DrawContext ctx, String label, float x, float y, float w, float h, boolean accent, int mx, int my, Runnable action) {
        boolean hover = inside(mx, my, x, y, w, h);
        if (accent) Render2D.roundRect(ctx, x, y, w, h, 6, hover ? Theme.accent() : Theme.accent(0xD8));
        else {
            Render2D.roundRect(ctx, x, y, w, h, 6, hover ? 0xFF262B3A : 0xFF1A1E29);
            Render2D.roundOutline(ctx, x, y, w, h, 6, 1, hover ? 0x40FFFFFF : 0x1EFFFFFF);
        }
        Fonts.drawCentered(ctx, label, x + w / 2f, y + h / 2f, accent || hover ? 0xFFFFFFFF : Theme.TEXT_DIM, false, 0.66f);
        hit(x, y, w, h, () -> {
            Sounds.click();
            action.run();
        });
    }

    private void searchBox(DrawContext ctx, float x, float y, float w, float h, int mx, int my, long now) {
        boolean hover = inside(mx, my, x, y, w, h);
        Render2D.roundRect(ctx, x, y, w, h, 7, 0xFF0C0E14);
        Render2D.roundOutline(ctx, x, y, w, h, 7, 1, typing ? Theme.accent(0xC8) : hover ? 0x30FFFFFF : 0x18FFFFFF);
        float ix = x + 12, iy = y + h / 2f - 1;
        Render2D.ring(ctx, ix, iy, 3.6f, 1.2f, typing ? Theme.accent() : Theme.TEXT_MUTED);
        Render2D.line(ctx, ix + 2.6f, iy + 2.6f, ix + 5.2f, iy + 5.2f, 1.3f, typing ? Theme.accent() : Theme.TEXT_MUTED);
        float tx = x + 22, room = w - 30;
        if (query.isEmpty()) {
            Fonts.drawV(ctx, "Search your packs…", tx, y + h / 2f, typing ? 0xFF3C4150 : 0xFF545A6C, false, 0.72f);
        } else {
            String shown = query;
            while (shown.length() > 1 && Fonts.width(shown, false, 0.72f) > room) shown = shown.substring(1);
            Fonts.drawV(ctx, shown, tx, y + h / 2f, Theme.TEXT, false, 0.72f);
        }
        if (typing && (now / 500_000_000L) % 2 == 0) {
            String shown = query;
            while (shown.length() > 1 && Fonts.width(shown, false, 0.72f) > room) shown = shown.substring(1);
            Render2D.rect(ctx, tx + 1 + Fonts.width(shown, false, 0.72f), y + 6, 1, h - 12, Theme.TEXT_DIM);
        }
        hit(x, y, w, h, () -> typing = true);
    }

    private void filters(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        Render2D.roundRect(ctx, x, y, w, h, 7, 0xFF0C0E14);
        Render2D.roundOutline(ctx, x, y, w, h, 7, 1, 0x18FFFFFF);
        int on = onList().size();
        int[] counts = {packs.size(), on, packs.size() - on};
        float sw = (w - 4) / FILTERS.length;
        for (int i = 0; i < FILTERS.length; i++) {
            float sx = x + 2 + i * sw;
            boolean picked = filter == i, hover = inside(mx, my, sx, y + 2, sw, h - 4);
            if (picked) Render2D.roundRect(ctx, sx, y + 2, sw, h - 4, 5.5f, Theme.accent(0xD0));
            else if (hover) Render2D.roundRect(ctx, sx, y + 2, sw, h - 4, 5.5f, 0xFF1F2430);
            Fonts.drawCentered(ctx, FILTERS[i] + " " + counts[i], sx + sw / 2f, y + h / 2f, picked ? 0xFFFFFFFF : hover ? Theme.TEXT : Theme.TEXT_DIM,
                    false, 0.64f);
            int index = i;
            hit(sx, y + 2, sw, h - 4, () -> {
                Sounds.click();
                filter = index;
                scrollTarget = scroll = 0;
            });
        }
    }

    private void grid(DrawContext ctx, float x, float y, float w, float h, int mx, int my, float dt) {
        Render2D.roundRect(ctx, x, y, w, h, 9, 0xFF0F1219);
        Render2D.roundOutline(ctx, x, y, w, h, 9, 1, 0x14FFFFFF);
        List<TexturePacks.Pack> shown = shown();
        if (shown.isEmpty()) {
            float cy = y + h / 2f;
            if (packs.isEmpty()) {
                Fonts.drawCentered(ctx, "No texture packs yet", x + w / 2f, cy - 6, Theme.TEXT_DIM, false, 0.8f);
                Fonts.drawCentered(ctx, "Drop .zip packs on this screen, or press Get More to find some.", x + w / 2f, cy + 8,
                        Theme.TEXT_MUTED, false, 0.6f);
            } else {
                Fonts.drawCentered(ctx, "No packs match", x + w / 2f, cy - 6, Theme.TEXT_DIM, false, 0.8f);
                Fonts.drawCentered(ctx, query.isBlank() ? "Try the All tab." : "Try other words, or the All tab.", x + w / 2f, cy + 8,
                        Theme.TEXT_MUTED, false, 0.6f);
            }
            return;
        }

        float pad = 7, cw = w - pad * 2 - 6;
        float content = shown.size() * (CARD_H + GAP) - GAP;
        maxScroll = Math.max(0, content - (h - pad * 2));
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        float top = y + pad, bottom = y + h - pad;
        Render2D.clip(ctx, Math.round(x + 1), Math.round(top), Math.round(x + w - 1), Math.round(bottom));
        for (int i = 0; i < shown.size(); i++) {
            float cy = top + i * (CARD_H + GAP) - scroll;
            if (cy + CARD_H < top || cy > bottom) continue;
            card(ctx, shown.get(i), x + pad, cy, cw, top, bottom, mx, my);
        }
        Render2D.unclip(ctx);
        if (maxScroll > 0) {
            float track = h - pad * 2, bar = Math.max(18, track * track / (content + 0.01f));
            Render2D.roundRect(ctx, x + w - 7, top + (track - bar) * (scroll / maxScroll), 3, bar, 1.5f, 0x40FFFFFF);
        }
    }

    private void card(DrawContext ctx, TexturePacks.Pack pack, float x, float y, float w, float clipTop, float clipBottom, int mx, int my) {
        int place = draft.indexOf(pack.id());
        boolean on = place >= 0;
        boolean hover = inside(mx, my, x, y, w, CARD_H) && my >= clipTop && my < clipBottom;

        int bg = on ? ColorUtil.lerp(0xFF161A23, Theme.accent(0xFF), 0.10f) : hover ? 0xFF1B202B : 0xFF161A23;
        Render2D.roundRect(ctx, x, y, w, CARD_H, 8, bg);
        Render2D.roundOutline(ctx, x, y, w, CARD_H, 8, 1, on ? Theme.accent(0x80) : hover ? 0x30FFFFFF : 0x16FFFFFF);
        if (on) Render2D.roundRect(ctx, x + 1, y + 11, 2.5f, CARD_H - 22, 1.25f, Theme.accent());

        drawIcon(ctx, pack, x + 10, y + 9, 36);

        float bw = 64, bh = 20, bx = x + w - 10 - bw, by = y + (CARD_H - bh) / 2f;
        float tx = x + 56, room = bx - 10 - tx;
        Fonts.drawV(ctx, Fonts.trim(pack.name(), room, true, 0.74f), tx, y + 14, Theme.TEXT, true, 0.74f);
        String about = pack.description().isBlank() ? "No description" : pack.description();
        Fonts.drawV(ctx, Fonts.trim(about, room, false, 0.56f), tx, y + 27, Theme.TEXT_DIM, false, 0.56f);

        float chipX = tx, chipY = y + 41;
        if (on) chipX += chip(ctx, place == 0 ? "On top" : "On  #" + (place + 1), chipX, chipY, Theme.accent(0x40), 0xFFFFFFFF) + 4;
        chipX += chip(ctx, pack.kind(), chipX, chipY, 0xFF222736, Theme.TEXT_DIM) + 4;
        if (pack.fits()) chip(ctx, pack.fitNote(), chipX, chipY, 0x3030C77B, Theme.GREEN);
        else chip(ctx, pack.fitNote(), chipX, chipY, 0x30F5A524, Theme.YELLOW);

        boolean overButton = inside(mx, my, bx, by, bw, bh) && my >= clipTop && my < clipBottom;
        String label;
        int fill, text;
        if (on) {
            label = overButton ? "Disable" : "Enabled";
            fill = overButton ? 0xFFE5484D : Theme.accent();
            text = 0xFFFFFFFF;
        } else {
            label = "Enable";
            fill = overButton || hover ? Theme.accent(0xE0) : 0xFF232838;
            text = overButton || hover ? 0xFFFFFFFF : Theme.TEXT;
        }
        Render2D.roundRect(ctx, bx, by, bw, bh, 6, fill);
        if (!on) Render2D.roundOutline(ctx, bx, by, bw, bh, 6, 1, Theme.accent(0x70));
        Fonts.drawCentered(ctx, label, bx + bw / 2f, by + bh / 2f, text, false, 0.66f);

        // The whole card switches it.
        float clipY = Math.max(y, clipTop), clipH = Math.min(y + CARD_H, clipBottom) - clipY;
        if (clipH > 0) {
            hit(x, clipY, w, clipH, () -> {
                Sounds.click();
                toggle(pack.id());
            });
        }
    }

    private void side(DrawContext ctx, float x, float y, float w, float h, int mx, int my, float dt) {
        sideX = x;
        sideY = y;
        sideW = w;
        sideH = h;
        Render2D.roundRect(ctx, x, y, w, h, 9, 0xFF0F1219);
        Render2D.roundOutline(ctx, x, y, w, h, 9, 1, 0x14FFFFFF);
        List<TexturePacks.Pack> on = onList();
        Fonts.drawV(ctx, "On, top first", x + 10, y + 13, Theme.TEXT, true, 0.7f);
        Fonts.drawRight(ctx, String.valueOf(on.size()), x + w - 10, y + 13, Theme.accent(), true, 0.7f);
        Render2D.rect(ctx, x + 8, y + 25, w - 16, 1, 0x14FFFFFF);

        float top = y + 31, bottom = y + h - 30;
        if (on.isEmpty()) {
            Fonts.drawCentered(ctx, "Nothing on", x + w / 2f, top + 24, Theme.TEXT_DIM, false, 0.68f);
            Fonts.drawCentered(ctx, "Enable packs on the left", x + w / 2f, top + 37, Theme.TEXT_MUTED, false, 0.56f);
        } else {
            float content = on.size() * (ROW_H + 4) - 4;
            sideMax = Math.max(0, content - (bottom - top));
            sideTarget = Math.max(0, Math.min(sideMax, sideTarget));
            sideScroll += (sideTarget - sideScroll) * Math.min(1f, dt * 16f);
            sideScroll = Math.max(0, Math.min(sideMax, sideScroll));
            Render2D.clip(ctx, Math.round(x + 1), Math.round(top), Math.round(x + w - 1), Math.round(bottom));
            for (int i = 0; i < on.size(); i++) {
                float ry = top + i * (ROW_H + 4) - sideScroll;
                if (ry + ROW_H < top || ry > bottom) continue;
                row(ctx, on.get(i), i, on.size(), x + 7, ry, w - 14, top, bottom, mx, my);
            }
            Render2D.unclip(ctx);
        }
        Fonts.drawCentered(ctx, Fonts.trim("Point at a pack to move it", w - 16, false, 0.52f), x + w / 2f, y + h - 19, Theme.TEXT_MUTED, false, 0.52f);
        Fonts.drawCentered(ctx, Fonts.trim("The top one wins where two clash", w - 16, false, 0.52f), x + w / 2f, y + h - 9, Theme.TEXT_MUTED, false, 0.52f);
    }

    private void row(DrawContext ctx, TexturePacks.Pack pack, int index, int count, float x, float y, float w, float clipTop, float clipBottom,
                     int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, ROW_H) && my >= clipTop && my < clipBottom;
        Render2D.roundRect(ctx, x, y, w, ROW_H, 6, hover ? 0xFF1B202B : 0xFF161A23);
        Render2D.roundOutline(ctx, x, y, w, ROW_H, 6, 1, index == 0 ? Theme.accent(0x90) : 0x16FFFFFF);
        Fonts.drawCentered(ctx, String.valueOf(index + 1), x + 9, y + ROW_H / 2f, index == 0 ? Theme.accent() : Theme.TEXT_MUTED, true, 0.6f);
        drawIcon(ctx, pack, x + 18, y + 5, 16);

        // The name gets the whole row; the up, down and off buttons show over its end while pointed at.
        float bs = 16, gap = 2, bx = x + w - 5 - 3 * bs - 2 * gap, by = y + (ROW_H - bs) / 2f;
        float nameEnd = hover ? bx - 6 : x + w - 8;
        Fonts.drawV(ctx, Fonts.trim(pack.name(), nameEnd - (x + 39), false, 0.62f), x + 39, y + ROW_H / 2f, Theme.TEXT, false, 0.62f);
        if (!hover || by < clipTop || by + bs > clipBottom) return;
        small(ctx, bx, by, bs, index > 0, mx, my, 0, () -> moveUp(pack.id()));
        small(ctx, bx + bs + gap, by, bs, index < count - 1, mx, my, 1, () -> moveDown(pack.id()));
        small(ctx, bx + 2 * (bs + gap), by, bs, true, mx, my, 2, () -> toggle(pack.id()));
    }

    /** A little square button: up, down, or off (a cross). */
    private void small(DrawContext ctx, float x, float y, float s, boolean usable, int mx, int my, int kind, Runnable action) {
        boolean hover = usable && inside(mx, my, x, y, s, s);
        int bg = !hover ? 0xFF1F2430 : kind == 2 ? 0x60E5484D : Theme.accent(0x90);
        Render2D.roundRect(ctx, x, y, s, s, 4, bg);
        int color = !usable ? 0xFF3C4150 : hover ? 0xFFFFFFFF : kind == 2 ? 0xFFFF8A8E : Theme.TEXT_DIM;
        float cx = x + s / 2f, cy = y + s / 2f;
        if (kind == 2) {
            Render2D.line(ctx, cx - 2.6f, cy - 2.6f, cx + 2.6f, cy + 2.6f, 1.3f, color);
            Render2D.line(ctx, cx - 2.6f, cy + 2.6f, cx + 2.6f, cy - 2.6f, 1.3f, color);
        } else {
            float d = kind == 0 ? -1.6f : 1.6f;
            Render2D.line(ctx, cx - 3.2f, cy - d, cx, cy + d, 1.3f, color);
            Render2D.line(ctx, cx, cy + d, cx + 3.2f, cy - d, 1.3f, color);
        }
        if (usable) {
            hit(x, y, s, s, () -> {
                Sounds.click();
                action.run();
            });
        }
    }

    private void footer(DrawContext ctx, float y, int mx, int my) {
        float x = px + 16, bh = 20;
        boolean any = !onList().isEmpty();
        float bw = 60;
        boolean hover = inside(mx, my, x, y, bw, bh);
        Render2D.roundRect(ctx, x, y, bw, bh, 6, hover && any ? 0x40E5484D : 0xFF1A1E29);
        Render2D.roundOutline(ctx, x, y, bw, bh, 6, 1, any ? 0x60E5484D : 0x1EFFFFFF);
        Fonts.drawCentered(ctx, "All Off", x + bw / 2f, y + bh / 2f, any ? 0xFFFF8A8E : Theme.TEXT_MUTED, false, 0.66f);
        if (any) {
            hit(x, y, bw, bh, () -> {
                Sounds.click();
                allOff();
            });
        }
        float fx = x + bw + 6;
        button(ctx, "Open Folder", fx, y, 76, bh, false, mx, my, () -> Util.getOperatingSystem().open(module.packDir()));

        float aw = 76, ax = px + pw - 16 - aw;
        boolean pending = pending();
        boolean overApply = inside(mx, my, ax, y, aw, bh);
        Render2D.roundRect(ctx, ax, y, aw, bh, 6, pending ? (overApply ? Theme.accent() : Theme.accent(0xD8)) : 0xFF1A1E29);
        if (!pending) Render2D.roundOutline(ctx, ax, y, aw, bh, 6, 1, 0x1EFFFFFF);
        Fonts.drawCentered(ctx, "Apply", ax + aw / 2f, y + bh / 2f, pending ? 0xFFFFFFFF : Theme.TEXT_MUTED, false, 0.66f);
        if (pending) {
            hit(ax, y, aw, bh, () -> {
                Sounds.click();
                apply();
            });
        }

        String note;
        int noteColor;
        if (flash != null && System.currentTimeMillis() < flashUntil) {
            note = flash;
            noteColor = Theme.GREEN;
        } else if (pending) {
            note = "Not applied yet: Apply, or close, to switch";
            noteColor = Theme.YELLOW;
        } else {
            note = "Drop .zip packs here to add them";
            noteColor = Theme.TEXT_MUTED;
        }
        float left = fx + 84, right = ax - 10;
        Fonts.drawRight(ctx, Fonts.trim(note, right - left, false, 0.58f), right, y + bh / 2f, noteColor, false, 0.58f);
    }

    private float chip(DrawContext ctx, String label, float x, float y, int bg, int fg) {
        float w = Fonts.width(label, false, 0.52f) + 8;
        Render2D.roundRect(ctx, x, y - 5.5f, w, 11, 3.5f, bg);
        Fonts.drawV(ctx, label, x + 4, y, fg, false, 0.52f);
        return w;
    }

    /** The pack's own icon, or a tile with its first letter when it has none (or while it loads). */
    private void drawIcon(DrawContext ctx, TexturePacks.Pack pack, float x, float y, int size) {
        Identifier texture = icon(pack);
        if (texture != null) {
            Render2D.roundRect(ctx, x - 1, y - 1, size + 2, size + 2, size > 20 ? 6 : 3, 0xFF0C0E14);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), 0, 0, size, size, 64, 64, 64, 64);
            return;
        }
        int hash = pack.id().hashCode();
        int base = 0xFF000000 | Theme.PRESETS[Math.floorMod(hash, Theme.PRESETS.length)] & 0xFFFFFF;
        Render2D.roundRect(ctx, x, y, size, size, size > 20 ? 6 : 3, ColorUtil.lerp(0xFF161A23, base, 0.55f));
        String letter = pack.name().isEmpty() ? "?" : pack.name().substring(0, 1).toUpperCase(Locale.ROOT);
        Fonts.drawCentered(ctx, letter, x + size / 2f, y + size / 2f, 0xFFFFFFFF, true, size / 22f);
    }

    private void hit(float x, float y, float w, float h, Action action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---- adding packs ---------------------------------------------------------------------------

    /** Packs dropped on the window (zips, or folders with a pack.mcmeta) are copied into the resourcepacks folder. */
    @Override
    public void onFilesDropped(List<Path> paths) {
        Path dir = module.packDir();
        int added = 0;
        for (Path path : paths) {
            try {
                String name = path.getFileName().toString();
                boolean folder = Files.isDirectory(path);
                if (folder ? !Files.isRegularFile(path.resolve("pack.mcmeta")) : !name.toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
                Path target = dir.resolve(name);
                if (Files.exists(target)) continue;
                if (folder) copyFolder(path, target);
                else Files.copy(path, target);
                added++;
            } catch (IOException | RuntimeException e) {
                say("Could not add " + path.getFileName() + ": " + e.getMessage());
            }
        }
        if (added > 0) {
            reload();
            say("Added " + added + (added == 1 ? " pack" : " packs") + ": enable it on the left");
        }
    }

    private static void copyFolder(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path target = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) Files.createDirectories(target);
                else Files.copy(file, target);
            }
        }
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                typing = false;
                hit.action().run();
                return true;
            }
        }
        typing = false;
        if (click.x() < px || click.x() > px + pw || click.y() < py || click.y() > py + ph) close();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, sideX, sideY, sideW, sideH)) sideTarget -= (float) vertical * 30;
        else scrollTarget -= (float) vertical * 40;
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.codepoint() > Character.MAX_VALUE) return true;
        char c = (char) input.codepoint();
        if (c < 32 || c == 127 || c == '§') return true;
        typing = true;
        if (query.length() < 48) {
            query += c;
            scrollTarget = scroll = 0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (query.isEmpty()) return true;
                if (ctrl) {
                    String trimmed = query.stripTrailing();
                    int cut = trimmed.lastIndexOf(' ');
                    query = cut < 0 ? "" : trimmed.substring(0, cut + 1);
                } else {
                    query = query.substring(0, query.length() - 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (pending()) apply();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (typing && !query.isEmpty()) {
                    query = "";
                    typing = false;
                } else {
                    close();
                }
                return true;
            }
            default -> {
                return super.keyPressed(input);
            }
        }
    }

    /** Leaving applies what was changed, as the game's own pack screen does. */
    @Override
    public void close() {
        if (pending()) apply();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
