package dev.maro.gametest;

import dev.maro.chunkpulse.*;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.RedstoneBypass;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

final class ChunkPulseChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        PulseCoreChecks.run();
        var module=ModuleManager.get(RedstoneBypass.class);
        context.runOnClient(c->{
            var visible=module.getSettingSections().stream().flatMap(s->s.getSettings().stream()).filter(dev.maro.setting.Setting::isVisible).map(dev.maro.setting.Setting::getName).toList();
            require(visible.equals(List.of("Chunk Tiles","Render Distance","Dig Guidance","Dig Cue","Show History")),"Activity settings are not the five simple controls: "+visible);
            module.loadExtra(new com.google.gson.JsonObject());module.saveConfig();
            require(module.config().minY==-64&&module.config().maxY==0&&module.config().planeMode==PulseConfig.Plane.PLAYER&&module.config().seeThrough&&module.config().simpleTiles,"Strong underground/flat-tile preset missing");
            require(!module.config().activityHud&&!module.config().showLabels&&module.config().minimumTier.equals("MEDIUM"),"Simple preset still has clutter/weak-only tiles");
            require(module.saveExtra().has("simpleActivityVersion"),"Simple preset will remigrate every load");
            System.out.println("[chunkpulse-simple] PASS: five visible settings, strong preset migration, Y=-64..-1, flat neutral player-plane tile, medium-or-higher activity and quiet HUD");
            module.setEnabled(true);module.onTick();require(!module.accepting(),"Singleplayer unexpectedly active");
            PacketSniffer.receive(new BlockEventS2CPacket(new BlockPos(-1,-40,-17),Blocks.PISTON,0,0));
            require(module.engine().size()==0,"Gated packet changed the activity engine");
            var config=new PulseConfig();var events=new ArrayList<SignalEvent>();var revealed=new java.util.concurrent.atomic.AtomicInteger();
            var sniffer=new PacketSniffer(new PacketSniffer.Sink(){
                public void observe(SignalEvent event){events.add(event);}
                public PulseConfig config(){return config;}
                public boolean blockEntities(){return true;}
                public void revealedMachine(int x,int y,int z,long time){revealed.incrementAndGet();}
            });
            BlockPos pos=new BlockPos(-1,-40,-17);
            sniffer.read(new BlockEventS2CPacket(pos,Blocks.CHEST,1,1));require(events.isEmpty(),"Chest lids became machine evidence");
            sniffer.read(new BlockEventS2CPacket(pos,Blocks.PISTON,0,0));
            require(events.size()==1&&events.getFirst().kind()==SignalEvent.Kind.PISTON&&events.getFirst().exact()&&SignalEvent.chunkX(events.getFirst().chunk())==-1,"Piston attribution incorrect");
            sniffer.read(new WorldEventS2CPacket(1049,pos,0,false));require(events.getLast().kind()==SignalEvent.Kind.CRAFTER,"Crafter event mapping incorrect");
            sniffer.read(new WorldEventS2CPacket(1000,pos,0,false));require(events.getLast().kind()==SignalEvent.Kind.DISPENSER,"Dispenser event mapping incorrect");
            int before=events.size();sniffer.read(new BlockUpdateS2CPacket(pos,Blocks.REDSTONE_LAMP.getDefaultState()));
            sniffer.read(new BlockUpdateS2CPacket(pos,Blocks.REDSTONE_LAMP.getDefaultState()));
            require(events.size()==before+1&&events.getLast().kind()==SignalEvent.Kind.MACHINE_UPDATE,"First machine update was not weak/deduplicated");
            require(revealed.get()==2,"Unchanged real machine update was deduplicated out of reveal evidence");
            sniffer.read(new BlockUpdateS2CPacket(pos,Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.state.property.Properties.LIT,true)));
            require(events.size()==before+2&&events.getLast().kind()==SignalEvent.Kind.STATE,"Real LIT toggle missed");
            require(sniffer.packets==7,"Packet read count incorrect");
            int stateCount=events.size();
            sniffer.read(new BlockUpdateS2CPacket(pos,Blocks.HOPPER.getDefaultState()));
            sniffer.read(new BlockUpdateS2CPacket(pos,Blocks.HOPPER.getDefaultState().with(net.minecraft.state.property.Properties.ENABLED,false)));
            require(events.size()==stateCount+2&&events.getLast().kind()==SignalEvent.Kind.STATE,"Hopper enabled toggle missed");
            int signalCount=events.size();
            sniffer.read(new ParticleS2CPacket(net.minecraft.particle.ParticleTypes.SMOKE,false,false,-1,-40,-17,0,0,0,0,1));
            require(events.size()==signalCount+1&&events.getLast().kind()==SignalEvent.Kind.PARTICLE,"Smoke packet attribution missed");
            sniffer.read(new PlaySoundS2CPacket(net.minecraft.registry.entry.RegistryEntry.of(net.minecraft.sound.SoundEvents.BLOCK_PISTON_EXTEND),net.minecraft.sound.SoundCategory.BLOCKS,-1,-40,-17,1,1,0));
            require(events.getLast().kind()==SignalEvent.Kind.SOUND&&!events.getLast().exact(),"Sound packet precision wrong");
            signalCount=events.size();
            sniffer.read(new EntitySpawnS2CPacket(7001,UUID.randomUUID(),-1,-40,-17,0,0,net.minecraft.entity.EntityType.VILLAGER,0,net.minecraft.util.math.Vec3d.ZERO,0));
            sniffer.read(new EntitySpawnS2CPacket(7002,UUID.randomUUID(),-1,-40,-17,0,0,net.minecraft.entity.EntityType.PLAYER,0,net.minecraft.util.math.Vec3d.ZERO,0));
            require(events.size()==signalCount,"Villager/player became farm evidence");
            sniffer.read(new EntitySpawnS2CPacket(7003,UUID.randomUUID(),-1,-40,-17,0,0,net.minecraft.entity.EntityType.ITEM,0,net.minecraft.util.math.Vec3d.ZERO,0));
            require(events.size()==signalCount,"Unknown item type scored before metadata");
            sniffer.read(new EntityTrackerUpdateS2CPacket(7003,List.of(new net.minecraft.entity.data.DataTracker.SerializedEntry<>(8,net.minecraft.entity.data.TrackedDataHandlerRegistry.ITEM_STACK,new net.minecraft.item.ItemStack(net.minecraft.item.Items.SUGAR_CANE)))));
            require(events.getLast().kind()==SignalEvent.Kind.ENTITY,"Item output packet missed");
            require(events.getLast().source().equals("item:minecraft:sugar_cane"),"Item type missing from cadence attribution");
            sniffer.read(new EntitiesDestroyS2CPacket(7001,7002,7003));
            var handler=c.getNetworkHandler();handler.onBlockEvent(new BlockEventS2CPacket(c.player.getBlockPos(),Blocks.PISTON,0,0));
            require(module.engine().size()==0,"Native HEAD hook bypassed the singleplayer gate");
        });
        context.waitTicks(4);context.takeScreenshot("maro-chunkpulse-server-gated");
        var engine=new ActivityEngine(new PulseConfig());long now=System.currentTimeMillis();
        for(int i=0;i<400;i++)for(int chunk=0;chunk<4;chunk++)for(int machine=0;machine<6;machine++)engine.accept(new SignalEvent(now-79800+i*200,chunk*16+machine,-40,0,SignalEvent.Kind.CRAFTER,true,"crafter"),true);
        String server="fixture.donutsmp.net:25565",dimension="minecraft:test_"+UUID.randomUUID();
        Path saved=Persistence.file(server,dimension);Path csv=null,points=null;
        try{
            Persistence.save(server,dimension,engine.snapshots()).join();
            var restored=new ActivityEngine(new PulseConfig());restored.restore(Persistence.load(server,dimension,2048).join(),now);
            require(restored.size()==engine.size(),"Location file failed to roundtrip");
            require(Persistence.load(server,"minecraft:other_dimension",2048).join().isEmpty(),"Dimension data leaked");
            var clusters=restored.clusters(now);csv=Persistence.export(server,dimension,clusters,engine.debugEvents(),false).join();
            points=Persistence.export(server,dimension,clusters,List.of(),true).join();
            require(Files.readString(csv).contains("HUGE")&&Files.readString(csv).contains("fixture.donutsmp.net"),"CSV export missing confidence/server");
            require(Files.readString(points).contains("waypoint:Pulse"),"Waypoint export missing coordinates");
            var discovery=new ClusterDiscovery(new PulseConfig());var player=new SelfActivityFilter.Position(8,60,0);discovery.update(clusters,player,now);String found=discovery.mark(null,true,player,now+1);
            Persistence.save(server,dimension,engine.snapshots(),List.of(),discovery.snapshots(),Persistence.reserve(server,dimension)).join();
            var state=Persistence.loadState(server,dimension,2048).join();var again=new ClusterDiscovery(new PulseConfig());again.restore(state.discoveries());again.update(clusters,player,now+2);require(again.state(found)==ClusterDiscovery.State.REVEALED,"Found marker did not persist to disk/reconnect");
            Persistence.exportDiscovery(csv,discovery.snapshots(),discovery.transitions()).join();require(Files.readString(csv.resolveSibling(csv.getFileName().toString().replace(".csv","-clusters.json"))).contains("C4_MANUAL_FOUND"),"Discovery state/reason export missing");
            System.out.println("[chunkpulse-found-persistence] PASS: version 3 state JSON keeps FOUND across reconnect; discovery export retains manual reveal reason");
            long old=Persistence.reserve(server,dimension),latest=Persistence.reserve(server,dimension);
            Persistence.save(server,dimension,List.of(),latest).join();
            Persistence.save(server,dimension,engine.snapshots(),old).join();
            require(Persistence.load(server,dimension,2048).join().isEmpty(),"Delayed save resurrected explicitly cleared locations");
            PulseRenderFixture.run(context,module);
            System.out.println("[chunkpulse-native] PASS: production HEAD mixin, SP/server gate, native piston/crafter/dispenser packets, chest exclusion, changed-state dedup, server/dimension JSON isolation, CSV/waypoint/replay exports");
        }catch(java.io.IOException error){throw new AssertionError(error);}
        finally{
            context.runOnClient(c->module.setEnabled(false));
            try{Files.deleteIfExists(saved);if(csv!=null){Files.deleteIfExists(csv.resolveSibling(csv.getFileName().toString().replace(".csv","-replay.json")));Files.deleteIfExists(csv.resolveSibling(csv.getFileName().toString().replace(".csv","-clusters.json")));Files.deleteIfExists(csv);}if(points!=null)Files.deleteIfExists(points);}
            catch(java.io.IOException ignored){}
        }
    }
}
