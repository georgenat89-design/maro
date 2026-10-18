package dev.maro.gametest;

import dev.maro.chunkpulse.*;
import dev.maro.module.impl.visuals.RedstoneBypass;
import dev.maro.render.esp.Renderer3D;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.*;

/** Positive render fixture independent of (and never overriding) the multiplayer detection gate. */
final class PulseRenderFixture {
    private static List<ClusterEngine.Cluster> active=List.of();
    private static final PulseConfig CONFIG=new PulseConfig();
    private static final TileTracker TILES=new TileTracker();
    private static final ClusterDiscovery DISCOVERY=new ClusterDiscovery(CONFIG);
    private static final java.lang.reflect.Constructor<Renderer3D> CONSTRUCTOR;
    private static final net.minecraft.client.render.VertexConsumerProvider.Immediate BUFFERS;
    static{
        try{
            CONSTRUCTOR=Renderer3D.class.getDeclaredConstructor(WorldRenderContext.class);CONSTRUCTOR.setAccessible(true);
            var field=Renderer3D.class.getDeclaredField("BUFFERS");field.setAccessible(true);BUFFERS=(net.minecraft.client.render.VertexConsumerProvider.Immediate)field.get(null);
        }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
        WorldRenderEvents.END_MAIN.register(context->{
            if(active.isEmpty())return;
            long now=System.currentTimeMillis();var p=net.minecraft.client.MinecraftClient.getInstance().player;DISCOVERY.tick(new SelfActivityFilter.Position(p.getX(),p.getY(),p.getZ()),now);TILES.update(active,now,CONFIG,c->true);
            try{PulseRenderer.world(CONSTRUCTOR.newInstance(context),TILES,CONFIG,net.minecraft.client.MinecraftClient.getInstance().player.getY(),now);}
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
            finally{BUFFERS.draw();}
        });
    }
    static void run(ClientGameTestContext context,RedstoneBypass module){
        int guiScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        int blur=context.computeOnClient(c->c.options.getMenuBackgroundBlurriness().getValue());
        try{
            context.runOnClient(c->{
                var engine=new ActivityEngine(CONFIG);long now=System.currentTimeMillis();
                c.options.getMenuBackgroundBlurriness().setValue(0);
                int bx=c.player.getChunkPos().x+1,bz=c.player.getChunkPos().z+1,y=-40;
                CONFIG.planeMode=PulseConfig.Plane.FIXED;CONFIG.planeY=40.02;CONFIG.digCue=false;CONFIG.activityHud=false;
                c.player.setPosition(c.player.getX(),60,c.player.getZ());c.player.getAbilities().flying=true;
                for(int tick=0;tick<400;tick++)for(int chunk=0;chunk<4;chunk++)for(int machine=0;machine<6;machine++)
                    engine.accept(new SignalEvent(now-79800+tick*200,(bx+chunk%2)*16+machine,y,(bz+chunk/2)*16+3,SignalEvent.Kind.CRAFTER,true,"fixture-crafter"),false);
                active=engine.clusters(now);if(active.isEmpty())throw new AssertionError("No render fixture");
                try{var field=RedstoneBypass.class.getDeclaredField("clusters");field.setAccessible(true);field.set(module,active);}
                catch(ReflectiveOperationException error){throw new AssertionError(error);}
                module.setEnabled(false);
                TILES.discovery(DISCOVERY);DISCOVERY.update(active,new SelfActivityFilter.Position(c.player.getX(),c.player.getY(),c.player.getZ()),now);
                var target=active.getFirst();var direction=new net.minecraft.util.math.Vec3d(target.x(),CONFIG.planeY,target.z()).subtract(c.player.getEyePos());
                c.player.setYaw((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));c.player.setPitch((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
                c.setScreen(new Screen(Text.literal("Synthetic ChunkPulse render fixture")){
                    @Override public void render(DrawContext draw,int mx,int my,float delta){if(CONFIG.activityHud)PulseRenderer.hud(draw,module,true,4);if(CONFIG.showLabels||CONFIG.showHistory)PulseRenderer.labels(draw,TILES,CONFIG);PulseRenderer.guidance(draw,DISCOVERY,CONFIG);}
                    @Override public boolean shouldPause(){return false;}
                });
                // Disabling correctly clears the runtime; the fixture supplies its own immutable display data.
                try{var field=RedstoneBypass.class.getDeclaredField("clusters");field.setAccessible(true);field.set(module,active);}
                catch(ReflectiveOperationException error){throw new AssertionError(error);}
                field(module,"discovery",DISCOVERY);field(module,"discoveryReady",true);
            });
            context.waitTicks(5);context.takeScreenshot("maro-chunkpulse-synthetic-overlay");
            context.runOnClient(c->{CONFIG.seeThrough=false;CONFIG.showLabels=true;});context.waitTicks(3);context.takeScreenshot("maro-chunkpulse-depth-tested-tiles");context.runOnClient(c->CONFIG.seeThrough=true);
            for(int scale:new int[]{3,4}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});
                context.waitTicks(3);context.takeScreenshot("maro-chunkpulse-synthetic-scale-"+scale);
            }
            context.runOnClient(c->{c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();var chunk=active.getFirst().chunks().getFirst();c.player.setPosition(chunk.x*16+8,60,chunk.z*16+8);c.player.setPitch(55);CONFIG.planeMode=PulseConfig.Plane.PLAYER;CONFIG.showLabels=false;});
            context.waitTicks(4);context.runOnClient(c->{var g=DISCOVERY.guidance(new SelfActivityFilter.Position(c.player.getX(),c.player.getY(),c.player.getZ()));if(g==null||g.mode()!=ClusterDiscovery.Mode.DIG_DOWN||Math.abs(g.depth()-100)>2)throw new AssertionError("Native DIG DOWN missing");});context.takeScreenshot("maro-chunkpulse-dig-down");
            context.runOnClient(c->c.player.setPosition(c.player.getX(),15,c.player.getZ()));context.waitTicks(3);context.runOnClient(c->{var g=DISCOVERY.guidance(new SelfActivityFilter.Position(c.player.getX(),c.player.getY(),c.player.getZ()));if(g==null||Math.abs(g.depth()-55)>2)throw new AssertionError("Native descending depth did not update");});context.takeScreenshot("maro-chunkpulse-dig-descending");
            context.runOnClient(c->c.player.setPosition(c.player.getX(),-20,c.player.getZ()));context.waitTicks(3);context.runOnClient(c->{if(DISCOVERY.guidance(new SelfActivityFilter.Position(c.player.getX(),c.player.getY(),c.player.getZ()))!=null||!TILES.visible(System.currentTimeMillis(),CONFIG).isEmpty())throw new AssertionError("Found cluster still signalled/rendered");});context.takeScreenshot("maro-chunkpulse-found-hidden");
            context.runOnClient(c->{CONFIG.showHistory=true;module.config().showHistory=true;});context.waitTicks(3);context.takeScreenshot("maro-chunkpulse-found-ghost");
            context.runOnClient(c->{CONFIG.revealRadius=1;DISCOVERY.mark(null,false,new SelfActivityFilter.Position(c.player.getX(),60,c.player.getZ()),System.currentTimeMillis());c.player.setPosition(c.player.getX(),-35,c.player.getZ());});context.waitTicks(3);context.runOnClient(c->{var g=DISCOVERY.guidance(new SelfActivityFilter.Position(c.player.getX(),c.player.getY(),c.player.getZ()));if(g==null||g.mode()!=ClusterDiscovery.Mode.ALIGN)throw new AssertionError("Native horizontal alignment missing");});context.takeScreenshot("maro-chunkpulse-tunnel-guidance");
            RenderFaultChecks.assertClean();
            System.out.println("[chunkpulse-render] PASS: flat native tiles, merged borders, see-through/depth-tested pipelines and populated HUD; production gate unchanged");
            System.out.println("[chunkpulse-dig-render] PASS: outside direction, GUI scales 2/3/4, DIG DOWN 100 -> 55 blocks, automatic revealed suppression, FOUND ghosts and tunnelling offset; production SP gate unchanged");
        }finally{context.runOnClient(c->{active=List.of();TILES.clear();c.setScreen(null);module.config().showHistory=false;module.finish();c.options.getMenuBackgroundBlurriness().setValue(blur);c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();});}
    }
    private static void field(RedstoneBypass module,String name,Object value){try{var field=RedstoneBypass.class.getDeclaredField(name);field.setAccessible(true);field.set(module,value);}catch(Exception e){throw new AssertionError(e);}}
}
