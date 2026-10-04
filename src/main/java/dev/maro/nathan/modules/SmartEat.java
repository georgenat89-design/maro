package dev.maro.nathan.modules;

import java.util.ArrayList;
import java.util.List;

import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.events.world.TickEvent;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.ItemListSetting;
import dev.maro.runtime.settings.ItemSetting;
import dev.maro.runtime.settings.KeybindSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.misc.Keybind;
import dev.maro.runtime.utils.player.FindItemResult;
import dev.maro.runtime.utils.player.InvUtils;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.ApplyEffectsConsumeEffect;
import net.minecraft.item.consume.ConsumeEffect;
import net.minecraft.registry.Registries;
import dev.maro.nathan.NameeProtectAddon;

/**
 * Eats when you get hungry, the way you would.
 *
 * <p><b>Nothing here changes how eating works.</b> It picks a hotbar slot and
 * holds the use key down, which is exactly what a hand does; the food takes the
 * time the food takes. There is no packet, no timer override and no way for this
 * to feed you faster than the game allows.
 *
 * <p><b>It watches rather than counts.</b> Eating stops when the hunger bar says
 * it should, when the stack in hand stops being food, or when the game stops
 * reporting an item in use - not after a fixed number of ticks. A meal that gets
 * interrupted by damage is noticed on the next tick and started again.
 *
 * <p><b>The use key is never left down.</b> Every path that stops eating goes
 * through one method that releases it, including the module being switched off,
 * dying, opening a screen and leaving the world.
 *
 * <p>Meteor already has a module called {@code auto-eat}; this is registered as
 * {@code smart-eat} so that registering it cannot unregister Meteor's, which is
 * what a duplicate name does.
 */
public class SmartEat extends Module {
    /** Full hunger, in food points. */
    private static final int FULL = 20;

    /** How long to wait before saying "no food" again, in ticks. */
    private static final int NOTIFY_EVERY = 200;

    public enum Priority {
        BestNutrition,
        LowestWaste,
        PreferredItem
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWhen = settings.createGroup("When To Eat");
    private final SettingGroup sgFood = settings.createGroup("Food Choice");
    private final SettingGroup sgSlots = settings.createGroup("Hotbar");

    private final Setting<Integer> threshold = sgWhen.add(new IntSetting.Builder()
        .name("hunger-threshold")
        .description("Start eating when hunger drops below this. 20 is a full bar.")
        .defaultValue(14)
        .min(0)
        .max(FULL)
        .sliderRange(0, FULL)
        .build()
    );

    private final Setting<Priority> priority = sgFood.add(new EnumSetting.Builder<Priority>()
        .name("food-priority")
        .description("Best Nutrition: the most filling. Lowest Waste: the one that overfills you least. Preferred Item: the food below, falling back to the most filling.")
        .defaultValue(Priority.LowestWaste)
        .build()
    );

    /**
     * A default is not optional.
     *
     * <p>{@code ItemSetting.Builder} starts at {@code null} and Meteor draws an
     * item setting by calling {@code get().getDefaultInstance()}, so one without a
     * default takes the game down the moment its settings are opened.
     */
    private final Setting<Item> preferred = sgFood.add(new ItemSetting.Builder()
        .name("preferred-item")
        .description("The food to reach for first, when Food Priority is Preferred Item.")
        .defaultValue(Items.COOKED_BEEF)
        .visible(() -> priority.get() == Priority.PreferredItem)
        .build()
    );

    private final Setting<List<Item>> allowed = sgFood.add(new ItemListSetting.Builder()
        .name("allowed-foods")
        .description("What it is allowed to eat. Starts as every food that has no harmful effect, minus both golden apples.")
        .defaultValue(safeFoods())
        .filter(SmartEat::isFood)
        .build()
    );

    private final Setting<Boolean> hotbarOnly = sgSlots.add(new BoolSetting.Builder()
        .name("hotbar-only")
        .description("Only eat what is already on the hotbar. Off, and food is moved up from the inventory when the hotbar has none.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> restoreSlot = sgSlots.add(new BoolSetting.Builder()
        .name("restore-previous-slot")
        .description("Go back to the slot you were holding once the meal is over.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseWhileAttacking = sgWhen.add(new BoolSetting.Builder()
        .name("pause-while-attacking")
        .description("Do not start a meal while you are holding attack.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> keybind = sgWhen.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("Turns the module on and off.")
        .defaultValue(Keybind.none())
        .build()
    );

    /** True while this module is the one holding use down. */
    private boolean eating;

    /** The slot we were holding before the meal, and the one we switched to. */
    private int previousSlot = -1;
    private int eatingSlot = -1;

    private int sinceNotified = NOTIFY_EVERY;
    private boolean keyWasDown;

    public SmartEat() {
        super(NameeProtectAddon.CATEGORY, "smart-eat",
            "Eats when your hunger drops below a threshold, by holding use like you would.");
    }

    // ------------------------------------------------------------- food rules

    private static boolean isFood(ItemStack stack) {
        return stack.get(DataComponentTypes.FOOD) != null;
    }

    private static boolean isFood(Item item) {
        return isFood(item.getDefaultStack());
    }

    /**
     * Whether eating this would apply something you do not want.
     *
     * <p>Read from the item rather than from a list of names: the consumable
     * component carries the effects it applies, and anything in the harmful
     * category - poison, hunger, nausea - disqualifies it. That covers rotten
     * flesh, spider eyes, pufferfish and raw chicken without naming any of them,
     * and covers a modded food nobody thought of.
     */
    private static boolean harmful(ItemStack stack) {
        ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);

        if (consumable == null) return false;

        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (!(effect instanceof ApplyEffectsConsumeEffect apply)) continue;

            for (StatusEffectInstance instance : apply.effects()) {
                if (instance.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) return true;
            }
        }

        return false;
    }

    /** Every safe food in the game, which is what the allowed list starts as. */
    private static Item[] safeFoods() {
        List<Item> out = new ArrayList<>();

        for (Item item : Registries.ITEM) {
            ItemStack stack = item.getDefaultStack();

            if (!isFood(stack) || harmful(stack)) continue;

            // Kept out by default because they are worth more than a meal.
            if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) continue;

            out.add(item);
        }

        return out.toArray(new Item[0]);
    }

