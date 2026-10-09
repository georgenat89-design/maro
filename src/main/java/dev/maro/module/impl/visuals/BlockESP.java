package dev.maro.module.impl.visuals;

import dev.maro.Maro;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.esp.BlockEspRenderer;
import dev.maro.render.esp.Renderer3D;
import dev.maro.render.esp.ShapeMode;
import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.event.EventHandler;
import dev.maro.runtime.events.world.BlockUpdateEvent;
import dev.maro.runtime.events.world.ChunkLoadEvent;
import dev.maro.runtime.utils.render.color.Color;
import com.google.gson.JsonObject;
import dev.maro.gui.hud.BlockEspScreen;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * Block ESP: the blocks you pick (spawners, storage, ores and any you type in) boxed through walls,
 * with clean glowing tracers running out to them and bloom over both, and a Y limit so it shows only
 * what is at or below a height, or only while you are.
 *
 * <p>Finding them: each chunk is scanned the moment it arrives from the server, before it is drawn,
 * and the loaded chunks round you are swept nearest first for any missed; scans run on worker
 * threads, from copies of only those chunk sections that hold a wanted block at all (each section's
 * palette says so without looking at its blocks). After that the block changes the game reports keep the list
 * up to date, so a chunk is only scanned again if it is reloaded or what you look for changes.
 */
public class BlockESP extends Module implements dev.maro.render.esp.EspStyle {
    /** A kind of block to look for, the colour it is drawn in, and whether it is storage (Storage ESP's). */
    private enum Group {
        SPAWNERS("Spawners", "Mob and trial spawners", 0xFFFF3B6B, true, false,
                b -> b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER),
        CHESTS("Chests", "Chests and trapped chests", 0xFFFFA733, true, true,
                b -> b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST),
        SHULKERS("Shulker Boxes", "Shulker boxes of every colour", 0xFFFF6AD5, true, true,
                b -> b instanceof ShulkerBoxBlock),
        ENDER_CHESTS("Ender Chests", "Ender chests", 0xFFB45CFF, true, true,
                b -> b == Blocks.ENDER_CHEST),
        BARRELS("Barrels", "Barrels", 0xFFD6904E, true, true,
                b -> b == Blocks.BARREL),
        HOPPERS("Hoppers", "Hoppers", 0xFF9AA3B5, false, true,
                b -> b == Blocks.HOPPER),
        DISPENSERS("Dispensers", "Dispensers and droppers", 0xFF7FD3FF, false, true,
                b -> b == Blocks.DISPENSER || b == Blocks.DROPPER),
        FURNACES("Furnaces", "Furnaces, blast furnaces and smokers", 0xFFFF8A4A, false, true,
                b -> b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER),
        OTHER_STORAGE("Other Storage", "Brewing stands, crafters, decorated pots and chiseled bookshelves", 0xFF5FE0B0, false, true,
                b -> b == Blocks.BREWING_STAND || b == Blocks.CRAFTER || b == Blocks.DECORATED_POT || b == Blocks.CHISELED_BOOKSHELF),
        DEBRIS("Ancient Debris", "Ancient debris in the Nether", 0xFFFF7A3D, true, false,
                b -> b == Blocks.ANCIENT_DEBRIS),
        DIAMONDS("Diamond Ore", "Diamond ore, stone and deepslate", 0xFF4DF0FF, true, false,
                b -> b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE),
        EMERALDS("Emerald Ore", "Emerald ore, stone and deepslate", 0xFF35F07A, false, false,
                b -> b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE),
        GOLD("Gold Ore", "Gold ore, including the Nether's", 0xFFFFD84A, false, false,
                b -> b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE),
        IRON("Iron Ore", "Iron ore, stone and deepslate", 0xFFE8B98F, false, false,
                b -> b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE),
        REDSTONE("Redstone Ore", "Redstone ore, stone and deepslate", 0xFFFF2E2E, false, false,
                b -> b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE),
        LAPIS("Lapis Ore", "Lapis ore, stone and deepslate", 0xFF3D6BFF, false, false,
                b -> b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE);

        final String label, description;
        final int color;
        final boolean on, storage;
        final Predicate<Block> matches;

        Group(String label, String description, int color, boolean on, boolean storage, Predicate<Block> matches) {
            this.label = label;
            this.description = description;
            this.color = color;
            this.on = on;
            this.storage = storage;
            this.matches = matches;
        }
    }

    /** Whether a block is storage, which Storage ESP finds and Block ESP leaves to it. */
    public static boolean isStorage(Block block) {
        for (Group g : Group.values()) if (g.storage && g.matches.test(block)) return true;
        return false;
    }

    /** Chunks looked at each tick and scans waiting at once: high, so a teleport's worth of chunks is done in a moment. */
    private static final int PER_TICK = 256, QUEUE_LIMIT = 1024;
    /** The list is redrawn at most this often while scans stream in (about every other frame). */
    private static final long REBUILD_NANOS = 30_000_000L;
    /** The nearest boxes on screen that get a soft edge and a glow; past these, the cost is not worth it. */
    private static final int SOFT_EDGES = 300, GLOWING = 400;

