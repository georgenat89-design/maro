package dev.maro.runtime.settings;

    public final class ItemListSetting { public static class Builder extends Setting.Builder<java.util.List<net.minecraft.item.Item>,Builder> {
     @SafeVarargs public final Builder defaultValue(net.minecraft.item.Item... values) { defaultValue=java.util.List.of(values);return this; }
     public Builder filter(java.util.function.Predicate<net.minecraft.item.Item> filter) { validator=items->items.stream().allMatch(filter);return this; }
    } }
