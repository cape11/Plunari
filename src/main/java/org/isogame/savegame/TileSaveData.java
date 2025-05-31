package org.isogame.savegame;

public class TileSaveData {
    public int typeOrdinal; // Store Tile.TileType.ordinal()
    public int elevation;
    public boolean hasTorch;
    public byte skyLightLevel;
    public byte blockLightLevel;
    public int treeTypeOrdinal; // <<< ADD THIS LINE

    // Add treeType if you save trees per tile: public int treeTypeOrdinal;
}