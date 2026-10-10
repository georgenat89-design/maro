package dev.maro.module.impl.misc;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.KeybindSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.KeyUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Inventory Hider: your loot never shows. Your inventory's items (and your armor, off hand, crafting
 * grid and the figure of you wearing it all) are not drawn, nor are your own items at the bottom of
 * chests and other menus; no tooltip or item on the cursor gives them away either. The slots can look
 * empty, be covered, or the whole menu can vanish while it stays open. The hotbar and what you hold
 * are hidden too, and chest contents and the Inventory HUD can be. Holding the peek key shows
 * everything for a moment.
 *
 * <p>Only drawing changes: the items are still there and every click works as usual.
 */
public class InventoryHider extends Module {
    private static InventoryHider instance;

    private final ModeSetting style = add(new ModeSetting("Style",
            "Empty Slots: they look empty. Blacked Out: each one is covered. Invisible Menu: the whole menu disappears while it is open",
            "Empty Slots", "Empty Slots", "Blacked Out", "Invisible Menu"));
    private final ColorSetting cover = add(new ColorSetting("Cover Color", "The squares over hidden slots", 0xFF15161C, true)
            .visible(() -> style.is("Blacked Out")));
    private final BooleanSetting inventory = add(new BooleanSetting("Your Inventory", "Your inventory menu (E): items, armor, off hand and crafting grid", true));
    private final BooleanSetting playerModel = add(new BooleanSetting("Player Model", "The figure of you in your inventory, which shows your armor and what you hold", true));
    private final BooleanSetting yourSlots = add(new BooleanSetting("Your Slots In Chests", "Your own items at the bottom of chests, shulkers and other menus", true));
    private final BooleanSetting contents = add(new BooleanSetting("Chest Contents", "What is in chests, shulkers, ender chests and other menus too", false));
    private final BooleanSetting hotbar = add(new BooleanSetting("Hotbar", "Your hotbar and off hand on screen, and the item name shown when you switch", true));
    private final BooleanSetting heldItem = add(new BooleanSetting("Held Item", "What you hold, in first person: your empty hand shows instead", true));
    private final BooleanSetting inventoryHud = add(new BooleanSetting("Inventory HUD", "The Inventory HUD module's panel", true));
    private final KeybindSetting peek = add(new KeybindSetting("Peek Key", "Hold it to see your items for a moment", GLFW.GLFW_KEY_LEFT_ALT));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Look", style, cover, peek),
            SettingSection.of("Hide", inventory, playerModel, yourSlots, contents, hotbar, heldItem, inventoryHud));

    public InventoryHider() {
        super("Inventory Hider", "Hides your items so no loot shows: in your inventory, in chests, and on the hotbar if you like", Category.MISC);
        instance = this;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    /** Saved before the hotbar was hidden by default: it is hidden from now on, once. */
    private static final int HIDER_REVISION = 1;

    @Override
    public com.google.gson.JsonObject saveExtra() {
        var data = super.saveExtra();
        data.addProperty("hider-revision", HIDER_REVISION);
        return data;
    }

    @Override
    public void loadExtra(com.google.gson.JsonObject data) {
        super.loadExtra(data);
        if (!data.has("hider-revision")) hotbar.set(true);
    }

    private static InventoryHider on() {
        InventoryHider m = instance;
        return m != null && m.isEnabled() && !m.peeking() ? m : null;
    }

    /** Whether the peek key is held down right now. */
    public boolean peeking() {
        int code = peek.get();
        if (code == KeyUtil.NONE || mc.getWindow() == null) return false;
        if (KeyUtil.isMouse(code)) return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        return net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), code);
    }

    private static boolean ownMenu(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen;
    }

    /** Whether a slot's item is hidden in this menu. */
    public static boolean hides(Screen screen, Slot slot) {
        InventoryHider m = on();
        if (m == null || slot == null) return false;
        boolean own = slot.inventory instanceof PlayerInventory;
        // The creative tabs are the game's items, not yours; your inventory tab is your own slots.
        if (screen instanceof CreativeInventoryScreen) return own && m.inventory.get();
        if (screen instanceof InventoryScreen) return m.inventory.get();
        return own ? m.yourSlots.get() : m.contents.get();
    }

    /** Whether anything in this menu is hidden: the item on the cursor is hidden too. */
    public static boolean hidesAny(Screen screen) {
        InventoryHider m = on();
        if (m == null || !(screen instanceof HandledScreen<?>)) return false;
        if (ownMenu(screen)) return m.inventory.get();
        return m.yourSlots.get() || m.contents.get();
    }

    /** Whether the whole menu is left undrawn: Invisible Menu, where everything in it would be hidden. */
    public static boolean hidesMenu(Screen screen) {
        InventoryHider m = on();
        if (m == null || !m.style.is("Invisible Menu") || !(screen instanceof HandledScreen<?>)) return false;
        if (ownMenu(screen)) return m.inventory.get();
        return m.yourSlots.get() && m.contents.get();
    }

    /** Draws what stands in for a hidden slot's item: nothing, or a cover. */
    public static void cover(DrawContext ctx, Slot slot) {
        InventoryHider m = instance;
        if (m != null && m.style.is("Blacked Out")) ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, m.cover.get());
    }

    /** Whether the figure of you in the inventory is left out. */
    public static boolean hidesPlayerModel() {
        InventoryHider m = on();
        return m != null && m.playerModel.get() && m.inventory.get() && ownMenu(mc.currentScreen);
    }

    public static boolean hidesHotbar() {
        InventoryHider m = on();
        return m != null && m.hotbar.get();
    }

    /** Whether the item in your hand is left out in first person. */
    public static boolean hidesHeldItem() {
        InventoryHider m = on();
        return m != null && m.heldItem.get();
    }

    public static boolean hidesInventoryHud() {
        InventoryHider m = on();
        return m != null && m.inventoryHud.get();
    }
}
