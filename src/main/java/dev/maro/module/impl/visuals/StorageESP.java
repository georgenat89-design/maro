package dev.maro.module.impl.visuals;

import net.minecraft.block.Block;

import java.util.List;
import java.util.Map;

/**
 * Storage ESP: Block ESP for storage alone (chests, trapped chests, barrels, shulker boxes, ender
 * chests, and hoppers, dispensers, furnaces and the rest when picked), with every setting Block ESP
 * has: the picker and colours, Y limit, boxes, tracers and bloom. Block ESP leaves storage to it.
 */
public class StorageESP extends BlockESP {
    public StorageESP() {
        super("Storage ESP", "Boxes round chests, barrels, shulkers and other storage through walls, with glowing tracers and bloom");
    }

    @Override
    public boolean allows(Block block) {
        return isStorage(block);
    }

    @Override
    public List<Map.Entry<String, List<Block>>> presetList() {
        return storagePresets();
    }
}
