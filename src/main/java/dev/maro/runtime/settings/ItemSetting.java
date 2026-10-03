package dev.maro.runtime.settings;
public final class ItemSetting { public static class Builder extends Setting.Builder<net.minecraft.item.Item, Builder> { public Builder filter(java.util.function.Predicate<net.minecraft.item.Item> filter){validator=filter;return this;} } }
