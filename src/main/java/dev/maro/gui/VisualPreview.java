package dev.maro.gui;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.render.*;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.impl.visuals.*;
import dev.maro.render.character.CharacterHolder;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Explicit, local-only subjects: never use an unrelated player as a fallback. */
public final class VisualPreview {
    public static final int ENTITY_ID = Integer.MIN_VALUE + 73;
    private final ClickGuiScreen gui;
    private final Animation turn = new Animation(20, -22), zoomMotion = new Animation(20, 1);
    private Module module;
    private float angle = -22, zoom = 1, opacity;
    private float[] bounds;
    private PlayerEntityRenderState lastState;
    private boolean preparedCharacters;
    public enum Kind {HUD,CROSSHAIR,PLAYER,ASPECT,FOV,TOTEM,PET,BLOCKS,RING,SKY,GEEKED,EMOTE,XP,FAKE_BLOCK,BASE,FULLBRIGHT,SPAWNER,SPOTIFY,SPIN,TRAJECTORY,ITEM,NONE}
    private Module active;
    private boolean playing,liveWorld=true;
    private double seconds;
    private double jumpRingAge;
    private float jumpLift;
    public double jumpRingAge(){return jumpRingAge;}
    public float jumpLift(){return jumpLift;}
    private long previewFrame;
    private dev.maro.render.emote.Emote selectedEmote=dev.maro.render.emote.Emote.WAVE;
    private float[] playPlace;
    public float[] playPlace(){return playPlace;}
    public boolean playing(){return playing;}
    public double playbackTime(){return seconds;}
    public void play(){seconds=0;playing=true;previewFrame=System.nanoTime();}
    public void stop(){seconds=0;playing=false;}
    public void selectEmote(dev.maro.render.emote.Emote emote){selectedEmote=emote;seconds=0;}
    private static boolean animated(Kind k){return switch(k){case PLAYER,SKY,GEEKED,EMOTE,XP,RING,PET,SPIN,TRAJECTORY,ITEM,SPOTIFY->true;default->false;};}
    private net.minecraft.entity.LivingEntity previewPet;
    private String previewPetKind;
    private net.minecraft.client.world.ClientWorld previewPetWorld;
    private net.minecraft.client.render.entity.state.EntityRenderState lastSubject;
    public net.minecraft.client.render.entity.state.EntityRenderState lastSubjectState(){return lastSubject;}
    public static Kind kind(Module m){
        if(m==null)return Kind.NONE;
        if(m instanceof BaseESP)return Kind.BASE;
        if(m instanceof HudElement)return Kind.HUD;
        if(m instanceof CustomSky)return Kind.SKY;
        if(m instanceof Geeked)return Kind.GEEKED;
        if(m instanceof Emotes)return Kind.EMOTE;
        if(m instanceof dev.maro.nathan.modules.FakeXp)return Kind.XP;
        if(m instanceof FakeBlock)return Kind.FAKE_BLOCK;
        if(m instanceof Fullbright)return Kind.FULLBRIGHT;
        if(m instanceof SpawnerNotifier)return Kind.SPAWNER;
        if(m instanceof dev.maro.nathan.modules.SpotifyHud)return Kind.SPOTIFY;
        if(m instanceof dev.maro.nathan.modules.SpinBot)return Kind.SPIN;
        if(m instanceof Trajectories)return Kind.TRAJECTORY;
        if(m instanceof ItemInspect||m instanceof dev.maro.anubis.inspect.WeaponInspectModule||m instanceof ViewModel||m instanceof dev.maro.nathan.modules.SwingSpeed)return Kind.ITEM;
        if(m instanceof CustomCrosshair)return Kind.CROSSHAIR;
        if(m instanceof StretchRes)return Kind.ASPECT;
        if(m instanceof dev.maro.nathan.modules.CustomFov||m instanceof dev.maro.nathan.modules.KeyZoom)return Kind.FOV;
        if(m instanceof CustomTotem)return Kind.TOTEM;
        if(m instanceof Pet)return Kind.PET;
        if(m instanceof BlockESP)return Kind.BLOCKS;
        if(m instanceof JumpCircle)return Kind.RING;
        if(m instanceof PlayerESP||m instanceof CustomModel||m instanceof Nametags||m instanceof SkinAccessories)return Kind.PLAYER;
        return Kind.NONE;
    }

