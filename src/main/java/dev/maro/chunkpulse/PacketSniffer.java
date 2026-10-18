package dev.maro.chunkpulse;

import dev.maro.module.impl.visuals.RedstoneBypass;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Read-only packet attribution. No packet send/cancel/mutation, no chunk/block-palette reads. */
public final class PacketSniffer {
    public interface Sink { void observe(SignalEvent event); PulseConfig config(); boolean blockEntities(); default void revealedMachine(int x,int y,int z,long time){} }
    private record LightKey(int x,int y,int z) { }
    private record State(String block,int mask) { }
    private static final class Tracked {
        double x,y,z;final boolean mob,item;boolean emitted;
        Tracked(double x,double y,double z,boolean mob,boolean item){this.x=x;this.y=y;this.z=z;this.mob=mob;this.item=item;}
    }
    private final Sink owner;
    private final LinkedHashMap<Long,State> states=new LinkedHashMap<>(128,.75f,true);
    private final LinkedHashMap<LightKey,Integer> lights=new LinkedHashMap<>(128,.75f,true);
    private final LinkedHashMap<Integer,Tracked> entities=new LinkedHashMap<>(128,.75f,true);
    private final Map<Long,Integer> density=new HashMap<>();
    private final LinkedHashMap<Long,Long> densitySent=new LinkedHashMap<>(32,.75f,true);
    public long packets;
    public PacketSniffer(Sink owner){this.owner=owner;}
    public void clear(){states.clear();lights.clear();entities.clear();density.clear();densitySent.clear();packets=0;}
    public static void receive(Object packet){
        var module=RedstoneBypass.instance();
        // Vanilla re-invokes handlers on its main thread. Observing that invocation avoids double-counting.
        if(module!=null&&MinecraftClient.getInstance().isOnThread()&&module.accepting())module.sniffer().read(packet);
    }
    private void emit(double x,double y,double z,SignalEvent.Kind kind,boolean exact,String source){
        owner.observe(new SignalEvent(System.currentTimeMillis(),(int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z),kind,exact,source,y));
    }
    private void emit(BlockPos pos,SignalEvent.Kind kind,boolean exact,String source){emit(pos.getX(),pos.getY(),pos.getZ(),kind,exact,source);}
    public void read(Object packet){
        packets++;
        if(packet instanceof BlockEventS2CPacket p){
            var block=p.getBlock();String id=Registries.BLOCK.getId(block).getPath();
            if((block instanceof PistonBlock||block instanceof PistonExtensionBlock||block instanceof PistonHeadBlock)&&p.getType()>=0&&p.getType()<=2)emit(p.getPos(),SignalEvent.Kind.PISTON,true,id);
            else if(block instanceof NoteBlock)emit(p.getPos(),SignalEvent.Kind.NOTE,true,id);
            // Chest lids, bells and other ordinary block events intentionally contribute nothing.
        }else if(packet instanceof WorldEventS2CPacket p){
            switch(p.getEventId()){
                case 1000,1001,1002 -> emit(p.getPos(),SignalEvent.Kind.DISPENSER,true,"dispenser");
                case 1049,1050 -> emit(p.getPos(),SignalEvent.Kind.CRAFTER,true,"crafter");
                case 2000,2010 -> emit(p.getPos(),SignalEvent.Kind.PARTICLE,true,"dispense-particles");
                default -> { }
            }
        }else if(packet instanceof BlockUpdateS2CPacket p)state(p.getPos(),p.getState());
        else if(packet instanceof ChunkDeltaUpdateS2CPacket p)p.visitUpdates(this::state);
        else if(packet instanceof PlaySoundS2CPacket p)sound(p.getSound().value().id().getPath(),p.getX(),p.getY(),p.getZ());
        else if(packet instanceof PlaySoundFromEntityS2CPacket p){
            var entity=MinecraftClient.getInstance().world.getEntityById(p.getEntityId());
            if(entity!=null)sound(p.getSound().value().id().getPath(),entity.getX(),entity.getY(),entity.getZ());
        }else if(packet instanceof ParticleS2CPacket p){
            String id=Registries.PARTICLE_TYPE.getId(p.getParameters().getType()).getPath();
            if(id.contains("dust")||id.contains("smoke")||id.equals("flame")||id.contains("crafter"))emit(p.getX(),p.getY(),p.getZ(),SignalEvent.Kind.PARTICLE,false,id);
        }else if(packet instanceof LightUpdateS2CPacket p)light(p);
        else if(packet instanceof EntitySpawnS2CPacket p)spawn(p);
        else if(packet instanceof EntityTrackerUpdateS2CPacket p){
            var tracked=entities.get(p.id());
            if(tracked!=null&&tracked.item&&!tracked.emitted)for(var entry:p.trackedValues())if(entry.value() instanceof net.minecraft.item.ItemStack stack&&!stack.isEmpty()){
                tracked.emitted=true;emit(tracked.x,tracked.y,tracked.z,SignalEvent.Kind.ENTITY,false,"item:"+Registries.ITEM.getId(stack.getItem()));break;
            }
        }
        else if(packet instanceof EntitiesDestroyS2CPacket p){for(int id:p.getEntityIds())remove(id);}
        else if(packet instanceof EntityS2CPacket p){
            if(!p.isPositionChanged())return;
            var entity=p.getEntity(MinecraftClient.getInstance().world);if(entity==null)return;
            var tracked=entities.get(entity.getId());if(tracked!=null)move(entity.getId(),tracked.x+p.getDeltaX()/4096d,tracked.y+p.getDeltaY()/4096d,tracked.z+p.getDeltaZ()/4096d);
        }else if(packet instanceof EntityPositionSyncS2CPacket p){var v=p.values().position();move(p.id(),v.x,v.y,v.z);}
        else if(packet instanceof EntityPositionS2CPacket p){
            // Resolve relative flags with the same pure position function vanilla uses.
            var entity=MinecraftClient.getInstance().world.getEntityById(p.entityId());
            if(entity!=null){var resolved=EntityPosition.apply(EntityPosition.fromEntity(entity),p.change(),p.relatives()).position();move(p.entityId(),resolved.x,resolved.y,resolved.z);}
        }else if(packet instanceof BlockEntityUpdateS2CPacket p&&owner.blockEntities()){
            var type=p.getBlockEntityType();
            String id=Registries.BLOCK_ENTITY_TYPE.getId(type).getPath();
            if(machine(id))emit(p.getPos(),SignalEvent.Kind.BLOCK_ENTITY,true,id);
        }
    }
    private static boolean machine(String id){return id.contains("piston")||id.equals("crafter")||id.equals("dispenser")||id.equals("dropper")||id.equals("observer")||id.equals("comparator")||id.equals("repeater")||id.equals("hopper")||id.equals("redstone_lamp")||id.contains("redstone_torch")||id.equals("target");}
    private void state(BlockPos pos,BlockState state){
        String id=Registries.BLOCK.getId(state.getBlock()).getPath();long key=pos.asLong();
        if(!machine(id)){states.remove(key);emit(pos,state.getFluidState().isEmpty()?SignalEvent.Kind.BLOCK_UPDATE:SignalEvent.Kind.FLUID,true,id);return;}
        if(pos.getY()<owner.config().minY||pos.getY()>=owner.config().maxY){emit(pos,SignalEvent.Kind.MACHINE_UPDATE,true,id);return;}
        // Observe real machine states even when unchanged; deduplication only applies to scoring.
        owner.revealedMachine(pos.getX(),pos.getY(),pos.getZ(),System.currentTimeMillis());
        int mask=0;
        for(var entry:state.getEntries().entrySet()){
            int bit=switch(entry.getKey().getName()){case "powered"->1;case "extended"->2;case "triggered"->4;case "crafting"->8;case "lit"->16;case "open"->32;case "enabled"->64;case "locked"->128;default->0;};
            if(bit!=0&&Boolean.TRUE.equals(entry.getValue()))mask|=bit;
            if(entry.getKey().getName().equals("power")&&entry.getValue() instanceof Integer power)mask|=Math.clamp(power,0,15)<<8;
        }
        var current=new State(id,mask);var previous=states.put(key,current);
        while(states.size()>owner.config().maxChunks*16)states.remove(states.keySet().iterator().next());
        if(previous==null||!previous.block().equals(id))emit(pos,SignalEvent.Kind.MACHINE_UPDATE,true,id);
        if(previous!=null&&previous.block().equals(id)&&previous.mask()!=mask)emit(pos,SignalEvent.Kind.STATE,true,id);
    }
    private void sound(String id,double x,double y,double z){
        if(id.startsWith("block.piston.")||id.startsWith("block.dispenser.")||id.startsWith("block.crafter.")||id.startsWith("block.comparator.")
                ||id.startsWith("block.note_block.")||id.startsWith("block.tripwire.")||id.startsWith("block.iron_trapdoor.")||id.startsWith("block.iron_door.")
                ||id.contains("pressure_plate.")||id.startsWith("block.lever.")||id.contains("button.")||id.startsWith("block.dropper.")||id.startsWith("block.hopper."))
            emit(x,y,z,SignalEvent.Kind.SOUND,false,id);
        else if(id.endsWith(".break")||id.endsWith(".place"))emit(x,y,z,SignalEvent.Kind.BLOCK_UPDATE,false,id);
    }
    private void light(LightUpdateS2CPacket packet){
        var data=packet.getData();int base=MinecraftClient.getInstance().world.getBottomSectionCoord()-1,index=0;
        var bits=data.getInitedBlock();
        for(int section=bits.nextSetBit(0);section>=0;section=bits.nextSetBit(section+1)){
            if(index>=data.getBlockNibbles().size())break;var nibbles=data.getBlockNibbles().get(index++);int y=base+section;
            if(y<0&&y*16>=owner.config().minY&&y*16+16<=owner.config().maxY)section(packet.getChunkX(),y,packet.getChunkZ(),lightHash(nibbles));
        }
        bits=data.getUninitedBlock();for(int section=bits.nextSetBit(0);section>=0;section=bits.nextSetBit(section+1))section(packet.getChunkX(),base+section,packet.getChunkZ(),0);
        while(lights.size()>owner.config().maxLightSections)lights.remove(lights.keySet().iterator().next());
    }
    private void section(int x,int y,int z,int hash){
        if(y>=0||y*16<owner.config().minY||y*16+16>owner.config().maxY)return;
        var previous=lights.put(new LightKey(x,y,z),hash);
        if(previous!=null&&previous!=hash)emit(x*16+8,y*16+8,z*16+8,SignalEvent.Kind.LIGHT,false,"block-light");
    }
    private static int lightHash(byte[] data){int hash=1;boolean lit=false;for(byte value:data){hash=31*hash+value;lit|=value!=0;}return lit?hash:0;}
    private static boolean output(EntityType<?> type){String id=Registries.ENTITY_TYPE.getId(type).getPath();return type==EntityType.ITEM||type==EntityType.FALLING_BLOCK||type==EntityType.TNT||id.contains("minecart");}
    private static long cell(Tracked entity){return BlockPos.asLong((int)Math.floor(entity.x/3),(int)Math.floor(entity.y/3),(int)Math.floor(entity.z/3));}
    private void spawn(EntitySpawnS2CPacket packet){
        var type=packet.getEntityType();boolean mob=type.getSpawnGroup()==SpawnGroup.MONSTER;
        if(!mob&&!output(type))return;
        remove(packet.getEntityId());
        if(packet.getY()<owner.config().minY+1||packet.getY()>=owner.config().maxY-1){if(!mob)emit(packet.getX(),packet.getY(),packet.getZ(),SignalEvent.Kind.ENTITY,false,"spawn-"+Registries.ENTITY_TYPE.getId(type).getPath());return;}
        var tracked=new Tracked(packet.getX(),packet.getY(),packet.getZ(),mob,type==EntityType.ITEM);entities.put(packet.getEntityId(),tracked);
        if(mob)increment(tracked);else if(!tracked.item)emit(tracked.x,tracked.y,tracked.z,SignalEvent.Kind.ENTITY,false,"spawn-"+Registries.ENTITY_TYPE.getId(type).getPath()+":"+packet.getEntityData());
        while(entities.size()>owner.config().maxEntities)remove(entities.keySet().iterator().next());
    }
    private void increment(Tracked tracked){
        long key=cell(tracked);int count=density.merge(key,1,Integer::sum);long now=System.currentTimeMillis();
        if(count>=owner.config().mobClusterMin&&now-densitySent.getOrDefault(key,0L)>=1000){densitySent.put(key,now);emit(Math.floor(tracked.x/3)*3+1.5,Math.floor(tracked.y/3)*3+1.5,Math.floor(tracked.z/3)*3+1.5,SignalEvent.Kind.DENSITY,false,"hostile-density-"+count);}
        while(densitySent.size()>2048)densitySent.remove(densitySent.keySet().iterator().next());
    }
    private void decrement(Tracked tracked){if(tracked.mob){long key=cell(tracked);density.computeIfPresent(key,(k,n)->n<=1?null:n-1);}}
    private void remove(int id){var old=entities.remove(id);if(old!=null)decrement(old);}
    private void move(int id,double x,double y,double z){
        var tracked=entities.get(id);if(tracked==null)return;
        if(y<owner.config().minY+1||y>=owner.config().maxY-1){remove(id);return;}
        decrement(tracked);tracked.x=x;tracked.y=y;tracked.z=z;
        if(tracked.mob)increment(tracked);
    }
}
