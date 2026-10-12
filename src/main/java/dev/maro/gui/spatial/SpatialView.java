package dev.maro.gui.spatial;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.mixin.GameRendererSpatialAccessor;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * The floating window: the menu on a glass pane in the world, like a headset's windows. The mouse
 * turns your head as in game, the crosshair is the pointer, and you can walk about while it hangs
 * where it is. The bar under the pane moves it (it follows your look while held, the wheel pulls
 * it nearer or pushes it away) and the red dot beside the bar closes the menu. It opens again
 * where you left it when that is nearby and in front of you.
 *
 * <p>The menu itself is drawn as usual but off screen ({@link SpatialCanvas}); here that picture
 * is laid on the pane in perspective, as a fine grid of quads, so it sits still in the world.
 */
public final class SpatialView {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    /** How far in front of you the pane opens, in blocks. */
    private static final double DISTANCE = 2.4;
    /** How much of the view the pane fills when it opens straight ahead; the rest shows the world round it. */
    private static final double PANE_SIZE = 0.8;
    /** A remembered spot is used again within this many blocks, and this far round from where you look. */
    private static final double REMEMBER_RANGE = 24, REMEMBER_COS = Math.cos(Math.toRadians(70));
    private static final int GRID_X = 32, GRID_Y = 18;
    private static final double NEAR = 0.05;

    /** Where the pane was left, so it can open there again. */
    private record Spot(Object world, double cx, double cy, double cz, double rx, double ry, double rz,
                        double ux, double uy, double uz, double paneH) {
    }

    private static Spot spot;

    private final ClickGuiScreen menu;
    private final SpatialCanvas canvas = new SpatialCanvas();
    private boolean open, placed;

    // The pane: its centre, its right and up directions, and its size in blocks (the whole canvas).
    private double cx, cy, cz, rx, ry, rz, ux, uy, uz, paneW, paneH;
    // The camera this frame: eye, forward, right, up, and the projection.
    private double ex, ey, ez, fx, fy, fz, crx, cry, crz, cux, cuy, cuz, tanV, aspect;
    private int sw = 1, sh = 1;
    /** Roughly how many screen pixels one menu unit covers on the pane, for hairline edges. */
    private float unitPixels = 2;
    /** Whether you are in front of the pane (from behind it shows only its glass). */
    private boolean front = true;

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

    /** Gives the cursor back (for another screen or the game), lets go of the walking keys and frees the picture. */
    public void close() {
        if (!open) return;
        open = false;
        moving = false;
        remember();
        walk(false);
        long handle = mc.getWindow().getHandle();
        GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPos(handle, mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0);
        canvas.close();
        if (active == this) active = null;
    }

    public boolean isOpen() {
        return open;
    }

    /** Puts the pane straight in front of you again on the next frame. */
    public void recenter() {
        placed = false;
        spot = null;
    }

    private void remember() {
        if (placed && mc.world != null) spot = new Spot(mc.world, cx, cy, cz, rx, ry, rz, ux, uy, uz, paneH);
    }

    // ---- walking --------------------------------------------------------------------------------

    /**
     * Each tick while the window is open: the movement keys follow the keyboard, so you can walk,
     * jump, sneak and sprint with the menu up ({@code allowed} is false while typing into a box).
     */
    public void walk(boolean allowed) {
        GameOptions o = mc.options;
        if (o == null) return;
        boolean on = allowed && open && ClientSettings.spatialWalk.get();
        for (KeyBinding key : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sneakKey, o.sprintKey}) {
            InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(key);
            key.setPressed(on && bound.getCategory() == InputUtil.Type.KEYSYM && bound.getCode() != InputUtil.UNKNOWN_KEY.getCode()
                    && InputUtil.isKeyPressed(mc.getWindow(), bound.getCode()));
        }
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
        paneW = paneH * aspect;
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
        if (mc.player == null || menu.isClosing()) return;
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

