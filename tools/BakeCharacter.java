import dev.maro.render.character.gltf.CharacterBake;
import dev.maro.render.character.gltf.ImageDecoder;
import dev.maro.render.character.gltf.SkinBake;
import dev.maro.render.character.skin.SkinFormat;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;

/**
 * Offline asset conversion: bakes a character GLB into the skinned .anc the game ships (rig,
 * weights, every mesh level, per-material textures). Original PNG/JPEG bytes are preserved;
 * textures over 8192 pixels are reduced and encoded losslessly as PNG. Falls back to the
 * rigid-part bake for a file the skinned bake cannot read. Run with the character sources, Gson
 * and the Fabric annotations on the class path: {@code java BakeCharacter in.glb out.anc}.
 */
public final class BakeCharacter {
    private static final ImageDecoder DECODER = data -> {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) throw new IOException("Unsupported texture");
        int w = image.getWidth(), h = image.getHeight();
        return new ImageDecoder.Picture(w, h, image.getRGB(0, 0, w, h, null, 0, w));
    };

    public static void main(String[] args) throws Exception {
        byte[] input = Files.readAllBytes(Path.of(args[0]));
        CharacterBake.Options options = new CharacterBake.Options();
        options.maxTexture = 8192;
        SkinFormat.Baked baked = SkinBake.build(input, options, DECODER);
        if (baked == null) {
            options.flat = .16f;
            var rigid = CharacterBake.bake(input, options, DECODER);
            Files.write(Path.of(args[1]), rigid.data());
            System.out.println("Baked rigid " + rigid.data().length + " bytes; limbs: " + rigid.limbs());
            return;
        }
        for (int i = 0; i < baked.images.length; i++) shrink(baked, i);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SkinFormat.write(new DataOutputStream(bytes), baked);
        Files.write(Path.of(args[1]), bytes.toByteArray());
        System.out.println("Baked skinned " + bytes.size() + " bytes; " + baked.rig);
    }

    /** Raw reduced images become PNG; authored compressed textures are never recompressed. */
    private static void shrink(SkinFormat.Baked b, int i) throws IOException {
        int w = b.imageWidth[i], h = b.imageHeight[i];
        BufferedImage image;
        if (b.imageRaw[i]) {
            byte[] raw = new InflaterInputStream(new ByteArrayInputStream(b.images[i])).readAllBytes();
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int[] argb = new int[w * h];
            for (int p = 0; p < argb.length; p++)
                argb[p] = (raw[p * 4 + 3] & 0xFF) << 24 | (raw[p * 4] & 0xFF) << 16 | (raw[p * 4 + 1] & 0xFF) << 8 | (raw[p * 4 + 2] & 0xFF);
            image.setRGB(0, 0, w, h, argb, 0, w);
        } else return;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        b.images[i] = out.toByteArray();
        b.imageRaw[i] = false;
    }
}
