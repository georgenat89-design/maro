package dev.maro.render.character.kit;

import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.client.texture.NativeImage;

/** UV-authored triangle geometry, prepared offline with independent detail levels. */
public final class IndexedGeometry {
    private record Level(float[] positions,float[] normals,float[] uv,int[] triangles) {}
    private final Level[] levels;
    final Canvas texture;
    private IndexedGeometry(String resource) throws IOException {
        try(var raw=IndexedGeometry.class.getResourceAsStream(resource+".mesh")) {
            if(raw==null)throw new IOException("Missing mesh "+resource);
            var in=new DataInputStream(raw);
            if(in.readInt()!=0x4D534831)throw new IOException("Invalid indexed mesh "+resource);
            in.readInt(); // Body skin colour; available without decoding the head texture.
            int count=in.readInt();
            if(count!=3)throw new IOException("Expected three mesh detail levels");
            levels=new Level[count];
            for(int level=0;level<count;level++) {
                int vertices=in.readInt(),triangles=in.readInt();
                if(vertices<3||vertices>250000||triangles<1||triangles>300000)throw new IOException("Invalid mesh dimensions");
                float[] pos=readFloats(in,vertices*3),normal=readFloats(in,vertices*3),uv=readFloats(in,vertices*2);
                int[] indices=new int[triangles*3];
                for(int i=0;i<indices.length;i++) {
                    indices[i]=in.readInt();
                    if(indices[i]<0||indices[i]>=vertices)throw new IOException("Invalid mesh index");
                }
                levels[level]=new Level(pos,normal,uv,indices);
            }
        }
        try(var raw=IndexedGeometry.class.getResourceAsStream(resource+".png")) {
            if(raw==null)throw new IOException("Missing mesh texture "+resource);
            try(var image=NativeImage.read(raw)) {
                int w=image.getWidth(),h=image.getHeight();
                int[] pixels=new int[w*h];
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=image.getColorArgb(x,y);
                texture=new Canvas(w,h).fillAll((x,y)->pixels[(int)y*w+(int)x]);
            }
        }
    }
    private static float[] readFloats(DataInputStream in,int count) throws IOException {
        float[] values=new float[count];
        for(int i=0;i<count;i++) {
            values[i]=in.readFloat();
            if(!Float.isFinite(values[i]))throw new IOException("Non-finite mesh attribute");
        }
        return values;
    }
    public static IndexedGeometry load(String resource) {
        if(IndexedGeometry.class.getResource(resource+".mesh")==null)return null;
        try{return new IndexedGeometry(resource);}
        catch(IOException failure){throw new IllegalStateException("Cannot load indexed geometry "+resource,failure);}
    }
    public static int skin(String resource,int fallback) {
        try(var raw=IndexedGeometry.class.getResourceAsStream(resource+".mesh")) {
            if(raw==null)return fallback;
            var in=new DataInputStream(raw);
            if(in.readInt()!=0x4D534831)throw new IOException("Invalid mesh header");
            return in.readInt();
        }catch(IOException failure){throw new IllegalStateException("Cannot read portrait palette",failure);}
    }
    Surface surface(Detail detail,Part part,Atlas atlas) {
        Level mesh=levels[detail.ordinal()];
        Surface.Builder b=new Surface.Builder();
        for(int i=0;i<mesh.positions.length/3;i++) {
            int p=i*3,u=i*2;
            b.vertex(mesh.positions[p]-part.pivotX,mesh.positions[p+1]-part.pivotY,mesh.positions[p+2]-part.pivotZ,
                mesh.normals[p],mesh.normals[p+1],mesh.normals[p+2],atlas.u(texture,mesh.uv[u]),atlas.v(texture,mesh.uv[u+1]),
                0xFFFFFFFF,false,part.flat>=0?part.flat:.12f);
        }
        CharacterBuilder.pair(mesh.triangles,mesh.triangles.length/3,mesh.positions.length/3,b);
        return b.build();
    }
}
