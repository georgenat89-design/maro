package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Your whole inventory on the HUD: armour and offhand, the 27 storage slots and the hotbar with
 * the held slot lit, on a dark panel with a capacity bar. Placed by dragging, at any scale.
 */
public class InventoryHud extends Module implements HudElement {
    // Layout in GUI pixels at a Scale of 1. Whole numbers, so items land on whole pixels.
    private static final int SLOT = 18;
    private static final int GAP = 2;
    private static final int PAD = 6;
    private static final int HEADER = 8;
    private static final int BAR = 2;
    private static final int SECTION = 5;
    private static final int COLUMNS = 9;
    private static final float RADIUS = 6;
    private static final float SLOT_RADIUS = 3.5f;
    /** Air kept between the panel and the edges of the screen. */
    private static final int MARGIN = 4;

    private static final Identifier[] ARMOR_SPRITES = {
            Identifier.ofVanilla("container/slot/helmet"),
            Identifier.ofVanilla("container/slot/chestplate"),
            Identifier.ofVanilla("container/slot/leggings"),
            Identifier.ofVanilla("container/slot/boots")};
    private static final Identifier OFFHAND_SPRITE = Identifier.ofVanilla("container/slot/shield");
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag the panel where you want it and scroll to resize it", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 100, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 100, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big the panel is", 1, 0.5, 2.5, 0.05).suffix("x"));
    private final BooleanSetting header = add(new BooleanSetting("Header", "The title and how many slots are in use", true));
    private final BooleanSetting capacity = add(new BooleanSetting("Capacity Bar", "A thin bar that fills up with your inventory", true).visible(header::get));
    private final BooleanSetting equipment = add(new BooleanSetting("Equipment", "Your armour and offhand over the grid", true));
    private final BooleanSetting hotbar = add(new BooleanSetting("Hotbar", "Your hotbar under the grid, with the held slot lit", true));
    private final ModeSetting slots = add(new ModeSetting("Slots", "How the slots are drawn", "Soft", "Soft", "Outlined", "Hidden"));
    private final BooleanSetting rarity = add(new BooleanSetting("Rarity Tint", "Tints the slot behind uncommon, rare and epic items", true));
    private final BooleanSetting lowDurability = add(new BooleanSetting("Low Durability", "Marks tools and armour about to break in red", true));
    private final BooleanSetting tooltips = add(new BooleanSetting("Tooltips", "Hover an item while chat or another screen is open to see what it is", true));
    private final BooleanSetting hideInInventory = add(new BooleanSetting("Hide In Inventory", "Hides the panel while your inventory, a chest or another container is open", true));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the panel is", 88, 0, 100, 1).suffix("%"));
    private final ColorSetting background = add(new ColorSetting("Background", "The panel colour", 0xFF0B0D12));

    /** One slot as laid out this frame, in the panel's own unscaled coordinates. */
    private record Slot(int x, int y, ItemStack stack, Identifier empty, boolean held) {
    }

    public InventoryHud() {
        super("Inventory", "Shows your whole inventory on screen", Category.VISUALS);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Placement", position, x, y, scale),
                SettingSection.of("Contents", header, capacity, equipment, hotbar, tooltips, hideInInventory),
                SettingSection.of("Look", slots, rarity, lowDurability, opacity, background));
    }

    // ---- placement ----------------------------------------------------------------------

    private int panelWidth() {
        return PAD * 2 + COLUMNS * SLOT + (COLUMNS - 1) * GAP;
    }

    private int panelHeight() {
        int h = PAD;
        if (header.get()) h += HEADER + (capacity.get() ? 3 + BAR : 0) + SECTION;
        if (equipment.get()) h += SLOT + SECTION;
        h += 3 * SLOT + 2 * GAP;
        if (hotbar.get()) h += SECTION + SLOT;
        return h + PAD;
    }

    @Override
    public String hudName() {
        return getName();
    }

    @Override
    public float hudScale() {
        return scale.getFloat();
    }

    @Override
    public float hudWidth() {
        return panelWidth() * hudScale();
    }

    @Override
    public float hudHeight() {
        return panelHeight() * hudScale();
    }

    private float roomX() {
        return Math.max(0, mc.getWindow().getScaledWidth() - hudWidth() - MARGIN * 2);
    }

    private float roomY() {
        return Math.max(0, mc.getWindow().getScaledHeight() - hudHeight() - MARGIN * 2);
    }

    @Override
    public float hudLeft() {
        return MARGIN + Math.round(roomX() * x.getFloat() / 100f);
    }

    @Override
    public float hudTop() {
        return MARGIN + Math.round(roomY() * y.getFloat() / 100f);
    }

    @Override
    public void hudMove(float left, float top) {
        float rx = roomX();
        float ry = roomY();
        x.set(rx <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (left - MARGIN) / rx * 100.0)));
        y.set(ry <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (top - MARGIN) / ry * 100.0)));
    }

    @Override
    public void hudResize(float by) {
        scale.set(Math.max(scale.getMin(), Math.min(scale.getMax(), Math.round((scale.get() + by) * 100) / 100.0)));
    }

    @Override
    public void hudReset() {
        x.reset();
        y.reset();
        scale.reset();
    }

    // ---- drawing ------------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden) return;
        if (hideInInventory.get() && mc.currentScreen instanceof HandledScreen<?>) return;

        PlayerEntity player = mc.player;
        PlayerInventory inventory = player.getInventory();
        float s = hudScale();
        float left = hudLeft();
        float top = hudTop();
        int w = panelWidth();
        int h = panelHeight();
        float hair = Math.max(Render2D.px() / s, 0.5f);

        List<Slot> layout = new ArrayList<>();
        int cy = PAD;
        int headerY = cy;
        if (header.get()) cy += HEADER + (capacity.get() ? 3 + BAR : 0) + SECTION;

        int equipmentY = cy;
        if (equipment.get()) {
            for (int i = 0; i < 4; i++) {
                layout.add(new Slot(column(i), cy, player.getEquippedStack(ARMOR[i]), ARMOR_SPRITES[i], false));
            }
            layout.add(new Slot(column(COLUMNS - 1), cy, player.getOffHandStack(), OFFHAND_SPRITE, false));
            cy += SLOT + SECTION;
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                layout.add(new Slot(column(col), cy + row * (SLOT + GAP), inventory.getStack(9 + row * COLUMNS + col), null, false));
            }
        }
        cy += 3 * SLOT + 2 * GAP;

        if (hotbar.get()) {
            cy += SECTION;
            int held = inventory.getSelectedSlot();
            for (int col = 0; col < COLUMNS; col++) {
                layout.add(new Slot(column(col), cy, inventory.getStack(col), null, col == held));
            }
        }

        int used = 0;
        for (int i = 0; i < 36; i++) if (!inventory.getStack(i).isEmpty()) used++;

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(left, top);
        matrices.scale(s, s);

        // ---- the panel
        float strength = opacity.getFloat() / 100f;
        Render2D.shadow(ctx, 0, 1, w, h, RADIUS, 9, ColorUtil.withAlpha(0xFF000000, Math.round(120 * Math.max(0.3f, strength))));
        Render2D.roundRect(ctx, 0, 0, w, h, RADIUS, ColorUtil.withAlpha(background.get(), Math.round(255 * strength)));
        Render2D.roundOutline(ctx, 0, 0, w, h, RADIUS, hair, 0x1CFFFFFF);
        crown(ctx, w);

        // ---- the header
        if (header.get()) {
            float mid = headerY + HEADER / 2f;
            Fonts.drawV(ctx, "Inventory", PAD, mid, ColorUtil.withAlpha(Theme.TEXT, 150), true, 0.8f);
            String total = "/36";
            float totalWidth = Fonts.width(total, true, 0.8f);
            Fonts.drawRight(ctx, total, w - PAD, mid, ColorUtil.withAlpha(Theme.TEXT, 110), true, 0.8f);
            Fonts.drawRight(ctx, Integer.toString(used), w - PAD - totalWidth, mid, Theme.TEXT, true, 0.8f);

            if (capacity.get()) {
                float barY = headerY + HEADER + 3;
                float barWidth = w - PAD * 2;
                float full = used / 36f;
                Render2D.roundRect(ctx, PAD, barY, barWidth, BAR, BAR / 2f, 0x14FFFFFF);
                if (full > 0) {
                    float fill = Math.max(BAR, barWidth * full);
                    if (full >= 1) Render2D.roundRect(ctx, PAD, barY, fill, BAR, BAR / 2f, Theme.RED);
                    else if (full >= 0.8f) Render2D.roundRect(ctx, PAD, barY, fill, BAR, BAR / 2f, Theme.YELLOW);
                    else Render2D.roundGradientH(ctx, PAD, barY, fill, BAR, BAR / 2f, Theme.accent(), Theme.accent2());
                }
            }
        }

        // ---- armour value between the armour and the offhand
        if (equipment.get()) {
            float from = column(4);
            float to = column(COLUMNS - 1) - GAP;
            float mid = equipmentY + SLOT / 2f;
            String label = "Armor ";
            String value = Integer.toString(player.getArmor());
            float labelWidth = Fonts.width(label, true, 0.75f);
            float start = (from + to) / 2f - (labelWidth + Fonts.width(value, true, 0.75f)) / 2f;
            Fonts.drawV(ctx, label, start, mid, ColorUtil.withAlpha(Theme.TEXT, 120), true, 0.75f);
            Fonts.drawV(ctx, value, start + labelWidth, mid, Theme.TEXT, true, 0.75f);
        }

        // ---- slots: backgrounds first, then the items, then their counts and bars on top
        for (Slot slot : layout) slotBackground(ctx, slot, hair);

        for (Slot slot : layout) {
            if (slot.stack().isEmpty() && slot.empty() != null) {
                ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, slot.empty(), slot.x() + 1, slot.y() + 1, 16, 16, 0x50FFFFFF);
            }
        }

        for (Slot slot : layout) if (!slot.stack().isEmpty()) ctx.drawItem(slot.stack(), slot.x() + 1, slot.y() + 1);
        for (Slot slot : layout) if (!slot.stack().isEmpty()) ctx.drawStackOverlay(mc.textRenderer, slot.stack(), slot.x() + 1, slot.y() + 1);

        matrices.popMatrix();

        if (tooltips.get() && mc.currentScreen != null && !(mc.currentScreen instanceof HandledScreen<?>)) tooltip(ctx, layout, left, top, s);
    }

    private static int column(int index) {
        return PAD + index * (SLOT + GAP);
    }

    /** A thin band of the accent colours along the top edge, fading out towards both corners. */
    private static void crown(DrawContext ctx, int w) {
        float from = RADIUS;
        float span = w - RADIUS * 2;
        float third = span / 3f;
        int a = Theme.accent();
        int b = Theme.accent2();
        int clearA = ColorUtil.withAlpha(a, 0);
        int clearB = ColorUtil.withAlpha(b, 0);
        Render2D.rectGradient(ctx, from, 0, third, 1, clearA, a, a, clearA);
        Render2D.rectGradient(ctx, from + third, 0, third, 1, a, b, b, a);
        Render2D.rectGradient(ctx, from + third * 2, 0, third, 1, b, clearB, clearB, b);
    }

    private void slotBackground(DrawContext ctx, Slot slot, float hair) {
        float sx = slot.x();
        float sy = slot.y();
        ItemStack stack = slot.stack();

        if (slot.held()) {
            Render2D.roundRect(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, Theme.accent(48));
            Render2D.roundOutline(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, Math.max(hair, 1), Theme.accent(220));
        } else if (slots.is("Soft")) {
            Render2D.roundRect(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, 0x10FFFFFF);
        } else if (slots.is("Outlined")) {
            Render2D.roundOutline(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, hair, 0x24FFFFFF);
        }

        if (stack.isEmpty()) return;

        if (lowDurability.get() && stack.isDamageable() && stack.getMaxDamage() > 0
                && stack.getMaxDamage() - stack.getDamage() <= stack.getMaxDamage() * 0.15f) {
            Render2D.roundRect(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, ColorUtil.withAlpha(Theme.RED, 44));
            Render2D.roundOutline(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, hair, ColorUtil.withAlpha(Theme.RED, 170));
            return;
        }

        if (rarity.get() && !slot.held()) {
            Rarity kind = stack.getRarity();
            Integer rgb = kind == Rarity.COMMON ? null : kind.getFormatting().getColorValue();
            if (rgb != null) {
                int color = 0xFF000000 | rgb;
                Render2D.roundGradientV(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, ColorUtil.withAlpha(color, 10), ColorUtil.withAlpha(color, 52));
                Render2D.roundOutline(ctx, sx, sy, SLOT, SLOT, SLOT_RADIUS, hair, ColorUtil.withAlpha(color, 110));
            }
        }
    }

    /** The vanilla tooltip for the item under the pointer, while a screen is open to point with. */
    private void tooltip(DrawContext ctx, List<Slot> layout, float left, float top, float s) {
        double mouseX = mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
        double mouseY = mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
        double localX = (mouseX - left) / s;
        double localY = (mouseY - top) / s;

        for (Slot slot : layout) {
            if (slot.stack().isEmpty()) continue;
            if (localX >= slot.x() && localY >= slot.y() && localX < slot.x() + SLOT && localY < slot.y() + SLOT) {
                ctx.drawItemTooltip(mc.textRenderer, slot.stack(), (int) mouseX, (int) mouseY);
                return;
            }
        }
    }
}
