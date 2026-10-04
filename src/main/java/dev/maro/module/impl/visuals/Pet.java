package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** A render-only companion. Never registered in a world, ticked by mob AI, or sent to a server. */
public final class Pet extends Module {
    public static final String[] STYLES = {"Wolf", "Cat", "Fox", "Bunny", "Bee", "Allay", "Parrot", "Axolotl", "Slime", "Turtle", "Panda", "Pig"};
    private final ModeSetting style = add(new ModeSetting("Pet", "Choose your companion", "Wolf", STYLES));
    private final NumberSetting size = add(new NumberSetting("Size", "Scale your companion", .85, .3, 2, .05));
    private final BooleanSetting baby = add(new BooleanSetting("Baby", "Use a baby model when available", false));
    private final ModeSetting collar = add(new ModeSetting("Collar", "Wolf and cat collar color", "Purple", "Purple", "Pink", "Cyan", "Red", "Blue", "Lime", "White", "Black"));
    private final ModeSetting formation = add(new ModeSetting("Formation", "Follow your trail, stay beside you, or orbit", "Follow", "Follow", "Sidekick", "Orbit"));
    private final ModeSetting side = add(new ModeSetting("Side", "Preferred side of the player", "Right", "Left", "Right"));
    private final NumberSetting distance = add(new NumberSetting("Distance", "Distance from you in blocks", 1.8, .8, 5, .1));
    private final NumberSetting speed = add(new NumberSetting("Follow Speed", "How quickly your pet catches up", 1, .25, 2.5, .05));
    private final ModeSetting movement = add(new ModeSetting("Movement", "Auto walks ground pets and floats flying pets", "Auto", "Auto", "Walk", "Hover"));
    private final NumberSetting height = add(new NumberSetting("Hover Height", "Height above your feet when hovering", 1, .3, 2.5, .05));
    private final NumberSetting recallDistance = add(new NumberSetting("Recall Distance", "Bring the pet back if it gets stuck or left behind", 12, 6, 24, 1));
    private final ButtonSetting recall = add(new ButtonSetting("Recall Pet", "Bring your companion back beside you", "Recall", this::recall));
    private final BooleanSetting animate = add(new BooleanSetting("Animate", "Walking, wings, gentle floating, and idle movement", true));
    private final BooleanSetting bright = add(new BooleanSetting("Fullbright", "Keep the pet bright in dark areas", false));
    private final BooleanSetting nameTag = add(new BooleanSetting("Name Tag", "Show a nickname above your companion", false));
    private final ModeSetting nickname = add(new ModeSetting("Nickname", "Your pet's name", "Buddy", "Buddy", "Nova", "Luna", "Mochi", "Pixel", "Bean", "Orbit").visible(nameTag::get));

    private final ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    private LivingEntity companion;
    private World world;
    private UUID owner;
    private String currentStyle;
    private Vec3d position, previous, lastOwner;
    private Vec3d velocity = Vec3d.ZERO;
    private float heading, yaw, previousYaw, walkPhase, walkAmplitude;
    private int ticks;

    public Pet() { super("Pet", "An animated companion that follows you. Cosmetic and visible only in your client.", Category.VISUALS); }
    @Override public List<SettingSection> getSettingSections() {
        return List.of(section("Companion", style, size, baby, collar), section("Following", formation, side, distance, speed, movement, height, recallDistance, recall), section("Appearance", animate, bright, nameTag, nickname));
    }
    private SettingSection section(String title, Setting<?>... settings) {
        var result = new SettingSection(title); for (var setting : settings) result.add(setting); return result;
    }
    @Override protected void onDisable() { clear(); }
    private void clear() {
        companion = null; world = null; owner = null; currentStyle = null;
        position = previous = lastOwner = null; trail.clear(); velocity = Vec3d.ZERO; ticks = 0;
        walkPhase = walkAmplitude = 0;
    }
    public LivingEntity companion() { return companion; }
    public Vec3d position() { return position; }

