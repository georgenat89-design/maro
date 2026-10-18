package dev.maro.anubis.inspect;

import java.util.HashMap;
import java.util.Map;

/** Original, continuous knife geometry with ground blades, machined fittings and separate moving parts. */
final class InspectionKnifeModels {
    record Part(KnifeMesh mesh, int hinge) { }
    record Model(Part[] parts, float grip, boolean reverse, float pivot, int axis) { }
    private static final Map<String, Model> CACHE = new HashMap<>();
    static Model get(String name) { return CACHE.computeIfAbsent(name, InspectionKnifeModels::make); }
    private static Model make(String name) {
        return switch (name) {
            case "Talon" -> hooked(true);
            case "Karambit" -> hooked(false);
            case "Ursus" -> ursus();
            case "Falchion" -> falchion();
            case "Butterfly" -> butterfly();
            case "Bayonet" -> bayonet();
            case "Bowie" -> bowie();
            case "Stiletto" -> stiletto();
            case "Kukri" -> kukri();
            case "Huntsman" -> huntsman();
            case "Maro Rift" -> rift();
            case "Katana" -> katana();
            default -> skeleton();
        };
    }
    private static Model single(KnifeMesh.Builder b, float grip, boolean reverse) {
        return new Model(new Part[]{new Part(b.build(),0)},grip,reverse,reverse?-.16f:.006f,2);
    }
    private static Model katana(){
        var b=new KnifeMesh.Builder();
        blade(b,new float[][]{{.014f,0,.017f,.004f},{.10f,.002f,.018f,.004f},{.24f,.012f,.018f,.0035f},
            {.36f,.030f,.016f,.003f},{.44f,.05f,.014f,.0025f},{.49f,.070f,.0003f,.0002f}});
        handle(b,new float[][]{{-.20f,0,.014f,.011f},{-.18f,0,.017f,.013f},{-.015f,0,.017f,.013f},{0,0,.014f,.011f}},4);
        for(int i=0;i<14;i++)band(b,-.184f+i*.012f,0,.0175f,.0135f,.003f,3);
        ring(b,.003f,0,0,.041f,.014f,.005f,8);
        band(b,-.014f,0,.018f,.014f,.008f,8);band(b,-.195f,0,.017f,.014f,.006f,8);
        rivet(b,-.052f,0,.014f,.003f,8);rivet(b,-.146f,0,.014f,.003f,8);
        return single(b,-.10f,false);
    }
    private static Model skeleton() {
        var b = new KnifeMesh.Builder();
        blade(b,new float[][]{{.032f,0,.018f,.004f},{.065f,.002f,.025f,.004f},
                {.17f,.006f,.024f,.0035f},{.23f,.013f,.014f,.002f},{.278f,.018f,.0004f,.0002f}});
        ring(b,.006f,0,0,.027f,.017f,.006f,1);
        // Open tang rails and a lanyard hole remain actual holes through the mesh.
        bar(b,-.14f,.015f,-.012f,.016f,.005f,4);
        bar(b,-.14f,-.017f,-.012f,-.016f,.005f,4);
        ring(b,-.145f,0,0,.020f,.009f,.0055f,1);
        for(float x:new float[]{-.10f,-.057f}) bar(b,x,-.015f,x+.009f,.016f,.005f,1);
        for(float x:new float[]{.07f,.088f,.106f}) rivet(b,x,.011f,.0045f,.002f,4);
        return single(b,-.078f,false);
    }
    private static Model ursus() {
        var b = new KnifeMesh.Builder();
        blade(b,new float[][]{{.005f,0,.022f,.004f},{.035f,.001f,.027f,.0045f},
                {.18f,.002f,.027f,.004f},{.23f,.014f,.014f,.0025f},{.269f,.028f,.0004f,.0002f}});
        handle(b,new float[][]{{-.166f,0,.012f,.009f},{-.15f,0,.019f,.012f},
                {-.045f,0,.019f,.012f},{-.012f,0,.016f,.011f},{0,0,.014f,.009f}},7);
        bar(b,-.007f,-.035f,.003f,.034f,.008f,5);
        for(int i=0;i<11;i++) band(b,-.145f+i*.011f,0,.0192f,.0122f,.0018f,4);
        rivet(b,-.133f,0,.013f,.003f,5); rivet(b,-.029f,0,.012f,.003f,5);
        ring(b,-.155f,0,0,.012f,.005f,.0095f,5);
        return single(b,-.085f,false);
    }
    private static Model falchion() {
        var b = new KnifeMesh.Builder();
        blade(b,new float[][]{{.005f,0,.024f,.004f},{.04f,-.001f,.032f,.005f},
                {.12f,.004f,.037f,.0045f},{.185f,.019f,.028f,.0035f},
                {.235f,.041f,.014f,.002f},{.27f,.062f,.0004f,.0002f}});
        handle(b,new float[][]{{-.17f,-.012f,.010f,.008f},{-.151f,-.010f,.020f,.012f},
                {-.08f,-.005f,.023f,.013f},{-.026f,0,.019f,.011f},{.004f,0,.026f,.010f}},3);
        for(float x:new float[]{-.142f,-.086f,-.024f}) rivet(b,x,-.004f,.0135f,.0035f,5);
        bar(b,-.17f,-.013f,-.15f,-.011f,.013f,5);
        return single(b,-.087f,false);
    }
    private static Model hooked(boolean talon) {
        var b = new KnifeMesh.Builder();
        float[][] curve = new float[25][4];
        for(int i=0;i<curve.length;i++) {
            float t=i/(float)(curve.length-1), a=t*(talon?1.88f:2.15f);
            curve[i]=new float[]{.014f+(talon?.175f:.157f)*(float)Math.sin(a),
                    -.008f-(talon?.145f:.143f)*(1-(float)Math.cos(a)),
                    Math.max(.0003f,(talon?.032f:.029f)*(float)Math.pow(1-t,.68)),
                    Math.max(.0002f,.0048f*(1-t*.9f))};
        }
        blade(b,curve);
        handle(b,new float[][]{{-.151f,.017f,.014f,.010f},{-.124f,.009f,.022f,.013f},
                {-.087f,.001f,.024f,.014f},{-.045f,-.005f,.021f,.013f},
                {-.012f,-.003f,.022f,.012f},{.01f,-.005f,.027f,.008f}},talon?8:3);
        ring(b,-.16f,.019f,0,.030f,.021f,.009f,talon?5:3);
        for(float x:new float[]{-.12f,-.035f}) rivet(b,x,.001f,.0145f,.003f,5);
        for(int i=0;i<6;i++) band(b,-.12f+i*.015f,.002f,.022f,.014f,.0015f,talon?5:4);
        if(talon) for(int i=0;i<5;i++) {
            float x=.028f+i*.012f;
            bar(b,x,.017f-i*.002f,x+.007f,.031f-i*.002f,.0035f,1);
        }
        return single(b,-.082f,true);
    }
    private static Model butterfly() {
        var blade = new KnifeMesh.Builder();
        blade(blade,new float[][]{{-.012f,0,.013f,.0035f},{.02f,0,.022f,.0045f},
                {.12f,.002f,.024f,.004f},{.21f,.008f,.017f,.0025f},{.262f,.018f,.0004f,.0002f}});
        rivet(blade,0,0,.006f,.006f,5);
        Part[] parts={new Part(blade.build(),0),new Part(butterflyHandle(-1),1),new Part(butterflyHandle(1),2)};
        return new Model(parts,-.105f,false,0,2);
    }
    private static KnifeMesh butterflyHandle(int side) {
        var b = new KnifeMesh.Builder();
        // Separate slotted handle rails, curved tips, pins and a latch.
        float z=side*.012f;
        for(int i=0;i<3;i++) {
            float from=-.21f+i*.067f,to=from+.067f;
            prism(b,from,-.013f,z-.004f,to,-.006f,z+.004f,3);
            prism(b,from,.006f,z-.004f,to,.013f,z+.004f,3);
            prism(b,from,-.006f,z-.004f,from+.014f,.006f,z+.004f,5);
        }
        prism(b,-.012f,-.013f,z-.004f,.008f,.013f,z+.004f,5);
        ring(b,-.209f,0,z,.013f,.004f,.005f,5);
        rivet(b,0,0,z+side*.006f,.0055f,5);
        if(side==1) prism(b,-.227f,.008f,z-.004f,-.207f,.017f,z+.004f,5);
        return b.build();
    }

