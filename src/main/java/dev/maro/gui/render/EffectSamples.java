package dev.maro.gui.render;

import dev.maro.module.Module;
import dev.maro.module.impl.visuals.*;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.ColorSetting;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.*;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;

/** Explicit examples for effects whose data normally comes from gameplay. No detections are inserted. */
public final class EffectSamples {
    private EffectSamples(){}
    private static String key(String s){return s.replace("-","").replace(" ","").toLowerCase(java.util.Locale.ROOT);}
    public static double number(Module m,String name,double fallback){for(var s:m.getSettings())if(key(s.getName()).equals(key(name)))try{return s.toJson().getAsDouble();}catch(RuntimeException ignored){}return fallback;}
    public static String mode(Module m,String name,String fallback){for(var s:m.getSettings())if(key(s.getName()).equals(key(name)))try{return s.toJson().getAsString();}catch(RuntimeException ignored){}return fallback;}
    public static boolean flag(Module m,String name,boolean fallback){for(var s:m.getSettings())if(key(s.getName()).equals(key(name)))try{return s.toJson().getAsBoolean();}catch(RuntimeException ignored){}return fallback;}
    public static int color(Module m,String name,int fallback){for(var s:m.getSettings())if(key(s.getName()).equals(key(name))&&s instanceof ColorSetting c)return c.get();return fallback;}
    public static void item(DrawContext c,ItemStack stack,float x,float y,float size){c.getMatrices().pushMatrix();try{c.getMatrices().translate(x,y);c.getMatrices().scale(size/16,size/16);c.drawItem(stack,0,0);}finally{c.getMatrices().popMatrix();}}
    public static void xp(DrawContext c,Module m,float x,float y,float w,float h,double seconds){
        int level=(int)number(m,"level",30);double phase=seconds*number(m,"animation speed",.5),progress=flag(m,"animate bar",false)?phase-Math.floor(phase):number(m,"bar progress",50)/100;
        float scale=Math.min(w/194f,h/40f);c.getMatrices().pushMatrix();try{c.getMatrices().translate(x+(w-182*scale)/2,y+h/2-9*scale);c.getMatrices().scale(scale,scale);var mc=net.minecraft.client.MinecraftClient.getInstance();if(level>0)c.drawCenteredTextWithShadow(mc.textRenderer,Integer.toString(level),91,-12,0xFF80FF20);c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,Identifier.ofVanilla("hud/experience_bar_background"),0,0,182,5);c.enableScissor(0,0,(int)(182*progress),5);try{c.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,Identifier.ofVanilla("hud/experience_bar_progress"),0,0,182,5);}finally{c.disableScissor();}}finally{c.getMatrices().popMatrix();}
    }
    public static void blocks(DrawContext c,FakeBlock m,float x,float y,float w,float h){
        float size=Math.min(48,Math.min(w*.23f,h-26)),cy=y+(h-size)/2;Item a=m.sourceItem(),b=m.replaceItem();
        FakeBlock.withoutSpoof(()->{if(a!=Items.AIR)item(c,new ItemStack(a),x+w*.24f-size/2,cy,size);if(b!=Items.AIR){var stack=new ItemStack(b);if(flag(m,"Enchanted",false))stack.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);item(c,stack,x+w*.76f-size/2,cy,size);}});
        Fonts.drawCentered(c,"→",x+w/2,cy+size/2,Theme.accent(),true,1.4f);
        Fonts.drawCentered(c,Fonts.trim(a==Items.AIR?"Choose source":a.getName().getString(),w*.45f,false,.65f),x+w*.24f,cy+size+12,Theme.TEXT_DIM,false,.65f);
        Fonts.drawCentered(c,Fonts.trim(b==Items.AIR?"Choose replacement":b.getName().getString(),w*.45f,false,.65f),x+w*.76f,cy+size+12,Theme.TEXT_DIM,false,.65f);
    }
    public static void fullbright(DrawContext c,Module m,float x,float y,float w,float h){
        float half=(w-8)/2;
        for(int side=0;side<2;side++){
            float xx=x+side*(half+8),height=Math.max(8,h-16);
            float light=side==0?.10f:(float)(.10+.90*(1-Math.exp(-3*number(m,"Strength",100)/100)));
            cave(c,xx,y,half,height,light);
            Fonts.drawCentered(c,side==0?"Before":"After",xx+half/2,y+h-6,Theme.TEXT_DIM,false,.72f);
        }
    }
    /** A fixed textured cave, so the comparison changes only its illustrative lighting. */
    private static void cave(DrawContext c,float x,float y,float w,float h,float light){
        var pose=new org.joml.Matrix3x2f(c.getMatrices());
        var area=new net.minecraft.client.gui.ScreenRect((int)x,(int)y,Math.max(1,(int)w),Math.max(1,(int)h)).transformEachVertex(pose);
        var parent=Render2D.getScissor();var clip=parent==null?area:parent.intersection(area);
        if(clip==null)return;
        Render2D.rect(c,x,y,w,h,0xFF07090D);
        var stone=new CaveMesh(x+w/2,y+h*.48f,Math.min(w,h)*.9f);
        for(int row=0;row<3;row++)for(int col=-2;col<2;col++){
            float a=row-1.5f;stone.quad(light*.72f,new float[]{col,a,7},new float[]{col+1,a,7},new float[]{col+1,a+1,7},new float[]{col,a+1,7});
        }
        for(int z=6;z>=1;z--){
            float shade=light*(.72f+.28f/z);
            for(int col=-2;col<2;col++){
                stone.quad(shade,new float[]{col,-1.5f,z},new float[]{col+1,-1.5f,z},new float[]{col+1,-1.5f,z+1},new float[]{col,-1.5f,z+1});
                stone.quad(shade*.65f,new float[]{col,1.5f,z},new float[]{col,1.5f,z+1},new float[]{col+1,1.5f,z+1},new float[]{col+1,1.5f,z});
            }
            for(int row=0;row<3;row++)for(int wall:new int[]{-2,2}){
                float a=row-1.5f;
                if(wall<0)stone.quad(shade*.84f,new float[]{wall,a,z},new float[]{wall,a,z+1},new float[]{wall,a+1,z+1},new float[]{wall,a+1,z});
                else stone.quad(shade*.84f,new float[]{wall,a,z},new float[]{wall,a+1,z},new float[]{wall,a+1,z+1},new float[]{wall,a,z+1});
            }
        }
        stone.draw(c,"deepslate",pose,clip);
        var ore=new CaveMesh(x+w/2,y+h*.48f,Math.min(w,h)*.9f);
        ore.quad(light*.72f,new float[]{0,-.5f,7},new float[]{1,-.5f,7},new float[]{1,.5f,7},new float[]{0,.5f,7});
        ore.quad(light*.84f,new float[]{2,-1.5f,3},new float[]{2,-.5f,3},new float[]{2,-.5f,4},new float[]{2,-1.5f,4});
        ore.draw(c,"deepslate_diamond_ore",pose,clip);
        Render2D.roundOutline(c,x,y,w,h,3,Render2D.px(),0x5046576D);
    }
    private static final class CaveMesh {
        private final float[] vertices=new float[2048];private final int[] colors=new int[512];
        private final float cx,cy,focal;private int count;
        CaveMesh(float cx,float cy,float focal){this.cx=cx;this.cy=cy;this.focal=focal;}
        void quad(float light,float[]... points){
            int brightness=Math.clamp(Math.round(255*light),0,255),tint=ColorUtil.argb(Math.round(255*Render2D.getAlpha()),brightness,brightness,brightness);
            for(int i=0;i<4;i++){
                var p=points[i];int at=count*4;
                vertices[at]=cx+p[0]/p[2]*focal;vertices[at+1]=cy-p[1]/p[2]*focal;
                vertices[at+2]=(i==1||i==2)?1:0;vertices[at+3]=i<2?1:0;colors[count++]=tint;
            }
        }
        void draw(DrawContext c,String name,org.joml.Matrix3x2f pose,net.minecraft.client.gui.ScreenRect clip){
            var texture=net.minecraft.client.MinecraftClient.getInstance().getTextureManager().getTexture(Identifier.ofVanilla("textures/block/"+name+".png"));
            ((dev.maro.mixin.DrawContextAccessor)c).maro$getState().addSimpleElement(new dev.maro.runtime.renderer.GuiMeshState(pose,java.util.Arrays.copyOf(vertices,count*4),java.util.Arrays.copyOf(colors,count),net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.client.texture.TextureSetup.of(texture.getGlTextureView(),texture.getSampler()),clip,clip));
        }
    }
    public static void base(DrawContext c,Module m,float x,float y,float w,float h){
        float bw=Math.min(w-40,150),bh=Math.min(h-32,75),left=x+(w-bw)/2,top=y+8;int tint=color(m,"box color",0xFF00FF00);String style=mode(m,"display style","Both");
        if(!style.equals("Outline"))Render2D.roundRect(c,left,top,bw,bh,4,ColorUtil.withAlpha(tint,(int)number(m,"box alpha",50)));
        if(!style.equals("Filled"))Render2D.roundOutline(c,left,top,bw,bh,4,(float)number(m,"outline width",1.5),tint);
        item(c,new ItemStack(Blocks.CHEST),left+bw*.25f-12,top+bh/2-12,24);item(c,new ItemStack(Blocks.BARREL),left+bw*.75f-12,top+bh/2-12,24);
        if(flag(m,"chunk mark",false))Render2D.roundOutline(c,left-8,top-5,bw+16,bh+10,3,1,color(m,"chunk mark color",0x6600FF00));
        Fonts.drawCentered(c,"Base detected · 48 storage",x+w/2,top+bh+14,Theme.TEXT_DIM,true,.65f);
    }
    public static void spawner(DrawContext c,Module m,float x,float y,float w,float h){float size=Math.min(50,h*.55f),cx=x+w/2,top=y+3;item(c,new ItemStack(Blocks.SPAWNER),cx-size/2,top,size);int tint=color(m,"Pillar Color",0xFFFF4D5E);if(flag(m,"Pillar",true)){Render2D.line(c,cx,top,cx,top+size/2,2,tint);Render2D.roundOutline(c,cx-size/2,top,size,size,2,1,tint);}Render2D.roundRect(c,x+6,top+size+8,w-12,25,5,0xE80D121A);Fonts.drawCentered(c,"Zombie spawner · 64m",cx,top+size+20,Theme.TEXT_DIM,true,.65f);}
    public static void spotify(DrawContext c,Module m,float x,float y,float w,float h,double time){
        String theme=mode(m,"theme","AlbumColours"),view=mode(m,"player mode","Expanded"),visualizer=mode(m,"visualizer style","Bars");
        int accent=theme.equals("Monochrome")?0xFFCCD4E0:theme.equals("Midnight")?0xFF8A9FC2:Theme.accent();boolean mini=view.equals("Mini");
        float cw=Math.min(mini?240:420,w-10),ch=Math.min(mini?56:108,h-10),left=x+(w-cw)/2,top=y+(h-ch)/2;
        Render2D.roundRect(c,left,top,cw,ch,8,theme.equals("FrostedGlass")?0xAD1B2230:0xF01B2230);Render2D.roundOutline(c,left,top,cw,ch,8,1,ColorUtil.withAlpha(accent,110));float cover=ch-22;
        Render2D.roundGradientV(c,left+11,top+11,cover,cover,6,accent,ColorUtil.shade(accent,-.35f));Fonts.drawCentered(c,"♪",left+11+cover/2,top+11+cover/2,-1,true,mini?1.4f:2);
        float textX=left+cover+22;Fonts.draw(c,"Spotify preview",textX,top+13,Theme.TEXT,true,.85f);Fonts.draw(c,"Artist · Track sample",textX,top+29,Theme.TEXT_MUTED,false,.63f);float tw=cw-cover-34;
        Render2D.roundRect(c,textX,top+ch-12,tw,3,1.5f,0xFF485263);Render2D.roundRect(c,textX,top+ch-12,tw*(float)(time%20/20),3,1.5f,accent);
        if(!mini&&flag(m,"visualizer",true)){if(visualizer.equals("Ring"))Render2D.ring(c,left+11+cover/2,top+11+cover/2,cover/2+3,1.5f,accent);else if(visualizer.equals("Wave")){for(int i=1;i<40;i++)Render2D.line(c,textX+(i-1)*3,top+ch-30+(float)Math.sin(time*3+(i-1)*.3)*6,textX+i*3,top+ch-30+(float)Math.sin(time*3+i*.3)*6,1.4f,accent);}else for(int i=0;i<18;i++){float bar=3+(float)Math.abs(Math.sin(time*3+i*.7))*12;Render2D.rect(c,textX+i*4,top+ch-26-bar,2,bar,accent);}}
    }
    public static void trajectory(DrawContext c,Module m,float x,float y,float w,float h,double time){float start=x+12,end=x+w-12,ground=y+h-18;int tint=color(m,"Line Color",-1);float previousX=start,previousY=ground;for(int i=1;i<=48;i++){float t=i/48f,xx=start+(end-start)*t,yy=ground-(h-32)*4*t*(1-t);Render2D.line(c,previousX,previousY,xx,yy,1.4f,tint);previousX=xx;previousY=yy;}if(flag(m,"Show Landing Point",true))Render2D.ring(c,end,ground,4,1.2f,color(m,"Hit Color",0xFFFF3B3B));float t=(float)Math.min(1,time/1.6),xx=start+(end-start)*t,yy=ground-(h-32)*4*t*(1-t);item(c,new ItemStack(Items.ENDER_PEARL),xx-7,yy-7,14);if(flag(m,"Trajectory Timer",true))Fonts.drawCentered(c,String.format(java.util.Locale.ROOT,"%.1fs",Math.max(0,1.6-time)),x+w/2,y+h-4,Theme.TEXT_MUTED,false,.6f);}
}
