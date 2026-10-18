package dev.maro.render.character;

import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.kit.*;
import dev.maro.setting.NumberSetting;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Vector3f;
import java.lang.reflect.Proxy;
import java.util.*;

/** Replays production queued geometry after the submission stack has been popped. */
public final class CharacterDeformationProbe {
    private record Command(MatrixStack.Entry pose, OrderedRenderCommandQueue.Custom draw) { }
    private record Point(int x,int y,int z) { }
    private record Vertex(Bone bone,boolean attached,Vector3f rest,Vector3f posed) { }
    private static void require(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    private static final class Output implements VertexConsumer {
        final List<Vector3f> vertices=new ArrayList<>();
        @Override public VertexConsumer vertex(float x,float y,float z){var v=new Vector3f(x,y,z);require(v.isFinite(),"Animated vertex is non-finite");vertices.add(v);return this;}
        @Override public void vertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){vertex(x,y,z);normal(nx,ny,nz);}
        @Override public VertexConsumer normal(float x,float y,float z){require(Float.isFinite(x+y+z),"Animated normal is non-finite");require(Math.abs(x*x+y*y+z*z-1)<.02,"Animated normal lost normalization");return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){return this;}
        @Override public VertexConsumer color(int color){return this;}
        @Override public VertexConsumer texture(float u,float v){return this;}
        @Override public VertexConsumer overlay(int u,int v){return this;}
        @Override public VertexConsumer light(int u,int v){return this;}
        @Override public VertexConsumer lineWidth(float width){return this;}
    }
    private static List<List<Vector3f>> capture(WornCharacter worn,PlayerEntityModel pm,PlayerEntityRenderState state){
        var commands=new ArrayList<Command>();
        var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args)->{
            if(args!=null&&args.length==1&&args[0] instanceof Integer)return proxy;
            if(args!=null&&args.length==3&&args[0] instanceof MatrixStack stack&&args[2] instanceof OrderedRenderCommandQueue.Custom draw){commands.add(new Command(stack.peek().copy(),draw));return null;}
            if(method.getName().equals("toString"))return "character-deformation-probe";
            throw new AssertionError("Unexpected renderer command: "+method);
        });
        pm.setAngles(state);CharacterRenderer.submit(worn,pm,new MatrixStack(),queue,0xF000F0,state);
        var result=new ArrayList<List<Vector3f>>();
        for(var command:commands){
            var out=new Output();command.draw.render(command.pose,out);result.add(out.vertices);
        }
        return result;
    }
    public static void check(WornCharacter worn,PlayerEntityModel pm,PlayerEntityRenderState state,CustomModel module){
        var setting=(NumberSetting)module.getSettings().stream().filter(s->s.getName().equals("Model Size")).findFirst().orElseThrow();
        setting.set(100d);var normal=capture(worn,pm,state);var groups=new HashMap<Point,List<Vertex>>();int command=0,attached=0;
        float hip=worn.model.hipY;
        var right=worn.model.part(Bone.RIGHT_LEG);var left=worn.model.part(Bone.LEFT_LEG);
        if(right!=null&&left!=null)hip=(right.pivotY+left.pivotY)/2;
        for(var part:worn.model.parts)if(part.bone==Bone.BODY)for(var surface:new Surface[]{part.base,part.decals})if(surface!=null)
            for(int q=0;q<surface.quads.length;q+=4){float y=0;int count=surface.quads[q+2]==surface.quads[q+3]?3:4;for(int j=0;j<count;j++)y+=surface.pos[surface.quads[q+j]*3+1]+part.pivotY;require(y/count>=hip-1.001f,"Torso still owns a detached leg face: "+worn.model.id);}
        for(var part:worn.model.parts)for(var surface:new Surface[]{part.base,part.decals})if(surface!=null){
            var output=normal.get(command++);require(output.size()==surface.quads.length,"Queued vertex count changed");
            var posed=new Vector3f[surface.vertexCount()];for(int i=0;i<surface.quads.length;i++)posed[surface.quads[i]]=output.get(i);
            for(int v=0;v<posed.length;v++)if(posed[v]!=null){
                boolean seam=surface.parentWeight!=null&&surface.parentWeight[v]>.99999f;
                if(part.bone!=Bone.BODY&&!seam)continue;
                if(seam)attached++;
                var rest=new Vector3f(surface.pos[v*3]+part.pivotX,surface.pos[v*3+1]+part.pivotY,surface.pos[v*3+2]+part.pivotZ);
                var key=new Point(Math.round(rest.x*500),Math.round(rest.y*500),Math.round(rest.z*500));
                groups.computeIfAbsent(key,k->new ArrayList<>()).add(new Vertex(part.bone,seam,rest,posed[v]));
            }
        }
        require(command==normal.size(),"Unmatched queued parts");int checked=0;
        for(var list:groups.values())for(int i=0;i<list.size();i++)for(int j=i+1;j<list.size();j++){
            var a=list.get(i);var b=list.get(j);
            if(a.bone==b.bone||!a.attached&&!b.attached||a.rest.distanceSquared(b.rest)>1e-9)continue;
            require(a.posed.distance(b.posed)<.00002f,"Open animated seam in "+worn.model.id+" between "+a.bone+" and "+b.bone+": "+a.posed.distance(b.posed));checked++;
        }
        if(worn.model.weldedSeams>0)require(attached>0&&checked>0,"No continuous joints tested for "+worn.model.id);
        var root=new MatrixStack();pm.getRootPart().applyTransform(root);var feet=root.peek().getPositionMatrix().transformPosition(new Vector3f(0,1.5f,0));
        var hands=new org.joml.Matrix4f[2];
        for(var arm:Arm.values()){var stack=new MatrixStack();require(CharacterRenderer.translateToHand(pm,state,arm,stack),"Held item did not attach");hands[arm.ordinal()]=new org.joml.Matrix4f(stack.peek().getPositionMatrix());}
        for(float factor:new float[]{.5f,2}){
            setting.set((double)factor*100);var resized=capture(worn,pm,state);require(resized.size()==normal.size(),"Sizing rebuilt geometry");
            for(int p=0;p<normal.size();p++){require(resized.get(p).size()==normal.get(p).size(),"Sizing changed topology");for(int v=0;v<normal.get(p).size();v++){
                var expected=new Vector3f(normal.get(p).get(v)).sub(feet).mul(factor).add(feet);
                require(expected.distance(resized.get(p).get(v))<.00004f,"Model did not resize around its feet: "+worn.model.id);
            }}
            for(var arm:Arm.values()){
                var stack=new MatrixStack();CharacterRenderer.translateToHand(pm,state,arm,stack);
                for(var point:new Vector3f[]{new Vector3f(),new Vector3f(.2f,-.4f,.3f)}){
                    var expected=hands[arm.ordinal()].transformPosition(new Vector3f(point)).sub(feet).mul(factor).add(feet);
                    var actual=stack.peek().getPositionMatrix().transformPosition(point);
                    require(expected.distance(actual)<.00003f,"Weapon failed to follow model size: "+worn.model.id+" "+arm);
                }
            }
            require(module.ready(worn.model.name)==worn,"Size change rebuilt the cached character");
        }
        setting.set(100d);
        System.out.println("[character-deformation] "+worn.model.id+" "+worn.model.detail+" phase="+state.limbSwingAnimationProgress+" crouch="+state.isInSneakingPose+" swing="+state.handSwingProgress+" continuous border pairs="+checked+"; size 50/100/200 and both held-item transforms pass");
    }
}
