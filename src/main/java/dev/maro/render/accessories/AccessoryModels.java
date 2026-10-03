package dev.maro.render.accessories;

import net.minecraft.client.model.*;
import java.util.*;

/** Immutable meshes, baked once per selected style, shared by slim and classic renderers.
 * No per-player ModelPart rotations: 1.21.11 keeps parts by reference until queue execution. */
public final class AccessoryModels {
    public enum Bone { HEAD, BODY, LEFT_ARM, RIGHT_ARM }
    public enum Motion { STILL, LEFT_WING, RIGHT_WING, TAIL, HALO }
    public record Group(Bone bone, Motion motion, int index, float x, float y, float z, ModelPart primary, ModelPart accent) { }
    private static final Map<String, List<Group>> CACHE = new HashMap<>();
    private AccessoryModels() { }
    public static List<Group> get(String category, String style) {
        if (style.equals("None")) return List.of();
        return CACHE.computeIfAbsent(category + ":" + style, key -> build(category, style));
    }
    private static List<Group> build(String category, String style) {
        List<Builder> groups = new ArrayList<>();
        switch (category) {
            case "head" -> head(groups, style);
            case "wings" -> wings(groups, style);
            case "tail" -> tail(groups, style);
            case "halo" -> halo(groups, style);
            case "shoulders" -> shoulders(groups, style);
            case "back" -> back(groups, style);
        }
        return groups.stream().map(Builder::bake).toList();
    }
    private static Builder group(List<Builder> out, Bone bone, Motion motion, int index, float x, float y, float z) {
        Builder b = new Builder(bone, motion, index, x, y, z); out.add(b); return b;
    }
    private static void head(List<Builder> out, String style) {
        Builder b = group(out, Bone.HEAD, Motion.STILL, 0, 0, -8, 0);
        for (int side : new int[]{-1, 1}) {
            switch (style) {
                case "Dragon Horns", "Devil Horns" -> {
                    boolean dragon = style.equals("Dragon Horns");
                    for (int i = 0; i < 5; i++) {
                        float width = 2.3f - i * .38f;
                        b.box(i == 4, side * (2.5f + i * .24f), -i * 1.35f, dragon ? 1.5f + i * .55f : -1 + i * .15f,
                            -width / 2, -1.8f, -width / 2, width, 2, width, dragon ? -12 - i * 7 : 8, 0, side * (8 + i * 6));
                    }
                }
                case "Cat Ears", "Fox Ears" -> {
                    boolean fox = style.equals("Fox Ears");
                    float height = fox ? 5 : 3.8f;
                    for (int i = 0; i < 4; i++) {
                        float w = 3.1f - i * .65f;
                        b.box(false, side * 2.7f, 0, 0, -w / 2, -height * (i + 1) / 4, -1, w, height / 4 + .1f, 2, 0, 0, side * 12);
                        if (i < 3) b.box(true, side * 2.7f, 0, 0, -w / 2 + .45f, -height * (i + 1) / 4 + .3f, -1.08f, Math.max(.3f, w - .9f), height / 4 - .2f, .2f, 0, 0, side * 12);
                    }
                }
                case "Bunny Ears" -> {
                    b.box(false, side * 2, 0, .5f, -1.2f, -9, -.7f, 2.4f, 9, 1.4f, -8, 0, side * 10);
                    b.box(true, side * 2, 0, .5f, -.6f, -8, -.81f, 1.2f, 6.9f, .2f, -8, 0, side * 10);
                    b.box(false, side * 2, 0, .5f, -.8f, -9.7f, -.5f, 1.6f, 1, 1, -8, 0, side * 10);
                }
                case "Antlers" -> {
                    b.box(false, side * 2.7f, 0, 1, -.55f, -7.5f, -.55f, 1.1f, 7.5f, 1.1f, -15, 0, side * 20);
                    for (int i = 0; i < 3; i++) b.box(i == 2, side * (3.1f + i * .5f), -2.1f - i * 1.8f, 1.5f,
                        -.4f, -3, -.4f, .8f, 3, .8f, -22, 0, side * 55);
                }
                case "Headphones" -> {
                    b.box(false, side * 4.3f, 2, 0, -.9f, -.5f, -1.8f, 1.8f, 4, 3.6f, 0, 0, 0);
                    b.box(true, side * 5.25f, 2, 0, -.2f, .15f, -1, .4f, 2.7f, 2, 0, 0, 0);
                    b.box(false, side * 3.7f, 0, 0, -.55f, -1.2f, -.6f, 1.1f, 3, 1.2f, 0, 0, side * -20);
                }
            }
        }
        if (style.equals("Headphones")) b.cube(true, -3.6f, -1.1f, -.65f, 7.2f, .8f, 1.3f);
        if (style.equals("Crown")) {
            for (int side : new int[]{-1, 1}) {
                b.cube(false, -4.4f, -.7f, side < 0 ? -4.4f : 3.6f, 8.8f, 1.8f, .8f);
                b.cube(false, side < 0 ? -4.4f : 3.6f, -.7f, -3.6f, .8f, 1.8f, 7.2f);
            }
            for (int i = 0; i < 8; i++) {
                float angle = i * 45;
                b.box(false, 0, 0, 0, -.55f, -3.5f, -4.4f, 1.1f, 3, .85f, 0, angle, 0);
                b.box(true, 0, 0, 0, -.4f, -2.1f, -4.55f, .8f, .9f, .2f, 0, angle, 0);
            }
        }
    }
    private static void wings(List<Builder> out, String style) {
        for (int s : new int[]{-1, 1}) {
            Builder b = group(out, Bone.BODY, s < 0 ? Motion.RIGHT_WING : Motion.LEFT_WING, 0, s * 2.3f, 2, 2.8f);
            if (style.equals("Angel")) {
                b.box(false, 0, 0, 0, s < 0 ? -11 : 0, -.7f, -.65f, 11, 1.4f, 1.3f, 0, 0, -s * 28);
                for (int i = 0; i < 9; i++) {
                    float x = s * (2 + i * 1.15f), y = -i * .52f;
                    b.box(false, x, y, .2f, -.9f, 0, -.45f, 1.8f, 7 + i * .5f, .9f, 0, 0, -s * (15 + i * 4));
                    b.box(true, x, y, -.32f, -.55f, .5f, -.2f, 1.1f, 2.4f, .4f, 0, 0, -s * (15 + i * 4));
                }
                for (int i = 0; i < 5; i++) b.box(false, s * (2 + i * 1.4f), -i * .6f, -.7f, -.8f, 0, -.35f, 1.6f, 3.4f, .7f, 0, 0, -s * 30);
            } else if (style.equals("Butterfly")) {
                for (int i = 0; i < 6; i++) {
                    float x = s * (1 + i * 1.35f), h = 11 - Math.abs(i - 3) * 1.5f;
                    b.cube(false, x - .85f, -h * .65f, -.6f, 1.7f, h, 1.2f);
                    b.cube(true, x - .58f, -h * .65f + .7f, -.71f, 1.16f, h - 1.4f, 1.42f);
                    if (i > 1) b.cube(false, x - .4f, -h * .25f, -.82f, .8f, 1.4f, 1.64f);
                    b.cube(false, x - .7f, 3.5f, -.4f, 1.4f, Math.max(2, 6 - Math.abs(i - 2) * 1.3f), .8f);
                }
            } else if (style.equals("Cyber")) {
                b.box(false, 0, 0, 0, s < 0 ? -8 : 0, -1, -.7f, 8, 2, 1.4f, 0, 0, -s * 25);
                for (int i = 0; i < 4; i++) {
                    b.box(false, s * (3 + i * 2), -i * .8f, 0, -1, 0, -.65f, 2, 7.5f - i, 1.3f, 0, 0, -s * 32);
                    b.box(true, s * (3 + i * 2), -i * .8f, 0, -.5f, .8f, -.76f, 1, 5.5f - i, 1.52f, 0, 0, -s * 32);
                }
            } else {
                boolean dragon = style.equals("Dragon");
                b.box(false, 0, 0, 0, s < 0 ? -12 : 0, -.65f, -.65f, 12, 1.3f, 1.3f, 0, 0, -s * 25);
                // Tapered membrane strips form the bat silhouette; ribs lie above the membrane.
                for (int i = 0; i < 11; i++) {
                    float x = s * (1 + i), top = -i * .45f;
                    float depth = (i % 4 == 0 ? 8 : 6) - i * .25f;
                    b.cube(true, x - .55f, top, -.2f, 1.1f, depth, .4f);
                }
                for (int i = 0; i < 4; i++) {
                    b.box(false, s * (1.8f + i * 2.8f), -i * 1.15f, 0, -.35f, 0, -.45f, .7f, 7.6f - i * .8f, .9f, 0, 0, s * (5 + i * 5));
                    if (dragon) b.box(false, s * (3 + i * 2.6f), -i * 1.1f, 0, -.4f, -2.4f, -.4f, .8f, 2.5f, .8f, 0, 0, s * 24);
                }
            }
        }
    }
    private static void tail(List<Builder> out, String style) {
        for (int i = 0; i < 7; i++) {
            Builder b = group(out, Bone.BODY, Motion.TAIL, i, 0, 10.3f, 2.7f);
            float width = switch (style) {
                case "Fox" -> 2.2f + (i < 3 ? i * .65f : (6 - i) * .65f);
                case "Dragon" -> 2.5f - i * .27f;
                case "Cyber" -> 1.5f;
                default -> 1.25f - i * .06f;
            };
            b.cube(style.equals("Fox") && i >= 5, -width / 2, -width / 2, -.15f, width, width, 2.55f);
            if (style.equals("Dragon")) b.box(true, 0, -width / 2, 1, -.32f, -1.5f, -.32f, .64f, 1.7f, .64f, -28, 0, 0);
            if (style.equals("Cyber")) b.cube(true, -.8f, -.8f, 1.3f, 1.6f, 1.6f, .5f);
            if (style.equals("Devil") && i == 6) for (int s : new int[]{-1, 1})
                b.box(true, 0, 0, 2.1f, s < 0 ? -2 : 0, -.4f, -.4f, 2, .8f, .8f, 0, s * -32, 0);
        }
    }
    private static void halo(List<Builder> out, String style) {
        Builder b = group(out, Bone.HEAD, Motion.HALO, 0, 0, -8, 0);
        int rings = style.equals("Double Ring") ? 2 : 1;
        for (int ring = 0; ring < rings; ring++) {
            float radius = 5 + ring * 1.3f;
            for (int i = 0; i < 24; i++) {
                float angle = i * 15;
                if (style.equals("Orbit")) {
                    if (i % 4 == 0) b.box(true, 0, -ring, 0, -.55f, -.55f, -radius, 1.1f, 1.1f, 1.1f, 20, angle, 45);
                } else b.box(true, 0, -ring * 1.4f, 0, -.72f, -.25f, -radius, 1.44f, .5f, .65f, 0, angle, 0);
            }
        }
        if (style.equals("Star")) for (int i = 0; i < 5; i++)
            b.box(true, 0, -1, 0, -.3f, -.3f, -2.8f, .6f, .6f, 3, 0, i * 72, 0);
    }
    private static void shoulders(List<Builder> out, String style) {
        for (int s : new int[]{-1, 1}) {
            Builder b = group(out, s < 0 ? Bone.RIGHT_ARM : Bone.LEFT_ARM, Motion.STILL, 0, 0, 0, 0);
            // Arm origins sit at +/-5; the shoulder cap stays centered for slim and classic skins.
            b.cube(false, -2.15f, -2.5f, -2.65f, 4.3f, 2.4f, 5.3f);
            if (style.equals("Pauldrons")) {
                b.cube(true, -2.25f, -2.6f, -2.8f, 4.5f, .5f, 5.6f);
                b.cube(false, s < 0 ? -2.7f : 1.3f, -1.8f, -2.2f, 1.4f, 3.7f, 4.4f);
            } else for (int i = 0; i < 3; i++) {
                float h = style.equals("Crystals") ? 3.6f : 2.6f;
                b.box(true, 0, -2.5f, (i - 1) * 1.5f, -.55f, -h, -.55f, 1.1f, h, 1.1f, (i - 1) * 12, 0, s * 18);
                b.box(true, 0, -2.5f, (i - 1) * 1.5f, -.3f, -h - .8f, -.3f, .6f, 1, .6f, (i - 1) * 12, 0, s * 18);
            }
        }
    }
    private static void back(List<Builder> out, String style) {
        Builder b = group(out, Bone.BODY, Motion.STILL, 0, 0, 5, 2.7f);
        switch (style) {
            case "Backpack" -> {
                b.cube(false, -3, -3, 0, 6, 8, 3.5f); b.cube(true, -2.5f, 1, 3.55f, 5, 3, .8f);
                b.cube(true, -2.8f, -3.2f, -.1f, 5.6f, 1.2f, 3.8f);
                for (int s : new int[]{-1, 1}) b.cube(false, s * 3 - .65f, -.8f, .6f, 1.3f, 3.8f, 2.3f);
            }
            case "Jetpack" -> {
                b.cube(false, -2, -2.5f, 0, 4, 6.5f, 2.5f);
                for (int s : new int[]{-1, 1}) {
                    b.cube(false, s * 2.5f - 1, -3.5f, .4f, 2, 7.5f, 2.6f);
                    b.cube(true, s * 2.5f - .8f, 4, .7f, 1.6f, 1.4f, 2);
                    b.cube(true, s * 2.5f - 1.1f, -2.3f, .25f, 2.2f, .6f, 2.9f);
                }
            }
            case "Sword" -> {
                b.box(false, 0, 0, 0, -.9f, -7, .3f, 1.8f, 17, 1.3f, 0, 0, -32);
                b.box(true, 0, 0, 0, -.65f, -6.9f, 1.68f, 1.3f, 15, .2f, 0, 0, -32);
                b.box(true, 0, 0, 0, -3, -7.9f, .1f, 6, .8f, 1.7f, 0, 0, -32);
                b.box(false, 0, 0, 0, -.6f, -11.2f, .4f, 1.2f, 3.5f, 1.1f, 0, 0, -32);
            }
        }
    }
    private static final class Builder {
        private final Bone bone; private final Motion motion; private final int index;
        private final float x, y, z;
        private final ModelData primary = new ModelData(), accent = new ModelData();
        private int count, mainCount, accentCount;
        Builder(Bone bone, Motion motion, int index, float x, float y, float z) {
            this.bone = bone; this.motion = motion; this.index = index; this.x = x; this.y = y; this.z = z;
        }
        void cube(boolean detail, float x, float y, float z, float w, float h, float d) { box(detail, 0, 0, 0, x, y, z, w, h, d, 0, 0, 0); }
        void box(boolean detail, float px, float py, float pz, float x, float y, float z, float w, float h, float d, float rx, float ry, float rz) {
            ModelData data = detail ? accent : primary;
            data.getRoot().addChild("box" + count++, ModelPartBuilder.create().uv(0, 0).cuboid(x, y, z, w, h, d),
                ModelTransform.of(px, py, pz, (float)Math.toRadians(rx), (float)Math.toRadians(ry), (float)Math.toRadians(rz)));
            if (detail) accentCount++; else mainCount++;
        }
        Group bake() {
            return new Group(bone, motion, index, x, y, z,
                mainCount == 0 ? null : TexturedModelData.of(primary, 128, 128).createModel(),
                accentCount == 0 ? null : TexturedModelData.of(accent, 128, 128).createModel());
        }
    }
}