    VisualPreview(ClickGuiScreen gui) { this.gui = gui; }
    public static boolean supports(Module module) { return kind(module)!=Kind.NONE; }
    public Module module() { return module; }
    public float[] bounds() { return bounds; }
    public float opacity() { return opacity; }
    public float angle() { return angle; }
    public PlayerEntityRenderState lastState() { return lastState; }
    public void beginFrame() { bounds = null; lastState = null; lastSubject=null; module = null;playPlace=null; }
    public void release(){
        var custom=dev.maro.module.ModuleManager.get(CustomModel.class);
        if(preparedCharacters&&custom!=null&&!custom.isEnabled())dev.maro.render.character.CharacterCache.clear();
        preparedCharacters=false;previewPet=null;previewPetWorld=null;previewPetKind=null;active=null;stop();PreviewShaders.release();
    }

    public void draw(DrawContext ctx, Module target, float x, float y, float w, float h) {
        draw(ctx,target,x,y,w,h,false);
    }
    public void drawCompact(DrawContext ctx, Module target, float x, float y, float w, float h) {
        draw(ctx,target,x,y,w,h,true);
    }
    /** The card's one padding, on every side and between its rows. */
    private static final float PAD = 11, GAP = 8, HEAD = 12, BUTTON = 20, TYPE = .6f;
    private static boolean drawingHud;
    /** Whether a HUD is being drawn into a preview, not onto the screen. */
    public static boolean drawingHud() { return drawingHud; }

