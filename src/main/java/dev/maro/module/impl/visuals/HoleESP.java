package dev.maro.module.impl.visuals;

import dev.maro.gui.notification.Notifications;
import dev.maro.runtime.events.world.BlockUpdateEvent;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Hole ESP: pockets of air walled and floored with bedrock, the kind players leave when they mine at
 * bedrock level or through the Nether roof, found far out (up to 64 chunks) the moment their chunks
 * arrive. A hole is one block (1x1), two side by side (2x1) or a 2x2, every side of it bedrock; with
 * Obsidian Holes on, obsidian and other blast-proof blocks count as walls too, and a hole of both is
 * Mixed. Drawn with all of Block ESP's look: boxes, tracers and bloom.
 */
public class HoleESP extends BlockESP {
    /** Stand-ins for the kinds of hole in the found lists, so each kind keeps its own colour. */
    private static final Block BEDROCK_HOLE = Blocks.BEDROCK, OBSIDIAN_HOLE = Blocks.OBSIDIAN, MIXED_HOLE = Blocks.CRYING_OBSIDIAN;
    /** Blast-proof blocks that make an obsidian hole. */
    private static final Set<Block> HARD = Set.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK,
            Blocks.RESPAWN_ANCHOR, Blocks.ENDER_CHEST, Blocks.ANCIENT_DEBRIS);
    private static final int[] DX = {-1, 1, 0, 0}, DZ = {0, 0, -1, 1};

    private final BooleanSetting obsidian = add(new BooleanSetting("Obsidian Holes", "Holes walled with obsidian and other blast-proof blocks as well (Mixed: some of each)", false));
    private final BooleanSetting doubles = add(new BooleanSetting("Double Holes", "Two blocks side by side (2x1)", true));
    private final BooleanSetting quads = add(new BooleanSetting("Quad Holes", "Four blocks in a square (2x2)", true));
    private final NumberSetting height = add(new NumberSetting("Height", "Air the hole must have: 1 for any pocket, 2 to stand in", 1, 1, 3, 1).suffix(" blocks"));
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "A notification when new bedrock holes are found", false));
    private final NumberSetting boxHeight = add(new NumberSetting("Box Height", "How tall the boxes are, from a flat pad to a whole block", 1, 0.05, 1, 0.05));
    private final ColorSetting bedrockColor = add(new ColorSetting("Bedrock Color", "Holes walled with bedrock", 0xFF3DDC97));
    private final ColorSetting obsidianColor = add(new ColorSetting("Obsidian Color", "Holes walled with obsidian", 0xFFFF4D5E).visible(obsidian::get));
    private final ColorSetting mixedColor = add(new ColorSetting("Mixed Color", "Holes walled with both", 0xFFFFC23D).visible(obsidian::get));

    private final List<SettingSection> holeSections;

    /** Chunks scanned while a neighbour was not loaded, so holes on that edge are looked at again once it is. */
    private final LongOpenHashSet incomplete = new LongOpenHashSet();
    /** Chunks to scan again after blocks changed in them, once a tick however many changed. */
    private final LongOpenHashSet changed = new LongOpenHashSet();
    private final LongOpenHashSet notified = new LongOpenHashSet();
    private ClientWorld notifiedWorld;
    private long lastNotified;

    public HoleESP() {
        super("Hole ESP", "Holes walled and floored with bedrock (or obsidian), found far out, with boxes, tracers and bloom");
        holeSections = List.of(
                SettingSection.of("Holes", obsidian, doubles, quads, height, notify),
                SettingSection.of("Look", range, yLimit, maxY, style, boxHeight, bedrockColor, obsidianColor, mixedColor,
                        fillOpacity, lineWidth, throughWalls, maxBlocks, smoothEdges),
                SettingSection.of("Tracers", tracers, tracerTo, tracerStart, tracerColor, tracerCustom, tracerWidth, tracerGlow, pulses, pulseSpeed, maxTracers),
                SettingSection.of("Bloom", espBloom, tracerBloom, bloomStrength, bloomSize));
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return holeSections;
    }

    @Override
    protected int defaultRange() {
        return 32;
    }

    @Override
    protected String defaultYLimit() {
        return "Off";
    }

    /** Nothing is picked: what makes a hole is set by the switches above. */
    @Override
    public boolean allows(Block block) {
        return false;
    }

    @Override
    protected int scanVersion() {
        return Objects.hash(obsidian.get(), doubles.get(), quads.get(), height.getInt());
    }

    @Override
    protected Collection<Block> wantedBlocks() {
        Set<Block> walls = new java.util.HashSet<>();
        walls.add(Blocks.BEDROCK);
        if (obsidian.get()) walls.addAll(HARD);
        return walls;
    }

    @Override
    protected int drawColorOf(Block block) {
        int color = block == OBSIDIAN_HOLE ? obsidianColor.get() : block == MIXED_HOLE ? mixedColor.get() : bedrockColor.get();
        return color | 0xFF000000;
    }

    @Override
    protected Box shapeOf(Block block) {
        return new Box(0, 0, 0, 1, boxHeight.get(), 1);
    }

    // ---- finding holes --------------------------------------------------------------------------

    @Override
    protected ChunkScan prepareScan(WorldChunk chunk, Set<Block> walls) {
        ChunkSection[] own = chunk.getSectionArray();
        int n = own.length;
        boolean[] wallSection = new boolean[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            ChunkSection section = own[i];
            if (section != null && !section.isEmpty() && section.hasAny(state -> walls.contains(state.getBlock()))) {
                wallSection[i] = true;
                any = true;
            }
        }
        long key = chunk.getPos().toLong();
        if (!any) {
            incomplete.remove(key);
            return null;
        }
        // Copies of the sections with walls, the ones just above and below (floors and headroom), and
        // the same sections of the four neighbours (walls on the chunk's edges).
        ChunkSection[] copies = new ChunkSection[n];
        for (int i = 0; i < n; i++) {
            if (!wallSection[i]) continue;
            for (int j = Math.max(0, i - 1); j <= Math.min(n - 1, i + 1); j++) {
                if (copies[j] == null && own[j] != null) copies[j] = own[j].copy();
            }
        }
        ChunkPos at = chunk.getPos();
        ChunkSection[][] side = new ChunkSection[4][];
        boolean complete = true;
        for (int d = 0; d < 4; d++) {
            WorldChunk near = loaded(at.x + DX[d], at.z + DZ[d]);
            if (near == null) {
                complete = false;
                continue;
            }
            ChunkSection[] theirs = near.getSectionArray();
            side[d] = new ChunkSection[n];
            for (int i = 0; i < n && i < theirs.length; i++) {
                if (wallSection[i] && theirs[i] != null) side[d][i] = theirs[i].copy();
            }
        }
        if (complete) incomplete.remove(key);
        else incomplete.add(key);

        Set<Block> wallSet = Set.copyOf(walls);
        int bottom = chunk.getBottomSectionCoord();
        int startX = at.getStartX(), startZ = at.getStartZ();
        int need = height.getInt();
        boolean allowDoubles = doubles.get(), allowQuads = quads.get();
        return () -> new Finder(copies, side, wallSection, wallSet, bottom, startX, startZ, need, allowDoubles, allowQuads).find();
    }

    /** The search of one chunk, on a worker, over its copied sections. */
    private record Finder(ChunkSection[] copies, ChunkSection[][] side, boolean[] wallSection, Set<Block> walls,
                          int bottom, int startX, int startZ, int need, boolean doubles, boolean quads) {

        Long2ObjectOpenHashMap<Block> find() {
            Long2ObjectOpenHashMap<Block> found = new Long2ObjectOpenHashMap<>();
            int[] cx = new int[5], cz = new int[5];
            for (int i = 0; i < wallSection.length; i++) {
                if (!wallSection[i]) continue;
                int baseY = (bottom + i) << 4;
                boolean[] seen = new boolean[4096];
                for (int ly = 0; ly < 16; ly++) {
                    int y = baseY + ly;
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            int index = (ly << 8) | (lz << 4) | lx;
                            if (seen[index] || !candidate(lx, y, lz)) continue;
                            // The pocket this cell is part of, given up past four cells.
                            int size = 1;
                            cx[0] = lx;
                            cz[0] = lz;
                            boolean ok = true;
                            for (int q = 0; q < size && ok; q++) {
                                for (int d = 0; d < 4; d++) {
                                    int nx = cx[q] + DX[d], nz = cz[q] + DZ[d];
                                    if (member(cx, cz, size, nx, nz) || !candidate(nx, y, nz)) continue;
                                    if (size == 4) {
                                        ok = false;
                                        break;
                                    }
                                    cx[size] = nx;
                                    cz[size] = nz;
                                    size++;
                                }
                            }
                            for (int q = 0; q < size; q++) {
                                if (cx[q] >= 0 && cx[q] < 16 && cz[q] >= 0 && cz[q] < 16) seen[(ly << 8) | (cz[q] << 4) | cx[q]] = true;
                            }
                            if (!ok || !shapeOk(cx, cz, size)) continue;
                            // Recorded by the chunk holding its first cell, so a hole across two chunks is found once.
                            int first = 0;
                            for (int q = 1; q < size; q++) {
                                if (cx[q] < cx[first] || (cx[q] == cx[first] && cz[q] < cz[first])) first = q;
                            }
                            if (cx[first] < 0 || cx[first] > 15 || cz[first] < 0 || cz[first] > 15) continue;
                            // Every side round it a wall, bedrock or the hard kind.
                            boolean bedrock = false, hard = false, walled = true;
                            for (int q = 0; q < size && walled; q++) {
                                int floor = wallType(at(cx[q], y - 1, cz[q]));
                                if (floor == 1) bedrock = true;
                                else hard = true;
                                for (int d = 0; d < 4; d++) {
                                    int nx = cx[q] + DX[d], nz = cz[q] + DZ[d];
                                    if (member(cx, cz, size, nx, nz)) continue;
                                    int type = wallType(at(nx, y, nz));
                                    if (type <= 0) {
                                        walled = false;
                                        break;
                                    }
                                    if (type == 1) bedrock = true;
                                    else hard = true;
                                }
                            }
                            if (!walled) continue;
                            Block kind = !hard ? BEDROCK_HOLE : bedrock ? MIXED_HOLE : OBSIDIAN_HOLE;
                            for (int q = 0; q < size; q++) found.put(BlockPos.asLong(startX + cx[q], y, startZ + cz[q]), kind);
                        }
                    }
                }
            }
            return found;
        }

        private boolean shapeOk(int[] cx, int[] cz, int size) {
            if (size == 1) return true;
            if (size == 2) return doubles;
            if (size != 4 || !quads) return false;
            int minX = Math.min(Math.min(cx[0], cx[1]), Math.min(cx[2], cx[3])), maxX = Math.max(Math.max(cx[0], cx[1]), Math.max(cx[2], cx[3]));
            int minZ = Math.min(Math.min(cz[0], cz[1]), Math.min(cz[2], cz[3])), maxZ = Math.max(Math.max(cz[0], cz[1]), Math.max(cz[2], cz[3]));
            return maxX - minX == 1 && maxZ - minZ == 1;
        }

        private static boolean member(int[] cx, int[] cz, int size, int x, int z) {
            for (int q = 0; q < size; q++) if (cx[q] == x && cz[q] == z) return true;
            return false;
        }

        /** Air with room above it and a wall block for a floor. */
        private boolean candidate(int lx, int y, int lz) {
            if (!open(at(lx, y, lz))) return false;
            for (int k = 1; k < need; k++) if (!open(at(lx, y + k, lz))) return false;
            return wallType(at(lx, y - 1, lz)) > 0;
        }

        private static boolean open(BlockState state) {
            return state != null && state.isAir();
        }

        /** 1 for bedrock, 2 for the other walls, 0 for anything else, -1 when it is not known. */
        private int wallType(BlockState state) {
            if (state == null) return -1;
            Block block = state.getBlock();
            if (block == Blocks.BEDROCK) return 1;
            return walls.contains(block) ? 2 : 0;
        }

        /** The block at a spot relative to the chunk's corner, from the copies, or null when it was not copied. */
        private BlockState at(int lx, int y, int lz) {
            int i = (y >> 4) - bottom;
            if (i < 0 || i >= copies.length) return null;
            int ly = y & 15;
            boolean insideX = lx >= 0 && lx < 16, insideZ = lz >= 0 && lz < 16;
            if (insideX && insideZ) {
                ChunkSection section = copies[i];
                return section == null ? null : section.getBlockState(lx, ly, lz);
            }
            if (!insideX && !insideZ) return null;
            int d = lx < 0 ? 0 : lx > 15 ? 1 : lz < 0 ? 2 : 3;
            ChunkSection[] theirs = side[d];
            if (theirs == null || theirs[i] == null) return null;
            return theirs[i].getBlockState(lx & 15, ly, lz & 15);
        }
    }

    // ---- keeping up with the world --------------------------------------------------------------

    /** A chunk arrived: the neighbours that were scanned without it are looked at again. */
    @Override
    protected void chunkLoaded(WorldChunk chunk) {
        ChunkPos at = chunk.getPos();
        for (int d = 0; d < 4; d++) {
            long key = ChunkPos.toLong(at.x + DX[d], at.z + DZ[d]);
            if (incomplete.remove(key)) rescan(key);
        }
    }

    /** A block changed near walls: its chunk (and the next one, on an edge) is looked at again. */
    @Override
    protected boolean blockChanged(BlockUpdateEvent event) {
        BlockPos pos = event.pos;
        if (!nearWalls(pos)) return true;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4, lx = pos.getX() & 15, lz = pos.getZ() & 15;
        changed.add(ChunkPos.toLong(cx, cz));
        if (lx == 0) changed.add(ChunkPos.toLong(cx - 1, cz));
        if (lx == 15) changed.add(ChunkPos.toLong(cx + 1, cz));
        if (lz == 0) changed.add(ChunkPos.toLong(cx, cz - 1));
        if (lz == 15) changed.add(ChunkPos.toLong(cx, cz + 1));
        return true;
    }

    private boolean nearWalls(BlockPos pos) {
        WorldChunk chunk = loaded(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        Collection<Block> walls = wantedBlocks();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        for (int y : new int[] {pos.getY() - 1, pos.getY(), pos.getY() + 1}) {
            int i = (y >> 4) - bottom;
            if (i < 0 || i >= sections.length || sections[i] == null) continue;
            if (sections[i].hasAny(state -> walls.contains(state.getBlock()))) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        super.onTick();
        if (changed.isEmpty()) return;
        long[] keys = changed.toLongArray();
        changed.clear();
        for (long key : keys) rescan(key);
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        incomplete.clear();
        changed.clear();
    }

    /** Notify: a notification for bedrock holes not told about before, at most one a second and a half. */
    @Override
    protected void published(long key, Long2ObjectOpenHashMap<Block> found) {
        if (!notify.get() || found.isEmpty() || mc.player == null) return;
        if (mc.world != notifiedWorld) {
            notifiedWorld = mc.world;
            notified.clear();
        }
        int fresh = 0;
        long nearest = 0;
        double nearestSq = Double.MAX_VALUE;
        for (Long2ObjectMap.Entry<Block> entry : found.long2ObjectEntrySet()) {
            if (entry.getValue() != BEDROCK_HOLE || !notified.add(entry.getLongKey())) continue;
            fresh++;
            double dsq = mc.player.squaredDistanceTo(BlockPos.unpackLongX(entry.getLongKey()) + 0.5,
                    BlockPos.unpackLongY(entry.getLongKey()), BlockPos.unpackLongZ(entry.getLongKey()) + 0.5);
            if (dsq < nearestSq) {
                nearestSq = dsq;
                nearest = entry.getLongKey();
            }
        }
        long now = System.currentTimeMillis();
        if (fresh == 0 || now - lastNotified < 1500) return;
        lastNotified = now;
        String where = String.format(Locale.ROOT, "%d %d %d (%dm)", BlockPos.unpackLongX(nearest), BlockPos.unpackLongY(nearest),
                BlockPos.unpackLongZ(nearest), Math.round(Math.sqrt(nearestSq)));
        Notifications.push("Bedrock Hole", fresh == 1 ? "At " + where : fresh + " found, nearest at " + where, Notifications.Type.INFO, 5000);
    }
}
