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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Nametags: a clean tag over every player in place of the game's own, with their health (and
 * absorption), distance and totem pops, and above it what they hold and wear, each
 * piece with its enchantments and durability. Friends get their own colour. Drawn on the screen, so
 * tags stay sharp and readable at any distance.
 */
public class Nametags extends Module {
    private static final int GREEN = 0xFF3DDC97, YELLOW = 0xFFFFC23D, RED = 0xFFFF5D6C, GREY = 0xFFA8B0BF, GOLD = 0xFFFFC94A;

    // ---- who
    private final BooleanSetting self = add(new BooleanSetting("Self", "Your own tag, in third person", false));
    private final BooleanSetting hideNpcs = add(new BooleanSetting("Hide NPCs", "No tag on players missing from the tab list (server NPCs and bots)", true));
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance for players the server sends to you", 1024, 16, 4096, 8).suffix(" blocks"));
    private final BooleanSetting edgeTags = add(new BooleanSetting("Edge Tags", "Names and distances at the screen edge for players outside your view", true));

    // ---- what the tag says
    private final BooleanSetting health = add(new BooleanSetting("Health", "Their health", true));
    private final ModeSetting healthMode = add(new ModeSetting("Health Mode", "Number (20), Percent (100%) or a Bar under the tag", "Number",
            "Number", "Percent", "Bar").visible(health::get));
    private final BooleanSetting absorption = add(new BooleanSetting("Absorption", "Golden hearts as +4 beside the health", true).visible(health::get));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", "How far away they are", true));
    private final BooleanSetting pops = add(new BooleanSetting("Totem Pops", "How many totems they have popped since they last died", true));

    // ---- what they hold and wear
    private final ModeSetting armor = add(new ModeSetting("Armor", "Their armour over the tag", "None", "None", "Above"));
    private final BooleanSetting heldItem = add(new BooleanSetting("Held Item", "What is in their hands, beside the armour", false));
    private final BooleanSetting itemName = add(new BooleanSetting("Item Name", "The name of what they hold, over everything", false));
    private final ModeSetting gearStyle = add(new ModeSetting("Gear Style", "Clean equipment cards or a minimal icon row", "Cards", "Cards", "Minimal")
            .visible(this::showsItems));
    private final BooleanSetting enchants = add(new BooleanSetting("Enchants", "Readable enchantment labels with Roman levels, such as Prot IV and Sharp V", false)
            .visible(this::showsItems));
    private final ModeSetting durability = add(new ModeSetting("Durability", "Off, the item's bar, a percent, or both (Full)", "Full",
            "Off", "Bar", "Percent", "Full").visible(this::showsItems));
    private final BooleanSetting itemBackground = add(new BooleanSetting("Item Background", "A dark square behind each piece", false)
            .visible(() -> showsItems() && gearStyle.is("Minimal")));

