package dev.maro.module.impl.visuals;

import dev.maro.config.FriendManager;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.render.esp.BlockEspRenderer;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Nametags: a clean tag over every player in place of the game's own, with their health (and
 * absorption), distance, ping, game mode and totem pops, and above it what they hold and wear, each
 * piece with its enchantments and durability. Friends get their own colour. Drawn on the screen, so
 * tags stay sharp and readable at any distance.
 */
public class Nametags extends Module {
    private static final int GREEN = 0xFF3DDC97, YELLOW = 0xFFFFC23D, RED = 0xFFFF5D6C, GREY = 0xFFA8B0BF, GOLD = 0xFFFFC94A;

    // ---- who
    private final BooleanSetting self = add(new BooleanSetting("Self", "Your own tag, in third person", false));
    private final BooleanSetting hideNpcs = add(new BooleanSetting("Hide NPCs", "No tag on players missing from the tab list (server NPCs and bots)", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Tags on players this close", 256, 16, 512, 8).suffix(" blocks"));

    // ---- what the tag says
    private final BooleanSetting health = add(new BooleanSetting("Health", "Their health", true));
    private final ModeSetting healthMode = add(new ModeSetting("Health Mode", "Number (20), Percent (100%) or a Bar under the tag", "Number",
            "Number", "Percent", "Bar").visible(health::get));
    private final BooleanSetting absorption = add(new BooleanSetting("Absorption", "Golden hearts as +4 beside the health", true).visible(health::get));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", "How far away they are", true));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", "Their ping, green to red", false));
    private final BooleanSetting gamemode = add(new BooleanSetting("Gamemode", "S, C, A or SP before the name", false));
    private final BooleanSetting pops = add(new BooleanSetting("Totem Pops", "How many totems they have popped since they last died", true));

    // ---- what they hold and wear
    private final ModeSetting armor = add(new ModeSetting("Armor", "Their armour over the tag", "None", "None", "Above"));
    private final BooleanSetting heldItem = add(new BooleanSetting("Held Item", "What is in their hands, beside the armour", false));
    private final BooleanSetting itemName = add(new BooleanSetting("Item Name", "The name of what they hold, over everything", false));
    private final BooleanSetting enchants = add(new BooleanSetting("Enchants", "Short enchantment names over each piece (Prot4, Shrp5)", false)
            .visible(this::showsItems));
    private final ModeSetting durability = add(new ModeSetting("Durability", "Off, the item's bar, a percent, or both (Full)", "Full",
            "Off", "Bar", "Percent", "Full").visible(this::showsItems));
    private final BooleanSetting itemBackground = add(new BooleanSetting("Item Background", "A dark square behind each piece", false)
            .visible(this::showsItems));

