package dev.maro.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.maro.render.esp.EntityRenderStateAccess;
import dev.maro.render.esp.PlayerEspRenderer;
import dev.maro.render.esp.SilhouetteBuffers;
import dev.maro.render.esp.SilhouetteCommandQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Renders the Player ESP's silhouettes.
 *
 * <p>After the frame's entities are queued, the tracked players are rendered again through a
 * {@link SilhouetteCommandQueue}, which reduces them to flat colour in the game's outline render
 * layers. While that happens - and again when the outline buffers are flushed - the world
 * renderer's entity outline framebuffer is swapped for the ESP's own, so the silhouettes land there
 * and vanilla's glowing outlines are left untouched. Adapted from Meteor Client's entity shader
 * hooks (GPL-3.0).
 */
@Mixin(WorldRenderer.class)
public abstract class PlayerEspWorldRendererMixin {
    @Shadow
    private Framebuffer entityOutlineFramebuffer;

    @Shadow
    @Final
    private DefaultFramebufferSet framebufferSet;

    @Shadow
    @Final
    private EntityRenderManager entityRenderManager;

    @Unique
    private final SilhouetteCommandQueue maro$silhouettes = new SilhouetteCommandQueue();

    @Unique
    private RenderDispatcher maro$silhouetteDispatcher;

    @Unique
    private VertexConsumerProvider maro$silhouetteProvider;

    @Unique
    private Framebuffer maro$savedOutline;

    @Unique
    private Handle<Framebuffer> maro$savedOutlineHandle;

    @Inject(method = "render", at = @At("HEAD"))
    private void maro$playerEspBegin(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
                                     Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f cullingProjection, GpuBufferSlice fog,
                                     Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        PlayerEspRenderer.beginFrame(positionMatrix, projectionMatrix);
    }

    @Inject(method = "pushEntityRenders", at = @At("TAIL"))
    private void maro$playerEspDraw(MatrixStack matrices, WorldRenderState worldState, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (!PlayerEspRenderer.active()) return;
        Framebuffer mask = PlayerEspRenderer.mask();
        if (mask == null) return;

        var camera = worldState.cameraRenderState.pos;
        Set<Entity> drawn = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var state : worldState.entityRenderStates) {
            Entity entity = ((EntityRenderStateAccess) state).maro$getEntity();
            if (entity == null || !PlayerEspRenderer.shouldDraw(entity)) continue;
            maro$drawSilhouette(entity, state, matrices, worldState);
            PlayerEspRenderer.addTracer(entity, state.x, state.y, state.z, camera);
            drawn.add(entity);
        }

        // Players the game left out of this frame - culled for distance, by another mod's entity
        // culling, or hidden by a render setting - are still tracked, so draw them ourselves.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            float tickProgress = client.getRenderTickCounter().getTickProgress(false);
            for (var player : client.world.getPlayers()) {
                if (drawn.contains(player) || !PlayerEspRenderer.shouldDraw(player)) continue;
                Vec3d at = player.getLerpedPos(tickProgress);
                PlayerEspRenderer.addTracer(player, at.x, at.y, at.z, camera);
                if (!PlayerEspRenderer.onScreen(at.x, at.y + player.getHeight() / 2, at.z, camera)) continue;
                EntityRenderState state = entityRenderManager.getAndUpdateRenderState(player, tickProgress);
                maro$drawSilhouette(player, state, matrices, worldState);
                drawn.add(player);
            }
        }

        if (drawn.isEmpty()) return;

        if (maro$silhouetteDispatcher == null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            maro$silhouetteDispatcher = new RenderDispatcher(
                maro$silhouettes,
                mc.getBlockRenderManager(),
                new SilhouetteBuffers.Forwarding(() -> maro$silhouetteProvider),
                mc.getAtlasManager(),
                SilhouetteBuffers.NoopOutline.INSTANCE,
                SilhouetteBuffers.NoopImmediate.INSTANCE,
                mc.textRenderer
            );
        }

        maro$pushOutline(mask);
        maro$silhouetteProvider = PlayerEspRenderer.provider();
        try {
            maro$silhouetteDispatcher.render();
            maro$silhouettes.onNextFrame();
        } finally {
            maro$silhouetteProvider = null;
            maro$popOutline();
        }

        PlayerEspRenderer.markDrawn();
    }

    /** Where the frame's outline geometry is flushed: flush the silhouettes too, into our target. */
    @Inject(method = "method_62214", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/OutlineVertexConsumerProvider;draw()V"))
    private void maro$playerEspFlush(CallbackInfo ci) {
        Framebuffer mask = PlayerEspRenderer.mask();
        if (!PlayerEspRenderer.pending() || mask == null) return;

        maro$pushOutline(mask);
        try {
            PlayerEspRenderer.provider().draw();
        } finally {
            maro$popOutline();
        }
    }

    @Unique
    private void maro$drawSilhouette(Entity entity, EntityRenderState state, MatrixStack matrices, WorldRenderState worldState) {
        var camera = worldState.cameraRenderState.pos;
        maro$silhouettes.setColor(PlayerEspRenderer.color(entity));

        var renderer = entityRenderManager.getRenderer(state);
        var offset = renderer.getPositionOffset(state);

        matrices.push();
        matrices.translate(state.x - camera.x + offset.x, state.y - camera.y + offset.y, state.z - camera.z + offset.z);
        renderer.render(state, matrices, maro$silhouettes, worldState.cameraRenderState);
        matrices.pop();

        PlayerEspRenderer.include(entity, state.x, state.y, state.z, camera);
    }

    @Unique
    private void maro$pushOutline(Framebuffer target) {
        maro$savedOutline = entityOutlineFramebuffer;
        entityOutlineFramebuffer = target;

        maro$savedOutlineHandle = framebufferSet.entityOutlineFramebuffer;
        framebufferSet.entityOutlineFramebuffer = () -> target;
    }

    @Unique
    private void maro$popOutline() {
        entityOutlineFramebuffer = maro$savedOutline;
        framebufferSet.entityOutlineFramebuffer = maro$savedOutlineHandle;
        maro$savedOutline = null;
        maro$savedOutlineHandle = null;
    }
}
