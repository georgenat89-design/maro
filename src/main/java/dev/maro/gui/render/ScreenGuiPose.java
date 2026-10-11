package dev.maro.gui.render;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A fixed world plane, projected and picked with the current game's camera. */
public final class ScreenGuiPose {
    private final Matrix3f forward = new Matrix3f(), inverse = new Matrix3f();
    private final Quaternionf rotation = new Quaternionf(), viewRotation = new Quaternionf();
    private final Vector3f axisX = new Vector3f(), axisY = new Vector3f(), corner = new Vector3f();
    private final Vector3f cameraX = new Vector3f(), cameraY = new Vector3f(), cameraCorner = new Vector3f();
    private Vec3d centre;
    private float width = 1, height = 1, layoutScale = 1, offsetX, offsetY;
    private float planeWidth, planeHeight;
    private boolean active, visible;

    public void layout(float scale, float x, float y) { layoutScale = scale; offsetX = x; offsetY = y; }

    public void update(int width, int height, boolean enabled) {
        this.width = Math.max(1, width); this.height = Math.max(1, height);
        active = enabled;
        if (!enabled) { centre = null; visible = false; forward.identity(); inverse.identity(); }
    }

    /** Capture the placement once. Camera translation and rotation never move that placement. */
    public void view(Camera camera, float fov, float distance) {
        if (!active) return;
        float halfFov = (float) Math.tan(Math.toRadians(fov * .5));
        if (centre == null) {
            rotation.set(camera.getRotation());
            var ahead = rotation.transform(new Vector3f(0, 0, -distance));
            centre = camera.getCameraPos().add(ahead.x, ahead.y, ahead.z);
            planeHeight = 2 * halfFov * distance * .88f;
            planeWidth = planeHeight * width / height;
            rotation.transform(new Vector3f(planeWidth, 0, 0), axisX);
            rotation.transform(new Vector3f(0, -planeHeight, 0), axisY);
        }
        viewRotation.set(camera.getRotation()).conjugate();
        viewRotation.transform(axisX, cameraX); viewRotation.transform(axisY, cameraY);
        corner.set((float) (centre.x - camera.getCameraPos().x), (float) (centre.y - camera.getCameraPos().y), (float) (centre.z - camera.getCameraPos().z))
                .sub(axisX.x * .5f, axisX.y * .5f, axisX.z * .5f).sub(axisY.x * .5f, axisY.y * .5f, axisY.z * .5f);
        viewRotation.transform(corner, cameraCorner);
        float fx = 1 / (2 * width / height * halfFov), fy = 1 / (2 * halfFov);
        forward.set(-.5f * cameraX.z + fx * cameraX.x, -.5f * cameraX.z - fy * cameraX.y, -cameraX.z,
                -.5f * cameraY.z + fx * cameraY.x, -.5f * cameraY.z - fy * cameraY.y, -cameraY.z,
                -.5f * cameraCorner.z + fx * cameraCorner.x, -.5f * cameraCorner.z - fy * cameraCorner.y, -cameraCorner.z);
        // A plane viewed edge-on has no usable area. Behind it, do not show a mirrored menu.
        var normal = rotation.transform(new Vector3f(0, 0, 1));
        double side = normal.x * (camera.getCameraPos().x - centre.x) + normal.y * (camera.getCameraPos().y - centre.y) + normal.z * (camera.getCameraPos().z - centre.z);
        visible = side > .03 && Math.abs(forward.determinant()) > 1e-7f;
        if (visible) inverse.set(forward).invert();
        else inverse.zero();
    }

    private double[] map(Matrix3f matrix, double x, double y) {
        var v = matrix.transform(new Vector3f((float) (x / width), (float) (y / height), 1));
        if (Math.abs(v.z) < 1e-7 || !Float.isFinite(v.z)) return new double[] {Double.NaN, Double.NaN};
        return new double[] {v.x / v.z * width, v.y / v.z * height};
    }
    public double[] project(double x, double y) { return map(forward, x * layoutScale + offsetX, y * layoutScale + offsetY); }
    public double[] unproject(double x, double y) {
        if (active && !visible) return new double[] {Double.NaN, Double.NaN};
        double[] p = map(inverse, x, y);
        // Reject the opposite ray direction even if the homography has a mathematical inverse.
        if (active) {
            var depth = inverse.transform(new Vector3f((float) (x / width), (float) (y / height), 1));
            if (depth.z <= 0) return new double[] {Double.NaN, Double.NaN};
        }
        return new double[] {(p[0] - offsetX) / layoutScale, (p[1] - offsetY) / layoutScale};
    }
    public Vec3d worldPoint(double x, double y) {
        if (centre == null) return null;
        double u = (x * layoutScale + offsetX) / width - .5, v = (y * layoutScale + offsetY) / height - .5;
        return centre.add(axisX.x * u + axisY.x * v, axisX.y * u + axisY.y * v, axisX.z * u + axisY.z * v);
    }
    public Matrix3f inverse() { return inverse; }
    public boolean active() { return active; }
    public boolean visible() { return visible; }
    public Vec3d centre() { return centre; }
}
