package dev.maro.nathan.render;

import dev.maro.runtime.renderer.MeshBuilder;
import dev.maro.runtime.renderer.Renderer2D;
import dev.maro.runtime.utils.render.color.Color;

/**
 * A rounded rectangle with a rim, soft edges and an optional shadow, turned
 * through any angle about its own centre, written into Meteor's 2D mesh.
 *
 * <p>Meteor's 2D renderer draws without anti-aliasing, so a curve - or, once the
 * box is turned, a straight side - made of solid triangles is a staircase. The
 * cure is a skin round the outside that fades to nothing, so a pixel the true
 * edge cuts through gets part of the colour rather than all or none.
 *
 * <p>It is bands that never overlap: fill, a pixel of blend, the rim, then the
 * skin, each starting exactly where the last stops, so a see-through box is not
 * darker where they meet. The blend is what stops the rim's inside edge being a
 * second staircase. The rim itself is a band of one flat colour: a rim that was
 * all gradient only reached its full colour along a line with no width, and a
 * line with no width is caught by some pixels and missed by others, which round
 * a curve shows as a rim that comes and goes.
 *
 * <p>Every triangle is wound the way the renderer winds its own quads. Its 2D
 * pipelines cull back faces, and turning a shape does not change which way it
 * is wound, so that holds at any angle.
 *
 * <p>The caller owns the batch: {@code Renderer2D.COLOR.begin()} before, and
 * {@code render()} after.
 */
public final class RoundedBox {
    // Scratch for the outline, shared because everything that draws one of
    // these does so on the render thread, one at a time.
    private static double[] outX = new double[0];
    private static double[] outY = new double[0];
    private static double[] inX = new double[0];
    private static double[] inY = new double[0];
    private static double[] coreX = new double[0];
    private static double[] coreY = new double[0];
    private static double[] faceX = new double[0];
    private static double[] faceY = new double[0];

    private RoundedBox() {
    }

    /**
     * @param cx,cy   centre of the box
     * @param w,h     its size before it is turned
     * @param radius  corner radius
     * @param rim     width of the rim, or 0 for none
     * @param skin    width of the fading edge
     * @param degrees how far it is turned about its centre, clockwise on screen
     */
    public static void draw(double cx, double cy, double w, double h, double radius, double rim, double skin,
                            double degrees, Color fill, Color rimColor) {
        draw(cx, cy, w, h, radius, rim, skin, degrees, fill, rimColor, 0);
    }

    /** The same rounded box with fewer corner segments for dense, small map tiles. */
    public static void drawCompact(double cx, double cy, double w, double h, double radius, double rim, double skin,
                                   double degrees, Color fill, Color rimColor) {
        draw(cx, cy, w, h, radius, rim, skin, degrees, fill, rimColor, 1);
    }

