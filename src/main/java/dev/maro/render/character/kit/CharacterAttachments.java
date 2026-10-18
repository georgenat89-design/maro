package dev.maro.render.character.kit;

import java.util.IdentityHashMap;

/** Repairs stray torso faces below the hips in imported rigid-part meshes, before seam preparation. */
final class CharacterAttachments {
    private CharacterAttachments(){ }
    static PartMesh[] repair(PartMesh[] parts){
        PartMesh body=null,right=null,left=null;
        for(var p:parts){
            if(p.extra||p.bends()||p.outline!=null)return parts;
            if(p.bone==Bone.BODY)body=p;
            if(p.bone==Bone.RIGHT_LEG)right=p;
            if(p.bone==Bone.LEFT_LEG)left=p;
        }
        if(body==null||right==null||left==null)return parts;
        float hip=(right.pivotY+left.pivotY)/2;
        // Keep the original baked topology unless it contains a torso fragment well down a leg.
        boolean stray=false;
        for(var s:new Surface[]{body.base,body.decals})if(s!=null)for(int v=0;v<s.vertexCount();v++)
            if(s.pos[v*3+1]+body.pivotY<hip-1)stray=true;
        if(!stray)return parts;
        PartMesh[] owners={body,right,left};Surface[][] surfaces=new Surface[3][2];
        for(int layer=0;layer<2;layer++){
            var bins=new Bin[]{new Bin(body),new Bin(right),new Bin(left)};
            for(var owner:owners){
                var s=layer==0?owner.base:owner.decals;if(s==null)continue;
                for(int q=0;q<s.quads.length;q+=4){
                    float x=0,y=0;int count=s.quads[q+2]==s.quads[q+3]?3:4;
                    for(int j=0;j<count;j++){int v=s.quads[q+j];x+=s.pos[v*3]+owner.pivotX;y+=s.pos[v*3+1]+owner.pivotY;}
                    int target=y/count>=hip?0:x<0?1:2;
                    bins[target].quad(owner,s,q,count==3?1:2);
                }
            }
            for(int i=0;i<bins.length;i++)surfaces[i][layer]=bins[i].build();
        }
        var out=parts.clone();
        for(int i=0;i<out.length;i++)for(int b=0;b<owners.length;b++)if(out[i]==owners[b]){
            PartMesh p=out[i];
            out[i]=new PartMesh(p.name,p.bone,p.parent,p.extra,p.pivotX,p.pivotY,p.pivotZ,p.gripX,p.gripY,p.gripZ,p.motion,
                surfaces[b][0],p.outline,surfaces[b][1],p.motionSlot,p.swingScale,p.swingLimit,p.jointed,p.jointX,p.jointY,p.jointZ,p.neck,p.lookPitch,p.lookYaw,p.lookLimit);
        }
        return out;
    }
    private static final class Bin {
        final PartMesh target;
        final Surface.Builder builder=new Surface.Builder();
        final IdentityHashMap<Surface,int[]> indices=new IdentityHashMap<>();
        Bin(PartMesh target){this.target=target;}
        int vertex(PartMesh owner,Surface s,int vertex){
            int[] remap=indices.computeIfAbsent(s,k->{int[] map=new int[s.vertexCount()];java.util.Arrays.fill(map,-1);return map;});
            if(remap[vertex]>=0)return remap[vertex];int i=vertex*3;
            return remap[vertex]=builder.vertex(s.pos[i]+owner.pivotX-target.pivotX,s.pos[i+1]+owner.pivotY-target.pivotY,s.pos[i+2]+owner.pivotZ-target.pivotZ,
                s.nrm[i],s.nrm[i+1],s.nrm[i+2],s.uv[vertex*2],s.uv[vertex*2+1],s.color[vertex],s.glow!=null&&s.glow[vertex]!=0,s.flat==null?0:s.flat[vertex]);
        }
        void quad(PartMesh owner,Surface s,int q,int triangles){builder.quad(vertex(owner,s,s.quads[q]),vertex(owner,s,s.quads[q+1]),vertex(owner,s,s.quads[q+2]),vertex(owner,s,s.quads[q+3]),triangles);}
        Surface build(){var s=builder.build();if(s!=null&&target.jointed)s.joint=new float[s.vertexCount()];return s;}
    }
}
