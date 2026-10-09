package dev.maro.builder;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.Direction;

/** The normal entry point: schematic, anchor, budget and Start together on one screen. */
public final class BuilderControlScreen extends Screen {
    private final Screen parent;
    private final AutoBuilder builder;
    private int left,top,panelWidth,panelHeight,column;
    private ButtonWidget start,buy,mode,preview,restart,cancel,deposit,removeChest;
    public BuilderControlScreen(Screen parent,AutoBuilder builder){super(Text.literal("Auto Builder"));this.parent=parent;this.builder=builder;}
    @Override protected void init(){
        panelWidth=Math.min(440,width-24);panelHeight=Math.min(228,height-12);left=(width-panelWidth)/2;top=(height-panelHeight)/2;column=(panelWidth-42)/2;
        var directions=new Direction[]{Direction.WEST,Direction.EAST,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH};
        String[] labels={"X−","X+","Y−","Y+","Z−","Z+"};
        for(int i=0;i<directions.length;i++){Direction direction=directions[i];addDrawableChild(ButtonWidget.builder(Text.literal(labels[i]),b->{if(builder.origin()!=null)builder.setOrigin(builder.origin().offset(direction));}).dimensions(left+18+i*29,top+54,26,12).build());}
        var budget=new TextFieldWidget(textRenderer,left+62,top+71,column-44,17,Text.literal("AH session budget"));
        budget.setMaxLength(10);budget.setText(String.valueOf((long)builder.auctionBudget()));budget.setTextPredicate(value->value.matches("[0-9]*")&&(value.isEmpty()||Long.parseLong(value)<=1_000_000_000L));
        budget.setChangedListener(value->{try{builder.auctionBudget(value.isEmpty()?0:Long.parseLong(value));}catch(NumberFormatException ignored){}});addDrawableChild(budget);
        mode=button("",1,top+71,17,builder::cycleBuildMode);
        button("Choose Schematic",0,top+94,18,()->client.setScreen(new BuilderScreen(this,builder,false)));
        button("Materials",1,top+94,18,()->client.setScreen(new BuilderScreen(this,builder,true)));
        button("Origin: Here",0,top+114,18,()->{if(client.player!=null)builder.setOrigin(client.player.getBlockPos());});
        button("Origin: Target",1,top+114,18,()->{if(client.crosshairTarget instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK)builder.setOrigin(hit.getBlockPos().offset(hit.getSide()));});
        start=button("Start Build",0,top+134,18,builder::startBuild);
        button("Pause / Cancel Buy",1,top+134,18,()->builder.pause("Paused"));
        restart=button("Restart Build",0,top+154,18,builder::restartBuild);
        cancel=button("Cancel Schematic",1,top+154,18,()->{builder.cancelSchematic();tick();});
        buy=smallButton("Buy Missing",0,top+174,builder::buyMaterials);
        deposit=smallButton("Deposit All",1,top+174,builder::depositAll);
        smallButton("Add Chest",2,top+174,builder::markContainer);
        removeChest=smallButton("Remove Chest",3,top+174,builder::removeContainer);
        removeChest.setTooltip(Tooltip.of(Text.literal("Look at either half of a selected double chest to remove it from supplies")));
        smallButton("Saved Builds",4,top+174,()->client.setScreen(new BuilderPlacementsScreen(this,builder)));
        button("Options",1,top+194,18,()->{var gui=parent instanceof ClickGuiScreen existing?existing:new ClickGuiScreen();client.setScreen(gui);gui.openModuleOptions(builder);});
        preview=button("",0,top+194,18,builder::togglePreview);

        tick();
    }
    private ButtonWidget button(String label,int col,int y,int height,Runnable action){return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(left+18+col*(column+6),y,column,height).build());}
    private ButtonWidget smallButton(String label,int col,int y,Runnable action){int w=(panelWidth-60)/5;return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(left+18+col*(w+6),y,w,18).build());}
    @Override public void tick(){
        boolean loaded=builder.schematic()!=null&&!builder.loading();start.active=loaded&&!builder.buying()&&!builder.depositing();buy.active=loaded&&builder.auctionBudget()>0&&!builder.buying()&&!builder.depositing();preview.active=loaded;restart.active=loaded;cancel.active=loaded||builder.loading()||builder.depositing();deposit.active=!builder.buying()&&!builder.depositing();
        start.setMessage(Text.literal(builder.building()?"Resume Build":"Start Build"));mode.setMessage(Text.literal(builder.buildMode().equals("Semi Auto")?"Mode: Hold Right Click":"Mode: Automatic"));preview.setMessage(Text.literal(builder.previewVisible()?"Hide Preview":"Show Preview"));
        removeChest.active=!builder.restockContainers().isEmpty();
    }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xC50B101A);SmoothHudText.beginFrame();Render2D.shadow(ctx,left,top,panelWidth,panelHeight,12,10,0x60000000);Render2D.roundRect(ctx,left,top,panelWidth,panelHeight,12,0xFF171F2D);
        SmoothHudText.draw(ctx,"AUTO BUILDER",left+18,top+12,0xFFF0F5FF,true,1.2f);
        var schematic=builder.schematic();String name=builder.loading()?"Loading schematic…":schematic==null?"Choose a schematic to start":schematic.name+"  ·  "+schematic.width+" × "+schematic.height+" × "+schematic.length;
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,name,panelWidth-36,false,.85f),left+18,top+31,0xFFBDD0E8,false,.85f);
        SmoothHudText.draw(ctx,"Origin: "+(builder.origin()==null?"automatic":builder.origin().toShortString()),left+18,top+43,0xFF93ABC9,false,.65f);
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,builder.etaText(),panelWidth-220,false,.65f),left+202,top+43,0xFF9BDDCB,false,.65f);
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,"Chests: "+(builder.restockContainers().isEmpty()?"press R to add":builder.restockContainers().size()+" selected"),panelWidth-220,false,.65f),left+202,top+54,0xFF93ABC9,false,.65f);
        SmoothHudText.draw(ctx,"Budget",left+18,top+76,0xFFBDCEE5,false,.7f);
        String status=builder.auctionBudget()<=0&&!builder.buying()?"AH buying: enter a budget above 0":builder.status();
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,status,panelWidth-36,false,.7f),left+18,top+217,builder.buying()?0xFF7EF0C1:0xFFA4BAD5,false,.7f);
        super.render(ctx,mouseX,mouseY,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
