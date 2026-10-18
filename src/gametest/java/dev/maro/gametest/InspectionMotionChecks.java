package dev.maro.gametest;

import dev.maro.anubis.inspect.InspectionMotion;
import java.util.HashSet;

/** Choreography must stay finite, settle without a jump and remain distinct for every routine. */
final class InspectionMotionChecks {
    static void run() {
        var names=new HashSet<String>();var paths=new HashSet<Long>();
        for(String knife:InspectionMotion.KNIVES)for(boolean alternate:new boolean[]{false,true}) {
            if(!names.add(InspectionMotion.routineName(knife,alternate)))throw new AssertionError("Duplicate inspection name");
            long hash=1;
            for(int i=0;i<=1000;i++) {
                var p=InspectionMotion.sample(knife,alternate,i/1000d);
                float[] values={p.x(),p.y(),p.z(),p.pitch(),p.yaw(),p.roll(),p.spin(),p.grip(),p.air()};
                for(float value:values){if(!Float.isFinite(value))throw new AssertionError("Nonfinite inspection "+knife);hash=hash*31+Float.floatToIntBits(value);}
                if(Math.abs(p.x())>.3||Math.abs(p.y())>.3||Math.abs(p.z())>.15||p.grip()<0||p.grip()>1||p.air()<0||p.air()>.3)
                    throw new AssertionError("Inspection escaped presentation bounds: "+knife);
                if(i==0||i==1000) {
                    if(p.x()!=0||p.y()!=0||p.z()!=0||p.pitch()!=0||p.yaw()!=0||p.roll()!=0||p.grip()!=0||p.air()!=0||Math.abs(p.spin()%360)>.001)
                        throw new AssertionError("Inspection endpoint does not match idle: "+knife);
                }
            }
            if(!paths.add(hash))throw new AssertionError("Duplicate choreography "+knife);
            var end=InspectionMotion.sample(knife,alternate,.9999);
            if(Math.abs(end.x())+Math.abs(end.y())+Math.abs(end.z())>.00002||Math.abs(end.pitch())+Math.abs(end.yaw())+Math.abs(end.roll())>.005)
                throw new AssertionError("Inspection does not ease into idle: "+knife);
        }
        for(boolean alternate:new boolean[]{false,true})for(int part=1;part<=2;part++) {
            for(int i=0;i<=1000;i++)if(!Float.isFinite(InspectionMotion.hinge(i/1000d,part,alternate)))throw new AssertionError("Nonfinite Butterfly hinge");
            if(Math.abs(InspectionMotion.hinge(1,part,alternate)%360)>.001)throw new AssertionError("Butterfly did not return to open idle");
        }
        System.out.println("[knife-motion] PASS: 12 knives, 24 distinct bounded routines, 24024 sampled poses, settled idle endpoints and both Butterfly hinge sequences");
    }
}
