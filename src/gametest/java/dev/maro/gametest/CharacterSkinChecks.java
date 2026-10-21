package dev.maro.gametest;

import dev.maro.render.character.gltf.CharacterBake;
import dev.maro.render.character.gltf.ImageDecoder;
import dev.maro.render.character.gltf.SkinBake;
import dev.maro.render.character.kit.SkinImages;
import dev.maro.render.character.skin.Animator;
import dev.maro.render.character.skin.Human;
import dev.maro.render.character.skin.MotionInput;
import dev.maro.render.character.skin.SkinBody;
import dev.maro.render.character.skin.SkinFormat;
import dev.maro.render.character.skin.SkinLevel;
import dev.maro.render.character.skin.SkinnedCharacter;
import dev.maro.render.character.skin.Skinner;
import dev.maro.render.emote.Emote;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * The skinned pipeline end to end, without drawing: every bundled GLB is baked (skeleton, weights,
 * levels, textures), written and read back, then posed through every movement state and every
 * emote frame by frame and skinned at all three levels. Every skinned position and normal must be
 * finite and the body must stay a body: feet near the floor where grounded, inside a sane box.
 * The shipped .anc files and the bendable default skin go through the same.
 */
final class CharacterSkinChecks {
    private static final String[] BUNDLED = {"spongebob", "lebron", "chun_li", "skeleton", "jeffrey_epstein", "donald_trump", "michael_jackson",
            "roblox_bacon_hair", "verity", "minion", "travis_scott", "undead_fuhrer"};

    private CharacterSkinChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run() {
        List<String> report = new ArrayList<>();
        long start = System.nanoTime();
        Path dir = Path.of(System.getProperty("maro.gametest.projectDir", "."), "src", "main", "resources", "assets", "maro", "characters");
        int glbs = 0;
        for (String id : BUNDLED) {
            Path glb = dir.resolve(id + ".glb");
            if (!Files.isRegularFile(glb)) continue;
            try {
                long t0 = System.nanoTime();
                SkinFormat.Baked baked = SkinBake.build(Files.readAllBytes(glb), new CharacterBake.Options(), CharacterSkinChecks::decode);
                require(baked != null, id + ": the skinned bake gave nothing");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                SkinFormat.write(new DataOutputStream(bytes), baked);
                SkinnedCharacter c = read(new ByteArrayInputStream(bytes.toByteArray()), id);
                report.add(exercise(id + " (glb)", c) + String.format(Locale.ROOT, ", baked in %d ms", (System.nanoTime() - t0) / 1_000_000));
                glbs++;
            } catch (IOException e) {
                throw new AssertionError(id + ": " + e, e);
            }
        }
        require(glbs == BUNDLED.length || glbs == 0, "only " + glbs + " of the bundled GLBs were found in " + dir);
        for (String id : BUNDLED) {
            try (InputStream in = CharacterSkinChecks.class.getResourceAsStream("/assets/maro/characters/" + id + ".anc")) {
                require(in != null, id + ".anc is missing from the jar");
                report.add(exercise(id + " (anc)", read(in, id)));
            } catch (IOException e) {
                throw new AssertionError(id + ": " + e, e);
            }
        }
        report.add(exercise("default skin", SkinBody.of(false)));
        report.add(exercise("default skin (slim)", SkinBody.of(true)));
        for (String line : report) System.out.println("[character-skin] " + line);
        System.out.printf(Locale.ROOT, "[character-skin] %d GLBs and %d shipped characters skinned through %d states and %d emotes in %d ms%n",
                glbs, BUNDLED.length, STATES.length, Emote.values().length, (System.nanoTime() - start) / 1_000_000);
    }

