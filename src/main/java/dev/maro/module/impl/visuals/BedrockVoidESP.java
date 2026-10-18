package dev.maro.module.impl.visuals;

import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.esp.*;
import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.event.EventHandler;
import dev.maro.runtime.events.world.BlockUpdateEvent;
import dev.maro.runtime.events.world.ChunkLoadEvent;
import dev.maro.runtime.utils.render.color.Color;
import dev.maro.setting.*;
import dev.maro.util.ColorUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Bedrock pockets, discovered incrementally on the client thread without reading live chunks from workers. */
public final class BedrockVoidESP extends Module implements EspStyle {
    private final NumberSetting minimum=add(new NumberSetting("Minimum Size","Smallest enclosed bedrock pocket",2,1,128,1).onChange(v->refresh()));
    private final NumberSetting maximum=add(new NumberSetting("Maximum Size","Reject open terrain and oversized components",512,2,2048,1).onChange(v->refresh()));
    private final NumberSetting range=add(new NumberSetting("Scan Range","Loaded chunks within this horizontal distance",128,16,256,16).suffix("m").onChange(v->refresh()));
    private final ModeSetting bands=add(new ModeSetting("Nether Layers","Which Nether bedrock layers to search","Both","Both","Floor","Roof").onChange(v->refresh()));
    private final BooleanSetting boxes=add(new BooleanSetting("Boxes","Highlight each pocket as one clean region",true));
    private final ModeSetting shape=add(new ModeSetting("Shape","Region outline and translucent fill","Both","Both","Outline","Fill").visible(boxes::get));
    private final ColorSetting color=add(new ColorSetting("Pocket Color","Bedrock pocket highlight",0xFFB78AFF));
    private final NumberSetting opacity=add(new NumberSetting("Fill Opacity","Transparent pocket fill",12,0,50,1).suffix("%").visible(boxes::get));
    private final BooleanSetting tracers=add(new BooleanSetting("Tracers","One stable tracer per pocket",false));
    private final NumberSetting width=add(new NumberSetting("Tracer Width","Tracer thickness in pixels",1.5,.5,4,.25).visible(tracers::get));
    private final BooleanSetting glow=add(new BooleanSetting("Glow","Soft tracer halo",true).visible(tracers::get));
    private final BooleanSetting labels=add(new BooleanSetting("Labels","Show pocket size and distance",false));
    private final BooleanSetting coordinates=add(new BooleanSetting("Coordinates","Add coordinates beneath each label",false).visible(labels::get));
    private final BooleanSetting alerts=add(new BooleanSetting("Discovery Alerts","Notify once for each new pocket",false));
    private final NumberSetting budget=add(new NumberSetting("Scan Budget","Maximum planned client scanning time per tick",1.5,.25,4,.25).suffix("ms"));
    private final NumberSetting limit=add(new NumberSetting("Visible Pockets","Nearest pocket regions to render",64,1,256,1));
    private final LinkedHashSet<Work> pending=new LinkedHashSet<>();
    private final Set<Long> scanned=new HashSet<>();
    private final Set<Long> dirty=new HashSet<>();
    private final Map<BlockPos,Pocket> pockets=new HashMap<>();
    private final Set<BlockPos> announced=new HashSet<>();
    private ClientWorld world;
    private Scan job;
    private List<Pocket> visible=List.of();
    private int ticks,lastOperations;
    private long lastAlert;
    public record Pocket(BlockPos key,List<BlockPos> cells,Box bounds) { public Vec3d center(){return bounds.getCenter();} }
    private record Work(int x,int z,int low,int high) { }