    /** Where it was left if that is near and in front of you, else straight ahead at a little less than the view's size. */
    private void place() {
        Spot s = spot;
        if (s != null && ClientSettings.spatialRemember.get() && s.world() == mc.world) {
            double dx = s.cx() - ex, dy = s.cy() - ey, dz = s.cz() - ez;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < REMEMBER_RANGE && dist > 0.3 && (dx * fx + dy * fy + dz * fz) / dist > REMEMBER_COS) {
                cx = s.cx();
                cy = s.cy();
                cz = s.cz();
                rx = s.rx();
                ry = s.ry();
                rz = s.rz();
                ux = s.ux();
                uy = s.uy();
                uz = s.uz();
                paneH = s.paneH();
                return;
            }
        }
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
        // The pane's normal, towards whoever it was placed for.
        double nx = ry * uz - rz * uy, ny = rz * ux - rx * uz, nz = rx * uy - ry * ux;
        front = (ex - cx) * nx + (ey - cy) * ny + (ez - cz) * nz > 0;
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
        onPane = front && px >= 0 && py >= 0 && px < sw && py < sh;
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
        if (moving) {
            moving = false;
            remember();
        }
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

    private static final RenderPipeline GLASS_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/floating_window_glass"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).withDepthWrite(false).build());
    private static final RenderPipeline PICTURE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/floating_window_menu"))
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            // The picture holds premultiplied colour, as the GUI renderer leaves it.
            .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
            .withCull(false).withDepthWrite(false).build());
    // Three layers, drawn in turn: the glass under the menu, the menu, and the edge, bar and dot over it.
    private static final RenderLayer GLASS = RenderLayer.of("maro_floating_window_glass", RenderSetup.builder(GLASS_PIPELINE).translucent().build());
    private static final RenderLayer PICTURE = RenderLayer.of("maro_floating_window_menu",
            RenderSetup.builder(PICTURE_PIPELINE).texture("Sampler0", CanvasTexture.ID).translucent().build());
    private static final RenderLayer EDGE = RenderLayer.of("maro_floating_window_edge", RenderSetup.builder(GLASS_PIPELINE).translucent().build());
    private static final VertexConsumerProvider.Immediate BUFFERS;

    /** The floating window open now, drawn into the world each frame. */
    private static SpatialView active;

    static {
        var buffers = new java.util.LinkedHashMap<RenderLayer, BufferAllocator>();
        buffers.put(GLASS, new BufferAllocator(65536));
        buffers.put(PICTURE, new BufferAllocator(4096));
        buffers.put(EDGE, new BufferAllocator(65536));
        BUFFERS = VertexConsumerProvider.immediate(buffers, new BufferAllocator(1024));
        // In the world, after the blocks and entities: blocks in front hide it, and up close it is
        // cut where it meets your eye, just as anything else in the world would be.
        WorldRenderEvents.END_MAIN.register(context -> {
            SpatialView view = active;
            if (view == null || !view.open || !view.placed) return;
            try {
                view.drawWorld(context.matrices().peek(), context.worldState().cameraRenderState.pos);
            } catch (RuntimeException e) {
                dev.maro.Maro.LOGGER.error("The floating window could not be drawn", e);
            }
        });
    }

    private float scale = 1f;
    private MatrixStack.Entry entry;
    private double camX, camY, camZ;

    /** The pointer, over everything on the screen: the crosshair, a dot while it is on something of the menu's. */
    private void draw(DrawContext ctx, float dt) {
        active = this;
        barGlow += ((barHover ? 1f : 0f) - barGlow) * Math.min(1f, dt * 14f);
        dotGrow += ((dotHover ? 1f : 0f) - dotGrow) * Math.min(1f, dt * 14f);
        float p = menu.menuProgress();
        if (p <= 0.002f) return;
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

    /** {@code pixels} screen pixels in menu units, near enough, for edges that stay hairline. */
    private float px(float pixels) {
        return pixels / Math.max(0.05f, unitPixels);
    }

    /** The glass, the menu on it and its edge, bar and dot, as things in the world. */
    private void drawWorld(MatrixStack.Entry entry, Vec3d camera) {
        float p = menu.menuProgress();
        if (p <= 0.002f) return;
        this.entry = entry;
        camX = camera.x;
        camY = camera.y;
        camZ = camera.z;
        scale = 0.9f + 0.1f * p;
        // How many pixels one menu unit covers here, for hairline edges at any distance.
        double dist = Math.max(0.2, Math.sqrt((cx - camX) * (cx - camX) + (cy - camY) * (cy - camY) + (cz - camZ) * (cz - camZ)));
        unitPixels = (float) (paneH / sh * mc.getWindow().getFramebufferHeight() / (2 * dist * tanV));
        float[] win = menu.spatialWindow();

        if (win != null) {
            VertexConsumer glass = BUFFERS.getBuffer(GLASS);
            float x = win[0], y = win[1], w = win[2], h = win[3], r = win[4];
            band(glass, x, y + 3, w, h, r, 0, 18, fade(0x40000000, p), fade(0x58000000, p), 0, 0);
            int top = fade(0xB0262A30, p), bottom = fade(0xC2171A1E, p);
            roundFill(glass, x, y, w, h, r, top, bottom);
            band(glass, x, y, w, h, r, 0, px(1.2f), top, bottom, clear(top), clear(bottom));
            // A faint sheen down from the top edge, as light catching glass.
            roundFill(glass, x, y, w, Math.min(60, h * 0.4f), r, fade(0x16FFFFFF, p), 0x00FFFFFF);
            BUFFERS.draw(GLASS);
        }

        // The menu itself (only from in front: from behind the pane is just glass). One quad: the
        // graphics card keeps the picture true to perspective.
        GpuTextureView view = canvas.view();
        if (view != null && front) {
            CanvasTexture.show(view);
            VertexConsumer menuQuad = BUFFERS.getBuffer(PICTURE);
            textured(menuQuad, 0, 0, 0, 1);
            textured(menuQuad, 0, sh, 0, 0);
            textured(menuQuad, sw, sh, 1, 0);
            textured(menuQuad, sw, 0, 1, 1);
            BUFFERS.draw(PICTURE);
        }

        if (win != null) {
            VertexConsumer lines = BUFFERS.getBuffer(EDGE);
            float x = win[0], y = win[1], w = win[2], h = win[3], r = win[4];
            // The edge: a hairline of light, brightest along the top, fading away on both sides.
            float line = px(1.4f), soft = px(1.1f);
            int lt = fade(0xD2FFFFFF, p), lb = fade(0x46FFFFFF, p);
            band(lines, x, y, w, h, r, -line, 0, lt, lb, lt, lb);
            band(lines, x, y, w, h, r, 0, soft, lt, lb, clear(lt), clear(lb));
            band(lines, x, y, w, h, r, -line, -line - soft * 1.6f, lt, lb, clear(lt), clear(lb));

            // The bar: a soft pill with a shadow, brighter while pointed at, the accent while moving.
            float[] bar = bar(), dot = dot();
            float grow = barGlow * 6f, bh = bar[3] + barGlow;
            float bx = bar[0] - grow, by = bar[1] - barGlow * 0.5f, bw = bar[2] + grow * 2;
            int barColor = fade(moving ? Theme.accent() : ColorUtil.lerp(0xE0E3E6EA, 0xFFFFFFFF, barGlow), p);
            band(lines, bx, by + 1.2f, bw, bh, bh / 2f, 0, 4, fade(0x44000000, p), fade(0x44000000, p), 0, 0);
            roundFill(lines, bx, by, bw, bh, bh / 2f, barColor, barColor);
            band(lines, bx, by, bw, bh, bh / 2f, 0, px(1.1f), barColor, barColor, clear(barColor), clear(barColor));

            // The close dot, red, with a cross while pointed at.
            float rr = dot[2] + dotGrow * 1.6f;
            int red = fade(ColorUtil.lerp(0xFFE5484D, 0xFFFF6B6F, dotGrow), p);
            band(lines, dot[0] - rr, dot[1] - rr + 1, rr * 2, rr * 2, rr, 0, 3, fade(0x40000000, p), fade(0x40000000, p), 0, 0);
            roundFill(lines, dot[0] - rr, dot[1] - rr, rr * 2, rr * 2, rr, red, red);
            band(lines, dot[0] - rr, dot[1] - rr, rr * 2, rr * 2, rr, 0, px(1.1f), red, red, clear(red), clear(red));
            if (dotGrow > 0.05f) {
                float c = rr * 0.42f;
                int cross = fade(ColorUtil.withAlpha(0xFFFFFFFF, Math.round(255 * dotGrow)), p);
                line(lines, dot[0] - c, dot[1] - c, dot[0] + c, dot[1] + c, 0.8f, cross);
                line(lines, dot[0] - c, dot[1] + c, dot[0] + c, dot[1] - c, 0.8f, cross);
            }
            BUFFERS.draw(EDGE);
        }
    }

    /** Menu coordinates to a point in the world, relative to the camera, into {@code out}. */
    private void place(double x, double y, float[] out) {
        double lx = (x / sw - 0.5) * paneW * scale, ly = (0.5 - y / sh) * paneH * scale;
        out[0] = (float) (cx + rx * lx + ux * ly - camX);
        out[1] = (float) (cy + ry * lx + uy * ly - camY);
        out[2] = (float) (cz + rz * lx + uz * ly - camZ);
    }

    private final float[] tmp = new float[3];

    private void colored(VertexConsumer b, double x, double y, int color) {
        place(x, y, tmp);
        b.vertex(entry, tmp[0], tmp[1], tmp[2]).color(color);
    }

    private void textured(VertexConsumer b, double x, double y, float u, float v) {
        place(x, y, tmp);
        b.vertex(entry, tmp[0], tmp[1], tmp[2]).texture(u, v).color(0xFFFFFFFF);
    }

    private static int fade(int color, float p) {
        return ColorUtil.withAlpha(color, Math.round((color >>> 24) * p));
    }

    /** The same colour, fully clear: feathered edges fade to this so they do not darken on the way. */
    private static int clear(int color) {
        return color & 0x00FFFFFF;
    }

    /** The colour between {@code top} and {@code bottom} at height {@code py}. */
    private static int shade(int top, int bottom, float py, float y, float h) {
        float t = Math.max(0f, Math.min(1f, (py - y) / Math.max(1e-3f, h)));
        return ColorUtil.lerp(top, bottom, t);
    }

    /** The points round a rounded rectangle, clockwise from the top left, in menu coordinates. */
    private static float[] outline(float x, float y, float w, float h, float r) {
        r = Math.max(0.01f, Math.min(r, Math.min(w, h) / 2f));
        int seg = 10;
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
    private void roundFill(VertexConsumer b, float x, float y, float w, float h, float r, int top, int bottom) {
        float[] pts = outline(x, y, w, h, r);
        float mxp = x + w / 2f, myp = y + h / 2f;
        int mid = ColorUtil.lerp(top, bottom, 0.5f);
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int c0 = shade(top, bottom, pts[i * 2 + 1], y, h), c1 = shade(top, bottom, pts[j * 2 + 1], y, h);
            // A triangle, as a quad with its last corner twice.
            colored(b, mxp, myp, mid);
            colored(b, pts[i * 2], pts[i * 2 + 1], c0);
            colored(b, pts[j * 2], pts[j * 2 + 1], c1);
            colored(b, pts[j * 2], pts[j * 2 + 1], c1);
        }
    }

    /**
     * A band round a rounded rectangle on the pane, from {@code d0} to {@code d1} out from its edge
     * (negative is inside), coloured top to bottom at each side of the band; a side that is clear
     * fades out, which is how the edges are feathered.
     */
    private void band(VertexConsumer b, float x, float y, float w, float h, float r, float d0, float d1,
                      int top0, int bottom0, int top1, int bottom1) {
        float[] p0 = outline(x - d0, y - d0, w + d0 * 2, h + d0 * 2, Math.max(0.01f, r + d0));
        float[] p1 = outline(x - d1, y - d1, w + d1 * 2, h + d1 * 2, Math.max(0.01f, r + d1));
        int n = p0.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            colored(b, p0[i * 2], p0[i * 2 + 1], shade(top0, bottom0, p0[i * 2 + 1], y, h));
            colored(b, p0[j * 2], p0[j * 2 + 1], shade(top0, bottom0, p0[j * 2 + 1], y, h));
            colored(b, p1[j * 2], p1[j * 2 + 1], shade(top1, bottom1, p1[j * 2 + 1], y, h));
            colored(b, p1[i * 2], p1[i * 2 + 1], shade(top1, bottom1, p1[i * 2 + 1], y, h));
        }
    }

    private void line(VertexConsumer b, float x1, float y1, float x2, float y2, float width, int color) {
        double len = Math.hypot(x2 - x1, y2 - y1);
        if (len < 1e-3) return;
        float nx = (float) (-(y2 - y1) / len * width / 2), ny = (float) ((x2 - x1) / len * width / 2);
        colored(b, x1 + nx, y1 + ny, color);
        colored(b, x2 + nx, y2 + ny, color);
        colored(b, x2 - nx, y2 - ny, color);
        colored(b, x1 - nx, y1 - ny, color);
    }
}
