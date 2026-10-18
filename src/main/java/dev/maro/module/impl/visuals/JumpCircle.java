package dev.maro.module.impl.visuals;

import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.nathan.audio.SpotifyMedia;
import dev.maro.nathan.audio.SpotifySession;
import dev.maro.nathan.render.SpotifyHudAnimation;
import dev.maro.render.esp.Renderer3D;
import dev.maro.setting.*;
import dev.maro.util.ColorUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import java.util.*;

/** Adapted from Anubis JumpCirclesModule (4ldenz, GPL-3.0-only); Maro logo and native lifecycle. */
public final class JumpCircle extends Module {
    private static final int SEGMENTS=64, MAX_CIRCLES=24;
    private static final double TAU=Math.PI*2;
    private static final double[] COS=new double[65],SIN=new double[65];
    static {for(int i=0;i<=64;i++){COS[i]=Math.cos(i*TAU/64);SIN[i]=Math.sin(i*TAU/64);}}
    private final ModeSetting colorMode=add(new ModeSetting("Color Mode","Use the client accent or your own color","Accent","Accent","Custom"));
    private final ColorSetting color=add(new ColorSetting("Custom Color","Ring and logo glow color",0xFFAA65FF).visible(()->colorMode.is("Custom")));
    private final NumberSetting radius=add(new NumberSetting("Radius","Maximum ring radius",1.2,.5,3,.1).suffix("m"));
    private final NumberSetting lifetime=add(new NumberSetting("Lifetime","Smooth expansion and fade duration",1,.3,3,.1).suffix("s"));
    private final BooleanSetting logo=add(new BooleanSetting("Logo","Draw your Maro logo inside the ring",true));
    private final BooleanSetting logoGlow=add(new BooleanSetting("Logo Glow","Soft glow behind the Maro logo",true).visible(logo::get));
    private final ModeSetting trigger=add(new ModeSetting("Trigger","When to leave a circle","Jump","Jump","Land","Both"));
    private final ModeSetting who=add(new ModeSetting("Players","Whose jumps leave rings","Self","Self","Everyone"));
    private final BooleanSetting spotifySync=add(new BooleanSetting("Spotify Sync","Pulse to live Spotify bass from the shared Windows output feed",false).onChange(v->syncMedia()));
    private final BooleanSetting beatRipples=add(new BooleanSetting("Beat Ripples","Leave a smaller ring on bass hits while standing",true).visible(spotifySync::get));
    private final NumberSetting sensitivity=add(new NumberSetting("Beat Strength","Live audio response",1.5,.5,3,.1).suffix("x").visible(spotifySync::get));
    private final List<SettingSection> sections=List.of(
        SettingSection.of("Circles",trigger,who,radius,lifetime),
        SettingSection.of("Appearance",colorMode,color,logo,logoGlow),
        SettingSection.of("Spotify",spotifySync,beatRipples,sensitivity));
    private final double[] ringX=new double[MAX_CIRCLES],ringY=new double[MAX_CIRCLES],ringZ=new double[MAX_CIRCLES];
    private final float[] facingX=new float[MAX_CIRCLES],facingZ=new float[MAX_CIRCLES],sizeFactor=new float[MAX_CIRCLES];
    private final long[] born=new long[MAX_CIRCLES];
    private int head,count,emitted,tick;
    private boolean wasGrounded;
    private static final class Lift {double x,y,z;float yaw;int airTicks=6,seen;boolean judged=true,grounded,seenGround;}
    private final Map<Integer,Lift> lifts=new HashMap<>();
    private final SpotifyHudAnimation music=new SpotifyHudAnimation();
    private final float[] audioBands=new float[7];
    private ClientWorld world;
    private SpotifyMedia media;
    private long frameAt;
    private double lastBeatAt=-100,energyFloor,previousEnergy;

