package dev.maro.render.pickaxe;

import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

/** Neutral surface and soft mesh lighting keep painted colors out of the knife's steel texture. */
public final class PickaxeSurface {
    private static final Identifier WHITE=Identifier.of("maro","textures/misc/pickaxe_surface.png");
    public static final RenderLayer LAYER=make();
    private PickaxeSurface(){}
    private static RenderLayer make(){
        var nativePipeline=net.minecraft.client.gl.RenderPipelines.ENTITY_CUTOUT_NO_CULL;
        var builder=com.mojang.blaze3d.pipeline.RenderPipeline.builder()
            .withLocation(Identifier.of("maro","pipeline/pickaxe_surface"))
            .withVertexShader(nativePipeline.getVertexShader()).withFragmentShader(nativePipeline.getFragmentShader())
            .withShaderDefine("NO_CARDINAL_LIGHTING").withShaderDefine("ALPHA_CUTOUT",.1f)
            .withVertexFormat(nativePipeline.getVertexFormat(),nativePipeline.getVertexFormatMode())
            .withCull(false).withDepthWrite(true).withDepthTestFunction(nativePipeline.getDepthTestFunction());
        nativePipeline.getSamplers().forEach(builder::withSampler);
        nativePipeline.getUniforms().forEach(u->builder.withUniform(u.name(),u.type()));
        var pipeline=net.minecraft.client.gl.RenderPipelines.register(builder.build());
        return RenderLayer.of("maro_pickaxe_surface",RenderSetup.builder(pipeline).texture("Sampler0",WHITE).useLightmap().useOverlay().build());
    }
}
