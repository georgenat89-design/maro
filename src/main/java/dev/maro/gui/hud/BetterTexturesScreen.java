package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.BetterTextures;
import dev.maro.textures.Modrinth;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Better Textures: search box over a grid of Modrinth resource packs, each with its icon, who made
 * it, how many have it, what it is and whether it is made for this Minecraft. Enable downloads a
 * pack if it is not here yet and switches it on over the others; the box at the top right shows what
 * is on.
 */
public final class BetterTexturesScreen extends Screen {
    private static final int PAGE = 30;
    private static final float CARD_H = 70, GAP = 8;

    private final Screen parent;
    private final BetterTextures module;

    private String query = "";
    private boolean typing = true;
    /** When the typed words are searched for: a moment after the last key, not on every key. */
    private long searchAt = 0;
    private int searchToken;
    private final List<Modrinth.Pack> results = new ArrayList<>();
    private boolean loading, hasMore = true;
    private String error;
    private Modrinth.Pack selected;

    private float scroll, scrollTarget, maxScroll;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    private interface Action {
        void run(double mx, double my, int button);
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private float px, py, pw, ph;

    public BetterTexturesScreen(Screen parent, BetterTextures module) {
        super(Text.literal("Better Textures"));
        this.parent = parent;
        this.module = module;
    }

    // ---- searching ----------------------------------------------------------------------------

    /** Searches now for the words (none: the most popular packs); for tests as well as typing. */
    public void search(String words) {
        query = words;
        runSearch(false);
    }

    private void runSearch(boolean more) {
        searchAt = 0;
        int token = more ? searchToken : ++searchToken;
        if (!more) {
            results.clear();
            scrollTarget = scroll = 0;
            hasMore = true;
        }
        loading = true;
        error = null;
        Modrinth.search(query, more ? results.size() : 0, PAGE).whenComplete((packs, failure) -> {
            if (client == null) return;
            client.execute(() -> {
                if (token != searchToken) return;
                loading = false;
                if (failure != null) {
                    Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                    error = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                    return;
                }
                for (Modrinth.Pack pack : packs) {
                    if (results.stream().noneMatch(p -> p.id().equals(pack.id()))) results.add(pack);
                }
                hasMore = packs.size() == PAGE;
            });
        });
    }

    public List<Modrinth.Pack> results() {
        return results;
    }

    public boolean loading() {
        return loading;
    }

    public String error() {
        return error;
    }

    @Override
    protected void init() {
        if (results.isEmpty() && !loading) runSearch(false);
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
        if (searchAt != 0 && System.currentTimeMillis() >= searchAt) runSearch(false);

        pw = Math.min(780, width - 24);
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
            float searchY = py + 46;
            float activeW = Math.min(210, pw * 0.3f);
            searchBox(ctx, px + 16, searchY, pw - 32 - activeW - 8, 24, mx, my, now);
            activeBox(ctx, px + pw - 16 - activeW, searchY, activeW, 24, mx, my);

            // What is shown, and which is picked.
            float infoY = searchY + 24 + 9;
            String shown = !query.isBlank() ? (loading && results.isEmpty() ? "Searching for “" + query.trim() + "”…"
                    : results.size() + (hasMore ? "+" : "") + " packs for “" + query.trim() + "”")
                    : "Most downloaded resource packs";
            Fonts.drawV(ctx, shown, px + 17, infoY, Theme.TEXT_MUTED, false, 0.6f);
            String pick = "Selected: " + (selected == null ? "None" : selected.title());
            Fonts.drawRight(ctx, Fonts.trim(pick, pw * 0.45f, false, 0.6f), px + pw - 17, infoY, Theme.accent(), false, 0.6f);

            float gx = px + 16, gy = infoY + 8, gw = pw - 32, gh = py + ph - 40 - gy;
            grid(ctx, gx, gy, gw, gh, mx, my, now, dt);
            footer(ctx, py + ph - 30, mx, my);
        } finally {
            Fonts.endRaw();
            Render2D.setAlpha(1f);
        }
    }

    private void header(DrawContext ctx, int mx, int my) {
        Fonts.drawV(ctx, "Better Textures", px + 16, py + 19, Theme.TEXT, true, 1.05f);
        Fonts.drawV(ctx, "Any Modrinth resource pack, in one click", px + 16, py + 33, Theme.TEXT_MUTED, false, 0.6f);

        float bw = 62, bh = 20, by = py + 12;
        float cx = px + pw - 16 - bw;
        button(ctx, "Close", cx, by, bw, bh, true, mx, my, this::close);
        button(ctx, "Refresh", cx - 6 - bw, by, bw, bh, false, mx, my, () -> runSearch(false));
    }