    private static Model bayonet() {
        var b=new KnifeMesh.Builder();
        blade(b,new float[][]{{.003f,0,.024f,.0048f},{.04f,0,.026f,.005f},{.23f,.003f,.025f,.0045f},
                {.295f,.009f,.016f,.0028f},{.35f,.016f,.0003f,.0002f}});
        handle(b,new float[][]{{-.175f,0,.016f,.010f},{-.16f,0,.020f,.013f},{-.04f,0,.021f,.013f},
                {-.015f,0,.017f,.011f},{0,0,.017f,.010f}},3);
        for(int i=0;i<10;i++)band(b,-.153f+i*.0115f,0,.021f,.0138f,.0013f,4);
        guard(b,-.005f,.045f,.009f);ring(b,-.004f,.047f,0,.020f,.014f,.006f,5);
        pommel(b,-.171f,.018f,.012f);fuller(b,.045f,.19f,.006f,.0052f,.0035f);
        rivet(b,-.138f,0,.0145f,.003f,5);rivet(b,-.04f,0,.0145f,.003f,5);
        return single(b,-.087f,false);
    }
    private static Model bowie() {
        var b=new KnifeMesh.Builder();
        blade(b,new float[][]{{.008f,0,.028f,.006f},{.04f,-.003f,.039f,.0065f},{.17f,.003f,.046f,.006f},
                {.235f,.020f,.039f,.005f},{.29f,.042f,.022f,.003f},{.34f,.059f,.0003f,.0002f}});
        handle(b,new float[][]{{-.183f,-.007f,.014f,.011f},{-.158f,-.004f,.023f,.015f},
                {-.075f,.001f,.025f,.016f},{-.027f,.003f,.021f,.014f},{0,0,.022f,.013f}},7);
        guard(b,.002f,.050f,.012f);pommel(b,-.18f,.023f,.016f);
        for(float x:new float[]{-.147f,-.101f,-.05f})rivet(b,x,0,.016f,.004f,8);
        band(b,-.017f,.002f,.022f,.014f,.006f,8);fuller(b,.06f,.14f,.009f,.0067f,.004f);
        return single(b,-.091f,false);
    }
    private static Model stiletto() {
        var b=new KnifeMesh.Builder();
        blade(b,new float[][]{{0,0,.013f,.004f},{.03f,0,.015f,.0045f},{.17f,0,.012f,.0038f},
                {.24f,0,.007f,.0025f},{.294f,0,.0002f,.00015f}});
        handle(b,new float[][]{{-.177f,0,.010f,.008f},{-.16f,0,.015f,.011f},{-.04f,0,.017f,.011f},
                {-.012f,0,.013f,.009f},{0,0,.015f,.010f}},8);
        guard(b,-.004f,.036f,.007f);pommel(b,-.174f,.014f,.010f);
        for(float x:new float[]{-.145f,-.042f})band(b,x,0,.016f,.0115f,.007f,5);
        for(float x:new float[]{-.12f,-.064f})rivet(b,x,0,.0115f,.0028f,5);
        prism(b,-.027f,-.005f,.011f,-.017f,.005f,.014f,4);
        return new Model(new Part[]{new Part(b.build(),0)},-.085f,false,-.085f,0);
    }
    private static Model kukri() {
        var b=new KnifeMesh.Builder();
        blade(b,new float[][]{{.004f,0,.019f,.005f},{.045f,-.005f,.025f,.005f},{.10f,-.025f,.037f,.006f},
                {.17f,-.056f,.052f,.0065f},{.24f,-.075f,.052f,.0055f},{.30f,-.070f,.032f,.004f},
                {.35f,-.041f,.0003f,.0002f}});
        handle(b,new float[][]{{-.175f,.01f,.018f,.012f},{-.15f,.004f,.023f,.014f},
                {-.08f,-.002f,.020f,.013f},{-.03f,0,.023f,.014f},{.002f,0,.024f,.012f}},7);
        pommel(b,-.172f,.022f,.014f);guard(b,.004f,.034f,.009f);
        for(float x:new float[]{-.13f,-.058f})rivet(b,x,0,.0145f,.004f,8);
        for(int i=0;i<4;i++)band(b,-.12f+i*.02f,0,.022f,.014f,.001f,4);
        return new Model(new Part[]{new Part(b.build(),0)},-.086f,false,-.086f,0);
    }
    private static Model huntsman() {
        var b=new KnifeMesh.Builder();
        blade(b,new float[][]{{.004f,0,.028f,.0055f},{.04f,-.002f,.037f,.006f},{.16f,.002f,.039f,.0055f},
                {.22f,.018f,.032f,.004f},{.28f,.037f,.019f,.0027f},{.315f,.054f,.0003f,.0002f}});
        handle(b,new float[][]{{-.18f,0,.013f,.011f},{-.155f,0,.024f,.015f},{-.075f,-.003f,.023f,.015f},
                {-.028f,0,.020f,.014f},{0,0,.029f,.011f}},3);
        for(int i=0;i<7;i++)band(b,-.145f+i*.017f,-.002f,.023f,.0155f,.0015f,4);
        for(float x:new float[]{-.14f,-.038f})rivet(b,x,0,.016f,.0038f,5);
        for(int i=0;i<7;i++)prism(b,.035f+i*.013f,.031f,-.004f,.042f+i*.013f,.041f,.004f,1);
        ring(b,-.164f,0,0,.012f,.005f,.012f,5);fuller(b,.06f,.11f,.01f,.0062f,.0038f);
        return single(b,-.09f,false);
    }
    private static Model rift() {
        var b=new KnifeMesh.Builder();
        // Maro's original asymmetric tanto: split spine, blue inlay, exposed full tang.
        blade(b,new float[][]{{.012f,0,.022f,.005f},{.045f,0,.030f,.0055f},{.205f,.003f,.029f,.005f},
                {.245f,.023f,.021f,.0038f},{.285f,.047f,.0003f,.0002f}});
        handle(b,new float[][]{{-.174f,.005f,.015f,.010f},{-.145f,0,.022f,.014f},
                {-.075f,-.006f,.023f,.015f},{-.02f,0,.022f,.014f},{.009f,0,.027f,.011f}},3);
        ring(b,-.17f,.006f,0,.026f,.018f,.009f,5);
        for(int side:new int[]{-1,1}) {
            float z=side*.0157f;
            prism(b,-.14f,-.014f,z-.0006f,-.023f,-.010f,z+.0006f,6);
            // M badge cut into the grip panel, made from four narrow metal bars.
            decalBar(b,-.112f,.009f,-.112f,-.005f,z,6);
            decalBar(b,-.112f,.009f,-.103f,-.001f,z,6);
            decalBar(b,-.103f,-.001f,-.094f,.009f,z,6);
            decalBar(b,-.094f,.009f,-.094f,-.005f,z,6);
        }
        for(float x:new float[]{-.142f,-.042f})rivet(b,x,0,.0158f,.0032f,5);
        fuller(b,.044f,.155f,.006f,.0058f,.004f);
        for(int i=0;i<4;i++)prism(b,.038f+i*.014f,.026f,-.004f,.047f+i*.014f,.035f,.004f,1);
        return new Model(new Part[]{new Part(b.build(),0)},-.083f,false,-.17f,2);
    }
    private static void guard(KnifeMesh.Builder b,float x,float width,float depth) {
        handle(b,new float[][]{{x-.006f,0,width*.88f,depth*.8f},{x-.003f,0,width,depth},
                {x+.003f,0,width,depth},{x+.006f,0,width*.88f,depth*.8f}},5);
    }
    private static void pommel(KnifeMesh.Builder b,float x,float width,float depth) {
        handle(b,new float[][]{{x-.005f,0,width*.78f,depth*.8f},{x-.003f,0,width,depth},
                {x+.006f,0,width,depth},{x+.009f,0,width*.9f,depth*.9f}},5);
    }
    private static void fuller(KnifeMesh.Builder b,float x,float length,float y,float z,float width) {
        for(int side:new int[]{-1,1}) {
            float dz=side*z;
            b.quad(1,x,y-width,dz,x+length,y-width,dz,x+length-.008f,y+width,dz,x+.008f,y+width,dz);
            b.quad(5,x+.006f,y-width*.45f,dz+side*.00015f,x+length-.005f,y-width*.45f,dz+side*.00015f,
                    x+length-.009f,y-width*.1f,dz+side*.00015f,x+.009f,y-width*.1f,dz+side*.00015f);
        }
    }
    private static void decalBar(KnifeMesh.Builder b,float x,float y,float ex,float ey,float z,int ink) {
        float dx=ex-x,dy=ey-y,l=(float)Math.hypot(dx,dy),nx=-dy/l*.0009f,ny=dx/l*.0009f;
        b.quad(ink,x+nx,y+ny,z,ex+nx,ey+ny,z,ex-nx,ey-ny,z,x-nx,y-ny,z);
    }

