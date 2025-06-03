package org.isogame.savegame;

import org.isogame.tile.Tile; // Not strictly needed here, but TileSaveData is
import java.util.List;
import java.util.ArrayList;

public class MapSaveData {
    public long worldSeed; // To recreate the world procedurally
    public int playerSpawnR; // Player's spawn row
    public int playerSpawnC; // Player's spawn column

    // Instead of a single massive TileSaveData list,
    // we'll store data for chunks that have been modified or are otherwise important to save.
    public List<ChunkDiskData> explicitlySavedChunks;

    // Inner class to represent the data of a single chunk on disk
    public static class ChunkDiskData {
        public int chunkX, chunkY;
        public List<List<TileSaveData>> tiles; // Tile data for this specific CHUNK_SIZE_TILES x CHUNK_SIZE_TILES chunk

        // Default constructor for GSON or other deserialization libraries
        public ChunkDiskData() {
            this.tiles = new ArrayList<>();
        }

        public ChunkDiskData(int cx, int cy) {
            this.chunkX = cx;
            this.chunkY = cy;
            this.tiles = new ArrayList<>();
        }
    }

    // Constructor for MapSaveData
    public MapSaveData() {
        this.explicitlySavedChunks = new ArrayList<>();
    }
}
