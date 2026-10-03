package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.StretchRes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;

/** Compare the real GPU upload, world/culling matrices and optional Meteor ESP projection. */
public final class StretchProjectionChecks {
    public static Matrix4f lastUpload, worldUpload, worldProjection, cullingProjection, worldView;

    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        StretchRes stretch = ModuleManager.get(StretchRes.class);
        boolean bob = context.computeOnClient(client -> client.options.getBobView().getValue());
        Object meteor = meteorEsp();
        context.runOnClient(client -> client.options.getBobView().setValue(false));
        try {
            for (String ratio : new String[]{"4:3", "5:4", "3:2", "16:10", "1:1", "Custom"}) {
                context.runOnClient(client -> {
                    setting(stretch, "Ratio", ratio); setting(stretch, "Custom Ratio", 0.8);
                    stretch.setEnabled(true);
                    Matrix4f perspective = client.gameRenderer.getBasicProjectionMatrix(70);
                    require(close(perspective.m00() / perspective.m11(), 1 / stretch.targetAspect()),
                            "Shared projection does not use the selected aspect: " + ratio);
                    Matrix4f ortho = new Matrix4f().ortho(0, 1280, 720, 0, -1, 1);
                    require(StretchRes.apply(ortho) == ortho, "Stretch Res changed an orthographic HUD matrix");
                });
                context.waitTicks(3);
                context.runOnClient(client -> {
                    require(worldProjection != null && worldUpload != null, "No world projection was recorded");
                    require(worldProjection.equals(worldUpload, .00001f), "GPU and CPU world projection disagree: " + ratio);
                    require(close(cullingProjection.m00() / cullingProjection.m11(), 1 / stretch.targetAspect()),
                            "Culling still uses the window aspect: " + ratio);
                    if (meteor != null) checkMeteorProjection();
                });
            }
            if (meteor != null) checkMeteorEsp(context, singleplayer, stretch, meteor);
        } finally {
            context.runOnClient(client -> {
                stretch.setEnabled(false);
                for (var setting : stretch.getSettings()) setting.reset();
                client.options.getBobView().setValue(bob);
                if (meteor != null) invoke(meteor, "disable");
                Matrix4f perspective = client.gameRenderer.getBasicProjectionMatrix(70);
                float aspect = (float) client.getWindow().getFramebufferWidth() / client.getWindow().getFramebufferHeight();
                require(close(perspective.m00() / perspective.m11(), 1 / aspect), "Disabling Stretch Res did not restore the window aspect");
            });
        }
    }

    private static void checkMeteorEsp(ClientGameTestContext context, TestSingleplayerContext singleplayer, StretchRes stretch, Object esp) {
        BlockPos p = context.computeOnClient(client -> client.player.getBlockPos().up(25));
        for (String command : new String[]{"gamemode creative @a", "time set noon", "clear @a",
                "fill " + xyz(p.add(-10, 0, -2)) + " " + xyz(p.add(10, 5, 12)) + " air",
                "fill " + xyz(p.add(-10, -1, -2)) + " " + xyz(p.add(10, -1, 12)) + " stone",
                "fill " + xyz(p.add(-10, 0, 9)) + " " + xyz(p.add(10, 5, 9)) + " black_concrete",
                "tp @a " + (p.getX() + .5) + " " + p.getY() + " " + (p.getZ() + .5) + " 0 0",
                "summon armor_stand " + (p.getX() - 1.5) + " " + p.getY() + " " + (p.getZ() + 6.5) + " {NoGravity:1b,ShowArms:1b}",
                "summon armor_stand " + (p.getX() + 2.5) + " " + p.getY() + " " + (p.getZ() + 6.5) + " {NoGravity:1b,ShowArms:1b}"})
            singleplayer.getServer().runCommand(command);
        context.waitTicks(10);
        context.runOnClient(client -> {
            meteorSetting(esp, "entities", Set.of(EntityType.ARMOR_STAND));
            meteorSetting(esp, "highlight-target", false); meteorSetting(esp, "fade-distance", 0.0);
            meteorSetting(esp, "fill-opacity", .6); meteorSetting(esp, "glow-multiplier", 1.0);
            try {
                Object pink = Class.forName("meteordevelopment.meteorclient.utils.render.color.SettingColor")
                        .getConstructor(int.class, int.class, int.class, int.class).newInstance(255, 0, 255, 255);
                meteorSetting(esp, "misc-color", pink); meteorSetting(esp, "non-living-entity-color", pink);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            setting(stretch, "Ratio", "4:3"); stretch.setEnabled(true); invoke(esp, "enable");
        });
        for (String mode : new String[]{"Shader", "Box", "_2D"}) {
            for (int yaw : new int[]{-10, 0, 10}) {
                context.runOnClient(client -> {
                    meteorSetting(esp, "mode", enumValue("meteordevelopment.meteorclient.systems.modules.render.ESP$Mode", mode));
                    client.player.setYaw(yaw); client.player.setPitch(0);
                });
                context.waitTicks(4);
                var bounds = context.computeOnClient(client -> {
                    checkMeteorProjection();
                    Matrix4f viewProjection = new Matrix4f(worldProjection).mul(worldView);
                    var camera = client.gameRenderer.getCamera().getCameraPos();
                    var rectangles = new ArrayList<float[]>();
                    for (var entity : client.world.getEntities()) if (entity instanceof ArmorStandEntity && entity.squaredDistanceTo(client.player) < 100) {
                        var box = entity.getBoundingBox().expand(.2);
                        float[] r = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
                        for (double x : new double[]{box.minX, box.maxX}) for (double y : new double[]{box.minY, box.maxY}) for (double z : new double[]{box.minZ, box.maxZ}) {
                            var clip = viewProjection.transform(new Vector4f((float)(x-camera.x), (float)(y-camera.y), (float)(z-camera.z), 1));
                            float sx = (clip.x / clip.w * .5f + .5f) * client.getWindow().getFramebufferWidth();
                            float sy = (.5f - clip.y / clip.w * .5f) * client.getWindow().getFramebufferHeight();
                            r[0] = Math.min(r[0], sx); r[1] = Math.min(r[1], sy); r[2] = Math.max(r[2], sx); r[3] = Math.max(r[3], sy);
                        }
                        rectangles.add(r);
                    }
                    require(rectangles.size() == 2, "ESP fixture did not have two armor stands");
                    return rectangles;
                });
                checkPinkPixels(context.takeScreenshot("maro-stretch-esp-" + mode + "-" + yaw), bounds);
            }
        }
        singleplayer.getServer().runCommand("kill @e[type=armor_stand]");
    }

    private static void checkPinkPixels(Path screenshot, ArrayList<float[]> bounds) {
        try {
            var image = ImageIO.read(screenshot.toFile()); int pink = 0, aligned = 0;
            for (int y = 0; y < image.getHeight() - 80; y++) for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y), r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                if (r < 90 || b < 90 || r < g * 1.6 || b < g * 1.6) continue;
                pink++;
                for (float[] rect : bounds) if (x >= rect[0]-12 && x <= rect[2]+12 && y >= rect[1]-12 && y <= rect[3]+12) { aligned++; break; }
            }
            require(pink > 50, "Meteor ESP did not render its test entities: " + screenshot.getFileName());
            require(aligned >= pink * .95, "ESP drifted outside the world-projected entities: " + aligned + "/" + pink + " " + screenshot.getFileName());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static Object meteorEsp() {
        try {
            Class<?> modules = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
            Object manager = modules.getMethod("get").invoke(null);
            for (Object module : (Iterable<?>) modules.getMethod("getAll").invoke(manager)) invoke(module, "disable");
            return modules.getMethod("get", Class.class).invoke(manager, Class.forName("meteordevelopment.meteorclient.systems.modules.render.ESP"));
        } catch (ClassNotFoundException absent) { return null; }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void checkMeteorProjection() {
        try {
            // RenderUtils.projection becomes orthographic during HUD rendering. ESP's
            // screen-space conversion uses the perspective cached by NametagUtils.
            var field = Class.forName("meteordevelopment.meteorclient.utils.render.NametagUtils").getDeclaredField("projection");
            field.setAccessible(true);
            Matrix4f cached = (Matrix4f) field.get(null);
            require(cached.equals(worldProjection, .00001f), "Meteor ESP projection differs from the rendered world\nMeteor:\n" + cached + "\nWorld:\n" + worldProjection);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void meteorSetting(Object module, String name, Object value) {
        try {
            Object settings = module.getClass().getField("settings").get(module);
            Object setting = settings.getClass().getMethod("get", String.class).invoke(settings, name);
            require(setting != null, "Missing Meteor setting: " + name);
            setting.getClass().getMethod("set", Object.class).invoke(setting, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(String type, String value) {
        try { return Enum.valueOf((Class)Class.forName(type), value); }
        catch (ClassNotFoundException e) { throw new AssertionError(e); }
    }
    private static void invoke(Object object, String method) {
        try { object.getClass().getMethod(method).invoke(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void setting(StretchRes stretch, String name, Object value) {
        for (var setting : stretch.getSettings()) if (setting.getName().equals(name))
            setting.fromJson(value instanceof Number n ? new JsonPrimitive(n) : new JsonPrimitive(value.toString()));
    }
    private static String xyz(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }
    private static boolean close(float a, float b) { return Math.abs(a - b) < .00001; }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
