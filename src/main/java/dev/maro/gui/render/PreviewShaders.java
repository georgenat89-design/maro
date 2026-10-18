package dev.maro.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.module.impl.visuals.*;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import java.util.OptionalInt;

/** Actual world shaders, isolated targets and clocks; previewing never enables a world effect. */
public final class PreviewShaders {
    private static final Pass SKY=new Pass("custom_sky","custom_sky","SkyData","u_Sky",144),GEEK=new Pass("player_esp","geeked","GeekedData","Scene",48);
    private static Texture placeholder;
    private PreviewShaders(){}
    public static boolean hasResources(){return SKY.target!=null||GEEK.target!=null;}
    private static Texture blank(){if(placeholder==null){placeholder=new Texture(1,1,TextureFormat.RGBA8,FilterMode.NEAREST,FilterMode.NEAREST);placeholder.upload(new byte[]{0,0,0,-1});}return placeholder;}
    public static GpuTextureView sky(CustomSky m,double seconds,int w,int h){
        if(m.showingPicture()){m.loadStoredPicture();if(m.picture().texture()==null)return null;}
        var inverse=new Matrix4f().perspective((float)Math.toRadians(80),(float)w/h,.05f,128).rotateX(.25f).invert();
        float[] values=new float[36];inverse.get(values);var settings=m.previewUniformValues(2f/(h*1.2f),seconds);System.arraycopy(settings,0,values,16,settings.length);
        var texture=m.showingPicture()?m.picture().texture():blank();
        return SKY.draw(w,h,values,texture.getGlTextureView(),m.previewWraps());
    }
    public static void draw(DrawContext ctx,dev.maro.module.Module module,float x,float y,float w,float h,double time,boolean liveWorld){
        float physical=(float)Math.hypot(ctx.getMatrices().m00(),ctx.getMatrices().m01())*(float)MinecraftClient.getInstance().getWindow().getScaleFactor();
        float reduction=Math.min(1,Math.min(640/(w*physical),360/(h*physical)));
        int width=Math.max(64,Math.round(w*physical*reduction)),height=Math.max(32,Math.round(h*physical*reduction));GpuTextureView view;
        if(module instanceof CustomSky sky)view=sky(sky,time,width,height);
        else{
            var m=(Geeked)module;var mc=MinecraftClient.getInstance();
            var source=liveWorld&&mc.world!=null?mc.getFramebuffer().getColorAttachmentView():sky(dev.maro.module.ModuleManager.get(CustomSky.class),0,width,height);
            float value=0;for(var s:m.getSettings())if(s.getName().equals("Intensity"))value=s.toJson().getAsFloat()/100;
            float speed=1;for(var s:m.getSettings())if(s.getName().equals("Speed"))speed=s.toJson().getAsFloat();
            view=source==null?null:GEEK.draw(width,height,new float[]{(float)time*speed,value,m.modeIndex(),w/h,m.wobble(),m.colors(),m.haze(),m.separation(),m.vignette(),1f/width,1f/height,0},source,false);
        }
        if(view==null){Fonts.drawCentered(ctx,"Choose an image to preview",x+w/2,y+h/2,0xFFACB8C9,false,.7f);return;}
        var pose=new Matrix3x2f(ctx.getMatrices());var area=new ScreenRect((int)x,(int)y,Math.max(1,(int)w),Math.max(1,(int)h)).transformEachVertex(pose);var clip=Render2D.getScissor();var visible=clip==null?area:area.intersection(clip);if(visible==null)return;
        int tint=dev.maro.util.ColorUtil.mulAlpha(-1,Render2D.getAlpha());
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(pose,new float[]{x,y,0,1,x,y+h,0,0,x+w,y+h,1,0,x+w,y,1,1},new int[]{tint,tint,tint,tint},RenderPipelines.GUI_TEXTURED,TextureSetup.of(view,RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),visible,clip));
    }
    public static void release(){SKY.close();GEEK.close();if(placeholder!=null){placeholder.close();placeholder=null;}}
    private static final class Pass {
        final RenderPipeline pipeline;final String uniform,sampler;final java.nio.ByteBuffer data;SimpleFramebuffer target;GpuBuffer buffer,vertices,indices;
        Pass(String vertex,String fragment,String uniform,String sampler,int bytes){this.uniform=uniform;this.sampler=sampler;data=BufferUtils.createByteBuffer(bytes);pipeline=RenderPipeline.builder().withLocation(Identifier.of("maro","pipeline/preview_"+fragment)).withVertexShader(Identifier.of("maro","core/"+vertex)).withFragmentShader(Identifier.of("maro","core/"+fragment)).withSampler(sampler).withUniform(uniform,UniformType.UNIFORM_BUFFER).withVertexFormat(VertexFormats.POSITION,VertexFormat.DrawMode.TRIANGLES).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build();}
        GpuTextureView draw(int w,int h,float[] values,GpuTextureView source,boolean wraps){
            if(target==null)target=new SimpleFramebuffer("Maro shader preview",w,h,false);else if(target.textureWidth!=w||target.textureHeight!=h)target.resize(w,h);
            var device=RenderSystem.getDevice();
            if(buffer==null){buffer=device.createBuffer(()->"Maro shader preview data",GpuBuffer.USAGE_UNIFORM|GpuBuffer.USAGE_COPY_DST,data.capacity());var v=BufferUtils.createByteBuffer(48);for(float[] p:new float[][]{{-1,-1},{-1,1},{1,1},{1,-1}})v.putFloat(p[0]).putFloat(p[1]).putFloat(0);v.flip();var i=BufferUtils.createByteBuffer(24);for(int index:new int[]{0,1,2,0,2,3})i.putInt(index);i.flip();vertices=device.createBuffer(()->"Maro shader preview quad",GpuBuffer.USAGE_VERTEX,v);indices=device.createBuffer(()->"Maro shader preview indices",GpuBuffer.USAGE_INDEX,i);}
            data.clear();for(float v:values)data.putFloat(v);data.flip();var encoder=device.createCommandEncoder();encoder.writeToBuffer(buffer.slice(),data);
            var filter=RenderSystem.getSamplerCache().get(wraps?AddressMode.REPEAT:AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.LINEAR,FilterMode.LINEAR,false);
            try(var pass=encoder.createRenderPass(()->"Maro isolated shader preview",target.getColorAttachmentView(),OptionalInt.of(0))){pass.setPipeline(pipeline);pass.setUniform(uniform,buffer.slice());pass.bindTexture(sampler,source,filter);pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(indices,VertexFormat.IndexType.INT);pass.drawIndexed(0,0,6,1);}
            return target.getColorAttachmentView();
        }
        void close(){if(target!=null){target.delete();target=null;}if(buffer!=null){buffer.close();buffer=null;}if(vertices!=null){vertices.close();vertices=null;}if(indices!=null){indices.close();indices=null;}}
    }
}
