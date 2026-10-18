package dev.maro.render.character.designs;

import dev.maro.render.character.kit.*;
import java.util.ArrayList;

/** Original articulated characters in model pixels. Fine features use separate meshes/decals. */
public final class MaroCharacter extends CharacterDesign {
    private static final int DARK=0xFF202735, WHITE=0xFFE5EDF6, CYAN=0xFF54E9EF, SKIN=0xFFE7B894;
    public MaroCharacter(String id,String name) { super(id,name); }
    @Override protected void define(Rig r) {
        r.outline(DARK,.08f).occlusion(.23f).flatLight(.25f);
        switch(id()) {
            case "astronaut" -> astronaut(r);
            case "robot" -> robot(r);
            case "ninja" -> human(r,true,false,false);
            case "frog" -> frog(r);
            case "penguin" -> penguin(r);
            case "hitler_historical" -> human(r,false,true,false);
            case "jewish_casual" -> human(r,false,false,true);
            default -> throw new IllegalArgumentException(id());
        }
    }
    private static Shape oval(float x,float y,float z,float a,float b,float c,int color) {
        return Shapes.ellipsoid(x,y,z,a,b,c).color(color);
    }
    private static Shape box(float x,float y,float z,float a,float b,float c,float round,int color) {
        return Shapes.box(x,y,z,a,b,c,round).color(color);
    }
    private static Shape line(float ax,float ay,float az,float bx,float by,float bz,float radius,int color) {
        return Shapes.capsule(ax,ay,az,bx,by,bz,radius).color(color);
    }
    private static Shape ring(float x,float y,float z,float major,float minor,int color) {
        return Shapes.torus(x,y,z,major,minor).color(color);
    }
    private static Part body(Rig r,Shape shape) { return r.part(Bone.BODY).pivot(0,23,0).shape(shape).resolution(.8f); }
    private static Part head(Rig r,Shape shape) { return r.part(Bone.HEAD).pivot(0,24,0).shape(shape).resolution(.65f).detail(1.6f); }
    private static Shape hand(float x,float y,int color,boolean glove) {
        var pieces=new ArrayList<Shape>();
        pieces.add(oval(x,y,0,.85f,1.05f,.55f,color));
        for(int i=0;i<4;i++) {
            float dx=(i-1.5f)*.38f, length=i==0||i==3?.8f:1.05f;
            pieces.add(line(x+dx,y-.4f,0,x+dx,y-.4f-length,.12f,glove?.24f:.21f,color));
        }
        float side=Math.signum(x);
        pieces.add(line(x-side*.7f,y+.35f,.05f,x-side*1.05f,y-.25f,.2f,.29f,color));
        return Shapes.union(pieces.toArray(Shape[]::new));
    }
    private static void humanArms(Rig r,int cloth,int cuff,int skin,boolean glove,float spread) {
        for(int side:new int[]{-1,1}) {
            float x=side*spread;
            Bone bone=side<0?Bone.RIGHT_ARM:Bone.LEFT_ARM;
            Shape sleeve=Shapes.blend(.22f,line(x-side*1.2f,21.6f,0,x,21.6f,0,1.0f,cloth),
                oval(x,21.6f,0,1.2f,1.4f,1.3f,cloth),
                Shapes.cone(x,21.8f,0,1.12f,x+side*.28f,17,0,.88f).color(cloth),
                Shapes.cone(x+side*.28f,17,0,.91f,x+side*.32f,13.4f,0,.76f).color(cloth));
            r.part(bone).pivot(x,22,0).grip(x+side*.32f,11.3f,.12f)
                .shape(Shapes.union(sleeve,box(x+side*.32f,13.4f,0,.8f,.45f,.82f,.25f,cuff)))
                .joint(x+side*.28f,17,0,.8f).resolution(.75f);
            r.extra("hand_"+side,bone).shape(hand(x+side*.32f,12.0f,skin,glove)).resolution(.55f).detail(.9f);
        }
    }
    private static void humanLegs(Rig r,int cloth,int shoes,float spread,boolean baggy) {
        for(int side:new int[]{-1,1}) {
            float x=side*spread;
            Shape leg=Shapes.blend(.3f,oval(x,10.5f,0,baggy?1.6f:1.35f,2.5f,1.6f,cloth),
                Shapes.cone(x,10.7f,0,baggy?1.45f:1.2f,x,6.4f,.1f,1.05f).color(cloth),
                Shapes.cone(x,6.4f,.1f,1.1f,x,2,0,.82f).color(cloth));
            r.part(side<0?Bone.RIGHT_LEG:Bone.LEFT_LEG).pivot(x,12,0).shape(Shapes.union(leg,
                oval(x,1.1f,.7f,1.25f,1.0f,2.0f,shoes),box(x,.3f,.75f,1.25f,.26f,2,.2f,DARK)))
                .joint(x,6.3f,.1f,.7f).resolution(.8f);
        }
    }
    private static void astronaut(Rig r) {
        int seam=0xFF94A1B1, gold=0xFFAE7D36;
        body(r,Shapes.union(Shapes.blend(.5f,oval(0,18.5f,0,4.2f,5.0f,2.7f,WHITE),
            oval(0,13.5f,0,3.5f,2.4f,2.5f,WHITE)),box(0,18,-3.7f,3.6f,4.5f,1.55f,.65f,seam),
            box(0,19.5f,2.6f,2.65f,1.9f,.55f,.35f,0xFFC7D0DA),ring(0,23.6f,0,2.3f,.45f,seam),
            box(0,12.9f,.1f,3.4f,.55f,2.5f,.4f,seam)));
        r.extra("chest_controls",Bone.BODY).shape(Shapes.union(
            box(-.9f,20.1f,3.18f,1.05f,.45f,.15f,.1f,DARK),
            line(.65f,20.1f,3.35f,.65f,20.1f,3.55f,.38f,CYAN),
            line(1.75f,20.1f,3.35f,1.75f,20.1f,3.55f,.38f,0xFFE28D65),
            line(-1.3f,18.8f,3.3f,-1.3f,18.8f,3.5f,.3f,DARK),
            line(0,18.8f,3.3f,0,18.8f,3.5f,.3f,DARK),
            line(1.3f,18.8f,3.3f,1.3f,18.8f,3.5f,.3f,DARK)))
            .resolution(.45f).detail(.7f).noOutline();
        Shape shell=oval(0,28,0,4.4f,4.45f,4.1f,WHITE);
        Shape visor=oval(0,28.1f,2.75f,3.65f,3.25f,1.6f,gold);
        head(r,Shapes.union(shell,visor,oval(0,28.15f,3.4f,3.3f,2.8f,1.05f,0xFF17344A),
            box(0,24.7f,1.6f,2.7f,.45f,1.3f,.45f,seam),
            oval(-1.25f,29.5f,4.15f,1.3f,.33f,.1f,0xFFB9E1EA)));
        humanArms(r,WHITE,seam,WHITE,true,5.2f);
        humanLegs(r,WHITE,seam,2.0f,true);
        for(int side:new int[]{-1,1}) {
            Bone arm=side<0?Bone.RIGHT_ARM:Bone.LEFT_ARM;
            r.extra("suit_elbow_"+side,arm).shape(ring(side*5.4f,16.6f,0,.98f,.22f,seam)).resolution(.6f);
            r.extra("suit_boot_"+side,side<0?Bone.RIGHT_LEG:Bone.LEFT_LEG).shape(
                box(side*2,1.3f,1.0f,1.4f,1.15f,2.1f,.5f,seam));
        }
        r.extra("oxygen_hose",Bone.BODY).shape(Shapes.strand(.22f,.22f,
            -2.8f,14.2f,2.5f,-4.2f,13.0f,2.2f,-4.3f,15,-1.5f,-3.2f,17.0f,-4.3f).color(DARK)).resolution(.5f).detail(.5f);
    }
    private static void robot(Rig r) {
        int plate=0xFF52667E, metal=0xFF9DAABD;
        body(r,Shapes.union(box(0,18.8f,0,3.6f,4.4f,2.15f,.85f,plate),
            oval(0,13.9f,0,2.9f,1.35f,1.8f,DARK),box(0,12.1f,0,3,.85f,1.85f,.45f,metal),
            box(0,18.6f,2.2f,2.65f,2.7f,.3f,.4f,DARK)));
        r.extra("reactor",Bone.BODY).shape(Shapes.union(
            ring(0,18.8f,2.7f,1.55f,.3f,metal).rotate(90,0,0,0,18.8f,2.7f),
            oval(0,18.8f,2.8f,1.25f,1.25f,.3f,CYAN),
            box(-1.45f,15.4f,2.35f,.75f,.16f,.2f,.1f,0xFFB593FF),
            box(1.45f,15.4f,2.35f,.75f,.16f,.2f,.1f,0xFFB593FF))).resolution(.5f).detail(.8f);
        head(r,Shapes.union(box(0,27.6f,0,3.6f,3.2f,2.85f,.85f,metal),
            box(0,27.9f,2.8f,3.05f,1.5f,.35f,.45f,DARK),
            box(0,25.4f,2.9f,1.6f,.35f,.2f,.15f,plate),
            line(0,30.7f,0,0,32.8f,0,.28f,DARK),oval(0,33.1f,0,.6f,.6f,.6f,CYAN)));
        r.extra("eyes",Bone.HEAD).shape(Shapes.union(
            box(-1.45f,28.0f,3.18f,.7f,.38f,.18f,.2f,CYAN),
            box(1.45f,28.0f,3.18f,.7f,.38f,.18f,.2f,CYAN))).resolution(.4f).detail(.65f).noOutline();
        for(int side:new int[]{-1,1}) {
            float x=side*5.1f;
            Bone arm=side<0?Bone.RIGHT_ARM:Bone.LEFT_ARM;
            r.part(arm).pivot(x,22,0).grip(x,11.3f,.1f).shape(Shapes.union(
                line(side*3.3f,21.5f,0,x,21.5f,0,.55f,metal),
                oval(x,21.5f,0,1.3f,1.3f,1.3f,DARK),box(x,19.1f,0,1.2f,2.2f,1.25f,.45f,plate),
                oval(x,16.5f,0,1.1f,1.1f,1.1f,metal),box(x,14,0,1.05f,1.7f,1.1f,.4f,plate)))
                .joint(x,16.5f,0,.6f).resolution(.7f);
            r.extra("fingers_"+side,arm).shape(hand(x,11.8f,metal,true)).resolution(.5f).detail(.8f);
            r.part(side<0?Bone.RIGHT_LEG:Bone.LEFT_LEG).pivot(side*1.85f,12,0).shape(Shapes.union(
                box(side*1.85f,9.3f,0,1.3f,2.3f,1.4f,.5f,plate),
                oval(side*1.85f,6.5f,.1f,1.1f,1.1f,1.2f,metal),
                box(side*1.85f,4,0,1.15f,1.8f,1.25f,.4f,plate),
                box(side*1.85f,1.1f,.7f,1.5f,1.0f,2.1f,.45f,metal)))
                .joint(side*1.85f,6.5f,0,.5f).resolution(.75f);
        }
    }
    private static Shape face(int skin) {
        return Shapes.blend(.35f,oval(0,28.15f,-.15f,2.65f,3.35f,2.3f,skin),
            oval(0,26.25f,.65f,2.0f,1.3f,1.65f,skin),
            oval(0,27.1f,2.1f,.35f,.65f,.6f,skin),
            oval(-2.6f,27.8f,-.05f,.42f,.8f,.6f,skin),oval(2.6f,27.8f,-.05f,.42f,.8f,.6f,skin),
            line(0,23.3f,0,0,25.1f,0,.8f,skin));
    }
    private static void human(Rig r,boolean ninja,boolean historical,boolean kippah) {
        int shirt=ninja?0xFF263044:historical?0xFF74624F:0xFF496E96;
        int pants=ninja?0xFF313A4D:historical?0xFF514A40:0xFF293E55;
        body(r,Shapes.union(Shapes.blend(.4f,oval(0,18.8f,0,3.4f,4.2f,1.95f,shirt),
            oval(0,14.1f,0,2.8f,2.0f,1.9f,shirt)),box(0,12.9f,0,2.9f,.45f,1.95f,.3f,DARK)));
        Shape hair=Shapes.subtract(oval(0,29.5f,-.4f,2.72f,2.1f,2.35f,0xFF312D2D),
            box(0,27.5f,2.1f,3,1.8f,1.3f,.4f,0xFF312D2D));
        head(r,Shapes.union(face(SKIN),hair));
        Part faceDetails=r.extra("face_detail",Bone.HEAD).shape(Shapes.union(
            oval(-1.04f,28.2f,2.05f,.48f,.25f,.18f,WHITE),oval(1.04f,28.2f,2.05f,.48f,.25f,.18f,WHITE),
            oval(-1.04f,28.2f,2.23f,.18f,.22f,.12f,DARK),oval(1.04f,28.2f,2.23f,.18f,.22f,.12f,DARK),
            line(-1.65f,28.85f,1.9f,-.65f,28.8f,2.2f,.12f,0xFF52423A),
            line(.65f,28.8f,2.2f,1.65f,28.85f,1.9f,.12f,0xFF52423A),
            line(-.55f,25.95f,2.1f,.55f,25.95f,2.1f,.1f,0xFFA16E61))).resolution(.32f).detail(1.0f).flatLight(.7f).noOutline();
        if(historical) {
            r.extra("portrait_hair",Bone.HEAD).shape(Shapes.union(
                oval(-1.1f,30,1.4f,1.65f,.8f,1.0f,0xFF312D2D).rotate(0,0,16,-1.1f,30,1.4f),
                box(0,26.2f,2.75f,.65f,.22f,.13f,.06f,0xFF312D2D))).resolution(.4f).detail(.8f);
            r.extra("coat_details",Bone.BODY).shape(Shapes.union(
                line(-1.7f,22.1f,1.65f,-.25f,19.6f,2.0f,.25f,0xFF9B8973),
                line(1.7f,22.1f,1.65f,.25f,19.6f,2.0f,.25f,0xFF9B8973),
                line(0,20.4f,2.05f,0,16.5f,2.05f,.17f,DARK),
                oval(0,15.5f,2.0f,.16f,.16f,.14f,DARK),oval(0,17.0f,2.1f,.16f,.16f,.14f,DARK)))
                .resolution(.45f).detail(.65f).noOutline();
        } else if(kippah) {
            r.extra("kippah",Bone.HEAD).shape(oval(0,31.2f,-.45f,1.9f,.55f,1.75f,0xFF22334D)).resolution(.55f);
            for(int side:new int[]{-1,1}) {
                var curls=new ArrayList<Shape>();
                int segments=48;
                for(int i=0;i<segments;i++) {
                    float t=i/(float)segments,u=(i+1)/(float)segments;
                    double a=t*Math.PI*6,b=u*Math.PI*6;
                    curls.add(line(side*(2.48f+.27f*(float)Math.cos(a)),29.1f-t*4.5f,1.1f+.27f*(float)Math.sin(a),
                        side*(2.48f+.27f*(float)Math.cos(b)),29.1f-u*4.5f,1.1f+.27f*(float)Math.sin(b),.20f,0xFF312D2D));
                }
                r.extra("sidecurl_"+side,Bone.HEAD).pivot(side*2.48f,29.1f,1.1f)
                    .shape(Shapes.union(curls.toArray(Shape[]::new))).resolution(.28f).detail(1.1f)
                    .motion(Motion.hair().walk(2,0,2,1).drag(3).spring(45,8).limit(12));
            }
            r.extra("hoodie",Bone.BODY).shape(Shapes.union(
                ring(0,22.5f,0,1.45f,.45f,0xFF3B5C81),
                line(-.55f,21.9f,2,-.65f,19.2f,2.05f,.1f,WHITE),
                line(.55f,21.9f,2,.65f,19.2f,2.05f,.1f,WHITE),
                box(0,15.8f,1.94f,1.8f,.8f,.18f,.3f,0xFF3B5C81))).resolution(.5f).detail(.7f);
        } else {
            head(r,Shapes.union(oval(0,28,0,2.9f,3.75f,2.55f,shirt),
                line(0,22.5f,0,0,25,0,.75f,shirt),
                box(0,28.25f,2.2f,2.2f,.65f,.4f,.45f,SKIN),
                box(0,26.8f,2.1f,2.4f,.75f,.5f,.4f,shirt)));
            faceDetails.shape(faceDetails.shape().move(0,0,.65f));
            r.extra("headband",Bone.HEAD).shape(Shapes.union(
                ring(0,30,0,2.5f,.3f,0xFFC55568),
                box(0,29.85f,2.25f,2.45f,.32f,.28f,.2f,0xFFC55568))).resolution(.5f);
            r.extra("scarf",Bone.HEAD).pivot(-1,26.5f,-2.3f).shape(
                Shapes.strand(.5f,.18f,-1,26.5f,-2.3f,-2,24,-3.2f,-1,19.3f,-4.0f).flatten(2,1,0,0).color(0xFFC55568))
                .resolution(.6f).motion(Motion.cloth().walk(5,0,8,1).drag(5).bend(1));
            r.extra("cloth_wrap",Bone.BODY).shape(Shapes.union(
                line(-2.6f,21.7f,1.6f,2.7f,15,1.8f,.4f,0xFF465269),
                box(0,13.2f,1.8f,2.9f,.55f,.4f,.35f,0xFFC55568))).resolution(.6f);
        }
        humanArms(r,shirt,ninja?0xFF465269:shirt,ninja?DARK:SKIN,ninja,4.45f);
        humanLegs(r,pants,DARK,1.65f,ninja);
    }
    private static Shape webbedFoot(float x,float y,float z,int color,int toes) {
        var parts=new ArrayList<Shape>();
        parts.add(oval(x,y,z,1.7f,.65f,1.8f,color));
        for(int i=0;i<toes;i++) {
            float dx=(i-(toes-1)*.5f)*.65f;
            parts.add(line(x+dx*.65f,y,z+.3f,x+dx,y,z+2.1f-Math.abs(dx)*.3f,.38f,color));
        }
        return Shapes.union(parts.toArray(Shape[]::new));
    }
    private static void frog(Rig r) {
        int green=0xFF79B954, shade=0xFF4C853F, pale=0xFFD3E599;
        r.firstPersonArms(false);
        body(r,Shapes.union(oval(0,15.2f,0,4.7f,6.1f,3.5f,green),oval(0,14.6f,2.8f,3.6f,4.7f,1.0f,pale)));
        head(r,Shapes.union(oval(0,24.1f,0,5.2f,3.75f,3.7f,green),
            oval(-3.15f,27.2f,1.05f,1.9f,2.0f,1.9f,green),oval(3.15f,27.2f,1.05f,1.9f,2.0f,1.9f,green)));
        r.extra("frog_face",Bone.HEAD).shape(Shapes.union(
            oval(-3.15f,27.3f,2.6f,1.25f,1.3f,.65f,WHITE),oval(3.15f,27.3f,2.6f,1.25f,1.3f,.65f,WHITE),
            oval(-3.15f,27.3f,3.15f,.6f,.85f,.28f,DARK),oval(3.15f,27.3f,3.15f,.6f,.85f,.28f,DARK),
            oval(-3.3f,27.65f,3.4f,.2f,.24f,.09f,WHITE),oval(3,27.65f,3.4f,.2f,.24f,.09f,WHITE),
            oval(-.9f,24.9f,3.6f,.15f,.2f,.12f,shade),oval(.9f,24.9f,3.6f,.15f,.2f,.12f,shade),
            Shapes.strand(.14f,.14f,-3.4f,23.2f,2.7f,-1.8f,22.6f,3.35f,0,22.4f,3.5f,1.8f,22.6f,3.35f,3.4f,23.2f,2.7f).color(shade)))
            .resolution(.4f).detail(1.1f).flatLight(.65f).noOutline();
        for(int side:new int[]{-1,1}) {
            Bone arm=side<0?Bone.RIGHT_ARM:Bone.LEFT_ARM;
            r.part(arm).pivot(side*4.4f,19.4f,0).grip(side*5.2f,9.6f,1).shape(
                Shapes.blend(.4f,oval(side*4.7f,17.7f,0,1.25f,2.7f,1.2f,green),
                line(side*5,16,0,side*5.2f,10.8f,.7f,.7f,green))).joint(side*5,14,0,.65f).swing(.65f,35);
            r.extra("frog_hand_"+side,arm).shape(webbedFoot(side*5.2f,9.9f,1,green,4).scale(.6f,side*5.2f,9.9f,1))
                .resolution(.55f).detail(.75f);
            r.part(side<0?Bone.RIGHT_LEG:Bone.LEFT_LEG).pivot(side*2.8f,10.5f,0).shape(Shapes.union(
                oval(side*3.3f,8.3f,0,2,2.7f,2,green),line(side*4,7,0,side*3.1f,2.4f,1.2f,1,green),
                webbedFoot(side*3.1f,1,2,green,5))).joint(side*4,6.5f,.3f,.7f).swing(.65f,35).resolution(.75f);
        }
    }
    private static void penguin(Rig r) {
        int ink=0xFF344050, orange=0xFFF4B35B;
        r.firstPersonArms(false);
        body(r,Shapes.union(oval(0,13.2f,0,6.0f,10.6f,4.25f,ink),oval(0,12.8f,3.15f,4.7f,8.7f,1.55f,WHITE)));
        head(r,Shapes.union(oval(0,25.6f,0,4.8f,5,4.1f,ink),
            oval(-1.5f,25.5f,3.1f,2.25f,2.8f,1.2f,WHITE),oval(1.5f,25.5f,3.1f,2.25f,2.8f,1.2f,WHITE),
            Shapes.cone(0,24.8f,3.5f,1.0f,0,24.5f,5.7f,.15f).color(orange)));
        r.extra("penguin_eyes",Bone.HEAD).shape(Shapes.union(
            oval(-1.7f,26.2f,4.18f,.46f,.65f,.22f,DARK),oval(1.7f,26.2f,4.18f,.46f,.65f,.22f,DARK),
            oval(-1.82f,26.45f,4.36f,.14f,.2f,.08f,WHITE),oval(1.58f,26.45f,4.36f,.14f,.2f,.08f,WHITE)))
            .resolution(.35f).detail(.8f).noOutline();
        for(int side:new int[]{-1,1}) {
            r.part(side<0?Bone.RIGHT_ARM:Bone.LEFT_ARM).pivot(side*5,20,0).grip(side*6.5f,8.5f,.5f)
                .shape(Shapes.blend(.35f,oval(side*5.8f,17.6f,0,1.3f,3.3f,1.7f,ink),
                    Shapes.cone(side*6.2f,16,0,1.2f,side*6.8f,8.1f,.7f,.3f).color(ink)))
                .swing(.45f,30).resolution(.75f);
            r.part(side<0?Bone.RIGHT_LEG:Bone.LEFT_LEG).pivot(side*2.5f,3,0)
                .shape(Shapes.union(line(side*2.5f,1.4f,0,side*2.5f,4.4f,0,.75f,ink),
                    webbedFoot(side*2.5f,.9f,1.7f,orange,3))).swing(.35f,22).resolution(.65f);
        }
        r.extra("tail",Bone.BODY).shape(Shapes.cone(0,6,-3,1.7f,0,3.5f,-6,.2f).color(ink)).resolution(.75f);
    }
}
