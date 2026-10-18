package dev.maro.gui;

import dev.maro.gui.render.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.*;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.List;

/** One persistent home page. Native menu screens run inside its right-hand viewport. */
public final class MaroHomeScreen extends TitleScreen {
    private static boolean embeddedRendering;
    private Screen page;
    private Screen initialPage;
    private String active="";
    private boolean pageDrag;
    private float paneX,paneY,paneW,paneH,pageScale=1;
    private int pageHeight=400;
    private final MenuSceneControls scene=new MenuSceneControls();
    public boolean sceneOpen(){return scene.isOpen();}
    public MaroHomeScreen(){super(false);}
    public MaroHomeScreen(Screen initialPage){this();this.initialPage=initialPage;}
    public static boolean embeddedRendering(){return embeddedRendering;}
    public Screen page(){return page;}
    public double[] pagePoint(double x,double y){return new double[]{paneX+x*pageScale,paneY+y*pageScale};}
    public int[] paneBounds(){return new int[]{Math.round(paneX),Math.round(paneY),Math.round(paneX+paneW),Math.round(paneY+paneH)};}
    private void layoutPane(){
        float s=MaroTitleStyle.canvasScale(width,height),ox=(width-960*s)/2,oy=(height-540*s)/2;
        paneX=ox+338*s;paneY=oy+76*s;paneW=568*s;paneH=380*s;pageScale=paneW/600;
        pageHeight=Math.max(1,Math.round(paneH/pageScale));
    }
    @Override protected void init(){
        super.init();layoutPane();scene.init(width,height,this::addDrawableChild);
        float s=MaroTitleStyle.canvasScale(width,height),ox=(width-960*s)/2,oy=(height-540*s)/2;
        addDrawableChild(new MenuCommunityButton(Math.round(ox+388*s),Math.round(oy+477*s),Math.max(1,Math.round(184*s)),Math.max(1,Math.round(31*s))));
        if(initialPage!=null){var next=initialPage;initialPage=null;navigate(next);}
        else if(page!=null)page.resize(600,pageHeight);
    }
    public static boolean isLibrary(Screen screen){return screen instanceof MultiplayerScreen||screen instanceof SelectWorldScreen;}
    private static boolean canEmbed(Screen next){
        if(next==null||next instanceof TitleScreen||next instanceof DisconnectedScreen
                ||BootFlow.isLoading(next)&&!(next instanceof MessageScreen))return false;
        String name=next.getClass().getName();
        // The net.minecraft namespace is stable in both named and production-remapped builds.
        return name.startsWith("net.minecraft.")||name.startsWith("dev.maro.gui.accounts.");
    }
    /** Called before Minecraft replaces the home page, preserving native callbacks and parent screens. */
    public boolean navigate(Screen next){
        if(next instanceof TitleScreen){show(null);return true;}
        if(next==page&&page!=null)return true;
        if(!canEmbed(next))return false;
        if(next instanceof SelectWorldScreen)active="menu.singleplayer";
        else if(next instanceof MultiplayerScreen)active="menu.multiplayer";
        else if(next instanceof net.minecraft.client.gui.screen.option.OptionsScreen)active="menu.options";
        else if(next instanceof dev.maro.gui.accounts.AccountsScreen)active="maro.menu.accounts";
        show(next);return true;
    }
    private void show(Screen next){
        if(page!=null)page.removed();page=next;pageDrag=false;setFocused(null);setDragging(false);
        if(page==null){active="";MaroTitleStyle.layout(this);return;}
        MaroTitleStyle.layout(this);
        layoutPane();page.init(600,pageHeight);page.onDisplayed();
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        MaroTitleStyle.render(ctx,width,height);
        MaroTitleStyle.activePage(active);
        try{for(var child:children())if(!scene.owns(child)&&child instanceof Drawable drawable)drawable.render(ctx,mx,my,delta);}
        finally{MaroTitleStyle.activePage("");}
        if(page==null){scene.render(ctx,mx,my,delta);return;}
        float s=MaroTitleStyle.canvasScale(width,height),ox=(width-960*s)/2,oy=(height-540*s)/2;
        Render2D.roundRect(ctx,ox+328*s,oy+66*s,588*s,400*s,12*s,dev.maro.util.ColorUtil.withAlpha(0xFF131820,dev.maro.config.ClientSettings.menuSurfaceAlpha()));
        Render2D.roundOutline(ctx,ox+328*s,oy+66*s,588*s,400*s,12*s,.7f*s,0xFF2A313D);
        ctx.createNewRootLayer();ctx.enableScissor((int)Math.ceil(paneX),(int)Math.ceil(paneY),(int)Math.floor(paneX+paneW),(int)Math.floor(paneY+paneH));
        var previousClip=Render2D.getScissor();Render2D.setScissor(new ScreenRect((int)paneX,(int)paneY,(int)paneW,(int)paneH).transformEachVertex(ctx.getMatrices()));
        var pose=ctx.getMatrices();pose.pushMatrix();
        try{
            pose.translate(paneX,paneY);pose.scale(pageScale,pageScale);Fonts.setMeasureScale(pageScale);embeddedRendering=true;
            page.renderWithTooltip(ctx,(int)Math.floor((mx-paneX)/pageScale),(int)Math.floor((my-paneY)/pageScale),delta);
        }finally{embeddedRendering=false;Fonts.setMeasureScale(1);pose.popMatrix();ctx.disableScissor();Render2D.setScissor(previousClip);}
        scene.render(ctx,mx,my,delta);
    }
    private boolean inPane(double x,double y){return page!=null&&x>=paneX&&x<paneX+paneW&&y>=paneY&&y<paneY+paneH;}
    private Click local(Click click){return new Click((click.x()-paneX)/pageScale,(click.y()-paneY)/pageScale,click.buttonInfo());}
    @Override public boolean mouseClicked(Click click,boolean doubled){
        if(scene.mouseClicked(click,doubled,this::setFocused))return true;
        if(inPane(click.x(),click.y())){pageDrag=page.mouseClicked(local(click),doubled);return true;}
        for(var element:children())if(element instanceof ClickableWidget button&&button.isMouseOver(click.x(),click.y())){
            String key=MaroTitleStyle.key(button);if(key.equals("menu.singleplayer")||key.equals("menu.multiplayer")||key.equals("menu.options")||key.equals("maro.menu.accounts"))active=key;
        }
        return super.mouseClicked(click,doubled);
    }
    @Override public boolean mouseReleased(Click click){if(page!=null){boolean handled=page.mouseReleased(local(click));pageDrag=false;if(handled)return true;}return super.mouseReleased(click);}
    @Override public boolean mouseDragged(Click click,double dx,double dy){if(page!=null&&pageDrag)return page.mouseDragged(local(click),dx/pageScale,dy/pageScale);return super.mouseDragged(click,dx,dy);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x,y);if(page!=null)page.mouseMoved((x-paneX)/pageScale,(y-paneY)/pageScale);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(scene.blocksPointer(x,y))return true;return inPane(x,y)?page.mouseScrolled((x-paneX)/pageScale,(y-paneY)/pageScale,horizontal,vertical):super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(KeyInput input){if(scene.isOpen()){if(input.key()==256){scene.dismiss();setFocused(null);return true;}return super.keyPressed(input);}if(getFocused() instanceof MenuCommunityButton link&&link.keyPressed(input))return true;return page!=null?page.keyPressed(input):super.keyPressed(input);}
    @Override public boolean keyReleased(KeyInput input){return page!=null?page.keyReleased(input):super.keyReleased(input);}
    @Override public boolean charTyped(CharInput input){return page!=null?page.charTyped(input):super.charTyped(input);}
    @Override public void onFilesDropped(List<Path> paths){if(page!=null)page.onFilesDropped(paths);else super.onFilesDropped(paths);}
    @Override public void tick(){if(page!=null){page.tick();page.updateNarrator();}else super.tick();}
    @Override public void removed(){if(page!=null){page.removed();page=null;}super.removed();}
    @Override public void close(){if(page!=null)page.close();}
    @Override public Text getNarratedTitle(){return page!=null?page.getNarratedTitle():super.getNarratedTitle();}
}