    private boolean isAllowed(ItemStack stack) {
        return isFood(stack) && allowed.get().contains(stack.getItem());
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void onDeactivate() {
        stopEating();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        stopEating();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean down = mc.currentScreen == null && keybind.get().isPressed();

        if (down && !keyWasDown) toggle();

        keyWasDown = down;

        if (!isActive()) return;

        if (sinceNotified < NOTIFY_EVERY) sinceNotified++;

        // Every reason to not be eating, in one place, so the use key cannot be
        // left down by a path that forgot about it.
        if (mc.player == null || mc.world == null || mc.currentScreen != null
            || mc.player.isDead() || mc.player.getHealth() <= 0) {
            stopEating();
            return;
        }

        int hunger = mc.player.getHungerManager().getFoodLevel();

        if (eating) {
            keepEating(hunger);
            return;
        }

        if (hunger >= threshold.get()) return;

        // Somebody else's item use - a bow being drawn, a shield up, a potion -
        // is left alone. Starting a meal here is what yanks a bow out of a draw.
        if (mc.player.isUsingItem()) return;

        if (pauseWhileAttacking.get() && mc.options.attackKey.isPressed()) return;

        start(hunger);
    }

    // ---------------------------------------------------------------- eating

    private void start(int hunger) {
        FindItemResult food = hotbarOnly.get()
            ? InvUtils.findInHotbar(this::isAllowed)
            : InvUtils.find(this::isAllowed);

        if (!food.found()) {
            if (sinceNotified >= NOTIFY_EVERY) {
                warning("Nothing to eat.");
                sinceNotified = 0;
            }

            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        int slot = bestSlot(hunger);

        if (slot < 0) {
            // findInHotbar found something the chooser did not, which means the
            // food is off the hotbar and we are allowed to fetch it.
            if (hotbarOnly.get() || !food.found()) return;

            InvUtils.move().from(food.slot()).toHotbar(inventory.getSelectedSlot());

            return; // next tick it will be on the hotbar and chosen normally
        }

        previousSlot = inventory.getSelectedSlot();
        eatingSlot = slot;

        inventory.setSelectedSlot(slot);

        eating = true;
        mc.options.useKey.setPressed(true);
    }

    /**
     * Whether to carry on, checked against the world rather than a countdown.
     *
     * <p>The meal ends when the bar is full enough, when what is in hand is no
     * longer food we are allowed to eat - the stack ran out - or when you take the
     * slot off it yourself.
     */
    private void keepEating(int hunger) {
        PlayerInventory inventory = mc.player.getInventory();

        if (hunger >= FULL) {
            stopEating();
            return;
        }

        // You moved the slot yourself. Yours wins; let go and do not fight it.
        if (inventory.getSelectedSlot() != eatingSlot) {
            eating = false;
            eatingSlot = -1;
            previousSlot = -1;
            mc.options.useKey.setPressed(false);

            return;
        }

        if (!isAllowed(mc.player.getMainHandStack())) {
            stopEating();
            return;
        }

        mc.options.useKey.setPressed(true);
    }

    /**
     * The one way out.
     *
     * <p>Releases the key first, so that whatever else goes wrong below, the key
     * is not still held.
     */
    private void stopEating() {
        if (mc.options != null) mc.options.useKey.setPressed(false);

        if (!eating) return;

        eating = false;

        if (restoreSlot.get() && previousSlot >= 0 && mc.player != null) {
            PlayerInventory inventory = mc.player.getInventory();

            // Only if the slot is still the one we set. If it is not, you changed
            // it during the meal and putting it back would fight you.
            if (inventory.getSelectedSlot() == eatingSlot) inventory.setSelectedSlot(previousSlot);
        }

        previousSlot = -1;
        eatingSlot = -1;
    }

    // -------------------------------------------------------------- choosing

    /** The hotbar slot to eat from, or -1 if the hotbar has nothing allowed. */
    private int bestSlot(int hunger) {
        PlayerInventory inventory = mc.player.getInventory();
        int wanted = FULL - hunger;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);

            if (!isAllowed(stack)) continue;

            FoodComponent food = stack.get(DataComponentTypes.FOOD);

            if (food == null) continue;

            int score = switch (priority.get()) {
                case BestNutrition -> food.nutrition();
                // Least thrown away: the overshoot past a full bar, negated so
                // that bigger is still better, and nutrition as the tie-break.
                case LowestWaste -> -Math.max(0, food.nutrition() - wanted) * 100 + food.nutrition();
                case PreferredItem -> stack.getItem() == preferred.get() ? 1000 : food.nutrition();
            };

            if (score > bestScore) {
                bestScore = score;
                best = slot;
            }
        }

        return best;
    }

    @Override
    public String getInfoString() {
        return eating ? "eating" : null;
    }
}
