/*
 * Adapted from the Spawner Protect addon by Larpbase (package larp.spawnerprotect),
 * which is marked All-Rights-Reserved. The detection logic, the state machine and
 * the stash routine below are that author's work, carried over rather than
 * rewritten. What changed here is the port from Meteor 26.2 to 1.21.11:
 * ContainerInput became ClickType, the package and category moved, and the mixin
 * invoker was renamed to this addon's prefix.
 */
package dev.maro.nathan.modules;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.nathan.mixin.ContainerScreenAccessor;
import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.events.packets.PacketEvent;
import dev.maro.runtime.events.render.Render3DEvent;
import dev.maro.runtime.events.world.BlockUpdateEvent;
import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.pathing.PathManagers;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.ColorSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.friends.Friends;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.Utils;
import dev.maro.runtime.utils.player.FindItemResult;
import dev.maro.runtime.utils.player.InvUtils;
import dev.maro.runtime.utils.player.PlayerUtils;
import dev.maro.runtime.utils.player.Rotations;
import dev.maro.runtime.utils.world.BlockUtils;
import dev.maro.runtime.utils.render.color.SettingColor;
import dev.maro.runtime.event.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.chunk.WorldChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Watches for somebody digging toward you, and if they get close it empties your spawner room
 * into an ender chest and logs you out.
 *
 * Detection does not rely on seeing the player, because a server that hides players still has to
 * tell you what happens to the world:
 *   - block-breaking progress carries the breaker's entity id and the exact block, and the server
 *     sends it to everyone within 32 blocks of that block. Nothing but somebody holding left
 *     click produces it.
 *   - solid block turning into air anywhere in view distance is a block update, which reaches
 *     every client tracking the chunk at any depth. Noisy on its own, so several distinct
 *     positions inside the window are required before it counts.
 *   - the break particle/sound level event covers the 64 block range in between.
 *
 * Everything the module then does goes through the same code path your hands would: it turns the
 * head with the mouse call, holds the attack key to mine, taps the use key to place and open, and
 * clicks container slots. No place packets, no look packets, no instant rotations.
 */
public class SpawnerProtect extends Module {
    private final SettingGroup sgDetect = settings.getDefaultGroup();
    private final SettingGroup sgResponse = settings.createGroup("Response");
    private final SettingGroup sgAim = settings.createGroup("Aim");
    private final SettingGroup sgAlarm = settings.createGroup("Alarm");
    private final SettingGroup sgRender = settings.createGroup("Highlight");

    // Detection ---------------------------------------------------------------------------------

    private final Setting<Double> triggerRadius = sgDetect.add(new DoubleSetting.Builder()
        .name("trigger-radius")
        .description("How far away somebody can be digging and still set this off.")
        .defaultValue(50)
        .min(4)
        .sliderRange(8, 128)
        .build()
    );

