package dev.maro.render.pickaxe;

import java.util.Map;
import java.util.LinkedHashMap;

/** Cached geometry reconstructed from the five supplied turntable/swing recordings. */
public final class PickaxeModels {
    public static final String[] NAMES={"Driver","Merry Mint Axe","Leviathan Axe","Cold Snap","Star Wand"};
    private static final Map<String,PickaxeMesh> CACHE=new LinkedHashMap<>();
    private static final java.util.Set<String> KNOWN=java.util.Set.of(NAMES);
    private PickaxeModels(){}
    public static PickaxeMesh get(String name){if(!contains(name))throw new IllegalArgumentException("Unknown pickaxe: "+name);return CACHE.computeIfAbsent(name,PickaxeModels::build);}
    public static String key(String name){return name.toLowerCase(java.util.Locale.ROOT).replace(' ','_');}
    /** Center of the lower handle, in the original mesh coordinates. */
    public static org.joml.Vector3fc grip(String name){return switch(name){
        case "Driver" -> new org.joml.Vector3f(0,-.61f,0);
        case "Leviathan Axe" -> new org.joml.Vector3f(-.067f,-.52f,0);
        case "Cold Snap" -> new org.joml.Vector3f(-.025f,-.55f,0);
        case "Merry Mint Axe","Star Wand" -> new org.joml.Vector3f(0,-.55f,0);
        default -> throw new IllegalArgumentException("Unknown pickaxe: "+name);
    };}
    public static boolean contains(String name){return name!=null&&KNOWN.contains(name);}
    public static boolean matches(String name,String query){return name.toLowerCase(java.util.Locale.ROOT).contains(query)||name.equals("Merry Mint Axe")&&("minty minty axe".contains(query));}
    private static PickaxeMesh build(String name){var b=new PickaxeMesh.Builder();switch(name){case "Driver"->driver(b);case "Merry Mint Axe"->minty(b);case "Leviathan Axe"->leviathan(b);case "Cold Snap"->cold(b);case "Star Wand"->star(b);default->throw new IllegalArgumentException(name);}return b.build();}
    private static void path(PickaxeMesh.Builder b,double[][] p,double[] r,int color,boolean metal){b.tube(p,r,color,0,0,metal);}
    private static void line(PickaxeMesh.Builder b,double x,double y,double z,double xx,double yy,double zz,double r,int color){b.cylinder(x,y,z,xx,yy,zz,r,color,false);}
    private static void spiral(PickaxeMesh.Builder b,double low,double high,double radius,int turns,int color,double width){
        int n=turns*40;double[][] l=new double[n+1][3],r=new double[n+1][3];
        for(int i=0;i<=n;i++){double t=(double)i/n,a=t*turns*Math.PI*2,yy=low+(high-low)*t;l[i]=new double[]{Math.cos(a)*radius,yy-width/2,Math.sin(a)*radius};r[i]=new double[]{Math.cos(a)*radius,yy+width/2,Math.sin(a)*radius};}b.ribbon(l,r,color,false);
    }
    private static void driver(PickaxeMesh.Builder b){
        b.cylinder(0,-.76,0,0,.48,0,.014,0xFF171D27,true);
        // The reference has a light perforated grip, blue collar and graphite shaft.
        double[][] grip=new double[65][5];for(int i=0;i<grip.length;i++)grip[i]=new double[]{0,-.79+i*.35/64,0,.029-i*.000045,.029-i*.000045};
        b.loft(grip,48,0xFFCEDAE4,true,true,(t,a)->{double yy=t*8,aa=a/(Math.PI*2)*8+(Math.floor(yy)%2)*.5;double dy=yy-Math.rint(yy),dx=aa-Math.rint(aa);return 1-.22*Math.exp(-(dx*dx+dy*dy)*27);});
        b.cylinder(0,-.80,0,0,-.772,0,.032,0xFF202530,false);b.cylinder(0,-.451,0,0,-.436,0,.029,0xFF2E78B9,true);b.cylinder(0,-.436,0,0,-.420,0,.031,0xFF65717D,true);
        b.cylinder(0,.44,0,0,.50,0,.023,0xFF202633,true);b.cylinder(0,.47,0,0,.488,0,.025,0xFFBBCAD7,true);
        // Asymmetric, swept teardrop club head rather than a sphere or rectangular iron.
        double[][] head={{0,.485,0,.024,.026},{.025,.515,0,.060,.050},{.070,.550,0,.110,.075},{.115,.595,0,.150,.090},{.135,.645,0,.161,.094},{.132,.695,0,.146,.085},{.115,.735,0,.112,.067},{.095,.758,0,.065,.039},{.087,.765,0,.008,.008}};
        b.loft(head,40,0xFFD4DFE9,true,true,null);
        // Dark striking face with fine score lines, and the blue inset on its opposite side.
        b.blade(new double[][]{{.003,.54},{.08,.526},{.22,.57},{.287,.64},{.27,.70},{.21,.737},{.09,.748},{.01,.697},{-.02,.61}},.188,.06,0,0xFF212731,0xFF667280);
        for(int i=0;i<7;i++){double y=.563+i*.024;line(b,.027,y,.098,.22,y+.025,.098,.0013,0xFF515965);}
        double[][] l=new double[22][3],r=new double[22][3];for(int i=0;i<22;i++){double t=i/21d,x=.02+t*.205,y=.585+Math.sin(t*Math.PI)*.125;l[i]=new double[]{x,y,-.096};r[i]=new double[]{x,y+.012,-.096};}b.ribbon(l,r,0xFF287CC7,true);
    }
    private static double[][] cane(){
        double[][] p=new double[93][3];for(int i=0;i<65;i++)p[i]=new double[]{0,-.78+i*1.28/64,0};for(int i=0;i<28;i++){double a=Math.PI-(i+1)*Math.PI/28;p[i+65]=new double[]{.19+Math.cos(a)*.19,.50+Math.sin(a)*.19,0};}return p;
    }
    private static void minty(PickaxeMesh.Builder b){
        var p=cane();double[] r=new double[p.length];java.util.Arrays.fill(r,.048);b.tube(p,r,0xFF342357,0xFF399C9D,9,false);
        int[] bulbs={0xFFB44EAD,0xFF41D9D1,0xFF74E453,0xFF547AC4};
        for(int i=0;i<p.length-2;i+=2){double a=i*.48,aa=(i+2)*.48;var v=p[i];var n=p[i+2];line(b,v[0]+Math.cos(a)*.059,v[1],Math.sin(a)*.059,n[0]+Math.cos(aa)*.059,n[1],Math.sin(aa)*.059,.009,0xFF172A30);if(i%10==0){final int k=i;b.glow(()->b.sphere(v[0]+Math.cos(a)*.066,v[1],Math.sin(a)*.066,.014,.022,.014,bulbs[(k/10)%4],false));}if(i%12==0){line(b,v[0]-.044,v[1],0,v[0]-.070,v[1]+.035,.018,.004,0xFF172A30);}}
        // Layered curved flame ribbons continue around the hook, with pale green cores.
        b.glow(()->{for(int j=0;j<8;j++){double x=.005+j*.053,y=.52+Math.sin(j*Math.PI/8)*.14,h=.17+(j%3)*.036;for(int layer=0;layer<2;layer++){
            double[][] l=new double[9][3],rr=new double[9][3];for(int i=0;i<9;i++){double t=i/8d,w=.047*Math.sin((1-t)*Math.PI*.65)*(layer==0?1:.48),xx=x+Math.sin(t*5+j)*.025*t,z=Math.sin(t*4+j)*.026+(layer==0?0:.008);l[i]=new double[]{xx-w,y+t*h*(layer==0?1:.72),z};rr[i]=new double[]{xx+w,y+t*h*(layer==0?1:.72),z};}b.ribbon(l,rr,layer==0?0xFF4BDD69:0xFFC8FFAF,false);
        }}});
    }
    private static void leviathan(PickaxeMesh.Builder b){
        double[][] p=new double[35][3];double[] r=new double[35];for(int i=0;i<35;i++){double t=i/34d;p[i]=new double[]{-.075*Math.sin(t*Math.PI)-.025*t,-.82+t*1.48,0};r[i]=.025+.014*Math.sin(t*Math.PI);}
        path(b,p,r,0xFF87533D,false);
        for(int i=0;i<8;i++){double x=(i-3.5)*.006;line(b,x-.045,-.68,.032,x-.075,.34,.035,.0015,i%2==0?0xFF432A20:0xFFAD7650);}
        for(int i=0;i<15;i++){double y=-.66+i*.027,t=(y+.82)/1.48,x=-.075*Math.sin(t*Math.PI)-.025*t;b.cylinder(x,y,0,x-.001,y+.022,0,.042,i%3==0?0xFF725342:0xFF47332A,false);}
        path(b,new double[][]{{-.02,-.77,0},{-.015,-.83,0},{.033,-.85,0},{.052,-.81,0}},new double[]{.038,.035,.025,.004},0xFFBD9C62,true);
        // Deep beard and a convex cutting edge, with a short rear spike.
        b.blade(new double[][]{{-.055,.72},{.07,.75},{.18,.69},{.29,.60},{.41,.54},{.46,.56},{.48,.49},{.475,.39},{.44,.29},{.38,.21},{.28,.16},{.22,.17},{.29,.29},{.31,.40},{.29,.49},{.21,.56},{.08,.59},{-.05,.58}},.074,.065,.025,0xFF344853,0xFF90A9B3);
        b.blade(new double[][]{{-.055,.72},{-.14,.77},{-.22,.82},{-.25,.76},{-.27,.64},{-.23,.60},{-.17,.64},{-.09,.61},{-.055,.57}},.072,.075,0,0xFF344853,0xFF90A9B3);
        b.cylinder(-.027,.44,0,-.027,.72,0,.044,0xFF4A5657,true);
        b.blade(new double[][]{{-.074,.70},{.025,.70},{.016,.31},{-.025,.27},{-.064,.32}},.100,.10,0,0xFF3A4C52,0xFF8D886D);
        for(int side:new int[]{-1,1}){
            double z=side*.060;
            for(int i=0;i<3;i++){double x=-.025,y=.63-i*.105;b.sphere(x,y,z,.024,.027,.005,0xFFC5A46C,true);b.sphere(x,y,z+side*.005,.017,.020,.003,0xFF567785,true);line(b,x-.009,y,z+side*.010,x+.007,y+.012,z+side*.010,.0015,0xFFADDDE4);}
            // Engraved whorls follow the crescent; a fine inset traces the sharpened edge.
            for(int j=0;j<3;j++)for(int i=0;i<15;i++){double a=i*.20,aa=(i+1)*.20,x=.29+j*.033,y=.29+j*.062;line(b,x+Math.cos(a)*.027,y+Math.sin(a)*.037,side*.047,x+Math.cos(aa)*.027,y+Math.sin(aa)*.037,side*.047,.0014,0xFF849EA7);}
            for(int i=0;i<5;i++){double y=.31+i*.040,x=.411-i*.012;line(b,x,y,side*.045,x+.011,y+.018,side*.045,.0014,0xFF9EBBC5);line(b,x+.011,y+.018,side*.045,x+.021,y+.008,side*.045,.0014,0xFF9EBBC5);}
            // Carved handle and pommel motif, with diagonal leather lacing.
            for(int i=0;i<9;i++){double y=-.64+i*.029,x=-.067;line(b,x-.025,y,side*.036,x+.023,y+.026,side*.036,.0018,0xFFB89A71);}
            for(int i=0;i<14;i++){double a=i*.36,aa=(i+1)*.36;line(b,.012+Math.cos(a)*.021,-.79+Math.sin(a)*.029,side*.030,.012+Math.cos(aa)*.021,-.79+Math.sin(aa)*.029,side*.030,.0020,0xFF513827);}
        }
        b.sphere(-.025,.72,.062,.023,.023,.009,0xFFB8975D,true);
    }
    private static void flower(PickaxeMesh.Builder b,double x,double y,double z,double radius,int petals,int petal,int center){
        for(int i=0;i<petals;i++){double a=i*Math.PI*2/petals;double[][] l=new double[7][3],r=new double[7][3];for(int k=0;k<7;k++){double t=k/6d,dist=radius*(.15+.85*t),w=radius*.27*Math.pow(Math.sin(t*Math.PI),.55),zz=z+Math.sin(t*Math.PI)*radius*.19;l[k]=new double[]{x+Math.cos(a)*dist-Math.sin(a)*w,y+Math.sin(a)*dist+Math.cos(a)*w,zz};r[k]=new double[]{x+Math.cos(a)*dist+Math.sin(a)*w,y+Math.sin(a)*dist-Math.cos(a)*w,zz};}b.ribbon(l,r,petal,false);}
        b.sphere(x,y,z+.015,radius*.25,radius*.25,.027,center,false);
    }
    private static void cold(PickaxeMesh.Builder b){
        // A frozen branch and hanging lantern, as shown in the supplied Cold Snap clip.
        double[][] wood={{.015,-.83,0},{-.035,-.65,0},{-.015,-.40,0},{.016,-.12,0},{-.015,.13,0},{0,.40,0},{-.07,.61,0},{-.14,.73,0}};
        path(b,wood,new double[]{.015,.027,.028,.030,.036,.045,.057,.041},0xFF9F7851,false);
        path(b,new double[][]{{-.08,.63,0},{-.25,.61,0},{-.40,.50,0},{-.51,.38,0}},new double[]{.064,.056,.041,.007},0xFFC5A675,false);
        path(b,new double[][]{{.01,-.76,0},{.075,-.79,0},{.10,-.76,0}},new double[]{.023,.015,.004},0xFFBA9B71,false);
        double[][] ice=new double[15][5];for(int i=0;i<ice.length;i++){double t=i/14d;ice[i]=new double[]{Math.sin(t*9)*.012,-.77+t*1.12,0,.051+Math.sin(t*19)*.007+t*.006,.048+Math.cos(t*16)*.007};}
        b.loft(ice,7,0xFF2CACDA,false,false,null);
        for(int j=0;j<4;j++){double a=j*Math.PI/2;double[][] l=new double[14][3],r=new double[14][3];for(int i=0;i<14;i++){double t=i/13d,rad=.059+(i%3)*.003,x=Math.cos(a)*rad,y=-.72+t*1.03,z=Math.sin(a)*rad;l[i]=new double[]{x-.004,y,z};r[i]=new double[]{x+.004,y,z};}b.ribbon(l,r,j%2==0?0xFF9FEAFF:0xFF1889C4,true);}
        flower(b,-.16,.79,.018,.168,15,0xFFE6B63C,0xFFD8772B);flower(b,-.26,.66,.057,.078,5,0xFF8260CE,0xFFD8D56B);flower(b,-.01,.46,.042,.045,5,0xFF8860E6,0xFFD9D775);
        for(int side:new int[]{-1,1})b.sphere(-.15+side*.075,.67,-.005,.06,.025,.020,0xFF789B47,false);
        // Lantern suspension loop, tapered glass, metal roof and four cross-braced corners.
        double[][] loop=new double[25][3];double[] lr=new double[25];for(int i=0;i<25;i++){double a=i*Math.PI*2/24;loop[i]=new double[]{-.435+Math.cos(a)*.028,.355+Math.sin(a)*.070,0};lr[i]=.008;}path(b,loop,lr,0xFF344961,true);
        b.loft(new double[][]{{-.435,-.09,0,.057,.052},{-.435,.17,0,.085,.067}},4,0xFFCFDCE1,false,false,null);
        b.loft(new double[][]{{-.435,.16,0,.105,.087},{-.435,.22,0,.052,.044},{-.435,.24,0,.052,.044}},4,0xFF304864,true,false,null);
        b.box(-.435,.245,0,.100,.025,.080,0xFF243B55,true);b.box(-.435,-.097,0,.124,.023,.109,0xFF3C5670,true);
        for(int side:new int[]{-1,1})for(int zz:new int[]{-1,1}){line(b,-.435+side*.057,-.09,zz*.05,-.435+side*.080,.16,zz*.065,.008,0xFF31475E);}
        for(int zz:new int[]{-1,1}){line(b,-.495,-.08,zz*.058,-.355,.16,zz*.071,.007,0xFF344D67);line(b,-.375,-.08,zz*.058,-.515,.16,zz*.071,.007,0xFF344D67);}
        for(int side:new int[]{-1,1})line(b,-.435+side*.070,-.08,-.055,-.435+side*.083,.15,.067,.006,0xFF3D566F);
    }
    private static void star(PickaxeMesh.Builder b){
        b.loft(new double[][]{{0,-.81,0,.052,.052},{0,-.78,0,.052,.052},{0,-.72,0,.030,.030},{0,-.1,0,.023,.023},{0,.28,0,.026,.026},{0,.35,0,.050,.050},{0,.37,0,.026,.026},{0,.45,0,.020,.020}},24,0xFFF17EB8,false,true,null);
        b.cylinder(0,-.68,0,0,-.65,0,.037,0xFF53D5D3,true);b.cylinder(0,.375,0,0,.389,0,.048,0xFFFFA9D6,true);
        spiral(b,-.59,.08,.038,3,0xFF5AD5E8,.024);
        spiral(b,-.59,.08,.040,3,0xFFD8B4EC,.005);
        double[][] p=new double[10][2];for(int i=0;i<10;i++){double a=Math.PI/2+i*Math.PI/5,r=i%2==0?.285:.125;p[i]=new double[]{Math.cos(a)*r,.64+Math.sin(a)*r};}
        b.blade(p,.087,.15,0,0xFFF7C834,0xFFFFE583);
        for(int face:new int[]{-1,1}){
            double z=face*.049;b.sphere(-.053,.676,z,.014,.022,.004,0xFF4B3545,false);b.sphere(.053,.676,z,.014,.022,.004,0xFF4B3545,false);
            for(int i=0;i<8;i++){double a=Math.PI+i*Math.PI/8,aa=Math.PI+(i+1)*Math.PI/8;line(b,Math.cos(a)*.047,.621+Math.sin(a)*.034,z,Math.cos(aa)*.047,.621+Math.sin(aa)*.034,z,.0045,0xFF53364A);}
        }
        // Small blue bow with two loops and loose tails, instead of gold rings on a bare rod.
        for(int side:new int[]{-1,1}){
            double[][] l={{0,-.735,.033},{side*.058,-.704,.036},{side*.070,-.73,.043},{0,-.747,.040}},r={{0,-.747,.048},{side*.050,-.725,.061},{side*.060,-.747,.052},{0,-.754,.048}};b.ribbon(l,r,0xFF379DE1,false);
            b.ribbon(new double[][]{{side*.006,-.75,.044},{side*.027,-.79,.048},{side*.043,-.82,.035}},new double[][]{{side*.020,-.75,.046},{side*.040,-.786,.052},{side*.058,-.817,.035}},0xFF41C5DE,false);
        }b.sphere(0,-.746,.043,.014,.014,.010,0xFF437FD2,true);
    }
}
