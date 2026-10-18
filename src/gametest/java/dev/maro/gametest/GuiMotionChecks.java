package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import java.util.ArrayList;

/** Drawn poses stay continuous and pickable during real open, switch and close frames. */
final class GuiMotionChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void run(ClientGameTestContext context){
        String style=ClientSettings.menuStyle.get();boolean spatial=ClientSettings.spatialMenu.get(),screen=ClientSettings.screenGui.get();
        try{
            for(String device:new String[]{"Panel","Panels"}){
                context.runOnClient(c->{ClientSettings.menuStyle.set(device);ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);c.setScreen(new ClickGuiScreen());});
                float previous=0;
                for(int i=0;i<8;i++){
                    context.waitTicks(1);float p=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).menuProgress());require(p>=previous&&p<=1,"Opening reversed/overshot");previous=p;
                    context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;var projected=g.screenPose().project(320,200);var picked=g.screenPose().unproject(projected[0],projected[1]);require(Double.isFinite(projected[0]+projected[1])&&Math.abs(picked[0]-320)<.01&&Math.abs(picked[1]-200)<.01,"Animated drawing and picking diverged");});
                }
                context.waitTicks(14);var path=new ArrayList<double[]>();
                context.runOnClient(c->{var g=(ClickGuiScreen)c.currentScreen;g.getSearch().setText("radar");path.add(g.screenPose().project(0,0));var at=g.layoutPlace(device.equals("Panel"));var p=g.screenPose().project(at[0],at[1]);g.mouseClicked(new Click(p[0],p[1],new MouseInput(0,0)),false);});
                previous=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).layoutProgress());
                require(previous<1,"Style fade skipped its intermediate frames");
                require(context.computeOnClient(c->ClientSettings.menuStyle.is(device.equals("Panel")?"Panels":"Panel")),"Footer did not switch layouts");
                for(int i=0;i<10;i++){context.waitTicks(1);path.add(context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).screenPose().project(0,0)));float p=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).layoutProgress());require(p>=previous&&p<=1,"Style fade reversed/overshot");previous=p;}
                context.waitTicks(10);var end=context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).screenPose().project(0,0));var start=path.getFirst();
                require(previous==1,"Style fade did not finish");
                for(var p:path)for(int axis=0;axis<2;axis++)require(Double.isFinite(p[axis])&&p[axis]>=Math.min(start[axis],end[axis])-.5&&p[axis]<=Math.max(start[axis],end[axis])+.5,"Style transition overshot/jittered");
                require(context.computeOnClient(c->((ClickGuiScreen)c.currentScreen).getSearch().getText().equals("radar")),"Animated switch lost search");
                context.takeScreenshot("maro-gui-motion-"+device.toLowerCase());context.runOnClient(c->((ClickGuiScreen)c.currentScreen).close());previous=1;
                for(int i=0;i<16&&context.computeOnClient(c->c.currentScreen instanceof ClickGuiScreen);i++){
                    context.waitTicks(1);float p=context.computeOnClient(c->c.currentScreen instanceof ClickGuiScreen g?g.menuProgress():0f);require(p<=previous+.001&&p>=0,"Closing reversed/overshot");previous=p;
                }
                require(context.computeOnClient(c->c.currentScreen==null),"Animated close left input captured");
            }
            System.out.println("[gui-motion] PASS: monotonic open/close and layout fade, finite matched drawing/picking without overshoot, preserved search and close/input cleanup");
        }finally{context.runOnClient(c->{c.setScreen(null);ClientSettings.menuStyle.set(style);ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(screen);});}
    }
}
