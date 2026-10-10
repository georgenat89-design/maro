package dev.maro.gametest;

import dev.maro.config.FriendManager;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.module.impl.visuals.Nametags;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.RegistryKeys;

/**
 * Nametags on Fake Player: the tag is drawn (and the game's own label left off), it shows health and
 * distance, the armour's enchantments read short ("Prot4"), and a popped totem shows as -1.
 */
final class NametagsChecks {
    private NametagsChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(dev.maro.module.Module module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context) {
        Nametags tags = ModuleManager.get(Nametags.class);
        FakePlayer fakes = ModuleManager.get(FakePlayer.class);
        require(tags != null, "Nametags was not registered");
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        try {
            context.runOnClient(c -> {
                fakes.getSettings().forEach(Setting::reset);
                ((ModeSetting) setting(fakes, "Armor")).set("Netherite");
                ((NumberSetting) setting(fakes, "Health")).set(1.0);
                fakes.setEnabled(true);
                tags.getSettings().forEach(Setting::reset);
                ((ModeSetting) setting(tags, "Armor")).set("Above");
                ((BooleanSetting) setting(tags, "Held Item")).set(true);
                ((BooleanSetting) setting(tags, "Enchants")).set(true);
                ((BooleanSetting) setting(tags, "Ping")).set(true);
                ((BooleanSetting) setting(tags, "Gamemode")).set(true);
                ((BooleanSetting) setting(tags, "Outline")).set(true);
                tags.setEnabled(true);
                c.options.setPerspective(Perspective.FIRST_PERSON);
            });
            context.waitTicks(5);
            var fake = context.computeOnClient(c -> fakes.entity());
            require(fake != null, "No fake player to tag");
            // Step back and face it, and give its helmet Protection IV.
            context.runOnClient(c -> {
                c.player.refreshPositionAndAngles(fake.getX(), fake.getY(), fake.getZ() - 4, 0, 5);
                var protection = c.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
                fake.getEquippedStack(EquipmentSlot.HEAD).addEnchantment(protection, 4);
            });
            context.waitTicks(5);
            context.runOnClient(c -> {
                require(Nametags.hidesVanilla(fake), "The game's own label is still drawn over a tagged player");
                String said = tags.describe(fake);
                require(said.contains("FakePlayer") && said.contains("m"), "The tag does not name it with its distance: " + said);
                require(tags.drawnTags().stream().anyMatch(t -> t.contains("FakePlayer")), "No tag was drawn: " + tags.drawnTags());
                require(Nametags.enchantLines(fake.getEquippedStack(EquipmentSlot.HEAD)).contains("Prot4"),
                        "Protection IV does not read Prot4: " + Nametags.enchantLines(fake.getEquippedStack(EquipmentSlot.HEAD)));
            });
            context.takeScreenshot("maro-nametags");

            // A popped totem counts.
            for (int i = 0; i < 20 && context.computeOnClient(c -> tags.popsOf(fake)) == 0; i++) {
                context.runOnClient(c -> c.interactionManager.attackEntity(c.player, fake));
                context.waitTicks(12);
            }
            require(context.computeOnClient(c -> tags.popsOf(fake)) > 0, "Totem pops were not counted");
            require(context.computeOnClient(c -> tags.describe(fake)).contains("-"), "The tag does not show the pops: " + context.computeOnClient(c -> tags.describe(fake)));

            // A friend's card in Friend Color.
            context.runOnClient(c -> FriendManager.add("FakePlayer"));
            context.waitTicks(3);
            context.takeScreenshot("maro-nametags-friend");
        } finally {
            context.runOnClient(c -> {
                FriendManager.remove("FakePlayer");
                tags.setEnabled(false);
                tags.getSettings().forEach(Setting::reset);
                fakes.setEnabled(false);
                fakes.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
            });
        }
    }
}
