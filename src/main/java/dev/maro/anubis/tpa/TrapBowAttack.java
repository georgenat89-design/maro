package dev.maro.anubis.tpa;

import dev.maro.anubis.CombatBridge;
import dev.maro.anubis.Rotation;
import dev.maro.anubis.ServerRotation;
import dev.maro.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Native bow use/release, with Meteor BowSpam's configurable charge as a behavior reference.
 * Copyright Meteor Development; reference under GPL-3.0, see THIRD_PARTY_NOTICES.md.
 * Target ownership, trajectory checks and cancellation are Maro additions. */
final class TrapBowAttack {
    enum Result { WAITING, SHOT, NO_BOW, NO_ARROWS }
    private ClientPlayerEntity owner;
    private Object world;
    private ItemStack drawn = ItemStack.EMPTY;
    private int slot = -1, previousSlot = -1, targetId = -1, nextUseAge, alignedTicks;
    private boolean ownsUse;

    boolean ownsUse(ClientPlayerEntity player) {
        return ownsUse && owner == player && player.isUsingItem()
            && player.getActiveHand() == Hand.MAIN_HAND && player.getActiveItem() == drawn;
    }

    Result tick(MinecraftClient mc, AbstractClientPlayerEntity target, int requestedSlot,
                int charge, boolean adaptive, int delay, boolean autoShoot, boolean autoLock, boolean conservative) {
        ClientPlayerEntity player = mc.player;
        if (owner != player || world != mc.world) {
            cancel(mc);
            owner = player;
            world = mc.world;
            nextUseAge = 0;
        }
        if (target == null || !target.isAlive() || target.deathTime > 0 || target.isRemoved()
            || target.getEntityWorld() != mc.world) {
            cancel(mc);
            return Result.WAITING;
        }
        if (!autoShoot && ownsUse) cancel(mc);
        if (ownsUse && CombatBridge.isPhysicallyDown(mc, mc.options.useKey)) {
            // Hand an active draw back to the real use button without releasing it.
            ownsUse = false;
            drawn = ItemStack.EMPTY;
            slot = previousSlot = targetId = -1;
        }
        if (ownsUse && (!ownsUse(player) || player.getInventory().getSelectedSlot() != slot
            || targetId != target.getId() || requestedSlot > 0 && requestedSlot - 1 != slot)) {
            cancel(mc);
            return Result.WAITING;
        }
        boolean manual = player.isUsingItem() && !ownsUse(player);
        if (manual && (player.getActiveHand() != Hand.MAIN_HAND || !player.getActiveItem().isOf(Items.BOW))) return Result.WAITING;
        if (!ownsUse && !manual && player.age < nextUseAge) return Result.WAITING;
        int bowSlot = ownsUse ? slot : manual ? player.getInventory().getSelectedSlot() : findBow(player, requestedSlot);
        if (bowSlot < 0) { cancel(mc); return Result.NO_BOW; }
        ItemStack bow = player.getInventory().getStack(bowSlot);
        if (!player.getAbilities().creativeMode && player.getProjectileType(bow).isEmpty()) {
            cancel(mc);
            return Result.NO_ARROWS;
        }
        if (bow.willBreakNextUse() || player.getItemCooldownManager().isCoolingDown(bow)) {
            cancel(mc);
            return Result.WAITING;
        }

        Vec3d origin = player.getEyePos().add(0, -0.1, 0);
        Vec3d point = target.getBoundingBox().getCenter();
        if (conservative && !manual) charge = 20;
        Shot shot = null;
        for (int ticks = charge; ticks <= (adaptive ? 20 : charge); ticks++) {
            shot = solve(origin, point, ticks);
            if (shot != null) break;
        }
        if ((ownsUse || manual) && shot != null && player.getItemUseTime() > shot.charge()) {
            shot = solve(origin, point, Math.min(20, player.getItemUseTime()));
        }
        if (shot == null || !pathClear(mc, target, origin, shot)) {
            cancel(mc);
            return Result.WAITING;
        }
        Rotation aim = shot.aim();
        if (autoLock) ServerRotation.request(new Rotation(
            MathHelper.stepUnwrappedAngleTowards(player.getYaw(), aim.yaw(), conservative ? 12 : 18),
            MathHelper.stepTowards(player.getPitch(), aim.pitch(), conservative ? 8 : 12)));
        Rotation sent = ServerRotation.sent();
        boolean aligned = sent != null && Math.abs(MathHelper.wrapDegrees(sent.yaw() - aim.yaw())) < 1.2
            && Math.abs(sent.pitch() - aim.pitch()) < 1.2;
        alignedTicks = aligned ? alignedTicks + 1 : 0;

        if (ownsUse) {
            // Always measure the native item use duration, never synthesize charge packets.
            if (player.getItemUseTime() >= shot.charge() && alignedTicks >= (conservative ? 2 : 1)) {
                mc.options.useKey.setPressed(false);
                mc.interactionManager.stopUsingItem(player);
                ownsUse = false;
                drawn = ItemStack.EMPTY;
                nextUseAge = player.age + Math.max(conservative ? 5 : 1, delay);
                return Result.SHOT;
            }
            mc.options.useKey.setPressed(true);
            return Result.WAITING;
        }
        if (!autoShoot || manual || CombatBridge.isPhysicallyDown(mc, mc.options.useKey)) return Result.WAITING;
        if (!aligned) return Result.WAITING;
        if (previousSlot < 0) previousSlot = player.getInventory().getSelectedSlot();
        select(mc, bowSlot);
        if (mc.interactionManager.interactItem(player, Hand.MAIN_HAND).isAccepted()
            && player.isUsingItem() && player.getActiveItem().isOf(Items.BOW)) {
            ownsUse = true;
            slot = bowSlot;
            targetId = target.getId();
            drawn = player.getActiveItem();
            mc.options.useKey.setPressed(true);
        } else nextUseAge = player.age + 5;
        return Result.WAITING;
    }

