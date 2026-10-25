package dev.maro.gui.render;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.TranslatableTextContent;
import java.util.Map;
import java.util.WeakHashMap;
/** Restyles the actual native buttons; callbacks, account restrictions, focus and narration survive. */
public final class MaroTitleStyle {
    private record Style(String hint,boolean footer){}
    private static final Map<ClickableWidget,Style> STYLES=new WeakHashMap<>();
    private static String activePage="";
    private static final dev.maro.util.Animation brandHover=new dev.maro.util.Animation(13,0);
    private MaroTitleStyle(){}
    public static void activePage(String key){activePage=key;}
    private static float titleScale(int w,int h){return Math.max(.01f,Math.min(w/960f,h/540f)*.94f);}
    public static float canvasScale(int w,int h){return titleScale(w,h);}
    public static String key(ClickableWidget w){return w.getMessage().getContent() instanceof TranslatableTextContent t?t.getKey():"";}
    private static boolean browsing(){return net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof dev.maro.gui.MaroHomeScreen home&&home.page()!=null;}
    public static void layout(Screen screen){
        if(dev.maro.render.capture.CapturePass.active())return;
        float s=titleScale(screen.width,screen.height),ox=(screen.width-960*s)/2,oy=(screen.height-540*s)/2;int extra=0;
        for(var child:screen.children())if(child instanceof PressableWidget b){
            String hint="";float x=48,y,w=240,h=42;boolean footer=false;
            switch(key(b)){
                case "menu.singleplayer","menu.playdemo"->{y=206;hint="Saved worlds and new adventures";}
                case "menu.multiplayer"->{y=256;hint="Your servers and direct connect";}
                case "maro.menu.accounts"->{y=306;hint="Manage your Minecraft profiles";}
                case "menu.options"->{y=356;hint="Video, sound and controls";}
                case "menu.online"->{y=406;h=30;}
                case "menu.quit"->{x=858;y=24;w=54;h=28;}
                case "title.credits"->{x=550;y=512;w=365;h=12;footer=true;}
                case "options.language"->{y=476;w=112;h=26;}
                case "options.accessibility"->{x=168;y=476;w=120;h=26;}
                default->{x=328+extra%3*190;y=480+extra/3*30;w=180;h=25;extra++;}
            }
            if(!(screen instanceof dev.maro.gui.MaroHomeScreen home&&home.page()!=null)){
                hint="";
                switch(key(b)){
                    case "menu.singleplayer","menu.playdemo"->{x=352;y=244;w=256;h=30;}
                    case "menu.multiplayer"->{x=352;y=280;w=256;h=30;}
                    case "menu.online"->{x=352;y=316;w=256;h=30;}
                    case "menu.options"->{x=352;y=362;w=124;h=28;}
                    case "menu.quit"->{x=484;y=362;w=124;h=28;}
                    case "options.language"->{x=316;y=362;w=28;h=28;}
                    case "options.accessibility"->{x=616;y=362;w=28;h=28;}
                    case "maro.menu.accounts"->{x=840;y=24;w=72;h=25;}
                    case "title.credits"->{x=550;y=520;w=365;h=12;}
                    default->{x=352;y=402+(extra-1)*30;w=256;h=25;}
                }
            }
            b.setDimensionsAndPosition(Math.max(1,Math.round(w*s)),Math.max(1,Math.round(h*s)),Math.round(ox+x*s),Math.round(oy+y*s));STYLES.put(b,new Style(hint,footer));
        }
    }
    public static void layoutLoading(Screen screen){
        if(dev.maro.render.capture.CapturePass.active())return;
        float s=BootScene.scale(screen.width,screen.height),ox=(screen.width-960*s)/2,oy=(screen.height-540*s)/2;int row=0;
        for(var child:screen.children())if(child instanceof PressableWidget b){
            b.setDimensionsAndPosition(Math.max(1,Math.round(180*s)),Math.max(1,Math.round(30*s)),Math.round(ox+390*s),Math.round(oy+(398+row++*34)*s));STYLES.put(b,new Style("",false));
        }
    }
    public static void render(DrawContext ctx,int w,int h){
        if(dev.maro.render.capture.CapturePass.active())return;
        float old=Render2D.getAlpha();Render2D.setAlpha(1);var m=ctx.getMatrices();m.pushMatrix();
        try{
            MenuLandscape.draw(ctx,w,h);ctx.createNewRootLayer();float s=titleScale(w,h);m.translate((w-960*s)/2,(h-540*s)/2);m.scale(s,s);
            if(browsing()){
                MenuBrand.draw(ctx,48,110,34,0);MenuTypography.label(ctx,"MARO.GG",94,120,18,0xFFEDF1F6,true);
                MenuTypography.label(ctx,"Minecraft, with your settings.",49,159,12,0xFF7C8798,false);
                Render2D.rect(ctx,48,190,240,.7f,0xFF282E39);
            }else{
                var client=net.minecraft.client.MinecraftClient.getInstance();
                float px=(float)(client.mouse.getX()*w/client.getWindow().getWidth()),py=(float)(client.mouse.getY()*h/client.getWindow().getHeight());
                float canvasX=(px-(w-960*s)/2)/s,canvasY=(py-(h-540*s)/2)/s;
                float hover=brandHover.update(canvasX>=440&&canvasX<=520&&canvasY>=90&&canvasY<=210?1:0);
                MenuBrand.draw(ctx,451,98,58,hover);ctx.createNewRootLayer();
                String brand="MARO.GG";float font=18;
                MenuTypography.label(ctx,brand,480-MenuTypography.labelWidth(brand,font,true)/2,177,font,0xFFF2F6FD,true);

            }
            String user=dev.maro.module.impl.misc.NameProtect.mask(net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername());
            MenuTypography.label(ctx,user,49,520,11,0xFF8A95A5,false);
            MenuTypography.label(ctx,"Minecraft 1.21.11",48,33,10,0xFF636F80,false);
        }finally{m.popMatrix();Render2D.setAlpha(old);}
    }
    public static boolean button(DrawContext ctx,ClickableWidget b){
        if(dev.maro.render.capture.CapturePass.active())return false;
        Style style=STYLES.get(b);if(style==null)return false;
        var c=net.minecraft.client.MinecraftClient.getInstance();float s=c.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen?titleScale(c.getWindow().getScaledWidth(),c.getWindow().getScaledHeight()):BootScene.scale(c.getWindow().getScaledWidth(),c.getWindow().getScaledHeight());
        float x=b.getX(),y=b.getY(),w=b.getWidth(),h=b.getHeight();boolean selected=!activePage.isEmpty()?key(b).equals(activePage):b.isSelected();
        String label=switch(key(b)){case "menu.quit"->"Quit";case "options.accessibility"->"Accessibility";case "options.language"->"Language";default->b.getMessage().getString().replace("...","").replace("…","");};
        if(style.footer){MenuTypography.label(ctx,label,x,y+(h-7*s)/2,7*s,0xFF576273,false);return true;}
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen&&!browsing())return homeButton(ctx,b,label,s);
        boolean hover=b.isSelected();int fill=!b.active?0xFF11151D:selected?0xFF202D40:hover?0xFF1C222D:0xFF141A23;
        if(c.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen)fill=dev.maro.util.ColorUtil.withAlpha(fill,dev.maro.config.ClientSettings.menuSurfaceAlpha());
        Render2D.roundRect(ctx,x,y,w,h,6*s,fill);if(selected&&b.active)Render2D.roundOutline(ctx,x,y,w,h,6*s,.8f*s,0xFF4B6588);
        int color=b.active?0xFFE4E9F0:0xFF566171;
        if(style.hint.isEmpty()){
            float fs=Math.min(12*s,(w-14*s)/Math.max(1,MenuTypography.labelWidth(label,1,false)));MenuTypography.label(ctx,label,x+(w-MenuTypography.labelWidth(label,fs,false))/2,y+(h-fs*.73f)/2,fs,color,false);
        }else{
            MenuTypography.label(ctx,label,x+13*s,y+8*s,15*s,color,true);MenuTypography.label(ctx,style.hint,x+13*s,y+27*s,9*s,b.active?0xFF7F8A9A:0xFF4D5868,false);
            if(selected){Render2D.roundRect(ctx,x+3*s,y+11*s,2*s,h-22*s,s,0xFF81ADDF);}
        }
        return true;
    }
    private static boolean homeButton(DrawContext ctx,ClickableWidget b,String label,float s){
        float x=b.getX(),y=b.getY(),w=b.getWidth(),h=b.getHeight(),radius=h/2;
        boolean over=b.active&&b.isSelected();float opacity=dev.maro.config.ClientSettings.menuSurfaceAlpha()/255f;
        int top=!b.active?0xAD26303F:over?0xED4086CE:0xC5365275;
        int bottom=!b.active?0xC91B2432:over?0xF1235894:0xDA1A304D;
        if(over)Render2D.shadow(ctx,x,y,w,h,radius,8*s,0x283391FC);
        Render2D.roundGradientV(ctx,x,y,w,h,radius,dev.maro.util.ColorUtil.mulAlpha(top,opacity),dev.maro.util.ColorUtil.mulAlpha(bottom,opacity));
        Render2D.roundOutline(ctx,x,y,w,h,radius,.8f*s,over?0xB9B3D7FF:0x687F9FC6);
        Render2D.line(ctx,x+radius,y+1.2f*s,x+w-radius,y+1.2f*s,.55f*s,over?0x70D8E9FF:0x327BA6D3);
        int ink=b.active?0xFFF3F6FC:0xFF8793A5;ctx.createNewRootLayer();
        String key=key(b);
        if(key.equals("options.language")){
            float cx=x+w/2,cy=y+h/2,r=7*s;
            Render2D.ring(ctx,cx,cy,r,1.05f*s,ink);
            Render2D.line(ctx,cx-r,cy,cx+r,cy,.9f*s,ink);
            Render2D.arc(ctx,cx+3*s,cy,r,.9f*s,105,150,ink,ink);
            Render2D.arc(ctx,cx-3*s,cy,r,.9f*s,-75,150,ink,ink);
        }else if(key.equals("options.accessibility")){
            float cx=x+w/2,cy=y+h/2;
            Render2D.circle(ctx,cx,cy-6*s,2*s,ink);
            Render2D.line(ctx,cx-6*s,cy-2*s,cx+6*s,cy-2*s,1.3f*s,ink);
            Render2D.line(ctx,cx,cy-2*s,cx,cy+2*s,1.3f*s,ink);
            Render2D.line(ctx,cx,cy+2*s,cx-4*s,cy+7*s,1.3f*s,ink);
            Render2D.line(ctx,cx,cy+2*s,cx+4*s,cy+7*s,1.3f*s,ink);
        }else{
            float size=Math.min(12*s,(w-18*s)/Math.max(1,MenuTypography.labelWidth(label,1,false)));
            MenuTypography.label(ctx,label,x+(w-MenuTypography.labelWidth(label,size,false))/2,y+(h-size*.73f)/2,size,ink,false);
        }
        return true;
    }

}
