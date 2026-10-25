package dev.maro.gui.render;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.maro.gui.hud.HandPlacementScreen;
import dev.maro.mixin.GameRendererSpatialAccessor;
import dev.maro.render.capture.CapturePass;
import dev.maro.render.capture.CleanCapture;
import dev.maro.render.capture.StreamproofScenePolicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.ProjectionMatrix3;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import dev.maro.module.impl.visuals.ViewModel;
import net.minecraft.client.util.math.MatrixStack;

/** Only the private hand pass is repeated. The world still renders exactly once. */
public final class HandPlacementRender {
    private static SimpleFramebuffer beforeHands;
    private static ProjectionMatrix3 projection;
    private static com.mojang.blaze3d.buffers.GpuBufferSlice nativeLights;
    private static final Matrix4f view=new Matrix4f();
    private static final long[] emptyArms=new long[2];
    private static final Matrix4f trackingMatrix=new Matrix4f();
    private static final double[][] handAxes=new double[2][9],grips=new double[2][3];
    private static final boolean[] gripValid=new boolean[2];
    private static final ViewModel.Placement[] gripPlacement=new ViewModel.Placement[2];
    private static long worldCopies,privateFrames;
    private static boolean worldPending,ready,registered;
    private static float fov=70;
    private HandPlacementRender(){}