    private EntityType<? extends LivingEntity> type() {
        return switch (style.get()) {
            case "Cat" -> EntityType.CAT; case "Fox" -> EntityType.FOX; case "Bunny" -> EntityType.RABBIT;
            case "Bee" -> EntityType.BEE; case "Allay" -> EntityType.ALLAY; case "Parrot" -> EntityType.PARROT;
            case "Axolotl" -> EntityType.AXOLOTL; case "Slime" -> EntityType.SLIME; case "Turtle" -> EntityType.TURTLE;
            case "Panda" -> EntityType.PANDA; case "Pig" -> EntityType.PIG; default -> EntityType.WOLF;
        };
    }
    private boolean hovering() {
        if (movement.is("Hover")) return true;
        if (movement.is("Walk")) return false;
        return style.is("Bee") || style.is("Allay") || style.is("Parrot") || mc.player.getAbilities().flying || mc.player.isGliding() || mc.player.isTouchingWater();
    }
    private Vec3d offset(double behind, double sideways) {
        double angle = Math.toRadians(heading);
        return mc.player.getEntityPos().add(Math.sin(angle) * behind - Math.cos(angle) * sideways, 0,
            -Math.cos(angle) * behind - Math.sin(angle) * sideways);
    }
    public void recall() {
        if (!inGame() || companion == null) return;
        heading = mc.player.getYaw();
        Vec3d target = offset(formation.is("Sidekick") ? 0 : distance.get(), side.is("Right") ? .8 : -.8);
        position = previous = grounded(target, hovering()); velocity = Vec3d.ZERO;
        yaw = previousYaw = heading; lastOwner = mc.player.getEntityPos();
        trail.clear(); trail.add(lastOwner); syncEntity();
    }
    @Override public void onTick() {
        if (!inGame() || !mc.player.isAlive() || mc.player.isSpectator()) { clear(); return; }
        if (companion == null || world != mc.world || !mc.player.getUuid().equals(owner) || !style.get().equals(currentStyle)) {
            clear(); world = mc.world; owner = mc.player.getUuid(); currentStyle = style.get();
            companion = type().create(world, SpawnReason.COMMAND);
            if (companion == null) { clear(); return; }
            if (companion instanceof TameableEntity tameable) tameable.setTamed(true, false);
            if (companion instanceof SlimeEntity slime) slime.setSize(1, false);
            recall();
        }
        if (companion instanceof PassiveEntity passive) passive.setBaby(baby.get());
        Vec3d ownerPos = mc.player.getEntityPos();
        Vec3d ownerStep = ownerPos.subtract(lastOwner);
        if (ownerStep.lengthSquared() > 256 || position.squaredDistanceTo(ownerPos) > recallDistance.get() * recallDistance.get()) { recall(); return; }
        if (ownerStep.horizontalLengthSquared() > .0025) heading = (float)Math.toDegrees(Math.atan2(-ownerStep.x, ownerStep.z));
        lastOwner = ownerPos;
        if (trail.isEmpty() || trail.peekLast().squaredDistanceTo(ownerPos) > .0225) trail.addLast(ownerPos);
        while (trail.size() > 160) trail.removeFirst();
        ticks++;
        boolean floating = hovering();
        Vec3d goal;
        if (formation.is("Orbit")) {
            double angle = ticks * .025 * speed.get();
            goal = ownerPos.add(Math.cos(angle) * distance.get(), 0, Math.sin(angle) * distance.get());
        } else if (formation.is("Sidekick")) goal = offset(.25, side.is("Right") ? distance.get() : -distance.get());
        else goal = followGoal();
        goal = grounded(goal, floating);
        if (floating && animate.get()) goal = goal.add(0, Math.sin(ticks * .11) * .07, 0);
        previous = position; previousYaw = yaw;
        Vec3d delta = goal.subtract(position);
        double maximum = .38 * speed.get();
        Vec3d desired = delta.multiply(.22 * speed.get());
        if (desired.length() > maximum) desired = desired.normalize().multiply(maximum);
        velocity = velocity.lerp(desired, .42);
        if (delta.lengthSquared() < .0004) velocity = velocity.multiply(.5);
        Vec3d next = position.add(velocity);
        if (!floating) next = new Vec3d(next.x, MathHelper.lerp(.45, position.y, grounded(next, false).y), next.z);
        if (clearAt(next)) position = next;
        else if (!floating && clearAt(next.add(0, .6, 0))) position = next.add(0, .6, 0);
        else { velocity = Vec3d.ZERO; }
        Vec3d step = position.subtract(previous);
        float desiredYaw = step.horizontalLengthSquared() > .00005 ? (float)Math.toDegrees(Math.atan2(-step.x, step.z)) : yaw;
        yaw += MathHelper.clamp(MathHelper.wrapDegrees(desiredYaw - yaw), -18, 18);
        walkAmplitude = MathHelper.lerp(.35f, walkAmplitude, animate.get() ? (float)Math.min(1, step.horizontalLength() * 4) : 0);
        if (animate.get()) walkPhase += (float)step.horizontalLength() * 3;
        syncEntity();
    }
    private Vec3d followGoal() {
        Vec3d cursor = mc.player.getEntityPos(); double remaining = distance.get();
        var samples = trail.descendingIterator();
        while (samples.hasNext()) {
            Vec3d sample = samples.next(); double length = cursor.distanceTo(sample);
            if (length >= remaining && length > .0001) return cursor.lerp(sample, remaining / length);
            remaining -= length; cursor = sample;
        }
        return offset(distance.get(), side.is("Right") ? .8 : -.8);
    }
    private Vec3d grounded(Vec3d target, boolean floating) {
        if (floating) return new Vec3d(target.x, mc.player.getY() + height.get(), target.z);
        double reference = Math.max(target.y, mc.player.getY());
        var hit = mc.world.raycast(new RaycastContext(new Vec3d(target.x, reference + 1.25, target.z),
            new Vec3d(target.x, reference - 4, target.z), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        // Do not walk into an unloaded chunk or fall into a deep hole.
        return new Vec3d(target.x, hit.getType() == HitResult.Type.BLOCK ? hit.getPos().y + .015 : position != null ? position.y : mc.player.getY(), target.z);
    }
    private boolean clearAt(Vec3d target) {
        double width = companion.getWidth() * size.get() * .45;
        double tall = companion.getHeight() * size.get() * .9;
        return mc.world.isBlockSpaceEmpty(null, new Box(target.x - width, target.y + .04, target.z - width, target.x + width, target.y + tall, target.z + width));
    }
    private void syncEntity() {
        companion.lastX = companion.lastRenderX = previous.x;
        companion.lastY = companion.lastRenderY = previous.y;
        companion.lastZ = companion.lastRenderZ = previous.z;
        companion.lastYaw = companion.lastBodyYaw = companion.lastHeadYaw = previousYaw;
        companion.setPosition(position); companion.setYaw(yaw); companion.setBodyYaw(yaw); companion.setHeadYaw(yaw);
        companion.setPitch(0); companion.age = animate.get() ? ticks : 0;
    }
    public void render(EntityRenderManager manager, MatrixStack matrices, WorldRenderState worldState, OrderedRenderCommandQueue queue) {
        if (!isEnabled() || !inGame() || !mc.player.isAlive() || mc.player.isSpectator() || world != mc.world || companion == null || !owner.equals(mc.player.getUuid())) return;
        float delta = mc.getRenderTickCounter().getTickProgress(false);
        var state = (LivingEntityRenderState)manager.getAndUpdateRenderState(companion, delta);
        Vec3d interpolated = previous.lerp(position, delta);
        state.x = interpolated.x; state.y = interpolated.y; state.z = interpolated.z;
        state.baseScale *= size.getFloat(); state.shadowRadius *= size.getFloat();
        state.age = animate.get() ? ticks + delta : 0;
        state.limbSwingAmplitude = walkAmplitude;
        state.limbSwingAnimationProgress = walkPhase;
        state.outlineColor = 0;
        if (bright.get()) state.light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        DyeColor dye = DyeColor.valueOf(collar.get().toUpperCase(java.util.Locale.ROOT));
        if (state instanceof WolfEntityRenderState wolf) { wolf.collarColor = dye; wolf.tailAngle = 1.2f; }
        if (state instanceof CatEntityRenderState cat) cat.collarColor = dye;
        if (state instanceof ParrotEntityRenderState parrot) parrot.flapAngle = animate.get() ? (float)Math.sin(state.age * .7) * .7f : 0;
        if (state instanceof RabbitEntityRenderState bunny) bunny.jumpProgress = animate.get() ? Math.max(0, (float)Math.sin(walkPhase * 2)) * walkAmplitude : 0;
        if (state instanceof SlimeEntityRenderState slime) slime.stretch = animate.get() ? (float)Math.sin(state.age * .3) * .12f : 0;
        state.displayName = nameTag.get() ? Text.literal(nickname.get()) : null;
        state.nameLabelPos = nameTag.get() ? new Vec3d(0, companion.getHeight() * size.get() + .35, 0) : null;
        Vec3d camera = worldState.cameraRenderState.pos;
        manager.render(state, worldState.cameraRenderState, state.x - camera.x, state.y - camera.y, state.z - camera.z, matrices, queue);
    }
}
