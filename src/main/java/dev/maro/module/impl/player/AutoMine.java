package dev.maro.module.impl.player;

import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.mixin.ClientPlayerInteractionManagerAccessor;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.util.ColorUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strip mining on its own: digs a straight tunnel the way you were facing when you turned it on,
 * walking forward as it goes. It mines the way a player does: it turns to the block first and only
 * digs, or places, once the crosshair is on it, using the face the crosshair hits - so the server
 * sees the same thing it would from a player, and blocks do not come back as ghosts. Picks the right tool, places torches, fills holes in the floor, and
 * stops by itself for lava, a full inventory, low health, a tool about to break or the distance
 * you set. Ores in the walls are announced, and can be mined on the way past.
 */
public class AutoMine extends Module {
    private final ModeSetting size = add(new ModeSetting("Tunnel", "Width x height of the tunnel", "1x2", "1x2", "2x2", "3x3", "1x3", "2x3"));
    private final BooleanSetting sprint = add(new BooleanSetting("Sprint", "Sprint between blocks", false));
    private final BooleanSetting center = add(new BooleanSetting("Stay Centered", "Strafe back to the middle of the tunnel if you drift", true));
    private final BooleanSetting smooth = add(new BooleanSetting("Smooth Look", "Turn towards each block instead of snapping", true));
    private final BooleanSetting autoTool = add(new BooleanSetting("Auto Tool", "Pick the fastest hotbar tool for each block", true));
    private final NumberSetting saveTool = add(new NumberSetting("Save Tools", "Never use a tool below this much durability, and stop if none is left", 3, 0, 25, 1)
            .suffix("%"));

    private final BooleanSetting torches = add(new BooleanSetting("Torches", "Put a torch on the wall every few blocks, from your hotbar", true));
    private final NumberSetting torchGap = add(new NumberSetting("Torch Gap", "Blocks between torches", 8, 3, 16, 1).visible(torches::get));
    private final BooleanSetting fillHoles = add(new BooleanSetting("Fill Holes", "Place cobblestone or similar over gaps in the floor, otherwise stop", true));

    private final BooleanSetting oreAlerts = add(new BooleanSetting("Ore Alerts", "A notification for every ore the tunnel uncovers", true));
    private final BooleanSetting mineOres = add(new BooleanSetting("Mine Ores", "Also mine ores showing in the walls, floor and ceiling as you pass", true));
    private final BooleanSetting stopOnDiamond = add(new BooleanSetting("Stop On Diamonds", "Stop when diamonds or ancient debris show up", false));

    private final BooleanSetting stopLava = add(new BooleanSetting("Stop At Lava", "Stop before opening into lava", true));
    private final BooleanSetting stopWater = add(new BooleanSetting("Stop At Water", "Stop before opening into water", false));
    private final BooleanSetting stopFull = add(new BooleanSetting("Stop When Full", "Stop when your inventory has no empty slot", true));
    private final NumberSetting minHealth = add(new NumberSetting("Min Health", "Stop when health drops to this, in hearts. 0 never stops", 4, 0, 10, 0.5));
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distance", "Stop after this many blocks. 0 keeps going", 0, 0, 1000, 1));

    private final BooleanSetting statusBar = add(new BooleanSetting("Status Bar", "A bar at the top of the screen with what it is doing and how far it got", true));

    /** How far the view turns each tick with Smooth Look, in degrees. */
    private static final float TURN = 35;
    private static final double REACH = 4.4;

    private Direction facing;
    private BlockPos origin;
    private int originalSlot = -1;
    private BlockPos target;
    private int mined;
    private int ores;
    private int lastTorch;
    private long startedAt;
    private String status = "";
    private final Set<BlockPos> announced = new HashSet<>();
    /** Ores the crosshair could not get onto from where you were, left alone. */
    private final Set<BlockPos> skipped = new HashSet<>();
    /** Ticks spent turning towards the current block or placement without reaching it. */
    private int aimTicks;
    /** Where to look for the ore chosen this tick: the middle of the face it shows the tunnel. */
    private Vec3d oreFace;
    private Vec3d lastPos;
    private int stuckTicks;

    public AutoMine() {
        super("Auto Mine", "Digs a straight strip-mining tunnel for you", Category.PLAYER);
    }

    // ---- lifecycle ----------------------------------------------------------------------

