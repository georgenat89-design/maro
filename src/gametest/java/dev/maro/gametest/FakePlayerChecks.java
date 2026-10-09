package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;

/**
 * Fake Player: it appears where you stand wearing the armour picked, a hit takes health, at none
 * left it pops a totem and is healed, and turning the module off takes it away.
 */
final class FakePlayerChecks {
    private FakePlayerChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        FakePlayer module = ModuleManager.get(FakePlayer.class);
        require(module != null, "Fake Player was not registered");
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        try {
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                ((ModeSetting) module.getSettings().stream().filter(s -> s.getName().equals("Armor")).findFirst().orElseThrow()).set("Netherite");
                ((NumberSetting) module.getSettings().stream().filter(s -> s.getName().equals("Health")).findFirst().orElseThrow()).set(1.0);
                module.setEnabled(true);
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            });
            context.waitTicks(5);
            var fake = context.computeOnClient(c -> module.entity());
            require(fake != null && context.computeOnClient(c -> c.world.getEntityById(fake.getId()) == fake), "The fake player is not in the world");
            require(context.computeOnClient(c -> fake.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.NETHERITE_CHESTPLATE)),
                    "The fake player is not wearing the netherite picked");
            // Step back so it shows in the screenshot, then hit it until a totem pops.
            context.runOnClient(c -> c.player.setPosition(c.player.getX(), c.player.getY(), c.player.getZ() - 3));
            context.waitTicks(5);
            context.takeScreenshot("maro-fake-player");
            float before = context.computeOnClient(c -> fake.getHealth());
            context.runOnClient(c -> c.interactionManager.attackEntity(c.player, fake));
            context.waitTicks(2);
            float after = context.computeOnClient(c -> fake.getHealth());
            System.out.println("FAKE PLAYER health " + before + " -> " + after);
            require(after < before, "Hitting the fake player took no health: " + before + " -> " + after);
            for (int i = 0; i < 20 && context.computeOnClient(c -> module.totemPops()) == 0; i++) {
                context.runOnClient(c -> c.interactionManager.attackEntity(c.player, fake));
                context.waitTicks(12);
            }
            require(context.computeOnClient(c -> module.totemPops()) > 0, "The fake player never popped a totem");
            require(context.computeOnClient(c -> fake.getHealth()) > 0, "The fake player was not healed by its totem");
            context.takeScreenshot("maro-fake-player-totem");
            context.runOnClient(c -> module.setEnabled(false));
            context.waitTicks(2);
            require(context.computeOnClient(c -> c.world.getEntityById(fake.getId()) == null), "Turning Fake Player off left it in the world");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
            });
        }
    }
}
