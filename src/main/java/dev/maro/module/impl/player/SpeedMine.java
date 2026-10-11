package dev.maro.module.impl.player;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import dev.maro.mixin.MiningAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import java.util.Set;
import java.util.HashSet;
/** Meteor Speed Mine, adapted to native Maro settings and mining hooks. */
public final class SpeedMine extends Module {
    public static volatile SpeedMine active;
    private final ModeSetting mode = add(new ModeSetting("Mode", "Normal multiplier, local Haste, or early damage completion", "Damage", "Normal", "Haste", "Damage").onChange(v -> removeHaste()));
    private final TextSetting blocks = add(new TextSetting("Blocks", "Comma-separated block IDs for the filter", "", 512, "minecraft:stone, minecraft:obsidian").visible(() -> !mode.is("Haste")));
    private final ModeSetting filterMode = add(new ModeSetting("Block Filter", "How to use the selected blocks", "Blacklist", "Whitelist", "Blacklist").visible(() -> !mode.is("Haste")));
    private final NumberSetting modifier = add(new NumberSetting("Modifier", "Mining speed multiplier; 1.2 is one Haste level", 1.4, 0, 10, 0.1).visible(() -> mode.is("Normal")));
    private final NumberSetting haste = add(new NumberSetting("Haste Amplifier", "Local Haste level", 2, 1, 10, 1).visible(() -> mode.is("Haste")).onChange(v -> removeHaste()));
    private final BooleanSetting instant = add(new BooleanSetting("Instamine", "Immediately complete blocks already over half a break per tick", true).visible(() -> mode.is("Damage")));
    private final BooleanSetting abortAfterStop = add(new BooleanSetting("Abort After Stop", "Meteor's optional extra abort packet after completing a break", false).visible(() -> mode.is("Damage")));
    private String parsed = "";
    private Set<String> selected = Set.of();
    private StatusEffectInstance injected;
    private net.minecraft.client.network.ClientPlayerEntity hastePlayer;
    public SpeedMine() { super("Speed Mine", "Meteor mining speed modes and block filters; anti-cheat off only", Category.ANTI_CHEAT_OFF); }
    @Override public boolean persistEnabled() { return false; }
    @Override protected void onEnable() { active = this; }
    @Override protected void onDisable() { if (active == this) active = null; removeHaste(); }
    private void removeHaste() {
        if (hastePlayer != null && injected != null && hastePlayer.getStatusEffect(StatusEffects.HASTE) == injected) hastePlayer.removeStatusEffect(StatusEffects.HASTE);
        injected = null; hastePlayer = null;
    }
    public boolean filter(Block block) {
        if (!parsed.equals(blocks.get())) {
            parsed = blocks.get(); var ids = new HashSet<String>();
            for (String id : parsed.split("[,;\\s]+")) { if (!id.isBlank()) ids.add(id.contains(":") ? id : "minecraft:" + id); }
            selected = Set.copyOf(ids);
        }
        boolean contains = selected.contains(Registries.BLOCK.getId(block).toString());
        return filterMode.is("Whitelist") == contains;
    }
    @Override public void onTick() {
        if (!inGame() || mc.interactionManager == null) { removeHaste(); return; }
        if (hastePlayer != null && hastePlayer != mc.player) removeHaste();
        if (mode.is("Haste")) {
            var existing = mc.player.getStatusEffect(StatusEffects.HASTE);
            // Leave real finite or stronger effects intact; remove only this exact injected instance.
            if (existing == null || existing == injected && existing.getAmplifier() != haste.getInt() - 1) {
                removeHaste(); injected = new StatusEffectInstance(StatusEffects.HASTE, -1, haste.getInt() - 1, false, false, false);
                hastePlayer = mc.player; mc.player.setStatusEffect(injected, null);
            }
        }
    }
    public static void beforeTick() {
        var m = active;
        if (m == null || !inGame() || mc.interactionManager == null || !m.mode.is("Damage") || AutoBuilder.holdingBreak() || AutoMine.holdingBreak()) return;
        var im = (MiningAccessor) mc.interactionManager;
        var pos = im.maro$breakingPos(); float progress = im.maro$progress();
        if (pos == null || progress <= 0) return;
        var state = mc.world.getBlockState(pos);
        if (m.filter(state.getBlock()) && progress + state.calcBlockBreakingDelta(mc.player, mc.world, pos) >= 0.7f) im.maro$progress(1);
    }
    public static boolean instamine(BlockState state) {
        var m = active; return m != null && m.mode.is("Damage") && m.instant.get() && m.filter(state.getBlock())
            && !AutoBuilder.holdingBreak() && !AutoMine.holdingBreak() && !BreakDelay.preventInstant();
    }
    public static boolean extraAbort() { return active != null && active.mode.is("Damage") && active.abortAfterStop.get(); }
    public static float breakingSpeed(float original, BlockState state) {
        var m = active;
        if (m == null || !m.mode.is("Normal") || !m.filter(state.getBlock()) || !inGame()
            || AutoBuilder.holdingBreak() || AutoMine.holdingBreak() || !(mc.crosshairTarget instanceof BlockHitResult hit)) return original;
        float result = original * m.modifier.get().floatValue();
        float hardness = state.getHardness(mc.world, hit.getBlockPos());
        if (hardness <= 0) return original;
        float divisor = hardness * (mc.player.canHarvest(state) ? 30 : 100);
        return m.modifier.get() >= 1 && original / divisor < 1 && result / divisor >= 1 ? 0.9f * divisor : result;
    }
}
