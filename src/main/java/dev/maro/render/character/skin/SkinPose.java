package dev.maro.render.character.skin;

/**
 * A rig's pose: local transforms of every node, then (after {@link #evaluate}) their world
 * matrices and the skinning matrices vertices are moved by. Buffers are sized once per rig and
 * reused every frame.
 */
public final class SkinPose {
    public final Rig rig;
    public final float[] t, r, s;
    public final float[] world;
    public final float[] skin;
    /** Body shift in character units (added above the root) and how grounded the pose is. */
    public float shiftX, shiftY, shiftZ, grounded = 1;
    private final float[] local = new float[12], rootM = new float[12], q = new float[8];

    public SkinPose(Rig rig) {
        this.rig = rig;
        t = new float[rig.nodes * 3];
        r = new float[rig.nodes * 4];
        s = new float[rig.nodes * 3];
        world = new float[rig.nodes * 12];
        skin = new float[rig.slots.length * 12];
        canonical();
    }

    /** The canonical rest pose: limbs hanging straight, nothing else moved. */
    public void canonical() {
        rig.canonical(t, r, s);
        shiftX = shiftY = shiftZ = 0;
        grounded = 1;
    }

    /** The rig's own rest (bind) pose, for a clip of the model's own to be sampled over. */
    public void rest() {
        rig.rest(t, r, s);
        shiftX = shiftY = shiftZ = 0;
        grounded = 1;
    }

    public void set(SkinPose o) {
        System.arraycopy(o.t, 0, t, 0, t.length);
        System.arraycopy(o.r, 0, r, 0, r.length);
        System.arraycopy(o.s, 0, s, 0, s.length);
        shiftX = o.shiftX;
        shiftY = o.shiftY;
        shiftZ = o.shiftZ;
        grounded = o.grounded;
    }

    /**
     * Turns the rig by a shared-skeleton pose, over the canonical rest: each driven node's local
     * rotation becomes {@code canonWorld(parent)^-1 * turn * canonWorld(node)}, so the turn is
     * about the joint along the character's axes whatever way the rig's bones point.
     */
    public void apply(HumanPose pose) {
        canonical();
        float[] rot = pose.rot;
        for (int n = 0; n < rig.nodes; n++) {
            int d1 = rig.drive[n * 2], d2 = rig.drive[n * 2 + 1];
            if (d1 < 0) continue;
            turn(rot, d1, rig.share[n * 2], q, 0);
            if (d2 >= 0) {
                turn(rot, d2, rig.share[n * 2 + 1], q, 4);
                SkinMath.mulQ(q, 0, q, 4, q, 0);
            }
            retarget(n, q, 0, r, n * 4);
        }
        float leg = rig.legLength;
        shiftX = pose.shift[0] * leg;
        shiftY = pose.shift[1] * leg;
        shiftZ = pose.shift[2] * leg;
        grounded = pose.grounded;
    }

    private static void turn(float[] rot, int bone, float share, float[] out, int o) {
        if (share >= 0.999f) System.arraycopy(rot, bone * 4, out, o, 4);
        else SkinMath.powQ(rot, bone * 4, share, out, o);
    }

    /** {@code out = canonWorld(parent)^-1 * turn * canonWorld(node)}. */
    private void retarget(int node, float[] turn, int to, float[] out, int oo) {
        int p = rig.parent[node];
        float[] pw = p < 0 ? rig.rootRotation : rig.canonWorld;
        int po = p < 0 ? 0 : p * 4;
        SkinMath.mulQ(turn, to, rig.canonWorld, node * 4, q, 4);
        SkinMath.mulConjQ(pw, po, q, 4, out, oo);
    }

    /**
     * A turn on top of what a node already does, about its joint along the character axes as
     * the canonical rest has them: {@code r = canonWorld(parent)^-1 * turn * canonWorld(parent) * r}.
     */
    public void turnBy(int node, float[] turn, int to) {
        if (node < 0) return;
        int p = rig.parent[node];
        float[] pw = p < 0 ? rig.rootRotation : rig.canonWorld;
        int po = p < 0 ? 0 : p * 4;
        SkinMath.mulQ(turn, to, pw, po, q, 0);
        SkinMath.mulConjQ(pw, po, q, 0, q, 4);
        SkinMath.mulQ(q, 4, r, node * 4, r, node * 4);
    }

