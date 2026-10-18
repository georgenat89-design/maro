// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.inspect;

import dev.maro.anubis.inspect.FirstPersonArm;
import dev.maro.anubis.inspect.InspectAnimation;
import dev.maro.anubis.inspect.KnifeMesh;
import dev.maro.anubis.inspect.KnifeModels;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(value=EnvType.CLIENT)
final class KnifeSkin {
    static final String NONE = "None";
    static final String BUTTERFLY = "Butterfly";
    static final String KARAMBIT = "Karambit";
    static final String KATANA = "Katana";
    static final int MAX_STREAKS = 2;
    private static final Vector3f OFF_FOREARM = new Vector3f(0.4f, -0.85f, 0.35f).normalize();
    private static final Vector3f OFF_AWAY = new Vector3f(-0.3f, -0.75f, 0.1f);
    private static final float WIPE_LIFT = 3.0f;
    private static final float WIPE_TAIL = 16.0f;
    private static final Vector3f SCABBARD_FALL = new Vector3f(-0.3f, -0.9f, 0.25f);
    private static final float SCABBARD_TURN = 0.6981317f;
    private static final float[][] BUTTERFLY_INSPECT = new float[][]{{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}, {0.08f, -0.07f, 0.09f, 0.05f, -8.0f, 8.0f, 10.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 8.0f}, {0.14f, -0.09f, 0.12f, 0.06f, -12.0f, 8.0f, 14.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 12.0f}, {0.19f, -0.09f, 0.12f, 0.06f, -10.0f, 10.0f, 2.0f, -18.0f, 0.0f, 90.0f, 180.0f, 0.0f, 12.0f}, {0.24f, -0.09f, 0.13f, 0.06f, -12.0f, 8.0f, 10.0f, 0.0f, 0.0f, 180.0f, 360.0f, 0.0f, 12.0f}, {0.29f, -0.09f, 0.12f, 0.06f, -10.0f, 10.0f, 2.0f, 18.0f, 0.0f, 270.0f, 540.0f, 0.0f, 12.0f}, {0.34f, -0.09f, 0.12f, 0.06f, -12.0f, 8.0f, 14.0f, 0.0f, 0.0f, 360.0f, 720.0f, 0.0f, 12.0f}, {0.39f, -0.09f, 0.12f, 0.06f, -10.0f, 10.0f, 2.0f, -18.0f, 0.0f, 450.0f, 900.0f, 0.0f, 12.0f}, {0.44f, -0.09f, 0.13f, 0.06f, -12.0f, 8.0f, 10.0f, 0.0f, 0.0f, 540.0f, 1080.0f, 0.0f, 12.0f}, {0.49f, -0.09f, 0.12f, 0.06f, -10.0f, 10.0f, 2.0f, 18.0f, 0.0f, 630.0f, 1260.0f, 0.0f, 12.0f}, {0.54f, -0.09f, 0.12f, 0.06f, -12.0f, 8.0f, 14.0f, 0.0f, 0.0f, 720.0f, 1440.0f, 0.0f, 12.0f}, {0.62f, -0.1f, 0.12f, 0.07f, -35.0f, 6.0f, 20.0f, 0.0f, 0.0f, 720.0f, 1440.0f, 0.0f, 0.0f}, {0.76f, -0.1f, 0.13f, 0.07f, 25.0f, 6.0f, 16.0f, 0.0f, 0.0f, 720.0f, 1440.0f, 1.0f, 0.0f}, {0.88f, -0.05f, 0.06f, 0.03f, 8.0f, 3.0f, 6.0f, 0.0f, 0.0f, 720.0f, 1440.0f, 1.0f, 0.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 720.0f, 1440.0f, 1.0f, 0.0f}};
    private static final float[][] BUTTERFLY_DEPLOY = new float[][]{{0.0f, 0.02f, -0.14f, 0.02f, 0.0f, -30.0f, -20.0f, 0.0f, 0.0f, 180.0f, 0.0f, 0.0f, 10.0f}, {0.3f, 0.01f, -0.04f, 0.01f, 0.0f, -10.0f, -10.0f, 0.0f, 0.0f, 220.0f, 100.0f, 0.0f, 10.0f}, {0.62f, 0.0f, 0.01f, 0.0f, 0.0f, 4.0f, 6.0f, 0.0f, 0.0f, 350.0f, 340.0f, 0.0f, 3.0f}, {0.8f, 0.0f, 0.0f, 0.0f, 0.0f, -2.0f, -2.0f, 0.0f, 0.0f, 365.0f, 368.0f, 0.0f, 0.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 360.0f, 360.0f, 0.0f, 0.0f}};
    private static final float[][] KARAMBIT_INSPECT = new float[][]{{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}, {0.1f, -0.12f, 0.14f, 0.06f, -10.0f, 14.0f, 10.0f, 0.0f, 0.0f, -25.0f, 0.0f, 0.0f}, {0.14f, -0.14f, 0.16f, 0.06f, -10.0f, 14.0f, 10.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}, {0.205f, -0.14f, 0.17f, 0.06f, -10.0f, 14.0f, 10.0f, 0.0f, 0.0f, 180.0f, 0.0f, 0.0f}, {0.27f, -0.14f, 0.16f, 0.06f, -10.0f, 14.0f, 10.0f, 0.0f, 0.0f, 360.0f, 0.0f, 0.0f}, {0.335f, -0.14f, 0.17f, 0.06f, -10.0f, 14.0f, 10.0f, 0.0f, 0.0f, 540.0f, 0.0f, 0.0f}, {0.4f, -0.14f, 0.16f, 0.06f, -10.0f, 14.0f, 10.0f, 0.0f, 0.0f, 720.0f, 0.0f, 0.0f}, {0.48f, -0.13f, 0.16f, 0.06f, -10.0f, 12.0f, 10.0f, 0.0f, 0.0f, 750.0f, 0.0f, 0.0f}, {0.56f, -0.1f, 0.12f, 0.06f, 20.0f, 10.0f, -70.0f, 0.0f, 0.0f, 720.0f, 0.0f, 0.0f}, {0.72f, -0.1f, 0.13f, 0.06f, -10.0f, 10.0f, -60.0f, 0.0f, 0.0f, 720.0f, 0.0f, 1.0f}, {0.86f, -0.06f, 0.08f, 0.05f, -6.0f, 4.0f, 8.0f, 0.0f, 0.0f, 360.0f, 0.0f, 1.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 360.0f, 0.0f, 1.0f}};
    private static final float[][] KARAMBIT_DEPLOY = new float[][]{{0.0f, 0.02f, -0.14f, 0.02f, 0.0f, -30.0f, -20.0f, 0.0f, 0.0f, -540.0f, 0.0f, 0.0f}, {0.55f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f, 4.0f, 0.0f, 0.0f, -60.0f, 0.0f, 0.0f}, {0.78f, 0.0f, 0.0f, 0.0f, 0.0f, -2.0f, -2.0f, 0.0f, 0.0f, 12.0f, 0.0f, 0.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}};
    private static final float[][] KATANA_INSPECT = new float[][]{{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 4.0f}, {0.1f, -0.06f, 0.12f, 0.06f, 0.0f, 0.0f, 60.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.6f, 4.0f}, {0.15f, -0.07f, 0.14f, 0.08f, -2.0f, 0.0f, 60.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 4.0f}, {0.2f, -0.07f, 0.14f, 0.08f, -2.0f, 0.0f, 60.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 12.0f}, {0.36f, -0.07f, 0.14f, 0.08f, 0.0f, 0.0f, 58.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 44.0f}, {0.4f, -0.07f, 0.14f, 0.08f, 0.0f, 0.0f, 58.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 53.0f}, {0.44f, -0.06f, 0.13f, 0.06f, 2.0f, 1.0f, 54.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.6f, 58.0f}, {0.48f, -0.05f, 0.11f, 0.03f, 4.0f, 2.0f, 42.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 62.0f}, {0.52f, -0.04f, 0.1f, 0.02f, 0.0f, 4.0f, 20.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 62.0f}, {0.6f, -0.04f, 0.12f, 0.02f, 0.0f, 4.0f, -160.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 62.0f}, {0.68f, -0.04f, 0.1f, 0.02f, 0.0f, 4.0f, -340.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 62.0f}, {0.78f, 0.04f, -0.03f, 0.0f, 0.0f, -6.0f, -400.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 62.0f}, {0.88f, 0.0f, 0.02f, 0.0f, 0.0f, 2.0f, -365.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 62.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -360.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 62.0f}};
    private static final float[][] KATANA_DEPLOY = new float[][]{{0.0f, 0.06f, 0.0f, 0.02f, -10.0f, 0.0f, 45.0f, 180.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f}, {0.05f, 0.05f, 0.01f, 0.01f, -12.0f, 0.0f, 47.0f, 180.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.0f, 1.0f, 0.0f}, {0.18f, 0.15f, 0.0f, 0.05f, -10.0f, 0.0f, 45.0f, 180.0f, 0.0f, 0.0f, 0.0f, 0.3f, 0.0f, 16.0f, 1.0f, 0.0f}, {0.3f, 0.3f, -0.02f, 0.1f, -8.0f, 0.0f, 43.0f, 180.0f, 0.0f, 0.0f, 0.0f, 0.9f, 0.0f, 50.0f, 1.0f, 0.0f}, {0.33f, 0.3f, 0.0f, 0.1f, -4.0f, 4.0f, 36.0f, 180.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 52.0f, 1.0f, 0.0f}, {0.41f, 0.08f, 0.12f, 0.04f, 0.0f, 10.0f, -40.0f, 180.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 52.0f, 0.25f, 0.0f}, {0.47f, -0.02f, 0.1f, 0.02f, 5.0f, 8.0f, -100.0f, 180.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 52.0f, 0.0f, 0.0f}, {0.62f, -0.02f, 0.08f, 0.02f, 0.0f, 6.0f, -460.0f, 180.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 52.0f, 0.0f, 0.0f}, {0.71f, 0.04f, -0.04f, 0.0f, 0.0f, -6.0f, -520.0f, 180.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 52.0f, 0.0f, 0.0f}, {0.82f, 0.0f, 0.02f, 0.0f, 0.0f, 2.0f, -370.0f, 300.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 52.0f, 0.0f, 0.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -360.0f, 360.0f, 0.0f, 0.0f, 0.0f, 3.0f, 0.0f, 52.0f, 0.0f, 0.0f}};
    private static KnifeSkin butterfly;
    private static KnifeSkin karambit;
    private static KnifeSkin katana;
    final String name;
    final InspectAnimation.Clip inspect;
    final InspectAnimation.Clip deploy;
    private final Part[] parts;
    private final Streak[] streaks;
    private final float voxel;
    private final float gripX;
    private final float gripY;
    private final Vector3f fist;
    private final Vector3f elbow = new Vector3f();
    private final Quaternionf rest;
    private KnifeMesh scabbard;
    private float saddleX;
    private float saddleY;
    private float[] letGo;
    private Centre centre;
    private float bladeFrom;
    private float bladeTo;

