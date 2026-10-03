package dev.maro.runtime.renderer;

import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gl.GpuSampler;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.systems.RenderSystem;
/** GPU resource owned by one HUD, with one-byte font coverage expanded to RGBA. */
public final class Texture implements AutoCloseable {
 private static final List<NativeImageBackedTexture> RETIRED=new ArrayList<>();
 private final NativeImageBackedTexture texture;private final TextureFormat format;private final int width,height;private final GpuSampler sampler;
 private boolean closed;
 public Texture(int width,int height,TextureFormat format,FilterMode min,FilterMode mag){
  this.width=width;this.height=height;this.format=format;
  texture=new NativeImageBackedTexture("maro-hud",width,height,false);
  sampler=RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,min,mag,false);
 }
 public void upload(byte[] bytes){upload(ByteBuffer.wrap(bytes));}
 public void upload(ByteBuffer buffer){
  ByteBuffer bytes=buffer.duplicate();NativeImage image=texture.getImage();
  for(int y=0;y<height;y++)for(int x=0;x<width;x++){
   int r=255,g=255,b=255,a;
   if(format==TextureFormat.RED8)a=bytes.get()&255;
   else{r=bytes.get()&255;g=bytes.get()&255;b=bytes.get()&255;a=bytes.get()&255;}
   image.setColorArgb(x,y,(a<<24)|(r<<16)|(g<<8)|b);
  }
  texture.upload();
 }
 public GpuTextureView getGlTextureView(){return texture.getGlTextureView();}
 public GpuSampler getSampler(){return sampler;}
 @Override public void close(){if(!closed){closed=true;RETIRED.add(texture);}}
 /** Queued GUI batches may still reference a just-retired atlas until the frame finishes. */
 public static void endFrame(){RETIRED.forEach(NativeImageBackedTexture::close);RETIRED.clear();}
}
