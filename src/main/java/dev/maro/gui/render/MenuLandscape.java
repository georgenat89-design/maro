package dev.maro.gui.render;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

/** A bounded, decorative voxel scene. Never creates a world, connection, or player. */
public final class MenuLandscape {
    private static final int SIZE=32, SLOT=34, COLS=8;
    private static final String[] SPRITES={"grass_block_top","grass_block_side","dirt","sand","stone",
        "oak_log","oak_log_top","oak_leaves","birch_log","birch_log_top","birch_leaves",
        "cherry_log","cherry_log_top","cherry_leaves","spruce_log","spruce_log_top","spruce_leaves",
        "water_still","lily_pad","poppy","dandelion","short_grass"};
    private static final int ATLAS_W=COLS*SLOT, ATLAS_H=((SPRITES.length+COLS-1)/COLS)*SLOT;
    private record Block(int x,int y,int z,int top,int side,int tint){}
    private static final class Face {
        final float[] xyz; final int sprite,normal,tint; float depth;
        Face(float[] xyz,int sprite,int normal,int tint){this.xyz=xyz;this.sprite=sprite;this.normal=normal;this.tint=tint;}
    }
    private static Texture atlas;
    private static ResourceManager resources;
    private static boolean atlasFailed;
    private static volatile boolean reloadPending;
    private static byte[] atlasPixels;
    private static BufferedImage waterSheet;
    private static int waterFrame;
    private static double waterAt;
    private static final List<Face> faces=new ArrayList<>();
    private static String trees="";
    private static double angle=.72, drift, last;
    private static float pitch=.7854f,basePitch=.7854f,zoomFactor=1;
    private static long sampled;
    private static float[] mesh=new float[0];
    private static int[] ink=new int[0];
    private static int meshW,meshH;
    private static double meshAngle=-1,meshDrift=-1;
    private static float meshPitch=-1,meshZoom=-1;
    private static int lightKey=-1;
    private static long buildNanos, buildCount;
    private MenuLandscape(){}

    /** Called on the client thread after resource reload and before subsequent scene submission. */
    public static void invalidate(){reloadPending=true;}
    public static double cameraAngle(){return angle;}
    public static float cameraPitch(){return pitch;}
    public static float cameraZoom(){return zoomFactor;}
    public static int faceCount(){return faces.size();}
    public static double averageBuildMillis(){return buildCount==0?0:buildNanos/1e6/buildCount;}

