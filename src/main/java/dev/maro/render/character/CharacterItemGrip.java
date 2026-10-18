package dev.maro.render.character;

import dev.maro.mixin.AccessorHandItemLayer;
import dev.maro.mixin.AccessorHandItemRenderState;
import dev.maro.render.pickaxe.PickaxeRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Places an item's actual contact point at the imported palm, including resource-pack geometry. */
public final class CharacterItemGrip {
    private static final WeakHashMap<Supplier<Vector3fc[]>,Vector3f[]> POINTS=new WeakHashMap<>();
    private CharacterItemGrip(){ }
    public static Vector3f offset(ItemRenderState item,ItemStack stack,Arm arm,Vector3f out){
        out.zero();
        if(item==null||item.isEmpty()||stack.isEmpty())return out;
        var state=(AccessorHandItemRenderState)item;
        if(state.anubis$handLayerCount()==0)return out;
        var layer=(AccessorHandItemLayer)state.anubis$handLayers()[0];
        // Maro's 3D pickaxes already put their calibrated handle at item origin.
        if(layer.maro$specialModel() instanceof PickaxeRenderer)return out;
        // These native articulated models define their grip through their own use/hold poses.
        if(stack.isOf(Items.SHIELD)||stack.isOf(Items.TRIDENT)||stack.isOf(Items.BOW)||stack.isOf(Items.CROSSBOW)||stack.isOf(Items.SPYGLASS))return out;
        var vertices=layer.anubis$handExtents();if(vertices==null)return out;
        int kind=stack.getItem() instanceof BlockItem?2:tool(stack)?1:0;
        Vector3f[] known=POINTS.computeIfAbsent(vertices,key->new Vector3f[3]);
        if(known[kind]==null)known[kind]=point(vertices.get(),kind);
        Vector3f contact=known[kind];if(contact==null)return out;
        var transform=layer.anubis$handTransform();var pose=new MatrixStack.Entry();
        if(transform!=null)transform.apply(arm==Arm.LEFT,pose);
        pose.getPositionMatrix().transformPosition(contact,out);
        if(!out.isFinite()||out.lengthSquared()>4)out.zero();
        if(POINTS.size()>1024)POINTS.clear();
        return out;
    }
    private static boolean tool(ItemStack stack){
        return stack.isIn(ItemTags.PICKAXES)||stack.isIn(ItemTags.AXES)||stack.isIn(ItemTags.SHOVELS)||stack.isIn(ItemTags.HOES)||stack.isIn(ItemTags.SWORDS);
    }
    private static Vector3f point(Vector3fc[] points,int kind){
        if(points==null||points.length==0)return new Vector3f(.5f,kind==2?0:.3f,.5f);
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        float lowest=Float.POSITIVE_INFINITY,bottomX=0,bottomY=0;int ties=0;
        for(var p:points){
            if(p==null||!Float.isFinite(p.x())||!Float.isFinite(p.y())||!Float.isFinite(p.z()))continue;
            minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());minZ=Math.min(minZ,p.z());
            maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());maxZ=Math.max(maxZ,p.z());
            float sum=p.x()+p.y();
            if(sum<lowest-.0001f){lowest=sum;bottomX=p.x();bottomY=p.y();ties=1;}
            else if(Math.abs(sum-lowest)<.0001f){bottomX+=p.x();bottomY+=p.y();ties++;}
        }
        if(!Float.isFinite(minX))return new Vector3f(.5f,.3f,.5f);
        float x=(minX+maxX)/2,z=(minZ+maxZ)/2;
        if(kind==2)return new Vector3f(x,minY+Math.min(.08f,(maxY-minY)*.12f),z);
        // Flat tool textures run diagonally from the handle at bottom-left to the head.
        // Other items (especially 3D totems) use their lower centre, not the model's origin.
        if(kind==1&&maxZ-minZ<.2f&&ties>0){
            float step=Math.min(maxX-minX,maxY-minY)*.18f;
            return new Vector3f(Math.clamp(bottomX/ties+step,minX,maxX),Math.clamp(bottomY/ties+step,minY,maxY),z);
        }
        return new Vector3f(x,minY+(maxY-minY)*.24f,z);
    }
}
