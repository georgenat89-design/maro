import org.jcodec.api.awt.AWTSequenceEncoder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Two original, silent demo loops. No external footage or account is needed for the initial feed. */
public final class GeneratePhoneClips {
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();
        for(int mode=0;mode<2;mode++){
            var encoder=AWTSequenceEncoder.createSequenceEncoder(new File(dir,"demo-"+(mode==0?"neon":"orbit")+".mp4"),24);
            for(int frame=0;frame<96;frame++){
                double t=frame*Math.PI*2/96;
                BufferedImage image=new BufferedImage(160,288,BufferedImage.TYPE_3BYTE_BGR);Graphics2D g=image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0,0,new Color(8,12,30),160,288,new Color(mode==0?35:10,8,mode==0?48:38)));g.fillRect(0,0,160,288);
                if(mode==0){
                    for(int i=0;i<24;i++){double z=((i/24.0+frame/96.0)%1);float y=(float)(110+z*z*178);g.setColor(new Color(51,231,239,(int)(50+z*150)));g.setStroke(new BasicStroke((float)(.5+z*1.2)));g.drawLine(0,(int)y,160,(int)y);}
                    for(int i=-8;i<=8;i++){g.setColor(new Color(201,75,255,150));g.drawLine(80,112,80+i*48,288);}
                    g.setPaint(new GradientPaint(0,54,new Color(255,130,95),0,120,new Color(221,48,183)));g.fillOval(44,55,72,72);
                    g.setColor(new Color(12,11,33));for(int y=98;y<128;y+=7)g.fillRect(42,y,76,3);
                }else{
                    for(int i=0;i<65;i++){int x=(i*73+19)%160,y=(i*31+17)%288;g.setColor(new Color(130,183,220,70+(i*13)%150));g.fillOval(x,y,i%3+1,i%3+1);}
                    g.setColor(new Color(78,60,133));g.fillOval(29,91,102,102);
                    g.setPaint(new GradientPaint(45,105,new Color(75,251,215),120,175,new Color(58,73,205)));g.fillOval(40,102,80,80);
                    for(int i=0;i<3;i++){double a=t+i*Math.PI*2/3;int x=(int)(80+63*Math.cos(a)),y=(int)(142+33*Math.sin(a));g.setColor(new Color(255,194,111));g.fillOval(x-6,y-6,12,12);}
                    g.setStroke(new BasicStroke(2));g.setColor(new Color(189,162,255,160));g.drawOval(12,108,136,68);
                }
                g.setFont(new Font("SansSerif",Font.BOLD,16));g.setColor(new Color(240,245,255));String title=mode==0?"NEON DRIFT":"ORBIT";g.drawString(title,(160-g.getFontMetrics().stringWidth(title))/2,237);
                g.setFont(new Font("SansSerif",Font.PLAIN,9));g.setColor(new Color(157,173,201));g.drawString("MARO ORIGINAL",41,255);g.dispose();encoder.encodeImage(image);
            }
            encoder.finish();
        }
    }
}
