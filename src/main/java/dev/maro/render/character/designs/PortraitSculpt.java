package dev.maro.render.character.designs;

import dev.maro.render.character.kit.*;
import java.util.ArrayList;

/** Cross-section sculpting: continuous anatomy with cheek, jaw and brow planes. */
final class PortraitSculpt {
    private static final int INK=0xFF29241F, WHITE=0xFFE2DFD7;
    private static final float DROP=.7f;
    private PortraitSculpt() {}

    /** Sections are y, half width, front depth, back depth. The eight face planes form a bevelled section. */
    static Shape loft(float cx,float cz,float... sections) {
        float min=sections[0],max=sections[sections.length-4],w=0,f=0,b=0;
        for(int i=0;i<sections.length;i+=4) {w=Math.max(w,sections[i+1]);f=Math.max(f,sections[i+2]);b=Math.max(b,sections[i+3]);}
        final float bottom=min,top=max;
        return Shapes.field((x,y,z)->{
            int i=0;while(i+8<sections.length && y>sections[i+4])i+=4;
            float t=Math.clamp((y-sections[i])/(sections[i+4]-sections[i]),0,1);
            float width=sections[i+1]+(sections[i+5]-sections[i+1])*t;
            float front=sections[i+2]+(sections[i+6]-sections[i+2])*t;
            float back=sections[i+3]+(sections[i+7]-sections[i+3])*t;
            float ax=Math.abs(x-cx),depth=z-cz;
            float kf=.38f*width/(.65f*front),kb=.38f*width/(.65f*back);
            float d=Math.max(ax-width,Math.max(depth-front,-depth-back));
            d=Math.max(d,(ax+kf*depth-width-kf*.35f*front)/(float)Math.sqrt(1+kf*kf));
            d=Math.max(d,(ax-kb*depth-width-kb*.35f*back)/(float)Math.sqrt(1+kb*kb));
            return Math.max(d,Math.max(bottom-y,y-top))-.025f;
        },cx-w-.04f,min-.04f,cz-b-.04f,cx+w+.04f,max+.04f,cz+f+.04f);
    }
    private static final class Face {
        float width=2.12f,jaw=1.72f,chin=.88f,cheek=2.08f,forehead=2.05f,tall=0;
        float eyeY=28.25f,eyeSep=.92f,eyeW=.4f,eyeH=.15f,brow=.52f,tilt=.06f;
        float nose=.34f,projection=.60f,noseLength=1.10f,mouth=.65f,lip=.1f,ear=.43f,hairline=29.55f;
        int iris=0xFF634735,wrinkles=0;
    }
    private static Face face(String id) {
        Face p=new Face();
        switch(id) {
            case "arnold"->{p.jaw=2.02f;p.chin=1.14f;p.cheek=2.16f;p.brow=.43f;p.eyeH=.125f;p.nose=.37f;p.projection=.72f;p.mouth=.73f;p.lip=.085f;p.iris=0xFF698995;p.hairline=29.45f;}
            case "david_laid"->{p.width=1.95f;p.cheek=1.95f;p.jaw=1.72f;p.chin=.82f;p.tall=.18f;p.eyeH=.13f;p.tilt=.13f;p.nose=.29f;p.projection=.74f;p.mouth=.68f;p.lip=.14f;p.hairline=29.55f;}
            case "lebron"->{p.width=2.20f;p.cheek=2.24f;p.jaw=1.95f;p.chin=1.08f;p.tall=.18f;p.eyeW=.44f;p.eyeH=.14f;p.nose=.50f;p.projection=.60f;p.mouth=.83f;p.lip=.15f;p.brow=.47f;p.hairline=30.0f;}
            case "ishowspeed"->{p.width=2.04f;p.cheek=2.12f;p.jaw=1.65f;p.chin=.83f;p.eyeW=.42f;p.eyeH=.19f;p.tilt=.1f;p.nose=.43f;p.projection=.55f;p.mouth=.78f;p.lip=.17f;p.hairline=29.50f;}
            case "kai_cenat"->{p.width=2.14f;p.cheek=2.18f;p.jaw=1.77f;p.chin=.96f;p.tall=-.12f;p.eyeW=.43f;p.eyeH=.17f;p.nose=.45f;p.projection=.51f;p.mouth=.77f;p.lip=.15f;p.hairline=29.35f;}
            case "jeffrey_epstein"->{p.width=2.13f;p.cheek=2.18f;p.jaw=1.95f;p.chin=1.03f;p.eyeH=.105f;p.brow=.43f;p.eyeY=28.17f;p.nose=.38f;p.noseLength=1.32f;p.projection=.80f;p.mouth=.70f;p.lip=.075f;p.ear=.48f;p.wrinkles=3;p.iris=0xFF657A7E;p.hairline=30.15f;}
            case "p_diddy"->{p.width=2.16f;p.jaw=1.91f;p.chin=1.03f;p.cheek=2.13f;p.eyeH=.14f;p.tilt=.12f;p.nose=.46f;p.projection=.56f;p.mouth=.78f;p.lip=.15f;p.hairline=29.48f;}
            case "michael_jordan"->{p.width=2.16f;p.jaw=1.86f;p.chin=.96f;p.tall=.15f;p.eyeSep=.98f;p.eyeW=.43f;p.eyeH=.15f;p.nose=.43f;p.projection=.67f;p.mouth=.75f;p.lip=.13f;p.ear=.47f;}
            case "kobe_bryant"->{p.width=2.04f;p.cheek=2.16f;p.jaw=1.63f;p.chin=.81f;p.tall=.10f;p.eyeW=.43f;p.eyeH=.13f;p.tilt=.11f;p.nose=.40f;p.projection=.64f;p.mouth=.73f;p.lip=.13f;p.hairline=29.60f;}
            case "shaquille_oneal"->{p.width=2.36f;p.cheek=2.38f;p.jaw=2.15f;p.chin=1.20f;p.forehead=2.25f;p.eyeSep=1.0f;p.eyeW=.43f;p.eyeH=.12f;p.nose=.54f;p.projection=.59f;p.mouth=.86f;p.lip=.15f;p.brow=.44f;}
            case "stephen_curry"->{p.width=2.0f;p.cheek=2.06f;p.jaw=1.62f;p.chin=.80f;p.eyeW=.44f;p.eyeH=.18f;p.nose=.37f;p.projection=.60f;p.mouth=.72f;p.lip=.12f;p.iris=0xFF819087;p.hairline=29.48f;}
            case "kevin_durant"->{p.width=1.99f;p.cheek=2.04f;p.jaw=1.58f;p.chin=.81f;p.tall=.34f;p.eyeSep=.96f;p.eyeW=.43f;p.eyeH=.15f;p.nose=.47f;p.projection=.62f;p.mouth=.72f;p.lip=.15f;p.ear=.51f;p.hairline=29.65f;}
            case "lionel_messi"->{p.width=2.10f;p.cheek=2.15f;p.jaw=1.88f;p.chin=.98f;p.eyeW=.40f;p.eyeH=.13f;p.nose=.37f;p.projection=.85f;p.noseLength=1.20f;p.mouth=.69f;p.lip=.105f;p.hairline=29.55f;}
            case "cristiano_ronaldo"->{p.width=2.04f;p.cheek=2.17f;p.jaw=1.77f;p.chin=.83f;p.eyeSep=.9f;p.eyeW=.41f;p.eyeH=.12f;p.tilt=.14f;p.nose=.30f;p.projection=.78f;p.mouth=.70f;p.lip=.12f;p.hairline=29.55f;}
            case "mike_tyson"->{p.width=2.23f;p.cheek=2.25f;p.jaw=2.03f;p.chin=1.04f;p.forehead=2.18f;p.eyeH=.115f;p.eyeSep=.91f;p.brow=.42f;p.nose=.48f;p.projection=.57f;p.mouth=.76f;p.lip=.15f;p.hairline=29.60f;}
            case "bruce_lee"->{p.width=2.02f;p.cheek=2.18f;p.jaw=1.66f;p.chin=.80f;p.eyeH=.10f;p.eyeW=.44f;p.tilt=.09f;p.nose=.30f;p.projection=.66f;p.mouth=.69f;p.lip=.11f;p.hairline=29.40f;}
            case "the_rock"->{p.width=2.16f;p.cheek=2.22f;p.jaw=2.04f;p.chin=1.05f;p.eyeW=.4f;p.eyeH=.13f;p.brow=.49f;p.nose=.43f;p.projection=.70f;p.mouth=.74f;p.lip=.12f;}
            case "john_cena"->{p.width=2.19f;p.jaw=2.12f;p.chin=1.21f;p.cheek=2.17f;p.eyeH=.12f;p.brow=.40f;p.nose=.36f;p.projection=.74f;p.mouth=.81f;p.lip=.10f;p.iris=0xFF6B8D95;p.hairline=29.55f;}
            case "hitler_historical"->{p.jaw=1.85f;p.chin=.97f;p.nose=.35f;p.projection=.77f;p.eyeH=.14f;p.iris=0xFF718C93;p.hairline=29.65f;}
            default->{}
        }
        return p;
    }
    private static Shape oval(float x,float y,float z,float a,float b,float c,int color) {return Shapes.ellipsoid(x,y,z,a,b,c).color(color);}
    private static Shape stroke(float radius,int color,float... xyz) {return Shapes.strand(radius,radius*.85f,xyz).color(color);}
    private static Shape join(ArrayList<Shape> parts) {return Shapes.union(parts.toArray(Shape[]::new));}
    static void head(Rig r,String id,int skin,int hairColor,String hair,int beard) {
        Face p=face(id);float chinY=25.05f;
        Shape skull=loft(0,-.18f,
            chinY,p.chin,1.18f,.78f,25.45f,p.jaw*.84f,1.42f,1.20f,26.15f,p.jaw,1.55f,1.55f,
            26.75f,p.width*.91f,1.73f,1.75f,27.42f,p.cheek,1.78f,1.92f,
            28.17f,p.width,1.63f,2.00f,28.75f,p.width*.98f,1.83f,2.02f,
            29.65f,p.forehead,1.67f,1.93f,30.45f+p.tall,p.width*.85f,1.37f,1.60f,
            31.03f+p.tall,p.width*.45f,.75f,.85f,31.20f+p.tall,.12f,.18f,.20f);
        for(int side:new int[]{-1,1})skull=Shapes.subtract(.025f,skull,
            Shapes.ellipsoid(side*p.eyeSep,p.eyeY,1.71f,p.eyeW+.13f,p.eyeH+.15f,.30f));
        final Shape head=skull;
        boolean fullBeard=beard==2 || id.equals("stephen_curry") || id.equals("kevin_durant");
        Paint paint=Paint.field((x,y,z)->{
            int color=skin;
            if(beard>0 && z>.5f && y<27.35f && (Math.abs(x)>p.mouth+.14f || y<26.15f)) {
                if(fullBeard || Math.abs(x)<.7f && y<26.2f)color=hairColor;
            }
            float cheek=(float)Math.exp(-Math.pow((y-27.0f)/.6f,2))*Math.min(1,Math.abs(x)/2)*.06f;
            return Colors.shade(color,1-cheek);
        }).grain(.13f,.035f);
        Shape neck=loft(0,-.12f,22.6f,.91f,.80f,.85f,23.6f,.86f,.75f,.85f,25.45f,.80f,.72f,.8f).color(skin);
        r.part(Bone.HEAD).pivot(0,24,0).shape(Shapes.union(head.paint(paint),neck).move(0,-DROP,0)).resolution(.4f).detail(2.8f);
        var features=new ArrayList<Shape>();
        for(int side:new int[]{-1,1}) {
            float ex=side*p.eyeSep,ey=p.eyeY;
            Shape2D almond=Shape2D.polygon(ex-p.eyeW,ey,ex-p.eyeW*.45f,ey+p.eyeH,
                ex+p.eyeW*.4f,ey+p.eyeH*.85f,ex+p.eyeW,ey,ex+p.eyeW*.35f,ey-p.eyeH*.7f,ex-p.eyeW*.4f,ey-p.eyeH*.8f);
            features.add(Shapes.extrude(almond,.055f,.015f).move(0,0,1.66f).color(WHITE));
            features.add(oval(ex,ey,1.73f,.13f,p.eyeH*.91f,.045f,p.iris));
            features.add(oval(ex,ey,1.77f,.063f,p.eyeH*.64f,.025f,INK));
            features.add(oval(ex-.035f,ey+.035f,1.80f,.025f,.03f,.01f,WHITE));
            int fold=Colors.shade(skin,.68f);
            features.add(stroke(.045f,fold,ex-p.eyeW,ey,1.72f,ex,ey+p.eyeH,1.73f,ex+p.eyeW,ey,1.72f));
            features.add(stroke(.035f,Colors.shade(skin,.83f),ex-p.eyeW,ey-.12f,1.69f,ex,ey-.2f,1.72f,ex+p.eyeW,ey-.1f,1.68f));
            float by=ey+p.brow+(id.equals("the_rock")&&side==1?.13f:0);
            features.add(stroke(.105f,Colors.shade(skin,.94f),ex-side*.43f,by-.05f,1.76f,ex,by+.02f,1.87f,ex+side*.45f,by-p.tilt,1.72f));
            features.add(stroke(.075f,hairColor,ex-side*.39f,by,1.90f,ex,by+.05f,1.96f,ex+side*.43f,by-p.tilt,1.82f));
            float earX=side*(p.width+.12f);
            features.add(oval(earX,27.7f,-.03f,p.ear,.68f,.34f,skin));
            features.add(oval(earX+side*.09f,27.72f,.25f,p.ear*.52f,.43f,.09f,Colors.shade(skin,.77f)));
            features.add(stroke(.07f,skin,earX-side*.08f,28.15f,.29f,earX+side*.15f,27.9f,.32f,earX,27.35f,.29f));
        }
        float noseBottom=27.05f-(p.noseLength-1.10f)*.5f,tip=1.72f+p.projection;
        Shape nose=loft(0,0,noseBottom-.17f,p.nose*.55f,1.91f,.06f,
            noseBottom,p.nose,tip-.08f,.06f,noseBottom+.22f,p.nose*.91f,tip,.06f,
            noseBottom+.55f,p.nose*.56f,2.05f,.06f,28.62f,.13f,1.78f,.06f).color(skin);
        features.add(nose);
        features.add(oval(-p.nose*.64f,noseBottom,tip-.12f,.075f,.04f,.04f,Colors.shade(skin,.48f)));
        features.add(oval(p.nose*.64f,noseBottom,tip-.12f,.075f,.04f,.04f,Colors.shade(skin,.48f)));
        float my=26.55f;int lip=Colors.mix(skin,0xFF885650,.28f);
        features.add(stroke(p.lip,lip,-p.mouth,my,1.64f,-p.mouth*.32f,my+.07f,1.81f,0,my+.02f,1.84f,p.mouth*.32f,my+.07f,1.81f,p.mouth,my,1.64f));
        features.add(stroke(p.lip*.9f,Colors.shade(lip,1.06f),-p.mouth*.85f,my-.08f,1.70f,0,my-.15f,1.86f,p.mouth*.85f,my-.08f,1.70f));
        features.add(stroke(.025f,Colors.shade(lip,.52f),-p.mouth*.9f,my-.015f,1.76f,0,my-.02f,1.91f,p.mouth*.9f,my-.015f,1.76f));
        if(beard>0)features.add(stroke(.065f,hairColor,-p.mouth*.82f,my+.25f,1.73f,-.12f,my+.32f,1.90f,.12f,my+.32f,1.90f,p.mouth*.82f,my+.25f,1.73f));
        if(id.equals("hitler_historical"))features.add(Shapes.box(0,my+.28f,1.93f,.42f,.16f,.07f,.025f).color(hairColor));
        for(int i=0;i<p.wrinkles;i++)features.add(stroke(.025f,Colors.shade(skin,.83f),-1.0f,29.12f+i*.24f,1.48f,0,29.16f+i*.24f,1.53f,1.0f,29.12f+i*.24f,1.48f));
        r.extra("face_features",Bone.HEAD).shape(join(features).move(0,-DROP,0)).resolution(.35f).detail(2.1f).flatLight(.28f).noOutline();
        if(fullBeard) {
            Shape volume=head.scale(1.045f,0,27.1f,0).intersect(Shapes.below(26.1f)).intersect(Shapes.plane(0,0,-.25f,0,0,-1));
            r.extra("beard_jaw",Bone.HEAD).shape(volume.paint(Paint.of(hairColor).grain(.10f,.17f)).move(0,-DROP,0)).resolution(.4f).detail(.8f);
        }
        hair(r,id,p,head,hairColor,hair);
    }
    private static void hair(Rig r,String id,Face p,Shape skull,int color,String kind) {
        if(kind.equals("BALD"))return;
        Shape shell=skull.scale(1.025f,0,28.5f,0);
        Shape cap=Shapes.field((x,y,z)->{
            float line=p.hairline;
            if(z>.3f)line+=Math.abs(x)*.13f;
            else line-=Math.min(1.3f,-z*.55f);
            if(kind.equals("GRAY") && z>.4f)line+=.35f*(1-Math.min(1,Math.abs(x)/1.8f));
            return Math.max(shell.distance(x,y,z),line-y);
        },-3,27,-3,3,32+p.tall,3);
        var shapes=new ArrayList<Shape>();shapes.add(cap);
        if(kind.equals("SWEPT") || kind.equals("SIDE") || kind.equals("GRAY") || kind.equals("FRINGE")) {
            float height=kind.equals("SWEPT")?.32f:kind.equals("FRINGE")?.18f:.08f;
            Shape mass=loft(0,-.25f,29.7f,1.90f,1.7f,1.75f,30.4f,2.04f,1.72f,1.78f,
                30.95f+p.tall+height,1.78f,1.40f,1.5f,31.30f+p.tall+height,.95f,.75f,.80f,
                31.45f+p.tall+height,.1f,.12f,.15f);
            if(kind.equals("GRAY"))mass=mass.intersect(Shapes.plane(0,30.1f,1.0f,0,-.30f,1));
            shapes.add(mass);
            for(int i=0;i<13;i++) {
                float t=i/12f,x=-1.7f+t*3.4f;
                if(kind.equals("FRINGE")) {
                    float tip=29.35f+(float)Math.abs(Math.sin(i*1.4f))*.38f;
                    shapes.add(Shapes.bezier(x-.3f,30.85f+p.tall,1.1f,x+.18f,30.55f,1.85f,x+.12f,tip,1.72f,.14f,.055f));
                } else if(kind.equals("GRAY")) {
                    float wave=(float)Math.sin(t*Math.PI);
                    shapes.add(Shapes.bezier(x,30.20f,1.25f,x-.25f,30.65f+wave*.35f,1.45f,x-.40f,30.95f+wave*.16f,-.15f,.12f,.045f));
                } else {
                    // Locks sweep across the crown, instead of forming a row of vertical spikes.
                    shapes.add(Shapes.bezier(x,29.85f+Math.abs(x)*.13f,1.55f,x-.65f,31.05f+height,1.70f,
                        x-.45f,31.05f+p.tall,-.35f,.14f,.055f));
                }
            }
        }
        if(kind.equals("CURLS"))for(int row=0;row<6;row++)for(int col=0;col<11;col++) {
            float theta=(col-5)*.27f,z=(row-2.5f)*.56f,x=(float)Math.sin(theta)*2.03f,y=30.1f+(float)Math.cos(theta)*1.35f;
            shapes.add(Shapes.ellipsoid(x,y,z,.23f,.24f,.23f));
        }
        Paint hairPaint=Paint.field((x,y,z)->Colors.shade(color,1+.10f*(float)Math.sin(x*20+z*7+y*2))).grain(.08f,.13f);
        r.extra("hair",Bone.HEAD).shape(join(shapes).paint(hairPaint).move(0,-DROP,0)).resolution(.4f).detail(1.3f);
        if(kind.equals("TWISTS") || kind.equals("LOCS"))for(int side:new int[]{-1,1}) {
            var locks=new ArrayList<Shape>();
            for(int i=0;i<11;i++) {
                double a=(i+.5)*Math.PI/11;float x=side*(.85f+(float)Math.sin(a)*1.35f),z=(float)Math.cos(a)*1.8f;
                float length=kind.equals("LOCS")?3.0f+(i%4)*.24f:1.35f+(i%4)*.15f;
                float outward=kind.equals("TWISTS")?.42f:.12f;
                locks.add(Shapes.strand(.17f,.12f,x,30.65f,z,x+side*outward,30.05f,z+.12f,x+side*outward*.7f,30.6f-length,z+.27f));
            }
            r.extra("hair_locks_"+side,Bone.HEAD).pivot(side*1.6f,30.5f-DROP,0).shape(join(locks).paint(hairPaint).move(0,-DROP,0))
                .resolution(.4f).detail(1.0f).motion(Motion.hair().walk(1.5f,0,1.5f,1).drag(2).spring(50,9).limit(8));
        }
    }
}
