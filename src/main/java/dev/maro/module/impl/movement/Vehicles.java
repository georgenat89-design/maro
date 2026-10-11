package dev.maro.module.impl.movement;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.esp.Renderer3D;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Vehicles: drive a car, sports car, truck or go-kart that only you can see.
 *
 * <p>Drive moves you for real, at sprinting pace, steered like a car: W and S drive and reverse, A
 * and D turn the wheel, and you hop up single blocks; the server only ever sees you running, so it
 * is safe anywhere. Free Drive leaves you standing where you are and drives a car of its own as fast
 * as you like, the camera following it, until you get out (sneak) and the view goes back to you.
 * Either way you sit in the driver's seat, the wheels turn and spin, the lights come on at night and
 * the engine note rises with your speed. Nothing about it is sent to the server.
 */
public class Vehicles extends Module {
    private static Vehicles instance;

    private enum Model {CAR, SPORTS, TRUCK, KART}

    private final ModeSetting mode = add(new ModeSetting("Mode",
            "Drive: you move for real at running pace, steered like a car (safe on servers). Free Drive: you stay put and a car of its own drives anywhere, fast",
            "Drive", "Drive", "Free Drive"));
    private final ModeSetting vehicle = add(new ModeSetting("Vehicle", "What you drive", "Car", "Car", "Sports Car", "Truck", "Kart"));
    private final ColorSetting color = add(new ColorSetting("Color", "The paint", 0xFFD7263D));
    private final NumberSetting topSpeed = add(new NumberSetting("Top Speed", "Free Drive's top speed (sprint boosts it)", 28, 8, 80, 1)
            .suffix(" m/s").visible(() -> mode.is("Free Drive")));
    private final NumberSetting acceleration = add(new NumberSetting("Acceleration", "How quickly Free Drive gets going", 9, 2, 30, 0.5)
            .suffix(" m/s²").visible(() -> mode.is("Free Drive")));
    private final NumberSetting steering = add(new NumberSetting("Steering", "How fast the car turns", 110, 30, 220, 5).suffix("°/s"));
    private final ModeSetting camera = add(new ModeSetting("Camera", "Behind the car, or from the driver's seat", "Chase", "Chase", "Driver"));
    private final NumberSetting distance = add(new NumberSetting("Camera Distance", "How far behind the chase camera sits", 6.5, 3, 14, 0.5)
            .visible(() -> camera.is("Chase")));
    private final BooleanSetting headlights = add(new BooleanSetting("Headlights", "Lit lamps, and beams ahead in the dark", true));
    private final BooleanSetting engineSound = add(new BooleanSetting("Engine Sound", "An engine note that rises with your speed", true));
    private final NumberSetting volume = add(new NumberSetting("Volume", "How loud the engine is", 60, 0, 100, 1).suffix("%").visible(engineSound::get));
    private final BooleanSetting speedometer = add(new BooleanSetting("Speedometer", "Your speed over the hotbar", true));
    private final BooleanSetting seated = add(new BooleanSetting("Seated Driver", "You, sitting in the driver's seat", true));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Vehicle", mode, vehicle, color, topSpeed, acceleration, steering),
            SettingSection.of("View", camera, distance, headlights, speedometer, seated),
            SettingSection.of("Sound", engineSound, volume));

    // ---- the drive
    private boolean free;
    private double x, y, z, lastX, lastY, lastZ, vy;
    private float yaw, lastYaw, bodyPitch, lastBodyPitch;
    private double speed;
    private float steer, lastSteer, spin, lastSpin;
    private float orbitYaw, orbitPitch;
    private long lastLook;
    private boolean blockedAhead;
    private Engine engine;

    public Vehicles() {
        super("Vehicles", "Drive a car, sports car, truck or kart only you can see: for real at running pace, or Free Drive anywhere", Category.MOVEMENT);
        instance = this;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    /** The module while you are driving. */
    private static Vehicles driving() {
        Vehicles v = instance;
        return v != null && v.isEnabled() && inGame() ? v : null;
    }

    @Override
    protected void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        free = mode.is("Free Drive");
        x = lastX = mc.player.getX();
        y = lastY = mc.player.getY();
        z = lastZ = mc.player.getZ();
        yaw = lastYaw = mc.player.getYaw();
        speed = vy = 0;
        steer = lastSteer = 0;
        orbitYaw = orbitPitch = 0;
        bodyPitch = lastBodyPitch = 0;
        if (engineSound.get()) {
            engine = new Engine(this);
            mc.getSoundManager().play(engine);
        }
    }

    @Override
    protected void onDisable() {
        if (engine != null) engine.stop();
        engine = null;
        speed = 0;
    }

    private Model model() {
        return switch (vehicle.get()) {
            case "Sports Car" -> Model.SPORTS;
            case "Truck" -> Model.TRUCK;
            case "Kart" -> Model.KART;
            default -> Model.CAR;
        };
    }

    // ---- driving ----------------------------------------------------------------------------

    @Override
    public void onTick() {
        if (!inGame()) return;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastSteer = steer;
        lastSpin = spin;
        lastBodyPitch = bodyPitch;
        boolean keys = mc.currentScreen == null;
        if (keys && mc.options.sneakKey.isPressed()) {
            // Getting out.
            setEnabled(false);
            return;
        }
        boolean forward = keys && mc.options.forwardKey.isPressed(), back = keys && mc.options.backKey.isPressed();
        boolean left = keys && mc.options.leftKey.isPressed(), right = keys && mc.options.rightKey.isPressed();
        boolean brake = keys && mc.options.jumpKey.isPressed(), boost = keys && mc.options.sprintKey.isPressed();
        float wheel = (right ? 1 : 0) - (left ? 1 : 0);
        steer += (wheel - steer) * 0.35f;
        double dt = 0.05;

        if (free) freeDrive(forward, back, brake, boost, dt);
        else drive(dt);

        spin += (float) (speed * dt / radius());
        // The camera drifts back behind the car once you stop looking round.
        if (System.currentTimeMillis() - lastLook > 1500) {
            orbitYaw *= 0.85f;
            orbitPitch *= 0.85f;
        }
    }

    /** Free Drive: a car of its own, with its own speed, over the ground as it is. */
    private void freeDrive(boolean forward, boolean back, boolean brake, boolean boost, double dt) {
        double top = topSpeed.get() * (boost ? 1.35 : 1);
        double acc = acceleration.get();
        if (forward) speed += acc * dt * (speed < 0 ? 3 : 1);
        else if (back) speed -= (speed > 0 ? acc * 3 : acc * 0.7) * dt;
        else speed *= 0.985;
        if (brake) speed *= 0.88;
        if (Math.abs(speed) < 0.02 && !forward && !back) speed = 0;
        speed = MathHelper.clamp(speed, -top * 0.35, top);
        turn(dt, brake ? 1.5f : 1f);

        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad) * speed * dt, dz = Math.cos(rad) * speed * dt;
        double ground = groundAt(x + dx, z + dz, y);
        blockedAhead = Double.isNaN(ground) || ground > y + 1.05 || !clear(x + dx, z + dz, Math.max(ground, y));
        if (blockedAhead) {
            // A wall, or the end of the world the server has sent: stop, with a little bounce.
            speed *= -0.2;
            ground = groundAt(x, z, y);
        } else {
            x += dx;
            z += dz;
        }
        if (Double.isNaN(ground)) return;
        if (ground >= y - 0.02) {
            // Up a step smoothly, or along the ground.
            y = Math.min(ground, y + 0.45);
            vy = 0;
        } else {
            vy -= 22 * dt;
            y += vy * dt;
            if (y <= ground) {
                y = ground;
                vy = 0;
            }
        }
        // The body tilts with the ground under its front and back wheels.
        double half = length() * 0.4;
        double front = groundAt(x - Math.sin(rad) * half, z + Math.cos(rad) * half, y);
        double rear = groundAt(x + Math.sin(rad) * half, z - Math.cos(rad) * half, y);
        float want = Double.isNaN(front) || Double.isNaN(rear) ? 0 : (float) Math.atan2(MathHelper.clamp(front - rear, -1, 1), half * 2);
        bodyPitch += (want - bodyPitch) * 0.3f;
    }

    /** Drive: you, moving as the game moves you, turned like a car. */
    private void drive(double dt) {
        Vec3d v = mc.player.getVelocity();
        double rad = Math.toRadians(yaw);
        speed = (-Math.sin(rad) * v.x + Math.cos(rad) * v.z) * 20;
        turn(dt, 1f);
        mc.player.setYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.player.setBodyYaw(yaw);
        if (mc.options.forwardKey.isPressed() && mc.currentScreen == null) mc.player.setSprinting(true);
        x = mc.player.getX();
        y = mc.player.getY();
        z = mc.player.getZ();
    }

    /** Turns with the wheel, more at speed, the other way in reverse. */
    private void turn(double dt, float sharpness) {
        double grip = MathHelper.clamp(Math.abs(speed) / 4.0, 0, 1);
        double dir = speed < -0.05 ? -1 : 1;
        yaw += (float) (steer * steering.get() * dt * grip * dir * sharpness);
    }

    /** The top of the ground at a spot, looking down from a little over this height; NaN where the world is not loaded. */
    private double groundAt(double gx, double gz, double fromY) {
        int bx = MathHelper.floor(gx), bz = MathHelper.floor(gz);
        if (!mc.world.isChunkLoaded(bx >> 4, bz >> 4)) return Double.NaN;
        int top = MathHelper.floor(fromY + 1.2), bottom = Math.max(mc.world.getBottomY(), MathHelper.floor(fromY) - 12);
        for (int by = top; by >= bottom; by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            var shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
            if (shape.isEmpty()) continue;
            double surface = by + shape.getMax(Direction.Axis.Y);
            if (surface <= fromY + 1.2) return surface;
        }
        return fromY - 12;
    }

    /** Whether there is head room over a spot for the car. */
    private boolean clear(double gx, double gz, double floor) {
        int bx = MathHelper.floor(gx), bz = MathHelper.floor(gz);
        for (double h = 0.3; h <= 1.3; h += 0.5) {
            BlockPos pos = BlockPos.ofFloored(bx + 0.5, floor + h, bz + 0.5);
            if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) return false;
        }
        return true;
    }

    // ---- the car's measurements --------------------------------------------------------------

    private double radius() {
        return switch (model()) {
            case TRUCK -> 0.46;
            case KART -> 0.22;
            case SPORTS -> 0.33;
            default -> 0.35;
        };
    }

    private double length() {
        return switch (model()) {
            case TRUCK -> 4.4;
            case KART -> 2.0;
            case SPORTS -> 3.9;
            default -> 3.7;
        };
    }

    /** Where you sit, from the car's middle on the ground: right, up (your feet, legs out in front), forward. */
    private double[] seat() {
        return switch (model()) {
            case TRUCK -> new double[] {-0.45, 0.5, 0.95};
            case KART -> new double[] {0, -0.4, -0.45};
            case SPORTS -> new double[] {-0.36, -0.28, -0.3};
            default -> new double[] {-0.38, -0.18, -0.2};
        };
    }

    // ---- where things are this frame ---------------------------------------------------------------

    private Vec3d carPos(float delta) {
        if (!free) return mc.player.getLerpedPos(delta);
        return new Vec3d(MathHelper.lerp(delta, lastX, x), MathHelper.lerp(delta, lastY, y), MathHelper.lerp(delta, lastZ, z));
    }

    private float carYaw(float delta) {
        return MathHelper.lerp(delta, lastYaw, yaw);
    }

    /** The car's right, up and forward, tilted with the ground. */
    private Vec3d[] basis(float delta) {
        double rad = Math.toRadians(carYaw(delta));
        float p = MathHelper.lerp(delta, lastBodyPitch, bodyPitch);
        Vec3d f = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        Vec3d r = new Vec3d(-Math.cos(rad), 0, -Math.sin(rad));
        Vec3d u = new Vec3d(0, 1, 0);
        Vec3d ft = f.multiply(Math.cos(p)).add(u.multiply(Math.sin(p)));
        Vec3d ut = u.multiply(Math.cos(p)).subtract(f.multiply(Math.sin(p)));
        return new Vec3d[] {r, ut, ft};
    }

    private static Vec3d at(Vec3d origin, Vec3d[] b, double lx, double ly, double lz) {
        return origin.add(b[0].multiply(lx)).add(b[1].multiply(ly)).add(b[2].multiply(lz));
    }

    // ---- hooks for the camera, input, mouse and the player's model -----------------------------------

    /** The camera this frame, {x, y, z, yaw, pitch}, or null when not driving. */
    public static double[] cameraFrame(float delta) {
        Vehicles v = driving();
        if (v == null) return null;
        Vec3d pos = v.carPos(delta);
        Vec3d[] b = v.basis(delta);
        float carYaw = v.carYaw(delta);
        if (v.camera.is("Driver")) {
            double[] s = v.seat();
            Vec3d eye = at(pos, b, s[0], s[1] + 1.55, s[2]);
            return new double[] {eye.x, eye.y, eye.z, carYaw + v.orbitYaw, MathHelper.clamp(v.orbitPitch + 4, -80, 80)};
        }
        double around = Math.toRadians(carYaw + v.orbitYaw);
        double lift = MathHelper.clamp(v.orbitPitch + 14, -10, 75);
        double dist = v.distance.get() * (v.model() == Model.TRUCK ? 1.15 : 1);
        Vec3d target = pos.add(0, v.model() == Model.TRUCK ? 2.1 : 1.6, 0);
        Vec3d back = new Vec3d(Math.sin(around), 0, -Math.cos(around)).multiply(Math.cos(Math.toRadians(lift)) * dist);
        Vec3d want = target.add(back).add(0, Math.sin(Math.toRadians(lift)) * dist, 0);
        // Kept out of walls: pulled in to just short of whatever is between the car and the camera.
        BlockHitResult hit = mc.world.raycast(new RaycastContext(target, want, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) want = hit.getPos().add(target.subtract(hit.getPos()).normalize().multiply(0.3));
        Vec3d look = target.subtract(want);
        double camYaw = Math.toDegrees(Math.atan2(-look.x, look.z));
        double camPitch = Math.toDegrees(-Math.atan2(look.y, Math.hypot(look.x, look.z)));
        return new double[] {want.x, want.y, want.z, camYaw, camPitch};
    }

    /** From the driver's seat: your own model is left out so it does not fill the view. */
    public static boolean driverView() {
        Vehicles v = driving();
        return v != null && v.camera.is("Driver");
    }

    /** The mouse looks round the car rather than turning you. */
    public static boolean look(double byX, double byY) {
        Vehicles v = driving();
        if (v == null) return false;
        v.orbitYaw = MathHelper.wrapDegrees(v.orbitYaw + (float) byX * 0.15f);
        v.orbitPitch = MathHelper.clamp(v.orbitPitch + (float) byY * 0.15f, -60, 60);
        v.lastLook = System.currentTimeMillis();
        return true;
    }

    /** Free Drive: the player stands still, so the movement keys go to the car only. */
    public static boolean freeDriving() {
        Vehicles v = driving();
        return v != null && v.free;
    }

    /** Drive: the movement keys drive you like a car. */
    public static boolean drivesPlayer() {
        Vehicles v = driving();
        return v != null && !v.free;
    }

    /** Drive: hop up a block when driving into one. */
    public static boolean hop() {
        Vehicles v = driving();
        return v != null && !v.free && mc.player.horizontalCollision && mc.player.isOnGround()
                && (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed());
    }

    /** No hands floating in front of the camera while driving. */
    public static boolean hidesHands() {
        return driving() != null;
    }

    /** Whether your own model is drawn sitting in the car (and so must be drawn wherever the car is). */
    public static boolean seats(Entity entity) {
        Vehicles v = driving();
        return v != null && v.seated.get() && entity == mc.player;
    }

    /** How far your model is moved from where you are to sit in the car, this frame. */
    public static Vec3d seatOffset(float delta) {
        Vehicles v = driving();
        if (v == null) return Vec3d.ZERO;
        double[] s = v.seat();
        Vec3d seat = at(v.carPos(delta), v.basis(delta), s[0], s[1], s[2]);
        return seat.subtract(mc.player.getLerpedPos(delta));
    }

    /** The way your model faces in the car. */
    public static float seatYaw(float delta) {
        Vehicles v = driving();
        return v == null ? 0 : v.carYaw(delta);
    }

    // ---- drawing the car --------------------------------------------------------------------------

    private record Face(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int argb, double depth) {
    }

    /** Draws the car into the world, faces sorted back to front so it reads solid. */
    public void render(Renderer3D renderer, float delta) {
        if (!inGame()) return;
        Vec3d pos = carPos(delta);
        Vec3d[] b = basis(delta);
        Vec3d cam = renderer.camera();
        float light = lightAt(pos);
        float st = MathHelper.lerp(delta, lastSteer, steer) * 0.45f;
        float sp = MathHelper.lerp(delta, lastSpin, spin);
        int paint = color.get() | 0xFF000000;
        List<Face> faces = new ArrayList<>();
        Parts parts = new Parts(faces, pos, b, cam, light);

        switch (model()) {
            case CAR -> car(parts, paint, st, sp);
            case SPORTS -> sports(parts, paint, st, sp);
            case TRUCK -> truck(parts, paint, st, sp);
            case KART -> kart(parts, paint, st, sp);
        }

        renderer.throughWalls(false);
        faces.sort(Comparator.comparingDouble(Face::depth).reversed());
        for (Face f : faces) {
            renderer.quad(f.a.x, f.a.y, f.a.z, f.b.x, f.b.y, f.b.z, f.c.x, f.c.y, f.c.z, f.d.x, f.d.y, f.d.z, new Color(f.argb));
        }
        if (headlights.get() && light < 0.6f) beams(renderer, pos, b, 0.6f - light);
        renderer.throughWalls(true);
    }

    /** How lit the spot is, 0.25 in the dark to 1 in daylight. */
    private static float lightAt(Vec3d pos) {
        BlockPos p = BlockPos.ofFloored(pos.add(0, 1, 0));
        int sky = mc.world.getLightLevel(LightType.SKY, p) - mc.world.getAmbientDarkness();
        int block = mc.world.getLightLevel(LightType.BLOCK, p);
        return 0.25f + 0.75f * Math.max(0, Math.max(sky, block)) / 15f;
    }

    /** Collects the car's faces, shaded by which way they face and how lit the spot is. */
    private static final class Parts {
        private static final Vec3d SUN = new Vec3d(0.35, 1, 0.55).normalize();
        final List<Face> faces;
        final Vec3d origin, cam;
        final Vec3d[] b;
        final float light;

        Parts(List<Face> faces, Vec3d origin, Vec3d[] b, Vec3d cam, float light) {
            this.faces = faces;
            this.origin = origin;
            this.b = b;
            this.cam = cam;
            this.light = light;
        }

        Vec3d p(double lx, double ly, double lz) {
            return at(origin, b, lx, ly, lz);
        }

        int shade(int argb, Vec3d normal, boolean glow) {
            if (glow) return argb;
            float k = (float) (0.58 + 0.42 * Math.max(0, normal.dotProduct(SUN))) * light;
            int r = Math.round((argb >> 16 & 0xFF) * k), g = Math.round((argb >> 8 & 0xFF) * k), bl = Math.round((argb & 0xFF) * k);
            return (argb & 0xFF000000) | MathHelper.clamp(r, 0, 255) << 16 | MathHelper.clamp(g, 0, 255) << 8 | MathHelper.clamp(bl, 0, 255);
        }

        void face(Vec3d a, Vec3d bb, Vec3d c, Vec3d d, int argb, boolean glow) {
            Vec3d normal = bb.subtract(a).crossProduct(d.subtract(a)).normalize();
            Vec3d mid = a.add(bb).add(c).add(d).multiply(0.25);
            // Faces turned away from the camera are left out, so the box reads solid.
            if (normal.dotProduct(cam.subtract(mid)) < 0 && (argb >>> 24) == 0xFF) return;
            faces.add(new Face(a, bb, c, d, shade(argb, normal, glow), mid.squaredDistanceTo(cam)));
        }

        /** A box in the car's own space: x right, y up, z forward. */
        void box(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
            box(x0, y0, z0, x1, y1, z1, argb, false);
        }

        void box(double x0, double y0, double z0, double x1, double y1, double z1, int argb, boolean glow) {
            Vec3d a = p(x0, y0, z0), bx = p(x1, y0, z0), c = p(x1, y0, z1), d = p(x0, y0, z1);
            Vec3d e = p(x0, y1, z0), f = p(x1, y1, z0), g = p(x1, y1, z1), h = p(x0, y1, z1);
            face(a, d, c, bx, argb, glow);   // bottom
            face(e, f, g, h, argb, glow);    // top
            face(a, bx, f, e, argb, glow);   // back
            face(d, h, g, c, argb, glow);    // front
            face(a, e, h, d, argb, glow);    // the x0 side
            face(bx, c, g, f, argb, glow);   // the x1 side
        }

        /** A wheel: an eight-sided tyre turning about the axle, with a hub and a spoke that show it spin; front wheels steer. */
        void wheel(double cx, double cz, double radius, double width, float steerRad, float spin) {
            double cy = radius;
            Vec3d centre = p(cx, cy, cz);
            Vec3d axle = b[0].multiply(Math.cos(steerRad)).add(b[2].multiply(Math.sin(steerRad)));
            Vec3d fwd = b[2].multiply(Math.cos(steerRad)).subtract(b[0].multiply(Math.sin(steerRad)));
            Vec3d up = b[1];
            int n = 8;
            Vec3d[] in = new Vec3d[n], out = new Vec3d[n];
            for (int i = 0; i < n; i++) {
                double a = spin + Math.PI * 2 * i / n;
                Vec3d rim = up.multiply(Math.cos(a) * radius).add(fwd.multiply(Math.sin(a) * radius));
                in[i] = centre.add(rim).subtract(axle.multiply(width / 2));
                out[i] = centre.add(rim).add(axle.multiply(width / 2));
            }
            int tyre = 0xFF1A1B1F, hub = 0xFFB9BEC7, spoke = 0xFF5A5F69;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                face(in[i], out[i], out[j], in[j], tyre, false);
            }
            Vec3d oc = centre.add(axle.multiply(width / 2 + 0.005)), ic = centre.subtract(axle.multiply(width / 2 + 0.005));
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                Vec3d o1 = oc.add(out[i].subtract(oc).multiply(0.55)), o2 = oc.add(out[j].subtract(oc).multiply(0.55));
                face(oc, o2, o1, o1, hub, false);
                face(out[i], o1, o2, out[j], tyre, false);
                Vec3d i1 = ic.add(in[i].subtract(ic).multiply(0.55)), i2 = ic.add(in[j].subtract(ic).multiply(0.55));
                face(ic, i1, i2, i2, hub, false);
                face(in[i], in[j], i2, i1, tyre, false);
            }
            // A dark spoke across the hub, turning with the wheel.
            double a = spin;
            Vec3d s1 = up.multiply(Math.cos(a) * radius * 0.5).add(fwd.multiply(Math.sin(a) * radius * 0.5));
            Vec3d s2 = up.multiply(-Math.sin(a) * radius * 0.08).add(fwd.multiply(Math.cos(a) * radius * 0.08));
            Vec3d sc = oc.add(axle.multiply(0.004));
            face(sc.add(s1).add(s2), sc.add(s1).subtract(s2), sc.subtract(s1).subtract(s2), sc.subtract(s1).add(s2), spoke, false);
        }
    }

    private static int darker(int argb, float k) {
        return ColorUtil.shade(argb, -k);
    }

    private static final int GLASS = 0xFF26323F, TRIM = 0xFF22252B, CHROME = 0xFFC9CED6, LAMP = 0xFFFFF4C2, TAIL = 0xFFE0283A, SEAT = 0xFF2B2D33;

    private void lights(Parts p, double halfW, double y0, double y1, double front, double rear) {
        boolean on = headlights.get();
        p.box(-halfW + 0.12, y0, front - 0.02, -halfW + 0.42, y1, front + 0.03, on ? LAMP : 0xFFD9D4BC, on);
        p.box(halfW - 0.42, y0, front - 0.02, halfW - 0.12, y1, front + 0.03, on ? LAMP : 0xFFD9D4BC, on);
        p.box(-halfW + 0.1, y0, rear - 0.03, -halfW + 0.38, y1, rear + 0.02, TAIL, on);
        p.box(halfW - 0.38, y0, rear - 0.03, halfW - 0.1, y1, rear + 0.02, TAIL, on);
    }

    private void car(Parts p, int paint, float st, float sp) {
        p.box(-0.86, 0.28, -1.85, 0.86, 0.92, 1.85, paint);
        p.box(-0.8, 0.92, -0.95, 0.8, 1.72, 0.55, darker(paint, 0.06f));
        // Windows a hair proud of the cabin, all round.
        p.box(-0.72, 1.0, 0.55, 0.72, 1.62, 0.58, GLASS);
        p.box(-0.72, 1.0, -0.98, 0.72, 1.62, -0.95, GLASS);
        p.box(-0.83, 1.0, -0.85, -0.8, 1.62, 0.45, GLASS);
        p.box(0.8, 1.0, -0.85, 0.83, 1.62, 0.45, GLASS);
        p.box(-0.88, 0.28, 1.85, 0.88, 0.48, 1.97, TRIM);
        p.box(-0.88, 0.28, -1.97, 0.88, 0.48, -1.85, TRIM);
        p.box(-0.5, 0.52, 1.85, 0.5, 0.66, 1.88, CHROME);
        lights(p, 0.86, 0.62, 0.78, 1.86, -1.86);
        wheels(p, 0.8, 1.2, 0.35, 0.28, st, sp);
    }

    private void sports(Parts p, int paint, float st, float sp) {
        p.box(-0.9, 0.25, -1.95, 0.9, 0.78, 1.95, paint);
        p.box(-0.86, 0.78, -1.95, 0.86, 0.86, -0.7, darker(paint, 0.08f));
        // A convertible: a raked windscreen and two roll hoops.
        p.box(-0.78, 0.78, 0.45, 0.78, 1.28, 0.52, GLASS);
        p.box(-0.62, 0.78, -0.75, -0.48, 1.32, -0.62, TRIM);
        p.box(0.48, 0.78, -0.75, 0.62, 1.32, -0.62, TRIM);
        p.box(-0.55, 0.62, -1.0, 0.0, 0.95, -0.2, SEAT);
        p.box(0.05, 0.62, -1.0, 0.6, 0.95, -0.2, SEAT);
        // The wing at the back.
        p.box(-0.7, 0.86, -1.88, -0.62, 1.12, -1.8, TRIM);
        p.box(0.62, 0.86, -1.88, 0.7, 1.12, -1.8, TRIM);
        p.box(-0.95, 1.12, -1.98, 0.95, 1.18, -1.72, darker(paint, 0.15f));
        p.box(-0.92, 0.25, 1.95, 0.92, 0.42, 2.05, TRIM);
        lights(p, 0.9, 0.55, 0.68, 1.96, -1.96);
        wheels(p, 0.84, 1.3, 0.33, 0.32, st, sp);
    }

    private void truck(Parts p, int paint, float st, float sp) {
        p.box(-0.95, 0.48, -2.2, 0.95, 1.25, 2.2, paint);
        p.box(-0.92, 1.25, 0.35, 0.92, 2.15, 1.65, darker(paint, 0.05f));
        p.box(-0.82, 1.4, 1.65, 0.82, 2.0, 1.68, GLASS);
        p.box(-0.95, 1.4, 0.5, -0.92, 2.0, 1.5, GLASS);
        p.box(0.92, 1.4, 0.5, 0.95, 2.0, 1.5, GLASS);
        p.box(-0.82, 1.4, 0.32, 0.82, 2.0, 0.35, GLASS);
        // The open bed behind the cab.
        p.box(-0.95, 1.25, -2.2, -0.85, 1.7, 0.3, darker(paint, 0.1f));
        p.box(0.85, 1.25, -2.2, 0.95, 1.7, 0.3, darker(paint, 0.1f));
        p.box(-0.85, 1.25, -2.2, 0.85, 1.7, -2.1, darker(paint, 0.1f));
        p.box(-0.97, 0.45, 2.2, 0.97, 0.75, 2.34, CHROME);
        p.box(-0.97, 0.45, -2.34, 0.97, 0.7, -2.2, TRIM);
        lights(p, 0.95, 0.95, 1.12, 2.21, -2.21);
        wheels(p, 0.9, 1.45, 0.46, 0.36, st, sp);
    }

    private void kart(Parts p, int paint, float st, float sp) {
        p.box(-0.55, 0.14, -0.95, 0.55, 0.26, 0.95, TRIM);
        p.box(-0.32, 0.22, 0.7, 0.32, 0.45, 1.2, paint);
        p.box(-0.72, 0.18, -0.2, -0.55, 0.42, 0.55, paint);
        p.box(0.55, 0.18, -0.2, 0.72, 0.42, 0.55, paint);
        p.box(-0.28, 0.26, -0.75, 0.28, 0.42, -0.2, SEAT);
        p.box(-0.28, 0.42, -0.8, 0.28, 0.85, -0.65, SEAT);
        p.box(-0.04, 0.26, 0.2, 0.04, 0.7, 0.28, CHROME);
        p.box(-0.2, 0.68, 0.18, 0.2, 0.74, 0.3, TRIM);
        p.box(-0.5, 0.42, -1.0, 0.5, 0.5, -0.92, darker(paint, 0.15f));
        wheels(p, 0.62, 0.72, 0.22, 0.24, st, sp);
    }

    private void wheels(Parts p, double x, double z, double radius, double width, float st, float sp) {
        p.wheel(-x, z, radius, width, st, sp);
        p.wheel(x, z, radius, width, st, sp);
        p.wheel(-x, -z, radius, width, 0, sp);
        p.wheel(x, -z, radius, width, 0, sp);
    }

    /** Soft cones of light ahead of the headlights, brighter the darker it is. */
    private void beams(Renderer3D renderer, Vec3d pos, Vec3d[] b, float dark) {
        double front = length() / 2 + 0.05, h = model() == Model.TRUCK ? 1.0 : 0.68;
        for (int side = -1; side <= 1; side += 2) {
            double lx = side * (model() == Model.KART ? 0.25 : 0.6);
            for (int i = 0; i < 6; i++) {
                double z0 = front + i * 1.6, z1 = z0 + 1.6;
                double w0 = 0.15 + i * 0.35, w1 = w0 + 0.35;
                int a = Math.round(60 * dark * (1 - i / 6f));
                Color c = new Color(ColorUtil.withAlpha(0xFFFFF2C0, a));
                Vec3d p0 = at(pos, b, lx - w0, h, z0), p1 = at(pos, b, lx + w0, h, z0), p2 = at(pos, b, lx + w1, h - 0.25 * (i + 1) / 6, z1),
                        p3 = at(pos, b, lx - w1, h - 0.25 * (i + 1) / 6, z1);
                renderer.quad(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, c);
            }
        }
    }

    // ---- the speedometer ------------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden || !speedometer.get()) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        double kmh = Math.abs(speed) * 3.6;
        String value = Long.toString(Math.round(kmh));
        // Bottom right, clear of the car in the middle of the view and of the hotbar.
        float cw = 74, ch = 34, x0 = w - cw - 8, y0 = h - ch - 8;
        Fonts.beginRaw();
        try {
            Render2D.shadow(ctx, x0, y0 + 1, cw, ch, 8, 8, 0x90000000);
            Render2D.roundRect(ctx, x0, y0, cw, ch, 8, 0xE00D0F14);
            Render2D.roundOutline(ctx, x0, y0, cw, ch, 8, 0.6f, 0x22FFFFFF);
            float frac = (float) MathHelper.clamp(Math.abs(speed) / (free ? topSpeed.get() * 1.35 : 8.0), 0, 1);
            Render2D.arc(ctx, x0 + cw / 2f, y0 + ch + 6, 26, 2.2f, 200, 140, 0x26FFFFFF, 0x26FFFFFF);
            if (frac > 0.01f) Render2D.arc(ctx, x0 + cw / 2f, y0 + ch + 6, 26, 2.2f, 200, 140 * frac, Theme.accent(), Theme.accent2());
            Fonts.drawCentered(ctx, value, x0 + cw / 2f, y0 + 15, 0xFFF4F6FA, true, 1.15f);
            String unit = (speed < -0.05 ? "R · " : "") + "km/h" + (free && blockedAhead && Math.abs(speed) < 0.5 ? " · blocked" : "");
            Fonts.drawCentered(ctx, unit, x0 + cw / 2f, y0 + 27, 0xFF8A93A3, false, 0.55f);
        } finally {
            Fonts.endRaw();
        }
    }

    // ---- for tests ----------------------------------------------------------------------------

    public Vec3d carPosition() {
        return new Vec3d(x, y, z);
    }

    public float carYaw() {
        return yaw;
    }

    public double speed() {
        return speed;
    }

    // ---- the engine note --------------------------------------------------------------------------

    /** A rumble that follows the car, its pitch and loudness rising with the speed. */
    private static final class Engine extends MovingSoundInstance {
        private final Vehicles owner;

        Engine(Vehicles owner) {
            super(SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.NEUTRAL, SoundInstance.createRandom());
            this.owner = owner;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 0.001f;
            this.pitch = 0.5f;
        }

        void stop() {
            setDone();
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            if (!owner.isEnabled() || mc.player == null) {
                setDone();
                return;
            }
            Vec3d at = owner.carPos(1f);
            this.x = at.x;
            this.y = at.y + 0.5;
            this.z = at.z;
            float frac = (float) MathHelper.clamp(Math.abs(owner.speed) / 30.0, 0, 1);
            this.volume = (0.18f + 0.55f * frac) * owner.volume.getFloat() / 100f;
            this.pitch = 0.55f + 1.15f * frac;
        }
    }
}
