package dev.maro.grimfixture;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.events.FlagEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local test fixture only. Never cancels or changes Grim checks, events, or permissions. */
public final class GrimFixture extends JavaPlugin implements Listener {
    private GrimAbstractAPI grim;
    private String scenario="idle";
    private int tick,breaks,places,rejectedBreaks,rejectedPlaces,targetZ=2;
    private double firstPlaceDistance,lastPlaceDistance;
    private final List<String> placePositions=new ArrayList<>();
    private final List<Integer> breakTicks=new ArrayList<>(),placeTicks=new ArrayList<>();
    private final List<String> flags=new CopyOnWriteArrayList<>();
    private UUID playerId;
    private File proof;

    @Override public void onEnable(){
        grim=GrimAPI.INSTANCE.getExternalAPI();
        proof=new File(getDataFolder(),"proof.properties");getDataFolder().mkdirs();
        getServer().getPluginManager().registerEvents(this,this);
        grim.getEventBus().subscribe(this,FlagEvent.class,event->{
            if(playerId!=null&&playerId.equals(event.getUser().getUniqueId())){
                String detail=event.getCheck().getCheckName()+" VL="+event.getViolations()+" "+event.getVerbose();
                flags.add(detail);getLogger().warning("[grim-fixture-flag] "+detail);
            }
        });
        getServer().getScheduler().runTaskTimer(this,()->{tick++;if(tick%5==0)writeProof();},1,1);
        getLogger().info("[grim-fixture] active Grim="+grim.getGrimVersion()+" checks preserved, console-only control");
    }
    @EventHandler public void join(PlayerJoinEvent event){
        Player player=event.getPlayer();playerId=player.getUniqueId();
        getServer().getScheduler().runTaskLater(this,()->prepare(player,"plain-mining"),20);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(sender instanceof Player){sender.sendMessage("Local fixture requires console control");return true;}
        Player player=getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if(player==null){sender.sendMessage("NO_PLAYER");return true;}
        if(args.length>0&&args[0].equals("prepare"))prepare(player,args.length>1?args[1]:"plain-mining");
        writeProof();sender.sendMessage("FIXTURE "+scenario+" breaks="+breaks+" places="+places+" rejected="+rejectedBreaks+","+rejectedPlaces+" flags="+flags.size()+" op="+player.isOp()+" exempt="+player.hasPermission("grim.exempt"));return true;
    }
    private void prepare(Player player,String next){
        scenario="setup";World world=player.getWorld();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            world.getBlockAt(x,63,z).setType(Material.STONE,false);
            for(int y=64;y<=74;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        world.setStorm(false);world.setTime(6000);world.setSpawnLocation(0,64,0);
        player.setOp(false);player.setGameMode(GameMode.SURVIVAL);player.getInventory().clear();
        player.getInventory().setItem(0,new ItemStack(next.equals("stone-mining")?Material.GLASS:Material.STONE,1));
        player.getInventory().setItem(1,new ItemStack(Material.DIAMOND_SHOVEL));
        player.getInventory().setItem(2,new ItemStack(Material.DIAMOND_PICKAXE));
        player.getInventory().setHeldItemSlot(1);player.setHealth(20);player.setFoodLevel(20);player.setSaturation(20);
        player.teleport(new Location(world,.5,64,.5,0,0));
        targetZ=next.equals("rejected-placement")?4:2;
        if(next.contains("mining"))world.getBlockAt(0,64,targetZ).setType(next.equals("stone-mining")?Material.STONE:Material.DIRT,false);
        breaks=places=rejectedBreaks=rejectedPlaces=0;firstPlaceDistance=lastPlaceDistance=0;breakTicks.clear();placeTicks.clear();placePositions.clear();flags.clear();
        scenario=next;playerId=player.getUniqueId();writeProof();
        getLogger().info("[grim-fixture] prepared "+next+" player="+player.getName()+" op="+player.isOp()+" grim.exempt="+player.hasPermission("grim.exempt")+" grim.nomodifypacket="+player.hasPermission("grim.nomodifypacket")+" grim.nosetback="+player.hasPermission("grim.nosetback"));
    }
    private boolean target(Block block){return block.getX()==0&&block.getY()==64&&block.getZ()==targetZ;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void mine(BlockBreakEvent event){
        if(!target(event.getBlock())||!event.getPlayer().getUniqueId().equals(playerId))return;
        breaks++;breakTicks.add(tick);
        if(scenario.equals("rejected-mining")&&rejectedBreaks<3){
            rejectedBreaks++;event.setCancelled(true);getLogger().info("[grim-fixture] rejected mining "+rejectedBreaks+" tick="+tick);
        }else getLogger().info("[grim-fixture] mining attempt "+breaks+" cancelled="+event.isCancelled()+" tick="+tick);
        getServer().getScheduler().runTask(this,this::writeProof);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void place(BlockPlaceEvent event){
        if(!target(event.getBlock())||!event.getPlayer().getUniqueId().equals(playerId))return;
        places++;placeTicks.add(tick);
        lastPlaceDistance=event.getPlayer().getEyeLocation().distance(event.getBlock().getLocation().add(.5,.5,.5));
        if(places==1)firstPlaceDistance=lastPlaceDistance;
        var pos=event.getPlayer().getLocation();placePositions.add(pos.getX()+","+pos.getY()+","+pos.getZ()+" distance="+lastPlaceDistance);
        if(scenario.equals("rejected-placement")&&rejectedPlaces<3){
            rejectedPlaces++;event.setCancelled(true);getLogger().info("[grim-fixture] rejected placement "+rejectedPlaces+" tick="+tick);
        }else getLogger().info("[grim-fixture] placement attempt "+places+" cancelled="+event.isCancelled()+" tick="+tick);
        getServer().getScheduler().runTask(this,this::writeProof);
    }
    private void writeProof(){
        Player player=playerId==null?null:getServer().getPlayer(playerId);if(player==null)return;
        Properties values=new Properties();values.setProperty("scenario",scenario);values.setProperty("serverTick",""+tick);
        values.setProperty("grimVersion",grim.getGrimVersion());values.setProperty("grimStarted",""+grim.hasStarted());
        var user=grim.getGrimUser(player);values.setProperty("grimUser",""+(user!=null));
        values.setProperty("enabledChecks",""+(user==null?0:user.getChecks().stream().filter(check->check.isEnabled()).count()));
        values.setProperty("op",""+player.isOp());values.setProperty("exempt",""+player.hasPermission("grim.exempt"));
        values.setProperty("noModifyPacket",""+player.hasPermission("grim.nomodifypacket"));values.setProperty("noSetback",""+player.hasPermission("grim.nosetback"));
        values.setProperty("player",player.getName());values.setProperty("target",player.getWorld().getBlockAt(0,64,targetZ).getType().name());
        values.setProperty("targetZ",""+targetZ);values.setProperty("firstPlaceDistance",""+firstPlaceDistance);values.setProperty("lastPlaceDistance",""+lastPlaceDistance);values.setProperty("placePositions",placePositions.toString());
        values.setProperty("stone",""+count(player,Material.STONE));values.setProperty("glass",""+count(player,Material.GLASS));values.setProperty("cursor",player.getItemOnCursor().getType().name());
        values.setProperty("breaks",""+breaks);values.setProperty("places",""+places);values.setProperty("rejectedBreaks",""+rejectedBreaks);values.setProperty("rejectedPlaces",""+rejectedPlaces);
        values.setProperty("breakTicks",breakTicks.toString());values.setProperty("placeTicks",placeTicks.toString());
        values.setProperty("flags",""+flags.size());values.setProperty("flagDetails",String.join(" | ",flags));
        try{Path temp=proof.toPath().resolveSibling("proof.tmp");try(var out=Files.newOutputStream(temp)){values.store(out,"Actual Paper/Grim server fixture");}Files.move(temp,proof.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        catch(IOException error){throw new UncheckedIOException(error);}
    }
    private int count(Player player,Material type){int count=0;for(ItemStack stack:player.getInventory().getContents())if(stack!=null&&stack.getType()==type)count+=stack.getAmount();return count;}
}
