package dev.maro.nathan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.maro.nathan.modules.FreeCam;
import dev.maro.nathan.modules.FreeLook;
import dev.maro.nathan.modules.KeyZoom;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Two things done to the camera, neither of which touches the player.
 *
 * <p>Smooth F5 shortens the third person camera's distance. It works on what
 * the game's own wall check comes back with, never on what goes into it, and
 * only ever makes it smaller. A point nearer along a line that is clear as far
 * as the answer is clear too, so whatever the check decided still holds - and so
 * does anything another mod did to the distance on the way in.
 *
 * <p>Free Cam puts the camera somewhere else altogether. It does it after the
 * game has finished placing the camera on the player, and before the game works
 * out the view, the cull and the far plane from where the camera is - so all of
 * those are worked out for the place the camera really is. The camera is marked
 * as detached, which is what the game goes by to decide whether to draw your own
 * model: out of your head, you can see yourself.
 *
 * <p>Freelook turns the camera without turning you. In third person it puts the
 * camera back at your eyes - where they are drawn this frame, so it rides with
 * you - turns it to the camera's own angles, and swings it out again on the same
 * arm the game uses, through the game's own check for walls unless told to go
 * through them. In first person it turns the view where it is.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    private Entity focusedEntity;

    @Shadow
    private float cameraY;

    @Shadow
    private float lastCameraY;

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void moveBy(float forwards, float up, float right);

    @Shadow
    private float clipToSpace(float maxZoom) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "clipToSpace", at = @At("RETURN"), cancellable = true)
    private void nameeprotect$smoothF5(float maxZoom, CallbackInfoReturnable<Float> cir) {
        float factor = KeyZoom.distanceFactor();

        if (factor < 1) cir.setReturnValue(cir.getReturnValue() * factor);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void nameeprotect$freeCam(World level, Entity cameraEntity, boolean thirdPerson, boolean mirrored, float partialTick, CallbackInfo ci) {
        double[] at = FreeCam.frame((Camera) (Object) this);

        if (at != null) {
            setRotation((float) at[3], (float) at[4]);
            setPos(at[0], at[1], at[2]);
            this.thirdPerson = true;
            return;
        }

        float[] look = FreeLook.frame();

        if (look == null) return;

        setRotation(look[0], look[1]);

        if (!thirdPerson || !FreeLook.orbits() || focusedEntity == null) return;

        // Back to the eyes, as they are drawn this frame, and out again along
        // the camera's own line rather than the player's.
        setPos(
            MathHelper.lerp((double) partialTick, focusedEntity.lastX, focusedEntity.getX()),
            MathHelper.lerp((double) partialTick, focusedEntity.lastY, focusedEntity.getY()) + MathHelper.lerp(partialTick, lastCameraY, cameraY),
            MathHelper.lerp((double) partialTick, focusedEntity.lastZ, focusedEntity.getZ()));

        float reach = FreeLook.reach();

        moveBy(-(FreeLook.throughWalls() ? reach : clipToSpace(reach)), 0, 0);
    }
}
