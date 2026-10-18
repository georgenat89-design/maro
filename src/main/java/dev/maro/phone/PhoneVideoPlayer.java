package dev.maro.phone;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.*;

/** Only the decoder threads touch video files; the render thread takes the newest small RGB frame. */
public final class PhoneVideoPlayer implements AutoCloseable {
    public record Frame(long serial,int width,int height,byte[] rgba) { }
    private final ExecutorService decoder=executor("Maro clip decoder"),audio=executor("Maro clip audio");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final AtomicReference<Frame> frame=new AtomicReference<>();
    private volatile long generation,base,pausedAt,pausedTotal;
    private volatile boolean paused,muted=true;
    private volatile String status="Choose a clip";
    private volatile SourceDataLine line;
    private Future<?> job,audioJob;
    private long serial;
    private volatile int outputWidth=360,outputHeight=640;
    public void outputSize(int width,int height){outputWidth=Math.clamp(width,2,960);outputHeight=Math.clamp(height,2,1440);}
    private static ExecutorService executor(String name){return Executors.newSingleThreadExecutor(r->{var t=new Thread(r,name);t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY+1);return t;});}
    public Frame frame(){return frame.get();}
    public String status(){return status;}
    public boolean paused(){return paused;}
    public boolean muted(){return muted;}
    public void mute(){muted=!muted;}
    public synchronized void pause(){
        if(!paused){pausedAt=System.nanoTime();paused=true;}else{pausedTotal+=System.nanoTime()-pausedAt;paused=false;}
    }
    public void play(URI source,Path cache){
        stop();long run=generation;status="Loading clip…";paused=false;base=0;pausedTotal=0;
        job=decoder.submit(()->{
            try{
                Path file=resolve(source,cache);
                if(run!=generation)return;
                try(var channel=NIOUtils.readableChannel(file.toFile())){
                    var demux=MP4Demuxer.createMP4Demuxer(channel);
                    var video=demux.getVideoTrack();
                    if(video==null || video.getMeta().getVideoCodecMeta()==null)throw new IOException("No supported video track");
                    var size=video.getMeta().getVideoCodecMeta().getSize();
                    if(size.getWidth()<1 || size.getHeight()<1 || (long)size.getWidth()*size.getHeight()>1280L*720)
                        throw new IOException("Use MP4 clips at 720p or smaller");
                    channel.setPosition(0);FrameGrab grab=FrameGrab.createFrameGrab(channel);
                    var converter=new PhoneFrameConverter();
                    BufferedImage small=null;Graphics2D graphics=null;int[] pixels=null;double published=-1;
                    try{
                        int loops=0;
                        while(alive(run)){
                            var next=grab.getNativeFrameWithMetadata();
                            if(next==null){grab.seekToFramePrecise(0);base=0;pausedTotal=0;published=-1;if(++loops>10000)break;continue;}
                            if(base==0)synchronized(this){if(!alive(run))break;base=System.nanoTime();if(audioJob==null)audioJob=audio.submit(()->audio(file,run));}
                            double pts=next.getTimestamp();
                            while(alive(run) && (paused || pts>playhead()))LockSupport.parkNanos(4_000_000);
                            if(!alive(run))break;
                            if(playhead()-pts>.18)continue;
                            if(published>=0&&pts-published<.032)continue;
                            BufferedImage rgb=converter.convert(next.getPicture());
                            double fit=Math.min(1,Math.min((double)outputWidth/rgb.getWidth(),(double)outputHeight/rgb.getHeight()));
                            int w=Math.max(1,(int)(rgb.getWidth()*fit)),h=Math.max(1,(int)(rgb.getHeight()*fit));
                            if(small==null||small.getWidth()!=w||small.getHeight()!=h){
                                if(graphics!=null)graphics.dispose();small=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);pixels=new int[w*h];
                                graphics=small.createGraphics();graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            }
                            graphics.drawImage(rgb,0,0,w,h,null);small.getRGB(0,0,w,h,pixels,0,w);published=pts;
                            if(alive(run)){frame.set(new Frame(++serial,w,h,dev.maro.runtime.renderer.NativeImageUpload.rgba(pixels)));status="";}
                        }
                    }finally{if(graphics!=null)graphics.dispose();closeCodec(grab);}
                }
            }catch(Exception e){if(run==generation && !Thread.currentThread().isInterrupted()){
                status=e instanceof IOException && e.getMessage()!=null?e.getMessage():"Unsupported clip · Use H.264 MP4";
                dev.maro.Maro.LOGGER.warn("Local video decoding stopped",e);
            }}
        });
    }
    private boolean alive(long run){return generation==run && !Thread.currentThread().isInterrupted();}
    private double playhead(){return base==0?0:Math.max(0,((paused?pausedAt:System.nanoTime())-base-pausedTotal)/1e9);}
    private void audio(Path file,long run){
        try(var channel=NIOUtils.readableChannel(file.toFile())){
            var demux=MP4Demuxer.createMP4Demuxer(channel);
            if(demux.getAudioTracks().isEmpty())return;
            var track=(org.jcodec.common.SeekableDemuxerTrack)demux.getAudioTracks().getFirst();var meta=track.getMeta();
            if(meta.getCodec()!=org.jcodec.common.Codec.AAC)return;
            var aac=new org.jcodec.codecs.aac.AACDecoder(meta.getCodecPrivate());
            ByteBuffer decodeBuffer=ByteBuffer.allocate(65536);byte[] bytes=new byte[8192];
            SourceDataLine output=null;
            try{
                while(alive(run)){
                    var packet=track.nextFrame();
                    if(packet==null){track.gotoFrame(0);while(alive(run)&&playhead()>.25)LockSupport.parkNanos(5_000_000);continue;}
                    while(alive(run)&&(paused || packet.getPtsD()>playhead()+.06))LockSupport.parkNanos(4_000_000);
                    if(!alive(run))break;
                    decodeBuffer.clear();var decoded=aac.decodeFrame(packet.getData(),decodeBuffer);var f=decoded.getFormat();
                    if(output==null){
                        output=AudioSystem.getSourceDataLine(new AudioFormat(f.getSampleRate(),f.getSampleSizeInBits(),f.getChannels(),true,f.isBigEndian()));
                        output.open();if(!alive(run)){output.close();break;}line=output;output.start();
                    }
                    ByteBuffer pcm=decoded.getData();int length=pcm.remaining();if(bytes.length<length)bytes=new byte[length];pcm.get(bytes,0,length);if(muted)java.util.Arrays.fill(bytes,0,length,(byte)0);
                    output.write(bytes,0,length);
                }
            }finally{if(output!=null){output.stop();output.flush();output.close();if(line==output)line=null;}}
        }catch(Exception ignored){/* Video remains usable if a clip has no supported audio or an output device is absent. */}
    }
    public Path resolve(URI source,Path cache)throws Exception{
        if("file".equals(source.getScheme())){
            Path path=Path.of(source);if(Files.size(path)>64L*1024*1024)throw new IOException("Use clips smaller than 64 MB");return path;
        }
        Files.createDirectories(cache);
        String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Path file=cache.resolve(key+".mp4");if(Files.isRegularFile(file))return file;
        Path tmp=cache.resolve(key+".part");
        try{
            var response=http.send(HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var in=response.body()){
                if(response.statusCode()!=200)throw new IOException("Video link returned HTTP "+response.statusCode());
                long length=response.headers().firstValueAsLong("Content-Length").orElse(0);if(length>64L*1024*1024)throw new IOException("Use clips smaller than 64 MB");
                try(var out=Files.newOutputStream(tmp)){byte[] bytes=new byte[32768];long total=0;int n;long deadline=System.nanoTime()+30_000_000_000L;
                    while((n=in.read(bytes))>=0){if(Thread.currentThread().isInterrupted())throw new InterruptedException();total+=n;if(total>64L*1024*1024 || System.nanoTime()>deadline)throw new IOException("Clip download exceeded its limit");out.write(bytes,0,n);}
                }
            }
            Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
            // Bound downloaded clips, keeping the current file while removing the oldest cache entries.
            try(var entries=Files.list(cache)){
                var old=entries.filter(p->p.toString().endsWith(".mp4")&&!p.equals(file)&&!p.getFileName().toString().startsWith("demo-")).sorted(java.util.Comparator.comparingLong(p->p.toFile().lastModified())).toList();
                long total=Files.size(file);for(int i=old.size()-1;i>=0;i--){total+=Files.size(old.get(i));if(total>256L*1024*1024)Files.deleteIfExists(old.get(i));}
            }
            return file;
        }finally{Files.deleteIfExists(tmp);}
    }
    /** JCodec 0.2.5 exposes no decoder close API; retire its private slice pool on every clip switch. */
    private static void closeCodec(FrameGrab grab){
        try{var adapter=grab.getDecoder();var d=adapter.getClass().getDeclaredField("decoder");d.setAccessible(true);Object codec=d.get(adapter);
            if(codec!=null){var p=codec.getClass().getDeclaredField("tp");p.setAccessible(true);if(p.get(codec) instanceof ExecutorService pool)pool.shutdownNow();}
        }catch(ReflectiveOperationException ignored){/* Non-AVC adapters have no slice pool. */}
    }
    public synchronized void stop(){
        ++generation;if(job!=null){job.cancel(true);job=null;}if(audioJob!=null){audioJob.cancel(true);audioJob=null;}
        var output=line;line=null;if(output!=null){output.stop();output.flush();output.close();}frame.set(null);
    }
    @Override public void close(){stop();decoder.shutdownNow();audio.shutdownNow();http.shutdownNow();}
}
