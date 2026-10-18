// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class CharacterBuilder {
    private static final float DECAL_RESERVE = 0.1f;
    private static final float AO_STEP = 0.38f;
    private static final int MIN_TRIANGLES = 24;
    private static final float MAX_EXTENT = 160.0f;
    private static final float OUTLINE_SHARE = 0.5f;
    private static final float BURIED = 0.02f;
    private static final float SINK = 0.12f;
    private static final int CORNER = 10;

    private CharacterBuilder() {
    }

    public static CharacterModel build(CharacterDesign design, Detail detail) {
        if (design instanceof BakedDesign) {
            BakedDesign baked = (BakedDesign)design;
            return BakedModel.load(baked, detail);
        }
        long start = System.nanoTime();
        Rig rig = design.rig();
        List<Part> order = CharacterBuilder.order(rig);
        int n = order.size();
        if (n == 0) {
            throw new IllegalStateException(design.id() + ": the design defines no parts");
        }
        IdentityHashMap<Part, Integer> index = new IdentityHashMap<Part, Integer>();
        for (int i2 = 0; i2 < n; ++i2) {
            index.put(order.get(i2), i2);
        }
        ArrayList<Shape> solids = new ArrayList<>();
        ArrayList<Canvas> canvases = new ArrayList<Canvas>();
        Shape[] shapes = new Shape[n];
        float[] steps = new float[n];
        for (int i3 = 0; i3 < n; ++i3) {
            Part p = order.get(i3);
            if (p.shape != null) {
                Shape s = p.shape;
                if (!((Shape)s).bounded()) {
                    throw new IllegalStateException(p.name + ": shape is unbounded; intersect planes with a box");
                }
                if (((Shape)s).maxX - ((Shape)s).minX > 160.0f || ((Shape)s).maxY - ((Shape)s).minY > 160.0f || ((Shape)s).maxZ - ((Shape)s).minZ > 160.0f) {
                    throw new IllegalStateException(p.name + ": shape is larger than 160.0 units");
                }
                steps[i3] = SurfaceNets.fitStep(((Shape)s).maxX - ((Shape)s).minX, ((Shape)s).maxY - ((Shape)s).minY, ((Shape)s).maxZ - ((Shape)s).minZ, detail.step * p.resolution);
                float half = steps[i3] * 0.75f;
                shapes[i3] = s = ((Shape)s).map(x -> {
                    Shape shape;
                    if (x instanceof Strand) {
                        Strand strand = (Strand)x;
                        shape = strand.thickened(half);
                    } else {
                        shape = x;
                    }
                    return shape;
                });
                solids.add(s);
            }
            for (Decal d : p.decals) {
                canvases.add(d.canvas);
            }
        }
        Shape world = solids.isEmpty() ? null : Shapes.union(solids.toArray(new Shape[0]));
        Shape[] covers = CharacterBuilder.covers(order, shapes);
        Atlas atlas = Atlas.pack(canvases);
        float[] thickness = new float[n];
        for (int i4 = 0; i4 < n; ++i4) {
            Part p = order.get(i4);
            thickness[i4] = p.outline >= 0.0f ? p.outline : rig.outline;
        }
        Mesh[] fine = new Mesh[n];
        float[] shown = new float[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            Shape s = shapes[i];
            if (s == null) {
                return;
            }
            fine[i] = SurfaceNets.mesh(s, s.minX, s.minY, s.minZ, s.maxX, s.maxY, s.maxZ, steps[i]);
            shown[i] = CharacterBuilder.visible(fine[i], covers[i]);
        });
        int[] baseTarget = new int[n];
        CharacterBuilder.budget(order, fine, shown, detail, baseTarget, thickness);
        Mesh[] base = new Mesh[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            if (fine[i] == null || fine[i].triCount == 0) {
                return;
            }
            Simplifier s = new Simplifier(fine[i]);
            s.reduce(baseTarget[i]);
            base[i] = s.snapshot();
        });
        PartMesh[] parts = new PartMesh[n];
        float[] extent = new float[]{Float.MAX_VALUE, -3.4028235E38f};
        int[] slots = new int[n];
        int slotCount = 0;
        for (int i5 = 0; i5 < n; ++i5) {
            Part p = order.get(i5);
            slots[i5] = p.extra && p.motion != null ? slotCount++ : -1;
        }
        IntStream.range(0, n).parallel().forEach(i -> {
            boolean jointed;
            Motion motion;
            Part p = (Part)order.get(i);
            Surface baseSurface = null;
            Surface hullSurface = null;
            Surface decalSurface = null;
            if (base[i] != null && base[i].triCount > 0) {
                Mesh kept;
                float flat;
                float strength = p.occlusion >= 0.0f ? p.occlusion : rig.occlusion;
                Shaded shaded = CharacterBuilder.shade(shapes[i], world, base[i], steps[i], strength, flat = p.flat >= 0.0f ? p.flat : rig.flat);
                baseSurface = CharacterBuilder.baseSurface(shaded, kept = CharacterBuilder.bury(shaded, base[i], covers[i], steps[i]), p, atlas);
                if (baseSurface != null && thickness[i] > 0.0f) {
                    int color = p.outlineColorSet ? p.outlineColor : rig.outlineColor;
                    hullSurface = CharacterBuilder.hullSurface(shapes[i], shaded, baseSurface, p, thickness[i], steps[i], color, atlas);
                }
                if (!p.decals.isEmpty()) {
                    decalSurface = CharacterBuilder.decalSurface(p, shaded, kept, atlas);
                }
                synchronized (extent) {
                for (int v = 0; v < shaded.count; ++v) {
                    extent[0] = Math.min(extent[0], shaded.pos[v * 3 + 1]);
                    extent[1] = Math.max(extent[1], shaded.pos[v * 3 + 1]);
                }
                }
            }
            int parent = p.parent == null ? -1 : (Integer)index.get(p.parent);
            Motion motion2 = motion = p.extra ? p.motion : null;
            if (motion != null && motion.bendPower() > 0.0f) {
                float reach = CharacterBuilder.reach(baseSurface);
                CharacterBuilder.weigh(baseSurface, reach, motion.bendPower());
                if (hullSurface != null && baseSurface != null) {
                    hullSurface.weight = baseSurface.weight;
                }
                CharacterBuilder.weigh(decalSurface, reach, motion.bendPower());
            }
            boolean limb = !p.extra && (p.bone.arm() || p.bone.leg());
            Part owner = p;
            while (owner != null && !owner.jointSet) {
                owner = owner.parent;
            }
            boolean bl = jointed = owner != null;
            if (jointed) {
                CharacterBuilder.jointWeights(baseSurface, p, owner);
                CharacterBuilder.jointWeights(decalSurface, p, owner);
                if (p.name.startsWith("prop_")) {
                    for (Surface surface : new Surface[]{baseSurface, decalSurface}) if (surface != null) {
                        surface.joint = new float[surface.vertexCount()];
                        Arrays.fill(surface.joint, 1.0f);
                    }
                }
                if (hullSurface != null && baseSurface != null) {
                    hullSurface.joint = baseSurface.joint;
                }
            }
            parts[i] = new PartMesh(p.name, p.bone, parent, p.extra, p.pivotX, p.pivotY, p.pivotZ, p.gripX(), p.gripY(), p.gripZ(), motion, baseSurface, hullSurface, decalSurface, slots[i], limb ? p.swing : 1.0f, limb ? p.swingLimit : 0.0f, jointed, jointed ? owner.jointX - p.pivotX : 0.0f, jointed ? owner.jointY - p.pivotY : 0.0f, jointed ? owner.jointZ - p.pivotZ : 0.0f, jointed && owner.neck, jointed ? owner.lookPitch : 0.0f, jointed ? owner.lookYaw : 0.0f, jointed ? owner.lookLimit : 0.0f);
        });
        long finished = System.nanoTime();
        float hip = 12.0f;
        Part rl = rig.bones[Bone.RIGHT_LEG.ordinal()];
        Part ll = rig.bones[Bone.LEFT_LEG.ordinal()];
        if (rl != null && ll != null) {
            hip = (rl.pivotY + ll.pivotY) * 0.5f;
        } else if (rl != null) {
            hip = rl.pivotY;
        } else if (ll != null) {
            hip = ll.pivotY;
        }
        float minY = extent[0] == Float.MAX_VALUE ? 0.0f : extent[0];
        float maxY = extent[1] == -3.4028235E38f ? 32.0f : extent[1];
        long total = (finished - start) / 1000000L;
        return new CharacterModel(design.id(), design.name(), detail, rig.scale, parts, slotCount, atlas.width, atlas.height, atlas.mips, atlas.whiteU(), atlas.whiteV(), minY, maxY, hip, rig.firstPersonArms, total);
    }

    private static List<Part> order(Rig rig) {
        ArrayList<Part> list = new ArrayList<Part>();
        for (Part p : rig.bones) {
            if (p == null) continue;
            list.add(p);
        }
        list.addAll(rig.extras);
        return list;
    }

    private static Shape[] covers(List<Part> order, Shape[] shapes) {
        int n = order.size();
        Part[] root = new Part[n];
        for (int i = 0; i < n; ++i) {
            Part r = order.get(i);
            while (r.extra && r.motion == null && r.parent != null) {
                r = r.parent;
            }
            root[i] = r;
        }
        Shape[] covers = new Shape[n];
        for (int i = 0; i < n; ++i) {
            if (shapes[i] == null) continue;
            ArrayList<Shape> near = new ArrayList<Shape>();
            for (int j = 0; j < n; ++j) {
                if (j == i || shapes[j] == null || root[j] != root[i] || !CharacterBuilder.overlaps(shapes[i], shapes[j])) continue;
                near.add(shapes[j]);
            }
            if (near.isEmpty()) continue;
            covers[i] = Shapes.union(near.toArray(new Shape[0]));
        }
        return covers;
    }

    private static boolean overlaps(Shape a, Shape b) {
        return a.minX <= b.maxX && b.minX <= a.maxX && a.minY <= b.maxY && b.minY <= a.maxY && a.minZ <= b.maxZ && b.minZ <= a.maxZ;
    }

    private static float visible(Mesh mesh, Shape cover) {
        if (cover == null || mesh.triCount == 0) {
            return 1.0f;
        }
        boolean[] buried = new boolean[mesh.vertexCount];
        for (int v = 0; v < mesh.vertexCount; ++v) {
            buried[v] = cover.distance(mesh.pos[v * 3], mesh.pos[v * 3 + 1], mesh.pos[v * 3 + 2]) < -0.02f;
        }
        int hidden = 0;
        for (int t = 0; t < mesh.triCount; ++t) {
            if (!buried[mesh.tris[t * 3]] || !buried[mesh.tris[t * 3 + 1]] || !buried[mesh.tris[t * 3 + 2]]) continue;
            ++hidden;
        }
        return Math.max(0.05f, 1.0f - (float)hidden / (float)mesh.triCount);
    }

    private static void budget(List<Part> order, Mesh[] fine, float[] visible, Detail detail, int[] baseTarget, float[] thickness) {
        int n = order.size();
        float total = 0.0f;
        float outlined = 0.0f;
        float[] weight = new float[n];
        for (int i = 0; i < n; ++i) {
            Part p = order.get(i);
            if (fine[i] == null) continue;
            weight[i] = (float)fine[i].triCount * visible[i] * p.detail;
            total += weight[i];
            if (!(thickness[i] > 0.0f)) continue;
            outlined += weight[i];
        }
        if (total <= 0.0f) {
            return;
        }
        float available = (float)detail.budget * 0.9f;
        float share = available / (1.0f + outlined / total);
        for (int i = 0; i < n; ++i) {
            if (fine[i] == null) continue;
            int target = Math.max(24, Math.round(share * weight[i] / total / visible[i]));
            baseTarget[i] = Math.min(target, fine[i].triCount);
        }
    }

    private static Shaded shade(Shape shape, Shape world, Mesh mesh, float step, float occlusion, float flat) {
        Shaded out = new Shaded(mesh.vertexCount);
        int chunks = (mesh.vertexCount + 255) / 256;
        IntStream.range(0, chunks).parallel().forEach(c -> {
            float[] p = new float[3];
            float[] g = new float[3];
            Sample sample = new Sample();
            int end = Math.min(mesh.vertexCount, (c + 1) * 256);
            for (int v = c * 256; v < end; ++v) {
                p[0] = mesh.pos[v * 3];
                p[1] = mesh.pos[v * 3 + 1];
                p[2] = mesh.pos[v * 3 + 2];
                CharacterBuilder.project(shape, p, g, step);
                System.arraycopy(p, 0, out.pos, v * 3, 3);
                System.arraycopy(g, 0, out.nrm, v * 3, 3);
                shape.sample(p[0], p[1], p[2], sample);
                float ao = CharacterBuilder.occlusion(world, p[0], p[1], p[2], g[0], g[1], g[2], sample.occlusion(occlusion));
                ao += (1.0f - ao) * Math.min(1.0f, sample.glow);
                out.ao[v] = ao;
                out.flat[v] = sample.flat(flat);
                out.glow[v] = (byte)(sample.glow >= 0.5f ? 1 : 0);
                out.color[v] = 0xFF000000 | Sample.channel(sample.r * ao) << 16 | Sample.channel(sample.g * ao) << 8 | Sample.channel(sample.b * ao);
            }
        });
        return out;
    }

    private static Mesh bury(Shaded s, Mesh mesh, Shape cover, float step) {
        if (cover == null) {
            return mesh;
        }
        float[] depth = new float[s.count];
        int chunks = (s.count + 255) / 256;
        IntStream.range(0, chunks).parallel().forEach(c -> {
            float[] p = new float[3];
            float[] g = new float[3];
            int end = Math.min(s.count, (c + 1) * 256);
            for (int v = c * 256; v < end; ++v) {
                float d;
                p[0] = s.pos[v * 3];
                p[1] = s.pos[v * 3 + 1];
                p[2] = s.pos[v * 3 + 2];
                depth[v] = d = cover.distance(p[0], p[1], p[2]);
                if (!(d < 0.0f)) continue;
                CharacterBuilder.gradient(cover, p, Math.max(0.01f, step * 0.3f), g);
                float sink = Math.min(0.12f, -2.0f * d);
                s.pos[v * 3] = p[0] - g[0] * sink;
                s.pos[v * 3 + 1] = p[1] - g[1] * sink;
                s.pos[v * 3 + 2] = p[2] - g[2] * sink;
            }
        });
        s.cover = depth;
        int[] kept = new int[mesh.triCount * 3];
        int count = 0;
        for (int t = 0; t < mesh.triCount; ++t) {
            float cz;
            float cy;
            float cx;
            int a = mesh.tris[t * 3];
            int b = mesh.tris[t * 3 + 1];
            int c2 = mesh.tris[t * 3 + 2];
            if (depth[a] < -0.02f && depth[b] < -0.02f && depth[c2] < -0.02f && cover.distance(cx = (mesh.pos[a * 3] + mesh.pos[b * 3] + mesh.pos[c2 * 3]) / 3.0f, cy = (mesh.pos[a * 3 + 1] + mesh.pos[b * 3 + 1] + mesh.pos[c2 * 3 + 1]) / 3.0f, cz = (mesh.pos[a * 3 + 2] + mesh.pos[b * 3 + 2] + mesh.pos[c2 * 3 + 2]) / 3.0f) < -0.02f) continue;
            kept[count * 3] = a;
            kept[count * 3 + 1] = b;
            kept[count * 3 + 2] = c2;
            ++count;
        }
        if (count == mesh.triCount) {
            return mesh;
        }
        return new Mesh(mesh.pos, mesh.vertexCount, Arrays.copyOf(kept, count * 3), count);
    }

    static void project(Shape shape, float[] p, float[] n, float step) {
        CharacterBuilder.project(shape, p, n, step, 0.0f);
    }

    static void project(Shape shape, float[] p, float[] n, float step, float iso) {
        float eps = Math.max(0.01f, step * 0.3f);
        float tolerance = step * 0.01f;
        float d = shape.distance(p[0], p[1], p[2]) - iso;
        CharacterBuilder.gradient(shape, p, eps, n);
        for (int it = 0; it < 2 && Math.abs(d) > tolerance; ++it) {
            float move = Math.max(-step, Math.min(step, d));
            p[0] = p[0] - n[0] * move;
            p[1] = p[1] - n[1] * move;
            p[2] = p[2] - n[2] * move;
            d = shape.distance(p[0], p[1], p[2]) - iso;
            CharacterBuilder.gradient(shape, p, eps, n);
        }
    }

    static void gradient(Shape shape, float[] p, float eps, float[] out) {
        float gz;
        float gy;
        float x = p[0];
        float y = p[1];
        float z = p[2];
        float gx = shape.distance(x + eps, y, z) - shape.distance(x - eps, y, z);
        float l = (float)Math.sqrt(gx * gx + (gy = shape.distance(x, y + eps, z) - shape.distance(x, y - eps, z)) * gy + (gz = shape.distance(x, y, z + eps) - shape.distance(x, y, z - eps)) * gz);
        if (l < 1.0E-12f) {
            out[0] = 0.0f;
            out[1] = 1.0f;
            out[2] = 0.0f;
            return;
        }
        out[0] = gx / l;
        out[1] = gy / l;
        out[2] = gz / l;
    }

    private static float occlusion(Shape world, float x, float y, float z, float nx, float ny, float nz, float strength) {
        if (world == null || strength <= 0.0f) {
            return 1.0f;
        }
        float occ = 0.0f;
        float total = 0.0f;
        float w = 1.0f;
        for (int i = 1; i <= 5; ++i) {
            float h = 0.38f * (float)i;
            float d = world.distance(x + nx * h, y + ny * h, z + nz * h);
            occ += Math.max(0.0f, h - d) * w;
            total += h * w;
            w *= 0.72f;
        }
        float k = Math.min(1.0f, occ / total * 1.6f);
        return 1.0f - strength * k * (1.4f - 0.4f * k);
    }

    private static Surface baseSurface(Shaded s, Mesh mesh, Part p, Atlas atlas) {
        if (mesh.triCount == 0) {
            return null;
        }
        Surface.Builder b = new Surface.Builder();
        float u = atlas.whiteU();
        float v = atlas.whiteV();
        for (int i = 0; i < s.count; ++i) {
            b.vertex(s.pos[i * 3] - p.pivotX, s.pos[i * 3 + 1] - p.pivotY, s.pos[i * 3 + 2] - p.pivotZ, s.nrm[i * 3], s.nrm[i * 3 + 1], s.nrm[i * 3 + 2], u, v, s.color[i], s.glow[i] != 0, s.flat[i]);
        }
        CharacterBuilder.pair(mesh.tris, mesh.triCount, mesh.vertexCount, b);
        return b.build();
    }

    private static Surface hullSurface(Shape shape, Shaded s, Surface base, Part p, float thickness, float step, int color, Atlas atlas) {
        float[] hull = new float[s.count * 3];
        float[] width = new float[s.count];
        float reach = thickness / 0.5f * 1.5f;
        int chunks = (s.count + 255) / 256;
        IntStream.range(0, chunks).parallel().forEach(c -> {
            float[] q = new float[3];
            float[] g = new float[3];
            int end = Math.min(s.count, (c + 1) * 256);
            for (int i = c * 256; i < end; ++i) {
                float w;
                float px = s.pos[i * 3];
                float py = s.pos[i * 3 + 1];
                float pz = s.pos[i * 3 + 2];
                float nx = s.nrm[i * 3];
                float ny = s.nrm[i * 3 + 1];
                float nz = s.nrm[i * 3 + 2];
                float f = w = s.cover != null && s.cover[i] < 0.0f ? 0.0f : thickness;
                if (w > 0.0f) {
                    float inner = 0.0f;
                    for (int k = 1; k <= 5; ++k) {
                        float t = reach * (float)k / 5.0f;
                        inner = Math.max(inner, -shape.distance(px - nx * t, py - ny * t, pz - nz * t));
                    }
                    w = Math.min(w, 0.5f * inner);
                }
                q[0] = px + nx * w;
                q[1] = py + ny * w;
                q[2] = pz + nz * w;
                if (w > 0.0f) {
                    CharacterBuilder.project(shape, q, g, step, w);
                }
                hull[i * 3] = q[0];
                hull[i * 3 + 1] = q[1];
                hull[i * 3 + 2] = q[2];
                width[i] = w;
            }
        });
        Surface.Builder b = new Surface.Builder();
        float u = atlas.whiteU();
        float v = atlas.whiteV();
        for (int i = 0; i < s.count; ++i) {
            b.vertex(hull[i * 3] - p.pivotX, hull[i * 3 + 1] - p.pivotY, hull[i * 3 + 2] - p.pivotZ, 0.0f, 1.0f, 0.0f, u, v, color | 0xFF000000, false);
        }
        int[] quads = base.quads;
        for (int q = 0; q < quads.length; q += 4) {
            int a = quads[q];
            int bb = quads[q + 1];
            int c2 = quads[q + 2];
            int d = quads[q + 3];
            if (width[a] <= 0.0f && width[bb] <= 0.0f && width[c2] <= 0.0f && width[d] <= 0.0f) continue;
            b.quad(a, d, c2, bb, c2 == d ? 1 : 2);
        }
        return b.build();
    }

    static void pair(int[] tris, int count, int vertices, Surface.Builder out) {
        long[] edges = new long[count * 3];
        for (int t = 0; t < count; ++t) {
            for (int e = 0; e < 3; ++e) {
                int a = tris[t * 3 + e];
                int b = tris[t * 3 + (e + 1) % 3];
                long key = (long)Math.min(a, b) * (long)vertices + (long)Math.max(a, b);
                edges[t * 3 + e] = key << 21 | (long)t * 3L + (long)e;
            }
        }
        Arrays.sort(edges);
        int[] neighbour = new int[count * 3];
        Arrays.fill(neighbour, -1);
        long mask = 0x1FFFFFL;
        int i = 0;
        while (i + 1 < edges.length) {
            if (edges[i] >>> 21 == edges[i + 1] >>> 21) {
                if (i + 2 < edges.length && edges[i + 2] >>> 21 == edges[i] >>> 21) {
                    while (i + 1 < edges.length && edges[i + 1] >>> 21 == edges[i] >>> 21) {
                        ++i;
                    }
                } else {
                    int h1;
                    int h0 = (int)(edges[i] & mask);
                    neighbour[h0] = h1 = (int)(edges[i + 1] & mask);
                    neighbour[h1] = h0;
                    ++i;
                }
            }
            ++i;
        }
        boolean[] used = new boolean[count];
        for (int t = 0; t < count; ++t) {
            if (used[t]) continue;
            int pick = -1;
            for (int e = 0; e < 3 && pick < 0; ++e) {
                int h = neighbour[t * 3 + e];
                if (h < 0 || used[h / 3] || h / 3 == t) continue;
                pick = e;
            }
            int a0 = tris[t * 3];
            int a1 = tris[t * 3 + 1];
            int a2 = tris[t * 3 + 2];
            if (pick < 0) {
                used[t] = true;
                out.quad(a0, a1, a2, a2, 1);
                continue;
            }
            int h = neighbour[t * 3 + pick];
            int o = h / 3;
            used[t] = true;
            used[o] = true;
            int a = tris[t * 3 + pick];
            int b = tris[t * 3 + (pick + 1) % 3];
            int c = tris[t * 3 + (pick + 2) % 3];
            int d = tris[o * 3 + (h % 3 + 2) % 3];
            out.quad(a, d, b, c, 2);
        }
    }

    private static Surface decalSurface(Part p, Shaded s, Mesh mesh, Atlas atlas) {
        Surface.Builder b = new Surface.Builder();
        float[] poly = new float[200];
        float[] tmp = new float[200];
        for (Decal d : p.decals) {
            for (int t = 0; t < mesh.triCount; ++t) {
                int i0 = mesh.tris[t * 3];
                int i1 = mesh.tris[t * 3 + 1];
                int i2 = mesh.tris[t * 3 + 2];
                if (!CharacterBuilder.faces(d, s, i0, i1, i2)) continue;
                int[] idx = new int[]{i0, i1, i2};
                float firstAngle = 0.0f;
                for (int c = 0; c < 3; ++c) {
                    int v = idx[c];
                    int o = c * 10;
                    poly[o] = s.pos[v * 3];
                    poly[o + 1] = s.pos[v * 3 + 1];
                    poly[o + 2] = s.pos[v * 3 + 2];
                    poly[o + 3] = s.nrm[v * 3];
                    poly[o + 4] = s.nrm[v * 3 + 1];
                    poly[o + 5] = s.nrm[v * 3 + 2];
                    poly[o + 8] = s.ao[v];
                    poly[o + 9] = s.flat[v];
                    float rx = poly[o] - d.cx;
                    float ry = poly[o + 1] - d.cy;
                    float rz = poly[o + 2] - d.cz;
                    if (d.wrap) {
                        float along = rx * d.dx + ry * d.dy + rz * d.dz;
                        float fx = rx - along * d.dx;
                        float fy = ry - along * d.dy;
                        float fz = rz - along * d.dz;
                        float angle = (float)Math.toDegrees(Math.atan2(fx * d.rx + fy * d.ry + fz * d.rz, fx * d.ux + fy * d.uy + fz * d.uz));
                        if (c == 0) {
                            firstAngle = angle;
                        } else {
                            float delta;
                            for (delta = angle - firstAngle; delta > 180.0f; delta -= 360.0f) {
                            }
                            while (delta < -180.0f) {
                                delta += 360.0f;
                            }
                            angle = firstAngle + delta;
                        }
                        poly[o + 6] = 0.5f + angle / d.width;
                        poly[o + 7] = 0.5f - along / d.height;
                        continue;
                    }
                    poly[o + 6] = 0.5f + (rx * d.rx + ry * d.ry + rz * d.rz) / d.width;
                    poly[o + 7] = 0.5f - (rx * d.ux + ry * d.uy + rz * d.uz) / d.height;
                }
                CharacterBuilder.emitClipped(d, poly, tmp, 0.0f, p, atlas, b);
                if (!d.wrap || !(d.width >= 359.9f)) continue;
                CharacterBuilder.emitClipped(d, poly, tmp, 1.0f, p, atlas, b);
                CharacterBuilder.emitClipped(d, poly, tmp, -1.0f, p, atlas, b);
            }
        }
        return b.build();
    }

    private static boolean faces(Decal d, Shaded s, int i0, int i1, int i2) {
        float ay = s.pos[i0 * 3 + 1];
        float uy = s.pos[i1 * 3 + 1] - ay;
        float az = s.pos[i0 * 3 + 2];
        float vz = s.pos[i2 * 3 + 2] - az;
        float uz = s.pos[i1 * 3 + 2] - az;
        float vy = s.pos[i2 * 3 + 1] - ay;
        float nx = uy * vz - uz * vy;
        float ax = s.pos[i0 * 3];
        float vx = s.pos[i2 * 3] - ax;
        float ux = s.pos[i1 * 3] - ax;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float nl = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0E-10f) {
            return false;
        }
        nx /= nl;
        ny /= nl;
        nz /= nl;
        int[] idx = new int[]{i0, i1, i2};
        if (!d.wrap) {
            if (nx * d.dx + ny * d.dy + nz * d.dz < d.cosLimit) {
                return false;
            }
            for (int v : idx) {
                float depth = (s.pos[v * 3] - d.cx) * d.dx + (s.pos[v * 3 + 1] - d.cy) * d.dy + (s.pos[v * 3 + 2] - d.cz) * d.dz;
                if (!(depth < d.near) && !(depth > d.far)) continue;
                return false;
            }
            return true;
        }
        float cx = (s.pos[i0 * 3] + s.pos[i1 * 3] + s.pos[i2 * 3]) / 3.0f - d.cx;
        float cy = (s.pos[i0 * 3 + 1] + s.pos[i1 * 3 + 1] + s.pos[i2 * 3 + 1]) / 3.0f - d.cy;
        float cz = (s.pos[i0 * 3 + 2] + s.pos[i1 * 3 + 2] + s.pos[i2 * 3 + 2]) / 3.0f - d.cz;
        float along = cx * d.dx + cy * d.dy + cz * d.dz;
        float rx = cx - along * d.dx;
        float ry = cy - along * d.dy;
        float rz = cz - along * d.dz;
        float rl = (float)Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 1.0E-5f) {
            return false;
        }
        if ((nx * rx + ny * ry + nz * rz) / rl < d.cosLimit) {
            return false;
        }
        float near = d.near == -3.4028235E38f ? 0.0f : d.near;
        return rl >= near && rl <= d.far;
    }

    private static void emitClipped(Decal d, float[] tri, float[] tmp, float shift, Part p, Atlas atlas, Surface.Builder out) {
        float[] a = new float[200];
        System.arraycopy(tri, 0, a, 0, 30);
        if (shift != 0.0f) {
            for (int c = 0; c < 3; ++c) {
                int n = c * 10 + 6;
                a[n] = a[n] + shift;
            }
        }
        int n = 3;
        n = CharacterBuilder.clip(a, n, tmp, 6, 0.0f, true);
        n = CharacterBuilder.clip(tmp, n, a, 6, 1.0f, false);
        n = CharacterBuilder.clip(a, n, tmp, 7, 0.0f, true);
        if ((n = CharacterBuilder.clip(tmp, n, a, 7, 1.0f, false)) < 3) {
            return;
        }
        int first = -1;
        for (int c = 0; c < n; ++c) {
            int o = c * 10;
            float nx = a[o + 3];
            float ny = a[o + 4];
            float nz = a[o + 5];
            float l = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l > 1.0E-8f) {
                nx /= l;
                ny /= l;
                nz /= l;
            }
            int shade = Sample.channel(1.0f - d.occlusion * (1.0f - a[o + 8]));
            int argb = 0xFF000000 | shade << 16 | shade << 8 | shade;
            float flat = d.flat == d.flat ? d.flat : a[o + 9];
            int idx = out.vertex(a[o] + nx * d.offset - p.pivotX, a[o + 1] + ny * d.offset - p.pivotY, a[o + 2] + nz * d.offset - p.pivotZ, nx, ny, nz, atlas.u(d.canvas, a[o + 6]), atlas.v(d.canvas, a[o + 7]), argb, d.glow, flat);
            if (first >= 0) continue;
            first = idx;
        }
        int k = 1;
        while (k + 2 < n) {
            out.quad(first, first + k, first + k + 1, first + k + 2, 2);
            k += 2;
        }
        if (k + 1 < n) {
            out.quad(first, first + k, first + k + 1, first + k + 1, 1);
        }
    }

    private static int clip(float[] in, int n, float[] out, int comp, float bound, boolean keepAbove) {
        int m = 0;
        for (int i = 0; i < n; ++i) {
            float pd;
            int cur = i * 10;
            int prev = (i + n - 1) % n * 10;
            float cd = keepAbove ? in[cur + comp] - bound : bound - in[cur + comp];
            float f = pd = keepAbove ? in[prev + comp] - bound : bound - in[prev + comp];
            if (cd >= 0.0f) {
                if (pd < 0.0f) {
                    m = CharacterBuilder.cut(in, prev, cur, pd, cd, out, m);
                }
                System.arraycopy(in, cur, out, m * 10, 10);
                ++m;
            } else if (pd >= 0.0f) {
                m = CharacterBuilder.cut(in, prev, cur, pd, cd, out, m);
            }
            if (m >= 19) break;
        }
        return m;
    }

    private static int cut(float[] in, int a, int b, float da, float db, float[] out, int m) {
        float t = da / (da - db);
        for (int k = 0; k < 10; ++k) {
            out[m * 10 + k] = in[a + k] + (in[b + k] - in[a + k]) * t;
        }
        return m + 1;
    }

    private static float reach(Surface s) {
        if (s == null) {
            return 1.0f;
        }
        float r = 0.0f;
        for (int i = 0; i < s.vertexCount(); ++i) {
            float x = s.pos[i * 3];
            float y = s.pos[i * 3 + 1];
            float z = s.pos[i * 3 + 2];
            r = Math.max(r, x * x + y * y + z * z);
        }
        return Math.max(0.5f, (float)Math.sqrt(r));
    }

    private static void jointWeights(Surface s, Part p, Part owner) {
        if (s == null) {
            return;
        }
        float[] w = new float[s.vertexCount()];
        float ox = owner.jointX - p.pivotX;
        float oy = owner.jointY - p.pivotY;
        float oz = owner.jointZ - p.pivotZ;
        float blend = owner.jointBlend;
        boolean any = false;
        for (int i = 0; i < w.length; ++i) {
            float along = (s.pos[i * 3] - ox) * owner.jointDirX + (s.pos[i * 3 + 1] - oy) * owner.jointDirY + (s.pos[i * 3 + 2] - oz) * owner.jointDirZ;
            float t = Math.max(0.0f, Math.min(1.0f, (along + blend) / (2.0f * blend)));
            w[i] = t * t * (3.0f - 2.0f * t);
            any |= w[i] > 0.0f;
        }
        if (any) {
            s.joint = w;
        }
    }

    private static void weigh(Surface s, float reach, float power) {
        if (s == null) {
            return;
        }
        float[] w = new float[s.vertexCount()];
        for (int i = 0; i < w.length; ++i) {
            float x = s.pos[i * 3];
            float y = s.pos[i * 3 + 1];
            float z = s.pos[i * 3 + 2];
            float t = Math.min(1.0f, (float)Math.sqrt(x * x + y * y + z * z) / reach);
            w[i] = (float)Math.pow(t, power);
        }
        s.weight = w;
    }

    @Environment(value=EnvType.CLIENT)
    private static final class Shaded {
        final int count;
        final float[] pos;
        final float[] nrm;
        final int[] color;
        final byte[] glow;
        final float[] ao;
        final float[] flat;
        float[] cover;

        Shaded(int count) {
            this.count = count;
            this.pos = new float[count * 3];
            this.nrm = new float[count * 3];
            this.color = new int[count];
            this.glow = new byte[count];
            this.ao = new float[count];
            this.flat = new float[count];
        }
    }
}
