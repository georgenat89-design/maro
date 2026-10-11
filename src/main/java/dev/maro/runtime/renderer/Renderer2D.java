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
  triangles.ensureQuadCapacity();
  int a=texVertex(x,y,cx,cy,cos,sin,u0,v0,color);
  int b=texVertex(x,y+h,cx,cy,cos,sin,u0,v1,color);
  int c=texVertex(x+w,y+h,cx,cy,cos,sin,u1,v1,color);
  int d=texVertex(x+w,y,cx,cy,cos,sin,u1,v0,color);
  triangles.quad(a,b,c,d);
 }
 private int texVertex(double x,double y,double cx,double cy,double cos,double sin,double u,double v,Color color){
  double dx=x-cx,dy=y-cy;
  return triangles.vec2(cx+dx*cos-dy*sin,cy+dx*sin+dy*cos).vec2(u,v).color(color).next();
 }
}
