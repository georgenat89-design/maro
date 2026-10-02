package dev.maro.gui.notification;

import dev.maro.config.ClientSettings;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Toast notifications that slide in from the screen edge. Rendered on the HUD and in the menu. */
public final class Notifications {
    public enum Type {INFO, SUCCESS, WARNING, ERROR, ENABLED, DISABLED}

    private static final int MAX = 6;
    private static final List<Note> NOTES = new ArrayList<>();

    private static final class Note {
        final String title, message;
        final Type type;
        final long start = System.currentTimeMillis();
        final long duration;
        final Animation slide = new Animation(11f, 0f);
        final Animation y = new Animation(14f, -1f);

        Note(String title, String message, Type type, long duration) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.duration = duration;
        }
    }

    private Notifications() {
    }

    public static void push(String title, String message, Type type) {
        push(title, message, type, 2400);
    }

    public static void push(String title, String message, Type type, long durationMs) {
        if (!ClientSettings.notifications.get()) return;
        NOTES.add(0, new Note(title, message, type, durationMs));
        while (NOTES.size() > MAX) NOTES.remove(NOTES.size() - 1);
    }

    private static int color(Type type) {
        return switch (type) {
            case SUCCESS, ENABLED -> Theme.GREEN;
            case WARNING -> Theme.YELLOW;
            case ERROR, DISABLED -> Theme.RED;
            default -> Theme.accent();
        };
    }

    private static Icons.Icon icon(Type type) {
        return switch (type) {
            case SUCCESS, ENABLED -> Icons.CHECK;
            case ERROR, DISABLED -> Icons.CLOSE;
            case WARNING -> Icons.WARNING;
            default -> Icons.INFO;
        };
    }

    public static void render(DrawContext ctx) {
        if (NOTES.isEmpty()) return;
        float prevAlpha = Render2D.getAlpha();
        Render2D.setAlpha(1f);
        MinecraftClient mc = MinecraftClient.getInstance();
        float sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        boolean top = ClientSettings.notificationPos.is("Top");
        float h = 28f, gap = 5f, margin = 8f;
        long now = System.currentTimeMillis();

        int index = 0;
        Iterator<Note> it = NOTES.iterator();
        while (it.hasNext()) {
            Note n = it.next();
            boolean leaving = now - n.start > n.duration;
            float v = n.slide.update(leaving ? 0f : 1f);
            if (leaving && v < 0.01f) {
                it.remove();
                continue;
            }
            float targetY = index * (h + gap);
            if (n.y.get() < 0) n.y.snap(targetY);
            float yOff = n.y.update(targetY);
            index++;

            float tw = Math.max(Fonts.width(n.title, true, 0.85f), Fonts.width(n.message, false, 0.75f));
            float w = Math.max(120f, tw + 44f);
            float e = Easing.outCubic(v);
            float x = sw - margin - w * e + (1 - e) * 12f;
            float y = top ? margin + yOff : sh - margin - h - yOff;
            int accent = color(n.type);

            Render2D.setAlpha(e);
            Render2D.shadow(ctx, x, y + 1, w, h, 6, 8, 0x60000000);
            Render2D.roundRect(ctx, x, y, w, h, 6, 0xF20E131D);
            Render2D.roundOutline(ctx, x, y, w, h, 6, 1f, Theme.BORDER);

            float ix = x + 15, iy = y + h / 2f;
            if (Theme.glow()) Render2D.shadow(ctx, ix - 7, iy - 7, 14, 14, 7, 4, ColorUtil.withAlpha(accent, 0x40));
            Render2D.circle(ctx, ix, iy, 7, ColorUtil.withAlpha(accent, 0x2A));
            Render2D.ring(ctx, ix, iy, 7, 1f, ColorUtil.withAlpha(accent, 0x90));
            icon(n.type).draw(ctx, ix, iy, 6.5f, accent, 0);

            Fonts.draw(ctx, n.title, x + 28, y + 6.5f, Theme.TEXT, true, 0.85f);
            Fonts.draw(ctx, n.message, x + 28, y + 16f, Theme.TEXT_DIM, false, 0.75f);

            float progress = 1f - Math.min(1f, (now - n.start) / (float) n.duration);
            float bw = (w - 12) * progress;
            if (bw > 0.5f) Render2D.roundGradientH(ctx, x + 6, y + h - 2.5f, bw, 1.5f, 0.75f, ColorUtil.withAlpha(accent, 0xFF), ColorUtil.withAlpha(accent, 0x60));
        }
        Render2D.setAlpha(prevAlpha);
    }
}