    public JumpCircle(){super("Jump Circle","Anubis-style fading jump rings with your Maro logo and optional Spotify beat sync",Category.VISUALS);}
    @Override public List<SettingSection> getSettingSections(){return sections;}
    @Override protected void onEnable(){reset();syncMedia();}
    @Override protected void onDisable(){reset();if(media!=null){SpotifySession.release();media=null;}}
    private void reset(){head=count=tick=0;lifts.clear();world=null;frameAt=0;energyFloor=previousEnergy=0;lastBeatAt=-100;music.reset(false);}
    private void syncMedia(){
        boolean wanted=isEnabled()&&spotifySync.get();
        if(wanted&&media==null){media=SpotifySession.acquire();frameAt=0;music.reset(false);}
        else if(!wanted&&media!=null){SpotifySession.release();media=null;music.reset(false);}
    }
    private void syncWorld(){if(world!=mc.world){reset();world=mc.world;wasGrounded=mc.player!=null&&mc.player.isOnGround();}}
    private static boolean canLeaveRing(PlayerEntity p){return !p.isSpectator()&&!p.getAbilities().flying&&!p.hasVehicle()&&!p.isTouchingWater()&&!p.isClimbing()&&!p.isGliding()&&p.hurtTime==0;}
    public void onJump(LivingEntity entity){
        if(!isEnabled()||!inGame()||entity!=mc.player||trigger.is("Land")||!canLeaveRing(mc.player))return;
        syncWorld();spawn(entity.getX(),entity.getY(),entity.getZ(),entity.getYaw(),false);
    }
    private void spawn(double x,double y,double z,float yaw,boolean beat){
        if(count==MAX_CIRCLES){head=(head+1)%MAX_CIRCLES;count--;}
        int slot=(head+count)%MAX_CIRCLES;
        ringX[slot]=x;ringY[slot]=y+.015;ringZ[slot]=z;
        facingX[slot]=(float)-Math.sin(Math.toRadians(yaw));facingZ[slot]=(float)Math.cos(Math.toRadians(yaw));
        born[slot]=System.nanoTime();sizeFactor[slot]=beat?.65f:1;count++;emitted++;
    }
    private void emit(double x,double y,double z,double now,boolean beat){spawn(x,y,z,mc.player.getYaw(),beat);}
    private long life(int slot){return (long)(lifetime.get()*1e9*sizeFactor[slot]);}
    private void expire(long now){while(count>0&&now-born[head]>=life(head)){head=(head+1)%MAX_CIRCLES;count--;}}
    @Override public void onTick(){
        syncMedia();if(!inGame()){if(world!=null)reset();return;}syncWorld();expire(System.nanoTime());tick++;
        boolean down=mc.player.isOnGround();
        if(!wasGrounded&&down&&!trigger.is("Jump")&&canLeaveRing(mc.player))spawn(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYaw(),false);
        wasGrounded=down;
        if(!who.is("Everyone")){lifts.clear();return;}
        for(PlayerEntity p:world.getPlayers())if(p!=mc.player){
            Lift lift=lifts.computeIfAbsent(p.getId(),id->new Lift());lift.seen=tick;
            if(p.isOnGround()){
                if(!lift.grounded && lift.seenGround && !trigger.is("Jump") && canLeaveRing(p))spawn(p.getX(),p.getY(),p.getZ(),p.getYaw(),false);
                lift.x=p.getX();lift.y=p.getY();lift.z=p.getZ();lift.yaw=p.getYaw();lift.airTicks=-1;lift.judged=false;lift.grounded=lift.seenGround=true;
            }else {lift.grounded=false;if(!lift.judged){
                if(p.getY()-lift.y>.2){lift.judged=true;if(!trigger.is("Land")&&canLeaveRing(p))spawn(lift.x,lift.y,lift.z,lift.yaw,false);}
                else if(++lift.airTicks>=6)lift.judged=true;
            }}
        }
        if(tick%100==0)lifts.values().removeIf(lift->lift.seen!=tick);
    }

