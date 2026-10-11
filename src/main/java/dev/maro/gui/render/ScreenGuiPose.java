package dev.maro.gui.render;

import org.joml.Matrix3f;
import org.joml.Vector3f;

/** The same projective plane drives drawing and pointer picking. Coordinates are GUI units. */
public final class ScreenGuiPose {
    private final Matrix3f forward = new Matrix3f();
    private final Matrix3f inverse = new Matrix3f();
    private float width = 1, height = 1;
    private float yaw, pitch;
    private long last;
    private boolean active;
    private float layoutScale = 1, offsetX, offsetY;

    public void layout(float scale, float x, float y) { layoutScale = scale; offsetX = x; offsetY = y; }

    public void update(int width, int height, double mouseX, double mouseY, boolean enabled, boolean dragging, float tilt) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        active = enabled;
        if (!enabled) { yaw = pitch = 0; forward.identity(); inverse.identity(); last = 0; return; }
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(.1f, (now - last) / 1e9f);
        last = now;
        if (!dragging) {
            float tx = (float) Math.max(-1, Math.min(1, mouseX / this.width * 2 - 1));
            float ty = (float) Math.max(-1, Math.min(1, mouseY / this.height * 2 - 1));
            float k = 1 - (float) Math.exp(-12 * dt);
            yaw += (tx * tilt - yaw) * k;
            pitch += (-ty * tilt * .7f - pitch) * k;
        }
        float sy = (float) Math.sin(Math.toRadians(yaw)), cy = (float) Math.cos(Math.toRadians(yaw));
        float sp = (float) Math.sin(Math.toRadians(pitch)), cp = (float) Math.cos(Math.toRadians(pitch));
        float aspect = this.width / this.height, scale = .88f, distance = 4;
        float a = -2 * aspect * sy / distance, b = 2 * cy * sp / distance, c = 1 - a / 2 - b / 2;
        forward.set(.5f * a + scale * cy, .5f * a, a,
                .5f * b + scale * sy * sp / aspect, .5f * b + scale * cp, b,
                .5f * c - scale * cy / 2 - scale * sy * sp / (2 * aspect), .5f * c - scale * cp / 2, c);
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