    private final Setting<Integer> chunkRadius = sgDetect.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("Digging this many chunks away or closer always counts, whatever the radius says.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Boolean> useMiningAnimation = sgDetect.add(new BoolSetting.Builder()
        .name("mining-animation")
        .description("Block-breaking progress. Carries the breaker's id and the block, reaches 32 blocks, and only a person holding left click makes it. Fires on the first packet.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBlockBreaks = sgDetect.add(new BoolSetting.Builder()
        .name("block-breaks")
        .description("Solid blocks turning into air. This is the long range half - it works at full view distance and through any amount of rock.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> breakThreshold = sgDetect.add(new IntSetting.Builder()
        .name("break-threshold")
        .description("Distinct blocks that have to disappear inside the window before block breaks alone count as a person. Stops gravel and lava from arming you.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .visible(useBlockBreaks::get)
        .build()
    );

    private final Setting<Integer> breakWindow = sgDetect.add(new IntSetting.Builder()
        .name("break-window")
        .description("Seconds those breaks have to happen within.")
        .defaultValue(12)
        .min(2)
        .sliderRange(4, 60)
        .visible(useBlockBreaks::get)
        .build()
    );

    private final Setting<Double> selfRadius = sgDetect.add(new DoubleSetting.Builder()
        .name("ignore-radius")
        .description("Ignore anything happening this close to you, so your own mining never arms it.")
        .defaultValue(6)
        .min(0)
        .sliderRange(0, 24)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgDetect.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not react when the digger is a Maro friend. Only works when the server actually sends their entity.")
        .defaultValue(true)
        .build()
    );

    // Response ----------------------------------------------------------------------------------

    private final Setting<Boolean> mineSpawners = sgResponse.add(new BoolSetting.Builder()
        .name("mine-spawners")
        .description("Mine the spawners before leaving.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> scanRadius = sgResponse.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("How far around you to look for spawners.")
        .defaultValue(12)
        .min(1)
        .sliderRange(4, 32)
        .visible(mineSpawners::get)
        .build()
    );

    private final Setting<Boolean> requireSilk = sgResponse.add(new BoolSetting.Builder()
        .name("require-silk-touch")
        .description("Refuse to mine without a silk touch pickaxe. Leave this on - a spawner broken with anything else drops nothing at all.")
        .defaultValue(true)
        .visible(mineSpawners::get)
        .build()
    );

    private final Setting<Boolean> sneak = sgResponse.add(new BoolSetting.Builder()
        .name("sneak")
        .description("Hold shift while mining.")
        .defaultValue(true)
        .visible(mineSpawners::get)
        .build()
    );

    private final Setting<Boolean> walkToSpawners = sgResponse.add(new BoolSetting.Builder()
        .name("walk-to-out-of-reach")
        .description("Use Baritone, if you have it, to walk to a spawner or an ender chest you cannot reach from where you stand. Without a path manager, out of reach spawners are skipped and named in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stackConfirm = sgResponse.add(new IntSetting.Builder()
        .name("stack-confirm")
        .description("Ticks a spawner has to stay gone before the module accepts it is really gone. Breaking is predicted on your client, so the block reads as air for a moment even when a stacked spawner still has more in it - this is the wait that stops it walking away after the first one. Raise it if your ping is high.")
        .defaultValue(12)
        .min(2)
        .sliderRange(4, 40)
        .visible(mineSpawners::get)
        .build()
    );

    private final Setting<Integer> stallSeconds = sgResponse.add(new IntSetting.Builder()
        .name("stall-timeout")
        .description("Give up on one spawner after this many seconds with nothing coming off it. Every break resets the clock, so a tall stack is never cut short.")
        .defaultValue(25)
        .min(3)
        .sliderRange(5, 120)
        .visible(mineSpawners::get)
        .build()
    );

    public enum Deposit {
        Everything,
        SpawnersOnly
    }

    private final Setting<Deposit> deposit = sgResponse.add(new EnumSetting.Builder<Deposit>()
        .name("deposit")
        .description("What goes into the ender chest. Everything means the main inventory and the hotbar, keeping back a pickaxe since one is needed to pick the chest up again. Worn armour and the offhand are not part of a chest screen, so they stay on you either way.")
        .defaultValue(Deposit.Everything)
        .build()
    );

    private final Setting<Integer> chestSearchRadius = sgResponse.add(new IntSetting.Builder()
        .name("chest-search-radius")
        .description("How far to look for an ender chest that is already placed. One that is already there is always used instead of putting a new one down.")
        .defaultValue(16)
        .min(1)
        .sliderRange(4, 48)
        .build()
    );

    private final Setting<Boolean> placeEnderChest = sgResponse.add(new BoolSetting.Builder()
        .name("place-ender-chest")
        .description("Only if there is no ender chest anywhere nearby: put one down from your inventory. Turn this off if you never want a chest placed, for instance when you are sat in a hole with no room for one.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> reclaimEnderChest = sgResponse.add(new BoolSetting.Builder()
        .name("take-chest-back")
        .description("Break the ender chest again after depositing, so you do not leave one sitting at your base. Needs the silk pickaxe.")
        .defaultValue(true)
        .visible(placeEnderChest::get)
        .build()
    );

    private final Setting<Boolean> leaveServer = sgResponse.add(new BoolSetting.Builder()
        .name("leave-server")
        .description("Disconnect once everything is in the chest.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> timeBudget = sgResponse.add(new IntSetting.Builder()
        .name("time-budget")
        .description("Seconds the whole thing is allowed to take. When it runs out you get logged out with whatever is done, because being offline is the point. Stacked spawners take a while, so this is generous by default.")
        .defaultValue(240)
        .min(5)
        .sliderRange(30, 600)
        .build()
    );

    private final Setting<Integer> clickDelay = sgResponse.add(new IntSetting.Builder()
        .name("click-delay")
        .description("Ticks between container clicks while emptying the inventory.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    // Aim ---------------------------------------------------------------------------------------

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Top turning speed in degrees per second. This is a speed cap, not a per tick step - the turn eases in and out on its own.")
        .defaultValue(320)
        .min(30)
        .sliderRange(60, 900)
        .build()
    );

    private final Setting<Double> aimAccel = sgAim.add(new DoubleSetting.Builder()
        .name("acceleration")
        .description("How hard the turn is allowed to speed up and slow down, degrees per second squared. Lower is lazier and more human, higher snaps more.")
        .defaultValue(1400)
        .min(100)
        .sliderRange(200, 6000)
        .build()
    );

    private final Setting<Double> aimTremor = sgAim.add(new DoubleSetting.Builder()
        .name("tremor")
        .description("Degrees of idle hand wobble around the point you are looking at. Zero is a machine holding perfectly still.")
        .defaultValue(0.09)
        .min(0)
        .sliderRange(0, 0.5)
        .build()
    );

    private final Setting<Double> aimTolerance = sgAim.add(new DoubleSetting.Builder()
        .name("tolerance")
        .description("How close the crosshair has to be before the module will click.")
        .defaultValue(2.5)
        .min(0.2)
        .sliderRange(0.5, 10)
        .build()
    );

    // Alarm -------------------------------------------------------------------------------------

    private final Setting<Boolean> beep = sgAlarm.add(new BoolSetting.Builder()
        .name("beep")
        .description("Beep when the digging gets close.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> beepRadius = sgAlarm.add(new DoubleSetting.Builder()
        .name("beep-radius")
        .description("How close the digging has to be to start the beeping.")
        .defaultValue(25)
        .min(1)
        .sliderRange(4, 64)
        .visible(beep::get)
        .build()
    );

    private final Setting<Double> beepSeconds = sgAlarm.add(new DoubleSetting.Builder()
        .name("beep-seconds")
        .description("How long the beeping lasts.")
        .defaultValue(4)
        .min(0.5)
        .sliderRange(1, 15)
        .visible(beep::get)
        .build()
    );

    private final Setting<Double> beepVolume = sgAlarm.add(new DoubleSetting.Builder()
        .name("beep-volume")
        .description("Volume of each beep, 0.5 being half.")
        .defaultValue(0.5)
        .min(0)
        .max(1)
        .sliderRange(0, 1)
        .visible(beep::get)
        .build()
    );

    private final Setting<Integer> beepInterval = sgAlarm.add(new IntSetting.Builder()
        .name("beep-interval")
        .description("Ticks between beeps. 4 is five a second.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 20)
        .visible(beep::get)
        .build()
    );

    public enum Beep {
        Pling,
        Bit,
        Bell,
        Button
    }

    private final Setting<Beep> beepSound = sgAlarm.add(new EnumSetting.Builder<Beep>()
        .name("beep-sound")
        .defaultValue(Beep.Pling)
        .visible(beep::get)
        .build()
    );

    // Highlight ---------------------------------------------------------------------------------

    private final Setting<Boolean> highlight = sgRender.add(new BoolSetting.Builder()
        .name("highlight-players")
        .description("Give the player a glow outline, drawn only on your client. Nothing is sent to the server.")
        .defaultValue(true)
        .build()
    );

    public enum HighlightMode {
        Diggers,
        AllPlayers
    }

    private final Setting<HighlightMode> highlightMode = sgRender.add(new EnumSetting.Builder<HighlightMode>()
        .name("highlight-who")
        .description("Diggers glows only whoever tripped the detection. All Players glows anybody inside the trigger radius.")
        .defaultValue(HighlightMode.Diggers)
        .visible(highlight::get)
        .build()
    );

    private final Setting<SettingColor> highlightColor = sgRender.add(new ColorSetting.Builder()
        .name("highlight-color")
        .defaultValue(new SettingColor(255, 40, 80))
        .visible(highlight::get)
        .build()
    );

    private final Setting<Integer> highlightSeconds = sgRender.add(new IntSetting.Builder()
        .name("highlight-seconds")
        .description("How long a player keeps glowing after they were last seen digging.")
        .defaultValue(60)
        .min(1)
        .sliderRange(5, 300)
        .visible(highlight::get)
        .build()
    );

    // Runtime -----------------------------------------------------------------------------------

    private enum State {
        Watching,
        Mining,
        FindingChest,
        OpeningChest,
        Depositing,
        ClosingChest,
        TakingChestBack,
        Leaving,
        Finished
    }

    /** Break-progress packets and level events arrive on the netty thread, so they queue. */
    private record Dig(BlockPos pos, int entityId) {
    }

    private final Queue<Dig> pending = new ConcurrentLinkedQueue<>();
    private final Map<Long, Long> recentBreaks = new HashMap<>();

    private final Aim aim = new Aim();

    private State state = State.Watching;
    private int stateTicks;
    private int totalTicks;

    private BlockPos threatPos;
    private int beepTicksLeft;
    private int beepCooldown;

    private final List<BlockPos> targets = new ArrayList<>();
    private BlockPos current;
    private int mineTicks;
    private int goneTicks;
    private boolean sawSpawner;
    private int rescans;
    private int useTimer;
    private int walkTicks;
    private double walkBestDistance;
    private int skipped;
    private int mined;

    private BlockPos chestPos;
    private boolean chestWasPlaced;
    private BlockPos chestSpot;
    private final Set<Long> unreachableChests = new HashSet<>();
    private int chestWalkTicks;
    private double chestWalkBest;
    private int depositCooldown;
    private int deposited;
    private int lastClickSlot = -1;
    private int lastClickCount;
    private int lastClickRetries;
    private ItemStack lastClickStack = ItemStack.EMPTY;
    private final Set<Integer> stuckSlots = new HashSet<>();

    private int pickSlot = -1;
    private boolean attackDown, useDown, shiftDown;
    private boolean warnedNoSilk;

    public SpawnerProtect() {
        super(NameeProtectAddon.CATEGORY, "spawner-protect",
            "Mines your spawners into an ender chest and logs out when somebody digs toward you.");
    }

    @Override
    public void onActivate() {
        reset();

        Highlight.enabled = highlight.get();
        Highlight.color = highlightColor.get().getPacked() & 0xFFFFFF;

        if (mc.player != null && mineSpawners.get() && !findSilkPick().found()) {
            alert("No silk touch pickaxe in your inventory. A spawner broken without one drops nothing%s.",
                requireSilk.get() ? ", so mining will be skipped" : "");
            warnedNoSilk = true;
        }
    }

    @Override
    public void onDeactivate() {
        releaseKeys();
        Highlight.enabled = false;
        Highlight.clear();
        reset();
    }

    private void reset() {
        pending.clear();
        recentBreaks.clear();
        targets.clear();
        unreachableChests.clear();
        aim.stop();

        state = State.Watching;
        stateTicks = 0;
        totalTicks = 0;
        threatPos = null;
        beepTicksLeft = 0;
        beepCooldown = 0;
        current = null;
        mineTicks = 0;
        goneTicks = 0;
        sawSpawner = false;
        rescans = 0;
        useTimer = 0;
        walkTicks = 0;
        walkBestDistance = Double.MAX_VALUE;
        skipped = 0;
        mined = 0;
        chestPos = null;
        chestSpot = null;
        chestWasPlaced = false;
        chestWalkTicks = 0;
        chestWalkBest = Double.MAX_VALUE;
        depositCooldown = 0;
        deposited = 0;
        lastClickSlot = -1;
        lastClickCount = 0;
        lastClickRetries = 0;
        lastClickStack = ItemStack.EMPTY;
        stuckSlots.clear();
        pickSlot = -1;
        warnedNoSilk = false;
    }

    @Override
    public String getInfoString() {
        return state == State.Watching ? null : state.toString();
    }

    // Detection ---------------------------------------------------------------------------------

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        // Netty thread. Take one reference and only queue - all the judging happens on the tick.
        var self = mc.player;
        if (self == null) return;

        if (useMiningAnimation.get() && event.packet instanceof BlockBreakingProgressS2CPacket p) {
            if (p.getEntityId() != self.getId()) pending.add(new Dig(p.getPos().toImmutable(), p.getEntityId()));
        } else if (useBlockBreaks.get() && event.packet instanceof WorldEventS2CPacket p) {
            if (p.getEventId() == WorldEvents.BLOCK_BROKEN) pending.add(new Dig(p.getPos().toImmutable(), -1));
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!useBlockBreaks.get() || mc.player == null) return;
        if (state != State.Watching) return;

        // Somebody mining is stone turning into air. A block appearing, a fluid moving or a
        // redstone component flipping is not, and this is where farms would otherwise get in.
        if (!event.newState.isAir() || event.oldState.isAir()) return;
        if (!event.oldState.blocksMovement()) return;

        noteBreak(event.pos.toImmutable(), -1);
    }

    private void noteBreak(BlockPos pos, int entityId) {
        if (mc.player == null) return;
        if (PlayerUtils.distanceTo(pos) <= selfRadius.get()) return;
        if (!inRange(pos)) return;

        long now = System.currentTimeMillis();
        long window = (long) breakWindow.get() * 1000L;
        recentBreaks.entrySet().removeIf(e -> now - e.getValue() > window);
        recentBreaks.put(pos.asLong(), now);

        if (recentBreaks.size() >= breakThreshold.get()) trip(pos, entityId, "digging");
    }

    private boolean inRange(BlockPos pos) {
        if (PlayerUtils.distanceTo(pos) <= triggerRadius.get()) return true;

        // Chunks are a column with no top or bottom, so this needs a vertical limit as well or
        // somebody strip mining at the surface sets off a base sat on bedrock.
        if (Math.abs(pos.getY() - mc.player.getBlockY()) > triggerRadius.get()) return false;

        ChunkPos a = new ChunkPos(pos);
        ChunkPos b = mc.player.getChunkPos();
        return a.getChebyshevDistance(b) <= chunkRadius.get();
    }

    private void trip(BlockPos pos, int entityId, String why) {
        Entity digger = entityId >= 0 && mc.world != null ? mc.world.getEntityById(entityId) : null;

        // Decided before anything else happens. Doing it later meant a friend still set the
        // alarm off and still got a glow outline.
        if (digger instanceof AbstractClientPlayerEntity p && ignoreFriends.get() && Friends.get().isFriend(p)) {
            return;
        }

        threatPos = pos;

        if (entityId >= 0 && highlight.get()) {
            Highlight.add(entityId, System.currentTimeMillis() + highlightSeconds.get() * 1000L);
        }

        if (beep.get() && PlayerUtils.distanceTo(pos) <= beepRadius.get() && beepTicksLeft <= 0) {
            beepTicksLeft = (int) Math.round(beepSeconds.get() * 20);
        }

        if (state != State.Watching) return;

        String who = digger != null ? digger.getName().getString() : "somebody";
        note("(highlight)%s(default) %s at (highlight)%d %d %d(default), %.0f blocks away. Packing up.",
            who, why, pos.getX(), pos.getY(), pos.getZ(), PlayerUtils.distanceTo(pos));

        recentBreaks.clear();
        begin();
    }

    private void begin() {
        totalTicks = 0;
        pickSlot = -1;

        if (!mineSpawners.get()) {
            enter(State.FindingChest);
            return;
        }

        FindItemResult pick = findSilkPick();
        if (!pick.found() && requireSilk.get()) {
            if (!warnedNoSilk) alert("No silk touch pickaxe, so the spawners are staying where they are.");
            warnedNoSilk = true;
            enter(State.FindingChest);
            return;
        }

        scanForSpawners();
        if (targets.isEmpty()) {
            note("No spawners in range.");
            enter(State.FindingChest);
            return;
        }

        ItemStack tool = pick.found() ? pickStack(pick) : ItemStack.EMPTY;
        note("%d spawner%s to pull, using (highlight)%s(default)%s.",
            targets.size(), targets.size() == 1 ? "" : "s",
            tool.isEmpty() ? "nothing" : tool.getName().getString(),
            isSilkPick(tool) ? " with silk touch" : " WITHOUT SILK TOUCH");
        enter(State.Mining);
    }

    // Main loop ---------------------------------------------------------------------------------

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        Highlight.enabled = highlight.get();
        Highlight.color = highlightColor.get().getPacked() & 0xFFFFFF;

        drainPending();
        tickHighlight();
        tickBeep();

        if (state == State.Watching) {
            releaseKeys();
            return;
        }

        // Key bindings are only read when no screen is open, and the aim refuses to fight the
        // mouse there, so the response would sit frozen behind an inventory or a shop GUI.
        // The chest phases are exempt: those states are the ones WAITING for a screen, and
        // closing it the instant it arrived is what stopped the ender chest from ever opening.
        if (mc.currentScreen != null && (state == State.Mining || state == State.FindingChest
            || state == State.TakingChestBack)) {
            closeScreen();
        }

        stateTicks++;
        totalTicks++;

        if (totalTicks > timeBudget.get() * 20 && state != State.Leaving && state != State.Finished) {
            alert("Out of time, leaving with what is done.");
            enter(State.Leaving);
        }

        switch (state) {
            case Mining -> tickMining();
            case FindingChest -> tickChest();
            case OpeningChest -> tickOpeningChest();
            case Depositing -> tickDepositing();
            case ClosingChest -> tickClosingChest();
            case TakingChestBack -> tickTakingChestBack();
            case Leaving -> tickLeaving();
            default -> {
            }
        }
    }

    /** The aim runs on frames, not ticks, so it is smooth and frame rate independent. */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        // Never fight the mouse while a screen is open. pause() drops the clock so the frame
        // after the screen closes does not apply the whole gap as one jump.
        if (mc.currentScreen != null) {
            aim.pause();
            return;
        }

        aim.update(aimSpeed.get(), aimAccel.get(), aimTremor.get());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        releaseKeys();
        Highlight.clear();
        reset();
    }

    private void drainPending() {
        Dig dig;
        while ((dig = pending.poll()) != null) {
            if (dig.entityId() >= 0) {
                // A breaking-progress packet is one person holding left click on one block, and
                // it carries their entity id. Our own id is already filtered out, so the ignore
                // radius must NOT apply here - somebody standing on top of you is the whole
                // point, and that is exactly what the radius would hide.
                if (!inRange(dig.pos())) continue;
                trip(dig.pos(), dig.entityId(), "is mining");
            } else {
                noteBreak(dig.pos(), -1);
            }
        }
    }

    private void tickHighlight() {
        if (!highlight.get()) return;
        if (highlightMode.get() != HighlightMode.AllPlayers) return;

        long until = System.currentTimeMillis() + 1000L;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(p)) continue;
            if (PlayerUtils.distanceTo(p) > triggerRadius.get()) continue;
            Highlight.add(p.getId(), until);
        }
    }

    private void tickBeep() {
        if (beepTicksLeft <= 0) return;

        beepTicksLeft--;
        if (beepCooldown-- > 0) return;

        beepCooldown = beepInterval.get() - 1;
        float volume = beepVolume.get().floatValue();
        if (volume > 0) {
            mc.getSoundManager().play(PositionedSoundInstance.master(beepEvent(), 1.4f, volume));
        }
    }

    private SoundEvent beepEvent() {
        return switch (beepSound.get()) {
            case Bit -> SoundEvents.BLOCK_NOTE_BLOCK_BIT.value();
            case Bell -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case Button -> SoundEvents.UI_BUTTON_CLICK.value();
            default -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        };
    }

    // Mining ------------------------------------------------------------------------------------

    private void scanForSpawners() {
        targets.clear();

        int r = scanRadius.get();
        int cr = (r >> 4) + 1;
        ChunkPos centre = mc.player.getChunkPos();
        Vec3d me = mc.player.getEntityPos();

        for (int cx = centre.x - cr; cx <= centre.x + cr; cx++) {
            for (int cz = centre.z - cr; cz <= centre.z + cr; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;

                WorldChunk chunk = mc.world.getChunk(cx, cz);
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (!mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) continue;
                    if (me.distanceTo(Vec3d.ofCenter(pos)) > r) continue;
                    targets.add(pos.toImmutable());
                }
            }
        }

        targets.sort((a, b) -> Double.compare(PlayerUtils.squaredDistanceTo(a), PlayerUtils.squaredDistanceTo(b)));
    }

    private void tickMining() {
        if (current != null) {
            boolean stillThere = mc.world.getBlockState(current).isOf(Blocks.SPAWNER);

            if (stillThere) {
                sawSpawner = true;
                // It was gone a moment ago and it is back: that was one spawner off a stack,
                // and the server has just re-sent the rest of the pile. Keep going.
                if (goneTicks > 0) {
                    mined++;
                    mineTicks = 0;
                    note("Spawner at (highlight)%d %d %d(default) is stacked, still going (%d so far).",
                        current.getX(), current.getY(), current.getZ(), mined);
                }
                goneTicks = 0;
            } else {
                // Breaking a block is predicted client side, so the cell reads as air for a few
                // ticks before the server says otherwise. Declaring the target finished here is
                // exactly what makes a protect give up halfway through a stack, so wait it out.
                setKey(mc.options.attackKey, false);

                if (++goneTicks >= stackConfirm.get()) {
                    if (sawSpawner) {
                        mined++;
                        note("Cleared the spawner at (highlight)%d %d %d(default).",
                            current.getX(), current.getY(), current.getZ());
                    }
                    finishTarget();
                } else {
                    return;
                }
            }
        }

        if (current == null) {
            if (targets.isEmpty()) {
                // A stack that finished while we were looking elsewhere, or one we could not see
                // on the first pass, only shows up on a rescan.
                if (rescans < 5) {
                    rescans++;
                    scanForSpawners();
                }

                if (targets.isEmpty()) {
                    if (mined > 0 || skipped > 0) {
                        note("Mined %d spawner%s%s.", mined, mined == 1 ? "" : "s",
                            skipped > 0 ? ", could not reach " + skipped : "");
                    }
                    enter(State.FindingChest);
                    return;
                }
            }

            current = targets.removeFirst();
            mineTicks = 0;
            goneTicks = 0;
            sawSpawner = false;
            walkTicks = 0;
            walkBestDistance = Double.MAX_VALUE;
            aim.stop();
        }

        setKey(mc.options.sneakKey, sneak.get());

        if (!equipSilkPick()) {
            if (requireSilk.get()) {
                alert("Lost the silk pickaxe, stopping the mining.");
                releaseKeys();
                stopWalking();
                enter(State.FindingChest);
                return;
            }
        }

        if (!reachable(current)) {
            walkTo(current);
            return;
        }

        Vec3d point = aimPoint(current);
        if (point == null) {
            skipTarget("no line of sight");
            return;
        }

        stopWalking();
        aim.aimAt(Rotations.getYaw(point), Rotations.getPitch(point));

        boolean onTarget = aim.settled(aimTolerance.get()) && lookingAt(current);
        setKey(mc.options.attackKey, onTarget);

        // This clock is reset by every break, so a tall stack never times out - only a block
        // that has stopped yielding anything does.
        mineTicks++;
        if (mineTicks > stallSeconds.get() * 20) skipTarget("nothing came off it for " + stallSeconds.get() + "s");
    }

    private void skipTarget(String why) {
        if (current != null) {
            alert("Skipping spawner at %d %d %d (%s).", current.getX(), current.getY(), current.getZ(), why);
        }
        skipped++;
        finishTarget();
    }

    private void finishTarget() {
        current = null;
        goneTicks = 0;
        setKey(mc.options.attackKey, false);
        stopWalking();
        aim.stop();
    }

    private void walkTo(BlockPos pos) {
        setKey(mc.options.attackKey, false);

        if (!walkToSpawners.get() || !canPath()) {
            skipTarget("out of reach");
            return;
        }

        double d = PlayerUtils.distanceTo(pos);
        if (d < walkBestDistance - 0.5) {
            walkBestDistance = d;
            walkTicks = 0;
        }

        if (walkTicks == 0) PathManagers.get().moveTo(pos, false);

        // Look where we are going rather than staring at the block through the wall.
        aim.aimAt(Rotations.getYaw(Vec3d.ofCenter(pos)), 12);

        if (++walkTicks > 20 * 8) {
            stopWalking();
            skipTarget(PathManagers.get().isPathing() ? "walk timed out" : "no path manager");
        }
    }

    private void stopWalking() {
        if (PathManagers.get().isPathing()) PathManagers.get().stop();
    }

    /** Meteor falls back to a no-op path manager, which would silently swallow every moveTo. */
    private boolean canPath() {
        return !"none".equalsIgnoreCase(PathManagers.get().getName());
    }

    // Ender chest -------------------------------------------------------------------------------

    private void tickChest() {
        setKey(mc.options.attackKey, false);
        setKey(mc.options.sneakKey, false); // a sneaking player holding an item cannot open a chest

        // An ender chest that is already there always wins. Placing one is only a fallback for
        // when there is none, which matters when you are stood in a hole with no room to put one.
        if (chestPos == null || !mc.world.getBlockState(chestPos).isOf(Blocks.ENDER_CHEST)) {
            BlockPos found = findEnderChest();
            if (found != null && !found.equals(chestPos)) {
                chestWalkTicks = 0;
                chestWalkBest = Double.MAX_VALUE;
            }
            chestPos = found;
            chestWasPlaced = false;
        }

        if (chestPos != null) {
            if (reachable(chestPos) && aimPoint(chestPos) != null) {
                stopWalking();
                note("Using the ender chest at (highlight)%d %d %d(default).",
                    chestPos.getX(), chestPos.getY(), chestPos.getZ());
                enter(State.OpeningChest);
                return;
            }

            if (walkToChest()) return; // on the way to it

            alert("Could not get to the ender chest at %d %d %d.",
                chestPos.getX(), chestPos.getY(), chestPos.getZ());
            unreachableChests.add(chestPos.asLong());
            chestPos = null;
        }

        if (!placeEnderChest.get()) {
            alert("No ender chest nearby and placing one is off.");
            enter(State.Leaving);
            return;
        }

        FindItemResult ec = InvUtils.find(Items.ENDER_CHEST);
        if (!ec.found()) {
            alert("No ender chest to place.");
            enter(State.Leaving);
            return;
        }

        if (chestSpot == null) chestSpot = findChestSpot();
        if (chestSpot == null) {
            alert("Nowhere to put an ender chest.");
            enter(State.Leaving);
            return;
        }

        if (!equip(ec)) {
            alert("Could not get the ender chest into my hand.");
            enter(State.Leaving);
            return;
        }

        Direction support = supportSide(chestSpot);
        if (support == null) {
            chestSpot = null;
            return;
        }

        // Aim just inside the face of the block the chest leans against, then tap use. The game
        // does its own raycast from that rotation, so the placement is the vanilla one.
        Vec3d face = Vec3d.ofCenter(chestSpot).add(Vec3d.of(support.getVector()).multiply(0.51));
        aim.aimAt(Rotations.getYaw(face), Rotations.getPitch(face));

        boolean ready = aim.settled(aimTolerance.get())
            && mc.crosshairTarget instanceof BlockHitResult hit
            && hit.getBlockPos().equals(chestSpot.offset(support));

        pulseUse(ready);

        if (mc.world.getBlockState(chestSpot).isOf(Blocks.ENDER_CHEST)) {
            setKey(mc.options.useKey, false);
            chestPos = chestSpot;
            chestWasPlaced = true;
            enter(State.OpeningChest);
            return;
        }

        if (stateTicks > 20 * 6) {
            setKey(mc.options.useKey, false);
            alert("Could not place the ender chest.");
            enter(State.Leaving);
        }
    }

    /** Walks toward an ender chest that is out of reach. False means give up on walking. */
    private boolean walkToChest() {
        if (!walkToSpawners.get() || !canPath()) return false;

        double d = PlayerUtils.distanceTo(chestPos);
        if (d < chestWalkBest - 0.5) {
            chestWalkBest = d;
            chestWalkTicks = 0;
        }

        if (chestWalkTicks == 0) PathManagers.get().moveTo(chestPos, false);
        aim.aimAt(Rotations.getYaw(Vec3d.ofCenter(chestPos)), 10);

        if (++chestWalkTicks > 20 * 8) {
            stopWalking();
            return false;
        }

        return true;
    }

    private void tickOpeningChest() {
        setKey(mc.options.attackKey, false);
        setKey(mc.options.sneakKey, false);

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            setKey(mc.options.useKey, false);
            depositCooldown = 0;
            enter(State.Depositing);
            return;
        }

        if (chestPos == null || !mc.world.getBlockState(chestPos).isOf(Blocks.ENDER_CHEST)) {
            chestPos = null;
            enter(State.FindingChest);
            return;
        }

        Vec3d point = aimPoint(chestPos);
        if (point == null) point = Vec3d.ofCenter(chestPos);
        aim.aimAt(Rotations.getYaw(point), Rotations.getPitch(point));

        boolean ready = aim.settled(aimTolerance.get()) && lookingAt(chestPos) && reachable(chestPos);
        pulseUse(ready);

        if (stateTicks > 20 * 10) {
            setKey(mc.options.useKey, false);
            alert("The ender chest at %d %d %d would not open (aim %.1f deg off, looking at it: %s).",
                chestPos.getX(), chestPos.getY(), chestPos.getZ(), aim.error(), lookingAt(chestPos));
            enter(State.Leaving);
        }
    }

    private void tickDepositing() {
        setKey(mc.options.useKey, false);

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler menu) || !isEnderChestMenu(menu)) {
            enter(State.ClosingChest);
            return;
        }

        // Did the click we sent last time actually move anything? If the chest is full the
        // server just ignores it, and re-sending it forever is how this hangs until the time
        // budget runs out with a full inventory.
        if (lastClickSlot >= 0) {
            Slot slot = slotById(menu, lastClickSlot);
            ItemStack now = slot == null ? ItemStack.EMPTY : slot.getStack();

            if (!now.isEmpty() && now.getCount() == lastClickCount && ItemStack.areItemsEqual(now, lastClickStack)) {
                if (++lastClickRetries >= 2) {
                    stuckSlots.add(lastClickSlot);
                    lastClickRetries = 0;
                    lastClickSlot = -1;
                }
                // fall through and try the next slot rather than hammering this one
            } else {
                deposited++;
                lastClickRetries = 0;
                lastClickSlot = -1;
            }
        }

        if (depositCooldown-- > 0) return;
        depositCooldown = clickDelay.get();

        int pick = heldPickSlotId(menu);

        for (Slot slot : menu.slots) {
            if (slot.inventory != mc.player.getInventory()) continue;
            if (slot.id == pick) continue;
            if (stuckSlots.contains(slot.id)) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !shouldDeposit(stack)) continue;

            lastClickSlot = slot.id;
            lastClickCount = stack.getCount();
            lastClickStack = stack.copy();

            shiftClick(slot);
            return;
        }

        if (!stuckSlots.isEmpty()) {
            alert("Ender chest is full - %d stack%s left in the inventory.",
                stuckSlots.size(), stuckSlots.size() == 1 ? "" : "s");
        }

        note("Put %d stack%s in the ender chest.", deposited, deposited == 1 ? "" : "s");
        enter(State.ClosingChest);
    }

