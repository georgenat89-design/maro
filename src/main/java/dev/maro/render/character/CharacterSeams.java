package dev.maro.render.character;

import dev.maro.render.character.kit.*;
import java.util.*;

/** Bind-space seam welding and a short torso blend, prepared once on the character worker. */
public final class CharacterSeams {
    private static final float EPS=.002f;
    private record Vertex(PartMesh part,Surface surface,int index,float x,float y,float z) { }
    private static final class Group {
        final Vertex first;
        final List<Vertex> vertices=new ArrayList<>();
        int bones;
        Group(Vertex v){first=v;}
        void add(Vertex v){vertices.add(v);bones|=1<<v.part.bone.ordinal();}
    }
    private CharacterSeams(){ }
    private static long cell(int x,int y,int z){return ((long)x&0x1FFFFF)<<42|((long)y&0x1FFFFF)<<21|((long)z&0x1FFFFF);}
    private static int grid(float x,float size){return (int)Math.floor(x/size);}

    public static int prepare(PartMesh[] parts,float height){
        for(var p:parts)if(p.extra||p.bends())return 0;
        for(var p:parts)smoothJoint(p,height);
        var bins=new HashMap<Long,List<Group>>();var groups=new ArrayList<Group>();
        var points=new HashMap<Integer,List<float[]>>();
        for(var part:parts)for(var s:new Surface[]{part.base,part.decals})if(s!=null)for(int v=0;v<s.vertexCount();v++){
            int i=v*3;var vertex=new Vertex(part,s,v,s.pos[i]+part.pivotX,s.pos[i+1]+part.pivotY,s.pos[i+2]+part.pivotZ);
            int x=grid(vertex.x,EPS),y=grid(vertex.y,EPS),z=grid(vertex.z,EPS);Group found=null;
            search:for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
                var nearby=bins.get(cell(x+dx,y+dy,z+dz));if(nearby==null)continue;
                for(var g:nearby){var a=g.first;float qx=a.x-vertex.x,qy=a.y-vertex.y,qz=a.z-vertex.z;
                    if(qx*qx+qy*qy+qz*qz>EPS*EPS)continue;
                    int n=a.index*3;float dot=a.surface.nrm[n]*s.nrm[i]+a.surface.nrm[n+1]*s.nrm[i+1]+a.surface.nrm[n+2]*s.nrm[i+2];
                    if(dot<.85f)continue;found=g;break search;
                }
            }
            if(found==null){found=new Group(vertex);groups.add(found);bins.computeIfAbsent(cell(x,y,z),k->new ArrayList<>()).add(found);}
            found.add(vertex);
        }
        int seams=0;float hip=height*.4f;
        for(var p:parts)if(p.bone==Bone.RIGHT_LEG||p.bone==Bone.LEFT_LEG)hip=Math.max(hip,p.pivotY);
        for(var g:groups){
            boolean torso=(g.bones&2)!=0&&(g.bones&~2)!=0;
            boolean crotch=(g.bones&48)==48&&g.first.y>=hip-height*.09f;
            if(!torso&&!crotch)continue;
            float x=0,y=0,z=0,nx=0,ny=0,nz=0;
            for(var v:g.vertices){int i=v.index*3;x+=v.x;y+=v.y;z+=v.z;nx+=v.surface.nrm[i];ny+=v.surface.nrm[i+1];nz+=v.surface.nrm[i+2];}
            x/=g.vertices.size();y/=g.vertices.size();z/=g.vertices.size();float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=length;ny/=length;nz/=length;
            for(var v:g.vertices){int i=v.index*3;v.surface.pos[i]=x-v.part.pivotX;v.surface.pos[i+1]=y-v.part.pivotY;v.surface.pos[i+2]=z-v.part.pivotZ;v.surface.nrm[i]=nx;v.surface.nrm[i+1]=ny;v.surface.nrm[i+2]=nz;}
            for(int bone=0;bone<6;bone++)if(bone!=1&&(g.bones&(1<<bone))!=0)points.computeIfAbsent(bone,k->new ArrayList<>()).add(new float[]{x,y,z});
            seams++;
        }
        float radius=Math.max(.4f,height*.045f);
        for(var part:parts){
            var anchors=points.get(part.bone.ordinal());if(anchors==null)continue;
            var near=new HashMap<Long,List<float[]>>();
            for(var a:anchors)near.computeIfAbsent(cell(grid(a[0],radius),grid(a[1],radius),grid(a[2],radius)),k->new ArrayList<>()).add(a);
            for(var s:new Surface[]{part.base,part.decals})if(s!=null){
                float[] weights=new float[s.vertexCount()];boolean any=false;
                for(int v=0;v<weights.length;v++){
                    int i=v*3;float px=s.pos[i]+part.pivotX,py=s.pos[i+1]+part.pivotY,pz=s.pos[i+2]+part.pivotZ;
                    int x=grid(px,radius),y=grid(py,radius),z=grid(pz,radius);float best=radius*radius;
                    for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
                        var list=near.get(cell(x+dx,y+dy,z+dz));if(list==null)continue;
                        for(var a:list){float ax=a[0]-px,ay=a[1]-py,az=a[2]-pz;best=Math.min(best,ax*ax+ay*ay+az*az);}
                    }
                    float t=Math.min(1,(float)Math.sqrt(best)/radius);weights[v]=best<EPS*EPS?1:1-t*t*(3-2*t);any|=weights[v]>0;
                }
                if(any)s.parentWeight=weights;
            }
        }
        return seams;
    }

    /** Imported two-percent hinge bands can fold thick clothing inside out when crouching. */
    private static void smoothJoint(PartMesh part,float height){
        if(!part.jointed||!part.bone.arm()&&!part.bone.leg())return;
        float ax=0,ay=-1,az=0;
        if(part.bone.arm()){
            ax=part.gripX-part.pivotX;ay=part.gripY-part.pivotY;az=part.gripZ-part.pivotZ;
            float length=(float)Math.sqrt(ax*ax+ay*ay+az*az);if(length<.001f)return;
            ax/=length;ay/=length;az/=length;
        }
        float proximal=part.jointX*ax+part.jointY*ay+part.jointZ*az;
        if(proximal<.1f)return;
        float band=Math.min(height*.07f,proximal*.75f);
        for(var s:new Surface[]{part.base,part.outline,part.decals})if(s!=null&&s.joint!=null)for(int v=0;v<s.vertexCount();v++){
            int i=v*3;float distance=(s.pos[i]-part.jointX)*ax+(s.pos[i+1]-part.jointY)*ay+(s.pos[i+2]-part.jointZ)*az;
            float t=Math.max(0,Math.min(1,.5f+distance/(2*band)));s.joint[v]=t*t*(3-2*t);
        }
    }
}
