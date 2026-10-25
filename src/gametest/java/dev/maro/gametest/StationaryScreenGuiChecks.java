package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.config.ClientSettings;
import dev.maro.gametest.mixin.PhoneMouseProbe;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.ScreenGuiPose;
import dev.maro.gui.render.ScreenGuiSurface;
import dev.maro.gui.widget.Dropdown;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Native rendering/picking on the same stationary plane, in a disposable landmark fixture. */
public final class StationaryScreenGuiChecks {
    private static volatile boolean running, watchPackets, tooltipOnSurface, dropdownOnSurface;
    private static final List<String> GAMEPLAY = new CopyOnWriteArrayList<>();
    private static Fixture fixture;
    private record Anchor(Vec3d centre, Vec3d x, Vec3d y, double width, double height) { }
    private record Rectangle(double x, double y, double width, double height) {
        float[] at(double fraction) { return new float[] {(float)(x + width * fraction), (float)(y + height / 2)}; }
        boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}
    }
    private static final class Fixture extends Module {
        final BooleanSetting flag = add(new BooleanSetting("Fixture Toggle", "Stationary world-panel tooltip", false));
        final NumberSetting amount = add(new NumberSetting("Fixture Amount", "Native projected slider", 2, 0, 10, .5));
        final ModeSetting choice = add(new ModeSetting("Fixture Choice", "Attached dropdown choices", "Choice One",
                "Choice One", "Choice Two", "Choice Three", "Choice Four", "Choice Five", "Choice Six"));
        Fixture() { super("Stationary GUI Test", "Native world-plane interaction fixture", Category.MISC); }
    }
    private StationaryScreenGuiChecks() { }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static ClickGuiScreen gui(MinecraftClient c) {
        require(c.currentScreen instanceof ClickGuiScreen, "Stationary menu unexpectedly changed screens"); return (ClickGuiScreen)c.currentScreen;
    }
    private static Object field(Class<?> type, Object target, String name) {
        try { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static boolean surface(DrawContext c) { return ((DrawContextAccessor)c).maro$getState()==field(ScreenGuiSurface.class,null,"STATE"); }
    /** Only gametests draw these landmarks, through the actual menu's DrawContext. */
    public static void panelMarker(DrawContext c) {
        if(!running||!(MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen gui))return;
        var panel=gui.mainWindowBounds();if(panel==null)return;float alpha=Render2D.getAlpha();Render2D.setAlpha(1);
        try{for(float[] p:markers(panel))Render2D.rect(c,p[0]-5,p[1]-5,10,10,0xFFFF00FF);}finally{Render2D.setAlpha(alpha);}
    }
    public static void tooltipPass(DrawContext c){if(running)tooltipOnSurface=surface(c);}
    public static void dropdownPass(DrawContext c){if(running&&Dropdown.anyOpen())dropdownOnSurface=surface(c);}
    public static void outbound(Packet<?> packet){
        if(watchPackets&&(packet instanceof PlayerInteractBlockC2SPacket||packet instanceof PlayerInteractItemC2SPacket
                ||packet instanceof PlayerInteractEntityC2SPacket||packet instanceof PlayerActionC2SPacket
                ||packet instanceof HandSwingC2SPacket||packet instanceof ClickSlotC2SPacket
                ||packet instanceof CreativeInventoryActionC2SPacket||packet instanceof UpdateSelectedSlotC2SPacket))GAMEPLAY.add(packet.getClass().getSimpleName());
    }
    private static float[][] markers(float[] p){return new float[][]{{p[0]+8,p[1]+8},{p[0]+p[2]-8,p[1]+8},{p[0]+8,p[1]+p[3]-8},{p[0]+p[2]-8,p[1]+p[3]-8}};}
    private static Anchor anchor(ScreenGuiPose p){require(p.active()&&p.centre()!=null,"No world anchor");return new Anchor(p.centre(),p.anchorX(),p.anchorY(),p.planeWidth(),p.planeHeight());}
    private static void stationary(ClickGuiScreen gui,Anchor a,String phase){
        var p=gui.screenPose();require(p.active()&&p.centre()!=null&&p.centre().squaredDistanceTo(a.centre())<1e-10,phase+" moved world centre");
        require(p.anchorX().squaredDistanceTo(a.x())<1e-10&&p.anchorY().squaredDistanceTo(a.y())<1e-10,phase+" rotated/resized world basis");
        require(Math.abs(p.planeWidth()-a.width())<1e-6&&Math.abs(p.planeHeight()-a.height())<1e-6,phase+" changed physical size");
        require(ClientSettings.screenGui.get(),phase+" disabled Screen GUI as fallback");
    }
    private static BufferedImage screenshot(ClientGameTestContext ctx,String name){try{return ImageIO.read(ctx.takeScreenshot("maro-stationary-"+name).toFile());}catch(Exception e){throw new AssertionError(e);}}
    private static boolean magenta(int c){return(c>>16&255)>220&&(c>>8&255)<35&&(c&255)>220;}
    private static int marks(BufferedImage image){int n=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(magenta(image.getRGB(x,y)))n++;return n;}
    private static void markerProjection(ClientGameTestContext ctx,BufferedImage image,String phase){
        var points=ctx.computeOnClient(c->{var g=gui(c);var local=markers(g.mainWindowBounds());var out=new double[4][];double scale=c.getWindow().getScaleFactor();for(int i=0;i<4;i++){var p=g.screenPose().project(local[i][0],local[i][1]);out[i]=new double[]{p[0]*scale,p[1]*scale};}return out;});
        int checked=0;
        for(double[] p:points){if(!Double.isFinite(p[0])||!Double.isFinite(p[1])||p[0]<15||p[1]<15||p[0]>=image.getWidth()-15||p[1]>=image.getHeight()-15)continue;
            int n=0;double sx=0,sy=0;for(int y=(int)p[1]-14;y<=(int)p[1]+14;y++)for(int x=(int)p[0]-14;x<=(int)p[0]+14;x++)if(magenta(image.getRGB(x,y))){n++;sx+=x+.5;sy+=y+.5;}
            require(n>=3&&Math.hypot(sx/n-p[0],sy/n-p[1])<5,phase+" native marker/picking projection mismatch");checked++;
        }require(checked>=2,phase+" put too little actual panel in view");
    }
    private static double[][] contentQuad(ClientGameTestContext ctx){return ctx.computeOnClient(c->{var g=gui(c);var b=g.visualPreview().contentBounds();require(b!=null,"No attached preview content area");
        float[][] corners={{b[0],b[1]},{b[0]+b[2],b[1]},{b[0]+b[2],b[1]+b[3]},{b[0],b[1]+b[3]}};var result=new double[4][];double scale=c.getWindow().getScaleFactor();
        for(int i=0;i<4;i++){var p=g.screenPose().project(corners[i][0],corners[i][1]);require(Double.isFinite(p[0])&&Double.isFinite(p[1]),"Preview quad outside camera");result[i]=new double[]{p[0]*scale,p[1]*scale};}return result;});}
    private static boolean inside(double[][] quad,double x,double y){double sign=0;for(int i=0;i<4;i++){double[] a=quad[i],b=quad[(i+1)%4];double cross=(b[0]-a[0])*(y-a[1])-(b[1]-a[1])*(x-a[0]);if(sign==0)sign=cross;else if(sign*cross<0)return false;}return true;}
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void subjectPixels(ClientGameTestContext ctx){
        var esp=ModuleManager.get(PlayerESP.class);int frames=dev.maro.gui.render.VisualPreviewEffect.frames();
        ctx.runOnClient(c->{((ModeSetting)setting(esp,"Fill Style")).set("Solid");((NumberSetting)setting(esp,"Fill Opacity")).set(100d);((ColorSetting)setting(esp,"Color")).set(0xFFFF2020);});ctx.waitTicks(6);
        var red=screenshot(ctx,"attached-esp-red");var quad=contentQuad(ctx);ctx.runOnClient(c->((ColorSetting)setting(esp,"Color")).set(0xFF2080FF));ctx.waitTicks(6);var blue=screenshot(ctx,"attached-esp-blue");int changed=0;
        for(int y=0;y<red.getHeight();y++)for(int x=0;x<red.getWidth();x++)if(inside(quad,x+.5,y+.5)){int a=red.getRGB(x,y),b=blue.getRGB(x,y);if((a>>16&255)>(a&255)+70&&(b&255)>(b>>16&255)+70)changed++;}
        require(changed>80&&dev.maro.gui.render.VisualPreviewEffect.frames()>frames,"Anchored native player silhouette did not render/update its actual shader pixels: "+changed);
        var view=ModuleManager.get(dev.maro.module.impl.visuals.ViewModel.class);ctx.runOnClient(c->{gui(c).openModuleOptions(view);for(String name:List.of("Main X","Main Y","Main Z","Main Rot X","Main Rot Y","Main Rot Z"))((NumberSetting)setting(view,name)).set(0d);((NumberSetting)setting(view,"Main Scale")).set(1d);((ModeSetting)setting(view,"Swing Mode")).set("None");((BooleanSetting)setting(view,"Hide Off Hand")).set(true);});ctx.waitTicks(6);
        var normal=screenshot(ctx,"attached-item-normal");quad=contentQuad(ctx);ctx.runOnClient(c->((NumberSetting)setting(view,"Main Scale")).set(.35d));ctx.waitTicks(6);var small=screenshot(ctx,"attached-item-small");changed=0;
        for(int y=0;y<normal.getHeight();y++)for(int x=0;x<normal.getWidth();x++)if(inside(quad,x+.5,y+.5)&&normal.getRGB(x,y)!=small.getRGB(x,y))changed++;
        require(changed>80,"Anchored native held-item preview remained blank or ignored its scale: "+changed);
        ctx.runOnClient(c->{require(!view.isEnabled()&&!esp.isEnabled(),"Attached native subject preview activated a gameplay module");gui(c).openModuleOptions(esp);});ctx.waitTicks(6);
    }
    private static void aim(ClientGameTestContext ctx,Vec3d target){ctx.runOnClient(c->{var d=target.subtract(c.player.getEyePos());float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));c.player.setYaw(yaw);c.player.setPitch(pitch);c.player.setHeadYaw(yaw);});ctx.waitTicks(4);}
    private static void eye(ClientGameTestContext ctx,TestSingleplayerContext world,Vec3d eye,Vec3d target){
        double height=ctx.computeOnClient(c->c.player.getEyeY()-c.player.getY());var d=target.subtract(eye);
        world.getServer().runCommand(String.format(Locale.ROOT,"tp @a %.8f %.8f %.8f %.6f %.6f",eye.x,eye.y-height,eye.z,Math.toDegrees(Math.atan2(-d.x,d.z)),-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))));
        hover(ctx,world);ctx.waitTicks(6);aim(ctx,target);
    }
    private static void hover(ClientGameTestContext ctx,TestSingleplayerContext world){
        world.getServer().runOnServer(server->{var p=server.getPlayerManager().getPlayerList().getFirst();p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.setVelocity(Vec3d.ZERO);p.sendAbilitiesUpdate();});
        ctx.runOnClient(c->{c.player.getAbilities().allowFlying=true;c.player.getAbilities().flying=true;c.player.setVelocity(Vec3d.ZERO);});
    }
    private static Vec3d point(ClientGameTestContext ctx,float[] local){require(local!=null,"Missing native control");return ctx.computeOnClient(c->gui(c).screenPose().worldPoint(local[0],local[1]));}
    private static void aimProjected(ClientGameTestContext ctx,float[] local){
        aim(ctx,point(ctx,local));
        Rectangle intended=ctx.computeOnClient(c->topHit(gui(c),local[0],local[1]));
        for(int iteration=0;iteration<12;iteration++){
            boolean aligned=ctx.computeOnClient(c->{var g=gui(c);var picked=g.screenPose().unproject(g.width/2d,g.height/2d);
                if(intended!=null&&intended.contains(picked[0],picked[1]))return true;
                var p=g.screenPose().project(local[0],local[1]);double dx=p[0]-g.width/2d,dy=p[1]-g.height/2d;
                require(Double.isFinite(dx)&&Double.isFinite(dy),"Fixture target projected outside the visible plane");
                if(intended==null&&Math.hypot(dx,dy)<.25)return true;
                double focal=g.height/(2*Math.tan(Math.toRadians(c.options.getFov().getValue()*.5)));
                c.player.setYaw(c.player.getYaw()+(float)Math.toDegrees(Math.atan(dx/focal)));
                c.player.setPitch(c.player.getPitch()+(float)Math.toDegrees(Math.atan(dy/focal)));return false;});
            if(aligned)return;ctx.waitTicks(3);
        }
        ctx.runOnClient(c->{var g=gui(c);var p=g.screenPose().project(local[0],local[1]);var picked=g.screenPose().unproject(g.width/2d,g.height/2d);
            require(intended!=null?intended.contains(picked[0],picked[1]):Math.hypot(p[0]-g.width/2d,p[1]-g.height/2d)<1,
                "Fixture crosshair missed intended control "+intended+" desired "+Arrays.toString(local)+" pick "+Arrays.toString(picked)+" projection "+Arrays.toString(p));});
    }
    private static Rectangle topHit(ClickGuiScreen gui,double x,double y){
        var hits=(List<?>)field(ClickGuiScreen.class,gui,"hits");for(int i=hits.size()-1;i>=0;i--)try{
            Object hit=hits.get(i);double[] r=new double[4];int index=0;for(String name:List.of("x","y","w","h")){var m=hit.getClass().getDeclaredMethod(name);m.setAccessible(true);r[index++]=((Number)m.invoke(hit)).doubleValue();}
            var rectangle=new Rectangle(r[0],r[1],r[2],r[3]);if(rectangle.contains(x,y))return rectangle;
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}return null;
    }
    private static void inputDiagnostic(ClientGameTestContext ctx,float[] local){ctx.runOnClient(c->{var g=gui(c);var p=g.screenPose().unproject(g.width/2d,g.height/2d);var world=g.screenPose().worldPoint(local[0],local[1]);
        System.out.println("[stationary-input] desired="+Arrays.toString(local)+" world="+world+" centrePick="+Arrays.toString(p)
                +" mouse="+field(ClickGuiScreen.class,g,"mouseXd")+","+field(ClickGuiScreen.class,g,"mouseYd")
                +" focus="+(g.focused!=null)+" listening="+(g.listening!=null)+" drag="+(field(ClickGuiScreen.class,g,"drag")!=null));
        var hits=(List<?>)field(ClickGuiScreen.class,g,"hits");
        for(int index=hits.size()-1;index>=0;index--)try{
            Object hit=hits.get(index);double[] r=new double[4];int i=0;for(String n:List.of("x","y","w","h")){var m=hit.getClass().getDeclaredMethod(n);m.setAccessible(true);r[i++]=((Number)m.invoke(hit)).doubleValue();}
            if(!(p[0]>=r[0]&&p[0]<r[0]+r[2]&&p[1]>=r[1]&&p[1]<r[1]+r[3]))continue;
            var m=hit.getClass().getDeclaredMethod("handler");m.setAccessible(true);Object handler=m.invoke(hit);var names=new ArrayList<String>();
            for(Field f:handler.getClass().getDeclaredFields()){f.setAccessible(true);Object v=f.get(handler);if(v instanceof Setting<?> s)names.add(s.getName());}
            System.out.println("[stationary-input] topHit="+Arrays.toString(r)+" settings="+names);break;
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    });}
    private static void click(ClientGameTestContext ctx,float[] local,int button){aimProjected(ctx,local);inputDiagnostic(ctx,local);ctx.getInput().pressMouse(button);ctx.waitTicks(2);ctx.runOnClient(c->require(!c.options.attackKey.isPressed()&&!c.options.useKey.isPressed(),"Plane click leaked mining/item use"));}
    private static Rectangle hit(ClickGuiScreen gui,Object captured,String choice){
        for(Object hit:(List<?>)field(ClickGuiScreen.class,gui,"hits"))try{
            var hm=hit.getClass().getDeclaredMethod("handler");hm.setAccessible(true);Object handler=hm.invoke(hit);boolean object=false,option=choice==null;
            for(Field f:handler.getClass().getDeclaredFields()){f.setAccessible(true);Object v=f.get(handler);object|=v==captured;option|=choice!=null&&choice.equals(v);}if(!object||!option)continue;
            double[] r=new double[4];int i=0;for(String name:List.of("x","y","w","h")){var m=hit.getClass().getDeclaredMethod(name);m.setAccessible(true);r[i++]=((Number)m.invoke(hit)).doubleValue();}return new Rectangle(r[0],r[1],r[2],r[3]);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}throw new AssertionError("No native hit for "+captured+" / "+choice);
    }
    private static float[] control(ClientGameTestContext ctx,Setting<?> s,double fraction){return ctx.computeOnClient(c->hit(gui(c),s,null).at(fraction));}
    private static void open(ClientGameTestContext ctx){ctx.runOnClient(c->{var g=new ClickGuiScreen();c.setScreen(g);g.getSearch().setText(fixture.getName());});ctx.waitTicks(16);}

    private static void rotationAndWalking(ClientGameTestContext ctx,TestSingleplayerContext world,Anchor a,Vec3d normal){
        eye(ctx,world,a.centre().add(normal.multiply(4)),a.centre());
        for(int key:new int[]{GLFW.GLFW_KEY_W,GLFW.GLFW_KEY_A,GLFW.GLFW_KEY_S,GLFW.GLFW_KEY_D}){
            var before=ctx.computeOnClient(c->c.player.getEntityPos());ctx.getInput().holdKey(key);ctx.waitTicks(5);ctx.getInput().releaseKey(key);ctx.waitTicks(3);
            ctx.runOnClient(c->{require(c.player.getEntityPos().squaredDistanceTo(before)>.005,"Native WASD key did not move player: "+key);stationary(gui(c),a,"WASD "+key);});
        }
        eye(ctx,world,a.centre().add(normal.multiply(4)),a.centre());
        float yaw=ctx.computeOnClient(c->c.player.getYaw());
        for(int turn=0;turn<=360;turn+=45){final int angle=turn;ctx.runOnClient(c->{c.player.setYaw(yaw+angle);c.player.setPitch(0);});ctx.waitTicks(3);ctx.runOnClient(c->stationary(gui(c),a,"Full yaw rotation "+angle));}
        aim(ctx,a.centre());
        for(int pitch:new int[]{-65,65,-89,89}){
            ctx.runOnClient(c->c.player.setPitch(pitch));ctx.waitTicks(4);ctx.runOnClient(c->stationary(gui(c),a,"Vertical look "+pitch));
            var image=screenshot(ctx,(pitch<0?"looking-up-":"looking-down-")+Math.abs(pitch));
            if(Math.abs(pitch)==89)require(marks(image)==0,"Panel followed guaranteed offscreen vertical look "+pitch);
        }
        eye(ctx,world,a.centre().add(normal.multiply(4)),a.centre());
    }

    private static void viewpoints(ClientGameTestContext ctx,TestSingleplayerContext world,Anchor a,Vec3d normal){
        Vec3d right=a.x().normalize();eye(ctx,world,a.centre().add(normal.multiply(4)),a.centre());
        var front=screenshot(ctx,"front");markerProjection(ctx,front,"Front");int pixels=marks(front);require(pixels>80,"No native front markers");
        ctx.runOnClient(c->require(!gui(c).screenPose().backFacing(),"Initial side classified as back"));var start=ctx.computeOnClient(c->c.player.getEntityPos());
        ctx.getInput().holdKey(GLFW.GLFW_KEY_D);ctx.waitTicks(8);ctx.getInput().releaseKey(GLFW.GLFW_KEY_D);ctx.waitTicks(4);
        ctx.runOnClient(c->{require(c.player.getEntityPos().squaredDistanceTo(start)>.02,"Native walking did not move around plane");require(gui(c).getSearch().getText().equals(fixture.getName()),"Movement typed into search");stationary(gui(c),a,"Native walk");});
        ctx.runOnClient(c->{var p=(PhoneMouseProbe)c.mouse;c.mouse.setResolutionChanged();p.maro$cursor(c.getWindow().getHandle(),100,100);c.mouse.tick();float yaw=c.player.getYaw();p.maro$cursor(c.getWindow().getHandle(),140,95);c.mouse.tick();require(c.player.getYaw()!=yaw,"Captured mouse did not turn real player");});ctx.waitTicks(3);ctx.runOnClient(c->stationary(gui(c),a,"Native look"));
        for(int side:new int[]{-1,1}){
            eye(ctx,world,a.centre().add(normal.multiply(4)).add(right.multiply(side*3)),a.centre());ctx.runOnClient(c->stationary(gui(c),a,"Oblique "+side));
            markerProjection(ctx,screenshot(ctx,side<0?"left-oblique":"right-oblique"),"Oblique "+side);
            ctx.runOnClient(c->{var b=gui(c).mainWindowBounds();var p=gui(c).screenPose();var tl=p.project(b[0],b[1]);var tr=p.project(b[0]+b[2],b[1]);var bl=p.project(b[0],b[1]+b[3]);var br=p.project(b[0]+b[2],b[1]+b[3]);double l=Math.hypot(tl[0]-bl[0],tl[1]-bl[1]),r=Math.hypot(tr[0]-br[0],tr[1]-br[1]);require(Math.abs(l-r)/Math.max(l,r)>.06,"Oblique plane stayed billboard-flat");});
        }
        eye(ctx,world,a.centre().add(normal.multiply(8)),a.centre());var far=screenshot(ctx,"far");markerProjection(ctx,far,"Far");require(marks(far)<pixels*.55&&marks(far)>10,"Distance compensated apparent plane size");ctx.runOnClient(c->stationary(gui(c),a,"Far"));
        eye(ctx,world,a.centre().add(right.multiply(6)).add(normal.multiply(.001)),a.centre());
        ctx.runOnClient(c->{stationary(gui(c),a,"Edge");require(!gui(c).screenPose().visible(),"Edge-on plane remained clickable");var p=gui(c).screenPose().unproject(gui(c).width/2d,gui(c).height/2d);require(!Double.isFinite(p[0])&&!Double.isFinite(p[1]),"Edge-on pick remained live");});require(marks(screenshot(ctx,"edge-on"))==0,"Edge-on plane rendered a facing billboard");
        eye(ctx,world,a.centre().subtract(normal.multiply(4)),a.centre());ctx.runOnClient(c->{stationary(gui(c),a,"Back");require(gui(c).screenPose().backFacing(),"Back not identified");});markerProjection(ctx,screenshot(ctx,"back-readable"),"Back");
        boolean on=fixture.isEnabled();click(ctx,ctx.computeOnClient(c->gui(c).modulePlace(fixture)),GLFW.GLFW_MOUSE_BUTTON_RIGHT);require(fixture.isEnabled()!=on,"Back-side crosshair click missed");click(ctx,ctx.computeOnClient(c->gui(c).modulePlace(fixture)),GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        eye(ctx,world,a.centre().add(normal.multiply(4)),a.centre());ctx.runOnClient(c->{c.player.setYaw(c.player.getYaw()+180);c.player.setPitch(0);});ctx.waitTicks(4);require(marks(screenshot(ctx,"looked-away"))==0,"Plane followed look-away");on=fixture.isEnabled();ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);ctx.waitTick();require(fixture.isEnabled()==on,"Look-away dispatched hidden click");ctx.runOnClient(c->stationary(gui(c),a,"Look-away"));eye(ctx,world,a.centre().add(normal.multiply(4)),a.centre());
    }
    private static void controls(ClientGameTestContext ctx,Anchor a){
        GAMEPLAY.clear();watchPackets=true;click(ctx,ctx.computeOnClient(c->gui(c).modulePlace(fixture)),GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(8);
        ctx.runOnClient(c->{require(gui(c).settingsModule()==fixture,"Settings not opened on plane");stationary(gui(c),a,"Settings");});boolean before=fixture.flag.get();var booleanPoint=control(ctx,fixture.flag,.75);aimProjected(ctx,booleanPoint);
        ctx.runOnClient(c->System.out.println("[stationary-input] booleanHit="+hit(gui(c),fixture.flag,null)));screenshot(ctx,"boolean-before-click");click(ctx,booleanPoint,GLFW.GLFW_MOUSE_BUTTON_LEFT);require(fixture.flag.get()!=before,"Plane boolean missed");
        aimProjected(ctx,control(ctx,fixture.amount,.82));ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(3);ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(2);require(fixture.amount.get()>7,"Plane slider missed");
        click(ctx,control(ctx,fixture.choice,.5),GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(4);require(Dropdown.isOpen(fixture.choice)&&dropdownOnSurface,"Dropdown not attached to menu canvas");ctx.runOnClient(c->stationary(gui(c),a,"Dropdown"));screenshot(ctx,"attached-dropdown");
        click(ctx,ctx.computeOnClient(c->hit(gui(c),fixture.choice,"Choice Four").at(.5)),GLFW.GLFW_MOUSE_BUTTON_LEFT);require(fixture.choice.is("Choice Four")&&!Dropdown.anyOpen(),"Projected dropdown choice missed");
        aimProjected(ctx,ctx.computeOnClient(c->hit(gui(c),fixture.flag,null).at(.12)));ctx.waitTicks(12);
        ctx.runOnClient(c->require(tooltipOnSurface&&fixture.flag.getDescription().equals(field(ClickGuiScreen.class,gui(c),"shownTooltip")),"Tooltip lost plane context/hover"));screenshot(ctx,"attached-tooltip");ctx.runOnClient(c->stationary(gui(c),a,"Tooltip"));require(GAMEPLAY.isEmpty(),"Controls emitted gameplay packets: "+GAMEPLAY);watchPackets=false;
    }
    private static void occlusion(ClientGameTestContext ctx,TestSingleplayerContext world,Anchor a,Vec3d normal){
        ctx.runOnClient(c->gui(c).closeSettings());ctx.waitTicks(8);eye(ctx,world,a.centre().add(normal.multiply(4)),a.centre());int clear=marks(screenshot(ctx,"clear-before-wall"));require(clear>80,"No visible occlusion markers");
        world.getServer().runCommand("fill -8 77 2 0 89 2 minecraft:red_concrete");ctx.waitTicks(10);int partial=marks(screenshot(ctx,"partly-occluded"));require(partial>0&&partial<clear*.85,"Terrain did not clip covered panel portion");
        world.getServer().runCommand("fill -8 77 2 8 89 2 minecraft:red_concrete");ctx.waitTicks(10);require(ScreenGuiSurface.depthReady(),"No terrain depth snapshot");require(marks(screenshot(ctx,"fully-occluded"))==0,"Panel rendered through foreground wall");
        var card=ctx.computeOnClient(c->gui(c).modulePlace(fixture));boolean on=fixture.isEnabled();aim(ctx,point(ctx,card));ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);ctx.waitTicks(2);require(fixture.isEnabled()==on,"Clicked module through wall");ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(2);ctx.runOnClient(c->{require(gui(c).settingsModule()==null,"Occluded click opened settings");stationary(gui(c),a,"Wall");});
        world.getServer().runCommand("fill -8 77 2 8 89 2 minecraft:air");ctx.waitTicks(10);click(ctx,card,GLFW.GLFW_MOUSE_BUTTON_RIGHT);require(fixture.isEnabled()!=on,"Removing wall did not restore picking");click(ctx,card,GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    }
    private static void previewDrag(ClientGameTestContext ctx,Anchor a){
        var esp=ModuleManager.get(PlayerESP.class);ctx.runOnClient(c->gui(c).openModuleOptions(esp));ctx.waitTicks(8);ctx.runOnClient(c->{require(gui(c).screenPose().active()&&gui(c).worldControls()&&gui(c).previewPanelVisible()&&gui(c).visualPreview().lastState()!=null,"Preview detached from world plane");stationary(gui(c),a,"Preview");});
        var b=ctx.computeOnClient(c->gui(c).visualPreview().contentBounds());aimProjected(ctx,new float[]{b[0]+b[2]/2,b[1]+b[3]/2});float angle=ctx.computeOnClient(c->gui(c).visualPreview().angle()),yaw=ctx.computeOnClient(c->c.player.getYaw()),pitch=ctx.computeOnClient(c->c.player.getPitch());var position=ctx.computeOnClient(c->c.player.getEntityPos());
        ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTick();ctx.runOnClient(c->{require(gui(c).screenInteractionDragging(),"Preview did not latch plane drag");var p=(PhoneMouseProbe)c.mouse;c.mouse.setResolutionChanged();p.maro$cursor(c.getWindow().getHandle(),100,100);c.mouse.tick();p.maro$cursor(c.getWindow().getHandle(),142,111);c.mouse.tick();});ctx.waitTicks(3);ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTick();
        ctx.runOnClient(c->{require(Math.abs(gui(c).visualPreview().angle()-angle)>5,"Relative mouse did not rotate model");require(c.player.getYaw()==yaw&&c.player.getPitch()==pitch&&c.player.getEntityPos().distanceTo(position)<.001,"Preview drag moved/looked real player");require(!gui(c).screenInteractionDragging(),"Drag retained after release");((PhoneMouseProbe)c.mouse).maro$scroll(c.getWindow().getHandle(),0,-1);});ctx.waitTicks(3);
        ctx.runOnClient(c->{require(gui(c).visualPreview().zoom()<.99&&c.player.getYaw()==yaw&&c.player.getPitch()==pitch,"Preview zoom missed/leaked camera");stationary(gui(c),a,"Preview drag/zoom");});screenshot(ctx,"attached-model-drag");
        ctx.runOnClient(c->{var p=(PhoneMouseProbe)c.mouse;p.maro$cursor(c.getWindow().getHandle(),160,111);c.mouse.tick();require(c.player.getYaw()!=yaw,"Released drag did not restore native look");});ctx.waitTicks(2);
    }
    private static void resize(ClientGameTestContext ctx,TestSingleplayerContext world,Anchor a,Vec3d normal){
        eye(ctx,world,a.centre().add(normal.multiply(6)),a.centre());
        for(int[] size:new int[][]{{1280,720},{960,640},{1600,900}}){ctx.getInput().resizeWindow(size[0],size[1]);for(int scale:new int[]{2,3,4}){
            ctx.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});ctx.waitTicks(7);ctx.runOnClient(c->{stationary(gui(c),a,size[0]+" GUI "+scale);require(gui(c).previewPanelVisible()&&gui(c).visualPreview().lastState()!=null,"Resize detached/collapsed preview");var p=gui(c).screenPose();for(float[] m:markers(gui(c).mainWindowBounds())){var screen=p.project(m[0],m[1]);var picked=p.unproject(screen[0],screen[1]);require(Math.abs(picked[0]-m[0])<.08&&Math.abs(picked[1]-m[1])<.08,"Resize/GUI scale picking drift");}});
        }markerProjection(ctx,screenshot(ctx,"resize-"+size[0]+"x"+size[1]),"Resize "+size[0]);}
    }
    private static void hiddenPreview(ClientGameTestContext ctx,Anchor a){
        click(ctx,ctx.computeOnClient(c->gui(c).visualPreview().actionPlace("PLAY")),GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(3);
        require(ctx.computeOnClient(c->gui(c).visualPreview().playing()),"Preview clock did not start for hidden-work check");
        aim(ctx,a.centre());ctx.runOnClient(c->c.player.setYaw(c.player.getYaw()+180));ctx.waitTicks(4);
        long frames=ctx.computeOnClient(c->gui(c).visualPreview().workFrames());double time=ctx.computeOnClient(c->gui(c).visualPreview().playbackTime());
        float angle=ctx.computeOnClient(c->gui(c).visualPreview().angle()),zoom=ctx.computeOnClient(c->gui(c).visualPreview().zoom());ctx.waitTicks(8);
        ctx.runOnClient(c->{var p=gui(c).visualPreview();require(p.workFrames()==frames&&p.playbackTime()==time&&p.angle()==angle&&p.zoom()==zoom,
                "Offscreen anchored preview kept rendering/ticking or reset its presentation");stationary(gui(c),a,"Hidden preview");});
        aim(ctx,a.centre());ctx.waitTicks(4);ctx.runOnClient(c->{var p=gui(c).visualPreview();require(p.workFrames()>frames&&p.playbackTime()>time&&p.angle()==angle&&p.zoom()==zoom,
                "Looking back did not resume the retained preview presentation");});
        click(ctx,ctx.computeOnClient(c->gui(c).visualPreview().actionPlace("PLAY")),GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void reopenAndLarge(ClientGameTestContext ctx,TestSingleplayerContext world,Anchor old,Vec3d normal){
        ctx.runOnClient(c->c.setScreen(null));ctx.waitTicks(3);require(!ScreenGuiSurface.hasResources(),"Close retained GPU resources");eye(ctx,world,old.centre().add(normal.multiply(4)).add(old.x().normalize().multiply(2)),old.centre().add(old.x()));open(ctx);var fresh=ctx.computeOnClient(c->anchor(gui(c).screenPose()));require(fresh.centre().distanceTo(old.centre())>.5&&fresh.x().normalize().dotProduct(old.x().normalize())<.98,"Reopen reused old anchor/orientation");
        ctx.runOnClient(c->c.setScreen(null));world.getServer().runCommand("tp @a 20000000 90 -20000000 0 0");hover(ctx,world);ctx.waitTicks(18);world.getClientWorld().waitForChunksRender();open(ctx);var large=ctx.computeOnClient(c->anchor(gui(c).screenPose()));require(Math.abs(large.centre().x)>19_000_000,"Large coordinate fixture failed");markerProjection(ctx,screenshot(ctx,"large-world-coordinates"),"Large coordinates");
        boolean on=fixture.isEnabled();click(ctx,ctx.computeOnClient(c->gui(c).modulePlace(fixture)),GLFW.GLFW_MOUSE_BUTTON_RIGHT);require(fixture.isEnabled()!=on,"Large-coordinate native click missed");click(ctx,ctx.computeOnClient(c->gui(c).modulePlace(fixture)),GLFW.GLFW_MOUSE_BUTTON_RIGHT);var eye=ctx.computeOnClient(c->c.player.getEyePos());eye(ctx,world,eye.add(.37,.13,-.21),large.centre());ctx.runOnClient(c->stationary(gui(c),large,"Large-coordinate sub-block movement"));markerProjection(ctx,screenshot(ctx,"large-world-sub-block-motion"),"Large sub-block view");
    }
    private static void reload(ClientGameTestContext ctx,Anchor a){
        long depth=ctx.computeOnClient(c->ScreenGuiSurface.worldDepthFrames());int frames=ScreenGuiSurface.drawnFrames();
        var future=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>>();ctx.runOnClient(c->future.set(c.reloadResources()));
        for(int tick=0;tick<1000&&!ctx.computeOnClient(c->future.get().isDone()&&c.getOverlay()==null&&ScreenGuiSurface.depthReady()
                &&ScreenGuiSurface.worldDepthFrames()>=depth+3&&ScreenGuiSurface.drawnFrames()>=frames+3);tick++)ctx.waitTick();
        require(future.get().isDone(),"Reload did not complete with anchored GUI open");future.get().join();
        ctx.runOnClient(c->{require(c.getOverlay()==null&&ScreenGuiSurface.depthReady()&&ScreenGuiSurface.worldDepthFrames()>=depth+3
                &&ScreenGuiSurface.drawnFrames()>=frames+3,"Reload did not restore fresh terrain depth/menu frames");stationary(gui(c),a,"Resource reload");});
        markerProjection(ctx,screenshot(ctx,"resource-reloaded"),"Resource reload");
    }
    public static void run(ClientGameTestContext ctx,TestSingleplayerContext world){
        Map<Setting<?>,JsonElement> saved=new IdentityHashMap<>();Map<Module,Boolean> enabled=new IdentityHashMap<>();int scale=ctx.computeOnClient(c->c.options.getGuiScale().getValue()),fov=ctx.computeOnClient(c->c.options.getFov().getValue());Perspective view=ctx.computeOnClient(c->c.options.getPerspective());boolean flying=ctx.computeOnClient(c->c.player.getAbilities().flying),bob=ctx.computeOnClient(c->c.options.getBobView().getValue());
        boolean synchronousDebug=ctx.computeOnClient(c->org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS));
        ctx.runOnClient(c->org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS));
        ctx.runOnClient(c->{if(fixture==null){fixture=new Fixture();ModuleManager.register(fixture);}ClientSettings.ALL.forEach(g->g.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));for(Module m:ModuleManager.all()){enabled.put(m,m.isEnabled());m.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy()));m.setEnabled(false);}ClientSettings.screenGui.set(true);ClientSettings.spatialMenu.set(false);ClientSettings.screenDistance.set(4d);ClientSettings.guiSize.set(85d);ClientSettings.backgroundBlur.set(false);ClientSettings.backgroundDim.set(false);c.options.setPerspective(Perspective.FIRST_PERSON);c.options.getGuiScale().setValue(2);c.options.getFov().setValue(70);c.options.getBobView().setValue(false);c.onResolutionChanged();});
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");world.getServer().runCommand("fill -14 78 -8 14 78 18 minecraft:smooth_stone");world.getServer().runCommand("fill -8 77 2 8 89 2 minecraft:air");world.getServer().runCommand("fill -6 79 11 -5 85 12 minecraft:white_concrete");world.getServer().runCommand("fill 5 79 11 6 85 12 minecraft:blue_concrete");world.getServer().runCommand("tp @a 0 80 0 0 0");hover(ctx,world);ctx.waitTicks(8);ctx.getInput().resizeWindow(1600,900);open(ctx);running=true;ctx.waitTicks(5);
            var a=ctx.computeOnClient(c->anchor(gui(c).screenPose()));var eye=ctx.computeOnClient(c->c.player.getEyePos());var normal=a.x().crossProduct(a.y()).normalize();if(normal.dotProduct(eye.subtract(a.centre()))<0)normal=normal.multiply(-1);
            rotationAndWalking(ctx,world,a,normal);viewpoints(ctx,world,a,normal);controls(ctx,a);occlusion(ctx,world,a,normal);previewDrag(ctx,a);subjectPixels(ctx);hiddenPreview(ctx,a);resize(ctx,world,a,normal);reload(ctx,a);reopenAndLarge(ctx,world,a,normal);ctx.runOnClient(c->c.setScreen(null));ctx.waitTicks(3);require(!ScreenGuiSurface.hasResources(),"Final close retained plane resources");RenderFaultChecks.assertClean();
            System.out.println("[stationary-screen-gui] PASS: invariant world centre/basis/physical size; native walk/look; front/oblique/far/edge/back/away pixels; back-side/settings/dropdown/tooltip input; partial/full terrain occlusion and blocked clicks; attached model drag/zoom without real look; scales/resizes; fresh reopen and large-coordinate sub-block picking; cleanup");
        }finally{running=watchPackets=false;ctx.getInput().releaseKey(GLFW.GLFW_KEY_D);ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.runOnClient(c->{c.setScreen(null);Dropdown.close();saved.forEach(Setting::fromJson);enabled.forEach(Module::setEnabled);c.options.setPerspective(view);c.options.getFov().setValue(fov);c.options.getGuiScale().setValue(scale);c.options.getBobView().setValue(bob);c.onResolutionChanged();if(c.player!=null)c.player.getAbilities().flying=flying;});ctx.getInput().resizeWindow(1280,720);ctx.runOnClient(c->{if(!synchronousDebug)org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);});}
    }
}
