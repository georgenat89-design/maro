package dev.maro.module.impl.visuals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.maro.gui.hud.TexturePacksScreen;
import dev.maro.gui.hud.PacksMcScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.textures.Modrinth;
import dev.maro.textures.PacksMcCatalog;
import dev.maro.textures.PackArchive;
import dev.maro.textures.BundledPacks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Texture Packs: every resource pack you already have (your resourcepacks folder, the game's own
 * extras and ones Better Textures downloaded) on one screen, with its icon, to switch on and put in
 * order. While the module is on your picked packs are on; turning it off puts back the packs the
 * game had before, so its keybind swaps between the two.
 */
public class TexturePacks extends Module {
    private static TexturePacks instance;
    private final BetterLooks looks = new BetterLooks();
    private final BetterTextures better = new BetterTextures();
    public final BooleanSetting appearance = add(new BooleanSetting("Appearance", "Apply world, particle, interface and hand adjustments while Gameplay Textures is on", true));
    private final ButtonSetting open = add(new ButtonSetting("PacksMC", "Browse the first 50 Trending packs with previews", "Browse",
            () -> mc.setScreen(new PacksMcScreen(mc.currentScreen, this))));
    private final ButtonSetting local = add(new ButtonSetting("Installed Packs", "Manage local packs and their priority", "Manage",
            () -> mc.setScreen(new TexturePacksScreen(mc.currentScreen, this))));
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "A notification when the packs switch", true));

    /** One of your packs, as the screen shows it. */
    public record Pack(String id, String name, String description, String kind, boolean fits, String fitNote, ResourcePackProfile profile) {
    }

    /** Your picked packs, top first: on while the module is on. */
    private final List<String> picked = new ArrayList<>();
    /** The packs the game had on before the module turned on, top first; put back when it turns off. */
    private final List<String> saved = new ArrayList<>();
    private final Set<String> legacyManaged = new HashSet<>();
    /** Whether {@link #saved} is set aside, waiting to go back on. */
    private boolean holding;
    /** Whether the picks have been filled from the game's packs once, so the first switch on changes nothing. */
    private boolean seeded;
    /** The packs want putting into the game when it next can take them (not mid-reload or at startup). */
    private boolean dirty;

    private final PacksMcCatalog catalog = new PacksMcCatalog(mc.runDirectory.toPath().resolve("maro/packsmc/catalog.json"));
    private final Set<String> favorites = new HashSet<>();
    private final Map<String, String> imports = new HashMap<>();
    private long importEpoch;
    private volatile boolean importing;
    private volatile String importStatus = "";

    public TexturePacks() {
        super("Gameplay Textures", "Resource packs, in-game downloads and appearance controls in one place", Category.VISUALS);
        instance = this;
        looks.getSettings().forEach(this::add);
        // Also while off: putting the usual packs back can be waiting from the last session.
        ClientTickEvents.END_CLIENT_TICK.register(client -> pump());
    }

    public static TexturePacks get() {
        return instance != null ? instance : ModuleManager.get(TexturePacks.class);
    }

    @Override
    public Screen panel(Screen parent) {
        return new dev.maro.gui.hud.GameplayTexturesScreen(parent, this);
    }

    public BetterLooks looks() { return looks; }
    public BetterTextures modrinth() { return better; }
    public boolean notifications() { return notify.get(); }
    public long activationVersion() { return importEpoch; }
    public void cancelPendingActivation() { importEpoch++; }
    @Override public List<dev.maro.setting.SettingSection> getSettingSections() {
        var out = new ArrayList<dev.maro.setting.SettingSection>();
        out.add(dev.maro.setting.SettingSection.of("Library", open, local, notify, appearance));
        out.addAll(looks.getSettingSections()); return List.copyOf(out);
    }

    @Override
    protected void onEnable() {
        seed();
        if (!holding && mc.getResourcePackManager() != null) {
            saved.clear();
            saved.addAll(gameOn());
            saved.removeAll(legacyManaged); legacyManaged.clear();
            holding = true;
        }
        requestApply();
    }

    @Override
    protected void onDisable() {
        importEpoch++; // An import must not re-enable the module after it was disabled.
        // Whatever was changed while on (here or in the game's own menu) is kept as the picks.
        if (holding && !dirty && mc.getResourcePackManager() != null) {
            picked.clear();
            picked.addAll(gameOn());
        }
        requestApply();
    }

    // ---- what is picked ------------------------------------------------------------------------

    /** The picks, top first. */
    public List<String> picked() {
        return List.copyOf(picked);
    }

    /** Puts in new picks, top first, and switches them on (turning the module on if it is off). */
    public void setPicked(List<String> ids) {
        picked.clear();
        for (String id : ids) if (!picked.contains(id)) picked.add(id);
        seeded = true;
        if (!isEnabled()) setEnabled(true);
        else requestApply();
    }

    /** Puts in new picks without switching anything; for tests tidying up. */
    public void setPickedQuietly(List<String> ids) {
        picked.clear();
        picked.addAll(ids);
    }

    /** Called as the screen opens: the picks start as the game's packs, and follow the game while on. */
    public void opened() {
        seed();
        syncPicks();
        discoverImports();
    }

    /** ZIPs travel with the resourcepacks folder, even when the Maro profile did not. */
    private void discoverImports() {
        var manager=mc.getResourcePackManager();if(manager==null)return;
        manager.scanPacks();
        var pattern=Pattern.compile("file/maro-packsmc-([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})-[a-f0-9]{12}\\.zip");
        for(var profile:manager.getProfiles()){
            var match=pattern.matcher(profile.getId());
            if(match.matches()&&!cachedId(imports.get(match.group(1))))imports.put(match.group(1),profile.getId());
        }
    }

    private boolean cachedId(String id) {
        return id!=null&&mc.getResourcePackManager()!=null&&mc.getResourcePackManager().getProfile(id)!=null;
    }

    private void seed() {
        if (seeded || mc.getResourcePackManager() == null) return;
        picked.clear();
        picked.addAll(gameOn());
        seeded = true;
    }

    /** While on, the picks become what the game really has on (after Better Textures or the game's menu changed it). */
    public void syncPicks() {
        if (!isEnabled() || dirty || mc.getResourcePackManager() == null) return;
        picked.clear();
        picked.addAll(gameOn());
    }

    // ---- PacksMC catalog and player-selected local imports -------------------------------------

    public PacksMcCatalog catalog() { return catalog; }
    public boolean favorite(String id) { return favorites.contains(id); }
    public void toggleFavorite(String id) { if (!favorites.remove(id)) favorites.add(id); }
    public boolean importing() { return importing; }
    public String importStatus() { return importStatus; }
    public String importedProfile(PacksMcCatalog.Pack pack) { return imports.getOrDefault(pack.id(), ""); }
    public boolean cached(PacksMcCatalog.Pack pack) { return cachedId(importedProfile(pack)); }
    public int cachedCount() { return (int)catalog.packs().stream().filter(this::cached).count(); }
    public boolean included(PacksMcCatalog.Pack pack) { return BundledPacks.contains(pack.id()); }
    public CompletableFuture<List<PacksMcCatalog.Pack>> refreshCatalog(boolean force) { return catalog.refresh(force); }
    public void applyImported(PacksMcCatalog.Pack pack) {
        String profile = importedProfile(pack);
        if (!cached(pack)) return;
        opened();
        List<String> next = new ArrayList<>(picked);
        // PacksMC packs are full packs; replace only a previous PacksMC selection.
        next.removeIf(id -> id.startsWith("file/maro-packsmc-"));
        next.addFirst(profile); setPicked(next); dev.maro.config.ConfigManager.saveAll();
    }
    public CompletableFuture<Path> importPack(PacksMcCatalog.Pack pack, Path source) {
        return importPack(pack,source,true);
    }

    /** Collection downloads only populate the library; they never switch 50 full packs on. */
    public CompletableFuture<Path> importPack(PacksMcCatalog.Pack pack, Path source, boolean activate) {
        return importPack(pack,()->PackArchive.install(source,pack.id(),packDir()),activate);
    }
    public CompletableFuture<Path> installIncluded(PacksMcCatalog.Pack pack,boolean activate) {
        if(cached(pack)){if(activate)applyImported(pack);return CompletableFuture.completedFuture(packDir().resolve(importedProfile(pack).substring(5)));}
        if(!included(pack)){
            importStatus=pack.exclusive()?"Packs+ access required · Creator page opens the website":"Pack not included in this client · Use the client with library or Creator page";
            return CompletableFuture.failedFuture(new IOException(importStatus));
        }
        return importPack(pack,()->BundledPacks.install(pack.id(),packDir()),activate);
    }
    public CompletableFuture<Void> installIncludedCollection() {
        if(importing)return CompletableFuture.failedFuture(new IllegalStateException("An import is already running"));
        var pending=catalog.packs().stream().filter(this::included).filter(p->!cached(p)).toList();
        if(pending.isEmpty()){importStatus=cachedCount()>0?"Your included library is ready · Choose Apply on a pack":"No included library in this client · Creator page opens the website";return CompletableFuture.completedFuture(null);}
        CompletableFuture<Void> next=CompletableFuture.completedFuture(null);int[] saved={0};
        for(var pack:pending)next=next.thenCompose(v->installIncluded(pack,false).thenAccept(file->{saved[0]++;importStatus="Saved "+saved[0]+" / "+pending.size()+" included packs";}));
        return next;
    }
    private CompletableFuture<Path> importPack(PacksMcCatalog.Pack pack,java.util.concurrent.Callable<Path> installer,boolean activate) {
        if (importing) return CompletableFuture.failedFuture(new IllegalStateException("An import is already running"));
        importing = true; importStatus = "Checking resource-pack ZIP…"; long epoch = importEpoch;
        var result = new CompletableFuture<Path>();
        CompletableFuture.supplyAsync(() -> {
            try { return installer.call(); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        }, Modrinth.POOL).whenComplete((path,error) -> mc.execute(() -> {
            importing = false;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                importStatus = cause.getMessage() == null ? "Could not import this ZIP" : cause.getMessage();
                if(notify.get())Notifications.push("Gameplay Textures", importStatus, Notifications.Type.ERROR, 5000);
                result.completeExceptionally(error);
            } else {
                try {
                String profile = "file/" + path.getFileName();
                mc.getResourcePackManager().scanPacks();
                if (mc.getResourcePackManager().getProfile(profile) == null) {
                    importStatus = "Minecraft could not read this pack. Download its Java ZIP again.";
                    Notifications.push("Gameplay Textures", importStatus, Notifications.Type.ERROR, 5000);
                    result.completeExceptionally(new IOException(importStatus)); return;
                }
                imports.put(pack.id(), profile);
                importStatus = "Imported " + pack.name();
                if (activate && epoch == importEpoch) applyImported(pack);
                dev.maro.config.ConfigManager.saveAll();
                result.complete(path);
                } catch(RuntimeException failure) {
                    importStatus="Could not register this pack: "+failure.getMessage();
                    result.completeExceptionally(failure);
                }
            }
        }));
        return result;
    }

    // ---- the packs there are -------------------------------------------------------------------

    /** Every pack you can switch, A to Z, freshly looked for in the folder. */
    public List<Pack> scan() {
        ResourcePackManager manager = mc.getResourcePackManager();
        manager.scanPacks();
        BetterTextures better = BetterTextures.get();
        List<Pack> out = new ArrayList<>();
        for (ResourcePackProfile profile : manager.getProfiles()) {
            if (!switchable(profile)) continue;
            String id = profile.getId();
            String file = id.startsWith("file/") ? id.substring(5) : null;
            String name = plain(profile.getDisplayName().getString());
            String fromModrinth = file != null && better != null ? better.titleOf(file) : null;
            PacksMcCatalog.Pack online = catalog.packs().stream().filter(p -> id.equals(importedProfile(p))).findFirst().orElse(null);
            if (online != null) name = online.name();
            else if (fromModrinth != null) name = fromModrinth;
            else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
            String kind = online != null ? "PacksMC" : fromModrinth != null ? "Modrinth"
                    : file != null ? (file.toLowerCase(Locale.ROOT).endsWith(".zip") ? "Zip" : "Folder")
                    : profile.getSource() == ResourcePackSource.BUILTIN ? "Built-in" : "Mod";
            ResourcePackCompatibility compatibility = profile.getCompatibility();
            String note = switch (compatibility.name()) {
                case "TOO_OLD" -> "Older MC";
                case "TOO_NEW" -> "Newer MC";
                default -> compatibility.isCompatible() ? "For " + Modrinth.gameVersion() : "May not fit";
            };
            out.add(new Pack(id, name.isBlank() ? id : name, plain(profile.getDescription().getString()), kind,
                    compatibility.isCompatible(), note, profile));
        }
        out.sort(Comparator.comparing((Pack p) -> p.name().toLowerCase(Locale.ROOT)).thenComparing(Pack::id));
        return out;
    }

    /** A pack's own icon (its pack.png), read from its files. */
    public static byte[] readIcon(ResourcePackProfile profile) throws IOException {
        try (ResourcePack pack = profile.createResourcePack()) {
            InputSupplier<InputStream> icon = pack.openRoot("pack.png");
            if (icon == null) throw new IOException("No pack.png");
            try (InputStream in = icon.get()) {
                return in.readAllBytes();
            }
        }
    }

    /** A pack you pick yourself: not one the game or a mod needs, nor a server's or a world's own. */
    private static boolean switchable(ResourcePackProfile profile) {
        ResourcePackSource source = profile.getSource();
        return !profile.isRequired() && !profile.isPinned() && source != ResourcePackSource.SERVER && source != ResourcePackSource.WORLD;
    }

    private static String plain(String text) {
        String stripped = Formatting.strip(text);
        return stripped == null ? "" : stripped.replace('\n', ' ').replace('\r', ' ').trim();
    }

    /** The packs you can switch that the game has on right now, top first. */
    public List<String> gameOn() {
        ResourcePackManager manager = mc.getResourcePackManager();
        List<String> out = new ArrayList<>();
        for (ResourcePackProfile profile : manager.getEnabledProfiles()) {
            if (switchable(profile)) out.addFirst(profile.getId());
        }
        return out;
    }

    /** Whether the game has this pack on right now; for tests. */
    public boolean gameHasOn(String id) {
        return mc.getResourcePackManager().getEnabledIds().contains(id);
    }

    public Path packDir() {
        return mc.getResourcePackDir();
    }

    // ---- putting packs into the game -----------------------------------------------------------

    private void requestApply() {
        dirty = true;
        pump();
    }

    /** Puts the packs in if that is waiting and the game can take them now. */
    public void pump() {
        if (!dirty) return;
        if (mc.options == null || mc.getResourcePackManager() == null || mc.getOverlay() != null) return;
        if (mc.world == null && mc.currentScreen == null) return;
        dirty = false;
        try {
            apply();
        } catch (RuntimeException e) {
            Notifications.push("Gameplay Textures", "Could not switch packs: " + e.getMessage(), Notifications.Type.ERROR, 5000);
        }
    }

    private void apply() {
        // Loading an enabled profile while already on does not invoke onEnable again.
        if(isEnabled()&&!holding){saved.clear();saved.addAll(gameOn());saved.removeAll(legacyManaged);legacyManaged.clear();holding=true;}
        List<String> top;
        if (isEnabled()) top = picked;
        else if (holding) top = saved;
        else return;

        ResourcePackManager manager = mc.getResourcePackManager();
        manager.scanPacks();
        List<String> before = new ArrayList<>(manager.getEnabledIds());
        // The packs nobody picks (the game's own, mods', a server's) keep their places; the picks go
        // above them but under any pinned to the very top. The game draws later packs over earlier ones.
        List<String> wanted = new ArrayList<>();
        int at = -1;
        for (String id : before) {
            ResourcePackProfile profile = manager.getProfile(id);
            if (profile != null && switchable(profile)) continue;
            if (at < 0 && profile != null && profile.isPinned() && profile.getInitialPosition() == ResourcePackProfile.InsertionPosition.TOP) {
                at = wanted.size();
            }
            wanted.add(id);
        }
        if (at < 0) at = wanted.size();
        List<String> bottomFirst = new ArrayList<>();
        for (String id : top) {
            ResourcePackProfile profile = manager.getProfile(id);
            if (profile != null && switchable(profile) && !bottomFirst.contains(id)) bottomFirst.addFirst(id);
        }
        wanted.addAll(at, bottomFirst);

        boolean restoring = !isEnabled();
        if (restoring) {
            holding = false;
            saved.clear();
        }
        if (wanted.equals(before)) return;
        manager.setEnabledProfiles(wanted);
        mc.options.refreshResourcePacks(manager);
        BetterTextures better = BetterTextures.get();
        if (better != null) better.syncFromGame();
        if (notify.get()) {
            String message;
            if (restoring) message = "Back to your usual packs";
            else if (bottomFirst.isEmpty()) message = "All packs off";
            else {
                ResourcePackProfile first = manager.getProfile(bottomFirst.getLast());
                String name = first == null ? bottomFirst.getLast() : plain(first.getDisplayName().getString());
                if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
                message = name + (bottomFirst.size() > 1 ? "  +" + (bottomFirst.size() - 1) + " more" : "") + " on";
            }
            Notifications.push("Gameplay Textures", message, Notifications.Type.SUCCESS, 2600);
        }
    }

    // ---- saved -------------------------------------------------------------------------------

    @Override
    public JsonObject saveExtra() {
        JsonObject data = new JsonObject();
        JsonArray on = new JsonArray();
        picked.forEach(on::add);
        data.add("picked", on);
        JsonArray usual = new JsonArray();
        saved.forEach(usual::add);
        data.add("usual", usual);
        data.addProperty("holding", holding);
        data.addProperty("seeded", seeded);
        JsonArray stars = new JsonArray();
        favorites.stream().sorted().forEach(stars::add);
        data.add("favorites", stars);
        JsonObject files = new JsonObject();
        imports.forEach(files::addProperty); data.add("packsmcImports", files);
        data.add("modrinth", better.saveExtra());
        JsonArray legacy = new JsonArray(); legacyManaged.forEach(legacy::add); data.add("legacyManaged",legacy);
        return data;
    }

    @Override
    public void loadExtra(JsonObject data) {
        importEpoch++;
        better.loadExtra(data.has("modrinth") && data.get("modrinth").isJsonObject() ? data.getAsJsonObject("modrinth") : new JsonObject());
        picked.clear();
        saved.clear();
        legacyManaged.clear();
        if(data.has("legacyManaged"))for(var e:data.getAsJsonArray("legacyManaged"))legacyManaged.add(e.getAsString());
        if (data.has("picked")) for (JsonElement e : data.getAsJsonArray("picked")) picked.add(e.getAsString());
        if (data.has("usual")) for (JsonElement e : data.getAsJsonArray("usual")) saved.add(e.getAsString());
        holding = data.has("holding") && data.get("holding").getAsBoolean();
        saved.removeAll(legacyManaged);
        seeded = data.has("seeded") && data.get("seeded").getAsBoolean();
        imports.clear();
        if (data.has("packsmcImports") && data.get("packsmcImports").isJsonObject()) {
            data.getAsJsonObject("packsmcImports").entrySet().forEach(e -> {
                String id = e.getKey(), file = e.getValue().getAsString();
                if (id.matches("[a-f0-9-]{36}") && file.matches("file/maro-packsmc-" + id + "-[a-f0-9]{12}\\.zip")) imports.put(id, file);
            });
        }
        favorites.clear();
        if (data.has("favorites") && data.get("favorites").isJsonArray())
            for (JsonElement e : data.getAsJsonArray("favorites")) if (e.isJsonPrimitive()) favorites.add(e.getAsString());
        dirty = true;
    }
}