    private void draw(DrawContext ctx, Module target, float x, float y, float w, float h,boolean compact) {
        if (!supports(target) || w < 80 || h < 90) return;
        if(active!=target){active=target;stop();previewFrame=System.nanoTime();}
        long now=System.nanoTime();if(playing&&previewFrame!=0)seconds+=Math.min(.1,(now-previewFrame)/1e9);previewFrame=now;
        Kind kind=kind(target);boolean animated=animated(kind);
        module = target;
        bounds = new float[]{x,y,w,h};
        opacity = Render2D.getAlpha();
        // The card on whole pixels, so its edges and everything laid out from them are sharp.
        float[] from=Render2D.snap(ctx,x,y),to=Render2D.snap(ctx,x+w,y+h);
        x=from[0];y=from[1];w=to[0]-from[0];h=to[1]-from[1];
        Render2D.roundGradientV(ctx,x,y,w,h,7,0xE515202D,0xE60A101B);
        Render2D.roundOutline(ctx,x,y,w,h,7,Render2D.px(ctx),Theme.accent(0x40));

        // One header row: the status dot and title on the left, the state as a pill on the right.
        float hy=y+PAD+HEAD/2;
        String heading=switch(kind){case ASPECT,FOV->"Camera comparison";case FULLBRIGHT->"Before / after";case BLOCKS,BASE,SPAWNER,SPOTIFY,TRAJECTORY->"Example preview";case RING->"Jump preview";case HUD->"HUD preview";default->animated?"Live preview":"Appearance";};
        String state=target.isEnabled()?"On":"Preview only";
        int stateColor=target.isEnabled()?Theme.GREEN:Theme.accent();
        float pillW=Fonts.width(ctx,state,false,TYPE)+12,pillX=x+w-PAD-pillW;
        Render2D.roundRect(ctx,pillX,hy-HEAD/2,pillW,HEAD,HEAD/2,ColorUtil.withAlpha(stateColor,0x26));
        Fonts.drawCentered(ctx,state,pillX+pillW/2,hy,target.isEnabled()?Theme.GREEN:Theme.TEXT_DIM,false,TYPE);
        Render2D.circle(ctx,x+PAD+2.5f,hy,2.5f,stateColor);
        Fonts.drawV(ctx,Fonts.trim(ctx,heading,pillX-6-(x+PAD+9),true,TYPE),x+PAD+9,hy,Theme.TEXT_DIM,true,TYPE);

        // The footer: buttons for what plays or turns, otherwise a note, on the bottom padding.
        boolean character=kind==Kind.PLAYER||kind==Kind.PET||kind==Kind.EMOTE||kind==Kind.SPIN||kind==Kind.RING;
        boolean characterControls=character&&(!compact||h>=140);
        boolean note=!animated&&!characterControls&&!compact;
        float footer=(animated?BUTTON:0)+(characterControls?BUTTON+(animated?4:0):0)+(note?9:0);
        float left=x+PAD,width=w-PAD*2,top=y+PAD+HEAD+GAP,bottom=y+h-PAD-(footer>0?footer+GAP:0);
        gui.hit(x,y,w,h,(button,mx,my)->{});
        if(kind==Kind.EMOTE&&(!compact||h>=140)){
            var all=dev.maro.render.emote.Emote.values();
            button(ctx,"‹",left,top,22,()->selectEmote(all[Math.floorMod(selectedEmote.ordinal()-1,all.length)]));
            button(ctx,"›",left+width-22,top,22,()->selectEmote(all[(selectedEmote.ordinal()+1)%all.length]));
            Fonts.drawCentered(ctx,selectedEmote.label(),left+width/2,top+BUTTON/2,Theme.TEXT_DIM,false,.7f);top+=BUTTON+GAP;
        }
        var mc = MinecraftClient.getInstance();
        gui.hit(left,top,width,bottom-top,(button,mx,my)->{
            if(button==0&&character){float initial=angle;gui.startDrag(this,(nx,ny)->angle=initial+(float)(nx-mx)*.9f);}
        });
        if(character)gui.scrollHit(x,y,w,h,amount->zoom=Math.clamp(zoom+(float)amount*.08f,.65f,1.45f));
        float ch=Math.max(1,bottom-top);
        gui.pushClip(x+2,top-4,w-4,ch+8);
        try {
            if(kind==Kind.SKY||kind==Kind.GEEKED){
                PreviewShaders.draw(ctx,target,left,top,width,ch,seconds,liveWorld);
            }else if(kind==Kind.XP){EffectSamples.xp(ctx,target,left,top,width,ch,seconds);
            }else if(target instanceof FakeBlock fake){EffectSamples.blocks(ctx,fake,left,top,width,ch);
            }else if(kind==Kind.FULLBRIGHT){EffectSamples.fullbright(ctx,target,left,top,width,ch);
            }else if(kind==Kind.BASE){EffectSamples.base(ctx,target,left,top,width,ch);
            }else if(kind==Kind.SPAWNER){EffectSamples.spawner(ctx,target,left,top,width,ch);
            }else if(kind==Kind.SPOTIFY){EffectSamples.spotify(ctx,target,left,top,width,ch,seconds);
            }else if(kind==Kind.TRAJECTORY){EffectSamples.trajectory(ctx,target,left,top,width,ch,seconds);
            }else if(kind==Kind.ASPECT||kind==Kind.FOV){
                CameraPreview.draw(ctx,target,left,top,width,ch);
            }else if(target instanceof CustomTotem totem){
                drawTotem(ctx,totem,left,top,width,ch);
            }else if(target instanceof BlockESP blocks){
                SubjectPreview.blocks(ctx,blocks,left,top,width,ch);
            }else if(target instanceof JumpCircle ring){
                double life=EffectSamples.number(ring,"Lifetime",1),cycle=.8+life+.55;
                double phase=seconds%cycle;
                jumpRingAge=playing?(EffectSamples.mode(ring,"Trigger","Jump").equals("Land")?phase-.8:phase):life*.22;
                float body=ch,ground=top+body*.74f;
                jumpLift=playing&&phase<.8?(float)Math.sin(phase/.8*Math.PI)*body*.16f:0;
                float ringH=body*.60f;
                SubjectPreview.ring(ctx,ring,left,ground-ringH*.54f,width,ringH,jumpRingAge);
                if(playing&&EffectSamples.mode(ring,"Trigger","Jump").equals("Both"))SubjectPreview.ring(ctx,ring,left,ground-ringH*.54f,width,ringH,phase-.8);
                if(mc.player!=null)drawPlayer(ctx,target,left,top,width,body*.75f,ground-jumpLift);
            }else if(target instanceof CustomCrosshair crosshair){
                // At its real size: one GUI unit is as many pixels as in game, so it is as sharp.
                float[] centre=Render2D.snap(ctx,left+width/2,top+ch/2);
                float real=(float)(mc.getWindow().getScaleFactor()/Render2D.device(ctx));
                ctx.getMatrices().pushMatrix();
                try{ctx.getMatrices().translate(centre[0],centre[1]);ctx.getMatrices().scale(real,real);crosshair.renderPreview(ctx,0,0);}
                finally{ctx.getMatrices().popMatrix();}
            }else if(mc.player==null) {
                Fonts.drawCentered(ctx,"Join a world",left+width/2,top+ch/2-6,Theme.TEXT_DIM,false,.68f);
                Fonts.drawCentered(ctx,"to preview yourself",left+width/2,top+ch/2+7,Theme.TEXT_MUTED,false,.56f);
            } else if(target instanceof HudElement hud) {
                drawHud(ctx,target,hud,left,top,width,ch);
            } else if(target instanceof Pet pet){
                drawPet(ctx,pet,left,top,width,ch);
            } else if(kind==Kind.ITEM){
                dev.maro.anubis.inspect.ItemSubjectPreview.draw(ctx,target,left,top,width,ch,seconds,playing);
            } else {
                boolean tags=target instanceof Nametags;
                drawPlayer(ctx,target,left,top+(tags?38:0),width,ch-(tags?38:0));
                if(tags)((Nametags)target).renderPreview(ctx,left+width/2,top+38,width);
            }
        } finally {gui.popClip();}
        float fy=y+h-PAD-footer;
        if(characterControls){
            float bw=(width-8)/3;
            button(ctx,"Front",left,fy,bw,()->angle=0);
            button(ctx,"Back",left+bw+4,fy,bw,()->angle=180);
            button(ctx,"Reset",left+(bw+4)*2,fy,bw,()->{angle=-22;zoom=1;});
            fy+=BUTTON+4;
            if(gui.hovered(x,top,w,ch))gui.tooltip("Drag to rotate · scroll to zoom");
        }else if(note){
            Fonts.drawCentered(ctx,"Updates as you edit",x+w/2,fy+4.5f,Theme.TEXT_MUTED,false,TYPE);
        }
        if(animated){
            float bw=kind==Kind.GEEKED?(width-4)/2:width;
            button(ctx,playing?"■ Stop":"▶ Play",left,fy,bw,()->{if(playing)stop();else play();});playPlace=new float[]{left+bw/2,fy+BUTTON/2};
            if(kind==Kind.GEEKED)button(ctx,liveWorld?"Live world":"Scene sample",left+bw+4,fy,bw,()->liveWorld=!liveWorld);
        }
    }