    private KnifeSkin(String name, Part[] parts, Streak[] streaks, float voxel, float gripX, float gripY, Vector3f fist, Vector3f blade, Vector3f spine, InspectAnimation.Clip inspect, InspectAnimation.Clip deploy) {
        this.name = name;
        this.parts = parts;
        this.streaks = streaks;
        this.voxel = voxel;
        this.gripX = gripX;
        this.gripY = gripY;
        this.fist = fist;
        this.inspect = inspect;
        this.deploy = deploy;
        Vector3f x = new Vector3f((Vector3fc)blade).normalize();
        Vector3f y = new Vector3f((Vector3fc)spine).fma(-spine.dot((Vector3fc)x), (Vector3fc)x).normalize();
        this.rest = new Quaternionf().setFromNormalized((Matrix3fc)new Matrix3f((Vector3fc)x, (Vector3fc)y, (Vector3fc)new Vector3f((Vector3fc)x).cross((Vector3fc)y)));
        fist.fma(0.6f, (Vector3fc)FirstPersonArm.FOREARM, this.elbow);
    }

    static KnifeSkin of(String name) {
        return switch (name) {
            case BUTTERFLY -> {
                if (butterfly != null) {
                    yield butterfly;
                }
                yield butterfly = KnifeSkin.butterfly();
            }
            case KARAMBIT -> {
                if (karambit != null) {
                    yield karambit;
                }
                yield karambit = KnifeSkin.karambit();
            }
            case KATANA -> {
                if (katana != null) {
                    yield katana;
                }
                yield katana = KnifeSkin.katana();
            }
            default -> null;
        };
    }

