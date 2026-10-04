package dev.maro.module.impl.misc;

import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.runtime.settings.SettingAdapters;
import dev.maro.runtime.settings.StringListSetting;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Random-teleports over and over and gets you out the moment a block you are hunting for - a
 * beacon, netherite blocks, shulkers - shows up in the chunks around where you landed.
 *
 * <p>Each round: run the next ticked region's /rtp command, wait for the teleport, then look
 * through every chunk that loads for a few seconds. Found: disconnect (the disconnect screen says
 * what and where) or just stop. Not found: wait out the delay and go again. Chunks that were
 * already loaded where you started are never counted, so your own base cannot trip it.
 */
public class BlockDisconnect extends Module {
    private static final String[] REGIONS = {"East", "West", "EU Central", "EU West", "Oceania", "Asia"};

    private final BooleanSetting[] regions = new BooleanSetting[REGIONS.length];
    private final dev.maro.runtime.settings.Setting<List<String>> commands = new StringListSetting.Builder()
            .name("RTP Commands")
            .description("The command for each region, in the order above, without the slash. Change them if the server names regions differently")
            .defaultValue("rtp east", "rtp west", "rtp eu central", "rtp eu west", "rtp oceania", "rtp asia")
            .build();

    private final BooleanSetting beacon = new BooleanSetting("Beacon", "Beacons", true);
    private final BooleanSetting netherite = new BooleanSetting("Netherite Block", "Blocks of netherite", true);
    private final BooleanSetting diamond = new BooleanSetting("Diamond Block", "Blocks of diamond", false);
    private final BooleanSetting emerald = new BooleanSetting("Emerald Block", "Blocks of emerald", false);
    private final BooleanSetting shulker = new BooleanSetting("Shulker Box", "Shulker boxes of any colour", false);
    private final BooleanSetting enderChest = new BooleanSetting("Ender Chest", "Ender chests", false);
    private final BooleanSetting spawner = new BooleanSetting("Spawner", "Mob spawners", false);
    private final BooleanSetting anchor = new BooleanSetting("Respawn Anchor", "Respawn anchors", false);
    private final dev.maro.runtime.settings.Setting<List<String>> extra = new StringListSetting.Builder()
            .name("Extra Blocks")
            .description("More block ids to look for, like minecraft:ancient_debris. One per line or separated by ;")
            .defaultValue()
            .build();
    private final NumberSetting minBlocks = new NumberSetting("Min Blocks", "How many of them must show up at once", 1, 1, 50, 1);

    private final NumberSetting delay = new NumberSetting("RTP Delay", "Seconds between one /rtp and the next", 15, 3, 300, 1).suffix("s");
    private final NumberSetting scanTime = new NumberSetting("Scan Time", "How long to keep looking after landing, while chunks load", 5, 1, 30, 0.5).suffix("s");
    private final NumberSetting timeout = new NumberSetting("Teleport Timeout", "Try again if no teleport happens within this long", 25, 5, 120, 1).suffix("s");

    private final ModeSetting action = new ModeSetting("Action", "What to do when a block is found", "Disconnect", "Disconnect", "Stop");
    private final BooleanSetting statusBar = new BooleanSetting("Status Bar", "A bar at the top of the screen with what it is doing", true);

    private final List<SettingSection> sections;

    private enum Phase { SEND, TELEPORTING, SCANNING, WAITING }

    private Phase phase = Phase.SEND;
    private long phaseAt;
    private long sentAt;
    private int nextRegion;
    private String lastCommand = "";
    private Vec3d sentFrom;
    private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    private final Set<Long> scanned = new HashSet<>();
    private int rounds;
    private String found = "";

