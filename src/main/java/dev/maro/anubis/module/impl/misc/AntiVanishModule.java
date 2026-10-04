// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Anubis by 4ldenz, recovered from the user-provided Anubis Client Beta 0.9.8.jar.
// Modified for Maro / Yarn 1.21.11 on 2026-10-04; see THIRD_PARTY.md.
package dev.maro.anubis.module.impl.misc;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import dev.maro.anubis.chat.ModuleChat;
import dev.maro.anubis.gui.StaffGhost;
import dev.maro.anubis.module.ClientModule;

import dev.maro.module.ModuleManager;
import dev.maro.anubis.util.staff.StaffContext;
import dev.maro.anubis.util.NameFilter;
import dev.maro.anubis.util.antivanish.AntiVanishHeuristics;
import dev.maro.anubis.util.antivanish.AntiVanishText;
import dev.maro.anubis.util.staff.ShardTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.state.property.Property;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.TextContent;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.registry.Registries;

@Environment(value=EnvType.CLIENT)
public final class AntiVanishModule
extends ClientModule {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private final dev.maro.setting.BooleanSetting completionProbe=add(new dev.maro.setting.BooleanSetting("Completion probe","Check server-provided message target suggestions; sends no chat message",true));
    private final dev.maro.setting.NumberSetting probeInterval=add(new dev.maro.setting.NumberSetting("Probe interval","Ticks between command-completion requests",100,100,600,20));
    private final dev.maro.setting.NumberSetting range=add(new dev.maro.setting.NumberSetting("Sensor range","Range for nearby sound, particle and invisible-entity evidence",64,16,128,4));
    private int queued;
    private void enqueue(Observation observation){if(queued<4096){observations.offer(observation);queued++;}}
    @Override protected List<Row> panelRows(){return hudSnapshot().stream().map(e->new Row(hudTag(e),hudValue(e),e.reason())).toList();}
    private static final String PROBE_COMMAND = "minecraft:msg";
    private static final int SENSOR_RANGE = 64;
    private static final long SIGNAL_WINDOW_MS = 15000L;
    private static final long DETECTION_TTL_MS = 20000L;
    private static final long CRITICAL_COOLDOWN_MS = 12000L;
    private static final long ANNOUNCE_COOLDOWN_MS = 1500L;
    private static final long ANNOUNCE_WINDOW_MS = 4000L;
    private static final int MAX_ANNOUNCE_PER_WINDOW = 6;
    private static final int MAX_OBSERVATIONS_PER_TICK = 512;
    private static final int CRITICAL_SCORE = 35;
    private static final long SELF_PLACE_TTL_MS = 6000L;
    private static final long CONTAINER_SELF_GRACE_MS = 2500L;
    private static final long SPELL_SETTLE_MS = 1000L;
    private final ConcurrentLinkedQueue<Observation> observations = new ConcurrentLinkedQueue();
    private final Map<UUID, KnownPlayer> knownPlayers = new HashMap<UUID, KnownPlayer>();
    private final Map<String, Detection> detections = new LinkedHashMap<String, Detection>();
    private final Map<String, Long> signalCooldowns = new HashMap<String, Long>();
    private final Map<String, Long> announceCooldowns = new HashMap<String, Long>();
    private final Deque<Long> announceTimes = new ArrayDeque<Long>();
    private final Map<Long, Long> selfPlacedBlocks = new ConcurrentHashMap<Long, Long>();
    private final Map<UUID, Long> confirmedDepartures = new HashMap<UUID, Long>();
    private final Map<Long, Long> automatedMechanisms = new HashMap<Long, Long>();
    private final Map<String, Deque<Long>> weakParticleBursts = new HashMap<String, Deque<Long>>();
    private final Deque<Signal> signals = new ArrayDeque<Signal>();
    private final Deque<ExplosionEvent> recentExplosions = new ArrayDeque<ExplosionEvent>();
    private final Deque<PendingVanish> pendingVanishes = new ArrayDeque<PendingVanish>();
    private final Map<String, HiddenSpell> hiddenSpells = new LinkedHashMap<String, HiddenSpell>();
    private final Deque<RecentMessage> recentMessages = new ArrayDeque<RecentMessage>();
    private boolean serverSendsLeaveMessages;
    private final Set<Integer> completionRequestIds = ConcurrentHashMap.newKeySet();
    private volatile List<String> pendingCompletionNames;
    private int nextCompletionId = 30000;
    private static final long RECENT_MESSAGE_TTL_MS = 8000L;
    private static final long TRUSTED_LISTED_MS = 1500L;
    private final Map<UUID, Long> listedSinceMs = new HashMap<UUID, Long>();
    private final ShardTracker shard = new ShardTracker();
    private int tickCounter;
    private volatile long lastLocalActionMs;
    private long lastContainerActivityMs;
    private long lastCriticalMs;
    private long criticalUntilMs;
    private String criticalWatcher = "";
    private final Set<UUID> pendingChimes = new HashSet<UUID>();
    private UUID ghostId;
    private String ghostName;

    public AntiVanishModule() {
        super("Anti Vanish", "Reports evidence of hidden staff and unexplained activity nearby.");
    }

    @Override
    protected void onEnable() {
        this.resetRuntime();
        if (AntiVanishModule.MC.player != null && AntiVanishModule.MC.world != null) {
            this.trackListedPlayers();
        }
    }

    @Override
    protected void onDisable() {
        this.resetRuntime();
    }

    @Override
    public void onTick() {
        if (AntiVanishModule.MC.player == null || AntiVanishModule.MC.world == null || MC.getNetworkHandler() == null) {
            this.resetRuntime();
            return;
        }
        if (this.shard.moved(MC)) {
            this.resetRuntime();
        }
        this.playPendingChime();
        ++this.tickCounter;
        if (AntiVanishModule.MC.player.currentScreenHandler != AntiVanishModule.MC.player.playerScreenHandler) {
            this.lastContainerActivityMs = System.currentTimeMillis();
        }
        this.drainObservations();
        this.processPendingVanishes();
        this.processCompletionProbe();
        if (this.completionProbe.get() && this.tickCounter % this.probeInterval.getInt() == 0) {
            this.sendCompletionProbe();
        }
        if (this.tickCounter % 10 == 0) {
            this.trackListedPlayers();
            this.reviewSpells();
        }
        if (this.tickCounter % 5 == 0) {
            this.scanInvisiblePlayers();
        }
        this.pruneState();
    }



    public void handleOutbound(Packet<?> packet) {
        if (packet == null) {
            return;
        }
        if (packet instanceof PlayerInteractBlockC2SPacket) {
            PlayerInteractBlockC2SPacket useOn = (PlayerInteractBlockC2SPacket)packet;
            this.lastLocalActionMs = System.currentTimeMillis();
            if (useOn.getBlockHitResult() != null && useOn.getBlockHitResult().getBlockPos() != null) {
                long until = System.currentTimeMillis() + 6000L;
                BlockPos hit = useOn.getBlockHitResult().getBlockPos();
                BlockPos adjacent = hit.offset(useOn.getBlockHitResult().getSide());
                this.selfPlacedBlocks.put(hit.asLong(), until);
                this.selfPlacedBlocks.put(adjacent.asLong(), until);
                String itemPath = "";
                if (AntiVanishModule.MC.player != null && useOn.getHand() != null) {
                    Identifier itemId = Registries.ITEM.getId(AntiVanishModule.MC.player.getStackInHand(useOn.getHand()).getItem());
                    itemPath = AntiVanishHeuristics.path(itemId == null ? "" : itemId.toString());
                }
                AntiVanishModule.markSelfMultiBlockFootprint(hit, itemPath, this.selfPlacedBlocks, until);
                AntiVanishModule.markSelfMultiBlockFootprint(adjacent, itemPath, this.selfPlacedBlocks, until);
            }
            return;
        }
        if (packet instanceof PlayerActionC2SPacket || packet instanceof PlayerInteractItemC2SPacket || packet instanceof PlayerInteractEntityC2SPacket || packet instanceof HandSwingC2SPacket) {
            this.lastLocalActionMs = System.currentTimeMillis();
        }
    }

    static void markSelfMultiBlockFootprint(BlockPos pos, String path, Map<Long, Long> targets, long until) {
        boolean vertical;
        if (pos == null || targets == null) {
            return;
        }
        targets.put(pos.asLong(), until);
        String id = path == null ? "" : path;
        boolean bl = vertical = id.endsWith("_door") && !id.endsWith("trapdoor") || id.contains("sunflower") || id.contains("lilac") || id.contains("rose_bush") || id.contains("peony") || id.contains("tall_grass") || id.contains("large_fern") || id.contains("pitcher_plant");
        if (vertical) {
            targets.put(pos.up().asLong(), until);
            targets.put(pos.down().asLong(), until);
        }
        if (id.endsWith("_bed")) {
            targets.put(pos.add(1, 0, 0).asLong(), until);
            targets.put(pos.add(-1, 0, 0).asLong(), until);
            targets.put(pos.add(0, 0, 1).asLong(), until);
            targets.put(pos.add(0, 0, -1).asLong(), until);
        }
    }



    public boolean handleInbound(Packet<?> packet) {
        PlayerListS2CPacket info;
        PlayerListS2CPacket info2;
        if (packet instanceof PlayerRemoveS2CPacket) {
            PlayerRemoveS2CPacket remove = (PlayerRemoveS2CPacket)packet;
            if (remove.profileIds().size() < 4) {
                for (UUID id : remove.profileIds()) {
                    this.enqueue(Observation.tabRemove(id));
                }
            }
        } else if (packet instanceof PlayerListS2CPacket && (info2 = (PlayerListS2CPacket)packet).getActions().contains(PlayerListS2CPacket.Action.UPDATE_LISTED)) {
            int unlisted = 0;
            for (PlayerListS2CPacket.Entry entry : info2.getEntries()) {
                if (entry == null || entry.listed()) continue;
                ++unlisted;
            }
            if (unlisted > 0 && unlisted < 4) {
                for (PlayerListS2CPacket.Entry entry : info2.getEntries()) {
                    if (entry == null || entry.listed()) continue;
                    this.enqueue(Observation.tabHide(entry.profileId()));
                }
            }
        } else if (packet instanceof GameMessageS2CPacket) {
            String departedName;
            String text;
            GameMessageS2CPacket chat = (GameMessageS2CPacket)packet;
            String string = text = chat.content() == null ? "" : chat.content().getString();
            if (!text.isBlank()) {
                this.enqueue(Observation.systemChat(text));
            }
            if (!(departedName = AntiVanishModule.departedPlayerName(chat.content())).isBlank()) {
                this.enqueue(Observation.playerLeft(departedName));
            }
        } else {
            CommandSuggestionsS2CPacket suggestions;
            if (packet instanceof CommandSuggestionsS2CPacket && this.completionRequestIds.remove((suggestions = (CommandSuggestionsS2CPacket)packet).id())) {
                ArrayList<String> names = new ArrayList<String>();
                for (Suggestion suggestion : suggestions.getSuggestions().getList()) {
                    String text = suggestion.getText();
                    if (text == null || text.isBlank()) continue;
                    names.add(text.trim());
                }
                this.pendingCompletionNames = names;
                return true;
            }
            if (packet instanceof EntityTrackerUpdateS2CPacket) {
                EntityTrackerUpdateS2CPacket metadata = (EntityTrackerUpdateS2CPacket)packet;
                this.enqueue(Observation.entityMetadata(metadata.id()));
            } else if (packet instanceof ExplosionS2CPacket) {
                ExplosionS2CPacket explosion = (ExplosionS2CPacket)packet;
                this.enqueue(Observation.explosion(explosion.center(), explosion.radius()));
            } else if (packet instanceof PlaySoundFromEntityS2CPacket) {
                PlaySoundFromEntityS2CPacket sound = (PlaySoundFromEntityS2CPacket)packet;
                this.enqueue(Observation.entitySound(sound.getEntityId(), AntiVanishModule.soundId(((SoundEvent)sound.getSound().value()).id())));
            } else if (packet instanceof ParticleS2CPacket) {
                ParticleS2CPacket particles = (ParticleS2CPacket)packet;
                Identifier id = Registries.PARTICLE_TYPE.getId(particles.getParameters().getType());
                if(AntiVanishHeuristics.suspiciousParticle(id==null?"":id.toString()))
                    this.enqueue(Observation.position(ObservationType.PARTICLE, particles.getX(), particles.getY(), particles.getZ(), id == null ? "" : id.toString()));
            } else if (packet instanceof PlaySoundS2CPacket) {
                PlaySoundS2CPacket sound = (PlaySoundS2CPacket)packet;
                this.onSoundPacket(sound);
            }
        }
        if (packet instanceof PlayerListS2CPacket update && update.getActions().contains(PlayerListS2CPacket.Action.UPDATE_GAME_MODE)) this.queueGamemodeUpdates(update);
        return false;
    }

    private void onSoundPacket(PlaySoundS2CPacket packet) {
        if (packet == null || packet.getSound() == null || packet.getSound().value() == null) {
            return;
        }
        String id = AntiVanishModule.soundId(((SoundEvent)packet.getSound().value()).id());
        if(!AntiVanishHeuristics.suspiciousSound(id))return;
        this.enqueue(Observation.position(ObservationType.POSITIONAL_SOUND, packet.getX(), packet.getY(), packet.getZ(), id));
    }

    public static boolean shouldShowHud() {
        AntiVanishModule module = AntiVanishModule.instance();
        return module != null && module.isEnabled() && module.hasHudContent();
    }

    private boolean hasHudContent() {
        long now = System.currentTimeMillis();
        if (now < this.criticalUntilMs) {
            return true;
        }
        for (Detection detection : this.detections.values()) {
            if (detection.expiresAt <= now || !AntiVanishModule.detectionWorthShowing(detection)) continue;
            return true;
        }
        return false;
    }

    private static boolean detectionWorthShowing(Detection detection) {
        if (detection == null) {
            return false;
        }
        String name = detection.name == null ? "" : detection.name.trim();
        return !name.isBlank() && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name) && !"CRITICAL".equalsIgnoreCase(name);
    }

    public static String hudTag(HudEntry entry) {
        String lower;
        if (entry == null) {
            return "WATCH";
        }
        if ("CRITICAL".equalsIgnoreCase(entry.name())) {
            return "ALERT";
        }
        String string = lower = entry.reason() == null ? "" : entry.reason().toLowerCase(Locale.ROOT);
        if (lower.startsWith("vanish event")) {
            return "VANISH";
        }
        if (lower.startsWith("invisible entity")) {
            return "INVIS";
        }
        if (lower.startsWith("suspicious sound")) {
            return "SOUND";
        }
        if (lower.startsWith("ghost particle")) {
            return "PARTICLE";
        }
        return "WATCH";
    }

    public static String hudValue(HudEntry entry) {
        String name;
        if (entry == null) {
            return "Staff";
        }
        if ("CRITICAL".equalsIgnoreCase(entry.name())) {
            return AntiVanishModule.shortName(entry.reason());
        }
        String string = name = entry.name() == null ? "" : entry.name().trim();
        if (name.isBlank() || "Unknown".equalsIgnoreCase(name) || "You".equalsIgnoreCase(name)) {
            return "Staff";
        }
        return AntiVanishModule.shortName(name);
    }

    private static String shortName(String name) {
        String shown = name == null ? "" : NameFilter.apply(name.trim());
        return shown.length() <= 16 ? shown : shown.substring(0, 16);
    }

    public static List<HudEntry> hudEntries() {
        AntiVanishModule module = AntiVanishModule.instance();
        return module == null || !AntiVanishModule.shouldShowHud() ? List.of() : module.hudSnapshot();
    }

    private static AntiVanishModule instance() {
        return ModuleManager.get(AntiVanishModule.class);
    }

    private void drainObservations() {
        Observation observation;
        for (int i = 0; i < 512 && (observation = this.observations.poll()) != null; ++i) {
            queued--;
            this.processObservation(observation);
        }
        while (this.observations.size() > 4096) {
            this.observations.poll();
        }
    }

    private void processObservation(Observation observation) {
        switch (observation.type.ordinal()) {
            case 0: {
                this.handleTabRemoval(observation.profileId);
                break;
            }
            case 1: {
                this.handleTabHidden(observation.profileId);
                break;
            }
            case 2: {
                this.serverSendsLeaveMessages = true;
                this.confirmDeparture(observation.detail);
                break;
            }
            case 3: {
                this.cacheRecentMessage(observation.detail);
                break;
            }
            case 4: {
                this.inspectInvisibleEntity(observation.entityId);
                break;
            }
            case 5: {
                this.inspectPositionalSound(observation);
                break;
            }
            case 6: {
                this.inspectEntitySound(observation);
                break;
            }
            case 7: {
                this.inspectParticle(observation);
                break;
            }
            case 8: {
                this.rememberExplosion(observation);
                break;
            }
            case 9: {
                this.announceGamemode(observation.profileId, observation.detail);
            }
        }
    }

    private void trackListedPlayers() {
        if (MC.getNetworkHandler() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PlayerListEntry info : MC.getNetworkHandler().getListedPlayerListEntries()) {
            if (info == null || info.getProfile() == null || info.getProfile().id() == null) continue;
            UUID uuid = info.getProfile().id();
            if (AntiVanishModule.MC.player != null && AntiVanishModule.MC.player.getUuid().equals(uuid)) continue;
            this.listedSinceMs.putIfAbsent(uuid, now);
            String name = info.getProfile().name();
            if (name != null && !this.knownPlayers.containsKey(uuid)) {
                this.knownPlayers.put(uuid, new KnownPlayer(uuid, name));
            }
            if (name == null || this.hiddenSpells.isEmpty()) continue;
            this.endSpell(name, false, 1000L);
        }
        if (this.listedSinceMs.size() > 1024 || this.knownPlayers.size() > 1024) {
            HashSet<UUID> connected = new HashSet<UUID>();
            for (PlayerListEntry info : MC.getNetworkHandler().getPlayerList()) {
                if (info == null || info.getProfile() == null) continue;
                connected.add(info.getProfile().id());
            }
            this.listedSinceMs.keySet().retainAll(connected);
            this.knownPlayers.keySet().retainAll(connected);
        }
    }

    private boolean trustedListed(UUID uuid) {
        Long since = uuid == null ? null : this.listedSinceMs.get(uuid);
        return since != null && System.currentTimeMillis() - since >= 1500L;
    }

    private boolean credibleSubject(UUID uuid, String name) {
        return uuid != null && AntiVanishText.isUsername(name) && StaffContext.isStaffName(name) && this.trustedListed(uuid);
    }

    private void handleTabRemoval(UUID uuid) {
        if (uuid == null || AntiVanishModule.MC.player.getUuid().equals(uuid)) {
            return;
        }
        String name = this.knownName(uuid);
        if (!this.credibleSubject(uuid, name) || !AntiVanishText.isPlausiblePlayerName(name)) {
            return;
        }
        this.pendingVanishes.removeIf(pending -> pending.uuid.equals(uuid));
        this.pendingVanishes.addLast(new PendingVanish(uuid, name, this.tickCounter + 20));
    }

    private void handleTabHidden(UUID uuid) {
        String name;
        if (uuid == null || AntiVanishModule.MC.player.getUuid().equals(uuid)) {
            return;
        }
        KnownPlayer known = this.knownPlayers.get(uuid);
        String string = name = known != null && known.name != null ? known.name : this.knownName(uuid);
        if (!this.credibleSubject(uuid, name) || !AntiVanishText.isPlausiblePlayerName(name)) {
            return;
        }
        if (this.recentMessageNames(name)) {
            return;
        }
        this.reportHidden(uuid, name, false, "Vanish Event: hidden from TAB", "hidden from TAB", 100);
    }

    private String knownName(UUID uuid) {
        PlayerListEntry info;
        KnownPlayer known = this.knownPlayers.get(uuid);
        if (known != null && known.name != null && !known.name.isBlank()) {
            return known.name;
        }
        if (MC.getNetworkHandler() != null && (info = MC.getNetworkHandler().getPlayerListEntry(uuid)) != null && info.getProfile() != null) {
            return info.getProfile().name();
        }
        return null;
    }

    private UUID knownUuid(String name) {
        for (KnownPlayer known : this.knownPlayers.values()) {
            if (!known.name.equalsIgnoreCase(name)) continue;
            return known.uuid;
        }
        return null;
    }

    private void confirmDeparture(String displayedName) {
        long now = System.currentTimeMillis();
        for (KnownPlayer known : this.knownPlayers.values()) {
            if (!AntiVanishText.containsPlayerName(displayedName, known.name)) continue;
            this.confirmedDepartures.put(known.uuid, now + 5000L);
            this.pendingVanishes.removeIf(pending -> pending.uuid.equals(known.uuid));
            this.hiddenSpells.remove(AntiVanishModule.spellKey(known.name));
            Detection detection = this.detections.get(known.uuid.toString());
            if (detection != null && AntiVanishModule.tabDepartureReason(detection.reason)) {
                this.detections.remove(known.uuid.toString());
            }
            this.signals.removeIf(signal -> signal.type == SignalType.VANISH && signal.subject.equalsIgnoreCase(known.name) && AntiVanishModule.tabDepartureReason(signal.reason));
        }
    }

    private void processPendingVanishes() {
        while (!this.pendingVanishes.isEmpty() && this.pendingVanishes.peekFirst().dueTick <= this.tickCounter) {
            PendingVanish pending = this.pendingVanishes.removeFirst();
            if (MC.getNetworkHandler().getPlayerListEntry(pending.uuid) != null) continue;
            long now = System.currentTimeMillis();
            if (this.confirmedDepartures.getOrDefault(pending.uuid, 0L) > now) continue;
            if (this.recentMessageNames(pending.name)) {
                this.serverSendsLeaveMessages = true;
                continue;
            }
            PlayerEntity remaining = AntiVanishModule.MC.world.getPlayerByUuid(pending.uuid);
            if (remaining != null && !remaining.isRemoved()) {
                this.reportHidden(pending.uuid, pending.name, false, "Vanish Event: entity remained", "off TAB, still nearby", 100);
                continue;
            }
            String reason = "Vanish Event: silent TAB disappearance";
            int score = AntiVanishModule.silentTabRemovalScore(this.serverSendsLeaveMessages);
            if (this.serverSendsLeaveMessages) {
                this.reportHidden(pending.uuid, pending.name, false, reason, "left TAB silently", score);
                continue;
            }
            RegionProof proof = this.regionProof(pending.uuid);
            if (proof == null) continue;
            this.signals.addLast(new Signal(SignalType.VANISH, pending.name, proof, reason, score, now));
            this.evaluateCritical();
        }
    }

    static int silentTabRemovalScore(boolean serverSendsLeaveMessages) {
        return serverSendsLeaveMessages ? 100 : 30;
    }

    static boolean tabDepartureReason(String reason) {
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.startsWith("vanish event:") && (lower.contains("tab") || lower.contains("entity remained") || lower.contains("no leave packet"));
    }

    private void cacheRecentMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        this.recentMessages.addLast(new RecentMessage(text, now));
        while (!(this.recentMessages.isEmpty() || now - this.recentMessages.peekFirst().atMs <= 8000L && this.recentMessages.size() <= 64)) {
            this.recentMessages.removeFirst();
        }
    }

    private boolean recentMessageNames(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (RecentMessage message : this.recentMessages) {
            if (now - message.atMs > 8000L || !AntiVanishText.looksLikeLeaveMessage(message.text, name)) continue;
            return true;
        }
        return false;
    }

    private void sendCompletionProbe() {
        if (MC.getNetworkHandler() == null) {
            return;
        }
        String command = PROBE_COMMAND;
        int id = this.nextCompletionId++;
        if (this.nextCompletionId > 40000) {
            this.nextCompletionId = 30000;
        }
        this.completionRequestIds.add(id);
        while (this.completionRequestIds.size() > 8) {
            this.completionRequestIds.remove(this.completionRequestIds.iterator().next());
        }
        try {
            MC.getNetworkHandler().sendPacket((Packet)new RequestCommandCompletionsC2SPacket(id, command.trim() + " "));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private void processCompletionProbe() {
        List<String> current = this.pendingCompletionNames;
        if (current == null) {
            return;
        }
        this.pendingCompletionNames = null;
        if (MC.getNetworkHandler() == null || AntiVanishModule.MC.player == null) {
            return;
        }
        HashSet<String> tabNames = new HashSet<String>();
        for (PlayerListEntry info : MC.getNetworkHandler().getPlayerList()) {
            if (info == null || info.getProfile() == null || info.getProfile().name() == null) continue;
            tabNames.add(info.getProfile().name().toLowerCase(Locale.ROOT));
        }
        String self = AntiVanishModule.MC.player.getName().getString();
        ArrayList<String> hidden = new ArrayList<String>();
        for (String name : current) {
            if (!StaffContext.isStaffName(name) || !AntiVanishText.isPlausiblePlayerName(name) || name.equalsIgnoreCase(self) || tabNames.contains(name.toLowerCase(Locale.ROOT)) || this.recentMessageNames(name)) continue;
            hidden.add(name);
        }
        if (hidden.isEmpty() || hidden.size() > 3) {
            return;
        }
        for (String name : hidden) {
            this.reportHidden(this.knownUuid(name), name, false, "Vanish Event: hidden but targetable", "off TAB, still targetable", 90);
        }
    }

    private static String departedPlayerName(Text component) {
        String string;
        TranslatableTextContent translated;
        TextContent class_74172;
        if (component == null || !((class_74172 = component.getContent()) instanceof TranslatableTextContent) || !"multiplayer.player.left".equals((translated = (TranslatableTextContent)class_74172).getKey())) {
            return "";
        }
        Object[] args = translated.getArgs();
        if (args.length == 0 || args[0] == null) {
            return "";
        }
        Object object = args[0];
        if (object instanceof Text) {
            Text name = (Text)object;
            string = name.getString();
        } else {
            string = String.valueOf(args[0]);
        }
        return string;
    }

    private void reportHidden(UUID uuid, String name, boolean spectator, String reason, String detail, int score) {
        this.upsertDetection(AntiVanishModule.detectionKey(uuid, name), name, reason, score, System.currentTimeMillis() + 20000L);
        HiddenSpell spell = this.hiddenSpells.get(AntiVanishModule.spellKey(name));
        if (spell != null && spell.spectator == spectator) {
            return;
        }
        this.hiddenSpells.put(AntiVanishModule.spellKey(name), new HiddenSpell(uuid, name, spectator, detail, this.regionProof(uuid), this.tickCounter));
    }

    private void reviewSpells() {
        Iterator<HiddenSpell> it = this.hiddenSpells.values().iterator();
        while (it.hasNext()) {
            HiddenSpell spell = it.next();
            if (spell.spectator && MC.getNetworkHandler().getPlayerListEntry(spell.uuid) == null) {
                it.remove();
                continue;
            }
            if (spell.watched || spell.sinceTick >= this.tickCounter) continue;
            RegionProof proof = this.regionProof(spell.uuid);
            if (proof == null && !spell.told) {
                proof = spell.proof;
            }
            if (proof != null) {
                spell.told = true;
                spell.watched = true;
                this.watchingYou(spell.name, proof);
                continue;
            }
            if (spell.told) continue;
            spell.told = true;
            this.news(spell.name, spell.how(), spell.detail);
        }
    }

    private boolean endSpell(String name, boolean spectator, long settleMs) {
        HiddenSpell spell = this.hiddenSpells.get(AntiVanishModule.spellKey(name));
        if (spell == null || spell.spectator != spectator || System.currentTimeMillis() - spell.sinceMs < settleMs) {
            return false;
        }
        this.hiddenSpells.remove(AntiVanishModule.spellKey(name));
        this.detections.remove(AntiVanishModule.detectionKey(spell.uuid, spell.name));
        if (spell.told) {
            this.news(spell.name, ModuleChat.body().text("is ").good("visible again").build(), "");
        }
        return true;
    }

    private RegionProof regionProof(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerEntity body = AntiVanishModule.MC.world.getPlayerByUuid(uuid);
        if (body != null && body != AntiVanishModule.MC.player) {
            return new RegionProof(uuid, Math.round(body.distanceTo((Entity)AntiVanishModule.MC.player)));
        }
        return StaffContext.isInYourRegion(uuid) ? new RegionProof(uuid, -1) : null;
    }

    private static String spellKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String detectionKey(UUID uuid, String name) {
        return uuid != null ? uuid.toString() : name;
    }

    private void scanInvisiblePlayers() {
        double rangeSq = this.sensorRangeSq();
        for (PlayerEntity player : AntiVanishModule.MC.world.getPlayers()) {
            if (player == null || player == AntiVanishModule.MC.player || !player.isInvisible() || player.squaredDistanceTo((Entity)AntiVanishModule.MC.player) > rangeSq || !this.realPlayer(player)) continue;
            this.triggerSensor(SignalType.INVISIBLE, player, "Invisible Entity: metadata flag", 35, 5000L);
        }
    }

    private void inspectInvisibleEntity(int entityId) {
        PlayerEntity player;
        Entity entity = AntiVanishModule.MC.world.getEntityById(entityId);
        if (!(entity instanceof PlayerEntity) || (player = (PlayerEntity)entity) == AntiVanishModule.MC.player || !player.isInvisible()) {
            return;
        }
        if (player.squaredDistanceTo((Entity)AntiVanishModule.MC.player) > this.sensorRangeSq()) {
            return;
        }
        if (!this.realPlayer(player)) {
            return;
        }
        this.triggerSensor(SignalType.INVISIBLE, player, "Invisible Entity: metadata flag", 35, 5000L);
    }

    private boolean realPlayer(PlayerEntity player) {
        return this.credibleSubject(player.getUuid(), AntiVanishModule.profileName(player));
    }

    private static String profileName(PlayerEntity player) {
        return player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
    }

    private void inspectPositionalSound(Observation observation) {
        if (!AntiVanishHeuristics.suspiciousSound(observation.detail)) {
            return;
        }
        Vec3d source = observation.position();
        long now = System.currentTimeMillis();
        if (!this.nearPlayer(source) || this.hasVisibleCause(source) || this.isExplosionRelated(source) || this.isPoweredMechanism(source, observation.detail) || now - this.lastLocalActionMs < 1000L || this.recentlySelfPlaced(source) || this.villagerToggledDoor(observation.detail, source) || this.selfContainerActive() && AntiVanishModule.isContainerSignal(observation.detail)) {
            return;
        }
        this.triggerSensor(SignalType.SOUND, this.locatedSubject(source), "Suspicious Sound: " + AntiVanishModule.shortId(observation.detail), 14, 3000L);
    }

    private void inspectEntitySound(Observation observation) {
        PlayerEntity player;
        if (!AntiVanishHeuristics.suspiciousSound(observation.detail)) {
            return;
        }
        Entity entity = AntiVanishModule.MC.world.getEntityById(observation.entityId);
        if (entity instanceof PlayerEntity && (player = (PlayerEntity)entity) != AntiVanishModule.MC.player && player.isInvisible() && player.squaredDistanceTo((Entity)AntiVanishModule.MC.player) <= this.sensorRangeSq() && this.realPlayer(player)) {
            this.triggerSensor(SignalType.SOUND, player, "Suspicious Sound: invisible source", 16, 3000L);
        }
    }

    private void inspectParticle(Observation observation) {
        if (!AntiVanishHeuristics.suspiciousParticle(observation.detail)) {
            return;
        }
        Vec3d source = observation.position();
        long now = System.currentTimeMillis();
        if (!this.nearPlayer(source) || this.hasVisibleCause(source) || this.isExplosionRelated(source) || now - this.lastLocalActionMs < 900L || this.hasAmbientParticleSource(source, observation.detail)) {
            return;
        }
        String particle = AntiVanishModule.shortId(observation.detail);
        if ((particle.equals("block") || particle.contains("smoke")) && this.nearSelf(source, 6.25)) {
            return;
        }
        if ((particle.equals("block") || particle.contains("smoke")) && !this.particleBurstReady(particle, now)) {
            return;
        }
        this.triggerSensor(SignalType.PARTICLE, this.locatedSubject(source), "Ghost Particle: " + AntiVanishModule.shortId(observation.detail), 16, 3000L);
    }

    private boolean villagerToggledDoor(String blockId, Vec3d source) {
        boolean doorLike;
        String path = AntiVanishHeuristics.path(blockId);
        boolean bl = doorLike = path.contains("door") && !path.contains("trapdoor") || path.contains("fence_gate");
        if (!doorLike || source == null || AntiVanishModule.MC.world == null) {
            return false;
        }
        for (Entity entity : AntiVanishModule.MC.world.getEntities()) {
            if (entity.getType() != EntityType.VILLAGER || !(entity.getEntityPos().squaredDistanceTo(source) <= 9.0)) continue;
            return true;
        }
        return false;
    }

    private boolean recentlySelfPlaced(Vec3d source) {
        if (source == null) {
            return false;
        }
        Long until = this.selfPlacedBlocks.get(BlockPos.ofFloored((Position)source).asLong());
        return until != null && until > System.currentTimeMillis();
    }

    private void rememberExplosion(Observation observation) {
        long now = System.currentTimeMillis();
        this.recentExplosions.addLast(new ExplosionEvent(observation.position(), Math.max(2.0, AntiVanishModule.parseDouble(observation.detail, 4.0) + 4.0), now));
        while (this.recentExplosions.size() > 8) {
            this.recentExplosions.removeFirst();
        }
    }

    private boolean particleBurstReady(String particle, long now) {
        Deque burst = this.weakParticleBursts.computeIfAbsent(particle, ignored -> new ArrayDeque());
        burst.addLast(now);
        while (!burst.isEmpty() && now - (Long)burst.peekFirst() > 2000L) {
            burst.removeFirst();
        }
        if (burst.size() < 2) {
            return false;
        }
        burst.clear();
        return true;
    }

    private void triggerSensor(SignalType type, String where, String reason, int weight, long cooldownMs) {
        this.addSignal(type, where, null, reason, weight, cooldownMs);
    }

    private void triggerSensor(SignalType type, PlayerEntity player, String reason, int weight, long cooldownMs) {
        this.addSignal(type, AntiVanishModule.profileName(player), this.regionProof(player.getUuid()), reason, weight, cooldownMs);
    }

    private void addSignal(SignalType type, String subject, RegionProof proof, String reason, int weight, long cooldownMs) {
        String cooldownKey;
        long last;
        long now = System.currentTimeMillis();
        if (now - (last = this.signalCooldowns.getOrDefault(cooldownKey = type.name() + "|" + subject.toLowerCase(Locale.ROOT), 0L).longValue()) < cooldownMs) {
            return;
        }
        this.signalCooldowns.put(cooldownKey, now);
        this.signals.addLast(new Signal(type, subject, proof, reason, weight, now));
        this.upsertDetection("signal:" + cooldownKey, subject, reason, weight, now + 20000L);
        this.announceTrigger(cooldownKey, subject, proof != null, reason);
        this.evaluateCritical();
    }

    private void evaluateCritical() {
        long now = System.currentTimeMillis();
        this.pruneSignals(now);
        EnumMap<SignalType, Integer> strongest = new EnumMap<SignalType, Integer>(SignalType.class);
        Signal watcher = null;
        for (Signal signal : this.signals) {
            strongest.merge(signal.type, signal.weight, Math::max);
            if (signal.proof == null) continue;
            watcher = signal;
        }
        int score = strongest.values().stream().mapToInt(Integer::intValue).sum();
        if (watcher == null || strongest.size() < 2 || score < 35) {
            return;
        }
        if (now - this.lastCriticalMs < 12000L) {
            return;
        }
        this.watchingYou(watcher.subject, watcher.proof);
    }

    private void watchingYou(String name, RegionProof proof) {
        long now;
        this.lastCriticalMs = now = System.currentTimeMillis();
        this.criticalUntilMs = now + 7000L;
        this.criticalWatcher = name;
        this.pendingChimes.add(proof.id());
        this.ghostId = proof.id();
        this.ghostName = name;
    }

    private void playPendingChime() {
        if (this.pendingChimes.isEmpty()) {
            return;
        }
        boolean sirenCovers = this.pendingChimes.stream().allMatch(StaffContext::alarmedRecently);
        this.pendingChimes.clear();
        if (sirenCovers) {
            return;
        }
        if (sound.get() && MC.getSoundManager() != null) {
            MC.player.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,.6f,1.0f);
        }
        if(alerts.get()) StaffGhost.watching(this.ghostId, this.ghostName, 0);
    }

    private void news(String name, Text what, String detail) {
        ModuleChat.Body line = ModuleChat.body().name(NameFilter.apply(name)).text(" ").append(what);
        if (!detail.isEmpty()) {
            line.text(" (" + detail + ")");
        }
        ModuleChat.send((ClientModule)this, line);
    }

    private void queueGamemodeUpdates(PlayerListS2CPacket info) {
        if (info.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
            return;
        }
        for (PlayerListS2CPacket.Entry entry : info.getEntries()) {
            if (entry == null || entry.gameMode() == null || entry.profileId() == null) continue;
            this.enqueue(Observation.gamemode(entry.profileId(), entry.gameMode().getId()));
        }
    }

    private void announceGamemode(UUID uuid, String gameMode) {
        if (uuid == null || AntiVanishModule.MC.player != null && AntiVanishModule.MC.player.getUuid().equals(uuid)) {
            return;
        }
        KnownPlayer known = this.knownPlayers.get(uuid);
        if (known == null || !StaffContext.isStaffName(known.name)) {
            return;
        }
        if (GameMode.SPECTATOR.getId().equals(gameMode)) {
            if (this.credibleSubject(uuid, known.name)) {
                this.reportHidden(uuid, known.name, true, "Vanish Event: switched to spectator", "", 100);
                return;
            }
        } else if (this.endSpell(known.name, true, 0L)) {
            return;
        }
        ModuleChat.send((ClientModule)this, StaffContext.switchedTo(ModuleChat.body().name(NameFilter.apply(known.name)).text(" "), gameMode));
    }

    private void announceTrigger(String eventKey, String subject, boolean named, String reason) {
        boolean located;
        long now = System.currentTimeMillis();
        Long last = this.announceCooldowns.get(eventKey);
        if (last != null && now - last < 1500L) {
            return;
        }
        while (!this.announceTimes.isEmpty() && now - this.announceTimes.peekFirst() > 4000L) {
            this.announceTimes.removeFirst();
        }
        if (this.announceTimes.size() >= 6) {
            return;
        }
        this.announceCooldowns.put(eventKey, now);
        this.announceTimes.addLast(now);
        int colon = reason.indexOf(": ");
        ModuleChat.Body line = colon < 0 ? ModuleChat.body().warn(reason) : ModuleChat.body().warn(reason.substring(0, colon)).text(reason.substring(colon));
        boolean bl = located = subject != null && !subject.isBlank() && !"Unknown".equalsIgnoreCase(subject) && !"CRITICAL".equalsIgnoreCase(subject);
        if (located) {
            line.text(" (");
            if (named) {
                line.name(NameFilter.apply(subject));
            } else {
                line.value(subject);
            }
            line.text(")");
        }
        ModuleChat.send((ClientModule)this, line);
    }

    private void upsertDetection(String key, String name, String reason, int score, long expiresAt) {
        Detection existing = this.detections.get(key);
        if (existing == null) {
            this.detections.put(key, new Detection(name, reason, score, expiresAt));
            return;
        }
        existing.name = name;
        existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
        if (score >= existing.score) {
            existing.reason = reason;
            existing.score = score;
        }
    }

    private List<HudEntry> hudSnapshot() {
        long now = System.currentTimeMillis();
        ArrayList<HudEntry> out = new ArrayList<HudEntry>();
        if (now < this.criticalUntilMs) {
            out.add(new HudEntry("CRITICAL", this.criticalWatcher));
        }
        HashSet seenRows = new HashSet();
        this.detections.values().stream().filter(detection -> detection.expiresAt > now).filter(AntiVanishModule::detectionWorthShowing).sorted(Comparator.comparingInt((Detection detection) -> detection.score).reversed().thenComparing(detection -> detection.name, String.CASE_INSENSITIVE_ORDER)).map(detection -> new HudEntry(detection.name, detection.reason)).filter(entry -> seenRows.add(AntiVanishModule.hudTag(entry) + "\u0000" + AntiVanishModule.hudValue(entry))).limit(4L).forEach(out::add);
        return List.copyOf(out);
    }

    private void pruneState() {
        long now = System.currentTimeMillis();
        this.detections.entrySet().removeIf(entry -> ((Detection)entry.getValue()).expiresAt <= now);
        this.signalCooldowns.entrySet().removeIf(entry -> now - (Long)entry.getValue() > 60000L);
        this.announceCooldowns.entrySet().removeIf(entry -> now - (Long)entry.getValue() > 60000L);
        this.selfPlacedBlocks.entrySet().removeIf(entry -> (Long)entry.getValue() <= now);
        this.confirmedDepartures.entrySet().removeIf(entry -> (Long)entry.getValue() <= now);
        this.automatedMechanisms.entrySet().removeIf(entry -> (Long)entry.getValue() <= now);
        this.weakParticleBursts.values().removeIf(burst -> {
            while (!burst.isEmpty() && now - (Long)burst.peekFirst() > 2000L) {
                burst.removeFirst();
            }
            return burst.isEmpty();
        });
        while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs > 3000L) {
            this.recentExplosions.removeFirst();
        }
        this.pruneSignals(now);
    }

    private void pruneSignals(long now) {
        while (!this.signals.isEmpty() && now - this.signals.peekFirst().timeMs > 15000L) {
            this.signals.removeFirst();
        }
    }

    private boolean nearPlayer(Vec3d source) {
        return source != null && source.squaredDistanceTo(AntiVanishModule.MC.player.getEntityPos()) <= this.sensorRangeSq();
    }

    private boolean nearSelf(Vec3d source, double maxDistSq) {
        return source != null && AntiVanishModule.MC.player != null && source.squaredDistanceTo(AntiVanishModule.MC.player.getEntityPos()) < maxDistSq;
    }

    private boolean selfContainerActive() {
        return System.currentTimeMillis() - this.lastContainerActivityMs < 2500L;
    }

    private static boolean isContainerSignal(String id) {
        String path = AntiVanishHeuristics.path(id);
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
    }

    private String locatedSubject(Vec3d source) {
        if (source == null || AntiVanishModule.MC.player == null) {
            return "nearby";
        }
        Vec3d me = AntiVanishModule.MC.player.getEntityPos();
        double dx = source.x - me.x;
        double dy = source.y - me.y;
        double dz = source.z - me.z;
        long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        String dir = AntiVanishModule.compass(dx, dz);
        return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
    }

    private static String compass(double dx, double dz) {
        String ns = dz < -1.0 ? "N" : dz > 1.0 ? "S" : "";
        String ew = dx > 1.0 ? "E" : (dx < -1.0 ? "W" : "");
        return ns + ew;
    }

    private boolean hasVisibleCause(Vec3d source) {
        if (source == null) {
            return true;
        }
        for (Entity entity : AntiVanishModule.MC.world.getEntities()) {
            PlayerEntity player;
            if (entity == AntiVanishModule.MC.player || !(entity instanceof PlayerEntity ? !(player = (PlayerEntity)entity).isInvisible() && entity.getEntityPos().squaredDistanceTo(source) <= 16.0 : entity instanceof ProjectileEntity && entity.getEntityPos().squaredDistanceTo(source) <= 16.0)) continue;
            return true;
        }
        return false;
    }

    private boolean isExplosionRelated(Vec3d source) {
        if (source == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        while (!this.recentExplosions.isEmpty() && now - this.recentExplosions.peekFirst().timeMs > 3000L) {
            this.recentExplosions.removeFirst();
        }
        for (ExplosionEvent explosion : this.recentExplosions) {
            if (!(explosion.center.squaredDistanceTo(source) <= explosion.radius * explosion.radius)) continue;
            return true;
        }
        return false;
    }

    private boolean hasAmbientParticleSource(Vec3d source, String particleId) {
        if (source == null || !AntiVanishModule.shortId(particleId).contains("smoke")) {
            return false;
        }
        BlockPos center = BlockPos.ofFloored((Position)source);
        for (int dx = -2; dx <= 2; ++dx) {
            for (int dy = -2; dy <= 2; ++dy) {
                for (int dz = -2; dz <= 2; ++dz) {
                    BlockPos pos = center.add(dx, dy, dz);
                    Identifier id = Registries.BLOCK.getId(AntiVanishModule.MC.world.getBlockState(pos).getBlock());
                    String path = AntiVanishModule.shortId(id == null ? "" : id.toString());
                    if (!path.contains("campfire") && !path.contains("furnace") && !path.contains("smoker") && !path.contains("torch") && !path.contains("fire") && !path.contains("candle") && !path.contains("respawn_anchor")) continue;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPoweredMechanism(Vec3d source, String soundId) {
        String path = AntiVanishModule.shortId(soundId);
        if (!path.contains("door") && !path.contains("trapdoor")) {
            return false;
        }
        BlockPos center = BlockPos.ofFloored((Position)source);
        long now = System.currentTimeMillis();
        for (int dy = -1; dy <= 1; ++dy) {
            boolean powered;
            BlockPos pos = center.add(0, dy, 0);
            BlockState state = AntiVanishModule.MC.world.getBlockState(pos);
            long key = pos.asLong();
            boolean bl = powered = state.contains((Property)Properties.POWERED) && (Boolean)state.get((Property)Properties.POWERED) != false;
            if (powered || AntiVanishModule.MC.world.isReceivingRedstonePower(pos)) {
                this.automatedMechanisms.put(key, now + 5000L);
                return true;
            }
            if (this.automatedMechanisms.getOrDefault(key, 0L) <= now) continue;
            return true;
        }
        return false;
    }

    private double sensorRangeSq() {
        double r = this.range.get();
        return r * r;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        }
        catch (Exception ignored) {
            return fallback;
        }
    }

    private static String soundId(Identifier id) {
        return id == null ? "" : id.toString();
    }

    private static String shortId(String id) {
        return AntiVanishHeuristics.path(id);
    }

    private void resetRuntime() {
        this.observations.clear();
        this.queued = 0;
        this.knownPlayers.clear();
        this.detections.clear();
        this.signalCooldowns.clear();
        this.announceCooldowns.clear();
        this.announceTimes.clear();
        this.selfPlacedBlocks.clear();
        this.confirmedDepartures.clear();
        this.automatedMechanisms.clear();
        this.weakParticleBursts.clear();
        this.signals.clear();
        this.recentExplosions.clear();
        this.pendingVanishes.clear();
        this.hiddenSpells.clear();
        this.shard.clear();
        this.tickCounter = 0;
        this.lastLocalActionMs = 0L;
        this.lastContainerActivityMs = 0L;
        this.lastCriticalMs = 0L;
        this.criticalUntilMs = 0L;
        this.criticalWatcher = "";
        this.pendingChimes.clear();
        this.ghostId = null;
        this.ghostName = null;
        this.recentMessages.clear();
        this.listedSinceMs.clear();
        this.serverSendsLeaveMessages = false;
        this.completionRequestIds.clear();
        this.pendingCompletionNames = null;
        this.nextCompletionId = 30000;
    }

    @Environment(value=EnvType.CLIENT)
    private record Observation(ObservationType type, UUID profileId, int entityId, double x, double y, double z, String detail) {
        static Observation tabRemove(UUID id) {
            return new Observation(ObservationType.TAB_REMOVE, id, -1, 0.0, 0.0, 0.0, "");
        }

        static Observation tabHide(UUID id) {
            return new Observation(ObservationType.TAB_HIDE, id, -1, 0.0, 0.0, 0.0, "");
        }

        static Observation gamemode(UUID id, String mode) {
            return new Observation(ObservationType.GAMEMODE, id, -1, 0.0, 0.0, 0.0, mode);
        }

        static Observation playerLeft(String name) {
            return new Observation(ObservationType.PLAYER_LEFT, null, -1, 0.0, 0.0, 0.0, name);
        }

        static Observation systemChat(String text) {
            return new Observation(ObservationType.SYSTEM_CHAT, null, -1, 0.0, 0.0, 0.0, text);
        }

        static Observation entityMetadata(int id) {
            return new Observation(ObservationType.ENTITY_METADATA, null, id, 0.0, 0.0, 0.0, "");
        }

        static Observation explosion(Vec3d center, float radius) {
            return new Observation(ObservationType.EXPLOSION, null, -1, center.x, center.y, center.z, Float.toString(radius));
        }

        static Observation entitySound(int id, String sound) {
            return new Observation(ObservationType.ENTITY_SOUND, null, id, 0.0, 0.0, 0.0, sound);
        }

        static Observation position(ObservationType type, double x, double y, double z, String detail) {
            return new Observation(type, null, -1, x, y, z, detail);
        }

        Vec3d position() {
            return new Vec3d(this.x, this.y, this.z);
        }
    }

    @Environment(value=EnvType.CLIENT)
    private static enum ObservationType {
        TAB_REMOVE,
        TAB_HIDE,
        PLAYER_LEFT,
        SYSTEM_CHAT,
        ENTITY_METADATA,
        POSITIONAL_SOUND,
        ENTITY_SOUND,
        PARTICLE,
        EXPLOSION,
        GAMEMODE;

    }

    @Environment(value=EnvType.CLIENT)
    private static final class Detection {
        String name;
        String reason;
        int score;
        long expiresAt;

        Detection(String name, String reason, int score, long expiresAt) {
            this.name = name;
            this.reason = reason;
            this.score = score;
            this.expiresAt = expiresAt;
        }
    }

    @Environment(value=EnvType.CLIENT)
    public record HudEntry(String name, String reason) {
    }

    @Environment(value=EnvType.CLIENT)
    private record KnownPlayer(UUID uuid, String name) {
    }

    @Environment(value=EnvType.CLIENT)
    private record PendingVanish(UUID uuid, String name, int dueTick) {
    }

    @Environment(value=EnvType.CLIENT)
    private record RegionProof(UUID id, int distance) {
    }

    @Environment(value=EnvType.CLIENT)
    private record Signal(SignalType type, String subject, RegionProof proof, String reason, int weight, long timeMs) {
    }

    @Environment(value=EnvType.CLIENT)
    private static enum SignalType {
        VANISH,
        INVISIBLE,
        PARTICLE,
        SOUND;

    }

    @Environment(value=EnvType.CLIENT)
    private record RecentMessage(String text, long atMs) {
    }

    @Environment(value=EnvType.CLIENT)
    private static final class HiddenSpell {
        final UUID uuid;
        final String name;
        final boolean spectator;
        final String detail;
        final RegionProof proof;
        final int sinceTick;
        final long sinceMs = System.currentTimeMillis();
        boolean told;
        boolean watched;

        HiddenSpell(UUID uuid, String name, boolean spectator, String detail, RegionProof proof, int sinceTick) {
            this.uuid = uuid;
            this.name = name;
            this.spectator = spectator;
            this.detail = detail;
            this.proof = proof;
            this.sinceTick = sinceTick;
        }

        Text how() {
            return this.spectator ? StaffContext.switchedTo(ModuleChat.body(), GameMode.SPECTATOR.getId()).build() : ModuleChat.body().danger("vanished").build();
        }
    }

    @Environment(value=EnvType.CLIENT)
    private record ExplosionEvent(Vec3d center, double radius, long timeMs) {
    }
}

