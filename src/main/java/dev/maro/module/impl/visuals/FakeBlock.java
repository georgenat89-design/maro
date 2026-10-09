package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.ItemPickerScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Fake Block: one block or item looks like another, only on your screen. Spoof World redraws that
 * block wherever it is placed; Spoof Items draws the item (in hand, in the inventory, dropped) as
 * the other. Armour, tools and weapons it is drawn as can shine as if enchanted.
 */
public class FakeBlock extends Module {
    private static FakeBlock instance;

    private final TextSetting source = add(new TextSetting("Source", "The block or item to change", "", 64, "None"));
    private final ButtonSetting pickSource = add(new ButtonSetting("Pick Source", "Choose it from every block and item", "Edit",
            () -> mc.setScreen(new ItemPickerScreen(mc.currentScreen, "Source", item -> setSource(item)))));
    private final TextSetting replace = add(new TextSetting("Replace", "What it should look like", "", 64, "None"));
    private final ButtonSetting pickReplace = add(new ButtonSetting("Pick Replace", "Choose it from every block and item", "Edit",
            () -> mc.setScreen(new ItemPickerScreen(mc.currentScreen, "Replace", item -> setReplace(item)))));
    private final ButtonSetting clear = add(new ButtonSetting("Clear", "Forget both", "Clear", () -> {
        setSource(null);
        setReplace(null);
    }));
    private final BooleanSetting spoofItems = add(new BooleanSetting("Spoof Items", "Items of it look like the other", true));
    private final BooleanSetting spoofWorld = add(new BooleanSetting("Spoof World", "Placed blocks of it look like the other", true)
            .onChange(on -> rebuild()));
    private final BooleanSetting enchanted = add(new BooleanSetting("Enchanted", "The look shines as if enchanted (armour, tools, weapons)", false)
            .visible(this::replaceIsGear));

    private final List<SettingSection> sections = List.of(SettingSection.of("Fake Block", source, pickSource, replace, pickReplace, clear,
            spoofItems, spoofWorld, enchanted));

    /** Read by the chunk builder threads. */
    private static volatile Block worldFrom, worldTo;

    public FakeBlock() {
        super("Fake Block", "Make a block or item look like another, only for you", Category.VISUALS);
        instance = this;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        rebuild();
    }

    @Override
    protected void onDisable() {
        rebuild();
    }

    public void setSource(Item item) {
        source.set(item == null ? "" : Registries.ITEM.getId(item).toString());
        rebuild();
    }

    public void setReplace(Item item) {
        replace.set(item == null ? "" : Registries.ITEM.getId(item).toString());
        rebuild();
    }

    private static Item item(String id) {
        Identifier parsed = id == null || id.isBlank() ? null : Identifier.tryParse(id.trim());
        if (parsed == null || !Registries.ITEM.containsId(parsed)) return Items.AIR;
        return Registries.ITEM.get(parsed);
    }

    public Item sourceItem() {
        return item(source.get());
    }

    public Item replaceItem() {
        return item(replace.get());
    }

    private boolean replaceIsGear() {
        Item r = replaceItem();
        return r != Items.AIR && new ItemStack(r).isDamageable();
    }

    /** Works out the blocks again and has the world redrawn, so a change shows straight away. */
    private void rebuild() {
        Item from = sourceItem(), to = replaceItem();
        boolean world = isEnabled() && spoofWorld.get() && from instanceof BlockItem && to instanceof BlockItem;
        Block a = world ? ((BlockItem) from).getBlock() : null, b = world ? ((BlockItem) to).getBlock() : null;
        boolean changed = a != worldFrom || b != worldTo;
        worldFrom = a;
        worldTo = b;
        if (changed && mc.worldRenderer != null && mc.world != null) mc.worldRenderer.reload();
    }

    // ---- read while drawing --------------------------------------------------------------------

    /** The block to draw in place of this one, for the world's chunk meshes. */
    public static BlockState worldLook(BlockState state) {
        Block from = worldFrom, to = worldTo;
        if (from == null || to == null || !state.isOf(from)) return state;
        BlockState out = to.getDefaultState();
        // Keep the facing, half and so on where the two blocks share them.
        for (Property<?> p : state.getProperties()) out = copy(state, out, p);
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.contains(p) ? to.with(p, from.get(p)) : to;
    }

    /** The item to draw in place of this stack, or null to leave it. */
    public static ItemStack itemLook(ItemStack stack) {
        FakeBlock m = instance;
        if (m == null || !m.isEnabled() || !m.spoofItems.get()) return null;
        Item from = m.sourceItem(), to = m.replaceItem();
        if (from == Items.AIR || to == Items.AIR || !stack.isOf(from)) return null;
        ItemStack shown = stack.copy();
        shown.set(DataComponentTypes.ITEM_MODEL, to.getComponents().get(DataComponentTypes.ITEM_MODEL));
        if (m.enabled()) shown.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return shown;
    }

    private boolean enabled() {
        return enchanted.get() && replaceIsGear();
    }

    /** Whether the world is drawn with a block swapped right now; for tests. */
    public static boolean spoofingWorld() {
        return worldFrom != null && worldTo != null && worldFrom != Blocks.AIR;
    }
}
