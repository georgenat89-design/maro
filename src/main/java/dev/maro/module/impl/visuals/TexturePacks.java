package dev.maro.module.impl.visuals;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.maro.gui.hud.TexturePacksScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.textures.Modrinth;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Texture Packs: every resource pack you already have (your resourcepacks folder, the game's own
 * extras and ones Better Textures downloaded) on one screen, with its icon, to switch on and put in
 * order. While the module is on your picked packs are on; turning it off puts back the packs the
 * game had before, so its keybind swaps between the two.
 */
public class TexturePacks extends Module {
    private static TexturePacks instance;

    private final ButtonSetting open = add(new ButtonSetting("Packs", "Pick which of your texture packs are on and which goes on top", "Pick",
            () -> mc.setScreen(new TexturePacksScreen(mc.currentScreen, this))));
    private final BooleanSetting notify = add(new BooleanSetting("Notify", "A notification when the packs switch", true));

    /** One of your packs, as the screen shows it. */
    public record Pack(String id, String name, String description, String kind, boolean fits, String fitNote, ResourcePackProfile profile) {
    }

    /** Your picked packs, top first: on while the module is on. */
    private final List<String> picked = new ArrayList<>();
    /** The packs the game had on before the module turned on, top first; put back when it turns off. */
    private final List<String> saved = new ArrayList<>();
    /** Whether {@link #saved} is set aside, waiting to go back on. */
    private boolean holding;
    /** Whether the picks have been filled from the game's packs once, so the first switch on changes nothing. */
    private boolean seeded;
    /** The packs want putting into the game when it next can take them (not mid-reload or at startup). */
    private boolean dirty;

    public TexturePacks() {
        super("Texture Packs", "Pick which of your texture packs are on; its keybind swaps them with your usual packs", Category.VISUALS);
        instance = this;
        // Also while off: putting the usual packs back can be waiting from the last session.
        ClientTickEvents.END_CLIENT_TICK.register(client -> pump());
    }

    public static TexturePacks get() {
        return instance != null ? instance : ModuleManager.get(TexturePacks.class);
    }

    @Override
    public Screen panel(Screen parent) {
        return new TexturePacksScreen(parent, this);
    }

    @Override
    protected void onEnable() {
        seed();
        if (!holding && mc.getResourcePackManager() != null) {
            saved.clear();
            saved.addAll(gameOn());
            holding = true;
        }
        requestApply();
    }

    @Override
    protected void onDisable() {
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
            if (fromModrinth != null) name = fromModrinth;
            else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
            String kind = fromModrinth != null ? "Better Textures"
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
            Notifications.push("Texture Packs", "Could not switch packs: " + e.getMessage(), Notifications.Type.ERROR, 5000);
        }
    }

    private void apply() {
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
            Notifications.push("Texture Packs", message, Notifications.Type.SUCCESS, 2600);
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
        return data;
    }

    @Override
    public void loadExtra(JsonObject data) {
        picked.clear();
        saved.clear();
        if (data.has("picked")) for (JsonElement e : data.getAsJsonArray("picked")) picked.add(e.getAsString());
        if (data.has("usual")) for (JsonElement e : data.getAsJsonArray("usual")) saved.add(e.getAsString());
        holding = data.has("holding") && data.get("holding").getAsBoolean();
        seeded = data.has("seeded") && data.get("seeded").getAsBoolean();
        dirty = true;
    }
}
