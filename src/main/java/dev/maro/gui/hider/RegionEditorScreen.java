package dev.maro.gui.hider;

import dev.maro.config.ConfigManager;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.RegionsSetting;
import dev.maro.setting.RegionsSetting.Region;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Full-screen editor for Screen Hider areas. Scribble over what you want hidden and the stroke is
 * straightened into a clean rectangle (snapped to a grid and to the screen edges). Areas can be
 * moved, resized from their corners, deleted with right-click and undone with Ctrl+Z.
 */
public class RegionEditorScreen extends Screen {
    private static final float HANDLE = 5f;
    private static final float EDGE_SNAP = 8f;
    private static final float MIN_SIZE = 8f;

    private enum Mode {NONE, DRAWING, MOVING, RESIZING}

    private final Screen parent;
    private final RegionsSetting regions;
    private final Deque<List<Region>> undo = new ArrayDeque<>();
    private final List<float[]> stroke = new ArrayList<>();

    private Mode mode = Mode.NONE;
    private int active = -1;          // region being moved / resized
    private int corner;               // 0 tl, 1 tr, 2 br, 3 bl
    private float grabX, grabY;       // move offset
    private Region original;
    private double mouseX, mouseY;
    private long openedAt = System.currentTimeMillis();

    private float[] clearButton = new float[4], doneButton = new float[4], toolbar = new float[4];

    public RegionEditorScreen(RegionsSetting regions, Screen parent) {
        super(Text.literal("Hidden Areas"));
        this.regions = regions;
        this.parent = parent;
    }

    // ---- geometry helpers ---------------------------------------------------------------

    private float[] toScreen(Region r) {
        return new float[]{r.x() * width, r.y() * height, r.w() * width, r.h() * height};
    }

    private Region toRegion(float x, float y, float w, float h) {
        return new Region(x / width, y / height, w / width, h / height).clamped();
    }

    private float grid() {
        return Math.max(2f, Math.round(Math.min(width, height) / 90f));
    }

    private float snap(float v, float max) {
        if (v < EDGE_SNAP) return 0;
        if (v > max - EDGE_SNAP) return max;
        float g = grid();
        return Math.round(v / g) * g;
    }

    /** Turns a rough rectangle into a clean one: grid/edge snapped and clamped to the screen. */
    private float[] clean(float x1, float y1, float x2, float y2) {
        float l = snap(Math.max(0, Math.min(x1, x2)), width), r = snap(Math.min(width, Math.max(x1, x2)), width);
        float t = snap(Math.max(0, Math.min(y1, y2)), height), b = snap(Math.min(height, Math.max(y1, y2)), height);
        return new float[]{l, t, r - l, b - t};
    }

    private int regionAt(double mx, double my) {
        List<Region> list = regions.list();
        for (int i = list.size() - 1; i >= 0; i--) {
            float[] s = toScreen(list.get(i));
            if (mx >= s[0] && mx <= s[0] + s[2] && my >= s[1] && my <= s[1] + s[3]) return i;
        }
        return -1;
    }

    private int cornerAt(float[] s, double mx, double my) {
        float[][] c = {{s[0], s[1]}, {s[0] + s[2], s[1]}, {s[0] + s[2], s[1] + s[3]}, {s[0], s[1] + s[3]}};
        for (int i = 0; i < 4; i++) if (Math.abs(mx - c[i][0]) <= HANDLE + 1 && Math.abs(my - c[i][1]) <= HANDLE + 1) return i;
        return -1;
    }

    private static boolean inside(float[] r, double x, double y) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    private void pushUndo() {
        undo.push(new ArrayList<>(regions.list()));
        while (undo.size() > 50) undo.removeLast();
    }

    // ---- rendering ----------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Render2D.setAlpha(1f);
        Render2D.rect(ctx, 0, 0, width, height, 0x38000000);

        // faint snapping grid while drawing / dragging
        if (mode != Mode.NONE) {
            float g = grid() * 5;
            for (float x = g; x < width; x += g) Render2D.rect(ctx, x, 0, Render2D.px(), height, 0x14FFFFFF);
            for (float y = g; y < height; y += g) Render2D.rect(ctx, 0, y, width, Render2D.px(), 0x14FFFFFF);
        }

