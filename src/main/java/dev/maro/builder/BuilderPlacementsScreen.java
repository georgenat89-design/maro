package dev.maro.builder;

import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Named build slots independent of the active schematic or server connection. */
public final class BuilderPlacementsScreen extends Screen {
    private final Screen parent;
    private final AutoBuilder builder;
    private int left,top,w;
    private TextFieldWidget name;
    private ButtonWidget slot,load,save;
    public BuilderPlacementsScreen(Screen parent,AutoBuilder builder){super(Text.literal("Saved Builds"));this.parent=parent;this.builder=builder;}
    @Override protected void init(){
        w=Math.min(400,width-24);left=(width-w)/2;top=Math.max(6,(height-212)/2);
        addDrawableChild(ButtonWidget.builder(Text.literal("‹"),b->select(-1)).dimensions(left+18,top+37,28,20).build());
        slot=addDrawableChild(ButtonWidget.builder(Text.empty(),b->select(1)).dimensions(left+50,top+37,w-100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"),b->select(1)).dimensions(left+w-46,top+37,28,20).build());
        name=addDrawableChild(new TextFieldWidget(textRenderer,left+18,top+66,w-36,20,Text.literal("Build name")));name.setMaxLength(64);updateName();
        int half=(w-42)/2;
        save=addDrawableChild(ButtonWidget.builder(Text.literal("Save Current Placement"),b->builder.savePlacement(name.getText())).dimensions(left+18,top+143,half,20).build());
        load=addDrawableChild(ButtonWidget.builder(Text.literal("Load Saved Placement"),b->builder.loadPlacement()).dimensions(left+24+half,top+143,half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close()).dimensions(left+18,top+172,w-36,20).build());tick();
    }
    private void select(int direction){builder.cycleBuildSlot(direction);updateName();tick();}
    private void updateName(){var info=builder.buildSlotInfo();name.setText(info==null?builder.placementName():info.get("name").getAsString());}
    @Override public void tick(){slot.setMessage(Text.literal(builder.selectedBuildSlot()==0?"Last Session":"Build Slot "+builder.selectedBuildSlot()));load.active=builder.buildSlotInfo()!=null&&!builder.loading();save.active=builder.schematic()!=null&&!builder.loading();}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xC50B101A);SmoothHudText.beginFrame();Render2D.roundRect(ctx,left,top,w,204,12,0xFF171F2D);
        SmoothHudText.draw(ctx,"SAVED BUILDS",left+18,top+13,0xFFF0F5FF,true,1.1f);
        var info=builder.buildSlotInfo();String detail=info==null?"Empty slot — save the current placement here":info.get("schematic-name").getAsString();
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,detail,w-36,false,.8f),left+18,top+95,0xFFBDD0E8,false,.8f);
        if(info!=null){
            String where=info.get("world-scope").getAsString()+"  ·  "+info.get("origin");
            SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,where,w-36,false,.65f),left+18,top+110,0xFF93ABC9,false,.65f);
            SmoothHudText.draw(ctx,info.get("correct")+" / "+info.get("total")+" blocks  ·  "+info.get("rotation").getAsString()+"°  ·  "+info.get("mirror").getAsString()+" mirror",left+18,top+124,0xFF93ABC9,false,.65f);
        }
        super.render(ctx,mouseX,mouseY,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
