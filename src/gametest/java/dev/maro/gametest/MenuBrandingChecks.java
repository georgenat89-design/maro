package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.MaroHomeScreen;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import java.net.URI;
import java.util.ArrayList;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import dev.maro.gametest.mixin.PhoneMouseProbe;
import dev.maro.gametest.mixin.HandPlacementKeyboardProbe;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ViewModel;
import dev.maro.setting.Setting;
import dev.maro.setting.NumberSetting;
import dev.maro.gui.widget.SettingsList;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Arrays;
import org.lwjgl.glfw.GLFW;

public final class MenuBrandingChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private record WordmarkInk(double x,double y,double width,double height,double capHeight,long sequence){ }
    private static volatile WordmarkInk wordmarkInk;
    private static long wordmarkSequence;
    /** Observe the actual production call and transform, rather than reconstructing its layout. */
    public static void wordmarkDrawn(net.minecraft.client.gui.DrawContext context,MaroWordmark.Bounds bounds){
        var m=context.getMatrices();double pixels=net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor();
        double x0=Double.POSITIVE_INFINITY,y0=Double.POSITIVE_INFINITY,x1=Double.NEGATIVE_INFINITY,y1=Double.NEGATIVE_INFINITY;
        for(float x:new float[]{bounds.x(),bounds.x()+bounds.width()})for(float y:new float[]{bounds.y(),bounds.y()+bounds.height()}){
            double px=(m.m00()*x+m.m10()*y+m.m20())*pixels,py=(m.m01()*x+m.m11()*y+m.m21())*pixels;
            x0=Math.min(x0,px);x1=Math.max(x1,px);y0=Math.min(y0,py);y1=Math.max(y1,py);
        }
        wordmarkInk=new WordmarkInk(x0,y0,x1-x0,y1-y0,bounds.capHeight()*Math.hypot(m.m00(),m.m01())*pixels,++wordmarkSequence);
    }
    private static void measuredWordmark(ClientGameTestContext context,java.nio.file.Path path,String phase){
        var mark=wordmarkInk;require(mark!=null,"No shared wordmark was submitted in "+phase);
        try{var image=ImageIO.read(path.toFile());require(mark.x()>1&&mark.y()>1&&mark.x()+mark.width()<image.getWidth()-1&&mark.y()+mark.height()<image.getHeight()-1,"Shared wordmark clipped viewport in "+phase+": "+mark);require(mark.width()>mark.capHeight()*1.6&&mark.height()>=mark.capHeight()*.98&&mark.capHeight()>12,"Invalid native cap/wordmark geometry in "+phase+": "+mark);
            int ink=0;for(int y=Math.max(0,(int)Math.floor(mark.y()));y<Math.min(image.getHeight(),Math.ceil(mark.y()+mark.height()));y++)for(int x=Math.max(0,(int)Math.floor(mark.x()));x<Math.min(image.getWidth(),Math.ceil(mark.x()+mark.width()));x++){int rgb=image.getRGB(x,y);if(Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255))>160)ink++;}require(ink>80,"Actual shared wordmark pixels missing in "+phase+": "+ink);System.out.println("[wordmark] "+phase+" ink="+ink+" geometry="+mark);
        }catch(java.io.IOException error){throw new AssertionError(error);}
    }
    private static final class WordmarkReload implements ResourceReload{
        final CompletableFuture<Void> done=new CompletableFuture<>();
        @Override public CompletableFuture<?> whenComplete(){return done;}
        @Override public float getProgress(){return .58f;}
    }
    private static ClickGuiScreen gui(net.minecraft.client.MinecraftClient c){require(c.currentScreen instanceof ClickGuiScreen,"Expected native client menu");return(ClickGuiScreen)c.currentScreen;}
    private static void pointer(ClientGameTestContext ctx,double x,double y){ctx.runOnClient(c->{var screen=c.currentScreen;((PhoneMouseProbe)c.mouse).maro$cursor(c.getWindow().getHandle(),x*c.getWindow().getWidth()/screen.width,y*c.getWindow().getHeight()/screen.height);c.mouse.tick();});}
    private static void at(ClientGameTestContext ctx,float[] local){require(local!=null,"Missing sidebar/menu action");var screen=ctx.computeOnClient(c->gui(c).screenPose().project(local[0],local[1]));pointer(ctx,screen[0],screen[1]);}
    private static void tap(ClientGameTestContext ctx,float[] local){at(ctx,local);ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(6);}
    private static void wheel(ClientGameTestContext ctx,float[] rect,double amount){at(ctx,new float[]{rect[0]+rect[2]/2,rect[1]+rect[3]/2});ctx.runOnClient(c->((PhoneMouseProbe)c.mouse).maro$scroll(c.getWindow().getHandle(),0,amount));ctx.waitTicks(30);}
    private static int entry(ClientGameTestContext ctx,Category category){return ctx.computeOnClient(c->{for(int i=0;i<gui(c).entryCount();i++)if(gui(c).entryCategory(i)==category)return i;throw new AssertionError("Missing real category "+category);});}
    private static void revealEntry(ClientGameTestContext ctx,int index){for(int attempt=0;attempt<10&&ctx.computeOnClient(c->gui(c).entryPlace(index)==null);attempt++){double direction=index<ctx.computeOnClient(c->gui(c).entryCount())/2?3:-3;wheel(ctx,ctx.computeOnClient(c->gui(c).sidebarContentBounds()),direction);}require(ctx.computeOnClient(c->gui(c).entryPlace(index)!=null),"Sidebar clipping made entry inaccessible: "+index);}
    private static void chooseEntry(ClientGameTestContext ctx,int index){revealEntry(ctx,index);tap(ctx,ctx.computeOnClient(c->gui(c).entryPlace(index)));require(ctx.computeOnClient(c->gui(c).selectedEntry()==index&&gui(c).selectedPage()==index),"Native sidebar selection missed "+index);}
    private static void search(ClientGameTestContext ctx,String text){tap(ctx,ctx.computeOnClient(c->gui(c).placeOf(gui(c).getSearch())));ctx.getInput().holdControl();ctx.runOnClient(c->{var keyboard=(HandPlacementKeyboardProbe)c.keyboard;var key=new KeyInput(GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL);keyboard.maro$key(c.getWindow().getHandle(),GLFW.GLFW_PRESS,key);keyboard.maro$key(c.getWindow().getHandle(),GLFW.GLFW_RELEASE,key);});ctx.getInput().releaseControl();if(text.isEmpty())ctx.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);else ctx.getInput().typeChars(text);ctx.waitTicks(12);}
    private static SettingsList settingsList(ClickGuiScreen gui){try{var sf=ClickGuiScreen.class.getDeclaredField("settings");sf.setAccessible(true);var panel=sf.get(gui);var lf=panel.getClass().getDeclaredField("list");lf.setAccessible(true);return(SettingsList)lf.get(panel);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void sidebarGeometry(ClientGameTestContext ctx,String phase){ctx.runOnClient(c->{var g=gui(c);var side=g.sidebarBounds();var nav=g.sidebarContentBounds();var body=g.mainWindowBounds();require(side!=null&&nav!=null&&body!=null&&side[0]+side[2]<=body[0]&&body[0]-side[0]-side[2]<=32&&side[1]<body[1]&&side[1]+side[3]>=body[1]+body[3],phase+" detached/overlapped the connected left sidebar");float x=Float.NaN,y=-Float.MAX_VALUE;int visible=0;for(int i=0;i<g.entryCount();i++){require(g.entryLabel(i)!=null&&!g.entryLabel(i).isBlank(),phase+" lost a real sidebar label");var p=g.entryPlace(i);if(p==null)continue;require(p[0]>=nav[0]&&p[0]<nav[0]+nav[2]&&p[1]>=nav[1]&&p[1]<nav[1]+nav[3],phase+" exposed an unclipped sidebar hit");if(Float.isNaN(x))x=p[0];require(Math.abs(x-p[0])<.1&&p[1]>y,phase+" navigation is not one aligned vertical column");y=p[1];visible++;}require(visible>=4,phase+" hid too many navigation entries");for(float[] r:new float[][]{side,body,g.settingsBounds()})if(r!=null){var a=g.screenPose().project(r[0],r[1]);var b=g.screenPose().project(r[0]+r[2],r[1]+r[3]);require(a[0]>=-1&&a[1]>=-1&&b[0]<=g.width+1&&b[1]<=g.height+1,phase+" clipped a native pane");}});}
    private static void sidebarInput(ClientGameTestContext ctx,int scale){
        int count=ctx.computeOnClient(c->gui(c).entryCount());for(int i=0;i<count;i++){chooseEntry(ctx,i);sidebarGeometry(ctx,"entry "+i+" at GUI "+scale);}
        int visuals=entry(ctx,Category.VISUALS);chooseEntry(ctx,visuals);var view=ModuleManager.get(ViewModel.class);search(ctx,view.getName());require(ctx.computeOnClient(c->gui(c).modulePlace(view)!=null),"Native sidebar search lost the real Viewmodel module");boolean enabled=view.isEnabled();tap(ctx,ctx.computeOnClient(c->gui(c).modulePlace(view)));tap(ctx,ctx.computeOnClient(c->gui(c).settingsTabPlace()));require(ctx.computeOnClient(c->gui(c).settingsModule()==view)&&view.isEnabled()==enabled,"Native row selection toggled Viewmodel or lost settings");
        float[] row=ctx.computeOnClient(c->gui(c).modulePlace(view)),footer=ctx.computeOnClient(c->gui(c).moduleResetPlace());var settingsBefore=ctx.computeOnClient(c->settingsList(gui(c)).visiblePlaces());wheel(ctx,ctx.computeOnClient(c->gui(c).sidebarContentBounds()),3);ctx.runOnClient(c->{require(Arrays.equals(row,gui(c).modulePlace(view))&&Arrays.equals(footer,gui(c).moduleResetPlace()),"Sidebar wheel scrolled browser/settings");var after=settingsList(gui(c)).visiblePlaces();for(var item:settingsBefore.entrySet())if(after.containsKey(item.getKey()))require(Arrays.equals(item.getValue(),after.get(item.getKey())),"Sidebar wheel moved a setting row");});
        float settingsNavOffset=ctx.computeOnClient(c->gui(c).sidebarScroll());var number=ctx.computeOnClient(c->settingsList(gui(c)).visiblePlaces().keySet().stream().filter(NumberSetting.class::isInstance).findFirst().orElseThrow());float[] numberBefore=ctx.computeOnClient(c->settingsList(gui(c)).visiblePlaces().get(number));wheel(ctx,ctx.computeOnClient(c->gui(c).settingsContentBounds()),-6);ctx.runOnClient(c->{var after=settingsList(gui(c)).visiblePlaces().get(number);require(after==null||Math.abs(after[1]-numberBefore[1])>5,"Native settings content did not scroll");require(gui(c).sidebarScroll()==settingsNavOffset&&Arrays.equals(footer,gui(c).moduleResetPlace()),"Settings wheel moved sidebar/footer");});
        search(ctx,"");var first=ctx.computeOnClient(c->ModuleManager.all().stream().filter(m->m.getCategory()==Category.VISUALS&&gui(c).modulePlace(m)!=null).findFirst().orElseThrow());float[] before=ctx.computeOnClient(c->gui(c).modulePlace(first));float expectedOffset=ctx.computeOnClient(c->gui(c).sidebarScroll());wheel(ctx,ctx.computeOnClient(c->gui(c).browserContentBounds()),-6);ctx.runOnClient(c->{var after=gui(c).modulePlace(first);require(after==null||Math.abs(after[1]-before[1])>5,"Native module content did not scroll");require(gui(c).sidebarScroll()==expectedOffset,"Browser wheel moved sidebar navigation");});
        chooseEntry(ctx,visuals);revealEntry(ctx,0);pointer(ctx,8,8);ctx.waitTicks(12);var baseline=ctx.takeScreenshot("maro-sidebar-base-"+scale);var place=ctx.computeOnClient(c->gui(c).entryPlace(0));at(ctx,place);ctx.waitTicks(12);var hovered=ctx.takeScreenshot("maro-sidebar-hover-"+scale);try{var a=ImageIO.read(baseline.toFile());var b=ImageIO.read(hovered.toFile());int[] box=ctx.computeOnClient(c->{var g=gui(c);var nav=g.sidebarContentBounds();var left=g.screenPose().project(nav[0]+3,place[1]-12);var right=g.screenPose().project(nav[0]+nav[2]-3,place[1]+12);double pixels=c.getWindow().getScaleFactor();return new int[]{(int)left[0]*1,(int)left[1]*1,(int)right[0]*1,(int)right[1]*1,(int)Math.round(pixels*1000)};});double pixels=box[4]/1000d;int changed=0;for(int y=(int)(box[1]*pixels);y<(int)(box[3]*pixels);y++)for(int x=(int)(box[0]*pixels);x<(int)(box[2]*pixels);x++){int aa=a.getRGB(x,y),bb=b.getRGB(x,y);if(Math.abs((aa>>16&255)-(bb>>16&255))+Math.abs((aa>>8&255)-(bb>>8&255))+Math.abs((aa&255)-(bb&255))>25)changed++;}require(changed>10,"Native sidebar hover state was not visible: "+changed);}catch(java.io.IOException error){throw new AssertionError(error);}pointer(ctx,8,8);ctx.waitTicks(12);sidebarGeometry(ctx,"completed sidebar input at "+scale);
    }
    private static void capsInput(ClientGameTestContext ctx){
        pointer(ctx,8,8);ctx.waitTicks(12);var on=ctx.takeScreenshot("maro-sidebar-caps-on");var literal=wordmarkInk;
        search(ctx,"view model");require(ctx.computeOnClient(c->gui(c).getSearch().getText().equals("view model")&&gui(c).modulePlace(ModuleManager.get(ViewModel.class))!=null),"Caps presentation altered the literal search value/filter");
        float[] widths=ctx.computeOnClient(c->{float caps=Fonts.width("view model",false,1);Fonts.beginRaw();try{return new float[]{caps,Fonts.width("view model",false,1)};}finally{Fonts.endRaw();}});
        require(Math.abs(widths[0]-widths[1])>1,"Caps preference did not affect label measurement independently of raw input");search(ctx,"");ctx.runOnClient(c->{ClientSettings.capsText.set(false);require(dev.maro.nathan.render.CrispFont.current(false).sourceFile().equals(Fonts.fontFile(false)),"Native font did not follow Caps off");require(Math.abs(Fonts.width("view model",false,1)-widths[1])<.02,"Raw input measurement differs from literal sentence-case text");});pointer(ctx,8,8);ctx.waitTicks(12);var off=ctx.takeScreenshot("maro-sidebar-caps-off");var proper=wordmarkInk;
        require(Math.abs(literal.x()-proper.x())<.1&&Math.abs(literal.y()-proper.y())<.1&&Math.abs(literal.width()-proper.width())<.1&&Math.abs(literal.height()-proper.height())<.1,"Caps preference renamed/resized literal M+aro wordmark");
        try{var a=ImageIO.read(on.toFile());var b=ImageIO.read(off.toFile());int[] r=ctx.computeOnClient(c->{var nav=gui(c).sidebarContentBounds();var tl=gui(c).screenPose().project(nav[0],nav[1]);var br=gui(c).screenPose().project(nav[0]+nav[2],nav[1]+nav[3]);double pixel=c.getWindow().getScaleFactor();return new int[]{(int)(tl[0]*pixel),(int)(tl[1]*pixel),(int)(br[0]*pixel),(int)(br[1]*pixel)};});int changed=0;for(int y=r[1];y<r[3];y++)for(int x=r[0];x<r[2];x++){int aa=a.getRGB(x,y),bb=b.getRGB(x,y);if(Math.abs((aa>>16&255)-(bb>>16&255))+Math.abs((aa>>8&255)-(bb>>8&255))+Math.abs((aa&255)-(bb&255))>30)changed++;}require(changed>100,"Caps preference did not visibly update native sidebar labels: "+changed);}catch(java.io.IOException error){throw new AssertionError(error);}
        ctx.runOnClient(c->{ClientSettings.capsText.set(true);require(dev.maro.nathan.render.CrispFont.current(false).sourceFile().equals(Fonts.fontFile(false))&&Math.abs(Fonts.width("view model",false,1)-widths[0])<.02,"Caps on did not restore native font and label measurement");});ctx.waitTicks(12);
    }
    /** Focused native evidence for shared branding, typography and sidebar; no world or networking. */
    static void wordmark(ClientGameTestContext context){
        int originalScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        String font=ClientSettings.fontFace.get();boolean caps=ClientSettings.capsText.get(),custom=ClientSettings.customFont.get(),unified=ClientSettings.unifiedTypography.get(),screenGui=ClientSettings.screenGui.get(),spatial=ClientSettings.spatialMenu.get();double size=ClientSettings.guiSize.get();var serialized=new IdentityHashMap<Setting<?>,com.google.gson.JsonElement>();
        try{
            context.runOnClient(c->{ModuleManager.all().forEach(m->m.getSettings().forEach(s->serialized.put(s,s.toJson().deepCopy())));ClientSettings.screenGui.set(false);ClientSettings.spatialMenu.set(false);ClientSettings.guiSize.set(97d);ClientSettings.customFont.set(true);ClientSettings.fontFace.set("Barlow Condensed");ClientSettings.unifiedTypography.set(true);ClientSettings.capsText.set(true);require(Fonts.familyIndex()==3&&dev.maro.nathan.render.CrispFont.current(false).sourceFile().equals(Fonts.fontFile(false))&&dev.maro.nathan.render.CrispFont.current(true).sourceFile().equals("barlow_bold.ttf"),"Shared GUI/native text did not select genuine condensed strong font cuts");});
            for(int scale:new int[]{2,3,4}){
                context.runOnClient(c->{wordmarkInk=null;c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new MaroHomeScreen());require(c.getWindow().getScaleFactor()==scale,"Requested GUI scale "+scale+" was clamped; native scale coverage is incomplete");});context.getInput().setCursorPos(8,8);context.waitTicks(12);measuredWordmark(context,context.takeScreenshot("maro-wordmark-title-scale-"+scale),"title scale "+scale);
                context.runOnClient(c->{wordmarkInk=null;c.setScreen(new ClickGuiScreen());gui(c).openPage(Category.VISUALS.ordinal());});context.getInput().setCursorPos(8,8);context.waitTicks(12);sidebarGeometry(context,"scale "+scale);measuredWordmark(context,context.takeScreenshot("maro-wordmark-header-scale-"+scale),"sidebar scale "+scale);if(scale==2)capsInput(context);sidebarInput(context,scale);
                context.runOnClient(c->{wordmarkInk=null;c.setScreen(new MaroHomeScreen());});var reload=new WordmarkReload();context.runOnClient(c->c.setOverlay(new SplashOverlay(c,reload,result->{},false)));context.waitTicks(12);measuredWordmark(context,context.takeScreenshot("maro-wordmark-loading-scale-"+scale),"loading scale "+scale);context.runOnClient(c->reload.done.complete(null));context.waitTicks(35);require(context.computeOnClient(c->c.getOverlay()==null),"Controlled loading overlay failed to release at scale "+scale);
            }
            context.getInput().resizeWindow(960,540);context.runOnClient(c->{c.options.getGuiScale().setValue(4);c.onResolutionChanged();c.setScreen(new ClickGuiScreen());gui(c).openPage(Category.VISUALS.ordinal());});pointer(context,8,8);context.waitTicks(12);sidebarGeometry(context,"small viewport");sidebarInput(context,4);context.takeScreenshot("maro-sidebar-small-960x540");context.runOnClient(c->{for(var item:serialized.entrySet())require(item.getKey().toJson().equals(item.getValue()),"Global font/sidebar mutated saved module setting "+item.getKey().getName());});
            System.out.println("[wordmark-sidebar] PASS: literal shared wordmark/title/loading bounds+pixels; actual condensed font cuts; vertical native category/search/hover/independent scrolling; effective GUI scales2/3/4 and small viewport; module settings preserved");
        }finally{context.runOnClient(c->{c.setOverlay(null);ClientSettings.screenGui.set(screenGui);ClientSettings.spatialMenu.set(spatial);ClientSettings.guiSize.set(size);ClientSettings.customFont.set(custom);ClientSettings.fontFace.set(font);ClientSettings.unifiedTypography.set(unified);ClientSettings.capsText.set(caps);c.options.getGuiScale().setValue(originalScale);c.onResolutionChanged();c.setScreen(new TitleScreen());dev.maro.config.ConfigManager.saveClient();});context.getInput().resizeWindow(1280,720);}
    }
    static void run(ClientGameTestContext context){
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());String font=ClientSettings.fontFace.get();
        try{
            for(int size:new int[]{2,3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(size);c.onResolutionChanged();c.setScreen(new MaroHomeScreen());});context.getInput().setCursorPos(0,0);context.waitTicks(5);
                context.runOnClient(c->{var home=(MaroHomeScreen)c.currentScreen;var link=home.children().stream().filter(MenuCommunityButton.class::isInstance).map(MenuCommunityButton.class::cast).findFirst().orElseThrow();require(link.getX()>=0&&link.getY()>=0&&link.getRight()<=home.width&&link.getBottom()<=home.height,"Community link clipped at scale "+size);require(MenuCommunityButton.LINK.toString().equals("https://discord.gg/navo"),"Wrong community destination");require(ClientSettings.fontFace.get().equals(font),"Menu typography changed saved font");});
                context.takeScreenshot("maro-branding-home-"+size);
            }
            var opened=new ArrayList<URI>();
            context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new Screen(Text.literal("Community link input test")){
                @Override protected void init(){addDrawableChild(new MenuCommunityButton(width/2-92,height/2-15,184,31,opened::add));}
            });});context.waitTicks(4);
            context.runOnClient(c->{var screen=c.currentScreen;var link=(MenuCommunityButton)screen.children().getFirst();var click=new Click(link.getX()+10,link.getY()+10,new MouseInput(0,0));require(screen.mouseClicked(click,false),"Link missed native mouse input");screen.mouseReleased(click);screen.setFocused(link);require(screen.keyPressed(new KeyInput(257,0,0)),"Enter did not activate link");require(screen.keyPressed(new KeyInput(32,0,0)),"Space did not activate link");link.active=false;require(!link.keyPressed(new KeyInput(257,0,0)),"Inactive link accepted Enter");link.active=true;require(opened.size()==3&&opened.stream().allMatch(MenuCommunityButton.LINK::equals),"Link dispatched wrong destination/count");});context.waitTicks(3);context.takeScreenshot("maro-community-focused");
            System.out.println("[menu-branding] PASS: menu-only font leaves user preference intact, folded mark/link scales 2/3/4, native mouse/Enter/Space open exact navo URL, disabled link rejects input (browser opener captured)");
        }finally{context.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();c.setScreen(new TitleScreen());});}
    }
}