    // ---- look
    private final BooleanSetting background = add(new BooleanSetting("Background", "A dark rounded card behind the tag", true));
    private final ColorSetting backgroundColor = add(new ColorSetting("Background Color", "The card's colour", 0xB00C0E14, true)
            .visible(background::get));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "A thin accent line round the card", false).visible(background::get));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "The name's colour", 0xFFFFFFFF));
    private final BooleanSetting healthColors = add(new BooleanSetting("Health Colors", "Health from green to red", true).visible(health::get));
    private final BooleanSetting friendBackground = add(new BooleanSetting("Friend Background", "Friends' cards in Friend Color", true));
    private final ColorSetting friendColor = add(new ColorSetting("Friend Color", "Friends' colour", 0xFF3E8E4E));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big the tags are", 1, 0.5, 3, 0.05).suffix("x"));
    private final BooleanSetting constantSize = add(new BooleanSetting("Constant Size", "Tags stay the same size however far; off, far ones are smaller", true));
    private final ModeSetting font = add(new ModeSetting("Font", "The client's font, or Minecraft's", "Client", "Client", "Minecraft"));
    private final BooleanSetting shadow = add(new BooleanSetting("Text Shadow", "A shadow under Minecraft's font", true).visible(() -> font.is("Minecraft")));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Players", self, hideNpcs, range),
            SettingSection.of("Tag", health, healthMode, absorption, distance, ping, gamemode, pops),
            SettingSection.of("Items", armor, heldItem, itemName, enchants, durability, itemBackground),
            SettingSection.of("Look", background, backgroundColor, outline, textColor, healthColors, friendBackground, friendColor,
                    scale, constantSize, font, shadow));

    private static Nametags instance;
    /** Totems popped by each player since they last died. */
    private final Map<UUID, Integer> popped = new HashMap<>();
    private Object popWorld;
    /** What each tag drawn last frame said; for tests. */
    private final List<String> drawn = new ArrayList<>();

    public Nametags() {
        super("Nametags", "Clean tags over players with health, distance, ping, pops, armour and enchants", Category.VISUALS);
        instance = this;
    }

    public static Nametags get() {
        return instance != null ? instance : ModuleManager.get(Nametags.class);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    private boolean showsItems() {
        return armor.is("Above") || heldItem.get();
    }

    // ---- who gets a tag ----------------------------------------------------------------------------

    /** Whether the game's own label over this entity is left out, because a tag is drawn instead. */
    public static boolean hidesVanilla(Entity entity) {
        Nametags m = instance;
        return m != null && m.isEnabled() && entity instanceof PlayerEntity player && m.tags(player);
    }

    private boolean tags(PlayerEntity player) {
        if (mc.player == null || mc.world == null) return false;
        if (player == mc.player) {
            if (!self.get() || mc.options.getPerspective().isFirstPerson()) return false;
        } else if (hideNpcs.get() && entry(player) == null) {
            return false;
        }
        double reach = range.get();
        return player.squaredDistanceTo(mc.gameRenderer.getCamera().getCameraPos()) <= reach * reach;
    }

    /** Their tab list entry: ping and game mode. Fake Player counts as you. */
    private PlayerListEntry entry(PlayerEntity player) {
        if (mc.getNetworkHandler() == null) return null;
        FakePlayer fake = FakePlayer.get();
        UUID id = fake != null && fake.isFake(player) ? mc.player.getUuid() : player.getUuid();
        return mc.getNetworkHandler().getPlayerListEntry(id);
    }

    // ---- totem pops --------------------------------------------------------------------------------

    /** A player popped a totem (status 35) or died (status 3); from the network handler. */
    public static void entityStatus(Entity entity, byte status) {
        Nametags m = instance;
        if (m == null || !(entity instanceof PlayerEntity player)) return;
        m.syncWorld();
        if (status == 35) m.popped.merge(player.getUuid(), 1, Integer::sum);
        else if (status == 3) m.popped.remove(player.getUuid());
    }

    private void syncWorld() {
        if (mc.world != popWorld) {
            popWorld = mc.world;
            popped.clear();
        }
    }

    public int popsOf(PlayerEntity player) {
        FakePlayer fake = FakePlayer.get();
        if (fake != null && fake.isFake(player)) return fake.totemPops();
        syncWorld();
        return popped.getOrDefault(player.getUuid(), 0);
    }

    // ---- drawing -------------------------------------------------------------------------------------

    private record Part(String text, int color, boolean bold) {
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        drawn.clear();
        if (!inGame() || mc.options.hudHidden) return;
        Vec3d camera = mc.gameRenderer.getCamera().getCameraPos();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        List<PlayerEntity> players = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) if (tags(p)) players.add(p);
        // Farthest first, so nearer tags sit on top.
        players.sort(Comparator.comparingDouble((PlayerEntity p) -> p.squaredDistanceTo(camera)).reversed());
        Fonts.beginRaw();
        try {
            for (PlayerEntity p : players) {
                Vec3d at = p.getLerpedPos(tickDelta);
                float[] screen = BlockEspRenderer.toScreen(at.x, at.y + p.getHeight() + 0.45, at.z, camera, w, h);
                if (screen == null || screen[0] < -150 || screen[0] > w + 150 || screen[1] < -100 || screen[1] > h + 100) continue;
                double dist = Math.sqrt(p.squaredDistanceTo(camera));
                float s = scale.getFloat() * (constantSize.get() ? 1f : (float) Math.max(0.45, Math.min(1, 10 / Math.max(1, dist))));
                draw(ctx, p, screen[0], screen[1], s, dist);
            }
        } finally {
            Fonts.endRaw();
        }
    }

    private void draw(DrawContext ctx, PlayerEntity p, float x, float y, float s, double dist) {
        List<Part> parts = parts(p, dist);
        StringBuilder said = new StringBuilder();
        for (Part part : parts) said.append(part.text()).append(' ');
        drawn.add(said.toString().trim());

        float gap = 3.5f, pad = 5, tagH = 13;
        float textW = -gap;
        for (Part part : parts) textW += width(part.text(), part.bold()) + gap;
        float tagW = textW + pad * 2;
        boolean friend = FriendManager.isFriend(p.getName().getString());
        boolean bar = health.get() && healthMode.is("Bar");

        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(s, s);
        float left = -tagW / 2f, top = -tagH - (bar ? 2.5f : 0);
        if (background.get()) {
            int bg = backgroundColor.get();
            if (friend && friendBackground.get()) bg = ColorUtil.withAlpha(friendColor.get(), Math.max(0x90, bg >>> 24));
            Render2D.roundRect(ctx, left, top, tagW, tagH + (bar ? 2.5f : 0), 4, bg);
            if (outline.get()) Render2D.roundOutline(ctx, left, top, tagW, tagH + (bar ? 2.5f : 0), 4, 1, friend ? friendColor.get() : Theme.accent(0xA0));
        }
        float tx = left + pad, cy = top + tagH / 2f;
        for (Part part : parts) {
            text(ctx, part.text(), tx, cy, part.color(), part.bold());
            tx += width(part.text(), part.bold()) + gap;
        }
        if (bar) {
            float fraction = healthFraction(p);
            Render2D.roundRect(ctx, left + 3, top + tagH - 0.5f, tagW - 6, 2, 1, 0x60000000);
            Render2D.roundRect(ctx, left + 3, top + tagH - 0.5f, (tagW - 6) * fraction, 2, 1, healthColor(fraction));
        }
        float itemsTop = top - 2;
        if (showsItems()) itemsTop = items(ctx, p, top - 2);
        if (itemName.get() && !p.getMainHandStack().isEmpty()) {
            String name = p.getMainHandStack().getName().getString();
            float nw = width(name, false) * 0.85f;
            matrices.pushMatrix();
            matrices.translate(-nw / 2f, itemsTop - 6);
            matrices.scale(0.85f, 0.85f);
            text(ctx, name, 0, 0, GREY, false);
            matrices.popMatrix();
        }
        matrices.popMatrix();
    }

    /** The pieces of the tag, left to right. */
    private List<Part> parts(PlayerEntity p, double dist) {
        List<Part> parts = new ArrayList<>();
        PlayerListEntry entry = entry(p);
        if (gamemode.get() && entry != null && entry.getGameMode() != null) parts.add(new Part(shortMode(entry.getGameMode()), GREY, true));
        if (ping.get() && entry != null) {
            int ms = entry.getLatency();
            parts.add(new Part(ms + "ms", ms < 80 ? GREEN : ms < 150 ? YELLOW : RED, false));
        }
        boolean friend = FriendManager.isFriend(p.getName().getString());
        int nameColor = friend && !friendBackground.get() ? friendColor.get() | 0xFF000000 : textColor.get() | 0xFF000000;
        parts.add(new Part(p.getName().getString(), nameColor, true));
        if (health.get() && !healthMode.is("Bar")) {
            float fraction = healthFraction(p);
            String hp = healthMode.is("Percent") ? Math.round(fraction * 100) + "%" : number(p.getHealth());
            parts.add(new Part(hp, healthColors.get() ? healthColor(fraction) : 0xFFFFFFFF, true));
        }
        if (health.get() && absorption.get() && p.getAbsorptionAmount() > 0) parts.add(new Part("+" + number(p.getAbsorptionAmount()), GOLD, true));
        if (distance.get()) parts.add(new Part(Math.round(dist) + "m", GREY, false));
        if (pops.get()) {
            int n = popsOf(p);
            if (n > 0) parts.add(new Part("-" + n, RED, true));
        }
        return parts;
    }

    /** The row of what they wear and hold, with durability and enchantments; returns its top. */
    private float items(DrawContext ctx, PlayerEntity p, float bottom) {
        List<ItemStack> stacks = new ArrayList<>();
        if (heldItem.get()) stacks.add(p.getMainHandStack());
        if (armor.is("Above")) {
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                stacks.add(p.getEquippedStack(slot));
            }
        }
        if (heldItem.get()) stacks.add(p.getOffHandStack());
        stacks.removeIf(ItemStack::isEmpty);
        if (stacks.isEmpty()) return bottom;
        float cell = 18, rowW = stacks.size() * cell, left = -rowW / 2f, top = bottom - 17;
        boolean bar = durability.is("Bar") || durability.is("Full"), percent = durability.is("Percent") || durability.is("Full");
        float highest = top;
        var matrices = ctx.getMatrices();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            float ix = left + i * cell + 1;
            if (itemBackground.get()) Render2D.roundRect(ctx, ix - 0.5f, top - 0.5f, 17, 17, 3, 0x90000000);
            matrices.pushMatrix();
            matrices.translate(ix, top);
            ctx.drawItem(stack, 0, 0);
            if (bar) ctx.drawStackOverlay(mc.textRenderer, stack, 0, 0);
            matrices.popMatrix();
            float above = top - 1;
            if (percent && stack.isDamageable() && stack.getMaxDamage() > 0) {
                float left01 = 1 - stack.getDamage() / (float) stack.getMaxDamage();
                small(ctx, Math.round(left01 * 100) + "%", ix + 8, above - 3, healthColor(left01));
                above -= 6;
            }
            if (enchants.get()) {
                for (String line : enchantLines(stack)) {
                    boolean curse = line.startsWith("!");
                    small(ctx, curse ? line.substring(1) : line, ix + 8, above - 3, curse ? RED : 0xFFE0D2FF);
                    above -= 6;
                }
            }
            highest = Math.min(highest, above);
        }
        return highest;
    }

    /** Text drawn small and centred on (cx, cy), for durability and enchantments. */
    private void small(DrawContext ctx, String s, float cx, float cy, int color) {
        var matrices = ctx.getMatrices();
        float k = 0.55f;
        matrices.pushMatrix();
        matrices.translate(cx - width(s, true) * k / 2f, cy);
        matrices.scale(k, k);
        text(ctx, s, 0, 0, color, true);
        matrices.popMatrix();
    }

    /** "Prot4", "Mend": each enchantment shortened, curses marked with a leading '!'. */
    public static List<String> enchantLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        for (RegistryEntry<Enchantment> e : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(e);
            String path = e.getKey().map(k -> k.getValue().getPath()).orElse("?");
            String name = SHORT.getOrDefault(path, shorten(path));
            String line = level > 1 ? name + level : name;
            out.add(e.isIn(EnchantmentTags.CURSE) ? "!" + line : line);
        }
        return out;
    }

    private static final Map<String, String> SHORT = Map.ofEntries(
            Map.entry("protection", "Prot"), Map.entry("sharpness", "Shrp"), Map.entry("unbreaking", "Unb"), Map.entry("mending", "Mend"),
            Map.entry("efficiency", "Eff"), Map.entry("fortune", "Fort"), Map.entry("silk_touch", "Silk"), Map.entry("fire_aspect", "FA"),
            Map.entry("looting", "Loot"), Map.entry("feather_falling", "FF"), Map.entry("blast_protection", "Blast"),
            Map.entry("projectile_protection", "Proj"), Map.entry("fire_protection", "FP"), Map.entry("thorns", "Thrn"),
            Map.entry("depth_strider", "DS"), Map.entry("respiration", "Resp"), Map.entry("aqua_affinity", "Aqua"),
            Map.entry("knockback", "KB"), Map.entry("power", "Pow"), Map.entry("punch", "Pnch"), Map.entry("flame", "Flm"),
            Map.entry("infinity", "Inf"), Map.entry("swift_sneak", "SS"), Map.entry("soul_speed", "Soul"), Map.entry("smite", "Smt"),
            Map.entry("bane_of_arthropods", "BoA"), Map.entry("sweeping_edge", "Swp"), Map.entry("binding_curse", "Bind"),
            Map.entry("vanishing_curse", "Van"), Map.entry("density", "Dens"), Map.entry("breach", "Brch"), Map.entry("wind_burst", "Wind"),
            Map.entry("riptide", "Rip"), Map.entry("loyalty", "Loy"), Map.entry("channeling", "Chan"), Map.entry("impaling", "Imp"),
            Map.entry("multishot", "Multi"), Map.entry("piercing", "Pier"), Map.entry("quick_charge", "QC"), Map.entry("lure", "Lure"),
            Map.entry("luck_of_the_sea", "Luck"), Map.entry("frost_walker", "FW"));

    private static String shorten(String path) {
        String word = path.contains("_") ? path.substring(0, path.indexOf('_')) : path;
        word = word.substring(0, Math.min(4, word.length()));
        return word.isEmpty() ? "?" : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    private static String shortMode(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "S";
            case CREATIVE -> "C";
            case ADVENTURE -> "A";
            case SPECTATOR -> "SP";
        };
    }

    private static float healthFraction(PlayerEntity p) {
        return Math.max(0, Math.min(1, p.getHealth() / Math.max(1, p.getMaxHealth())));
    }

    private static int healthColor(float fraction) {
        return fraction > 0.5f ? ColorUtil.lerp(YELLOW, GREEN, (fraction - 0.5f) * 2) : ColorUtil.lerp(RED, YELLOW, fraction * 2);
    }

    private static String number(float value) {
        float rounded = Math.round(value * 2) / 2f;
        return rounded == Math.floor(rounded) ? String.valueOf((int) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    // ---- text in the chosen font ---------------------------------------------------------------------

    private static final float CLIENT_SIZE = 0.8f;

    private boolean vanillaFont() {
        return font.is("Minecraft");
    }

    private float width(String s, boolean bold) {
        return vanillaFont() ? mc.textRenderer.getWidth(s) : Fonts.width(s, bold, CLIENT_SIZE);
    }

    /** Text with its left edge at x and its middle at cy. */
    private void text(DrawContext ctx, String s, float x, float cy, int color, boolean bold) {
        if (!vanillaFont()) {
            Fonts.drawV(ctx, s, x, cy, color, bold, CLIENT_SIZE);
            return;
        }
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, cy - 4);
        ctx.drawText(mc.textRenderer, s, 0, 0, color | 0xFF000000, shadow.get());
        matrices.popMatrix();
    }

    // ---- for tests -------------------------------------------------------------------------------------

    /** What each tag drawn in the last frame said. */
    public List<String> drawnTags() {
        return new ArrayList<>(drawn);
    }

    /** What a player's tag would say now. */
    public String describe(PlayerEntity p) {
        StringBuilder out = new StringBuilder();
        for (Part part : parts(p, Math.sqrt(p.squaredDistanceTo(mc.gameRenderer.getCamera().getCameraPos())))) out.append(part.text()).append(' ');
        return out.toString().trim();
    }
}
