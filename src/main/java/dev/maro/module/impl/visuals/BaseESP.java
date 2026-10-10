/* Adapted from BaseESP in the user-provided KryptonAvengersAddonLeak source (dev.dexter.kryptionians). Detection and shell matching retain the original algorithm; lifecycle, mappings, scanning snapshots, and rendering are ported to Maro 1.21.11. */
package dev.maro.module.impl.visuals;

import dev.maro.nathan.NameeProtectAddon;
import dev.maro.render.esp.DonutSignatureCatalog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import dev.maro.runtime.events.game.GameJoinedEvent;
import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.render.esp.Renderer3D;
import dev.maro.runtime.events.world.TickEvent.Post;
import dev.maro.render.esp.ShapeMode;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.ColorSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.events.world.BlockUpdateEvent;
import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.render.color.SettingColor;
import dev.maro.runtime.event.EventHandler;
import net.minecraft.util.Formatting;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;

public final class BaseESP extends Module implements HudElement {
  public enum ScanSpeed { Fast, Balanced, Eco, Custom }
  public enum DisplayStyle { Both, Outline, Filled }
  private static final int SCAN_PADDING = 4;
  private static final int SCAN_MIN_Y = -64;
  private static final int SCAN_MAX_Y = -1;
  private static final int HEIGHT_BUCKETS = 165;
  private static final int REGION_COLUMN_GAP = 5;
  private static final int CELL_SIZE_XZ = 6;
  private static final int OUTLIER_NEIGHBOR_RADIUS = 3;
  private static final int OUTLIER_MIN_NEIGHBORS = 2;
  private static final int OUTLINE_BRIDGE_GAP = 5;
  private static final int OUTLINE_SUPPORTED_BRIDGE_GAP = 10;
  private static final int OUTLINE_SUPPORT_RADIUS = 2;
  private static final int OUTLINE_SUPPORT_NEIGHBORS = 3;
  private static final int OUTLINE_TRIM_NEIGHBORS = 2;
  private static final int OUTLINE_CORNER_FILL_PASSES = 2;
  private static final int OUTLINE_REGION_PADDING = 3;
  private static final int OUTLINE_CLUSTER_GAP = 6;
  private static final int BASE_VERTICAL_ATTACH_GAP = 10;
  private static final int MIN_BASE_STORAGE_BLOCKS = 16;
  private static final int MIN_BASE_STORAGE_COLUMNS = 4;
  private static final int MIN_BASE_OUTLINE_COLUMNS = 6;
  private static final int MIN_BASE_FOOTPRINT_AREA = 24;
  private static final int MIN_BASE_STRUCTURE_BLOCKS = 24;
  private static final int MIN_BASE_WALL_HEIGHT = 3;
  private static final int ISOLATED_REGION_MIN_DISTANCE = 18;
  private static final double ISOLATED_REGION_SIZE_RATIO = 0.18;
  private static final int ANCHOR_STRUCTURE_RADIUS = 7;
  private static final int STORAGE_STRUCTURE_LINK_RADIUS = 18;
  private static final double MIN_CATALOG_SHELL_COLUMN_COVERAGE = 0.92;
  private static final int REGION_BUILDER_MERGE_GAP = 10;
  private static final int STORAGE_BUILDER_MERGE_GAP = 32;
  private static final int REGION_BUILDER_HEIGHT_GAP = 10;
  private static final int LOCAL_BASE_SUPPRESS_RADIUS = 40;
  private static final int REGION_BUILDER_MIN_PROJECTION_OVERLAP = 3;
  private static final double REGION_BUILDER_MIN_TYPE_OVERLAP = 0.35;
  private static final int STRUCTURE_ONLY_MIN_BLOCKS = 72;
  private static final int STRUCTURE_ONLY_MIN_COLUMNS = 20;
  private static final double STRUCTURE_ONLY_MIN_DENSITY = 0.18;
  private static final int STRUCTURE_ONLY_MAX_ASPECT_SKEW = 5;
  private static final int STRUCTURE_ONLY_MAX_ASPECT_PADDING = 10;
  private static final int ENGINEERED_COLUMN_MIN_BLOCKS = 2;
  private static final int MATCH_MAX_VOTE_COLUMNS = 96;
  private static final int MATCH_MAX_TRANSLATIONS = 6;
  private static final double MIN_HISTOGRAM_OVERLAP = 0.45;
  private static final double MIN_MATCH_SCORE = 0.72;
  private static final double MIN_MATCH_FAMILY_BLOCK_COVERAGE = 0.16;
  private static final double MIN_MATCH_FAMILY_COLUMN_COVERAGE = 0.14;
  private static final double MIN_MATCH_OBSERVED_COLUMN_COVERAGE = 0.58;
  private static final int MAX_MATCH_FAMILY_SIZE_DELTA = 220;
  private static final double CHUNK_SLAB_HEIGHT = 0.2;
  private static final int CHUNK_PILLAR_MIN_Y = -64;
  private static final int CHUNK_PILLAR_MAX_Y = -1;
  private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
  private final SettingGroup sgRender = this.settings.createGroup("Render");
  private final SettingGroup sgChunkMark = this.settings.createGroup("Chunk Mark");
  private final SettingGroup sgScan = this.settings.createGroup("Scanning");
  private final SettingGroup sgHud = this.settings.createGroup("Detector HUD");
  private final Setting<ScanSpeed> scanSpeed = sgScan.add(new EnumSetting.Builder<ScanSpeed>()
      .name("scan-speed").description("Fast prioritizes discoveries; Eco spreads scans over more ticks").defaultValue(ScanSpeed.Fast).build());
  private final Setting<Integer> scanRange = sgScan.add(new IntSetting.Builder().name("scan-radius")
      .description("Radius in chunks; scans only chunks the client has loaded").defaultValue(12).range(1,32).build());
  private final Setting<Integer> customChunks = sgScan.add(new IntSetting.Builder().name("chunks-per-tick")
      .defaultValue(16).range(1,32).visible(() -> scanSpeed.get() == ScanSpeed.Custom).build());
  private final Setting<Integer> customInterval = sgScan.add(new IntSetting.Builder().name("rescan-interval")
      .description("Ticks between routine scans; block changes get priority immediately").defaultValue(40).range(20,400)
      .visible(() -> scanSpeed.get() == ScanSpeed.Custom).build());
  private final Setting<Double> customBudget = sgScan.add(new DoubleSetting.Builder().name("scan-budget-ms")
      .description("Time budget for client-thread snapshots; one chunk can exceed this budget").defaultValue(2).range(.25,5)
      .visible(() -> scanSpeed.get() == ScanSpeed.Custom).build());
  private final Setting<Integer> alertCooldown = sgGeneral.add(new IntSetting.Builder().name("alert-cooldown")
      .description("Seconds before a rediscovered nearby base can alert again").defaultValue(120).range(0,600).build());
  private final Setting<Boolean> detectorHud = sgHud.add(new BoolSetting.Builder().name("detector-hud").defaultValue(true).build());
  private final Setting<Integer> hudEntries = sgHud.add(new IntSetting.Builder().name("hud-entries").defaultValue(3).range(1,5).build());
  private final Setting<Double> hudX = sgHud.add(new DoubleSetting.Builder().name("hud-x").defaultValue(2).range(0,100).build());
  private final Setting<Double> hudY = sgHud.add(new DoubleSetting.Builder().name("hud-y").defaultValue(25).range(0,100).build());
  private final Setting<Double> hudSize = sgHud.add(new DoubleSetting.Builder().name("hud-scale").defaultValue(.8).range(.5,2.5).build());
  private final Setting<Integer> minBlocks =
      this.sgGeneral.add(
          new IntSetting.Builder().name("min-blocks").description("Minimum storage and utility blocks in a built underground base").defaultValue(15).range(1, 4096).build());
  private final Setting<Boolean> chatAlerts =
      this.sgGeneral.add(new BoolSetting.Builder().name("chat-alerts").defaultValue(true).build());
  private final Setting<Boolean> soundAlerts =
      this.sgGeneral.add(new BoolSetting.Builder().name("sound-alerts").defaultValue(true).build());
  private final Setting<DisplayStyle> displayStyle = sgRender.add(new EnumSetting.Builder<DisplayStyle>()
      .name("display-style").defaultValue(DisplayStyle.Both).build());
  private final Setting<Double> outlineWidth = sgRender.add(new DoubleSetting.Builder().name("outline-width")
      .defaultValue(1.5).range(.5,4).visible(() -> displayStyle.get()!=DisplayStyle.Filled).build());
  private final Setting<SettingColor> boxColor =
      this.sgRender.add(
          new ColorSetting.Builder()
              .name("box-color")
              .defaultValue(new SettingColor(0, 255, 0, 100))
              .build());
  private final Setting<Integer> boxAlpha =
      this.sgRender.add(
          new IntSetting.Builder()
              .name("box-alpha")
              .defaultValue(50)
              .range(0, 255)
              .sliderRange(0, 255)
              .build());
  private final Setting<Boolean> chunkMark =
      this.sgChunkMark.add(
          new BoolSetting.Builder().name("chunk-mark").defaultValue(false).build());
  private final Setting<BaseESP.ChunkMarkMode> chunkMarkMode =
      this.sgChunkMark.add(
          new EnumSetting.Builder<BaseESP.ChunkMarkMode>()
              .name("chunk-mark-mode")
              .defaultValue(BaseESP.ChunkMarkMode.Pillar)
              .visible(() -> this.chunkMark.get())
              .build());
  private final Setting<SettingColor> chunkMarkColor =
      this.sgChunkMark.add(
          new ColorSetting.Builder()
              .name("chunk-mark-color")
              .defaultValue(new SettingColor(0, 255, 0, 45))
              .visible(() -> this.chunkMark.get())
              .build());
  private final Setting<Integer> chunkMarkOpacity =
      this.sgChunkMark.add(
          new IntSetting.Builder()
              .name("chunk-mark-opacity")
              .defaultValue(45)
              .range(0, 255)
              .sliderRange(0, 255)
              .visible(() -> this.chunkMark.get())
              .build());
  private final Map<ChunkPos, BaseESP.ChunkScan> chunkScans = new ConcurrentHashMap<>();
  private final Map<ChunkPos, Integer> lastScanTickByChunk = new ConcurrentHashMap<>();
  private final List<BaseESP.BaseRegion> baseRegions = new ArrayList<>();
  private final Map<ChunkPos, Long> queuedChunks = new ConcurrentHashMap<>();
  // Client-thread queues and versions coalesce block bursts without submitting unlimited work.
  private final ArrayDeque<ChunkPos> urgentChunks = new ArrayDeque<>();
  private final Set<ChunkPos> urgentSet = new HashSet<>();
  private final Map<ChunkPos, Long> changeVersions = new HashMap<>();
  private final ArrayDeque<AlertStamp> alertHistory = new ArrayDeque<>();
  private long nextChangeVersion;
  private long nextScanId;
  private final Set<Long> alertedRegions = ConcurrentHashMap.newKeySet();
  private final List<BaseESP.ChunkOffset> scanOrder = new ArrayList<>();
  private DonutSignatureCatalog donutCatalog;
  private volatile long session;
  private ExecutorService scanExecutor;
  private ClientWorld lastWorld;
  private ChunkPos lastOrigin;
  private ChunkPos markedChunk;
  private int lastRadius = -1;
  private int lastMinBlocks = -1;
  private int scanIndex;
  private int tickCounter;
  private int lastRebuildRequestTick;
  private volatile long scanRevision;
  private volatile boolean regionsDirty;
  private volatile boolean rebuildQueued;
  private volatile BaseESP.RebuildResult pendingRebuild;

  public BaseESP() {
    super(NameeProtectAddon.CATEGORY, "base-esp", "Detects underground bases.");
  }

  /** Immutable bounds for the currently detected underground shells. */
  public List<net.minecraft.util.math.Box> detectedBounds() {
    return this.baseRegions.stream().map(region -> new net.minecraft.util.math.Box(
        region.shell.minX, region.shell.minY, region.shell.minZ,
        region.shell.maxX + 1, region.shell.maxY + 1, region.shell.maxZ + 1)).toList();
  }

  public record Detection(Box bounds, int storageBlocks, int totalBlocks, int chunks, String title, double templateSimilarity) {}
  public List<Detection> detections() {
    return baseRegions.stream().map(r -> new Detection(bounds(r), r.storageBlocks, r.totalBlocks,
        r.chunkCount, r.familyTitle == null ? "Built base" : r.familyTitle, r.matchScore))
        .sorted(Comparator.comparingDouble(d -> mc.player == null ? 0 : d.bounds.getCenter().squaredDistanceTo(mc.player.getEntityPos()))).toList();
  }
  private static Box bounds(BaseRegion r) {
    return new Box(r.shell.minX,r.shell.minY,r.shell.minZ,r.shell.maxX+1,r.shell.maxY+1,r.shell.maxZ+1);
  }
  public int pendingScans() { return queuedChunks.size(); }
  public int checkedChunks() { return lastScanTickByChunk.size(); }
  private int chunksPerTick() { return switch(scanSpeed.get()) { case Fast -> 16; case Balanced -> 6; case Eco -> 2; case Custom -> customChunks.get(); }; }
  /** How many chunks may wait for the scanner at once. */
  public int queueLimit() { return Math.max(12, chunksPerTick() * 2); }
  private int rescanTicks() { return switch(scanSpeed.get()) { case Fast -> 40; case Balanced -> 80; case Eco -> 160; case Custom -> customInterval.get(); }; }
  private int rebuildTicks() { return switch(scanSpeed.get()) { case Fast, Custom -> 1; case Balanced -> 4; case Eco -> 10; }; }
  private double snapshotBudget() { return switch(scanSpeed.get()) { case Fast -> 3; case Balanced -> 1.5; case Eco -> .6; case Custom -> customBudget.get(); }; }

  @EventHandler private void onBlockUpdate(BlockUpdateEvent event) {
    if (mc.world != lastWorld || event.pos.getY() < SCAN_MIN_Y || event.pos.getY() > SCAN_MAX_Y) return;
    if (!isTrackedState(event.oldState) && !isTrackedState(event.newState)) return;
    ChunkPos pos = new ChunkPos(event.pos);
    if (mc.player == null || chunkDistanceSq(mc.player.getChunkPos(),pos) > scanRadius()*scanRadius()) return;
    changeVersions.put(pos, ++nextChangeVersion);
    requestUrgent(pos);
  }
  private void requestUrgent(ChunkPos pos) {
    lastScanTickByChunk.remove(pos);
    if (urgentSet.add(pos)) {
      if (urgentChunks.size() >= 128) urgentSet.remove(urgentChunks.removeFirst());
      urgentChunks.addLast(pos);
    }
  }

  private ChunkPos nextCandidate(ChunkPos origin, int radius) {
    // Never let updates bypass the queue limit. A running stale snapshot is rejected on publication.
    for (int n = urgentChunks.size(); n > 0; n--) {
      ChunkPos pos = urgentChunks.removeFirst(); urgentSet.remove(pos);
      if (chunkDistanceSq(origin,pos) > radius*radius || getLoadedChunk(pos.x,pos.z) == null) continue;
      if (queuedChunks.containsKey(pos)) { urgentChunks.addLast(pos); urgentSet.add(pos); continue; }
      return pos;
    }
    // Finish the first pass over new chunks before spending work on routine refreshes.
    for (int pass=0; pass<2; pass++) {
      for (int n=0; n<scanOrder.size(); n++) {
        ChunkOffset offset = scanOrder.get(scanIndex);
        scanIndex = (scanIndex+1)%scanOrder.size();
        if (offset.distanceSq > radius*radius) continue;
        ChunkPos pos = new ChunkPos(origin.x+offset.x,origin.z+offset.z);
        if (queuedChunks.containsKey(pos) || (pass==0 && lastScanTickByChunk.containsKey(pos))
            || !shouldScanChunk(pos,tickCounter) || getLoadedChunk(pos.x,pos.z)==null) continue;
        return pos;
      }
    }
    return null;
  }

  @Override public String getInfoString() { return Integer.toString(this.baseRegions.size()); }

