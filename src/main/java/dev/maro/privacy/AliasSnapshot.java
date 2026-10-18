package dev.maro.privacy;

import net.minecraft.text.*;
import java.util.*;

/** One-pass whole-account-name matching, with bounded caches replaced on every live edit. */
public final class AliasSnapshot {
    private final Map<String,String> aliases;
    private final Map<String,String> strings=new LinkedHashMap<>(128,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>2048;}};
    private final IdentityHashMap<OrderedText,OrderedText> ordered=new IdentityHashMap<>();
    private record Protected(OrderedText text) implements OrderedText {public boolean accept(CharacterVisitor visitor){return text.accept(visitor);}}
    public static OrderedText protect(OrderedText text){return text instanceof Protected?text:new Protected(text);}
    public AliasSnapshot(Map<String,String> aliases){this.aliases=Map.copyOf(aliases);}
    private static boolean word(char c){return c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_';}
    public synchronized String mask(String text){
        if(text==null||aliases.isEmpty()||text.isEmpty())return text;var cached=strings.get(text);if(cached!=null)return cached;
        StringBuilder out=null;int copied=0;
        for(int i=0;i<text.length();){if(text.charAt(i)=='§'&&i+1<text.length()){i+=2;continue;}if(!word(text.charAt(i))){i++;continue;}int start=i;while(i<text.length()&&word(text.charAt(i)))i++;
            if(i-start>16)continue;String alias=aliases.get(text.substring(start,i).toLowerCase(Locale.ROOT));if(alias==null)continue;
            if(out==null)out=new StringBuilder(text.length()+16);out.append(text,copied,start).append(alias);copied=i;
        }
        String result=out==null?text:out.append(text,copied,text.length()).toString();if(text.length()<=4096)strings.put(text,result);return result;
    }
    public synchronized OrderedText mask(OrderedText input){
        if(input==null||aliases.isEmpty()||input instanceof Protected)return input;var cached=ordered.get(input);if(cached!=null)return cached;
        var raw=new StringBuilder();var styles=new ArrayList<Style>();
        input.accept((index,style,cp)->{raw.appendCodePoint(cp);for(int i=0;i<Character.charCount(cp);i++)styles.add(style);return true;});
        String source=raw.toString();var parts=new ArrayList<OrderedText>();int copied=0;
        for(int i=0;i<source.length();){if(!word(source.charAt(i))){i++;continue;}int start=i;while(i<source.length()&&word(source.charAt(i)))i++;
            if(i-start>16)continue;String alias=aliases.get(source.substring(start,i).toLowerCase(Locale.ROOT));if(alias==null)continue;
            append(parts,source,styles,copied,start);parts.add(OrderedText.styledForwardsVisitedString(alias,styles.get(start)));copied=i;
        }
        OrderedText result=input;
        if(copied>0){append(parts,source,styles,copied,source.length());result=new Protected(OrderedText.concat(parts));}
        if(ordered.size()>=1024)ordered.clear();ordered.put(input,result);return result;
    }
    private static void append(List<OrderedText> parts,String source,List<Style> styles,int start,int end){while(start<end){int stop=start+1;while(stop<end&&styles.get(start).equals(styles.get(stop)))stop++;parts.add(OrderedText.styledForwardsVisitedString(source.substring(start,stop),styles.get(start)));start=stop;}}
    public StringVisitable mask(StringVisitable input){
        if(input==null||aliases.isEmpty())return input;String raw=input.getString();if(mask(raw).equals(raw))return input;
        var parts=new ArrayList<OrderedText>();input.visit((style,part)->{parts.add(OrderedText.styledForwardsVisitedString(part,style));return Optional.empty();},Style.EMPTY);
        var out=Text.empty();var run=new StringBuilder();Style[] previous={null};
        mask(OrderedText.concat(parts)).accept((index,style,cp)->{if(previous[0]!=null&&!previous[0].equals(style)){out.append(Text.literal(run.toString()).setStyle(previous[0]));run.setLength(0);}previous[0]=style;run.appendCodePoint(cp);return true;});
        if(!run.isEmpty())out.append(Text.literal(run.toString()).setStyle(previous[0]));return out;
    }
    public synchronized int stringCacheSize(){return strings.size();}
}
