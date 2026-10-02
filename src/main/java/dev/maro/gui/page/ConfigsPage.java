package dev.maro.gui.page;

import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.TextField;
import dev.maro.gui.widget.Widgets;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Create, load, overwrite and delete module configs. */
public class ConfigsPage extends Page {
    private final TextField name;
    private final Map<String, Long> deleteConfirm = new HashMap<>();
    private List<ConfigManager.ConfigInfo> cache = List.of();
    private long cachedAt;

    public ConfigsPage(ClickGuiScreen gui) {
        super(gui);
        name = new TextField("New config name...", 32)
                .filter(c -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ')
                .onEnter(this::create);
    }

    private void refresh() {
        cache = ConfigManager.list();
        cachedAt = System.currentTimeMillis();
    }

    private void create() {
        String n = ConfigManager.sanitize(name.getText());
        if (n.isEmpty()) {
            Notifications.push("Configs", "Enter a name first", Notifications.Type.WARNING);
            return;
        }
        boolean existed = ConfigManager.exists(n);
        if (ConfigManager.save(n)) {
            Notifications.push("Config " + (existed ? "overwritten" : "created"), n, Notifications.Type.SUCCESS);
            name.clear();
            gui.focused = null;
        } else {
            Notifications.push("Configs", "Could not save " + n, Notifications.Type.ERROR);
        }
        refresh();
    }

    @Override
    public void onOpen() {
        super.onOpen();
        refresh();
    }

    @Override
    public void render(DrawContext ctx, float x, float y, float w, float h) {
        if (System.currentTimeMillis() - cachedAt > 1500) refresh();
        float lw = w - 6;

        // toolbar
        float bh = 18;
        float folderW = 18;
        float createW = Widgets.buttonWidth("Create", Icons.PLUS);
        float fieldW = lw - createW - folderW - 10;
        name.render(gui, ctx, x, y + 1, fieldW, bh, Icons.CONFIGS, null);
        Widgets.button(gui, ctx, "create", x + fieldW + 5, y + 1, createW, bh, "Create", Widgets.Style.PRIMARY, Icons.PLUS, this::create);
        Widgets.button(gui, ctx, "folder", x + lw - folderW, y + 1, folderW, bh, "", Widgets.Style.SECONDARY, Icons.FOLDER, () -> {
            try {
                Util.getOperatingSystem().open(ConfigManager.CONFIG_DIR.toFile());
            } catch (Throwable t) {
                Notifications.push("Configs", "Could not open folder", Notifications.Type.ERROR);
            }
        });
        if (gui.hovered(x + lw - folderW, y + 1, folderW, bh)) gui.tooltip("Open configs folder");

        float top = y + 26;
        Widgets.sectionLabel(ctx, "Configs • " + cache.size(), x + 2, top, lw - 4);
        top += 12;
        float viewH = h - (top - y);

        if (cache.isEmpty()) {
            Fonts.drawCentered(ctx, "No configs yet", x + lw / 2f, top + viewH / 2f - 6, Theme.TEXT, true, 0.9f);
            Fonts.drawCentered(ctx, "Type a name above and press Create", x + lw / 2f, top + viewH / 2f + 6, Theme.TEXT_MUTED, false, 0.75f);
            return;
        }

        float rowH = 30, gap = 4;
        scroll.setBounds(cache.size() * (rowH + gap) - gap + 2, viewH);
        float off = scroll.update();
        gui.pushClip(x - 6, top, w + 12, viewH);
        long now = System.currentTimeMillis();
        for (int i = 0; i < cache.size(); i++) {
            ConfigManager.ConfigInfo info = cache.get(i);
            float ry = top + i * (rowH + gap) - off;
            if (!gui.isVisible(ry, rowH)) continue;
            float in = intro(i);
            float prev = Render2D.getAlpha();
            Render2D.setAlpha(prev * in);
            ry += (1 - in) * 8;
            row(ctx, info, x, ry, lw, rowH, now);
            Render2D.setAlpha(prev);
        }
        gui.popClip();
        scroll.drawBar(gui, ctx, x + w - 2, top, viewH);
    }

    private void row(DrawContext ctx, ConfigManager.ConfigInfo info, float x, float y, float w, float h, long now) {
        boolean active = info.name().equalsIgnoreCase(ConfigManager.getCurrent());
        boolean hov = gui.hovered(x, y, w, h);
        float hv = Anims.of(info.name(), "cfgRow", hov);
        float av = Anims.of(info.name(), "cfgActive", active);
        float r = Theme.radius();
        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        if (av > 0.01f) Render2D.roundGradientH(ctx, x, y, w, h, r, Theme.accent(Math.round(0x22 * av)), Theme.accent2(0));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.lerp(Theme.BORDER, Theme.accent(0x60), av));

        Icons.CONFIGS.draw(ctx, x + 14, y + h / 2f, 10, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accent(), av), hv);
        float nameX = x + 27;
        Fonts.draw(ctx, info.name(), nameX, y + 7, Theme.TEXT, true, 0.88f);
        if (active) {
            float bx = nameX + Fonts.width(info.name(), true, 0.88f) + 5;
            float bw = Fonts.width("ACTIVE", true, 0.55f) + 7;
            Render2D.roundRect(ctx, bx, y + 6.5f, bw, 8, 3, Theme.accent(0x35));
            Fonts.drawCentered(ctx, "ACTIVE", bx + bw / 2f, y + 10.5f, ColorUtil.shade(Theme.accent(), 0.35f), true, 0.55f);
        }
        Fonts.draw(ctx, "Edited " + ago(now - info.lastModified()), nameX, y + 18, Theme.TEXT_MUTED, false, 0.68f);

        float bh = 16, by = y + (h - bh) / 2f;
        boolean confirm = now - deleteConfirm.getOrDefault(info.name(), 0L) < 3000;
        String delLabel = confirm ? "Confirm" : "Delete";
        float dw = Widgets.buttonWidth(delLabel, null), sw = Widgets.buttonWidth("Save", null), lw = Widgets.buttonWidth("Load", null);
        float bx = x + w - 7 - dw;
        Widgets.button(gui, ctx, info.name() + "#del", bx, by, dw, bh, delLabel, Widgets.Style.DANGER, null, () -> {
            if (System.currentTimeMillis() - deleteConfirm.getOrDefault(info.name(), 0L) < 3000) {
                if (ConfigManager.delete(info.name())) Notifications.push("Config deleted", info.name(), Notifications.Type.INFO);
                deleteConfirm.remove(info.name());
                refresh();
            } else {
                deleteConfirm.put(info.name(), System.currentTimeMillis());
            }
        });
        bx -= sw + 4;
        Widgets.button(gui, ctx, info.name() + "#save", bx, by, sw, bh, "Save", Widgets.Style.SECONDARY, null, () -> {
            if (ConfigManager.save(info.name())) Notifications.push("Config saved", info.name(), Notifications.Type.SUCCESS);
            refresh();
        });
        bx -= lw + 4;
        Widgets.button(gui, ctx, info.name() + "#load", bx, by, lw, bh, "Load", Widgets.Style.PRIMARY, null, () -> {
            if (ConfigManager.load(info.name())) Notifications.push("Config loaded", info.name(), Notifications.Type.SUCCESS);
            else Notifications.push("Configs", "Failed to load " + info.name(), Notifications.Type.ERROR);
        });
    }

    private static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }
}