    private static BlockESP instance;

    // ---- blocks: picked in BlockEspScreen, each with its own colour, kept in the order they were added
    private final Map<Block, Integer> picked = new LinkedHashMap<>();
    /** Bumped whenever a block is picked or dropped (not when a colour changes), so the scan starts over. */
    private int pickedVersion, builtVersion = Integer.MIN_VALUE;
    protected final ButtonSetting blocksButton = add(new ButtonSetting("Blocks",
            "Pick which blocks to find, and the colour of each", "Choose",
            () -> mc.setScreen(new BlockEspScreen(mc.currentScreen, this))));

    // ---- Y level
    protected final ModeSetting yLimit = add(new ModeSetting("Y Limit",
            "Blocks: only blocks at or below Max Y. You: only while you are at or below Max Y", defaultYLimit(), "Off", "Blocks", "You"));
    protected final NumberSetting maxY = add(new NumberSetting("Max Y", "The height blocks (or you) must be at or below", 0, -64, 320, 1)
            .visible(() -> !yLimit.is("Off")));

    // ---- look
    protected final NumberSetting range = add(new NumberSetting("Range", "How far out to look, in chunks (only chunks the server has sent you can be seen)", defaultRange(), 2, 64, 1).suffix(" chunks"));
    protected final ModeSetting style = add(new ModeSetting("Style", "Boxes filled and outlined, outlined only, or filled only", "Both",
            "Both", "Outline", "Fill"));
    protected final ModeSetting colorMode = add(new ModeSetting("Color", "Each kind of block in its own colour, or all in one", "By Block",
            "By Block", "One Color"));
    protected final ColorSetting oneColor = add(new ColorSetting("ESP Color", "The colour every box is drawn in", 0xFF7B2CFF)
            .visible(() -> colorMode.is("One Color")));
    protected final NumberSetting fillOpacity = add(new NumberSetting("Fill Opacity", "How solid the boxes' fill is", 22, 0, 100, 1)
            .suffix("%").visible(() -> !style.is("Outline")));
    protected final NumberSetting lineWidth = add(new NumberSetting("Line Width", "How thick the boxes' edges are", 2, 0.5, 4, 0.25)
            .suffix("px").visible(() -> !style.is("Fill")));
    protected final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", "See the boxes through blocks", true));
    protected final NumberSetting maxBlocks = add(new NumberSetting("Max Blocks", "Draw at most this many, nearest first", 10000, 100, 50000, 100));
    protected final BooleanSetting smoothEdges = add(new BooleanSetting("Smooth Edges", "A soft feathered edge on the nearest boxes' lines", true));

    // ---- tracers
    protected final BooleanSetting tracers = add(new BooleanSetting("Tracers", "Clean glowing lines from your crosshair to the blocks", true));
    protected final ModeSetting tracerTo = add(new ModeSetting("Tracer To",
            "Each Group: one line to each cluster of the same block (a row of chests gets one), to its nearest. Each Block: a line to every block",
            "Each Group", "Each Group", "Each Block").visible(tracers::get));
    protected final ModeSetting tracerStart = add(new ModeSetting("Tracer Start", "Where the lines start", "Crosshair", "Crosshair", "Bottom")
            .visible(tracers::get));
    protected final ModeSetting tracerColor = add(new ModeSetting("Tracer Color",
            "Block matches the box, Distance goes from green to red as you get close", "Block", "Block", "Distance", "Rainbow", "Custom")
            .visible(tracers::get));
    protected final ColorSetting tracerCustom = add(new ColorSetting("Tracer Custom", "Line colour in Custom mode", 0xFFFFFFFF)
            .visible(() -> tracers.get() && tracerColor.is("Custom")));
    protected final NumberSetting tracerWidth = add(new NumberSetting("Tracer Width", "Line thickness at 1080p (scales with resolution)", 1.5, 0.5, 5, 0.25)
            .suffix("px").visible(tracers::get));
    protected final NumberSetting tracerGlow = add(new NumberSetting("Tracer Glow", "How strong the soft glow round each line is", 40, 0, 150, 1)
            .suffix("%").visible(tracers::get));
    protected final BooleanSetting pulses = add(new BooleanSetting("Light Pulses", "Pulses of light travel along each line to its block", false)
            .visible(tracers::get));
    protected final NumberSetting pulseSpeed = add(new NumberSetting("Pulse Speed", "How fast the pulses travel", 1, 0.25, 3, 0.05)
            .suffix("x").visible(() -> tracers.get() && pulses.get()));
    protected final NumberSetting maxTracers = add(new NumberSetting("Max Tracers", "At most this many lines, nearest first",
            500, 1, BlockEspRenderer.MAX_TRACERS, 1).visible(tracers::get));

