package org.isogame.savegame;

import java.util.List;
import java.util.ArrayList;

public class MapSaveData {
    public long worldSeed;
    public int playerSpawnR;
    public int playerSpawnC;
    public List<ChunkDiskData> explicitlySavedChunks;

    // This list will store all non-player entities on the map.
    public List<EntitySaveData> entities;

    public static class ChunkDiskData {
        public int chunkX, chunkY;
        public List<List<TileSaveData>> tiles;

        public ChunkDiskData() {
            this.tiles = new ArrayList<>();
        }

        public ChunkDiskData(int cx, int cy) {
            this.chunkX = cx;
            this.chunkY = cy;
            this.tiles = new ArrayList<>();
        }
    }

    /**
     * The constructor for MapSaveData.
     * This now correctly initializes the entities list.
     */
    public MapSaveData() {
        this.explicitlySavedChunks = new ArrayList<>();
        this.entities = new ArrayList<>(); // <-- THIS IS THE FIX
    }
}
