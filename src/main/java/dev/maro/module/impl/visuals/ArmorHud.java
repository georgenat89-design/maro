package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.HudElement;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import dev.maro.util.Animation;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * A single equipment card with readable condition indicators, grouped hand slots,
 * and native item rendering. Slot metadata and durability animations are reused each frame.
 */
public class ArmorHud extends Module implements HudElement {
    private static final int CELL = 30, GAP = 3, PAD = 6, MARGIN = 4, COLUMN_WIDTH = 104, COLUMN_HEIGHT = 29;
    private static final float RADIUS = 8, ITEM_SIZE = 20;
    private record Piece(EquipmentSlot slot,Identifier empty,String label) { }
    private static final Piece[] SLOTS = {
            new Piece(EquipmentSlot.MAINHAND,null,"Main hand"),
            new Piece(EquipmentSlot.HEAD,Identifier.ofVanilla("container/slot/helmet"),"Helmet"),
            new Piece(EquipmentSlot.CHEST,Identifier.ofVanilla("container/slot/chestplate"),"Chestplate"),
            new Piece(EquipmentSlot.LEGS,Identifier.ofVanilla("container/slot/leggings"),"Leggings"),
            new Piece(EquipmentSlot.FEET,Identifier.ofVanilla("container/slot/boots"),"Boots"),
            new Piece(EquipmentSlot.OFFHAND,Identifier.ofVanilla("container/slot/shield"),"Offhand")};
    private final Animation[] condition = new Animation[SLOTS.length];
    private final Item[] previousItem = new Item[SLOTS.length];

    private final ButtonSetting position = add(new ButtonSetting("Position", "Drag it where you want it and scroll to resize it (it starts beside the hotbar)", "Place",
            () -> mc.setScreen(new HudPlacementScreen(mc.currentScreen, this))));
    private final NumberSetting x = add(new NumberSetting("X", "Across the screen: 0 is the left edge, 100 the right", 50, 0, 100, 0.5).suffix("%"));
    private final NumberSetting y = add(new NumberSetting("Y", "Down the screen: 0 is the top, 100 the bottom", 86, 0, 100, 0.5).suffix("%"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "How big it is", 1, 0.5, 2.5, 0.05).suffix("x"));
    /** Dragged somewhere by you; until then it sits beside the hotbar. */
    private final BooleanSetting placed = add(new BooleanSetting("Placed", "", false).visible(() -> false));

