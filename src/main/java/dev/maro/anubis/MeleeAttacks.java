package dev.maro.anubis;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
public final class MeleeAttacks {
    private static int claimedAge=-1;
    public static boolean isEnemy(PlayerEntity self,Entity target) { return target!=self&&target.isAlive()&&!target.isSpectator()&&!self.isTeammate(target)&&!dev.maro.config.FriendManager.isFriend(target.getName().getString()); }
    public static float charge(PlayerEntity player) { return player.getAttackCooldownProgress(0); }
    public static int ticksToFull(PlayerEntity player) { return (int)Math.ceil((1-charge(player))*player.getAttackCooldownProgressPerTick()); }
    public static boolean lineIsClear(PlayerEntity player,Vec3d from,Vec3d to) { return player.getEntityWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType()==HitResult.Type.MISS; }
    public static boolean attack(MinecraftClient client,EntityHitResult hit) {
        if(client.player==null||client.interactionManager==null||client.player.age==claimedAge||!isEnemy(client.player,hit.getEntity())||charge(client.player)<1)return false;
        client.interactionManager.attackEntity(client.player,hit.getEntity());client.player.swingHand(Hand.MAIN_HAND);return true;
    }
    public static void claimTick(MinecraftClient client) { if(client.player!=null)claimedAge=client.player.age; }
}
