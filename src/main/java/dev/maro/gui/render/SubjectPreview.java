package dev.maro.gui.render;

import dev.maro.module.Module;
import dev.maro.module.impl.visuals.BlockESP;
import dev.maro.module.impl.visuals.JumpCircle;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.*;
import dev.maro.util.ColorUtil;
import dev.maro.mixin.DrawContextAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Isolated style samples. They never scan chunks, emit world effects or enable modules. */
public final class SubjectPreview {
    private SubjectPreview(){}
    private static Setting<?> setting(Module m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static double number(Module m,String name){return setting(m,name).toJson().getAsDouble();}
    private static boolean flag(Module m,String name){return setting(m,name).toJson().getAsBoolean();}
    private static String mode(Module m,String name){return setting(m,name).toJson().getAsString();}
    private static int color(Module m,String name){return ((ColorSetting)setting(m,name)).get();}
    public static void blocks(DrawContext ctx,BlockESP module,float x,float y,float w,float h){
        var blocks=module.pickedBlocks();
        if(blocks.isEmpty()){Fonts.drawCentered(ctx,"Choose blocks to preview",x+w/2,y+h/2,Theme.TEXT_MUTED,false,.6f);return;}
        int count=Math.min(3,blocks.size());float cell=w/count,side=Math.min(42,Math.min(cell-12,h-24));
        for(int i=0;i<count;i++){
            var block=blocks.get(i);float left=x+cell*i+(cell-side)/2,top=y+(h-side)/2-4;
            int tint=mode(module,"Color").equals("One Color")?color(module,"ESP Color"):module.colorOf(block);
            if(!mode(module,"Style").equals("Outline"))Render2D.roundRect(ctx,left-2,top-2,side+4,side+4,3,ColorUtil.withAlpha(tint,(int)Math.round(number(module,"Fill Opacity")*2.55)));
            ctx.getMatrices().pushMatrix();try{ctx.getMatrices().translate(left,top);ctx.getMatrices().scale(side/16,side/16);ctx.drawItem(new ItemStack(block),0,0);}finally{ctx.getMatrices().popMatrix();}
            ctx.createNewRootLayer();
            if(!mode(module,"Style").equals("Fill"))Render2D.roundOutline(ctx,left-2,top-2,side+4,side+4,3,(float)number(module,"Line Width")*.55f,tint);
            Fonts.drawCentered(ctx,Fonts.trim(block.getName().getString(),cell-6,false,.5f),left+side/2,top+side+10,Theme.TEXT_MUTED,false,.5f);
        }
    }
    public static void ring(DrawContext ctx,JumpCircle module,float x,float y,float w,float h,double age){
        double life=number(module,"Lifetime"),t=age/life;
        if(t<0||t>=1)return;
        double fade=(1-t)*(1-t)*Math.min(1,t*life/.08),max=number(module,"Radius");
        float fit=(float)Math.min(w/6.8,h/3.5),cx=x+w/2,cy=y+h*.54f;
        float r=(float)(max*(.18+.82*(1-Math.pow(1-t,3)))*fit),line=(float)((.025+.02*max)*fit),halo=(float)((.08+.08*max)*(1-.35*t)*fit);
        int rgb=mode(module,"Color Mode").equals("Custom")?color(module,"Custom Color"):Theme.accent(),core=ColorUtil.lerp(rgb,0xFFFFFFFF,.45f);
        float inner=Math.max(.001f,r-line),outer=r+line;
        var batch=new ShapeRenderState.Builder(ctx,4*64);
        band(batch,cx,cy,Math.max(0,inner-halo*.7f),inner,ColorUtil.withAlpha(rgb,0),ColorUtil.withAlpha(rgb,(int)(140*fade)));
        band(batch,cx,cy,inner,r,ColorUtil.withAlpha(rgb,(int)(140*fade)),ColorUtil.withAlpha(core,(int)(242*fade)));
        band(batch,cx,cy,r,outer,ColorUtil.withAlpha(core,(int)(242*fade)),ColorUtil.withAlpha(rgb,(int)(140*fade)));
        band(batch,cx,cy,outer,outer+halo,ColorUtil.withAlpha(rgb,(int)(140*fade)),ColorUtil.withAlpha(rgb,0));
        var state=batch.build(Render2D.getScissor());if(state!=null)((DrawContextAccessor)ctx).maro$getState().addSimpleElement(state);
        if(flag(module,"Logo")){
            float side=inner*1.36f;
            if(flag(module,"Logo Glow"))Render2D.shadow(ctx,cx-side/2,cy-side*.24f,side,side*.48f,side*.2f,4,ColorUtil.withAlpha(rgb,(int)(75*fade)));
            ctx.getMatrices().pushMatrix();try{ctx.getMatrices().translate(cx-side/2,cy-side*.24f);ctx.getMatrices().scale(1,.48f);ctx.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,Logo.MARK,0,0,0,0,Math.max(1,(int)side),Math.max(1,(int)side),128,128,128,128,ColorUtil.withAlpha(0xFFFFFF,(int)(242*fade)));}finally{ctx.getMatrices().popMatrix();}
        }
    }
    private static void band(ShapeRenderState.Builder b,float cx,float cy,float inner,float outer,int c0,int c1){for(int i=0;i<64;i++){double a=i*Math.PI/32,d=(i+1)*Math.PI/32;float ax=(float)Math.cos(a),ay=(float)Math.sin(a)*.48f,bx=(float)Math.cos(d),by=(float)Math.sin(d)*.48f;b.quad(cx+ax*inner,cy+ay*inner,c0,cx+bx*inner,cy+by*inner,c0,cx+bx*outer,cy+by*outer,c1,cx+ax*outer,cy+ay*outer,c1);}}
}