    // ---- look
    private final ModeSetting layout = add(new ModeSetting("Layout", "Compact equipment and a quiet nameplate, or a larger detailed card", "Compact", "Compact", "Detailed"));
    private final ModeSetting style = add(new ModeSetting("Style", "Ember fire accents, Aurora neon, your own colours, or the original card", "Ember", "Ember", "Aurora", "Custom", "Classic"));
    private final BooleanSetting glow = add(new BooleanSetting("Glow", "A soft accent glow around the detailed card", true).visible(() -> layout.is("Detailed") && !style.is("Classic")));
    private final NumberSetting glowStrength = add(new NumberSetting("Glow Strength", "How bright the card's glow is", 0.75, 0, 1, 0.05).visible(() -> layout.is("Detailed") && !style.is("Classic") && glow.get()));
    private final BooleanSetting pulse = add(new BooleanSetting("Pulse", "Slowly breathe the accent glow", true).visible(() -> layout.is("Detailed") && !style.is("Classic")));
    private final ColorSetting accent = add(new ColorSetting("Accent Color", "The gradient's first colour", 0xFFFFA84A).visible(() -> style.is("Custom")));
    private final ColorSetting accentEnd = add(new ColorSetting("Second Accent", "The gradient's second colour", 0xFFFF4567).visible(() -> style.is("Custom")));
    private final BooleanSetting healthStrip = add(new BooleanSetting("Health Strip", "A slim health meter under the detailed nameplate", true).visible(() -> layout.is("Detailed") && !style.is("Classic") && health.get()));
    private final BooleanSetting background = add(new BooleanSetting("Background", "A dark rounded card behind the tag", true));
    private final ColorSetting backgroundColor = add(new ColorSetting("Background Color", "The card's colour", 0xB00C0E14, true)
            .visible(background::get));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "A subtle border in Compact; an accent border in Detailed", false).visible(background::get));
    private final ColorSetting textColor = add(new ColorSetting("Text Color", "The name's colour", 0xFFFFFFFF));
    private final BooleanSetting healthColors = add(new BooleanSetting("Health Colors", "Health from green to red", true).visible(health::get));
    private final BooleanSetting friendBackground = add(new BooleanSetting("Friend Background", "Friends' cards in Friend Color", true));
    private final ColorSetting friendColor = add(new ColorSetting("Friend Color", "Friends' colour", 0xFF3E8E4E));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big the tags are", 1, 0.5, 3, 0.05).suffix("x"));
    private final BooleanSetting constantSize = add(new BooleanSetting("Constant Size", "Tags stay the same size however far; off, far ones are smaller", true));
    private final ModeSetting font = add(new ModeSetting("Font", "The client's font, or Minecraft's", "Client", "Client", "Minecraft"));
    private final BooleanSetting shadow = add(new BooleanSetting("Text Shadow", "A shadow under Minecraft's font", true).visible(() -> font.is("Minecraft")));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Players", self, hideNpcs, range, edgeTags),
            SettingSection.of("Tag", health, healthMode, absorption, distance, pops),
            SettingSection.of("Items", armor, heldItem, gearStyle, itemName, enchants, durability, itemBackground),
            SettingSection.of("Look", layout, style, glow, glowStrength, pulse, accent, accentEnd, healthStrip, background, backgroundColor, outline, textColor, healthColors, friendBackground, friendColor,
                    scale, constantSize, font, shadow));

    private static Nametags instance;
    /** Totems popped by each player since they last died. */
    private final Map<UUID, Integer> popped = new HashMap<>();
    private Object popWorld;
    /** What each tag drawn last frame said; for tests. */
    private final List<String> drawn = new ArrayList<>();

    public Nametags() {
        super("Nametags", "Glowing player cards, long-range tags and edge indicators, with health, gear and totem pops", Category.VISUALS);
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

    /** Their tab list entry for the NPC filter. Fake Player counts as you. */
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
        List<float[]> edges = new ArrayList<>();
        Fonts.beginRaw();
        try {
            for (PlayerEntity p : players) {
                Vec3d at = p.getLerpedPos(tickDelta);
                float[] screen = BlockEspRenderer.toScreen(at.x, at.y + p.getHeight() + 0.45, at.z, camera, w, h);
                double dist = Math.sqrt(p.squaredDistanceTo(camera));
                if (screen == null || screen[0] < 0 || screen[0] > w || screen[1] < 0 || screen[1] > h) {
                    if (edgeTags.get() && edges.size() < 12) drawEdge(ctx, p, camera, w, h, dist, edges);
                    continue;
                }
                float s = scale.getFloat() * (layout.is("Compact") ? 0.85f : 1) * (constantSize.get() ? 1f : (float) Math.max(0.45, Math.min(1, 10 / Math.max(1, dist))));
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

        boolean compact = layout.is("Compact"), styled = !style.is("Classic");
        float gap = compact ? 3 : styled ? 4.5f : 3.5f, pad = compact ? 4 : styled ? 7 : 5, tagH = compact ? 12 : styled ? 17 : 13;
        float textW = -gap;
        for (Part part : parts) textW += width(part.text(), part.bold()) + gap;
        float tagW = textW + pad * 2;
        boolean friend = FriendManager.isFriend(p.getName().getString());
        boolean bar = health.get() && (healthMode.is("Bar") || !compact && styled && healthStrip.get());
        int[] colors = accents(friend);
        x = Math.max(tagW * s / 2 + 4, Math.min(ctx.getScaledWindowWidth() - tagW * s / 2 - 4, x));

        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(s, s);
        float left = -tagW / 2f, top = -tagH - (bar ? 2.5f : 0);
        if (background.get()) {
            float height = tagH + (bar ? 2.5f : 0);
            if (!compact && styled && glow.get()) glow(ctx, left, top, tagW, height, colors);
            int bg = backgroundColor.get();
            if (friend && friendBackground.get()) bg = ColorUtil.withAlpha(friendColor.get(), Math.max(0x90, bg >>> 24));
            if (compact) Render2D.roundRect(ctx, left, top, tagW, height, 3, bg);
            else if (styled) {
                Render2D.roundGradientV(ctx, left, top, tagW, height, 5, ColorUtil.lerp(bg, ColorUtil.withAlpha(colors[0], bg >>> 24), 0.12f), bg);
                Render2D.roundOutline(ctx, left, top, tagW, height, 5, 0.75f,
                        ColorUtil.withAlpha(colors[0], 150), ColorUtil.withAlpha(colors[1], 150), ColorUtil.withAlpha(colors[1], 70), ColorUtil.withAlpha(colors[0], 70));
            } else Render2D.roundRect(ctx, left, top, tagW, height, 4, bg);
            if (outline.get()) {
                if (compact) Render2D.roundOutline(ctx, left, top, tagW, height, 3, 0.5f, 0x385C6576);
                else if (styled) Render2D.roundOutline(ctx, left, top, tagW, height, 5, 1, colors[0], colors[1], colors[1], colors[0]);
                else Render2D.roundOutline(ctx, left, top, tagW, height, 4, 1, friend ? friendColor.get() : Theme.accent(0xA0));
            }
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

    private int[] accents(boolean friend) {
        if (friend) return new int[] {friendColor.get() | 0xFF000000, ColorUtil.shade(friendColor.get() | 0xFF000000, 0.4f)};
        if (style.is("Aurora")) return new int[] {0xFFB68AFF, 0xFF53E8EF};
        if (style.is("Custom")) return new int[] {accent.get() | 0xFF000000, accentEnd.get() | 0xFF000000};
        return new int[] {0xFFFFAA4C, 0xFFFF4D69};
    }

    private void glow(DrawContext ctx, float x, float y, float w, float h, int[] colors) {
        float strength = glowStrength.getFloat() * (pulse.get() ? 0.82f + 0.18f * (float) Math.sin(System.nanoTime() / 900_000_000.0) : 1);
        for (int i = 3; i >= 1; i--) {
            float spread = i * 1.6f;
            int opacity = Math.round((45 - i * 8) * strength);
            Render2D.roundOutline(ctx, x - spread, y - spread, w + spread * 2, h + spread * 2, 5 + spread, 1.8f,
                    ColorUtil.withAlpha(colors[0], opacity), ColorUtil.withAlpha(colors[1], opacity),
                    ColorUtil.withAlpha(colors[1], opacity), ColorUtil.withAlpha(colors[0], opacity));
        }
    }

    private void drawEdge(DrawContext ctx, PlayerEntity player, Vec3d camera, int w, int h, double dist, List<float[]> placed) {
        double angle = Math.atan2(-(player.getX() - camera.x), player.getZ() - camera.z)
                - Math.toRadians(mc.gameRenderer.getCamera().getYaw());
        float dx = (float) Math.sin(angle), dy = -(float) Math.cos(angle);
        String label = player.getName().getString() + "  " + Math.round(dist) + "m";
        boolean compact = layout.is("Compact");
        float edgeScale = compact ? 0.85f : 1;
        float tagW = (width(label, true) + 14) * edgeScale, halfW = w / 2f - tagW / 2 - 14, halfH = h / 2f - (dy > 0 ? 68 : 24);
        if (halfW <= 0 || halfH <= 0) return;
        float length = Math.min(halfW / Math.max(0.001f, Math.abs(dx)), halfH / Math.max(0.001f, Math.abs(dy)));
        float x = w / 2f + dx * length, y = h / 2f + dy * length;
        for (int tries = 0; tries < 12; tries++) {
            boolean overlaps = false;
            for (float[] tag : placed) if (Math.abs(x - tag[0]) < (tagW + tag[2]) / 2 + 5 && Math.abs(y - tag[1]) < 21) { overlaps = true; break; }
            if (!overlaps) break;
            y += y > h / 2f ? -22 : 22;
            if (tries == 11 || y < 15 || y > h - 68) return;
        }
        placed.add(new float[] {x, y, tagW});
        drawn.add(label);
        int[] colors = accents(FriendManager.isFriend(player.getName().getString()));
        if (!compact && glow.get() && !style.is("Classic")) glow(ctx, x - tagW / 2, y - 8, tagW, 16, colors);
        float edgeH = compact ? 12 : 16;
        Render2D.roundRect(ctx, x - tagW / 2, y - edgeH / 2, tagW, edgeH, 3, 0xDC0C0E14);
        if (compact) Render2D.roundOutline(ctx, x - tagW / 2, y - edgeH / 2, tagW, edgeH, 3, 0.5f, 0x385C6576);
        else Render2D.roundOutline(ctx, x - tagW / 2, y - 8, tagW, 16, 5, 0.8f,
                colors[0], colors[1], colors[1], colors[0]);
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x - width(label, true) * edgeScale / 2, y);
        matrices.scale(edgeScale, edgeScale);
        text(ctx, label, 0, 0, 0xFFF5F1FF, true);
        matrices.popMatrix();
        // The chevron points towards the player's bearing, including players behind the camera.
        float tipX = x + dx * (Math.abs(dx) > 0.7f ? tagW / 2 + 6 : 12), tipY = y + dy * 13;
        Render2D.line(ctx, tipX, tipY, tipX - dx * 4 + dy * 3, tipY - dy * 4 - dx * 3, 1.4f, colors[0]);
        Render2D.line(ctx, tipX, tipY, tipX - dx * 4 - dy * 3, tipY - dy * 4 + dx * 3, 1.4f, colors[1]);
    }

    /** The pieces of the tag, left to right. */
    private List<Part> parts(PlayerEntity p, double dist) {
        List<Part> parts = new ArrayList<>();
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
        if (gearStyle.is("Cards")) return itemCards(ctx, p, stacks, bottom);
        float cell = 18;
        if (enchants.get()) for (ItemStack stack : stacks) for (String line : enchantLines(stack)) {
            String label = line.startsWith("!") ? line.substring(1) : line;
            cell = Math.max(cell, width(label, false) * 0.65f + 5);
        }
        float rowW = stacks.size() * cell, left = -rowW / 2f, top = bottom - 17;
        boolean bar = durability.is("Bar") || durability.is("Full"), percent = durability.is("Percent") || durability.is("Full");
        float highest = top;
        var matrices = ctx.getMatrices();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            float ix = left + i * cell + (cell - 16) / 2;
            if (itemBackground.get()) Render2D.roundRect(ctx, ix - 0.5f, top - 0.5f, 17, 17, 3, 0x90000000);
            matrices.pushMatrix();
            matrices.translate(ix, top);
            ctx.drawItem(stack, 0, 0);
            if (bar) ctx.drawStackOverlay(mc.textRenderer, stack, 0, 0);
            matrices.popMatrix();
            float above = top - 1;
            if (percent && stack.isDamageable() && stack.getMaxDamage() > 0) {
                float left01 = 1 - stack.getDamage() / (float) stack.getMaxDamage();
                small(ctx, Math.round(left01 * 100) + "%", ix + 8, above - 3, left01 < 0.25f ? RED : 0xFFCDD3DF);
                above -= 8;
            }
            if (enchants.get()) {
                for (String line : enchantLines(stack)) {
                    boolean curse = line.startsWith("!");
                    small(ctx, curse ? line.substring(1) : line, ix + 8, above - 3, curse ? 0xFFFF8B96 : 0xFFCDD3DF);
                    above -= 8;
                }
            }
            highest = Math.min(highest, above);
        }
        return highest;
    }

    private float itemCards(DrawContext ctx, PlayerEntity player, List<ItemStack> stacks, float bottom) {
        boolean compact = layout.is("Compact");
        boolean bar = durability.is("Bar") || durability.is("Full");
        boolean percent = durability.is("Percent") || durability.is("Full");
        List<List<String>> labels = new ArrayList<>();
        float cardW = compact ? 21 : 26;
        int lines = 0;
        for (ItemStack stack : stacks) {
            List<String> itemLabels = enchants.get() ? enchantLines(stack) : List.of();
            labels.add(itemLabels);
            lines = Math.max(lines, itemLabels.size());
            for (String label : itemLabels) {
                String shown = label.startsWith("!") ? label.substring(1) : label;
                cardW = Math.max(cardW, width(shown, false) * GEAR_TEXT_SCALE + (compact ? 6 : 10));
            }
        }
        float headerH = compact ? 16 + (percent ? 6 : 0) + (bar ? 2 : 0) : 23 + (percent ? 8 : 0) + (bar ? 3 : 0);
        float lineH = compact ? 7.5f : 8.5f, gap = compact ? 2 : 3;
        float cardH = headerH + (lines > 0 ? 4 + lines * lineH : 0);
        float rowW = stacks.size() * (cardW + gap) - gap, left = -rowW / 2, top = bottom - cardH - 1;
        int[] colors = accents(FriendManager.isFriend(player.getName().getString()));
        var matrices = ctx.getMatrices();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            float x = left + i * (cardW + gap), cx = x + cardW / 2;
            Render2D.roundRect(ctx, x, top, cardW, cardH, compact ? 3 : 4, 0xCD10131B);
            if (compact) Render2D.roundOutline(ctx, x, top, cardW, cardH, 3, 0.4f, 0x285C6576);
            else Render2D.roundOutline(ctx, x, top, cardW, cardH, 4, 0.6f,
                    ColorUtil.withAlpha(colors[0], 65), ColorUtil.withAlpha(colors[1], 65),
                    0x305C6576, 0x305C6576);
            matrices.pushMatrix();
            matrices.translate(cx - (compact ? 6 : 8), top + (compact ? 2 : 3));
            if (compact) matrices.scale(0.75f, 0.75f);
            ctx.drawItem(stack, 0, 0);
            matrices.popMatrix();
            float fraction = stack.isDamageable() && stack.getMaxDamage() > 0
                    ? Math.max(0, Math.min(1, 1 - stack.getDamage() / (float) stack.getMaxDamage())) : -1;
            if (percent && fraction >= 0) gearText(ctx, Math.round(fraction * 100) + "%", cx, top + (compact ? 18.5f : 24),
                    fraction < 0.25f ? RED : 0xFFCDD3DF);
            if (bar && fraction >= 0) {
                float by = top + headerH - (compact ? 2 : 4);
                Render2D.roundRect(ctx, x + 4, by, cardW - 8, 1.5f, 0.75f, 0xFF303746);
                Render2D.roundRect(ctx, x + 4, by, (cardW - 8) * fraction, 1.5f, 0.75f,
                        fraction < 0.25f ? RED : ColorUtil.lerp(colors[0], colors[1], 0.4f));
            }
            if (lines > 0) {
                Render2D.rect(ctx, x + 4, top + headerH, cardW - 8, 0.5f, 0x305C6576);
                List<String> itemLabels = labels.get(i);
                for (int j = 0; j < itemLabels.size(); j++) {
                    String label = itemLabels.get(j);
                    boolean curse = label.startsWith("!");
                    gearText(ctx, curse ? label.substring(1) : label, cx, top + headerH + 5 + j * lineH,
                            curse ? 0xFFFF8B96 : 0xFFCDD3DF);
                }
            }
            if (stack.getCount() > 1) gearText(ctx, String.valueOf(stack.getCount()), x + cardW - 6, top + 16, 0xFFF3F5FA);
        }
        return top;
    }

    private static final float GEAR_TEXT_SCALE = 0.75f;

    private void gearText(DrawContext ctx, String label, float cx, float cy, int color) {
        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(cx - width(label, false) * GEAR_TEXT_SCALE / 2, cy);
        matrices.scale(GEAR_TEXT_SCALE, GEAR_TEXT_SCALE);
        text(ctx, label, 0, 0, color, false);
        matrices.popMatrix();
    }

    /** Text drawn small and centred on (cx, cy), for durability and enchantments. */
    private void small(DrawContext ctx, String s, float cx, float cy, int color) {
        var matrices = ctx.getMatrices();
        float k = 0.65f;
        matrices.pushMatrix();
        matrices.translate(cx - width(s, false) * k / 2f, cy);
        matrices.scale(k, k);
        text(ctx, s, 0, 0, color, false);
        matrices.popMatrix();
    }

    /** Readable, consistently ordered labels; curses retain their colour marker. */
    public static List<String> enchantLines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        ItemEnchantmentsComponent enchantments = stack.getEnchantments();
        for (RegistryEntry<Enchantment> e : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(e);
            String path = e.getKey().map(k -> k.getValue().getPath()).orElse("?");
            String name = SHORT.getOrDefault(path, shorten(path));
            String line = level > 1 ? name + " " + romanLevel(level) : name;
            out.add(e.isIn(EnchantmentTags.CURSE) ? "!" + line : line);
        }
        out.sort(Comparator.comparing((String label) -> label.startsWith("!")).thenComparing(String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String romanLevel(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }

    private static final Map<String, String> SHORT = Map.ofEntries(
            Map.entry("protection", "Prot"), Map.entry("sharpness", "Sharp"), Map.entry("unbreaking", "Unb"), Map.entry("mending", "Mending"),
            Map.entry("efficiency", "Eff"), Map.entry("fortune", "Fort"), Map.entry("silk_touch", "Silk"), Map.entry("fire_aspect", "Fire"),
            Map.entry("looting", "Loot"), Map.entry("feather_falling", "Feather"), Map.entry("blast_protection", "Blast"),
            Map.entry("projectile_protection", "Proj"), Map.entry("fire_protection", "Fire Prot"), Map.entry("thorns", "Thorns"),
            Map.entry("depth_strider", "Depth"), Map.entry("respiration", "Resp"), Map.entry("aqua_affinity", "Aqua"),
            Map.entry("knockback", "Knock"), Map.entry("power", "Power"), Map.entry("punch", "Punch"), Map.entry("flame", "Flame"),
            Map.entry("infinity", "Infinity"), Map.entry("swift_sneak", "Sneak"), Map.entry("soul_speed", "Soul"), Map.entry("smite", "Smite"),
            Map.entry("bane_of_arthropods", "Bane"), Map.entry("sweeping_edge", "Sweep"), Map.entry("binding_curse", "Binding"),
            Map.entry("vanishing_curse", "Vanishing"), Map.entry("density", "Density"), Map.entry("breach", "Breach"), Map.entry("wind_burst", "Wind"),
            Map.entry("riptide", "Riptide"), Map.entry("loyalty", "Loyalty"), Map.entry("channeling", "Channel"), Map.entry("impaling", "Impale"),
            Map.entry("multishot", "Multi"), Map.entry("piercing", "Pierce"), Map.entry("quick_charge", "Charge"), Map.entry("lure", "Lure"),
            Map.entry("luck_of_the_sea", "Luck"), Map.entry("frost_walker", "Frost"));

    private static String shorten(String path) {
        String word = path.contains("_") ? path.substring(0, path.indexOf('_')) : path;
        word = word.substring(0, Math.min(4, word.length()));
        return word.isEmpty() ? "?" : Character.toUpperCase(word.charAt(0)) + word.substring(1);
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
