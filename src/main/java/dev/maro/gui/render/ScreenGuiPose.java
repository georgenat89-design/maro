package dev.maro.gui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4d;

/** One upright world anchor. Only its view/projection and texture rasterization change. */
public final class ScreenGuiPose {
    private final Matrix3d forward = new Matrix3d(), inverseCpu = new Matrix3d();
    private final Matrix3f inverse = new Matrix3f();
    private final Matrix4f view = new Matrix4f(), projection = new Matrix4f();
    private final Matrix4d viewProjection = new Matrix4d(), inverseViewProjection = new Matrix4d();
    private final Vector4d clipX = new Vector4d(), clipY = new Vector4d(), clipCorner = new Vector4d(), clipTest = new Vector4d();
    private final Vector4d rayNear = new Vector4d();
    private final Vector3f clipDepth = new Vector3f();
    private Vec3d centre, anchorX, anchorY, normal, eye;
    private Object world;
    private float width = 1, height = 1, layoutScale = 1, offsetX, offsetY;
    private float canvasWidth = 1080, canvasHeight = 480, planeWidth, planeHeight;
    private float lastFov = 70, lastDistance = 3;
    private boolean active, visible, back;

    /** Logical canvas is reserved once, including settings and the optional preview column. */
    public void canvas(float width, float height) {
        if (centre == null) { canvasWidth = Math.max(1, width); canvasHeight = Math.max(1, height); }
    }
    public void layout(float scale, float x, float y) {
        if (layoutScale == scale && offsetX == x && offsetY == y) return;
        layoutScale = Math.max(.00001f, scale); offsetX = x; offsetY = y;
        if (active && centre != null) rebuild();
    }
    public void update(int width, int height, boolean enabled) {
        boolean resized = this.width != width || this.height != height;
        this.width = Math.max(1, width); this.height = Math.max(1, height);
        active = enabled;
        if (!enabled) reset();
        else if (resized && centre != null) rebuild();
    }
    private void reset() {
        centre = anchorX = anchorY = normal = eye = null; world = null;
        visible = back = false; forward.identity(); inverseCpu.identity(); inverse.identity(); clipDepth.zero();
    }
    private void place(Camera camera) {
        var client = MinecraftClient.getInstance();
        world = client.world;
        // Use yaw only: the panel is vertical even when opened while looking up/down.
        double yaw = Math.toRadians(camera.getYaw()), distance = Math.max(.1, lastDistance);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        eye = camera.getCameraPos();
        centre = eye.add(-sin * distance, 0, cos * distance);
        double aspect = canvasWidth / canvasHeight;
        planeHeight = (float)(2 * Math.tan(Math.toRadians(lastFov * .5)) * distance * .88
                * Math.min(1, width / height / aspect));
        planeWidth = (float)(planeHeight * aspect);
        anchorX = new Vec3d(-cos * planeWidth, 0, -sin * planeWidth);
        anchorY = new Vec3d(0, -planeHeight, 0);
        normal = new Vec3d(sin, 0, -cos);
    }
    /** Initial placement uses the camera once; subsequent camera motion only changes the view. */
    public void view(Camera camera, float fov, float distance) {
        if (!active) return;
        lastFov = fov; lastDistance = distance;
        if (centre == null || world != MinecraftClient.getInstance().world) place(camera);
        eye = camera.getCameraPos();
        view.rotation(new Quaternionf(camera.getRotation()).conjugate());
        projection.identity().perspective((float)Math.toRadians(fov), width / height, .05f, 4096);
        rebuild();
    }
    /** Exact world matrices include the game's interpolated view, hurt tilt, bob and distortion. */
    public void worldProjection(Camera camera, Matrix4fc view, Matrix4fc projection) {
        if (!active) return;
        if (centre == null || world != MinecraftClient.getInstance().world) place(camera);
        eye = camera.getCameraPos(); this.view.set(view); this.projection.set(projection); rebuild();
    }
    private void rebuild() {
        if (centre == null || eye == null) return;
        double side = normal.dotProduct(eye.subtract(centre));
        back = side < 0;
        // The fixed world basis does not turn. The back uses readable, horizontally folded UVs.
        double u0 = -offsetX / (layoutScale * canvasWidth), v0 = -offsetY / (layoutScale * canvasHeight);
        double us = width / (layoutScale * canvasWidth), vs = height / (layoutScale * canvasHeight);
        if (back) { u0 = 1 - u0; us = -us; }
        viewProjection.set(projection).mul(view);
        viewProjection.invert(inverseViewProjection);
        clipX.set(anchorX.x * us, anchorX.y * us, anchorX.z * us, 0);
        clipY.set(anchorY.x * vs, anchorY.y * vs, anchorY.z * vs, 0);
        // Subtract in double precision before conversion/projection, including near world borders.
        clipCorner.set(centre.x - eye.x + anchorX.x * (u0 - .5) + anchorY.x * (v0 - .5),
                centre.y - eye.y + anchorX.y * (u0 - .5) + anchorY.y * (v0 - .5),
                centre.z - eye.z + anchorX.z * (u0 - .5) + anchorY.z * (v0 - .5), 1);
        viewProjection.transform(clipX); viewProjection.transform(clipY); viewProjection.transform(clipCorner);
        forward.set((clipX.w + clipX.x) * .5, (clipX.w - clipX.y) * .5, clipX.w,
                (clipY.w + clipY.x) * .5, (clipY.w - clipY.y) * .5, clipY.w,
                (clipCorner.w + clipCorner.x) * .5, (clipCorner.w - clipCorner.y) * .5, clipCorner.w);
        clipDepth.set((float)clipX.z, (float)clipY.z, (float)clipCorner.z);
        double determinant = forward.determinant();
        visible = Math.abs(side) > .005 && Double.isFinite(determinant) && Math.abs(determinant) > 1e-10 && inFrustum();
        if (visible) {
            inverseCpu.set(forward).invert();
            inverse.set((float)inverseCpu.m00(), (float)inverseCpu.m01(), (float)inverseCpu.m02(),
                    (float)inverseCpu.m10(), (float)inverseCpu.m11(), (float)inverseCpu.m12(),
                    (float)inverseCpu.m20(), (float)inverseCpu.m21(), (float)inverseCpu.m22());
        }
        else { inverseCpu.zero(); inverse.zero(); }
    }
    private boolean inFrustum() {
        int common = 63;
        for (int corner = 0; corner < 4; corner++) {
            double x = (corner & 1) == 0 ? -.5 : .5, y = (corner & 2) == 0 ? -.5 : .5;
            clipTest.set(centre.x - eye.x + anchorX.x * x + anchorY.x * y,
                    centre.y - eye.y + anchorX.y * x + anchorY.y * y,
                    centre.z - eye.z + anchorX.z * x + anchorY.z * y, 1);
            viewProjection.transform(clipTest);
            int outside = (clipTest.x < -clipTest.w ? 1 : 0) | (clipTest.x > clipTest.w ? 2 : 0)
                    | (clipTest.y < -clipTest.w ? 4 : 0) | (clipTest.y > clipTest.w ? 8 : 0)
                    | (clipTest.z < -clipTest.w ? 16 : 0) | (clipTest.z > clipTest.w ? 32 : 0);
            common &= outside;
        }
        return common == 0;
    }
    public double[] project(double x, double y) {
        double sx = (x * layoutScale + offsetX) / width, sy = (y * layoutScale + offsetY) / height;
        double z = forward.m02() * sx + forward.m12() * sy + forward.m22();
        if (!Double.isFinite(z) || active && z <= .00001) return miss();
        return new double[]{(forward.m00() * sx + forward.m10() * sy + forward.m20()) / z * width,
                (forward.m01() * sx + forward.m11() * sy + forward.m21()) / z * height};
    }
    /** Plane coordinates beyond finite edges for a latched drag, still blocked by world geometry. */
    public double[] unprojectDrag(double x, double y) { return pick(x, y, false); }
    /** Actual cursor/crosshair ray; no hover, click or scroll can pass through a world block. */
    public double[] unproject(double x, double y) { return pick(x, y, true); }
    private double[] pick(double x, double y, boolean bounded) {
        if (!active) return new double[]{(x - offsetX) / layoutScale, (y - offsetY) / layoutScale};
        if (!visible) return miss();
        double nx = x / width, ny = y / height;
        double z = inverseCpu.m02() * nx + inverseCpu.m12() * ny + inverseCpu.m22();
        if (!Double.isFinite(z) || z <= .00001) return miss();
        double u = (inverseCpu.m00() * nx + inverseCpu.m10() * ny + inverseCpu.m20()) / z;
        double v = (inverseCpu.m01() * nx + inverseCpu.m11() * ny + inverseCpu.m21()) / z;
        double depth = (clipX.z * u + clipY.z * v + clipCorner.z) * z;
        if (!Double.isFinite(depth) || depth < -1 || depth > 1) return miss();
        double px = (u * width - offsetX) / layoutScale, py = (v * height - offsetY) / layoutScale;
        if (!Double.isFinite(px) || !Double.isFinite(py)) return miss();
        if (bounded && (px < 0 || py < 0 || px > canvasWidth || py > canvasHeight)) return miss();
        if (obstructed(px, py, nx, ny)) return miss();
        return new double[]{px, py};
    }
    private boolean obstructed(double x, double y, double screenX, double screenY) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.getCameraEntity() == null || eye == null) return true;
        Vec3d point = worldPoint(x, y);
        // The rendered near ray also includes projection translations from view bob/hurt effects.
        inverseViewProjection.transform(rayNear.set(screenX * 2 - 1, 1 - screenY * 2, -1, 1));
        if (!Double.isFinite(rayNear.w) || Math.abs(rayNear.w) < 1e-10) return true;
        Vec3d start = eye.add(rayNear.x / rayNear.w, rayNear.y / rayNear.w, rayNear.z / rayNear.w);
        var hit = client.world.raycast(new RaycastContext(start, point, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, client.getCameraEntity()));
        return hit.getType() != HitResult.Type.MISS && start.squaredDistanceTo(hit.getPos()) + .0001 < start.squaredDistanceTo(point);
    }
    public Vec3d worldPoint(double x, double y) {
        if (centre == null) return null;
        double u = x / canvasWidth, v = y / canvasHeight;
        if (back) u = 1 - u;
        return centre.add(anchorX.x * (u - .5) + anchorY.x * (v - .5),
                anchorX.y * (u - .5) + anchorY.y * (v - .5), anchorX.z * (u - .5) + anchorY.z * (v - .5));
    }
    private static double[] miss() { return new double[]{Double.NaN, Double.NaN}; }
    public Matrix3f inverse() { return inverse; }
    public Vector3f clipDepthRow() { return clipDepth; }
    public boolean active() { return active; }
    public boolean visible() { return visible; }
    public boolean backFacing() { return back; }
    public Vec3d centre() { return centre; }
    public Vec3d anchorX() { return anchorX; }
    public Vec3d anchorY() { return anchorY; }
    public float planeWidth() { return planeWidth; }
    public float planeHeight() { return planeHeight; }
}
