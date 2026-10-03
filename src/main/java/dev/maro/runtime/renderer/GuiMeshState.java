package dev.maro.runtime.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
/** Immutable GUI batch; texture, order and transforms use vanilla's render state. */
public record GuiMeshState(Matrix3x2f pose, float[] xyuv, int[] colors, RenderPipeline pipeline,
                           TextureSetup textureSetup, ScreenRect bounds) implements SimpleGuiElementRenderState {
 @Override public ScreenRect scissorArea(){return null;}
 @Override public void setupVertices(VertexConsumer out){
  boolean textured=textureSetup.texure0()!=null;
  for(int i=0;i<colors.length;i++){
   var v=out.vertex(pose,xyuv[i*4],xyuv[i*4+1]).color(colors[i]);
   if(textured)v.texture(xyuv[i*4+2],xyuv[i*4+3]);
  }
 }
}
