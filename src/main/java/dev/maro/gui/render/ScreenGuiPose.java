package dev.maro.gui.render;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** A plane fixed in front of the opening view; looking around projects it like the world. */
public final class ScreenGuiPose {
    private final Matrix3f forward = new Matrix3f();
    private final Matrix3f inverse = new Matrix3f();
    private float width = 1, height = 1;
    private float yaw, pitch;
    private long last;
    private boolean active;
    private float layoutScale = 1, offsetX, offsetY;
    private final Quaternionf anchor = new Quaternionf();
    private boolean anchored;

    public void layout(float scale, float x, float y) { layoutScale = scale; offsetX = x; offsetY = y; }

    public void update(int width, int height, double mouseX, double mouseY, boolean enabled, boolean dragging, float tilt) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        active = enabled;
        if (!enabled) { yaw = pitch = 0; forward.identity(); inverse.identity(); last = 0; anchored = false; return; }
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(.1f, (now - last) / 1e9f);
        last = now;
        if (!dragging) {
            float tx = (float) Math.max(-1, Math.min(1, mouseX / this.width * 2 - 1));
            float ty = (float) Math.max(-1, Math.min(1, mouseY / this.height * 2 - 1));
            float k = 1 - (float) Math.exp(-12 * dt);
            yaw += (tx * tilt - yaw) * k;
            pitch += (ty * tilt * .7f - pitch) * k;
        }
    }

    /** Called from the camera update, so the menu and world use this frame's exact rotation. */
    public void view(Quaternionfc openingView, Quaternionfc currentView, float fov) {
        if (!active) return;
        if (!anchored) { anchor.set(openingView); anchored = true; }
        Quaternionf relative = new Quaternionf(currentView).conjugate().mul(anchor);
        float aspect = width / height;
        float halfFov = (float) Math.tan(Math.toRadians(fov * .5f));
        float planeX = 2 * aspect * halfFov * .88f, planeY = 2 * halfFov * .88f;
        Vector3f x = relative.transform(new Vector3f(planeX, 0, 0));
        Vector3f y = relative.transform(new Vector3f(0, -planeY, 0));
        Vector3f c = relative.transform(new Vector3f(-planeX / 2, planeY / 2, -1));
        float fx = 1 / (2 * aspect * halfFov), fy = 1 / (2 * halfFov);
        forward.set(-.5f * x.z + fx * x.x, -.5f * x.z - fy * x.y, -x.z,
                -.5f * y.z + fx * y.x, -.5f * y.z - fy * y.y, -y.z,
                -.5f * c.z + fx * c.x, -.5f * c.z - fy * c.y, -c.z);
        inverse.set(forward).invert();
    }

    private double[] map(Matrix3f matrix, double x, double y) {
        Vector3f v = matrix.transform(new Vector3f((float) (x / width), (float) (y / height), 1));
        return new double[] {v.x / v.z * width, v.y / v.z * height};
    }

    public double[] project(double x, double y) { return map(forward, x * layoutScale + offsetX, y * layoutScale + offsetY); }
    public double[] unproject(double x, double y) {
        double[] p = map(inverse, x, y);
        return new double[] {(p[0] - offsetX) / layoutScale, (p[1] - offsetY) / layoutScale};
    }
    public Matrix3f inverse() { return inverse; }
    public boolean active() { return active; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
