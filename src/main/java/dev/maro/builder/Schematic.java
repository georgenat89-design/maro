package dev.maro.builder;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.BedPart;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Immutable schematic coordinates: x varies fastest, then z, then y. */
public final class Schematic {
    public static final int MAX_CELLS = 2_000_000;
    public final String name, format;
    public final int width, height, length;
    public final BlockPos offset;
    private final BlockState[] states;
    public Schematic(String name, String format, int width, int height, int length, BlockPos offset, BlockState[] states) {
        if (volume(width,height,length) != states.length) throw new IllegalArgumentException("Invalid schematic volume");
        this.name=name; this.format=format; this.width=width; this.height=height; this.length=length;
        this.offset=offset.toImmutable(); this.states=states.clone();
    }
    public static int volume(int x,int y,int z) {
        long volume=(long)x*y*z;
        if (x<=0||y<=0||z<=0||x>2048||y>2048||z>2048||volume>MAX_CELLS)
            throw new IllegalArgumentException("Schematic exceeds 2 million cells or has invalid dimensions");
        return (int)volume;
    }
    public int size(){return states.length;}
    public BlockState state(int index){return states[index];}
    public int index(int x,int y,int z){return x+z*width+y*width*length;}
    public BlockPos local(int index){return new BlockPos(index%width,index/(width*length),(index/width)%length);}
    public boolean included(int index){return !states[index].isOf(Blocks.STRUCTURE_VOID);}
    public int solidCount(){int count=0;for(var state:states)if(!state.isAir()&&!state.isOf(Blocks.STRUCTURE_VOID))count++;return count;}
    public int rotatedWidth(int turns){return (turns&1)==0?width:length;}
    public int rotatedLength(int turns){return (turns&1)==0?length:width;}
    public BlockPos transformed(int index,int turns,String mirror){
        var p=local(index);int x=p.getX(),z=p.getZ();
        if(mirror.equals("X"))x=width-1-x;
        if(mirror.equals("Z"))z=length-1-z;
        return switch(turns&3){
            case 1->new BlockPos(length-1-z,p.getY(),x);
            case 2->new BlockPos(width-1-x,p.getY(),length-1-z);
            case 3->new BlockPos(z,p.getY(),width-1-x);
            default->new BlockPos(x,p.getY(),z);
        };
    }
    public BlockState transformedState(int index,int turns,String mirror){
        var state=states[index].mirror(mirror.equals("X")?BlockMirror.FRONT_BACK:mirror.equals("Z")?BlockMirror.LEFT_RIGHT:BlockMirror.NONE);
        return state.rotate(switch(turns&3){case 1->BlockRotation.CLOCKWISE_90;case 2->BlockRotation.CLOCKWISE_180;case 3->BlockRotation.COUNTERCLOCKWISE_90;default->BlockRotation.NONE;});
    }
    public int indexAt(BlockPos transformed,int turns,String mirror){
        int rx=transformed.getX(),rz=transformed.getZ(),y=transformed.getY(),x,z;
        switch(turns&3){case 1->{x=rz;z=length-1-rx;}case 2->{x=width-1-rx;z=length-1-rz;}case 3->{x=width-1-rz;z=rx;}default->{x=rx;z=rz;}}
        if(mirror.equals("X"))x=width-1-x;if(mirror.equals("Z"))z=length-1-z;
        return x<0||z<0||y<0||x>=width||z>=length||y>=height?-1:index(x,y,z);
    }
    /** Upper halves and bed heads are supplied by placing their companion cell. */
    public static boolean companion(BlockState state){
        return state.contains(Properties.DOUBLE_BLOCK_HALF)&&state.get(Properties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER
            ||state.contains(Properties.BED_PART)&&state.get(Properties.BED_PART)==BedPart.HEAD;
    }
    public static Item material(BlockState state){
        if(state.isAir()||state.isOf(Blocks.STRUCTURE_VOID)||companion(state))return Items.AIR;
        if(state.isOf(Blocks.WATER))return state.get(net.minecraft.block.FluidBlock.LEVEL)==0?Items.WATER_BUCKET:Items.AIR;
        if(state.isOf(Blocks.LAVA))return state.get(net.minecraft.block.FluidBlock.LEVEL)==0?Items.LAVA_BUCKET:Items.AIR;
        return state.getBlock().asItem();
    }
    public static int units(BlockState state){return state.contains(Properties.SLAB_TYPE)&&state.get(Properties.SLAB_TYPE)==net.minecraft.block.enums.SlabType.DOUBLE?2:1;}
    public Map<Item,Integer> materials(){
        Map<Item,Integer> result=new HashMap<>();
        for(var state:states){var item=material(state);if(item!=Items.AIR)result.merge(item,units(state),Integer::sum);}
        return result;
    }
}
