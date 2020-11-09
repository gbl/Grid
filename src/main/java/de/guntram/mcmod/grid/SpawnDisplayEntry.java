package de.guntram.mcmod.grid;

public class SpawnDisplayEntry {

    public int x, y, z;
    public int display;
    public long generated;
    
    public SpawnDisplayEntry(int x, int y, int z, int display, long time) {
        this.x=x;
        this.y=y;
        this.z=z;
        this.display=display;
        this.generated = time;
    }
    
    @Override
    public String toString() {
        return "SpawnDisplayEntry at "+x+"/"+y+"/"+z+", display="+display+" generated at "+generated;
    }
}
