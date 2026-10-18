package dev.maro.render.character.gltf;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Offline repair for the supplied unrigged Skeleton: keep anatomical shells intact at the arms. */
public final class BakeSkeleton {
    private record Point(int x,int y,int z) { }
    private static int root(int[] parent,int i){while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i];}return i;}
    private static final class Bounds {
        double x;float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;int count;
        void add(float[] p,int i){x+=p[i];minY=Math.min(minY,p[i+1]);maxY=Math.max(maxY,p[i+1]);count++;}
        int arm(){double centre=x/count;return Math.abs(centre)>3.2&&minY>10&&maxY<25?centre<0?2:3:0;}
    }
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{
        Method method=CharacterBake.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(null,args);
    }
    public static void main(String[] args)throws Exception{
        byte[] input=Files.readAllBytes(Path.of(args[0]));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        if(!hash.equals("ae74e0a94fd63947474fd95928551a4c73eeac7234faa220d6ac85a8cf579f8d"))throw new IllegalArgumentException("Expected the supplied Skeleton source");
        GltfFile file=GltfFile.read(input);PosedMesh mesh=PosedMesh.pose(file,Map.of());mesh.normalise(30,false);mesh.cluster(150000);
        CharacterBake.Cut cut=ShapeCut.cut(mesh);if(cut==null)throw new IllegalStateException("Missing limb cut");
        int[] parent=new int[mesh.vertices];var welded=new HashMap<Point,Integer>();
        for(int v=0;v<parent.length;v++){
            parent[v]=v;int i=v*3;var key=new Point(Math.round(mesh.pos[i]*100000),Math.round(mesh.pos[i+1]*100000),Math.round(mesh.pos[i+2]*100000));
            Integer other=welded.putIfAbsent(key,v);if(other!=null)parent[v]=root(parent,other);
        }
        for(int t=0;t<mesh.triangles;t++){
            int a=root(parent,mesh.tris[t*3]),b=root(parent,mesh.tris[t*3+1]),c=root(parent,mesh.tris[t*3+2]);parent[b]=a;parent[c]=a;
        }
        var groups=new HashMap<Integer,Bounds>();
        for(int v=0;v<parent.length;v++)groups.computeIfAbsent(root(parent,v),k->new Bounds()).add(mesh.pos,v*3);
        int repaired=0,armVertices=0;
        for(int v=0;v<parent.length;v++){
            int arm=groups.get(root(parent,v)).arm(),before=cut.part()[v];
            if(arm!=0){cut.part()[v]=arm;armVertices++;}else if(before==2||before==3)cut.part()[v]=1;
            if(before!=cut.part()[v])repaired++;
        }
        if(armVertices<1000||repaired<1000)throw new IllegalStateException("Arm repair did not find the expected anatomical shells");
        for(int side=0;side<2;side++){
            int arm=side==0?2:3;float sign=side==0?-1:1;
            cut.pivot()[arm]=new float[]{sign*3.0f,24.0f,-1.0f};
            cut.grip()[arm]=new float[]{sign*4.45f,14.4f,-.55f};
            cut.bendAt()[arm]=new float[]{sign*4.3f,19.1f,-1.2f};
            // Forearm and hand bones move as complete shells around the elbow.
            // A band through the empty elbow space avoids slicing the narrow wrist bones.
            float[] weights=cut.bendWeights()[arm]=new float[mesh.vertices];
            for(int v=0;v<weights.length;v++){
                var group=groups.get(root(parent,v));weights[v]=group.arm()==arm&&group.maxY<20?1:0;
            }
        }
        @SuppressWarnings("unchecked") List<Object> pieces=(List<Object>)call("split",new Class<?>[]{PosedMesh.class,int[].class},mesh,cut.part());
        call("reduce",new Class<?>[]{PosedMesh.class,List.class},mesh,pieces);
        // The slender fingers are already low-poly. A body-wide proportional budget
        // collapses their small, disconnected bones; keep these source shells at every LOD.
        for(Object piece:pieces){
            var boneField=piece.getClass().getDeclaredField("bone");boneField.setAccessible(true);int bone=boneField.getInt(piece);
            if(bone!=2&&bone!=3)continue;
            var sourceField=piece.getClass().getDeclaredField("source");sourceField.setAccessible(true);
            @SuppressWarnings("unchecked") var source=(List<Integer>)sourceField.get(piece);int[] corners=new int[source.size()*3];
            for(int i=0;i<source.size();i++)System.arraycopy(mesh.tris,source.get(i)*3,corners,i*3,3);
            var levelsField=piece.getClass().getDeclaredField("levels");levelsField.setAccessible(true);levelsField.set(piece,new int[][]{corners,corners,corners});
            System.out.println("Preserved "+source.size()+" source triangles in arm "+bone+" at every detail level");
        }
        @SuppressWarnings("unchecked") var uv=(Map<Integer,float[]>)call("uvBounds",new Class<?>[]{PosedMesh.class,List.class},mesh,pieces);
        TextureAtlas atlas=TextureAtlas.pack(file,uv,data->{var image=ImageIO.read(new ByteArrayInputStream(data));if(image==null)throw new IOException("Unsupported texture");return new ImageDecoder.Picture(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()));});
        var options=new CharacterBake.Options();options.flat=.16f;
        try(var out=new DataOutputStream(Files.newOutputStream(Path.of(args[1])))){
            call("write",new Class<?>[]{DataOutputStream.class,PosedMesh.class,CharacterBake.Cut.class,List.class,TextureAtlas.class,CharacterBake.Options.class},out,mesh,cut,pieces,atlas,options);
        }
        System.out.println("Repaired "+repaired+" vertex assignments; "+armVertices+" vertices in "+groups.values().stream().filter(g->g.arm()!=0).count()+" complete arm/hand shells. Written "+args[1]);
    }
}