    private void button(DrawContext ctx, String label, float x, float y, float w, float h, boolean accent, int mx, int my, Runnable action) {
        boolean hover = inside(mx, my, x, y, w, h);
        if (accent) Render2D.roundRect(ctx, x, y, w, h, 6, hover ? Theme.accent() : Theme.accent(0xD8));
        else {
            Render2D.roundRect(ctx, x, y, w, h, 6, hover ? 0xFF262B3A : 0xFF1A1E29);
            Render2D.roundOutline(ctx, x, y, w, h, 6, 1, hover ? 0x40FFFFFF : 0x1EFFFFFF);
        }
        Fonts.drawCentered(ctx, label, x + w / 2f, y + h / 2f, accent || hover ? 0xFFFFFFFF : Theme.TEXT_DIM, false, 0.66f);
        hit(x, y, w, h, (hx, hy, b) -> {
            Sounds.click();
            action.run();
        });
    }

    private void searchBox(DrawContext ctx, float x, float y, float w, float h, int mx, int my, long now) {
        boolean hover = inside(mx, my, x, y, w, h);
        Render2D.roundRect(ctx, x, y, w, h, 7, 0xFF0C0E14);
        Render2D.roundOutline(ctx, x, y, w, h, 7, 1, typing ? Theme.accent(0xC8) : hover ? 0x30FFFFFF : 0x18FFFFFF);
        // A magnifier.
        float ix = x + 12, iy = y + h / 2f - 1;
        Render2D.ring(ctx, ix, iy, 3.6f, 1.2f, typing ? Theme.accent() : Theme.TEXT_MUTED);
        Render2D.line(ctx, ix + 2.6f, iy + 2.6f, ix + 5.2f, iy + 5.2f, 1.3f, typing ? Theme.accent() : Theme.TEXT_MUTED);
        float tx = x + 22, room = w - 30;
        if (query.isEmpty() && !typing) {
            Fonts.drawV(ctx, "Search any Modrinth pack…", tx, y + h / 2f, 0xFF545A6C, false, 0.72f);
        } else {
            String shown = query;
            while (shown.length() > 1 && Fonts.width(shown, false, 0.72f) > room) shown = shown.substring(1);
            if (query.isEmpty()) Fonts.drawV(ctx, "Search any Modrinth pack…", tx, y + h / 2f, 0xFF3C4150, false, 0.72f);
            else Fonts.drawV(ctx, shown, tx, y + h / 2f, Theme.TEXT, false, 0.72f);
            if (typing && (now / 500_000_000L) % 2 == 0) {
                Render2D.rect(ctx, tx + 1 + Fonts.width(query.isEmpty() ? "" : shown, false, 0.72f), y + 6, 1, h - 12, Theme.TEXT_DIM);
            }
        }
        if (loading) spinner(ctx, x + w - 12, y + h / 2f, 4.5f, now);
        hit(x, y, w, h, (hx, hy, b) -> typing = true);
    }

    private void activeBox(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        List<BetterTextures.Installed> on = module.activePacks();
        Render2D.roundRect(ctx, x, y, w, h, 7, on.isEmpty() ? 0xFF161A23 : ColorUtil.lerp(0xFF161A23, Theme.accent(0xFF), 0.12f));
        Render2D.roundOutline(ctx, x, y, w, h, 7, 1, on.isEmpty() ? 0x1EFFFFFF : Theme.accent(0x80));
        if (on.isEmpty()) {
            Fonts.drawCentered(ctx, "No Active Pack", x + w / 2f, y + h / 2f, Theme.TEXT_MUTED, false, 0.68f);
            return;
        }
        BetterTextures.Installed top = on.getFirst();
        icon(ctx, top.projectId(), top.iconUrl(), top.title(), top.color(), x + 5, y + 4, 16);
        String label = on.size() == 1 ? top.title() : top.title() + "  +" + (on.size() - 1);
        Fonts.drawV(ctx, Fonts.trim(label, w - 32, false, 0.68f), x + 26, y + h / 2f, Theme.TEXT, false, 0.68f);
        if (inside(mx, my, x, y, w, h)) {
            StringBuilder all = new StringBuilder("On, top first: ");
            for (int i = 0; i < on.size(); i++) all.append(i == 0 ? "" : ", ").append(on.get(i).title());
            Fonts.drawRight(ctx, Fonts.trim(all.toString(), pw - 40, false, 0.56f), x + w, y + h + 9, Theme.TEXT_DIM, false, 0.56f);
        }
    }

