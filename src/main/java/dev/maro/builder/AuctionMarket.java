package dev.maro.builder;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.entity.player.PlayerInventory;
import java.util.*;
import java.util.regex.*;

/** Reads server lore; parsing failures never become zero-price offers. */
public final class AuctionMarket {
    private static final Pattern AMOUNT=Pattern.compile("(?i)^\\s*[:=]?\\s*[$€£]?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*([kmb])?(?![\\p{Alnum}.,])");
    private static final Pattern PAGE=Pattern.compile("(?i)\\bpage\\s*[:#]?\\s*(\\d+)\\s*(?:/|of)\\s*(\\d+)\\b");
    public record Offer(int slot,Item item,int count,double total){public double each(){return total/count;}}
    private AuctionMarket(){}
    public static double price(String text,String keyword){
        if(text==null||keyword==null||keyword.isBlank())return Double.NaN;
        String clean=text.replaceAll("§.","");int position=clean.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        if(position<0)return Double.NaN;
        if(clean.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT),position+keyword.length())>=0)return Double.NaN;
        Matcher match=AMOUNT.matcher(clean.substring(position+keyword.length()));
        if(!match.lookingAt())return Double.NaN;
        try{
            double amount=Double.parseDouble(match.group(1).replace(",",""));
            if(match.group(2)!=null)amount*=switch(match.group(2).toLowerCase(Locale.ROOT)){case "k"->1000;case "m"->1_000_000;default->1_000_000_000;};
            return amount>0&&Double.isFinite(amount)?amount:Double.NaN;
        }catch(NumberFormatException e){return Double.NaN;}
    }
    public static double price(ItemStack stack,String keyword){
        var lore=stack.get(DataComponentTypes.LORE);if(lore==null)return Double.NaN;
        double found=Double.NaN;
        for(var line:lore.lines()){
            double value=price(line.getString(),keyword);
            if(Double.isFinite(value)){if(Double.isFinite(found)&&Math.abs(found-value)>.001)return Double.NaN;found=value;}
        }
        return found;
    }
    public static List<Offer> offers(ScreenHandler handler,PlayerInventory inventory,Item item,String keyword){
        List<Offer> result=new ArrayList<>();
        for(var slot:handler.slots){
            var stack=slot.getStack();if(slot.inventory==inventory||!stack.isOf(item))continue;
            double value=price(stack,keyword);
            if(Double.isFinite(value))result.add(new Offer(slot.id,item,stack.getCount(),value));
        }
        return result;
    }
    public static boolean hasPriceField(ItemStack stack,String keyword){
        var lore=stack.get(DataComponentTypes.LORE);return lore!=null&&!keyword.isBlank()&&lore.lines().stream().anyMatch(line->line.getString().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));
    }
    public static Offer choose(List<Offer> offers,int needed,int overbuy,double perItem,double budget,boolean preferStacks,double tolerance){
        var valid=offers.stream().filter(o->o.count>0&&o.count<=needed+overbuy&&o.total<=budget&&(!Double.isFinite(perItem)||perItem<=0||o.each()<=perItem)).toList();
        Offer cheapest=valid.stream().min(Comparator.comparingDouble(Offer::each).thenComparingDouble(Offer::total)).orElse(null);
        // Bulk requests buy full stacks first, including across different pages.
        if(cheapest!=null&&needed>=cheapest.item.getMaxCount()&&cheapest.item.getMaxCount()>1)
            return valid.stream().filter(o->o.count>=o.item.getMaxCount())
                .min(Comparator.comparingDouble(Offer::each).thenComparingDouble(Offer::total)).orElse(cheapest);
        if(cheapest==null||!preferStacks)return cheapest;
        return valid.stream().filter(o->o.count>=Math.min(64,needed)&&o.each()<=cheapest.each()*(1+tolerance/100))
            .min(Comparator.comparingDouble(Offer::each).thenComparingDouble(Offer::total)).orElse(cheapest);
    }
    public static boolean better(Offer candidate,Offer current,int needed){
        if(current==null)return true;
        int size=candidate.item.getMaxCount();
        if(size>1&&needed>=size&&(candidate.count>=size)!=(current.count>=size))return candidate.count>=size;
        return candidate.each()<current.each()||candidate.each()==current.each()&&candidate.total<current.total;
    }
    /** Zero means the server did not publish a usable page count. */
    public static int pageCount(String text){
        if(text==null)return 0;var match=PAGE.matcher(text.replaceAll("§.",""));
        if(!match.find())return 0;
        try{int current=Integer.parseInt(match.group(1)),total=Integer.parseInt(match.group(2));return current>=1&&current<=total?total:0;}
        catch(NumberFormatException ignored){return 0;}
    }
    public static boolean disabledNext(ItemStack stack){
        String text=stack.getName().getString();var lore=stack.get(DataComponentTypes.LORE);
        if(lore!=null)for(var line:lore.lines())text+=" "+line.getString();
        return word(text,"no next page;no more pages;last page;final page;next page unavailable;next page is unavailable;disabled");
    }
    public static boolean word(String label,String words){
        if(label==null)return false;String value=label.toLowerCase(Locale.ROOT);
        return Arrays.stream(words.split(";")).map(String::strip).filter(s->!s.isEmpty()).anyMatch(s->value.contains(s.toLowerCase(Locale.ROOT)));
    }
    public static boolean unavailable(String message){
        if(message==null)return false;
        String clean=message.replaceAll("§.","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        return clean.matches(".*(?:already (?:been )?(?:purchased|bought|sold)|(?:item|listing|auction) (?:was |has been )?sold|(?:item|listing|auction) (?:is |has )?(?:no longer available|expired)).*");
    }
    public static String listingIdentity(ItemStack stack){
        StringBuilder key=new StringBuilder(stack.getName().getString());var lore=stack.get(DataComponentTypes.LORE);
        if(lore!=null)for(var line:lore.lines()){
            String text=line.getString().replaceAll("§.","");String lower=text.toLowerCase(Locale.ROOT);
            if(lower.matches(".*(?:expir|time left|time remaining|remaining time|ends in|listed .*ago).*"))continue;
            key.append('|').append(text);
        }
        return key.toString();
    }
}
