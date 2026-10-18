package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.*;
import dev.maro.render.esp.BaseDiscoveryLog;
import dev.maro.render.esp.BaseDiscoveryLog.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Box;
import net.minecraft.client.gui.screen.ChatScreen;
import java.nio.file.*;
import java.time.*;
import java.util.*;

final class BaseCounterChecks {
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static Setting<?> setting(BaseESP m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void bool(BaseESP m,String name,boolean value){((BooleanSetting)setting(m,name)).set(value);}
    private static void await(ClientGameTestContext c,java.util.function.BooleanSupplier done,int ticks,String why){
        for(int n=0;n<ticks;n+=5){if(c.computeOnClient(client->done.getAsBoolean()))return;c.waitTicks(5);}
        throw new AssertionError(why+c.computeOnClient(client->" totals="+ModuleManager.get(BaseESP.class).discoveryTotals()+" detections="+ModuleManager.get(BaseESP.class).detections()+" checked="+ModuleManager.get(BaseESP.class).checkedChunks()));
    }
    private static void ledger() throws Exception {
        Path dir=Files.createTempDirectory("maro-base-counter-check-");
        Clock day=Clock.fixed(Instant.parse("2026-10-18T14:00:00Z"),ZoneId.of("America/Toronto"));
        Box a=new Box(-32,-44,-32,-16,-34,-16),partial=new Box(-25,-44,-32,-16,-34,-16),b=a.offset(100,0,0);
        var log=new BaseDiscoveryLog(dir,"server-a|overworld",day);
        log.observe(List.of(new Sighting(partial,16,Condition.LikelyIntact,true)));
        log.observe(List.of(new Sighting(a,32,Condition.LikelyIntact,true),new Sighting(b,32,Condition.LikelyIntact,true)));
        log.observe(List.of(new Sighting(a,32,Condition.LikelyIntact,true)));
        require(log.totals().today()==2,"Revisits or same templates miscounted");
        log.observe(List.of(new Sighting(partial,0,Condition.LikelyRaided,false)));
        require(log.condition(a,Condition.Uncertain)==Condition.LikelyIntact,"Incomplete chunk load marked a raid");
        log.observe(List.of(new Sighting(a,0,Condition.LikelyRaided,true)));
        require(log.totals().raided()==1&&log.totals().total()==2,"Raid changed unique count");
        log.adjust(1);log.adjust(-1);log.adjust(-1);
        require(log.totals().today()==1&&log.totals().automaticToday()==2&&log.totals().correction()==-1,"Manual corrections were not independent");
        log.observe(List.of(new Sighting(a,0,Condition.LikelyRaided,true)));
        require(log.totals().today()==1,"Rescan undid a manual correction");
        BaseDiscoveryLog.awaitWrites();
        var reloaded=new BaseDiscoveryLog(dir,"server-a|overworld",day);
        require(reloaded.totals().equals(log.totals()),"Disk reload lost discovery history");
        require(new BaseDiscoveryLog(dir,"server-b|overworld",day).totals().total()==0,"Servers shared counts");
        require(new BaseDiscoveryLog(dir,"server-a|the_nether",day).totals().total()==0,"Dimensions shared counts");
        var next=new BaseDiscoveryLog(dir,"server-a|overworld",Clock.offset(day,Duration.ofDays(1)));
        require(next.totals().today()==0&&next.totals().total()==1,"Day rollover lost lifetime or repeated yesterday");
        next.observe(List.of(new Sighting(a,0,Condition.LikelyRaided,true)));
        require(next.totals().today()==0,"Yesterday's base was rediscovered as new today");
        next.adjust(-1);require(next.totals().today()==0,"Counter went negative");
        var corrupt=new BaseDiscoveryLog(dir,"corrupt",day);Files.writeString(corrupt.file(),"broken original");
        corrupt=new BaseDiscoveryLog(dir,"corrupt",day);corrupt.adjust(1);BaseDiscoveryLog.awaitWrites();
        require(corrupt.saveFailed()&&Files.readString(corrupt.file()).equals("broken original"),"Corrupt original history was overwritten");
        System.out.println("[base-counter-ledger] PASS: duplicates, templates, partial views, raid loss, corrections, reload, scopes, midnight, corrupt-history preservation");
    }
    private static void shell(TestSingleplayerContext world,int x,int y,int z,String storage){
        world.getServer().runCommand("fill "+x+" "+y+" "+z+" "+(x+11)+" "+(y+6)+" "+(z+11)+" minecraft:deepslate_bricks");
        world.getServer().runCommand("fill "+(x+1)+" "+(y+1)+" "+(z+1)+" "+(x+10)+" "+(y+5)+" "+(z+10)+" minecraft:air");
        if(storage!=null)world.getServer().runCommand("fill "+(x+2)+" "+(y+1)+" "+(z+2)+" "+(x+5)+" "+(y+2)+" "+(z+5)+" minecraft:"+storage);
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        BaseESP m=ModuleManager.get(BaseESP.class);StorageESP storage=ModuleManager.get(StorageESP.class);BlockESP blocks=ModuleManager.get(BlockESP.class);
        require(m!=null&&storage!=null&&blocks!=null,"ESP modules missing");
        try {
            ledger();
            context.runOnClient(c->{c.options.getViewDistance().setValue(12);c.options.sendClientSettings();});
            world.getServer().runOnServer(s->s.getPlayerManager().setViewDistance(12));
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("forceload add -64 -80 143 111");
            context.waitTicks(80);
            world.getServer().runCommand("tp @a 64 -15 32 0 15");
            world.getServer().runOnServer(s->{var p=s.getPlayerManager().getPlayerList().getFirst();p.getAbilities().flying=true;p.sendAbilitiesUpdate();});
            for(int x=0;x<144;x+=36)world.getServer().runCommand("fill "+x+" -45 0 "+Math.min(x+35,143)+" -27 27 minecraft:air");
            shell(world,12,-40,12,"barrel");shell(world,124,-40,12,"barrel");
            shell(world,68,-40,12,null);
            world.getServer().runCommand("setblock 70 -39 14 minecraft:crafter");world.getServer().runCommand("setblock 72 -39 16 minecraft:crafter");
            world.getServer().runCommand("fill 69 -40 13 78 -40 22 minecraft:blue_concrete");
            shell(world,12,20,-60,"barrel");
            world.getServer().runCommand("fill -44 -39 14 -41 -38 17 minecraft:barrel");
            shell(world,12,-40,96,"chest");world.getServer().runCommand("setblock 18 -39 102 minecraft:trial_spawner");
            shell(world,68,-40,96,"chest");world.getServer().runCommand("setblock 74 -39 102 minecraft:sculk_shrieker");
            world.getServer().runCommand("setblock 75 -40 102 minecraft:reinforced_deepslate");
            world.getServer().runCommand("fill 125 -39 97 130 -37 102 minecraft:diamond_ore");
            world.getServer().runCommand("setblock 126 -36 99 minecraft:spawner");
            context.waitTicks(40);
            context.runOnClient(c->{
                c.player.getAbilities().flying=true;c.player.setVelocity(0,0,0);
                m.setEnabled(false);m.getSettings().forEach(Setting::reset);bool(m,"chat alerts",false);bool(m,"sound alerts",false);
                blocks.clearPicked();blocks.pick(Blocks.BLUE_CONCRETE);blocks.pick(Blocks.DIAMOND_ORE);blocks.pick(Blocks.TRIAL_SPAWNER);
                m.setEnabled(true);storage.setEnabled(true);blocks.setEnabled(true);
            });
            long start=System.nanoTime();
            await(context,()->m.discoveryTotals().total()==5,1200,"Expected four intact bases (including a bare storage cluster) and one raided shell; generated structures/ores must not count");
            context.runOnClient(c->{
                require(m.detections().size()==4,"Likely raided base was not hidden or natural structure escaped filter");
                require(m.discoveryTotals().raided()==1,"Raided site was not retained in counter");
                require(m.linkedEspChunks()>0&&m.linkedEspSources()==3,"Both Storage ESP and Block ESP findings were not linked");
                require(m.hudLeft()>c.getWindow().getScaledWidth()/2f&&m.hudTop()<20,"Counter did not default to top right");
                bool(m,"hide likely raided",false);
                bool(m,"nearby bases",true);
            });
            context.waitTicks(5);context.takeScreenshot("maro-base-counter-statuses");
            context.runOnClient(c->{require(m.detections().size()==5,"Showing raids lost or added detections");c.setScreen(new ChatScreen("",false));});
            context.waitTicks(3);
            context.runOnClient(c->{
                float x=m.hudLeft(),y=m.hudTop(),scale=m.hudScale();
                require(m.counterClick(x+206*scale,y+20*scale),"HUD plus did not accept pointer");require(m.discoveryTotals().today()==6,"HUD plus did not add");
                require(m.counterClick(x+180*scale,y+20*scale),"HUD minus did not accept pointer");require(m.discoveryTotals().today()==5,"HUD minus did not subtract");
            });
            for(int buttonX:new int[]{206,180}) {
                double[] point=context.computeOnClient(c->new double[]{(m.hudLeft()+buttonX*m.hudScale())*c.getWindow().getScaleFactor(),(m.hudTop()+20*m.hudScale())*c.getWindow().getScaleFactor()});
                context.getInput().setCursorPos(point[0],point[1]);context.getInput().holdMouse(0);context.waitTick();context.getInput().releaseMouse(0);context.waitTick();
                require(context.computeOnClient(c->m.discoveryTotals().today()==(buttonX==206?6:5)),"Real mouse + / - did not update the counter");
            }
            context.runOnClient(c->{
                float x=m.hudLeft(),y=m.hudTop(),scale=m.hudScale();
                c.setScreen(null);require(!m.counterClick(x+206*scale,y+20*scale),"In-game aiming altered counter");
                m.setEnabled(false);m.setEnabled(true);
            });
            await(context,()->m.detections().size()==5,800,"Re-enable did not resume scanning");
            require(context.computeOnClient(c->m.discoveryTotals().total()==5),"Re-enable double-counted bases");
            // Remove every storage block from the first discovered site. Its remembered shell should
            // now be likely raided; empty-looking chests alone are never treated as proof of a raid.
            world.getServer().runCommand("fill 14 -39 14 17 -38 17 minecraft:air");
            await(context,()->m.discoveryTotals().raided()==2,800,"Storage-loss reclassification failed");
            context.runOnClient(c->{bool(m,"hide likely raided",true);bool(m,"nearby bases",false);require(m.detections().size()==3,"Raided outlines were not hidden");require(m.discoveryTotals().total()==5,"Raid inflated discovery count");});
            context.waitTicks(5);context.takeScreenshot("maro-base-counter-top-right");
            // Use actual vanilla templates too. Storage is deliberately made dense enough to
            // qualify without the local natural-structure veto.
            world.getServer().runCommand("forceload remove -64 -80 143 111");
            world.getServer().runCommand("forceload add 240 0 432 96");
            world.getServer().runCommand("tp @a 336 -15 32 0 15");context.waitTicks(80);
            world.getServer().runCommand("place template minecraft:trial_chambers/chamber/assembly 256 -48 16");
            world.getServer().runCommand("fill 258 -46 18 261 -45 21 minecraft:chest");
            world.getServer().runCommand("place template minecraft:ancient_city/structures/barracks 384 -48 16");
            world.getServer().runCommand("fill 386 -46 18 389 -45 21 minecraft:chest");
            context.waitTicks(200);
            context.runOnClient(c->{require(m.detections().stream().noneMatch(d->d.bounds().minX>200),"Vanilla template was identified as a base: "+m.detections());require(m.discoveryTotals().total()==5&&m.discoveryTotals().raided()==2,"Vanilla template changed saved totals");});
            world.getServer().runCommand("tp @a 1000 -15 32 0 15");
            await(context,()->m.detections().isEmpty(),300,"Unloaded discoveries stayed rendered");
            context.waitTicks(40);
            require(context.computeOnClient(c->m.discoveryTotals().total()==5&&m.discoveryTotals().raided()==2),"Chunk unload changed saved total or falsely marked a raid");
            BaseDiscoveryLog.awaitWrites();
            System.out.printf(Locale.ROOT,"[base-counter-native] PASS: 5 unique sites, 2 raid estimates, both-height scans, shared ESP evidence, vanilla trial/city templates and marker/ore rejection, chat +/-, re-enable, chunk unload, persistent total; %.2fs%n",(System.nanoTime()-start)/1e9);
        } catch(Exception e){throw new AssertionError(e);}
        finally {context.runOnClient(c->{c.setScreen(null);m.setEnabled(false);storage.setEnabled(false);blocks.setEnabled(false);blocks.resetBlocks();m.getSettings().forEach(Setting::reset);});}
    }
}
