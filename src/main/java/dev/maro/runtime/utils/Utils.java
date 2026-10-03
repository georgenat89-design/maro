package dev.maro.runtime.utils;

import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
public final class Utils { public static boolean hasEnchantment(ItemStack stack,RegistryKey<Enchantment> enchantment){return stack.getEnchantments().getEnchantmentEntries().stream().anyMatch(e->e.getKey().matchesKey(enchantment)&&e.getIntValue()>0);} }
