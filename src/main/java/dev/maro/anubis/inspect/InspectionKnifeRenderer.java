package dev.maro.anubis.inspect;

import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** First-person knife replacement; the original stack and all gameplay actions stay native. */
public final class InspectionKnifeRenderer {
    private static final Identifier SATIN=Identifier.of("maro","textures/misc/knife_satin.png");
    private static final Identifier DAMASCUS=Identifier.of("maro","textures/misc/knife_damascus.png");
    private static final com.mojang.blaze3d.pipeline.RenderPipeline SURFACE=surfacePipeline();
    private static final net.minecraft.client.render.RenderLayer SATIN_LAYER=surfaceLayer(SATIN,"satin");
    private static final net.minecraft.client.render.RenderLayer DAMASCUS_LAYER=surfaceLayer(DAMASCUS,"damascus");
    private static final int[] STEEL={0xFFD6DBDF,0xFF8B959E,0xFFF4F7F9,0xFF34383D,0xFF191D21,
            0xFF9DA8AE,0xFF128CD0,0xFF66624B,0xFFC6BEA7};
    private static final java.util.Map<String,int[]> PALETTES=new java.util.HashMap<>();
    private InspectionKnifeRenderer() { }
    private static com.mojang.blaze3d.pipeline.RenderPipeline surfacePipeline(){
        var nativePipeline=net.minecraft.client.gl.RenderPipelines.ENTITY_CUTOUT_NO_CULL;
        var builder=com.mojang.blaze3d.pipeline.RenderPipeline.builder()
            .withLocation(Identifier.of("maro","pipeline/knife_surface"))
            .withVertexShader(nativePipeline.getVertexShader()).withFragmentShader(nativePipeline.getFragmentShader())
            .withShaderDefine("NO_CARDINAL_LIGHTING").withShaderDefine("ALPHA_CUTOUT",.1f)
            .withVertexFormat(nativePipeline.getVertexFormat(),nativePipeline.getVertexFormatMode())
            .withCull(false).withDepthWrite(true).withDepthTestFunction(nativePipeline.getDepthTestFunction());
        nativePipeline.getSamplers().forEach(builder::withSampler);
        nativePipeline.getUniforms().forEach(uniform->builder.withUniform(uniform.name(),uniform.type()));
        return net.minecraft.client.gl.RenderPipelines.register(builder.build());
    }
    private static net.minecraft.client.render.RenderLayer surfaceLayer(Identifier texture,String name){
        return net.minecraft.client.render.RenderLayer.of("maro_knife_"+name,net.minecraft.client.render.RenderSetup.builder(SURFACE)
            .texture("Sampler0",texture).useLightmap().useOverlay().build());
    }
    private static int[] palette(String name,String finish) {
        return PALETTES.computeIfAbsent(name+"/"+finish,key->{
            int[] p=STEEL.clone();
            switch(name){
                case "Bayonet" -> p[3]=0xFF3B4237;
                case "Bowie" -> {p[7]=0xFF76543D;p[8]=0xFFB29962;}
                case "Kukri" -> {p[7]=0xFF594134;p[8]=0xFF9B8050;}
                case "Talon","Stiletto" -> p[8]=0xFFDAD2BC;
                case "Butterfly" -> {p[3]=0xFF303B42;p[5]=0xFFB2BBC0;}
                case "Maro Rift" -> {p[3]=0xFF242D35;p[6]=0xFF169FCB;}
            }
            switch(finish){
                case "Black Cerakote" -> {p[0]=0xFF41484D;p[1]=0xFF2B3338;p[2]=0xFF9AA5AD;p[5]=0xFF68747D;}
                case "Maro Blue" -> {p[0]=0xFF4389B5;p[1]=0xFF224E70;p[2]=0xFFC2DEE6;p[6]=0xFF33B9E8;}
                case "Damascus" -> {p[0]=0xFFDFDFDB;p[1]=0xFFAFB3B2;p[2]=0xFFF5F4EF;}
            }
            return p;
        });
    }
    public static void draw(MatrixStack pose,OrderedRenderCommandQueue queue,int light,ItemStack stack,
                            String name,InspectionMotion.Pose at,double progress,boolean inspecting,
                            boolean alternate,boolean left,boolean showArm,float size,float equip,float swing,
                            String finish,float reflections,boolean tacticalGloves) {
        draw(pose,queue,light,stack,name,at,progress,inspecting,alternate,left,showArm,size,equip,swing,finish,reflections,tacticalGloves,null);
    }
    public static void draw(MatrixStack pose,OrderedRenderCommandQueue queue,int light,ItemStack stack,
                            String name,InspectionMotion.Pose at,double progress,boolean inspecting,
                            boolean alternate,boolean left,boolean showArm,float size,float equip,float swing,
                            String finish,float reflections,boolean tacticalGloves,float[] trace) {
        var model=InspectionKnifeModels.get(name);
        int[] colors=finish.equals("Item Material")?KnifeMaterial.of(stack).palette:palette(name,finish);
        var solid=finish.equals("Damascus")?DAMASCUS_LAYER:SATIN_LAYER;
        float polish=reflections*(finish.equals("Black Cerakote")?.2f:1);
        pose.push();
        try {
            pose.translate((left?-1:1)*.39f,(model.reverse()?-.12f:-.27f)-equip*.4f,-.82f);
            if(left)pose.scale(-1,1,1);
            float attack=(float)Math.sin(Math.sqrt(swing)*Math.PI);
            pose.translate(-attack*.12f,attack*.08f,-attack*.08f);
            pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-attack*35));
            pose.translate(at.x(),at.y(),at.z());
            if(showArm&&!tacticalGloves) {
                var arm=FirstPersonArm.arm();
                if(arm!=null) {
                    pose.push();
                    try {
                        pose.translate(.014f,-.026f,-.005f);
                        pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(at.yaw()*.35f));
                        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(at.pitch()*.55f));
                        pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(at.roll()*.45f));
                        pose.scale(.46f,.46f,.46f);
                        FirstPersonArm.place(pose,new Vector3f(),new Vector3f(.55f,-.48f,.60f),
                                arm.slim(),hand->arm.draw(hand,left,queue,light));
                    } finally {pose.pop();}
                }
            }
            pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(at.yaw()));
            pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(at.pitch()));
            pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(at.roll()));
            pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(18));
            if(showArm&&tacticalGloves){
                pose.push();
                // The sleeve and wrist share a transform. A reverse knife grip does not invert the arm.
                pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(125));
                queue.submitCustom(pose,RenderLayers.entityCutoutNoCull(SATIN),
                    (entry,out)->InspectionGlove.SLEEVE.emitSurface(entry,out,InspectionGlove.COLORS,light,.03f));
                queue.submitCustom(pose,RenderLayers.entityCutoutNoCull(SATIN),
                    (entry,out)->InspectionGlove.MESH.emitSurface(entry,out,InspectionGlove.COLORS,light,.08f));
                pose.pop();
            }
            // Only the released knife moves through the toss arc; the hand remains below it.
            pose.translate(0,at.air(),0);
            pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(model.reverse()?-125:125));
            pose.scale(size,size,size);
            float h1=inspecting&&name.equals("Butterfly")?InspectionMotion.hinge(progress,1,alternate):0;
            float h2=inspecting&&name.equals("Butterfly")?InspectionMotion.hinge(progress,2,alternate):0;
            if(name.equals("Butterfly")) {
                if(h1==0&&h2==0)pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(at.spin()));
                // The safe handle stays in the hand while the hinge and blade travel around it.
                double a=Math.toRadians(h1);pose.translate(-model.grip()*(float)Math.cos(a),-model.grip()*(float)Math.sin(a),0);
            } else {
                float hold=model.grip()+(model.pivot()-model.grip())*at.grip();
                pose.translate(-hold,0,0);pose.translate(model.pivot(),0,0);
                pose.multiply((model.axis()==0?RotationAxis.POSITIVE_X:RotationAxis.POSITIVE_Z).rotationDegrees(at.spin()));
                pose.translate(-model.pivot(),0,0);
            }
            for(var part:model.parts()) {
                pose.push();
                try {
                    if(part.hinge()!=0)pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(part.hinge()==1?h1:h2));
                    if(trace!=null&&part.hinge()==0)part.mesh().sampleBlade(pose.peek().getPositionMatrix(),trace);
                    queue.submitCustom(pose,solid,(entry,out)->part.mesh().emitSurface(entry,out,colors,light,polish));
                    if(stack.hasGlint())queue.getBatchingQueue(1).submitCustom(pose,RenderLayers.entityGlint(),
                            (entry,out)->part.mesh().emit(entry,out,colors,light,KnifeMesh.Shine.NONE));
                } finally {pose.pop();}
            }
        } finally {pose.pop();}
    }
}
