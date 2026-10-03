package dev.maro.runtime.renderer;

import java.util.*;
import dev.maro.runtime.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;
import org.joml.Vector2f;
public final class MeshBuilder {
 private record Vertex(double x,double y,double u,double v,int color){}
 private final List<Vertex> vertices=new ArrayList<>(), output=new ArrayList<>();
 private double x,y,u,v; private int color=0xFFFFFFFF; private int vectors; private boolean building;
 public MeshBuilder(Object pipeline){}
 public void begin(){vertices.clear();output.clear();vectors=0;building=true;}
 public void end(){building=false;}
 public boolean isBuilding(){return building;}
 public void ensureCapacity(int vertices,int indices){}
 public void ensureTriCapacity(){}
 public void ensureQuadCapacity(){}
 public MeshBuilder vec2(double a,double b){if(vectors++==0){x=a;y=b;}else{u=a;v=b;}return this;}
 public MeshBuilder color(Color color){this.color=color.getPacked();return this;}
 public int next(){vertices.add(new Vertex(x,y,u,v,color));vectors=0;u=v=0;return vertices.size()-1;}
 public void quad(int a,int b,int c,int d){
  var va=vertices.get(a);var vb=vertices.get(b);var vc=vertices.get(c);var vd=vertices.get(d);
  double area=va.x*vb.y-vb.x*va.y+vb.x*vc.y-vc.x*vb.y+vc.x*vd.y-vd.x*vc.y+vd.x*va.y-va.x*vd.y;
  if(area>0){output.add(va);output.add(vd);output.add(vc);output.add(vb);}else{output.add(va);output.add(vb);output.add(vc);output.add(vd);}
 }
 public void triangle(int a,int b,int c){quad(a,b,c,c);}
 public void submit(TextureSetup texture,Matrix4f transform){
  DrawContext context=Renderer2D.context();if(context==null || output.isEmpty())return;
  double factor=MinecraftClient.getInstance().getWindow().getScaleFactor();
  float[] xyuv=new float[output.size()*4];int[] colors=new int[output.size()];
  float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
  for(int i=0;i<output.size();i++){
   var v=output.get(i);var p=new Vector4f((float)v.x,(float)v.y,0,1);if(transform!=null)transform.transform(p);
   float px=(float)(p.x/factor),py=(float)(p.y/factor);
   xyuv[i*4]=px;xyuv[i*4+1]=py;xyuv[i*4+2]=(float)v.u;xyuv[i*4+3]=(float)v.v;colors[i]=v.color;
   var screen=new Vector2f(px,py);context.getMatrices().transformPosition(screen);
   minX=Math.min(minX,screen.x);minY=Math.min(minY,screen.y);maxX=Math.max(maxX,screen.x);maxY=Math.max(maxY,screen.y);
  }
  if(!Float.isFinite(minX) || maxX<minX)return;
  var bounds=new ScreenRect((int)Math.floor(minX),(int)Math.floor(minY),Math.max(1,(int)Math.ceil(maxX)-(int)Math.floor(minX)),Math.max(1,(int)Math.ceil(maxY)-(int)Math.floor(minY)));
  var state=new GuiMeshState(new Matrix3x2f(context.getMatrices()),xyuv,colors,texture.texure0()==null?RenderPipelines.GUI:RenderPipelines.GUI_TEXTURED,texture,bounds);
  context.createNewRootLayer();
  ((dev.maro.mixin.DrawContextAccessor)context).maro$getState().addSimpleElement(state);
 }
}
