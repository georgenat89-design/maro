package dev.maro.render.esp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BaseESP;
import dev.maro.runtime.utils.render.color.Color;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;

/** Batched world geometry using the same perspective projection as terrain and other ESP. */
public final class Renderer3D {
    private static final RenderLayer FILL = RenderLayer.of("maro_esp_fill", RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/esp_fill")).withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build())).translucent().build());
    private static final RenderLayer LINE = RenderLayer.of("maro_esp_lines", RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/esp_lines")).withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build())).translucent().build());
    private static final BufferAllocator FILL_BUFFER = new BufferAllocator(262144);
    private static final BufferAllocator LINE_BUFFER = new BufferAllocator(262144);
    private static final BufferAllocator FALLBACK_BUFFER = new BufferAllocator(1024);
    private static final VertexConsumerProvider.Immediate BUFFERS;
    static {
        var buffers = new LinkedHashMap<RenderLayer, BufferAllocator>();
        buffers.put(FILL, FILL_BUFFER); buffers.put(LINE, LINE_BUFFER);
        BUFFERS = VertexConsumerProvider.immediate(buffers, FALLBACK_BUFFER);
    }
    private final MatrixStack.Entry transform;
    private final Vec3d camera;
    private Renderer3D(WorldRenderContext context) {
        transform = context.matrices().peek(); camera = context.worldState().cameraRenderState.pos;
    }
    public static void init() {
        WorldRenderEvents.END_MAIN.register(context -> {
            BaseESP module = ModuleManager.get(BaseESP.class);
            if (module == null || !module.isEnabled()) return;
            try { module.render(new Renderer3D(context)); }
            finally { BUFFERS.draw(); }
        });
    }
    private void vertex(VertexConsumer consumer, double x, double y, double z, Color color) {
        consumer.vertex(transform, (float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z)).color(color.getPacked());
    }
    public void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                     double x3, double y3, double z3, double x4, double y4, double z4, Color color) {
        if (color.a == 0) return;
        VertexConsumer buffer = BUFFERS.getBuffer(FILL);
        vertex(buffer, x1,y1,z1,color); vertex(buffer,x2,y2,z2,color);
        vertex(buffer,x3,y3,z3,color); vertex(buffer,x4,y4,z4,color);
    }
    public void line(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        if (color.a == 0) return;
        Vec3d direction = new Vec3d(x2-x1,y2-y1,z2-z1).normalize();
        if (direction.lengthSquared() < .0001) return;
        VertexConsumer buffer = BUFFERS.getBuffer(LINE);
        vertex(buffer,x1,y1,z1,color);
        buffer.normal(transform,(float)direction.x,(float)direction.y,(float)direction.z).lineWidth(1.5f);
        vertex(buffer,x2,y2,z2,color);
        buffer.normal(transform,(float)direction.x,(float)direction.y,(float)direction.z).lineWidth(1.5f);
    }
    public void box(double x1, double y1, double z1, double x2, double y2, double z2,
                    Color fill, Color line, ShapeMode mode, int excluded) {
        if (mode.sides()) {
            quad(x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,fill);
            quad(x1,y2,z1,x1,y2,z2,x2,y2,z2,x2,y2,z1,fill);
            quad(x1,y1,z1,x1,y2,z1,x2,y2,z1,x2,y1,z1,fill);
            quad(x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,fill);
            quad(x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,fill);
            quad(x2,y1,z1,x2,y2,z1,x2,y2,z2,x2,y1,z2,fill);
        }
        if (mode.lines()) {
            line(x1,y1,z1,x2,y1,z1,line); line(x2,y1,z1,x2,y1,z2,line);
            line(x2,y1,z2,x1,y1,z2,line); line(x1,y1,z2,x1,y1,z1,line);
            line(x1,y2,z1,x2,y2,z1,line); line(x2,y2,z1,x2,y2,z2,line);
            line(x2,y2,z2,x1,y2,z2,line); line(x1,y2,z2,x1,y2,z1,line);
            line(x1,y1,z1,x1,y2,z1,line); line(x2,y1,z1,x2,y2,z1,line);
            line(x2,y1,z2,x2,y2,z2,line); line(x1,y1,z2,x1,y2,z2,line);
        }
    }
}
