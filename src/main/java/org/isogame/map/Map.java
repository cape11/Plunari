package org.isogame.map;

import org.isogame.tile.Tile;
import static org.isogame.constants.Constants.*;

public class Map {

    private final ChunkManager chunkManager;
    private final LightManager lightManager;

    private int characterSpawnRow;
    private int characterSpawnCol;

    public Map() {
        this.chunkManager = new ChunkManager();
        // Pass 'this' (Map instance) to LightManager if it needs map-level access,
        // and the chunkManager.
        this.lightManager = new LightManager(this, this.chunkManager);

        findSuitableCharacterPosition();
        System.out.println("Map (Chunked World) Initialized. Spawn: (" + characterSpawnRow + "," + characterSpawnCol + ")");
    }

    private void findSuitableCharacterPosition() {
        int searchRadiusTiles = 50; // Search a 50x50 tile area around (0,0)
        for (int r = -searchRadiusTiles / 2; r < searchRadiusTiles / 2; r++) {
            for (int c = -searchRadiusTiles / 2; c < searchRadiusTiles / 2; c++) {
                Tile tile = getTile(r,c); // This will load/generate chunk (0,0) if not already
                if (tile != null && tile.getType() != Tile.TileType.WATER && tile.getElevation() >= NIVEL_MAR) {
                    characterSpawnRow = r;
                    characterSpawnCol = c;
                    System.out.println("Found spawn at: " + r + "," + c + " in chunk (" + worldToChunkCoord(c) + "," + worldToChunkCoord(r) + ")");
                    return;
                }
            }
        }
        // Fallback if no suitable land found in the search radius
        characterSpawnRow = 0;
        characterSpawnCol = 0;
        System.out.println("Warning: No ideal spawn found near origin, defaulting to 0,0. Ensure chunk (0,0) generates land.");
        // Ensure chunk 0,0 is loaded if no other spawn found
        getTile(0,0);
    }

    public Tile getTile(int worldRow, int worldCol) {
        int chunkX = worldToChunkCoord(worldCol);
        int chunkY = worldToChunkCoord(worldRow);
        ChunkData chunk = chunkManager.getChunk(chunkX, chunkY); // Ensures chunk is loaded/generated

        if (chunk != null) {
            int localX = ChunkData.worldToLocalTile(worldCol);
            int localY = ChunkData.worldToLocalTile(worldRow);
            return chunk.getTile(localX, localY);
        }
        return null; // Should ideally not happen if getChunk works as expected
    }

    /**
     * Checks if the given world coordinates are valid.
     * For an infinite procedural world, this might always be true,
     * but could be used for defined map boundaries later.
     */
    public boolean isValid(int worldRow, int worldCol) {
        // For now, assume an infinite map within practical limits of data types
        return true;
    }

    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }
    public LightManager getLightManager() { return lightManager; }
    public ChunkManager getChunkManager() { return chunkManager; }

    public void setTileElevation(int worldRow, int worldCol, int elevation) {
        int chunkX = worldToChunkCoord(worldCol);
        int chunkY = worldToChunkCoord(worldRow);
        ChunkData chunkData = chunkManager.getLoadedChunk(chunkX, chunkY); // Get only if loaded

        if (chunkData != null) {
            Tile tile = getTile(worldRow, worldCol); // This uses the public getTile which ensures chunk is loaded
            if (tile != null) {
                int oldElevation = tile.getElevation();
                Tile.TileType oldType = tile.getType();

                int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
                tile.setElevation(clampedElevation);
                tile.setType(chunkManager.determineTileTypeFromElevation(clampedElevation)); // Update type based on new elevation

                // If a tree was on a tile that is now water, remove the tree
                if (tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() == Tile.TileType.WATER) {
                    tile.setTreeType(Tile.TreeVisualType.NONE);
                }

                // If elevation or type actually changed, trigger lighting updates
                if (oldElevation != clampedElevation || oldType != tile.getType()) {
                    // The LightManager's initializeLightingForNewChunk (called via the queue)
                    // will handle marking the chunk and its neighbors dirty for the renderer.
                    // So, no direct call to a 'markChunkDirty' is needed here.

                    // If the tile had a torch, its light source needs to be re-evaluated
                    if (tile.hasTorch()) {
                        lightManager.removeLightSource(worldRow, worldCol);
                        lightManager.addLightSource(worldRow, worldCol, (byte) TORCH_LIGHT_LEVEL);
                    }
                    // Enqueue this chunk for a full skylight recalculation.
                    lightManager.enqueueSkyLightRecalculationForChunk(chunkX, chunkY);

                    // Also enqueue neighbors as elevation changes can affect their lighting significantly
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            if (chunkManager.getLoadedChunk(chunkX + dx, chunkY + dy) != null) {
                                lightManager.enqueueSkyLightRecalculationForChunk(chunkX + dx, chunkY + dy);
                            }
                        }
                    }
                }
            }
        }
    }

    public void toggleTorch(int worldRow, int worldCol) {
        Tile tile = getTile(worldRow, worldCol);
        if (tile != null && tile.getType() != Tile.TileType.WATER) { // Can't place torches on water
            if (tile.hasTorch()) {
                lightManager.removeLightSource(worldRow, worldCol);
            } else {
                lightManager.addLightSource(worldRow, worldCol, (byte) TORCH_LIGHT_LEVEL);
            }
            // Adding/removing a light source in LightManager already handles
            // marking necessary chunks dirty for the renderer and re-propagating light.
        }
    }

    /**
     * Checks if a tile at the given world coordinates is opaque to light.
     * Considers null tiles (air) as transparent.
     * @param worldRow The world row of the tile.
     * @param worldCol The world column of the tile.
     * @return True if the tile is opaque, false if transparent or air.
     */
    public boolean isTileOpaque(int worldRow, int worldCol) {
        Tile tile = getTile(worldRow, worldCol);
        if (tile == null) {
            return false; // Air is transparent
        }
        return !tile.isTransparentToLight(); // Delegates to Tile's logic
    }

    /**
     * Converts a world tile coordinate (row or column) to its corresponding chunk coordinate.
     * @param tileCoord The world tile coordinate.
     * @return The chunk coordinate.
     */
    public static int worldToChunkCoord(int tileCoord) {
        // Ensure correct handling of negative coordinates for chunk indexing
        return (int) Math.floor((double) tileCoord / CHUNK_SIZE_TILES);
    }
}