    public BedrockVoidESP(){super("Bedrock Void ESP","Finds enclosed pockets in Overworld and Nether bedrock; checks chunk seams and live changes",Category.VISUALS);}
    @Override public List<SettingSection> getSettingSections(){return List.of(SettingSection.of("Search",minimum,maximum,range,bands),SettingSection.of("Highlights",boxes,shape,color,opacity,tracers,width,glow,labels,coordinates,limit),SettingSection.of("Scanning",budget,alerts));}
    @Override protected void onEnable(){MeteorClient.EVENT_BUS.subscribe(this);world=null;syncWorld();}
    @Override protected void onDisable(){MeteorClient.EVENT_BUS.unsubscribe(this);world=null;clear();}
    private void clear(){pending.clear();scanned.clear();dirty.clear();pockets.clear();announced.clear();visible=List.of();job=null;ticks=0;}
    private void syncWorld(){if(mc.world!=world){clear();world=mc.world;}}
    private void refresh(){clear();}
    private List<int[]> layers(){
        if(world==null)return List.of();
        if(world.getRegistryKey().equals(World.OVERWORLD))return List.of(new int[]{world.getBottomY(),world.getBottomY()+4});
        if(world.getRegistryKey().equals(World.NETHER)){
            var out=new ArrayList<int[]>();if(!bands.is("Roof"))out.add(new int[]{0,4});if(!bands.is("Floor"))out.add(new int[]{123,127});return out;
        }return List.of();
    }
    private boolean loaded(int x,int z){return world!=null&&world.isChunkLoaded(x,z);}
    private boolean inRange(int x,int z){if(mc.player==null)return false;int r=(range.getInt()+15)/16;return Math.abs(x-mc.player.getChunkPos().x)<=r&&Math.abs(z-mc.player.getChunkPos().z)<=r;}
    private void queue(int x,int z){if(loaded(x,z)&&inRange(x,z))for(var layer:layers())pending.add(new Work(x,z,layer[0],layer[1]));}
    @EventHandler private void chunk(ChunkLoadEvent event){syncWorld();var p=event.chunk.getPos();if(inRange(p.x,p.z))dirty.add(p.toLong());}
    @EventHandler private void block(BlockUpdateEvent event){
        syncWorld();if(layers().stream().noneMatch(b->event.pos.getY()>=b[0]-1&&event.pos.getY()<=b[1]+1))return;
        var p=new ChunkPos(event.pos);if(inRange(p.x,p.z))dirty.add(p.toLong());
    }
    private void invalidate(int x,int z){
        if(job!=null&&Math.abs(job.work.x-x)<=1&&Math.abs(job.work.z-z)<=1)job=null;
        pockets.values().removeIf(p->p.bounds.intersects(x*16-1,-2048,z*16-1,x*16+17,2048,z*16+17));
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){scanned.remove(ChunkPos.toLong(x+dx,z+dz));queue(x+dx,z+dz);}
        rebuildVisible();
    }
    @Override public void onTick(){
        syncWorld();if(!inGame())return;
        // A burst of block packets dirties each chunk once; work resumes on the next tick.
        if(!dirty.isEmpty()){for(long key:dirty){var p=new ChunkPos(key);invalidate(p.x,p.z);}dirty.clear();}
        if(ticks++%20==0){
            int r=(range.getInt()+15)/16;var center=mc.player.getChunkPos();var found=new ArrayList<ChunkPos>();
            for(int x=center.x-r;x<=center.x+r;x++)for(int z=center.z-r;z<=center.z+r;z++)if(loaded(x,z)&&!scanned.contains(ChunkPos.toLong(x,z)))found.add(new ChunkPos(x,z));
            found.sort(Comparator.comparingInt(p->Math.abs(p.x-center.x)+Math.abs(p.z-center.z)));
            for(var p:found){queue(p.x,p.z);scanned.add(p.toLong());}
            pending.removeIf(w->!loaded(w.x,w.z)||!inRange(w.x,w.z));
            pockets.values().removeIf(p->p.cells.stream().anyMatch(pos->!loaded(pos.getX()>>4,pos.getZ()>>4))||!inRange(p.key.getX()>>4,p.key.getZ()>>4));
            rebuildVisible();
        }
        long end=System.nanoTime()+(long)(budget.get()*1_000_000);lastOperations=0;
        while(lastOperations<1500&&System.nanoTime()<end){
            if(job==null){if(pending.isEmpty())break;var it=pending.iterator();var work=it.next();it.remove();if(!loaded(work.x,work.z)||!inRange(work.x,work.z))continue;job=new Scan(work);}
            if(!loaded(job.work.x,job.work.z)||!inRange(job.work.x,job.work.z)){job=null;continue;}
            boolean done=job.step();lastOperations++;
            if(done){job=null;rebuildVisible();}
        }
    }
    private void rebuildVisible(){if(mc.player==null){visible=List.of();return;}visible=pockets.values().stream().sorted(Comparator.comparingDouble(p->p.center().squaredDistanceTo(mc.player.getEntityPos()))).limit(limit.getInt()).toList();}
    private void publish(List<BlockPos> cells){
        if(cells.size()<minimum.getInt()||cells.size()>maximum.getInt())return;
        var key=cells.stream().min(BlockPos::compareTo).orElseThrow();
        double x=Double.MAX_VALUE,y=x,z=x,xx=-x,yy=-x,zz=-x;
        for(var p:cells){x=Math.min(x,p.getX());y=Math.min(y,p.getY());z=Math.min(z,p.getZ());xx=Math.max(xx,p.getX()+1);yy=Math.max(yy,p.getY()+1);zz=Math.max(zz,p.getZ()+1);}
        pockets.put(key,new Pocket(key,List.copyOf(cells),new Box(x,y,z,xx,yy,zz)));
        if(announced.size()>4096)announced.clear();
        if(announced.add(key)&&alerts.get()&&System.currentTimeMillis()-lastAlert>3000){lastAlert=System.currentTimeMillis();Notifications.push("Bedrock pocket",cells.size()+" blocks · "+key.toShortString(),Notifications.Type.INFO);}
    }
    /** A finite scan of one band and a loaded one-chunk halo; resumes between ticks. */
    private final class Scan {
        final Work work;
        final LongOpenHashSet visited=new LongOpenHashSet();
        final ArrayDeque<BlockPos> flood=new ArrayDeque<>();
        final ArrayList<BlockPos> component=new ArrayList<>();
        int cursor,total;boolean enclosed=true;
        Scan(Work work){this.work=work;total=256*(work.high-work.low+1);}
        boolean inBand(BlockPos p){return p.getY()>=work.low&&p.getY()<=work.high;}
        boolean withinHalo(BlockPos p){return Math.abs((p.getX()>>4)-work.x)<=1&&Math.abs((p.getZ()>>4)-work.z)<=1;}
        boolean step(){
            if(!flood.isEmpty()){
                var p=flood.removeFirst();
                if(component.size()<=maximum.getInt())component.add(p);
                for(var side:Direction.values()){
                    var next=p.offset(side);
                    if(!loaded(next.getX()>>4,next.getZ()>>4)){enclosed=false;continue;}
                    if(world.getBlockState(next).isOf(Blocks.BEDROCK))continue;
                    if(!inBand(next)||!withinHalo(next)){enclosed=false;continue;}
                    if(visited.add(next.asLong()))flood.addLast(next);
                }
                if(flood.isEmpty()){if(enclosed)publish(component);component.clear();enclosed=true;}
                return false;
            }
            if(cursor>=total)return true;
            int at=cursor++;var p=new BlockPos(work.x*16+at%16,work.low+at/256,work.z*16+(at/16)%16);
            if(visited.add(p.asLong())&&!world.getBlockState(p).isOf(Blocks.BEDROCK))flood.add(p);
            return false;
        }
    }
    public List<Pocket> pockets(){return List.copyOf(pockets.values());}
    public int pendingScans(){return pending.size()+(job==null?0:1);}
    public int lastScanOperations(){return lastOperations;}
    public void render(Renderer3D renderer){
        if(!inGame()||world!=mc.world)return;
        Vec3d camera=renderer.camera();int tint=color.get()|0xFF000000;renderer.lineWidth(1.25f);
        if(tracers.get()&&glow.get())BlockEspRenderer.prepareGlow(this);
        for(var p:visible){
            var b=p.bounds;if(boxes.get()&&BlockEspRenderer.inView(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,camera))
                renderer.box(b.minX+.015,b.minY+.015,b.minZ+.015,b.maxX-.015,b.maxY-.015,b.maxZ-.015,new Color(ColorUtil.withAlpha(tint,(int)(opacity.get()*2.55))),new Color(tint),shape.is("Outline")?ShapeMode.Lines:shape.is("Fill")?ShapeMode.Sides:ShapeMode.Both,0);
            if(tracers.get()){var c=p.center();BlockEspRenderer.addTracer(this,c.x,c.y,c.z,camera,tint,.8f);}
        }renderer.lineWidth(1.5f);
    }
    @Override public void onRender2D(DrawContext context,float delta){
        if(!inGame()||!labels.get()||world!=mc.world||mc.options.hudHidden)return;
        var camera=mc.gameRenderer.getCamera().getCameraPos();int count=0;Fonts.beginRaw();
        try{for(var p:visible){if(count++>=8)break;var c=p.center();var point=BlockEspRenderer.toScreen(c.x,p.bounds.maxY+.3,c.z,camera,context.getScaledWindowWidth(),context.getScaledWindowHeight());if(point==null)continue;
            String text=p.cells.size()+" blocks · "+Math.round(c.distanceTo(mc.player.getEntityPos()))+"m";float w=Math.max(Fonts.width(text,true,.7f),coordinates.get()?Fonts.width(p.key.toShortString(),false,.6f):0)+12;float x=point[0]-w/2,y=point[1];
            Render2D.roundRect(context,x,y,w,coordinates.get()?24:14,4,0xDC10141F);Fonts.draw(context,text,x+6,y+3,color.get()|0xFF000000,true,.7f);
            if(coordinates.get())Fonts.draw(context,p.key.toShortString(),x+6,y+14,0xFFB7BDCD,false,.6f);
        }}finally{Fonts.endRaw();}
    }
    public boolean tracersFromBottom(){return false;}
    public float tracerWidthPx(int height){return width.getFloat();}
    public float tracerHalo(){return glow.get()?3:0;}
    public boolean tracerPackets(){return false;}
    public float packetSpeed(){return 0;}
    public boolean tracerBloom(){return glow.get();}
    public boolean bloomOn(){return tracers.get()&&glow.get();}
    public float bloomStrength(){return .55f;}
    public float bloomSize(){return 4;}
    public float seconds(){return (System.nanoTime()%60_000_000_000L)/1e9f;}
    public void renderFailed(RuntimeException error){dev.maro.Maro.LOGGER.error("Bedrock Void ESP render failed",error);setEnabled(false);}
}
