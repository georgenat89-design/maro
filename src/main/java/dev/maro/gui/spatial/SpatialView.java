package dev.maro.gui.spatial;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.mixin.GameRendererSpatialAccessor;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * The Spatial menu: the menu on a glass pane floating in the world in front of you, like a
 * headset's windows. The mouse turns your head as in game and the crosshair is the pointer; the
 * bar under the pane moves it (it follows your look while held, the wheel pulls it nearer or
 * pushes it away) and the red dot beside the bar closes the menu.
 *
 * <p>The menu itself is drawn as usual but off screen ({@link SpatialCanvas}); here that picture
 * is laid on the pane in perspective, as a fine grid of quads, so it sits still in the world as
 * you look around it.
 */
public final class SpatialView {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    /** How far in front of you the pane opens, in blocks. */
    private static final double DISTANCE = 2.4;
    /** How much of the view the pane fills when it opens straight ahead; the rest shows the world round it. */
    private static final double PANE_SIZE = 0.8;
    private static final int GRID_X = 32, GRID_Y = 18;
    private static final double NEAR = 0.05;

    private final ClickGuiScreen menu;
    private final SpatialCanvas canvas = new SpatialCanvas();
    private boolean open, placed;

    // The pane: its centre, its right and up directions, and its size in blocks (the whole canvas).
    private double cx, cy, cz, rx, ry, rz, ux, uy, uz, paneW, paneH;
    // The camera this frame: eye, forward, right, up, and the projection.
    private double ex, ey, ez, fx, fy, fz, crx, cry, crz, cux, cuy, cuz, tanV, aspect;
    private int sw = 1, sh = 1;

    // Where the crosshair meets the pane, in menu coordinates, and how far away that is.
    private double px = -1e4, py = -1e4, hitDistance;
    private boolean onPane, barHover, dotHover;

    // Moving the pane by its bar.
    private boolean moving;
    private double grabX, grabY, grabDistance;

    private double lastCursorX, lastCursorY;
    private boolean cursorKnown;
    private long lastFrame;
    private float barGlow, dotGrow;

    public SpatialView(ClickGuiScreen menu) {
        this.menu = menu;
    }

    // ---- open and close -------------------------------------------------------------------------

