package dev.maro.render.capture;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;

import java.util.OptionalInt;

/** Composites only private world annotations. The terrain is never rendered again. */
final class OverlayComposite {
    private static final RenderPipeline PIPELINE=RenderPipeline.builder()
        .withLocation(Identifier.of("maro","pipeline/private_overlay"))
        .withVertexShader(Identifier.of("maro","core/player_esp"))
        .withFragmentShader(Identifier.of("maro","core/private_overlay"))
        .withSampler("Overlay").withVertexFormat(VertexFormats.POSITION,VertexFormat.DrawMode.TRIANGLES)
        .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build();
    private static GpuBuffer vertices,indices;
    static void draw(Framebuffer overlay,Framebuffer destination){
        var device=RenderSystem.getDevice();
        if(vertices==null){
            var points=BufferUtils.createByteBuffer(48);
            for(float[] point:new float[][]{{-1,-1},{-1,1},{1,1},{1,-1}})points.putFloat(point[0]).putFloat(point[1]).putFloat(0);
            points.flip();vertices=device.createBuffer(()->"Private overlay quad",GpuBuffer.USAGE_VERTEX,points);
            var order=BufferUtils.createByteBuffer(24);for(int i:new int[]{0,1,2,0,2,3})order.putInt(i);order.flip();
            indices=device.createBuffer(()->"Private overlay indices",GpuBuffer.USAGE_INDEX,order);
        }
        try(var pass=device.createCommandEncoder().createRenderPass(()->"Private world annotations",destination.getColorAttachmentView(),OptionalInt.empty())){
            pass.setPipeline(PIPELINE);pass.bindTexture("Overlay",overlay.getColorAttachmentView(),RenderSystem.getSamplerCache().get(FilterMode.NEAREST,false));
            pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(indices,VertexFormat.IndexType.INT);pass.drawIndexed(0,0,6,1);
        }
    }
    static void close(){if(vertices!=null){vertices.close();vertices=null;}if(indices!=null){indices.close();indices=null;}}
}