    public static long emptyArmDraws(Arm arm){return emptyArms[arm.ordinal()];}
    public static void emptyArmDrawn(Arm arm){emptyArms[arm.ordinal()]++;}
    public static long worldCopies(){return worldCopies;}
    public static long privateFrames(){return privateFrames;}
    public static boolean hasResources(){return beforeHands!=null||projection!=null;}
    /** Observe the native reference point, rather than assuming every item has the same depth. */
    public static void beginHand(Arm arm,MatrixStack matrices){
        if(ViewModel.editor()==null)return;
        gripValid[arm.ordinal()]=false;gripPlacement[arm.ordinal()]=null;
        trackingMatrix.set(RenderSystem.getModelViewStack()).mul(matrices.peek().getPositionMatrix());
        var axes=handAxes[arm.ordinal()];
        axes[0]=trackingMatrix.m00();axes[1]=trackingMatrix.m01();axes[2]=trackingMatrix.m02();
        axes[3]=trackingMatrix.m10();axes[4]=trackingMatrix.m11();axes[5]=trackingMatrix.m12();
        axes[6]=trackingMatrix.m20();axes[7]=trackingMatrix.m21();axes[8]=trackingMatrix.m22();
    }
    public static void grip(Arm arm,MatrixStack matrices){
        var editor=ViewModel.editor();if(editor==null)return;
        trackingMatrix.set(RenderSystem.getModelViewStack()).mul(matrices.peek().getPositionMatrix());
        var point=grips[arm.ordinal()];point[0]=trackingMatrix.m30();point[1]=trackingMatrix.m31();point[2]=trackingMatrix.m32();
        gripValid[arm.ordinal()]=Double.isFinite(point[2])&&point[2]<-.05;
        gripPlacement[arm.ordinal()]=editor.draft(arm);
    }
    public static double gripDepth(Arm arm){return gripValid[arm.ordinal()]?-grips[arm.ordinal()][2]:Double.NaN;}
    /** Solve the native perspective projection for cursor movement, including bobbed axes. */
    public static double[] dragDelta(Arm arm,ViewModel.Placement current,double dx,double dy,int width,int height){
        double tangent=Math.tan(Math.toRadians(handFov()*.5));
        double aspect=(double)Math.max(1,width)/Math.max(1,height);
        var stretch=dev.maro.module.impl.visuals.StretchRes.get();
        double projectionAspect=stretch!=null&&stretch.isEnabled()?stretch.targetAspect():aspect;
        if(gripValid[arm.ordinal()]&&gripPlacement[arm.ordinal()]!=null){
            var a=handAxes[arm.ordinal()];var p=grips[arm.ordinal()];var old=gripPlacement[arm.ordinal()];
            // Several cursor events may arrive before the next render-state observation.
            double x=p[0]+a[0]*(current.x()-old.x())+a[3]*(current.y()-old.y())+a[6]*(current.depth()-old.depth());
            double y=p[1]+a[1]*(current.x()-old.x())+a[4]*(current.y()-old.y())+a[7]*(current.depth()-old.depth());
            double z=p[2]+a[2]*(current.x()-old.x())+a[5]*(current.y()-old.y())+a[8]*(current.depth()-old.depth());
            double fx=1/(tangent*projectionAspect),fy=1/tangent;
            double nx=fx*x/-z+2*dx/Math.max(1,width),ny=fy*y/-z-2*dy/Math.max(1,height);
            double xx=fx*a[0]+nx*a[2],xy=fx*a[3]+nx*a[5],yx=fy*a[1]+ny*a[2],yy=fy*a[4]+ny*a[5];
            double bx=-nx*z-fx*x,by=-ny*z-fy*y,det=xx*yy-xy*yx;
            if(Math.abs(det)>1e-8){double moveX=(bx*yy-xy*by)/det,moveY=(xx*by-bx*yx)/det;
                if(Double.isFinite(moveX)&&Double.isFinite(moveY))return new double[]{moveX,moveY};}
        }
        double unit=2*Math.max(.25,.72-current.depth())*tangent/Math.max(1,height);
        return new double[]{dx*unit*projectionAspect/aspect,-dy*unit};
    }
    private static HandPlacementScreen screen(){
        var screen=MinecraftClient.getInstance().currentScreen;
        return screen instanceof HandPlacementScreen editor&&!editor.finished()?editor:null;
    }
    /** Actual native hand projection has its own FOV, independent of the terrain's FOV. */
    public static float handFov(){
        var client=MinecraftClient.getInstance();
        if(client.gameRenderer!=null&&client.player!=null){
            float value=((GameRendererSpatialAccessor)client.gameRenderer).maro$fov(client.gameRenderer.getCamera(),
                    client.getRenderTickCounter().getTickProgress(false),false);
            if(Float.isFinite(value)&&value>1&&value<179)fov=value;
        }
        return fov;
    }
    private static void register(){
        if(registered)return;registered=true;
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloader(Identifier.of("maro","hand_placement"),(net.minecraft.resource.SynchronousResourceReloader) manager->
                        MinecraftClient.getInstance().execute(HandPlacementRender::release));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->release());
    }
    public static void worldBegin(Matrix4fc cameraView){
        ready=false;
        worldPending=screen()!=null&&CleanCapture.activeFrame()&&CleanCapture.protectedLayer()&&StreamproofScenePolicy.ready();
        if(worldPending){register();view.set(cameraView);}
    }
    /** Called before the game's native hand pass, while only world color is present. */
    public static void worldEnd(){
        if(!worldPending)return;worldPending=false;
        var client=MinecraftClient.getInstance();var source=client.getFramebuffer();
        int width=source.textureWidth,height=source.textureHeight;
        if(beforeHands==null)beforeHands=new SimpleFramebuffer("Hand placement pre-hand world",width,height,false);
        else if(beforeHands.textureWidth!=width||beforeHands.textureHeight!=height)beforeHands.resize(width,height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source.getColorAttachment(),beforeHands.getColorAttachment(),
                0,0,0,0,0,width,height);
        nativeLights=RenderSystem.getShaderLights();
        ready=true;worldCopies++;
    }
    /** CleanCapture has kept ordinary hands for capture. Replace only the local scene's hands. */
    public static void afterCleanGui(){
        var client=MinecraftClient.getInstance();
        if(screen()==null||CapturePass.active()||!CleanCapture.activeFrame()||!CleanCapture.protectedLayer()
                ||!StreamproofScenePolicy.ready()||!ready||beforeHands==null||client.player==null)return;
        var destination=client.getFramebuffer();
        if(destination.textureWidth!=beforeHands.textureWidth||destination.textureHeight!=beforeHands.textureHeight)return;
        var encoder=RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(beforeHands.getColorAttachment(),destination.getColorAttachment(),0,0,0,0,0,
                destination.textureWidth,destination.textureHeight);
        encoder.clearDepthTexture(destination.getDepthAttachment(),1);
        var previousProjection=RenderSystem.getProjectionMatrixBuffer();var previousType=RenderSystem.getProjectionType();
        var previousColor=RenderSystem.outputColorTextureOverride;var previousDepth=RenderSystem.outputDepthTextureOverride;
        var previousLights=RenderSystem.getShaderLights();
        try{
            if(projection==null)projection=new ProjectionMatrix3("Hand placement private hands",.05f,100);
            int width=destination.textureWidth;
            var stretch=dev.maro.module.impl.visuals.StretchRes.get();
            if(stretch!=null&&stretch.isEnabled())width=Math.max(1,Math.round(destination.textureHeight*stretch.targetAspect()));
            RenderSystem.setProjectionMatrix(projection.set(width,destination.textureHeight,handFov()),ProjectionType.PERSPECTIVE);
            RenderSystem.outputColorTextureOverride=destination.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride=destination.getDepthAttachmentView();
            if(nativeLights!=null)RenderSystem.setShaderLights(nativeLights);
            float delta=client.getRenderTickCounter().getTickProgress(false);
            ((GameRendererSpatialAccessor)client.gameRenderer).maro$renderPlacementHands(delta,false,view);
            client.gameRenderer.getEntityRenderDispatcher().render();
            client.getBufferBuilders().getEntityVertexConsumers().draw();
            privateFrames++;
        }finally{
            RenderSystem.outputColorTextureOverride=previousColor;RenderSystem.outputDepthTextureOverride=previousDepth;
            RenderSystem.setShaderLights(previousLights);
            RenderSystem.setProjectionMatrix(previousProjection,previousType);
        }
    }
    public static void release(){
        worldPending=ready=false;
        nativeLights=null;
        java.util.Arrays.fill(gripValid,false);java.util.Arrays.fill(gripPlacement,null);
        if(beforeHands!=null){beforeHands.delete();beforeHands=null;}
        if(projection!=null){projection.close();projection=null;}
    }
}