    /** The mouse turns your head from now on (the cursor is hidden and free, as in game). */
    public void open() {
        if (open) return;
        open = true;
        cursorKnown = false;
        GLFW.glfwSetInputMode(mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    /** Gives the cursor back (for another screen or the game) and frees the picture. */
    public void close() {
        if (!open) return;
        open = false;
        moving = false;
        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(handle, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
        canvas.close();
    }

    public boolean isOpen() {
        return open;
    }

    /** Puts the pane straight in front of you again on the next frame. */
    public void recenter() {
        placed = false;
    }

    // ---- each frame -----------------------------------------------------------------------------

    /** Turns your head by the mouse, finds what the crosshair points at, draws the menu and lays it on the pane. */
    public void frame(DrawContext ctx, float delta) {
        if (!open) open();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        sw = Math.max(1, menu.width);
        sh = Math.max(1, menu.height);

        look();
        camera();
        if (!placed) {
            place();
            placed = true;
        }
        if (moving) follow(dt);
        point();
        menu.spatialPointer(onPane ? px : -1e4, onPane ? py : -1e4);

        canvas.draw(c -> menu.spatialDraw(c, delta), onPane ? (int) px : -10000, onPane ? (int) py : -10000);
        draw(ctx, dt);
    }

    /** Turns your head by how far the hidden cursor moved, with the game's own sensitivity. */
    private void look() {
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), x, y);
        if (!cursorKnown) {
            lastCursorX = x[0];
            lastCursorY = y[0];
            cursorKnown = true;
            return;
        }
        double dx = x[0] - lastCursorX, dy = y[0] - lastCursorY;
        lastCursorX = x[0];
        lastCursorY = y[0];
        if (mc.player == null || menu.menuProgress() < 0.05f && menu.isClosing()) return;
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double g = s * s * s * 8.0;
        double turn = dx * g, tilt = dy * g;
        if (mc.options.getInvertMouseX().getValue()) turn = -turn;
        if (mc.options.getInvertMouseY().getValue()) tilt = -tilt;
        if (turn != 0 || tilt != 0) mc.player.changeLookDirection(turn, tilt);
    }

    /** The camera as the world was drawn this frame. */
    private void camera() {
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d eye = camera.getCameraPos();
        ex = eye.x;
        ey = eye.y;
        ez = eye.z;
        double yaw = Math.toRadians(camera.getYaw()), pitch = Math.toRadians(camera.getPitch());
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        crx = -Math.cos(yaw);
        cry = 0;
        crz = -Math.sin(yaw);
        cux = cry * fz - crz * fy;
        cuy = crz * fx - crx * fz;
        cuz = crx * fy - cry * fx;
        float fov = ((GameRendererSpatialAccessor) mc.gameRenderer).maro$fov(camera, mc.getRenderTickCounter().getTickProgress(true), true);
        tanV = Math.tan(Math.toRadians(fov) / 2.0);
        aspect = (double) sw / sh;
    }

    /** The pane straight ahead, a little smaller than the view so the world shows round it. */
    private void place() {
        cx = ex + fx * DISTANCE;
        cy = ey + fy * DISTANCE;
        cz = ez + fz * DISTANCE;
        rx = crx;
        ry = cry;
        rz = crz;
        ux = cux;
        uy = cuy;
        uz = cuz;
        paneH = 2 * DISTANCE * tanV * PANE_SIZE;
        paneW = paneH * aspect;
    }

    /** While the bar is held: the pane swings round to keep the bar under the crosshair, facing you. */
    private void follow(float dt) {
        double gx = ex + fx * grabDistance, gy = ey + fy * grabDistance, gz = ez + fz * grabDistance;
        double tx = gx - crx * grabX - cux * grabY, ty = gy - cry * grabX - cuy * grabY, tz = gz - crz * grabX - cuz * grabY;
        double k = 1 - Math.exp(-dt * 16);
        cx += (tx - cx) * k;
        cy += (ty - cy) * k;
        cz += (tz - cz) * k;
        rx += (crx - rx) * k;
        ry += (cry - ry) * k;
        rz += (crz - rz) * k;
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        rx /= rl;
        ry /= rl;
        rz /= rl;
        ux += (cux - ux) * k;
        uy += (cuy - uy) * k;
        uz += (cuz - uz) * k;
        // Up stays at right angles to right.
        double d = ux * rx + uy * ry + uz * rz;
        ux -= rx * d;
        uy -= ry * d;
        uz -= rz * d;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
    }

    /** Where the crosshair's line meets the pane, and whether that is on the bar or the dot. */
    private void point() {
        // The pane's normal.
        double nx = ry * uz - rz * uy, ny = rz * ux - rx * uz, nz = rx * uy - ry * ux;
        double facing = fx * nx + fy * ny + fz * nz;
        onPane = barHover = dotHover = false;
        if (Math.abs(facing) < 1e-5) return;
        double t = ((cx - ex) * nx + (cy - ey) * ny + (cz - ez) * nz) / facing;
        if (t <= NEAR) return;
        double hx = ex + fx * t - cx, hy = ey + fy * t - cy, hz = ez + fz * t - cz;
        double lx = hx * rx + hy * ry + hz * rz, ly = hx * ux + hy * uy + hz * uz;
        px = (lx / paneW + 0.5) * sw;
        py = (0.5 - ly / paneH) * sh;
        hitDistance = t;
        onPane = px >= 0 && py >= 0 && px < sw && py < sh;
        float[] bar = bar(), dot = dot();
        if (bar != null && !menu.isClosing()) {
            barHover = moving || px >= bar[0] - 6 && px <= bar[0] + bar[2] + 6 && py >= bar[1] - 6 && py <= bar[1] + bar[3] + 6;
            dotHover = !moving && Math.hypot(px - dot[0], py - dot[1]) <= 8;
        }
    }

    /** The bar under the menu window, as {x, y, w, h} in menu coordinates; null before the window is known. */
    private float[] bar() {
        float[] win = menu.spatialWindow();
        if (win == null) return null;
        float w = Math.min(130, win[2] * 0.3f), h = 5f;
        return new float[]{win[0] + (win[2] - w) / 2f, win[1] + win[3] + 10, w, h};
    }

    /** The close dot beside the bar, as {x, y, radius}. */
    private float[] dot() {
        float[] bar = bar();
        if (bar == null) return null;
        return new float[]{bar[0] + bar[2] + 11, bar[1] + bar[3] / 2f, 3.4f};
    }

    // ---- input ----------------------------------------------------------------------------------

    /** A press on the bar starts moving the pane, on the dot closes the menu; whether it was one of them. */
    public boolean press(int button) {
        if (button != 0) return false;
        if (dotHover) {
            Sounds.click();
            menu.close();
            return true;
        }
        if (barHover) {
            moving = true;
            // Where on the pane it was taken, from its centre, in blocks.
            grabX = (px / sw - 0.5) * paneW;
            grabY = (0.5 - py / sh) * paneH;
            grabDistance = hitDistance;
            Sounds.click();
            return true;
        }
        return false;
    }

    public void release() {
        moving = false;
    }

    /** The wheel while moving the pane pulls it nearer or pushes it away; whether it was used so. */
    public boolean scroll(double amount) {
        if (!moving) return false;
        grabDistance = Math.max(1.0, Math.min(8.0, grabDistance * (1 - amount * 0.08)));
        return true;
    }

    public boolean onPane() {
        return onPane;
    }

    public boolean moving() {
        return moving;
    }

    /** Where the crosshair is on the menu, {x, y}; for tests. */
    public double[] pointer() {
        return new double[]{px, py};
    }

    /** The middle of the bar, {x, y} in menu coordinates; for tests. */
    public float[] barCenter() {
        float[] bar = bar();
        return bar == null ? null : new float[]{bar[0] + bar[2] / 2f, bar[1] + bar[3] / 2f};
    }

    /** The middle of the close dot, {x, y} in menu coordinates; for tests. */
    public float[] dotCenter() {
        float[] dot = dot();
        return dot == null ? null : new float[]{dot[0], dot[1]};
    }

    /** The pane's centre in the world; for tests. */
    public Vec3d centre() {
        return new Vec3d(cx, cy, cz);
    }

    /** The point in the world at menu coordinates {@code x, y}; for tests to look at. */
    public Vec3d worldAt(double x, double y) {
        double lx = (x / sw - 0.5) * paneW, ly = (0.5 - y / sh) * paneH;
        return new Vec3d(cx + rx * lx + ux * ly, cy + ry * lx + uy * ly, cz + rz * lx + uz * ly);
    }

    // ---- drawing --------------------------------------------------------------------------------

    private float scale = 1f;

    /** Menu coordinates to the screen through the pane, into {@code out} at {@code i}; false if behind the eye. */
    private boolean project(double x, double y, float[] out, int i) {
        double lx = (x / sw - 0.5) * paneW * scale, ly = (0.5 - y / sh) * paneH * scale;
        double dx = cx + rx * lx + ux * ly - ex, dy = cy + ry * lx + uy * ly - ey, dz = cz + rz * lx + uz * ly - ez;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < NEAR) return false;
        double sx = dx * crx + dy * cry + dz * crz, sy = dx * cux + dy * cuy + dz * cuz;
        out[i] = (float) ((sx / (depth * tanV * aspect) + 1) * 0.5 * sw);
        out[i + 1] = (float) ((1 - sy / (depth * tanV)) * 0.5 * sh);
        return true;
    }

