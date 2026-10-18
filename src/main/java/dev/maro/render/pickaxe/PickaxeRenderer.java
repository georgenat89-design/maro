package dev.maro.render.pickaxe;

import dev.maro.mixin.AccessorHandItemRenderState;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Quaternionf;
import java.util.*;
import java.util.function.Consumer;

/** Vanilla special-item render state, shared by hand, inventory and third-person rendering. */
public final class PickaxeRenderer implements SpecialModelRenderer<PickaxeRenderer.Style> {
    public record Style(float polish,boolean outline,int color,float width,float size,float yaw,float pitch,float zoom){}
    private static final Map<String,PickaxeRenderer> CACHE=new HashMap<>();
    private static final Identifier SURFACE=Identifier.of("maro","textures/misc/knife_satin.png");
    private static final Style DEFAULT=new Style(.75f,true,0xFFE84053,.004f,1,0,0,1);
    private final PickaxeMesh mesh;
    private PickaxeRenderer(String name){mesh=PickaxeModels.get(name);}
    public static void update(ItemRenderState state,String name,ItemDisplayContext context,Style style,boolean glint){
        var renderer=CACHE.computeIfAbsent(name,PickaxeRenderer::new);state.clear();
        ((AccessorHandItemRenderState)state).maro$setDisplayContext(context);
        var layer=state.newLayer();layer.setSpecialModel(renderer,style);layer.setVertices(()->renderer.mesh.bounds);layer.setUseLight(true);
        layer.setGlint(glint?ItemRenderState.Glint.STANDARD:ItemRenderState.Glint.NONE);
        float size=style.size;Transformation transform;
        if(context==ItemDisplayContext.GUI)transform=anchored(new Vector3f(12+style.pitch,-22+style.yaw,-20),new Vector3f(),new Vector3f(.52f*style.zoom),new Vector3f(),false);
        else if(context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
            transform=anchored(new Vector3f(-10,-90,55),new Vector3f(),new Vector3f(.55f*size),PickaxeModels.grip(name),context.isLeftHand());
        else transform=anchored(new Vector3f(0,-28,-18),new Vector3f(.02f,.12f,0),new Vector3f(.72f*size),new Vector3f(),context.isLeftHand());
        layer.setTransform(transform);state.addModelKey(renderer);state.addModelKey(style);state.addModelKey(transform);
    }
    private static Transformation anchored(Vector3f rotation,Vector3f offset,Vector3f scale,Vector3fc grip,boolean left){
        float sign=left?-1:1,radians=(float)Math.PI/180;
        var turn=new Quaternionf().rotationXYZ(rotation.x*radians,rotation.y*sign*radians,rotation.z*sign*radians);
        // Transformation.apply subtracts .5 from every model axis after scaling/rotation.
        // Cancel that block-model centering and put the chosen handle point at the palm instead.
        var translation=new Vector3f(.5f).sub(grip).mul(scale).rotate(turn)
            .add(offset.x*sign,offset.y,offset.z);
        translation.x*=sign;
        return new Transformation(rotation,translation,scale);
    }
    public static void draw(MatrixStack pose,OrderedRenderCommandQueue queue,int light,ItemStack stack,String name,Style style){
        CACHE.computeIfAbsent(name,PickaxeRenderer::new).render(style,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,pose,queue,light,OverlayTexture.DEFAULT_UV,stack.hasGlint(),0);
    }
    @Override public Style getData(ItemStack stack){return DEFAULT;}
    @Override public void collectVertices(Consumer<Vector3fc> consumer){for(var p:mesh.bounds)consumer.accept(p);}
    @Override public void render(Style style,ItemDisplayContext context,MatrixStack pose,OrderedRenderCommandQueue queue,int light,int overlay,boolean glint,int outlineColor){
        if(style.outline)queue.submitCustom(pose,RenderLayers.entityCutout(SURFACE),
            (entry,out)->mesh.emit(entry,out,light,overlay,0,true,style.color,style.width));
        queue.submitCustom(pose,PickaxeSurface.LAYER,
            (entry,out)->mesh.emit(entry,out,light,overlay,style.polish,false,0,0));
        if(glint)queue.getBatchingQueue(1).submitCustom(pose,RenderLayers.entityGlint(),
            (entry,out)->mesh.emit(entry,out,light,OverlayTexture.DEFAULT_UV,0,false,0,0));
    }
}
