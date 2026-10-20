// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.gltf;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class Skeleton {
    static final int RIGHT = 0;
    static final int LEFT = 1;
    private static final Set<String> NOISE = Set.of("j", "bip", "bip01", "def", "deform", "mch", "org", "jnt", "jt", "bone", "bn", "b", "c", "x", "stretch", "valve", "biped", "mixamorig");
    private static final Set<String> LEFT_WORDS = Set.of("left", "l", "lf", "lft");
    private static final Set<String> RIGHT_WORDS = Set.of("right", "r", "rt", "rgt");
    private static final List<String> UPPER_ARM = List.of("upperarm", "uparm", "armupper");
    private static final List<String> FOREARM = List.of("forearm", "lowerarm", "elbow", "armlower", "loarm");
    private static final List<String> HAND = List.of("hand", "wrist");
    private static final List<String> THIGH = List.of("thigh", "upleg", "upperleg", "legupper");
    private static final List<String> SHIN = List.of("calf", "shin", "knee", "lowerleg", "leglower", "loleg");
    private static final List<String> CLAVICLE = List.of("clavicle", "shoulder", "collar", "clav");
    private static final List<String> FOOT = List.of("foot", "ankle");
    private static final List<String> TOE = List.of("toe", "ball");
    private static final List<String> HIPS = List.of("hips", "pelvis", "hip", "lowertorso");
    int head = -1;
    final int[] upperArm = new int[]{-1, -1};
    final int[] forearm = new int[]{-1, -1};
    final int[] hand = new int[]{-1, -1};
    final int[] thigh = new int[]{-1, -1};
    final int[] shin = new int[]{-1, -1};
    boolean facesBack;
    private final PosedMesh mesh;
    private final String[] core;
    private final int[] named;

    private Skeleton(PosedMesh mesh) {
        this.mesh = mesh;
        int n = mesh.file.nodes();
        this.core = new String[n];
        this.named = new int[n];
        for (int i = 0; i < n; ++i) {
            List<String> words = Skeleton.words(mesh.file.nodeName(i));
            this.named[i] = -1;
            StringBuilder b = new StringBuilder();
            for (String w : words) {
                if (LEFT_WORDS.contains(w)) {
                    this.named[i] = 1;
                    continue;
                }
                if (RIGHT_WORDS.contains(w)) {
                    this.named[i] = 0;
                    continue;
                }
                if (NOISE.contains(w)) continue;
                b.append(w);
            }
            this.core[i] = b.toString().replaceAll("\\d+$", "");
        }
    }

    static Skeleton find(PosedMesh mesh, Map<String, String> bones) throws IOException {
        Skeleton s = new Skeleton(mesh);
        if (!bones.isEmpty()) {
            s.head = s.named(bones, "head");
            for (int side = 0; side < 2; ++side) {
                String w = side == 0 ? "right " : "left ";
                s.upperArm[side] = s.named(bones, w + "arm");
                s.forearm[side] = s.named(bones, w + "elbow");
                s.hand[side] = s.named(bones, w + "hand");
                s.thigh[side] = s.named(bones, w + "leg");
                s.shin[side] = s.named(bones, w + "knee");
            }
            s.facesBack = s.upperArm[1] >= 0 && s.upperArm[0] >= 0 && mesh.world(s.upperArm[1])[0] < mesh.world(s.upperArm[0])[0];
            return s;
        }
        s.facesBack = s.facingBack();
        s.head = s.shallowest(n -> s.core[n].startsWith("head"));
        for (int side = 0; side < 2; ++side) {
            int namedKnee;
            int namedElbow;
            int sd = side;
            int elbow = namedElbow = s.shallowest(n -> s.side(n) == sd && s.is(n, FOREARM));
            int arm = s.shallowest(n -> s.side(n) == sd && (s.is(n, UPPER_ARM) || s.core[n].equals("arm")) && (namedElbow < 0 || mesh.under(namedElbow, n) && n != namedElbow));
            if (arm < 0 && elbow >= 0) {
                arm = mesh.parent[elbow];
            }
            if (elbow < 0 && arm >= 0) {
                elbow = s.mainChild(arm);
            }
            s.upperArm[sd] = arm;
            s.forearm[sd] = elbow;
            if (elbow >= 0) {
                int e = elbow;
                int wrist = s.shallowest(n -> n != e && mesh.under(n, e) && s.is(n, HAND));
                s.hand[sd] = wrist >= 0 ? wrist : s.mainChild(e);
            }
            int knee = namedKnee = s.shallowest(n -> s.side(n) == sd && s.is(n, SHIN));
            int leg = s.shallowest(n -> s.side(n) == sd && (s.is(n, THIGH) || s.core[n].equals("leg")) && (namedKnee < 0 || mesh.under(namedKnee, n) && n != namedKnee));
            if (knee < 0 && leg >= 0) {
                int hip = leg;
                knee = s.shallowest(n -> n != hip && mesh.under(n, hip) && s.core[n].equals("leg"));
                if (knee < 0) knee = s.mainChild(hip);
            }
            if (leg < 0 && knee >= 0) {
                leg = mesh.parent[knee];
            }
            s.thigh[sd] = leg;
            s.shin[sd] = knee;
        }
        return s;
    }

    /**
     * Finds every shared bone, hips to toes, among a file's nodes from their names and how they
     * hang together: Mixamo ("mixamorig:LeftForeArm"), Unreal ("lowerarm_l"), Valve
     * ("Bip01_L_Forearm"), Blender/Rigify ("DEF-forearm.L"), VRM/Unity ("LowerArm.L") and Roblox
     * ("LeftLowerArm") rigs alike. The spine is found as the chain between the hips and the head;
     * the chest is where the arms hang from it. Returns the node for each
     * {@link dev.maro.render.character.skin.Human} bone, or -1. Only nodes marked usable (a skin's
     * joints, when the file has any) can play a bone, so a mesh node named "head" does not.
     */
    static int[] roles(String[] names, int[] parent, float[] x, boolean[] usable) {
        int n = names.length;
        String[] core = new String[n];
        int[] side = new int[n];
        int[] depth = new int[n];
        for (int i = 0; i < n; ++i) {
            side[i] = -1;
            StringBuilder b = new StringBuilder();
            for (String w : words(names[i])) {
                if (LEFT_WORDS.contains(w)) {
                    side[i] = 1;
                    continue;
                }
                if (RIGHT_WORDS.contains(w)) {
                    side[i] = 0;
                    continue;
                }
                if (NOISE.contains(w)) continue;
                b.append(w);
            }
            core[i] = b.toString().replaceAll("\\d+$", "");
            int d = 0;
            for (int q = parent[i]; q >= 0 && d <= n; q = parent[q]) ++d;
            depth[i] = d;
        }
        // Sides from position where the names do not say (facing +z, the right is -x).
        for (int i = 0; i < n; ++i) if (side[i] < 0) side[i] = x[i] < 0 ? 0 : 1;
        int[] role = new int[dev.maro.render.character.skin.Human.COUNT];
        Arrays.fill(role, -1);
        java.util.function.BiPredicate<Integer, Integer> under = (node, anc) -> {
            for (int q = node, steps = 0; q >= 0 && steps <= n; q = parent[q], ++steps) if (q == anc) return true;
            return false;
        };
        java.util.function.Function<IntPredicate, Integer> shallowest = test -> {
            int best = -1;
            for (int i = 0; i < n; ++i) {
                if (!usable[i] || !test.test(i)) continue;
                if (best < 0 || depth[i] < depth[best]) best = i;
            }
            return best;
        };
        java.util.function.BiPredicate<Integer, List<String>> is = (node, words) -> {
            for (String w : words) if (core[node].contains(w)) return true;
            return false;
        };
        java.util.function.IntUnaryOperator child = node -> {
            int best = -1, bestSize = -1;
            for (int c = 0; c < n; ++c) {
                if (parent[c] != node || !usable[c]) continue;
                int size = 0;
                for (int k = 0; k < n; ++k) if (under.test(k, c)) ++size;
                if (size > bestSize) {
                    best = c;
                    bestSize = size;
                }
            }
            return best;
        };
        int head = shallowest.apply(i -> core[i].startsWith("head") && !core[i].contains("end") && !core[i].contains("top"));
        int[][] arm = new int[2][4], leg = new int[2][4];
        for (int sd = 0; sd < 2; ++sd) {
            final int s2 = sd;
            final int named = shallowest.apply(i -> side[i] == s2 && is.test(i, FOREARM));
            int fore = named;
            int upper = shallowest.apply(i -> side[i] == s2 && (is.test(i, UPPER_ARM) || core[i].equals("arm"))
                    && (named < 0 || under.test(named, i) && i != named));
            if (upper < 0 && fore >= 0) upper = parent[fore];
            if (fore < 0 && upper >= 0) fore = child.applyAsInt(upper);
            int hand = -1;
            if (fore >= 0) {
                final int f = fore;
                hand = shallowest.apply(i -> i != f && under.test(i, f) && is.test(i, HAND));
                if (hand < 0) hand = child.applyAsInt(fore);
            }
            int clav = -1;
            if (upper >= 0) {
                for (int q = parent[upper]; q >= 0; q = parent[q]) {
                    if (is.test(q, CLAVICLE) && side[q] == sd) {
                        clav = q;
                        break;
                    }
                    if (core[q].contains("spine") || core[q].contains("chest") || core[q].contains("neck")) break;
                }
            }
            arm[sd][0] = clav;
            arm[sd][1] = upper;
            arm[sd][2] = fore;
            arm[sd][3] = hand;
            final int knee = shallowest.apply(i -> side[i] == s2 && is.test(i, SHIN));
            int shin = knee;
            int thigh = shallowest.apply(i -> side[i] == s2 && (is.test(i, THIGH) || core[i].equals("leg"))
                    && (knee < 0 || under.test(knee, i) && i != knee));
            if (shin < 0 && thigh >= 0) {
                final int h = thigh;
                shin = shallowest.apply(i -> i != h && under.test(i, h) && core[i].equals("leg"));
                if (shin < 0) shin = child.applyAsInt(thigh);
            }
            if (thigh < 0 && shin >= 0) thigh = parent[shin];
            int foot = -1, toe = -1;
            if (shin >= 0) {
                final int k = shin;
                foot = shallowest.apply(i -> i != k && under.test(i, k) && is.test(i, FOOT));
                if (foot < 0) foot = child.applyAsInt(shin);
            }
            if (foot >= 0) {
                final int f = foot;
                toe = shallowest.apply(i -> i != f && under.test(i, f) && is.test(i, TOE) && !core[i].endsWith("end"));
                if (toe < 0) {
                    int c = child.applyAsInt(foot);
                    if (c >= 0 && !core[c].endsWith("end")) toe = c;
                }
            }
            leg[sd][0] = thigh;
            leg[sd][1] = shin;
            leg[sd][2] = foot;
            leg[sd][3] = toe;
        }
        // The hips: where the legs meet, else whatever is named so.
        int hips = -1;
        if (leg[0][0] >= 0 && leg[1][0] >= 0) {
            for (int q = parent[leg[0][0]]; q >= 0; q = parent[q]) {
                if (under.test(leg[1][0], q)) {
                    hips = q;
                    break;
                }
            }
        }
        if (hips < 0) hips = shallowest.apply(i -> is.test(i, HIPS));
        // The spine: the chain from the hips up to the head; the chest is where the arms hang from it.
        List<Integer> chain = new ArrayList<>();
        if (head >= 0) {
            for (int q = parent[head]; q >= 0 && q != hips; q = parent[q]) chain.add(0, q);
            if (hips >= 0 && !under.test(head, hips)) chain.clear();
        }
        int chest = -1;
        int armTop0 = arm[0][0] >= 0 ? arm[0][0] : arm[0][1], armTop1 = arm[1][0] >= 0 ? arm[1][0] : arm[1][1];
        for (int i = chain.size() - 1; i >= 0; --i) {
            int q = chain.get(i);
            if ((armTop0 < 0 || under.test(armTop0, q)) && (armTop1 < 0 || under.test(armTop1, q))) {
                chest = q;
                break;
            }
        }
        int spine = chain.isEmpty() ? -1 : chain.get(0);
        if (chest < 0 && !chain.isEmpty()) chest = chain.get(chain.size() - 1);
        int neck = -1;
        if (chest >= 0) {
            int at = chain.indexOf(chest);
            if (at >= 0 && at + 1 < chain.size()) neck = chain.get(at + 1);
        }
        if (spine == chest && chain.size() > 1 && chain.get(0) != chest) spine = chain.get(0);
        role[dev.maro.render.character.skin.Human.HIPS.ordinal()] = hips;
        role[dev.maro.render.character.skin.Human.SPINE.ordinal()] = spine;
        role[dev.maro.render.character.skin.Human.CHEST.ordinal()] = chest;
        role[dev.maro.render.character.skin.Human.NECK.ordinal()] = neck;
        role[dev.maro.render.character.skin.Human.HEAD.ordinal()] = head;
        String[] armBones = {"SHOULDER", "UPPER_ARM", "FOREARM", "HAND"}, legBones = {"THIGH", "SHIN", "FOOT", "TOE"};
        for (int sd = 0; sd < 2; ++sd) {
            String prefix = sd == 0 ? "RIGHT_" : "LEFT_";
            for (int k = 0; k < 4; ++k) {
                role[dev.maro.render.character.skin.Human.valueOf(prefix + armBones[k]).ordinal()] = arm[sd][k];
                role[dev.maro.render.character.skin.Human.valueOf(prefix + legBones[k]).ordinal()] = leg[sd][k];
            }
        }
        return role;
    }

    private boolean is(int node, List<String> words) {
        for (String w : words) {
            if (!this.core[node].contains(w)) continue;
            return true;
        }
        return false;
    }

    private int named(Map<String, String> bones, String key) throws IOException {
        String name = bones.get(key);
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < this.core.length; ++i) {
            if (!this.mesh.file.nodeName(i).equalsIgnoreCase(name)) continue;
            return i;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < this.core.length; ++i) {
            if (!this.mesh.file.nodeName(i).toLowerCase(Locale.ROOT).startsWith(lower)) continue;
            return i;
        }
        throw new IOException("no bone named " + name);
    }

    private static List<String> words(String name) {
        String n = name.replaceAll("_\\d+$", "").replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT).replace("\u624b\u9996", " wrist ").replace("\u8db3\u9996", " ankle ").replace("\u5de6", " left ").replace("\u53f3", " right ").replace("\u3072\u3058", " elbow ").replace("\u3072\u3056", " knee ").replace("\u8155", " arm ").replace("\u8db3", " leg ").replace("\u982d", " head ").replace("\u9996", " neck ");
        ArrayList<String> out = new ArrayList<String>();
        for (String w : n.split("[^a-z0-9]+")) {
            if (w.isEmpty()) continue;
            out.add(w);
        }
        return out;
    }

    private boolean facingBack() {
        int left = this.shallowest(n -> this.named[n] == 1 && this.is(n, HAND));
        int right = this.shallowest(n -> this.named[n] == 0 && this.is(n, HAND));
        if (left < 0 || right < 0) {
            left = this.shallowest(n -> this.named[n] == 1 && this.is(n, SHIN));
            right = this.shallowest(n -> this.named[n] == 0 && this.is(n, SHIN));
        }
        if (left >= 0 && right >= 0) {
            return this.mesh.world(left)[0] < this.mesh.world(right)[0];
        }
        float[] box = this.mesh.bounds();
        float h = box[4] - box[1];
        float feet = 0.0f;
        float shins = 0.0f;
        int nf = 0;
        int ns = 0;
        for (int v = 0; v < this.mesh.vertices; ++v) {
            float y = this.mesh.pos[v * 3 + 1] - box[1];
            float z = this.mesh.pos[v * 3 + 2];
            if (y < h * 0.04f) {
                feet += z;
                ++nf;
                continue;
            }
            if (!(y > h * 0.1f) || !(y < h * 0.2f)) continue;
            shins += z;
            ++ns;
        }
        return nf > 0 && ns > 0 && feet / (float)nf < shins / (float)ns - h * 0.01f;
    }

    private int side(int node) {
        if (this.named[node] >= 0) {
            return this.named[node];
        }
        return this.mesh.world(node)[0] < 0.0f != this.facesBack ? 0 : 1;
    }

    private int shallowest(IntPredicate test) {
        int best = -1;
        int bestDepth = Integer.MAX_VALUE;
        for (int n : this.mesh.joints) {
            int depth;
            if (!test.test(n)) continue;
            int p = this.mesh.parent[n];
            for (depth = 0; p >= 0 && depth <= this.core.length; ++depth) {
                p = this.mesh.parent[p];
            }
            if (depth >= bestDepth && (depth != bestDepth || n >= best)) continue;
            best = n;
            bestDepth = depth;
        }
        return best;
    }

    private int mainChild(int node) {
        int best = -1;
        int bestSize = -1;
        for (JsonElement c : GltfFile.array(this.mesh.file.node(node), "children")) {
            int child = c.getAsInt();
            int size = 0;
            for (int n = 0; n < this.core.length; ++n) {
                if (!this.mesh.under(n, child)) continue;
                ++size;
            }
            if (size <= bestSize) continue;
            best = child;
            bestSize = size;
        }
        return best;
    }

    Map<Integer, Float> armTurns() {
        HashMap<Integer, Float> turns = new HashMap<Integer, Float>();
        for (int s = 0; s < 2; ++s) {
            if (this.upperArm[s] < 0 || this.forearm[s] < 0) continue;
            float[] a = this.mesh.world(this.upperArm[s]);
            float[] b = this.mesh.world(this.forearm[s]);
            float dx = b[0] - a[0];
            float dy = b[1] - a[1];
            float angle = (float)Math.toDegrees(Math.atan2(Math.abs(dx), -dy));
            if (!(angle > 40.0f)) continue;
            turns.put(this.upperArm[s], Float.valueOf((dx < 0.0f ? 1.0f : -1.0f) * (angle - 10.0f)));
        }
        return turns;
    }

    int[] groups() {
        int[] out = new int[this.core.length];
        for (int n = 0; n < out.length; ++n) {
            out[n] = 1;
            if (this.head >= 0 && this.mesh.under(n, this.head)) {
                out[n] = 0;
                continue;
            }
            if (this.upperArm[0] >= 0 && this.mesh.under(n, this.upperArm[0])) {
                out[n] = 2;
                continue;
            }
            if (this.upperArm[1] >= 0 && this.mesh.under(n, this.upperArm[1])) {
                out[n] = 3;
                continue;
            }
            if (this.thigh[0] >= 0 && this.mesh.under(n, this.thigh[0])) {
                out[n] = 4;
                continue;
            }
            if (this.thigh[1] < 0 || !this.mesh.under(n, this.thigh[1])) continue;
            out[n] = 5;
        }
        return out;
    }

    boolean found() {
        return this.head >= 0 || this.upperArm[0] >= 0 || this.upperArm[1] >= 0 || this.thigh[0] >= 0 || this.thigh[1] >= 0;
    }

    String describe() {
        StringBuilder b = new StringBuilder();
        b.append("facing ").append(this.facesBack ? "-z (turned around)" : "+z").append('\n');
        this.line(b, "head", this.head);
        for (int s = 0; s < 2; ++s) {
            String w = s == 0 ? "right " : "left ";
            this.line(b, w + "arm", this.upperArm[s]);
            this.line(b, w + "elbow", this.forearm[s]);
            this.line(b, w + "hand", this.hand[s]);
            this.line(b, w + "leg", this.thigh[s]);
            this.line(b, w + "knee", this.shin[s]);
        }
        return b.toString();
    }

    private void line(StringBuilder b, String role, int node) {
        b.append(String.format(Locale.ROOT, "%-11s %s%n", role, node < 0 ? "-" : this.mesh.file.nodeName(node)));
    }

}
