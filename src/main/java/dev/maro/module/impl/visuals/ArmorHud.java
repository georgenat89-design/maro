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
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Your armour on the HUD, clean: each piece in a small cell with a thin durability bar under it and
 * the percent left in its own colour, green to red. A piece about to break glows red. Your off hand
 * and what you hold can sit beside it. Placed by dragging, at any scale, in a row or a column.
 */
public class ArmorHud extends Module implements HudElement {
    private static final int CELL = 20, GAP = 3, PAD = 4, MARGIN = 4;
    private static final float RADIUS = 6, CELL_RADIUS = 4.5f;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier[] EMPTY = {
            Identifier.ofVanilla("container/slot/helmet"),
            Identifier.ofVanilla("container/slot/chestplate"),
            Identifier.ofVanilla("container/slot/leggings"),
            Identifier.ofVanilla("container/slot/boots")};
    private static final Identifier EMPTY_OFFHAND = Identifier.ofVanilla("container/slot/shield");

    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag it where you want it and scroll to resize it (it starts beside the hotbar)", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 50, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 86, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big it is", 1, 0.5, 2.5, 0.05).suffix("x"));
    /** Dragged somewhere by you; until then it sits beside the hotbar. */
    private final BooleanSetting placed = add(new BooleanSetting("Placed", "", false).visible(() -> false));

    private final ModeSetting layout = add(new ModeSetting("Layout", "A row or a column", "Row", "Row", "Column"));
    private final ModeSetting durability = add(new ModeSetting("Durability", "A thin bar, the percent left, both, or nothing",
            "Bar & Percent", "Bar & Percent", "Bar", "Percent", "Off"));
    private final BooleanSetting offHand = add(new BooleanSetting("Off Hand", "Your off hand (a totem or shield) after the armour", true));
    private final BooleanSetting mainHand = add(new BooleanSetting("Main Hand", "What you hold, before the armour", false));
    private final BooleanSetting showEmpty = add(new BooleanSetting("Show Empty", "Faint outlines where nothing is worn", true));
    private final BooleanSetting warn = add(new BooleanSetting("Low Warning", "A piece about to break glows red", true));
    private final NumberSetting warnAt = add(new NumberSetting("Warn Below", "How low counts as about to break", 15, 1, 50, 1).suffix("%").visible(warn::get));
    private final BooleanSetting panel = add(new BooleanSetting("Panel", "A dark card behind it all", true));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the card and cells are", 85, 0, 100, 1).suffix("%"));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Look", layout, durability, offHand, mainHand, showEmpty, warn, warnAt, panel, opacity),
            SettingSection.of("Placement", position, x, y, scale));

    /** One cell: what is in it and what to show when it is empty. */
    private record Piece(ItemStack stack, Identifier empty) {
    }

    public ArmorHud() {
        super("Armor HUD", "Your armour on screen, clean: thin durability bars and percents, red when about to break", Category.VISUALS);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    private List<Piece> pieces() {
        List<Piece> out = new ArrayList<>();
        PlayerEntity p = mc.player;
        if (mainHand.get()) out.add(new Piece(p.getMainHandStack(), null));
        for (int i = 0; i < ARMOR.length; i++) out.add(new Piece(p.getEquippedStack(ARMOR[i]), EMPTY[i]));
        if (offHand.get()) out.add(new Piece(p.getOffHandStack(), EMPTY_OFFHAND));
        if (!showEmpty.get()) out.removeIf(piece -> piece.stack().isEmpty());
        return out;
    }

    private boolean bar() {
        return durability.is("Bar") || durability.is("Bar & Percent");
    }

    private boolean percent() {
        return durability.is("Percent") || durability.is("Bar & Percent");
    }

    /** One cell's height: the item, then the bar, then the percent. */
    private int cellHeight() {
        return CELL + (percent() ? 8 : bar() ? 2 : 0);
    }

    private int count() {
        return inGame() ? Math.max(1, pieces().size()) : 5;
    }

    private boolean row() {
        return layout.is("Row");
    }

    private int panelWidth() {
        int n = count();
        return PAD * 2 + (row() ? n * CELL + (n - 1) * GAP : CELL);
    }

    private int panelHeight() {
        int n = count();
        return PAD * 2 + (row() ? cellHeight() : n * cellHeight() + (n - 1) * GAP);
    }

    // ---- placement ----------------------------------------------------------------------

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

    /** Beside the right end of the hotbar, bottoms level; above the health bars when there is no room there. */
    private boolean besideHotbar() {
        return mc.getWindow().getScaledWidth() / 2f + 97 + hudWidth() <= mc.getWindow().getScaledWidth() - MARGIN;
    }

    @Override
    public float hudLeft() {
        if (!placed.get()) {
            int sw = mc.getWindow().getScaledWidth();
            return besideHotbar() ? sw / 2f + 97 : Math.round((sw - hudWidth()) / 2f);
        }
        return MARGIN + Math.round(roomX() * x.getFloat() / 100f);
    }

    @Override
    public float hudTop() {
        if (!placed.get()) {
            int sh = mc.getWindow().getScaledHeight();
            return besideHotbar() ? sh - hudHeight() - 1 : sh - 50 - hudHeight();
        }
        return MARGIN + Math.round(roomY() * y.getFloat() / 100f);
    }

    @Override
    public void hudMove(float left, float top) {
        placed.set(true);
        float rx = roomX(), ry = roomY();
        x.set(rx <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (left - MARGIN) / rx * 100.0)));
        y.set(ry <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (top - MARGIN) / ry * 100.0)));
    }

    @Override
    public void hudResize(float by) {
        scale.set(Math.max(scale.getMin(), Math.min(scale.getMax(), Math.round((scale.get() + by) * 100) / 100.0)));
    }

    @Override
    public void hudReset() {
        placed.reset();
        x.reset();
        y.reset();
        scale.reset();
    }

    // ---- drawing ------------------------------------------------------------------------

    /** How much is left, 0 to 1; -1 for things that do not wear out. */
    public static float left(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) return -1;
        return 1 - stack.getDamage() / (float) stack.getMaxDamage();
    }

    /** Green when whole, through yellow, to red when nearly gone. */
    public static int wearColor(float left) {
        int green = 0xFF3DDC97, yellow = 0xFFFFC23D, red = 0xFFFF5D6C;
        return left > 0.5f ? ColorUtil.lerp(yellow, green, (left - 0.5f) * 2) : ColorUtil.lerp(red, yellow, left * 2);
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden || dev.maro.module.impl.misc.InventoryHider.hidesHotbar()) return;
        List<Piece> pieces = pieces();
        if (pieces.isEmpty()) return;
        float s = hudScale();
        int w = panelWidth(), h = panelHeight();
        float hair = Math.max(Render2D.px() / s, 0.5f);
        float strength = opacity.getFloat() / 100f;

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(hudLeft(), hudTop());
        matrices.scale(s, s);
        Fonts.beginRaw();
        try {
            if (panel.get()) {
                Render2D.shadow(ctx, 0, 1, w, h, RADIUS, 8, ColorUtil.withAlpha(0xFF000000, Math.round(110 * Math.max(0.3f, strength))));
                Render2D.roundRect(ctx, 0, 0, w, h, RADIUS, ColorUtil.withAlpha(0xFF0B0D12, Math.round(235 * strength)));
                Render2D.roundOutline(ctx, 0, 0, w, h, RADIUS, hair, 0x1CFFFFFF);
            }
            long now = System.currentTimeMillis();
            for (int i = 0; i < pieces.size(); i++) {
                Piece piece = pieces.get(i);
                float cx = PAD + (row() ? i * (CELL + GAP) : 0);
                float cy = PAD + (row() ? 0 : i * (cellHeight() + GAP));
                cell(ctx, piece, cx, cy, hair, strength, now);
            }
        } finally {
            Fonts.endRaw();
            matrices.popMatrix();
        }
    }

    private void cell(DrawContext ctx, Piece piece, float cx, float cy, float hair, float strength, long now) {
        ItemStack stack = piece.stack();
        float wear = left(stack);
        boolean low = warn.get() && wear >= 0 && wear * 100 < warnAt.getFloat();

        // The cell: a soft square, or a red one pulsing gently when the piece is about to break.
        if (low) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(now / 220.0);
            Render2D.shadow(ctx, cx, cy, CELL, CELL, CELL_RADIUS, 5, ColorUtil.withAlpha(Theme.RED, Math.round(90 * pulse)));
            Render2D.roundRect(ctx, cx, cy, CELL, CELL, CELL_RADIUS, ColorUtil.withAlpha(Theme.RED, Math.round(50 * pulse)));
            Render2D.roundOutline(ctx, cx, cy, CELL, CELL, CELL_RADIUS, hair, ColorUtil.withAlpha(Theme.RED, 190));
        } else {
            Render2D.roundRect(ctx, cx, cy, CELL, CELL, CELL_RADIUS, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(16 * Math.max(0.4f, strength))));
            Render2D.roundOutline(ctx, cx, cy, CELL, CELL, CELL_RADIUS, hair, 0x14FFFFFF);
        }

        if (stack.isEmpty()) {
            if (piece.empty() != null) ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, piece.empty(), Math.round(cx + 2), Math.round(cy + 2), 16, 16, 0x40FFFFFF);
            return;
        }
        ctx.drawItem(stack, Math.round(cx + 2), Math.round(cy + 2));
        // Stack counts (totems, rockets), never the game's own durability bar: ours is cleaner.
        if (stack.getCount() > 1) {
            String n = Integer.toString(stack.getCount());
            Fonts.drawRight(ctx, n, cx + CELL - 1.5f, cy + CELL - 4, 0xFFF4F6FA, true, 0.55f);
        }
        if (wear < 0) return;
        int color = wearColor(wear);
        float by = cy + CELL + 1.5f;
        if (bar()) {
            float bw = CELL - 6;
            Render2D.roundRect(ctx, cx + 3, by, bw, 1.6f, 0.8f, 0x30FFFFFF);
            Render2D.roundRect(ctx, cx + 3, by, Math.max(1.6f, bw * wear), 1.6f, 0.8f, color);
        }
        if (percent()) {
            String text = Math.round(wear * 100) + "%";
            Fonts.drawCentered(ctx, text, cx + CELL / 2f, by + (bar() ? 5.5f : 3.5f), low ? Theme.RED : ColorUtil.lerp(color, 0xFFF4F6FA, 0.35f), true, 0.5f);
        }
    }
}
