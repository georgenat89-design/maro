package dev.maro.module.impl.visuals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.maro.gui.hud.BetterTexturesScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.textures.Modrinth;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.ResourcePackManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Better Textures: any resource pack on Modrinth, searched, downloaded and switched on from one
 * screen. Packs go into the normal resourcepacks folder, so they are ordinary packs afterwards too.
 * Turning the module off takes the packs it put on back off; turning it on puts them back.
 */
public class BetterTextures extends Module {
    private static BetterTextures instance;

    private final ButtonSetting browse = add(new ButtonSetting("Packs", "Search Modrinth for resource packs and switch them on", "Browse",
            () -> mc.setScreen(new BetterTexturesScreen(mc.currentScreen, this))));
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "A notification when a pack is ready or fails", true));

    /** A pack downloaded through here: which Modrinth project, and the file it is in. */
    public record Installed(String projectId, String title, String file, String iconUrl, int color, String versionNumber,
                            List<String> gameVersions) {
    }

    public enum Stage {FINDING, DOWNLOADING, FAILED}

    /** A pack on its way: looking up its file, downloading it, or failed (with why). */
    public static final class Job {
        public volatile Stage stage = Stage.FINDING;
        public volatile double progress;
        public volatile String message = "";
    }

    private final Map<String, Installed> installed = new LinkedHashMap<>();
    /** Switched-on packs by project, the one drawn over all the others first. */
    private final List<String> active = new ArrayList<>();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    /** The pack list wants putting into the game when it next can be (not mid-reload or at startup). */
    private boolean dirty;

    public BetterTextures() {
        super("Better Textures", "Search Modrinth resource packs and switch them on in one click", Category.VISUALS);
        instance = this;
    }

    public static BetterTextures get() {
        return instance != null ? instance : ModuleManager.get(BetterTextures.class);
    }

    @Override
    public Screen panel(Screen parent) {
        return new BetterTexturesScreen(parent, this);
    }

    @Override
    protected void onEnable() {
        requestApply();
    }

    @Override
    protected void onDisable() {
        requestApply();
    }

    @Override
    public void onTick() {
        pump();
    }

    // ---- what is on --------------------------------------------------------------------------

    public boolean isActive(String projectId) {
        return isEnabled() && active.contains(projectId);
    }

    /** The switched-on packs, top first. */
    public List<Installed> activePacks() {
        List<Installed> out = new ArrayList<>();
        if (!isEnabled()) return out;
        for (String id : active) {
            Installed pack = installed.get(id);
            if (pack != null) out.add(pack);
        }
        return out;
    }

    public Job job(String projectId) {
        return jobs.get(projectId);
    }

    public boolean isInstalled(String projectId) {
        Installed pack = installed.get(projectId);
        return pack != null && Files.isRegularFile(packDir().resolve(pack.file()));
    }

    /** Switches a pack on, downloading it first if it is not here yet. It goes over the others. */
    public void enable(Modrinth.Pack pack) {
        if (isActive(pack.id())) return;
        Job running = jobs.get(pack.id());
        if (running != null && running.stage != Stage.FAILED) return;
        if (isInstalled(pack.id())) {
            activate(pack.id());
            return;
        }
        Job job = new Job();
        jobs.put(pack.id(), job);
        Modrinth.POOL.execute(() -> {
            try {
                Modrinth.Version version = Modrinth.bestVersion(pack.id()).get(60, TimeUnit.SECONDS);
                job.stage = Stage.DOWNLOADING;
                String name = fileName(version.filename(), pack.slug());
                Modrinth.download(version, packDir(), name, p -> job.progress = p);
                Installed done = new Installed(pack.id(), pack.title(), name, pack.iconUrl(), pack.color(),
                        version.versionNumber(), version.gameVersions());
                mc.execute(() -> {
                    installed.put(pack.id(), done);
                    jobs.remove(pack.id());
                    activate(pack.id());
                });
            } catch (Exception e) {
                Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
                job.message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                job.stage = Stage.FAILED;
                if (notify.get()) mc.execute(() -> Notifications.push("Better Textures", pack.title() + " failed: " + job.message,
                        Notifications.Type.ERROR, 5000));
            }
        });
    }

    private void activate(String projectId) {
        active.remove(projectId);
        active.addFirst(projectId);
        Installed pack = installed.get(projectId);
        if (notify.get() && pack != null) Notifications.push("Better Textures", pack.title() + " on", Notifications.Type.SUCCESS, 2600);
        if (!isEnabled()) setEnabled(true);
        else requestApply();
    }

    public void disable(String projectId) {
        if (active.remove(projectId)) requestApply();
    }

    public void disableAll() {
        if (active.isEmpty()) return;
        active.clear();
        requestApply();
    }

    /** Puts a pack over the others, or under them. */
    public void moveUp(String projectId) {
        int i = active.indexOf(projectId);
        if (i > 0) {
            active.remove(i);
            active.add(i - 1, projectId);
            requestApply();
        }
    }

    // ---- putting packs into the game -----------------------------------------------------------

    private void requestApply() {
        dirty = true;
        pump();
    }

    /** Applies the pack list if it is waiting and the game can take it now. */
    public void pump() {
        if (!dirty) return;
        if (mc.options == null || mc.getResourcePackManager() == null || mc.getOverlay() != null) return;
        if (mc.world == null && mc.currentScreen == null) return;
        dirty = false;
        try {
            apply();
        } catch (RuntimeException e) {
            Notifications.push("Better Textures", "Could not switch packs: " + e.getMessage(), Notifications.Type.ERROR, 5000);
        }
    }

    private void apply() {
        ResourcePackManager manager = mc.getResourcePackManager();
        manager.scanPacks();
        Collection<String> ids = manager.getIds();
        List<String> before = new ArrayList<>(manager.getEnabledIds());
        Set<String> ours = new HashSet<>();
        for (Installed pack : installed.values()) {
            String id = profileId(ids, pack.file());
            if (id != null) ours.add(id);
        }
        List<String> wanted = new ArrayList<>(before);
        wanted.removeAll(ours);
        if (isEnabled()) {
            // The game draws later packs over earlier ones, so the top pack goes in last.
            for (int i = active.size() - 1; i >= 0; i--) {
                Installed pack = installed.get(active.get(i));
                String id = pack == null ? null : profileId(ids, pack.file());
                if (id != null) wanted.add(id);
            }
        }
        if (wanted.equals(before)) return;
        manager.setEnabledProfiles(wanted);
        mc.options.refreshResourcePacks(manager);
    }

    /** The game's name for a pack file in the resourcepacks folder ("file/Faithful.zip"). */
    private static String profileId(Collection<String> ids, String file) {
        if (ids.contains("file/" + file)) return "file/" + file;
        for (String id : ids) if (id.equals(file) || id.endsWith("/" + file)) return id;
        return null;
    }

    /** Whether the game has this pack switched on right now; for tests. */
    public boolean gameHasOn(String projectId) {
        Installed pack = installed.get(projectId);
        if (pack == null) return false;
        String id = profileId(mc.getResourcePackManager().getIds(), pack.file());
        return id != null && mc.getResourcePackManager().getEnabledIds().contains(id);
    }

    public Path packDir() {
        return mc.getResourcePackDir();
    }

    private static String fileName(String filename, String slug) {
        String name = filename == null || filename.isBlank() ? slug + ".zip" : filename;
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.startsWith(".")) name = "_" + name;
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) name += ".zip";
        return name;
    }

    // ---- saved -------------------------------------------------------------------------------

    @Override
    public JsonObject saveExtra() {
        JsonObject data = new JsonObject();
        JsonArray packs = new JsonArray();
        for (Installed pack : installed.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("project", pack.projectId());
            o.addProperty("title", pack.title());
            o.addProperty("file", pack.file());
            o.addProperty("icon", pack.iconUrl());
            o.addProperty("color", pack.color());
            o.addProperty("version", pack.versionNumber());
            JsonArray versions = new JsonArray();
            pack.gameVersions().forEach(versions::add);
            o.add("gameVersions", versions);
            packs.add(o);
        }
        data.add("packs", packs);
        JsonArray on = new JsonArray();
        active.forEach(on::add);
        data.add("active", on);
        return data;
    }

    @Override
    public void loadExtra(JsonObject data) {
        installed.clear();
        active.clear();
        if (data.has("packs")) {
            for (JsonElement e : data.getAsJsonArray("packs")) {
                JsonObject o = e.getAsJsonObject();
                List<String> versions = new ArrayList<>();
                if (o.has("gameVersions")) o.getAsJsonArray("gameVersions").forEach(v -> versions.add(v.getAsString()));
                Installed pack = new Installed(o.get("project").getAsString(), o.get("title").getAsString(), o.get("file").getAsString(),
                        o.has("icon") ? o.get("icon").getAsString() : "", o.has("color") ? o.get("color").getAsInt() : 0x3391FC,
                        o.has("version") ? o.get("version").getAsString() : "", versions);
                installed.put(pack.projectId(), pack);
            }
        }
        if (data.has("active")) {
            for (JsonElement e : data.getAsJsonArray("active")) {
                if (installed.containsKey(e.getAsString())) active.add(e.getAsString());
            }
        }
    }
}
