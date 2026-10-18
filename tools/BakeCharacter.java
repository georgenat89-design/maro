import dev.maro.render.character.gltf.CharacterBake;
import dev.maro.render.character.gltf.ImageDecoder;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;

/** Offline asset conversion. Run with the character glTF sources, Gson and Fabric annotations. */
public final class BakeCharacter {
    public static void main(String[] args)throws Exception{
        var options=new CharacterBake.Options();options.flat=.16f;
        byte[] input=Files.readAllBytes(Path.of(args[0]));
        System.out.print(CharacterBake.describe(input,options));
        var baked=CharacterBake.bake(input,options,data->{
            var image=ImageIO.read(new ByteArrayInputStream(data));
            if(image==null)throw new IOException("Unsupported texture");
            int w=image.getWidth(),h=image.getHeight();return new ImageDecoder.Picture(w,h,image.getRGB(0,0,w,h,null,0,w));
        });
        Files.write(Path.of(args[1]),baked.data());System.out.println("Baked "+baked.data().length+" bytes; limbs: "+baked.limbs());
    }
}
