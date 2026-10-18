package dev.maro.gui.spotify;

import dev.maro.phone.PhoneVideoSizing;

/** Input gesture behavior and the bounded physical-pixel budget are independent of render FPS. */
public final class PhoneVideoQualityChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void decoderColors(){
        var converter=new dev.maro.phone.PhoneFrameConverter();
        for(var color:new org.jcodec.common.model.ColorSpace[]{org.jcodec.common.model.ColorSpace.YUV420,org.jcodec.common.model.ColorSpace.YUV420J,org.jcodec.common.model.ColorSpace.RGB,org.jcodec.common.model.ColorSpace.BGR})
            for(boolean cropped:new boolean[]{false,true}) {
                java.awt.image.BufferedImage previous=null;
                for(int frame=0;frame<6;frame++) {
                    var picture=org.jcodec.common.model.Picture.createCropped(32,32,color,cropped?new org.jcodec.common.model.Rect(2,2,26,24):null);
                    for(int plane=0;plane<picture.getData().length;plane++){var bytes=picture.getData()[plane];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)((i*17+frame*29+plane*53)%256-128);}
                    var reference=org.jcodec.scale.AWTUtil.toBufferedImage(org.jcodec.common.model.Picture.createPicture(32,32,picture.getData(),color));
                    var expected=cropped?reference.getSubimage(2,2,26,24):reference;var actual=converter.convert(picture);
                    require(expected.getWidth()==actual.getWidth()&&expected.getHeight()==actual.getHeight(),"Coded padding leaked into the video");
                    int w=expected.getWidth(),h=expected.getHeight();require(java.util.Arrays.equals(expected.getRGB(0,0,w,h,null,0,w),actual.getRGB(0,0,w,h,null,0,w)),"Reused decoder color/crop conversion differs: "+color+" frame="+frame);
                    if(previous!=null)require(actual==previous,"Frame conversion reallocates its output on every frame");previous=actual;
                }
            }
        System.out.println("[phone-frame-converter] PASS: six successive frames in YUV420/YUV420J/RGB/BGR with and without coded padding match JCodec's reference pixels and reuse output storage");
    }
    public static void run(){
        decoderColors();
        var wheel=new FeedWheel();long now=1_000_000_000L;
        for(int i=0;i<4;i++)require(wheel.take(-.2,now+i*1_000_000)==0,"Fractional wheel stepped too early");
        require(wheel.take(-.2,now+4_000_000)==1,"Trackpad fractions did not accumulate");
        for(int i=5;i<15;i++)require(wheel.take(-1,now+i*1_000_000)==1,"A fast wheel notch was discarded");
        require(wheel.take(1,now+100_000_000)==-1,"Direction reversal was delayed");
        require(wheel.take(1,now+500_000_000)==-1,"A fresh notch did not advance");
        wheel.reset();require(wheel.take(-.4,now)==0&&wheel.take(-.6,now+400_000_000)==0,"Stale trackpad fractions carried into a new gesture");
        require(wheel.take(Double.NaN,now)==0&&wheel.take(Double.POSITIVE_INFINITY,now)==0,"Invalid wheel input advanced the feed");
        wheel.reset();require(wheel.take(-100,now)==1&&wheel.take(-100,now+1)==1,"One callback did not map to one bounded step");
        for(float width:new float[]{192,400})for(float height:new float[]{210,338})for(double scale:new double[]{.5,1,1.5,2,4,8}){
            var size=PhoneVideoSizing.viewport(width,height,scale);
            require(size.width()>0&&size.width()<=960&&size.height()>0&&size.height()<=1440&&(long)size.width()*size.height()<=921600,"Video size exceeded its budget");
            require(Math.abs((double)size.height()/size.width()-height/width)<.012,"Adaptive size stretched the viewport");
        }
        var small=PhoneVideoSizing.viewport(192,338,1.2);var large=PhoneVideoSizing.viewport(192,338,3.6);
        for(float w:new float[]{192,400})for(float h:new float[]{200,338})for(double scale:new double[]{1,2,4}) {
            var feed=PhoneVideoSizing.feedViewport(w,h,scale);
            require(Math.abs((double)feed.height()/feed.width()-16d/9)<.005,"Tablet browser viewport cropped portrait content");
            require((long)feed.width()*feed.height()<=921600,"Feed capture exceeded the pixel budget");
        }
        require(small.width()<480&&large.width()>480,"Adaptive surface did not reduce small-device work and increase large-device detail");
        System.out.println("[phone-video-quality] PASS: fractional gestures, ten fast notches retained, immediate reverse, stale/invalid input, 48 bounded adaptive viewport cases; small="+small+" large="+large);
    }
}
