package dev.maro.builder;

import dev.maro.gui.render.Render2D;
import dev.maro.gui.render.SmoothHudText;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import java.nio.file.*;
import java.util.*;

/** Schematic browser and live materials list, with pagination and an editable filename filter. */
public final class BuilderScreen extends Screen {
    private final Screen parent;
    private final AutoBuilder builder;
    private final boolean materials;
    private List<Path> files=List.of();
    private List<Map.Entry<Item,Integer>> rows=List.of();
    private int page,perPage,left,top,panelWidth,panelHeight;
    private boolean compact;
    private String error="",filter="";
    public BuilderScreen(Screen parent,AutoBuilder builder,boolean materials){super(Text.literal(materials?"Build Materials":"Schematic Library"));this.parent=parent;this.builder=builder;this.materials=materials;}
    @Override protected void init(){
        panelWidth=Math.min(580,width-24);panelHeight=Math.min(410,height-24);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        compact=materials&&panelWidth<460;
        perPage=Math.max(1,(panelHeight-142)/(compact?44:30));
        if(materials){
            if(builder.schematic()!=null)rows=builder.schematic().materials().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().getName().getString())).toList();
        }else{
            var search=new TextFieldWidget(textRenderer,left+18,top+46,panelWidth-36,22,Text.literal("Filter schematics"));
            search.setMaxLength(256);search.setText(filter);search.setChangedListener(value->{filter=value;page=0;refreshFiles();rebuild();});addDrawableChild(search);
            refreshFiles();
        }
        rebuild();
    }
    private void refreshFiles(){
        try(var paths=Files.list(builder.folder())){files=paths.filter(Files::isRegularFile).filter(SchematicIO::supported).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase(Locale.ROOT))).toList();error="";}
        catch(Exception e){files=List.of();error=e.getMessage();}
    }
    private void rebuild(){
        // Retain the search widget, replacing only buttons. This also keeps typed text and focus.
        for(var child:List.copyOf(children()))if(child instanceof ButtonWidget)remove(child);
        int size=materials?rows.size():files.size();int pages=Math.max(1,(size+perPage-1)/perPage);page=Math.max(0,Math.min(page,pages-1));
        int y=top+(materials?76:82);
        if(!materials)for(int i=page*perPage;i<Math.min(size,(page+1)*perPage);i++){
            Path file=files.get(i);String label=file.getFileName().toString();if(label.length()>65)label=label.substring(0,62)+"…";
            addDrawableChild(ButtonWidget.builder(Text.literal(label),button->{builder.load(file);client.setScreen(parent);}).dimensions(left+18,y,panelWidth-36,24).build());y+=30;
        }
        if(materials)for(int i=page*perPage;i<Math.min(size,(page+1)*perPage);i++){
            Item item=rows.get(i).getKey();addDrawableChild(ButtonWidget.builder(Text.literal(builder.materialIgnored(item)?"Include":"Ignore"),button->{builder.toggleMaterialIgnored(item);rebuild();}).dimensions(left+panelWidth-78,y,60,18).build());y+=compact?44:30;
        }
        int bottom=top+panelHeight-34;
        var previous=ButtonWidget.builder(Text.literal("‹"),b->{page--;rebuild();}).dimensions(left+18,bottom,32,20).build();previous.active=page>0;addDrawableChild(previous);
        var next=ButtonWidget.builder(Text.literal("›"),b->{page++;rebuild();}).dimensions(left+58,bottom,32,20).build();next.active=page+1<pages;addDrawableChild(next);
        boolean small=panelWidth<350;
        addDrawableChild(ButtonWidget.builder(Text.literal(materials?"Copy Missing":"Open Folder"),b->{
            if(materials){StringBuilder result=new StringBuilder();for(var entry:builder.remainingMaterials().entrySet()){int missing=Math.max(0,entry.getValue()-builder.inventoryCount(entry.getKey()));if(missing>0)result.append(entry.getKey().getName().getString()).append(": ").append(missing).append('\n');}client.keyboard.setClipboard(result.toString());}
            else net.minecraft.util.Util.getOperatingSystem().open(builder.folder().toFile());
        }).dimensions(left+panelWidth-(small?172:220),bottom,small?96:112,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"),b->close()).dimensions(left+panelWidth-(small?66:98),bottom,small?56:80,20).build());
    }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xC50B101A);SmoothHudText.beginFrame();
        Render2D.shadow(ctx,left,top,panelWidth,panelHeight,12,12,0x60000000);Render2D.roundRect(ctx,left,top,panelWidth,panelHeight,12,0xFF171F2D);
        SmoothHudText.draw(ctx,title.getString(),left+18,top+16,0xFFEAF0FF,true,1.4f);
        if(materials){
            String supply=builder.sectionSupply()?" · nearby section supply":builder.layerSupply()&&builder.supplyLayer()>=0?" · layer "+(builder.supplyLayer()+1)+" supply":"";
            SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,builder.schematic()==null?"No schematic loaded":builder.schematic().name+supply,panelWidth-36,false,.9f),left+18,top+40,0xFFACBED8,false,.9f);
            SmoothHudText.draw(ctx,"MATERIAL",left+18,top+61,0xFF8D9FB7,true,.7f);
            int column=left+panelWidth-294;
            if(!compact)for(String label:new String[]{"TOTAL",builder.sectionSupply()?"BATCH":builder.layerSupply()?"LAYER":"LEFT","OWNED","MISSING"}){
                SmoothHudText.draw(ctx,label,column,top+61,0xFF8D9FB7,true,.7f);column+=52;
            }
            var remaining=builder.remainingMaterials();int y=top+79;
            for(int i=page*perPage;i<Math.min(rows.size(),(page+1)*perPage);i++){
                var entry=rows.get(i);int need=remaining.getOrDefault(entry.getKey(),0),owned=builder.inventoryCount(entry.getKey()),missing=Math.max(0,need-owned);
                Render2D.roundRect(ctx,left+12,y-4,panelWidth-24,compact?39:25,5,i%2==0?0xFF1C2839:0xFF1A2534);
                ctx.drawItem(new net.minecraft.item.ItemStack(entry.getKey()),left+18,y-1);
                SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,entry.getKey().getName().getString(),panelWidth-(compact?140:350),false,.9f),left+40,y,builder.materialIgnored(entry.getKey())?0xFF8091A8:0xFFE8F0FF,false,.9f);
                int x=left+panelWidth-294;int[] counts={entry.getValue(),need,owned,missing};
                if(compact){
                    x=left+18;String[] labels={"Total ",builder.sectionSupply()?"Batch ":builder.layerSupply()?"Layer ":"Left ","Own ","Need "};int spacing=(panelWidth-36)/4;
                    for(int c=0;c<counts.length;c++){SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,labels[c]+counts[c],spacing-3,false,.7f),x,y+19,c==3&&missing>0?0xFFFFC38B:0xFFB6D1E7,false,.7f);x+=spacing;}
                }else for(int c=0;c<counts.length;c++){SmoothHudText.draw(ctx,String.valueOf(counts[c]),x,y,c==3&&missing>0?0xFFFFC38B:0xFFB6D1E7,false,.85f);x+=52;}
                y+=compact?44:30;
            }
        }else{
            SmoothHudText.draw(ctx,".schem  ·  .schematic  ·  .litematic  ·  .nbt",left+18,top+35,0xFFACBED8,false,.8f);
            if(files.isEmpty()){
                SmoothHudText.draw(ctx,"Drop schematic files into your game's schematics folder.",left+18,top+100,0xFFCBD8EE,false,.95f);
                SmoothHudText.draw(ctx,"Use Open Folder below, then reopen this library.",left+18,top+124,0xFF94AAC7,false,.85f);
            }
        }
        String footer=error.isEmpty()?"Page "+(page+1)+"  ·  "+builder.status():error;
        SmoothHudText.draw(ctx,SmoothHudText.trim(ctx,footer,panelWidth-36,false,.75f),left+18,top+panelHeight-57,0xFF9AB0CB,false,.75f);
        super.render(ctx,mouseX,mouseY,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(parent);}
}
