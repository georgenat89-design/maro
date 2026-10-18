package dev.maro.render.character;

import dev.maro.module.impl.visuals.Nametags;
import dev.maro.render.character.kit.PartMesh;
import dev.maro.render.character.kit.Surface;
import dev.maro.render.esp.EntityRenderStateAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Nametags follow the head's actual posed world transform, including flight and swimming. */
public final class CharacterLabelAnchors {
    private static final Map<PartMesh,float[]> BOXES=new WeakHashMap<>();
    private static final Map<Integer,Vec3d> POINTS=new HashMap<>();
    private static final Map<Integer,Vec3d> BODIES=new HashMap<>();
    private static final Vector3f POINT=new Vector3f();
    private CharacterLabelAnchors(){}
    public static void beginFrame(){POINTS.clear();BODIES.clear();}
    public static Vec3d point(PlayerEntity player){return POINTS.get(player.getId());}
    public static Vec3d body(PlayerEntity player){return BODIES.get(player.getId());}
    static void recordBody(PlayerEntityRenderState state,PartMesh body,Matrix4f pose){
        var esp=dev.maro.module.ModuleManager.get(dev.maro.module.impl.visuals.PlayerESP.class);
        if(esp==null||!esp.isEnabled()||!esp.tracersOn()||!(((EntityRenderStateAccess)state).maro$getEntity() instanceof PlayerEntity player)||state.id!=player.getId())return;
        float[] b=BOXES.computeIfAbsent(body,CharacterLabelAnchors::bounds);if(!Float.isFinite(b[0]))return;
        POINT.set((b[0]+b[3])*.5f,(b[1]+b[4])*.5f,(b[2]+b[5])*.5f);pose.transformPosition(POINT);
        if(Float.isFinite(POINT.x+POINT.y+POINT.z))BODIES.put(player.getId(),MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos().add(POINT.x,POINT.y,POINT.z));
    }
    static void record(PlayerEntityRenderState state,PartMesh head,Matrix4f pose){
        var tags=Nametags.get();
        if(tags==null||!tags.isEnabled()||!(((EntityRenderStateAccess)state).maro$getEntity() instanceof PlayerEntity player)||state.id!=player.getId())return;
        float[] b=BOXES.computeIfAbsent(head,CharacterLabelAnchors::bounds);
        if(!Float.isFinite(b[0]))return;
        float x=0,z=0,top=Float.NEGATIVE_INFINITY;
        for(int i=0;i<8;i++){
            POINT.set(b[(i&1)==0?0:3],b[(i&2)==0?1:4],b[(i&4)==0?2:5]);
            pose.transformPosition(POINT);x+=POINT.x;z+=POINT.z;top=Math.max(top,POINT.y);
        }
        var camera=MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        float gap=player.getHeight()<1?.12f:.25f;
        if(Float.isFinite(x+z+top))POINTS.put(player.getId(),camera.add(x/8,top+gap,z/8));
    }
    private static float[] bounds(PartMesh part){
        float[] b={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(var s:new Surface[]{part.base,part.decals})if(s!=null)for(int i=0;i<s.pos.length;i+=3)for(int k=0;k<3;k++){
            b[k]=Math.min(b[k],s.pos[i+k]);b[k+3]=Math.max(b[k+3],s.pos[i+k]);
        }
        return b;
    }
}