    // ---- bloom
    protected final BooleanSetting espBloom = add(new BooleanSetting("ESP Bloom", "The boxes glow, their light bleeding out round them", true));
    protected final BooleanSetting tracerBloom = add(new BooleanSetting("Tracer Bloom", "The tracers glow the same way", true));
    protected final NumberSetting bloomStrength = add(new NumberSetting("Bloom Strength", "How bright the glow is", 100, 10, 300, 5)
            .suffix("%").visible(this::bloomOn));
    protected final NumberSetting bloomSize = add(new NumberSetting("Bloom Size", "How far the glow spreads", 1.5, 0.5, 4, 0.1)
            .suffix("x").visible(this::bloomOn));

    protected final List<SettingSection> sections;

    // ---- scanning, on the client thread except where it says
    private record Hits(WorldChunk chunk, Long2ObjectOpenHashMap<Block> blocks) {
    }

    private ExecutorService worker;
    private final Long2ObjectOpenHashMap<Hits> chunks = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    /** Bumped by a block change in a chunk being scanned, so that scan's result is thrown away. */
    private final Long2IntOpenHashMap versions = new Long2IntOpenHashMap();
    private long session;
    private ClientWorld world;
    /** The picked blocks, as a set the worker reads. Replaced whole when the pick changes. */
    private volatile Set<Block> targets = Set.of();
    private int[] offsetsX = new int[0], offsetsZ = new int[0];
    private int offsetsRadius = -1;
    private int ticks, listTick;
    private long listNanos;
    private boolean dirty;

    /** What is drawn: the nearest wanted blocks within the limits, nearest first. */
    private long[] shown = new long[0];
    private Block[] shownBlocks = new Block[0];
    private int shownCount;
    /** Which of the shown blocks get a tracer: indices into {@link #shown}, nearest first. */
    private int[] tracerTargets = new int[0];
    private int tracerTargetCount;

    private final Map<Block, Box> shapes = new IdentityHashMap<>();
    private final long start = System.nanoTime();

    public BlockESP() {
        this("Block ESP", "Boxes round the blocks you pick through walls, with glowing tracers, bloom and a Y limit");
    }

