package dev.maro.module.impl.visuals;

import dev.maro.anubis.inspect.InspectionKnifeRenderer;
import dev.maro.anubis.inspect.InspectionMotion;
import net.minecraft.util.math.MathHelper;
import dev.maro.gui.hud.ItemPickerScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Twelve detailed knives and twenty-four grip-aware inspections for a selected main-hand item. */
public final class ItemInspect extends Module {
    private static ItemInspect instance;
    private static final String[] KNIVES=InspectionMotion.KNIVES;
    private final KeybindSetting key=add(new KeybindSetting("Inspect Key","Press to inspect the equipped knife",GLFW.GLFW_KEY_Y));
    private final ModeSetting knife=add(new ModeSetting("Knife","Knife silhouette, fittings and its two unique inspections","Skeleton",KNIVES));
    private final ModeSetting items=add(new ModeSetting("Items","Replace your chosen item, or every held item","Selected Item","Selected Item","Any Item"));
    private final TextSetting selectedItem=add(new TextSetting("Selected Item","Registry ID of the item that will appear as a knife","minecraft:diamond_sword",256,"minecraft:item").visible(()->items.is("Selected Item")));
    private final ButtonSetting chooseItem=add(new ButtonSetting("Choose Item","Search and select any registered Minecraft item","Choose",()->mc.setScreen(new ItemPickerScreen(mc.currentScreen,"Select knife item",this::selectItem))));
    private final ButtonSetting useHeld=add(new ButtonSetting("Use Held Item","Apply the knife to the item in your main hand","Select",()->{if(inGame())selectItem(mc.player.getMainHandStack().getItem());}));
    private final NumberSetting duration=add(new NumberSetting("Duration","Inspection playback time",3.2,1,6,.1).suffix("s"));
    private final NumberSetting size=add(new NumberSetting("Knife Size","Size of the first-person knife",100,65,135,5).suffix("%"));
    private final ModeSetting finish=add(new ModeSetting("Finish","Blade coating and grip materials","Satin Steel","Satin Steel","Damascus","Black Cerakote","Maro Blue","Item Material","Vanilla Steel"));
    private final BooleanSetting showArm=add(new BooleanSetting("Show Arm","Use your own skin for the knife hand",true));
    private final ModeSetting handStyle=add(new ModeSetting("Hand Style","Curved tactical gloves or your Minecraft skin","Tactical Gloves","Tactical Gloves","Your Skin").visible(showArm::get));
    private final BooleanSetting sound=add(new BooleanSetting("Swish Sound","A soft swish during inspection",true));
    private final BooleanSetting onEquip=add(new BooleanSetting("Inspect On Equip","Inspect when equipping an eligible item",false));
    private final ModeSetting inspectionStyle=add(new ModeSetting("Inspection Style","Cycle alternates the two routines every time you inspect","Cycle","Cycle","Signature","Alternate"));
    private final NumberSetting reflections=add(new NumberSetting("Metal Reflections","View-dependent metal highlights; grips retain a rough finish",75,0,100,5).suffix("%"));
    private final BooleanSetting idleMotion=add(new BooleanSetting("Idle Motion","Subtle breathing while the knife is held",true));
    private final NumberSetting sway=add(new NumberSetting("Camera Sway","Soft, frame-rate independent response to looking around",45,0,100,5).suffix("%"));
    private final java.util.Map<String,Integer> cycles=new java.util.HashMap<>();
    private final long idleEpoch=System.nanoTime();
    private long startedAt,lastRender;
    private boolean playing,queued,currentAlternate,swished;
    private float previousYaw,previousPitch,swayX,swayY;
    private int slot=-1;
    private ItemStack held=ItemStack.EMPTY;
    private String currentKnife="Skeleton";

