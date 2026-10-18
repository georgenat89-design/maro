package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.DrawContext;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/** Verify the actual post-process pixels and layer boundary with vanilla blur disabled. */
final class MenuBlurChecks {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void run(ClientGameTestContext context) {
        int vanilla=context.computeOnClient(c -> c.options.getMenuBackgroundBlurriness().getValue());
        int scale=context.computeOnClient(c -> c.options.getGuiScale().getValue());
        boolean blur=ClientSettings.backgroundBlur.get(), dim=ClientSettings.backgroundDim.get();
        boolean floating=ClientSettings.spatialMenu.get(),screenGui=ClientSettings.screenGui.get();

        try {
            context.runOnClient(c -> {
                c.options.getGuiScale().setValue(2);c.onResolutionChanged();
                c.options.getMenuBackgroundBlurriness().setValue(0);
                ClientSettings.backgroundDim.set(false);ClientSettings.spatialMenu.set(false);
                ClientSettings.screenGui.set(false);
                ClientSettings.backgroundBlur.set(false);c.setScreen(new Probe());
            });
            context.waitTicks(15);
            var off=read(context.takeScreenshot("maro-menu-blur-off"));
            context.runOnClient(c -> {
                require(c.options.getMenuBackgroundBlurrinessValue()==0,"Blur off changed vanilla radius");
                ClientSettings.backgroundBlur.set(true);
                require(c.options.getMenuBackgroundBlurrinessValue()==5,"Enabled blur has no fallback radius");
                require(c.options.getMenuBackgroundBlurriness().getValue()==0,"Toggle changed saved vanilla setting");
            });
            context.waitTicks(4);
            var on=read(context.takeScreenshot("maro-menu-blur-on"));
            double before=contrast(off,20,20,190,120),after=contrast(on,20,20,190,120);
            require(before>40 && after<before*.55,"Background pixels were not blurred: "+before+" -> "+after);
            require(contrast(on,20,610,190,690)>100,"Foreground controls were blurred");
            for(boolean spatial:new boolean[]{false}) {
                context.runOnClient(c -> {
                    ClientSettings.spatialMenu.set(spatial);ClientSettings.screenGui.set(!spatial);
                    c.setScreen(new Probe());
                });
                context.waitTicks(15);
                var shot=read(context.takeScreenshot("maro-menu-blur-"+(spatial?"spatial":"screen-gui")));
                require(contrast(shot,20,20,190,120)<before*.55,"Floating mode skipped background blur");
                require(contrast(shot,20,610,190,690)>100,"Floating mode blurred foreground");
            }
            context.runOnClient(c -> {
                ClientSettings.spatialMenu.set(false);ClientSettings.screenGui.set(false);
                c.setScreen(new ClickGuiScreen());((ClickGuiScreen)c.currentScreen).openPage(6);
                c.options.getMenuBackgroundBlurriness().setValue(8);
                require(c.options.getMenuBackgroundBlurrinessValue()==8,"Custom vanilla strength lost");
                c.options.getMenuBackgroundBlurriness().setValue(0);
            });
            context.waitTicks(15);context.takeScreenshot("maro-menu-settings-blur-on");
            context.runOnClient(c -> ClientSettings.backgroundBlur.set(false));
            context.waitTicks(4);context.takeScreenshot("maro-menu-settings-blur-off");
            context.setScreen(() -> null);
            context.runOnClient(c -> require(c.options.getMenuBackgroundBlurrinessValue()==0,"Blur override leaked outside Maro menu"));
            RenderFaultChecks.assertClean();
            System.out.println("[menu-blur] PASS: visible with vanilla radius zero, toggle off, sharp foreground, Window/Screen GUI, vanilla setting preserved");
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);ClientSettings.backgroundBlur.set(blur);ClientSettings.backgroundDim.set(dim);
                ClientSettings.spatialMenu.set(floating);ClientSettings.screenGui.set(screenGui);
                c.options.getMenuBackgroundBlurriness().setValue(vanilla);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();
            });
        }
    }
    private static BufferedImage read(java.nio.file.Path path){try{return ImageIO.read(path.toFile());}catch(Exception e){throw new AssertionError(e);}}
    private static double contrast(BufferedImage image,int x0,int y0,int x1,int y1) {
        double sum=0;int count=0;
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
            sum+=Math.abs((image.getRGB(x,y)&255)-(image.getRGB(x+2,y)&255));count++;
        }
        return sum/count;
    }
    private static final class Probe extends ClickGuiScreen {
        @Override public void render(DrawContext ctx,int mx,int my,float delta){
            checker(ctx,0,0,110,70);
            ctx.createNewRootLayer();
            super.render(ctx,mx,my,delta);
            ctx.createNewRootLayer();checker(ctx,0,height-60,110,60);
        }
        private void checker(DrawContext ctx,int left,int top,int w,int h){
            for(int y=0;y<h;y+=2)for(int x=0;x<w;x+=2)
                ctx.fill(left+x,top+y,left+x+2,top+y+2,((x+y)/2%2==0)?0xFFEEEEEE:0xFF111111);
        }
    }
}
