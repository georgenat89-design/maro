package dev.maro.module.impl.visuals;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.inventory.ChestSessions;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import java.util.*;

/** Passive multiplayer snapshots, plus thread-safe local-world reads on an integrated server. */
public final class ChestPreview extends Module {
    public record Snapshot(List<ItemStack> items,long capturedAt,boolean local){ }
    private static final Snapshot PREVIEW = sampleContents();
    private static Snapshot sampleContents(){
        var items=new ArrayList<ItemStack>(java.util.Collections.nCopies(27,ItemStack.EMPTY));
        items.set(0,new ItemStack(net.minecraft.item.Items.DIAMOND,12));
        items.set(3,new ItemStack(net.minecraft.item.Items.OAK_LOG,32));
        items.set(9,new ItemStack(net.minecraft.item.Items.IRON_INGOT,24));
        items.set(14,new ItemStack(net.minecraft.item.Items.GOLDEN_APPLE,3));
        items.set(22,new ItemStack(net.minecraft.item.Items.DIAMOND_PICKAXE));
        return new Snapshot(List.copyOf(items),0,false);
    }
    private record Key(BlockPos pos,boolean ender){ }
    private final BooleanSetting localWorld=add(new BooleanSetting("Live Singleplayer","Read existing container items from your local world before opening it",true));
    private final BooleanSetting holdSneak=add(new BooleanSetting("Hold Sneak","Only show the preview while holding your sneak key",false));
    private final NumberSetting scale=add(new NumberSetting("Preview Size","Size of the container preview",100,60,140,5).suffix("%"));
    private final ButtonSetting clear=add(new ButtonSetting("Clear Saved Chests","Forget the chests saved in this world","Clear",this::clear));
    private final LinkedHashMap<Key,Snapshot> saved=new LinkedHashMap<>(32,.75f,true);
    private final Map<Key,BlockEntity> identities=new HashMap<>();
    private Object world;
    private ScreenHandler lastHandler;
    private int lastRevision=Integer.MIN_VALUE;
    private boolean requestPending;
    private long nextRequest;
    private int generation;
    public ChestPreview(){super("Chest Preview","Look at a chest to preview its saved contents. Local worlds can show live items before opening.",Category.VISUALS);}
    private void clear(){saved.clear();identities.clear();lastHandler=null;lastRevision=Integer.MIN_VALUE;generation++;requestPending=false;nextRequest=0;}
    @Override protected void onDisable(){clear();world=null;}
    private void syncWorld(){if(world!=mc.world){clear();world=mc.world;}}
    private static boolean supported(BlockEntity b){return b instanceof ChestBlockEntity||b instanceof BarrelBlockEntity||b instanceof EnderChestBlockEntity;}
    private static Key key(BlockPos pos){boolean ender=mc.world!=null&&mc.world.getBlockEntity(pos) instanceof EnderChestBlockEntity;return new Key(ender?BlockPos.ORIGIN:pos.toImmutable(),ender);}
    private void save(BlockPos pos,Snapshot snapshot){Key key=key(pos);saved.put(key,snapshot);identities.put(key,mc.world.getBlockEntity(pos));}
    private void put(BlockPos pos,Snapshot snapshot){
        save(pos,snapshot);
        if(mc.world!=null){var state=mc.world.getBlockState(pos);if(state.getBlock() instanceof ChestBlock&&state.get(ChestBlock.CHEST_TYPE)!=ChestType.SINGLE)save(pos.offset(ChestBlock.getFacing(state)),snapshot);}
        while(saved.size()>128){var oldest=saved.keySet().iterator().next();saved.remove(oldest);identities.remove(oldest);}
    }
    public Snapshot preview(BlockPos pos){
        syncWorld();Key key=key(pos);var snapshot=saved.get(key);
        if(snapshot!=null&&!key.ender){
            var state=mc.world.getBlockState(pos);int size=state.getBlock() instanceof ChestBlock&&state.get(ChestBlock.CHEST_TYPE)!=ChestType.SINGLE?54:27;
            if(identities.get(key)!=mc.world.getBlockEntity(pos)||snapshot.items.size()!=size){saved.remove(key);identities.remove(key);return null;}
        }
        return snapshot;
    }
    public int savedCount(){return saved.size();}
    private static Snapshot copy(Inventory inventory,boolean local){
        int count=Math.min(54,inventory.size());var items=new ArrayList<ItemStack>(count);
        for(int i=0;i<count;i++)items.add(inventory.getStack(i).copy());
        return new Snapshot(List.copyOf(items),System.nanoTime(),local);
    }
    @Override public void onTick(){
        syncWorld();if(!inGame())return;
        var menu=mc.player.currentScreenHandler;BlockPos pos=ChestSessions.position(menu);
        if(pos!=null&&ChestSessions.ready(menu)&&menu instanceof GenericContainerScreenHandler container){
            if(lastHandler!=menu||lastRevision!=menu.getRevision()){
                put(pos,copy(container.getInventory(),false));lastHandler=menu;lastRevision=menu.getRevision();
            }
        }else{lastHandler=null;lastRevision=Integer.MIN_VALUE;}
        if(mc.currentScreen==null&&mc.crosshairTarget instanceof BlockHitResult hit&&supported(mc.world.getBlockEntity(hit.getBlockPos())))requestLocal(hit.getBlockPos());
    }
    private void requestLocal(BlockPos pos){
        var server=mc.getServer();long now=System.nanoTime();
        if(!localWorld.get()||!mc.isInSingleplayer()||server==null||requestPending||now<nextRequest)return;
        requestPending=true;nextRequest=now+250_000_000L;
        int ticket=generation;Object level=world;var dimension=mc.world.getRegistryKey();var playerId=mc.player.getUuid();var target=pos.toImmutable();
        server.submit(()->{
            var serverWorld=server.getWorld(dimension);if(serverWorld==null||!serverWorld.isChunkLoaded(ChunkPos.toLong(target)))return null;
            var block=serverWorld.getBlockEntity(target);if(!supported(block))return null;
            // A preview must never roll an unopened loot table or load a chunk.
            if(block instanceof LootableContainerBlockEntity loot&&loot.getLootTable()!=null)return null;
            Inventory inventory=null;
            if(block instanceof EnderChestBlockEntity){var player=server.getPlayerManager().getPlayer(playerId);if(player!=null)inventory=player.getEnderChestInventory();}
            else if(block instanceof ChestBlockEntity){
                var state=serverWorld.getBlockState(target);
                if(state.get(ChestBlock.CHEST_TYPE)!=ChestType.SINGLE){var adjacent=target.offset(ChestBlock.getFacing(state));if(!serverWorld.isChunkLoaded(ChunkPos.toLong(adjacent)))return null;var other=serverWorld.getBlockEntity(adjacent);if(other instanceof LootableContainerBlockEntity loot&&loot.getLootTable()!=null)return null;}
                inventory=ChestBlock.getInventory((ChestBlock)state.getBlock(),state,serverWorld,target,true);
            }else if(block instanceof Inventory inv)inventory=inv;
            return inventory==null?null:copy(inventory,true);
        }).whenComplete((snapshot,error)->mc.execute(()->{
            if(ticket!=generation)return;requestPending=false;
            if(isEnabled()&&mc.world==level&&snapshot!=null)put(target,snapshot);
        }));
    }
    @Override public void onRender2D(DrawContext ctx,float delta){
        syncWorld();if(!inGame()||mc.currentScreen!=null||holdSneak.get()&&!mc.options.sneakKey.isPressed()||!(mc.crosshairTarget instanceof BlockHitResult hit))return;
        var block=mc.world.getBlockEntity(hit.getBlockPos());if(!supported(block))return;
        Snapshot snapshot=preview(hit.getBlockPos());
        String name=block instanceof BarrelBlockEntity?"Barrel":block instanceof EnderChestBlockEntity?"Ender Chest":"Chest";
        float factor=Math.min(scale.getFloat()/100,Math.min((ctx.getScaledWindowWidth()-16)/186f,(ctx.getScaledWindowHeight()-16)/160f));
        int rows=snapshot==null?0:(snapshot.items.size()+8)/9;float w=186,h=snapshot==null?60:43+rows*18;
        float x=Math.min(ctx.getScaledWindowWidth()-w*factor-8,ctx.getScaledWindowWidth()/2f+22),y=Math.max(8,Math.min(ctx.getScaledWindowHeight()-h*factor-8,ctx.getScaledWindowHeight()/2f-h*factor/2));
        drawCard(ctx,name,snapshot,x,y,factor,null);
    }
    /** Shared card renderer with immutable local examples; no chest lookup, request or saved entry. */
    public void renderPreview(DrawContext ctx,float x,float y,float width,float height){
        float w=186,h=97;
        float factor=Math.min(width/(w*1.4f),height/(h*1.4f))*scale.getFloat()/100f;
        drawCard(ctx,"Chest",PREVIEW,x+(width-w*factor)/2,y+(height-h*factor)/2,factor,"Sample contents");
    }
    private void drawCard(DrawContext ctx,String name,Snapshot snapshot,float x,float y,float factor,String label){
        int rows=snapshot==null?0:(snapshot.items.size()+8)/9;
        float w=186,h=snapshot==null?60:43+rows*18;
        ctx.getMatrices().pushMatrix();ctx.getMatrices().translate(x,y);ctx.getMatrices().scale(factor,factor);
        try {
        Render2D.shadow(ctx,0,2,w,h,7,12,0x70000000);Render2D.roundRect(ctx,0,0,w,h,7,0xEE10171F);Render2D.roundOutline(ctx,0,0,w,h,7,1,Theme.accent(0x70));
        Fonts.draw(ctx,name+" preview",10,9,Theme.TEXT,true,.8f);
        if(snapshot==null){Fonts.draw(ctx,"Contents unknown",10,28,Theme.TEXT_DIM,false,.75f);Fonts.draw(ctx,"Open once to save a preview",10,43,Theme.TEXT_MUTED,false,.64f);}
        else{
            long seconds=Math.max(0,(System.nanoTime()-snapshot.capturedAt)/1_000_000_000L);
            Fonts.draw(ctx,label!=null?label:snapshot.local?"Local world":"Saved "+(seconds<60?seconds+"s":seconds/60+"m")+" ago",10,24,Theme.TEXT_MUTED,false,.64f);
            for(int i=0;i<rows*9;i++){
                int ix=12+i%9*18,iy=37+i/9*18;Render2D.roundRect(ctx,ix-1,iy-1,17,17,2,0xFF202C38);
                if(i<snapshot.items.size()){var item=snapshot.items.get(i);if(!item.isEmpty()){ctx.drawItem(item,ix,iy);dev.maro.gui.render.OwnedHudText.stackOverlay(ctx,mc.textRenderer,item,ix,iy);}}
            }
        }
        } finally {ctx.getMatrices().popMatrix();}
    }
}
