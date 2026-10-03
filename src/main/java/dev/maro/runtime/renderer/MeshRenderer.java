package dev.maro.runtime.renderer;

import org.joml.Matrix4f;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.gl.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
public final class MeshRenderer {
 private MeshBuilder mesh;private Matrix4f transform;private TextureSetup texture;
 public static MeshRenderer begin(){return new MeshRenderer();}
 public MeshRenderer attachments(Object ignored){return this;}
 public MeshRenderer pipeline(Object ignored){return this;}
 public MeshRenderer mesh(MeshBuilder mesh){this.mesh=mesh;return this;}
 public MeshRenderer transform(Matrix4f matrix){transform=matrix;return this;}
 public MeshRenderer sampler(String name,GpuTextureView texture,GpuSampler sampler){this.texture=TextureSetup.of(texture,sampler);return this;}
 public void end(){mesh.submit(texture,transform);}
}
