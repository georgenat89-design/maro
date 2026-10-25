package dev.maro.gametest;

import com.google.gson.*;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.config.ModuleFavorites;
import dev.maro.gametest.mixin.PhoneMouseProbe;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hud.HandPlacementScreen;
import dev.maro.gui.render.HandPlacementRender;
import dev.maro.gui.widget.Dropdown;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.TextField;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.Streamproof;
import dev.maro.module.impl.visuals.ViewModel;
import dev.maro.render.capture.CleanCapture;
import dev.maro.render.capture.StreamproofScenePolicy;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** Real GUI controls and native first-person hand pixels; no extra world render or inventory stand-in. */
public final class RedesignHandPlacementChecks {
    private static final String PROFILE_A="redesign-native-a",PROFILE_B="redesign-native-b",RESTART_PROFILE="redesign-hands-restart";
    private static final Path RECEIPT=ConfigManager.DIR.resolve("hand-placement-restart-proof.json");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> VIOLATIONS=new CopyOnWriteArrayList<>();
    private static volatile boolean watching,handProbe;
    private static volatile float maxSwing,maxEquip;
    private static volatile long handFrames;
    private static final Map<Arm,ItemStack> renderedItems=new EnumMap<>(Arm.class);
    private static Snapshot anchor;
    private static Fixture fixture;
    private record Snapshot(Vec3d position,float yaw,float pitch,int slot,List<ItemStack> inventory,Set<Integer> entities,
                            Vec3d camera,float cameraYaw,float cameraPitch,Perspective perspective,int fov){}
    private record Rect(double x,double y,double width,double height){
        float[] at(double fraction){return new float[]{(float)(x+width*fraction),(float)(y+height/2)};}
    }
    private record FileState(Path path,byte[] bytes){
        static FileState capture(Path path){try{return new FileState(path,Files.exists(path)?Files.readAllBytes(path):null);}catch(Exception e){throw new AssertionError(e);}}
        void restore(){try{if(bytes==null)Files.deleteIfExists(path);else{Files.createDirectories(path.getParent());Files.write(path,bytes);}}catch(Exception e){throw new AssertionError(e);}}
    }
    private static final class Fixture extends Module{
        final BooleanSetting flag=add(new BooleanSetting("Quiet mode","Native boolean fixture",false));
        final NumberSetting amount=add(new NumberSetting("Sensitivity","Native slider and numeric entry",.5,-2,2,.01));
        final ModeSetting mode=add(new ModeSetting("Style","Native dropdown fixture","Balanced","Balanced","Soft","Sharp","Minimal","Custom","Detailed"));
        final ColorSetting color=add(new ColorSetting("Tint","Native color picker fixture",0xFF9966CC,true));
        Fixture(){super("Interface Test","Native test fixture for the shared controls",Category.MISC);for(int i=1;i<=12;i++)add(new NumberSetting("Extra setting "+i,"Scroll fixture",i,0,20,1));}
    }
    private RedesignHandPlacementChecks(){}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static ClickGuiScreen gui(MinecraftClient c){require(c.currentScreen instanceof ClickGuiScreen,"Expected redesigned ClickGUI");return(ClickGuiScreen)c.currentScreen;}
    private static HandPlacementScreen editor(MinecraftClient c){require(c.currentScreen instanceof HandPlacementScreen,"Expected native hand-placement editor");return(HandPlacementScreen)c.currentScreen;}
    private static Object field(Class<?> owner,Object target,String name){try{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static SettingsList settingsList(ClickGuiScreen g){Object panel=field(ClickGuiScreen.class,g,"settings");return(SettingsList)field(panel.getClass(),panel,"list");}
    private static void settle(ClientGameTestContext c){c.waitTicks(6);}
    private static BufferedImage shot(ClientGameTestContext c,String name){try{return ImageIO.read(c.takeScreenshot("maro-redesign-"+name).toFile());}catch(Exception e){throw new AssertionError(e);}}
    private static void cursor(ClientGameTestContext ctx,double x,double y){ctx.runOnClient(c->{Screen s=c.currentScreen;require(s!=null,"Pointer has no screen");((PhoneMouseProbe)c.mouse).maro$cursor(c.getWindow().getHandle(),x*c.getWindow().getWidth()/s.width,y*c.getWindow().getHeight()/s.height);c.mouse.tick();});}
    private static float[] project(ClientGameTestContext ctx,float[] local){require(local!=null,"Missing GUI action center");return ctx.computeOnClient(c->{var p=gui(c).screenPose().project(local[0],local[1]);return new float[]{(float)p[0],(float)p[1]};});}
    private static void tap(ClientGameTestContext ctx,float[] point){require(point!=null,"Missing action center");cursor(ctx,point[0],point[1]);ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(2);}
    private static void guiTap(ClientGameTestContext ctx,float[] local){tap(ctx,project(ctx,local));}
    private static void action(ClientGameTestContext ctx,String id){tap(ctx,ctx.computeOnClient(c->editor(c).actionPlace(id)));}
    private static Rect hit(ClickGuiScreen g,Object captured,String choice){
        for(Object hit:(List<?>)field(ClickGuiScreen.class,g,"hits"))try{var hm=hit.getClass().getDeclaredMethod("handler");hm.setAccessible(true);Object handler=hm.invoke(hit);boolean object=false,option=choice==null;
            for(Field f:handler.getClass().getDeclaredFields()){f.setAccessible(true);Object v=f.get(handler);object|=v==captured;option|=choice!=null&&choice.equals(v);}if(!object||!option)continue;
            double[] r=new double[4];int i=0;for(String n:List.of("x","y","w","h")){var m=hit.getClass().getDeclaredMethod(n);m.setAccessible(true);r[i++]=((Number)m.invoke(hit)).doubleValue();}return new Rect(r[0],r[1],r[2],r[3]);
        }catch(Exception e){throw new AssertionError(e);}throw new AssertionError("No visible native control for "+captured+" / "+choice);
    }
    private static void control(ClientGameTestContext ctx,Setting<?> setting,double fraction){guiTap(ctx,ctx.computeOnClient(c->hit(gui(c),setting,null).at(fraction)));}
    private static void selectAll(ClientGameTestContext ctx){
        // Fabric TestInput sends modifiers=0 even while Control is held. Exercise the same native
        // Keyboard callback with the real modifier bits so replacement, rather than append, is tested.
        ctx.getInput().holdControl();ctx.runOnClient(c->{var keyboard=(dev.maro.gametest.mixin.HandPlacementKeyboardProbe)c.keyboard;var key=new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL);keyboard.maro$key(c.getWindow().getHandle(),GLFW.GLFW_PRESS,key);keyboard.maro$key(c.getWindow().getHandle(),GLFW.GLFW_RELEASE,key);});ctx.getInput().releaseControl();
    }
    private static void search(ClientGameTestContext ctx,String text){guiTap(ctx,ctx.computeOnClient(c->gui(c).placeOf(gui(c).getSearch())));selectAll(ctx);ctx.getInput().typeChars(text);settle(ctx);}
    private static void openGui(ClientGameTestContext ctx,Module module){ctx.runOnClient(c->{if(!(c.currentScreen instanceof ClickGuiScreen))c.setScreen(new ClickGuiScreen());gui(c).openModuleOptions(module);});settle(ctx);}
    private static JsonObject settings(Module m){var out=new JsonObject();m.getSettings().forEach(s->out.add(s.getName(),s.toJson().deepCopy()));out.add("extra",m.saveExtra().deepCopy());return out;}
    private static Snapshot snapshot(MinecraftClient c){var items=new ArrayList<ItemStack>();for(int i=0;i<c.player.getInventory().size();i++)items.add(c.player.getInventory().getStack(i).copy());var ids=new HashSet<Integer>();for(var e:c.world.getEntities())ids.add(e.getId());var camera=c.gameRenderer.getCamera();return new Snapshot(c.player.getEntityPos(),c.player.getYaw(),c.player.getPitch(),c.player.getInventory().getSelectedSlot(),List.copyOf(items),Set.copyOf(ids),camera.getCameraPos(),camera.getYaw(),camera.getPitch(),c.options.getPerspective(),c.options.getFov().getValue());}
    private static void isolated(MinecraftClient c,String phase){
        require(c.player.getEntityPos().distanceTo(anchor.position())<.001&&c.player.getYaw()==anchor.yaw()&&c.player.getPitch()==anchor.pitch(),phase+" moved/looked the real player");
        require(c.player.getInventory().getSelectedSlot()==anchor.slot(),phase+" changed hotbar selection");for(int i=0;i<anchor.inventory().size();i++){var a=anchor.inventory().get(i);var b=c.player.getInventory().getStack(i);require(a.getCount()==b.getCount()&&ItemStack.areItemsAndComponentsEqual(a,b),phase+" edited inventory slot "+i);}
        var ids=new HashSet<Integer>();for(var e:c.world.getEntities())ids.add(e.getId());require(ids.equals(anchor.entities()),phase+" changed live entities");var camera=c.gameRenderer.getCamera();require(camera.getCameraPos().distanceTo(anchor.camera())<.001&&camera.getYaw()==anchor.cameraYaw()&&camera.getPitch()==anchor.cameraPitch()&&c.options.getPerspective()==anchor.perspective()&&c.options.getFov().getValue()==anchor.fov(),phase+" changed real camera state");require(VIOLATIONS.isEmpty(),phase+" emitted gameplay/lifecycle work: "+VIOLATIONS);
    }
    public static void lifecycle(Module module,boolean on){if(watching)VIOLATIONS.add("Lifecycle "+module.getName()+" -> "+on);}
    public static void outbound(Packet<?> p){if(!watching)return;if(p instanceof PlayerMoveC2SPacket move){if(anchor!=null&&(Math.abs(move.getX(anchor.position().x)-anchor.position().x)>.001||Math.abs(move.getY(anchor.position().y)-anchor.position().y)>.001||Math.abs(move.getZ(anchor.position().z)-anchor.position().z)>.001||move.getYaw(anchor.yaw())!=anchor.yaw()||move.getPitch(anchor.pitch())!=anchor.pitch()))VIOLATIONS.add("Changed player movement");return;}if(p instanceof PlayerInteractBlockC2SPacket||p instanceof PlayerInteractItemC2SPacket||p instanceof PlayerInteractEntityC2SPacket||p instanceof PlayerActionC2SPacket||p instanceof UpdateSelectedSlotC2SPacket||p instanceof ClickSlotC2SPacket||p instanceof CreativeInventoryActionC2SPacket||p instanceof HandSwingC2SPacket||p instanceof ClientCommandC2SPacket)VIOLATIONS.add(p.getClass().getSimpleName());}
    public static void nativeHand(Arm arm,ItemStack stack,float swing,float equip){if(!handProbe)return;handFrames++;maxSwing=Math.max(maxSwing,swing);maxEquip=Math.max(maxEquip,equip);renderedItems.put(arm,stack.copy());}

    private static void layout(ClientGameTestContext ctx){ctx.runOnClient(c->{var g=gui(c);var b=g.mainWindowBounds();var s=g.settingsBounds();require(b!=null&&s!=null&&b[0]+b[2]<s[0]&&b[2]>s[2]&&b[2]<s[2]*1.3&&Math.abs(b[1]-s[1])<.1&&Math.abs(b[3]-s[3])<.1,"Redesign browser/settings proportions or alignment drifted");for(float[] r:new float[][]{b,s}){var a=g.screenPose().project(r[0],r[1]);var z=g.screenPose().project(r[0]+r[2],r[1]+r[3]);require(a[0]>=-1&&a[1]>=-1&&z[0]<=g.width+1&&z[1]<=g.height+1,"Redesign panel clipped viewport");}float y=-1,x=-1;int tabs=0;for(int i=0;i<g.entryCount();i++){var p=g.entryPlace(i);if(p==null)continue;if(y<0)y=p[1];require(Math.abs(p[1]-y)<.1&&p[0]>x,"Navigation is not a horizontal aligned row");x=p[0];tabs++;}require(tabs>=4&&g.profilePlace()!=null&&g.placeOf("close-menu")!=null,"Header/profile/navigation actions missing");require(ClientSettings.fontFace.is("Inter")&&!ClientSettings.capsText.get(),"Requested proportional sentence-case font presentation missing");});}
    private static void guiControls(ClientGameTestContext ctx){
        ctx.runOnClient(c->{c.setScreen(new ClickGuiScreen());gui(c).openPage(fixture.getCategory().ordinal());});ctx.waitTicks(12);layout(ctx);shot(ctx,"overview-reference");search(ctx,fixture.getName());
        guiTap(ctx,ctx.computeOnClient(c->gui(c).modulePlace(fixture)));require(ctx.computeOnClient(c->gui(c).settingsModule()==fixture&&!fixture.isEnabled()&&!ModuleFavorites.contains(fixture)),"Row selection changed enable/favorite state");
        guiTap(ctx,ctx.computeOnClient(c->gui(c).moduleTogglePlace(fixture)));require(fixture.isEnabled()&&!ModuleFavorites.contains(fixture),"Row toggle changed favorite or missed");guiTap(ctx,ctx.computeOnClient(c->gui(c).moduleTogglePlace(fixture)));
        guiTap(ctx,ctx.computeOnClient(c->gui(c).moduleFavoritePlace(fixture)));require(!fixture.isEnabled()&&ModuleFavorites.contains(fixture),"Favorite changed enabled state or missed");guiTap(ctx,ctx.computeOnClient(c->gui(c).moduleFavoritePlace(fixture)));
        control(ctx,fixture.flag,.4);require(fixture.flag.get(),"Boolean widget missed");
        float[] slider=ctx.computeOnClient(c->hit(gui(c),fixture.amount,null).at(.8));var p=project(ctx,slider);cursor(ctx,p[0],p[1]);ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(3);ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);require(fixture.amount.get()>.9,"Slider did not synchronize numeric value");
        TextField numeric=ctx.computeOnClient(c->settingsList(gui(c)).numericFields().get(fixture.amount));require(numeric!=null,"Numeric value field not constructed");guiTap(ctx,ctx.computeOnClient(c->hit(gui(c),numeric,null).at(.5)));selectAll(ctx);ctx.getInput().typeChars("1.37");ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);settle(ctx);require(Math.abs(fixture.amount.get()-1.37)<.011,"Native numeric entry did not synchronize slider");
        control(ctx,fixture.mode,.5);settle(ctx);require(Dropdown.isOpen(fixture.mode),"Mode dropdown did not open");guiTap(ctx,ctx.computeOnClient(c->hit(gui(c),fixture.mode,"Detailed").at(.5)));require(fixture.mode.is("Detailed")&&!Dropdown.anyOpen(),"Dropdown selection missed");
        int oldColor=fixture.color.get();control(ctx,fixture.color,.8);settle(ctx);var color=ctx.computeOnClient(c->settingsList(gui(c)).visiblePlaces().get(fixture.color));guiTap(ctx,new float[]{color[0]+color[2]*.45f,color[1]+22+2+26});settle(ctx);require(fixture.color.get()!=oldColor,"Color picker did not change swatch");shot(ctx,"settings-controls");
        guiTap(ctx,ctx.computeOnClient(c->gui(c).placeOf(fixture.getBind())));ctx.getInput().pressKey(GLFW.GLFW_KEY_K);require(fixture.getBind().get()==GLFW.GLFW_KEY_K&&!fixture.isEnabled(),"Keybind input missed or toggled module");
        var footer=ctx.computeOnClient(c->gui(c).moduleResetPlace());var browser=ctx.computeOnClient(c->gui(c).modulePlace(fixture));var body=ctx.computeOnClient(c->gui(c).settingsContentBounds());cursor(ctx,project(ctx,new float[]{body[0]+body[2]*.25f,body[1]+body[3]*.6f})[0],project(ctx,new float[]{body[0]+body[2]*.25f,body[1]+body[3]*.6f})[1]);ctx.runOnClient(c->((PhoneMouseProbe)c.mouse).maro$scroll(c.getWindow().getHandle(),0,-7));settle(ctx);
        ctx.runOnClient(c->{require(Arrays.equals(footer,gui(c).moduleResetPlace())&&Arrays.equals(browser,gui(c).modulePlace(fixture)),"Settings scroll moved footer/browser");});guiTap(ctx,footer);require(fixture.getSettings().stream().allMatch(s->Objects.equals(s.get(),s.getDefault()))&&fixture.getBind().get()==GLFW.GLFW_KEY_K&&!fixture.isEnabled(),"Reset failed or overwrote keybind/enable state");
        search(ctx,"zz-native-no-match-zz");require(ctx.computeOnClient(c->gui(c).modulePlace(fixture)==null&&gui(c).modulePlace(ModuleManager.get(ViewModel.class))==null),"Search empty state retained module rows");shot(ctx,"search-empty");search(ctx,"View Model");require(ctx.computeOnClient(c->gui(c).modulePlace(ModuleManager.get(ViewModel.class))!=null),"Search did not filter actual modules");
        ctx.runOnClient(c->{require(ConfigManager.save(PROFILE_A),"Could not save profile A");fixture.amount.set(-1d);require(ConfigManager.save(PROFILE_B),"Could not save profile B");});guiTap(ctx,ctx.computeOnClient(c->gui(c).profilePlace()));settle(ctx);guiTap(ctx,ctx.computeOnClient(c->gui(c).placeOf("profile:"+PROFILE_A)));require(ConfigManager.getCurrent().equals(PROFILE_A)&&Math.abs(fixture.amount.get()-.5)<.001,"Native profile selector did not load settings");shot(ctx,"profile-and-navigation");
    }

    private static void openEditor(ClientGameTestContext ctx){
        var view=ModuleManager.get(ViewModel.class);openGui(ctx,view);ctx.runOnClient(c->gui(c).selectPreviewTab(false));settle(ctx);
        var parent=ctx.computeOnClient(c->c.currentScreen);guiTap(ctx,ctx.computeOnClient(c->gui(c).placeOf(view.handPlacementAction())));settle(ctx);
        ctx.runOnClient(c->{require(editor(c).module()==view&&editor(c).selectedArm()==Arm.RIGHT&&!editor(c).linked(),"Editor entry/physical default state failed");require(field(HandPlacementScreen.class,editor(c),"parent")==parent,"Editor lost its settings parent");});
    }
    private static void beginIsolation(ClientGameTestContext ctx){ctx.runOnClient(c->{anchor=snapshot(c);VIOLATIONS.clear();watching=true;});}
    private static void checkIsolation(ClientGameTestContext ctx,String phase){ctx.runOnClient(c->isolated(c,phase));}
    private static void drag(ClientGameTestContext ctx,Arm arm,double dx,double dy,boolean shift){
        float[] r=ctx.computeOnClient(c->editor(c).regionBounds(arm));double x=r[0]+r[2]*.5,y=r[1]+r[3]*.65;
        cursor(ctx,x,y);if(shift)ctx.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        cursor(ctx,x+dx,y+dy);ctx.waitTicks(2);ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);if(shift)ctx.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);settle(ctx);
    }
    private static void scroll(ClientGameTestContext ctx,double notches){var r=ctx.computeOnClient(c->editor(c).canvasBounds());cursor(ctx,r[0]+r[2]*.5,r[1]+r[3]*.65);ctx.runOnClient(c->((PhoneMouseProbe)c.mouse).maro$scroll(c.getWindow().getHandle(),0,notches));settle(ctx);}
    private static ViewModel.Placement draft(ClientGameTestContext ctx,Arm arm){return ctx.computeOnClient(c->editor(c).draft(arm));}
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-5;}
    private static void committed(ViewModel.Placement actual,ViewModel.Placement expected,String phase){require(Math.abs(actual.x()-expected.x())<.006&&Math.abs(actual.y()-expected.y())<.006&&Math.abs(actual.depth()-expected.depth())<.006&&Math.abs(actual.scale()-expected.scale())<.026&&Math.abs(actual.rotX()-expected.rotX())<.51&&Math.abs(actual.rotY()-expected.rotY())<.51&&Math.abs(actual.rotZ()-expected.rotZ())<.51,phase+" did not commit the displayed physical pose: "+actual+" / "+expected);}
    private static void equip(ClientGameTestContext ctx,TestSingleplayerContext world,ItemStack main,ItemStack off){
        watching=false;world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();for(int i=0;i<p.getInventory().size();i++)p.getInventory().setStack(i,ItemStack.EMPTY);p.getInventory().setSelectedSlot(0);p.getInventory().setStack(0,main.copy());p.equipStack(EquipmentSlot.OFFHAND,off.copy());p.currentScreenHandler.syncState();});settle(ctx);
        ctx.runOnClient(c->{require(ItemStack.areItemsAndComponentsEqual(c.player.getMainHandStack(),main)&&ItemStack.areItemsAndComponentsEqual(c.player.getOffHandStack(),off),"Native fixture item synchronization failed");});
    }
    private static void mainArm(ClientGameTestContext ctx,TestSingleplayerContext world,Arm arm){
        // Vanilla's option consumer is empty; leaving its options screen writes/sends client options.
        // Send through that same native path, then wait for the server-tracked physical arm.
        ctx.runOnClient(c->{c.options.getMainArm().setValue(arm);c.options.sendClientSettings();});
        for(int t=0;t<100&&!ctx.computeOnClient(c->c.player.getMainArm()==arm);t++)ctx.waitTick();
        require(ctx.computeOnClient(c->c.player.getMainArm()==arm)&&world.getServer().computeOnServer(s->s.getPlayerManager().getPlayerList().getFirst().getMainArm()==arm),"Vanilla options did not synchronize physical main arm "+arm);settle(ctx);
    }
    private static int difference(BufferedImage a,BufferedImage b,int x0,int y0,int x1,int y1){int n=0;for(int y=Math.max(0,y0);y<Math.min(a.getHeight(),y1);y++)for(int x=Math.max(0,x0);x<Math.min(a.getWidth(),x1);x++){int aa=a.getRGB(x,y),bb=b.getRGB(x,y);if(Math.abs((aa>>16&255)-(bb>>16&255))+Math.abs((aa>>8&255)-(bb>>8&255))+Math.abs((aa&255)-(bb&255))>35)n++;}return n;}
    private static void editorGestures(ClientGameTestContext ctx,TestSingleplayerContext world){
        var view=ModuleManager.get(ViewModel.class);equip(ctx,world,ItemStack.EMPTY,ItemStack.EMPTY);ctx.runOnClient(c->{view.commitPlacement(Arm.LEFT,ViewModel.Placement.defaults());view.commitPlacement(Arm.RIGHT,ViewModel.Placement.defaults());});openEditor(ctx);
        long left=HandPlacementRender.emptyArmDraws(Arm.LEFT),right=HandPlacementRender.emptyArmDraws(Arm.RIGHT);settle(ctx);
        require(HandPlacementRender.emptyArmDraws(Arm.LEFT)>left&&HandPlacementRender.emptyArmDraws(Arm.RIGHT)>right,"Empty native physical hands were not drawn");
        require(ctx.computeOnClient(c->Double.isFinite(HandPlacementRender.gripDepth(Arm.LEFT))&&Double.isFinite(HandPlacementRender.gripDepth(Arm.RIGHT))),"Native grip projection was not observed");
        var before=shot(ctx,"editor-empty-native-hands");double scale=ctx.computeOnClient(c->c.getWindow().getScaleFactor());var canvas=ctx.computeOnClient(c->editor(c).canvasBounds());int y0=(int)((canvas[1]+30)*scale),y1=(int)((canvas[1]+canvas[3]-3)*scale);
        ctx.runOnClient(c->{c.options.getGuiScale().setValue(4);c.onResolutionChanged();});settle(ctx);shot(ctx,"editor-early-scale4");ctx.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();});settle(ctx);
        beginIsolation(ctx);var savedL=view.placement(Arm.LEFT);var savedR=view.placement(Arm.RIGHT);var initialL=draft(ctx,Arm.LEFT);int h=ctx.computeOnClient(c->editor(c).height);
        drag(ctx,Arm.RIGHT,h*.06,h*.035,false);var moved=draft(ctx,Arm.RIGHT);require(moved.x()>0&&moved.y()<0&&draft(ctx,Arm.LEFT).equals(initialL),"Independent physical-right XY drag failed");
        var after=shot(ctx,"editor-right-drag");require(difference(before,after,before.getWidth()/2,y0,before.getWidth(),y1)>100,"Right XY gesture did not move actual hand pixels");checkIsolation(ctx,"Independent empty-hand drag");
        drag(ctx,Arm.LEFT,-h*.035,-h*.02,false);var leftAfter=shot(ctx,"editor-left-drag");require(difference(after,leftAfter,(int)(canvas[0]*scale+8),y0,before.getWidth()/2-8,y1)>100,"Left XY gesture advanced native counters without moving visible native hand pixels");scroll(ctx,2);var oldL=draft(ctx,Arm.LEFT);var oldR=draft(ctx,Arm.RIGHT);action(ctx,"LINK");require(draft(ctx,Arm.LEFT).equals(oldL)&&draft(ctx,Arm.RIGHT).equals(oldR),"Link toggle overwrote asymmetric hands");
        drag(ctx,Arm.RIGHT,h*.025,h*.015,false);var linkedL=draft(ctx,Arm.LEFT);var linkedR=draft(ctx,Arm.RIGHT);require(close(linkedR.x()-oldR.x(),-(linkedL.x()-oldL.x()))&&close(linkedR.y()-oldR.y(),linkedL.y()-oldL.y()),"Linked XY did not apply mirrored-X/equal-Y deltas");
        scroll(ctx,1);require(close(draft(ctx,Arm.LEFT).scale()-linkedL.scale(),.05)&&close(draft(ctx,Arm.RIGHT).scale()-linkedR.scale(),.05),"Linked scroll did not add the same size delta");oldL=draft(ctx,Arm.LEFT);oldR=draft(ctx,Arm.RIGHT);drag(ctx,Arm.RIGHT,0,-h*.03,true);require(close(draft(ctx,Arm.RIGHT).depth()-oldR.depth(),.045)&&close(draft(ctx,Arm.LEFT).depth()-oldL.depth(),.045)&&close(draft(ctx,Arm.RIGHT).x(),oldR.x())&&close(draft(ctx,Arm.RIGHT).y(),oldR.y()),"Shift depth changed XY or failed equal linked deltas");
        oldL=draft(ctx,Arm.LEFT);action(ctx,"RESET_SELECTED");require(draft(ctx,Arm.RIGHT).equals(ViewModel.Placement.defaults())&&draft(ctx,Arm.LEFT).equals(oldL),"Reset selected touched the other physical hand");action(ctx,"RESET_BOTH");require(draft(ctx,Arm.LEFT).equals(ViewModel.Placement.defaults())&&draft(ctx,Arm.RIGHT).equals(ViewModel.Placement.defaults()),"Draft reset both missed");require(view.placement(Arm.LEFT).equals(savedL)&&view.placement(Arm.RIGHT).equals(savedR),"Draft gestures/resets wrote saved settings");
        action(ctx,"LINK");drag(ctx,Arm.LEFT,-h*.03,-h*.015,false);scroll(ctx,2);drag(ctx,Arm.LEFT,0,-h*.02,true);drag(ctx,Arm.RIGHT,h*.04,h*.02,false);scroll(ctx,-2);var expectedL=draft(ctx,Arm.LEFT);var expectedR=draft(ctx,Arm.RIGHT);checkIsolation(ctx,"Linked gestures and draft resets");
        action(ctx,"SAVE");ctx.runOnClient(c->{require(c.currentScreen instanceof ClickGuiScreen&&gui(c).settingsModule()==view&&!gui(c).previewTabSelected(),"Save did not return to the same Viewmodel settings");require(!view.isEnabled()&&ViewModel.editor()==null&&!HandPlacementRender.hasResources(),"Save changed enabled state or left editor rendering active");committed(view.placement(Arm.LEFT),expectedL,"Left save");committed(view.placement(Arm.RIGHT),expectedR,"Right save");});watching=false;
        var committedL=view.placement(Arm.LEFT);var committedR=view.placement(Arm.RIGHT);openEditor(ctx);require(draft(ctx,Arm.LEFT).equals(committedL)&&draft(ctx,Arm.RIGHT).equals(committedR),"Reopen lost saved independent poses");beginIsolation(ctx);action(ctx,"RESET_BOTH");drag(ctx,Arm.RIGHT,h*.02,0,false);ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);settle(ctx);require(view.placement(Arm.LEFT).equals(committedL)&&view.placement(Arm.RIGHT).equals(committedR)&&!view.isEnabled(),"Cancel committed a draft or toggled Viewmodel");checkIsolation(ctx,"Cancel draft");watching=false;
        ctx.runOnClient(c->view.setEnabled(true));openEditor(ctx);beginIsolation(ctx);ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);settle(ctx);require(view.isEnabled(),"Editor cancelled an enabled Viewmodel");checkIsolation(ctx,"Enabled-module cancellation");watching=false;ctx.runOnClient(c->view.setEnabled(false));
    }

    private static void physicalItemsAndNormalInput(ClientGameTestContext ctx,TestSingleplayerContext world){
        var view=ModuleManager.get(ViewModel.class);equip(ctx,world,new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.TOTEM_OF_UNDYING));mainArm(ctx,world,Arm.RIGHT);openEditor(ctx);handProbe=true;renderedItems.clear();settle(ctx);
        require(renderedItems.get(Arm.RIGHT)!=null&&renderedItems.get(Arm.RIGHT).isOf(Items.DIAMOND_SWORD)&&renderedItems.get(Arm.LEFT)!=null&&renderedItems.get(Arm.LEFT).isOf(Items.TOTEM_OF_UNDYING),"Native main/offhand did not match physical right-handed mode");
        var l=view.placement(Arm.LEFT);var r=view.placement(Arm.RIGHT);var binding=view.saveExtra().deepCopy();mainArm(ctx,world,Arm.LEFT);renderedItems.clear();settle(ctx);
        require(renderedItems.get(Arm.LEFT)!=null&&renderedItems.get(Arm.LEFT).isOf(Items.DIAMOND_SWORD)&&renderedItems.get(Arm.RIGHT)!=null&&renderedItems.get(Arm.RIGHT).isOf(Items.TOTEM_OF_UNDYING),"Native left-handed mode did not swap actual item ownership");require(view.placement(Arm.LEFT).equals(l)&&view.placement(Arm.RIGHT).equals(r)&&view.saveExtra().equals(binding),"Minecraft main-arm option remapped saved physical placements");
        beginIsolation(ctx);var right=draft(ctx,Arm.RIGHT);drag(ctx,Arm.LEFT,20,-12,false);require(draft(ctx,Arm.RIGHT).equals(right),"Physical-left edit touched the right hand in left-handed mode");checkIsolation(ctx,"Left-handed item edit");shot(ctx,"editor-left-handed-items");watching=false;ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);ctx.runOnClient(c->c.setScreen(null));settle(ctx);
        long emptyL=HandPlacementRender.emptyArmDraws(Arm.LEFT),emptyR=HandPlacementRender.emptyArmDraws(Arm.RIGHT);maxSwing=maxEquip=0;handFrames=0;ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);ctx.waitTicks(3);require(maxSwing>0&&handFrames>0,"Normal native swing animation did not resume after editor exit");
        world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();p.getInventory().setStack(1,new ItemStack(Items.DIAMOND_PICKAXE));p.currentScreenHandler.syncState();});settle(ctx);maxEquip=0;ctx.getInput().pressKey(GLFW.GLFW_KEY_2);ctx.waitTicks(3);require(ctx.computeOnClient(c->c.player.getInventory().getSelectedSlot()==1)&&maxEquip>0,"Normal hotbar/equip animation did not resume after editor exit");
        ctx.waitTicks(8);require(HandPlacementRender.emptyArmDraws(Arm.LEFT)==emptyL&&HandPlacementRender.emptyArmDraws(Arm.RIGHT)==emptyR&&ViewModel.editor()==null&&!HandPlacementRender.hasResources(),"Empty-hand override or resources leaked after editor exit");
        ctx.getInput().pressKey(GLFW.GLFW_KEY_1);ctx.waitTicks(2);require(ctx.computeOnClient(c->c.player.getInventory().getSelectedSlot()==0),"Normal native hotbar return did not work");equip(ctx,world,new ItemStack(Items.SHIELD),ItemStack.EMPTY);ctx.waitTicks(10);var lowered=shot(ctx,"normal-shield-lowered");ctx.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);ctx.waitTicks(7);require(ctx.computeOnClient(c->c.player.isUsingItem()&&c.player.isBlocking()),"Normal native item use did not resume after editor exit");var raised=shot(ctx,"normal-shield-using");require(difference(lowered,raised,0,lowered.getHeight()/3,lowered.getWidth(),lowered.getHeight())>100,"Native use state changed without visible held-item animation");ctx.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);ctx.waitTicks(4);require(ctx.computeOnClient(c->!c.player.isUsingItem()),"Normal item-use release remained captured by editor");handProbe=false;mainArm(ctx,world,Arm.RIGHT);
    }

    private static void editorLifecycle(ClientGameTestContext ctx,TestSingleplayerContext world){
        equip(ctx,world,ItemStack.EMPTY,ItemStack.EMPTY);openEditor(ctx);double[] response=null;int[][] cases={{1600,900,2,50},{960,540,2,110},{1600,900,4,70}};
        for(int[] size:cases){ctx.getInput().resizeWindow(size[0],size[1]);ctx.runOnClient(c->{c.options.getGuiScale().setValue(size[2]);c.options.getFov().setValue(size[3]);c.onResolutionChanged();});settle(ctx);action(ctx,"RESET_BOTH");settle(ctx);int h=ctx.computeOnClient(c->editor(c).height);require(Double.isFinite(HandPlacementRender.gripDepth(Arm.RIGHT)),"Resize lost observed native hand projection");drag(ctx,Arm.RIGHT,h*.04,h*.025,false);var p=draft(ctx,Arm.RIGHT);double[] now={p.x(),p.y()};if(response==null)response=now;else require(Math.abs(now[0]-response[0])<.012&&Math.abs(now[1]-response[1])<.012,"Normalized drag sensitivity drifted across resolution/GUI scale/world FOV: "+Arrays.toString(now)+" / "+Arrays.toString(response));ctx.runOnClient(c->{var e=editor(c);for(String id:List.of("SAVE","LINK","RESET_BOTH","RESET_SELECTED")){var at=e.actionPlace(id);require(at!=null&&at[0]>=0&&at[0]<=e.width&&at[1]>=0&&at[1]<=e.height,"Editor action clipped at scale "+size[2]);}});shot(ctx,"editor-"+size[0]+"x"+size[1]+"-scale"+size[2]);}
        ctx.getInput().resizeWindow(1600,900);ctx.runOnClient(c->{c.options.getGuiScale().setValue(2);c.options.getFov().setValue(70);c.onResolutionChanged();});settle(ctx);action(ctx,"RESET_BOTH");scroll(ctx,100);require(close(draft(ctx,Arm.RIGHT).scale(),3),"Size upper clamp missed");scroll(ctx,-100);require(close(draft(ctx,Arm.RIGHT).scale(),.15),"Size lower clamp missed");int h=ctx.computeOnClient(c->editor(c).height);drag(ctx,Arm.RIGHT,0,-h*3,true);require(close(draft(ctx,Arm.RIGHT).depth(),.45),"Depth upper clamp missed");drag(ctx,Arm.RIGHT,0,h*3,true);require(close(draft(ctx,Arm.RIGHT).depth(),-1.6),"Depth lower clamp missed");action(ctx,"RESET_BOTH");
        var l=draft(ctx,Arm.LEFT);var r=draft(ctx,Arm.RIGHT);boolean full=ctx.computeOnClient(c->c.getWindow().isFullscreen());try{ctx.runOnClient(c->{c.getWindow().toggleFullscreen();c.onResolutionChanged();});settle(ctx);require(ctx.computeOnClient(c->c.getWindow().isFullscreen())!=full,"Native fullscreen transition did not occur");require(draft(ctx,Arm.LEFT).equals(l)&&draft(ctx,Arm.RIGHT).equals(r),"Fullscreen changed hand drafts");shot(ctx,"editor-fullscreen");}finally{ctx.runOnClient(c->{if(c.getWindow().isFullscreen()!=full)c.getWindow().toggleFullscreen();c.onResolutionChanged();});settle(ctx);}
        var reload=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();ctx.runOnClient(c->reload.set(c.reloadResources()));for(int t=0;t<800&&!reload.get().isDone();t++)ctx.waitTick();require(reload.get().isDone(),"Editor resource reload did not complete");reload.get().join();for(int t=0;t<160&&ctx.computeOnClient(c->c.getOverlay()!=null);t++)ctx.waitTick();settle(ctx);require(draft(ctx,Arm.LEFT).equals(l)&&draft(ctx,Arm.RIGHT).equals(r)&&Double.isFinite(HandPlacementRender.gripDepth(Arm.RIGHT)),"Resource reload lost drafts/native hand rendering");shot(ctx,"editor-after-resource-reload");ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);settle(ctx);
    }

    private static NativeImage gpu(ClientGameTestContext ctx,boolean clean){var result=new CompletableFuture<NativeImage>();ctx.runOnClient(c->ScreenshotRecorder.takeScreenshot(clean?CleanCapture.framebuffer():c.getFramebuffer(),result::complete));for(int t=0;t<160&&!result.isDone();t++)ctx.waitTick();require(result.isDone(),"GPU hand target readback timed out");return result.join();}
    private static void saveGpu(ClientGameTestContext ctx,NativeImage image,String name){ctx.runOnClient(c->{try{var path=c.runDirectory.toPath().resolve("screenshots/maro-redesign-"+name+".png");Files.createDirectories(path.getParent());image.writeTo(path);}catch(Exception e){throw new AssertionError(e);}});}
    private static int different(NativeImage a,NativeImage b,int top,int bottom){int n=0;for(int y=top;y<Math.min(a.getHeight(),bottom);y++)for(int x=0;x<a.getWidth();x++){int aa=a.getColorArgb(x,y),bb=b.getColorArgb(x,y);if(Math.abs((aa>>16&255)-(bb>>16&255))+Math.abs((aa>>8&255)-(bb>>8&255))+Math.abs((aa&255)-(bb&255))>45)n++;}return n;}
    private static void streamproofEditor(ClientGameTestContext ctx,TestSingleplayerContext world){
        equip(ctx,world,new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.TOTEM_OF_UNDYING));openEditor(ctx);var proof=ModuleManager.get(Streamproof.class);ctx.runOnClient(c->proof.setEnabled(true));
        try{for(int t=0;t<600&&!ctx.computeOnClient(c->c.getOverlay()==null&&StreamproofScenePolicy.ready()&&CleanCapture.protectedLayer()&&HandPlacementRender.privateFrames()>3);t++)ctx.waitTick();require(ctx.computeOnClient(c->c.getOverlay()==null&&StreamproofScenePolicy.ready()&&CleanCapture.protectedLayer()&&HandPlacementRender.privateFrames()>3),"Streamproof editor did not restore ordinary scene and private hands");action(ctx,"RESET_BOTH");settle(ctx);long frames=HandPlacementRender.privateFrames(),copies=HandPlacementRender.worldCopies();long[] worldBefore=ctx.computeOnClient(c->new long[]{CleanCapture.frames(),CleanCapture.worldRenders()});
            try(var privateA=gpu(ctx,false);var cleanA=gpu(ctx,true)){saveGpu(ctx,privateA,"editor-private-before");saveGpu(ctx,cleanA,"editor-clean-before");int height=ctx.computeOnClient(c->editor(c).height);drag(ctx,Arm.RIGHT,-height*.12,-height*.05,false);scroll(ctx,-7);
                try(var privateB=gpu(ctx,false);var cleanB=gpu(ctx,true)){saveGpu(ctx,privateB,"editor-private-after");saveGpu(ctx,cleanB,"editor-clean-after");require(privateB.getWidth()==cleanB.getWidth()&&privateB.getHeight()==cleanB.getHeight(),"Capture hand target dimensions stale");int top=(int)(privateB.getHeight()*.32),bottom=(int)(privateB.getHeight()*.82);int localChange=different(privateA,privateB,top,bottom),cleanChange=different(cleanA,cleanB,top,bottom);require(localChange>250&&cleanChange<Math.max(80,localChange/15),"Draft hand transforms missing locally or leaked into ordinary clean target: "+localChange+" / "+cleanChange);require(different(privateB,cleanB,0,(int)(privateB.getHeight()*.22))>1000,"Editor controls missing privately or present in clean target");}
            }require(HandPlacementRender.privateFrames()>frames&&HandPlacementRender.worldCopies()>copies,"Capture did not reuse one-world pre-hand snapshot/private hand path");ctx.runOnClient(c->{long cleanFrames=CleanCapture.frames()-worldBefore[0],worldFrames=CleanCapture.worldRenders()-worldBefore[1];require(cleanFrames>0&&Math.abs(cleanFrames-worldFrames)<=1,"Editor rendered extra worlds: "+worldFrames+" / "+cleanFrames);});
        }finally{ctx.runOnClient(c->proof.setEnabled(false));ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);settle(ctx);}require(!HandPlacementRender.hasResources(),"Capture editor resources survived close");
    }

    private static void verifyRestart(ClientGameTestContext ctx){
        try{var expected=JsonParser.parseString(Files.readString(RECEIPT)).getAsJsonObject();ctx.runOnClient(c->{var view=ModuleManager.get(ViewModel.class);require(ConfigManager.getCurrent().equals(RESTART_PROFILE),"Restart did not automatically load committed test profile");require(JSON.toJsonTree(view.placement(Arm.LEFT)).equals(expected.get("left"))&&JSON.toJsonTree(view.placement(Arm.RIGHT)).equals(expected.get("right")),"Restart changed saved physical placements");require(view.saveExtra().equals(expected.getAsJsonObject("extra"))&&view.isEnabled()==expected.get("enabled").getAsBoolean(),"Restart changed slot binding or module enabled state");});System.out.println("[redesign-hands-restart] PASS: fresh JVM startup loaded exact committed physical placements and binding before fixture setup");}catch(Exception e){throw new AssertionError(e);}
    }
    private static void writeRestart(ClientGameTestContext ctx){ctx.runOnClient(c->{var view=ModuleManager.get(ViewModel.class);require(ConfigManager.save(RESTART_PROFILE),"Could not save restart profile");ConfigManager.saveClient();c.options.write();var receipt=new JsonObject();receipt.add("left",JSON.toJsonTree(view.placement(Arm.LEFT)));receipt.add("right",JSON.toJsonTree(view.placement(Arm.RIGHT)));receipt.add("extra",view.saveExtra().deepCopy());receipt.addProperty("enabled",view.isEnabled());try{Files.writeString(RECEIPT,JSON.toJson(receipt));}catch(Exception e){throw new AssertionError(e);}});}
    public static void run(ClientGameTestContext ctx,TestSingleplayerContext world){
        String stage=System.getProperty("maro.gametest.handPlacementRestartStage","");if(stage.equals("read")){verifyRestart(ctx);return;}
        var saved=new IdentityHashMap<Setting<?>,JsonElement>();var enabled=new IdentityHashMap<Module,Boolean>();var extras=new IdentityHashMap<Module,JsonObject>();var files=new ArrayList<FileState>();String originalProfile=ConfigManager.getCurrent();for(Path path:List.of(ConfigManager.DIR.resolve("client.json"),ConfigManager.CONFIG_DIR.resolve(originalProfile+".json"),ConfigManager.CONFIG_DIR.resolve(PROFILE_A+".json"),ConfigManager.CONFIG_DIR.resolve(PROFILE_B+".json"),ConfigManager.CONFIG_DIR.resolve(RESTART_PROFILE+".json")))files.add(FileState.capture(path));
        Arm arm=ctx.computeOnClient(c->c.options.getMainArm().getValue());Perspective perspective=ctx.computeOnClient(c->c.options.getPerspective());int scale=ctx.computeOnClient(c->c.options.getGuiScale().getValue()),fov=ctx.computeOnClient(c->c.options.getFov().getValue());boolean bob=ctx.computeOnClient(c->c.options.getBobView().getValue());
        var inventory=new java.util.concurrent.atomic.AtomicReference<List<ItemStack>>();world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();var list=new ArrayList<ItemStack>();for(int i=0;i<p.getInventory().size();i++)list.add(p.getInventory().getStack(i).copy());inventory.set(list);});
        ctx.runOnClient(c->{if(fixture==null){fixture=new Fixture();ModuleManager.register(fixture);}ClientSettings.ALL.forEach(g->g.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy())));for(Module m:ModuleManager.all()){enabled.put(m,m.isEnabled());extras.put(m,m.saveExtra().deepCopy());m.getSettings().forEach(s->saved.put(s,s.toJson().deepCopy()));m.setEnabled(false);}fixture.getSettings().forEach(Setting::reset);if(ModuleFavorites.contains(fixture))ModuleFavorites.toggle(fixture);ClientSettings.screenGui.set(false);ClientSettings.spatialMenu.set(false);ClientSettings.guiSize.set(97d);ClientSettings.menuTextSize.set(100d);ClientSettings.customFont.set(true);ClientSettings.fontFace.set("Inter");ClientSettings.capsText.set(false);ClientSettings.ACCENT.getSettings().forEach(Setting::reset);ClientSettings.WINDOW.getSettings().forEach(Setting::reset);ClientSettings.guiBackgroundMode.set("Default");ClientSettings.backgroundBlur.set(false);ClientSettings.backgroundDim.set(false);c.options.setPerspective(Perspective.FIRST_PERSON);c.options.getGuiScale().setValue(2);c.options.getFov().setValue(70);c.options.getMainArm().setValue(Arm.RIGHT);c.options.getBobView().setValue(false);c.onResolutionChanged();});
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("gamerule minecraft:advance_time false");world.getServer().runCommand("weather clear");world.getServer().runCommand("gamerule minecraft:advance_weather false");world.getServer().runCommand("fill -12 70 6 12 90 6 minecraft:white_concrete");world.getServer().runCommand("tp @a 0 80 0 0 12");world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();p.getAbilities().allowFlying=true;p.getAbilities().flying=true;p.sendAbilitiesUpdate();});ctx.runOnClient(c->{c.player.getAbilities().flying=true;c.player.setVelocity(Vec3d.ZERO);c.inGameHud.getChatHud().clear(false);});ctx.getInput().resizeWindow(1600,900);settle(ctx);
            guiControls(ctx);
            editorGestures(ctx,world);
            physicalItemsAndNormalInput(ctx,world);
            editorLifecycle(ctx,world);
            streamproofEditor(ctx,world);
            ctx.runOnClient(c->{c.setScreen(new ClickGuiScreen());gui(c).openPage(Category.VISUALS.ordinal());gui(c).openModuleOptions(ModuleManager.get(ViewModel.class));gui(c).selectPreviewTab(false);});cursor(ctx,8,8);ctx.waitTicks(12);shot(ctx,"view-model-settings-reference");
            if(stage.equals("write"))writeRestart(ctx);
            System.out.println("[redesign-hands] PASS: redesigned native widgets/profiles, physical drafts, empty/item pixels, isolation, animations, scales/fullscreen/reload, and internal clean/private targets");
        }finally{watching=handProbe=false;anchor=null;ctx.runOnClient(c->{c.setScreen(null);ModuleManager.get(Streamproof.class).setEnabled(false);if(!stage.equals("write")){saved.forEach(Setting::fromJson);extras.forEach(Module::loadExtra);enabled.forEach(Module::setEnabled);ConfigManager.save(originalProfile);}if(ModuleFavorites.contains(fixture))ModuleFavorites.toggle(fixture);c.options.setPerspective(perspective);c.options.getMainArm().setValue(arm);c.options.getGuiScale().setValue(scale);c.options.getFov().setValue(fov);c.options.getBobView().setValue(bob);c.onResolutionChanged();});world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();for(int i=0;i<inventory.get().size();i++)p.getInventory().setStack(i,inventory.get().get(i).copy());p.currentScreenHandler.syncState();});if(!stage.equals("write"))files.forEach(FileState::restore);ctx.getInput().resizeWindow(1280,720);}
    }
}