    private void draw(DrawContext ctx, float dt) {
        float p = menu.menuProgress();
        if (p <= 0.002f) return;
        scale = 0.9f + 0.1f * p;
        barGlow += ((barHover ? 1f : 0f) - barGlow) * Math.min(1f, dt * 14f);
        dotGrow += ((dotHover ? 1f : 0f) - dotGrow) * Math.min(1f, dt * 14f);
        float[] win = menu.spatialWindow();

        // The glass behind the window.
        if (win != null) {
            ctx.createNewRootLayer();
            MeshRenderState.Builder glass = new MeshRenderState.Builder(false);
            roundFill(glass, win[0], win[1], win[2], win[3], win[4], fade(0xAA2C3035, p), fade(0xBE1A1D21, p));
            glass.submit(ctx, RenderPipelines.GUI, TextureSetup.empty(), sw, sh);
        }

        // The menu itself.
        GpuTextureView view = canvas.view();
        if (view != null) {
            ctx.createNewRootLayer();
            MeshRenderState.Builder mesh = new MeshRenderState.Builder(true);
            float[] at = new float[(GRID_X + 1) * (GRID_Y + 1) * 2];
            boolean[] ok = new boolean[(GRID_X + 1) * (GRID_Y + 1)];
            for (int j = 0; j <= GRID_Y; j++) {
                for (int i = 0; i <= GRID_X; i++) {
                    int k = j * (GRID_X + 1) + i;
                    ok[k] = project((double) i / GRID_X * sw, (double) j / GRID_Y * sh, at, k * 2);
                }
            }
            int tint = 0xFFFFFFFF; // the menu fades itself as it opens and closes
            for (int j = 0; j < GRID_Y; j++) {
                for (int i = 0; i < GRID_X; i++) {
                    int k00 = j * (GRID_X + 1) + i, k10 = k00 + 1, k01 = k00 + GRID_X + 1, k11 = k01 + 1;
                    if (!ok[k00] || !ok[k10] || !ok[k01] || !ok[k11]) continue;
                    float u0 = (float) i / GRID_X, u1 = (float) (i + 1) / GRID_X;
                    float v0 = 1f - (float) j / GRID_Y, v1 = 1f - (float) (j + 1) / GRID_Y;
                    mesh.quad(at[k00 * 2], at[k00 * 2 + 1], u0, v0, tint,
                            at[k01 * 2], at[k01 * 2 + 1], u0, v1, tint,
                            at[k11 * 2], at[k11 * 2 + 1], u1, v1, tint,
                            at[k10 * 2], at[k10 * 2 + 1], u1, v0, tint);
                }
            }
            mesh.submit(ctx, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)), sw, sh);
        }

        // The rim, the bar and the dot over everything on the pane.
        if (win != null) {
            ctx.createNewRootLayer();
            MeshRenderState.Builder lines = new MeshRenderState.Builder(false);
            roundRing(lines, win[0], win[1], win[2], win[3], win[4], 1.2f, 1.6f, fade(0xF0E9ECEF, p), fade(0xC8B9BEC4, p));
            float[] bar = bar(), dot = dot();
            int barColor = moving ? Theme.accent() : ColorUtil.lerp(0xD8D2D5D9, 0xFFFFFFFF, barGlow);
            float grow = barGlow * 6f;
            roundFill(lines, bar[0] - grow, bar[1] - barGlow * 0.5f, bar[2] + grow * 2, bar[3] + barGlow, (bar[3] + barGlow) / 2f,
                    fade(barColor, p), fade(barColor, p));
            float r = dot[2] + dotGrow * 1.6f;
            circle(lines, dot[0], dot[1], r, fade(ColorUtil.lerp(0xFFE5484D, 0xFFFF6B6F, dotGrow), p));
            if (dotGrow > 0.05f) {
                float c = r * 0.45f;
                int cross = fade(ColorUtil.withAlpha(0xFFFFFFFF, Math.round(255 * dotGrow)), p);
                line(lines, dot[0] - c, dot[1] - c, dot[0] + c, dot[1] + c, 0.7f, cross);
                line(lines, dot[0] - c, dot[1] + c, dot[0] + c, dot[1] - c, 0.7f, cross);
            }
            lines.submit(ctx, RenderPipelines.GUI, TextureSetup.empty(), sw, sh);
        }

        // The pointer: the crosshair, a ring while it is on something of the menu's.
        ctx.createNewRootLayer();
        float mx = sw / 2f, my = sh / 2f;
        int white = fade(0xF2FFFFFF, p);
        if (onPane || barHover || dotHover) {
            Render2D.circle(ctx, mx, my, 3.2f, fade(0x50000000, p));
            Render2D.circle(ctx, mx, my, 2.2f, white);
        } else {
            Render2D.rect(ctx, mx - 4.5f, my - 0.5f, 9, 1, white);
            Render2D.rect(ctx, mx - 0.5f, my - 4.5f, 1, 9, white);
        }
    }

    private static int fade(int color, float p) {
        return ColorUtil.withAlpha(color, Math.round((color >>> 24) * p));
    }

    /** The points round a rounded rectangle, clockwise from the top left, in menu coordinates. */
    private static float[] outline(float x, float y, float w, float h, float r) {
        r = Math.max(0.01f, Math.min(r, Math.min(w, h) / 2f));
        int seg = 8;
        float[] pts = new float[4 * (seg + 1) * 2];
        float[][] corners = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        int n = 0;
        for (float[] c : corners) {
            for (int s = 0; s <= seg; s++) {
                double a = Math.toRadians(c[2] + 90.0 * s / seg);
                pts[n++] = (float) (c[0] + Math.cos(a) * r);
                pts[n++] = (float) (c[1] + Math.sin(a) * r);
            }
        }
        return pts;
    }

    /** A rounded rectangle on the pane, shaded from {@code top} to {@code bottom}. */
    private void roundFill(MeshRenderState.Builder b, float x, float y, float w, float h, float r, int top, int bottom) {
        float[] pts = outline(x, y, w, h, r);
        float mxp = x + w / 2f, myp = y + h / 2f;
        float[] c = new float[2], s0 = new float[2], s1 = new float[2];
        if (!project(mxp, myp, c, 0)) return;
        int mid = ColorUtil.lerp(top, bottom, 0.5f);
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            if (!project(pts[i * 2], pts[i * 2 + 1], s0, 0) || !project(pts[j * 2], pts[j * 2 + 1], s1, 0)) continue;
            int c0 = ColorUtil.lerp(top, bottom, (pts[i * 2 + 1] - y) / h), c1 = ColorUtil.lerp(top, bottom, (pts[j * 2 + 1] - y) / h);
            b.triangle(c[0], c[1], mid, s0[0], s0[1], c0, s1[0], s1[1], c1);
        }
    }

    /** The edge of a rounded rectangle on the pane, {@code out} outside it and {@code in} inside. */
    private void roundRing(MeshRenderState.Builder b, float x, float y, float w, float h, float r, float out, float in, int top, int bottom) {
        float[] outer = outline(x - out, y - out, w + out * 2, h + out * 2, r + out);
        float[] inner = outline(x + in, y + in, w - in * 2, h - in * 2, Math.max(0.5f, r - in));
        float[] a0 = new float[2], a1 = new float[2], b0 = new float[2], b1 = new float[2];
        int n = outer.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            if (!project(outer[i * 2], outer[i * 2 + 1], a0, 0) || !project(outer[j * 2], outer[j * 2 + 1], a1, 0)
                    || !project(inner[i * 2], inner[i * 2 + 1], b0, 0) || !project(inner[j * 2], inner[j * 2 + 1], b1, 0)) continue;
            int c0 = ColorUtil.lerp(top, bottom, (outer[i * 2 + 1] - y) / h), c1 = ColorUtil.lerp(top, bottom, (outer[j * 2 + 1] - y) / h);
            b.quad(a0[0], a0[1], 0, 0, c0, a1[0], a1[1], 0, 0, c1, b1[0], b1[1], 0, 0, c1, b0[0], b0[1], 0, 0, c0);
        }
    }

    private void circle(MeshRenderState.Builder b, float x, float y, float r, int color) {
        roundFill(b, x - r, y - r, r * 2, r * 2, r, color, color);
    }

    private void line(MeshRenderState.Builder b, float x1, float y1, float x2, float y2, float width, int color) {
        double len = Math.hypot(x2 - x1, y2 - y1);
        if (len < 1e-3) return;
        float nx = (float) (-(y2 - y1) / len * width / 2), ny = (float) ((x2 - x1) / len * width / 2);
        float[] a = new float[2], c = new float[2], d = new float[2], e = new float[2];
        if (!project(x1 + nx, y1 + ny, a, 0) || !project(x2 + nx, y2 + ny, c, 0) || !project(x2 - nx, y2 - ny, d, 0)
                || !project(x1 - nx, y1 - ny, e, 0)) return;
        b.quad(a[0], a[1], 0, 0, color, c[0], c[1], 0, 0, color, d[0], d[1], 0, 0, color, e[0], e[1], 0, 0, color);
    }
}