    private double updateMusic(double now) {
        long nanos = System.nanoTime();
        double dt = frameAt == 0 ? 1.0 / 60 : Math.max(0, Math.min(.1, (nanos - frameAt) / 1e9));
        frameAt = nanos;
        boolean playing = media != null && media.state().available() && media.state().playing();
        float[] measured = playing ? media.audioLevels() : null;
        double energy = 0;
        for (int i = 0; i < audioBands.length; i++) {
            audioBands[i] = measured != null && i < measured.length ? measured[i] : 0;
            if (i < 3 && Float.isFinite(audioBands[i])) energy += Math.max(0, audioBands[i]) * (i == 0 ? .5 : .25);
        }
        music.step(dt, playing, audioBands, false, false, false, false, false);
        double previousFloor = energyFloor;
        energyFloor += (energy - energyFloor) * (1 - Math.exp(-3 * dt));
        if (playing && beatRipples.get() && !mc.isPaused() && mc.player.isOnGround()
                && energy > .08 / sensitivity.get() && energy > previousFloor + .045 / sensitivity.get()
                && energy - previousEnergy > .025 / sensitivity.get()
                && now - lastBeatAt >= 4) {
            var pos = mc.player.getLerpedPos(mc.getRenderTickCounter().getTickProgress(false));
            emit(pos.x, pos.y, pos.z, now, true); lastBeatAt = now;
        }
        previousEnergy = energy;
        return spotifySync.get() ? Math.min(1, music.beat() * sensitivity.get()) : 0;
    }

    public void render(Renderer3D renderer,float tickDelta){
        if(!inGame()||world!=mc.world)return;
        long nanos=System.nanoTime();expire(nanos);
        if(count==0&&!spotifySync.get())return;
        double beat=updateMusic(world.getTime()+tickDelta);
        if(count==0)return;
        renderer.throughWalls(false);
        int base=colorMode.is("Custom")?color.get():Theme.accent();
        int rgb=base&0xFFFFFF,core=ColorUtil.lerp(0xFF000000|rgb,0xFFFFFFFF,.45f);
        double max=radius.get()*(1+beat*.09);
        for(int n=0;n<count;n++){
            int slot=(head+n)%MAX_CIRCLES;
            double x=ringX[slot],y=ringY[slot],z=ringZ[slot];
            if(renderer.camera().squaredDistanceTo(x,y,z)>16384)continue;
            long age=nanos-born[slot];double t=Math.clamp((double)age/life(slot),0,1);
            double fade=(1-t)*(1-t)*Math.min(1,age/80_000_000.0);
            if(fade<=.004)continue;
            double r=max*sizeFactor[slot]*(.18+.82*(1-Math.pow(1-t,3)));
            double lineWidth=(.025+.02*max)*(1+beat*.3);
            double halo=(.08+.08*max)*(1-.35*t),inner=Math.max(.001,r-lineWidth),outer=r+lineWidth;
            if(logo.get()){
                double half=inner*.68;
                if(logoGlow.get())drawLogo(renderer,slot,half*1.18,alpha(rgb,.55*fade),true);
                drawLogo(renderer,slot,half,alpha(0xFFFFFF,.95*fade),false);
            }
            band(renderer,x,y,z,Math.max(0,inner-halo*.7),inner,alpha(rgb,0),alpha(rgb,.55*fade));
            band(renderer,x,y,z,inner,r,alpha(rgb,.55*fade),alpha(core,.95*fade));
            band(renderer,x,y,z,r,outer,alpha(core,.95*fade),alpha(rgb,.55*fade));
            band(renderer,x,y,z,outer,outer+halo,alpha(rgb,.55*fade),alpha(rgb,0));
        }
    }
    private void drawLogo(Renderer3D renderer,int slot,double half,int tint,boolean glow){
        double fx=facingX[slot],fz=facingZ[slot];
        renderer.jumpLogo(ringX[slot],ringY[slot]+.0003,ringZ[slot],(fx+fz)*half,(fz-fx)*half,(fx-fz)*half,(fz+fx)*half,tint,glow);
    }
    private static void band(Renderer3D renderer,double x,double y,double z,double r0,double r1,int c0,int c1){
        for(int i=0;i<SEGMENTS;i++)renderer.gradientQuad(
            x+COS[i]*r0,y,z+SIN[i]*r0,x+COS[i+1]*r0,y,z+SIN[i+1]*r0,
            x+COS[i+1]*r1,y,z+SIN[i+1]*r1,x+COS[i]*r1,y,z+SIN[i]*r1,c0,c0,c1,c1);
    }
    private static int alpha(int rgb,double value){return rgb&0xFFFFFF|Math.clamp((int)Math.round(value*255),0,255)<<24;}
    public int circleCount(){return count;}
    public int emittedCount(){return emitted;}
    public double beatLevel(){return music.beat();}
}
