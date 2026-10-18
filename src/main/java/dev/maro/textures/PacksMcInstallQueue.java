package dev.maro.textures;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/** A bounded snapshot: each pack is saved or skipped once, with existing ZIPs kept. */
public final class PacksMcInstallQueue {
    private final List<PacksMcCatalog.Pack> pending;
    private final int total,existing;
    private int index,downloaded,skipped;
    public PacksMcInstallQueue(List<PacksMcCatalog.Pack> catalog,Predicate<PacksMcCatalog.Pack> cached){
        var unique=new HashSet<String>();var list=new ArrayList<PacksMcCatalog.Pack>();int count=0,ready=0;
        for(var pack:catalog.subList(0,Math.min(PacksMcCatalog.LIMIT,catalog.size())))if(unique.add(pack.id())){
            count++;if(cached.test(pack))ready++;else list.add(pack);
        }
        pending=List.copyOf(list);total=count;existing=ready;
    }
    public PacksMcCatalog.Pack current(){return index<pending.size()?pending.get(index):null;}
    public int total(){return total;}
    public int saved(){return existing+downloaded;}
    public int skipped(){return skipped;}
    public int processed(){return existing+index;}
    public String progress(){return saved()+" / "+total+" saved"+(skipped>0?" · "+skipped+" skipped":"");}
    public void downloaded(){if(current()!=null){downloaded++;index++;}}
    public void skip(){if(current()!=null){skipped++;index++;}}
}
