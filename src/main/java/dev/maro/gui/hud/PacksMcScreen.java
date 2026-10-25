package dev.maro.gui.hud;

import dev.maro.config.ConfigManager;
import dev.maro.gui.render.*;
import dev.maro.gui.theme.Theme;
import dev.maro.module.impl.visuals.TexturePacks;
import dev.maro.textures.*;
import dev.maro.textures.PacksMcCatalog.Pack;
import dev.maro.util.Sounds;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.*;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;
import java.net.URI;
import java.util.*;

/** Original native Maro browser for the first 50 PacksMC Trending packs. */
public final class PacksMcScreen extends Screen {
    private static final float W=820,H=480,GX=20,GY=111,GW=575,GH=303,CW=185,CH=145,STEP=155;
    private final Screen parent;
    private final TexturePacks module;
    private record Hit(float x,float y,float w,float h,Runnable action) {
        boolean contains(double x,double y){return x>=this.x&&y>=this.y&&x<this.x+w&&y<this.y+h;}
    }
    private final List<Hit> hits=new ArrayList<>();
    private Pack selected;
    private String query="";
    private boolean typing,ignoreSlash,favoritesOnly,zoom;
    private float scale=1,ox,oy,mx,my,scroll,target,maxScroll;
    private long lastFrame;
    private java.util.function.Consumer<URI> openInBrowser=uri->Util.getMainWorkerExecutor().execute(()->Util.getOperatingSystem().open(uri));
    public PacksMcScreen(Screen parent,TexturePacks module){super(Text.literal("PacksMC"));this.parent=parent;this.module=module;}
    @Override protected void init(){module.opened();module.refreshCatalog(false);}
    public Pack selected(){return selected;}
    public void selectPack(Pack pack){selected=pack;}
    public void query(String value){query=value;scroll=target=0;}
    public void favoritesOnly(boolean value){favoritesOnly=value;scroll=target=0;}
    public List<Pack> shown(){String q=query.strip().toLowerCase(Locale.ROOT);return module.catalog().packs().stream()
            .filter(p->!favoritesOnly||module.favorite(p.key())).filter(p->q.isEmpty()||(p.name()+" "+p.author()+" "+p.resolution()).toLowerCase(Locale.ROOT).contains(q)).toList();}
    public double[] cardPoint(int i){return point(GX+i%3*195+CW/2,GY+i/3*STEP-scroll+45);}
    public double[] point(float x,float y){return new double[]{ox+x*scale,oy+y*scale};}
    public boolean previewReady(Pack p){return preview(p)!=null;}
    public void zoom(boolean value){zoom=value;}
    public void scrollToEnd(){target=maxScroll;}
    public static URI downloadPage(Pack p){if(!PacksMcCatalog.packPage(p.page()))throw new IllegalArgumentException("Invalid pack page");return URI.create(p.page());}
    private void download(Pack p){openInBrowser.accept(downloadPage(p));}
    private void browse(Pack p){
        if(module.importing())return;
        client.setScreen(new dev.maro.gui.PngFilePickerScreen(this,Path.of(System.getProperty("user.home"),"Downloads"),"Import downloaded resource pack",file->module.importPack(p,file),"zip"));
    }
    @Override public void onFilesDropped(List<Path> files){if(selected!=null&&!files.isEmpty())module.importPack(selected,files.getFirst());}
    @Override public void renderBackground(DrawContext ctx,int x,int y,float delta){}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        long now=System.nanoTime();float dt=lastFrame==0?0:Math.min(.1f,(now-lastFrame)/1e9f);lastFrame=now;
        scale=Math.min((width-16f)/W,(height-16f)/H);ox=(width-W*scale)/2;oy=(height-H*scale)/2;
        mx=(mouseX-ox)/scale;my=(mouseY-oy)/scale;hits.clear();Theme.update();module.pump();
        target=Math.clamp(target,0,maxScroll);scroll+=(target-scroll)*(1-(float)Math.exp(-18*dt));
        if(selected==null&&!module.catalog().packs().isEmpty())selected=module.catalog().packs().getFirst();
        Render2D.rect(ctx,0,0,width,height,0xBD060A12);ctx.getMatrices().pushMatrix();ctx.getMatrices().translate(ox,oy);ctx.getMatrices().scale(scale,scale);
        Fonts.beginRaw();Fonts.setMeasureScale(scale);
        try{
            Render2D.shadow(ctx,0,5,W,H,14,24,0x88000000);Render2D.roundRect(ctx,0,0,W,H,14,0xFC101724);
            Render2D.roundRect(ctx,17,16,4,30,2,Theme.accent());label(ctx,"PacksMC",30,25,1.18f,true,Theme.TEXT);
            label(ctx,module.cachedCount()+" / "+module.catalog().packs().size()+" saved · "+module.catalog().message(),30,44,.66f,false,Theme.TEXT_MUTED);
            button(ctx,"Browse website",446,17,124,25,false,()->openInBrowser.accept(URI.create(PacksMcCatalog.WEBSITE)));
            button(ctx,"Installed",578,17,81,25,false,()->client.setScreen(new TexturePacksScreen(this,module)));
            button(ctx,module.catalog().loading()?"Loading…":"Refresh",667,17,70,25,false,this::refresh);
            button(ctx,"Done",745,17,61,25,true,this::close);
            Render2D.rect(ctx,16,60,W-32,.7f,0x24FFFFFF);
            Render2D.roundRect(ctx,20,72,400,28,7,0xFF0A101B);
            label(ctx,query.isEmpty()?(typing?"Type to search…":"Search these 50 packs or their creators…"):query,30,86,.76f,false,query.isEmpty()?Theme.TEXT_MUTED:Theme.TEXT);
            hit(20,72,400,28,()->typing=true);button(ctx,favoritesOnly?"★ Saved":"All 50",428,72,89,28,favoritesOnly,()->favoritesOnly(!favoritesOnly));
            label(ctx,shown().size()+" packs",530,86,.68f,false,Theme.TEXT_MUTED);
            grid(ctx);inspector(ctx);
            Render2D.rect(ctx,16,430,W-32,.7f,0x24FFFFFF);
            label(ctx,module.importStatus().isEmpty()?"Choose a pack · Right-click preview · / search":module.importStatus(),22,447,.65f,false,Theme.TEXT_DIM);
            label(ctx,"Download opens your web browser · Import the ZIP here · Saved packs apply offline.",22,465,.61f,false,Theme.TEXT_MUTED);
            button(ctx,"Creator page",593,441,109,26,false,()->{if(selected!=null)download(selected);});
            button(ctx,module.isEnabled()?"Packs: On":"Packs: Off",710,441,96,26,module.isEnabled(),module::toggle);
            if(zoom&&selected!=null){ctx.createNewRootLayer();hits.clear();Render2D.roundRect(ctx,0,0,W,H,14,0xF5050B13);
                picture(ctx,selected,30,62,760,350);label(ctx,Fonts.trim(selected.name(),600,true,1f),30,435,1f,true,Theme.TEXT);
                label(ctx,"by "+selected.author(),30,457,.78f,false,Theme.TEXT_DIM);button(ctx,"Back",730,17,76,27,true,()->zoom=false);}
        }finally{Render2D.setScissor(null);Render2D.setAlpha(1);Fonts.setMeasureScale(1);Fonts.endRaw();ctx.getMatrices().popMatrix();}
    }
    private void grid(DrawContext ctx){
        List<Pack> packs=shown();maxScroll=Math.max(0,((packs.size()+2)/3)*STEP-10-GH);
        Render2D.clip(ctx,(int)GX,(int)GY,(int)(GX+GW),(int)(GY+GH));
        try{for(int i=0;i<packs.size();i++){
            Pack p=packs.get(i);float x=GX+i%3*195,y=GY+i/3*STEP-scroll;if(y+CH<GY||y>GY+GH)continue;
            boolean active=selected!=null&&selected.id().equals(p.id());
            Render2D.roundRect(ctx,x,y,CW,CH,8,active?0xFF24354B:0xFF172335);picture(ctx,p,x+4,y+4,CW-8,99.56f);
            label(ctx,Fonts.trim(p.name(),CW-16,true,.78f),x+8,y+115,.78f,true,Theme.TEXT);
            label(ctx,Fonts.trim("by "+p.author(),CW-52,false,.65f),x+8,y+133,.65f,false,Theme.TEXT_DIM);
            Fonts.drawRight(ctx,p.resolution(),x+CW-8,y+133,Theme.TEXT_MUTED,false,.61f);
            Render2D.roundOutline(ctx,x,y,CW,CH,8,.8f,active?Theme.accent():0x22FFFFFF);
            clippedHit(x,y,CW,CH,()->selected=p);
            if(module.cached(p)){
                boolean applied=module.gameHasOn(module.importedProfile(p));
                Render2D.roundRect(ctx,x+8,y+8,43,16,4,0xE20D201B);label(ctx,"SAVED",x+14,y+16,.52f,true,0xFFA4E8CD);
                float by=y+78;
                Render2D.roundRect(ctx,x+CW-57,by,49,20,5,applied?0xED225844:0xED192B35);
                Fonts.drawCentered(ctx,applied?"Active":"Apply",x+CW-32.5f,by+10,0xFFBCEBD9,true,.62f);
                clippedHit(x+CW-57,by,49,20,()->{selected=p;module.applyImported(p);});
            }
            if(inside(x,y,CW,CH)||module.favorite(p.key())){
                Render2D.roundRect(ctx,x+CW-27,y+7,20,20,6,0xE409111B);label(ctx,"★",x+CW-23,y+17,.85f,true,module.favorite(p.key())?Theme.YELLOW:Theme.TEXT_DIM);
                clippedHit(x+CW-27,y+7,20,20,()->module.toggleFavorite(p.key()));}
        }}finally{Render2D.unclip(ctx);}
        if(packs.isEmpty())label(ctx,module.catalog().loading()?"Loading packs…":"No packs match this search.",GX+12,GY+30,.85f,false,Theme.TEXT_MUTED);
        if(maxScroll>0){float thumb=Math.max(24,GH*GH/(GH+maxScroll));Render2D.roundRect(ctx,599,GY+(GH-thumb)*scroll/maxScroll,2,thumb,1,Theme.accent(0x80));}
    }
    private void inspector(DrawContext ctx){
        Render2D.roundRect(ctx,612,72,194,342,10,0xFF0C1421);Pack p=selected;if(p==null)return;
        label(ctx,"PREVIEW",624,87,.62f,true,Theme.TEXT_MUTED);picture(ctx,p,620,102,178,100.125f);
        label(ctx,Fonts.trim(p.name(),170,true,.85f),624,219,.85f,true,Theme.TEXT);
        label(ctx,Fonts.trim("by "+p.author(),170,false,.7f),624,238,.7f,false,Theme.TEXT_DIM);
        label(ctx,p.resolution()+" · "+String.format(Locale.ROOT,"%.1f MB",p.bytes()/1048576d),624,256,.65f,false,Theme.TEXT_MUTED);
        label(ctx,Fonts.trim(p.exclusive()?"Packs+ / paid access on site":"MC "+String.join(", ",p.versions()),170,false,.64f),624,274,.64f,false,p.exclusive()?Theme.YELLOW:Theme.TEXT_DIM);
        label(ctx,Fonts.trim(p.license().isEmpty()?"Creator terms on PacksMC":p.license(),170,false,.6f),624,291,.6f,false,Theme.TEXT_MUTED);
        boolean imported=module.cached(p);
        button(ctx,imported?"Apply pack":"Download pack",622,306,174,27,true,()->{if(imported)module.applyImported(p);else download(p);});
        button(ctx,module.importing()?"Importing…":"Import downloaded ZIP",622,340,174,27,false,()->browse(p));
        button(ctx,"Enlarge",622,376,82,25,false,()->zoom=true);button(ctx,module.favorite(p.key())?"★ Saved":"★ Save",712,376,84,25,false,()->module.toggleFavorite(p.key()));
    }
    private String previewKey(Pack p){return p.id()+"_"+Integer.toHexString(p.preview().hashCode());}
    private void refresh(){for(var p:module.catalog().packs())if(PackIcons.failed("preview/"+previewKey(p))){PackIcons.forget("preview/"+previewKey(p));PacksMcCatalog.forgetPreview(p.preview());}module.refreshCatalog(true);}
    private net.minecraft.util.Identifier preview(Pack p){return PackIcons.preview(previewKey(p),p.preview());}
    private void picture(DrawContext ctx,Pack p,float x,float y,float w,float h){
        Render2D.roundRect(ctx,x,y,w,h,5,0xFF080E17);var id=preview(p);
        if(id==null){Fonts.drawCentered(ctx,p.preview().isBlank()?"No preview":PackIcons.failed("preview/"+previewKey(p))?"Preview unavailable · Refresh":"Loading preview…",x+w/2,y+h/2,Theme.TEXT_MUTED,false,.68f);return;}
        float fit=Math.min(w/512,h/288),dw=512*fit,dh=288*fit;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED,id,Math.round(x+(w-dw)/2),Math.round(y+(h-dh)/2),0,0,Math.round(dw),Math.round(dh),512,288,512,288);
    }
    private void label(DrawContext c,String s,float x,float y,float size,boolean bold,int color){Fonts.drawV(c,s,x,y,color,bold,size);}
    private void button(DrawContext c,String s,float x,float y,float w,float h,boolean accent,Runnable action){Render2D.roundRect(c,x,y,w,h,6,accent?Theme.accent(inside(x,y,w,h)?0xFF:0xD8):inside(x,y,w,h)?0xFF2C3D53:0xFF1B293B);Fonts.drawCentered(c,s,x+w/2,y+h/2,Theme.TEXT,false,.7f);hit(x,y,w,h,action);}
    private boolean inside(float x,float y,float w,float h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private void hit(float x,float y,float w,float h,Runnable a){hits.add(new Hit(x,y,w,h,a));}
    private void clippedHit(float x,float y,float w,float h,Runnable a){float top=Math.max(GY,y),bottom=Math.min(GY+GH,y+h);if(bottom>top)hit(x,top,w,bottom-top,a);}
    @Override public boolean mouseClicked(Click c,boolean doubled){
        double x=(c.x()-ox)/scale,y=(c.y()-oy)/scale;typing=false;
        if(c.button()==GLFW.GLFW_MOUSE_BUTTON_RIGHT&&!zoom&&x>=GX&&x<GX+GW&&y>=GY&&y<GY+GH){int i=(int)((y-GY+scroll)/STEP)*3+(int)((x-GX)/195);var list=shown();if(i>=0&&i<list.size()){selected=list.get(i);zoom=true;}return true;}
        if(c.button()!=0)return true;for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)){Sounds.click();hits.get(i).action.run();break;}return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(!zoom&&(x-ox)/scale<GX+GW&&(y-oy)/scale>=GY&&(y-oy)/scale<GY+GH)target-=v*48;return true;}
    @Override public boolean charTyped(CharInput input){if(ignoreSlash){ignoreSlash=false;if(input.codepoint()=='/')return true;}if(typing&&input.codepoint()>=32&&input.codepoint()<=Character.MAX_VALUE&&query.length()<64)query(query+(char)input.codepoint());return true;}
    @Override public boolean keyPressed(KeyInput input){int k=input.key();ignoreSlash=false;if(k==GLFW.GLFW_KEY_ESCAPE){if(zoom)zoom=false;else if(typing){typing=false;query("");}else close();}
        else if(typing){if(k==GLFW.GLFW_KEY_BACKSPACE&&!query.isEmpty())query(query.substring(0,query.length()-1));if(k==GLFW.GLFW_KEY_ENTER)typing=false;}
        else if(k==GLFW.GLFW_KEY_SLASH||k==GLFW.GLFW_KEY_F&&(input.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0){typing=true;ignoreSlash=k==GLFW.GLFW_KEY_SLASH;}
        else if(k==GLFW.GLFW_KEY_F&&selected!=null)module.toggleFavorite(selected.key());else if(k==GLFW.GLFW_KEY_ENTER&&selected!=null)zoom=true;return true;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public void removed(){module.cancelPendingActivation();ConfigManager.saveAll();}
    @Override public boolean shouldPause(){return false;}
}
