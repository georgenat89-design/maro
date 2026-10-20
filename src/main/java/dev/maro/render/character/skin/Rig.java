package dev.maro.render.character.skin;

import java.util.Arrays;

/**
 * A character's skeleton in character units (1/16 block, Y up, Z forward, feet at y = 0): every
 * node's rest transform, the inverse of its rest world matrix for skinning, which node plays each
 * {@link Human} bone, and the "canonical rest" every shared clip starts from: the rig's own pose
 * with its arms and legs hanging straight, whatever it was modelled in (a T-pose, an A-pose).
 */
public final class Rig {
    public final int nodes;
    public final String[] names;
    public final int[] parent;
    /** Node indices with every parent before its children. */
    public final int[] order;
    /** Rest (bind) local transform of each node: translation, rotation (x, y, z, w) and scale. */
    public final float[] restT, restR, restS;
    /** Inverse of each node's rest world matrix, 3x4 per node. */
    public final float[] bindInverse;
    /** The node behind each skinning slot a vertex can name. */
    public final int[] slots;
    /** The node playing each {@link Human} bone, or -1. */
    public final int[] role;
    /** Canonical rest local rotation of each node: the rest one, with limbs straightened. */
    public final float[] canonLocal;
    /** Canonical rest world rotation of each node, in the character frame. */
    public final float[] canonWorld;
    /**
     * Up to two shared bones turning each node and the share of their turn it takes: a spine of
     * five nodes splits the clip's spine and chest bends between them.
     */
    public final int[] drive;
    public final float[] share;
    /** Rotation above the root nodes (facing), as a quaternion: what a root's parent is. */
    public final float[] rootRotation;
    /** World matrix above the root nodes: scale, facing and placement into character units. */
    public final float[] root;
    /** Character height, and measurements clips and grounding scale by. */
    public final float height, hipHeight, legLength, armLength;
    /** Height of each foot's joint and toe joint above the floor at canonical rest (right, left). */
    public final float[] footRest, toeRest;
    /** Head top above the head joint, in the head's own rest frame, for name tags. */
    public final float[] headTop;
    /** Palm centre in each hand's canonical rest frame (right, left). */
    public final float[] palm;
    /**
     * Where each sole meets the floor at rest, three points per foot (heel, ball, toe tip: right
     * then left), each in the frame of the node in {@link #soleNode}; -1 where there is none.
     */
    public final int[] soleNode;
    public final float[] solePoint;
    /** Whether the bones form a usable humanoid (arms, legs and a head found). */
    public final boolean humanoid;

    public Rig(String[] names, int[] parent, float[] restT, float[] restR, float[] restS, float[] bindInverse, int[] slots,
               int[] role, float[] canonLocal, float[] canonWorld, int[] drive, float[] share, float[] rootRotation, float[] root,
               float height, float hipHeight, float legLength, float armLength, float[] footRest, float[] toeRest, float[] headTop,
               float[] palm, int[] soleNode, float[] solePoint, boolean humanoid) {
        this.nodes = names.length;
        this.names = names;
        this.parent = parent;
        this.order = order(parent);
        this.restT = restT;
        this.restR = restR;
        this.restS = restS;
        this.bindInverse = bindInverse;
        this.slots = slots;
        this.role = role;
        this.canonLocal = canonLocal;
        this.canonWorld = canonWorld;
        this.drive = drive;
        this.share = share;
        this.rootRotation = rootRotation;
        this.root = root;
        this.height = height;
        this.hipHeight = hipHeight;
        this.legLength = legLength;
        this.armLength = armLength;
        this.footRest = footRest;
        this.toeRest = toeRest;
        this.headTop = headTop;
        this.palm = palm;
        this.soleNode = soleNode;
        this.solePoint = solePoint;
        this.humanoid = humanoid;
    }

    public int node(Human bone) {
        return role[bone.ordinal()];
    }

    public boolean has(Human bone) {
        return role[bone.ordinal()] >= 0;
    }

    /** Parents before children, so one pass over this order computes every world transform. */
    static int[] order(int[] parent) {
        int n = parent.length;
        int[] out = new int[n];
        boolean[] done = new boolean[n];
        int count = 0;
        for (int pass = 0; pass <= n && count < n; pass++) {
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                int p = parent[i];
                if (p < 0 || p >= n || done[p]) {
                    done[i] = true;
                    out[count++] = i;
                }
            }
        }
        if (count < n) {
            // A loop in a broken file: treat what is left as roots.
            for (int i = 0; i < n; i++) if (!done[i]) out[count++] = i;
        }
        return out;
    }

    /** A copy of the rest pose, as a starting point for posing. */
    public void rest(float[] t, float[] r, float[] s) {
        System.arraycopy(restT, 0, t, 0, nodes * 3);
        System.arraycopy(restR, 0, r, 0, nodes * 4);
        System.arraycopy(restS, 0, s, 0, nodes * 3);
    }

    /** The canonical rest pose: rest translation and scale, straightened rotations. */
    public void canonical(float[] t, float[] r, float[] s) {
        System.arraycopy(restT, 0, t, 0, nodes * 3);
        System.arraycopy(canonLocal, 0, r, 0, nodes * 4);
        System.arraycopy(restS, 0, s, 0, nodes * 3);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Rig " + nodes + " nodes, " + slots.length + " slots, humanoid=" + humanoid + "\n");
        for (Human h : Human.ALL) {
            int n = role[h.ordinal()];
            b.append(String.format(java.util.Locale.ROOT, "  %-16s %s%n", h, n < 0 ? "-" : names[n]));
        }
        b.append(String.format(java.util.Locale.ROOT, "  height %.2f hips %.2f leg %.2f arm %.2f feet %s toes %s%n", height, hipHeight,
                legLength, armLength, Arrays.toString(footRest), Arrays.toString(toeRest)));
        return b.toString();
    }
}
