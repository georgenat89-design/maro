package dev.maro.gui.spotify;

import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import java.util.Arrays;

/** A fixed rounded viewport: the images slide beneath it, with no moving corner masks. */
final class PhoneAlbumMesh {
    static final int X=30,Y=100,SIZE=148,RADIUS=10;
    private record Polygon(float[] xy,int count){}
    private record Mesh(float[] vertices,int[] colors){}
    private static final int[][] WHITE=new int[129][];
    private static final Polygon RIM=rim();
    private static final Mesh IDLE=mesh(RIM,0);
    private static Polygon rim(){
        float[] xy=new float[56];int at=0;
        float[] cx={X+RADIUS,X+RADIUS,X+SIZE-RADIUS,X+SIZE-RADIUS},cy={Y+RADIUS,Y+SIZE-RADIUS,Y+SIZE-RADIUS,Y+RADIUS};
        for(int corner=0;corner<4;corner++)for(int step=0;step<=6;step++){
            double angle=(1.5-corner*.5-step/12d)*Math.PI;
            xy[at++]=cx[corner]+RADIUS*(float)Math.cos(angle);xy[at++]=cy[corner]+RADIUS*(float)Math.sin(angle);
        }
        return new Polygon(xy,28);
    }
    private static Polygon clip(Polygon source,float edge,boolean greater){
        float[] out=new float[(source.count+2)*2];int n=0;
        for(int i=0;i<source.count;i++){
            int prev=(i+source.count-1)%source.count;float ax=source.xy[prev*2],ay=source.xy[prev*2+1],bx=source.xy[i*2],by=source.xy[i*2+1];
            boolean a=greater?ax>=edge:ax<=edge,b=greater?bx>=edge:bx<=edge;
            if(a!=b){float t=(edge-ax)/(bx-ax);out[n++]=edge;out[n++]=ay+(by-ay)*t;}
            if(b){out[n++]=bx;out[n++]=by;}
        }
        return new Polygon(out,n/2);
    }
    private static Mesh mesh(Polygon polygon,float dx){
        int n=polygon.count;if(n<3)return null;
        float cx=0,cy=0;for(int i=0;i<n;i++){cx+=polygon.xy[i*2];cy+=polygon.xy[i*2+1];}cx/=n;cy/=n;
        float[] data=new float[n*16];int at=0;
        for(int i=0;i<n;i++){
            int next=(i+1)%n;
            at=vertex(data,at,cx,cy,dx);at=vertex(data,at,polygon.xy[i*2],polygon.xy[i*2+1],dx);
            at=vertex(data,at,polygon.xy[next*2],polygon.xy[next*2+1],dx);at=vertex(data,at,polygon.xy[next*2],polygon.xy[next*2+1],dx);
        }
        if(WHITE[n*4]==null){WHITE[n*4]=new int[n*4];Arrays.fill(WHITE[n*4],0xFFFFFFFF);}
        return new Mesh(data,WHITE[n*4]);
    }
    private static int vertex(float[] data,int at,float x,float y,float dx){data[at++]=x;data[at++]=y;data[at++]=(x-X-dx)/SIZE;data[at++]=(y-Y)/SIZE;return at;}
    static void draw(DrawContext ctx,Texture texture,float dx){
        if(dx<=-SIZE||dx>=SIZE)return;
        Mesh mesh=dx==0?IDLE:mesh(clip(clip(RIM,X+dx,true),X+SIZE+dx,false),dx);if(mesh==null)return;
        var pose=new Matrix3x2f(ctx.getMatrices());
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(pose,mesh.vertices,mesh.colors,RenderPipelines.GUI_TEXTURED,
            TextureSetup.of(texture.getGlTextureView(),texture.getSampler()),new ScreenRect(X,Y,SIZE,SIZE).transformEachVertex(pose)));
    }
}
