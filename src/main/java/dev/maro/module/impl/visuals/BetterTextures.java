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
    @Override public boolean isEnabled() { TexturePacks owner=TexturePacks.get(); return owner!=null&&owner.isEnabled(); }

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
        Installed p=installed.get(projectId);return isEnabled() && p!=null && TexturePacks.get().picked().contains("file/"+p.file());
    }

    /** The switched-on packs, top first. */
    public List<Installed> activePacks() {
        List<Installed> out = new ArrayList<>();
        if (!isEnabled()) return out;
        for (String id : TexturePacks.get().picked())
            for (Installed pack : installed.values()) if(id.equals("file/"+pack.file()))out.add(pack);
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
        long epoch=TexturePacks.get().activationVersion();
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
                    if(epoch==TexturePacks.get().activationVersion())activate(pack.id());
                    dev.maro.config.ConfigManager.saveAll();
                });
            } catch (Exception e) {
                Throwable cause = e instanceof java.util.concurrent.ExecutionException && e.getCause() != null ? e.getCause() : e;
                job.message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                job.stage = Stage.FAILED;
                if (TexturePacks.get().notifications()) mc.execute(() -> Notifications.push("Gameplay Textures", pack.title() + " failed: " + job.message,
                        Notifications.Type.ERROR, 5000));
            }
        });
    }

    private void activate(String projectId) {
        Installed pack=installed.get(projectId);if(pack==null)return;
        var owner=TexturePacks.get();owner.opened();mc.getResourcePackManager().scanPacks();
        var next=new ArrayList<>(owner.picked());String profile="file/"+pack.file();next.remove(profile);next.addFirst(profile);owner.setPicked(next);
        if(owner.notifications())Notifications.push("Gameplay Textures",pack.title()+" on",Notifications.Type.SUCCESS,2600);
        dev.maro.config.ConfigManager.saveAll();
    }
    public void disable(String projectId) {
        Installed pack=installed.get(projectId);if(pack==null)return;
        var owner=TexturePacks.get();var next=new ArrayList<>(owner.picked());if(next.remove("file/"+pack.file()))owner.setPicked(next);
    }
    public void disableAll() {
        var owner=TexturePacks.get();var next=new ArrayList<>(owner.picked());
        next.removeIf(id->installed.values().stream().anyMatch(p->id.equals("file/"+p.file())));owner.setPicked(next);
    }
    public void moveUp(String projectId) {
        Installed pack=installed.get(projectId);if(pack==null)return;
        var owner=TexturePacks.get();var next=new ArrayList<>(owner.picked());int i=next.indexOf("file/"+pack.file());
        if(i>0){java.util.Collections.swap(next,i,i-1);owner.setPicked(next);}
    }
    private void requestApply() { pump(); }
    public void pump() { var owner=TexturePacks.get();if(owner!=null)owner.pump(); }
    public void syncFromGame() {
        active.clear();var owner=TexturePacks.get();if(owner==null)return;
        for(String id:owner.picked())for(Installed p:installed.values())if(id.equals("file/"+p.file()))active.add(p.projectId());
    }

    /** The Modrinth name of a pack file downloaded through here, or null for any other file. */
    public String titleOf(String file) {
        for (Installed pack : installed.values()) if (pack.file().equals(file)) return pack.title();
        return null;
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
        syncFromGame();
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