    /**
     * A HUD as it is in game, centred. It is scaled so each of its own units is a whole or half
     * number of pixels and placed on a whole pixel, so its edges and text land on the pixel grid.
     */
    private void drawHud(DrawContext ctx,Module target,HudElement hud,float x,float y,float w,float h){
        var mc=MinecraftClient.getInstance();
        float sw=Math.max(1,hud.hudWidth()),sh=Math.max(1,hud.hudHeight());
        float fit=Math.min(4,Math.min(w/sw,h/sh));
        double unit=Render2D.device(ctx)*Math.max(.05f,hud.hudScale());
        double pixels=fit*unit;
        float size=pixels>=1?(float)(Math.floor(pixels*2)/2/unit):fit;
        float[] at=Render2D.snap(ctx,x+(w-sw*size)/2,y+(h-sh*size)/2);
        var matrices=ctx.getMatrices();
        matrices.pushMatrix();
        float measure=Fonts.measureScale();
        int menu=Fonts.pauseMenu();
        drawingHud=true;
        try {
            matrices.translate(at[0],at[1]);
            matrices.scale(size,size);
            // Text measured without a context uses the same atlas it is drawn with, as on the HUD.
            Fonts.setMeasureScale((float)Math.hypot(matrices.m00(),matrices.m01()));
            matrices.translate(-hud.hudLeft(),-hud.hudTop());
            if(target instanceof Radar radar)radar.renderPreview(ctx);else target.onRender2D(ctx,mc.getRenderTickCounter().getTickProgress(false));
        } finally {
            drawingHud=false;
            Fonts.resumeMenu(menu);
            Fonts.setMeasureScale(measure);
            matrices.popMatrix();
        }
    }

    private void button(DrawContext ctx,String label,float x,float y,float w,Runnable action) {
        boolean over=gui.hovered(x,y,w,BUTTON);
        Render2D.roundRect(ctx,x,y,w,BUTTON,4,over?Theme.accent(0x40):0x1C9BAEC4);
        Fonts.drawCentered(ctx,label,x+w/2,y+BUTTON/2,over?Theme.TEXT:Theme.TEXT_DIM,false,.7f);
        gui.hit(x,y,w,BUTTON,(button,mx,my)->{if(button==0)action.run();});
    }

