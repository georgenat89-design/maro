package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import javax.imageio.ImageIO;
import java.util.List;

/** Real production UI tour, grayscale coverage/clipping checks and native text editing. */
final class TypographyChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        int gui = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        context.runOnClient(c -> {
            require(ClientSettings.capsText.get() && ClientSettings.customFont.get(), "Existing profile did not adopt uppercase smooth text");
            require(Fonts.text("Mixed Case", false).getString().equals("MIXED CASE"), "Common font bypasses caps");
            Fonts.beginRaw();
            try { require(Fonts.text("Raw panel label", false).getString().equals("RAW PANEL LABEL"), "Panel scope bypasses caps"); }
            finally { Fonts.endRaw(); }
            require(Math.abs(Fonts.width("mixed case")-Fonts.width("MIXED CASE")) < .001, "Case measurement differs");
            require(Fonts.width("ABC ") > Fonts.width("ABC"), "Trailing space lost from cursor measurement");
            require(Fonts.trim("LONG LABEL", .1f, false, 1).isEmpty(), "Tiny text box overflows with ellipsis");
            for (String line : Fonts.wrap("VERYVERYLONGUNBROKENLABEL long words\nsecond line",40,false,1))
                require(Fonts.width(line) <= 40.1f,"Word wrapping overflows: " + line);
            ClientSettings.spatialMenu.set(false); ClientSettings.screenGui.set(false);
        });
        try {
            for (int scale : new int[]{2,3,4}) {
                context.runOnClient(c -> { c.options.getGuiScale().setValue(scale); c.onResolutionChanged(); c.setScreen(new Probe()); });
                context.waitTicks(4);
                var shot = context.takeScreenshot("maro-typography-scale-"+scale);
                if (scale==2) try {
                    var image=ImageIO.read(shot.toFile()); int gray=0, white=0, outside=0;
                    for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                        int rgb=image.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
                        if(y<270 && Math.abs(r-g)<3 && Math.abs(g-b)<3) { if(r>10&&r<240)gray++; if(r>=240)white++; }
                        if(y>=302&&y<332 && x>=142 && r>20)outside++;
                    }
                    require(gray>200&&white>200,"Antialiased text lost grayscale coverage: "+gray+"/"+white);
                    require(outside==0,"Text escaped native clipping: "+outside);
                } catch(java.io.IOException e){throw new AssertionError(e);}
            }
            context.runOnClient(c -> { c.options.getGuiScale().setValue(2); c.onResolutionChanged(); c.setScreen(new InputProbe()); });
            context.waitTicks(3);
            context.runOnClient(c -> {
                var screen=(InputProbe)c.currentScreen;
                require(screen.field.getText().equals("FileCase Abc"),"Display changed the stored editable value");
                int x=screen.field.getX()+4+(int)Math.ceil(Fonts.width("File",false,1));
                screen.field.mouseClicked(new Click(x+.25,screen.field.getY()+10,new MouseInput(0,0)),false);
                require(screen.field.getCursor()==4,"Native mouse caret missed smooth glyphs: "+screen.field.getCursor());
            });
            context.getInput().typeChars("Xy");
            context.runOnClient(c -> require(((InputProbe)c.currentScreen).field.getText().equals("FileXyCase Abc"),"Native editing changed input casing or cursor"));
            context.takeScreenshot("maro-typography-native-input");
            for(String layout:List.of("Window")) {
                context.runOnClient(c -> { c.setScreen(new ClickGuiScreen()); });
                context.waitTicks(12);
                context.takeScreenshot("maro-typography-"+layout.toLowerCase());
            }
            context.runOnClient(c -> { c.setScreen(new ClickGuiScreen()); });
            String[] pages={"combat","movement","player","visuals","misc","anti-cheat-off","settings","configs","theme","socials"};
            for(int i=0;i<pages.length;i++) {
                final int page=i; context.runOnClient(c -> ((ClickGuiScreen)c.currentScreen).openPage(page));
                context.waitTicks(7);context.takeScreenshot("maro-typography-page-"+pages[i]);
            }
            int modules=0;
            for(var module:ModuleManager.all()) {
                context.runOnClient(c -> {
                    if (!(c.currentScreen instanceof ClickGuiScreen)) c.setScreen(new ClickGuiScreen());
                    ((ClickGuiScreen)c.currentScreen).openModuleSettings(module);
                });
                context.waitTicks(7);
                context.takeScreenshot("maro-typography-settings-"+module.getName().replaceAll("[^a-zA-Z0-9]+","-").toLowerCase());
                modules++;
                for (var section : module.getSettingSections()) {
                    context.runOnClient(c -> {
                        if (!(c.currentScreen instanceof ClickGuiScreen)) c.setScreen(new ClickGuiScreen());
                        ((ClickGuiScreen)c.currentScreen).openModuleSettings(module, section.getTitle());
                    });
                    context.waitTicks(7);
                    context.takeScreenshot("maro-typography-section-" + module.getName().replaceAll("[^a-zA-Z0-9]+","-").toLowerCase()
                            + "-" + section.getTitle().replaceAll("[^a-zA-Z0-9]+","-").toLowerCase());
                }
            }
            context.setScreen(() -> null);
            System.out.println("[typography] Module editor tour complete: " + modules);
            if (!Boolean.getBoolean("maro.gametest.typographyTourOnly")) {
                runHud(context, world);
            } else ScreenGuiChecks.run(context);
            RenderFaultChecks.assertClean();
            System.out.println("[typography] PASS: uppercase defaults, AA coverage, nested clipping, input/caret, scales 2/3/4, the window, 10 pages, "+modules+" module editors, all setting sections and Screen GUI"
                    + (Boolean.getBoolean("maro.gametest.typographyTourOnly") ? " (resumed UI tour)" : ", HUD/phone/image/pack screens"));
        } finally {
            context.runOnClient(c -> {c.options.getGuiScale().setValue(gui);c.onResolutionChanged();c.setScreen(null);});
        }
    }
    /** Independently runnable HUD half, so a failure can resume without repeating every editor. */
    static void runHud(ClientGameTestContext context, TestSingleplayerContext world) {
        BetterLooksChecks.run(context);
        BetterTexturesChecks.run(context);
        KeyboardStrokesChecks.run(context);
        HudReadabilityChecks.run(context);
        SpotifyLyricsChecks.run(context);
        SpotifyVolumeChecks.run(context);
        SpotifyPhoneChecks.run(context);
        NametagsChecks.run(context);
        ArmorHudChecks.run(context,world);
        SkinAccessoriesChecks.run(context);
        CrosshairChecks.run(context);
        CrosshairPngChecks.run(context);
        SkyChecks.run(context,world);
        TotemChecks.run(context,world);
        TexturePacksChecks.run(context);
        ScreenGuiChecks.run(context);
        RenderFaultChecks.assertClean();
        System.out.println("[typography-huds] PASS: appearance, keyboard, HUD/phone, nametags, armor, cosmetics, crosshairs, sky, totems, packs and Screen GUI");
    }

    private static final class Probe extends Screen {
        Probe(){super(Text.literal("Smooth uppercase text"));}
        @Override public boolean shouldPause(){return false;}
        @Override public void renderBackground(DrawContext ctx,int x,int y,float d){ }
        @Override public void render(DrawContext ctx,int x,int y,float d){
            Render2D.setAlpha(1);Fonts.setMeasureScale(1);ctx.fill(0,0,width,height,0xFF000000);
            float y1=12;
            for(float size:new float[]{.65f,.85f,1,1.4f,2}) {
                Fonts.draw(ctx,"Maro client - clear smooth text 012345",12,y1,0xFFFFFFFF,false,size);y1+=22;
            }
            ctx.getMatrices().pushMatrix();ctx.getMatrices().translate(280,100);ctx.getMatrices().scale(.73f,.73f);
            Fonts.setMeasureScale(.73f);
            require(Math.abs(Fonts.width(ctx,"Measured text",false,1)-Fonts.width("Measured text",false,1))<.01,"Scaled text metrics differ");
            Fonts.draw(ctx,"scaled text",0,40,0xFFFFFFFF,false,1);ctx.getMatrices().popMatrix();Fonts.setMeasureScale(1);
            ctx.enableScissor(10,145,100,170);ctx.enableScissor(20,150,70,167);
            Fonts.draw(ctx,"clipping check long label",20,151,0xFFFFFFFF,false,1.4f);
            ctx.disableScissor();ctx.disableScissor();
        }
    }
    private static final class InputProbe extends Screen {
        TextFieldWidget field;
        InputProbe(){super(Text.literal("Native smooth input"));}
        @Override protected void init(){
            field=new TextFieldWidget(textRenderer,40,40,200,22,Text.literal("Value"));
            addDrawableChild(field);field.setText("FileCase Abc");setInitialFocus(field);
            addDrawableChild(ButtonWidget.builder(Text.literal("Native Button Label"), b -> {}).dimensions(40,76,200,20).build());
        }
        @Override public void render(DrawContext ctx,int x,int y,float d){ctx.fill(0,0,width,height,0xFF10121A);super.render(ctx,x,y,d);}
        @Override public boolean shouldPause(){return false;}
    }
}
