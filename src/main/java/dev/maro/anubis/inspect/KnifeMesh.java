// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.inspect;

import java.util.Arrays;
import dev.maro.anubis.inspect.KnifeMaterial;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;

@Environment(value=EnvType.CLIENT)
final class KnifeMesh {
    private static final float SHINE_WIDTH = 2.6f;
    private static final float SHINE_BOOST = 1.5f;
    private static final float WIPE_FRONT = 1.5f;
    private static final float GLOW_ALPHA = 0.85f;
    private static final float GLINT_UV = 0.09f;
    private final float[] pos;
    private final float[] normal;
    private final byte[] material;
    private final float[] tone;
    private final int quads;
    private final float shineFrom;
    private final float shineTo;
    private float[] surfaceEnds;

    private KnifeMesh(float[] pos, float[] normal, byte[] material, float[] tone, int quads, float shineFrom, float shineTo) {
        this.pos = pos;
        this.normal = normal;
        this.material = material;
        this.tone = tone;
        this.quads = quads;
        this.shineFrom = shineFrom;
        this.shineTo = shineTo;
    }

    void emit(MatrixStack.Entry pose, VertexConsumer out, int[] palette, int light, Shine shine) {
        boolean shining = shine.gain() > 0.004f;
        for (int q = 0; q < this.quads; ++q) {
            byte mat = this.material[q];
            int argb = palette[mat];
            boolean lit = shining && (shine.tail() > 0.0f ? KnifeMaterial.blade(mat) : KnifeMaterial.metallic(mat));
            float nx = this.normal[q * 3];
            float ny = this.normal[q * 3 + 1];
            float nz = this.normal[q * 3 + 2];
            for (int v = 0; v < 4; ++v) {
                int p = q * 12 + v * 3;
                float x = this.pos[p];
                float y = this.pos[p + 1];
                float z = this.pos[p + 2];
                float bright = this.tone[q];
                if (lit) {
                    bright += shine.gain() * 1.5f * this.profile(x, shine);
                }
                out.vertex(pose, x, y, z).color(KnifeMesh.tint(argb, bright)).texture((x + z) * 0.09f, y * 0.09f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(pose, nx, ny, nz);
            }
        }
    }

    void emitGlow(MatrixStack.Entry pose, VertexConsumer out, int rgb, Shine shine) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        float[] alpha = new float[4];
        for (int q = 0; q < this.quads; ++q) {
            if (!KnifeMaterial.blade(this.material[q])) continue;
            float most = 0.0f;
            for (int v = 0; v < 4; ++v) {
                alpha[v] = Math.min(1.0f, shine.gain() * 0.85f * this.profile(this.pos[q * 12 + v * 3], shine));
                most = Math.max(most, alpha[v]);
            }
            if (most < 0.01f) continue;
            float nx = this.normal[q * 3];
            float ny = this.normal[q * 3 + 1];
            float nz = this.normal[q * 3 + 2];
            for (int v = 0; v < 4; ++v) {
                int p = q * 12 + v * 3;
                out.vertex(pose, this.pos[p], this.pos[p + 1], this.pos[p + 2]).color(r, g, b, Math.round(alpha[v] * 255.0f)).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(pose, nx, ny, nz);
            }
        }
    }

