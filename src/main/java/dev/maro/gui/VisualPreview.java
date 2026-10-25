package dev.maro.gui;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.render.*;
import dev.maro.gui.theme.Theme;
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
    private float[] contentBounds;
    private final float[] panelArea=new float[4],subjectArea=new float[4],playbackPoint=new float[2];
    private String caption="";
    private final java.util.Map<String,float[]> controlPlaces=new java.util.HashMap<>();
    private final java.util.Set<String> visibleActions=new java.util.HashSet<>();
    private long workFrames;
    private java.util.List<dev.maro.setting.Setting<?>> presentationSettings=java.util.List.of();
    private int settingsStamp;
    private PlayerEntityRenderState lastState;
    private boolean preparedCharacters;
    private final PlayerPreviewState playerState = new PlayerPreviewState();
    private dev.maro.render.character.kit.CharacterModel fittedModel;
    private float fittedRadius;
    public enum Kind {HUD,CHEST,CROSSHAIR,PLAYER,NAMETAG,ASPECT,FOV,TOTEM,PET,BLOCKS,RING,SKY,GEEKED,EMOTE,XP,FAKE_BLOCK,BASE,FULLBRIGHT,SPAWNER,SPOTIFY,SPIN,TRAJECTORY,ITEM,NONE}
    private Module active;
    private CustomModel pendingModelTarget;
    private boolean otherModel,pendingOtherModel;
    private boolean playing,playbackStarted;
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
    public float zoom(){return zoom;}
    public boolean otherModel(){return otherModel;}
    public void selectModelTarget(CustomModel target,boolean other){
        if(active==target){otherModel=other;fittedModel=null;dev.maro.render.character.CharacterMotion.forgetPreview(ENTITY_ID);}
        else {pendingModelTarget=target;pendingOtherModel=other;}
    }
    public long workFrames(){return workFrames;}
    public float[] contentBounds(){return contentBounds;}
    public float[] actionPlace(String id){return bounds!=null&&visibleActions.contains(id.toUpperCase(java.util.Locale.ROOT))?controlPlaces.get(id.toUpperCase(java.util.Locale.ROOT)):null;}
    public void play(){playbackStarted=true;playing=true;previewFrame=System.nanoTime();}
    public void pause(){playing=false;previewFrame=0;}
    /** An offscreen world panel retains presentation without advancing time or drawing its subject. */
    public void suspendFrame(){previewFrame=0;}
    /** Kept for callers; stopping playback pauses at its current presentation position. */
    public void stop(){pause();}
    public void resetPresentation(){
        angle=-22;zoom=1;turn.snap(angle);zoomMotion.snap(zoom);seconds=0;playbackStarted=false;pause();
        jumpRingAge=0;jumpLift=0;selectedEmote=dev.maro.render.emote.Emote.WAVE;
        dev.maro.render.character.CharacterMotion.forgetPreview(ENTITY_ID);
    }
    public void selectEmote(dev.maro.render.emote.Emote emote){selectedEmote=emote;seconds=0;dev.maro.render.character.CharacterMotion.forgetPreview(ENTITY_ID);}
    private net.minecraft.entity.LivingEntity previewPet;
    @SuppressWarnings("rawtypes") private net.minecraft.client.render.entity.EntityRenderer petRenderer;
    private net.minecraft.client.render.entity.state.EntityRenderState petState;
    private String previewPetKind;
    private net.minecraft.client.world.ClientWorld previewPetWorld;
    private net.minecraft.client.render.entity.state.EntityRenderState lastSubject;
    public net.minecraft.client.render.entity.state.EntityRenderState lastSubjectState(){return lastSubject;}
    public static Kind kind(Module m){
        return dev.maro.gui.preview.PreviewProviders.kind(m);
    }

    private static boolean reloadListener;
    VisualPreview(ClickGuiScreen gui) {
        this.gui = gui;
        if(!reloadListener){
            reloadListener=true;
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->{
                if(client.currentScreen instanceof ClickGuiScreen open)open.visualPreview().release();
                else {PreviewShaders.release();VisualPreviewEffect.release();dev.maro.render.character.CharacterMotion.forgetPreview(ENTITY_ID);}
            });
            net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloader(net.minecraft.util.Identifier.of("maro","unified_preview"),(net.minecraft.resource.SynchronousResourceReloader)manager->MinecraftClient.getInstance().execute(()->{
                    if(MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen open)open.visualPreview().resourcesReloaded();
                }));
        }
    }
    public static boolean supports(Module module) { return dev.maro.gui.preview.PreviewProviders.supports(module); }
    public Module module() { return module; }
    public float[] bounds() { return bounds; }
    public float opacity() { return opacity; }
    public float angle() { return angle; }
    public PlayerEntityRenderState lastState() { return lastState; }
    public void beginFrame() {
        if(active!=null&&gui.settingsModule()!=active)hidden();
        bounds = null;contentBounds=null;lastState = null;lastSubject=null;module = null;playPlace=null;visibleActions.clear();
    }
    private void releaseSubject(){
        var custom=dev.maro.module.ModuleManager.get(CustomModel.class);
        if(preparedCharacters&&custom!=null&&!custom.isEnabled())dev.maro.render.character.CharacterCache.clear();
        if(active instanceof dev.maro.module.impl.misc.SkinChanger skin)skin.releasePreview();
        var hats=dev.maro.module.ModuleManager.get(dev.maro.nathan.modules.Hats.class);
        if(hats!=null)hats.releasePreview();
        playerState.release();fittedModel=null;
        dev.maro.render.character.CharacterMotion.forgetPreview(ENTITY_ID);
        preparedCharacters=false;previewPet=null;previewPetWorld=null;previewPetKind=null;petState=null;petRenderer=null;
        PreviewShaders.release();VisualPreviewEffect.release();dev.maro.anubis.inspect.ItemSubjectPreview.release();
    }
    public void hidden(){
        pause();
        if(active!=null){releaseSubject();active=null;}
        bounds=null;contentBounds=null;lastState=null;lastSubject=null;module=null;playPlace=null;visibleActions.clear();pendingModelTarget=null;
    }
    public void resourcesReloaded(){releaseSubject();previewFrame=0;}
    public void release(){
        hidden();resetPresentation();
    }

    public void draw(DrawContext ctx, Module target, float x, float y, float w, float h) {
        draw(ctx,target,x,y,w,h,false);
    }
    public void drawCompact(DrawContext ctx, Module target, float x, float y, float w, float h) {
        draw(ctx,target,x,y,w,h,true);
    }
    /** The card's one padding, on every side and between its rows. */
    private static final float PAD = 12, GAP = 9, HEAD = 14, BUTTON = 22, TYPE = .6f;
    private static boolean drawingHud;
    /** Whether a HUD is being drawn into a preview, not onto the screen. */
    public static boolean drawingHud() { return drawingHud; }

    private void draw(DrawContext ctx, Module target, float x, float y, float w, float h,boolean compact) {
        var provider=dev.maro.gui.preview.PreviewProviders.provider(target);
        if (provider==null || w < 140 || h < 150) return;
        if(active!=target){
            releaseSubject();active=target;presentationSettings=java.util.List.copyOf(target.getSettings());settingsStamp=0;resetPresentation();previewFrame=System.nanoTime();
            otherModel=target instanceof CustomModel&&pendingModelTarget==target&&pendingOtherModel;pendingModelTarget=null;
            caption=provider.caption();
            if(!provider.exactRenderer()&&!caption.toLowerCase(java.util.Locale.ROOT).startsWith("illustrative"))caption="Illustrative sample · "+caption;
        }
        int currentStamp=1;for(var setting:presentationSettings)currentStamp=31*currentStamp+java.util.Objects.hashCode(setting.get());
        if(currentStamp!=settingsStamp){dev.maro.render.character.CharacterMotion.forgetPreview(ENTITY_ID);settingsStamp=currentStamp;}
        long now=System.nanoTime();if(playing&&previewFrame!=0)seconds+=Math.min(.1,(now-previewFrame)/1e9);previewFrame=now;
        Kind kind=kind(target);boolean animated=provider.animated();
        workFrames++;
        module = target;
        panelArea[0]=x;panelArea[1]=y;panelArea[2]=w;panelArea[3]=h;bounds=panelArea;
        opacity = Render2D.getAlpha();
        // The card on whole pixels, so its edges and everything laid out from them are sharp.
        float[] from=Render2D.snap(ctx,x,y),to=Render2D.snap(ctx,x+w,y+h);
        x=from[0];y=from[1];w=to[0]-from[0];h=to[1]-from[1];
        Render2D.roundGradientV(ctx,x,y,w,h,Theme.PANEL_RADIUS,Theme.PANEL,Theme.BG);
        Render2D.roundOutline(ctx,x,y,w,h,Theme.PANEL_RADIUS,Render2D.px(ctx),Theme.BORDER);

        // One header row: the status dot and title on the left, the state as a pill on the right.
        float hy=y+PAD+HEAD/2;
        String heading="Live preview",state="Preview only";
        float badgeType=.49f,pillW=Fonts.width(ctx,state,true,badgeType)+12,pillX=x+w-PAD-pillW;
        Render2D.roundRect(ctx,pillX,hy-HEAD/2,pillW,HEAD,4,Theme.selected());
        Render2D.roundOutline(ctx,pillX,hy-HEAD/2,pillW,HEAD,4,Render2D.px(ctx),Theme.accent(0x55));
        Fonts.drawCentered(ctx,state,pillX+pillW/2,hy,Theme.TEXT_DIM,true,badgeType);
        Render2D.circle(ctx,x+PAD+2,hy,2,Theme.accent());
        float headingType=Math.min(.63f,(pillX-8-(x+PAD+9))/Math.max(1,Fonts.width(ctx,heading,true,1)));
        Fonts.drawV(ctx,heading,x+PAD+9,hy,Theme.TEXT_DIM,true,headingType);
        Fonts.drawV(ctx,Fonts.trim(ctx,caption,w-PAD*2,false,.54f),x+PAD,y+PAD+HEAD+13,Theme.TEXT_MUTED,false,.54f);

        // The footer: buttons for what plays or turns, otherwise a note, on the bottom padding.
        boolean character=provider.controls()==dev.maro.gui.preview.PreviewProvider.Controls.MODEL;
        boolean characterControls=character;
        boolean note=!animated&&!characterControls&&!compact;
        boolean emoteControls=kind==Kind.EMOTE&&h>=230;
        boolean modelTargetControls=target instanceof CustomModel&&h>=230;
        float footer=(animated?BUTTON:0)+(characterControls?BUTTON+(animated?6:0):0)+(emoteControls?BUTTON+6:0)+(modelTargetControls?BUTTON+6:0)+(note?9:0);
        float left=x+PAD,width=w-PAD*2,top=y+PAD+HEAD+GAP+21,bottom=y+h-PAD-(footer>0?footer+12:0);
        gui.hit(x,y,w,h,(button,mx,my)->{});
        var mc = MinecraftClient.getInstance();
        gui.hit(left,top,width,bottom-top,(button,mx,my)->{
            if(button==0&&character){float initial=angle;gui.startDrag(this,(nx,ny)->angle=initial+(float)(nx-mx)*.9f);}
        });
        if(character)gui.scrollHit(left,top,width,Math.max(1,bottom-top),amount->zoom=Math.clamp(zoom+(float)amount*.08f,.55f,1f));
        float ch=Math.max(1,bottom-top);
        subjectArea[0]=left;subjectArea[1]=top;subjectArea[2]=width;subjectArea[3]=ch;contentBounds=subjectArea;
        gui.pushClip(left,top,width,ch);
        try {
            if(kind==Kind.SKY||kind==Kind.GEEKED){
                PreviewShaders.draw(ctx,target,left,top,width,ch,seconds);
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
                jumpRingAge=playbackStarted?(EffectSamples.mode(ring,"Trigger","Jump").equals("Land")?phase-.8:phase):life*.22;
                float body=ch,ground=top+body*.74f;
                jumpLift=playbackStarted&&phase<.8?(float)Math.sin(phase/.8*Math.PI)*body*.16f:0;
                float ringH=body*.60f;
                SubjectPreview.ring(ctx,ring,left,ground-ringH*.54f,width,ringH,jumpRingAge);
                if(playbackStarted&&EffectSamples.mode(ring,"Trigger","Jump").equals("Both"))SubjectPreview.ring(ctx,ring,left,ground-ringH*.54f,width,ringH,phase-.8);
            }else if(kind==Kind.NAMETAG){
                ((Nametags)target).renderPreview(ctx,left+width/2,top+ch/2,width);
            }else if(kind==Kind.SPIN){
                float radius=Math.min(width,ch)*.32f,cx=left+width/2,cy=top+ch/2;
                float yaw=(float)(seconds*EffectSamples.number(target,"spin speed",360))
                    *(EffectSamples.mode(target,"direction","Clockwise").equals("Clockwise")?1:-1);
                double a=Math.toRadians(yaw);float ex=cx+(float)Math.sin(a)*radius,ey=cy-(float)Math.cos(a)*radius;
                Render2D.ring(ctx,cx,cy,radius,1,Theme.accent(0x65));
                Render2D.line(ctx,cx,cy,ex,ey,2,Theme.accent());Render2D.circle(ctx,ex,ey,3,Theme.TEXT);
            }else if(target instanceof CustomCrosshair crosshair){
                // At its real size: one GUI unit is as many pixels as in game, so it is as sharp.
                float[] centre=Render2D.snap(ctx,left+width/2,top+ch/2);
                float real=(float)(mc.getWindow().getScaleFactor()/Render2D.device(ctx));
                ctx.getMatrices().pushMatrix();
                try{ctx.getMatrices().translate(centre[0],centre[1]);ctx.getMatrices().scale(real,real);crosshair.renderPreview(ctx,0,0);}
                finally{ctx.getMatrices().popMatrix();}
            }else if(target instanceof ChestPreview chest){
                chest.renderPreview(ctx,left,top,width,ch);
            }else if(mc.player==null) {
                Fonts.drawCentered(ctx,"Join a world",left+width/2,top+ch/2-6,Theme.TEXT_DIM,false,.68f);
                Fonts.drawCentered(ctx,"to preview yourself",left+width/2,top+ch/2+7,Theme.TEXT_MUTED,false,.56f);
            } else if(target instanceof HudElement hud) {
                drawHud(ctx,target,hud,left,top,width,ch);
            } else if(target instanceof Pet pet){
                drawPet(ctx,pet,left,top,width,ch);
            } else if(kind==Kind.ITEM){
                dev.maro.anubis.inspect.ItemSubjectPreview.draw(ctx,target,left,top,width,ch,seconds,playbackStarted);
            } else {
                drawPlayer(ctx,target,left,top,width,ch);
            }
        } finally {gui.popClip();}
        float fy=y+h-PAD-footer;
        Render2D.rect(ctx,left,fy-10,width,Render2D.px(ctx),Theme.BORDER);
        if(modelTargetControls){
            float bw=(width-6)/2;var custom=(CustomModel)target;
            button(ctx,"TARGET_SELF","Self",left,fy,bw,()->selectModelTarget(custom,false),!otherModel);
            button(ctx,"TARGET_OTHERS","Others",left+bw+6,fy,bw,()->selectModelTarget(custom,true),otherModel);fy+=BUTTON+6;
        }
        if(emoteControls){
            var all=dev.maro.render.emote.Emote.values();
            button(ctx,"EMOTE_PREVIOUS","‹",left,fy,22,()->selectEmote(all[Math.floorMod(selectedEmote.ordinal()-1,all.length)]));
            button(ctx,"EMOTE_NEXT","›",left+width-22,fy,22,()->selectEmote(all[(selectedEmote.ordinal()+1)%all.length]));
            Fonts.drawCentered(ctx,Fonts.trim(ctx,selectedEmote.label(),width-54,false,.63f),left+width/2,fy+BUTTON/2,Theme.TEXT_DIM,false,.63f);fy+=BUTTON+6;
        }
        if(characterControls){
            float bw=(width-8)/3;
            button(ctx,"FRONT","Front",left,fy,bw,()->angle=0);
            button(ctx,"BACK","Back",left+bw+4,fy,bw,()->angle=180);
            button(ctx,"RESET","Reset",left+(bw+4)*2,fy,bw,this::resetPresentation);
            fy+=BUTTON+6;
            if(gui.hovered(x,top,w,ch))gui.tooltip("Drag to rotate · scroll to zoom");
        }else if(note){
            Fonts.drawCentered(ctx,"Updates as you edit",x+w/2,fy+4.5f,Theme.TEXT_MUTED,false,TYPE);
        }
        if(animated){
            float bw=characterControls?width:(width-6)/2;
            button(ctx,"PLAY",playing?"Ⅱ  Pause":"▶  Play",left,fy,bw,()->{if(playing)pause();else play();});playbackPoint[0]=left+bw/2;playbackPoint[1]=fy+BUTTON/2;playPlace=playbackPoint;
            if(!characterControls)button(ctx,"RESET","Reset",left+bw+6,fy,bw,this::resetPresentation);
        }
        if(target instanceof CustomModel custom && gui.hovered(left,top,width,ch))
            gui.tooltip("Model size "+custom.sizeLabel()+" · fitted to keep the whole character visible");
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

    private void button(DrawContext ctx,String id,String label,float x,float y,float w,Runnable action) {
        button(ctx,id,label,x,y,w,action,false);
    }
    private void button(DrawContext ctx,String id,String label,float x,float y,float w,Runnable action,boolean selected) {
        boolean over=gui.hovered(x,y,w,BUTTON);
        Render2D.roundRect(ctx,x,y,w,BUTTON,Theme.CONTROL_RADIUS,selected?Theme.selected():over?Theme.CARD_HOVER:Theme.INPUT);
        Render2D.roundOutline(ctx,x,y,w,BUTTON,Theme.CONTROL_RADIUS,Render2D.px(ctx),selected?Theme.accent(0x90):over?Theme.accent(0x60):Theme.BORDER);
        float font=Math.min(.72f,(w-10)/Math.max(1,Fonts.width(ctx,label,false,1)));
        Fonts.drawCentered(ctx,label,x+w/2,y+BUTTON/2,selected?Theme.accent():over?Theme.TEXT:Theme.TEXT_DIM,false,font);
        float[] place=controlPlaces.computeIfAbsent(id,key->new float[2]);place[0]=x+w/2;place[1]=y+BUTTON/2;visibleActions.add(id);
        gui.hit(x,y,w,BUTTON,(button,mx,my)->{if(button==0)action.run();});
    }

    private void drawPlayer(DrawContext ctx,Module target,float x,float y,float w,float h) {
        drawPlayer(ctx,target,x,y,w,h,Float.NaN);
    }
    private void drawPlayer(DrawContext ctx,Module target,float x,float y,float w,float h,float feetY) {
        var mc=MinecraftClient.getInstance();
        var skin = mc.player.getSkin();
        if(target instanceof dev.maro.module.impl.misc.SkinChanger changer){
            changer.preparePreview();skin=changer.previewSkin(mc.player.getSkin());
        }
        var state=playerState.update(mc.player,mc.getRenderTickCounter().getTickProgress(false),skin,target);
        ((PlayerPreviewData)state).maro$setPreview(target,seconds,playbackStarted);
        state.id=ENTITY_ID;state.light=LightmapTextureManager.MAX_LIGHT_COORDINATE;
        state.shadowPieces.clear();state.outlineColor=0;state.displayName=null;state.nameLabelPos=null;
        state.bodyYaw=180+turn.update(angle);state.relativeHeadYaw=0;state.pitch=0;
        state.pose=net.minecraft.entity.EntityPose.STANDING;state.sleepingDirection=null;
        state.isInSneakingPose=false;state.sneaking=false;state.isSwimming=false;state.isGliding=false;
        state.leaningPitch=0;state.hasVehicle=false;state.usingRiptide=false;state.touchingWater=false;
        state.deathTime=0;state.hurt=false;state.onFire=false;state.shaking=false;state.flipUpsideDown=false;
        state.age=(float)seconds*20;
        state.limbSwingAnimationProgress=(float)seconds*5;
        state.limbSwingAmplitude=playbackStarted?.55f:0;
        state.limbAmplitudeInverse=playbackStarted?.26f:0;
        if(target instanceof dev.maro.nathan.modules.SpinBot)state.bodyYaw+=(float)(seconds*EffectSamples.number(target,"spin speed",360))*(EffectSamples.mode(target,"direction","Clockwise").equals("Clockwise")?1:-1);
        if(state instanceof dev.maro.render.emote.EmoteRenderState emote){
            if(target instanceof Emotes&&playbackStarted){float t=(float)(seconds*EffectSamples.number(target,"Speed",1));float blend=t>selectedEmote.length()&&!selectedEmote.loops()?Math.max(0,1-(t-selectedEmote.length())/.28f):Math.min(1,t/.18f);emote.maro$setEmote(selectedEmote,selectedEmote.loops()?t:Math.min(t,selectedEmote.length()),blend);}
            else emote.maro$setEmote(null,0,0);
        }
        var standing=mc.player.getDimensions(net.minecraft.entity.EntityPose.STANDING);
        state.width=standing.width()/state.baseScale;state.height=standing.height()/state.baseScale;state.baseScale=1;state.spectator=false;
        state.invisible=false;state.invisibleToPlayer=false;
        if(target instanceof CustomModel custom){
            ((CharacterHolder)state).maro$setCharacter(custom.ready(custom.selected(otherModel)));
            preparedCharacters=true;
        }else if(target instanceof SkinAccessories || target instanceof dev.maro.module.impl.misc.SkinChanger || target instanceof dev.maro.nathan.modules.Hats || target instanceof Acrobatics){
            ((CharacterHolder)state).maro$setCharacter(null);
        }
        // Previews stand still unless they show movement; an emote bends the default skin too.
        ((CharacterHolder)state).maro$setMotion(playbackStarted?4.3f:0,0,true);
        dev.maro.render.character.DefaultSkins.wear(state);
        var worn=CustomModel.worn(state);
        float modelHeight=worn==null?1.9f:worn.model.maxY*worn.model.scale*CustomModel.sizeFactor()/16;
        float extent=1.8f,height=Math.max(2.1f,modelHeight+.3f),centerHeight=modelHeight/2;
        if(worn!=null){
            if(fittedModel!=worn.model){
                fittedModel=worn.model;fittedRadius=.6f;
                if(fittedModel.skinned!=null){
                    var points=fittedModel.skinned.level(0).pos;float radius=0;
                    for(int i=0;i<points.length;i+=3)radius=Math.max(radius,(float)Math.hypot(points[i],points[i+2]));
                    fittedRadius=Math.max(.6f,radius*fittedModel.scale/16);
                }
            }
            extent=Math.max(extent,2*fittedRadius*CustomModel.sizeFactor()+.35f);
        }
        if(target instanceof SkinAccessories cosmetics){
            if(!cosmetics.wings().equals("None"))extent=Math.max(extent,3.5f*cosmetics.wingSize());
            height=Math.max(height,2.9f*Math.max(1,cosmetics.headSize()*.85f));
        }
        if(target instanceof dev.maro.nathan.modules.Hats hats){height=Math.max(height,hats.previewHeight());extent=Math.max(extent,2.2f);centerHeight=(height-.2f)/2;}
        if(target instanceof Acrobatics){height=Math.max(height,3.8f);extent=Math.max(extent,2.8f);centerHeight=1.4f;}
        float size=Math.min((w-14)/extent,(h-18)/height)*zoomMotion.update(zoom);
        if(Float.isFinite(feetY))y=feetY-modelHeight*size/2-h/2;
        // addEntity does not apply DrawContext's matrix. Transform its rectangle and scale once.
        var area=new ScreenRect(Math.round(x),Math.round(y),Math.max(1,Math.round(w)),Math.max(1,Math.round(h)))
            .transformEachVertex(ctx.getMatrices());
        float matrixScale=(float)Math.hypot(ctx.getMatrices().m00(),ctx.getMatrices().m01());
        ctx.addEntity(state,size*matrixScale,new Vector3f(0,centerHeight,0),
            new Quaternionf().rotateZ((float)Math.PI).rotateX(.08f),new Quaternionf().rotateX(.08f),
            area.getLeft(),area.getTop(),area.getRight(),area.getBottom());
        lastState=state;lastSubject=state;
    }
    private static String mode(Module module,String name,String fallback){return EffectSamples.mode(module,name,fallback);}
    private static double number(Module module,String name,double fallback){return EffectSamples.number(module,name,fallback);}
    private static boolean flag(Module module,String name,boolean fallback){return EffectSamples.flag(module,name,fallback);}
    @SuppressWarnings({"rawtypes","unchecked"})
    private void drawPet(DrawContext ctx,Pet pet,float x,float y,float w,float h){
        var mc=MinecraftClient.getInstance();String kind=mode(pet,"Pet","Wolf");
        if(previewPet==null||previewPetWorld!=mc.world||!kind.equals(previewPetKind)){
            var type=switch(kind){case "Cat"->net.minecraft.entity.EntityType.CAT;case "Fox"->net.minecraft.entity.EntityType.FOX;case "Bunny"->net.minecraft.entity.EntityType.RABBIT;case "Bee"->net.minecraft.entity.EntityType.BEE;case "Allay"->net.minecraft.entity.EntityType.ALLAY;case "Parrot"->net.minecraft.entity.EntityType.PARROT;case "Axolotl"->net.minecraft.entity.EntityType.AXOLOTL;case "Slime"->net.minecraft.entity.EntityType.SLIME;case "Turtle"->net.minecraft.entity.EntityType.TURTLE;case "Panda"->net.minecraft.entity.EntityType.PANDA;case "Pig"->net.minecraft.entity.EntityType.PIG;case "Dragon"->net.minecraft.entity.EntityType.ENDER_DRAGON;default->net.minecraft.entity.EntityType.WOLF;};
            previewPet=type.create(mc.world,net.minecraft.entity.SpawnReason.COMMAND);previewPetWorld=mc.world;previewPetKind=kind;
            if(previewPet instanceof net.minecraft.entity.passive.TameableEntity tameable)tameable.setTamed(true,false);
            if(previewPet instanceof net.minecraft.entity.mob.SlimeEntity slime)slime.setSize(1,false);
            if(previewPet instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity dragon)dragon.frameTracker.tick(0,0);
        }
        if(previewPet==null)return;
        if(previewPet instanceof net.minecraft.entity.passive.PassiveEntity passive)passive.setBaby(flag(pet,"Baby",false));
        var collar=net.minecraft.util.DyeColor.PURPLE;
        try{collar=net.minecraft.util.DyeColor.valueOf(mode(pet,"Collar","Purple").toUpperCase(java.util.Locale.ROOT));}catch(IllegalArgumentException ignored){}
        previewPet.age=(int)(seconds*20);
        if(previewPet instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity dragon)dragon.lastWingPosition=dragon.wingPosition=(float)seconds;
        var renderer=mc.getEntityRenderDispatcher().getRenderer(previewPet);
        if(petRenderer!=renderer||petState==null){petRenderer=renderer;petState=renderer.createRenderState();}
        petRenderer.updateRenderState(previewPet,petState,0);var state=petState;
        if(state instanceof net.minecraft.client.render.entity.state.WolfEntityRenderState wolf){wolf.collarColor=collar;wolf.tailAngle=1.2f;}
        if(state instanceof net.minecraft.client.render.entity.state.CatEntityRenderState cat)cat.collarColor=collar;
        state.age=flag(pet,"Animate",true)?(float)seconds*20:0;
        state.light=LightmapTextureManager.MAX_LIGHT_COORDINATE;state.shadowPieces.clear();state.displayName=null;state.nameLabelPos=null;
        if(state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState living){living.bodyYaw=180+turn.update(angle);living.limbSwingAnimationProgress=(float)seconds*4;living.limbSwingAmplitude=playbackStarted?.5f:0;}
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
