package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.Pet;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.util.math.Vec3d;

/** Real queued entity rendering, client-only lifecycle, movement, and live settings. */
public final class PetChecks {
    public static long renderedFrames;
    public static float renderedScale, renderedAge;
    public static net.minecraft.util.DyeColor renderedCollar;
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static Setting<?> setting(Pet pet, String name) { return pet.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var pet = ModuleManager.get(Pet.class);
        require(pet != null, "Pet was not registered");
        var original = context.computeOnClient(c -> c.player.getEntityPos());
        var angles = context.computeOnClient(c -> new float[]{c.player.getYaw(), c.player.getPitch()});
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("fill -18 99 -18 18 99 18 minecraft:stone");
            world.getServer().runCommand("fill -18 100 -18 18 108 18 minecraft:air");
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a 0 100 0 0 0");
            context.waitTicks(8);
            context.runOnClient(c -> {
                c.player.getAbilities().flying = false;
                pet.getSettings().forEach(Setting::reset); pet.setEnabled(true);
                ((ModeSetting)setting(pet, "Formation")).set("Sidekick");
                ((BooleanSetting)setting(pet, "Name Tag")).set(true);
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            });
            context.waitTicks(15);
            context.runOnClient(c -> {
                require(pet.companion() != null, "Pet did not spawn");
                require(c.world.getEntityById(pet.companion().getId()) == null, "Cosmetic pet was inserted into ClientWorld");
                require(pet.position().distanceTo(c.player.getEntityPos()) < 4, "Pet spawned too far away");
            });
            var uuid = context.computeOnClient(c -> pet.companion().getUuid());
            require(world.getServer().computeOnServer(s -> s.getOverworld().getEntity(uuid)) == null, "Pet leaked into server world");
            context.takeScreenshot("maro-pet-wolf-third-person");
            for (String style : Pet.STYLES) {
                long frames = context.computeOnClient(c -> renderedFrames);
                context.runOnClient(c -> ((ModeSetting)setting(pet, "Pet")).set(style));
                context.waitTicks(5);
                context.runOnClient(c -> {
                    require(pet.companion() != null && pet.companion().getType() != null, "Pet style failed: " + style);
                    require(c.world.getEntityById(pet.companion().getId()) == null, "Style change registered an entity: " + style);
                    require(renderedFrames > frames, "Pet did not submit real render commands: " + style);
                });
                context.takeScreenshot("maro-pet-" + style.toLowerCase());
            }
            context.runOnClient(c -> {
                ((ModeSetting)setting(pet, "Pet")).set("Cat");
                ((BooleanSetting)setting(pet, "Baby")).set(true);
                ((NumberSetting)setting(pet, "Size")).set(1.5);
                ((ModeSetting)setting(pet, "Collar")).set("Cyan");
            });
            context.waitTicks(5);
            require(context.computeOnClient(c -> ((PassiveEntity)pet.companion()).isBaby()), "Baby setting did not update live");
            require(context.computeOnClient(c -> renderedCollar == net.minecraft.util.DyeColor.CYAN), "Collar setting did not reach renderer");
            float largeScale = context.computeOnClient(c -> renderedScale);
            context.runOnClient(c -> {
                ((NumberSetting)setting(pet, "Size")).set(.75);
                ((BooleanSetting)setting(pet, "Animate")).set(false);
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.player.setYaw(90); c.player.setPitch(26);
            });
            context.waitTicks(3);
            require(context.computeOnClient(c -> Math.abs(renderedScale * 2 - largeScale) < .001), "Size did not update renderer immediately");
            require(context.computeOnClient(c -> renderedAge == 0), "Animation toggle did not freeze rendered motion");
            context.takeScreenshot("maro-pet-cat-first-person");
            context.runOnClient(c -> {
                ((ModeSetting)setting(pet, "Formation")).set("Follow");
                ((BooleanSetting)setting(pet, "Baby")).set(false);
                ((BooleanSetting)setting(pet, "Animate")).set(true);
                ((NumberSetting)setting(pet, "Size")).reset();
                c.player.setYaw(0); c.player.setPitch(0); pet.recall();
            });
            Vec3d start = context.computeOnClient(c -> pet.position());
            context.runOnClient(c -> c.options.forwardKey.setPressed(true));
            context.waitTicks(22);
            context.runOnClient(c -> c.options.forwardKey.setPressed(false));
            context.waitTicks(25);
            context.runOnClient(c -> {
                require(pet.position().distanceTo(start) > 3, "Pet did not follow walking player");
                double gap = pet.position().distanceTo(c.player.getEntityPos());
                require(gap > .8 && gap < 3, "Pet follow distance did not settle: " + gap);
                require(Math.abs(pet.position().y - 100.015) < .1, "Walking pet floated or sank through floor");
            });
            context.runOnClient(c -> {
                ((ModeSetting)setting(pet, "Formation")).set("Orbit");
                ((ModeSetting)setting(pet, "Movement")).set("Hover");
                ((NumberSetting)setting(pet, "Hover Height")).set(1.5);
            });
            context.waitTicks(25);
            Vec3d orbitStart = context.computeOnClient(c -> pet.position());
            context.waitTicks(30);
            require(context.computeOnClient(c -> pet.position().distanceTo(orbitStart)) > .6, "Orbit did not move");
            require(context.computeOnClient(c -> pet.position().y - c.player.getY()) > 1.2, "Hover height did not update");
            world.getServer().runCommand("tp @a 30 100 0 0 0");
            context.waitTicks(3);
            require(context.computeOnClient(c -> pet.position().distanceTo(c.player.getEntityPos())) < 4, "Pet did not catch up after teleport");
            context.runOnClient(c -> {
                pet.setEnabled(false); require(pet.companion() == null && pet.position() == null, "Disabling did not release pet");
                pet.setEnabled(true);
            });
            context.waitTicks(3);
            require(context.computeOnClient(c -> pet.companion() != null), "Re-enabling pet failed");
        } finally {
            context.runOnClient(c -> {
                c.options.forwardKey.setPressed(false); pet.setEnabled(false); pet.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective); c.player.getAbilities().flying = flying;
            });
            world.getServer().runCommand("tp @a " + original.x + " " + original.y + " " + original.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(5);
        }
    }
}