    /** View-dependent polished metal and a material atlas, isolated from legacy voxel rendering. */
    void emitSurface(MatrixStack.Entry pose,VertexConsumer out,int[] palette,int light,float reflectivity) {
        var n=new org.joml.Vector3f();var view=new org.joml.Vector3f();var half=new org.joml.Vector3f();
        for(int q=0;q<quads;q++) {
            int mat=material[q];boolean metal=KnifeMaterial.metallic(mat);
            n.set(normal[q*3],normal[q*3+1],normal[q*3+2]);pose.getNormalMatrix().transform(n);n.normalize();
            for(int v=0;v<4;v++) {
                int p=q*12+v*3;float x=pos[p],y=pos[p+1],z=pos[p+2];
                pose.getPositionMatrix().transformPosition(x,y,z,view);view.negate().normalize();
                half.set(view).add(.25f,.55f,.80f).normalize();
                float spec=(float)Math.pow(Math.max(0,n.dot(half)),mat==2?86:mat<=1?42:28);
                float rim=(float)Math.pow(1-Math.min(1,Math.abs(n.dot(view))),4);
                  float reflectedY=2*n.y*n.dot(view)-view.y;
                  float sky=Math.clamp((reflectedY+.45f)/1.2f,0,1);
                  float band=(reflectedY-.12f)*6;
                  float horizon=1/(1+band*band);
                  float brightness=tone[q]*(metal?.82f+reflectivity*(sky*.42f+horizon*.66f+spec*.7f+rim*.18f):.98f+spec*.025f);
                float u=Math.max(.015f,Math.min(.985f,(x+.25f)*1.55f+z*.55f));
                float local=Math.max(.025f,Math.min(.975f,(y+.31f)*2.15f+z*.75f));
                out.vertex(pose,x,y,z).color(tint(palette[mat],brightness)).texture(u,(mat+.03f+local*.94f)/9f)
                        .overlay(OverlayTexture.DEFAULT_UV).light(light)
                        .normal(pose,normal[q*3],normal[q*3+1],normal[q*3+2]);
            }
        }
    }
    void sampleBlade(org.joml.Matrix4f matrix,float[] out){
        if(surfaceEnds==null){
            float lo=Float.POSITIVE_INFINITY,hi=Float.NEGATIVE_INFINITY,ly=0,hy=0;
            for(int q=0;q<quads;q++)if(KnifeMaterial.blade(material[q]))for(int v=0;v<4;v++){
                int p=q*12+v*3;if(pos[p]<lo){lo=pos[p];ly=pos[p+1];}if(pos[p]>hi){hi=pos[p];hy=pos[p+1];}
            }
            surfaceEnds=new float[]{lo,ly,0,hi,hy,0};
        }
        var v=new org.joml.Vector3f();
        for(int i=0;i<6;i+=3){matrix.transformPosition(surfaceEnds[i],surfaceEnds[i+1],surfaceEnds[i+2],v);out[i]=v.x;out[i+1]=v.y;out[i+2]=v.z;}
    }

    private float profile(float x, Shine shine) {
        float d = x - (this.shineFrom + (this.shineTo - this.shineFrom) * shine.at());
        if (shine.tail() <= 0.0f) {
            float k = d / 2.6f;
            return (float)Math.exp(-k * k);
        }
        if (d > 0.0f) {
            float k = d / 1.5f;
            return (float)Math.exp(-k * k);
        }
        return (float)Math.exp(d / shine.tail());
    }