    /** For the modules built on this one (Storage ESP, Hole ESP): the same settings, their own name. */
    protected BlockESP(String name, String description) {
        super(name, description, Category.VISUALS);
        if (getClass() == BlockESP.class && instance == null) instance = this;
        resetBlocks();
        sections = List.of(
                SettingSection.of("Blocks", blocksButton),
                SettingSection.of("Y Level", yLimit, maxY),
                SettingSection.of("Look", range, style, colorMode, oneColor, fillOpacity, lineWidth, smoothEdges, throughWalls, maxBlocks),
                SettingSection.of("Tracers", tracers, tracerTo, tracerStart, tracerColor, tracerCustom, tracerWidth, tracerGlow, pulses, pulseSpeed, maxTracers),
                SettingSection.of("Bloom", espBloom, tracerBloom, bloomStrength, bloomSize));
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    /** The Range a new config starts with. */
    protected int defaultRange() {
        return 12;
    }

    /** The Y Limit a new config starts with. */
    protected String defaultYLimit() {
        return "Blocks";
    }

    /** Which blocks this module may find: everything but storage, which is Storage ESP's. */
    public boolean allows(Block block) {
        return block != Blocks.AIR && !isStorage(block);
    }

    @Override
    protected void onEnable() {
        // A few scanners side by side: each chunk is scanned on its own, from its own copy.
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        worker = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "Maro Block ESP");
            thread.setDaemon(true);
            return thread;
        });
        builtVersion = Integer.MIN_VALUE;
        clearScans();
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    protected void onDisable() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
        if (worker != null) worker.shutdownNow();
        worker = null;
        clearScans();
    }

    private void clearScans() {
        session++;
        chunks.clear();
        queued.clear();
        versions.clear();
        shownCount = 0;
        dirty = false;
    }

    // ---- what to look for ---------------------------------------------------------------------

    /** Rebuilds the wanted blocks when the pick changes, and starts the scan over. */
    private void refreshTargets() {
        int version = scanVersion();
        if (builtVersion == version) return;
        builtVersion = version;
        Set<Block> next = Collections.newSetFromMap(new IdentityHashMap<>());
        next.addAll(wantedBlocks());
        targets = next;
        clearScans();
    }

    /** Changes whenever what is looked for changes, so the scan starts over. */
    protected int scanVersion() {
        return pickedVersion;
    }

    /** The blocks a chunk section must hold to be scanned at all. */
    protected java.util.Collection<Block> wantedBlocks() {
        return picked.keySet();
    }

    protected int drawColorOf(Block block) {
        if (colorMode.is("One Color")) return oneColor.get() | 0xFF000000;
        return colorOf(block);
    }

    // ---- the pick, for BlockEspScreen -----------------------------------------------------------

    /** The picked blocks, in the order they were added. */
    public List<Block> pickedBlocks() {
        return new ArrayList<>(picked.keySet());
    }

    public boolean isPicked(Block block) {
        return picked.containsKey(block);
    }

    /** The block's own colour: the one chosen for it, or the one it would get if picked. */
    public int colorOf(Block block) {
        Integer color = picked.get(block);
        return color != null ? color | 0xFF000000 : defaultColor(block);
    }

    public void pick(Block block) {
        if (block == null || !allows(block) || picked.containsKey(block)) return;
        picked.put(block, defaultColor(block));
        pickedVersion++;
    }

    public void unpick(Block block) {
        if (picked.remove(block) != null) pickedVersion++;
    }

    public void setColor(Block block, int color) {
        if (picked.containsKey(block)) picked.put(block, color | 0xFF000000);
    }

    public void clearPicked() {
        if (picked.isEmpty()) return;
        picked.clear();
        pickedVersion++;
    }

    /** Back to the starting pick: spawners, ancient debris and diamond ore (chests and the like for Storage ESP). */
    public void resetBlocks() {
        picked.clear();
        for (Block block : Registries.BLOCK) {
            if (!allows(block)) continue;
            for (Group g : Group.values()) {
                if (g.on && g.matches.test(block)) {
                    picked.put(block, g.color);
                    break;
                }
            }
        }
        pickedVersion++;
    }

    /** The presets the picker offers: a name and the blocks it adds. */
    public List<Map.Entry<String, List<Block>>> presetList() {
        List<Map.Entry<String, List<Block>>> out = new ArrayList<>();
        out.add(Map.entry("Spawners", groupBlocks(Group.SPAWNERS)));
        out.add(Map.entry("Valuables", groupBlocks(Group.DEBRIS, Group.DIAMONDS, Group.EMERALDS)));
        out.add(Map.entry("All Ores", groupBlocks(Group.DIAMONDS, Group.EMERALDS, Group.GOLD, Group.IRON, Group.REDSTONE, Group.LAPIS, Group.DEBRIS)));
        return out;
    }

    /** Storage ESP's presets. */
    protected static List<Map.Entry<String, List<Block>>> storagePresets() {
        List<Map.Entry<String, List<Block>>> out = new ArrayList<>();
        out.add(Map.entry("Containers", groupBlocks(Group.CHESTS, Group.SHULKERS, Group.ENDER_CHESTS, Group.BARRELS)));
        out.add(Map.entry("Shulkers", groupBlocks(Group.SHULKERS)));
        out.add(Map.entry("Redstone", groupBlocks(Group.HOPPERS, Group.DISPENSERS)));
        out.add(Map.entry("All Storage", groupBlocks(Group.CHESTS, Group.SHULKERS, Group.ENDER_CHESTS, Group.BARRELS, Group.HOPPERS,
                Group.DISPENSERS, Group.FURNACES, Group.OTHER_STORAGE)));
        return out;
    }

    private static List<Block> groupBlocks(Group... groups) {
        List<Block> out = new ArrayList<>();
        for (Block block : Registries.BLOCK) {
            for (Group g : groups) {
                if (g.matches.test(block)) {
                    out.add(block);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * The colour a block starts with: its kind's if it is one Block ESP knows (spawners pink, diamond
     * ore cyan and so on), otherwise its map colour made bright enough to see through walls.
     */
    public static int defaultColor(Block block) {
        for (Group g : Group.values()) if (g.matches.test(block)) return g.color;
        int rgb = block.getDefaultMapColor().color;
        if (rgb == 0) {
            float hue = (Registries.BLOCK.getId(block).hashCode() & 0xFFFF) / 65535f;
            return ColorUtil.hsv(hue, 0.65f, 1f);
        }
        float[] hsv = ColorUtil.toHsv(0xFF000000 | rgb);
        if (hsv[1] < 0.08f) return 0xFFE6E9F0;
        return ColorUtil.hsv(hsv[0], Math.max(0.55f, hsv[1]), Math.max(0.9f, hsv[2]));
    }

    @Override
    public JsonObject saveExtra() {
        JsonObject data = super.saveExtra();
        JsonObject blocks = new JsonObject();
        picked.forEach((block, color) -> blocks.addProperty(Registries.BLOCK.getId(block).toString(),
                String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF)));
        data.add("blocks", blocks);
        return data;
    }

    @Override
    public void loadExtra(JsonObject data) {
        super.loadExtra(data);
        if (!data.has("blocks") || !data.get("blocks").isJsonObject()) return;
        Map<Block, Integer> loaded = new LinkedHashMap<>();
        for (var entry : data.getAsJsonObject("blocks").entrySet()) {
            Identifier id = Identifier.tryParse(entry.getKey());
            if (id == null || !Registries.BLOCK.containsId(id)) continue;
            Block block = Registries.BLOCK.get(id);
            // Storage picked here before Storage ESP existed is Storage ESP's now.
            if (!allows(block)) continue;
            int color = defaultColor(block);
            try {
                String hex = entry.getValue().getAsString().replace("#", "");
                color = (int) Long.parseLong(hex, 16) | 0xFF000000;
            } catch (RuntimeException ignored) {
                // A colour that does not read keeps the block's own.
            }
            loaded.put(block, color);
        }
        picked.clear();
        picked.putAll(loaded);
        pickedVersion++;
    }

    // ---- scanning -------------------------------------------------------------------------------

    @Override
    public void onTick() {
        if (!inGame() || worker == null) return;
        ticks++;
        syncWorld();
        refreshTargets();
        if (targets.isEmpty()) {
            shownCount = 0;
            return;
        }
        ChunkPos origin = mc.player.getChunkPos();
        int radius = range.getInt();
        if (radius != offsetsRadius) buildOffsets(radius);
        if (ticks % 10 == 0) prune(origin, radius);
        submit(origin);
        if (dirty || ticks - listTick >= 5) rebuildList();
    }

    private void syncWorld() {
        if (mc.world != world) {
            world = mc.world;
            clearScans();
        }
    }

    /**
     * A chunk just arrived from the server: scanned at once, before it is even drawn, so its blocks
     * and tracers show up with it instead of on a later sweep.
     */
    @EventHandler
    private void onChunkLoad(ChunkLoadEvent event) {
        if (!inGame() || worker == null) return;
        syncWorld();
        refreshTargets();
        if (targets.isEmpty()) return;
        WorldChunk chunk = event.chunk;
        ChunkPos at = chunk.getPos(), origin = mc.player.getChunkPos();
        int dx = at.x - origin.x, dz = at.z - origin.z, radius = range.getInt();
        if (dx * dx + dz * dz > (radius + 0.5) * (radius + 0.5)) return;
        long key = at.toLong();
        // One already being scanned is from before this load; the sweep scans this one when it is back.
        if (!queued.contains(key)) scan(chunk, key);
        chunkLoaded(chunk);
    }

    /** Chunk offsets within the radius, nearest first. */
    private void buildOffsets(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= (radius + 0.5) * (radius + 0.5)) offsets.add(new int[] {x, z, x * x + z * z});
            }
        }
        offsets.sort((a, b) -> Integer.compare(a[2], b[2]));
        offsetsX = new int[offsets.size()];
        offsetsZ = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++) {
            offsetsX[i] = offsets.get(i)[0];
            offsetsZ[i] = offsets.get(i)[1];
        }
        offsetsRadius = radius;
    }

    protected WorldChunk loaded(int x, int z) {
        return mc.world != null && mc.world.getChunkManager().isChunkLoaded(x, z) ? mc.world.getChunk(x, z) : null;
    }

    /** Sends the nearest loaded chunks not yet scanned (or reloaded since) to the worker. */
    private void submit(ChunkPos origin) {
        int submitted = 0;
        for (int i = 0; i < offsetsX.length && submitted < PER_TICK && queued.size() < QUEUE_LIMIT; i++) {
            int cx = origin.x + offsetsX[i], cz = origin.z + offsetsZ[i];
            long key = ChunkPos.toLong(cx, cz);
            if (queued.contains(key)) continue;
            WorldChunk chunk = loaded(cx, cz);
            if (chunk == null) continue;
            Hits hits = chunks.get(key);
            if (hits != null && hits.chunk() == chunk) continue;
            scan(chunk, key);
            submitted++;
        }
    }

    /** A scan of one chunk, made ready on the game thread and run on a worker. */
    protected interface ChunkScan {
        Long2ObjectOpenHashMap<Block> run();
    }

    protected void scan(WorldChunk chunk, long key) {
        ChunkScan job = prepareScan(chunk, targets);
        if (job == null) {
            Hits old = chunks.put(key, new Hits(chunk, new Long2ObjectOpenHashMap<>(0)));
            if (old != null && !old.blocks().isEmpty()) dirty = true;
            return;
        }
        long scanSession = session;
        int version = versions.get(key);
        queued.add(key);
        worker.submit(() -> {
            Long2ObjectOpenHashMap<Block> found;
            try {
                found = job.run();
            } catch (RuntimeException e) {
                Maro.LOGGER.warn(getName() + " could not scan a chunk", e);
                found = new Long2ObjectOpenHashMap<>(0);
            }
            Long2ObjectOpenHashMap<Block> result = found;
            mc.execute(() -> publish(key, chunk, result, scanSession, version));
        });
    }

    /**
     * Copies what the worker needs of a chunk (only the sections that hold a wanted block at all) and
     * returns the scan of it, or null when there is nothing in it to find.
     */
    protected ChunkScan prepareScan(WorldChunk chunk, Set<Block> wanted) {
        ChunkSection[] sections = chunk.getSectionArray();
        ChunkSection[] copies = new ChunkSection[sections.length];
        boolean any = false;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            // The palette says whether a section holds a wanted block at all, without its blocks.
            if (section != null && !section.isEmpty() && section.hasAny(state -> wanted.contains(state.getBlock()))) {
                copies[i] = section.copy();
                any = true;
            }
        }
        if (!any) return null;
        int bottom = chunk.getBottomSectionCoord();
        int startX = chunk.getPos().getStartX(), startZ = chunk.getPos().getStartZ();
        return () -> {
            Long2ObjectOpenHashMap<Block> found = new Long2ObjectOpenHashMap<>();
            for (int i = 0; i < copies.length; i++) {
                ChunkSection section = copies[i];
                if (section == null) continue;
                int baseY = (bottom + i) << 4;
                Block last = null;
                boolean lastWanted = false;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            Block block = section.getBlockState(x, y, z).getBlock();
                            if (block != last) {
                                last = block;
                                lastWanted = wanted.contains(block);
                            }
                            if (lastWanted) found.put(BlockPos.asLong(startX + x, baseY + y, startZ + z), block);
                        }
                    }
                }
            }
            return found;
        };
    }

    /** Throws away what was found in a chunk and scans it again (now, if it is loaded). */
    protected void rescan(long key) {
        if (queued.contains(key)) {
            versions.addTo(key, 1);
            Hits old = chunks.remove(key);
            if (old != null && !old.blocks().isEmpty()) dirty = true;
            return;
        }
        Hits old = chunks.remove(key);
        if (old != null && !old.blocks().isEmpty()) dirty = true;
        WorldChunk chunk = loaded(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
        if (chunk != null && worker != null && !targets.isEmpty()) scan(chunk, key);
    }

    /** A chunk was loaded and has been sent for scanning; for modules that also look at its neighbours. */
    protected void chunkLoaded(WorldChunk chunk) {
    }

    /** A block changed; true when the module has dealt with it itself. */
    protected boolean blockChanged(BlockUpdateEvent event) {
        return false;
    }

    /** A chunk's scan was taken; for modules that tell you about new finds. */
    protected void published(long key, Long2ObjectOpenHashMap<Block> found) {
    }

    private void publish(long key, WorldChunk chunk, Long2ObjectOpenHashMap<Block> found, long scanSession, int version) {
        if (scanSession != session) return;
        queued.remove(key);
        if (worker == null || mc.world == null) return;
        // Reloaded since, or a block changed while it was scanned: it is scanned again.
        if (loaded(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key)) != chunk || versions.get(key) != version) return;
        chunks.put(key, new Hits(chunk, found));
        if (!found.isEmpty()) dirty = true;
        published(key, found);
    }

    /** Drops chunks out of range, unloaded or replaced. */
    private void prune(ChunkPos origin, int radius) {
        var it = chunks.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<Hits> entry = it.next();
            long key = entry.getLongKey();
            int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
            if (Math.max(Math.abs(cx - origin.x), Math.abs(cz - origin.z)) > radius + 1 || loaded(cx, cz) != entry.getValue().chunk()) {
                if (!entry.getValue().blocks().isEmpty()) dirty = true;
                it.remove();
                versions.remove(key);
            }
        }
    }

    /** Keeps the list up to date as blocks are placed and broken, without scanning again. */
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (worker == null || blockChanged(event)) return;
        Set<Block> wanted = targets;
        boolean was = wanted.contains(event.oldState.getBlock()), now = wanted.contains(event.newState.getBlock());
        if (!was && !now) return;
        long key = ChunkPos.toLong(event.pos.getX() >> 4, event.pos.getZ() >> 4);
        if (queued.contains(key)) {
            versions.addTo(key, 1);
            chunks.remove(key);
            return;
        }
        Hits hits = chunks.get(key);
        if (hits == null) return;
        if (now) hits.blocks().put(event.pos.asLong(), event.newState.getBlock());
        else hits.blocks().remove(event.pos.asLong());
        dirty = true;
    }

    /** The nearest wanted blocks within range and the Y limit, at most Max Blocks of them. */
    private void rebuildList() {
        listTick = ticks;
        listNanos = System.nanoTime();
        dirty = false;
        double px = mc.player.getX(), py = mc.player.getEyeY(), pz = mc.player.getZ();
        double reach = (range.get() + 0.5) * 16, reachSq = reach * reach;
        boolean limited = yLimit.is("Blocks");
        int top = maxY.getInt();

        int n = 0;
        for (Hits hits : chunks.values()) n += hits.blocks().size();
        long[] positions = new long[n];
        Block[] blocks = new Block[n];
        long[] order = new long[n];
        int found = 0;
        for (Hits hits : chunks.values()) {
            for (Long2ObjectMap.Entry<Block> entry : hits.blocks().long2ObjectEntrySet()) {
                long pos = entry.getLongKey();
                int y = BlockPos.unpackLongY(pos);
                if (limited && y > top) continue;
                double dx = BlockPos.unpackLongX(pos) + 0.5 - px, dy = y + 0.5 - py, dz = BlockPos.unpackLongZ(pos) + 0.5 - pz;
                double distanceSq = dx * dx + dy * dy + dz * dz;
                if (distanceSq > reachSq) continue;
                positions[found] = pos;
                blocks[found] = entry.getValue();
                // Non-negative floats sort as their bits do, so distance and index pack into one long.
                order[found] = (long) Float.floatToIntBits((float) distanceSq) << 32 | found;
                found++;
            }
        }
        Arrays.sort(order, 0, found);
        int keep = Math.min(found, maxBlocks.getInt());
        if (shown.length < keep) {
            shown = new long[keep];
            shownBlocks = new Block[keep];
        }
        for (int i = 0; i < keep; i++) {
            int at = (int) order[i];
            shown[i] = positions[at];
            shownBlocks[i] = blocks[at];
        }
        shownCount = keep;
        pickTracerTargets();
    }

    /**
     * The blocks the tracers go to. Each Block: the nearest ones. Each Group: blocks of one kind
     * within three blocks of each other are a group, and each group gets one line, to its nearest
     * block, so a row of chests is one line and not twenty.
     */
    private void pickTracerTargets() {
        int limit = Math.min(maxTracers.getInt(), BlockEspRenderer.MAX_TRACERS);
        if (tracerTargets.length < limit) tracerTargets = new int[limit];
        int count = 0;
        if (tracerTo.is("Each Block")) {
            for (int i = 0; i < shownCount && count < limit; i++) tracerTargets[count++] = i;
            tracerTargetCount = count;
            return;
        }
        // Union-find over the shown blocks, neighbours found through 4-block cells.
        int[] parent = new int[shownCount];
        Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntArrayList> cells = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < shownCount; i++) {
            parent[i] = i;
            long pos = shown[i];
            int cx = BlockPos.unpackLongX(pos) >> 2, cy = BlockPos.unpackLongY(pos) >> 2, cz = BlockPos.unpackLongZ(pos) >> 2;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        var cell = cells.get(BlockPos.asLong(cx + dx, cy + dy, cz + dz));
                        if (cell == null) continue;
                        for (int k = 0; k < cell.size(); k++) {
                            int j = cell.getInt(k);
                            if (shownBlocks[j] != shownBlocks[i] && drawColorOf(shownBlocks[j]) != drawColorOf(shownBlocks[i])) continue;
                            long other = shown[j];
                            if (Math.abs(BlockPos.unpackLongX(other) - BlockPos.unpackLongX(pos)) <= 3
                                    && Math.abs(BlockPos.unpackLongY(other) - BlockPos.unpackLongY(pos)) <= 3
                                    && Math.abs(BlockPos.unpackLongZ(other) - BlockPos.unpackLongZ(pos)) <= 3) {
                                union(parent, i, j);
                            }
                        }
                    }
                }
            }
            cells.computeIfAbsent(BlockPos.asLong(cx, cy, cz), k -> new it.unimi.dsi.fastutil.ints.IntArrayList()).add(i);
        }
        // The shown blocks are nearest first, so a group's first member is its nearest.
        boolean[] taken = new boolean[shownCount];
        for (int i = 0; i < shownCount && count < limit; i++) {
            int root = find(parent, i);
            if (taken[root]) continue;
            taken[root] = true;
            tracerTargets[count++] = i;
        }
        tracerTargetCount = count;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[Math.max(ra, rb)] = Math.min(ra, rb);
    }

    // ---- drawing --------------------------------------------------------------------------------

    /** Draws the boxes, queues the tracers and, with bloom on, fills the glow target. */
    public void render(Renderer3D renderer) {
        // Scans finished since the last tick are drawn this frame, not on the next tick.
        if (dirty && worker != null && inGame() && System.nanoTime() - listNanos >= REBUILD_NANOS) rebuildList();
        if (!inGame() || shownCount == 0) return;
        if (yLimit.is("You") && mc.player.getY() > maxY.get()) return;

        boolean boxGlow = espBloom.get(), lineGlow = tracerBloom();
        if (boxGlow || lineGlow) BlockEspRenderer.prepareGlow(this);
        renderer.throughWalls(throughWalls.get());
        renderer.lineWidth(lineWidth.getFloat());
        ShapeMode mode = style.is("Outline") ? ShapeMode.Lines : style.is("Fill") ? ShapeMode.Sides : ShapeMode.Both;
        int fillAlpha = Math.round(fillOpacity.getFloat() * 2.55f);
        int glowFillAlpha = Math.min(255, Math.round(fillAlpha * 1.6f) + 30);
        Vec3d camera = renderer.camera();
        double reach = range.get() * 16;
        float seconds = seconds();
        float width = lineWidth.getFloat();
        boolean soft = smoothEdges.get() && mode.lines();

        // Only boxes on screen are drawn; the nearest of them also get a soft edge and a glow.
        int visible = 0;
        for (int i = 0; i < shownCount; i++) {
            long pos = shown[i];
            int x = BlockPos.unpackLongX(pos), y = BlockPos.unpackLongY(pos), z = BlockPos.unpackLongZ(pos);
            Block block = shownBlocks[i];
            Box shape = shapeOf(block);
            double x1 = x + shape.minX, y1 = y + shape.minY, z1 = z + shape.minZ;
            double x2 = x + shape.maxX, y2 = y + shape.maxY, z2 = z + shape.maxZ;
            if (!BlockEspRenderer.inView(x1, y1, z1, x2, y2, z2, camera)) continue;
            int near = visible++;
            int color = drawColorOf(block);
            Color line = new Color(color | 0xFF000000);
            if (soft && near < SOFT_EDGES) {
                // A wider, faint line under the crisp one feathers its edge.
                renderer.lineWidth(Math.min(4f, width * 2.4f));
                renderer.box(x1, y1, z1, x2, y2, z2, line, new Color(ColorUtil.withAlpha(color, 0x46)), ShapeMode.Lines, 0);
                renderer.lineWidth(width);
            }
            renderer.box(x1, y1, z1, x2, y2, z2, new Color(ColorUtil.withAlpha(color, fillAlpha)), line, mode, 0);
            if (boxGlow && near < GLOWING) {
                renderer.glow(true);
                renderer.box(x1, y1, z1, x2, y2, z2, new Color(ColorUtil.withAlpha(color, glowFillAlpha)), line, mode, 0);
                renderer.glow(false);
            }
        }
        // Tracers, to the very blocks drawn this frame: they come and go with the boxes.
        if (tracers.get()) {
            for (int t = 0; t < tracerTargetCount; t++) {
                int i = tracerTargets[t];
                if (i >= shownCount) continue;
                long pos = shown[i];
                Box shape = shapeOf(shownBlocks[i]);
                double cx = BlockPos.unpackLongX(pos) + (shape.minX + shape.maxX) / 2;
                double cy = BlockPos.unpackLongY(pos) + (shape.minY + shape.maxY) / 2;
                double cz = BlockPos.unpackLongZ(pos) + (shape.minZ + shape.maxZ) / 2;
                double distance = Math.sqrt(mc.player.squaredDistanceTo(cx, cy, cz));
                float weight = (float) Math.max(0.4, 1 - distance / Math.max(1, reach) * 0.6);
                int color = drawColorOf(shownBlocks[i]);
                BlockEspRenderer.addTracer(this, cx, cy, cz, camera, tracerColorOf(color, distance / Math.max(1, reach), t, seconds), weight);
            }
        }
        renderer.throughWalls(true);
        renderer.lineWidth(1.5f);
    }

    private int tracerColorOf(int blockColor, double nearness, int index, float seconds) {
        return switch (tracerColor.get()) {
            case "Distance" -> ColorUtil.lerp(0xFFFF3B3B, 0xFF3BFF7A, (float) Math.min(1, nearness));
            case "Rainbow" -> ColorUtil.hsv((seconds * 0.15f + index * 0.07f) % 1f, 0.75f, 1f);
            case "Custom" -> tracerCustom.get() | 0xFF000000;
            default -> blockColor;
        };
    }

    /** The block's outline, for chests and other blocks smaller than a whole block; a full block otherwise. */
    protected Box shapeOf(Block block) {
        return shapes.computeIfAbsent(block, b -> {
            try {
                VoxelShape shape = b.getDefaultState().getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
                if (!shape.isEmpty()) return shape.getBoundingBox();
            } catch (RuntimeException ignored) {
                // Some shapes need a real world round them; those get a whole block.
            }
            return new Box(0, 0, 0, 1, 1, 1);
        });
    }

    // ---- for the renderer -----------------------------------------------------------------------

    public boolean tracersFromBottom() {
        return tracerStart.is("Bottom");
    }

    /** Tracer thickness in pixels for a frame {@code height} pixels tall. */
    public float tracerWidthPx(int height) {
        return tracerWidth.getFloat() * Math.max(1, height) / 1080f;
    }

    public float tracerHalo() {
        return tracerGlow.getFloat() / 100f;
    }

    public boolean tracerPackets() {
        return pulses.get();
    }

    public float packetSpeed() {
        return pulseSpeed.getFloat();
    }

    public boolean tracerBloom() {
        return tracers.get() && tracerBloom.get();
    }

    public boolean bloomOn() {
        return espBloom.get() || tracerBloom.get();
    }

    public float bloomStrength() {
        return bloomStrength.getFloat() / 100f;
    }

    public float bloomSize() {
        return bloomSize.getFloat();
    }

    /** Seconds since the module was made, kept small so the shaders' float maths stays exact. */
    public float seconds() {
        return (float) ((System.nanoTime() - start) / 1e9 % 3600.0);
    }

    public void renderFailed(RuntimeException e) {
        Maro.LOGGER.error("Block ESP could not render", e);
        Notifications.push(getName(), "Rendering failed - see the log. Turned off.", Notifications.Type.ERROR);
        setEnabled(false);
    }

    // ---- for tests ------------------------------------------------------------------------------

    /** The blocks drawn now, nearest first. */
    public List<BlockPos> shownPositions() {
        List<BlockPos> out = new ArrayList<>(shownCount);
        for (int i = 0; i < shownCount; i++) out.add(BlockPos.fromLong(shown[i]));
        return out;
    }

    /** Whether nothing is waiting to be scanned. */
    public boolean settled() {
        return queued.isEmpty();
    }

    public static BlockESP instance() {
        return instance;
    }
}