  public void onActivate() {
    this.clearAll();
    this.scanExecutor = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "Maro Base ESP"); thread.setDaemon(true); return thread;
    });
  }

  @EventHandler
  private void onGameJoined(GameJoinedEvent event) {
    this.clearAll();
  }

  @EventHandler
  private void onGameLeft(GameLeftEvent event) {
    this.clearAll();
  }

  public void onDeactivate() {
    if (this.scanExecutor != null) {
      this.scanExecutor.shutdownNow();
      this.scanExecutor = null;
    }

    this.clearAll();
  }

  @EventHandler
  private void onTick(Post event) {
    if (this.mc.world != null && this.mc.player != null && this.scanExecutor != null) {
      this.tickCounter++;
      if (this.mc.world != this.lastWorld) {
        this.clearAll();
        this.lastWorld = this.mc.world;
      }

      ChunkPos playerChunk = this.mc.player.getChunkPos();
      int radius = this.scanRadius();
      if (!playerChunk.equals(this.lastOrigin)
          || radius != this.lastRadius
          || this.scanOrder.isEmpty()) {
        this.resetCursor(playerChunk, radius);
      }

      if (this.lastMinBlocks != this.minBlocks.get()) {
        this.lastMinBlocks = this.minBlocks.get();
        this.markRegionsDirty();
      }

      long deadline = System.nanoTime() + (long)(snapshotBudget()*1_000_000);
      for (int submitted=0; submitted<chunksPerTick() && queuedChunks.size()<queueLimit() && System.nanoTime()<deadline; submitted++) {
        ChunkPos chunkPos = nextCandidate(playerChunk,radius);
        if (chunkPos == null) break;
        WorldChunk chunk = getLoadedChunk(chunkPos.x,chunkPos.z);
        if (chunk != null) {
          int scanTick = this.tickCounter;
          long scanId = ++this.nextScanId;
          this.queuedChunks.put(chunkPos, scanId);
          WorldChunk sourceChunk = chunk;
          long scanSession = this.session;
          long version = changeVersions.getOrDefault(chunkPos,0L);
          // Palette snapshots are copied on the client thread; workers never read mutable chunks.
          ChunkSection[] sections = chunk.getSectionArray();
          ChunkSection[] snapshot = new ChunkSection[sections.length];
          int bottomSection = chunk.getBottomSectionCoord();
          int bottomY = Math.max(this.mc.world.getBottomY(), SCAN_MIN_Y);
          for (int i = 0; i < sections.length; i++) {
            int sectionY = (bottomSection + i) << 4;
            if (sectionY <= SCAN_MAX_Y && sectionY + 15 >= bottomY && sections[i] != null
                && !sections[i].isEmpty() && sections[i].hasAny(BaseESP::isTrackedState)) snapshot[i] = sections[i].copy();
          }
          boolean touchesStorage = this.touchesStorageChunk(chunkPos);
          this.scanExecutor.submit(() -> this.scanChunk(snapshot, bottomSection, bottomY, chunkPos, scanTick, touchesStorage, scanSession, sourceChunk, scanId, version));
        }
      }

      this.prune(playerChunk, radius + 4);
      this.applyPendingRebuild();
      this.scheduleRegionRebuildIfNeeded();
    }
  }

  private boolean shouldScanChunk(ChunkPos chunkPos, int currentTick) {
    Integer lastTick = this.lastScanTickByChunk.get(chunkPos);
    return lastTick == null || currentTick - lastTick >= rescanTicks();
  }

  private void markRegionsDirty() {
    this.scanRevision++;
    this.regionsDirty = true;
  }

  private void applyPendingRebuild() {
    BaseESP.RebuildResult result = this.pendingRebuild;
    if (result != null) {
      this.pendingRebuild = null;
      this.rebuildQueued = false;
      if (result.revision == this.scanRevision) {
        this.baseRegions.clear();
        this.baseRegions.addAll(result.regions);
        this.markedChunk = result.markedChunk;
        this.alertedRegions.retainAll(result.activeAnchors);

        for (BaseESP.BaseRegion region : this.baseRegions) {
          if (this.alertedRegions.add(region.anchor) && shouldAlert(region)) {
            this.alertRegion(region);
          }
        }
      }
    }
  }

  private void scheduleRegionRebuildIfNeeded() {
    if (this.regionsDirty && !this.rebuildQueued && this.scanExecutor != null) {
      if (this.tickCounter - this.lastRebuildRequestTick >= rebuildTicks()) {
        this.lastRebuildRequestTick = this.tickCounter;
        this.regionsDirty = false;
        this.rebuildQueued = true;
        long revision = this.scanRevision;
        ArrayList<BaseESP.ChunkScan> snapshot = new ArrayList<>(this.chunkScans.values());
        int minBlocksValue = this.minBlocks.get();
        long rebuildSession = this.session;
        this.scanExecutor.submit(
            () -> {
              try {
                if (rebuildSession != this.session || Thread.currentThread().isInterrupted()) return;
                RebuildResult result = this.rebuildRegionsSnapshot(snapshot, minBlocksValue, revision);
                this.mc.execute(() -> { if (isActive() && rebuildSession == this.session) this.pendingRebuild = result; });
              } catch (Exception error) {
                dev.maro.Maro.LOGGER.error("Base ESP rebuild failed", error);
                this.mc.execute(() -> {
                  if (rebuildSession == this.session) { this.rebuildQueued = false; this.regionsDirty = true; }
                });
              }
            });
      }
    }
  }

  private void scanChunk(ChunkSection[] sections, int baseSectionY, int worldBottom, ChunkPos chunkPos,
                         int scanTick, boolean touchesStorage, long scanSession, WorldChunk sourceChunk, long scanId, long version) {
    try {
      if (scanSession != this.session || Thread.currentThread().isInterrupted()) return;
      HashMap<Long, BaseESP.ChunkColumnBuilder> columns = new HashMap<>();
      int chunkAnchorBlocks = 0;
      int startX = chunkPos.getStartX();
      int startZ = chunkPos.getStartZ();
      int worldTop = -1;
      Block lastBlock = null;
      BlockKind lastKind = UNTRACKED;

      for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
        ChunkSection section = sections[sectionIndex];
        int baseY;
        if (section != null
            && !section.isEmpty()
            && section .hasAny(BaseESP::isTrackedState)
            && (baseY = baseSectionY + sectionIndex << 4) <= worldTop
            && baseY + 15 >= worldBottom) {
          for (int y = 0; y < 16; y++) {
            int worldY = baseY + y;
            if (worldY >= worldBottom && worldY <= worldTop) {
              for (int x = 0; x < 16; x++) {
                int worldX = startX + x;

                for (int z = 0; z < 16; z++) {
                  Block block = section.getBlockState(x, y, z).getBlock();
                  // Runs of the same block (stone, deepslate) skip even the cache.
                  if (block != lastBlock) { lastBlock = block; lastKind = kindOf(block); }
                  BaseESP.BaseBlockType type = lastKind.type();
                  BaseESP.StructureTrait trait = lastKind.trait();
                  if (lastKind != UNTRACKED) {
                    int worldZ = startZ + z;
                    BaseESP.ChunkColumnBuilder column2 =
                        columns.computeIfAbsent(
                            this.packXZ(worldX, worldZ),
                            ignored -> new BaseESP.ChunkColumnBuilder(worldX, worldZ));
                    column2.add(worldY, type, trait);
                    if (type != null && isStorageAnchor(type)) {
                      chunkAnchorBlocks++;
                    }
                  }
                }
              }
            }
          }
        }
      }

      ArrayList<BaseESP.ScannedColumn> compactColumns = new ArrayList<>(columns.size());
      BaseESP.ChunkScan nextScan = null;
      boolean includeChunk = chunkAnchorBlocks > 0 || touchesStorage;
      if (includeChunk) {
        for (BaseESP.ChunkColumnBuilder builder : columns.values()) {
          if (builder.anchorCount > 0
              || builder.hasStructureSeed()
              || builder.engineeredCount >= 2) {
            compactColumns.add(builder.build());
          }
        }

        compactColumns.sort(
            Comparator.<BaseESP.ScannedColumn>comparingInt(column -> column.x)
                .thenComparingInt(column -> column.z));
        if (!compactColumns.isEmpty()) {
          nextScan = new BaseESP.ChunkScan(chunkPos, List.copyOf(compactColumns));
        }
      }

      ChunkScan result = nextScan;
      int anchors = chunkAnchorBlocks;
      this.mc.execute(() -> {
        if (!isActive() || scanSession != this.session || this.mc.world != this.lastWorld || this.mc.player == null) return;
        if (this.chunkDistanceSq(this.mc.player.getChunkPos(), chunkPos) > (this.lastRadius + SCAN_PADDING) * (this.lastRadius + SCAN_PADDING)) return;
        if (this.getLoadedChunk(chunkPos.x, chunkPos.z) != sourceChunk || !Long.valueOf(scanId).equals(this.queuedChunks.get(chunkPos))) return;
        if (changeVersions.getOrDefault(chunkPos,0L) != version) { requestUrgent(chunkPos); return; }
        BaseESP.ChunkScan previous =
          result == null
              ? this.chunkScans.remove(chunkPos)
              : this.chunkScans.put(chunkPos, result);
      this.lastScanTickByChunk.put(chunkPos, scanTick);
      boolean changed = !this.sameScan(previous,result);
      if (changed && result != null && anchors > 0) {
        for (int dx = -1; dx <= 1; dx++) {
          for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) {
              requestUrgent(new ChunkPos(chunkPos.x + dx, chunkPos.z + dz));
            }
          }
        }
      }

      if (changed) {
        this.markRegionsDirty();
      }
      });
    } catch (Exception error) {
      dev.maro.Maro.LOGGER.error("Base ESP chunk scan failed", error);
    } finally {
      this.mc.execute(() -> { if (scanSession == this.session) this.queuedChunks.remove(chunkPos, scanId); });
    }
  }

  private boolean touchesStorageChunk(ChunkPos chunkPos) {
    for (int dx = -1; dx <= 1; dx++) {
      for (int dz = -1; dz <= 1; dz++) {
        BaseESP.ChunkScan neighbor;
        if ((dx != 0 || dz != 0)
            && (neighbor = this.chunkScans.get(new ChunkPos(chunkPos.x + dx, chunkPos.z + dz)))
                != null) {
          for (BaseESP.ScannedColumn column : neighbor.columns) {
            if (column.anchorCount > 0) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private boolean sameScan(BaseESP.ChunkScan previous, BaseESP.ChunkScan next) {
    if (previous == next) {
      return true;
    } else {
      return previous != null && next != null
          ? previous.signatureHash == next.signatureHash
              && previous.columns.size() == next.columns.size()
          : previous == next;
    }
  }

  private BaseESP.RebuildResult rebuildRegionsSnapshot(
      List<BaseESP.ChunkScan> scans, int minBlocksValue, long revision) {
    HashMap<Long, BaseESP.ColumnRef> columnMap = new HashMap<>();

    for (BaseESP.ChunkScan scan : scans) {
      for (BaseESP.ScannedColumn scannedColumn : scan.columns) {
        long columnKey = this.packXZ(scannedColumn.x, scannedColumn.z);
        BaseESP.ColumnRef column = columnMap.get(columnKey);
        if (column == null) {
          columnMap.put(columnKey, new BaseESP.ColumnRef(scan.chunkPos, scannedColumn));
        } else {
          column.add(scannedColumn);
        }
      }
    }

    if (columnMap.isEmpty()) {
      return new BaseESP.RebuildResult(List.of(), null, Set.of(), revision);
    } else {
      ArrayList<BaseESP.ColumnRef> columns =
          this.filterColumns(new ArrayList<>(columnMap.values()));
      BaseESP.UnionFind unionFind = new BaseESP.UnionFind(columns.size());
      HashMap<BaseESP.CellKey, ArrayList<Integer>> spatial = new HashMap<>();

      for (int i = 0; i < columns.size(); i++) {
        BaseESP.ColumnRef column = columns.get(i);
        BaseESP.CellKey cell =
            new BaseESP.CellKey(Math.floorDiv(column.x, 6), Math.floorDiv(column.z, 6));

        for (int cx = -1; cx <= 1; cx++) {
          for (int cz = -1; cz <= 1; cz++) {
            ArrayList<Integer> bucket = spatial.get(new BaseESP.CellKey(cell.x + cx, cell.z + cz));
            if (bucket != null) {
              for (int otherIndex : bucket) {
                BaseESP.ColumnRef other = columns.get(otherIndex);
                if (this.shouldMergeColumns(column, other)) {
                  unionFind.union(i, otherIndex);
                }
              }
            }
          }
        }

        spatial.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(i);
      }

      HashMap<Integer, BaseESP.RegionBuilder> builders = new HashMap<>();

      for (int i = 0; i < columns.size(); i++) {
        int root = unionFind.find(i);
        builders.computeIfAbsent(root, ignored -> new BaseESP.RegionBuilder()).add(columns.get(i));
      }

      ArrayList<BaseESP.RegionBuilder> mergedBuilders =
          this.mergeNearbyBuilders(new ArrayList<>(builders.values()));
      mergedBuilders = this.dropVerticallyDetachedBuilders(mergedBuilders);
      ArrayList<BaseESP.BaseRegion> nextRegions = new ArrayList<>();
      Set<Long> activeAnchors = ConcurrentHashMap.newKeySet();
      ChunkPos bestMarkedChunk = null;
      int bestMarkedCount = -1;
      int bestMarkedRegionBlocks = -1;

      for (BaseESP.RegionBuilder builder : mergedBuilders) {
        if (builder.qualifies(minBlocksValue) && this.isBuiltRegion(builder)) {
          ChunkPos bestChunk = builder.bestChunk();
          int bestChunkCount = builder.bestChunkCount();
          long anchor = ChunkPos.toLong(bestChunk.x, bestChunk.z);
          ArrayList<BaseESP.ColumnRef> outlineColumns =
              this.outlineColumnsForRegion(this.outlineColumnsFor(builder));
          if (!outlineColumns.isEmpty() && this.qualifiesAsBase(builder, outlineColumns)) {
            ArrayList<BaseESP.ColumnRef> shellColumns =
                this.shellColumnsForRegion(this.outlineColumnsFor(builder));
            if (shellColumns.isEmpty()) {
              shellColumns = outlineColumns;
            }

            int[] yRange = this.shellYRange(shellColumns);
            int regionMinY = yRange[0];
            int regionMaxY = yRange[1];
            BaseESP.ShellGeometry shell;
            if (!this.shouldSkipOutlierRegion(builder, outlineColumns)
                && !this.shouldSkipStashRegion(builder, outlineColumns)
                && !this.shouldSkipOutlierShell(
                    builder,
                    shell = this.buildUnifiedShell(shellColumns, regionMinY, regionMaxY),
                    yRange)) {
              BaseESP.CatalogMatch match = this.matchCatalog(builder, shell, minBlocksValue);
              if (match != null && match.score >= 0.95) {
                shell = match.shell;
              }

              BaseESP.BaseRegion region2 =
                  new BaseESP.BaseRegion(
                      anchor,
                      builder.totalBlocks,
                      storageAnchorBlocks(builder),
                      builder.chunkCounts.size(),
                      bestChunk,
                      bestChunkCount,
                      shell,
                      builder.minY,
                      builder.maxY,
                      match == null ? null : match.family.familyHash,
                      match == null ? null : match.family.primarySlug,
                      match == null ? 0.0 : match.score,
                      match == null ? null : match.family.title);
              nextRegions.add(region2);
              activeAnchors.add(anchor);
              if (bestChunkCount > bestMarkedCount
                  || bestChunkCount == bestMarkedCount
                      && builder.totalBlocks > bestMarkedRegionBlocks) {
                bestMarkedCount = bestChunkCount;
                bestMarkedRegionBlocks = builder.totalBlocks;
              }
            }
          }
        }
      }

      nextRegions.sort(
          Comparator.<BaseESP.BaseRegion>comparingInt(region -> region.totalBlocks)
              .reversed()
              .thenComparing(
                  Comparator.<BaseESP.BaseRegion>comparingInt(region -> region.chunkCount)
                      .reversed()));
      nextRegions = this.suppressDuplicateFamilies(nextRegions);
      nextRegions = this.suppressIsolatedRegions(nextRegions);
      activeAnchors.clear();
      bestMarkedChunk = null;
      bestMarkedCount = -1;
      bestMarkedRegionBlocks = -1;

      for (BaseESP.BaseRegion region3 : nextRegions) {
        activeAnchors.add(region3.anchor);
        if (region3.bestChunkCount > bestMarkedCount
            || region3.bestChunkCount == bestMarkedCount
                && region3.totalBlocks > bestMarkedRegionBlocks) {
          bestMarkedChunk = region3.bestChunk;
          bestMarkedCount = region3.bestChunkCount;
          bestMarkedRegionBlocks = region3.totalBlocks;
        }
      }

      return new BaseESP.RebuildResult(
          List.copyOf(nextRegions), bestMarkedChunk, Set.copyOf(activeAnchors), revision);
    }
  }

  private boolean shouldMergeColumns(BaseESP.ColumnRef a, BaseESP.ColumnRef b) {
    int dx = Math.abs(a.x - b.x);
    int dz = Math.abs(a.z - b.z);
    if (dx <= 5 && dz <= 5) {
      if (this.axisGap(a.minY, a.maxY, b.minY, b.maxY) > 10) {
        return false;
      } else if (a.anchorCount <= 0 && b.anchorCount <= 0) {
        return dx <= 3 && dz <= 3;
      } else {
        return this.columnStorageScore(a) == 0 && this.columnStorageScore(b) == 0
            ? dx <= 2 && dz <= 2
            : true;
      }
    } else {
      return false;
    }
  }

  private BaseESP.ShellGeometry buildUnifiedShell(
      List<BaseESP.ColumnRef> columns, int minY, int maxY) {
    if (columns.isEmpty()) {
      return new BaseESP.ShellGeometry(
          0, minY, 0, 0, maxY, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    } else {
      int minX = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      HashSet<Long> columnFootprint = new HashSet<>();

      for (BaseESP.ColumnRef columnRef : columns) {
        minX = Math.min(minX, columnRef.x);
        minZ = Math.min(minZ, columnRef.z);
        maxX = Math.max(maxX, columnRef.x);
        maxZ = Math.max(maxZ, columnRef.z);
        columnFootprint.add(this.packXZ(columnRef.x, columnRef.z));
      }

      HashSet<Long> seeds = this.shellFootprintSeeds(columns);
      if (seeds.isEmpty()) {
        for (BaseESP.ColumnRef column : columns) {
          if (column.structureCount >= 1) {
            seeds.add(this.packXZ(column.x, column.z));
          }
        }
      }

      if (seeds.isEmpty()) {
        seeds = columnFootprint;
      }

      HashSet<Long> hashSet = new HashSet<>(seeds);
      HashSet<Long> allowed = this.footprintBoundingAllowed(minX, maxX, minZ, maxZ);
      this.recoverUnifiedFootprint(hashSet, seeds, allowed, minX, maxX, minZ, maxZ);
      hashSet.retainAll(allowed);
      return this.buildShellFromFootprint(hashSet, minY, maxY, false);
    }
  }

  private HashSet<Long> footprintBoundingAllowed(int minX, int maxX, int minZ, int maxZ) {
    HashSet<Long> allowed = new HashSet<>();
    int pad = 4;

    for (int x = minX - pad; x <= maxX + pad; x++) {
      for (int z = minZ - pad; z <= maxZ + pad; z++) {
        allowed.add(this.packXZ(x, z));
      }
    }

    return allowed;
  }

  private HashSet<Long> shellFootprintSeeds(List<BaseESP.ColumnRef> columns) {
    HashSet<Long> seeds = new HashSet<>();

    for (BaseESP.ColumnRef column : columns) {
      if (column.structureCount >= 1) {
        seeds.add(this.packXZ(column.x, column.z));
      }
    }

    if (seeds.isEmpty()) {
      for (BaseESP.ColumnRef columnx : columns) {
        if (this.columnStorageScore(columnx) > 0) {
          seeds.add(this.packXZ(columnx.x, columnx.z));
        }
      }
    }

    return seeds;
  }

  private int regionMergeRadius(int minX, int maxX, int minZ, int maxZ) {
    int span = Math.max(maxX - minX + 1, maxZ - minZ + 1);
    return Math.min(14, Math.max(5, span / 5 + 3));
  }

  private void recoverUnifiedFootprint(
      Set<Long> footprint,
      Set<Long> seeds,
      Set<Long> allowed,
      int minX,
      int maxX,
      int minZ,
      int maxZ) {
    for (int pass = 0; pass < 2; pass++) {
      this.bridgeFootprintRows(footprint);
      this.bridgeFootprintColumns(footprint);
      this.bridgeSupportedRowsBounded(footprint, allowed);
      this.bridgeSupportedColumnsBounded(footprint, allowed);
      this.fillSupportedFootprintBounded(footprint, allowed, minX, maxX, minZ, maxZ);
    }

    for (int i = 0; i < 2; i++) {
      this.fillCornerStepsBounded(footprint, allowed, minX, maxX, minZ, maxZ);
    }

    this.fillFootprintHolesBounded(footprint, allowed, minX, maxX, minZ, maxZ);
    this.trimWeakFootprint(footprint, seeds, minX, maxX, minZ, maxZ);
    footprint.retainAll(allowed);
  }

  private BaseESP.ShellGeometry buildShell(List<BaseESP.ColumnRef> columns, int minY, int maxY) {
    return this.buildUnifiedShell(columns, minY, maxY);
  }

  private ArrayList<BaseESP.IntRange> occupiedRuns(BaseESP.ColumnRef column) {
    ArrayList<BaseESP.IntRange> runs = new ArrayList<>();
    int start = Integer.MIN_VALUE;
    int last = Integer.MIN_VALUE;

    for (int bucket = 0; bucket < 165; bucket++) {
      if (column.yCounts[bucket] <= 0) {
        if (start != Integer.MIN_VALUE) {
          runs.add(new BaseESP.IntRange(start, last));
          start = Integer.MIN_VALUE;
          last = Integer.MIN_VALUE;
        }
      } else {
        int y = -64 + bucket;
        if (start == Integer.MIN_VALUE) {
          start = y;
          last = y;
        } else if (y == last + 1) {
          last = y;
        } else {
          runs.add(new BaseESP.IntRange(start, last));
          start = y;
          last = y;
        }
      }
    }

    if (start != Integer.MIN_VALUE) {
      runs.add(new BaseESP.IntRange(start, last));
    }

    return runs;
  }

  private BaseESP.ShellGeometry buildShellFromFootprint(
      Set<Long> footprint, int minY, int maxY, boolean recoverFootprint) {
    int minX = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;

    for (long packed : footprint) {
      minX = Math.min(minX, this.unpackX(packed));
      minZ = Math.min(minZ, this.unpackZ(packed));
      maxX = Math.max(maxX, this.unpackX(packed));
      maxZ = Math.max(maxZ, this.unpackZ(packed));
    }

    if (recoverFootprint) {
      HashSet<Long> seeds = new HashSet<>(footprint);
      this.bridgeFootprintRows(footprint);
      this.bridgeFootprintColumns(footprint);

      for (int pass = 0; pass < 2; pass++) {
        this.fillCornerSteps(footprint, minX, maxX, minZ, maxZ);
      }

      this.trimWeakFootprint(footprint, seeds, minX, maxX, minZ, maxZ);
    }

    HashMap<Integer, ArrayList<Integer>> footprintRows = this.footprintRows(footprint);
    ArrayList<BaseESP.TopRect> topRects = this.buildTopRects(footprintRows);
    ArrayList<BaseESP.BoundarySegment> north = new ArrayList<>();
    ArrayList<BaseESP.BoundarySegment> south = new ArrayList<>();
    ArrayList<BaseESP.BoundarySegment> west = new ArrayList<>();
    ArrayList<BaseESP.BoundarySegment> east = new ArrayList<>();
    HashMap<Integer, ArrayList<Integer>> northRows = new HashMap<>();
    HashMap<Integer, ArrayList<Integer>> southRows = new HashMap<>();
    HashMap<Integer, ArrayList<Integer>> westCols = new HashMap<>();
    HashMap<Integer, ArrayList<Integer>> eastCols = new HashMap<>();

    for (long packed : footprint) {
      int x = this.unpackX(packed);
      int z;
      if (!footprint.contains(this.packXZ(x, (z = this.unpackZ(packed)) - 1))) {
        northRows.computeIfAbsent(z, ignored -> new ArrayList<>()).add(x);
      }

      if (!footprint.contains(this.packXZ(x, z + 1))) {
        southRows.computeIfAbsent(z + 1, ignored -> new ArrayList<>()).add(x);
      }

      if (!footprint.contains(this.packXZ(x - 1, z))) {
        westCols.computeIfAbsent(x, ignored -> new ArrayList<>()).add(z);
      }

      if (!footprint.contains(this.packXZ(x + 1, z))) {
        eastCols.computeIfAbsent(x + 1, ignored -> new ArrayList<>()).add(z);
      }
    }

    this.mergeHorizontalSegments(northRows, north);
    this.mergeHorizontalSegments(southRows, south);
    this.mergeVerticalSegments(westCols, west);
    this.mergeVerticalSegments(eastCols, east);
    return new BaseESP.ShellGeometry(
        minX, minY, minZ, maxX, maxY, maxZ, topRects, north, south, west, east, List.of());
  }

  private BaseESP.CatalogMatch matchCatalog(
      BaseESP.RegionBuilder builder, BaseESP.ShellGeometry genericShell, int minBlocksValue) {
    // Loading and parsing the bundled catalog stays off the render/client thread.
    if (this.donutCatalog == null) this.donutCatalog = DonutSignatureCatalog.get();
    if (!this.donutCatalog.families().isEmpty()
        && builder.anchorBlocks >= Math.max(minBlocksValue, 8)) {
      double bestScore = 0.0;
      DonutSignatureCatalog.Family bestFamily = null;
      DonutSignatureCatalog.Variant bestVariant = null;
      long bestOffset = 0L;
      ArrayList<BaseESP.ColumnRef> anchorColumns = builder.anchorColumns();
      if (anchorColumns.isEmpty()) {
        return null;
      } else {
        int observedWidth = builder.maxX - builder.minX + 1;
        int observedLength = builder.maxZ - builder.minZ + 1;
        if (builder.anchorBlocks < minBlocksValue * 2) {
          return null;
        } else {
          for (DonutSignatureCatalog.Family family : this.donutCatalog.families()) {
            double histogramScore;
            if (builder.anchorBlocks
                    <= family.baseBlockCount + Math.max(24, family.baseBlockCount / 3)
                && family.baseBlockCount <= builder.anchorBlocks + 220
                && !((histogramScore =
                        this.histogramOverlap(
                            builder.typeCounts, family.baseHistogram, builder.anchorBlocks))
                    < 0.45)) {
              for (DonutSignatureCatalog.Variant variant : family.variants()) {
                if (observedWidth <= variant.width + 5 && observedLength <= variant.length + 5) {
                  for (long packedOffset : this.topCandidateTranslations(anchorColumns, variant)) {
                    int offsetX = this.unpackX(packedOffset);
                    BaseESP.MatchScore translationScore =
                        this.scoreTranslation(
                            builder, anchorColumns, variant, offsetX, this.unpackZ(packedOffset));
                    double finalScore;
                    if (translationScore != null
                        && !(translationScore.familyBlockCoverage < 0.16)
                        && !(translationScore.familyColumnCoverage < 0.14)
                        && !(translationScore.observedColumnCoverage < 0.58)
                        && !((finalScore = translationScore.score * 0.7 + histogramScore * 0.3)
                            <= bestScore)) {
                      bestScore = finalScore;
                      bestFamily = family;
                      bestVariant = variant;
                      bestOffset = packedOffset;
                    }
                  }
                }
              }
            }
          }

          if (bestFamily != null && bestVariant != null && !(bestScore < 0.72)) {
            int worldMinX = this.unpackX(bestOffset);
            int worldMinZ = this.unpackZ(bestOffset);
            int worldMinY = builder.minY - bestFamily.baseMinY + bestFamily.outlineMinY;
            int worldMaxY = builder.minY - bestFamily.baseMinY + bestFamily.outlineMaxY;
            HashSet<Long> scannedFootprint = new HashSet<>();

            for (BaseESP.ColumnRef column : builder.columns) {
              scannedFootprint.add(this.packXZ(column.x, column.z));
            }

            HashSet<Long> hashSet =
                this.expandFootprintAroundSeeds(
                    scannedFootprint,
                    this.regionMergeRadius(builder.minX, builder.maxX, builder.minZ, builder.maxZ));
            HashSet<Long> catalogFootprint =
                new HashSet<>(
                    bestFamily.buildWorldFootprint(
                        bestVariant.transformIndex, worldMinX, worldMinZ));
            catalogFootprint.retainAll(hashSet);
            if (catalogFootprint.isEmpty()) {
              return null;
            } else {
              int coveredScanned = 0;

              for (long scanned : scannedFootprint) {
                if (catalogFootprint.contains(scanned)) {
                  coveredScanned++;
                }
              }

              if ((double) coveredScanned / (double) scannedFootprint.size() < 0.92) {
                return null;
              } else {
                BaseESP.ShellGeometry exactShell =
                    this.buildShellFromFootprint(catalogFootprint, worldMinY, worldMaxY, false);
                return new BaseESP.CatalogMatch(bestFamily, exactShell, bestScore);
              }
            }
          } else {
            return null;
          }
        }
      }
    } else {
      return null;
    }
  }

  private ArrayList<BaseESP.RegionBuilder> mergeNearbyBuilders(
      ArrayList<BaseESP.RegionBuilder> builders) {
    if (builders.size() < 2) {
      return builders;
    } else {
      BaseESP.UnionFind unionFind = new BaseESP.UnionFind(builders.size());

      for (int i = 0; i < builders.size(); i++) {
        BaseESP.RegionBuilder a = builders.get(i);

        for (int j = i + 1; j < builders.size(); j++) {
          BaseESP.RegionBuilder b = builders.get(j);
          if (this.shouldMergeBuilders(a, b)) {
            unionFind.union(i, j);
          }
        }
      }

      HashMap<Integer, BaseESP.RegionBuilder> merged = new HashMap<>();

      for (int i = 0; i < builders.size(); i++) {
        int root = unionFind.find(i);
        merged.computeIfAbsent(root, ignored -> new BaseESP.RegionBuilder()).merge(builders.get(i));
      }

      return new ArrayList<>(merged.values());
    }
  }

  private boolean shouldMergeBuilders(BaseESP.RegionBuilder a, BaseESP.RegionBuilder b) {
    boolean aStorage = this.storageAnchorBlocks(a) >= 4;
    boolean bStorage = this.storageAnchorBlocks(b) >= 4;
    int mergeGap = !aStorage && !bStorage ? 10 : 32;
    int gapX = this.axisGap(a.minX, a.maxX, b.minX, b.maxX);
    int gapZ = this.axisGap(a.minZ, a.maxZ, b.minZ, b.maxZ);
    if (gapX <= mergeGap && gapZ <= mergeGap) {
      int gapY = this.axisGap(a.minY, a.maxY, b.minY, b.maxY);
      if (gapY > 10) {
        return false;
      } else if (!aStorage && !bStorage) {
        if (a.anchorBlocks == 0 && b.anchorBlocks == 0) {
          if (gapX > 6 || gapZ > 6) {
            return false;
          }

          if (!this.isBuiltRegion(a) || !this.isBuiltRegion(b)) {
            return false;
          }
        }

        if (a.anchorBlocks == 0 == (b.anchorBlocks == 0) || gapX <= 8 && gapZ <= 8) {
          int overlapX = this.axisOverlap(a.minX, a.maxX, b.minX, b.maxX);
          int overlapZ = this.axisOverlap(a.minZ, a.maxZ, b.minZ, b.maxZ);
          double typeOverlap =
              this.typeOverlap(a.typeCounts, a.totalBlocks, b.typeCounts, b.totalBlocks);
          boolean rowAligned = gapX <= mergeGap && overlapZ >= 3;
          boolean columnAligned = gapZ <= mergeGap && overlapX >= 3;
          boolean compactMerge =
              gapX <= mergeGap / 2 && gapZ <= mergeGap / 2 && typeOverlap >= 0.35;
          return rowAligned || columnAligned || compactMerge;
        } else {
          return false;
        }
      } else if (aStorage && bStorage) {
        return true;
      } else {
        BaseESP.RegionBuilder storageSide = aStorage ? a : b;
        BaseESP.RegionBuilder structureSide = aStorage ? b : a;
        return structureSide.structureBlocks < 8
            ? false
            : this.structureNearStorageBuilder(
                structureSide, storageSide, this.structureLinkRadius(storageSide));
      }
    } else {
      return false;
    }
  }

  private boolean isBuiltRegion(BaseESP.RegionBuilder builder) {
    return this.storageAnchorBlocks(builder) >= 4;
  }

  private int storageAnchorBlocks(BaseESP.RegionBuilder builder) {
    int total = 0;

    for (BaseESP.ColumnRef column : builder.columns) {
      total += this.columnStorageScore(column);
    }

    return total;
  }

  private ArrayList<BaseESP.ColumnRef> outlineColumnsFor(BaseESP.RegionBuilder builder) {
    return builder.columns;
  }

  private ArrayList<BaseESP.ColumnRef> outlineColumnsForRegion(
      ArrayList<BaseESP.ColumnRef> columns) {
    if (columns.isEmpty()) {
      return columns;
    } else {
      int coreMinY = Integer.MAX_VALUE;
      int coreMaxY = Integer.MIN_VALUE;
      boolean hasStorage = false;

      for (BaseESP.ColumnRef column : columns) {
        if (column.storageMinY != Integer.MAX_VALUE) {
          hasStorage = true;
          coreMinY = Math.min(coreMinY, column.storageMinY);
          coreMaxY = Math.max(coreMaxY, column.storageMaxY);
        }
      }

      if (!hasStorage) {
        return this.pruneSparseOutliers(this.pruneMainClusterColumns(columns));
      } else {
        ArrayList<BaseESP.ColumnRef> kept = new ArrayList<>();

        for (BaseESP.ColumnRef columnx : columns) {
          if (columnx.minY <= coreMaxY + 10) {
            if (this.columnStorageScore(columnx) > 0) {
              kept.add(columnx);
            } else if (columnx.structureCount >= 1
                && this.axisGap(columnx.minY, columnx.maxY, coreMinY, coreMaxY) <= 10) {
              kept.add(columnx);
            }
          }
        }

        return this.pruneSparseOutliers(kept.isEmpty() ? columns : kept);
      }
    }
  }

  private ArrayList<BaseESP.ColumnRef> shellColumnsForRegion(ArrayList<BaseESP.ColumnRef> columns) {
    if (columns.isEmpty()) {
      return columns;
    } else {
      int coreMinY = Integer.MAX_VALUE;
      int coreMaxY = Integer.MIN_VALUE;
      boolean hasStorage = false;

      for (BaseESP.ColumnRef columnRef : columns) {
        if (columnRef.storageMinY != Integer.MAX_VALUE) {
          hasStorage = true;
          coreMinY = Math.min(coreMinY, columnRef.storageMinY);
          coreMaxY = Math.max(coreMaxY, columnRef.storageMaxY);
        }
      }

      if (!hasStorage) {
        ArrayList<BaseESP.ColumnRef> structureOnly = new ArrayList<>();

        for (BaseESP.ColumnRef column : columns) {
          if (column.structureCount >= 1) {
            structureOnly.add(column);
          }
        }

        return this.pruneSparseOutliers(
            structureOnly.isEmpty() ? this.pruneMainClusterColumns(columns) : structureOnly);
      } else {
        ArrayList<BaseESP.ColumnRef> storageColumns = this.storageColumnsFrom(columns);
        int n = this.structureLinkRadius(storageColumns);
        ArrayList<BaseESP.ColumnRef> kept = new ArrayList<>();

        for (BaseESP.ColumnRef columnx : columns) {
          if (columnx.minY <= coreMaxY + 10
              && (columnx.structureCount >= 1 || this.columnStorageScore(columnx) > 0)) {
            if (columnx.storageMinY != Integer.MAX_VALUE) {
              kept.add(columnx);
            } else if (this.axisGap(columnx.minY, columnx.maxY, coreMinY, coreMaxY) <= 10
                && this.nearColumnXZ(columnx, storageColumns, n)) {
              kept.add(columnx);
            }
          }
        }

        return this.pruneSparseOutliers(kept.isEmpty() ? columns : kept);
      }
    }
  }

  private int storageFootprintSpan(ArrayList<BaseESP.ColumnRef> storageColumns) {
    if (storageColumns.isEmpty()) {
      return 0;
    } else {
      int minX = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (BaseESP.ColumnRef column : storageColumns) {
        minX = Math.min(minX, column.x);
        maxX = Math.max(maxX, column.x);
        minZ = Math.min(minZ, column.z);
        maxZ = Math.max(maxZ, column.z);
      }

      return Math.max(maxX - minX + 1, maxZ - minZ + 1);
    }
  }

  private int storageFootprintSpan(BaseESP.RegionBuilder builder) {
    return this.storageFootprintSpan(this.storageColumnsFrom(builder));
  }

  private int structureLinkRadius(ArrayList<BaseESP.ColumnRef> storageColumns) {
    int span = this.storageFootprintSpan(storageColumns);
    int storageBlocks = 0;

    for (BaseESP.ColumnRef column : storageColumns) {
      storageBlocks += this.columnStorageScore(column);
    }

    return span <= 12 && storageBlocks <= 12 ? 18 : Math.min(32, Math.max(18, span / 2 + 3 + 2));
  }

  private int structureLinkRadius(BaseESP.RegionBuilder builder) {
    return this.structureLinkRadius(this.storageColumnsFrom(builder));
  }

  private ArrayList<BaseESP.ColumnRef> storageColumnsFrom(ArrayList<BaseESP.ColumnRef> columns) {
    ArrayList<BaseESP.ColumnRef> storageColumns = new ArrayList<>();

    for (BaseESP.ColumnRef column : columns) {
      if (this.columnStorageScore(column) > 0) {
        storageColumns.add(column);
      }
    }

    return storageColumns;
  }

  private ArrayList<BaseESP.ColumnRef> storageColumnsFrom(BaseESP.RegionBuilder builder) {
    return this.storageColumnsFrom(builder.columns);
  }

  private boolean nearColumnXZ(
      BaseESP.ColumnRef column, ArrayList<BaseESP.ColumnRef> others, int gap) {
    for (BaseESP.ColumnRef other : others) {
      if (Math.abs(column.x - other.x) <= gap && Math.abs(column.z - other.z) <= gap) {
        return true;
      }
    }

    return false;
  }

  private boolean structureNearStorageBuilder(
      BaseESP.RegionBuilder structure, BaseESP.RegionBuilder storage, int radius) {
    ArrayList<BaseESP.ColumnRef> storageColumns = this.storageColumnsFrom(storage);
    if (storageColumns.isEmpty()) {
      return false;
    } else {
      for (BaseESP.ColumnRef column : structure.columns) {
        if (column.structureCount >= 1 && this.nearColumnXZ(column, storageColumns, radius)) {
          return true;
        }
      }

      return false;
    }
  }

  private int structureBlocksNearStorage(BaseESP.RegionBuilder builder, int radius) {
    ArrayList<BaseESP.ColumnRef> storageColumns = this.storageColumnsFrom(builder);
    if (storageColumns.isEmpty()) {
      return 0;
    } else {
      int total = 0;

      for (BaseESP.ColumnRef column : builder.columns) {
        if (column.structureCount >= 2 && this.nearColumnXZ(column, storageColumns, radius)) {
          total += column.structureCount;
        }
      }

      return total;
    }
  }

  private int builtStructureColumnsNearStorage(BaseESP.RegionBuilder builder, int radius) {
    ArrayList<BaseESP.ColumnRef> storageColumns = this.storageColumnsFrom(builder);
    if (storageColumns.isEmpty()) {
      return 0;
    } else {
      int total = 0;

      for (BaseESP.ColumnRef column : builder.columns) {
        if (column.structureCount >= 2 && this.nearColumnXZ(column, storageColumns, radius)) {
          total++;
        }
      }

      return total;
    }
  }

  private ArrayList<BaseESP.ColumnRef> pruneMainClusterColumns(
      ArrayList<BaseESP.ColumnRef> columns) {
    if (columns.size() <= 2) {
      return columns;
    } else {
      BaseESP.UnionFind unionFind = new BaseESP.UnionFind(columns.size());

      for (int i = 0; i < columns.size(); i++) {
        BaseESP.ColumnRef a = columns.get(i);

        for (int j = i + 1; j < columns.size(); j++) {
          BaseESP.ColumnRef b = columns.get(j);
          if (Math.abs(a.x - b.x) <= 6
              && Math.abs(a.z - b.z) <= 6
              && this.axisGap(a.minY, a.maxY, b.minY, b.maxY) <= 10) {
            unionFind.union(i, j);
          }
        }
      }

      HashMap<Integer, ArrayList<Integer>> groups = new HashMap<>();

      for (int i = 0; i < columns.size(); i++) {
        groups.computeIfAbsent(unionFind.find(i), ignored -> new ArrayList<>()).add(i);
      }

      int bestRoot = -1;
      int bestScore = -1;

      for (Entry<Integer, ArrayList<Integer>> iterator : groups.entrySet()) {
        int score = 0;

        for (int index : iterator.getValue()) {
          score += this.columnBaseScore(columns.get(index));
        }

        if (score > bestScore) {
          bestScore = score;
          bestRoot = iterator.getKey();
        }
      }

      ArrayList<BaseESP.ColumnRef> kept = new ArrayList<>();

      for (int index : groups.get(bestRoot)) {
        kept.add(columns.get(index));
      }

      return kept;
    }
  }

  private int[] shellYRange(ArrayList<BaseESP.ColumnRef> columns) {
    ArrayList<BaseESP.ColumnRef> storageColumns = this.storageColumnsFrom(columns);
    int bandMin = Integer.MAX_VALUE;
    int bandMax = Integer.MIN_VALUE;

    for (BaseESP.ColumnRef column : storageColumns) {
      bandMin = Math.min(bandMin, column.storageMinY);
      bandMax = Math.max(bandMax, column.storageMaxY);
    }

    if (bandMin == Integer.MAX_VALUE) {
      return new int[] {-64, -64};
    } else {
      int minY = bandMin;
      int maxY = bandMax;
      int linkRadius = this.structureLinkRadius(storageColumns);

      for (BaseESP.ColumnRef column : columns) {
        int[] span;
        if ((column.storageMinY != Integer.MAX_VALUE
                || this.nearColumnXZ(column, storageColumns, linkRadius))
            && (span = this.columnShellSpan(column, bandMin, bandMax)) != null) {
          minY = Math.min(minY, span[0]);
          maxY = Math.max(maxY, span[1]);
        }
      }

      return new int[] {minY, maxY};
    }
  }

  private int[] columnShellSpan(BaseESP.ColumnRef column, int bandMin, int bandMax) {
    if (column.storageMinY != Integer.MAX_VALUE) {
      return new int[] {column.storageMinY, column.storageMaxY};
    } else {
      return column.minY > bandMax + 10
          ? null
          : this.connectedSpan(this.occupiedRuns(column), bandMin, bandMax);
    }
  }

  private int[] connectedSpan(ArrayList<BaseESP.IntRange> runs, int bandMin, int bandMax) {
    int low = bandMax;
    int high = bandMin;
    boolean hit = false;

    for (BaseESP.IntRange run : runs) {
      if (run.to() >= bandMin - 2
          && run.from() <= bandMax + 10
          && this.axisGap(run.from(), run.to(), bandMin, bandMax) <= 10) {
        low = Math.min(low, run.from());
        high = Math.max(high, run.to());
        hit = true;
      }
    }

    int[] nArray;
    if (hit) {
      int[] nArray2 = new int[] {low, 0};
      nArray = nArray2;
      nArray2[1] = high;
    } else {
      nArray = null;
    }

    return nArray;
  }

  private boolean qualifiesAsBase(
      BaseESP.RegionBuilder builder, ArrayList<BaseESP.ColumnRef> outlineColumns) {
    int storageBlocks = 0;
    int storageColumns = 0;

    for (BaseESP.ColumnRef column : outlineColumns) {
      int score = this.columnStorageScore(column);
      if (score > 0) {
        storageBlocks += score;
        storageColumns++;
      }
    }

    if (storageBlocks < 16) {
      return false;
    } else if (storageColumns < 4) {
      return false;
    } else {
      int nearbyStructure = this.structureBlocksNearStorage(builder, 18);
      if (nearbyStructure < 24) {
        return false;
      } else if (this.builtStructureColumnsNearStorage(builder, 18) < 6) {
        return false;
      } else {
        ArrayList<BaseESP.ColumnRef> builderStorageColumns = this.storageColumnsFrom(builder);
        int linkRadius = this.structureLinkRadius(builder);
        ArrayList<BaseESP.ColumnRef> footprintColumns = new ArrayList<>();

        for (BaseESP.ColumnRef columnx : builder.columns) {
          if (columnx.structureCount >= 1
              && this.nearColumnXZ(columnx, builderStorageColumns, linkRadius)) {
            footprintColumns.add(columnx);
          }
        }

        if (footprintColumns.size() < 6) {
          return false;
        } else {
          int minX = Integer.MAX_VALUE;
          int maxX = Integer.MIN_VALUE;
          int minZ = Integer.MAX_VALUE;
          int maxZ = Integer.MIN_VALUE;

          for (BaseESP.ColumnRef columnxx : footprintColumns) {
            minX = Math.min(minX, columnxx.x);
            maxX = Math.max(maxX, columnxx.x);
            minZ = Math.min(minZ, columnxx.z);
            maxZ = Math.max(maxZ, columnxx.z);
          }

          int area = (maxX - minX + 1) * (maxZ - minZ + 1);
          if (area < 24) {
            return false;
          } else if (storageColumns <= 2 && storageBlocks <= 8) {
            return false;
          } else if (area <= 16 && storageBlocks <= 12) {
            return false;
          } else {
            int[] yRange = this.shellYRange(footprintColumns);
            return yRange[1] - yRange[0] + 1 >= 3 || area >= 48;
          }
        }
      }
    }
  }

  private ArrayList<BaseESP.RegionBuilder> dropVerticallyDetachedBuilders(
      ArrayList<BaseESP.RegionBuilder> builders) {
    if (builders.size() <= 1) {
      return builders;
    } else {
      int bestIndex = 0;
      int bestScore = Integer.MIN_VALUE;

      for (int i = 0; i < builders.size(); i++) {
        int score = this.builderBaseScore(builders.get(i));
        if (score > bestScore) {
          bestScore = score;
          bestIndex = i;
        }
      }

      BaseESP.RegionBuilder primary = builders.get(bestIndex);
      int[] primaryBand = this.storageBand(primary);
      ArrayList<BaseESP.RegionBuilder> kept = new ArrayList<>();

      for (int ix = 0; ix < builders.size(); ix++) {
        BaseESP.RegionBuilder candidate = builders.get(ix);
        int[] band;
        if (ix == bestIndex
            || this.axisGap(
                    primaryBand[0],
                    primaryBand[1],
                    (band = this.storageBand(candidate))[0],
                    band[1])
                <= 10) {
          kept.add(candidate);
        }
      }

      return kept;
    }
  }

  private int[] storageBand(BaseESP.RegionBuilder builder) {
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;

    for (BaseESP.ColumnRef column : builder.columns) {
      if (column.storageMinY != Integer.MAX_VALUE) {
        minY = Math.min(minY, column.storageMinY);
        maxY = Math.max(maxY, column.storageMaxY);
      }
    }

    return minY == Integer.MAX_VALUE
        ? new int[] {builder.minY, builder.maxY}
        : new int[] {minY, maxY};
  }

  private int builderBaseScore(BaseESP.RegionBuilder builder) {
    int score = 0;

    for (BaseESP.ColumnRef column : builder.columns) {
      score += this.columnBaseScore(column);
    }

    return score;
  }

  private int columnBaseScore(BaseESP.ColumnRef column) {
    int score = column.structureCount;

    for (Entry<BaseESP.BaseBlockType, Integer> entry : column.typeCounts.entrySet()) {
      score += entry.getValue() * anchorWeight(entry.getKey());
    }

    return score;
  }

  private int columnStorageScore(BaseESP.ColumnRef column) {
    int score = 0;

    for (Entry<BaseESP.BaseBlockType, Integer> entry : column.typeCounts.entrySet()) {
      if (anchorWeight(entry.getKey()) >= 7) {
        score += entry.getValue();
      }
    }

    return score;
  }

  private static boolean isStorageAnchor(BaseESP.BaseBlockType type) {
    return anchorWeight(type) >= 7;
  }

  private static int anchorWeight(BaseESP.BaseBlockType type) {
    return switch (type) {
      case CHEST, TRAPPED_CHEST, BARREL, HOPPER, SHULKER_BOX -> 10;
      case CRAFTER, BEACON, ENCHANTING_TABLE -> 9;
      case SPAWNER -> 6;
      case DISPENSER, DROPPER -> 1;
      case FURNACE, BLAST_FURNACE, SMOKER -> 7;
    };
  }

  private boolean shouldSkipOutlierRegion(
      BaseESP.RegionBuilder builder, ArrayList<BaseESP.ColumnRef> outlineColumns) {
    if (builder.anchorBlocks > 0) {
      return false;
    } else if (outlineColumns.size() > 8) {
      return false;
    } else {
      int anchorColumns = 0;

      for (BaseESP.ColumnRef column : outlineColumns) {
        if (column.anchorCount > 0) {
          anchorColumns++;
        }
      }

      return anchorColumns == 0 && outlineColumns.size() <= 4 && builder.structureBlocks < 72;
    }
  }

  private boolean shouldSkipStashRegion(
      BaseESP.RegionBuilder builder, ArrayList<BaseESP.ColumnRef> outlineColumns) {
    int storageBlocks = 0;
    int storageColumns = 0;

    for (BaseESP.ColumnRef column : outlineColumns) {
      int score = this.columnStorageScore(column);
      if (score > 0) {
        storageBlocks += score;
        storageColumns++;
      }
    }

    int nearbyStructure = this.structureBlocksNearStorage(builder, 18);
    int nearbyStructureColumns = this.builtStructureColumnsNearStorage(builder, 18);
    if (nearbyStructureColumns < 6) {
      return true;
    } else if (nearbyStructure < 24) {
      return true;
    } else if (storageColumns <= 3 && storageBlocks <= 6) {
      return true;
    } else {
      int minX = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (BaseESP.ColumnRef columnx : outlineColumns) {
        if (this.columnStorageScore(columnx) > 0) {
          minX = Math.min(minX, columnx.x);
          maxX = Math.max(maxX, columnx.x);
          minZ = Math.min(minZ, columnx.z);
          maxZ = Math.max(maxZ, columnx.z);
        }
      }

      int storageArea = (maxX - minX + 1) * (maxZ - minZ + 1);
      return storageArea <= 16 && storageBlocks <= 12
          ? true
          : storageBlocks < 16 && nearbyStructure < 48;
    }
  }

  private boolean shouldSkipOutlierShell(
      BaseESP.RegionBuilder builder, BaseESP.ShellGeometry shell, int[] storageBand) {
    int width = shell.maxX - shell.minX + 1;
    int length = shell.maxZ - shell.minZ + 1;
    int area = width * length;
    int shellHeight = shell.maxY - shell.minY + 1;
    int bandHeight = storageBand[1] - storageBand[0] + 1;
    int nearbyStructure = this.structureBlocksNearStorage(builder, 18);
    if (nearbyStructure >= 24 && area >= 24) {
      return false;
    } else if (this.structureBlocksNearStorage(builder, this.structureLinkRadius(builder)) >= 24
        && area >= 24) {
      return false;
    } else if (shellHeight > bandHeight + 10 + 3) {
      return true;
    } else {
      return builder.anchorBlocks > 0 ? false : area <= 4 && Math.min(width, length) <= 2;
    }
  }

  private ArrayList<BaseESP.BaseRegion> suppressIsolatedRegions(
      ArrayList<BaseESP.BaseRegion> regions) {
    if (regions.size() <= 1) {
      return regions;
    } else {
      ArrayList<BaseESP.BaseRegion> sorted = new ArrayList<>(regions);
      sorted.sort(
          Comparator.<BaseESP.BaseRegion>comparingInt(region -> region.totalBlocks).reversed());
      ArrayList<BaseESP.BaseRegion> kept = new ArrayList<>();

      for (BaseESP.BaseRegion candidate : sorted) {
        int candidateCenterX = candidate.shell.minX + candidate.shell.maxX >> 1;
        int candidateCenterZ = candidate.shell.minZ + candidate.shell.maxZ >> 1;
        boolean nearKept = false;

        for (BaseESP.BaseRegion existing : kept) {
          int existingCenterX = existing.shell.minX + existing.shell.maxX >> 1;
          int existingCenterZ = existing.shell.minZ + existing.shell.maxZ >> 1;
          int existingSpan =
              Math.max(
                  existing.shell.maxX - existing.shell.minX + 1,
                  existing.shell.maxZ - existing.shell.minZ + 1);
          int distance =
              Math.max(
                  Math.abs(candidateCenterX - existingCenterX),
                  Math.abs(candidateCenterZ - existingCenterZ));
          if (distance < Math.max(existingSpan + 24, 40)) {
            nearKept = true;
            break;
          }
        }

        if (!nearKept) {
          kept.add(candidate);
        }
      }

      return kept.isEmpty() ? sorted : kept;
    }
  }

  private ArrayList<BaseESP.ColumnRef> pruneSparseOutliers(ArrayList<BaseESP.ColumnRef> columns) {
    if (columns.size() <= 3) {
      return columns;
    } else {
      long weightedX = 0L;
      long weightedZ = 0L;
      int totalWeight = 0;

      for (BaseESP.ColumnRef column : columns) {
        int weight = Math.max(1, column.weight());
        weightedX += (long) column.x * (long) weight;
        weightedZ += (long) column.z * (long) weight;
        totalWeight += weight;
      }

      if (totalWeight <= 0) {
        return columns;
      } else {
        int centerX = (int) (weightedX / (long) totalWeight);
        int centerZ = (int) (weightedZ / (long) totalWeight);
        ArrayList<BaseESP.ColumnRef> trimmed = new ArrayList<>();

        for (BaseESP.ColumnRef column : columns) {
          int distance = Math.max(Math.abs(column.x - centerX), Math.abs(column.z - centerZ));
          if (column.anchorCount != 0 || column.count > 3 || distance <= 8) {
            trimmed.add(column);
          }
        }

        return trimmed.isEmpty() ? columns : trimmed;
      }
    }
  }

  private ArrayList<Long> topCandidateTranslations(
      List<BaseESP.ColumnRef> observedColumns, DonutSignatureCatalog.Variant variant) {
    ArrayList<BaseESP.ColumnRef> observed = new ArrayList<>(observedColumns);
    observed.sort(Comparator.<BaseESP.ColumnRef>comparingInt(column -> column.count).reversed());
    if (observed.size() > 96) {
      observed = new ArrayList<>(observed.subList(0, 96));
    }

    HashMap<Long, Integer> votes = new HashMap<>();

    for (BaseESP.ColumnRef observedColumn : observed) {
      for (DonutSignatureCatalog.BaseColumn familyColumn : variant.columns) {
        int overlap = this.sharedTypeWeight(observedColumn, familyColumn);
        if (overlap > 0) {
          long offset =
              this.packXZ(observedColumn.x - familyColumn.x, observedColumn.z - familyColumn.z);
          votes.merge(offset, overlap * overlap, Integer::sum);
        }
      }
    }

    ArrayList entries = new ArrayList<>(votes.entrySet());
    entries.sort(Entry.comparingByValue().reversed());
    ArrayList<Long> translations = new ArrayList<>();

    for (int i = 0; i < entries.size() && i < 6; i++) {
      translations.add((Long) ((Entry) entries.get(i)).getKey());
    }

    return translations;
  }

  private BaseESP.MatchScore scoreTranslation(
      BaseESP.RegionBuilder builder,
      List<BaseESP.ColumnRef> observedColumns,
      DonutSignatureCatalog.Variant variant,
      int offsetX,
      int offsetZ) {
    int matchedWeight = 0;
    int matchedColumns = 0;

    for (BaseESP.ColumnRef observed : observedColumns) {
      DonutSignatureCatalog.BaseColumn familyColumn =
          variant.columnsByPosition.get(this.packXZ(observed.x - offsetX, observed.z - offsetZ));
      int overlap;
      if (familyColumn != null && (overlap = this.sharedTypeWeight(observed, familyColumn)) > 0) {
        matchedWeight += overlap;
        matchedColumns++;
      }
    }

    if (matchedWeight != 0 && matchedColumns != 0) {
      double observedBlockCoverage =
          (double) matchedWeight / (double) Math.max(1, builder.anchorBlocks);
      double observedColumnCoverage =
          (double) matchedColumns / (double) Math.max(1, observedColumns.size());
      double familyBlockCoverage =
          (double) matchedWeight
              / (double)
                  Math.max(1, variant.columns.stream().mapToInt(column -> column.count).sum());
      double familyColumnCoverage =
          (double) matchedColumns / (double) Math.max(1, variant.columns.size());
      double score =
          observedBlockCoverage * 0.45
              + observedColumnCoverage * 0.25
              + familyBlockCoverage * 0.2
              + familyColumnCoverage * 0.1;
      return new BaseESP.MatchScore(
          score,
          observedBlockCoverage,
          observedColumnCoverage,
          familyBlockCoverage,
          familyColumnCoverage);
    } else {
      return null;
    }
  }

  private int sharedTypeWeight(
      BaseESP.ColumnRef observed, DonutSignatureCatalog.BaseColumn familyColumn) {
    int shared = 0;

    for (Entry<BaseESP.BaseBlockType, Integer> entry : observed.typeCounts.entrySet()) {
      Integer familyCount = familyColumn.types.get(entry.getKey().name());
      if (familyCount != null) {
        shared += Math.min(entry.getValue(), familyCount);
      }
    }

    return shared;
  }

  private double histogramOverlap(
      EnumMap<BaseESP.BaseBlockType, Integer> observed,
      Map<String, Integer> familyHistogram,
      int totalObserved) {
    int shared = 0;

    for (Entry<BaseESP.BaseBlockType, Integer> entry : observed.entrySet()) {
      Integer familyCount = familyHistogram.get(entry.getKey().name());
      if (familyCount != null) {
        shared += Math.min(entry.getValue(), familyCount);
      }
    }

    return (double) shared / (double) Math.max(1, totalObserved);
  }

  private double typeOverlap(
      EnumMap<BaseESP.BaseBlockType, Integer> a,
      int totalA,
      EnumMap<BaseESP.BaseBlockType, Integer> b,
      int totalB) {
    int shared = 0;

    for (Entry<BaseESP.BaseBlockType, Integer> entry : a.entrySet()) {
      Integer other = b.get(entry.getKey());
      if (other != null) {
        shared += Math.min(entry.getValue(), other);
      }
    }

    return (double) shared / (double) Math.max(1, Math.min(totalA, totalB));
  }

  private ArrayList<BaseESP.BaseRegion> suppressDuplicateFamilies(
      ArrayList<BaseESP.BaseRegion> regions) {
    HashMap<String, BaseESP.BaseRegion> strongestByFamily = new HashMap<>();
    ArrayList<BaseESP.BaseRegion> result = new ArrayList<>();

    for (BaseESP.BaseRegion region2 : regions) {
      if (region2.familyHash == null) {
        result.add(region2);
      } else {
        BaseESP.BaseRegion current = strongestByFamily.get(region2.familyHash);
        if (current == null || this.isBetterFamilyMatch(region2, current)) {
          strongestByFamily.put(region2.familyHash, region2);
        }
      }
    }

    result.addAll(strongestByFamily.values());
    result.sort(
        Comparator.<BaseESP.BaseRegion>comparingDouble(region -> region.matchScore)
            .reversed()
            .thenComparing(
                Comparator.<BaseESP.BaseRegion>comparingInt(region -> region.totalBlocks)
                    .reversed())
            .thenComparing(
                Comparator.<BaseESP.BaseRegion>comparingInt(region -> region.chunkCount)
                    .reversed()));
    return result;
  }

  private int axisGap(int minA, int maxA, int minB, int maxB) {
    if (maxA < minB) {
      return minB - maxA - 1;
    } else {
      return maxB < minA ? minA - maxB - 1 : 0;
    }
  }

  private int axisOverlap(int minA, int maxA, int minB, int maxB) {
    return Math.max(0, Math.min(maxA, maxB) - Math.max(minA, minB) + 1);
  }

  private boolean isBetterFamilyMatch(BaseESP.BaseRegion candidate, BaseESP.BaseRegion current) {
    if (candidate.matchScore != current.matchScore) {
      return candidate.matchScore > current.matchScore;
    } else if (candidate.totalBlocks != current.totalBlocks) {
      return candidate.totalBlocks > current.totalBlocks;
    } else {
      return candidate.chunkCount != current.chunkCount
          ? candidate.chunkCount > current.chunkCount
          : candidate.anchor < current.anchor;
    }
  }

  private ArrayList<BaseESP.ColumnRef> filterColumns(ArrayList<BaseESP.ColumnRef> columns) {
    Set<Long> occupied = ConcurrentHashMap.newKeySet();

    for (BaseESP.ColumnRef column : columns) {
      occupied.add(this.packXZ(column.x, column.z));
    }

    ArrayList<BaseESP.ColumnRef> filtered = new ArrayList<>();

    for (BaseESP.ColumnRef column : columns) {
      if (column.anchorCount <= 0 && column.count < 3) {
        int neighbors = 0;

        for (int dx = -3; dx <= 3; dx++) {
          for (int dz = -3; dz <= 3; dz++) {
            if ((dx != 0 || dz != 0)
                && occupied.contains(this.packXZ(column.x + dx, column.z + dz))) {
              if (++neighbors >= 2) {
                filtered.add(column);
                dx = 4;
                break;
              }
            }
          }
        }
      } else {
        filtered.add(column);
      }
    }

    return filtered.isEmpty() ? columns : filtered;
  }

  private void bridgeFootprintRows(Set<Long> footprint) {
    HashMap<Integer, ArrayList<Integer>> rows = this.footprintRows(footprint);

    for (Entry<Integer, ArrayList<Integer>> entry : rows.entrySet()) {
      ArrayList<Integer> xs = entry.getValue();
      xs.sort(Integer::compareTo);

      for (int i = 1; i < xs.size(); i++) {
        int previous = xs.get(i - 1);
        int current = xs.get(i);
        int gap = current - previous - 1;
        if (gap > 0 && gap <= 5) {
          for (int x = previous + 1; x < current; x++) {
            footprint.add(this.packXZ(x, entry.getKey()));
          }
        }
      }
    }
  }

  private void bridgeFootprintColumns(Set<Long> footprint) {
    HashMap<Integer, ArrayList<Integer>> cols = this.footprintColumns(footprint);

    for (Entry<Integer, ArrayList<Integer>> entry : cols.entrySet()) {
      ArrayList<Integer> zs = entry.getValue();
      zs.sort(Integer::compareTo);

      for (int i = 1; i < zs.size(); i++) {
        int previous = zs.get(i - 1);
        int current = zs.get(i);
        int gap = current - previous - 1;
        if (gap > 0 && gap <= 5) {
          for (int z = previous + 1; z < current; z++) {
            footprint.add(this.packXZ(entry.getKey(), z));
          }
        }
      }
    }
  }

  private void bridgeSupportedRowsBounded(Set<Long> footprint, Set<Long> allowed) {
    HashMap<Integer, ArrayList<Integer>> rows = this.footprintRows(footprint);

    for (Entry<Integer, ArrayList<Integer>> entry : rows.entrySet()) {
      ArrayList<Integer> xs = entry.getValue();
      xs.sort(Integer::compareTo);

      for (int i = 1; i < xs.size(); i++) {
        int previous = xs.get(i - 1);
        int current = xs.get(i);
        int gap = current - previous - 1;
        if (gap > 5
            && gap <= 10
            && this.hasParallelRowSupport(footprint, entry.getKey(), previous + 1, current - 1)) {
          for (int x = previous + 1; x < current; x++) {
            long packed = this.packXZ(x, entry.getKey());
            if (allowed.contains(packed)) {
              footprint.add(packed);
            }
          }
        }
      }
    }
  }

  private void bridgeSupportedColumnsBounded(Set<Long> footprint, Set<Long> allowed) {
    HashMap<Integer, ArrayList<Integer>> cols = this.footprintColumns(footprint);

    for (Entry<Integer, ArrayList<Integer>> entry : cols.entrySet()) {
      ArrayList<Integer> zs = entry.getValue();
      zs.sort(Integer::compareTo);

      for (int i = 1; i < zs.size(); i++) {
        int previous = zs.get(i - 1);
        int current = zs.get(i);
        int gap = current - previous - 1;
        if (gap > 5
            && gap <= 10
            && this.hasParallelColumnSupport(
                footprint, entry.getKey(), previous + 1, current - 1)) {
          for (int z = previous + 1; z < current; z++) {
            long packed = this.packXZ(entry.getKey(), z);
            if (allowed.contains(packed)) {
              footprint.add(packed);
            }
          }
        }
      }
    }
  }

  private void fillSupportedFootprintBounded(
      Set<Long> footprint, Set<Long> allowed, int minX, int maxX, int minZ, int maxZ) {
    ArrayList<Long> additions = new ArrayList<>();

    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        long packed = this.packXZ(x, z);
        if (allowed.contains(packed) && !footprint.contains(packed)) {
          boolean horizontal = this.hasHorizontalSpan(footprint, x, z);
          boolean vertical = this.hasVerticalSpan(footprint, x, z);
          if (horizontal || vertical) {
            int neighbors = this.countNeighbors(footprint, x, z, 2);
            if (horizontal && vertical || neighbors >= 4) {
              additions.add(packed);
            }
          }
        }
      }
    }

    footprint.addAll(additions);
  }

  private void fillCornerStepsBounded(
      Set<Long> footprint, Set<Long> allowed, int minX, int maxX, int minZ, int maxZ) {
    ArrayList<Long> additions = new ArrayList<>();

    for (int x = minX - 1; x <= maxX; x++) {
      for (int z = minZ - 1; z <= maxZ; z++) {
        long a = this.packXZ(x, z);
        long b = this.packXZ(x + 1, z);
        long c = this.packXZ(x, z + 1);
        long d = this.packXZ(x + 1, z + 1);
        int occupied = 0;
        if (footprint.contains(a)) {
          occupied++;
        }

        if (footprint.contains(b)) {
          occupied++;
        }

        if (footprint.contains(c)) {
          occupied++;
        }

        if (footprint.contains(d)) {
          occupied++;
        }

        long missing;
        if (occupied == 3
            && allowed.contains(
                missing =
                    !footprint.contains(a)
                        ? a
                        : (!footprint.contains(b) ? b : (!footprint.contains(c) ? c : d)))) {
          additions.add(missing);
        }
      }
    }

    footprint.addAll(additions);
  }

  private void fillFootprintHolesBounded(
      Set<Long> footprint, Set<Long> allowed, int minX, int maxX, int minZ, int maxZ) {
    int width = maxX - minX + 3;
    int height = maxZ - minZ + 3;
    boolean[][] solid = new boolean[width][height];
    boolean[][] outside = new boolean[width][height];
    ArrayDeque<BaseESP.GridCell> queue = new ArrayDeque<>();

    for (long packed : footprint) {
      int gridX = this.unpackX(packed) - minX + 1;
      int gridZ = this.unpackZ(packed) - minZ + 1;
      if (gridX >= 0 && gridX < width && gridZ >= 0 && gridZ < height) {
        solid[gridX][gridZ] = true;
      }
    }

    queue.add(new BaseESP.GridCell(0, 0));
    outside[0][0] = true;

    while (!queue.isEmpty()) {
      BaseESP.GridCell cell = queue.removeFirst();
      this.tryVisit(cell.x + 1, cell.z, width, height, solid, outside, queue);
      this.tryVisit(cell.x - 1, cell.z, width, height, solid, outside, queue);
      this.tryVisit(cell.x, cell.z + 1, width, height, solid, outside, queue);
      this.tryVisit(cell.x, cell.z - 1, width, height, solid, outside, queue);
    }

    for (int x = 1; x < width - 1; x++) {
      for (int z = 1; z < height - 1; z++) {
        long packedx;
        if (!solid[x][z]
            && !outside[x][z]
            && allowed.contains(packedx = this.packXZ(minX + x - 1, minZ + z - 1))) {
          footprint.add(packedx);
        }
      }
    }
  }

  private void bridgeSupportedRows(Set<Long> footprint) {
    HashMap<Integer, ArrayList<Integer>> rows = this.footprintRows(footprint);

    for (Entry<Integer, ArrayList<Integer>> entry : rows.entrySet()) {
      ArrayList<Integer> xs = entry.getValue();
      xs.sort(Integer::compareTo);

      for (int i = 1; i < xs.size(); i++) {
        int previous = xs.get(i - 1);
        int current = xs.get(i);
        int gap = current - previous - 1;
        if (gap > 5
            && gap <= 10
            && this.hasParallelRowSupport(footprint, entry.getKey(), previous + 1, current - 1)) {
          for (int x = previous + 1; x < current; x++) {
            footprint.add(this.packXZ(x, entry.getKey()));
          }
        }
      }
    }
  }

  private void bridgeSupportedColumns(Set<Long> footprint) {
    HashMap<Integer, ArrayList<Integer>> cols = this.footprintColumns(footprint);

    for (Entry<Integer, ArrayList<Integer>> entry : cols.entrySet()) {
      ArrayList<Integer> zs = entry.getValue();
      zs.sort(Integer::compareTo);

      for (int i = 1; i < zs.size(); i++) {
        int previous = zs.get(i - 1);
        int current = zs.get(i);
        int gap = current - previous - 1;
        if (gap > 5
            && gap <= 10
            && this.hasParallelColumnSupport(
                footprint, entry.getKey(), previous + 1, current - 1)) {
          for (int z = previous + 1; z < current; z++) {
            footprint.add(this.packXZ(entry.getKey(), z));
          }
        }
      }
    }
  }

  private boolean hasParallelRowSupport(Set<Long> footprint, int z, int fromX, int toX) {
    for (int dz = 1; dz <= 2; dz++) {
      if (this.rowSupportSpan(footprint, z - dz, fromX, toX)
          || this.rowSupportSpan(footprint, z + dz, fromX, toX)) {
        return true;
      }
    }

    return false;
  }

  private boolean hasParallelColumnSupport(Set<Long> footprint, int x, int fromZ, int toZ) {
    for (int dx = 1; dx <= 2; dx++) {
      if (this.columnSupportSpan(footprint, x - dx, fromZ, toZ)
          || this.columnSupportSpan(footprint, x + dx, fromZ, toZ)) {
        return true;
      }
    }

    return false;
  }

  private boolean rowSupportSpan(Set<Long> footprint, int z, int fromX, int toX) {
    int support = 0;

    for (int x = fromX; x <= toX; x++) {
      if (footprint.contains(this.packXZ(x, z))) {
        if (++support >= 3) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean columnSupportSpan(Set<Long> footprint, int x, int fromZ, int toZ) {
    int support = 0;

    for (int z = fromZ; z <= toZ; z++) {
      if (footprint.contains(this.packXZ(x, z))) {
        if (++support >= 3) {
          return true;
        }
      }
    }

    return false;
  }

  private void fillSupportedFootprint(Set<Long> footprint, int minX, int maxX, int minZ, int maxZ) {
    ArrayList<Long> additions = new ArrayList<>();

    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        long packed = this.packXZ(x, z);
        if (!footprint.contains(packed)) {
          boolean horizontal = this.hasHorizontalSpan(footprint, x, z);
          boolean vertical = this.hasVerticalSpan(footprint, x, z);
          if (horizontal || vertical) {
            int neighbors = this.countNeighbors(footprint, x, z, 2);
            if (horizontal && vertical || neighbors >= 4) {
              additions.add(packed);
            }
          }
        }
      }
    }

    footprint.addAll(additions);
  }

  private HashSet<Long> expandFootprintAroundSeeds(Set<Long> seeds, int radius) {
    HashSet<Long> expanded = new HashSet<>(seeds);
    ArrayDeque<long[]> queue = new ArrayDeque<>();

    for (long seed : seeds) {
      queue.addLast(new long[] {seed, 0L});
    }

    while (!queue.isEmpty()) {
      long[] state = queue.removeFirst();
      long packed = state[0];
      int distance = (int) state[1];
      if (distance < radius) {
        int x = this.unpackX(packed);
        int z = this.unpackZ(packed);

        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
          long neighbor = this.packXZ(x + step[0], z + step[1]);
          if (expanded.add(neighbor)) {
            queue.addLast(new long[] {neighbor, (long) (distance + 1)});
          }
        }
      }
    }

    return expanded;
  }

  private void fillCornerSteps(Set<Long> footprint, int minX, int maxX, int minZ, int maxZ) {
    ArrayList<Long> additions = new ArrayList<>();

    for (int x = minX - 1; x <= maxX; x++) {
      for (int z = minZ - 1; z <= maxZ; z++) {
        long a = this.packXZ(x, z);
        long b = this.packXZ(x + 1, z);
        long c = this.packXZ(x, z + 1);
        long d = this.packXZ(x + 1, z + 1);
        int occupied = 0;
        if (footprint.contains(a)) {
          occupied++;
        }

        if (footprint.contains(b)) {
          occupied++;
        }

        if (footprint.contains(c)) {
          occupied++;
        }

        if (footprint.contains(d)) {
          occupied++;
        }

        if (occupied == 3) {
          if (!footprint.contains(a)) {
            additions.add(a);
          } else if (!footprint.contains(b)) {
            additions.add(b);
          } else if (!footprint.contains(c)) {
            additions.add(c);
          } else {
            additions.add(d);
          }
        }
      }
    }

    footprint.addAll(additions);
  }

  private boolean hasHorizontalSpan(Set<Long> footprint, int x, int z) {
    boolean left = false;
    boolean right = false;

    for (int step = 1; step <= 10; step++) {
      if (!left && footprint.contains(this.packXZ(x - step, z))) {
        left = true;
      }

      if (!right && footprint.contains(this.packXZ(x + step, z))) {
        right = true;
      }

      if (left && right) {
        return true;
      }
    }

    return false;
  }

  private boolean hasVerticalSpan(Set<Long> footprint, int x, int z) {
    boolean up = false;
    boolean down = false;

    for (int step = 1; step <= 10; step++) {
      if (!up && footprint.contains(this.packXZ(x, z - step))) {
        up = true;
      }

      if (!down && footprint.contains(this.packXZ(x, z + step))) {
        down = true;
      }

      if (up && down) {
        return true;
      }
    }

    return false;
  }

  private int countNeighbors(Set<Long> footprint, int x, int z, int radius) {
    int neighbors = 0;

    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        if ((dx != 0 || dz != 0) && footprint.contains(this.packXZ(x + dx, z + dz))) {
          neighbors++;
        }
      }
    }

    return neighbors;
  }

  private void fillFootprintHoles(Set<Long> footprint, int minX, int maxX, int minZ, int maxZ) {
    int width = maxX - minX + 3;
    int height = maxZ - minZ + 3;
    boolean[][] solid = new boolean[width][height];
    boolean[][] outside = new boolean[width][height];
    ArrayDeque<BaseESP.GridCell> queue = new ArrayDeque<>();

    for (long packed : footprint) {
      int gridX = this.unpackX(packed) - minX + 1;
      int gridZ = this.unpackZ(packed) - minZ + 1;
      if (gridX >= 0 && gridX < width && gridZ >= 0 && gridZ < height) {
        solid[gridX][gridZ] = true;
      }
    }

    queue.add(new BaseESP.GridCell(0, 0));
    outside[0][0] = true;

    while (!queue.isEmpty()) {
      BaseESP.GridCell cell = queue.removeFirst();
      this.tryVisit(cell.x + 1, cell.z, width, height, solid, outside, queue);
      this.tryVisit(cell.x - 1, cell.z, width, height, solid, outside, queue);
      this.tryVisit(cell.x, cell.z + 1, width, height, solid, outside, queue);
      this.tryVisit(cell.x, cell.z - 1, width, height, solid, outside, queue);
    }

    for (int x = 1; x < width - 1; x++) {
      for (int z = 1; z < height - 1; z++) {
        if (!solid[x][z] && !outside[x][z]) {
          footprint.add(this.packXZ(minX + x - 1, minZ + z - 1));
        }
      }
    }
  }

  private void trimWeakFootprint(
      Set<Long> footprint, Set<Long> seeds, int minX, int maxX, int minZ, int maxZ) {
    boolean changed;
    do {
      changed = false;
      ArrayList<Long> removals = new ArrayList<>();

      for (int x = minX; x <= maxX; x++) {
        for (int z = minZ; z <= maxZ; z++) {
          long packed = this.packXZ(x, z);
          if (footprint.contains(packed) && !seeds.contains(packed)) {
            int orthogonal = 0;
            if (footprint.contains(this.packXZ(x + 1, z))) {
              orthogonal++;
            }

            if (footprint.contains(this.packXZ(x - 1, z))) {
              orthogonal++;
            }

            if (footprint.contains(this.packXZ(x, z + 1))) {
              orthogonal++;
            }

            if (footprint.contains(this.packXZ(x, z - 1))) {
              orthogonal++;
            }

            if (orthogonal < 2) {
              boolean anchored =
                  this.hasHorizontalSpan(footprint, x, z) && this.hasVerticalSpan(footprint, x, z);
              if (!anchored) {
                removals.add(packed);
              }
            }
          }
        }
      }

      if (!removals.isEmpty()) {
        footprint.removeAll(removals);
        changed = true;
      }
    } while (changed);
  }

  private void tryVisit(
      int x,
      int z,
      int width,
      int height,
      boolean[][] solid,
      boolean[][] outside,
      ArrayDeque<BaseESP.GridCell> queue) {
    if (x >= 0 && z >= 0 && x < width && z < height) {
      if (!solid[x][z] && !outside[x][z]) {
        outside[x][z] = true;
        queue.addLast(new BaseESP.GridCell(x, z));
      }
    }
  }

  private HashMap<Integer, ArrayList<Integer>> footprintRows(Set<Long> footprint) {
    HashMap<Integer, ArrayList<Integer>> rows = new HashMap<>();

    for (long packed : footprint) {
      rows.computeIfAbsent(this.unpackZ(packed), ignored -> new ArrayList<>())
          .add(this.unpackX(packed));
    }

    return rows;
  }

  private HashMap<Integer, ArrayList<Integer>> footprintColumns(Set<Long> footprint) {
    HashMap<Integer, ArrayList<Integer>> cols = new HashMap<>();

    for (long packed : footprint) {
      cols.computeIfAbsent(this.unpackX(packed), ignored -> new ArrayList<>())
          .add(this.unpackZ(packed));
    }

    return cols;
  }

  private record AlertStamp(Box bounds, long time) {}
  private boolean shouldAlert(BaseRegion region) {
    long now = System.nanoTime(), cooldown = alertCooldown.get()*1_000_000_000L;
    alertHistory.removeIf(stamp -> now-stamp.time >= cooldown);
    Box box = bounds(region);
    for (AlertStamp stamp : alertHistory) {
      var a = stamp.bounds.getCenter(); var b = box.getCenter();
      if (stamp.bounds.intersects(box) || (Math.abs(a.y-b.y)<=10 && Math.hypot(a.x-b.x,a.z-b.z)<=24)) return false;
    }
    if (alertHistory.size()>=256) alertHistory.removeFirst();
    alertHistory.addLast(new AlertStamp(box,now));
    return true;
  }

  @Override public dev.maro.runtime.gui.widgets.WWidget getWidget(dev.maro.runtime.gui.GuiTheme theme) {
    var button = theme.button("Place detector HUD");
    button.action = () -> { detectorHud.set(true); setEnabled(true); mc.setScreen(new HudPlacementScreen(mc.currentScreen,this)); };
    return button;
  }
  @Override public String hudName() { return "Base ESP"; }
  @Override public float hudScale() {
    return (float)Math.min(hudSize.get(), Math.min((mc.getWindow().getScaledWidth()-8)/224.0,
        (mc.getWindow().getScaledHeight()-8)/(double)panelHeight()));
  }
  private int panelHeight() { return 42+Math.max(1,Math.min(hudEntries.get(),baseRegions.size()))*42; }
  @Override public float hudWidth() { return 224*hudScale(); }
  @Override public float hudHeight() { return panelHeight()*hudScale(); }
  private float roomX() { return Math.max(0,mc.getWindow().getScaledWidth()-hudWidth()-8); }
  private float roomY() { return Math.max(0,mc.getWindow().getScaledHeight()-hudHeight()-8); }
  @Override public float hudLeft() { return 4+Math.round(roomX()*hudX.get()/100); }
  @Override public float hudTop() { return 4+Math.round(roomY()*hudY.get()/100); }
  @Override public void hudMove(float left,float top) {
    hudX.set(roomX()==0?0:clamp((left-4)/roomX()*100,0,100));
    hudY.set(roomY()==0?0:clamp((top-4)/roomY()*100,0,100));
  }
  @Override public void hudResize(float by) { hudSize.set(clamp(hudSize.get()+by,.5,2.5)); }
  @Override public void hudReset() { hudX.reset(); hudY.reset(); hudSize.reset(); }
  @Override public void onRender2D(DrawContext ctx,float delta) {
    if (!detectorHud.get() || mc.player==null || mc.world!=lastWorld || mc.options.hudHidden) return;
    var matrix = ctx.getMatrices(); matrix.pushMatrix();
    Fonts.beginRaw();
    try {
      matrix.translate(hudLeft(),hudTop()); matrix.scale(hudScale(),hudScale());
      Render2D.roundRect(ctx,0,0,224,panelHeight(),8,0xE80B0D12);
      Render2D.roundOutline(ctx,0,0,224,panelHeight(),8,.6f,0x504F645C);
      Fonts.draw(ctx,"BASE ESP",10,9,0xFFECF5EF,true,.7f);
      Fonts.drawRight(ctx,scanSpeed.get().name().toUpperCase(),214,10+Fonts.height(.6f)/2,0xFF83DCA0,false,.6f);
      Fonts.draw(ctx,baseRegions.size()+" found  ·  "+checkedChunks()+" checked  ·  "+pendingScans()+" pending",10,25,0xFF98A69F,false,.6f);
      var found = detections();
      if (found.isEmpty()) {
        Fonts.draw(ctx,"Scanning loaded underground chunks",10,49,0xFFCFDAD4,false,.65f);
        Fonts.draw(ctx,"New chunks and block changes get priority",10,65,0xFF7F9186,false,.55f);
      }
      for (int i=0; i<Math.min(hudEntries.get(),found.size()); i++) {
        Detection d = found.get(i); var center=d.bounds.getCenter(); int y=42+i*42;
        Render2D.roundRect(ctx,7,y,210,37,5,0x70303B34);
        Fonts.draw(ctx,Fonts.trim(d.title,148,true,.65f),12,y+5,0xFFF1F6F2,true,.65f);
        Fonts.drawRight(ctx,Math.round(center.distanceTo(mc.player.getEntityPos()))+"m",210,y+5+Fonts.height(.65f)/2,0xFF83DCA0,true,.65f);
        String info=(int)Math.floor(center.x)+" / "+(int)Math.floor(center.y)+" / "+(int)Math.floor(center.z)+"  ·  "+d.storageBlocks+" storage";
        Fonts.draw(ctx,Fonts.trim(info,198,false,.55f),12,y+21,0xFFADBAB2,false,.55f);
      }
    } finally { Fonts.endRaw(); matrix.popMatrix(); }
  }

  private void alertRegion(BaseESP.BaseRegion region) {
    this.mc.execute(
        () -> {
          if (this.mc.player != null) {
            int centerX = region.shell.minX + region.shell.maxX >> 1;
            int centerY = region.shell.minY + region.shell.maxY >> 1;
            int centerZ = region.shell.minZ + region.shell.maxZ >> 1;
            if (this.chatAlerts.get()) {
              this.mc.player .sendMessage(
                  Text.literal(
                          "Base found at x " + centerX + " y " + centerY + " z " + centerZ
                              + " · " + region.storageBlocks + " storage blocks · "
                              + Math.round(bounds(region).getCenter().distanceTo(mc.player.getEntityPos())) + "m away")
                      .formatted(Formatting.BLUE),
                  false);
            }

            if (this.soundAlerts.get()) {
              this.playAlertSound();
            }
          }
        });
  }

  private void playAlertSound() {
    if (this.mc.player != null) {
      this.mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), 0.92F, 1.06F);
      this.mc.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.55F, 1.14F);
    }
  }

  public void render(Renderer3D event) {
    if (isActive() && this.mc.world != null && this.mc.world == this.lastWorld && !this.baseRegions.isEmpty()) {
      SettingColor base = (SettingColor) this.boxColor.get();
      event.lineWidth(outlineWidth.get().floatValue());
      SettingColor sideColor = new SettingColor(base.r, base.g, base.b, displayStyle.get()==DisplayStyle.Outline?0:this.boxAlpha.get());
      SettingColor lineColor = new SettingColor(base.r, base.g, base.b, displayStyle.get()==DisplayStyle.Filled?0:255);
      SettingColor mark = (SettingColor) this.chunkMarkColor.get();
      int markerAlpha = this.chunkMarkOpacity.get();
      SettingColor markSide = new SettingColor(mark.r, mark.g, mark.b, markerAlpha);
      SettingColor markLine =
          new SettingColor(mark.r, mark.g, mark.b, Math.min(255, Math.max(markerAlpha + 80, 150)));
      double slabY =
          this.clamp(
              63.0,
              this.mc.world == null ? 63.0 : (double) this.mc.world.getBottomY(),
              this.mc.world == null
                  ? 63.0
                  : (double) (this.mc.world.getBottomY() + this.mc.world.getHeight() - 1));
      double pillarMinY =
          this.mc.world == null ? -64.0 : (double) Math.max(-64, this.mc.world.getBottomY());
      double pillarMaxY =
          this.mc.world == null
              ? 100.0
              : (double) Math.min(100, this.mc.world.getBottomY() + this.mc.world.getHeight() - 1);

      for (BaseESP.BaseRegion region : this.baseRegions) {
        this.renderShell(event, region.shell, sideColor, lineColor);
      }

      if (this.chunkMark.get() && this.markedChunk != null) {
        double y2;
        double y1;
        if (this.chunkMarkMode.get() == BaseESP.ChunkMarkMode.Pillar) {
          y1 = pillarMinY;
          y2 = pillarMaxY + 1.0;
        } else {
          y1 = slabY;
          y2 = slabY + 0.2;
        }

        event.box(
            (double) this.markedChunk.getStartX(),
            y1,
            (double) this.markedChunk.getStartZ(),
            (double) this.markedChunk.getStartX() + 16.0,
            y2,
            (double) this.markedChunk.getStartZ() + 16.0,
            markSide,
            markLine,
            ShapeMode.Both,
            0);
      }
    }
  }

  private WorldChunk getLoadedChunk(int x, int z) {
    return this.mc.world != null && this.mc.world.getChunkManager().isChunkLoaded(x, z)
        ? this.mc.world.getChunk(x, z)
        : null;
  }

  private void renderShell(
      Renderer3D event,
      BaseESP.ShellGeometry shell,
      SettingColor sideColor,
      SettingColor lineColor) {
    if (!shell.prismBoxes.isEmpty()) {
      SettingColor noLine = new SettingColor(lineColor.r, lineColor.g, lineColor.b, 0);

      for (BaseESP.PrismBox prism : shell.prismBoxes) {
        event.box(
            (double) prism.minX,
            (double) prism.minY,
            (double) prism.minZ,
            (double) prism.maxX + 1.0,
            (double) prism.maxY + 1.0,
            (double) prism.maxZ + 1.0,
            sideColor,
            noLine,
            ShapeMode.Both,
            0);
      }
      event.box(shell.minX,shell.minY,shell.minZ,shell.maxX+1.0,shell.maxY+1.0,shell.maxZ+1.0,
          noLine,lineColor,ShapeMode.Lines,0);
    } else {
      double minY = (double) shell.minY;
      double maxY = (double) shell.maxY + 1.0;

      for (BaseESP.TopRect rect : shell.topRects) {
        event.quad(
            (double) rect.minX,
            maxY,
            (double) rect.minZ,
            (double) rect.maxX + 1.0,
            maxY,
            (double) rect.minZ,
            (double) rect.maxX + 1.0,
            maxY,
            (double) rect.maxZ + 1.0,
            (double) rect.minX,
            maxY,
            (double) rect.maxZ + 1.0,
            sideColor);
      }

      this.renderNorthSouthFaces(event, shell.northSegments, minY, maxY, sideColor, lineColor);
      this.renderNorthSouthFaces(event, shell.southSegments, minY, maxY, sideColor, lineColor);
      this.renderWestEastFaces(event, shell.westSegments, minY, maxY, sideColor, lineColor);
      this.renderWestEastFaces(event, shell.eastSegments, minY, maxY, sideColor, lineColor);
      Set<Long> corners = new HashSet<>();
      for (var segments : List.of(shell.northSegments,shell.southSegments)) for (var s : segments) {
        corners.add(packXZ(s.from,s.fixed)); corners.add(packXZ(s.to+1,s.fixed));
      }
      for (var segments : List.of(shell.westSegments,shell.eastSegments)) for (var s : segments) {
        corners.add(packXZ(s.fixed,s.from)); corners.add(packXZ(s.fixed,s.to+1));
      }
      for (long p : corners) event.line(unpackX(p),minY,unpackZ(p),unpackX(p),maxY,unpackZ(p),lineColor);
    }
  }

  private void renderNorthSouthFaces(
      Renderer3D event,
      List<BaseESP.BoundarySegment> segments,
      double minY,
      double maxY,
      SettingColor sideColor,
      SettingColor lineColor) {
    for (BaseESP.BoundarySegment segment : segments) {
      double z = (double) segment.fixed;
      double x1 = (double) segment.from;
      double x2 = (double) segment.to + 1.0;
      event.quad(x1, minY, z, x2, minY, z, x2, maxY, z, x1, maxY, z, sideColor);
      event.line(x1, minY, z, x2, minY, z, lineColor);
      event.line(x1, maxY, z, x2, maxY, z, lineColor);
    }
  }

  private void renderWestEastFaces(
      Renderer3D event,
      List<BaseESP.BoundarySegment> segments,
      double minY,
      double maxY,
      SettingColor sideColor,
      SettingColor lineColor) {
    for (BaseESP.BoundarySegment segment : segments) {
      double x = (double) segment.fixed;
      double z1 = (double) segment.from;
      double z2 = (double) segment.to + 1.0;
      event.quad(x, minY, z1, x, minY, z2, x, maxY, z2, x, maxY, z1, sideColor);
      event.line(x, minY, z1, x, minY, z2, lineColor);
      event.line(x, maxY, z1, x, maxY, z2, lineColor);
    }
  }

  private ArrayList<BaseESP.TopRect> buildTopRects(Map<Integer, ArrayList<Integer>> rows) {
    HashMap<BaseESP.RectRowKey, ArrayList<BaseESP.Range>> groupedRows = new HashMap<>();

    for (Entry<Integer, ArrayList<Integer>> entry : rows.entrySet()) {
      ArrayList<Integer> xs = entry.getValue();
      xs.sort(Integer::compareTo);
      if (!xs.isEmpty()) {
        int start;
        int last = start = xs.getFirst();

        for (int i = 1; i < xs.size(); i++) {
          int current = xs.get(i);
          if (current == last + 1) {
            last = current;
          } else {
            groupedRows
                .computeIfAbsent(new BaseESP.RectRowKey(start, last), ignored -> new ArrayList<>())
                .add(new BaseESP.Range(entry.getKey(), entry.getKey()));
            start = current;
            last = current;
          }
        }

        groupedRows
            .computeIfAbsent(new BaseESP.RectRowKey(start, last), ignored -> new ArrayList<>())
            .add(new BaseESP.Range(entry.getKey(), entry.getKey()));
      }
    }

    ArrayList<BaseESP.TopRect> rects = new ArrayList<>();

    for (Entry<BaseESP.RectRowKey, ArrayList<BaseESP.Range>> entryx : groupedRows.entrySet()) {
      ArrayList<BaseESP.Range> ranges = entryx.getValue();
      ranges.sort(Comparator.comparingInt(range -> range.from));
      if (!ranges.isEmpty()) {
        BaseESP.Range first = ranges.getFirst();
        int start = first.from;
        int last = first.to;

        for (int ix = 1; ix < ranges.size(); ix++) {
          BaseESP.Range range2 = (BaseESP.Range) ranges.get(ix);
          if (range2.from == last + 1) {
            last = range2.to;
          } else {
            rects.add(
                new BaseESP.TopRect(
                    ((BaseESP.RectRowKey) entryx.getKey()).min,
                    ((BaseESP.RectRowKey) entryx.getKey()).max,
                    start,
                    last));
            start = range2.from;
            last = range2.to;
          }
        }

        rects.add(
            new BaseESP.TopRect(
                ((BaseESP.RectRowKey) entryx.getKey()).min,
                ((BaseESP.RectRowKey) entryx.getKey()).max,
                start,
                last));
      }
    }

    return rects;
  }

  private void mergeHorizontalSegments(
      Map<Integer, ArrayList<Integer>> rows, List<BaseESP.BoundarySegment> out) {
    for (Entry<Integer, ArrayList<Integer>> entry : rows.entrySet()) {
      ArrayList<Integer> xs = entry.getValue();
      xs.sort(Integer::compareTo);
      if (!xs.isEmpty()) {
        int start;
        int last = start = xs.getFirst();

        for (int i = 1; i < xs.size(); i++) {
          int current = xs.get(i);
          if (current == last + 1) {
            last = current;
          } else {
            out.add(new BaseESP.BoundarySegment(entry.getKey(), start, last));
            start = current;
            last = current;
          }
        }

        out.add(new BaseESP.BoundarySegment(entry.getKey(), start, last));
      }
    }
  }

  private void mergeVerticalSegments(
      Map<Integer, ArrayList<Integer>> cols, List<BaseESP.BoundarySegment> out) {
    for (Entry<Integer, ArrayList<Integer>> entry : cols.entrySet()) {
      ArrayList<Integer> zs = entry.getValue();
      zs.sort(Integer::compareTo);
      if (!zs.isEmpty()) {
        int start;
        int last = start = zs.getFirst();

        for (int i = 1; i < zs.size(); i++) {
          int current = zs.get(i);
          if (current == last + 1) {
            last = current;
          } else {
            out.add(new BaseESP.BoundarySegment(entry.getKey(), start, last));
            start = current;
            last = current;
          }
        }

        out.add(new BaseESP.BoundarySegment(entry.getKey(), start, last));
      }
    }
  }

  private long packXZ(int x, int z) {
    return (long) x << 32 ^ (long) z & 4294967295L;
  }

  private int unpackX(long packed) {
    return (int) (packed >> 32);
  }

  private int unpackZ(long packed) {
    return (int) packed;
  }

  private static boolean isTrackedState(BlockState state) {
    return kindOf(state.getBlock()) != UNTRACKED;
  }

  /** What a block counts as: its storage type or structure trait, or neither. */
  private record BlockKind(BaseESP.BaseBlockType type, BaseESP.StructureTrait trait) {}
  private static final BlockKind UNTRACKED = new BlockKind(null, null);
  /**
   * Each block's kind, worked out once. Working it out takes the block's registry name and a score
   * of string tests; a chunk is sixteen thousand blocks, so doing that per block made one chunk
   * take milliseconds and a full sweep of the scan radius take seconds.
   */
  private static final Map<Block, BlockKind> KINDS = new ConcurrentHashMap<>();

  private static BlockKind kindOf(Block block) {
    BlockKind kind = KINDS.get(block);
    if (kind == null) {
      BaseESP.BaseBlockType type = blockType(block);
      BaseESP.StructureTrait trait = structureTrait(block);
      kind = type == null && trait == null ? UNTRACKED : new BlockKind(type, trait);
      KINDS.put(block, kind);
    }
    return kind;
  }

  private static boolean isBaseState(BlockState state) {
    return blockType(state.getBlock()) != null;
  }

  private static BaseESP.BaseBlockType blockType(Block block) {
    if (block == Blocks.CHEST) {
      return BaseESP.BaseBlockType.CHEST;
    } else if (block == Blocks.TRAPPED_CHEST) {
      return BaseESP.BaseBlockType.TRAPPED_CHEST;
    } else if (block == Blocks.BARREL) {
      return BaseESP.BaseBlockType.BARREL;
    } else if (block == Blocks.HOPPER) {
      return BaseESP.BaseBlockType.HOPPER;
    } else if (block == Blocks.CRAFTER) {
      return BaseESP.BaseBlockType.CRAFTER;
    } else if (block == Blocks.SPAWNER) {
      return BaseESP.BaseBlockType.SPAWNER;
    } else if (block == Blocks.BEACON) {
      return BaseESP.BaseBlockType.BEACON;
    } else if (block == Blocks.ENCHANTING_TABLE) {
      return BaseESP.BaseBlockType.ENCHANTING_TABLE;
    } else if (block == Blocks.FURNACE) {
      return BaseESP.BaseBlockType.FURNACE;
    } else if (block == Blocks.BLAST_FURNACE) {
      return BaseESP.BaseBlockType.BLAST_FURNACE;
    } else if (block == Blocks.SMOKER) {
      return BaseESP.BaseBlockType.SMOKER;
    } else {
      return block instanceof ShulkerBoxBlock ? BaseESP.BaseBlockType.SHULKER_BOX : null;
    }
  }

  private static BaseESP.StructureTrait structureTrait(Block block) {
    if (blockType(block) != null) {
      return null;
    } else {
      String path = Registries.BLOCK.getId(block).getPath();
      if (path.endsWith("_stairs")
          || path.endsWith("_slab")
          || path.endsWith("_wall")
          || path.endsWith("_trapdoor")
          || path.endsWith("_door")
          || path.endsWith("_fence")
          || path.endsWith("_fence_gate")) {
        return BaseESP.StructureTrait.SHAPED;
      } else if (path.contains("glass") || path.endsWith("_pane")) {
        return BaseESP.StructureTrait.TRANSPARENT;
      } else if (isEngineeredSolid(path)) {
        return BaseESP.StructureTrait.ENGINEERED;
      } else {
        return !path.contains("shelf")
                && !path.contains("sign")
                && !path.contains("head")
                && !path.contains("carpet")
                && !path.contains("banner")
            ? null
            : BaseESP.StructureTrait.DETAIL;
      }
    }
  }

  private static boolean isEngineeredSolid(String path) {
    if (path.contains("quartz")
        || path.contains("purpur")
        || path.contains("prismarine")
        || path.contains("terracotta")
        || path.contains("concrete") && !path.contains("powder")
        || path.contains("blackstone")
        || path.contains("brick")
        || path.contains("tiles")
        || path.contains("chiseled")
        || path.contains("_pillar")
        || path.contains("_column")
        || path.startsWith("polished_")) {
      return true;
    } else if (path.contains("deepslate")) {
      return path.equals("cobbled_deepslate")
          || path.contains("polished_deepslate")
          || path.contains("deepslate_bricks")
          || path.contains("deepslate_tiles")
          || path.contains("chiseled_deepslate");
    } else {
      return !path.contains("copper")
          ? false
          : path.contains("cut_copper")
              || path.contains("chiseled_copper")
              || path.contains("copper_grate")
              || path.contains("copper_door")
              || path.contains("copper_trapdoor");
    }
  }

  private int scanRadius() {
    int render = 8;

    try {
      render = (Integer) this.mc.options.getViewDistance().getValue();
    } catch (RuntimeException ignored) {
    }

    return Math.min(scanRange.get(), Math.max(1, render + 1));
  }

  private void resetCursor(ChunkPos origin, int radius) {
    if (!origin.equals(this.lastOrigin) || radius != this.lastRadius) this.scanIndex = 0;
    this.lastOrigin = origin;
    this.lastRadius = radius;
    this.rebuildScanOrder(radius);
  }

  private void rebuildScanOrder(int radius) {
    this.scanOrder.clear();

    for (int x = -radius; x <= radius; x++) {
      for (int z = -radius; z <= radius; z++) {
        this.scanOrder.add(new BaseESP.ChunkOffset(x, z, x * x + z * z));
      }
    }

    this.scanOrder.sort(Comparator.comparingInt(offset -> offset.distanceSq));
  }

  private void prune(ChunkPos center, int radius) {
    int radiusSq = radius * radius;
    boolean removed = false;

    for (ChunkPos chunkPos : Set.copyOf(this.chunkScans.keySet())) {
      if (this.chunkDistanceSq(center, chunkPos) > radiusSq || this.getLoadedChunk(chunkPos.x, chunkPos.z) == null) {
        this.chunkScans.remove(chunkPos);
        this.lastScanTickByChunk.remove(chunkPos);
        removed = true;
      }
    }

    for (ChunkPos chunkPosx : Set.copyOf(this.lastScanTickByChunk.keySet())) {
      if (this.chunkDistanceSq(center, chunkPosx) > radiusSq || getLoadedChunk(chunkPosx.x,chunkPosx.z)==null) {
        this.lastScanTickByChunk.remove(chunkPosx);
        removed = true;
      }
    }

    if (removed) {
      this.markRegionsDirty();
    }
    changeVersions.keySet().removeIf(pos -> chunkDistanceSq(center,pos)>radiusSq || getLoadedChunk(pos.x,pos.z)==null);
  }

  private int chunkDistanceSq(ChunkPos a, ChunkPos b) {
    int dx = a.x - b.x;
    int dz = a.z - b.z;
    return dx * dx + dz * dz;
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private void clearAll() {
    this.session++;
    this.chunkScans.clear();
    this.lastScanTickByChunk.clear();
    this.baseRegions.clear();
    this.queuedChunks.clear();
    this.urgentChunks.clear(); this.urgentSet.clear(); this.changeVersions.clear(); this.alertHistory.clear();
    this.alertedRegions.clear();
    this.scanOrder.clear();
    this.scanIndex = 0;
    this.tickCounter = 0;
    this.lastRebuildRequestTick = 0;
    this.lastRadius = -1;
    this.lastOrigin = null;
    this.markedChunk = null;
    this.lastMinBlocks = this.minBlocks.get();
    this.lastWorld = this.mc.world;
    this.scanRevision = 0L;
    this.regionsDirty = false;
    this.rebuildQueued = false;
    this.pendingRebuild = null;
  }

  private static enum BaseBlockType {
    CHEST,
    TRAPPED_CHEST,
    BARREL,
    HOPPER,
    CRAFTER,
    SPAWNER,
    BEACON,
    ENCHANTING_TABLE,
    DISPENSER,
    DROPPER,
    FURNACE,
    BLAST_FURNACE,
    SMOKER,
    SHULKER_BOX;
  }

  private static class BaseRegion {
    final long anchor;
    final int totalBlocks;
    final int storageBlocks;
    final String familyTitle;
    final int chunkCount;
    final ChunkPos bestChunk;
    final int bestChunkCount;
    final BaseESP.ShellGeometry shell;
    final int baseMinY;
    final int baseMaxY;
    final String familyHash;
    final String familySlug;
    final double matchScore;

    BaseRegion(
        long anchor,
        int totalBlocks,
        int storageBlocks,
        int chunkCount,
        ChunkPos bestChunk,
        int bestChunkCount,
        BaseESP.ShellGeometry shell,
        int baseMinY,
        int baseMaxY,
        String familyHash,
        String familySlug,
        double matchScore, String familyTitle) {
      this.anchor = anchor;
      this.totalBlocks = totalBlocks;
      this.storageBlocks = storageBlocks;
      this.familyTitle = familyTitle;
      this.chunkCount = chunkCount;
      this.bestChunk = bestChunk;
      this.bestChunkCount = bestChunkCount;
      this.shell = shell;
      this.baseMinY = baseMinY;
      this.baseMaxY = baseMaxY;
      this.familyHash = familyHash;
      this.familySlug = familySlug;
      this.matchScore = matchScore;
    }
  }

  private static record BoundarySegment(int fixed, int from, int to) {}

  private static record CatalogMatch(
      DonutSignatureCatalog.Family family, BaseESP.ShellGeometry shell, double score) {}

  private static record CellKey(int x, int z) {}

  private static class ChunkColumnBuilder {
    final int x;
    final int z;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    int count;
    int anchorCount;
    int structureCount;
    int engineeredCount;
    int storageMinY = Integer.MAX_VALUE;
    int storageMaxY = Integer.MIN_VALUE;
    final int[] yCounts = new int[165];
    final EnumMap<BaseESP.BaseBlockType, Integer> typeCounts =
        new EnumMap<>(BaseESP.BaseBlockType.class);
    final EnumSet<BaseESP.StructureTrait> structureTraits =
        EnumSet.noneOf(BaseESP.StructureTrait.class);

    ChunkColumnBuilder(int x, int z) {
      this.x = x;
      this.z = z;
    }

    void add(int y, BaseESP.BaseBlockType type, BaseESP.StructureTrait trait) {
      this.minY = Math.min(this.minY, y);
      this.maxY = Math.max(this.maxY, y);
      this.count++;
      int bucket = y - -64;
      if (bucket >= 0 && bucket < 165) {
        this.yCounts[bucket]++;
      }

      if (type != null) {
        this.anchorCount++;
        this.typeCounts.merge(type, Integer.valueOf(1), Integer::sum);
        if (BaseESP.isStorageAnchor(type)) {
          this.storageMinY = Math.min(this.storageMinY, y);
          this.storageMaxY = Math.max(this.storageMaxY, y);
        }
      }

      if (trait != null) {
        this.structureCount++;
        if (trait == BaseESP.StructureTrait.ENGINEERED) {
          this.engineeredCount++;
        } else {
          this.structureTraits.add(trait);
        }
      }
    }

    boolean hasStructureSeed() {
      return !this.structureTraits.isEmpty();
    }

    BaseESP.ScannedColumn build() {
      return new BaseESP.ScannedColumn(
          this.x,
          this.z,
          this.minY,
          this.maxY,
          this.count,
          this.anchorCount,
          this.structureCount,
          this.storageMinY,
          this.storageMaxY,
          Arrays.copyOf(this.yCounts, 165),
          new EnumMap<>(this.typeCounts),
          this.structureTraits.isEmpty()
              ? EnumSet.noneOf(BaseESP.StructureTrait.class)
              : EnumSet.copyOf(this.structureTraits));
    }
  }

  private static enum ChunkMarkMode {
    Pillar,
    Slab;
  }

  private static record ChunkOffset(int x, int z, int distanceSq) {}

  private static class ChunkScan {
    final ChunkPos chunkPos;
    final List<BaseESP.ScannedColumn> columns;
    final int signatureHash;

    ChunkScan(ChunkPos chunkPos, List<BaseESP.ScannedColumn> columns) {
      this.chunkPos = chunkPos;
      this.columns = columns;
      int hash = 1;

      for (BaseESP.ScannedColumn column : columns) {
        hash = 31 * hash + column.signatureHash();
      }

      this.signatureHash = hash;
    }
  }

  private static class ColumnRef {
    final int x;
    final int z;
    int minY;
    int maxY;
    int count;
    int anchorCount;
    int structureCount;
    int storageMinY = Integer.MAX_VALUE;
    int storageMaxY = Integer.MIN_VALUE;
    final int[] yCounts = new int[165];
    final ChunkPos chunkPos;
    final EnumMap<BaseESP.BaseBlockType, Integer> typeCounts =
        new EnumMap<>(BaseESP.BaseBlockType.class);
    final EnumSet<BaseESP.StructureTrait> structureTraits =
        EnumSet.noneOf(BaseESP.StructureTrait.class);

    ColumnRef(ChunkPos chunkPos, BaseESP.ScannedColumn scannedColumn) {
      this.x = scannedColumn.x;
      this.z = scannedColumn.z;
      this.minY = scannedColumn.minY;
      this.maxY = scannedColumn.maxY;
      this.count = scannedColumn.count;
      this.anchorCount = scannedColumn.anchorCount;
      this.structureCount = scannedColumn.structureCount;
      this.storageMinY = scannedColumn.storageMinY;
      this.storageMaxY = scannedColumn.storageMaxY;
      System.arraycopy(scannedColumn.yCounts, 0, this.yCounts, 0, 165);
      this.chunkPos = chunkPos;
      this.typeCounts.putAll(scannedColumn.typeCounts);
      this.structureTraits.addAll(scannedColumn.structureTraits);
    }

    void add(int y, BaseESP.BaseBlockType type, BaseESP.StructureTrait trait) {
      this.minY = Math.min(this.minY, y);
      this.maxY = Math.max(this.maxY, y);
      this.count++;
      int bucket = y - -64;
      if (bucket >= 0 && bucket < 165) {
        this.yCounts[bucket]++;
      }

      if (type != null) {
        this.anchorCount++;
        this.typeCounts.merge(type, Integer.valueOf(1), Integer::sum);
        if (BaseESP.isStorageAnchor(type)) {
          this.storageMinY = Math.min(this.storageMinY, y);
          this.storageMaxY = Math.max(this.storageMaxY, y);
        }
      }

      if (trait != null) {
        this.structureCount++;
        if (trait != BaseESP.StructureTrait.ENGINEERED) {
          this.structureTraits.add(trait);
        }
      }
    }

    void add(BaseESP.ScannedColumn scannedColumn) {
      this.minY = Math.min(this.minY, scannedColumn.minY);
      this.maxY = Math.max(this.maxY, scannedColumn.maxY);
      this.count = this.count + scannedColumn.count;
      this.anchorCount = this.anchorCount + scannedColumn.anchorCount;
      this.structureCount = this.structureCount + scannedColumn.structureCount;
      if (scannedColumn.storageMinY != Integer.MAX_VALUE) {
        this.storageMinY = Math.min(this.storageMinY, scannedColumn.storageMinY);
        this.storageMaxY = Math.max(this.storageMaxY, scannedColumn.storageMaxY);
      }

      for (int i = 0; i < 165; i++) {
        this.yCounts[i] = this.yCounts[i] + scannedColumn.yCounts[i];
      }

      for (Entry<BaseESP.BaseBlockType, Integer> entry : scannedColumn.typeCounts.entrySet()) {
        this.typeCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }

      this.structureTraits.addAll(scannedColumn.structureTraits);
    }

    int weight() {
      return this.anchorCount * 4 + this.structureCount;
    }
  }

  private static record GridCell(int x, int z) {}

  private static record HeightBand(int minY, int maxY) {}

  private static record IntRange(int from, int to) {}

  private static record MatchScore(
      double score,
      double observedBlockCoverage,
      double observedColumnCoverage,
      double familyBlockCoverage,
      double familyColumnCoverage) {}

  private static record PrismBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

  private static record Range(int from, int to) {}

  private static record RebuildResult(
      List<BaseESP.BaseRegion> regions,
      ChunkPos markedChunk,
      Set<Long> activeAnchors,
      long revision) {}

  private static record RectRowKey(int min, int max) {}

  private static class RegionBuilder {
    final ArrayList<BaseESP.ColumnRef> columns = new ArrayList<>();
    final HashMap<ChunkPos, Integer> chunkCounts = new HashMap<>();
    final int[] yCounts = new int[165];
    final EnumMap<BaseESP.BaseBlockType, Integer> typeCounts =
        new EnumMap<>(BaseESP.BaseBlockType.class);
    final EnumSet<BaseESP.StructureTrait> structureTraits =
        EnumSet.noneOf(BaseESP.StructureTrait.class);
    int totalBlocks;
    int anchorBlocks;
    int structureBlocks;
    int minY = Integer.MAX_VALUE;
    int maxY = Integer.MIN_VALUE;
    int minX = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;

    void add(BaseESP.ColumnRef column) {
      this.columns.add(column);
      this.totalBlocks = this.totalBlocks + column.count;
      this.anchorBlocks = this.anchorBlocks + column.anchorCount;
      this.structureBlocks = this.structureBlocks + column.structureCount;
      this.minY = Math.min(this.minY, column.minY);
      this.maxY = Math.max(this.maxY, column.maxY);
      this.minX = Math.min(this.minX, column.x);
      this.minZ = Math.min(this.minZ, column.z);
      this.maxX = Math.max(this.maxX, column.x);
      this.maxZ = Math.max(this.maxZ, column.z);
      this.chunkCounts.merge(column.chunkPos, column.weight(), Integer::sum);

      for (int i = 0; i < 165; i++) {
        this.yCounts[i] = this.yCounts[i] + column.yCounts[i];
      }

      for (Entry<BaseESP.BaseBlockType, Integer> entry : column.typeCounts.entrySet()) {
        this.typeCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
      }

      this.structureTraits.addAll(column.structureTraits);
    }

    void merge(BaseESP.RegionBuilder other) {
      for (BaseESP.ColumnRef column : other.columns) {
        this.add(column);
      }
    }

    boolean qualifies(int minBlocks) {
      int storage = 0;

      for (BaseESP.ColumnRef column : this.columns) {
        for (Entry<BaseESP.BaseBlockType, Integer> entry : column.typeCounts.entrySet()) {
          if (BaseESP.anchorWeight(entry.getKey()) >= 7) {
            storage += entry.getValue();
          }
        }
      }

      return storage >= minBlocks;
    }

    ArrayList<BaseESP.ColumnRef> anchorColumns() {
      ArrayList<BaseESP.ColumnRef> anchorColumns = new ArrayList<>();

      for (BaseESP.ColumnRef column : this.columns) {
        if (column.anchorCount > 0) {
          anchorColumns.add(column);
        }
      }

      return anchorColumns;
    }

    ChunkPos bestChunk() {
      ChunkPos best = null;
      int bestCount = -1;

      for (Entry<ChunkPos, Integer> entry : this.chunkCounts.entrySet()) {
        int count = entry.getValue();
        ChunkPos chunkPos = entry.getKey();
        if (count > bestCount) {
          best = chunkPos;
          bestCount = count;
        } else if (count == bestCount
            && best != null
            && (chunkPos.x < best.x || chunkPos.x == best.x && chunkPos.z < best.z)) {
          best = chunkPos;
        }
      }

      return best;
    }

    int bestChunkCount() {
      int best = 0;

      for (int count : this.chunkCounts.values()) {
        if (count > best) {
          best = count;
        }
      }

      return best;
    }

    int outlineMinY() {
      int threshold = this.yThreshold();

      for (int i = 0; i < 165; i++) {
        if (this.yCounts[i] >= threshold) {
          return -64 + i;
        }
      }

      return this.minY;
    }

    int outlineMaxY() {
      int threshold = this.yThreshold();

      for (int i = 164; i >= 0; i--) {
        if (this.yCounts[i] >= threshold) {
          return -64 + i;
        }
      }

      return this.maxY;
    }

    private int yThreshold() {
      int peak = 0;

      for (int count : this.yCounts) {
        if (count > peak) {
          peak = count;
        }
      }

      return Math.max(2, (peak + 5) / 6);
    }
  }

  private static class ScannedColumn {
    final int x;
    final int z;
    final int minY;
    final int maxY;
    final int count;
    final int anchorCount;
    final int structureCount;
    final int storageMinY;
    final int storageMaxY;
    final int[] yCounts;
    final EnumMap<BaseESP.BaseBlockType, Integer> typeCounts;
    final EnumSet<BaseESP.StructureTrait> structureTraits;

    ScannedColumn(
        int x,
        int z,
        int minY,
        int maxY,
        int count,
        int anchorCount,
        int structureCount,
        int storageMinY,
        int storageMaxY,
        int[] yCounts,
        EnumMap<BaseESP.BaseBlockType, Integer> typeCounts,
        EnumSet<BaseESP.StructureTrait> structureTraits) {
      this.x = x;
      this.z = z;
      this.minY = minY;
      this.maxY = maxY;
      this.count = count;
      this.anchorCount = anchorCount;
      this.structureCount = structureCount;
      this.storageMinY = storageMinY;
      this.storageMaxY = storageMaxY;
      this.yCounts = yCounts;
      this.typeCounts = typeCounts;
      this.structureTraits = structureTraits;
    }

    int signatureHash() {
      return Arrays.hashCode(
          new int[] {
            this.x,
            this.z,
            this.minY,
            this.maxY,
            this.count,
            this.anchorCount,
            this.structureCount,
            Arrays.hashCode(this.yCounts),
            this.typeCounts.hashCode(),
            this.structureTraits.hashCode()
          });
    }
  }

  private static class ShellGeometry {
    final int minX;
    final int minY;
    final int minZ;
    final int maxX;
    final int maxY;
    final int maxZ;
    final List<BaseESP.TopRect> topRects;
    final List<BaseESP.BoundarySegment> northSegments;
    final List<BaseESP.BoundarySegment> southSegments;
    final List<BaseESP.BoundarySegment> westSegments;
    final List<BaseESP.BoundarySegment> eastSegments;
    final List<BaseESP.PrismBox> prismBoxes;

    ShellGeometry(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        List<BaseESP.TopRect> topRects,
        List<BaseESP.BoundarySegment> northSegments,
        List<BaseESP.BoundarySegment> southSegments,
        List<BaseESP.BoundarySegment> westSegments,
        List<BaseESP.BoundarySegment> eastSegments,
        List<BaseESP.PrismBox> prismBoxes) {
      this.minX = minX;
      this.minY = minY;
      this.minZ = minZ;
      this.maxX = maxX;
      this.maxY = maxY;
      this.maxZ = maxZ;
      this.topRects = topRects;
      this.northSegments = northSegments;
      this.southSegments = southSegments;
      this.westSegments = westSegments;
      this.eastSegments = eastSegments;
      this.prismBoxes = prismBoxes;
    }
  }

  private static enum StructureTrait {
    SHAPED,
    TRANSPARENT,
    ENGINEERED,
    DETAIL;
  }

  private static record TopRect(int minX, int maxX, int minZ, int maxZ) {}

  private static class UnionFind {
    final int[] parent;
    final byte[] rank;

    UnionFind(int size) {
      this.parent = new int[size];
      this.rank = new byte[size];
      int i = 0;

      while (i < size) {
        this.parent[i] = i++;
      }
    }

    int find(int index) {
      int root = index;

      while (this.parent[root] != root) {
        root = this.parent[root];
      }

      while (this.parent[index] != index) {
        int next = this.parent[index];
        this.parent[index] = root;
        index = next;
      }

      return root;
    }

    void union(int a, int b) {
      int rootA = this.find(a);
      int rootB;
      if (rootA != (rootB = this.find(b))) {
        if (this.rank[rootA] < this.rank[rootB]) {
          this.parent[rootA] = rootB;
        } else if (this.rank[rootA] > this.rank[rootB]) {
          this.parent[rootB] = rootA;
        } else {
          this.parent[rootB] = rootA;
          this.rank[rootA]++;
        }
      }
    }
  }
}
