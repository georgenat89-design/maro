package dev.maro.phone;

import java.awt.image.BufferedImage;
import java.util.Objects;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.ColorUtil;
import org.jcodec.scale.RgbToBgr;
import org.jcodec.scale.Transform;

/** Reuses conversion buffers while respecting decoder color space and padded-frame cropping. */
public final class PhoneFrameConverter {
    private final RgbToBgr swizzle=new RgbToBgr();
    private Picture bgr;
    private ColorSpace sourceColor;
    private Transform transform;
    private BufferedImage image;

    public BufferedImage convert(Picture source) {
        Picture converted=source;
        if(source.getColor()!=ColorSpace.BGR) {
            if(bgr==null||bgr.getWidth()!=source.getWidth()||bgr.getHeight()!=source.getHeight()
                    ||!Objects.equals(bgr.getCrop(),source.getCrop()))
                bgr=Picture.createCropped(source.getWidth(),source.getHeight(),ColorSpace.BGR,source.getCrop());
            if(source.getColor()==ColorSpace.RGB)swizzle.transform(source,bgr);
            else {
                if(sourceColor!=source.getColor()) {
                    sourceColor=source.getColor();transform=ColorUtil.getTransform(sourceColor,ColorSpace.RGB);
                }
                if(transform==null)throw new IllegalArgumentException("Unsupported video color space");
                transform.transform(source,bgr);swizzle.transform(bgr,bgr);
            }
            converted=bgr;
        }
        int width=converted.getCroppedWidth(),height=converted.getCroppedHeight();
        if(image==null||image.getWidth()!=width||image.getHeight()!=height)
            image=new BufferedImage(width,height,BufferedImage.TYPE_3BYTE_BGR);
        if(converted.getCrop()==null)AWTUtil.toBufferedImage(converted,image);
        else {
            var crop=converted.getCrop();byte[] input=converted.getPlaneData(0);
            byte[] output=((java.awt.image.DataBufferByte)image.getRaster().getDataBuffer()).getData();
            int stride=converted.getWidth()*3,rowBytes=width*3,start=crop.getY()*stride+crop.getX()*3;
            for(int y=0;y<height;y++)for(int x=0;x<rowBytes;x++)output[y*rowBytes+x]=(byte)(input[start+y*stride+x]+128);
        }
        return image;
    }
}