    /** Moves a node's rotation {@code weight} of the way to what a shared turn from canonical rest gives. */
    public void aim(int node, float[] turn, int to, float weight) {
        if (node < 0 || weight <= 0) return;
        retarget(node, turn, to, q, 0);
        SkinMath.nlerpQ(r, node * 4, q, 0, Math.min(1, weight), r, node * 4);
    }

    /** Moves {@code weight} of the way to another pose, for the nodes {@code mask} allows (all when null). */
    public void blend(SkinPose o, float weight, boolean[] mask) {
        if (weight <= 0) return;
        if (weight >= 1 && mask == null) {
            set(o);
            return;
        }
        float w = Math.min(1, weight);
        for (int n = 0; n < rig.nodes; n++) {
            if (mask != null && !mask[n]) continue;
            for (int k = 0; k < 3; k++) {
                t[n * 3 + k] += (o.t[n * 3 + k] - t[n * 3 + k]) * w;
                s[n * 3 + k] += (o.s[n * 3 + k] - s[n * 3 + k]) * w;
            }
            SkinMath.nlerpQ(r, n * 4, o.r, n * 4, w, r, n * 4);
        }
        if (mask == null) {
            shiftX += (o.shiftX - shiftX) * w;
            shiftY += (o.shiftY - shiftY) * w;
            shiftZ += (o.shiftZ - shiftZ) * w;
            grounded += (o.grounded - grounded) * w;
        }
    }

    /** World matrices for every node from the local transforms, with the body shift and a lift. */
    public void forward(float lift) {
        System.arraycopy(rig.root, 0, rootM, 0, 12);
        rootM[3] += shiftX;
        rootM[7] += shiftY + lift;
        rootM[11] += shiftZ;
        for (int n : rig.order) {
            SkinMath.composeM(t, n * 3, r, n * 4, s, n * 3, local, 0);
            int p = rig.parent[n];
            if (p < 0) SkinMath.mulM(rootM, 0, local, 0, world, n * 12);
            else SkinMath.mulM(world, p * 12, local, 0, world, n * 12);
        }
    }

    /** Raises or lowers every world matrix (after {@link #forward}): grounding the feet. */
    public void raise(float dy) {
        if (dy == 0) return;
        for (int n = 0; n < rig.nodes; n++) world[n * 12 + 7] += dy;
    }

    /** Skinning matrices from the world matrices. */
    public void skin() {
        int[] slots = rig.slots;
        for (int i = 0; i < slots.length; i++) SkinMath.mulM(world, slots[i] * 12, rig.bindInverse, slots[i] * 12, skin, i * 12);
    }

    /** {@link #forward} then {@link #skin}. */
    public void evaluate() {
        forward(0);
        skin();
    }

    /** Height of the lower sole above the floor in the current world pose (needs {@link #forward}). */
    public float sole() {
        float best = Float.POSITIVE_INFINITY;
        int[] nodes = rig.soleNode;
        float[] p = rig.solePoint;
        for (int i = 0; i < 6; i++) {
            int n = nodes[i];
            if (n < 0) continue;
            int o = n * 12;
            best = Math.min(best, world[o + 4] * p[i * 3] + world[o + 5] * p[i * 3 + 1] + world[o + 6] * p[i * 3 + 2] + world[o + 7]);
        }
        if (best < Float.POSITIVE_INFINITY) return best;
        for (int side = 0; side < 2; side++) {
            int foot = rig.role[(side == 0 ? Human.RIGHT_FOOT : Human.LEFT_FOOT).ordinal()];
            int toe = rig.role[(side == 0 ? Human.RIGHT_TOE : Human.LEFT_TOE).ordinal()];
            if (foot >= 0) best = Math.min(best, world[foot * 12 + 7] - rig.footRest[side]);
            if (toe >= 0) best = Math.min(best, world[toe * 12 + 7] - rig.toeRest[side]);
        }
        return best;
    }

    public float x(int node) {
        return world[node * 12 + 3];
    }

    public float y(int node) {
        return world[node * 12 + 7];
    }

    public float z(int node) {
        return world[node * 12 + 11];
    }
}
