package dev.maro.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;

/** Registry-backed, immutable selection edited by the item picker. */
public final class ItemSelectionSetting extends Setting<Set<Item>> {
    public ItemSelectionSetting(String name, Item... defaults) {
        super(name, "", Set.of(defaults));
        setVisibility(() -> false);
    }

    public boolean contains(Item item) { return value.contains(item); }

    public void toggle(Item item) {
        var next = new LinkedHashSet<>(value);
        if (!next.remove(item)) next.add(item);
        set(next);
    }

    /** One-click preset; registry enumeration happens on selection, never during label rendering. */
    public void selectAllBlocks() {
        set(Registries.ITEM.stream().filter(item -> item != Items.AIR && item instanceof BlockItem)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Override protected Set<Item> validate(Set<Item> items) {
        if (items == null) return null;
        var next = new LinkedHashSet<Item>();
        for (Item item : items) if (item != null && item != Items.AIR && Registries.ITEM.containsId(Registries.ITEM.getId(item))) next.add(item);
        return Set.copyOf(next);
    }

    @Override public JsonElement toJson() {
        var array = new JsonArray();
        value.stream().map(Registries.ITEM::getId).map(Identifier::toString).sorted().forEach(array::add);
        return array;
    }

    @Override public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonArray()) return;
        var next = new LinkedHashSet<Item>();
        for (JsonElement entry : json.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) continue;
            Identifier id = Identifier.tryParse(entry.getAsString());
            if (id != null && Registries.ITEM.containsId(id)) next.add(Registries.ITEM.get(id));
        }
        set(next);
    }
}