    void cancel(MinecraftClient mc) {
        if (ownsUse) mc.options.useKey.setPressed(CombatBridge.isPhysicallyDown(mc, mc.options.useKey));
        if (owner == mc.player && ownsUse) {
            if (ownsUse(owner)) {
                // A release would fire the arrow. A normal switch to a different item lets
                // the server discard the draw instead; leave that slot selected while idle.
                if (owner.getInventory().getSelectedSlot() == slot && mc.interactionManager != null) {
                    int safe = previousSlot;
                    if (safe < 0 || owner.getInventory().getStack(safe).isOf(Items.BOW)) {
                        safe = -1;
                        for (int i = 0; i < 9; i++) {
                            if (!owner.getInventory().getStack(i).isOf(Items.BOW)) { safe = i; break; }
                        }
                    }
                    if (safe >= 0) select(mc, safe);
                }
                owner.clearActiveItem();
            }
            nextUseAge = owner.age + 3;
        }
        ownsUse = false;
        drawn = ItemStack.EMPTY;
        slot = previousSlot = targetId = -1;
        alignedTicks = 0;
    }

    void reset(MinecraftClient mc) {
        cancel(mc);
        owner = null;
        world = null;
        nextUseAge = 0;
    }

    private static void select(MinecraftClient mc, int slot) {
        if (mc.player.getInventory().getSelectedSlot() == slot) return;
        mc.player.getInventory().setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).maro$syncSelectedSlot();
    }

    int findBow(ClientPlayerEntity player, int requestedSlot) {
        var inventory = player.getInventory();
        if (requestedSlot > 0) return inventory.getStack(requestedSlot - 1).isOf(Items.BOW) ? requestedSlot - 1 : -1;
        int held = inventory.getSelectedSlot();
        if (inventory.getStack(held).isOf(Items.BOW) && !inventory.getStack(held).willBreakNextUse()) return held;
        int best = -1, remaining = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Items.BOW) && !stack.willBreakNextUse()
                && stack.getMaxDamage() - stack.getDamage() > remaining) {
                best = i;
                remaining = stack.getMaxDamage() - stack.getDamage();
            }
        }
        return best;
    }

    private record Shot(Rotation aim, int charge, double flight, double speed) { }

    /** Low arc with native arrow drag (0.99) and gravity (0.05 blocks/tick). */
    private static Shot solve(Vec3d origin, Vec3d point, int charge) {
        Vec3d delta = point.subtract(origin);
        double horizontal = Math.hypot(delta.x, delta.z), speed = BowItem.getPullProgress(charge) * 3;
        if (horizontal < .05 || speed < .1) return null;
        double low = -80, last = heightAt(horizontal, speed, low) - delta.y;
        for (double high = -77; high <= 70; high += 3) {
            double height = heightAt(horizontal, speed, high) - delta.y;
            if (Double.isFinite(height) && height >= 0 && last <= 0) {
                for (int i = 0; i < 16; i++) {
                    double mid = (low + high) / 2;
                    if (heightAt(horizontal, speed, mid) < delta.y) low = mid; else high = mid;
                }
                double angle = (low + high) / 2;
                double flight = flightAt(horizontal, speed, angle);
                return new Shot(new Rotation((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)), (float) -angle), charge, flight, speed);
            }
            low = high;
            last = height;
        }
        return null;
    }

    private static double flightAt(double distance, double speed, double angle) {
        double factor = 1 - distance * .01 / (speed * Math.cos(Math.toRadians(angle)));
        return factor > 0 ? Math.log(factor) / Math.log(.99) : Double.POSITIVE_INFINITY;
    }

    private static double heightAt(double distance, double speed, double angle) {
        double time = flightAt(distance, speed, angle);
        if (!Double.isFinite(time) || time > 60) return Double.NEGATIVE_INFINITY;
        double sum = (1 - Math.pow(.99, time)) / .01;
        return speed * Math.sin(Math.toRadians(angle)) * sum - 5 * (time - sum);
    }

    private static boolean pathClear(MinecraftClient mc, AbstractClientPlayerEntity target, Vec3d origin, Shot shot) {
        Vec3d velocity = Vec3d.fromPolar(shot.aim().pitch(), shot.aim().yaw()).multiply(shot.speed());
        Vec3d from = origin;
        for (double time = 0; time < shot.flight(); time++) {
            Vec3d to = from.add(velocity.multiply(Math.min(1, shot.flight() - time)));
            if (mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player)).getType() != HitResult.Type.MISS) return false;
            for (var other : mc.world.getPlayers()) {
                if (other != mc.player && other != target && other.isAlive() && !other.isSpectator()
                    && other.getBoundingBox().expand(.3).raycast(from, to).isPresent()) return false;
            }
            from = to;
            velocity = velocity.multiply(.99).add(0, -.05, 0);
        }
        return true;
    }
}