    private void grid(DrawContext ctx, float x, float y, float w, float h, int mx, int my, long now, float dt) {
        Render2D.roundRect(ctx, x, y, w, h, 9, 0xFF0F1219);
        Render2D.roundOutline(ctx, x, y, w, h, 9, 1, 0x14FFFFFF);

        if (results.isEmpty()) {
            float cy = y + h / 2f;
            if (loading) {
                spinner(ctx, x + w / 2f, cy - 12, 7, now);
                Fonts.drawCentered(ctx, "Loading packs from Modrinth…", x + w / 2f, cy + 8, Theme.TEXT_DIM, false, 0.72f);
            } else if (error != null) {
                Fonts.drawCentered(ctx, "Couldn't reach Modrinth", x + w / 2f, cy - 6, Theme.RED, false, 0.8f);
                Fonts.drawCentered(ctx, Fonts.trim(error, w - 40, false, 0.6f), x + w / 2f, cy + 8, Theme.TEXT_MUTED, false, 0.6f);
            } else {
                Fonts.drawCentered(ctx, "No packs found", x + w / 2f, cy - 6, Theme.TEXT_DIM, false, 0.8f);
                Fonts.drawCentered(ctx, "Try other words, like “faithful”, “pvp” or “dark”.", x + w / 2f, cy + 8,
                        Theme.TEXT_MUTED, false, 0.6f);
            }
            return;
        }

        int cols = w >= 520 ? 2 : 1;
        float pad = 8, innerW = w - pad * 2 - 6;
        float cw = (innerW - (cols - 1) * GAP) / cols;
        int rows = (results.size() + cols - 1) / cols;
        float content = rows * (CARD_H + GAP) - GAP + (loading || hasMore ? 30 : 0);
        maxScroll = Math.max(0, content - (h - pad * 2));
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        // Near the bottom: the next page.
        if (hasMore && !loading && error == null && scroll >= maxScroll - CARD_H * 2) runSearch(true);

        float top = y + pad, bottom = y + h - pad;
        Render2D.clip(ctx, Math.round(x + 1), Math.round(top), Math.round(x + w - 1), Math.round(bottom));
        for (int i = 0; i < results.size(); i++) {
            float cx = x + pad + (i % cols) * (cw + GAP);
            float cy = top + (i / cols) * (CARD_H + GAP) - scroll;
            if (cy + CARD_H < top || cy > bottom) continue;
            card(ctx, results.get(i), cx, cy, cw, top, bottom, mx, my, now);
        }
        float endY = top + rows * (CARD_H + GAP) - scroll;
        if (loading && endY < bottom) spinner(ctx, x + w / 2f, endY + 10, 5, now);
        Render2D.unclip(ctx);
        if (maxScroll > 0) {
            float track = h - pad * 2, bar = Math.max(18, track * track / (content + 0.01f));
            Render2D.roundRect(ctx, x + w - 7, top + (track - bar) * (scroll / maxScroll), 3, bar, 1.5f, 0x40FFFFFF);
        }
    }

