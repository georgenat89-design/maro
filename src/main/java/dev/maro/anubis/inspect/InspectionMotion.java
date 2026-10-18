package dev.maro.anubis.inspect;

import java.util.LinkedHashMap;
import java.util.Map;

/** Hand-authored choreography. Presentation, finger pivot and airborne motion are independent. */
public final class InspectionMotion {
    public static final String[] KNIVES={"Skeleton","Talon","Ursus","Falchion","Butterfly","Karambit",
            "Bayonet","Bowie","Stiletto","Kukri","Huntsman","Maro Rift"};
    public record Pose(float x,float y,float z,float pitch,float yaw,float roll,float spin,float grip,float air) {
        public static final Pose REST=new Pose(0,0,0,0,0,0,0,0,0);
    }
    private record Routine(String name,double[][] keys) { }
    private static final Map<String,Routine[]> ROUTINES=new LinkedHashMap<>();
    private static final double[] S={0,0,0,0,0,0,0,0,0,0};
    private static final double[][][] HINGES=new double[4][][];
    private static double[] e(double spin){return k(1,0,0,0,0,0,0,spin,0,0);}
    private static double[] k(double... values){return values;}
    private static Routine r(String name,double[]... keys){return new Routine(name,keys);}
    private static void add(String knife,Routine signature,Routine alternate){ROUTINES.put(knife,new Routine[]{signature,alternate});}
    static {
        add("Skeleton",r("Guard Orbit",S,
                k(.10,-.025,.01,0,-8,-12,-12,0,.35,0),k(.23,-.09,.075,.02,-12,-24,-8,270,1,0),
                k(.34,-.10,.10,.025,-10,-35,4,360,1,0),k(.55,-.10,.10,.025,-8,30,4,360,.7,0),
                k(.72,-.065,.07,.015,4,12,8,610,1,0),k(.86,-.02,-.012,0,5,0,-5,720,.25,0),e(720)),
                r("Tang Balance",S,k(.16,-.055,.06,.025,-14,-38,18,0,0,0),
                k(.35,-.10,.115,.035,-10,-65,28,160,.45,0),k(.53,-.10,.115,.035,10,20,12,180,.45,0),
                k(.72,-.06,.075,.02,0,35,-10,320,.3,0),k(.87,-.02,-.01,0,5,0,-4,360,0,0),e(360)));
        add("Talon",r("Hook Fan",S,k(.12,-.035,.02,0,8,-18,-20,0,.5,0),
                k(.29,-.105,.085,.015,-10,-40,-12,-330,1,0),k(.40,-.12,.11,.025,-15,-52,5,-360,1,0),
                k(.63,-.12,.11,.025,-6,35,12,-360,.7,0),k(.79,-.055,.04,.01,6,5,-8,-690,1,0),
                k(.9,-.012,-.012,0,3,0,3,-720,.1,0),e(-720)),
                r("Reverse Edge",S,k(.18,-.08,.08,.02,-8,-55,-25,0,.4,0),
                k(.38,-.11,.12,.04,-10,65,-32,185,1,0),k(.60,-.11,.12,.04,8,80,-20,185,1,0),
                k(.79,-.055,.06,.015,4,20,10,350,.5,0),e(360)));
        add("Ursus",r("Balance Toss",S,k(.13,-.035,-.025,0,12,-8,6,0,.1,0),
                k(.24,-.065,.015,.015,4,-20,-6,-120,.8,.16),k(.33,-.08,.035,.025,-4,-25,-8,-265,1,.23),
                k(.44,-.10,.07,.02,-8,-32,-4,-360,.2,0),k(.64,-.10,.07,.02,-8,35,6,-360,0,0),
                k(.82,-.04,.025,.01,4,5,4,-360,0,0),e(-360)),
                r("Spine Check",S,k(.16,-.06,.07,.025,-18,-42,20,0,0,0),
                k(.36,-.115,.12,.035,-14,-70,30,0,0,0),k(.57,-.115,.12,.035,8,45,30,0,0,0),
                k(.76,-.055,.07,.02,3,20,-8,350,.75,0),k(.9,-.015,-.008,0,4,0,4,360,0,0),e(360)));
        add("Falchion",r("Palm Swivel",S,k(.14,-.06,.045,.015,-8,-25,-10,0,.15,0),
                k(.30,-.10,.09,.03,-14,-35,12,160,.65,0),k(.43,-.12,.12,.03,-8,-55,18,180,.4,0),
                k(.64,-.12,.12,.03,4,28,18,180,.4,0),k(.82,-.045,.035,.01,6,12,-8,355,.6,0),e(360)),
                r("Edge Sweep",S,k(.18,-.08,.08,.025,-15,-45,24,0,0,0),
                k(.39,-.14,.12,.035,-12,-65,38,0,0,0),k(.60,-.06,.13,.03,8,50,-16,0,0,0),
                k(.80,-.02,.05,.01,4,12,-10,-350,.7,0),e(-360)));
        add("Butterfly",r("Latch Cascade",S,k(.10,-.03,.025,.005,-6,-12,-14,0,0,0),
                k(.26,-.085,.07,.02,14,25,12,0,0,0),k(.42,-.10,.105,.025,-12,-35,-18,0,0,0),
                k(.60,-.10,.105,.025,-6,38,12,0,0,0),k(.78,-.065,.055,.015,8,8,-12,0,0,0),
                k(.91,-.015,-.008,0,3,0,4,0,0,0),e(0)),
                r("Reverse Fan",S,k(.14,-.045,.04,.01,8,18,15,0,0,0),
                k(.32,-.105,.095,.025,-10,-40,-24,0,0,0),k(.54,-.12,.12,.03,-6,45,22,0,0,0),
                k(.74,-.07,.065,.015,6,-20,-16,0,0,0),k(.9,-.01,-.01,0,3,0,4,0,0,0),e(0)));
        add("Karambit",r("Finger Figure Eight",S,k(.10,-.02,.015,0,6,-15,-18,0,.4,0),
                k(.25,-.08,.08,.02,-10,-38,-28,300,1,0),k(.37,-.11,.11,.025,-18,-55,-8,360,1,0),
                k(.57,-.11,.11,.025,-10,30,12,360,1,0),k(.74,-.07,.05,.01,8,-15,25,650,1,0),
                k(.88,-.01,-.012,0,3,0,-4,720,.15,0),e(720)),
                r("Reverse Catch",S,k(.13,-.045,.03,.01,8,20,18,0,.55,0),
                k(.29,-.10,.09,.025,-8,-48,20,-310,1,0),k(.46,-.12,.12,.035,-18,-65,5,-360,.8,0),
                k(.66,-.12,.12,.035,6,40,-12,-360,.6,0),k(.85,-.03,.015,0,4,10,-5,-710,.7,0),e(-720)));
        add("Bayonet",r("Guard Salute",S,k(.17,-.065,.055,.02,-16,-30,-12,0,0,0),
                k(.35,-.12,.11,.035,-20,-60,-6,0,0,0),k(.55,-.12,.11,.035,5,40,18,0,0,0),
                k(.73,-.07,.075,.025,12,10,-12,300,.8,0),k(.88,-.01,-.015,0,3,0,4,360,0,0),e(360)),
                r("Rifleman's Roll",S,k(.15,-.04,.025,.01,12,-15,-18,0,.2,0),
                k(.31,-.09,.075,.025,-10,-38,-14,-180,.8,0),k(.50,-.12,.11,.03,-8,-55,10,-180,.5,0),
                k(.69,-.09,.09,.025,6,35,20,-180,.5,0),k(.87,-.02,.01,0,4,0,-6,-360,.2,0),e(-360)));
        add("Bowie",r("Heavy Balance",S,k(.20,-.075,.055,.015,-12,-25,20,0,0,0),
                k(.40,-.13,.10,.025,-18,-60,28,0,0,0),k(.61,-.13,.10,.025,8,55,12,0,0,0),
                k(.80,-.055,.055,.015,5,18,-14,0,0,0),k(.91,-.008,-.009,0,2,0,3,0,0,0),e(0)),
                r("Clip Point Turn",S,k(.17,-.055,.03,.01,8,-20,-15,0,0,0),
                k(.36,-.105,.08,.025,-8,-45,-22,155,.6,0),k(.56,-.12,.105,.035,-12,35,-10,180,.5,0),
                k(.76,-.065,.065,.02,5,25,15,340,.6,0),e(360)));
        add("Stiletto",r("Needle Roll",S,k(.13,-.04,.035,.01,-8,-20,14,0,0,0),
                k(.28,-.08,.075,.025,-12,-45,20,180,0,0),k(.46,-.11,.11,.035,-6,-60,10,180,0,0),
                k(.65,-.11,.11,.035,6,40,-10,180,0,0),k(.82,-.04,.035,.01,4,10,-6,355,0,0),e(360)),
                r("Spring Lock Check",S,k(.16,-.055,.055,.025,-20,-38,18,0,0,0),
                k(.36,-.10,.105,.04,-24,-58,12,-90,0,0),k(.55,-.10,.105,.04,-6,45,-14,-90,0,0),
                k(.76,-.055,.06,.025,5,20,-6,-350,0,0),e(-360)));
        add("Kukri",r("Weighted Arc",S,k(.20,-.075,.045,.015,-8,-30,24,0,0,0),
                k(.42,-.14,.085,.025,-16,-55,36,0,0,0),k(.62,-.10,.12,.035,10,50,-20,0,0,0),
                k(.82,-.045,.05,.01,6,15,-8,0,0,0),e(0)),
                r("Spine Balance",S,k(.18,-.06,.07,.025,-14,-25,14,0,0,0),
                k(.37,-.12,.12,.035,-20,-65,20,175,0,0),k(.61,-.12,.12,.035,8,30,-16,180,0,0),
                k(.81,-.05,.05,.015,4,15,-8,355,0,0),e(360)));
        add("Huntsman",r("Sawback Check",S,k(.16,-.055,.055,.02,-12,-30,-18,0,0,0),
                k(.34,-.12,.10,.035,-18,-65,-26,0,0,0),k(.56,-.12,.10,.035,12,55,15,0,0,0),
                k(.74,-.065,.075,.025,5,10,12,285,.7,0),k(.9,-.012,-.01,0,3,0,-4,360,0,0),e(360)),
                r("Field Flip",S,k(.12,-.03,-.015,0,10,-15,8,0,.1,0),
                k(.28,-.07,.04,.02,-5,-30,-12,-220,.9,.09),k(.43,-.10,.09,.025,-14,-50,-10,-360,.25,0),
                k(.66,-.10,.09,.025,6,40,12,-360,0,0),k(.84,-.03,.02,0,4,5,-4,-360,0,0),e(-360)));
        add("Maro Rift",r("Rift Orbit",S,k(.10,-.025,.02,0,8,15,-16,0,.35,0),
                k(.25,-.075,.08,.025,-10,-35,-26,-300,1,0),k(.39,-.11,.115,.035,-14,-60,-8,-360,1,0),
                k(.61,-.11,.115,.035,8,45,18,-360,.6,0),k(.79,-.055,.05,.015,8,12,12,-690,1,0),
                k(.9,-.012,-.012,0,3,0,-4,-720,.1,0),e(-720)),
                r("Meridian Catch",S,k(.13,-.04,-.02,0,12,-12,6,0,.1,0),
                k(.27,-.075,.035,.02,-6,-28,-12,210,1,.12),k(.40,-.10,.095,.03,-14,-48,-8,360,.25,0),
                k(.63,-.10,.095,.03,8,55,15,360,0,0),k(.82,-.04,.03,.01,5,12,-6,360,0,0),e(360)));
        double[] times={0,.10,.22,.34,.47,.60,.73,.86,1};
        double[][] turns={{0,-25,140,340,360,360,520,700,720},
                {0,65,350,700,720,720,1070,1420,1440},
                {0,15,-155,-350,-360,-360,-190,-25,0},
                {0,-45,-330,-700,-720,-720,-1060,-1400,-1440}};
        for(int r=0;r<4;r++){
            HINGES[r]=new double[times.length][2];
            for(int i=0;i<times.length;i++){HINGES[r][i][0]=times[i];HINGES[r][i][1]=turns[r][i];}
        }
    }
    private InspectionMotion() { }
    public static String routineName(String knife,boolean alternate){return routines(knife)[alternate?1:0].name;}
    private static Routine[] routines(String knife){return ROUTINES.getOrDefault(knife,ROUTINES.get("Skeleton"));}
    public static Pose sample(String knife,boolean alternate,double time) {
        double[][] keys=routines(knife)[alternate?1:0].keys;
        return new Pose((float)sample(keys,time,1),(float)sample(keys,time,2),(float)sample(keys,time,3),
                (float)sample(keys,time,4),(float)sample(keys,time,5),(float)sample(keys,time,6),
                (float)sample(keys,time,7),(float)sample(keys,time,8),(float)sample(keys,time,9));
    }
    private static double sample(double[][] keys,double time,int column) {
        if(time<=0)return keys[0][column];if(time>=1)return keys[keys.length-1][column];
        int i=0;while(i<keys.length-2&&time>keys[i+1][0])i++;
        double span=keys[i+1][0]-keys[i][0],u=(time-keys[i][0])/span,u2=u*u,u3=u2*u;
        return (2*u3-3*u2+1)*keys[i][column]+(u3-2*u2+u)*slope(keys,i,column)*span
                +(-2*u3+3*u2)*keys[i+1][column]+(u3-u2)*slope(keys,i+1,column)*span;
    }
    private static double slope(double[][] keys,int i,int column) {
        if(i==0||i==keys.length-1)return 0;
        double a=(keys[i][column]-keys[i-1][column])/(keys[i][0]-keys[i-1][0]);
        double b=(keys[i+1][column]-keys[i][column])/(keys[i+1][0]-keys[i][0]);
        return a*b<=0?0:Math.copySign(Math.min(Math.abs((a+b)*.5),3*Math.min(Math.abs(a),Math.abs(b))),a);
    }
    public static float hinge(double t,int part,boolean alternate) {
        return (float)sample(HINGES[(alternate?2:0)+part-1],t,1);
    }
}