    @Override
    protected void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        ClientPlayerEntity player = mc.player;
        facing = player.getHorizontalFacing();
        origin = player.getBlockPos();
        originalSlot = player.getInventory().getSelectedSlot();
        target = null;
        mined = 0;
        ores = 0;
        lastTorch = 0;
        startedAt = System.currentTimeMillis();
        announced.clear();
        skipped.clear();
        aimTicks = 0;
        lastPos = null;
        stuckTicks = 0;
        status = "Starting";
    }

    @Override
    protected void onDisable() {
        releaseKeys();
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        if (mc.player != null && originalSlot >= 0 && originalSlot < 9) select(originalSlot);
        originalSlot = -1;
        target = null;
    }

    private void stop(String reason) {
        Notifications.push("Auto Mine stopped", reason, Notifications.Type.WARNING, 4000);
        status = reason;
        setEnabled(false);
    }

    private void releaseKeys() {
        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    // ---- the tunnel ---------------------------------------------------------------------

    private int width() {
        return size.get().charAt(0) - '0';
    }

    private int height() {
        return size.get().charAt(2) - '0';
    }

    /** Columns across the tunnel, as steps to the right of the centre line. */
    private int firstColumn() {
        return -((width() - 1) / 2);
    }

    /** How far along the tunnel a position is, in whole blocks from where it started. */
    private int along(BlockPos pos) {
        return (pos.getX() - origin.getX()) * facing.getOffsetX() + (pos.getZ() - origin.getZ()) * facing.getOffsetZ();
    }

    /** One block of the tunnel: this far along, this many columns right of centre, this many rows up. */
    private BlockPos cell(int step, int column, int row) {
        Direction right = facing.rotateYClockwise();
        return origin.offset(facing, step).offset(right, column).up(row);
    }

    private List<BlockPos> slice(int step) {
        List<BlockPos> cells = new ArrayList<>();
        // Top down, so gravel and sand drop into space already being dug.
        for (int row = height() - 1; row >= 0; row--) {
            for (int column = firstColumn(); column < firstColumn() + width(); column++) cells.add(cell(step, column, row));
        }
        return cells;
    }

    private boolean solid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private static boolean ore(BlockState state) {
        return state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.IRON_ORES) || state.isIn(BlockTags.COPPER_ORES)
                || state.isIn(BlockTags.GOLD_ORES) || state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.LAPIS_ORES)
                || state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.EMERALD_ORES)
                || state.isOf(Blocks.ANCIENT_DEBRIS) || state.isOf(Blocks.NETHER_QUARTZ_ORE) || state.isOf(Blocks.NETHER_GOLD_ORE);
    }

    private static boolean precious(BlockState state) {
        return state.isIn(BlockTags.DIAMOND_ORES) || state.isOf(Blocks.ANCIENT_DEBRIS);
    }

    // ---- every tick ---------------------------------------------------------------------

    @Override
    public void onTick() {
        if (!inGame() || mc.interactionManager == null || facing == null) return;
        ClientPlayerEntity player = mc.player;

        if (mc.currentScreen != null) {
            releaseKeys();
            status = "Paused";
            return;
        }
        if (player.getAbilities().flying || player.isSpectator()) {
            stop("Not walking");
            return;
        }

        int here = along(player.getBlockPos());
        if (maxDistance.getInt() > 0 && here >= maxDistance.getInt()) {
            stop("Reached " + maxDistance.getInt() + " blocks");
            return;
        }
        if (minHealth.get() > 0 && player.getHealth() <= minHealth.get() * 2) {
            stop("Low health");
            return;
        }
        if (stopFull.get() && player.getInventory().getEmptySlot() < 0) {
            stop("Inventory full");
            return;
        }

        countMined();

        List<BlockPos> next = slice(here + 1);
        List<BlockPos> after = slice(here + 2);

        String danger = danger(next, after);
        if (danger != null) {
            stop(danger);
            return;
        }

        scanOres(here);
        if (!isEnabled()) return;

        // What to dig: an ore showing beside the tunnel, then the next slice of the tunnel itself.
        BlockPos dig = null;
        oreFace = null;
        if (mineOres.get()) dig = exposedOre(here);
        if (dig == null) for (BlockPos pos : slice(here)) if (solid(pos)) { dig = pos; break; }
        if (dig == null) for (BlockPos pos : next) if (solid(pos)) { dig = pos; break; }

        if (dig != null) {
            BlockState state = mc.world.getBlockState(dig);
            if (state.getHardness(mc.world, dig) < 0) {
                stop("Unbreakable block ahead");
                return;
            }
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            stuckTicks = 0;
            look(oreFace != null && dig.equals(exposedOreAt) ? oreFace : Vec3d.ofCenter(dig));

            // Dig only what the crosshair is already on. It was worked out from the view the
            // server was sent last tick, so the server agrees which block and which face it is.
            BlockHitResult aimed = crosshairBlock();
            if (aimed == null || !aimed.getBlockPos().equals(dig)) {
                BlockPos inWay = aimed == null ? null : aimed.getBlockPos();
                if (inWay != null && solid(inWay) && tunnel(inWay, here)) {
                    dig = inWay;
                    state = mc.world.getBlockState(dig);
                } else {
                    if (++aimTicks > 20) {
                        // An ore that cannot be seen from here: leave it.
                        skipped.add(dig.toImmutable());
                        aimTicks = 0;
                    }
                    status = "Aiming";
                    return;
                }
            }
            aimTicks = 0;
            if (autoTool.get() && !pickTool(state, dig)) return;
            target = dig;
            mc.interactionManager.updateBlockBreakingProgress(dig, aimed.getSide());
            player.swingHand(Hand.MAIN_HAND);
            status = "Mining " + state.getBlock().getName().getString();
            return;
        }

        // The way is clear: check the floor, light the tunnel, then walk on.
        BlockPos floor = cell(here + 1, 0, -1);
        if (!solid(floor)) {
            releaseKeys();
            Boolean filled = fillHoles.get() ? fill(floor) : Boolean.FALSE;
            if (filled == null) {
                // Still turning to the spot.
                if (++aimTicks > 40) stop("Could not fill the hole in the floor");
                return;
            }
            aimTicks = 0;
            if (!filled) stop("Hole in the floor");
            return;
        }

        if (torches.get() && here - lastTorch >= torchGap.getInt()) {
            Boolean lit = placeTorch(here);
            if (lit == null && ++aimTicks <= 20) {
                releaseKeys();
                return;
            }
            // Placed, no torch or wall to use, or it could not be reached: move on either way.
            aimTicks = 0;
            lastTorch = here;
        }

        walk(here);
    }

    private void walk(int here) {
        ClientPlayerEntity player = mc.player;
        float yaw = switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            default -> -90f;
        };
        turnTo(yaw, 18f);
        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(sprint.get());

        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        if (center.get()) {
            // Distance right of the centre line of the tunnel's middle column.
            Direction right = facing.rotateYClockwise();
            double middle = (firstColumn() + (width() - 1) / 2.0);
            double cx = origin.getX() + 0.5 + right.getOffsetX() * middle;
            double cz = origin.getZ() + 0.5 + right.getOffsetZ() * middle;
            double offRight = (player.getX() - cx) * right.getOffsetX() + (player.getZ() - cz) * right.getOffsetZ();
            if (offRight > 0.18) mc.options.leftKey.setPressed(true);
            else if (offRight < -0.18) mc.options.rightKey.setPressed(true);
        }

        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        if (lastPos != null && pos.squaredDistanceTo(lastPos) < 1.0e-4) {
            if (++stuckTicks > 60) {
                stop("Stuck");
                return;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = pos;
        status = "Walking";
        target = null;
    }

    /** Counts a block once the one being dug has gone. */
    private void countMined() {
        if (target != null && !solid(target)) {
            mined++;
            target = null;
        }
    }

    /** Lava or water about to be opened into, or null if it is safe. */
    private String danger(List<BlockPos> next, List<BlockPos> after) {
        List<BlockPos> check = new ArrayList<>(next);
        check.addAll(after);
        for (BlockPos pos : check) {
            for (BlockPos near : new BlockPos[]{pos, pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()}) {
                var fluid = mc.world.getFluidState(near);
                if (fluid.isEmpty()) continue;
                if (stopLava.get() && fluid.isIn(FluidTags.LAVA)) return "Lava ahead";
                if (stopWater.get() && fluid.isIn(FluidTags.WATER)) return "Water ahead";
            }
        }
        return null;
    }

    /** Announces ores around the last few slices, once each. */
    private void scanOres(int here) {
        for (int step = here - 1; step <= here + 1; step++) {
            for (BlockPos cell : slice(step)) {
                for (Direction side : Direction.values()) {
                    BlockPos pos = cell.offset(side);
                    BlockState state = mc.world.getBlockState(pos);
                    if (!ore(state) || !announced.add(pos.toImmutable())) continue;
                    ores++;
                    if (oreAlerts.get()) {
                        Notifications.push("Ore found", state.getBlock().getName().getString(),
                                precious(state) ? Notifications.Type.SUCCESS : Notifications.Type.INFO);
                    }
                    if (stopOnDiamond.get() && precious(state)) {
                        stop(state.getBlock().getName().getString() + " found");
                        return;
                    }
                }
            }
        }
    }

    /** An ore touching the tunnel near you, close enough to dig, with no lava behind it. */
    private BlockPos exposedOreAt;

    private BlockPos exposedOre(int here) {
        Vec3d eyes = mc.player.getEyePos();
        for (int step = here - 1; step <= here + 1; step++) {
            for (BlockPos cell : slice(step)) {
                for (Direction side : Direction.values()) {
                    BlockPos pos = cell.offset(side);
                    BlockState state = mc.world.getBlockState(pos);
                    if (!ore(state) || state.getHardness(mc.world, pos) < 0 || skipped.contains(pos)) continue;
                    if (eyes.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH * REACH) continue;
                    boolean lava = false;
                    for (Direction around : Direction.values()) {
                        if (mc.world.getFluidState(pos.offset(around)).isIn(FluidTags.LAVA)) lava = true;
                    }
                    if (lava) continue;
                    // Aim at the face it shows the tunnel: its middle is nearer than the
                    // block's, and nothing but tunnel lies in the way.
                    oreFace = Vec3d.ofCenter(pos).add(-side.getOffsetX() * 0.5, -side.getOffsetY() * 0.5, -side.getOffsetZ() * 0.5);
                    exposedOreAt = pos.toImmutable();
                    return pos;
                }
            }
        }
        return null;
    }

    // ---- tools, blocks and torches ------------------------------------------------------

    /** Selects the fastest safe hotbar tool. False (and stops) if every usable tool is too worn. */
    private boolean pickTool(BlockState state, BlockPos pos) {
        PlayerInventory inventory = mc.player.getInventory();
        RegistryEntry<Enchantment> efficiency = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        int best = -1;
        double bestSpeed = -1;
        boolean bestHarvests = false;
        boolean anyWorn = false;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            boolean harvests = !state.isToolRequired() || stack.isSuitableFor(state);
            double speed = stack.getMiningSpeedMultiplier(state);
            if (speed > 1) {
                int level = EnchantmentHelper.getLevel(efficiency, stack);
                if (level > 0) speed += level * level + 1;
            }
            if (worn(stack)) {
                if (speed > 1) anyWorn = true;
                continue;
            }
            if (best < 0 || (harvests && !bestHarvests) || (harvests == bestHarvests && speed > bestSpeed)) {
                best = slot;
                bestSpeed = speed;
                bestHarvests = harvests;
            }
        }
        if (best < 0 || (state.isToolRequired() && !bestHarvests)) {
            if (anyWorn) {
                stop("Tools almost broken");
                return false;
            }
            if (state.isToolRequired()) {
                stop("No tool for " + state.getBlock().getName().getString());
                return false;
            }
            return true;
        }
        select(best);
        return true;
    }

    private boolean worn(ItemStack stack) {
        if (!stack.isDamageable() || stack.getMaxDamage() <= 0) return false;
        int left = stack.getMaxDamage() - stack.getDamage();
        return left <= 1 || left * 100.0 / stack.getMaxDamage() < saveTool.get();
    }

    private void select(int slot) {
        PlayerInventory inventory = mc.player.getInventory();
        if (inventory.getSelectedSlot() == slot) return;
        inventory.setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).maro$syncSelectedSlot();
    }

    private int hotbarSlot(java.util.function.Predicate<ItemStack> wanted) {
        for (int slot = 0; slot < 9; slot++) if (wanted.test(mc.player.getInventory().getStack(slot))) return slot;
        return -1;
    }

    private static boolean filler(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        Block block = item.getBlock();
        return block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.STONE || block == Blocks.DEEPSLATE
                || block == Blocks.NETHERRACK || block == Blocks.DIRT || block == Blocks.ANDESITE || block == Blocks.DIORITE
                || block == Blocks.GRANITE || block == Blocks.TUFF || block == Blocks.BLACKSTONE || block == Blocks.STONE_BRICKS;
    }

    /**
     * Patches a hole in the floor with a block from the hotbar, placed on the top of the block
     * under the hole, or else on the near face of the block past it - the faces a player standing
     * here can see. True when placed, false if it cannot be done, null while still turning.
     */
    private Boolean fill(BlockPos hole) {
        int slot = hotbarSlot(AutoMine::filler);
        if (slot < 0) return false;
        if (solid(hole.down())) return place(slot, hole.down(), Direction.UP);
        BlockPos beyond = hole.offset(facing);
        if (solid(beyond)) return place(slot, beyond, facing.getOpposite());
        return false;
    }

    /** A torch on the left wall, at head height if the tunnel is tall enough. Same answers as {@link #fill}. */
    private Boolean placeTorch(int here) {
        Item[] lights = {Items.TORCH, Items.SOUL_TORCH};
        int slot = hotbarSlot(stack -> {
            for (Item light : lights) if (stack.isOf(light)) return true;
            return false;
        });
        if (slot < 0) return false;
        Direction left = facing.rotateYCounterclockwise();
        BlockPos wall = cell(here, firstColumn(), Math.min(1, height() - 1)).offset(left);
        if (!mc.world.getBlockState(wall).isSideSolidFullSquare(mc.world, wall, left.getOpposite())) return false;
        if (!mc.world.getBlockState(wall.offset(left.getOpposite())).isAir()) return false;
        return place(slot, wall, left.getOpposite());
    }

    /**
     * Turns to one face of a block and, once the crosshair is on that face, places the item from
     * this hotbar slot against it, exactly where the crosshair points. Null while still turning.
     */
    private Boolean place(int slot, BlockPos against, Direction side) {
        Vec3d face = Vec3d.ofCenter(against).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        look(face);
        BlockHitResult aimed = crosshairBlock();
        if (aimed == null || !aimed.getBlockPos().equals(against) || aimed.getSide() != side) {
            status = "Aiming";
            return null;
        }
        int previous = mc.player.getInventory().getSelectedSlot();
        select(slot);
        boolean placed = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, aimed).isAccepted();
        if (placed) mc.player.swingHand(Hand.MAIN_HAND);
        select(previous);
        return placed;
    }

    /** The block under the crosshair, or null if it is not on one. */
    private static BlockHitResult crosshairBlock() {
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) return hit;
        return null;
    }

    /** Whether a block is part of the tunnel around you: this slice or the next. */
    private boolean tunnel(BlockPos pos, int here) {
        return slice(here).contains(pos) || slice(here + 1).contains(pos);
    }

    // ---- looking ------------------------------------------------------------------------

    private void look(Vec3d point) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = point.x - eyes.x;
        double dy = point.y - eyes.y;
        double dz = point.z - eyes.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        turnTo(yaw, pitch);
    }

    private void turnTo(float yaw, float pitch) {
        ClientPlayerEntity player = mc.player;
        float dyaw = MathHelper.wrapDegrees(yaw - player.getYaw());
        float dpitch = pitch - player.getPitch();
        if (smooth.get()) {
            dyaw = MathHelper.clamp(dyaw, -TURN, TURN);
            dpitch = MathHelper.clamp(dpitch, -TURN, TURN);
        }
        player.setYaw(player.getYaw() + dyaw);
        player.setPitch(MathHelper.clamp(player.getPitch() + dpitch, -90f, 90f));
    }

    // ---- status bar ---------------------------------------------------------------------

    /** Blocks mined and ores found since it was turned on, for the in-game test. */
    public int minedCount() {
        return mined;
    }

    public int oreCount() {
        return ores;
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!statusBar.get() || !inGame() || mc.options.hudHidden || facing == null) return;
        int here = Math.max(0, along(mc.player.getBlockPos()));
        double minutes = Math.max(1 / 60.0, (System.currentTimeMillis() - startedAt) / 60000.0);
        String[] parts = {
                status,
                here + " blocks",
                mined + " mined",
                ores + " ores",
                Math.round(mined / minutes) + "/min"};

        float scale = 0.8f;
        float gap = 12;
        float width = 18 + Fonts.width("Auto Mine", true, scale) + gap + 10;
        for (String part : parts) width += Fonts.width(part, true, scale);
        width += gap * (parts.length - 1);
        float height = 16;
        float left = mc.getWindow().getScaledWidth() / 2f - width / 2f;
        float top = 6;

        Render2D.shadow(ctx, left, top + 1, width, height, 8, 7, 0x66000000);
        Render2D.roundRect(ctx, left, top, width, height, 8, 0xE00B0D12);
        Render2D.roundOutline(ctx, left, top, width, height, 8, Render2D.px(), 0x1CFFFFFF);

        // A pulsing dot while it works.
        float pulse = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 220.0));
        Render2D.circle(ctx, left + 11, top + height / 2f, 3, ColorUtil.withAlpha(Theme.accent(), Math.round(255 * pulse)));
        Fonts.drawV(ctx, "Auto Mine", left + 18, top + height / 2f, Theme.TEXT, true, scale);

        float x = left + 18 + Fonts.width("Auto Mine", true, scale) + gap;
        for (int i = 0; i < parts.length; i++) {
            int color = i == 0 ? Theme.accent() : ColorUtil.withAlpha(Theme.TEXT, 170);
            Fonts.drawV(ctx, parts[i], x, top + height / 2f, color, true, scale);
            x += Fonts.width(parts[i], true, scale) + gap;
        }
    }
}
