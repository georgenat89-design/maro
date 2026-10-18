package dev.maro.anubis.inspect;

/** Original curved first-person glove with individual curled fingers, thumb and padded wrist. */
final class InspectionGlove {
    static final int[] COLORS={0xFFD6DBDF,0xFF8B959E,0xFFF4F7F9,0xFF39434A,0xFF151C22,
        0xFF9DA8AE,0xFF1689B7,0xFF53616B,0xFFC6BEA7};
    static final KnifeMesh MESH=make();
    static final KnifeMesh SLEEVE=sleeve();
    private static KnifeMesh sleeve(){
        var b=new KnifeMesh.Builder();
        float[][] sleeve={{-.70f,.065f,.055f},{-.40f,.055f,.047f},{-.15f,.041f,.036f},{-.075f,.032f,.029f}};
        for(int i=0;i<sleeve.length-1;i++)for(int j=0;j<24;j++){
            float[] face=new float[12];int p=0;
            for(int[] corner:new int[][]{{i,j},{i+1,j},{i+1,j+1},{i,j+1}}){
                var row=sleeve[corner[0]];double angle=corner[1]*Math.PI*2/24;
                face[p++]=row[0];face[p++]=row[1]*(float)Math.cos(angle);face[p++]=-.014f+row[2]*(float)Math.sin(angle);
            }
            b.reverseQuad(3,face);
        }
        return b.build();
    }
    private static KnifeMesh make(){
        var b=new KnifeMesh.Builder();
        ellipsoid(b,-.061f,-.010f,-.024f,.031f,.040f,.031f,4);
        ellipsoid(b,-.005f,.004f,-.026f,.052f,.035f,.026f,3);
        ellipsoid(b,-.005f,.003f,-.048f,.038f,.027f,.009f,7);
        for(int finger=0;finger<4;finger++){
            float x=-.036f+finger*.023f,r=finger==3?.0085f:.010f;
            float[][] points={{x,.026f,-.032f},{x,.038f,-.007f},{x,.026f,.018f},{x,.005f,.025f},{x,-.014f,.012f}};
            for(int i=0;i<points.length-1;i++)capsule(b,points[i],points[i+1],r,i==0?7:4);
        }
        capsule(b,new float[]{.034f,-.023f,-.030f},new float[]{.052f,-.031f,-.003f},.013f,3);
        capsule(b,new float[]{.052f,-.031f,-.003f},new float[]{.033f,-.024f,.021f},.012f,4);
        ellipsoid(b,-.065f,.005f,-.052f,.020f,.022f,.005f,6);
        return b.build();
    }
    private static void capsule(KnifeMesh.Builder b,float[] a,float[] c,float radius,int ink){
        var axis=new org.joml.Vector3f(c[0]-a[0],c[1]-a[1],c[2]-a[2]);
        float length=axis.length();axis.normalize();
        var q=new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(1,0,0),axis);
        var center=new org.joml.Vector3f(a[0]+c[0],a[1]+c[1],a[2]+c[2]).mul(.5f);
        ellipsoid(b,center.x,center.y,center.z,length*.65f,radius,radius,ink,q);
    }
    private static void ellipsoid(KnifeMesh.Builder b,float x,float y,float z,float rx,float ry,float rz,int ink){
        ellipsoid(b,x,y,z,rx,ry,rz,ink,new org.joml.Quaternionf());
    }
    private static void ellipsoid(KnifeMesh.Builder b,float x,float y,float z,float rx,float ry,float rz,int ink,org.joml.Quaternionf rotation){
        for(int i=0;i<12;i++)for(int j=0;j<20;j++){
            float[] face=new float[12];int p=0;
            for(int[] step:new int[][]{{i,j},{i+1,j},{i+1,j+1},{i,j+1}}){
                double a=step[0]*Math.PI/12,t=step[1]*Math.PI*2/20;
                var v=new org.joml.Vector3f(rx*(float)Math.cos(a),ry*(float)(Math.sin(a)*Math.cos(t)),rz*(float)(Math.sin(a)*Math.sin(t)));
                rotation.transform(v);face[p++]=x+v.x;face[p++]=y+v.y;face[p++]=z+v.z;
            }
            b.quad(ink,face);
        }
    }
}