    private final ModeSetting layout = add(new ModeSetting("Layout", "A row or a column", "Row", "Row", "Column"));
    private final ModeSetting durability = add(new ModeSetting("Durability", "A thin bar, the percent left, both, or nothing",
            "Bar & Percent", "Bar & Percent", "Bar", "Percent", "Off"));
    private final BooleanSetting offHand = add(new BooleanSetting("Off Hand", "Your off hand (a totem or shield) after the armour", true));
    private final BooleanSetting mainHand = add(new BooleanSetting("Main Hand", "What you hold, before the armour", false));
    private final BooleanSetting showEmpty = add(new BooleanSetting("Show Empty", "Faint outlines where nothing is worn", true));
    private final BooleanSetting warn = add(new BooleanSetting("Low Warning", "A subtle red highlight when equipment is about to break", true));
    private final NumberSetting warnAt = add(new NumberSetting("Warn Below", "How low counts as about to break", 15, 1, 50, 1).suffix("%").visible(warn::get));
    private final BooleanSetting panel = add(new BooleanSetting("Panel", "A dark card behind it all", true));
    private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "How solid the card and cells are", 85, 0, 100, 1).suffix("%"));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Look", layout, durability, offHand, mainHand, showEmpty, warn, warnAt, panel, opacity),
            SettingSection.of("Placement", position, x, y, scale));

    public ArmorHud() {
        super("Armor HUD", "A clean equipment card with crisp icons, readable durability and low condition warnings", Category.VISUALS);
        for(int i=0;i<condition.length;i++)condition[i]=new Animation(18,0);
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    private int visibleSlots() {
        PlayerEntity p = mc.player;
        int mask=0;
        for(int i=0;i<SLOTS.length;i++){
            if(i==0&&!mainHand.get()||i==5&&!offHand.get())continue;
            if(p==null||showEmpty.get()||!p.getEquippedStack(SLOTS[i].slot()).isEmpty())mask|=1<<i;
        }
        return mask;
    }

    private boolean bar() {
        return durability.is("Bar") || durability.is("Bar & Percent");
    }

    private boolean percent() {
        return durability.is("Percent") || durability.is("Bar & Percent");
    }

    private int cellHeight() {
        return 26+(percent()?10:0)+(bar()?5:0);
    }

    private boolean row() {
        return layout.is("Row");
    }

    private static boolean handBoundary(int before,int current){
        return before==0&&current<5||before>0&&current==5;
    }
    private static int gap(int before,int current){return handBoundary(before,current)?8:GAP;}
    private int panelWidth(int mask) {
        if(!row())return PAD*2+COLUMN_WIDTH;
        int width=PAD*2,previous=-1;
        for(int i=0;i<SLOTS.length;i++)if((mask&(1<<i))!=0){width+=CELL+(previous<0?0:gap(previous,i));previous=i;}
        return mask==0?PAD*2+CELL:width;
    }
    private int panelHeight(int mask) {
        return PAD*2+(row()?cellHeight():Math.max(1,Integer.bitCount(mask))*(COLUMN_HEIGHT+GAP)-GAP);
    }

    // ---- placement ----------------------------------------------------------------------

    @Override
    public String hudName() {
        return getName();
    }

    @Override
    public float hudScale() {
        int mask=visibleSlots();float w=panelWidth(mask),h=panelHeight(mask);
        float sw=mc.getWindow().getScaledWidth(),sh=mc.getWindow().getScaledHeight();
        float fitted=Math.min(scale.getFloat(),Math.min(Math.max(1,sw-MARGIN*2)/w,Math.max(1,sh-MARGIN*2)/h));
        if(!placed.get()&&!besideHotbar(w*fitted))fitted=Math.min(fitted,Math.max(1,sh-50-MARGIN)/h);
        return fitted;
    }

    @Override
    public float hudWidth() {
        return panelWidth(visibleSlots()) * hudScale();
    }

    @Override
    public float hudHeight() {
        return panelHeight(visibleSlots()) * hudScale();
    }

    private float roomX() {
        return Math.max(0, mc.getWindow().getScaledWidth() - hudWidth() - MARGIN * 2);
    }

    private float roomY() {
        return Math.max(0, mc.getWindow().getScaledHeight() - hudHeight() - MARGIN * 2);
    }

    /** Beside the right end of the hotbar, bottoms level; above the health bars when there is no room there. */
    private boolean besideHotbar(float width) {
        return mc.getWindow().getScaledWidth() / 2f + 97 + width <= mc.getWindow().getScaledWidth() - MARGIN;
    }

    @Override
    public float hudLeft() {
        if (!placed.get()) {
            int sw = mc.getWindow().getScaledWidth();
            return besideHotbar(hudWidth()) ? sw / 2f + 97 : Math.round((sw - hudWidth()) / 2f);
        }
        return MARGIN + Math.round(roomX() * x.getFloat() / 100f);
    }

    @Override
    public float hudTop() {
        if (!placed.get()) {
            int sh = mc.getWindow().getScaledHeight();
            return besideHotbar(hudWidth()) ? sh - hudHeight() - MARGIN : Math.max(MARGIN,sh - 50 - hudHeight());
        }
        return MARGIN + Math.round(roomY() * y.getFloat() / 100f);
    }

    @Override
    public void hudMove(float left, float top) {
        placed.set(true);
        float rx = roomX(), ry = roomY();
        x.set(rx <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (left - MARGIN) / rx * 100.0)));
        y.set(ry <= 0 ? 0.0 : Math.max(0.0, Math.min(100.0, (top - MARGIN) / ry * 100.0)));
    }

    @Override
    public void hudResize(float by) {
        scale.set(Math.max(scale.getMin(), Math.min(scale.getMax(), Math.round((scale.get() + by) * 100) / 100.0)));
    }

    @Override
    public void hudReset() {
        placed.reset();
        x.reset();
        y.reset();
        scale.reset();
    }

    // ---- drawing ------------------------------------------------------------------------

    /** How much is left, 0 to 1; -1 for things that do not wear out. */
    public static float left(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) return -1;
        return Math.clamp(1 - stack.getDamage() / (float) stack.getMaxDamage(),0,1);
    }

    /** Green when whole, through yellow, to red when nearly gone. */
    public static int wearColor(float left) {
        left=Math.clamp(Float.isFinite(left)?left:0,0,1);
        int green = 0xFF3DDC97, yellow = 0xFFFFC23D, red = 0xFFFF5D6C;
        return left > 0.5f ? ColorUtil.lerp(yellow, green, (left - 0.5f) * 2) : ColorUtil.lerp(red, yellow, left * 2);
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (!inGame() || mc.options.hudHidden || dev.maro.module.impl.misc.InventoryHider.hidesHotbar()) return;
        int mask=visibleSlots();
        if(mask==0)return;
        float s = hudScale();
        int w = panelWidth(mask), h = panelHeight(mask);
        float hair = Render2D.hairline(ctx, 0.5f * s) / s;
        float strength = opacity.getFloat() / 100f;

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(hudLeft(), hudTop());
        matrices.scale(s, s);
        Fonts.beginRaw();
        try {
            if (panel.get()) {
                Render2D.shadow(ctx, 0, 1, w, h, RADIUS, 7, ColorUtil.withAlpha(0xFF000000, Math.round(90 * strength)));
                Render2D.roundGradientV(ctx,0,0,w,h,RADIUS,ColorUtil.withAlpha(0xFF1B2230,Math.round(240*strength)),ColorUtil.withAlpha(0xFF0D121B,Math.round(240*strength)));
                Render2D.roundOutline(ctx, 0, 0, w, h, RADIUS, hair, ColorUtil.withAlpha(0xFFB6C8E5,Math.round(28*strength)));
            }
            float cursor=PAD;int previous=-1;
            for(int i=0;i<SLOTS.length;i++){
                if((mask&(1<<i))==0){previousItem[i]=null;continue;}
                if(previous>=0&&row()){
                    int space=gap(previous,i);
                    if(handBoundary(previous,i))Render2D.rect(ctx,cursor+space/2-.25f,PAD+4,.5f,cellHeight()-8,ColorUtil.withAlpha(0xFFB6C8E5,Math.round(30*strength)));
                    cursor+=space;
                }
                cell(ctx,i,row()?cursor:PAD,row()?PAD:cursor,hair,strength);
                cursor+=row()?CELL:COLUMN_HEIGHT+GAP;previous=i;
            }
        } finally {
            Fonts.endRaw();
            matrices.popMatrix();
        }
    }

    @Override protected void onEnable(){java.util.Arrays.fill(previousItem,null);}

    private void cell(DrawContext ctx, int index, float cx, float cy, float hair, float strength) {
        Piece piece=SLOTS[index];ItemStack stack=mc.player.getEquippedStack(piece.slot());
        float wear = left(stack);
        boolean low = warn.get() && wear >= 0 && wear * 100 < warnAt.getFloat();
        float wellX=cx+(row()?3:0),wellY=cy+1;
        Render2D.roundGradientV(ctx,wellX,wellY,24,24,5,ColorUtil.withAlpha(0xFFFFFFFF,Math.round((stack.isEmpty()?5:13)*strength)),ColorUtil.withAlpha(0xFFFFFFFF,Math.round(4*strength)));
        if(low){
            float pulse=.8f+.2f*(float)Math.sin(System.nanoTime()/450_000_000d);
            Render2D.roundRect(ctx,wellX,wellY,24,24,5,ColorUtil.withAlpha(Theme.RED,Math.round(24*strength*pulse)));
            Render2D.roundOutline(ctx,wellX,wellY,24,24,5,hair,ColorUtil.withAlpha(Theme.RED,Math.round(130*strength*pulse)));
        }
        float iconX=wellX+2,iconY=wellY+2;
        var matrices=ctx.getMatrices();matrices.pushMatrix();matrices.translate(iconX,iconY);matrices.scale(ITEM_SIZE/16,ITEM_SIZE/16);
        if(stack.isEmpty()){
            if(piece.empty()!=null)ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED,piece.empty(),0,0,16,16,0x50FFFFFF);
        }else ctx.drawItem(stack,0,0);
        matrices.popMatrix();
        if(stack.getCount()>1){
            String count=Integer.toString(stack.getCount());float width=Fonts.width(ctx,count,true,.62f)+5;
            Render2D.roundRect(ctx,wellX+26-width,wellY+17,width,9,3,0xF00C111A);
            Fonts.drawRight(ctx,count,wellX+24,wellY+21.5f,0xFFF1F5FC,true,.62f);
        }
        int color=wearColor(wear),ink=low?Theme.RED:ColorUtil.lerp(color,0xFFF1F5FC,.8f);
        float amount=0;
        if(wear>=0){
            if(previousItem[index]!=stack.getItem()||wear==0)condition[index].snap(wear);
            amount=condition[index].update(wear);previousItem[index]=stack.getItem();
        }else previousItem[index]=null;
        if(row()){
            if(percent())Fonts.drawCentered(ctx,wear>=0?Math.round(wear*100)+"%":stack.isEmpty()?"—":index==5?"Offhand":"Held",cx+CELL/2f,cy+31,wear>=0?ink:0xFF8997AB,wear>=0,wear>=0?.72f:.56f);
            if(bar()&&wear>=0)meter(ctx,cx+3,cy+(percent()?38:28),24,amount,color);
        }else{
            Fonts.drawV(ctx,piece.label(),cx+32,cy+8,stack.isEmpty()?0xFF7B899D:0xFFDBE4F3,true,.67f);
            if(percent()&&wear>=0)Fonts.drawRight(ctx,Math.round(wear*100)+"%",cx+COLUMN_WIDTH-2,cy+8,ink,true,.7f);
            if(bar()&&wear>=0)meter(ctx,cx+32,cy+19,COLUMN_WIDTH-35,amount,color);
            else if(wear<0)Fonts.drawV(ctx,stack.isEmpty()?"Empty":stack.getCount()>1?stack.getCount()+" items":"Equipped",cx+32,cy+20,0xFF8997AB,false,.59f);
        }
    }

    private static void meter(DrawContext ctx,float x,float y,float width,float amount,int color){
        Render2D.roundRect(ctx,x,y,width,2,1,0x28C1D3EE);
        if(amount>0)Render2D.roundRect(ctx,x,y,width*amount,2,Math.min(1,width*amount/2),color);
    }
}