        List<Region> list = regions.list();
        int hovered = mode == Mode.NONE ? regionAt(mouseX, mouseY) : active;
        for (int i = 0; i < list.size(); i++) {
            float[] s = toScreen(list.get(i));
            boolean hot = i == hovered;
            Render2D.rect(ctx, s[0], s[1], s[2], s[3], Theme.accent(hot ? 0x30 : 0x18));
            Render2D.roundOutline(ctx, s[0], s[1], s[2], s[3], 0, hot ? 1.5f : 1f, Theme.accent(hot ? 0xFF : 0xB0));
            String label = (i + 1) + "  " + Math.round(s[2]) + " × " + Math.round(s[3]);
            float lw = Fonts.width(label, true, 0.62f) + 8;
            if (s[2] > lw + 4 && s[3] > 14) {
                Render2D.roundRect(ctx, s[0] + 3, s[1] + 3, lw, 10, 3, 0xC0101016);
                Fonts.draw(ctx, label, s[0] + 7, s[1] + 5.5f, Theme.TEXT, true, 0.62f);
            }
            if (hot) {
                float[][] c = {{s[0], s[1]}, {s[0] + s[2], s[1]}, {s[0] + s[2], s[1] + s[3]}, {s[0], s[1] + s[3]}};
                for (float[] p : c) {
                    Render2D.roundRect(ctx, p[0] - HANDLE / 2 - 1, p[1] - HANDLE / 2 - 1, HANDLE + 2, HANDLE + 2, 1.5f, Theme.accent());
                    Render2D.roundRect(ctx, p[0] - HANDLE / 2, p[1] - HANDLE / 2, HANDLE, HANDLE, 1f, 0xFFFFFFFF);
                }
            }
        }

