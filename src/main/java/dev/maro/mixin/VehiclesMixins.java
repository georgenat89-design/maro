package dev.maro.mixin;

import dev.maro.module.impl.movement.Vehicles;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The hooks behind {@link Vehicles} that touch how you are drawn: sitting in the driver's seat. */
public final class VehiclesMixins {
    private VehiclesMixins() {
    }

    @Mixin(EntityRenderer.class)
    public abstract static class Seat {
        /** You, moved into the driver's seat, legs out as when riding, facing the way the car does. */
        @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
        private void maro$seat(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
            if (!Vehicles.seats(entity)) return;
            EntityRenderState state = cir.getReturnValue();
            Vec3d offset = state.positionOffset == null ? Vec3d.ZERO : state.positionOffset;
            state.positionOffset = offset.add(Vehicles.seatOffset(tickDelta));
            if (state instanceof BipedEntityRenderState biped) biped.hasVehicle = true;
            if (state instanceof LivingEntityRenderState living) {
                living.bodyYaw = Vehicles.seatYaw(tickDelta);
                living.relativeHeadYaw = 0;
            }
        }

        /** In Free Drive you may be far from where you stand: still drawn in the car. */
        @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
        private void maro$seatShown(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
            if (Vehicles.seats(entity) && Vehicles.freeDriving()) cir.setReturnValue(true);
        }
    }
}