    private static KnifeSkin butterfly() {
        float pivotX = -1.5f;
        float pivotY = 0.5f;
        Part[] parts = new Part[]{new Part(KnifeModels.butterflyHandle(1), -1, pivotX, pivotY), new Part(KnifeModels.butterflyBlade(), 8, pivotX, pivotY), new Part(KnifeModels.butterflyHandle(-1), 9, pivotX, pivotY)};
        Streak[] streaks = new Streak[]{new Streak(1, 2.5f, 0.5f, 21.5f, -0.5f, 1.0f), new Streak(2, -4.0f, 0.5f, -27.5f, 0.5f, 0.8f)};
        return new KnifeSkin(BUTTERFLY, parts, streaks, 0.021f, -10.0f, 0.5f, new Vector3f(-0.06f, 0.06f, 0.02f), new Vector3f(-0.15f, 1.0f, -0.1f), new Vector3f(1.0f, 0.15f, 0.3f), new InspectAnimation.Clip(BUTTERFLY_INSPECT, 3.8f, 0.14f, 0.54f), new InspectAnimation.Clip(BUTTERFLY_DEPLOY, 0.9f));
    }

    private static KnifeSkin karambit() {
        float ringX = -16.0f;
        float ringY = -1.0f;
        Part[] parts = new Part[]{new Part(KnifeModels.karambit(), 8, ringX, ringY)};
        float[] tip = KnifeModels.KARAMBIT_TIP;
        int k = 2;
        Streak[] streaks = new Streak[]{new Streak(0, 6.0f * (float)k, 0.5f * (float)k, tip[0], tip[1], 1.0f)};
        return new KnifeSkin(KARAMBIT, parts, streaks, 0.022f / (float)k, ringX + 2.0f, ringY, new Vector3f(-0.06f, 0.1f, 0.02f), new Vector3f(-0.5f, -0.8f, -0.3f), new Vector3f(0.8f, -0.5f, 0.35f), new InspectAnimation.Clip(KARAMBIT_INSPECT, 3.2f, 0.14f, 0.4f), new InspectAnimation.Clip(KARAMBIT_DEPLOY, 0.8f));
    }

