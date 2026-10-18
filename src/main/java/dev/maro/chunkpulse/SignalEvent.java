package dev.maro.chunkpulse;

/** A received activity observation. Coordinates are never inferred from chunk palettes. */
public record SignalEvent(long time, int x, int y, int z, Kind kind, boolean exact, String source, double rawY) {
    public SignalEvent(long time,int x,int y,int z,Kind kind,boolean exact,String source){this(time,x,y,z,kind,exact,source,y+.5);}
    public enum Kind {
        PISTON(10, true), CRAFTER(12, true), DISPENSER(8, true), NOTE(1, false),
        STATE(4, true), MACHINE_UPDATE(.5, false), WORLD(2, false), LIGHT(.35, false), SOUND(.45, false),
        PARTICLE(.04, false), ENTITY(.15, false), DENSITY(.1, false), BLOCK_ENTITY(.12, false),
        BLOCK_UPDATE(0, false), FLUID(0, false);
        public final double weight;
        public final boolean machine;
        Kind(double weight, boolean machine) { this.weight=weight;this.machine=machine; }
    }
    public long chunk() { return key(x >> 4, z >> 4); }
    public long position() { return ((long)x & 0x3ffffffL) << 38 | ((long)z & 0x3ffffffL) << 12 | (y & 4095L); }
    public static long key(int x, int z) { return (long)x << 32 | z & 0xffffffffL; }
    public static int chunkX(long key) { return (int)(key >> 32); }
    public static int chunkZ(long key) { return (int)key; }
    public boolean valid() {
        return kind!=null && time>=0 && Math.abs((long)x)<=30_000_000 && Math.abs((long)z)<=30_000_000
                && y>=-2048 && y<=2047 && source!=null && source.length()<=160 && Double.isFinite(rawY);
    }
}