    /**
     * A shift-click on a slot, put through the screen that is on the player's monitor rather
     * than assembled as a packet. AbstractContainerScreen.slotClicked is the exact method the
     * mouse lands in once mouseClicked has worked out which slot is under the cursor, so the
     * hover state, the cursor stack and the click sound all behave as if it had been clicked.
     */
    private void shiftClick(Slot slot) {
        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            ((ContainerScreenAccessor) screen).nameeprotect$slotClicked(
                slot, slot.id, 0, SlotActionType.QUICK_MOVE);
            return;
        }

        // No screen on this client (headless or the server opened the menu without one).
        InvUtils.shiftClick().slotId(slot.id);
    }

    /**
     * An ender chest is always a three row chest. Without this the module would happily empty
     * the whole inventory into whatever container or plugin menu happened to be open.
     */
    private boolean isEnderChestMenu(GenericContainerScreenHandler menu) {
        return menu.getRows() == 3 && menu.syncId != 0;
    }

    private Slot slotById(GenericContainerScreenHandler menu, int id) {
        return id >= 0 && id < menu.slots.size() ? menu.slots.get(id) : null;
    }

    private void tickClosingChest() {
        if (mc.currentScreen != null || mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            closeScreen();
            return;
        }

        if (chestWasPlaced && reclaimEnderChest.get() && chestPos != null
            && mc.world.getBlockState(chestPos).isOf(Blocks.ENDER_CHEST)) {
            enter(State.TakingChestBack);
        } else {
            enter(State.Leaving);
        }
    }

    private void tickTakingChestBack() {
        if (!mc.world.getBlockState(chestPos).isOf(Blocks.ENDER_CHEST)) {
            setKey(mc.options.attackKey, false);
            enter(State.Leaving);
            return;
        }

        equipSilkPick();

        Vec3d point = aimPoint(chestPos);
        if (point == null) point = Vec3d.ofCenter(chestPos);
        aim.aimAt(Rotations.getYaw(point), Rotations.getPitch(point));

        boolean onTarget = aim.settled(aimTolerance.get()) && lookingAt(chestPos) && reachable(chestPos);
        setKey(mc.options.attackKey, onTarget);

        if (stateTicks > 20 * 8) {
            setKey(mc.options.attackKey, false);
            alert("Leaving the ender chest behind.");
            enter(State.Leaving);
        }
    }

    private void tickLeaving() {
        releaseKeys();
        aim.stop();

        if (!leaveServer.get()) {
            note("Done. Not leaving, because leave-server is off. Watching again.");
            reset();
            return;
        }

        // Give the last container click a tick to actually go out on the wire.
        if (stateTicks < 4) return;

        state = State.Finished;
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(
            Text.literal("[Spawner Protect] Somebody was digging at your base.")));
    }

    // Helpers -----------------------------------------------------------------------------------

    /** Chat plus the log. The log line is what makes a run readable afterwards. */
    private void note(String message, Object... args) {
        info(message, args);
        NameeProtectAddon.LOG.info("[SpawnerProtect] {}", plain(message, args));
    }

    private void alert(String message, Object... args) {
        warning(message, args);
        NameeProtectAddon.LOG.warn("[SpawnerProtect] {}", plain(message, args));
    }

    private static String plain(String message, Object... args) {
        String text = args.length == 0 ? message : String.format(message, args);
        return text.replace("(highlight)", "").replace("(default)", "");
    }

    /** Closes whatever is open the same way the escape key does. */
    private void closeScreen() {
        if (mc.currentScreen instanceof HandledScreen<?> open) open.close();
        else if (mc.currentScreen != null) mc.setScreen(null);
        else if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
    }

    private void enter(State next) {
        if (next != state) NameeProtectAddon.LOG.info("[SpawnerProtect] {} -> {}", state, next);
        state = next;
        stateTicks = 0;
        useTimer = 0;
        aim.stop();
    }

    private boolean shouldDeposit(ItemStack stack) {
        if (deposit.get() == Deposit.SpawnersOnly) return stack.isOf(Items.SPAWNER);
        if (chestWasPlaced && reclaimEnderChest.get() && stack.isOf(Items.ENDER_CHEST)) return false;
        return true;
    }

    /** Menu slot id currently holding the silk pickaxe, so the deposit leaves it alone. */
    private int heldPickSlotId(GenericContainerScreenHandler menu) {
        if (deposit.get() == Deposit.SpawnersOnly) return -1;

        // The silk pickaxe is preferred, but any pickaxe is worth keeping back: without one
        // there is nothing to pick the ender chest up again with.
        int fallback = -1;

        for (Slot slot : menu.slots) {
            if (slot.inventory != mc.player.getInventory()) continue;

            ItemStack stack = slot.getStack();
            if (isSilkPick(stack)) return slot.id;
            if (fallback == -1 && !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES)) fallback = slot.id;
        }

        return fallback;
    }

    private ItemStack pickStack(FindItemResult result) {
        if (!result.found()) return ItemStack.EMPTY;
        if (result.isOffhand()) return mc.player.getOffHandStack();

        var items = mc.player.getInventory().getMainStacks();
        return result.slot() >= 0 && result.slot() < items.size() ? items.get(result.slot()) : ItemStack.EMPTY;
    }

    private boolean isSilkPick(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES)
            && Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH);
    }

    private FindItemResult findSilkPick() {
        FindItemResult silk = InvUtils.find(this::isSilkPick);
        if (silk.found() || requireSilk.get()) return silk;
        return InvUtils.find(stack -> stack.isIn(ItemTags.PICKAXES));
    }

    private boolean equipSilkPick() {
        ItemStack held = mc.player.getMainHandStack();
        if (isSilkPick(held)) return true;
        if (!requireSilk.get() && held.isIn(ItemTags.PICKAXES)) return true;

        return equip(findSilkPick());
    }

    /** Put an item in the main hand the way a person would: change slot, or swap it into one. */
    private boolean equip(FindItemResult result) {
        if (!result.found()) return false;
        if (result.isMainHand()) return true;

        if (result.isHotbar()) return InvUtils.swap(result.slot(), false);

        // An empty hotbar slot if there is one, so nothing already in the bar gets displaced.
        int hotbar = mc.player.getInventory().getSwappableHotbarSlot();

        if (result.isOffhand()) {
            InvUtils.quickSwap().fromId(hotbar).toOffhand();
            return InvUtils.swap(hotbar, false);
        }

        if (result.isMain()) {
            InvUtils.quickSwap().fromId(hotbar).to(result.slot());
            return InvUtils.swap(hotbar, false);
        }

        return false;
    }

    private boolean reachable(BlockPos pos) {
        double reach = mc.player.getBlockInteractionRange();
        return mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= reach + 0.35;
    }

    private boolean lookingAt(BlockPos pos) {
        return mc.crosshairTarget instanceof BlockHitResult hit
            && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(pos);
    }

    /**
     * A point on the block that is actually visible from the eye. Tries the centre first, then
     * the middle of each face that points our way, so a block in a wall niche still gets hit.
     */
    private Vec3d aimPoint(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();

        // Off the block's own shape, not a guessed cube. An ender chest is inset on every side,
        // so points half a block out from the centre miss it entirely.
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        Box box = (shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox()).offset(pos);
        Vec3d centre = box.getCenter();

        if (visible(eye, centre, pos)) return centre;

        Vec3d toEye = eye.subtract(centre).normalize();
        Vec3d best = null;
        double bestDot = -2;

        for (Direction dir : Direction.values()) {
            Vec3d normal = Vec3d.of(dir.getVector());
            double dot = toEye.dotProduct(normal);
            if (dot <= 0.05) continue; // that face points away from us

            Vec3d point = centre.add(
                normal.x * Math.max(0, box.getLengthX() / 2 - 0.02),
                normal.y * Math.max(0, box.getLengthY() / 2 - 0.02),
                normal.z * Math.max(0, box.getLengthZ() / 2 - 0.02));

            if (visible(eye, point, pos) && dot > bestDot) {
                bestDot = dot;
                best = point;
            }
        }

        return best;
    }

    private boolean visible(Vec3d from, Vec3d to, BlockPos pos) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, to,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    /**
     * The nearest ender chest that is already in the world. Reach is deliberately not a filter -
     * if it is out of reach we walk to it rather than putting a second one down.
     */
    private BlockPos findEnderChest() {
        int r = chestSearchRadius.get();
        int cr = (r >> 4) + 1;
        ChunkPos centre = mc.player.getChunkPos();
        Vec3d me = mc.player.getEyePos();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int cx = centre.x - cr; cx <= centre.x + cr; cx++) {
            for (int cz = centre.z - cr; cz <= centre.z + cr; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;

                WorldChunk chunk = mc.world.getChunk(cx, cz);
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (!mc.world.getBlockState(pos).isOf(Blocks.ENDER_CHEST)) continue;
                    if (unreachableChests.contains(pos.asLong())) continue;

                    double d = me.distanceTo(Vec3d.ofCenter(pos));
                    if (d > r || d >= bestDistance) continue;

                    bestDistance = d;
                    best = pos.toImmutable();
                }
            }
        }

        return best;
    }

    /** An empty cell next to a solid block, in reach, that we can see. */
    private BlockPos findChestSpot() {
        BlockPos me = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        Box self = mc.player.getBoundingBox();

        for (BlockPos pos : BlockPos.iterate(me.add(-3, -1, -3), me.add(3, 2, 3))) {
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir()) continue;
            // A block cannot go where we are stood: the game rejects it and the deposit dies.
            if (self.intersects(new Box(pos))) continue;
            if (!mc.world.getBlockState(pos.up()).isAir()) continue; // needs headroom to open
            if (!BlockUtils.canPlace(pos, true)) continue;
            if (!reachable(pos)) continue;
            if (supportSide(pos) == null) continue;

            double d = PlayerUtils.squaredDistanceTo(pos);
            if (d < bestDistance) {
                bestDistance = d;
                best = pos.toImmutable();
            }
        }

        return best;
    }

    /** Direction from an empty cell to a solid neighbour we can click on. */
    private Direction supportSide(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();

        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbour);
            if (!state.isFullCube(mc.world, neighbour)) continue;
            // Right-clicking a crafting table or a furnace opens it instead of placing on it,
            // and sneak is deliberately off at this point so that bypass is not available.
            if (BlockUtils.isClickable(state.getBlock())) continue;

            Vec3d face = Vec3d.ofCenter(pos).add(Vec3d.of(dir.getVector()).multiply(0.51));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, face,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(neighbour)) return dir;
        }

        return null;
    }

    // Key presses are the real thing: the game reads these bindings itself, so mining, placing
    // and opening all run through the normal vanilla paths.
    private void setKey(KeyBinding key, boolean want) {
        if (key.isPressed() == want) {
            track(key, want);
            return;
        }

        // Shift and sprint are toggle bindings when the matching option is on, and then setDown
        // flips instead of setting.
        if (key == mc.options.sneakKey && mc.options.getSneakToggled().getValue()) key.setPressed(true);
        else key.setPressed(want);

        track(key, want);
    }

    /**
     * Taps the use key instead of holding it. Held down, vanilla re-fires the interaction every
     * few ticks, and on a slow round trip the second one arrives just after the chest opened and
     * shuts it again.
     */
    private void pulseUse(boolean want) {
        if (!want) {
            setKey(mc.options.useKey, false);
            useTimer = 0;
            return;
        }

        useTimer++;
        int phase = useTimer % 14;
        setKey(mc.options.useKey, phase >= 1 && phase <= 2);
    }

    private void track(KeyBinding key, boolean down) {
        if (key == mc.options.attackKey) attackDown = down;
        else if (key == mc.options.useKey) useDown = down;
        else if (key == mc.options.sneakKey) shiftDown = down;
    }

    private void releaseKeys() {
        if (mc.options == null) return;

        if (attackDown) setKey(mc.options.attackKey, false);
        if (useDown) setKey(mc.options.useKey, false);
        if (shiftDown) setKey(mc.options.sneakKey, false);

        attackDown = useDown = shiftDown = false;
    }
}