    public static void draw(DrawContext ctx,int w,int h){
        double now=System.nanoTime()/1e9,dt=last==0?0:Math.min(.1,now-last);last=now;
        // A long visit to another screen must not jump the camera on return.
        if(!ClientSettings.sceneCamera.is("Paused")){
            if(ClientSettings.sceneCamera.is("Orbit"))angle+=dt*.018*ClientSettings.orbitSpeed.getFloat()/100*(ClientSettings.orbitDirection.is("Clockwise")?1:-1);
            else drift+=dt*.18;
        }
        float k=1-(float)Math.exp(-10*dt),targetPitch=(float)Math.toRadians(ClientSettings.orbitAngle.getFloat()),targetZoom=ClientSettings.sceneZoom.getFloat()/100;
        basePitch+= (targetPitch-basePitch)*k;zoomFactor+=(targetZoom-zoomFactor)*k;
        if(Math.abs(targetPitch-basePitch)<.0001f)basePitch=targetPitch;if(Math.abs(targetZoom-zoomFactor)<.0001f)zoomFactor=targetZoom;
        pitch=basePitch+(float)Math.sin(drift)*.025f;
        boolean galaxy=ClientSettings.sceneBackdrop.is("Galaxy");String keyTrees=ClientSettings.sceneTrees.get()+"/"+galaxy;
        if(!trees.equals(keyTrees)){trees=keyTrees;generate(ClientSettings.sceneTrees.get(),galaxy);sampled=0;}
        float phase=phase();int key=Math.round(phase*1000);
        int[] light=lighting(phase);
        if(galaxy){MenuGalaxy.draw(ctx,w,h,now,angle);light[2]=mix(light[2],0xFFD4DBF6,.3f);}
        else Render2D.rectGradient(ctx,0,0,w,h,light[0],light[0],light[1],light[1]);
        if(!ensureAtlas())return;
        animateWater(now);
        boolean moved=Math.abs(angle-meshAngle)>1e-6||Math.abs(drift-meshDrift)>1e-6||Math.abs(pitch-meshPitch)>1e-6||Math.abs(zoomFactor-meshZoom)>1e-6;
        if(sampled==0||meshW!=w||meshH!=h||key!=lightKey||moved&&System.nanoTime()-sampled>=16_000_000L){
            long start=System.nanoTime();project(w,h,light[2]);buildNanos+=System.nanoTime()-start;buildCount++;
            meshW=w;meshH=h;meshAngle=angle;meshDrift=drift;meshPitch=pitch;meshZoom=zoomFactor;lightKey=key;sampled=System.nanoTime();
        }
        ctx.createNewRootLayer();
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(new Matrix3x2f(ctx.getMatrices()),mesh,ink,
            RenderPipelines.GUI_TEXTURED,TextureSetup.of(atlas.getGlTextureView(),atlas.getSampler()),new ScreenRect(0,0,w,h)));
        ctx.createNewRootLayer();
        // Keep the scenery visible while giving small foreground text a stable contrast floor.
        Render2D.rectGradient(ctx,0,0,w,h,0x30090D18,0x08090D18,0x40090D18,0x70090D18);
        Render2D.rectGradient(ctx,0,0,w*.42f,h,0xA30A101B,0x000A101B,0x000A101B,0xB80A101B);
    }
    private static float phase(){
        return switch(ClientSettings.sceneTime.get()){
            case "Dawn"->0;case "Day"->1;case "Night"->3;
            case "Auto"->{var t=java.time.LocalTime.now();float hour=t.getHour()+t.getMinute()/60f;yield hour<6?3:hour<9?(hour-6)/3:hour<16?1:hour<20?1+(hour-16)/4:2+(hour-20)/4;}
            default->2;
        };
    }
    private static int[] lighting(float phase){
        int[][] palettes={{0xFFB8889F,0xFFECC39A,0xFFEECDAC},{0xFF91C9E7,0xFFD6E8DD,0xFFFFFFFF},
            {0xFF84789D,0xFFF2BD8A,0xFFE8E1EC},{0xFF111D3C,0xFF405379,0xFF697CA9}};
        int a=(int)Math.floor(phase)%4,b=(a+1)%4;float f=phase-(float)Math.floor(phase);
        return new int[]{mix(palettes[a][0],palettes[b][0],f),mix(palettes[a][1],palettes[b][1],f),mix(palettes[a][2],palettes[b][2],f)};
    }
    private static int mix(int a,int b,float f){int c=0xFF000000;for(int s:new int[]{16,8,0})c|=Math.round((a>>s&255)*(1-f)+(b>>s&255)*f)<<s;return c;}
    private static int tint(int a,int b,float shade){return 0xFF000000|Math.min(255,Math.round((a>>16&255)*(b>>16&255)/255f*shade))<<16|Math.min(255,Math.round((a>>8&255)*(b>>8&255)/255f*shade))<<8|Math.min(255,Math.round((a&255)*(b&255)/255f*shade));}
    private static long pos(int x,int y,int z){return (long)(x+128)<<24|(long)(y+128)<<12|(z+128);}
    private static double bank(int x,int z){return Math.abs(x-4*Math.sin(z*.115)-2*Math.cos(z*.21));}
    private static int elevation(int x,int z){double d=bank(x,z);return d<4.2?1:d<6?3:4+(int)Math.max(0,2.5*Math.sin(x*.17+z*.06)*Math.cos(z*.14));}
    private static void generate(String kind,boolean galaxy){
        Map<Long,Block> blocks=new HashMap<>();Random random=new Random(91835);
        for(int x=-38;x<=38;x++)for(int z=-38;z<=38;z++){
            double radius=Math.hypot(x*.9,z),edge=15.5+Math.sin(Math.atan2(z,x)*5)*1.1;
            if(galaxy&&radius>edge)continue;
            int top=elevation(x,z);double d=bank(x,z);
            int floor=galaxy?-1-(int)(6*Math.max(0,1-radius/17)):0;
            for(int y=floor;y<=top;y++){
                int texture=y<0?4:y<top?2:d<6?3:0;
                blocks.put(pos(x,y,z),new Block(x,y,z,texture,texture==0?1:texture,0xFFFFFFFF));
            }
            if(d<4.2){blocks.put(pos(x,3,z),new Block(x,3,z,17,17,0xFFFFFFFF));
                if(d>1.4&&random.nextFloat()<.035)facesLily(blocks,x,z);}
        }
        List<int[]> planted=new ArrayList<>();
        for(int i=0;i<240;i++){
            if(planted.size()>=40)break;
            int x=random.nextInt(65)-32,z=random.nextInt(65)-32;
            if(galaxy&&!blocks.containsKey(pos(x,elevation(x,z),z)))continue;
            if(bank(x,z)<7||planted.stream().anyMatch(p->Math.hypot(p[0]-x,p[1]-z)<6.5))continue;
            planted.add(new int[]{x,z});String species=kind.equals("Mixed")?new String[]{"Oak","Birch","Cherry","Spruce"}[random.nextInt(4)]:kind;
            tree(blocks,x,elevation(x,z)+1,z,species,random);
        }
        faces.clear();
        for(Block b:blocks.values()){
            boolean foliage=b.top==7||b.top==10||b.top==13||b.top==16;
            for(int n=0;n<5;n++){
                int dx=n==1?1:n==2?-1:0,dz=n==3?1:n==4?-1:0,dy=n==0?1:0;
                Block neighbour=blocks.get(pos(b.x+dx,b.y+dy,b.z+dz));
                if(neighbour!=null&&!(neighbour.top==17&&b.top!=17)&&!(neighbour.top==18&&n==0))continue;
                // Water is a flat translucent-looking surface; its bed is shaded into the sprite.
                if((b.top==17||b.top==18)&&n!=0)continue;
                float y=b.y+(b.top==17?.86f:b.top==18?.90f:1);
                float x=b.x,z=b.z,y0=b.y;
                float[] v=switch(n){
                    case 0->new float[]{x,y,z,x,y,z+1,x+1,y,z+1,x+1,y,z};
                    case 1->new float[]{x+1,y,z,x+1,y,z+1,x+1,y0,z+1,x+1,y0,z};
                    case 2->new float[]{x,y,z+1,x,y,z,x,y0,z,x,y0,z+1};
                    case 3->new float[]{x+1,y,z+1,x,y,z+1,x,y0,z+1,x+1,y0,z+1};
                    default->new float[]{x,y,z,x+1,y,z,x+1,y0,z,x,y0,z};
                };
                int variation=foliage?0xFFF0F6E8:0xFFFFFFFF;
                faces.add(new Face(v,n==0?b.top:b.side,n,variation));
            }
        }
        // Crossed plants use the same atlas and depth ordering as blocks.
        for(int i=0;i<380;i++){
            int x=random.nextInt(68)-34,z=random.nextInt(68)-34;if(bank(x,z)<6.5||planted.stream().anyMatch(p->Math.hypot(p[0]-x,p[1]-z)<2))continue;
            if(galaxy&&!blocks.containsKey(pos(x,elevation(x,z),z)))continue;
            float y=elevation(x,z)+1;int sprite=i%6==0?19:i%5==0?20:21;
            faces.add(new Face(new float[]{x,y+.8f,z+.5f,x+1,y+.8f,z+.5f,x+1,y,z+.5f,x,y,z+.5f},sprite,5,0xFFFFFFFF));
            faces.add(new Face(new float[]{x+.5f,y+.8f,z,x+.5f,y+.8f,z+1,x+.5f,y,z+1,x+.5f,y,z},sprite,5,0xFFFFFFFF));
        }
    }
    private static void facesLily(Map<Long,Block> blocks,int x,int z){blocks.put(pos(x,4,z),new Block(x,3,z,18,18,0xFFFFFFFF));}
    private static void tree(Map<Long,Block> map,int x,int y,int z,String kind,Random r){
        int type=switch(kind){case "Birch"->1;case "Cherry"->2;case "Spruce"->3;default->0;};
        int h=type==3?7:4+r.nextInt(3),log=5+type*3,leaf=log+2;
        for(int k=0;k<h;k++)map.put(pos(x,y+k,z),new Block(x,y+k,z,log+1,log,0xFFFFFFFF));
        int lower=type==3?2:h-3;
        for(int k=lower;k<=h+1;k++){
            int radius=type==3?Math.max(0,(h+1-k)/2):k>=h?1:2;
            if(type==2&&k<h)radius=3;
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.abs(dx)==radius&&Math.abs(dz)==radius&&r.nextBoolean())continue;
                map.putIfAbsent(pos(x+dx,y+k,z+dz),new Block(x+dx,y+k,z+dz,leaf,leaf,0xFFFFFFFF));
            }
        }
    }
    private static void project(int w,int h,int ambient){
        double c=Math.cos(angle),s=Math.sin(angle),cp=Math.cos(pitch),sp=Math.sin(pitch);
        float zoom=h/(ClientSettings.sceneBackdrop.is("Galaxy")?40f:26f)*zoomFactor;List<Face> visible=new ArrayList<>(faces.size());
        for(Face f:faces){
            if(f.normal==1&&s<=0||f.normal==2&&s>=0||f.normal==3&&c<=0||f.normal==4&&c>=0)continue;
            float[] v=f.xyz;double q=0;for(int i=0;i<4;i++)q+=(v[i*3]*s+v[i*3+2]*c)*cp+v[i*3+1]*sp;
            f.depth=(float)(q/4);visible.add(f);
        }
        visible.sort(Comparator.comparingDouble(f->f.depth));
        float[] vertices=new float[visible.size()*16];int[] colors=new int[visible.size()*4];int count=0;
        for(Face f:visible){
            float[] screen=new float[8];float minX=Float.MAX_VALUE,minY=minX,maxX=-minX,maxY=-minX;
            for(int i=0;i<4;i++){
                float x=f.xyz[i*3],y=f.xyz[i*3+1]-3,z=f.xyz[i*3+2];
                float px=w*.53f+(float)(x*c-z*s)*zoom+(float)Math.sin(drift)*h*.02f,py=h*.54f+(float)((x*s+z*c)*sp-y*cp)*zoom;
                screen[i*2]=px;screen[i*2+1]=py;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);
            }
            if(maxX<0||minX>w||maxY<0||minY>h)continue;
            float area=0;for(int i=0;i<4;i++){int j=(i+1)%4;area+=screen[i*2]*screen[j*2+1]-screen[j*2]*screen[i*2+1];}
            float shade=switch(f.normal){case 1,2->.75f;case 3,4->.88f;default->1;};
            int color=tint(f.tint,ambient,shade);float u=(f.sprite%COLS*SLOT+1f)/ATLAS_W,v=(f.sprite/COLS*SLOT+1f)/ATLAS_H,du=(float)SIZE/ATLAS_W,dv=(float)SIZE/ATLAS_H;
            for(int i=0;i<4;i++){
                int k=area>0?(4-i)%4:i,offset=count*4;
                vertices[offset]=screen[k*2];vertices[offset+1]=screen[k*2+1];vertices[offset+2]=u+(k>=2?du:0);vertices[offset+3]=v+(k==1||k==2?dv:0);colors[count++]=color;
            }
        }
        mesh=Arrays.copyOf(vertices,count*4);ink=Arrays.copyOf(colors,count);
    }
    private static boolean ensureAtlas(){
        if(reloadPending){if(atlas!=null)atlas.close();atlas=null;resources=null;atlasFailed=false;sampled=0;reloadPending=false;}
        ResourceManager manager=MinecraftClient.getInstance().getResourceManager();
        if(atlas!=null&&resources==manager)return true;if(atlasFailed)return false;
        try{
            byte[] rgba=new byte[ATLAS_W*ATLAS_H*4];
            for(int i=0;i<SPRITES.length;i++){
                BufferedImage image=read(manager,SPRITES[i]);BufferedImage overlay=i==1?read(manager,"grass_block_side_overlay"):null;
                if(i==17)waterSheet=image;
                int color=switch(i){case 0,21->0xFF88B34A;case 7->0xFF75A349;case 10->0xFF86AD57;case 16->0xFF5D8D65;case 17->0xFF79B7EF;case 18->0xFF4B7836;default->0xFFFFFFFF;};
                for(int y=-1;y<=SIZE;y++)for(int x=-1;x<=SIZE;x++){
                    int sx=Math.max(0,Math.min(SIZE-1,x))*image.getWidth()/SIZE,sy=Math.max(0,Math.min(SIZE-1,y))*Math.min(image.getWidth(),image.getHeight())/SIZE;
                    int p=image.getRGB(sx,sy),alpha=p>>>24;p=(alpha<<24)|(tint(p,color,1)&0xFFFFFF);
                    // Opaque leaf backs avoid distant alpha noise and expose no unrendered interior.
                    if((i==7||i==10||i==13||i==16)&&alpha<255){int back=tint(i==13?0xFFA97F91:color,0xFFFFFFFF,.52f);p=mix(back,p,alpha/255f);}
                    if(overlay!=null){int o=overlay.getRGB(sx*overlay.getWidth()/image.getWidth(),sy*overlay.getWidth()/image.getWidth()),a=o>>>24;if(a>0){int green=tint(o,0xFF88B34A,1);p=mix(p,green,a/255f);}}
                    // Water's original greyscale animation is colored and kept opaque for stable sorting.
                    if(i==17)p=0xFF000000|(p&0xFFFFFF);
                    int offset=((i/COLS*SLOT+y+1)*ATLAS_W+i%COLS*SLOT+x+1)*4;
                    rgba[offset]=(byte)(p>>16);rgba[offset+1]=(byte)(p>>8);rgba[offset+2]=(byte)p;rgba[offset+3]=(byte)(p>>>24);
                }
            }
            atlas=new Texture(ATLAS_W,ATLAS_H,TextureFormat.RGBA8,FilterMode.NEAREST,FilterMode.NEAREST);atlas.upload(rgba);atlasPixels=rgba;waterAt=0;waterFrame=0;resources=manager;return true;
        }catch(IOException|RuntimeException error){atlasFailed=true;Maro.LOGGER.warn("Menu scene textures unavailable; using the plain background",error);return false;}
    }
    private static void animateWater(double now){
        if(waterSheet==null||now-waterAt<.125)return;waterAt=now;
        int frames=waterSheet.getHeight()/waterSheet.getWidth();if(frames<2)return;
        waterFrame=(waterFrame+1)%frames;int size=waterSheet.getWidth();
        for(int y=-1;y<=SIZE;y++)for(int x=-1;x<=SIZE;x++){
            int sx=Math.max(0,Math.min(SIZE-1,x))*size/SIZE,sy=waterFrame*size+Math.max(0,Math.min(SIZE-1,y))*size/SIZE;
            int p=tint(waterSheet.getRGB(sx,sy),0xFF79B7EF,1),offset=((17/COLS*SLOT+y+1)*ATLAS_W+17%COLS*SLOT+x+1)*4;
            atlasPixels[offset]=(byte)(p>>16);atlasPixels[offset+1]=(byte)(p>>8);atlasPixels[offset+2]=(byte)p;atlasPixels[offset+3]=(byte)255;
        }
        atlas.upload(atlasPixels);
    }
    private static BufferedImage read(ResourceManager manager,String name)throws IOException{
        try(var stream=manager.getResourceOrThrow(Identifier.ofVanilla("textures/block/"+name+".png")).getInputStream()){
            BufferedImage image=ImageIO.read(stream);if(image==null)throw new IOException("Unreadable block texture: "+name);return image;
        }
    }
}