    public ItemInspect() {
        super("Item Inspect","Twelve detailed knives with paired, grip-aware inspections",Category.VISUALS);
        instance=this;
    }
    @Override public String getDisplayName(){return "Item Inspection";}
    @Override public java.util.List<SettingSection> getSettingSections(){
        return java.util.List.of(SettingSection.of("Item Selection",items,selectedItem,chooseItem,useHeld),
                SettingSection.of("Knife",knife,finish,reflections,size,showArm,handStyle),
                SettingSection.of("Inspection",key,inspectionStyle,duration,onEquip,sound),
                SettingSection.of("Movement",idleMotion,sway));
    }
    public void selectItem(Item item){
        if(item==null||item==Items.AIR)return;
        selectedItem.set(Registries.ITEM.getId(item).toString());items.set("Selected Item");cancel();
    }
    public boolean accepts(ItemStack stack){
        if(stack.isEmpty())return false;
        if(items.is("Any Item"))return true;
        Identifier id=Identifier.tryParse(selectedItem.get().trim());
        return id!=null&&Registries.ITEM.containsId(id)&&stack.isOf(Registries.ITEM.get(id));
    }
    private void cancel(){playing=false;queued=false;}
    @Override protected void onEnable(){cancel();slot=-1;held=ItemStack.EMPTY;lastRender=0;swayX=swayY=0;}
    @Override protected void onDisable(){cancel();}
    @Override public void onTick(){
        if(!inGame()){cancel();return;}
        ItemStack stack=mc.player.getMainHandStack();
        boolean switched=mc.player.getInventory().getSelectedSlot()!=slot||!ItemStack.areItemsEqual(stack,held);
        slot=mc.player.getInventory().getSelectedSlot();held=stack.copy();
        if(playing&&(switched||mc.player.handSwinging||mc.player.isUsingItem()||!accepts(stack)
                ||mc.currentScreen!=null||!currentKnife.equals(knife.get())))cancel();
        if(switched&&onEquip.get()&&canStart(stack))start();
        if(playing&&progress()>=1){boolean next=queued;cancel();if(next&&canStart(stack))start();}
        if(playing&&!swished&&progress()>=(currentKnife.equals("Butterfly")?.15:.24)){
            swished=true;
            if(sound.get())mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    currentKnife.equals("Bowie")||currentKnife.equals("Kukri")?1.12f:currentAlternate?1.65f:1.48f,.19f));
        }
    }
    private void start(){
        slot=mc.player.getInventory().getSelectedSlot();held=mc.player.getMainHandStack().copy();currentKnife=knife.get();
        int count=cycles.getOrDefault(currentKnife,0);cycles.put(currentKnife,count+1);
        currentAlternate=inspectionStyle.is("Alternate")||(inspectionStyle.is("Cycle")&&(count&1)!=0);
        startedAt=System.nanoTime();playing=true;queued=false;swished=false;
    }
    private void request(){
        if(playing&&currentKnife.equals(knife.get()))queued=true;else start();
    }
    public static boolean input(int code,int action){
        ItemInspect m=instance;
        if(m==null||!inGame()||!m.key.matches(code)||action==GLFW.GLFW_RELEASE||!m.canStart(mc.player.getMainHandStack()))return false;
        if(action==GLFW.GLFW_PRESS)m.request();return true;
    }
    private double progress(){return (System.nanoTime()-startedAt)/1e9/duration.get();}
    private boolean canStart(ItemStack stack){return isEnabled()&&mc.currentScreen==null&&!mc.player.isSpectator()
            &&!mc.player.isUsingItem()&&!mc.player.handSwinging&&accepts(stack);}
    public static boolean handles(ItemStack stack){
        ItemInspect m=instance;
        return m!=null&&m.isEnabled()&&inGame()&&!mc.player.isSpectator()&&m.accepts(stack)
                &&ItemStack.areItemsEqual(stack,mc.player.getMainHandStack());
    }
    public static boolean renderHand(MatrixStack pose,OrderedRenderCommandQueue queue,int light,ItemStack stack,
                                     boolean left,float equip,float swing){
        if(!handles(stack))return false;
        ItemInspect m=instance;double progress=m.progress();
        boolean animate=m.playing&&progress<1&&m.slot==mc.player.getInventory().getSelectedSlot()
                &&ItemStack.areItemsEqual(stack,m.held)&&!mc.player.handSwinging&&!mc.player.isUsingItem()
                &&mc.currentScreen==null&&m.currentKnife.equals(m.knife.get());
        double t=animate?Math.max(0,progress):0;
        var p=animate?InspectionMotion.sample(m.currentKnife,m.currentAlternate,t):InspectionMotion.Pose.REST;
        long now=System.nanoTime();float yaw=mc.player.getYaw(),pitch=mc.player.getPitch();
        double dt=m.lastRender==0?0:Math.max(.0001,Math.min(.05,(now-m.lastRender)/1e9));
        if(dt>0){
            float factor=m.sway.getFloat()/100f;
            float targetX=MathHelper.clamp(MathHelper.wrapDegrees(yaw-m.previousYaw)/(float)dt*-.00032f,-.018f,.018f)*factor;
            float targetY=MathHelper.clamp((pitch-m.previousPitch)/(float)dt*.00025f,-.014f,.014f)*factor;
            float alpha=(float)(1-Math.exp(-dt*12));m.swayX+=(targetX-m.swayX)*alpha;m.swayY+=(targetY-m.swayY)*alpha;
        }
        m.lastRender=now;m.previousYaw=yaw;m.previousPitch=pitch;
        double phase=(now-m.idleEpoch)/1e9;
        float breathe=m.idleMotion.get()?(float)Math.sin(phase*1.65)*.0018f:0;
        float rest=(float)(animate?1-Math.sin(Math.PI*t):1);
        p=new InspectionMotion.Pose(p.x()+m.swayX,p.y()+m.swayY+breathe*rest,p.z(),p.pitch(),p.yaw(),
                p.roll()+(m.idleMotion.get()?(float)Math.sin(phase*1.1)*.25f*rest:0),p.spin(),p.grip(),p.air());
        InspectionKnifeRenderer.draw(pose,queue,light,stack,m.knife.get(),p,t,animate,m.currentAlternate,left,
                m.showArm.get(),m.size.getFloat()/100f,equip,swing,m.finish.get(),m.reflections.getFloat()/100f,m.handStyle.is("Tactical Gloves"));
        return true;
    }
    public boolean inspecting(){return playing;}
    public boolean alternateInspection(){return currentAlternate;}
    public String inspectionName(){return InspectionMotion.routineName(currentKnife,currentAlternate);}
    public void inspect(){if(inGame()&&canStart(mc.player.getMainHandStack()))request();}
}
