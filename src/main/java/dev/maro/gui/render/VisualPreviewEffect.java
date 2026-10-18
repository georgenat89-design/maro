package dev.maro.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.module.impl.visuals.PlayerESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.OptionalInt;

/** Reuses the world ESP shader on the native portrait alpha, with an independent small target. */
public final class VisualPreviewEffect {
    private static final int UNIFORM_BYTES=(9+16+4+256*2+64)*16;
    private static final RenderPipeline PIPELINE=RenderPipeline.builder()
        .withLocation(Identifier.of("maro","pipeline/visual_preview"))
        .withVertexShader(Identifier.of("maro","core/player_esp"))
        .withFragmentShader(Identifier.of("maro","core/player_esp"))
        .withSampler("u_Mask").withUniform("EspData",UniformType.UNIFORM_BUFFER)
        .withVertexFormat(VertexFormats.POSITION,VertexFormat.DrawMode.TRIANGLES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build();
    private static final ByteBuffer DATA=BufferUtils.createByteBuffer(UNIFORM_BYTES);
    private static SimpleFramebuffer target;
    private static GpuBuffer uniforms,vertices,indices;
    private static int frames;
    private VisualPreviewEffect(){}
    public static int frames(){return frames;}
    public static boolean hasResources(){return target!=null;}

    public static GpuTextureView apply(GpuTextureView portrait,PlayerESP module){
        int w=portrait.texture().getWidth(0),h=portrait.texture().getHeight(0);
        if(target==null)target=new SimpleFramebuffer("Maro visual preview",w,h,false);
        else if(target.textureWidth!=w||target.textureHeight!=h)target.resize(w,h);
        if(uniforms==null){
            uniforms=RenderSystem.getDevice().createBuffer(()->"Maro preview uniforms",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,UNIFORM_BYTES);
            var v=BufferUtils.createByteBuffer(4*12);float[] xy={-1,-1,-1,1,1,1,1,-1};
            for(int i=0;i<4;i++){v.putFloat(xy[i*2]);v.putFloat(xy[i*2+1]);v.putFloat(0);}v.flip();
            var ix=BufferUtils.createByteBuffer(24);for(int i:new int[]{0,1,2,0,2,3})ix.putInt(i);ix.flip();
            vertices=RenderSystem.getDevice().createBuffer(()->"Maro preview quad",GpuBuffer.USAGE_VERTEX,v);
            indices=RenderSystem.getDevice().createBuffer(()->"Maro preview indices",GpuBuffer.USAGE_INDEX,ix);
        }
        DATA.clear();for(int i=0;i<UNIFORM_BYTES;i+=4)DATA.putFloat(i,0);
        float[] values=module.uniformValues();values[PlayerESP.MASK_SCALE_INDEX]=1;
        for(int i=0;i<values.length;i++)DATA.putFloat(i*4,values[i]);
        int color=module.playerColor(MinecraftClient.getInstance().player);
        // Negative count selects portrait composition; yzw carry the real player's ESP color.
        DATA.putFloat(128,-1);DATA.putFloat(132,(color>>16&255)/255f);
        DATA.putFloat(136,(color>>8&255)/255f);DATA.putFloat(140,(color&255)/255f);
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniforms.slice(),DATA);
        try(var pass=encoder.createRenderPass(()->"Maro live visual preview",target.getColorAttachmentView(),OptionalInt.of(0))){
            pass.setPipeline(PIPELINE);pass.setUniform("EspData",uniforms.slice());
            pass.bindTexture("u_Mask",portrait,RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(indices,VertexFormat.IndexType.INT);pass.drawIndexed(0,0,6,1);
        }
        frames++;return target.getColorAttachmentView();
    }
    public static void release(){
        if(target!=null){target.delete();target=null;}
        if(uniforms!=null){uniforms.close();uniforms=null;}
        if(vertices!=null){vertices.close();vertices=null;}
        if(indices!=null){indices.close();indices=null;}
    }
}
