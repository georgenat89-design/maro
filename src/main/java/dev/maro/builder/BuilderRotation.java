package dev.maro.builder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/** One native yaw/pitch controller shared by walking, peeking and interactions. */
final class BuilderRotation {
    private final MinecraftClient mc=MinecraftClient.getInstance();
    private float yawVelocity,pitchVelocity,yawLimit=12,pitchLimit=8,spentYaw,spentPitch;
    private int tick,lastUse=-100,turned=-100;
    private boolean smooth=true;
    void configure(boolean smooth,float speed){this.smooth=smooth;yawLimit=Math.min(speed,12);pitchLimit=Math.min(speed,8);}
    void begin(int tick){if(this.tick==tick)return;this.tick=tick;spentYaw=spentPitch=0;}
    void reset(){yawVelocity=pitchVelocity=0;lastUse=turned=-100;spentYaw=spentPitch=0;}
    private float velocity(float current,float error,float limit){
        // Brake before reaching the target; retain momentum across walking/aiming changes.
        float desired=Math.copySign(Math.min(limit,Math.min((float)Math.sqrt(2*Math.abs(error)),Math.abs(error)*.45f)),error);
        return MathHelper.clamp(current+MathHelper.clamp(desired-current,-2,2),-limit,limit);
    }
    boolean turn(float yaw,float pitch){
        pitch=MathHelper.clamp(pitch,-90,90);
        if(turned!=tick){
            if(lastUse<tick-1)yawVelocity=pitchVelocity=0;
            float yawError=MathHelper.wrapDegrees(yaw-mc.player.getYaw()),pitchError=pitch-mc.player.getPitch();
            yawVelocity=smooth?velocity(yawVelocity,yawError,yawLimit):MathHelper.clamp(yawError,-yawLimit,yawLimit);
            pitchVelocity=smooth?velocity(pitchVelocity,pitchError,pitchLimit):MathHelper.clamp(pitchError,-pitchLimit,pitchLimit);
            apply(yawVelocity,pitchVelocity);turned=lastUse=tick;
        }
        float yawError=MathHelper.wrapDegrees(yaw-mc.player.getYaw()),pitchError=pitch-mc.player.getPitch();
        if(Math.abs(yawError)<=.35&&Math.abs(pitchError)<=.35&&Math.abs(yawVelocity)<=.75&&Math.abs(pitchVelocity)<=.75&&finish(yaw,pitch)){yawVelocity=pitchVelocity=0;return true;}
        return false;
    }
    private void apply(float yaw,float pitch){
        yaw=MathHelper.clamp(yaw,-Math.max(0,yawLimit-spentYaw),Math.max(0,yawLimit-spentYaw));
        pitch=MathHelper.clamp(pitch,-Math.max(0,pitchLimit-spentPitch),Math.max(0,pitchLimit-spentPitch));
        mc.player.setYaw(mc.player.getYaw()+yaw);mc.player.setPitch(MathHelper.clamp(mc.player.getPitch()+pitch,-90,90));
        spentYaw+=Math.abs(yaw);spentPitch+=Math.abs(pitch);
    }
    /** Only tiny physics corrections may finish the exact native ray before publication. */
    boolean finish(float yaw,float pitch){
        float dy=MathHelper.wrapDegrees(yaw-mc.player.getYaw()),dp=pitch-mc.player.getPitch();
        if(Math.abs(dy)>2||Math.abs(dp)>2||Math.abs(dy)>yawLimit-spentYaw+.0001||Math.abs(dp)>pitchLimit-spentPitch+.0001)return false;
        apply(dy,dp);return true;
    }
}