    private void card(DrawContext ctx, Modrinth.Pack pack, float x, float y, float w, float clipTop, float clipBottom, int mx, int my, long now) {
        boolean on = module.isActive(pack.id());
        BetterTextures.Job job = module.job(pack.id());
        boolean hover = inside(mx, my, x, y, w, CARD_H) && my >= clipTop && my < clipBottom;
        boolean picked = selected != null && selected.id().equals(pack.id());

        int bg = on ? ColorUtil.lerp(0xFF161A23, Theme.accent(0xFF), 0.10f) : hover ? 0xFF1B202B : 0xFF161A23;
        Render2D.roundRect(ctx, x, y, w, CARD_H, 8, bg);
        Render2D.roundOutline(ctx, x, y, w, CARD_H, 8, 1, picked ? Theme.accent(0xD0) : on ? Theme.accent(0x70) : hover ? 0x30FFFFFF : 0x16FFFFFF);
        if (on) Render2D.roundRect(ctx, x + 1, y + 12, 2.5f, CARD_H - 24, 1.25f, Theme.accent());

        icon(ctx, pack.id(), pack.iconUrl(), pack.title(), pack.color(), x + 10, y + 10, 42);

        float bw = 64, bx = x + w - 10 - bw, tx = x + 62, room = bx - 8 - tx;
        Fonts.drawV(ctx, Fonts.trim(pack.title(), room, true, 0.76f), tx, y + 15, Theme.TEXT, true, 0.76f);
        String meta = "by " + pack.author() + "  ·  " + Modrinth.count(pack.downloads()) + " downloads";
        Fonts.drawV(ctx, Fonts.trim(meta, room, false, 0.56f), tx, y + 27, Theme.TEXT_MUTED, false, 0.56f);
        Fonts.drawV(ctx, Fonts.trim(pack.description(), w - 72, false, 0.58f), tx, y + 40, Theme.TEXT_DIM, false, 0.58f);

        // Tags: its resolution and style, then whether it is made for this Minecraft.
        float chipX = tx, chipY = y + 51;
        for (String tag : pack.categories()) {
            if (chipX > x + w - 120) break;
            chipX += chip(ctx, pretty(tag), chipX, chipY, 0xFF222736, Theme.TEXT_DIM) + 4;
        }
        if (pack.supportsThisVersion()) chip(ctx, Modrinth.gameVersion(), chipX, chipY, 0x3030C77B, Theme.GREEN);
        else chip(ctx, "for " + pack.newestVersion(), chipX, chipY, 0x30F5A524, Theme.YELLOW);

        // Pick the card anywhere; the button switches it.
        float clipH = Math.min(y + CARD_H, clipBottom) - Math.max(y, clipTop);
        if (clipH > 0) hit(x, Math.max(y, clipTop), w, clipH, (hx, hy, b) -> selected = pack);

        float by = y + 10, bh = 20;
        boolean overButton = inside(mx, my, bx, by, bw, bh) && my >= clipTop && my < clipBottom;
        if (job != null && job.stage != BetterTextures.Stage.FAILED) {
            Render2D.roundRect(ctx, bx, by, bw, bh, 6, 0xFF1A1E29);
            if (job.stage == BetterTextures.Stage.DOWNLOADING) {
                float fill = (float) Math.max(0.04, job.progress);
                Render2D.roundRect(ctx, bx, by, bw * fill, bh, 6, Theme.accent(0x90));
                Fonts.drawCentered(ctx, Math.round(job.progress * 100) + "%", bx + bw / 2f, by + bh / 2f, 0xFFFFFFFF, false, 0.64f);
            } else {
                spinner(ctx, bx + 11, by + bh / 2f, 4, now);
                Fonts.drawV(ctx, "Finding", bx + 20, by + bh / 2f, Theme.TEXT_DIM, false, 0.62f);
            }
            Render2D.roundOutline(ctx, bx, by, bw, bh, 6, 1, Theme.accent(0x60));
        } else {
            String label;
            int fill, text;
            if (on) {
                label = overButton ? "Disable" : "Enabled";
                fill = overButton ? 0xFFE5484D : Theme.accent();
                text = 0xFFFFFFFF;
            } else if (job != null) {
                label = "Retry";
                fill = overButton ? 0x60E5484D : 0x30E5484D;
                text = 0xFFFF8A8E;
            } else {
                label = "Enable";
                fill = overButton ? Theme.accent(0xE0) : 0xFF232838;
                text = overButton ? 0xFFFFFFFF : Theme.TEXT;
            }
            Render2D.roundRect(ctx, bx, by, bw, bh, 6, fill);
            if (!on) Render2D.roundOutline(ctx, bx, by, bw, bh, 6, 1, job != null ? 0x80E5484D : Theme.accent(0x70));
            Fonts.drawCentered(ctx, label, bx + bw / 2f, by + bh / 2f, text, false, 0.66f);
            if (by >= clipTop && by + bh <= clipBottom) {
                hit(bx, by, bw, bh, (hx, hy, b) -> {
                    selected = pack;
                    toggle(pack);
                });
            }
        }
        if (job != null && job.stage == BetterTextures.Stage.FAILED) {
            Fonts.drawRight(ctx, Fonts.trim(job.message, w * 0.5f, false, 0.52f), x + w - 10, y + 39, 0xFFFF8A8E, false, 0.52f);
        } else if (module.isInstalled(pack.id()) && !on) {
            Fonts.drawRight(ctx, "Downloaded", x + w - 10, y + 39, Theme.TEXT_MUTED, false, 0.52f);
        }
    }

