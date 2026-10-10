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
 * distance, the armour has readable enchantment labels ("Prot IV"), and a popped totem shows as -1.
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
                ((BooleanSetting) setting(tags, "Outline")).set(true);
                tags.setEnabled(true);
                c.options.setPerspective(Perspective.FIRST_PERSON);
            });
            context.waitTicks(5);
            var fake = context.computeOnClient(c -> fakes.entity());
            require(fake != null, "No fake player to tag");
            // Step back and face it, and give its helmet Protection IV.
            context.runOnClient(c -> {
                c.player.refreshPositionAndAngles(fake.getX(), fake.getY(), fake.getZ() - 4, 0, -12);
                var enchantments = c.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    var stack = fake.getEquippedStack(slot);
                    stack.addEnchantment(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
                    stack.addEnchantment(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
                    stack.addEnchantment(enchantments.getOrThrow(Enchantments.MENDING), 1);
                }
                fake.getEquippedStack(EquipmentSlot.HEAD).addEnchantment(enchantments.getOrThrow(Enchantments.BINDING_CURSE), 1);
                var boots = fake.getEquippedStack(EquipmentSlot.FEET);
                boots.setDamage((int) (boots.getMaxDamage() * 0.82));
            });
            context.waitTicks(5);
            context.runOnClient(c -> {
                require(Nametags.hidesVanilla(fake), "The game's own label is still drawn over a tagged player");
                String said = tags.describe(fake);
                require(tags.getSettings().stream().noneMatch(s -> s.getName().equals("Ping") || s.getName().equals("Gamemode")), "Unused ping/game-mode options remain");
                require(!said.contains("ms") && said.startsWith("FakePlayer"), "Ping or game-mode prefix remains: " + said);
                require(said.contains("FakePlayer") && said.contains("m"), "The tag does not name it with its distance: " + said);
                require(tags.drawnTags().stream().anyMatch(t -> t.contains("FakePlayer")), "No tag was drawn: " + tags.drawnTags());
                var helmetLabels = Nametags.enchantLines(fake.getEquippedStack(EquipmentSlot.HEAD));
                require(helmetLabels.contains("Prot IV") && helmetLabels.contains("Unb III") && helmetLabels.contains("Mending") && helmetLabels.contains("!Binding"),
                        "Roman levels or curse label missing: " + helmetLabels);
            });
            context.takeScreenshot("maro-nametags");
            context.runOnClient(c -> ((BooleanSetting) setting(tags, "Enchants")).set(false));
            context.waitTicks(3);
            context.takeScreenshot("maro-nametags-armor-cards");
            context.runOnClient(c -> ((BooleanSetting) setting(tags, "Enchants")).set(true));

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

            // Tags use tracked player positions beyond vanilla entity rendering distance.
            context.runOnClient(c -> {
                FriendManager.remove("FakePlayer");
                ((BooleanSetting) setting(fakes, "Movable")).set(false);
                ((ModeSetting) setting(tags, "Armor")).set("None");
                ((BooleanSetting) setting(tags, "Held Item")).set(false);
                ((BooleanSetting) setting(tags, "Pulse")).set(false);
                fake.refreshPositionAndAngles(c.player.getX(), c.player.getY(), c.player.getZ() + 1280, 180, 0);
                ((NumberSetting) setting(tags, "Range")).set(512.0);
            });
            context.waitTicks(6);
            require(context.computeOnClient(c -> tags.drawnTags().stream().noneMatch(t -> t.contains("FakePlayer"))), "Range filter left a distant tag visible");
            context.runOnClient(c -> ((NumberSetting) setting(tags, "Range")).set(2048.0));
            context.waitTicks(6);
            require(context.computeOnClient(c -> tags.drawnTags().stream().anyMatch(t -> t.contains("FakePlayer") && t.contains("1280m"))), "No readable tag at 1280 blocks: " + context.computeOnClient(c -> tags.drawnTags()));
            context.takeScreenshot("maro-nametags-ember-1280m");
            context.runOnClient(c -> ((ModeSetting) setting(tags, "Style")).set("Aurora"));
            context.waitTicks(3);
            context.takeScreenshot("maro-nametags-aurora-1280m");
            context.runOnClient(c -> fake.refreshPositionAndAngles(c.player.getX(), c.player.getY(), c.player.getZ() - 1280, 0, 0));
            context.waitTicks(6);
            require(context.computeOnClient(c -> tags.drawnTags().stream().anyMatch(t -> t.contains("FakePlayer") && t.contains("1280m"))), "Player behind camera has no edge tag");
            context.takeScreenshot("maro-nametags-edge-1280m");
            context.runOnClient(c -> ((BooleanSetting) setting(tags, "Edge Tags")).set(false));
            context.waitTicks(3);
            require(context.computeOnClient(c -> tags.drawnTags().stream().noneMatch(t -> t.contains("FakePlayer"))), "Edge Tags off still drew a player behind camera");
            System.out.println("[nametags-long-range-proof] tracked-player tag at1280m,512m cutoff,Ember/Aurora styles,and behind-camera edge tag; server tracking remains the limit");
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
