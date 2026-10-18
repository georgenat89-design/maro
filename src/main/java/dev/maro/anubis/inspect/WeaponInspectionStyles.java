package dev.maro.anubis.inspect;

/** Original wrist choreography shared by vanilla weapons and the legacy knife skins. */
final class WeaponInspectionStyles {
    static final String[] STYLES={"Classic","Twirl","Edge Check","Reverse Grip","Palm Roll","Showcase","Quick Flip","Cycle"};
    private static final InspectAnimation.Clip EDGE=clip(3.0f,
        k(0),k(.18f,-.17f,.12f,.04f,48,-12,24,0,0),k(.36f,-.20f,.15f,.06f,65,-6,30,0,0),
        k(.60f,-.20f,.15f,.06f,-38,12,24,0,0),k(.82f,-.12f,.07f,.02f,-12,4,8,0,0),k(1));
    private static final InspectAnimation.Clip REVERSE=clip(2.8f,
        k(0),k(.16f,-.12f,.08f,.03f,30,-8,16,0,0),k(.34f,-.17f,.13f,.04f,42,8,22,180,0),
        k(.61f,-.17f,.13f,.04f,-30,4,18,180,0),k(.82f,-.06f,.03f,.01f,12,-4,8,360,0),k(1,0,0,0,0,0,0,360,0));
    private static final InspectAnimation.Clip PALM=clip(2.6f,
        k(0),k(.16f,-.12f,.10f,.04f,30,-8,12,0,0),k(.32f,-.16f,.14f,.05f,38,-5,24,0,170),
        k(.48f,-.16f,.14f,.05f,38,-5,24,0,360),k(.68f,-.14f,.12f,.04f,-32,8,16,0,360),k(1,0,0,0,0,0,0,0,360));
    private static final InspectAnimation.Clip SHOWCASE=clip(3.6f,
        k(0),k(.18f,-.20f,.17f,.08f,62,-8,38,0,0),k(.34f,-.22f,.18f,.08f,70,-6,40,0,0),
        k(.64f,-.22f,.18f,.08f,-58,10,32,0,0),k(.80f,-.16f,.12f,.05f,-30,4,16,0,0),k(1));
    private static final InspectAnimation.Clip QUICK=clip(1.45f,
        k(0),k(.18f,-.08f,.05f,.02f,20,-10,12,0,0),k(.43f,-.12f,.10f,.03f,30,6,24,165,0),
        k(.64f,-.10f,.07f,.02f,-18,4,12,360,0),k(1,0,0,0,0,0,0,360,0));
    private WeaponInspectionStyles() { }
    private static float[] k(float... values){return values;}
    private static InspectAnimation.Clip clip(float seconds,float[]... keys){return new InspectAnimation.Clip(keys,seconds);}
    static InspectAnimation.Clip clip(String style,boolean knife) {
        var result=switch(style){
            case "Edge Check" -> EDGE;case "Reverse Grip" -> REVERSE;case "Palm Roll" -> PALM;
            case "Showcase" -> SHOWCASE;case "Quick Flip" -> QUICK;default -> InspectAnimation.clip(style);
        };
        return knife ? KNIFE_CLIPS.computeIfAbsent(result,WeaponInspectionStyles::knifeClip):result;
    }
    private static final java.util.Map<InspectAnimation.Clip,InspectAnimation.Clip> KNIFE_CLIPS=new java.util.IdentityHashMap<>();
    private static InspectAnimation.Clip knifeClip(InspectAnimation.Clip source) {
        float[][] keys=new float[source.keys.length][];
        for(int i=0;i<keys.length;i++) {
            keys[i]=java.util.Arrays.copyOf(source.keys[i],17);
            // Legacy knife models rotate about their blade with FLIP, not vanilla's SPIN channel.
            keys[i][7]+=keys[i][8];keys[i][8]=0;
        }
        return new InspectAnimation.Clip(keys,source.seconds,source.loopEnter,source.loopExit);
    }
}
