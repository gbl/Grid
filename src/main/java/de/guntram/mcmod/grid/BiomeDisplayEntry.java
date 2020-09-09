package de.guntram.mcmod.grid;

public class BiomeDisplayEntry {

    public int x, z;
    public boolean display;
    public int displayHeight;
    public long generated;
    
    public BiomeDisplayEntry(int x, int z, boolean display, int height, long time) {
        this.x=x;
        this.z=z;
        this.display=display;
        this.displayHeight=height;
        this.generated = time;
    }

    @Override
    public int hashCode() {
        return x << 16 + z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BiomeDisplayEntry other = (BiomeDisplayEntry) obj;
        return this.x == other.x && this.z == other.z;
    }
}
