package dev.maro.gui.hud;

import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.GuiBackground;
import dev.maro.gui.render.MaroWordmark;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Same-session background workspace. Only this screen and its texture preview are rendered. */
public final class GuiBackgroundScreen extends Screen {
    private static final float W = 760, H = 440;
    @FunctionalInterface private interface Action { void run(double x, double y, int button); }
    @FunctionalInterface private interface Drag { void move(double x, double y); }
    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }
    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private float scale = 1, ox, oy, mx, my;
    private boolean imageTab;
    private int focus = -1;
    private ColorSetting editingColor;
    private Drag drag;
    private String pickerError = "";

    public GuiBackgroundScreen(Screen parent) { super(Text.literal("GUI Background Studio")); this.parent = parent; }
    public double[] point(float x, float y) { return new double[]{ox + x * scale, oy + y * scale}; }
    @Override protected void init() { GuiBackground.image().ensureLoaded(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) { }
    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Theme.update(); Render2D.setAlpha(1);
        GuiBackground.draw(ctx, 0, 0, width, height);
        scale = Math.min(width * .94f / W, height * .92f / H);
        ox = (width - W * scale) / 2; oy = (height - H * scale) / 2;
        mx = (mouseX - ox) / scale; my = (mouseY - oy) / scale;
        hits.clear();
        ctx.getMatrices().pushMatrix(); ctx.getMatrices().translate(ox, oy); ctx.getMatrices().scale(scale, scale);
        Fonts.beginRaw(); Fonts.setMeasureScale(scale);
        try {
            Render2D.shadow(ctx, 0, 5, W, H, 12, 20, 0x80000000);
            Render2D.roundRect(ctx, 0, 0, W, H, 12, 0xF20A101B);
            Render2D.roundOutline(ctx, 0, 0, W, H, 12, .8f, 0x66475C7D);
            Fonts.drawV(ctx, "Background Studio", 20, 25, Theme.TEXT, true, 1.15f);
            Fonts.drawV(ctx, "Your backdrop, with a live preview.", 20, 47, Theme.TEXT_MUTED, false, .74f);
            button(ctx, "Done", W - 82, 16, 62, 26, true, this::close);
            button(ctx, "Style", 20, 65, 136, 26, !imageTab, () -> { imageTab = false; focus = -1; });
            button(ctx, "PNG Image", 164, 65, 136, 26, imageTab, () -> { imageTab = true; focus = -1; });
            Render2D.roundRect(ctx, 20, 101, 280, 282, 8, 0xA7111C2D);
            if (imageTab) imageControls(ctx); else styleControls(ctx);
            preview(ctx);
            button(ctx, "Reset backdrop", 20, 397, 136, 25, false, () -> { GuiBackground.reset(); pickerError = ""; });
            Fonts.drawV(ctx, "Saved automatically", 170, 410, Theme.TEXT_MUTED, false, .67f);
            if (focus >= 0 && focus < hits.size() && editingColor == null) {
                Hit hit = hits.get(focus); Render2D.roundOutline(ctx, hit.x(),hit.y(),hit.w(),hit.h(),5,1.2f,Theme.accent());
            }
            if (editingColor != null) colorPicker(ctx);
        } finally { Fonts.setMeasureScale(1); Fonts.endRaw(); ctx.getMatrices().popMatrix(); }
    }
    private void styleControls(DrawContext ctx) {
        mode(ctx, ClientSettings.guiBackgroundMode, 114);
        if (!GuiBackground.isDefault()) {
            number(ctx, ClientSettings.guiBackgroundDim, 151);
            if (!ClientSettings.guiBackgroundMode.is("Transparent")) number(ctx, ClientSettings.guiBackgroundOpacity, 193);
        }
        if (ClientSettings.guiBackgroundMode.is("Solid")) color(ctx,ClientSettings.guiBackgroundColor,241);
        else if (ClientSettings.guiBackgroundMode.is("Gradient")) {
            mode(ctx,ClientSettings.guiBackgroundPreset,239);
            number(ctx,ClientSettings.guiBackgroundDirection,278);
            color(ctx,ClientSettings.guiBackgroundColor,326);color(ctx,ClientSettings.guiBackgroundColor2,351);
        } else if (ClientSettings.guiBackgroundMode.is("PNG Image")) {
            Fonts.drawV(ctx,"The PNG's alpha blends with gameplay.",34,254,Theme.TEXT_DIM,false,.7f);
            button(ctx,"Image layout & tint →",34,273,252,27,true,()->imageTab=true);
        } else if (GuiBackground.isDefault()) {
            Fonts.drawV(ctx,"Maro's original backdrop.",34,173,Theme.TEXT_DIM,false,.77f);
            Fonts.drawV(ctx,"Existing dim and frosted-glass settings",34,195,Theme.TEXT_MUTED,false,.67f);
            Fonts.drawV(ctx,"remain in Settings → Interface.",34,212,Theme.TEXT_MUTED,false,.67f);
        } else {
            Fonts.drawV(ctx,"Gameplay shows through behind the menu.",34,254,Theme.TEXT_DIM,false,.68f);
            Fonts.drawV(ctx,"Use Gameplay Dimming to set the contrast.",34,276,Theme.TEXT_MUTED,false,.66f);
        }
    }
    private void imageControls(DrawContext ctx) {
        mode(ctx,ClientSettings.guiBackgroundFit,114);
        if (!ClientSettings.guiBackgroundFit.is("Stretch")) {
            number(ctx,ClientSettings.guiBackgroundScale,155);
            number(ctx,ClientSettings.guiBackgroundAlignX,201);
            number(ctx,ClientSettings.guiBackgroundAlignY,247);
        } else {
            Fonts.drawV(ctx,"Stretch fills every edge.",34,175,Theme.TEXT_DIM,false,.76f);
            Fonts.drawV(ctx,"Choose Fit or Fill to preserve proportions.",34,198,Theme.TEXT_MUTED,false,.67f);
        }
        number(ctx,ClientSettings.guiBackgroundOpacity,293);
        color(ctx,ClientSettings.guiBackgroundTint,344);
    }
    private void preview(DrawContext ctx) {
        Fonts.drawV(ctx,"LIVE PREVIEW",322,81,Theme.TEXT_MUTED,true,.65f);
        float x = 320,y = 103,w = 420,h = 236.25f;
        Render2D.roundRect(ctx,x-1,y-1,w+2,h+2,7,0xFF33445E);
        Render2D.clip(ctx,(int)x,(int)y,(int)(x+w),(int)(y+h));
        try {
            // A lightweight illustration makes transparency visible without rendering another world.
            Render2D.rectGradient(ctx,x,y,w,h,0xFF618DAA,0xFF618DAA,0xFFBCD5D5,0xFFBCD5D5);
            Render2D.rect(ctx,x,y+h*.62f,w,h*.38f,0xFF536C4A);
            Render2D.rect(ctx,x+w*.64f,y+h*.4f,w*.11f,h*.24f,0xFF3F5843);
            Render2D.rect(ctx,x+w*.685f,y+h*.56f,w*.025f,h*.15f,0xFF695447);
            GuiBackground.draw(ctx,x,y,w,h);
            float panelX=x+w*.17f,panelY=y+h*.19f,panelW=w*.65f,panelH=h*.63f;
            Render2D.roundRect(ctx,panelX,panelY,panelW,panelH,7,0xEB0B1321);
            Render2D.rect(ctx,panelX+69,panelY,1,panelH,0x50465B76);
            MaroWordmark.draw(ctx,panelX+12,panelY+15,9,Theme.accent(),MaroWordmark.Style.THEMED,false);
            for(int i=0;i<4;i++)Render2D.roundRect(ctx,panelX+10,panelY+39+i*18,49,11,3,i==1?Theme.accent(65):0x152D456A);
            Fonts.drawV(ctx,"Your client",panelX+82,panelY+20,Theme.TEXT,true,.84f);
            for(int i=0;i<3;i++){
                Render2D.roundRect(ctx,panelX+81,panelY+38+i*30,panelW-93,24,4,0xDC162239);
                Render2D.roundRect(ctx,panelX+91,panelY+46+i*30,76,3,1,0xB3C4D2E6);
                Render2D.roundRect(ctx,panelX+91,panelY+53+i*30,108,2,1,0x637C92B2);
            }
        } finally { Render2D.unclip(ctx); }
        String state = !pickerError.isEmpty()?pickerError:GuiBackground.image().status();
        Fonts.drawV(ctx,Fonts.trim(state,418,false,.67f),321,354,Theme.TEXT_MUTED,false,.67f);
        button(ctx,GuiBackground.image().loaded()?"Replace PNG":"Import PNG",320,369,135,26,true,this::browse);
        button(ctx,"Use PNG",463,369,135,26,false,()->{ClientSettings.guiBackgroundMode.set("PNG Image");imageTab=true;});
        button(ctx,"Remove PNG",606,369,134,26,false,()->{GuiBackground.image().remove();if(ClientSettings.guiBackgroundMode.is("PNG Image"))ClientSettings.guiBackgroundMode.set("Default");});
        Fonts.drawV(ctx,"PNG · up to 32 MB · large images resize safely · drop a file here",321,413,Theme.TEXT_MUTED,false,.61f);
    }
    private void mode(DrawContext ctx, ModeSetting setting, float y) {
        Fonts.drawV(ctx,setting.getName(),34,y+3,Theme.TEXT_DIM,false,.73f);
        Render2D.roundRect(ctx,34,y+14,252,20,4,hover(34,y+14,252,20)?0xFF253D5F:0xFF1B2C46);
        Fonts.drawCentered(ctx,setting.get(),160,y+24,Theme.TEXT,true,.71f);
        Fonts.drawV(ctx,"‹",43,y+24,Theme.TEXT_MUTED,true,.85f);Fonts.drawRight(ctx,"›",276,y+24,Theme.TEXT_MUTED,true,.85f);
        hit(34,y+14,252,20,(x,py,button)->{setting.cycle(button==1||x<70?-1:1);Sounds.click();});
    }
    private void number(DrawContext ctx, NumberSetting setting, float y) {
        Fonts.drawV(ctx,setting.getName(),34,y+4,Theme.TEXT_DIM,false,.70f);
        Fonts.drawRight(ctx,setting.format(),286,y+4,Theme.TEXT,true,.70f);
        Render2D.roundRect(ctx,34,y+18,252,4,2,0xFF2A3C56);
        Render2D.roundRect(ctx,34,y+18,252*(float)setting.getPercent(),4,2,Theme.accent());
        Render2D.circle(ctx,34+252*(float)setting.getPercent(),y+20,4,Theme.TEXT);
        hit(30,y+11,260,18,(x,py,button)->{
            if(button==1){setting.reset();return;}
            drag=(px,sy)->setting.setPercent((px-34)/252);drag.move(x,py);
        });
    }
    private void color(DrawContext ctx, ColorSetting setting, float y) {
        Fonts.drawV(ctx,setting.getName(),34,y+10,Theme.TEXT_DIM,false,.69f);
        Render2D.roundRect(ctx,234,y+2,52,17,4,setting.get());
        Render2D.roundOutline(ctx,234,y+2,52,17,4,.8f,0x66FFFFFF);
        hit(30,y,260,22,(x,py,button)->{if(button==1)setting.reset();else editingColor=setting;});
    }
    private void colorPicker(DrawContext ctx) {
        hits.clear();
        Render2D.rect(ctx,0,0,W,H,0xC5000000);
        float x=245,y=128,w=270,h=198;
        Render2D.roundRect(ctx,x,y,w,h,10,0xFF111F34);Render2D.roundOutline(ctx,x,y,w,h,10,1,Theme.accent(100));
        Fonts.drawV(ctx,editingColor.getName(),x+14,y+20,Theme.TEXT,true,.82f);
        float sx=x+14,sy=y+40,sw=208,sh=112;
        Render2D.rectGradient(ctx,sx,sy,sw,sh,0xFFFFFFFF,ColorUtil.hsv(editingColor.getHue(),1,1),ColorUtil.hsv(editingColor.getHue(),1,1),0xFFFFFFFF);
        Render2D.rectGradient(ctx,sx,sy,sw,sh,0,0,0xFF000000,0xFF000000);
        Render2D.ring(ctx,sx+sw*editingColor.getSaturation(),sy+sh*(1-editingColor.getBrightness()),4,1.5f,0xFFFFFFFF);
        hit(sx,sy,sw,sh,(px,py,button)->{drag=(dx,dy)->editingColor.setHsv(editingColor.getHue(),clamp((dx-sx)/sw),1-clamp((dy-sy)/sh),255);drag.move(px,py);});
        for(int i=0;i<6;i++)Render2D.rectGradient(ctx,x+234,sy+i*sh/6,18,sh/6+.01f,ColorUtil.hsv(i/6f,1,1),ColorUtil.hsv(i/6f,1,1),ColorUtil.hsv((i+1)/6f,1,1),ColorUtil.hsv((i+1)/6f,1,1));
        Render2D.roundRect(ctx,x+232,sy+sh*editingColor.getHue()-2,22,4,2,0xFFFFFFFF);
        hit(x+230,sy,26,sh,(px,py,button)->{drag=(dx,dy)->editingColor.setHsv(clamp((dy-sy)/sh),editingColor.getSaturation(),editingColor.getBrightness(),255);drag.move(px,py);});
        Fonts.drawV(ctx,ColorUtil.toHex(editingColor.get(),false),x+14,y+175,Theme.TEXT_MUTED,false,.8f);
        button(ctx,"Done",x+w-80,y+h-32,65,23,true,()->{editingColor=null;drag=null;});
    }
    private void browse() {
        if(GuiBackground.image().busy())return;
        pickerError="";
        client.setScreen(new dev.maro.gui.PngFilePickerScreen(this,Path.of(System.getProperty("user.home")),file->{GuiBackground.image().importPng(file);imageTab=true;}));
    }
    @Override public void onFilesDropped(List<Path> paths) { if(!paths.isEmpty()){pickerError="";GuiBackground.image().importPng(paths.getFirst());imageTab=true;} }
    private boolean hover(float x,float y,float w,float h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private void hit(float x,float y,float w,float h,Action action){hits.add(new Hit(x,y,w,h,action));}
    private void button(DrawContext ctx,String label,float x,float y,float w,float h,boolean primary,Runnable action){
        Render2D.roundRect(ctx,x,y,w,h,5,primary?(hover(x,y,w,h)?0xFF438FDB:0xFF2C71B8):(hover(x,y,w,h)?0xFF2A405E:0xFF1B2C46));
        Fonts.drawCentered(ctx,label,x+w/2,y+h/2,Theme.TEXT,true,.70f);hit(x,y,w,h,(px,py,button)->{if(button==0){Sounds.click();action.run();}});
    }
    @Override public boolean mouseClicked(Click click,boolean doubled){
        double x=(click.x()-ox)/scale,y=(click.y()-oy)/scale;
        for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)){focus=-1;hits.get(i).action().run(x,y,click.button());return true;}
        return true;
    }
    @Override public boolean mouseDragged(Click click,double dx,double dy){if(drag!=null){drag.move((click.x()-ox)/scale,(click.y()-oy)/scale);return true;}return false;}
    @Override public boolean mouseReleased(Click click){drag=null;return true;}
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){if(editingColor!=null){editingColor=null;drag=null;}else close();return true;}
        if(input.key()==GLFW.GLFW_KEY_TAB&&!hits.isEmpty()){focus=Math.floorMod(focus+((input.modifiers()&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),hits.size());return true;}
        if((input.key()==GLFW.GLFW_KEY_ENTER||input.key()==GLFW.GLFW_KEY_SPACE)&&focus>=0&&focus<hits.size()){
            Hit hit=hits.get(focus);hit.action().run(hit.x()+hit.w()/2,hit.y()+hit.h()/2,0);drag=null;return true;
        }
        return super.keyPressed(input);
    }
    @Override public void close(){drag=null;client.setScreen(parent);}
    @Override public void removed(){drag=null;ConfigManager.saveClient();}
    private static float clamp(double value){return (float)Math.clamp(value,0,1);}
}