    /** A diamond-ground blade swept through its silhouette, with polished cutting bevels. */
    private static void blade(KnifeMesh.Builder b,float[][] rows) {
        // Enough longitudinal sections for reflected light to travel along curved steel.
        var samples=new java.util.ArrayList<float[]>();
        for(int i=0;i<rows.length-1;i++) {
            float[] a=rows[i],c=rows[i+1];
            int count=Math.max(1,(int)Math.ceil(Math.hypot(c[0]-a[0],c[1]-a[1])/.012));
            for(int j=0;j<count;j++) {float t=j/(float)count;float[] r=new float[4];
                for(int k=0;k<4;k++)r[k]=a[k]+(c[k]-a[k])*t;samples.add(r);}
        }
        samples.add(rows[rows.length-1]); rows=samples.toArray(float[][]::new);
        float[][][] rings=new float[rows.length][8][3];
        for(int i=0;i<rows.length;i++) {
            float[] r=rows[i],prev=rows[Math.max(0,i-1)],next=rows[Math.min(rows.length-1,i+1)];
            float dx=next[0]-prev[0],dy=next[1]-prev[1],length=(float)Math.hypot(dx,dy);
            float nx=-dy/length,ny=dx/length,w=r[2],h=r[3];
            float[] across={-w,-w*.88f,-w*.48f,w*.54f,w,w*.54f,-w*.48f,-w*.88f};
            float[] depth={0,-h*.25f,-h,-h,0,h,h,h*.25f};
            for(int j=0;j<8;j++) rings[i][j]=new float[]{r[0]+nx*across[j],r[1]+ny*across[j],depth[j]};
        }
        connect(b,rings,new int[]{2,0,0,1,1,0,0,2});
    }
    private static void handle(KnifeMesh.Builder b,float[][] rows,int material) {
        float[][][] rings=new float[rows.length][24][3];
        for(int i=0;i<rows.length;i++)for(int j=0;j<24;j++) {
            float a=(float)(Math.PI*2*j/24),r[]=rows[i];
            rings[i][j]=new float[]{r[0],r[1]+r[2]*(float)Math.cos(a),r[3]*(float)Math.sin(a)};
        }
        int[] inks=new int[24]; java.util.Arrays.fill(inks,material); connect(b,rings,inks);
    }
    private static void connect(KnifeMesh.Builder b,float[][][] rings,int[] inks) {
        int sides=inks.length;
        for(int i=0;i<rings.length-1;i++)for(int j=0;j<sides;j++)
            quad(b,inks[j],rings[i][j],rings[i][(j+1)%sides],rings[i+1][(j+1)%sides],rings[i+1][j]);
        for(int end:new int[]{0,rings.length-1}) for(int j=1;j<sides-1;j++)
            quad(b,inks[j],rings[end][0],rings[end][end==0?j+1:j],rings[end][end==0?j:j+1],rings[end][end==0?j:j+1]);
    }
    private static void quad(KnifeMesh.Builder b,int ink,float[] a,float[] c,float[] d,float[] e) {
        b.quad(ink,a[0],a[1],a[2],c[0],c[1],c[2],d[0],d[1],d[2],e[0],e[1],e[2]);
    }
    private static void ring(KnifeMesh.Builder b,float x,float y,float z,float outer,float inner,float thick,int ink) {
        int steps=outer<.007f?12:outer<.016f?24:64;float bevel=Math.min((outer-inner)*.18f,thick*.35f);
        float[] radii={inner,inner+bevel,outer-bevel,outer,outer,outer-bevel,inner+bevel,inner};
        float[] depth={-thick+bevel,-thick,-thick,-thick+bevel,thick-bevel,thick,thick,thick-bevel};
        float[][][] rings=new float[steps+1][8][3];
        for(int i=0;i<=steps;i++)for(int j=0;j<8;j++) {
            double a=Math.PI*2*i/steps;
            rings[i][j]=new float[]{x+(float)Math.cos(a)*radii[j],y+(float)Math.sin(a)*radii[j],z+depth[j]};
        }
        int[] inks=new int[8];java.util.Arrays.fill(inks,ink);
        for(int i=0;i<steps;i++)for(int j=0;j<8;j++)
            quad(b,ink,rings[i][j],rings[i+1][j],rings[i+1][(j+1)%8],rings[i][(j+1)%8]);
    }
    private static void rivet(KnifeMesh.Builder b,float x,float y,float z,float radius,int ink) {
        for(int side:new int[]{-1,1}) {
            ring(b,x,y,side*Math.abs(z),radius,0,.0009f,ink);
            float front=side*(Math.abs(z)+.00105f);
            for(int spoke=0;spoke<3;spoke++) {
                double a=spoke*Math.PI/3;float dx=(float)Math.cos(a)*radius*.55f,dy=(float)Math.sin(a)*radius*.55f;
                float nx=-(float)Math.sin(a)*radius*.11f,ny=(float)Math.cos(a)*radius*.11f;
                b.quad(4,x-dx-nx,y-dy-ny,front,x+dx-nx,y+dy-ny,front,
                        x+dx+nx,y+dy+ny,front,x-dx+nx,y-dy+ny,front);
            }
        }
    }
    private static void band(KnifeMesh.Builder b,float x,float y,float width,float depth,float thick,int ink) {
        handle(b,new float[][]{{x-thick,y,width,depth},{x+thick,y,width,depth}},ink);
    }
    private static void bar(KnifeMesh.Builder b,float x1,float y1,float x2,float y2,float z,int ink) {
        prism(b,x1,Math.min(y1,y2)-.002f,-z,x2,Math.max(y1,y2)+.002f,z,ink);
    }
    private static void prism(KnifeMesh.Builder b,float x1,float y1,float z1,float x2,float y2,float z2,int ink) {
        b.reverseQuad(ink,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1);
        b.reverseQuad(ink,x1,y1,z2,x1,y2,z2,x2,y2,z2,x2,y1,z2);
        b.reverseQuad(ink,x1,y1,z1,x1,y1,z2,x2,y1,z2,x2,y1,z1);
        b.reverseQuad(ink,x1,y2,z1,x2,y2,z1,x2,y2,z2,x1,y2,z2);
        b.reverseQuad(ink,x1,y1,z1,x1,y2,z1,x1,y2,z2,x1,y1,z2);
        b.reverseQuad(ink,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1);
    }
}
