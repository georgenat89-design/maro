package dev.maro.render.pickaxe;

import dev.maro.module.impl.visuals.SkinAccessories;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.Objects;

/** Local audio only. Samples are isolated from the user-provided reference recordings, decoded by the game's sound engine. */
public final class PickaxeSounds {
    private final long[] last=new long[3];
    private final PositionedSoundInstance[] active=new PositionedSoundInstance[3];
    private String equipped="";
    private Object world;
    private int played;
    public int played(){return played;}
    public boolean playing(){var sounds=MinecraftClient.getInstance().getSoundManager();if(sounds==null)return false;for(var voice:active)if(voice!=null&&sounds.isPlaying(voice))return true;return false;}
    private static boolean eligible(SkinAccessories m){
        var c=MinecraftClient.getInstance();if(m==null||!m.isEnabled()||!m.pickaxeSounds.get()||m.pickaxeVolume.getFloat()<=0||c.player==null||c.world==null||c.currentScreen!=null||!PickaxeModels.contains(m.selected()))return false;
        var stack=c.player.getMainHandStack();var model=stack.get(DataComponentTypes.ITEM_MODEL);
        return stack.isIn(ItemTags.PICKAXES)&&(model==null||Objects.equals(model,stack.getItem().getComponents().get(DataComponentTypes.ITEM_MODEL)));
    }
    public void tick(SkinAccessories m){
        var c=MinecraftClient.getInstance();if(world!=c.world){reset();world=c.world;}
        if(!m.isEnabled()||!m.pickaxeSounds.get()||m.pickaxeVolume.getFloat()<=0){reset();world=c.world;return;}
        String next=eligible(m)?m.selected():"";
        if(next.isEmpty()&&!equipped.isEmpty()){reset();world=c.world;return;}
        if(!next.equals(equipped)){equipped=next;if(!next.isEmpty())play(m,0,false);}
    }
    public static void swing(){var m=SkinAccessories.get();if(eligible(m))m.audio().play(m,1,false);}
    public static void impact(){var m=SkinAccessories.get();if(eligible(m))m.audio().play(m,2,false);}
    public void preview(SkinAccessories m){if(m.pickaxeSounds.get()&&PickaxeModels.contains(m.selected()))play(m,1,true);}
    private void play(SkinAccessories m,int type,boolean preview){
        long now=System.nanoTime();if(type==2&&now-last[1]<220_000_000L)return;long cooldown=(type==2?150:preview?250:90)*1_000_000L;if(now-last[type]<cooldown)return;
        var c=MinecraftClient.getInstance();float volume=m.pickaxeVolume.getFloat()/100;if(volume<=0||c.getSoundManager()==null)return;
        last[type]=now;String event="pickaxe."+PickaxeModels.key(m.selected())+"."+new String[]{"equip","swing","impact"}[type];
        // One voice per effect type bounds rapid swings and continuous mining.
        if(active[type]!=null)c.getSoundManager().stop(active[type]);
        active[type]=PositionedSoundInstance.master(SoundEvent.of(Identifier.of("maro",event)),1,volume);
        c.getSoundManager().play(active[type]);played++;
    }
    public void reset(){
        var sounds=MinecraftClient.getInstance().getSoundManager();for(int i=0;i<3;i++){if(active[i]!=null&&sounds!=null)sounds.stop(active[i]);active[i]=null;last[i]=0;}equipped="";world=null;
    }
}
