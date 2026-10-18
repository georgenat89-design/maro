package dev.maro.gui.render;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleSections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Small semantic glyphs for module rows and their inspector. No emoji font dependency. */
public final class ModuleIcons {
    private static final Map<Module,Icons.Icon> CACHE=new IdentityHashMap<>();
    private ModuleIcons(){}
    private static final Icons.Icon PERSON=(c,x,y,s,ink,h)->{
        float t=s*.08f;
        Render2D.ring(c,x,y-s*.27f,s*.18f,t,ink);
        Render2D.arc(c,x,y+s*.28f,s*.35f,t,180,180,ink,ink);
        Render2D.line(c,x-s*.35f,y+s*.28f,x-s*.35f,y+s*.5f,t,ink);
        Render2D.line(c,x+s*.35f,y+s*.28f,x+s*.35f,y+s*.5f,t,ink);
    };
    private static final Icons.Icon WINGS=(c,x,y,s,ink,h)->{
        float t=s*.08f;
        Render2D.line(c,x,y-s*.18f,x,y+s*.48f,t,ink);
        for(int side:new int[]{-1,1}){
            Render2D.arc(c,x+side*s*.25f,y-s*.19f,s*.26f,t,side<0?65:245,245,ink,ink);
            Render2D.arc(c,x+side*s*.2f,y+s*.25f,s*.2f,t,side<0?0:180,180,ink,ink);
            Render2D.line(c,x,y,x+side*s*.35f,y+s*.36f,t,ink);
        }
    };
    private static final Icons.Icon GHOST=(c,x,y,s,ink,h)->{
        float t=s*.08f;
        Render2D.arc(c,x,y-s*.12f,s*.36f,t,180,180,ink,ink);
        Render2D.line(c,x-s*.36f,y-s*.12f,x-s*.36f,y+s*.45f,t,ink);
        Render2D.line(c,x+s*.36f,y-s*.12f,x+s*.36f,y+s*.45f,t,ink);
        for(int i=0;i<4;i++){float a=x+s*(-.36f+i*.18f),b=a+s*.18f;Render2D.line(c,a,y+s*(i%2==0?.45f:.3f),b,y+s*(i%2==0?.3f:.45f),t,ink);}
        Render2D.circle(c,x-s*.13f,y-s*.04f,s*.045f,ink);Render2D.circle(c,x+s*.13f,y-s*.04f,s*.045f,ink);
    };
    public static Icons.Icon of(Module module){return CACHE.computeIfAbsent(module,ModuleIcons::choose);}
    private static Icons.Icon choose(Module m){
        String name=m.getName().toLowerCase(Locale.ROOT),section=ModuleSections.of(m).id();
        if(name.contains("ghost")||name.contains("afterimage"))return GHOST;
        if(name.contains("totem")||name.contains("armor")||name.contains("staff")||name.contains("protect"))return Icons.SHIELD;
        if(name.contains("swing")||name.contains("inspect")||name.contains("motion blur"))return WINGS;
        if(name.contains("free look")||name.contains("free cam")||name.contains("no render"))return Icons.VISUALS;
        if(name.contains("fov")||name.contains("model")||name.contains("player")||name.contains("jump")||name.contains("footstep"))return PERSON;
        if(name.contains("radar")||name.contains("compass")||name.contains("map")||name.contains("coord"))return Icons.PIN;
        if(name.contains("keyboard")||name.contains("key sounds"))return Icons.KEY;
        if(name.contains("spotify")||name.contains("emote"))return Icons.PLAY;
        if(name.contains("sky")||name.contains("bright")||name.contains("color")||name.contains("bloom"))return Icons.THEME;
        if(section.endsWith("/characters")||section.endsWith("/awareness"))return PERSON;
        if(section.endsWith("/items"))return WINGS;
        if(section.endsWith("/scanning")||section.endsWith("/building")||section.endsWith("/inventory")||section.endsWith("/world"))return Icons.MISC;
        if(section.endsWith("/hud"))return Icons.LAYOUT;
        return switch(m.getCategory()){case COMBAT->Icons.COMBAT;case MOVEMENT->Icons.MOVEMENT;case PLAYER->PERSON;case VISUALS->PERSON;case MISC->Icons.MISC;case ANTI_CHEAT_OFF->Icons.SHIELD;};
    }
    public static boolean entities(Module m){String name=m.getName().toLowerCase(Locale.ROOT),section=ModuleSections.of(m).id();return section.endsWith("/characters")||section.endsWith("/awareness")||name.contains("player")||name.contains("totem");}
    public static boolean motion(Module m){String name=m.getName().toLowerCase(Locale.ROOT);return m.getCategory()==Category.MOVEMENT||name.contains("swing")||name.contains("jump")||name.contains("motion")||name.contains("spin")||name.contains("acrobat")||name.contains("afterimage")||name.contains("footstep");}
}
