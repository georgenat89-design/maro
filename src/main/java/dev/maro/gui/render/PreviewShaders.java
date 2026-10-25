package dev.maro.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.gui.preview.PreviewSettings;
import dev.maro.module.impl.visuals.*;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import java.util.OptionalInt;

/** Actual effect shaders on owned, bounded targets. No world rendering or framebuffer feedback. */
public final class PreviewShaders {
    private static final Pass SKY = new Pass("custom_sky", "custom_sky", "SkyData", "u_Sky", 144);
    private static final Pass SCENE = new Pass("player_esp", "preview_scene", "PreviewSceneData", null, 16);
    private static final Pass GEEK = new Pass("player_esp", "geeked", "GeekedData", "Scene", 48);
    private static final float[] SKY_VALUES = new float[36], SCENE_VALUES = new float[4], GEEK_VALUES = new float[12];
    private static float skyAspect = -1;
    private static Texture placeholder;
    private PreviewShaders() { }

    public static boolean hasResources() { return SKY.target != null || SCENE.target != null || GEEK.target != null; }
    private static Texture blank() {
        if (placeholder == null) {
            placeholder = new Texture(1, 1, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);
            placeholder.upload(new byte[]{0, 0, 0, -1});
        }
        return placeholder;
    }
    public static GpuTextureView sky(CustomSky module, double seconds, int width, int height) {
        if (module.showingPicture()) {
            module.loadStoredPicture();
            if (module.picture().texture() == null) return null;
        }
        float aspect = (float) width / height;
        if (aspect != skyAspect) {
            new Matrix4f().perspective((float)Math.toRadians(80), aspect, .05f, 128).rotateX(.25f).invert().get(SKY_VALUES);
            skyAspect = aspect;
        }
        var settings = module.previewUniformValues(2f / (height * 1.2f), seconds);
        System.arraycopy(settings, 0, SKY_VALUES, 16, settings.length);
        var texture = module.showingPicture() ? module.picture().texture() : blank();
        return SKY.draw(width, height, SKY_VALUES, texture.getGlTextureView(), module.previewWraps());
    }
    public static void draw(DrawContext ctx, dev.maro.module.Module module, float x, float y, float w, float h, double time) {
        float physical = (float)Math.hypot(ctx.getMatrices().m00(), ctx.getMatrices().m01())
                * (float)MinecraftClient.getInstance().getWindow().getScaleFactor();
        float reduction = Math.min(1, Math.min(640 / (w * physical), 360 / (h * physical)));
        int width = Math.max(64, Math.round(w * physical * reduction));
        int height = Math.max(32, Math.round(h * physical * reduction));
        GpuTextureView view;
        if (module instanceof CustomSky sky) view = sky(sky, time, width, height);
        else {
            var geeked = (Geeked)module;
            SCENE_VALUES[0] = (float)time;
            SCENE_VALUES[1] = (float)width / height;
            var source = SCENE.draw(width, height, SCENE_VALUES, null, false);
            GEEK_VALUES[0] = (float)time * (float)PreviewSettings.number(module, "Speed", 1);
            GEEK_VALUES[1] = (float)PreviewSettings.number(module, "Intensity", 0) / 100;
            GEEK_VALUES[2] = geeked.modeIndex(); GEEK_VALUES[3] = (float)width / height;
            GEEK_VALUES[4] = geeked.wobble(); GEEK_VALUES[5] = geeked.colors();
            GEEK_VALUES[6] = geeked.haze(); GEEK_VALUES[7] = geeked.separation();
            GEEK_VALUES[8] = geeked.vignette(); GEEK_VALUES[9] = 1f / width; GEEK_VALUES[10] = 1f / height;
            view = GEEK.draw(width, height, GEEK_VALUES, source, false);
        }
        if (view == null) {
            Fonts.drawCentered(ctx, "Choose an image to preview", x + w / 2, y + h / 2, 0xFFACB8C9, false, .7f);
            return;
        }
        var pose = new Matrix3x2f(ctx.getMatrices());
        var area = new ScreenRect((int)x, (int)y, Math.max(1, (int)w), Math.max(1, (int)h)).transformEachVertex(pose);
        var clip = Render2D.getScissor();
        var visible = clip == null ? area : area.intersection(clip);
        if (visible == null) return;
        int tint = dev.maro.util.ColorUtil.mulAlpha(-1, Render2D.getAlpha());
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(pose,
                new float[]{x,y,0,1, x,y+h,0,0, x+w,y+h,1,0, x+w,y,1,1}, new int[]{tint,tint,tint,tint},
                RenderPipelines.GUI_TEXTURED, TextureSetup.of(view, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)), visible, clip));
    }
    public static void release() {
        SKY.close(); SCENE.close(); GEEK.close(); skyAspect = -1;
        if (placeholder != null) { placeholder.close(); placeholder = null; }
    }
    private static final class Pass {
        final RenderPipeline pipeline;
        final String uniform, sampler;
        final java.nio.ByteBuffer data;
        SimpleFramebuffer target;
        GpuBuffer buffer, vertices, indices;
        Pass(String vertex, String fragment, String uniform, String sampler, int bytes) {
            this.uniform = uniform; this.sampler = sampler; data = BufferUtils.createByteBuffer(bytes);
            var builder = RenderPipeline.builder().withLocation(Identifier.of("maro", "pipeline/preview_" + fragment))
                    .withVertexShader(Identifier.of("maro", "core/" + vertex)).withFragmentShader(Identifier.of("maro", "core/" + fragment))
                    .withUniform(uniform, UniformType.UNIFORM_BUFFER).withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false);
            if (sampler != null) builder.withSampler(sampler);
            pipeline = builder.build();
        }
        GpuTextureView draw(int width, int height, float[] values, GpuTextureView source, boolean wraps) {
            if (target == null) target = new SimpleFramebuffer("Maro shader preview", width, height, false);
            else if (target.textureWidth != width || target.textureHeight != height) target.resize(width, height);
            var device = RenderSystem.getDevice();
            if (buffer == null) {
                buffer = device.createBuffer(() -> "Maro shader preview data", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data.capacity());
                var quad = BufferUtils.createByteBuffer(48);
                for (float[] point : new float[][]{{-1,-1}, {-1,1}, {1,1}, {1,-1}}) quad.putFloat(point[0]).putFloat(point[1]).putFloat(0);
                quad.flip(); var triangles = BufferUtils.createByteBuffer(24);
                for (int index : new int[]{0,1,2,0,2,3}) triangles.putInt(index);
                triangles.flip();
                vertices = device.createBuffer(() -> "Maro shader preview quad", GpuBuffer.USAGE_VERTEX, quad);
                indices = device.createBuffer(() -> "Maro shader preview indices", GpuBuffer.USAGE_INDEX, triangles);
            }
            data.clear(); for (float value : values) data.putFloat(value); data.flip();
            var encoder = device.createCommandEncoder(); encoder.writeToBuffer(buffer.slice(), data);
            try (var pass = encoder.createRenderPass(() -> "Maro isolated shader preview", target.getColorAttachmentView(), OptionalInt.of(0))) {
                pass.setPipeline(pipeline); pass.setUniform(uniform, buffer.slice());
                if (sampler != null) pass.bindTexture(sampler, source, RenderSystem.getSamplerCache().get(
                        wraps ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false));
                pass.setVertexBuffer(0, vertices); pass.setIndexBuffer(indices, VertexFormat.IndexType.INT); pass.drawIndexed(0, 0, 6, 1);
            }
            return target.getColorAttachmentView();
        }
        void close() {
            if (target != null) { target.delete(); target = null; }
            if (buffer != null) { buffer.close(); buffer = null; }
            if (vertices != null) { vertices.close(); vertices = null; }
            if (indices != null) { indices.close(); indices = null; }
        }
    }
}
