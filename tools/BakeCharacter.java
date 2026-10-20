import dev.maro.render.character.gltf.CharacterBake;
import dev.maro.render.character.gltf.ImageDecoder;
import dev.maro.render.character.gltf.SkinBake;
import dev.maro.render.character.skin.SkinFormat;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.zip.InflaterInputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Offline asset conversion: bakes a character GLB into the skinned .anc the game ships (rig,
 * weights, every mesh level, per-material textures). Bundled textures are kept at most 2048
 * pixels a side; large opaque ones are stored as JPEG to keep the JAR small. Falls back to the
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
        options.maxTexture = 2048;
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

    /** Raw images become PNG (or JPEG when opaque); a large opaque PNG becomes JPEG if that is smaller. */
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
        } else {
            if (b.images[i].length < 1_500_000) return;
            image = ImageIO.read(new ByteArrayInputStream(b.images[i]));
            if (image == null) return;
        }
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        boolean opaque = true;
        for (int p : argb) if ((p >>> 24) != 0xFF) {
            opaque = false;
            break;
        }
        byte[] best = b.imageRaw[i] ? null : b.images[i];
        if (opaque) {
            BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            rgb.setRGB(0, 0, w, h, argb, 0, w);
            byte[] jpeg = jpeg(rgb, 0.92f);
            if (best == null || jpeg.length < best.length) best = jpeg;
        } else if (best == null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            best = out.toByteArray();
        }
        b.images[i] = best;
        b.imageRaw[i] = false;
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
