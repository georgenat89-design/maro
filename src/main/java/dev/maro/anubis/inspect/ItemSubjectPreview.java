package dev.maro.anubis.inspect;

import dev.maro.gui.render.EffectSamples;
import dev.maro.gui.render.Render2D;
import dev.maro.mixin.*;
import dev.maro.module.Module;
import dev.maro.module.impl.visuals.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.util.math.RotationAxis;

/** Native first-person item models and the real knife choreography on an isolated GUI target. */
public final class ItemSubjectPreview extends SpecialGuiElementRenderer<ItemSubjectPreview.State> {
    public record State(Module module,ItemRenderState main,ItemRenderState off,ItemStack stack,double time,boolean playing,int x1,int y1,int x2,int y2,float scale,ScreenRect bounds) implements SpecialGuiElementRenderState {public ScreenRect scissorArea(){return bounds;}}
    private final ItemRenderState mainState=new ItemRenderState(),offState=new ItemRenderState();
    private final ItemStack fallbackMain=new ItemStack(Items.DIAMOND_SWORD),fallbackOff=new ItemStack(Items.TOTEM_OF_UNDYING);
    private ItemStack mainStack=ItemStack.EMPTY,offStack=ItemStack.EMPTY,selectedStack=ItemStack.EMPTY;
    private String selectedId;
    private final float[] choreography=new float[16];
    private net.minecraft.client.network.ClientPlayerEntity sourcePlayer;
    private net.minecraft.util.Arm sourceMainArm;
    private boolean mainReady,offReady;
    private ItemSubjectPreview(){super(MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers());}
    private static boolean knife(Module module){return module instanceof ItemInspect
            ||module instanceof WeaponInspectModule&&!EffectSamples.mode(module,"Knife Skin","None").equals("None");}
    private static boolean same(ItemStack a,ItemStack b){return a.getCount()==b.getCount()&&ItemStack.areItemsAndComponentsEqual(a,b);}
    private record WeaponPlayback(String style,InspectAnimation.Clip clip,float progress){}
    private static InspectAnimation.Clip weaponClip(String style,String knife){
        var skin=knife==null?null:KnifeSkin.of(knife);
        return skin!=null&&style.equals("Classic")?skin.inspect:WeaponInspectionStyles.clip(style,knife!=null);
    }
    /** Same immutable clips/durations and speed as gameplay, on the private presentation clock. */
    private static WeaponPlayback weaponPlayback(Module module,String knife,double seconds,boolean started){
        String style=EffectSamples.mode(module,"Style","Classic");
        double elapsed=started?Math.max(0,seconds)*EffectSamples.number(module,"Speed",1):0;
        if(style.equals("Cycle")){
            double total=0;int count=WeaponInspectionStyles.STYLES.length-1;
            for(int i=0;i<count;i++)total+=weaponClip(WeaponInspectionStyles.STYLES[i],knife).seconds;
            elapsed%=total;
            for(int i=0;i<count;i++){
                String current=WeaponInspectionStyles.STYLES[i];var clip=weaponClip(current,knife);
                if(elapsed<clip.seconds||i==count-1)return new WeaponPlayback(current,clip,(float)(elapsed/clip.seconds));
                elapsed-=clip.seconds;
            }
        }
        var clip=weaponClip(style,knife);
        return new WeaponPlayback(style,clip,(float)(elapsed%clip.seconds/clip.seconds));
    }
    private ItemStack mainSource(Module module,MinecraftClient mc){
        if(module instanceof ItemInspect&&EffectSamples.mode(module,"Items","Selected Item").equals("Selected Item")){
            String value=EffectSamples.mode(module,"Selected Item","minecraft:diamond_sword");
            if(!value.equals(selectedId)){
                selectedId=value;selectedStack=ItemStack.EMPTY;
                var id=net.minecraft.util.Identifier.tryParse(value);
                if(id!=null&&net.minecraft.registry.Registries.ITEM.containsId(id))selectedStack=new ItemStack(net.minecraft.registry.Registries.ITEM.get(id));
            }
            if(!selectedStack.isEmpty())return selectedStack;
        }
        return mc.player.getMainHandStack().isEmpty()?fallbackMain:mc.player.getMainHandStack();
    }
    private void prepare(Module module,MinecraftClient mc){
        if(sourcePlayer!=mc.player||sourceMainArm!=mc.player.getMainArm()){sourcePlayer=mc.player;sourceMainArm=mc.player.getMainArm();mainReady=offReady=false;}
        var source=mainSource(module,mc);
        if(!same(mainStack,source)){mainStack=source.copy();mainReady=false;}
        // A knife uses our cached mesh directly; resolving the vanilla model would be wasted work.
        if(!knife(module)&&(!mainReady||mainState.isAnimated())){
            mc.getItemModelManager().updateForLivingEntity(mainState,mainStack,module instanceof ViewModel&&sourceMainArm==net.minecraft.util.Arm.LEFT?ItemDisplayContext.FIRST_PERSON_LEFT_HAND:ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,mc.player);mainReady=true;
        }
        if(module instanceof ViewModel&&!EffectSamples.flag(module,"Hide Off Hand",false)){
            var sourceOff=mc.player.getOffHandStack().isEmpty()?fallbackOff:mc.player.getOffHandStack();
            if(!same(offStack,sourceOff)){offStack=sourceOff.copy();offReady=false;}
            if(!offReady||offState.isAnimated()){
                mc.getItemModelManager().updateForLivingEntity(offState,offStack,sourceMainArm==net.minecraft.util.Arm.LEFT?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND,mc.player);offReady=true;
            }
        }else if(offReady){offState.clear();offReady=false;}
    }
    /** The renderer belongs to the visible preview, including its native color/depth targets. */
    public static void release(){
        var mc=MinecraftClient.getInstance();
        if(mc.gameRenderer==null)return;
        var gui=((GameRendererSpatialAccessor)mc.gameRenderer).maro$guiRenderer();
        var renderer=((GuiRendererAccessor)gui).maro$specialRenderers().remove(State.class);
        if(renderer!=null)renderer.close();
        var surface=dev.maro.gui.render.ScreenGuiSurface.existingRenderer();
        if(surface!=null&&surface!=gui){
            var local=((GuiRendererAccessor)surface).maro$specialRenderers().remove(State.class);
            if(local!=null)local.close();
        }
    }
    public static void draw(DrawContext ctx,Module module,float x,float y,float w,float h,double time,boolean playing){
        var mc=MinecraftClient.getInstance();var gui=dev.maro.gui.render.ScreenGuiSurface.rendererFor(ctx);
        var area=new ScreenRect(Math.round(x),Math.round(y),Math.max(1,Math.round(w)),Math.max(1,Math.round(h))).transformEachVertex(ctx.getMatrices());var clip=Render2D.getScissor();var visible=clip==null?area:area.intersection(clip);if(visible==null)return;
        var renderer=(ItemSubjectPreview)((GuiRendererAccessor)gui).maro$specialRenderers().computeIfAbsent(State.class,k->new ItemSubjectPreview());
        renderer.prepare(module,mc);
        float scale=Math.min(area.width()*.85f,area.height()*1.2f);
        ((DrawContextAccessor)ctx).maro$getState().addSpecialElement(new State(module,renderer.mainState,renderer.offState,renderer.mainStack,time,playing,area.getLeft(),area.getTop(),area.getRight(),area.getBottom(),scale,visible));
    }
    @Override public void close(){
        try{super.close();}finally{mainState.clear();offState.clear();mainStack=offStack=selectedStack=ItemStack.EMPTY;sourcePlayer=null;selectedId=null;mainReady=offReady=false;}
    }
    @Override public void render(State state,GuiRenderState gui,int scale){
        var color=com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride;
        var depth=com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride;
        var projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrixBuffer();
        var type=com.mojang.blaze3d.systems.RenderSystem.getProjectionType();
        try{super.render(state,gui,scale);}
        finally{
            com.mojang.blaze3d.systems.RenderSystem.outputColorTextureOverride=color;
            com.mojang.blaze3d.systems.RenderSystem.outputDepthTextureOverride=depth;
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection,type);
        }
    }
    @Override public Class<State> getElementClass(){return State.class;}
    @Override protected String getName(){return "Maro item motion preview";}
    @Override protected float getYOffset(int height,int scale){return height*.55f;}
    @Override protected void render(State s,MatrixStack pose){
        var mc=MinecraftClient.getInstance();mc.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        var m=s.module();var queue=mc.gameRenderer.getEntityRenderCommandQueue();
        boolean weapon=m instanceof WeaponInspectModule,knife=knife(m);
        if(knife){
            String name=EffectSamples.mode(m,weapon?"Knife Skin":"Knife","Skeleton"),style=EffectSamples.mode(m,weapon?"Style":"Inspection Style",weapon?"Classic":"Signature");
            boolean alternate=style.equals("Alternate");double progress;
            WeaponPlayback playback=weapon?weaponPlayback(m,name,s.time(),s.playing()):null;
            if(playback!=null){style=playback.style();progress=playback.progress();}
            else{
                double duration=EffectSamples.number(m,"Duration",3.2);
                progress=s.playing()?s.time()%duration/duration:0;
                if(style.equals("Cycle"))alternate=((long)(s.time()/duration)&1)!=0;
            }
            var at=InspectionMotion.sample(name.equals("Katana")?"Bayonet":name,alternate,progress);
            if(weapon&&!style.equals("Classic")){var values=playback.clip().sample((float)progress,choreography);at=new InspectionMotion.Pose(values[0]*.6f,values[1]*.6f,values[2]*.5f,values[4]*.8f,values[3]*.75f,values[5]*.75f,values[6]+values[7],0,0);}
            pose.translate(-.39,.27,.82);
            InspectionKnifeRenderer.draw(pose,queue,LightmapTextureManager.MAX_LIGHT_COORDINATE,s.stack(),name,at,progress,s.playing()&&(!weapon||style.equals("Classic")),alternate,false,EffectSamples.flag(m,"Show Arm",true),(float)EffectSamples.number(m,"Knife Size",weapon?1:100)/(weapon?1:100),0,0,EffectSamples.mode(m,"Finish","Satin Steel"),(float)EffectSamples.number(m,"Metal Reflections",weapon?85:75)/100,EffectSamples.mode(m,"Hand Style","Tactical Gloves").equals("Tactical Gloves"));
        }else{
            pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-35));
            double speed=m instanceof dev.maro.nathan.modules.SwingSpeed?dev.maro.nathan.modules.SwingSpeed.speedOf((int)EffectSamples.number(m,"swing speed",1)):1;
            float swing=s.playing()&&!weapon?(float)(s.time()*speed/.30%1):0;
            WeaponPlayback playback=weapon?weaponPlayback(m,null,s.time(),s.playing()):null;
            for(int hand=0;hand<(m instanceof ViewModel&&!EffectSamples.flag(m,"Hide Off Hand",false)?2:1);hand++){
                boolean mainHand=hand==0;
                boolean left=m instanceof ViewModel?(mainHand?mc.player.getMainArm():mc.player.getMainArm().getOpposite())==net.minecraft.util.Arm.LEFT:!mainHand;
                float side=left?-1:1;pose.push();
                try{
                    pose.translate(side*.20,-.06,0);
                    if(m instanceof ViewModel view){var placement=view.placement(left?net.minecraft.util.Arm.LEFT:net.minecraft.util.Arm.RIGHT);ViewModel.placementTransform(pose,placement,left);ViewModel.itemRotation(pose,placement);}
                    String mode=EffectSamples.mode(m,"Swing Mode","Vanilla");
                    if(mode.equals("Spin"))pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-360*swing*side));
                    else if(mode.equals("Stab"))pose.translate(0,0,-Math.sin(swing*Math.PI)*.45);
                    else if(!mode.equals("None")){
                        // Match ViewModel.swingProgress's easing without touching its live swing fields.
                        float progress=mode.equals("Smooth")?swing*swing*(3-2*swing):swing;
                        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)-Math.sin(Math.sqrt(progress)*Math.PI)*70));pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)Math.sin(progress*progress*Math.PI)*20*side));
                    }
                    if(weapon)InspectAnimation.place(pose,playback.clip().sample(playback.progress(),choreography),1,1,.07f,.2f,.07f);
                    (mainHand?s.main():s.off()).render(pose,queue,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,0);
                }finally{pose.pop();}
            }
        }
        mc.gameRenderer.getEntityRenderDispatcher().render();
    }
}