    private static int tint(int argb, float k) {
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        if (k <= 1.0f) {
            r = (int)((float)r * k);
            g = (int)((float)g * k);
            b = (int)((float)b * k);
        } else {
            float w = Math.min(1.0f, k - 1.0f);
            r += (int)((float)(255 - r) * w);
            g += (int)((float)(255 - g) * w);
            b += (int)((float)(255 - b) * w);
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Environment(value=EnvType.CLIENT)
    record Shine(float at, float gain, float tail) {
        static final Shine NONE = new Shine(0.0f, 0.0f, 0.0f);
    }

    /** Beveled continuous surfaces for the reference knives, without voxel stair steps. */
    static final class Builder {
        private final java.util.List<float[]> faces = new java.util.ArrayList<>();
        private final java.util.List<Integer> materials = new java.util.ArrayList<>();
        void quad(int material, float... xyz) {
            if (xyz.length != 12) throw new IllegalArgumentException("Four vertices required");
            faces.add(xyz); materials.add(material);
        }
        void reverseQuad(int material,float... xyz) {
            quad(material,xyz[0],xyz[1],xyz[2],xyz[9],xyz[10],xyz[11],
                    xyz[6],xyz[7],xyz[8],xyz[3],xyz[4],xyz[5]);
        }
        KnifeMesh build() {
            int count = faces.size();
            float[] p = new float[count * 12], normals = new float[count * 3], tones = new float[count];
            byte[] inks = new byte[count];
            for (int i = 0; i < count; i++) {
                float[] face = faces.get(i);
                System.arraycopy(face, 0, p, i * 12, 12);
                var a = new org.joml.Vector3f(face[3]-face[0],face[4]-face[1],face[5]-face[2]);
                var b = new org.joml.Vector3f(face[6]-face[0],face[7]-face[1],face[8]-face[2]);
                a.cross(b);
                if (a.lengthSquared() < 1e-15f) a.set(0,0,1); else a.normalize();
                normals[i*3]=a.x; normals[i*3+1]=a.y; normals[i*3+2]=a.z;
                inks[i]=materials.get(i).byteValue(); tones[i]=1;
            }
            return new KnifeMesh(p,normals,inks,tones,count,0,.3f);
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Voxels {
        private static final int SX = 128;
        private static final int SY = 64;
        private static final int SZ = 16;
        private static final int OX = 64;
        private static final int OY = 32;
        private static final int OZ = 8;
        private static final int[][] FACES = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        private final byte[] cells = new byte[131072];
        private final float[] shade = new float[131072];

        Voxels() {
        }

        void set(int x, int y, int z, int material) {
            this.set(x, y, z, material, 1.0f);
        }

        void set(int x, int y, int z, int material, float tone) {
            int i = Voxels.index(x, y, z);
            this.cells[i] = (byte)(material + 1);
            this.shade[i] = tone;
        }

        void clear(int x, int y, int z) {
            this.cells[Voxels.index((int)x, (int)y, (int)z)] = 0;
        }

        boolean filled(int x, int y, int z) {
            int ix = x + 64;
            int iy = y + 32;
            int iz = z + 8;
            if (ix < 0 || iy < 0 || iz < 0 || ix >= 128 || iy >= 64 || iz >= 16) {
                return false;
            }
            return this.cells[(ix * 64 + iy) * 16 + iz] != 0;
        }

        KnifeMesh mesh(float shineFrom, float shineTo) {
            int capacity = 256;
            float[] pos = new float[capacity * 12];
            float[] normal = new float[capacity * 3];
            byte[] material = new byte[capacity];
            float[] tone = new float[capacity];
            int quads = 0;
            for (int ix = 0; ix < 128; ++ix) {
                for (int iy = 0; iy < 64; ++iy) {
                    for (int iz = 0; iz < 16; ++iz) {
                        int cell = (ix * 64 + iy) * 16 + iz;
                        if (this.cells[cell] == 0) continue;
                        int x = ix - 64;
                        int y = iy - 32;
                        int z = iz - 8;
                        for (int[] f : FACES) {
                            if (this.filled(x + f[0], y + f[1], z + f[2])) continue;
                            if (quads == capacity) {
                                pos = Arrays.copyOf(pos, (capacity *= 2) * 12);
                                normal = Arrays.copyOf(normal, capacity * 3);
                                material = Arrays.copyOf(material, capacity);
                                tone = Arrays.copyOf(tone, capacity);
                            }
                            Voxels.face(pos, quads * 12, x, y, z, f);
                            normal[quads * 3] = f[0];
                            normal[quads * 3 + 1] = f[1];
                            normal[quads * 3 + 2] = f[2];
                            material[quads] = (byte)(this.cells[cell] - 1);
                            tone[quads] = this.shade[cell];
                            ++quads;
                        }
                    }
                }
            }
            return new KnifeMesh(Arrays.copyOf(pos, quads * 12), Arrays.copyOf(normal, quads * 3), Arrays.copyOf(material, quads), Arrays.copyOf(tone, quads), quads, shineFrom, shineTo);
        }

        private static void face(float[] out, int at, int x, int y, int z, int[] f) {
            float x0 = x;
            float x1 = x + 1;
            float y0 = y;
            float y1 = y + 1;
            float z0 = z;
            float z1 = z + 1;
            float[] c = f[0] == 1 ? new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1} : (f[0] == -1 ? new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0} : (f[1] == 1 ? new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0} : (f[1] == -1 ? new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1} : (f[2] == 1 ? new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1} : new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}))));
            System.arraycopy(c, 0, out, at, 12);
        }

        private static int index(int x, int y, int z) {
            return ((x + 64) * 64 + (y + 32)) * 16 + (z + 8);
        }
    }
}
