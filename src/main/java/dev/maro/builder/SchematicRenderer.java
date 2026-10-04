package dev.maro.builder;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.module.impl.player.AutoBuilder;
import dev.maro.render.esp.Renderer3D;
import dev.maro.render.esp.ShapeMode;
import dev.maro.runtime.utils.render.color.Color;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;

/** Batched textured block models, shared world projection, depth-tested ghosts and status outlines. */
public final class SchematicRenderer {
    private static final MinecraftClient MC=MinecraftClient.getInstance();
    private static final RenderLayer GHOST=RenderLayer.of("maro_schematic_ghost",RenderSetup.builder(
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("maro","pipeline/schematic_ghost"))
            .withVertexShader("core/position_tex_color").withFragmentShader("core/position_tex_color").withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR,VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT).withCull(false).withDepthWrite(false).build()))
        .texture("Sampler0",SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,RenderLayers.BLOCK_SAMPLER).translucent().expectedBufferSize(1048576).build());
    private static final BufferAllocator ALLOCATOR=new BufferAllocator(1048576);
    private static final VertexConsumerProvider.Immediate BUFFERS=VertexConsumerProvider.immediate(ALLOCATOR);
    private record Model(BlockStateModel source,List<BlockModelPart> parts){}
    private static final LinkedHashMap<BlockState,Model> MODELS=new LinkedHashMap<>(128,.75f,true);
    private SchematicRenderer(){}
    public static int renderedCells;
    public static void render(AutoBuilder builder,Renderer3D shapes,WorldRenderContext context){
        if(MC.world==null)return;var camera=context.worldState().cameraRenderState.pos;var matrices=context.matrices();
        shapes.throughWalls(false);shapes.lineWidth(builder.outlineWidth.getFloat());
        renderedCells=0;
        if(builder.previewVisible()){
            for(int i:builder.visibleCells()){
                if(!builder.showCell(i))continue;var pos=builder.position(i);var state=builder.desired(i);
                double distance=Vec3d.ofCenter(pos).distanceTo(camera);if(distance>builder.previewRange.get())continue;
                float fade=builder.clarity.is("Fade Near Camera")?(float)MathHelper.clamp((distance-.3)/builder.fadeRadius.get(),0,1):1;
                if(fade<.01)continue;
                byte status=builder.state(i);int rgb=switch(status){case AutoBuilder.CORRECT->0x7EF0B6;case AutoBuilder.WRONG_BLOCK->0xFF7F8B;case AutoBuilder.WRONG_STATE->0xFFCB73;default->0x85BBFF;};
                double fill=status==AutoBuilder.WRONG_BLOCK?builder.wrongOpacity.get():status==AutoBuilder.WRONG_STATE?builder.stateOpacity.get():status==AutoBuilder.CORRECT?.05:builder.missingOpacity.get();
                Color face=color(rgb,(float)fill*fade),line=color(rgb,(builder.outlineStrength.is("Soft")?.45f:builder.outlineStrength.is("Bright")?1:.8f)*fade);
                boolean surface=!builder.clarity.is("Surface Only")||surface(builder,i);
                if(!surface)continue;
                if(builder.textured.get()&&!builder.previewMode.is("Outline Only")){
                    matrices.push();matrices.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
                    textured(state,pos,matrices.peek(),builder.ghostFill.getFloat()*fade);
                    matrices.pop();
                }else if(!builder.previewMode.is("Outline Only"))shapes.box(pos.getX()+.003,pos.getY()+.003,pos.getZ()+.003,pos.getX()+.997,pos.getY()+.997,pos.getZ()+.997,face,line,ShapeMode.Sides,0);
                if(builder.outlines.get()||builder.previewMode.is("Outline Only"))shapes.box(pos.getX()-.002,pos.getY()-.002,pos.getZ()-.002,pos.getX()+1.002,pos.getY()+1.002,pos.getZ()+1.002,new Color(0,0,0,0),line,ShapeMode.Lines,0);
                if(builder.throughWalls.get()&&status==AutoBuilder.MISSING){
                    shapes.throughWalls(true);shapes.box(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1,new Color(0,0,0,0),color(rgb,builder.occludedOpacity.getFloat()*fade),ShapeMode.Lines,0);shapes.throughWalls(false);
                }
                renderedCells++;
            }
            var anchor=builder.anchor();var schematic=builder.schematic();
            if(builder.buildBox.get())shapes.box(anchor.getX(),anchor.getY(),anchor.getZ(),anchor.getX()+schematic.rotatedWidth(builder.turns()),anchor.getY()+schematic.height,anchor.getZ()+schematic.rotatedLength(builder.turns()),new Color(0,0,0,0),color(0x98B8E5,.8f),ShapeMode.Lines,0);
            if(builder.originMarker.get()){
                shapes.line(anchor.getX(),anchor.getY()+.02,anchor.getZ(),anchor.getX()+1.2,anchor.getY()+.02,anchor.getZ(),color(0xFF8F9B,1));
                shapes.line(anchor.getX(),anchor.getY()+.02,anchor.getZ(),anchor.getX(),anchor.getY()+1.2,anchor.getZ(),color(0x8BF3BE,1));
                shapes.line(anchor.getX(),anchor.getY()+.02,anchor.getZ(),anchor.getX(),anchor.getY()+.02,anchor.getZ()+1.2,color(0x8EBAFF,1));
            }
        }
        if(builder.showContainers.get())for(var pos:builder.restockContainers()){
            if(Vec3d.ofCenter(pos).squaredDistanceTo(camera)>builder.containerRange.get()*builder.containerRange.get())continue;
            shapes.throughWalls(true);shapes.box(pos.getX()-.02,pos.getY()-.02,pos.getZ()-.02,pos.getX()+1.02,pos.getY()+1.02,pos.getZ()+1.02,new Color(0,0,0,0),color(0xA4D5FF,builder.containerAlpha.getFloat()),ShapeMode.Lines,0);shapes.throughWalls(false);
            if(builder.showLabels.get()){
                matrices.push();matrices.translate(pos.getX()+.5-camera.x,pos.getY()+1.25-camera.y,pos.getZ()+.5-camera.z);
                matrices.multiply(context.worldState().cameraRenderState.orientation);float size=.025f*builder.labelScale.getFloat();matrices.scale(size,-size,size);
                String label="Restock · "+pos.toShortString();MC.textRenderer.draw(label,-MC.textRenderer.getWidth(label)/2f,0,0xFFBCDFFF,true,matrices.peek().getPositionMatrix(),BUFFERS,TextRenderer.TextLayerType.SEE_THROUGH,0x60111B29,LightmapTextureManager.MAX_LIGHT_COORDINATE);matrices.pop();
            }
        }
        BUFFERS.draw();
    }
    private static boolean surface(AutoBuilder builder,int i){
        var p=builder.schematic().local(i);var s=builder.schematic();
        for(var direction:Direction.values()){
            var other=p.offset(direction);if(other.getX()<0||other.getY()<0||other.getZ()<0||other.getX()>=s.width||other.getY()>=s.height||other.getZ()>=s.length)return true;
            int index=s.index(other.getX(),other.getY(),other.getZ());if(s.state(index).isAir()||!builder.showCell(index))return true;
        }return false;
    }
    private static void textured(BlockState state,BlockPos pos,net.minecraft.client.util.math.MatrixStack.Entry transform,float alpha){
        var model=MC.getBlockRenderManager().getModel(state);Model cached=MODELS.get(state);
        if(cached==null||cached.source!=model){cached=new Model(model,model.getParts(net.minecraft.util.math.random.Random.create(42)));MODELS.put(state,cached);}
        if(MODELS.size()>512)MODELS.remove(MODELS.keySet().iterator().next());
        var consumer=BUFFERS.getBuffer(GHOST);
        for(var part:cached.parts){
            quads(part.getQuads(null),state,pos,transform,consumer,alpha);
            for(var side:Direction.values())quads(part.getQuads(side),state,pos,transform,consumer,alpha);
        }
    }
    private static void quads(List<BakedQuad> quads,BlockState state,BlockPos pos,net.minecraft.client.util.math.MatrixStack.Entry transform,VertexConsumer consumer,float alpha){
        for(var quad:quads){
            int tint=quad.hasTint()?MC.getBlockColors().getColor(state,MC.world,pos,quad.tintIndex()):0xFFFFFF;
            float shade=quad.shade()?switch(quad.face()){case DOWN->.6f;case NORTH,SOUTH->.8f;case EAST,WEST->.7f;default->1;}:1;
            int r=(int)(((tint>>16)&255)*shade),g=(int)(((tint>>8)&255)*shade),b=(int)((tint&255)*shade);
            for(int i=0;i<4;i++){var point=quad.getPosition(i);long uv=quad.getTexcoords(i);
                consumer.vertex(transform,point.x(),point.y(),point.z()).texture(net.minecraft.client.util.math.Vector2f.getX(uv),net.minecraft.client.util.math.Vector2f.getY(uv)).color(r,g,b,(int)(alpha*255));
            }
        }
    }
    private static Color color(int rgb,float alpha){return new Color((rgb>>16)&255,(rgb>>8)&255,rgb&255,MathHelper.clamp((int)(alpha*255),0,255));}
}
