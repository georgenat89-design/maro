package dev.maro.module.impl.visuals;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.esp.BlockEspRenderer;
import dev.maro.render.esp.Renderer3D;
import dev.maro.render.esp.ShapeMode;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.item.WindChargeItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Trajectories: where what you are about to throw or shoot will go (pearls, arrows, tridents and
 * more), drawn as a line to where it lands, with the landing point, the time it takes and the entity
 * it would hit; and the same for pearls, arrows and the rest already in the air, with whose they are.
 */
public class Trajectories extends Module {
    private enum Kind {
        PEARL("Pearl", 0.03, 0.99, 0.8, false),
        THROWN("Snowball", 0.03, 0.99, 0.8, false),
        POTION("Potion", 0.05, 0.99, 0.8, false),
        BOTTLE("XP Bottle", 0.07, 0.99, 0.8, false),
        ARROW("Arrow", 0.05, 0.99, 0.6, true),
        TRIDENT("Trident", 0.05, 0.99, 0.99, true),
        WIND("Wind Charge", 0, 1, 1, false);

        final String label;
        final double gravity, drag, waterDrag;
        /** Arrows move before they slow and fall; thrown things slow and fall first. */
        final boolean moveFirst;

        Kind(String label, double gravity, double drag, double waterDrag, boolean moveFirst) {
            this.label = label;
            this.gravity = gravity;
            this.drag = drag;
            this.waterDrag = waterDrag;
            this.moveFirst = moveFirst;
        }
    }

    // ---- what to show
    private final BooleanSetting pearls = add(new BooleanSetting("Ender Pearl", "Ender pearls", true));
    private final BooleanSetting arrows = add(new BooleanSetting("Arrow", "Arrows from bows and crossbows (all three with Multishot)", true));
    private final BooleanSetting tridents = add(new BooleanSetting("Trident", "Thrown tridents", true));
    private final BooleanSetting throwables = add(new BooleanSetting("Snowballs & Eggs", "Snowballs and eggs", true));
    private final BooleanSetting potions = add(new BooleanSetting("Potions", "Splash and lingering potions and bottles o' enchanting", true));
    private final BooleanSetting windCharges = add(new BooleanSetting("Wind Charges", "Wind charges", true));

    // ---- labels and points
    private final BooleanSetting showOwner = add(new BooleanSetting("Show Owner", "Whose pearl, arrow or trident it is, over it in the air", true));
    private final BooleanSetting labelsOnlyMoving = add(new BooleanSetting("Labels Only Moving", "No labels on things that have stopped (arrows stuck in the ground)", true));
    private final BooleanSetting impactLabels = add(new BooleanSetting("Impact Labels", "A label where each lands: what it is and how far", false));
    private final BooleanSetting timer = add(new BooleanSetting("Trajectory Timer", "How long until it lands", true));
    private final BooleanSetting landingPoint = add(new BooleanSetting("Show Landing Point", "A marker where it lands", true));
    private final BooleanSetting held = add(new BooleanSetting("Held Prediction", "The path of what you hold, before you throw it", true));
    private final BooleanSetting trackThrown = add(new BooleanSetting("Track Thrown", "Paths of pearls, arrows and the rest already in the air", true));
    private final NumberSetting trackDuration = add(new NumberSetting("Track Duration (s)", "How long a thrown thing is followed after it is first seen", 3, 0.5, 20, 0.5)
            .visible(trackThrown::get));
    private final BooleanSetting hitEntity = add(new BooleanSetting("Hit Highlight", "A box round the player or mob it would hit", true));
    private final BooleanSetting splashRing = add(new BooleanSetting("Splash Radius", "A ring where a splash potion lands, as far as it reaches", true));

