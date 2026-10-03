package dev.maro.runtime.renderer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.gl.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.maro.runtime.utils.render.color.Color;
/** Physical-pixel batches submitted to Maro's vanilla GUI pipeline. */
public final class Renderer2D {
 private static DrawContext context;
 public static final Renderer2D COLOR=new Renderer2D(), TEXTURE=new Renderer2D();
 public final MeshBuilder triangles=new MeshBuilder(null);
 public static DrawContext context(){return context;}
 public static void context(DrawContext next){context=next;}
 public void begin(){triangles.begin();}
 public void render(){triangles.end();triangles.submit(TextureSetup.empty(),null);}
 public void render(GpuTextureView texture,GpuSampler sampler){triangles.end();triangles.submit(TextureSetup.of(texture,sampler),null);}
 public void texQuad(double x,double y,double w,double h,Color color){texQuad(x,y,w,h,0,0,1,1,color);}
 public void texQuad(double x,double y,double w,double h,double u0,double v0,double u1,double v1,Color color){texQuad(x,y,w,h,0,u0,v0,u1,v1,color);}
 public void texQuad(double x,double y,double w,double h,double degrees,double u0,double v0,double u1,double v1,Color color){
  double cx=x+w/2,cy=y+h/2,cos=Math.cos(Math.toRadians(degrees)),sin=Math.sin(Math.toRadians(degrees));
  double[] px={x,x,x+w,x+w},py={y,y+h,y+h,y},us={u0,u0,u1,u1},vs={v0,v1,v1,v0};int[] ids=new int[4];
  for(int i=0;i<4;i++){double dx=px[i]-cx,dy=py[i]-cy;ids[i]=triangles.vec2(cx+dx*cos-dy*sin,cy+dx*sin+dy*cos).vec2(us[i],vs[i]).color(color).next();}
  triangles.quad(ids[0],ids[1],ids[2],ids[3]);
 }
}
