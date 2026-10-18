package dev.maro.gui.render;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import java.util.Random;

/** Procedural nebula and a bounded star batch; no downloaded image or world renderer. */
final class MenuGalaxy {
    private static Texture nebula;
    private static final float[][] STARS=new float[460][5];
    static {var r=new Random(72015);for(float[] star:STARS)for(int i=0;i<5;i++)star[i]=r.nextFloat();}
    private MenuGalaxy(){}
    static void draw(DrawContext ctx,int w,int h,double time,double angle){
        if(nebula==null)createNebula();
        float du=(float)Math.sin(angle)*.012f,dv=(float)Math.cos(angle*.7)*.008f;
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(new Matrix3x2f(ctx.getMatrices()),
            new float[]{0,0,.02f+du,.02f+dv,0,h,.02f+du,.98f+dv,w,h,.98f+du,.98f+dv,w,0,.98f+du,.02f+dv},
            new int[]{-1,-1,-1,-1},RenderPipelines.GUI_TEXTURED,TextureSetup.of(nebula.getGlTextureView(),nebula.getSampler()),new ScreenRect(0,0,w,h)));
        ctx.createNewRootLayer();
        var stars=new ShapeRenderState.Builder(ctx,STARS.length*8);float scale=Math.min(w/960f,h/540f);
        for(float[] star:STARS){
            double moving=star[0]+angle*.022;float x=(float)(moving-Math.floor(moving))*w,y=star[1]*h;
            float size=(star[2]>.94f?1.1f:.45f+star[2]*.35f)*scale;
            int alpha=Math.round((.6f+.4f*(float)Math.sin(time*(.45+star[3]) +star[4]*40))*(90+star[2]*155));
            int color=(alpha<<24)|(star[3]>.75f?0xCBDDFF:0xF4EDFF);
            stars.quad(x-size,y-size,color,x-size,y+size,color,x+size,y+size,color,x+size,y-size,color);
            if(star[2]>.98f){int soft=(Math.min(120,alpha)<<24)|0xDDEBFF;stars.quad(x-size*3,y-size*.25f,soft,x-size*3,y+size*.25f,soft,x+size*3,y+size*.25f,soft,x+size*3,y-size*.25f,soft);}
        }
        var batch=stars.build(null);if(batch!=null)((DrawContextAccessor)ctx).maro$getState().addSimpleElement(batch);
        // A brief, quiet meteor every 16 seconds, away from the left-hand navigation.
        double meteor=time%16;if(meteor<1.1){float p=(float)(meteor/1.1),x=w*(.52f+p*.28f),y=h*(.04f+p*.22f);int a=Math.round((float)Math.sin(p*Math.PI)*140);Render2D.line(ctx,x,y,x-w*.045f,y-h*.035f,Math.max(.4f,scale*.7f),(a<<24)|0xD5E7FF);}
    }
    private static void createNebula(){
        int w=384,h=216;byte[] pixels=new byte[w*h*4];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double px=(x/(double)w-.65)*1.65,py=(y/(double)h-.32)*1.25;
            double radius=Math.sqrt(px*px+py*py),a=Math.atan2(py,px),cloud=0,weight=.55;
            for(int o=0;o<5;o++){double f=9*(1<<o);cloud+=noise(px*f,py*f)*weight;weight*=.5;}
            double arms=Math.pow((Math.cos(a*2+Math.log(radius+.035)*3.2)+1)*.5,3);
            double dust=Math.max(0,cloud-.27)*Math.exp(-radius*2.3)*( .25+arms*1.6);
            double core=Math.exp(-radius*20)*.32,blue=Math.max(0,noise(px*5+72,py*5)-.4)*.15;
            int i=(y*w+x)*4;
            pixels[i]=(byte)Math.min(255,5+135*dust+150*core);
            pixels[i+1]=(byte)Math.min(255,9+55*dust+160*core+90*blue);
            pixels[i+2]=(byte)Math.min(255,23+190*dust+190*core+190*blue);pixels[i+3]=(byte)255;
        }
        nebula=new Texture(w,h,TextureFormat.RGBA8,FilterMode.LINEAR,FilterMode.LINEAR);nebula.upload(pixels);
    }
    private static double noise(double x,double y){int ix=(int)Math.floor(x),iy=(int)Math.floor(y);double fx=x-ix,fy=y-iy;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);return (hash(ix,iy)*(1-fx)+hash(ix+1,iy)*fx)*(1-fy)+(hash(ix,iy+1)*(1-fx)+hash(ix+1,iy+1)*fx)*fy;}
    private static double hash(int x,int y){int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0x7FFFFFFF)/(double)0x7FFFFFFF;}
}
