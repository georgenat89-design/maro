// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.kit.CharacterBuilder;
import dev.maro.render.character.kit.CharacterDesign;
import dev.maro.render.character.kit.CharacterModel;
import dev.maro.render.character.kit.Detail;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.SynchronousResourceReloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(value=EnvType.CLIENT)
public final class CharacterCache {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"Maro");
    private static final int KEEP = 4;
    private static final ForkJoinPool POOL = new ForkJoinPool(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 2)), pool -> {
        ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        thread.setName("Maro Character Builder " + thread.getPoolIndex());
        thread.setDaemon(true);
        thread.setPriority(4);
        return thread;
    }, null, false);
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>(8, 0.75f, true);
    private static boolean listening;

    private CharacterCache() {
    }

    public static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        ResourceLoader.get((ResourceType)ResourceType.CLIENT_RESOURCES).registerReloader(Identifier.of((String)"maro", (String)"character_textures"), (ResourceReloader)((SynchronousResourceReloader)manager -> MinecraftClient.getInstance().execute(CharacterCache::releaseTextures)));
    }

    public static void prepare(CharacterDesign design, Detail detail) {
        String key = design.id() + "/" + detail.name();
        if (ENTRIES.containsKey(key)) {
            return;
        }
        Entry created = new Entry(design, detail);
        ENTRIES.put(key, created);
        CharacterCache.trim();
        CompletableFuture.runAsync(() -> CharacterCache.build(created), POOL);
    }

    public static WornCharacter ready(CharacterDesign design, Detail detail) {
        listen();
        String key = design.id() + "/" + detail.name();
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            prepare(design, detail);
            return null;
        }
        if (entry.worn != null) {
            return entry.worn;
        }
        CharacterModel model = entry.model;
        if (entry.failed) {
            return null;
        }
        if (model == null) {
            return null;
        }
        MinecraftClient.getInstance().getTextureManager().registerTexture(entry.texture, (AbstractTexture)new CharacterTexture(entry.texture.toString(), model.atlasWidth, model.atlasHeight, model.atlasMips));
        entry.worn = new WornCharacter(model, entry.texture);
        return entry.worn;
    }

    private static void build(Entry entry) {
        if (entry.cancelled) return;
        try {
            CharacterModel model = CharacterBuilder.build(entry.design, entry.detail);
            LOGGER.info("[Maro] 3D Character '{}' ({}) built: {} triangles in {} ms", new Object[]{entry.design.name(), entry.detail.label, model.triangles(), model.buildMillis});
            if (!entry.cancelled) entry.model = model;
        }
        catch (Throwable failure) {
            entry.failed = true;
            LOGGER.error("[Maro] 3D Character '{}' ({}) failed to build", new Object[]{entry.design.name(), entry.detail.label, failure});
            CustomModel.failed(entry.design, failure);
        }
    }

    public static void retryFailures() {
        ENTRIES.values().removeIf(entry -> entry.failed);
    }

    private static void trim() {
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (ENTRIES.size() > KEEP && it.hasNext()) {
            Entry eldest = it.next();
            eldest.cancelled = true;
            CharacterCache.release(eldest);
            it.remove();
        }
    }

    public static void releaseTextures() {
        for (Entry entry : ENTRIES.values()) {
            CharacterCache.release(entry);
        }
    }

    public static void clear() {
        CharacterCache.releaseTextures();
        for (Entry entry : ENTRIES.values()) entry.cancelled = true;
        ENTRIES.clear();
    }

    private static void release(Entry entry) {
        if (entry.worn == null) {
            return;
        }
        entry.worn = null;
        MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.texture);
    }

    @Environment(value=EnvType.CLIENT)
    private static final class Entry {
        final CharacterDesign design;
        final Detail detail;
        final Identifier texture;
        volatile CharacterModel model;
        volatile boolean failed;
        volatile boolean cancelled;
        WornCharacter worn;

        Entry(CharacterDesign design, Detail detail) {
            this.design = design;
            this.detail = detail;
            this.texture = Identifier.of((String)"maro", (String)("character/" + design.id() + "_" + detail.name().toLowerCase(Locale.ROOT)));
        }
    }
}
