package dev.maro.render.esp;

import net.minecraft.client.MinecraftClient;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BaseESP;
import dev.maro.module.impl.visuals.BlockESP;
import dev.maro.module.impl.player.AutoBuilder;
import dev.maro.builder.SchematicRenderer;
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
    private static final RenderLayer VISIBLE_FILL = RenderLayer.of("maro_builder_fill", RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/builder_fill")).withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).withDepthWrite(false).build())).translucent().build());
    private static final RenderLayer VISIBLE_LINE = RenderLayer.of("maro_builder_lines", RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/builder_lines")).withCull(false).withDepthWrite(false).build())).translucent().build());
    /** The same through-wall fill and lines, drawn into Block ESP's glow target for its bloom. */
    private static final OutputTarget GLOW_TARGET = new OutputTarget("maro_block_esp_glow", BlockEspRenderer::glowTarget);
    private static final RenderLayer GLOW_FILL = RenderLayer.of("maro_esp_glow_fill", RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/esp_glow_fill")).withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build()))
        .outputTarget(GLOW_TARGET).translucent().build());
    private static final RenderLayer GLOW_LINE = RenderLayer.of("maro_esp_glow_lines", RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("maro", "pipeline/esp_glow_lines")).withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build()))
        .outputTarget(GLOW_TARGET).translucent().build());
    private static final BufferAllocator FILL_BUFFER = new BufferAllocator(262144);
    private static final BufferAllocator LINE_BUFFER = new BufferAllocator(262144);
    private static final BufferAllocator FALLBACK_BUFFER = new BufferAllocator(1024);
    private static final VertexConsumerProvider.Immediate BUFFERS;
    static {
        var buffers = new LinkedHashMap<RenderLayer, BufferAllocator>();
        buffers.put(FILL, FILL_BUFFER); buffers.put(LINE, LINE_BUFFER);
        buffers.put(VISIBLE_FILL,new BufferAllocator(262144)); buffers.put(VISIBLE_LINE,new BufferAllocator(262144));
        buffers.put(GLOW_FILL,new BufferAllocator(262144)); buffers.put(GLOW_LINE,new BufferAllocator(262144));
        BUFFERS = VertexConsumerProvider.immediate(buffers, FALLBACK_BUFFER);
    }
    private final MatrixStack.Entry transform;
    private final Vec3d camera;
    private float lineWidth = 1.5f;
    private boolean throughWalls=true;
    private boolean glow;
    public void throughWalls(boolean value){throughWalls=value;}
    /** While on, geometry goes into Block ESP's glow target instead of the frame. */
    public void glow(boolean value){glow=value&&BlockEspRenderer.glowTarget()!=null;}
    public Vec3d camera(){return camera;}
    public void lineWidth(float width) { lineWidth = Math.max(.5f,Math.min(4,width)); }
    private Renderer3D(WorldRenderContext context) {
        transform = context.matrices().peek(); camera = context.worldState().cameraRenderState.pos;
    }
    public static void init() {
        WorldRenderEvents.END_MAIN.register(context -> {
            BaseESP module = ModuleManager.get(BaseESP.class);
            AutoBuilder builder=ModuleManager.get(AutoBuilder.class);
            // Block ESP and the modules built on it (Storage ESP, Hole ESP).
            java.util.List<BlockESP> esps=new java.util.ArrayList<>();
            for(var m:ModuleManager.all())if(m instanceof BlockESP esp&&esp.isEnabled())esps.add(esp);
            dev.maro.module.impl.visuals.Trajectories paths=ModuleManager.get(dev.maro.module.impl.visuals.Trajectories.class);
            boolean pathsOn=paths!=null&&paths.isEnabled();
            dev.maro.module.impl.movement.CoordsFly coords=ModuleManager.get(dev.maro.module.impl.movement.CoordsFly.class);
            boolean coordsOn=coords!=null&&coords.isEnabled();
            if ((module == null || !module.isEnabled())&&(builder==null||!builder.isEnabled())&&esps.isEmpty()&&!pathsOn&&!coordsOn) return;
            try {
                if(module!=null&&module.isEnabled())module.render(new Renderer3D(context));
                if(pathsOn)paths.render(new Renderer3D(context),MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true));
                if(coordsOn)coords.render(new Renderer3D(context),MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true));
                for(BlockESP esp:esps)esp.render(new Renderer3D(context));
                if(builder!=null&&builder.isEnabled())SchematicRenderer.render(builder,new Renderer3D(context),context);
            }
            finally { BUFFERS.draw(); }
        });
    }
    private void vertex(VertexConsumer consumer, double x, double y, double z, Color color) {
        consumer.vertex(transform, (float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z)).color(color.getPacked());
    }
    public void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                     double x3, double y3, double z3, double x4, double y4, double z4, Color color) {
        if (color.a == 0) return;
        VertexConsumer buffer = BUFFERS.getBuffer(glow?GLOW_FILL:throughWalls?FILL:VISIBLE_FILL);
        vertex(buffer, x1,y1,z1,color); vertex(buffer,x2,y2,z2,color);
        vertex(buffer,x3,y3,z3,color); vertex(buffer,x4,y4,z4,color);
    }
    public void line(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        if (color.a == 0) return;
        Vec3d direction = new Vec3d(x2-x1,y2-y1,z2-z1).normalize();
        if (direction.lengthSquared() < .0001) return;
        VertexConsumer buffer = BUFFERS.getBuffer(glow?GLOW_LINE:throughWalls?LINE:VISIBLE_LINE);
        vertex(buffer,x1,y1,z1,color);
        buffer.normal(transform,(float)direction.x,(float)direction.y,(float)direction.z).lineWidth(lineWidth);
        vertex(buffer,x2,y2,z2,color);
        buffer.normal(transform,(float)direction.x,(float)direction.y,(float)direction.z).lineWidth(lineWidth);
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
