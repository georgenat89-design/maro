package dev.maro.gui.spotify;

import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import java.util.ArrayList;

/** Cached skin geometry: index, folded fingers, thumb, palm and shaded wrist in one batch. */
final class PhoneSwipeHand {
    private record Mesh(float[] vertices,int[] colors){}
    private static final Mesh LEFT=build(true),RIGHT=build(false);
    private static TextureSetup skinSetup;
    private static Mesh build(boolean left){
        var xy=new ArrayList<Float>();var colors=new ArrayList<Integer>();
        int u=left?36:44,v=left?52:20;
        // Clockwise faces, with separate edge lighting rather than stretched flat skin strips.
        block(xy,colors,-13,60,28,110,u,v,4,12);
        block(xy,colors,-14,31,30,34,u,v+6,4,5);
        for(int i=0;i<3;i++)block(xy,colors,(left?3:-13)+i*8,25+i*3,7,23,u+(i%2),v+8,1,3);
        block(xy,colors,left?14:-24,43,11,24,u,v+8,2,4);
        block(xy,colors,-5,0,11,39,u,v+8,2,4);
        float[] vertices=new float[xy.size()];for(int i=0;i<vertices.length;i++)vertices[i]=xy.get(i);
        int[] tint=colors.stream().mapToInt(Integer::intValue).toArray();return new Mesh(vertices,tint);
    }
    private static void block(ArrayList<Float> xy,ArrayList<Integer> colors,float x,float y,float w,float h,int u,int v,int uw,int vh){
        quad(xy,colors,x+2,y+2,w,h,u,v,uw,vh,0xFF7B858A);
        quad(xy,colors,x,y,w-2,h-2,u,v,uw,vh,0xFFF4F5F5);
        quad(xy,colors,x,y,1.6f,h-2,u,v,1,vh,0xFFFFFFFF);
        quad(xy,colors,x,y,w-2,1.6f,u,v,uw,1,0xFFFFFFFF);
    }
    private static void quad(ArrayList<Float> xy,ArrayList<Integer> colors,float x,float y,float w,float h,int u,int v,int uw,int vh,int color){
        float[] p={x,y,u/64f,v/64f,x,y+h,u/64f,(v+vh)/64f,x+w,y+h,(u+uw)/64f,(v+vh)/64f,x+w,y,(u+uw)/64f,v/64f};
        for(float value:p)xy.add(value);for(int i=0;i<4;i++)colors.add(color);
    }
    static void draw(DrawContext ctx,Identifier skin,boolean left){
        Mesh mesh=left?LEFT:RIGHT;var pose=new Matrix3x2f(ctx.getMatrices());
        var texture=MinecraftClient.getInstance().getTextureManager().getTexture(skin);
        if(skinSetup==null||skinSetup.texure0()!=texture.getGlTextureView())skinSetup=TextureSetup.of(texture.getGlTextureView(),texture.getSampler());
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(pose,mesh.vertices,mesh.colors,
            RenderPipelines.GUI_TEXTURED,skinSetup,
            new ScreenRect(-25,0,55,174).transformEachVertex(pose)));
    }
}