    /** Enables the pack (downloading it if need be), or disables it if it is on; for tests as well as clicks. */
    public void toggle(Modrinth.Pack pack) {
        Sounds.click();
        if (module.isActive(pack.id())) module.disable(pack.id());
        else module.enable(pack);
    }

    private float chip(DrawContext ctx, String label, float x, float y, int bg, int fg) {
        float w = Fonts.width(label, false, 0.52f) + 8;
        Render2D.roundRect(ctx, x, y - 5.5f, w, 11, 3.5f, bg);
        Fonts.drawV(ctx, label, x + 4, y, fg, false, 0.52f);
        return w;
    }

    private static String pretty(String tag) {
        if (tag.isEmpty() || Character.isDigit(tag.charAt(0))) return tag;
        return Character.toUpperCase(tag.charAt(0)) + tag.substring(1).replace('-', ' ');
    }

    /** The pack's icon, or a tile in its colour with its first letter while there is none. */
    private void icon(DrawContext ctx, String key, String url, String title, int color, float x, float y, int size) {
        Identifier texture = PackIcons.get(key, url);
        if (texture != null) {
            Render2D.roundRect(ctx, x - 1, y - 1, size + 2, size + 2, 6, 0xFF0C0E14);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), 0, 0, size, size, 64, 64, 64, 64);
            return;
        }
        int base = 0xFF000000 | (color & 0xFFFFFF);
        Render2D.roundRect(ctx, x, y, size, size, 6, ColorUtil.lerp(0xFF161A23, base, 0.55f));
        String letter = title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase(Locale.ROOT);
        float scale = size / 22f;
        Fonts.drawCentered(ctx, letter, x + size / 2f, y + size / 2f, 0xFFFFFFFF, true, scale);
    }

    private void spinner(DrawContext ctx, float cx, float cy, float r, long now) {
        float angle = (now / 1_000_000L % 1000) / 1000f * 360f;
        Render2D.arc(ctx, cx, cy, r, 1.4f, angle, 260, Theme.accent(), Theme.accent(0x20));
    }

    private void footer(DrawContext ctx, float y, int mx, int my) {
        float x = px + 16;
        boolean any = !module.activePacks().isEmpty();
        float bw = 76, bh = 20;
        boolean hover = inside(mx, my, x, y, bw, bh);
        Render2D.roundRect(ctx, x, y, bw, bh, 6, hover && any ? 0x40E5484D : 0xFF1A1E29);
        Render2D.roundOutline(ctx, x, y, bw, bh, 6, 1, any ? 0x60E5484D : 0x1EFFFFFF);
        Fonts.drawCentered(ctx, "Disable All", x + bw / 2f, y + bh / 2f, any ? 0xFFFF8A8E : Theme.TEXT_MUTED, false, 0.66f);
        hit(x, y, bw, bh, (hx, hy, b) -> {
            Sounds.click();
            module.disableAll();
        });
        float fx = x + bw + 6;
        button(ctx, "Open Folder", fx, y, 76, bh, false, mx, my, () -> Util.getOperatingSystem().open(module.packDir()));
        Fonts.drawV(ctx, Fonts.trim("Missing packs download when enabled. The newest one enabled is drawn over the rest.",
                pw - 32 - 170, false, 0.56f), fx + 84, y + bh / 2f, Theme.TEXT_MUTED, false, 0.56f);
    }

    private void hit(float x, float y, float w, float h, Action action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                typing = false;
                hit.action().run(click.x(), click.y(), click.button());
                return true;
            }
        }
        typing = false;
        if (click.x() < px || click.x() > px + pw || click.y() < py || click.y() > py + ph) close();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 40;
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!typing || input.codepoint() > Character.MAX_VALUE) return true;
        char c = (char) input.codepoint();
        if (c < 32 || c == 127 || c == '§') return true;
        if (query.length() < 64) {
            query += c;
            searchAt = System.currentTimeMillis() + 350;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (typing) {
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
                    searchAt = System.currentTimeMillis() + 350;
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    if (ctrl && client != null) {
                        String paste = client.keyboard.getClipboard().replace("\n", " ").replace("\r", "");
                        query = (query + paste).substring(0, Math.min(64, query.length() + paste.length()));
                        searchAt = System.currentTimeMillis() + 350;
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    runSearch(false);
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    if (!query.isEmpty()) {
                        query = "";
                        runSearch(false);
                    } else {
                        close();
                    }
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
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
