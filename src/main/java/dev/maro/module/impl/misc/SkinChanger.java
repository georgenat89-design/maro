package dev.maro.module.impl.misc;

import com.google.gson.JsonParser;
import dev.maro.Maro;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Adapted from Anubis 0.9.9 Skin Change (GPL-3.0); native Maro settings and skin lookup. */
public final class SkinChanger extends Module {
    private static SkinChanger instance;
    private final TextSetting selfPlayer = add(new TextSetting("Self Player", "Username whose skin you wear; empty keeps your own", "", 16, "Player username"));
    private final TextSetting othersPlayer = add(new TextSetting("Others Player", "Username used for other players; empty keeps their skins", "", 16, "Player username"));
    private final Slot self = new Slot(), others = new Slot();
    private final ButtonSetting reload = add(new ButtonSetting("Reload Skins", "Retry loading the selected usernames", "Reload", () -> {
        self.attempted = others.attempted = null; refresh();
    }));
    private long generation, nextTexture;

    private record CustomSkin(AssetInfo.TextureAsset body, PlayerSkinType model) { }
    private record Source(String uuid, String url, PlayerSkinType model) { }
    private static final class Slot {
        String wanted = "", attempted;
        long changedAt;
        boolean loading;
        CustomSkin skin;
        final Map<SkinTextures, SkinTextures> swaps = new IdentityHashMap<>();
    }
    public SkinChanger() {
        super("Skin Changer", "Wear another player's skin locally, with separate skins for yourself and other players", Category.MISC);
        instance = this;
    }
    @Override public List<SettingSection> getSettingSections() { return List.of(SettingSection.of("Skins", selfPlayer, othersPlayer, reload)); }
    @Override protected void onEnable() { refresh(); }
    @Override public void onTick() { refresh(); }
    @Override protected void onDisable() {
        generation++;
        release(self); release(others);
        self.attempted = others.attempted = null;
    }
    private void release(Slot slot) {
        if (slot.skin != null) mc.getTextureManager().destroyTexture(slot.skin.body().texturePath());
        slot.skin = null; slot.swaps.clear();
    }
    private void refresh() {
        if (!isEnabled()) return;
        refresh(self, selfPlayer.get().trim()); refresh(others, othersPlayer.get().trim());
    }
    private void refresh(Slot slot, String username) {
        if (!username.equals(slot.wanted)) {
            slot.wanted = username; slot.attempted = null; slot.changedAt = System.nanoTime(); release(slot);
        }
        // Text settings update with every character. Wait briefly for a complete username.
        if (slot.loading || username.isEmpty() || username.equals(slot.attempted)
                || System.nanoTime() - slot.changedAt < 300_000_000L) return;
        slot.attempted = username;
        if (!username.matches("[A-Za-z0-9_]{1,16}")) return;
        slot.loading = true;
        long requestGeneration = generation;
        Identifier id = Identifier.of(Maro.MOD_ID, "skin_change/" + (++nextTexture));
        CompletableFuture.supplyAsync(() -> {
            try { return source(username); } catch (Exception error) { return null; }
        }).thenCompose(source -> {
            if (source == null) return CompletableFuture.completedFuture((CustomSkin) null);
            try {
                var cache = mc.runDirectory.toPath().resolve("cache/maro_skins"); Files.createDirectories(cache);
                var downloader = new PlayerSkinTextureDownloader(mc.getNetworkProxy(), mc.getTextureManager(), mc);
                // Cache by texture, so Reload also picks up a player's newly changed skin.
                String texture = URI.create(source.url()).getPath().replaceFirst(".*/", "");
                if (!texture.matches("[0-9a-fA-F]{32,128}")) return CompletableFuture.completedFuture((CustomSkin) null);
                return downloader.downloadAndRegisterTexture(id, cache.resolve(texture + ".png"), source.url(), true)
                        .thenApply(body -> new CustomSkin(body, source.model()));
            } catch (Exception error) { return CompletableFuture.completedFuture((CustomSkin) null); }
        }).whenCompleteAsync((skin, error) -> {
            slot.loading = false;
            if (isEnabled() && generation == requestGeneration && username.equals(slot.wanted) && username.equals((slot == self ? selfPlayer : othersPlayer).get().trim()) && skin != null) {
                release(slot); slot.skin = skin;
            } else if (skin != null) mc.getTextureManager().destroyTexture(skin.body().texturePath());
            if (isEnabled() && generation == requestGeneration && username.equals(slot.wanted) && skin == null)
                Maro.LOGGER.warn("Skin Changer could not load skin for {}; use Reload Skins to retry", username);
            refresh();
        }, mc);
    }
    private static Source source(String username) throws Exception {
        var profile = JsonParser.parseString(get("https://api.mojang.com/users/profiles/minecraft/" + username)).getAsJsonObject();
        String uuid = profile.get("id").getAsString();
        if (!uuid.matches("[0-9a-fA-F]{32}")) return null;
        var session = JsonParser.parseString(get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)).getAsJsonObject();
        for (var value : session.getAsJsonArray("properties")) {
            var property = value.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) continue;
            String decoded = new String(Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
            var textures = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
            if (!textures.has("SKIN")) return null;
            var skin = textures.getAsJsonObject("SKIN");
            URI url = URI.create(skin.get("url").getAsString());
            if (!"textures.minecraft.net".equalsIgnoreCase(url.getHost())) return null;
            String model = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model") ? skin.getAsJsonObject("metadata").get("model").getAsString() : null;
            return new Source(uuid, "https://textures.minecraft.net" + url.getRawPath(), PlayerSkinType.byModelMetadata(model));
        }
        return null;
    }
    private static String get(String url) throws Exception {
        var connection = (HttpURLConnection) URI.create(url).toURL().openConnection(mc.getNetworkProxy());
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "Maro Skin Changer");
        try {
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Skin profile unavailable");
            try (var stream = connection.getInputStream()) { return new String(stream.readNBytes(262144), StandardCharsets.UTF_8); }
        } finally { connection.disconnect(); }
    }
    public static SkinTextures skinFor(UUID player, SkinTextures original) {
        var module = instance;
        if (module == null || !module.isEnabled() || mc.player == null) return original;
        Slot slot = mc.player.getUuid().equals(player) ? module.self : module.others;
        if (slot.skin == null) return original;
        if (slot.swaps.size() >= 256) slot.swaps.clear();
        return slot.swaps.computeIfAbsent(original, skin -> new SkinTextures(slot.skin.body(), skin.cape(), skin.elytra(), slot.skin.model(), skin.secure()));
    }
}
