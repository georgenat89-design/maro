package dev.maro.render.accessories;

import dev.maro.module.impl.visuals.FakeBlock;
import dev.maro.module.impl.visuals.SkinAccessories;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Identifier;

import java.util.Objects;

/** What an item is drawn as: itself, a Cosmetics skin, or Fake Block's replacement. */
public final class ItemLooks {
    private ItemLooks() {
    }

    /** The stack to draw in place of this one (the same stack when nothing changes it). */
    public static ItemStack shown(ItemStack stack, ItemDisplayContext context, HeldItemContext holder) {
        // A stack given its own model already (a skin's icon in a menu) is left as it is.
        Identifier own = stack.get(DataComponentTypes.ITEM_MODEL);
        if (own != null && !Objects.equals(own, stack.getItem().getComponents().get(DataComponentTypes.ITEM_MODEL))) return stack;

        ItemStack fake = FakeBlock.itemLook(stack);
        if (fake != null) return fake;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean yours = holder == mc.player || context == ItemDisplayContext.GUI;
        if (!yours) return stack;
        CosmeticItems.Skin skin = SkinAccessories.skinFor(stack);
        if (skin == null) return stack;
        ItemStack skinned = stack.copy();
        skinned.set(DataComponentTypes.ITEM_MODEL, skin.model());
        return skinned;
    }
}
