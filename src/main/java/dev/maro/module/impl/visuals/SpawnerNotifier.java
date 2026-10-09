package dev.maro.module.impl.visuals;

import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.esp.BlockEspRenderer;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spawner Notifier: tells you the moment a mob spawner comes into the loaded world round you, what
 * it spawns, where and how far, and hangs a tag over each one, "Zombie Spawner 12m", visible through
 * walls. A server that hides what a spawner spawns gets "Unknown Spawner".
 *
 * <p>Spawners are block entities, so finding them is a walk over the loaded chunks' block entities
 * twice a second, not a look at every block.
 */
public class SpawnerNotifier extends Module {
    private static final int SPAWNER_RED = 0xFFFF4D5E;

    private final NumberSetting range = add(new NumberSetting("Range", "How far out to look, in loaded chunks", 12, 2, 32, 1).suffix(" chunks"));
    private final BooleanSetting notify = add(new BooleanSetting("Notification", "A Spawner Found notification for each new one", true));
    private final BooleanSetting chat = add(new BooleanSetting("Chat Message", "A line in chat with the spawner's coordinates (only you see it)", true));
    private final BooleanSetting sound = add(new BooleanSetting("Sound", "A ping when one is found", true));
    private final BooleanSetting tags = add(new BooleanSetting("Tags", "A tag over each spawner: what it spawns and how far", true));
    private final NumberSetting tagRange = add(new NumberSetting("Tag Range", "Tags on spawners this close", 128, 16, 512, 8)
            .suffix(" blocks").visible(tags::get));
    private final NumberSetting tagScale = add(new NumberSetting("Tag Size", "How big the tags are", 1, 0.5, 2, 0.05)
            .suffix("x").visible(tags::get));
    private final BooleanSetting tagCoords = add(new BooleanSetting("Tag Coordinates", "Coordinates on the tag as well", false)
            .visible(tags::get));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Finding", range, notify, chat, sound),
            SettingSection.of("Tags", tags, tagRange, tagScale, tagCoords));

    /** A spawner found: where, and what it spawns ("Unknown" when the server does not say). */
    public record Found(BlockPos pos, String mob) {
    }

    /** Every spawner found in this world, in the order found. */
    private final Map<BlockPos, Found> found = new LinkedHashMap<>();
    private ClientWorld world;
    private int ticks;
    private final ItemStack icon = new ItemStack(Items.SPAWNER);

    public SpawnerNotifier() {
        super("Spawner Notifier", "Tells you when a spawner is near, what it spawns and where, with a tag over each one", Category.VISUALS);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        found.clear();
        world = null;
    }

    @Override
    protected void onDisable() {
        found.clear();
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        if (mc.world != world) {
            world = mc.world;
            found.clear();
        }
        if (ticks++ % 10 != 0) return;
        scan();
    }

    /** Looks over the loaded chunks' block entities for spawners not seen before. */
    public void scan() {
        if (!inGame()) return;
        int radius = range.getInt();
        int px = mc.player.getChunkPos().x, pz = mc.player.getChunkPos().z;
        List<Found> fresh = new ArrayList<>();
        for (int cx = px - radius; cx <= px + radius; cx++) {
            for (int cz = pz - radius; cz <= pz + radius; cz++) {
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof MobSpawnerBlockEntity spawner)) continue;
                    BlockPos pos = spawner.getPos().toImmutable();
                    if (found.containsKey(pos)) continue;
                    Found spawnerFound = new Found(pos, mobOf(spawner));
                    found.put(pos, spawnerFound);
                    fresh.add(spawnerFound);
                }
            }
        }
        // Gone: broken, or out of loaded range. They are told about again if they come back.
        found.values().removeIf(f -> {
            int cx = f.pos().getX() >> 4, cz = f.pos().getZ() >> 4;
            return Math.max(Math.abs(cx - px), Math.abs(cz - pz)) > radius + 1
                    || mc.world.getChunkManager().isChunkLoaded(cx, cz) && !(mc.world.getBlockEntity(f.pos()) instanceof MobSpawnerBlockEntity);
        });
        if (!fresh.isEmpty()) announce(fresh);
    }

    private String mobOf(MobSpawnerBlockEntity spawner) {
        try {
            Entity shown = spawner.getLogic().getRenderedEntity(mc.world, spawner.getPos());
            if (shown != null) return shown.getType().getName().getString();
        } catch (RuntimeException ignored) {
            // Odd spawn data: it is a spawner all the same.
        }
        return "Unknown";
    }

    private void announce(List<Found> fresh) {
        fresh.sort(Comparator.comparingDouble(f -> f.pos().getSquaredDistance(mc.player.getEntityPos())));
        Found nearest = fresh.getFirst();
        if (notify.get()) {
            if (fresh.size() == 1) {
                Notifications.push("Spawner Found", nearest.mob() + " Spawner  ·  " + coords(nearest.pos()) + "  ·  " + distance(nearest.pos()),
                        Notifications.Type.WARNING, 6000);
            } else {
                Notifications.push(fresh.size() + " Spawners Found", "Nearest: " + nearest.mob() + "  ·  " + coords(nearest.pos())
                        + "  ·  " + distance(nearest.pos()), Notifications.Type.WARNING, 6000);
            }
        }
        if (chat.get() && mc.inGameHud != null) {
            for (Found f : fresh.subList(0, Math.min(fresh.size(), 8))) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Spawner] ").formatted(Formatting.RED)
                        .append(Text.literal(f.mob() + " Spawner").formatted(Formatting.WHITE))
                        .append(Text.literal(" at " + coords(f.pos()) + " · " + distance(f.pos())).formatted(Formatting.GRAY)));
            }
            if (fresh.size() > 8) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Spawner] ").formatted(Formatting.RED)
                        .append(Text.literal("and " + (fresh.size() - 8) + " more").formatted(Formatting.GRAY)));
            }
        }
        if (sound.get()) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.6f, 0.8f));
        }
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private String distance(BlockPos pos) {
        return Math.round(Math.sqrt(pos.getSquaredDistance(mc.player.getEntityPos()))) + "m";
    }

    /** The spawners found now, nearest first; for tests. */
    public List<Found> spawners() {
        List<Found> out = new ArrayList<>(found.values());
        if (mc.player != null) out.sort(Comparator.comparingDouble(f -> f.pos().getSquaredDistance(mc.player.getEntityPos())));
        return out;
    }

    // ---- tags -----------------------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!tags.get() || !inGame() || mc.options.hudHidden || found.isEmpty()) return;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        double reach = tagRange.get(), reachSq = reach * reach;
        float scale = tagScale.getFloat();

        // Farthest first, so nearer tags sit on top.
        List<Found> list = new ArrayList<>(found.values());
        list.sort(Comparator.comparingDouble((Found f) -> f.pos().getSquaredDistance(camera)).reversed());
        for (Found f : list) {
            double dsq = f.pos().getSquaredDistance(camera);
            if (dsq > reachSq) continue;
            float[] at = BlockEspRenderer.toScreen(f.pos().getX() + 0.5, f.pos().getY() + 1.35, f.pos().getZ() + 0.5, camera, w, h);
            if (at == null || at[0] < -60 || at[0] > w + 60 || at[1] < -30 || at[1] > h + 30) continue;
            float fade = (float) Math.min(1, Math.max(0.35, 1.2 - Math.sqrt(dsq) / reach));
            tag(ctx, f, at[0], at[1], scale, fade, (int) Math.round(Math.sqrt(dsq)));
        }
    }

    /** "[icon] Zombie Spawner 12m" on a dark rounded card, centred on (x, y). */
    private void tag(DrawContext ctx, Found f, float x, float y, float scale, float fade, int meters) {
        float size = 0.78f;
        String mob = f.mob(), kind = " Spawner", far = "  " + meters + "m";
        String extra = tagCoords.get() ? "  " + coords(f.pos()) : "";
        Fonts.beginRaw();
        try {
            float textW = Fonts.width(mob, true, size) + Fonts.width(kind, true, size) + Fonts.width(far + extra, false, size);
            float w = textW + 26, h = 15;
            var matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            float left = -w / 2f, top = -h / 2f;
            float prev = Render2D.getAlpha();
            Render2D.setAlpha(prev * fade);
            Render2D.roundRect(ctx, left, top, w, h, 4, 0xD80D0F14);
            Render2D.roundOutline(ctx, left, top, w, h, 4, 1, 0x40FF4D5E);
            matrices.pushMatrix();
            matrices.translate(left + 4, top + 1.5f);
            matrices.scale(0.75f, 0.75f);
            ctx.drawItem(icon, 0, 0);
            matrices.popMatrix();
            float tx = left + 20;
            Fonts.drawV(ctx, mob, tx, 0, 0xFFF2F4F8, true, size);
            tx += Fonts.width(mob, true, size);
            Fonts.drawV(ctx, kind, tx, 0, SPAWNER_RED, true, size);
            tx += Fonts.width(kind, true, size);
            Fonts.drawV(ctx, far + extra, tx, 0, 0xFFA8B0BF, false, size);
            Render2D.setAlpha(prev);
            matrices.popMatrix();
        } finally {
            Fonts.endRaw();
        }
    }

    /** "12m", for the notification; also used by tests. */
    public String describe(Found f) {
        return String.format(Locale.ROOT, "%s Spawner at %s, %s", f.mob(), coords(f.pos()), distance(f.pos()));
    }
}
