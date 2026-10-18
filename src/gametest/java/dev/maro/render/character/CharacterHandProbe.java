package dev.maro.render.character;

import dev.maro.render.character.kit.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import java.lang.reflect.Proxy;
import java.util.*;

/** Replays the actual deferred first-person commands and guards the imported arm ownership. */
public final class CharacterHandProbe {
    private record Command(MatrixStack.Entry pose,OrderedRenderCommandQueue.Custom draw) { }
    private static void require(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    public static void check(WornCharacter worn,PlayerEntityModel pm){
        for(Bone bone:new Bone[]{Bone.RIGHT_ARM,Bone.LEFT_ARM}){
            PartMesh part=worn.model.part(bone);require(part!=null&&part.base!=null,"Missing Skeleton arm");
            int vertices=0,expected=0;float minY=100,maxY=-100;
            for(PartMesh p:worn.model.parts)if(p.bone==bone)for(Surface s:new Surface[]{p.base,p.decals})if(s!=null){
                expected+=s.quads.length;
                for(int v=0;v<s.vertexCount();v++){
                    float x=s.pos[v*3]+p.pivotX,y=s.pos[v*3+1]+p.pivotY;
                    require(bone==Bone.RIGHT_ARM?x<-2.5:x>2.5,"Skeleton arm still contains a rib/pelvis fragment: "+bone+" x="+x+" y="+y);
                    minY=Math.min(minY,y);maxY=Math.max(maxY,y);vertices++;
                }
            }
            require(vertices>200&&minY<12&&maxY>24,"Incomplete Skeleton arm/hand silhouette");
            require(part.gripY>14&&part.gripY<15,"Skeleton grip is outside the palm");
            var commands=new ArrayList<Command>();
            var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args)->{
                if(args!=null&&args.length==1&&args[0] instanceof Integer)return proxy;
                if(args!=null&&args.length==3&&args[0] instanceof MatrixStack stack&&args[2] instanceof OrderedRenderCommandQueue.Custom draw){commands.add(new Command(stack.peek().copy(),draw));return null;}
                if(method.getName().equals("toString"))return "first-person-hand-probe";
                throw new AssertionError("Unexpected hand command: "+method);
            });
            var stack=new MatrixStack();stack.translate(.3,-.2,.1);stack.multiply(new org.joml.Quaternionf().rotateXYZ(.2f,-.4f,.1f));
            require(CharacterRenderer.firstPersonHand(pm,stack,queue,0xF000F0,bone==Bone.RIGHT_ARM?pm.rightArm:pm.leftArm),"First-person replacement was skipped");
            var out=new Output();for(var command:commands)command.draw.render(command.pose,out);
            require(out.points.size()==expected,"First-person geometry was lost or duplicated");
            require(out.points.stream().allMatch(Vector3f::isFinite),"Non-finite first-person geometry");
            float span=0;for(var a:out.points)span=Math.max(span,a.distance(out.points.getFirst()));
            require(span>.5&&span<1.5,"First-person hand has stretched/detached geometry");
            System.out.println("[skeleton-hand] "+worn.model.detail+" "+bone+": "+vertices+" arm/hand vertices, "+expected+" queued corners; no torso fragments, finite normals, palm grip");
        }
    }
    private static final class Output implements VertexConsumer {
        final List<Vector3f> points=new ArrayList<>();
        @Override public VertexConsumer vertex(float x,float y,float z){points.add(new Vector3f(x,y,z));return this;}
        @Override public void vertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){vertex(x,y,z);normal(nx,ny,nz);}
        @Override public VertexConsumer normal(float x,float y,float z){require(Float.isFinite(x+y+z)&&Math.abs(x*x+y*y+z*z-1)<.02,"Invalid first-person hand normal");return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer color(int color){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer lineWidth(float width){return this;}
    }
}
