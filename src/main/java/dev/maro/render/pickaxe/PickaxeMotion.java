package dev.maro.render.pickaxe;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Visual windup, strike and recovery; native mining and attack timing are unchanged. */
public final class PickaxeMotion {
    private static long previewAt;
    private static String previewName="";
    public record Pose(float x,float y,float z,float pitch,float yaw,float roll){}
    private PickaxeMotion(){}
    private static float ease(float t){t=Math.clamp(t,0,1);return t*t*t*(t*(t*6-15)+10);}
    public static Pose sample(String name,float progress){
        float t=Float.isFinite(progress)?Math.clamp(progress,0,1):0;
        float wind=t<.24f?ease(t/.24f):1-ease((t-.24f)/.22f);
        float strike=t<.24f?0:t<.49f?ease((t-.24f)/.25f):1-ease((t-.49f)/.51f);
        float weight=name.equals("Leviathan Axe")?1.12f:name.equals("Star Wand")?.82f:1;
        return new Pose(-.23f*strike,.055f*wind-.12f*strike,-.04f*wind-.13f*strike,
            weight*(27*wind-73*strike),-17*wind+21*strike,weight*(-22*wind+47*strike));
    }
    public static void apply(MatrixStack pose,String name,float progress){
        var p=sample(name,progress);pose.translate(p.x,p.y,p.z);pose.translate(0,-.46,0);
        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.pitch));pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.yaw));pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.roll));pose.translate(0,.46,0);
    }
    public static void preview(String name){previewName=name;previewAt=System.nanoTime();}
    public static float previewProgress(String name){return name.equals(previewName)?Math.clamp((System.nanoTime()-previewAt)/820_000_000f,0,1):0;}
}