    private static KnifeSkin katana() {
        Part[] parts = new Part[]{new Part(KnifeModels.katana(), -1, 0.0f, 0.0f)};
        Streak[] streaks = new Streak[]{new Streak(0, 6.0f, (float)KnifeModels.katanaBow(6) + 0.5f, 48.5f, (float)KnifeModels.katanaBow(48) + 1.5f, 1.0f)};
        KnifeSkin skin = new KnifeSkin(KATANA, parts, streaks, 0.028f, -5.0f, 0.5f, new Vector3f(-0.06f, 0.02f, 0.02f), new Vector3f(-0.6f, 0.8f, -0.2f), new Vector3f(0.6f, 0.45f, 0.1f), new InspectAnimation.Clip(KATANA_INSPECT, 4.4f, 0.52f, 0.68f), new InspectAnimation.Clip(KATANA_DEPLOY, 2.0f));
        skin.scabbard = KnifeModels.katanaScabbard();
        skin.saddleX = 20.0f;
        skin.saddleY = 0.0f;
        skin.centre = x -> x < 3.0f ? 0.5f : KnifeModels.katanaCentre(x);
        skin.bladeFrom = 3.0f;
        skin.bladeTo = 49.0f;
        skin.letGo = skin.findLetGo();
        return skin;
    }

    private float[] findLetGo() {
        float[] at = new float[16];
        float[] held = new float[16];
        for (int i = 0; i <= 2000; ++i) {
            this.deploy.sample((float)i / 2000.0f, at);
            if (at[13] < 1.0f) break;
            System.arraycopy(at, 0, held, 0, 16);
        }
        return held;
    }

    int streakCount() {
        return this.streaks.length;
    }

    float streakStrength(int streak) {
        return this.streaks[streak].strength;
    }

