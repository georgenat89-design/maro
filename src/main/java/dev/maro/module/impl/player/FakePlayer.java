package dev.maro.module.impl.player;

import com.mojang.authlib.GameProfile;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * Fake Player: a copy of you standing where you were, only on your screen, for trying out how
 * things look and land. It wears your skin and name (or another name), the armour you choose, takes
 * your hits with the red flash, knockback and hurt sound, pops totems when it runs out of health
 * and, while Movable, is knocked about and falls like a real player.
 */
public class FakePlayer extends Module {
    private static final int ENTITY_ID = -18_042;
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final TextSetting name = add(new TextSetting("Name", "The name over its head", "FakePlayer", 16, "FakePlayer"));
    private final NumberSetting health = add(new NumberSetting("Health", "Its health, in half hearts", 20, 1, 100, 1));
    private final BooleanSetting infTotems = add(new BooleanSetting("Inf Totems", "Pops a totem instead of dying, every time", true));
    private final BooleanSetting movable = add(new BooleanSetting("Movable", "Knocked back by hits and pulled down by gravity, like a player", true));
    private final ModeSetting armor = add(new ModeSetting("Armor", "What it wears", "Copy Mine",
            "Copy Mine", "None", "Leather", "Chainmail", "Iron", "Gold", "Diamond", "Netherite"));
    private final BooleanSetting enchanted = add(new BooleanSetting("Enchanted", "Its armour and items shine as if enchanted", true));

    private final List<SettingSection> sections = List.of(SettingSection.of("Fake Player", name, health, infTotems, movable, armor, enchanted));

    private Fake fake;

    public FakePlayer() {
        super("Fake Player", "A copy of you only you can see, that takes hits and pops totems like a real player", Category.PLAYER);
    }

    public static FakePlayer get() {
        return ModuleManager.get(FakePlayer.class);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    /** The fake player, while there is one; for tests. */
    public OtherClientPlayerEntity entity() {
        return fake;
    }

    @Override
    protected void onEnable() {
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        spawn();
    }

    @Override
    protected void onDisable() {
        despawn();
    }

    @Override
    public void onTick() {
        if (!inGame()) {
            fake = null;
            return;
        }
        if (fake == null || fake.getEntityWorld() != mc.world) {
            spawn();
            return;
        }
        if (movable.get()) {
            // Gravity, ground friction and the knockback from hits, as a player falls and slides.
            Vec3d v = fake.getVelocity();
            double vy = fake.isOnGround() ? Math.max(v.y, -0.08) : (v.y - 0.08) * 0.98;
            fake.move(MovementType.SELF, new Vec3d(v.x, vy, v.z));
            double friction = fake.isOnGround() ? 0.546 : 0.91;
            fake.setVelocity(v.x * friction, fake.isOnGround() ? 0 : vy, v.z * friction);
        }
    }

    private void spawn() {
        despawn();
        ClientWorld world = mc.world;
        String shown = name.get().isBlank() ? "FakePlayer" : name.get().trim();
        Fake f = new Fake(world, new GameProfile(UUID.randomUUID(), shown));
        f.copyPositionAndRotation(mc.player);
        f.setHeadYaw(mc.player.getHeadYaw());
        f.setBodyYaw(mc.player.bodyYaw);
        f.setId(ENTITY_ID);
        dress(f);
        var max = f.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (max != null) max.setBaseValue(Math.max(20, health.get()));
        f.setHealth(health.getFloat());
        world.addEntity(f);
        fake = f;
    }

    private void despawn() {
        if (fake != null && mc.world != null) mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        fake = null;
    }

    private void dress(Fake f) {
        if (armor.is("Copy Mine")) {
            f.getInventory().clone(mc.player.getInventory());
            for (EquipmentSlot slot : WORN) f.equipStack(slot, mc.player.getEquippedStack(slot).copy());
        } else {
            Item[] set = switch (armor.get()) {
                case "Leather" -> new Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
                case "Chainmail" -> new Item[] {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
                case "Iron" -> new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
                case "Gold" -> new Item[] {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
                case "Diamond" -> new Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
                case "Netherite" -> new Item[] {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
                default -> null;
            };
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) f.equipStack(slots[i], set == null ? ItemStack.EMPTY : new ItemStack(set[i]));
            f.equipStack(EquipmentSlot.MAINHAND, mc.player.getMainHandStack().copy());
            f.equipStack(EquipmentSlot.OFFHAND, infTotems.get() ? new ItemStack(Items.TOTEM_OF_UNDYING) : ItemStack.EMPTY);
        }
        if (enchanted.get()) {
            for (EquipmentSlot slot : WORN) {
                ItemStack stack = f.getEquippedStack(slot);
                if (!stack.isEmpty() && !stack.isOf(Items.TOTEM_OF_UNDYING)) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
        }
    }

    public boolean isFake(Entity entity) {
        return fake != null && entity == fake;
    }

    /**
     * You hit it: it flashes red, makes the hurt sound, is knocked back and loses health as a
     * player would; at none left it pops a totem (and is healed) or, without totems, dies.
     */
    public void hit(Entity attacker) {
        Fake f = fake;
        if (f == null) return;
        float damage = (float) mc.player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        float charge = mc.player.getAttackCooldownProgress(0.5f);
        damage *= 0.2f + charge * charge * 0.8f;
        boolean crit = charge > 0.9f && mc.player.fallDistance > 0 && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater();
        if (crit) {
            damage *= 1.5f;
            mc.particleManager.addEmitter(f, net.minecraft.particle.ParticleTypes.CRIT);
        }
        // Armour softens it a little, as on a real player.
        damage *= 1 - Math.min(20, f.getArmor()) / 25f;
        f.animateDamage(attacker.getYaw());
        f.hurtTime = 10;
        f.maxHurtTime = 10;
        mc.world.playSound(mc.player, f.getX(), f.getY(), f.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1, 1);
        if (movable.get()) {
            double yaw = Math.toRadians(attacker.getYaw());
            f.takeKnockback(0.4 + (mc.player.isSprinting() ? 0.5 : 0), Math.sin(yaw), -Math.cos(yaw));
        }
        float left = f.getHealth() - damage;
        if (left > 0) {
            f.setHealth(left);
            return;
        }
        if (infTotems.get()) {
            f.handleStatus(EntityStatuses.USE_TOTEM_OF_UNDYING);
            f.setHealth(Math.max(1, health.getFloat()));
            totemPops++;
        } else {
            f.setHealth(0);
            f.handleStatus(EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES);
            despawn();
            setEnabled(false);
        }
    }

    private int totemPops;

    /** How many totems it has popped since it was made; for tests. */
    public int totemPops() {
        return totemPops;
    }

    /** The fake player: an ordinary other player that wears your skin. */
    private final class Fake extends OtherClientPlayerEntity {
        Fake(ClientWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public PlayerListEntry getPlayerListEntry() {
            return mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        }
    }
}
