package dev.maro.gui.widget;

import com.google.gson.*;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.ModuleSections;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

/** Shared folding state and section controls for Panel and Dropdown. */
public final class ModuleSectionHeaders {
    private static final Set<String> CLOSED=new HashSet<>();
    private ModuleSectionHeaders() { }
    public static boolean expanded(ModuleSections.Section section,boolean searching){return searching||!CLOSED.contains(section.id());}
    public static void toggle(ModuleSections.Section section){if(!CLOSED.remove(section.id()))CLOSED.add(section.id());}
    public static void reset(){CLOSED.clear();}
    public static void expandAll(boolean expand){if(expand)CLOSED.clear();else ModuleSections.all().forEach(s->CLOSED.add(s.id()));}
    public static JsonArray save(){var json=new JsonArray();CLOSED.stream().sorted().forEach(json::add);return json;}
    public static void load(JsonElement json){
        CLOSED.clear();if(json==null||!json.isJsonArray())return;
        var known=new HashSet<String>();ModuleSections.all().forEach(s->known.add(s.id()));
        for(JsonElement value:json.getAsJsonArray())if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&known.contains(value.getAsString()))CLOSED.add(value.getAsString());
    }
    public static void draw(ClickGuiScreen gui,DrawContext ctx,ModuleSections.Group group,float x,float y,float w,float h,boolean searching,boolean compact,boolean category,Runnable changed){
        var section=group.section();boolean open=expanded(section,searching),over=gui.hovered(x,y,w,h);
        Render2D.roundRect(ctx,x,y,w,h,3,over?Theme.accent(0x20):0x124C5668);
        if(open){Render2D.line(ctx,x+7,y+h/2-1.5f,x+9.5f,y+h/2+1,1,Theme.accent());Render2D.line(ctx,x+9.5f,y+h/2+1,x+12,y+h/2-1.5f,1,Theme.accent());}
        else Icons.CHEVRON_RIGHT.draw(ctx,x+9,y+h/2,6,Theme.accent(),0);
        String title=compact?section.compactTitle():section.title();if(category)title=section.category().getDisplayName()+" / "+title;
        float size=compact?.63f:.76f;
        Fonts.drawV(ctx,Fonts.trim(title,w-38,true,size),x+17,y+h/2,Theme.TEXT_DIM,true,size);
        Fonts.drawRight(ctx,Integer.toString(group.modules().size()),x+w-7,y+h/2,Theme.accent(),false,compact?.60f:.69f);
        gui.hit(x,y,w,h,(button,mx,my)->{if(button==0&&!searching){toggle(section);changed.run();Sounds.click();}});
        if(over)gui.tooltip(section.title()+": "+section.description()+(searching?" Search results stay open.":" Click to "+(open?"fold.":"expand.")));
    }
    public static void controls(ClickGuiScreen gui,DrawContext ctx,float x,float y,float w,Runnable changed){
        float half=(w-4)/2;
        for(int i=0;i<2;i++){
            boolean expand=i==0;float bx=x+i*(half+4);String label=w<120?(expand?"Open all":"Fold all"):(expand?"Expand all":"Collapse all");
            boolean hover=gui.hovered(bx,y,half,18);
            Render2D.roundRect(ctx,bx,y,half,18,4,hover?Theme.accent(0x23):0xE0141B28);
            Fonts.drawCentered(ctx,label,bx+half/2,y+9,hover?Theme.accent():Theme.TEXT_MUTED,false,w<120?.52f:.61f);
            gui.hit(bx,y,half,18,(button,mx,my)->{if(button==0){expandAll(expand);changed.run();Sounds.click();}});
        }
    }
}
