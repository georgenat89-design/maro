package dev.maro.module.impl.player;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;

/** Repeats ordinary trident use/release while the use button is held. */
public final class AutoTrident extends Module {
    private static AutoTrident active;
    private final BooleanSetting serverTiming = add(new BooleanSetting("Server Timing",
        "Use full native charges and adapt after server corrections", true));
    private final BooleanSetting precharge = add(new BooleanSetting("Precharge",
        "Start the next charge during Riptide and release once fully charged", true).visible(() -> serverTiming.get()));
    private final NumberSetting speed = add(new NumberSetting("Speed",
        "Higher is faster: 10 charges for 10 ticks; 1 charges for 28 ticks", 10, 1, 10, 1));
    private final NumberSetting repeatDelay = add(new NumberSetting("Repeat Delay",
        "Ticks between accepted releases and the next charge; Riptide spins and server backoff still take priority", 1, 1, 10, 1));
    private Object player,world;
    private int nextUseAge,lastUseAge=-100,lastCorrectionAge=-1000,chargePenalty;
    private long chargeStartedAt;

    public AutoTrident() {
        super("Auto Trident", "Hold right-click to repeatedly charge and release your trident", Category.PLAYER);
    }
    @Override protected void onEnable(){active=this;reset();}
    @Override protected void onDisable(){if(active==this)active=null;reset();}
    private void reset(){player=mc.player;world=mc.world;nextUseAge=0;lastUseAge=-100;lastCorrectionAge=-1000;chargePenalty=0;chargeStartedAt=0;}
    private boolean current(){if(!inGame())return false;if(player!=mc.player||world!=mc.world)reset();return mc.player.isAlive()&&!mc.player.isSpectator();}
    private boolean readyToCharge(){return !serverTiming.get()||mc.player.age>=nextUseAge&&(precharge.get()||!mc.player.isUsingRiptide());}
    private boolean readyToRelease(){return readyToCharge();}
    public static boolean allowNativeUse(PlayerEntity user,ItemStack stack){
        var m=active;
        return m==null||user!=mc.player||!m.current()||!m.serverTiming.get()
            ||m.readyToCharge()&&TridentUtil.eligible(stack)&&(!TridentUtil.riptide(stack)||mc.player.isTouchingWaterOrRain());
    }
    public static void useStarted(){var m=active;if(m!=null&&m.current()){m.chargeStartedAt=System.nanoTime();m.lastUseAge=mc.player.age;}}
    public static void useFinished(){var m=active;if(m!=null&&m.current()){m.nextUseAge=Math.max(m.nextUseAge,mc.player.age+m.repeatDelay.getInt());m.lastUseAge=mc.player.age;m.chargeStartedAt=0;}}
    public static void serverCorrection(){
        var m=active;if(m==null||!m.current()||!m.serverTiming.get()||mc.player.age-m.lastUseAge>40)return;
        m.nextUseAge=Math.max(m.nextUseAge,mc.player.age+20);m.chargePenalty=Math.min(10,m.chargePenalty+2);m.lastCorrectionAge=mc.player.age;
    }

    @Override
    public void onTick() {
        if (!current() || mc.interactionManager == null || mc.currentScreen != null || !mc.isWindowFocused()
            || !mc.options.useKey.isPressed()) { chargeStartedAt=0; return; }
        if(mc.player.age-lastCorrectionAge>200)chargePenalty=0;

        if (mc.player.isUsingItem()) {
            if(mc.player.getActiveItem().isOf(Items.TRIDENT)&&chargeStartedAt==0)chargeStartedAt=System.nanoTime();
            // Leave other item uses alone, including food, shields and bows.
            int charge=(serverTiming.get()?TridentItem.MIN_DRAW_DURATION+chargePenalty:TridentUtil.minChargeTicks(TridentItem.MIN_DRAW_DURATION, mc.player))+(10-speed.getInt())*2;
            if (mc.player.getActiveItem().isOf(Items.TRIDENT)
                && readyToRelease() && TridentUtil.ready()
                && mc.player.getItemUseTime() >= charge
                && (!serverTiming.get()||chargeStartedAt!=0&&System.nanoTime()-chargeStartedAt>=charge*50_000_000L)) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
            return;
        }

        Hand hand = mc.player.getMainHandStack().isOf(Items.TRIDENT) ? Hand.MAIN_HAND
            : mc.player.getOffHandStack().isOf(Items.TRIDENT) ? Hand.OFF_HAND : null;
        if (hand == null || !readyToCharge() || !TridentUtil.ready() || !TridentUtil.eligible(mc.player.getStackInHand(hand))) return;
        mc.interactionManager.interactItem(mc.player, hand);
    }
}