    public BlockDisconnect() {
        super("Block Disconnect", "Random-teleports until a block you are hunting for shows up, then disconnects", Category.MISC);

        SettingSection rtp = new SettingSection("RTP");
        for (int i = 0; i < REGIONS.length; i++) {
            regions[i] = rtp.add(add(new BooleanSetting(REGIONS[i], "Include /rtp " + REGIONS[i].toLowerCase(), i == 0)));
        }
        rtp.add(add(SettingAdapters.adapt(commands)));

        SettingSection blocks = new SettingSection("Blocks");
        for (BooleanSetting b : new BooleanSetting[]{beacon, netherite, diamond, emerald, shulker, enderChest, spawner, anchor}) blocks.add(add(b));
        blocks.add(add(SettingAdapters.adapt(extra)));
        blocks.add(add(minBlocks));

        SettingSection timing = new SettingSection("Timing");
        timing.add(add(delay));
        timing.add(add(scanTime));
        timing.add(add(timeout));

        SettingSection after = new SettingSection("When Found");
        after.add(add(action));
        after.add(add(statusBar));

        sections = List.of(rtp, blocks, timing, after);

        // New chunks after a teleport are checked as they arrive.
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (isEnabled() && phase == Phase.SCANNING) queue.add(chunk.getPos());
        });
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        phase = Phase.SEND;
        phaseAt = System.currentTimeMillis();
        nextRegion = 0;
        rounds = 0;
        found = "";
        queue.clear();
        scanned.clear();
    }

    // ---- the round ----------------------------------------------------------------------

    @Override
    public void onTick() {
        if (!inGame() || mc.getNetworkHandler() == null) return;
        long now = System.currentTimeMillis();

        switch (phase) {
            case SEND -> {
                String command = nextCommand();
                if (command == null) {
                    Notifications.push("Block Disconnect", "Tick at least one RTP region", Notifications.Type.WARNING);
                    setEnabled(false);
                    return;
                }
                lastCommand = command;
                sentFrom = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                sentAt = now;
                mc.getNetworkHandler().sendChatCommand(command);
                rounds++;
                to(Phase.TELEPORTING);
            }
            case TELEPORTING -> {
                // A jump of more than a few chunks, or a new world, is the teleport.
                if (new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).squaredDistanceTo(sentFrom) > 64 * 64) {
                    queue.clear();
                    scanned.clear();
                    queueLoaded();
                    to(Phase.SCANNING);
                } else if (now - sentAt > timeout.get() * 1000) {
                    to(Phase.SEND);
                }
            }
            case SCANNING -> {
                // A few chunks a tick, so a fresh landing does not stutter.
                for (int i = 0; i < 12 && !queue.isEmpty(); i++) {
                    if (scan(queue.poll())) return;
                }
                if (now - phaseAt > scanTime.get() * 1000 && queue.isEmpty()) to(Phase.WAITING);
            }
            case WAITING -> {
                if (now - sentAt >= delay.get() * 1000) to(Phase.SEND);
            }
        }
    }

    private void to(Phase next) {
        phase = next;
        phaseAt = System.currentTimeMillis();
    }

    private String nextCommand() {
        List<String> list = commands.get();
        for (int tries = 0; tries < REGIONS.length; tries++) {
            int i = nextRegion % REGIONS.length;
            nextRegion++;
            if (regions[i].get() && i < list.size() && !list.get(i).isBlank()) {
                String command = list.get(i).trim();
                return command.startsWith("/") ? command.substring(1) : command;
            }
        }
        return null;
    }

    private void queueLoaded() {
        int view = mc.options.getClampedViewDistance();
        ChunkPos center = mc.player.getChunkPos();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) queue.add(new ChunkPos(center.x + dx, center.z + dz));
        }
    }

    // ---- looking through chunks ---------------------------------------------------------

    private Predicate<BlockState> wanted() {
        Set<Block> blocks = new HashSet<>();
        if (beacon.get()) blocks.add(Blocks.BEACON);
        if (netherite.get()) blocks.add(Blocks.NETHERITE_BLOCK);
        if (diamond.get()) blocks.add(Blocks.DIAMOND_BLOCK);
        if (emerald.get()) blocks.add(Blocks.EMERALD_BLOCK);
        if (enderChest.get()) blocks.add(Blocks.ENDER_CHEST);
        if (spawner.get()) blocks.add(Blocks.SPAWNER);
        if (anchor.get()) blocks.add(Blocks.RESPAWN_ANCHOR);
        for (String id : extra.get()) {
            Identifier parsed = Identifier.tryParse(id.trim());
            if (parsed != null && Registries.BLOCK.containsId(parsed)) blocks.add(Registries.BLOCK.get(parsed));
        }
        boolean shulkers = shulker.get();
        return state -> blocks.contains(state.getBlock()) || (shulkers && state.getBlock() instanceof ShulkerBoxBlock);
    }

    /** Looks through one loaded chunk. True if it set things off. */
    private boolean scan(ChunkPos pos) {
        if (pos == null || !scanned.add(pos.toLong())) return false;
        if (!mc.world.getChunkManager().isChunkLoaded(pos.x, pos.z)) return false;
        WorldChunk chunk = mc.world.getChunk(pos.x, pos.z);
        Predicate<BlockState> wanted = wanted();
        ChunkSection[] sections = chunk.getSectionArray();
        List<BlockPos> hits = new ArrayList<>();
        String name = null;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty() || !section.hasAny(wanted)) continue;
            int baseY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!wanted.test(state)) continue;
                        hits.add(new BlockPos(pos.getStartX() + x, baseY + y, pos.getStartZ() + z));
                        if (name == null) name = state.getBlock().getName().getString();
                    }
                }
            }
        }

        if (hits.size() < minBlocks.getInt()) return false;
        trigger(name, hits.getFirst(), hits.size());
        return true;
    }

    private void trigger(String name, BlockPos at, int count) {
        found = (count > 1 ? count + "x " : "") + name + " at " + at.getX() + " " + at.getY() + " " + at.getZ();
        if (action.is("Disconnect") && mc.getNetworkHandler() != null) {
            setEnabled(false);
            mc.getNetworkHandler().getConnection().disconnect(Text.literal("Block Disconnect\n\nFound " + found + "\nafter /" + lastCommand));
            return;
        }
        Notifications.push("Block found", found, Notifications.Type.SUCCESS, 6000);
        setEnabled(false);
    }

    /** Looks through every loaded chunk around you now, for the in-game test. True if it found something. */
    public boolean scanAroundNow() {
        if (!inGame()) return false;
        scanned.clear();
        queue.clear();
        queueLoaded();
        while (!queue.isEmpty()) {
            if (scan(queue.poll())) return true;
        }
        return false;
    }

    public String lastFound() {
        return found;
    }

    // ---- status bar ---------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!statusBar.get() || !inGame() || mc.options.hudHidden) return;
        long now = System.currentTimeMillis();
        String state = switch (phase) {
            case SEND -> "Teleporting";
            case TELEPORTING -> "Waiting for /" + lastCommand;
            case SCANNING -> "Scanning " + scanned.size() + " chunks";
            case WAITING -> "Next /rtp in " + Math.max(0, (int) Math.ceil((sentAt + delay.get() * 1000 - now) / 1000.0)) + "s";
        };
        String rounds = "Round " + this.rounds;

        float scale = 0.8f;
        float gap = 12;
        float width = 18 + Fonts.width("Block Disconnect", true, scale) + gap + Fonts.width(state, true, scale) + gap
                + Fonts.width(rounds, true, scale) + 10;
        float height = 16;
        float left = mc.getWindow().getScaledWidth() / 2f - width / 2f;
        float top = 24;

        Render2D.shadow(ctx, left, top + 1, width, height, 8, 7, 0x66000000);
        Render2D.roundRect(ctx, left, top, width, height, 8, 0xE00B0D12);
        Render2D.roundOutline(ctx, left, top, width, height, 8, Render2D.px(), 0x1CFFFFFF);
        float pulse = (float) (0.55 + 0.45 * Math.sin(now / 220.0));
        Render2D.circle(ctx, left + 11, top + height / 2f, 3, ColorUtil.withAlpha(Theme.accent(), Math.round(255 * pulse)));
        float x = left + 18;
        Fonts.drawV(ctx, "Block Disconnect", x, top + height / 2f, Theme.TEXT, true, scale);
        x += Fonts.width("Block Disconnect", true, scale) + gap;
        Fonts.beginRaw();
        Fonts.drawV(ctx, state, x, top + height / 2f, Theme.accent(), true, scale);
        Fonts.endRaw();
        x += Fonts.width(state, true, scale) + gap;
        Fonts.drawV(ctx, rounds, x, top + height / 2f, ColorUtil.withAlpha(Theme.TEXT, 170), true, scale);
    }
}