    private static SkinnedCharacter read(InputStream raw, String id) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        require(in.readInt() == SkinFormat.MAGIC, id + ": not a character file");
        int version = in.readInt();
        require(version == SkinFormat.VERSION, id + ": version " + version + ", not the skinned version " + SkinFormat.VERSION);
        return SkinFormat.read(in);
    }

    private static ImageDecoder.Picture decode(byte[] data) throws IOException {
        int[] size = new int[2];
        int[] argb = SkinImages.decode(data, size);
        return new ImageDecoder.Picture(size[0], size[1], argb);
    }

    /** A movement state: how to set the input at a time, and whether the feet must stay on the floor. */
    private record State(String name, boolean grounded, BiConsumer<MotionInput, Float> setup) {
    }

    private static final State[] STATES = {
            new State("idle", true, (m, t) -> { }),
            new State("walk", true, (m, t) -> m.speed = 4.3f),
            new State("sprint", true, (m, t) -> { m.speed = 5.6f; m.sprinting = true; }),
            new State("back", true, (m, t) -> { m.speed = 3f; m.forward = -3f; }),
            new State("crouch", true, (m, t) -> m.sneaking = true),
            new State("creep", true, (m, t) -> { m.sneaking = true; m.speed = 1.3f; }),
            new State("jump", false, (m, t) -> { m.grounded = t < 0.3f; m.vertical = t < 0.3f ? 0 : 4.2f - (t - 0.3f) * 20; }),
            new State("land", false, (m, t) -> { m.grounded = t < 0.2f || t > 1.0f; m.vertical = t < 0.2f || t > 1.0f ? 0 : -12; }),
            new State("swim", false, (m, t) -> { m.swimming = true; m.inWater = true; m.speed = 2.5f; }),
            new State("ride", false, (m, t) -> m.riding = true),
            new State("sleep", false, (m, t) -> m.sleeping = true),
            new State("glide", false, (m, t) -> m.gliding = true),
            new State("attack", true, (m, t) -> { m.rightArm[0] = -2f; m.rightArmWeight = 1; m.rightElbow = 12; m.bodyTwist = 0.3f; m.headYaw = 30; m.headPitch = 20; }),
    };

    private static String exercise(String name, SkinnedCharacter c) {
        float height = c.rig.height;
        float[] buf = new float[0];
        long skinNanos = 0;
        int frames = 0;
        float worstFloat = 0;
        for (State s : STATES) {
            Animator a = new Animator(c.rig, c.clips);
            MotionInput in = new MotionInput();
            for (float t = 0; t <= 1.6f; t += 1 / 30f) {
                in.reset();
                in.time = t;
                s.setup.accept(in, t);
                a.update(in);
                for (int level = 0; level < c.levels.length; level++) {
                    SkinLevel lv = c.levels[level];
                    if (buf.length < lv.vertices * 6) buf = new float[lv.vertices * 6];
                    long t0 = System.nanoTime();
                    Skinner.skin(lv, a.out.skin, 0, lv.vertices, buf);
                    skinNanos += System.nanoTime() - t0;
                    frames++;
                    float low = check(name + " " + s.name + " at " + t, lv, buf, height);
                    if (s.grounded && t > 0.5f && c.rig.has(Human.LEFT_FOOT)) worstFloat = Math.max(worstFloat, low);
                }
            }
        }
        for (Emote e : Emote.values()) {
            Animator a = new Animator(c.rig, c.clips);
            MotionInput in = new MotionInput();
            float end = e.loops() ? e.length() * 1.5f : e.length() + 0.4f;
            for (float t = 0; t <= end; t += 1 / 30f) {
                in.reset();
                in.time = t;
                in.emote = e.clip();
                in.emoteTime = e.loops() ? t : Math.min(t, e.length());
                in.emoteBlend = Math.min(1, t / 0.18f);
                a.update(in);
                SkinLevel lv = c.level(2);
                if (buf.length < lv.vertices * 6) buf = new float[lv.vertices * 6];
                Skinner.skin(lv, a.out.skin, 0, lv.vertices, buf);
                frames++;
                check(name + " " + e.label() + " at " + t, lv, buf, height);
            }
        }
        // A grounded state keeps the lowest point of the body on the floor: no floating, no sinking.
        require(worstFloat < height * 0.06f, name + ": standing feet drift " + worstFloat + " units off the floor");
        SkinLevel high = c.level(2);
        return String.format(Locale.ROOT, "%s: %d bones, %d/%d/%d triangles, %d materials, %d clips, skinning %.3f ms a level, feet within %.2f",
                name, c.rig.nodes, c.levels[0].triangles(), c.levels[1].triangles(), high.triangles(), c.materials.length, c.clips.length,
                skinNanos / 1e6 / Math.max(1, frames), worstFloat);
    }

    /** Every value finite and the body inside a box a few heights across; returns how far the lowest point is from the floor. */
    private static float check(String what, SkinLevel lv, float[] buf, float height) {
        float low = Float.POSITIVE_INFINITY;
        for (int v = 0; v < lv.vertices; v++) {
            int o = v * 6;
            for (int k = 0; k < 6; k++) require(Float.isFinite(buf[o + k]), what + ": vertex " + v + " is not finite");
            float x = buf[o], y = buf[o + 1], z = buf[o + 2];
            require(Math.abs(x) < height * 1.6f && Math.abs(z) < height * 1.6f && y > -height * 0.8f && y < height * 1.8f,
                    what + ": vertex " + v + " flew off to (" + x + ", " + y + ", " + z + ")");
            low = Math.min(low, y);
        }
        return Math.abs(low);
    }
}