    private static void draw(double cx, double cy, double w, double h, double radius, double rim, double skin,
                             double degrees, Color fill, Color rimColor, int compact) {
        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2) - skin / 2);

        double hw = (w - skin) / 2;
        double hh = (h - skin) / 2;

        int perCorner = compact == 0
            ? Math.max(8, (int) Math.ceil(r)) + 1
            : Math.max(3, (int) Math.ceil(r / 2)) + 1;
        int count = perCorner * 4;

        if (outX.length < count) {
            outX = new double[count];
            outY = new double[count];
            inX = new double[count];
            inY = new double[count];
            coreX = new double[count];
            coreY = new double[count];
            faceX = new double[count];
            faceY = new double[count];
        }

        // Three outlines of the same shape: the outside, the rim's inside edge,
        // and - a pixel further in - where the fill stops and starts blending
        // into the rim. With no rim the inner two sit on the outside one.
        double blend = rim > 0 ? Math.min(1, Math.min(hw, hh) - rim) : 0;
        if (blend < 0) blend = 0;

        ring(outX, outY, perCorner, hw, hh, r, 0, true);
        if (rim > 0) {
            ring(inX, inY, perCorner, hw, hh, r, rim, false);
            ring(coreX, coreY, perCorner, hw, hh, r, rim + blend, false);
        } else {
            // With no rim all three outlines coincide. The map has 201 such
            // tiles, so avoid tracing two extra curves for every one of them.
            System.arraycopy(outX, 0, coreX, 0, count);
            System.arraycopy(outY, 0, coreY, 0, count);
        }

        double cos = Math.cos(Math.toRadians(degrees));
        double sin = Math.sin(Math.toRadians(degrees));

        // Laid out about the origin; now turned and moved into place.
        for (int i = 0; i < count; i++) {
            double ox = outX[i];
            double oy = outY[i];
            double ix = inX[i];
            double iy = inY[i];
            double kx = coreX[i];
            double ky = coreY[i];
            double fx = faceX[i];
            double fy = faceY[i];

            outX[i] = cx + ox * cos - oy * sin;
            outY[i] = cy + ox * sin + oy * cos;
            inX[i] = cx + ix * cos - iy * sin;
            inY[i] = cy + ix * sin + iy * cos;
            coreX[i] = cx + kx * cos - ky * sin;
            coreY[i] = cy + kx * sin + ky * cos;
            faceX[i] = fx * cos - fy * sin;
            faceY[i] = fx * sin + fy * cos;
        }

        Color edge = rim > 0 ? rimColor : fill;
        Color clear = new Color(edge.r, edge.g, edge.b, 0);
        MeshBuilder mesh = Renderer2D.COLOR.triangles;

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;

            mesh.ensureTriCapacity();
            mesh.triangle(
                mesh.vec2(cx, cy).color(fill).next(),
                mesh.vec2(coreX[next], coreY[next]).color(fill).next(),
                mesh.vec2(coreX[i], coreY[i]).color(fill).next());

            if (rim > 0) {
                mesh.ensureQuadCapacity();
                mesh.quad(
                    mesh.vec2(coreX[i], coreY[i]).color(fill).next(),
                    mesh.vec2(coreX[next], coreY[next]).color(fill).next(),
                    mesh.vec2(inX[next], inY[next]).color(edge).next(),
                    mesh.vec2(inX[i], inY[i]).color(edge).next());

                mesh.ensureQuadCapacity();
                mesh.quad(
                    mesh.vec2(inX[i], inY[i]).color(edge).next(),
                    mesh.vec2(inX[next], inY[next]).color(edge).next(),
                    mesh.vec2(outX[next], outY[next]).color(edge).next(),
                    mesh.vec2(outX[i], outY[i]).color(edge).next());
            }

            mesh.ensureQuadCapacity();
            mesh.quad(
                mesh.vec2(outX[i], outY[i]).color(edge).next(),
                mesh.vec2(outX[next], outY[next]).color(edge).next(),
                mesh.vec2(outX[next] + faceX[next] * skin, outY[next] + faceY[next] * skin).color(clear).next(),
                mesh.vec2(outX[i] + faceX[i] * skin, outY[i] + faceY[i] * skin).color(clear).next());
        }
    }

    /**
     * A glow round the inside of a box: a band that starts {@code inset} in
     * from the box's edge at full strength and fades to nothing {@code depth}
     * further in. Drawn after the box it belongs to, over its fill.
     */
    public static void innerGlow(double cx, double cy, double w, double h, double radius, double skin, double inset, double depth,
                                 double degrees, Color color) {
        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2) - skin / 2);
        double hw = (w - skin) / 2;
        double hh = (h - skin) / 2;

        depth = Math.min(depth, Math.min(hw, hh) - inset);
        if (depth <= 0 || color.a <= 0) return;

        int perCorner = Math.max(8, (int) Math.ceil(r)) + 1;
        int count = perCorner * 4;

        if (outX.length < count) {
            outX = new double[count];
            outY = new double[count];
            inX = new double[count];
            inY = new double[count];
            coreX = new double[count];
            coreY = new double[count];
            faceX = new double[count];
            faceY = new double[count];
        }

        ring(outX, outY, perCorner, hw, hh, r, inset, true);
        ring(inX, inY, perCorner, hw, hh, r, inset + depth, false);

        double cos = Math.cos(Math.toRadians(degrees));
        double sin = Math.sin(Math.toRadians(degrees));

        Color clear = new Color(color.r, color.g, color.b, 0);
        MeshBuilder mesh = Renderer2D.COLOR.triangles;

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;

            mesh.ensureQuadCapacity();
            mesh.quad(
                mesh.vec2(cx + inX[i] * cos - inY[i] * sin, cy + inX[i] * sin + inY[i] * cos).color(clear).next(),
                mesh.vec2(cx + inX[next] * cos - inY[next] * sin, cy + inX[next] * sin + inY[next] * cos).color(clear).next(),
                mesh.vec2(cx + outX[next] * cos - outY[next] * sin, cy + outX[next] * sin + outY[next] * cos).color(color).next(),
                mesh.vec2(cx + outX[i] * cos - outY[i] * sin, cy + outX[i] * sin + outY[i] * cos).color(color).next());
        }
    }

    /**
     * A thin line the shape of the box's edge, {@code offset} outside it: soft
     * on both sides, {@code thick} from its middle to where it has faded out.
     * Moved outwards frame by frame it is a ripple leaving the box.
     */
    public static void ripple(double cx, double cy, double w, double h, double radius, double skin, double offset, double thick,
                              Color color) {
        if (color.a <= 0 || thick <= 0) return;

        double r = Math.max(0, Math.min(radius, Math.min(w, h) / 2) - skin / 2);
        double hw = (w - skin) / 2;
        double hh = (h - skin) / 2;

        int perCorner = Math.max(8, (int) Math.ceil(r + offset)) + 1;
        int count = perCorner * 4;

        if (outX.length < count) {
            outX = new double[count];
            outY = new double[count];
            inX = new double[count];
            inY = new double[count];
            coreX = new double[count];
            coreY = new double[count];
            faceX = new double[count];
            faceY = new double[count];
        }

        // An inset of less than nothing is an outline outside the box's own:
        // the same corners on a bigger radius, so it stays parallel to the edge
        // all the way round. It never starts inside the edge it is leaving.
        ring(inX, inY, perCorner, hw, hh, r, -Math.max(0, offset - thick), false);
        ring(coreX, coreY, perCorner, hw, hh, r, -offset, false);
        ring(outX, outY, perCorner, hw, hh, r, -(offset + thick), true);

        Color clear = new Color(color.r, color.g, color.b, 0);
        MeshBuilder mesh = Renderer2D.COLOR.triangles;

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;

            mesh.ensureQuadCapacity();
            mesh.quad(
                mesh.vec2(cx + inX[i], cy + inY[i]).color(clear).next(),
                mesh.vec2(cx + inX[next], cy + inY[next]).color(clear).next(),
                mesh.vec2(cx + coreX[next], cy + coreY[next]).color(color).next(),
                mesh.vec2(cx + coreX[i], cy + coreY[i]).color(color).next());

            mesh.ensureQuadCapacity();
            mesh.quad(
                mesh.vec2(cx + coreX[i], cy + coreY[i]).color(color).next(),
                mesh.vec2(cx + coreX[next], cy + coreY[next]).color(color).next(),
                mesh.vec2(cx + outX[next], cy + outY[next]).color(clear).next(),
                mesh.vec2(cx + outX[i], cy + outY[i]).color(clear).next());
        }
    }

    /**
     * A shadow: the same shape in black with a skin so wide it is nearly all
     * skin, which is what a blurred edge is. Draw it before the box it belongs to.
     */
    public static void shadow(double cx, double cy, double w, double h, double radius, double spread,
                              double degrees, double alpha) {
        Color black = new Color(0, 0, 0, (int) Math.round(255 * Math.max(0, Math.min(1, alpha))));

        draw(cx, cy, w, h, radius, 0, spread, degrees, black, black);
    }

    /** A plain rectangle turned with the box, given in the box's own unturned coordinates about its centre. */
    public static void bar(double cx, double cy, double x, double y, double w, double h, double degrees, Color color) {
        double cos = Math.cos(Math.toRadians(degrees));
        double sin = Math.sin(Math.toRadians(degrees));
        MeshBuilder mesh = Renderer2D.COLOR.triangles;

        // Top left, bottom left, bottom right, top right: the renderer's own order.
        mesh.ensureQuadCapacity();
        mesh.quad(
            mesh.vec2(cx + x * cos - y * sin, cy + x * sin + y * cos).color(color).next(),
            mesh.vec2(cx + x * cos - (y + h) * sin, cy + x * sin + (y + h) * cos).color(color).next(),
            mesh.vec2(cx + (x + w) * cos - (y + h) * sin, cy + (x + w) * sin + (y + h) * cos).color(color).next(),
            mesh.vec2(cx + (x + w) * cos - y * sin, cy + (x + w) * sin + y * cos).color(color).next());
    }

    /**
     * A filled convex shape with the same soft edge the boxes have, round any set
     * of points rather than round a rectangle.
     *
     * <p>{@code grow} pushes every edge outward by that much before the shape is
     * filled. A corner is moved along the mitre of the two edges beside it and
     * lengthened by how sharply it leans, so that both edges move out by
     * {@code grow} and not only the corner - which is what lets one of these be
     * drawn behind another, slightly grown, to give it an outline of even width.
     *
     * <p>The points may be given either way round: which way they run is worked
     * out from their signed area, and the triangles are wound to match what the
     * renderer culls. Convex only. At a corner that turns the other way the
     * outward offsets would cross, and nothing here checks for it.
     */
    public static void polygon(double[] xs, double[] ys, int count, double grow, double skin, Color fill) {
        if (count < 3) return;

        // Shoelace. Screen y runs down, so a shape wound clockwise on screen
        // comes out positive - the way ring() runs, and so the way the fill below
        // is wound.
        double area = 0;

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            area += xs[i] * ys[next] - xs[next] * ys[i];
        }

        double[] px = new double[count];
        double[] py = new double[count];

        for (int i = 0; i < count; i++) {
            int at = area >= 0 ? i : count - 1 - i;

            px[i] = xs[at];
            py[i] = ys[at];
        }

        // Each edge's outward normal.
        double[] ex = new double[count];
        double[] ey = new double[count];

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            double dx = px[next] - px[i];
            double dy = py[next] - py[i];
            double len = Math.hypot(dx, dy);

            ex[i] = len == 0 ? 0 : dy / len;
            ey[i] = len == 0 ? 0 : -dx / len;
        }

        // Then each corner's, as a direction to move a unit distance along: the
        // mitre of its two edges, divided by how much of an edge's own normal the
        // mitre is, which is what makes the edges move rather than the point. A
        // floor on that keeps a very sharp corner from running away.
        double[] mx = new double[count];
        double[] my = new double[count];

        for (int i = 0; i < count; i++) {
            int prev = (i + count - 1) % count;
            double sx = ex[prev] + ex[i];
            double sy = ey[prev] + ey[i];
            double len = Math.hypot(sx, sy);

            if (len < 1e-6) {
                mx[i] = ex[i];
                my[i] = ey[i];
            } else {
                double lean = Math.max(0.3, (sx * ex[i] + sy * ey[i]) / len);

                mx[i] = sx / len / lean;
                my[i] = sy / len / lean;
            }
        }

        double middleX = 0;
        double middleY = 0;

        for (int i = 0; i < count; i++) {
            middleX += px[i] + mx[i] * grow;
            middleY += py[i] + my[i] * grow;
        }

        middleX /= count;
        middleY /= count;

        // The solid part is pulled in by half the skin and the skin starts where
        // it stops, so the shape does not grow and a see-through one is not
        // darker round its edge - the same arrangement as a box's.
        double solid = grow - skin / 2;
        Color clear = new Color(fill.r, fill.g, fill.b, 0);
        MeshBuilder mesh = Renderer2D.COLOR.triangles;

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;

            double ax = px[i] + mx[i] * solid;
            double ay = py[i] + my[i] * solid;
            double bx = px[next] + mx[next] * solid;
            double by = py[next] + my[next] * solid;

            mesh.ensureTriCapacity();
            mesh.triangle(
                mesh.vec2(middleX, middleY).color(fill).next(),
                mesh.vec2(bx, by).color(fill).next(),
                mesh.vec2(ax, ay).color(fill).next());

            mesh.ensureQuadCapacity();
            mesh.quad(
                mesh.vec2(ax, ay).color(fill).next(),
                mesh.vec2(bx, by).color(fill).next(),
                mesh.vec2(bx + mx[next] * skin, by + my[next] * skin).color(clear).next(),
                mesh.vec2(ax + mx[i] * skin, ay + my[i] * skin).color(clear).next());
        }
    }

    /**
     * One outline, clockwise from the top left, {@code inset} inside the box's
     * own. Where a corner is tighter than the inset there is no smaller curve to
     * draw, so that corner goes square rather than turning itself inside out.
     * The straight sides need no points of their own: they are the gap between
     * the end of one corner and the start of the next.
     */
    private static void ring(double[] xs, double[] ys, int perCorner, double hw, double hh, double r, double inset, boolean facing) {
        double ringR = Math.max(0, r - inset);
        double push = inset - (r - ringR);

        int at = 0;

        for (int corner = 0; corner < 4; corner++) {
            boolean left = corner == 0 || corner == 3;
            boolean top = corner == 0 || corner == 1;
            double centreX = left ? -hw + r : hw - r;
            double centreY = top ? -hh + r : hh - r;
            double pushX = left ? push : -push;
            double pushY = top ? push : -push;
            double start = Math.toRadians((180 + corner * 90) % 360);

            // Screen y runs down, so a growing angle is clockwise.
            for (int i = 0; i < perCorner; i++) {
                double angle = start + Math.PI / 2 * i / (perCorner - 1);
                double fx = Math.cos(angle);
                double fy = Math.sin(angle);

                if (facing) {
                    faceX[at] = fx;
                    faceY[at] = fy;
                }

                xs[at] = centreX + pushX + fx * ringR;
                ys[at] = centreY + pushY + fy * ringR;
                at++;
            }
        }
    }
}