    private void drawPlayer(DrawContext ctx,Module target,float x,float y,float w,float h) {
        drawPlayer(ctx,target,x,y,w,h,Float.NaN);
    }
    private void drawPlayer(DrawContext ctx,Module target,float x,float y,float w,float h,float feetY) {
        var mc=MinecraftClient.getInstance();
        var state=(PlayerEntityRenderState)mc.getEntityRenderDispatcher().getRenderer(mc.player)
            .getAndUpdateRenderState(mc.player,mc.getRenderTickCounter().getTickProgress(false));
        state.id=ENTITY_ID;state.light=LightmapTextureManager.MAX_LIGHT_COORDINATE;
        state.shadowPieces.clear();state.outlineColor=0;state.displayName=null;state.nameLabelPos=null;
        state.bodyYaw=180+turn.update(angle);state.relativeHeadYaw=0;state.pitch=0;
        state.age=(float)seconds*20;
        if(kind(target)==Kind.PLAYER){state.limbSwingAnimationProgress=(float)seconds*5;state.limbSwingAmplitude=playing?.55f:0;}
        if(target instanceof dev.maro.nathan.modules.SpinBot)state.bodyYaw+=(float)(seconds*EffectSamples.number(target,"spin speed",360))*(EffectSamples.mode(target,"direction","Clockwise").equals("Clockwise")?1:-1);
        if(state instanceof dev.maro.render.emote.EmoteRenderState emote){
            if(target instanceof Emotes&&playing){float t=(float)(seconds*EffectSamples.number(target,"Speed",1));float blend=t>selectedEmote.length()&&!selectedEmote.loops()?Math.max(0,1-(t-selectedEmote.length())/.28f):Math.min(1,t/.18f);emote.maro$setEmote(selectedEmote,selectedEmote.loops()?t:Math.min(t,selectedEmote.length()),blend);}
            else emote.maro$setEmote(null,0,0);
        }
        state.width/=state.baseScale;state.height/=state.baseScale;state.baseScale=1;
        state.invisible=false;state.invisibleToPlayer=false;
        if(target instanceof CustomModel custom){
            ((CharacterHolder)state).maro$setCharacter(custom.ready(custom.selected(false)));
            preparedCharacters=true;
        }
        var worn=CustomModel.worn(state);
        float modelHeight=worn==null?1.9f:worn.model.maxY*worn.model.scale*CustomModel.sizeFactor()/16;
        float size=Math.min((w-14)/2.2f,(h-18)/Math.max(2.1f,modelHeight+.3f))*zoomMotion.update(zoom);
        if(Float.isFinite(feetY))y=feetY-modelHeight*size/2-h/2;
        // addEntity does not apply DrawContext's matrix. Transform its rectangle and scale once.
        var area=new ScreenRect(Math.round(x),Math.round(y),Math.max(1,Math.round(w)),Math.max(1,Math.round(h)))
            .transformEachVertex(ctx.getMatrices());
        float matrixScale=(float)Math.hypot(ctx.getMatrices().m00(),ctx.getMatrices().m01());
        ctx.addEntity(state,size*matrixScale,new Vector3f(0,modelHeight/2,0),
            new Quaternionf().rotateZ((float)Math.PI).rotateX(.08f),new Quaternionf().rotateX(.08f),
            area.getLeft(),area.getTop(),area.getRight(),area.getBottom());
        lastState=state;lastSubject=state;
    }
    private static String mode(Module module,String name,String fallback){for(var s:module.getSettings())if(s.getName().equals(name)&&s instanceof dev.maro.setting.ModeSetting setting)return setting.get();return fallback;}
    private static double number(Module module,String name,double fallback){for(var s:module.getSettings())if(s.getName().equals(name))try{return s.toJson().getAsDouble();}catch(RuntimeException ignored){}return fallback;}
    private static boolean flag(Module module,String name,boolean fallback){for(var s:module.getSettings())if(s.getName().equals(name))try{return s.toJson().getAsBoolean();}catch(RuntimeException ignored){}return fallback;}
    private void drawPet(DrawContext ctx,Pet pet,float x,float y,float w,float h){
        var mc=MinecraftClient.getInstance();String kind=mode(pet,"Pet","Wolf");
        if(previewPet==null||previewPetWorld!=mc.world||!kind.equals(previewPetKind)){
            var type=switch(kind){case "Cat"->net.minecraft.entity.EntityType.CAT;case "Fox"->net.minecraft.entity.EntityType.FOX;case "Bunny"->net.minecraft.entity.EntityType.RABBIT;case "Bee"->net.minecraft.entity.EntityType.BEE;case "Allay"->net.minecraft.entity.EntityType.ALLAY;case "Parrot"->net.minecraft.entity.EntityType.PARROT;case "Axolotl"->net.minecraft.entity.EntityType.AXOLOTL;case "Slime"->net.minecraft.entity.EntityType.SLIME;case "Turtle"->net.minecraft.entity.EntityType.TURTLE;case "Panda"->net.minecraft.entity.EntityType.PANDA;case "Pig"->net.minecraft.entity.EntityType.PIG;case "Dragon"->net.minecraft.entity.EntityType.ENDER_DRAGON;default->net.minecraft.entity.EntityType.WOLF;};
            previewPet=type.create(mc.world,net.minecraft.entity.SpawnReason.COMMAND);previewPetWorld=mc.world;previewPetKind=kind;
            if(previewPet instanceof net.minecraft.entity.passive.TameableEntity tameable)tameable.setTamed(true,false);
            if(previewPet instanceof net.minecraft.entity.mob.SlimeEntity slime)slime.setSize(1,false);
        }
        if(previewPet==null)return;
        if(previewPet instanceof net.minecraft.entity.passive.PassiveEntity passive)passive.setBaby(flag(pet,"Baby",false));
        var collar=net.minecraft.util.DyeColor.PURPLE;
        try{collar=net.minecraft.util.DyeColor.valueOf(mode(pet,"Collar","Purple").toUpperCase(java.util.Locale.ROOT));}catch(IllegalArgumentException ignored){}
        previewPet.age=(int)(seconds*20);
        if(previewPet instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity dragon){dragon.frameTracker.tick(0,0);dragon.lastWingPosition=dragon.wingPosition=(float)seconds;}
        var state=mc.getEntityRenderDispatcher().getRenderer(previewPet).getAndUpdateRenderState(previewPet,0);
        if(state instanceof net.minecraft.client.render.entity.state.WolfEntityRenderState wolf){wolf.collarColor=collar;wolf.tailAngle=1.2f;}
        if(state instanceof net.minecraft.client.render.entity.state.CatEntityRenderState cat)cat.collarColor=collar;
        state.age=flag(pet,"Animate",true)?(float)seconds*20:0;
        state.light=LightmapTextureManager.MAX_LIGHT_COORDINATE;state.shadowPieces.clear();state.displayName=null;state.nameLabelPos=null;
        if(state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState living){living.bodyYaw=180+turn.update(angle);living.limbSwingAnimationProgress=(float)seconds*4;living.limbSwingAmplitude=playing?.5f:0;}
        float size=Math.min((w-14)/Math.max(1.4f,state.width+.4f),(h-12)/Math.max(1.2f,state.height+.3f))*(float)number(pet,"Size",.85)*zoomMotion.update(zoom);
        var area=new ScreenRect(Math.round(x),Math.round(y),Math.max(1,Math.round(w)),Math.max(1,Math.round(h))).transformEachVertex(ctx.getMatrices());
        float matrixScale=(float)Math.hypot(ctx.getMatrices().m00(),ctx.getMatrices().m01());
        ctx.addEntity(state,size*matrixScale,new Vector3f(0,state.height/2,0),new Quaternionf().rotateZ((float)Math.PI).rotateX(.08f),new Quaternionf().rotateX(.08f),area.getLeft(),area.getTop(),area.getRight(),area.getBottom());lastSubject=state;
    }
    private void drawTotem(DrawContext ctx,CustomTotem module,float x,float y,float w,float h){
        // Totem art is 16 pixels across: a whole number of screen pixels each, from a whole pixel.
        var texture=module.previewTexture();float side=Render2D.snapSize(ctx,Math.min(w,h)*.83f,16);
        float[] at=Render2D.snap(ctx,x+(w-side)/2,y+(h-side)/2);float left=at[0],top=at[1];
        if(texture==null){
            ctx.getMatrices().pushMatrix();try{float scale=side/16;ctx.getMatrices().translate(left,top);ctx.getMatrices().scale(scale,scale);ctx.drawItem(new net.minecraft.item.ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING),0,0);}finally{ctx.getMatrices().popMatrix();}return;
        }
        var pose=new org.joml.Matrix3x2f(ctx.getMatrices());
        var bounds=new ScreenRect(Math.round(left),Math.round(top),Math.max(1,Math.round(side)),Math.max(1,Math.round(side))).transformEachVertex(pose);
        var clip=Render2D.getScissor();var visible=clip==null?bounds:bounds.intersection(clip);if(visible==null)return;
        ((dev.maro.mixin.DrawContextAccessor)ctx).maro$getState().addSimpleElement(new dev.maro.runtime.renderer.GuiMeshState(pose,new float[]{left,top,0,0,left,top+side,0,1,left+side,top+side,1,1,left+side,top,1,0},new int[]{-1,-1,-1,-1},net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,net.minecraft.client.texture.TextureSetup.of(texture.getGlTextureView(),texture.getSampler()),visible,clip));
    }

}