    // ---- look
    private final NumberSetting lineWidth = add(new NumberSetting("Line Width", "How thick the path is", 2, 0.5, 4, 0.25));
    private final NumberSetting pointSize = add(new NumberSetting("Point Size", "How big the landing marker is", 5, 1, 15, 0.5));
    private final ColorSetting lineColor = add(new ColorSetting("Line Color", "Your own paths", 0xFFE8E8E8));
    private final ColorSetting hitColor = add(new ColorSetting("Hit Color", "A path that will hit someone, and its marker", 0xFFFF3B3B));
    private final ColorSetting thrownColor = add(new ColorSetting("Thrown Color", "Paths of things already in the air", 0xFF4FA3E0));
    private final BooleanSetting fade = add(new BooleanSetting("Fade", "The line fades in from your hand and out towards where it lands", true));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", "See the paths through blocks", true));
    private final NumberSetting maxPoints = add(new NumberSetting("Max Points", "How many ticks of flight are worked out at most", 200, 20, 600, 10));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Projectiles", pearls, arrows, tridents, throwables, potions, windCharges),
            SettingSection.of("Labels", showOwner, labelsOnlyMoving, impactLabels, timer, landingPoint, held, trackThrown, trackDuration,
                    hitEntity, splashRing),
            SettingSection.of("Look", lineWidth, pointSize, lineColor, hitColor, thrownColor, fade, throughWalls, maxPoints));

    /** A worked-out flight: its points, where it ends, what it hits and how long it takes. */
    public record Path(Kind kind, List<Vec3d> points, Vec3d end, Entity hit, int ticks, boolean landed) {
    }

    private record Label(Vec3d at, String text, int color) {
    }

    /** When each thrown thing was first seen, to stop following it after Track Duration. */
    private final Map<UUID, Long> firstSeen = new HashMap<>();
    /** The labels worked out while drawing the world, drawn over the screen after it. */
    private final List<Label> labels = new ArrayList<>();
    /** Your own predicted paths last frame; for tests. */
    private final List<Path> lastHeld = new ArrayList<>();
    private int lastThrown;

    public Trajectories() {
        super("Trajectories", "Where pearls, arrows, tridents and potions will land, yours and those in the air", Category.VISUALS);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onDisable() {
        firstSeen.clear();
        labels.clear();
        lastHeld.clear();
    }

    // ---- what you hold -------------------------------------------------------------------------

    /** The paths of what you hold now (several for a Multishot crossbow), or none. */
    public List<Path> heldPaths(float tickDelta) {
        List<Path> out = new ArrayList<>();
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) return out;
        for (Hand hand : Hand.values()) {
            ItemStack stack = p.getStackInHand(hand);
            if (stack.isEmpty()) continue;
            Kind kind = null;
            double speed = 0, pitchOffset = 0;
            float[] spreads = {0};
            if (stack.getItem() instanceof EnderPearlItem && pearls.get()) {
                kind = Kind.PEARL;
                speed = 1.5;
            } else if ((stack.getItem() instanceof SnowballItem || stack.getItem() instanceof EggItem) && throwables.get()) {
                kind = Kind.THROWN;
                speed = 1.5;
            } else if (stack.getItem() instanceof ThrowablePotionItem && potions.get()) {
                kind = Kind.POTION;
                speed = 0.5;
                pitchOffset = -20;
            } else if (stack.getItem() instanceof ExperienceBottleItem && potions.get()) {
                kind = Kind.BOTTLE;
                speed = 0.7;
                pitchOffset = -20;
            } else if (stack.getItem() instanceof WindChargeItem && windCharges.get()) {
                kind = Kind.WIND;
                speed = 1.5;
            } else if (stack.getItem() instanceof TridentItem && tridents.get()) {
                kind = Kind.TRIDENT;
                speed = 2.5;
            } else if (stack.getItem() instanceof BowItem && arrows.get()) {
                kind = Kind.ARROW;
                // While drawing, as far as it is pulled; otherwise as if pulled all the way.
                float pull = p.isUsingItem() && p.getActiveHand() == hand ? BowItem.getPullProgress(p.getItemUseTime()) : 1;
                if (pull < 0.1f) continue;
                speed = pull * 3.0;
            } else if (stack.getItem() instanceof CrossbowItem && arrows.get() && CrossbowItem.isCharged(stack)) {
                kind = Kind.ARROW;
                speed = 3.15;
                if (multishot(stack)) spreads = new float[] {0, -10, 10};
            }
            if (kind == null) continue;
            for (float spread : spreads) out.add(simulate(kind, start(p, tickDelta), launch(p, speed, pitchOffset, spread), p));
            break;
        }
        return out;
    }

    private boolean multishot(ItemStack stack) {
        try {
            var entry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.MULTISHOT);
            return EnchantmentHelper.getLevel(entry, stack) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Vec3d start(PlayerEntity p, float tickDelta) {
        Vec3d at = p.getLerpedPos(tickDelta);
        return new Vec3d(at.x, at.y + p.getStandingEyeHeight() - 0.1, at.z);
    }

    /** The velocity a projectile leaves your hand with, your own movement added as the game does. */
    private static Vec3d launch(PlayerEntity p, double speed, double pitchOffset, float yawSpread) {
        float yaw = p.getYaw() + yawSpread, pitch = p.getPitch();
        double x = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        double y = -MathHelper.sin((float) (pitch + pitchOffset) * MathHelper.RADIANS_PER_DEGREE);
        double z = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        Vec3d v = new Vec3d(x, y, z).normalize().multiply(speed);
        Vec3d move = p.getMovement();
        return v.add(move.x, p.isOnGround() ? 0 : move.y, move.z);
    }

    // ---- the flight ----------------------------------------------------------------------------

    /** Flies a projectile forward tick by tick until it hits a block or an entity, or runs out of points. */
    public Path simulate(Kind kind, Vec3d pos, Vec3d velocity, Entity owner) {
        List<Vec3d> points = new ArrayList<>();
        points.add(pos);
        int max = maxPoints.getInt();
        int bottom = mc.world.getBottomY() - 64;
        for (int tick = 1; tick <= max; tick++) {
            if (!kind.moveFirst) velocity = slow(kind, pos, velocity);
            Vec3d next = pos.add(velocity);
            // A block in the way.
            BlockHitResult block = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, owner == null ? mc.player : owner));
            Vec3d end = block.getType() == HitResult.Type.BLOCK ? block.getPos() : next;
            // An entity in the way, nearer than the block.
            Entity struck = null;
            double best = Double.MAX_VALUE;
            Box sweep = new Box(pos, end).expand(1);
            for (Entity e : mc.world.getOtherEntities(owner, sweep, e -> e.isAlive() && e.canHit() && !e.isSpectator()
                    && !(e instanceof ProjectileEntity))) {
                var hit = e.getBoundingBox().expand(0.3).raycast(pos, end);
                if (hit.isPresent() && hit.get().squaredDistanceTo(pos) < best) {
                    best = hit.get().squaredDistanceTo(pos);
                    struck = e;
                    end = hit.get();
                }
            }
            points.add(end);
            if (struck != null || block.getType() == HitResult.Type.BLOCK) return new Path(kind, points, end, struck, tick, true);
            pos = next;
            if (kind.moveFirst) velocity = slow(kind, pos, velocity);
            if (pos.y < bottom) break;
        }
        return new Path(kind, points, pos, null, max, false);
    }

    private Vec3d slow(Kind kind, Vec3d pos, Vec3d velocity) {
        FluidState fluid = mc.world.getFluidState(BlockPos.ofFloored(pos));
        double drag = fluid.isIn(FluidTags.WATER) ? kind.waterDrag : kind.drag;
        return new Vec3d(velocity.x * drag, velocity.y * drag - kind.gravity, velocity.z * drag);
    }

    // ---- what is in the air --------------------------------------------------------------------

    private Kind kindOf(Entity e) {
        if (e instanceof EnderPearlEntity) return pearls.get() ? Kind.PEARL : null;
        if (e instanceof SnowballEntity || e instanceof EggEntity) return throwables.get() ? Kind.THROWN : null;
        if (e instanceof PotionEntity) return potions.get() ? Kind.POTION : null;
        if (e instanceof ExperienceBottleEntity) return potions.get() ? Kind.BOTTLE : null;
        if (e instanceof WindChargeEntity) return windCharges.get() ? Kind.WIND : null;
        if (e instanceof TridentEntity) return tridents.get() ? Kind.TRIDENT : null;
        if (e instanceof ArrowEntity || e instanceof SpectralArrowEntity) return arrows.get() ? Kind.ARROW : null;
        return null;
    }

    // ---- drawing -------------------------------------------------------------------------------

    /** Draws every path into the world and works out the labels for {@link #onRender2D}. */
    public void render(Renderer3D renderer, float tickDelta) {
        labels.clear();
        lastHeld.clear();
        if (!inGame()) return;
        renderer.throughWalls(throughWalls.get());
        renderer.lineWidth(lineWidth.getFloat());
        if (held.get()) {
            for (Path path : heldPaths(tickDelta)) {
                lastHeld.add(path);
                int color = path.hit() != null ? hitColor.get() : lineColor.get();
                draw(renderer, path, color | 0xFF000000, true);
                if (impactLabels.get() || timer.get()) {
                    String text = impactLabels.get() ? path.kind().label + "  " + Math.round(path.end().distanceTo(mc.player.getEyePos())) + "m" : "";
                    if (timer.get()) text = (text.isEmpty() ? "" : text + "  ") + seconds(path.ticks());
                    if (path.landed()) labels.add(new Label(path.end().add(0, 0.4, 0), text, color | 0xFF000000));
                }
            }
        }
        lastThrown = 0;
        if (trackThrown.get()) {
            long now = System.currentTimeMillis();
            long keep = Math.round(trackDuration.get() * 1000);
            firstSeen.values().removeIf(t -> now - t > keep + 60_000);
            for (Entity e : mc.world.getEntities()) {
                Kind kind = kindOf(e);
                if (kind == null) continue;
                long seen = firstSeen.computeIfAbsent(e.getUuid(), id -> now);
                if (now - seen > keep) continue;
                Vec3d velocity = e.getVelocity();
                // Stuck in a block, an arrow or trident has no speed left.
                boolean moving = velocity.lengthSquared() > 1e-4;
                Entity owner = e instanceof ProjectileEntity projectile ? projectile.getOwner() : null;
                Vec3d at = e.getLerpedPos(tickDelta);
                if (moving) {
                    Path path = simulate(kind, at, velocity, e);
                    lastThrown++;
                    int color = path.hit() != null ? hitColor.get() : thrownColor.get();
                    draw(renderer, path, color | 0xFF000000, false);
                    if ((impactLabels.get() || timer.get()) && path.landed()) {
                        String text = impactLabels.get() ? kind.label : "";
                        if (timer.get()) text = (text.isEmpty() ? "" : text + "  ") + seconds(path.ticks());
                        labels.add(new Label(path.end().add(0, 0.4, 0), text, color | 0xFF000000));
                    }
                }
                if (showOwner.get() && owner != null && (moving || !labelsOnlyMoving.get())) {
                    labels.add(new Label(at.add(0, 0.6, 0), owner.getName().getString() + "'s " + kind.label, thrownColor.get() | 0xFF000000));
                }
            }
        }
        renderer.throughWalls(true);
        renderer.lineWidth(1.5f);
    }

    private void draw(Renderer3D renderer, Path path, int color, boolean fromHand) {
        List<Vec3d> points = path.points();
        int n = points.size();
        if (n < 2) return;
        // Your own path starts a little to the side, where your hand is, and joins the true path.
        Vec3d offset = Vec3d.ZERO;
        if (fromHand && mc.options.getPerspective().isFirstPerson()) {
            float yaw = mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            boolean right = mc.player.getMainArm() == net.minecraft.util.Arm.RIGHT;
            offset = new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw)).multiply(right ? 0.25 : -0.25).add(0, -0.12, 0);
        }
        for (int i = 0; i + 1 < n; i++) {
            Vec3d a = points.get(i), b = points.get(i + 1);
            if (fromHand) {
                a = a.add(offset.multiply(Math.max(0, 1 - i / 6.0)));
                b = b.add(offset.multiply(Math.max(0, 1 - (i + 1) / 6.0)));
            }
            int alpha = 255;
            if (fade.get()) {
                float t = i / (float) (n - 1);
                alpha = Math.round(255 * Math.min(1, Math.min(fromHand ? 0.25f + i / 3f : 1f, 1.15f - t * 0.55f)));
            }
            renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, new Color(ColorUtil.withAlpha(color, Math.max(40, alpha))));
        }
        if (landingPoint.get() && path.landed()) {
            double r = pointSize.get() * 0.03;
            Vec3d e = path.end();
            renderer.box(e.x - r, e.y - r, e.z - r, e.x + r, e.y + r, e.z + r,
                    new Color(ColorUtil.withAlpha(color, 0x50)), new Color(color), ShapeMode.Both, 0);
        }
        if (hitEntity.get() && path.hit() != null) {
            Box box = path.hit().getBoundingBox();
            renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                    new Color(ColorUtil.withAlpha(hitColor.get(), 0x30)), new Color(hitColor.get() | 0xFF000000), ShapeMode.Both, 0);
        }
        if (splashRing.get() && path.kind() == Kind.POTION && path.landed()) {
            Vec3d e = path.end();
            double radius = 4;
            int segments = 48;
            for (int i = 0; i < segments; i++) {
                double a1 = i * Math.PI * 2 / segments, a2 = (i + 1) * Math.PI * 2 / segments;
                renderer.line(e.x + Math.cos(a1) * radius, e.y + 0.05, e.z + Math.sin(a1) * radius,
                        e.x + Math.cos(a2) * radius, e.y + 0.05, e.z + Math.sin(a2) * radius, new Color(ColorUtil.withAlpha(color, 0xB0)));
            }
        }
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (labels.isEmpty() || !inGame() || mc.options.hudHidden) return;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        Fonts.beginRaw();
        try {
            for (Label label : labels) {
                if (label.text().isEmpty()) continue;
                float[] at = BlockEspRenderer.toScreen(label.at().x, label.at().y, label.at().z, camera, w, h);
                if (at == null) continue;
                float size = 0.72f, tw = Fonts.width(label.text(), true, size) + 10;
                Render2D.roundRect(ctx, at[0] - tw / 2f, at[1] - 6.5f, tw, 13, 4, 0xC00D0F14);
                Render2D.roundRect(ctx, at[0] - tw / 2f, at[1] + 5.5f, tw, 1, 0.5f, label.color());
                Fonts.drawCentered(ctx, label.text(), at[0], at[1], 0xFFF2F4F8, true, size);
            }
        } finally {
            Fonts.endRaw();
        }
    }

    // ---- for tests -------------------------------------------------------------------------------

    public List<Path> lastHeldPaths() {
        return new ArrayList<>(lastHeld);
    }

    public int lastThrownCount() {
        return lastThrown;
    }

    public List<String> lastLabels() {
        List<String> out = new ArrayList<>();
        for (Label label : labels) out.add(label.text());
        return out;
    }
}
