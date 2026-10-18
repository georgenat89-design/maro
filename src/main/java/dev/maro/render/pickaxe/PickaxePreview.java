package dev.maro.render.pickaxe;

import dev.maro.mixin.DrawContextAccessor;
import dev.maro.mixin.GameRendererSpatialAccessor;
import dev.maro.mixin.GuiRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Full-resolution native GUI rendering; never enlarges the 16-pixel inventory atlas. */
public final class PickaxePreview extends SpecialGuiElementRenderer<PickaxePreview.State> {
    public record State(String name,PickaxeRenderer.Style style,int x1,int y1,int x2,int y2,float scale,float swing,float effectClock,ScreenRect bounds) implements SpecialGuiElementRenderState {
        @Override public ScreenRect scissorArea(){return bounds;}
    }
    private static final Identifier SURFACE=Identifier.of("maro","textures/misc/knife_satin.png");
    private PickaxePreview(){super(MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers());}
    public static void draw(DrawContext c,String name,PickaxeRenderer.Style style,int x,int y,int w,int h){
        var mc=MinecraftClient.getInstance();var gui=((GameRendererSpatialAccessor)mc.gameRenderer).maro$guiRenderer();
        ((GuiRendererAccessor)gui).maro$specialRenderers().computeIfAbsent(State.class,ignored->new PickaxePreview());
        var bounds=new ScreenRect(x,y,w,h).transformEachVertex(c.getMatrices());
        float scale=Math.min(bounds.width()*.65f,bounds.height()/1.95f)*style.zoom();
        ((DrawContextAccessor)c).maro$getState().addSpecialElement(new State(name,style,bounds.getLeft(),bounds.getTop(),bounds.getRight(),bounds.getBottom(),scale,PickaxeMotion.previewProgress(name),name.equals("Merry Mint Axe")?(System.nanoTime()%60_000_000_000L)/1e9f:0,bounds));
    }
    @Override public Class<State> getElementClass(){return State.class;}
    @Override protected String getName(){return "Maro pickaxe preview";}
    @Override protected float getYOffset(int height,int guiScale){return height*.5f;}
    @Override protected void render(State state,MatrixStack pose){
        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);
        var style=state.style();pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(12+style.pitch()));pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-22+style.yaw()));pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20));
        PickaxeMotion.apply(pose,state.name(),state.swing());
        var mesh=PickaxeModels.get(state.name());
        if(style.outline())mesh.emit(pose.peek(),vertexConsumers.getBuffer(RenderLayers.entityCutout(SURFACE)),0xF000F0,OverlayTexture.DEFAULT_UV,0,true,style.color(),style.width());
        mesh.emit(pose.peek(),vertexConsumers.getBuffer(PickaxeSurface.LAYER),0xF000F0,OverlayTexture.DEFAULT_UV,style.polish(),false,0,0);
    }
}
