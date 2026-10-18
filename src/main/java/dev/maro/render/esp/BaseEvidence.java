package dev.maro.render.esp;

import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import java.util.*;

/** Evidence is collected from copied section palettes, never chest contents or hidden server data. */
public final class BaseEvidence {
    public record Cell(int x,int y,int z,int natural,int utilities,int materials,int kinds,int tuff,int copper,int sculk,int tiles,int ancientDetails) {
        boolean inside(Box b){return x+4>=b.minX&&x-4<=b.maxX&&y+4>=b.minY&&y-4<=b.maxY&&z+4>=b.minZ&&z-4<=b.maxZ;}
    }
    public record Assessment(boolean generated,boolean playerBuilt,int utilities,int materials) {}
    private record Key(int x,int y,int z) {}
    private final Map<Key,int[]> cells=new HashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Block,Integer> KINDS=new java.util.concurrent.ConcurrentHashMap<>();
    public static int kind(Block b) {return KINDS.computeIfAbsent(b,BaseEvidence::classify);}
    private static int classify(Block b) {
        if(b==Blocks.TRIAL_SPAWNER||b==Blocks.VAULT||b==Blocks.SCULK_SHRIEKER||b==Blocks.SCULK_SENSOR||b==Blocks.REINFORCED_DEEPSLATE)return 1;
        if(b==Blocks.CRAFTER||b==Blocks.BEACON||b==Blocks.ENCHANTING_TABLE||b==Blocks.ENDER_CHEST||b==Blocks.TRAPPED_CHEST||b instanceof ShulkerBoxBlock)return 2;
        String id=Registries.BLOCK.getId(b).getPath();
        if(id.contains("tuff_brick")||id.equals("chiseled_tuff"))return 16;
        if(id.contains("copper_grate")||id.contains("copper_bulb")||id.contains("chiseled_copper"))return 32;
        if(b==Blocks.SCULK||b==Blocks.SCULK_VEIN||b==Blocks.SCULK_CATALYST)return 64;
        if(id.contains("deepslate_tiles"))return 128;
        if(b==Blocks.WHITE_CANDLE||b==Blocks.SOUL_LANTERN||b==Blocks.SOUL_FIRE||b==Blocks.SOUL_CAMPFIRE)return 256;
        if(id.endsWith("_concrete")||id.endsWith("_stained_glass")||id.endsWith("_stained_glass_pane"))return 4;
        if(b==Blocks.DIAMOND_BLOCK||b==Blocks.NETHERITE_BLOCK||b==Blocks.RESPAWN_ANCHOR)return 8;
        return 0;
    }
    public void add(int x,int y,int z,int kind) {
        if(kind==0)return;
        int[] a=cells.computeIfAbsent(new Key(Math.floorDiv(x,8)*8+4,Math.floorDiv(y,8)*8+4,Math.floorDiv(z,8)*8+4),k->new int[9]);
        if(kind==1)a[0]++;else if(kind==2)a[1]++;else if(kind==4||kind==8)a[2]++;
        else if(kind==16)a[4]++;else if(kind==32)a[5]++;else if(kind==64)a[6]++;else if(kind==128)a[7]++;
        else if(kind==256)a[8]++;
        a[3]|=kind&14;
    }
    public List<Cell> snapshot() {return cells.entrySet().stream().map(e->new Cell(e.getKey().x,e.getKey().y,e.getKey().z,e.getValue()[0],e.getValue()[1],e.getValue()[2],e.getValue()[3],e.getValue()[4],e.getValue()[5],e.getValue()[6],e.getValue()[7],e.getValue()[8])).sorted(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z)).toList();}
    public static Assessment assess(List<Cell> cells,Box box) {
        int natural=0,utilities=0,materials=0,kinds=0,tuff=0,copper=0,sculk=0,tiles=0,ancientDetails=0;
        Box surroundings=box.expand(24,12,24);
        for(Cell c:cells) {
            if(c.inside(surroundings)){natural+=c.natural;tuff+=c.tuff;copper+=c.copper;sculk+=c.sculk;tiles+=c.tiles;ancientDetails+=c.ancientDetails;}
            if(c.inside(box)){utilities+=c.utilities;materials+=c.materials;kinds|=c.kinds&~1;}
        }
        // Strong location-local veto. ESP selection never overrides a generated-structure marker.
        return new Assessment(natural>0 || tuff>=64&&copper>=4 || sculk>=24&&tiles>=64 || tiles>=64&&ancientDetails>=4,
                utilities>=2 || materials>=24 && Integer.bitCount(kinds)>=2,utilities,materials);
    }
}