        // live stroke + the clean rectangle it will become
        if (mode == Mode.DRAWING && stroke.size() > 1) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] p : stroke) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            float[] box = clean(minX, minY, maxX, maxY);
            Render2D.rect(ctx, box[0], box[1], box[2], box[3], Theme.accent(0x22));
            Render2D.roundOutline(ctx, box[0], box[1], box[2], box[3], 0, 1f, Theme.accent(0xD0));
            for (int i = 1; i < stroke.size(); i++) {
                float[] a = stroke.get(i - 1), b = stroke.get(i);
                Render2D.line(ctx, a[0], a[1], b[0], b[1], 1.6f, 0xD0FFFFFF);
            }
        }

        renderToolbar(ctx);
    }

    private void renderToolbar(DrawContext ctx) {
        String hint = regions.list().isEmpty()
                ? "Draw over anything you want hidden"
                : "Draw • Drag to move • Corners resize • Right-click delete • Ctrl+Z undo";
        float hw = Fonts.width(hint, false, 0.72f);
        float title = Fonts.width("HIDDEN AREAS", true, 0.8f);
        float clearW = Fonts.width("Clear", true, 0.75f) + 16, doneW = Fonts.width("Done", true, 0.75f) + 16;
        float w = 12 + Math.max(title, hw) + 12 + clearW + 4 + doneW + 8, h = 30;
        float x = (width - w) / 2f, y = 8;
        // move out of the way while the cursor works near the top
        if (mode != Mode.NONE && mouseY < y + h + 12) y = height - h - 8;
        toolbar = new float[]{x, y, w, h};
        float fade = mode != Mode.NONE && inside(toolbar, mouseX, mouseY) ? 0.35f : 1f;
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(fade);
        Render2D.shadow(ctx, x, y + 1, w, h, 8, 8, 0x60000000);
        Render2D.roundRect(ctx, x, y, w, h, 8, 0xF00C0C11);
        Render2D.roundOutline(ctx, x, y, w, h, 8, 1f, Theme.BORDER);
        Fonts.draw(ctx, "HIDDEN AREAS", x + 12, y + 7, Theme.TEXT, true, 0.8f);
        Fonts.draw(ctx, hint, x + 12, y + 18, Theme.TEXT_MUTED, false, 0.72f);

        float bx = x + w - 8 - doneW - 4 - clearW, by = y + 7, bh = 16;
        clearButton = new float[]{bx, by, clearW, bh};
        boolean ch = inside(clearButton, mouseX, mouseY);
        Render2D.roundRect(ctx, bx, by, clearW, bh, 5, ColorUtil.withAlpha(Theme.RED, ch ? 0x55 : 0x2A));
        Fonts.drawCentered(ctx, "Clear", bx + clearW / 2f, by + bh / 2f, ColorUtil.lerp(0xFFFF8A8E, 0xFFFFFFFF, ch ? 1 : 0), true, 0.75f);

        bx += clearW + 4;
        doneButton = new float[]{bx, by, doneW, bh};
        boolean dh = inside(doneButton, mouseX, mouseY);
        Render2D.roundGradientH(ctx, bx, by, doneW, bh, 5, ColorUtil.shade(Theme.accent(), dh ? 0.12f : 0), ColorUtil.shade(Theme.accent2(), dh ? 0.12f : 0));
        Fonts.drawCentered(ctx, "Done", bx + doneW / 2f, by + bh / 2f, 0xFFFFFFFF, true, 0.75f);
        Render2D.setAlpha(prev);
    }

    // ---- input --------------------------------------------------------------------------

    @Override
    public void mouseMoved(double x, double y) {
        mouseX = x;
        mouseY = y;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        mouseX = click.x();
        mouseY = click.y();
        if (System.currentTimeMillis() - openedAt < 150) return true;
        if (inside(clearButton, mouseX, mouseY)) {
            if (!regions.list().isEmpty()) {
                pushUndo();
                regions.list().clear();
                Sounds.click();
            }
            return true;
        }
        if (inside(doneButton, mouseX, mouseY)) {
            Sounds.click();
            close();
            return true;
        }
        if (inside(toolbar, mouseX, mouseY)) return true;

        int hit = regionAt(mouseX, mouseY);
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hit >= 0) {
                pushUndo();
                regions.list().remove(hit);
                Sounds.click();
            }
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        // corners of any region win over its body
        for (int i = regions.list().size() - 1; i >= 0; i--) {
            int c = cornerAt(toScreen(regions.list().get(i)), mouseX, mouseY);
            if (c >= 0) {
                pushUndo();
                mode = Mode.RESIZING;
                active = i;
                corner = c;
                original = regions.list().get(i);
                return true;
            }
        }
        if (hit >= 0) {
            pushUndo();
            float[] s = toScreen(regions.list().get(hit));
            mode = Mode.MOVING;
            active = hit;
            grabX = (float) mouseX - s[0];
            grabY = (float) mouseY - s[1];
            return true;
        }
        mode = Mode.DRAWING;
        stroke.clear();
        stroke.add(new float[]{(float) mouseX, (float) mouseY});
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        mouseX = click.x();
        mouseY = click.y();
        float mx = (float) Math.max(0, Math.min(width, mouseX)), my = (float) Math.max(0, Math.min(height, mouseY));
        switch (mode) {
            case DRAWING -> {
                float[] last = stroke.get(stroke.size() - 1);
                if (Math.abs(last[0] - mx) + Math.abs(last[1] - my) >= 1.5f) stroke.add(new float[]{mx, my});
            }
            case MOVING -> {
                float[] s = toScreen(regions.list().get(active));
                float nx = Math.max(0, Math.min(width - s[2], snap(mx - grabX, width - s[2])));
                float ny = Math.max(0, Math.min(height - s[3], snap(my - grabY, height - s[3])));
                regions.list().set(active, toRegion(nx, ny, s[2], s[3]));
            }
            case RESIZING -> {
                float[] s = toScreen(original);
                float l = s[0], t = s[1], r = s[0] + s[2], b = s[1] + s[3];
                if (corner == 0 || corner == 3) l = mx;
                else r = mx;
                if (corner == 0 || corner == 1) t = my;
                else b = my;
                float[] box = clean(l, t, r, b);
                if (box[2] >= MIN_SIZE && box[3] >= MIN_SIZE) regions.list().set(active, toRegion(box[0], box[1], box[2], box[3]));
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (mode == Mode.DRAWING && stroke.size() > 1) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] p : stroke) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            float[] box = clean(minX, minY, maxX, maxY);
            if (box[2] >= MIN_SIZE && box[3] >= MIN_SIZE) {
                pushUndo();
                regions.list().add(toRegion(box[0], box[1], box[2], box[3]));
                Sounds.click();
            }
        }
        mode = Mode.NONE;
        active = -1;
        stroke.clear();
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (!undo.isEmpty()) {
                regions.list().clear();
                regions.list().addAll(undo.pop());
                Sounds.click();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            int hit = regionAt(mouseX, mouseY);
            if (hit >= 0) {
                pushUndo();
                regions.list().remove(hit);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            close();
            return true;
        }
        return true;
    }

    @Override
    public void close() {
        ConfigManager.save(ConfigManager.getCurrent());
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
