package dev.maro.render.character.designs;

import dev.maro.render.character.kit.*;
import java.util.ArrayList;
import java.util.List;

/** Original stylized portraits. Each has individual proportions, facial structure and clothing. */
public final class PortraitCharacter extends CharacterDesign {
    private static final int INK=0xFF202329, WHITE=0xFFF2EEE6, GOLD=0xFFDCB45B;
    private static final float HEAD_DROP=.7f;
    private enum Fit { TANK, BASKETBALL, FOOTBALL, TEE, SHIRT, SUIT, TRACK, BOXING }
    private enum Hair { BALD, CROP, CURLS, SIDE, SWEPT, FRINGE, TWISTS, LOCS, GRAY }
    private record Look(int skin,int hairColor,Hair hair,int beard,Fit fit,int top,int bottom,int trim,
                        float shoulders,float muscle,float jaw,float faceWidth,float nose,float eyeSpace,
                        String word,String number,boolean glasses,boolean chain,boolean cap) {}
    private final Look look;

    private PortraitCharacter(String id,String name,Look look) {super(id,name);this.look=look;}
    private static PortraitCharacter portrait(String id,String name,int skin,int hairColor,Hair hair,int beard,Fit fit,
        int top,int bottom,int trim,float shoulders,float muscle,float jaw,float faceWidth,float nose,float eyes,
        String word,String number,boolean glasses,boolean chain,boolean cap) {
        return new PortraitCharacter(id,name,new Look(skin,hairColor,hair,beard,fit,top,bottom,trim,
            shoulders,muscle,jaw,faceWidth,nose,eyes,word,number,glasses,chain,cap));
    }
    public static List<CharacterDesign> roster() {
        return List.of(
            portrait("hitler_historical","Hitler (Historical)",0xFFD6B392,0xFF43372E,Hair.SIDE,0,Fit.SUIT,0xFF746B5B,0xFF514A40,WHITE,4.25f,.15f,1,1,.35f,1,"","",false,false,false),
            portrait("jewish_casual","Jewish Skin",0xFFD5AD8A,0xFF3E3127,Hair.SIDE,0,Fit.SHIRT,0xFF496E96,0xFF293E55,WHITE,4.25f,.25f,1,1,.35f,1,"","",false,false,false),
            portrait("arnold","Arnold Schwarzenegger",0xFFC9946E,0xFF5A3F2C,Hair.SWEPT,0,Fit.TANK,0xFF55775B,0xFF253542,WHITE,5.1f,1.0f,1.12f,1.04f,.37f,1.0f,"GYM","",false,false,false),
            portrait("david_laid","David Laid",0xFFE1B592,0xFF40352F,Hair.FRINGE,0,Fit.TANK,0xFF252932,0xFF444B58,WHITE,4.65f,.72f,1.05f,.92f,.32f,.95f,"LIFT","",false,false,false),
            portrait("lebron","LeBron James",0xFF986247,0xFF282321,Hair.CROP,2,Fit.BASKETBALL,0xFFEFC448,0xFFEFC448,0xFF714394,4.8f,.75f,1.12f,1.02f,.43f,1.0f,"LA","23",false,false,false),
            portrait("ishowspeed","IShowSpeed",0xFF8B563D,0xFF242221,Hair.TWISTS,0,Fit.FOOTBALL,0xFFC44046,0xFF275345,0xFFE5C55C,4.2f,.38f,.94f,.94f,.35f,.98f,"PORTUGAL","7",false,false,false),
            portrait("kai_cenat","Kai Cenat",0xFF9C694B,0xFF292422,Hair.LOCS,1,Fit.TEE,WHITE,0xFF375577,0xFFCAB274,4.25f,.35f,.96f,1.02f,.37f,1.02f,"AMP","",false,true,false),
            portrait("jeffrey_epstein","Jeffrey Epstein",0xFFD4AF95,0xFF837C75,Hair.GRAY,0,Fit.SHIRT,0xFF8CA8BF,0xFF576475,WHITE,4.3f,.15f,1.08f,1.04f,.41f,.98f,"","",false,false,false),
            portrait("p_diddy","P. Diddy",0xFFAA7555,0xFF282422,Hair.CROP,1,Fit.SUIT,0xFFE8E4DB,0xFFE1DED5,WHITE,4.55f,.35f,1.03f,1.01f,.4f,1.02f,"","",false,true,false),
            portrait("enslaved_historical","Enslaved Person (Historical)",0xFFC49B7D,0xFF54443A,Hair.SIDE,0,Fit.SHIRT,0xFFC8B89D,0xFF6D6254,0xFF9A8A73,4.25f,.3f,.98f,.97f,.35f,1.0f,"","",false,false,false),
            portrait("michael_jordan","Michael Jordan",0xFFA46D50,0xFF302623,Hair.BALD,0,Fit.BASKETBALL,0xFFC73B46,0xFFC73B46,INK,4.55f,.52f,1.08f,1.02f,.38f,.96f,"CHICAGO","23",false,false,false),
            portrait("kobe_bryant","Kobe Bryant",0xFFB77E58,0xFF302723,Hair.CROP,1,Fit.BASKETBALL,0xFF674398,0xFF674398,0xFFE8BD48,4.45f,.48f,.94f,.98f,.37f,.98f,"LA","24",false,false,false),
            portrait("shaquille_oneal","Shaquille O'Neal",0xFF8D5C43,0xFF2D2522,Hair.BALD,1,Fit.BASKETBALL,0xFFE9BC49,0xFFE9BC49,0xFF764C9F,5.25f,1.0f,1.18f,1.12f,.44f,1.06f,"LA","34",false,false,false),
            portrait("stephen_curry","Stephen Curry",0xFFD2A175,0xFF44312A,Hair.CURLS,1,Fit.BASKETBALL,0xFF3768B4,0xFF3768B4,0xFFE8C456,4.25f,.35f,.9f,.94f,.32f,.98f,"BAY","30",false,false,false),
            portrait("kevin_durant","Kevin Durant",0xFF9A694A,0xFF2D2522,Hair.CROP,1,Fit.BASKETBALL,0xFF30313B,0xFF30313B,0xFFE6D0A2,4.15f,.2f,.87f,.90f,.41f,1.01f,"HOOPS","35",false,false,false),
            portrait("lionel_messi","Lionel Messi",0xFFD6AB89,0xFF5B4030,Hair.SIDE,2,Fit.FOOTBALL,WHITE,0xFF292E39,INK,4.15f,.35f,1.02f,.96f,.37f,1.01f,"ARGENTINA","10",false,false,false),
            portrait("cristiano_ronaldo","Cristiano Ronaldo",0xFFD1A078,0xFF392D25,Hair.SWEPT,0,Fit.FOOTBALL,0xFFBA3843,0xFF28594A,0xFFE5C55C,4.45f,.52f,1.10f,.96f,.36f,.95f,"PORTUGAL","7",false,false,false),
            portrait("mike_tyson","Mike Tyson",0xFF99674A,0xFF302623,Hair.CROP,0,Fit.BOXING,INK,INK,WHITE,4.9f,.9f,1.15f,1.05f,.39f,1.0f,"","",false,false,false),
            portrait("bruce_lee","Bruce Lee",0xFFDDB48B,0xFF292724,Hair.FRINGE,0,Fit.TRACK,0xFFE5C24B,0xFFE5C24B,INK,4.1f,.3f,1.01f,.94f,.32f,1.04f,"","",false,false,false),
            portrait("the_rock","Dwayne Johnson",0xFFC59167,0xFF39302B,Hair.BALD,1,Fit.TANK,0xFF292C33,0xFF454D59,0xFFCFC9BF,5.15f,.95f,1.13f,1.04f,.42f,1.0f,"IRON","",false,false,false),
            portrait("john_cena","John Cena",0xFFD5AB85,0xFF6C4C32,Hair.CROP,0,Fit.TEE,0xFFD86E36,0xFF41658A,0xFF93C350,4.95f,.8f,1.16f,1.06f,.37f,.96f,"NEVER","",false,false,true));
    }
    @Override protected void define(Rig r) {
        r.outline(INK,.06f).occlusion(.17f).flatLight(.3f);
        r.scale(switch(id()) {
            case "shaquille_oneal"->1.1f;case "kevin_durant"->1.08f;case "lebron"->1.05f;
            case "kai_cenat"->.91f;case "lionel_messi"->.95f;case "mike_tyson"->.97f;default->1;
        });
        torso(r);legs(r);arms(r);head(r);accessories(r);
    }
    private static Shape oval(float x,float y,float z,float a,float b,float c,int color) {return Shapes.ellipsoid(x,y,z,a,b,c).color(color);}
    private static Shape box(float x,float y,float z,float a,float b,float c,float round,int color) {return Shapes.box(x,y,z,a,b,c,round).color(color);}
    private static Shape line(float ax,float ay,float az,float bx,float by,float bz,float radius,int color) {return Shapes.capsule(ax,ay,az,bx,by,bz,radius).color(color);}
    private static Shape join(List<Shape> shapes) {return Shapes.union(shapes.toArray(Shape[]::new));}
    private Paint cloth(int color) {return Paint.of(color).grain(.35f,.025f);}
    private Paint skin() {return Paint.vertical(Colors.shade(look.skin,.94f),24,look.skin,30).grain(.22f,.015f);}
    private boolean sleeveless() {return look.fit==Fit.TANK || look.fit==Fit.BASKETBALL || look.fit==Fit.BOXING;}
    private void torso(Rig r) {
        float chest=2.95f+look.muscle*.65f,depth=1.75f+look.muscle*.25f;
        Shape core=PortraitSculpt.loft(0,0,
            12.8f,2.75f,1.7f,1.65f,14.3f,2.45f,1.55f,1.55f,
            16.6f,2.50f+look.muscle*.15f,1.70f,1.65f,19.3f,chest,depth,1.90f,
            21.4f,chest+.20f,depth,1.80f,22.0f,chest+.12f,1.60f,1.55f,
            23.0f,1.05f,.82f,.80f);
        if(look.fit==Fit.BOXING) {
            r.part(Bone.BODY).pivot(0,23,0).shape(core.paint(skin())).resolution(.65f);
            var muscles=new ArrayList<Shape>();
            for(int side:new int[]{-1,1}) {
                muscles.add(PortraitSculpt.loft(side*1.45f,1.2f,
                    18.9f,1.0f,.50f,.25f,20.1f,1.45f,.65f,.35f,21.2f,1.18f,.40f,.25f).color(look.skin));
                for(int i=0;i<3;i++)muscles.add(box(side*.68f,17.6f-i*1.1f,1.59f,.59f,.44f,.19f,.15f,look.skin));
            }
            r.extra("chest_anatomy",Bone.BODY).shape(join(muscles)).resolution(.5f).detail(.8f);
        } else {
            Part body=r.part(Bone.BODY).pivot(0,23,0).shape(core.paint(cloth(look.top))).resolution(.65f);
            if(look.fit==Fit.FOOTBALL && id().equals("lionel_messi"))
                body.shape(core.paint(Paint.field((x,y,z)->Math.abs(x%2.5f)<.68f?0xFF82BEDF:WHITE)));
            if(sleeveless()) {
                r.extra("neckline",Bone.BODY).shape(Shapes.union(
                    Shapes.torus(0,22.1f,0,1.2f,.16f).color(look.trim),
                    line(-chest+.25f,21.6f,1.1f,-chest+.45f,18.2f,1.4f,.17f,look.trim),
                    line(chest-.25f,21.6f,1.1f,chest-.45f,18.2f,1.4f,.17f,look.trim)))
                    .resolution(.32f).detail(.7f);
            }
            if(look.fit==Fit.SUIT || look.fit==Fit.SHIRT) {
                int collar=look.fit==Fit.SUIT?0xFFD2CEC6:look.trim;
                r.extra("collar_placket",Bone.BODY).shape(Shapes.union(
                    box(0,21.0f,1.7f,1.0f,1.0f,.24f,.16f,WHITE),
                    line(-1.2f,22.2f,1.3f,-.5f,20.4f,2.0f,.35f,collar),
                    line(1.2f,22.2f,1.3f,.5f,20.4f,2.0f,.35f,collar),
                    line(0,20.3f,1.86f,0,14,1.9f,.08f,Colors.shade(look.top,.7f))))
                    .resolution(.35f).detail(.65f);
                var buttons=new ArrayList<Shape>();
                for(int i=0;i<4;i++)buttons.add(oval(0,19.6f-i*1.6f,1.98f,.12f,.12f,.1f,WHITE));
                r.extra("shirt_buttons",Bone.BODY).shape(join(buttons)).resolution(.2f).detail(.45f).noOutline();
                if(look.fit==Fit.SUIT)r.extra("jacket_lapels",Bone.BODY).shape(Shapes.union(
                    line(-1.8f,22,1.4f,-.5f,18.8f,2,.35f,0xFFCBC8C0),
                    line(1.8f,22,1.4f,.5f,18.8f,2,.35f,0xFFCBC8C0),
                    box(-1.6f,16,1.8f,.7f,.12f,.12f,.08f,0xFFCBC8C0),
                    box(1.6f,16,1.8f,.7f,.12f,.12f,.08f,0xFFCBC8C0))).resolution(.4f);
            } else {
                r.extra("shirt_seams",Bone.BODY).shape(Shapes.union(
                    line(-2.55f,13.1f,1.4f,2.55f,13.1f,1.4f,.09f,Colors.shade(look.top,.8f)),
                    Shapes.torus(0,22.45f,0,1.05f,.17f).color(look.trim))).resolution(.4f).detail(.55f);
            }
            if(!look.word.isEmpty() || !look.number.isEmpty()) {
                Canvas front=uniform(look.word,look.number,look.trim);
                body.decal(Decal.front(front,0,18.4f,2.3f,5.6f,6.4f).depth(2,.8f).maxAngle(75).flatLight(.8f));
                if(look.fit==Fit.BASKETBALL || look.fit==Fit.FOOTBALL)body.decal(
                    Decal.back(uniform(surname(),look.number,look.trim),0,18.5f,-1.95f,5.6f,6.4f).depth(2,.8f).maxAngle(75).flatLight(.8f));
            }
        }
        if(look.fit==Fit.TRACK)for(int side:new int[]{-1,1})r.extra("track_side_"+side,Bone.BODY)
            .shape(line(side*2.7f,21,-.1f,side*2.55f,13,0,.22f,look.trim)).resolution(.4f);
    }
    private String surname() {
        return switch(id()) {
            case "lebron"->"JAMES";case "michael_jordan"->"JORDAN";case "kobe_bryant"->"BRYANT";
            case "shaquille_oneal"->"ONEAL";case "stephen_curry"->"CURRY";case "kevin_durant"->"DURANT";
            case "lionel_messi"->"MESSI";case "cristiano_ronaldo"->"RONALDO";default->"SPEED";
        };
    }
    private void legs(Rig r) {
        boolean shorts=look.fit==Fit.BASKETBALL || look.fit==Fit.FOOTBALL || look.fit==Fit.BOXING;
        float thigh=1.25f+look.muscle*.3f;
        for(int side:new int[]{-1,1}) {
            float x=side*1.55f;Bone leg=side<0?Bone.RIGHT_LEG:Bone.LEFT_LEG;
            var shapes=new ArrayList<Shape>();
            shapes.add(PortraitSculpt.loft(x,0,
                1.9f,.68f,.67f,.70f,3.6f,.80f,.80f,.95f,5.0f,.92f,.82f,1.04f,
                6.4f,.88f,.93f,.82f,8.0f,thigh*.90f,1.10f,1.15f,
                10.3f,thigh,1.32f,1.28f,11.9f,thigh,1.30f,1.25f).color(shorts?look.skin:look.bottom));
            if(shorts) {
                shapes.add(box(x,10.3f,0,thigh+.12f,2.15f,1.5f,.4f,look.bottom));
                shapes.add(box(x,8.25f,0,thigh+.13f,.18f,1.51f,.13f,look.trim));
                shapes.add(Shapes.cone(x,4.7f,0,.8f,x,1.9f,0,.76f).color(id().equals("lionel_messi")?0xFF82BEDF:look.fit==Fit.FOOTBALL?look.trim:WHITE));
            }
            int shoe=look.fit==Fit.SUIT?0xFF504A43:look.fit==Fit.SHIRT?0xFF67594A:WHITE;
            shapes.add(box(x,1.0f,.65f,1.08f,.65f,1.85f,.24f,shoe));
            shapes.add(box(x,.35f,.7f,1.13f,.25f,1.9f,.2f,INK));
            r.part(leg).pivot(x,12,0).shape(join(shapes)).joint(x,6.3f,0,.6f).resolution(.65f);
            var detail=new ArrayList<Shape>();
            for(int i=0;i<3;i++)detail.add(line(x-.5f,1.75f,.45f+i*.35f,x+.5f,1.75f,.45f+i*.35f,.07f,Colors.shade(shoe,.6f)));
            detail.add(line(x+side*.85f,1.2f,-.2f,x+side*.9f,1.1f,1.0f,.12f,look.trim));
            if(look.fit==Fit.TRACK)detail.add(line(x+side*1.0f,11.2f,0,x+side*.75f,2.2f,0,.2f,look.trim));
            r.extra("shoe_laces_"+side,leg).shape(join(detail)).resolution(.25f).detail(.55f).noOutline();
            if(!shorts)r.extra("pocket_seam_"+side,leg).shape(Shapes.union(
                line(x-side*.6f,11.6f,1.25f,x+side*.7f,9.6f,1.25f,.07f,Colors.shade(look.bottom,1.16f)),
                line(x+side*1.0f,9.8f,0,x+side*.75f,3.0f,0,.06f,Colors.shade(look.bottom,.75f))))
                .resolution(.3f).detail(.4f).noOutline();
        }
        r.extra("waistband",Bone.BODY).shape(box(0,12.4f,0,2.9f,.35f,1.9f,.25f,look.fit==Fit.BOXING?WHITE:Colors.shade(look.bottom,.8f))).resolution(.5f);
    }
    private void arms(Rig r) {
        boolean bare=sleeveless();
        boolean shortSleeves=look.fit==Fit.FOOTBALL || look.fit==Fit.TEE;
        for(int side:new int[]{-1,1}) {
            float x=side*look.shoulders,thick=.95f+look.muscle*.42f;Bone arm=side<0?Bone.RIGHT_ARM:Bone.LEFT_ARM;
            int upper=bare?look.skin:look.top,lower=bare||shortSleeves?look.skin:look.top;
            var pieces=new ArrayList<Shape>();
            Shape anatomy=PortraitSculpt.loft(x,0,
                12.9f,.60f,.58f,.60f,14.1f,.75f,.80f,.70f,15.6f,.84f+look.muscle*.12f,.86f,.80f,
                17.0f,.76f,.78f,.70f,18.3f,thick*.84f,thick*.90f,thick*.86f,
                20.0f,thick,thick,thick*.91f,21.5f,thick,thick*.92f,thick*.92f,
                22.25f,thick*.65f,thick*.65f,thick*.65f);
            if(bare)pieces.add(anatomy.paint(skin()));
            else if(shortSleeves)pieces.add(anatomy.paint(Paint.field((px,py,pz)->py>=19.6f?look.top:look.skin)));
            else pieces.add(anatomy.paint(cloth(look.top)));
            pieces.add(line(side*3.0f,21.7f,0,x,21.3f,0,.55f+look.muscle*.15f,upper));
            if(!bare)pieces.add(box(x,shortSleeves?19.6f:13.2f,.05f,shortSleeves?thick:.63f,.10f,shortSleeves?thick:.65f,.08f,look.trim));
            r.part(arm).pivot(x,22,0).grip(x+side*.35f,11.25f,.3f).shape(join(pieces)).joint(x+side*.25f,17,0,.65f).resolution(.6f);
            float hx=x+side*.35f;
            var fingers=new ArrayList<Shape>();
            fingers.add(oval(hx,12,.15f,.75f,.9f,.5f,look.skin));
            for(int i=0;i<4;i++) {
                float fx=hx+(i-1.5f)*.32f,tip=10.5f+(i==0||i==3?.23f:0);
                fingers.add(line(fx,11.7f,.23f,fx,tip,.36f,.18f,look.skin));
                fingers.add(oval(fx,11.2f,.40f,.19f,.22f,.1f,Colors.shade(look.skin,1.05f)));
            }
            fingers.add(line(hx-side*.6f,12.3f,.15f,hx-side*.95f,11.7f,.35f,.25f,look.skin));
            r.extra("hand_fingers_"+side,arm).shape(join(fingers)).resolution(.32f).detail(.9f);
            if(id().equals("john_cena") || look.fit==Fit.BASKETBALL || look.fit==Fit.BOXING)
                r.extra("wristband_"+side,arm).shape(box(hx,12.95f,.12f,.71f,.38f,.72f,.2f,look.fit==Fit.BOXING?WHITE:look.trim)).resolution(.35f);
            if(look.fit==Fit.TRACK)r.extra("sleeve_stripe_"+side,arm).shape(line(x+side*thick,21,0,hx+side*.66f,13.2f,.1f,.17f,look.trim)).resolution(.35f);
        }
    }
    private void head(Rig r) {
        PortraitSculpt.head(r,id(),look.skin,look.hairColor,look.hair.name(),look.beard);
    }
    private void accessories(Rig r) {
        if(id().equals("jewish_casual")) {
            r.extra("kippah",Bone.HEAD).shape(oval(0,30.65f,-.65f,1.65f,.40f,1.45f,0xFF22334D).move(0,-HEAD_DROP,0)).resolution(.4f);
            for(int side:new int[]{-1,1}) {
                var curls=new ArrayList<Shape>();
                for(int i=0;i<48;i++) {
                    float t=i/48f,u=(i+1)/48f;
                    double a=t*Math.PI*6,b=u*Math.PI*6;
                    curls.add(line(side*(2.29f+.22f*(float)Math.cos(a)),29.25f-t*4.5f,.62f+.22f*(float)Math.sin(a),
                        side*(2.29f+.22f*(float)Math.cos(b)),29.25f-u*4.5f,.62f+.22f*(float)Math.sin(b),.15f,look.hairColor));
                }
                r.extra("sidecurl_"+side,Bone.HEAD).pivot(side*2.29f,29.25f-HEAD_DROP,.62f)
                    .shape(join(curls).move(0,-HEAD_DROP,0)).resolution(.25f).detail(1.1f)
                    .motion(Motion.hair().walk(1.5f,0,1.5f,1).drag(3).spring(50,9).limit(8));
            }
        }
        if(look.glasses)r.extra("sunglasses",Bone.HEAD).shape(Shapes.union(
            box(-.99f,28.4f,2.12f,.78f,.38f,.13f,.16f,INK),box(.99f,28.4f,2.12f,.78f,.38f,.13f,.16f,INK),
            line(-.24f,28.5f,2.13f,.24f,28.5f,2.13f,.09f,GOLD),
            line(-1.8f,28.5f,2.1f,-2.3f,28.6f,0,.09f,GOLD),line(1.8f,28.5f,2.1f,2.3f,28.6f,0,.09f,GOLD)).move(0,-HEAD_DROP,0))
            .resolution(.24f).detail(.8f).flatLight(.5f);
        if(look.chain) {
            var links=new ArrayList<Shape>();
            for(int i=0;i<18;i++) {
                float t=i/17f,x=-1.2f+t*2.4f,y=22.2f-(float)Math.sin(t*Math.PI)*2.6f;
                links.add(oval(x,y,1.96f,.15f,.18f,.09f,GOLD));
            }
            links.add(box(0,19.2f,2.07f,.28f,.36f,.12f,.08f,GOLD));
            r.extra("necklace",Bone.BODY).shape(join(links)).resolution(.22f).detail(.7f).noOutline();
        }
        if(look.cap)r.extra("cap",Bone.HEAD).shape(Shapes.union(
            Shapes.subtract(Shapes.ellipsoid(0,30.5f,-.3f,2.55f,1.3f,2.25f),Shapes.box(0,29.3f,0,3,.4f,3,.1f)).color(look.trim),
            oval(0,30.1f,2.3f,2.6f,.17f,1.6f,look.trim)).move(0,-HEAD_DROP,0)).resolution(.4f).detail(.6f);
    }
    private static Canvas uniform(String word,String number,int color) {
        Canvas canvas=new Canvas(256,256);
        text(canvas,word,128,32,word.length()>7?3:4,color);
        text(canvas,number,128,94,number.length()>1?10:12,color);
        return canvas;
    }
    /** Small original stitched block alphabet; no downloaded fonts or logos. */
    private static void text(Canvas canvas,String text,float cx,float y,int size,int color) {
        float x=cx-(text.length()*6-1)*size/2f;
        for(char c:text.toCharArray()) {
            String glyph=switch(c) {
                case '0'->"01110/11011/11011/11011/11011/11011/01110";case '1'->"00100/01100/00100/00100/00100/00100/01110";
                case '2'->"01110/10001/00001/00110/01000/10000/11111";case '3'->"11110/00001/00001/01110/00001/00001/11110";
                case '4'->"00010/00110/01010/10010/11111/00010/00010";case '5'->"11111/10000/10000/11110/00001/00001/11110";
                case '6'->"01110/10000/10000/11110/10001/10001/01110";case '7'->"11111/00001/00010/00100/01000/01000/01000";
                case '8'->"01110/10001/10001/01110/10001/10001/01110";case '9'->"01110/10001/10001/01111/00001/00001/01110";
                case 'A'->"01110/10001/10001/11111/10001/10001/10001";case 'B'->"11110/10001/10001/11110/10001/10001/11110";
                case 'C'->"01111/10000/10000/10000/10000/10000/01111";case 'D'->"11110/10001/10001/10001/10001/10001/11110";
                case 'E'->"11111/10000/10000/11110/10000/10000/11111";case 'F'->"11111/10000/10000/11110/10000/10000/10000";
                case 'G'->"01111/10000/10000/10111/10001/10001/01111";
                case 'H'->"10001/10001/10001/11111/10001/10001/10001";case 'I'->"11111/00100/00100/00100/00100/00100/11111";
                case 'J'->"00111/00010/00010/00010/10010/10010/01100";case 'K'->"10001/10010/10100/11000/10100/10010/10001";
                case 'L'->"10000/10000/10000/10000/10000/10000/11111";case 'M'->"10001/11011/10101/10101/10001/10001/10001";
                case 'N'->"10001/11001/11001/10101/10011/10011/10001";case 'O'->"01110/10001/10001/10001/10001/10001/01110";
                case 'P'->"11110/10001/10001/11110/10000/10000/10000";case 'Q'->"01110/10001/10001/10001/10101/10010/01101";
                case 'R'->"11110/10001/10001/11110/10100/10010/10001";
                case 'S'->"01111/10000/10000/01110/00001/00001/11110";case 'T'->"11111/00100/00100/00100/00100/00100/00100";
                case 'U'->"10001/10001/10001/10001/10001/10001/01110";case 'V'->"10001/10001/10001/10001/10001/01010/00100";
                case 'W'->"10001/10001/10001/10101/10101/11011/10001";case 'X'->"10001/10001/01010/00100/01010/10001/10001";
                case 'Y'->"10001/10001/01010/00100/00100/00100/00100";case 'Z'->"11111/00001/00010/00100/01000/10000/11111";
                default->"00000/00000/00000/00000/00000/00000/00000";
            };
            String[] rows=glyph.split("/");
            for(int row=0;row<7;row++)for(int col=0;col<5;col++)if(rows[row].charAt(col)=='1')
                canvas.fill(Shape2D.box(x+(col+.5f)*size,y+(row+.5f)*size,size*.48f,size*.48f,size*.10f),color);
            x+=size*6;
        }
    }
}
