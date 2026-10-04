package dev.maro.builder;

import com.mojang.brigadier.StringReader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.datafixer.fix.BlockStateFlattening;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Native NBT readers, independent of Litematica/WorldEdit. Bounded reads run off the render thread. */
public final class SchematicIO {
    private static final long MAX_FILE=64L*1024*1024, MAX_NBT=256L*1024*1024;
    private SchematicIO(){}
    public static boolean supported(Path path){return path.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(schem|schematic|litematic|nbt)");}
    public static Schematic read(Path path) throws IOException {
        if(Files.size(path)>MAX_FILE)throw new IOException("File exceeds 64 MB");
        try(InputStream file=Files.newInputStream(path);BufferedInputStream buffer=new BufferedInputStream(file)){
            buffer.mark(2);int first=buffer.read(),second=buffer.read();buffer.reset();
            InputStream input=first==0x1f&&second==0x8b?new GZIPInputStream(buffer):buffer;
            var root=NbtIo.readCompound(new DataInputStream(input),new NbtSizeTracker(MAX_NBT,64));
            return decode(path.getFileName().toString(),root);
        }catch(RuntimeException e){throw new IOException("Invalid schematic: "+e.getMessage(),e);}
    }
    public static Schematic decode(String name,NbtCompound root) throws IOException {
        try{
            if(root.contains("Schematic"))root=root.getCompoundOrEmpty("Schematic");
            if(root.contains("Regions"))return litematic(name,root);
            if(root.contains("BlockData")||root.get("Blocks") instanceof NbtCompound)return sponge(name,root);
            if(root.get("Blocks") instanceof NbtByteArray)return legacy(name,root);
            if(root.contains("blocks")&&root.contains("size"))return structure(name,root);
            throw new IOException("Unrecognized schematic NBT format");
        }catch(IllegalArgumentException|IndexOutOfBoundsException e){throw new IOException("Invalid schematic: "+e.getMessage(),e);}
    }
    private static Schematic sponge(String name,NbtCompound n) throws IOException {
        int version=n.getInt("Version",2);
        if(version<1||version>3)throw new IOException("Unsupported Sponge version "+version);
        int w=n.getShort("Width",(short)0)&65535,h=n.getShort("Height",(short)0)&65535,l=n.getShort("Length",(short)0)&65535;
        int volume=Schematic.volume(w,h,l);
        NbtCompound blocks=version==3?n.getCompoundOrEmpty("Blocks"):n;
        var palette=blocks.getCompoundOrEmpty("Palette");
        if(palette.isEmpty())throw new IOException("Missing block palette");
        Map<Integer,BlockState> states=new HashMap<>();
        for(String key:palette.getKeys()){
            int id=palette.getInt(key,-1);if(id<0||states.put(id,parseState(key))!=null)throw new IOException("Invalid palette index");
        }
        byte[] data=blocks.getByteArray(version==3?"Data":"BlockData").orElseThrow(()->new IOException("Missing block data"));
        BlockState[] cells=new BlockState[volume];int p=0;
        for(int i=0;i<volume;i++){
            int id=0,shift=0,b;
            do{if(p>=data.length||shift>=35)throw new IOException("Truncated or invalid palette varint");b=data[p++]&255;id|=(b&127)<<shift;shift+=7;}while((b&128)!=0);
            cells[i]=states.get(id);if(cells[i]==null)throw new IOException("Unknown palette index "+id);
        }
        if(p!=data.length)throw new IOException("Block data length does not match dimensions");
        return new Schematic(name,"Sponge v"+version,w,h,l,vector(n,"Offset"),cells);
    }
    private static Schematic legacy(String name,NbtCompound n) throws IOException {
        int w=n.getShort("Width",(short)0)&65535,h=n.getShort("Height",(short)0)&65535,l=n.getShort("Length",(short)0)&65535;
        int size=Schematic.volume(w,h,l);
        byte[] ids=n.getByteArray("Blocks").orElseThrow(),meta=n.getByteArray("Data").orElseThrow();
        byte[] add=n.getByteArray("AddBlocks").orElse(new byte[0]);
        if(ids.length!=size||meta.length!=size||add.length>0&&add.length<(size+1)/2)throw new IOException("Invalid legacy block arrays");
        BlockState[] cells=new BlockState[size];Map<Integer,BlockState> cache=new HashMap<>();
        for(int i=0;i<size;i++){
            int high=add.length==0?0:((add[i>>1]&255)>>((i&1)*4))&15;
            int id=(((ids[i]&255)|(high<<8))<<4)|(meta[i]&15);
            BlockState state=cache.get(id);
            if(state==null){
                var converted=BlockStateFlattening.lookupState(id).convert(NbtOps.INSTANCE).getValue();
                if(!(converted instanceof NbtCompound compound))throw new IOException("Unknown legacy block "+id);
                state=paletteState(compound);if(id>>4!=0&&state.isAir())throw new IOException("Unsupported legacy block ID "+(id>>4));cache.put(id,state);
            }
            cells[i]=state;
        }
        return new Schematic(name,"Legacy MCEdit",w,h,l,new BlockPos(n.getInt("WEOffsetX",0),n.getInt("WEOffsetY",0),n.getInt("WEOffsetZ",0)),cells);
    }
    private static Schematic structure(String name,NbtCompound n) throws IOException {
        var size=vector(n,"size");int w=size.getX(),h=size.getY(),l=size.getZ();
        var list=n.getListOrEmpty("palette");
        if(list.isEmpty()){var palettes=n.getListOrEmpty("palettes");if(!palettes.isEmpty())list=palettes.getListOrEmpty(0);}
        List<BlockState> palette=palette(list);
        BlockState[] cells=new BlockState[Schematic.volume(w,h,l)];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(var block:n.getListOrEmpty("blocks").streamCompounds().toList()){
            var pos=vector(block,"pos");int id=block.getInt("state",-1);
            if(id<0||id>=palette.size()||pos.getX()<0||pos.getY()<0||pos.getZ()<0||pos.getX()>=w||pos.getY()>=h||pos.getZ()>=l)throw new IOException("Invalid structure block");
            cells[pos.getX()+pos.getZ()*w+pos.getY()*w*l]=palette.get(id);
        }
        return new Schematic(name,"Vanilla structure",w,h,l,BlockPos.ORIGIN,cells);
    }
    private record Region(BlockPos min,BlockPos size,List<BlockState> palette,long[] packed){}
    private static Schematic litematic(String name,NbtCompound n) throws IOException {
        var regions=n.getCompoundOrEmpty("Regions");List<Region> parts=new ArrayList<>();
        BlockPos min=null,max=null;long total=0;
        for(String key:new TreeSet<>(regions.getKeys())){
            var r=regions.getCompoundOrEmpty(key);var pos=vector(r,"Position");var signed=vector(r,"Size");
            int w=Math.abs(signed.getX()),h=Math.abs(signed.getY()),l=Math.abs(signed.getZ());
            total+=Schematic.volume(w,h,l);if(total>Schematic.MAX_CELLS)throw new IOException("Too many region cells");
            var low=pos.add(Math.min(0,signed.getX()+1),Math.min(0,signed.getY()+1),Math.min(0,signed.getZ()+1));
            var high=low.add(w-1,h-1,l-1);
            min=min==null?low:new BlockPos(Math.min(min.getX(),low.getX()),Math.min(min.getY(),low.getY()),Math.min(min.getZ(),low.getZ()));
            max=max==null?high:new BlockPos(Math.max(max.getX(),high.getX()),Math.max(max.getY(),high.getY()),Math.max(max.getZ(),high.getZ()));
            var palette=palette(r.getListOrEmpty("BlockStatePalette"));
            long[] packed=r.getLongArray("BlockStates").orElseThrow(()->new IOException("Missing litematic block states"));
            int bits=Math.max(2,32-Integer.numberOfLeadingZeros(palette.size()-1));
            if(packed.length<((long)w*h*l*bits+63)/64)throw new IOException("Truncated litematic packed states");
            parts.add(new Region(low,new BlockPos(w,h,l),palette,packed));
        }
        if(parts.isEmpty())throw new IOException("Litematic has no regions");
        int w=max.getX()-min.getX()+1,h=max.getY()-min.getY()+1,l=max.getZ()-min.getZ()+1;
        BlockState[] cells=new BlockState[Schematic.volume(w,h,l)];Arrays.fill(cells,Blocks.STRUCTURE_VOID.getDefaultState());
        for(var r:parts){
            int rw=r.size.getX(),rh=r.size.getY(),rl=r.size.getZ(),bits=Math.max(2,32-Integer.numberOfLeadingZeros(r.palette.size()-1));
            for(int i=0;i<rw*rh*rl;i++){
                int id=packedIndex(r.packed,i,bits);if(id>=r.palette.size())throw new IOException("Invalid litematic palette index");
                int x=i%rw+r.min.getX()-min.getX(),y=i/(rw*rl)+r.min.getY()-min.getY(),z=(i/rw)%rl+r.min.getZ()-min.getZ();
                cells[x+z*w+y*w*l]=r.palette.get(id);
            }
        }
        return new Schematic(name,"Litematic ("+parts.size()+" regions)",w,h,l,min,cells);
    }
    /** Litematica uses a continuous bit stream, including entries crossing word boundaries. */
    public static int packedIndex(long[] data,int index,int bits){
        long bit=(long)index*bits;int word=(int)(bit>>>6),shift=(int)(bit&63);
        long value=data[word]>>>shift;
        if(shift+bits>64)value|=data[word+1]<<(64-shift);
        return (int)(value&((1L<<bits)-1));
    }
    private static List<BlockState> palette(NbtList list) throws IOException {
        if(list.isEmpty()||list.size()>Schematic.MAX_CELLS)throw new IOException("Invalid block palette");
        List<BlockState> result=new ArrayList<>();for(var value:list)result.add(paletteState(value.asCompound().orElseThrow(()->new IOException("Invalid palette entry"))));return result;
    }
    private static BlockState paletteState(NbtCompound n) throws IOException {
        StringBuilder state=new StringBuilder(n.getString("Name", ""));var properties=n.getCompoundOrEmpty("Properties");
        if(!properties.isEmpty()){
            state.append('[');boolean first=true;
            for(String key:new TreeSet<>(properties.getKeys())){if(!first)state.append(',');first=false;state.append(key).append('=').append(properties.getString(key,""));}
            state.append(']');
        }
        return parseState(state.toString());
    }
    public static BlockState parseState(String state) throws IOException {
        try{return BlockArgumentParser.block(Registries.BLOCK,new StringReader(state),false).blockState();}
        catch(Exception e){throw new IOException("Unknown or incompatible block state: "+state,e);}
    }
    private static BlockPos vector(NbtCompound n,String key) throws IOException {
        if(!n.contains(key))return BlockPos.ORIGIN;
        if(n.get(key) instanceof NbtCompound){var v=n.getCompoundOrEmpty(key);return new BlockPos(v.getInt("x",0),v.getInt("y",0),v.getInt("z",0));}
        var array=n.getIntArray(key);if(array.isPresent()){int[] v=array.get();if(v.length!=3)throw new IOException("Invalid "+key);return new BlockPos(v[0],v[1],v[2]);}
        var list=n.getListOrEmpty(key);if(list.size()!=3)throw new IOException("Invalid "+key);
        return new BlockPos(list.getInt(0,0),list.getInt(1,0),list.getInt(2,0));
    }
    public static NbtCompound encodeStructure(Schematic schematic){
        var root=new NbtCompound();root.put("size",ints(schematic.width,schematic.height,schematic.length));NbtHelper.putDataVersion(root);
        Map<BlockState,Integer> ids=new LinkedHashMap<>();NbtList palette=new NbtList(),blocks=new NbtList();
        for(int i=0;i<schematic.size();i++){
            if(!schematic.included(i))continue;
            var state=schematic.state(i);int id=ids.computeIfAbsent(state,s->{palette.add(NbtHelper.fromBlockState(s));return palette.size()-1;});
            var p=schematic.local(i);var block=new NbtCompound();block.put("pos",ints(p.getX(),p.getY(),p.getZ()));block.putInt("state",id);blocks.add(block);
        }
        root.put("palette",palette);root.put("blocks",blocks);root.put("entities",new NbtList());return root;
    }
    public static NbtList ints(int... values){var list=new NbtList();for(int value:values)list.add(NbtInt.of(value));return list;}
}