    void draw(MatrixStack pose, float[] at, float weight, boolean slim, float size, Sink sink, float[] trail) {
        KnifeMesh.Shine wipe;
        KnifeMesh.Shine shine;
        Matrix4f knife = this.knifeMatrix(at, weight, size, new Matrix4f());
        Vector3f hand = new Vector3f((Vector3fc)this.fist).add(at[0] * weight, at[1] * weight, at[2] * weight);
        FirstPersonArm.place(pose, hand, this.elbow, slim, posed -> sink.arm((MatrixStack)posed, false));
        Matrix4f sheath = this.scabbard != null && weight >= 0.999f && at[13] > 0.01f ? this.scabbardMatrix(at, size) : null;
        Vector3f other = null;
        if (sheath != null) {
            other = sheath.transformPosition(this.saddleX, this.saddleY, 0.5f, new Vector3f());
        } else if (this.centre != null && at[14] * weight > 0.01f) {
            float t = MathHelper.clamp((float)(at[14] * weight), (float)0.0f, (float)1.0f);
            float x = at[15];
            float y = this.centre.y(x) + (x >= this.bladeFrom ? 3.0f : 0.0f);
            other = knife.transformPosition(x, y, 0.5f, new Vector3f()).fma(1.0f - t * t * (3.0f - 2.0f * t), (Vector3fc)OFF_AWAY);
        }
        if (other != null) {
            pose.push();
            pose.scale(-1.0f, 1.0f, 1.0f);
            Vector3f mirrored = new Vector3f(-other.x, other.y, other.z);
            FirstPersonArm.place(pose, mirrored, new Vector3f((Vector3fc)mirrored).fma(0.6f, (Vector3fc)OFF_FOREARM), slim, posed -> sink.arm((MatrixStack)posed, true));
            pose.pop();
        }
        if ((shine = (wipe = this.wipe(at, weight))) == null) {
            float sweep = KnifeSkin.shinePosition(at[10]);
            shine = new KnifeMesh.Shine(sweep, sweep > 0.0f ? MathHelper.sin((double)((float)Math.PI * sweep)) * weight : 0.0f, 0.0f);
        }
        pose.push();
        pose.multiplyPositionMatrix((Matrix4fc)knife);
        for (int i = 0; i < this.parts.length; ++i) {
            float angle;
            Part part = this.parts[i];
            pose.push();
            if (part.channel >= 0 && (angle = MathHelper.wrapDegrees((float)at[part.channel]) * weight) != 0.0f) {
                pose.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees(angle), part.pivotX, part.pivotY, 0.5f);
            }
            sink.part(pose, part.mesh, shine);
            if (wipe != null) {
                sink.glow(pose, part.mesh, wipe);
            }
            if (trail != null) {
                this.streaks(i, pose.peek().getPositionMatrix(), trail);
            }
            pose.pop();
        }
        pose.pop();
        if (sheath != null) {
            pose.push();
            pose.multiplyPositionMatrix((Matrix4fc)sheath);
            sink.part(pose, this.scabbard, KnifeMesh.Shine.NONE);
            pose.pop();
        }
    }

    private void streaks(int part, Matrix4f m, float[] trail) {
        Vector3f p = new Vector3f();
        for (int s = 0; s < this.streaks.length; ++s) {
            Streak streak = this.streaks[s];
            if (streak.part != part) continue;
            m.transformPosition(streak.baseX, streak.baseY, 0.5f, p);
            trail[s * 6] = p.x;
            trail[s * 6 + 1] = p.y;
            trail[s * 6 + 2] = p.z;
            m.transformPosition(streak.tipX, streak.tipY, 0.5f, p);
            trail[s * 6 + 3] = p.x;
            trail[s * 6 + 4] = p.y;
            trail[s * 6 + 5] = p.z;
        }
    }

    private Matrix4f knifeMatrix(float[] at, float weight, float size, Matrix4f dest) {
        float turn = (float)Math.PI / 180 * weight;
        float scale = this.voxel * size;
        dest.translation(this.fist.x + at[0] * weight, this.fist.y + at[1] * weight, this.fist.z + at[2] * weight).rotate((Quaternionfc)new Quaternionf().rotateY(MathHelper.wrapDegrees((float)at[3]) * turn).rotateX(MathHelper.wrapDegrees((float)at[4]) * turn).rotateZ(MathHelper.wrapDegrees((float)at[5]) * turn)).rotate((Quaternionfc)this.rest);
        float flip = MathHelper.wrapDegrees((float)at[6]) * turn;
        if (flip != 0.0f) {
            dest.rotateX(flip);
        }
        return dest.scale(scale).translate(at[11] * weight - this.gripX, -this.gripY, -0.5f);
    }

    private Matrix4f scabbardMatrix(float[] at, float size) {
        boolean held = at[13] >= 1.0f;
        float[] state = held ? at : this.letGo;
        float radius = 312.5f;
        Matrix4f m = this.knifeMatrix(state, 1.0f, size, new Matrix4f()).rotateAround((Quaternionfc)new Quaternionf().rotationZ(state[12] / radius), 3.0f, radius, 0.5f).translate(0.05f, 0.0f, 0.0f);
        if (!held) {
            float fall = 1.0f - Math.max(at[13], 0.0f);
            fall *= fall;
            Vector3f hold = m.transformPosition(this.saddleX, this.saddleY, 0.5f, new Vector3f());
            m.translateLocal(-hold.x, -hold.y, -hold.z).rotateLocalZ(0.6981317f * fall).translateLocal(hold.x + KnifeSkin.SCABBARD_FALL.x * fall, hold.y + KnifeSkin.SCABBARD_FALL.y * fall, hold.z + KnifeSkin.SCABBARD_FALL.z * fall);
        }
        return m;
    }

    private KnifeMesh.Shine wipe(float[] at, float weight) {
        float touch;
        float f = touch = this.centre != null ? MathHelper.clamp((float)((at[14] * weight - 0.7f) / 0.3f), (float)0.0f, (float)1.0f) : 0.0f;
        if (touch <= 0.01f || at[15] < this.bladeFrom) {
            return null;
        }
        float along = (at[15] - this.bladeFrom) / (this.bladeTo - this.bladeFrom);
        return new KnifeMesh.Shine(along, touch * touch * (3.0f - 2.0f * touch), 16.0f);
    }

    float sparkle(float[] at, float weight) {
        float flash = KnifeSkin.sparkle(at[10]);
        if (this.centre != null && at[15] >= this.bladeFrom) {
            float along = (at[15] - this.bladeFrom) / (this.bladeTo - this.bladeFrom);
            float leaving = MathHelper.clamp((float)((along - 0.96f) / 0.06f), (float)0.0f, (float)1.0f) * MathHelper.clamp((float)((1.3f - along) / 0.15f), (float)0.0f, (float)1.0f);
            flash = Math.max(flash, leaving * MathHelper.clamp((float)(at[14] * 1.6f), (float)0.0f, (float)1.0f));
        }
        return flash * weight;
    }

    static float shinePosition(float channel) {
        if (channel <= 0.0f) {
            return 0.0f;
        }
        float f = channel - 2.0f * (float)MathHelper.floor((float)(channel / 2.0f));
        return f < 1.0f ? f : 0.0f;
    }

    static float sparkle(float channel) {
        float f = KnifeSkin.shinePosition(channel);
        return MathHelper.clamp((float)((f - 0.72f) / 0.16f), (float)0.0f, (float)1.0f) * MathHelper.clamp((float)((1.0f - f) / 0.07f), (float)0.0f, (float)1.0f);
    }

    @Environment(value=EnvType.CLIENT)
    private record Part(KnifeMesh mesh, int channel, float pivotX, float pivotY) {
    }

    @Environment(value=EnvType.CLIENT)
    record Streak(int part, float baseX, float baseY, float tipX, float tipY, float strength) {
    }

    @Environment(value=EnvType.CLIENT)
    private static interface Centre {
        public float y(float var1);
    }

    @Environment(value=EnvType.CLIENT)
    static interface Sink {
        public void part(MatrixStack var1, KnifeMesh var2, KnifeMesh.Shine var3);

        public void glow(MatrixStack var1, KnifeMesh var2, KnifeMesh.Shine var3);

        public void arm(MatrixStack var1, boolean var2);
    }
}
