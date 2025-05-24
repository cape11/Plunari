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
        this.lightManager = new LightManager(this, this.chunkManager);

        findSuitableCharacterPosition();
        System.out.println("Map (Chunked World) Initialized. Spawn: (" + characterSpawnRow + "," + characterSpawnCol + ")");
    }

    private void findSuitableCharacterPosition() {
        int searchRadiusTiles = 50;
        for (int r = -searchRadiusTiles / 2; r < searchRadiusTiles / 2; r++) {
            for (int c = -searchRadiusTiles / 2; c < searchRadiusTiles / 2; c++) {
                Tile tile = getTile(r,c);
                if (tile != null && tile.getType() != Tile.TileType.WATER && tile.getElevation() >= NIVEL_MAR) {
                    characterSpawnRow = r;
                    characterSpawnCol = c;
                    System.out.println("Found spawn at: " + r + "," + c);
                    return;
                }
            }
        }
        characterSpawnRow = 0;
        characterSpawnCol = 0;
        System.out.println("Warning: No ideal spawn found near origin, defaulting to 0,0. Ensure chunk (0,0) generates land.");
        getTile(0,0);
    }

    public Tile getTile(int worldRow, int worldCol) {
        int chunkX = worldToChunkCoord(worldCol);
        int chunkY = worldToChunkCoord(worldRow);
        ChunkData chunk = chunkManager.getChunk(chunkX, chunkY);

        if (chunk != null) {
            int localX = ChunkData.worldToLocalTile(worldCol);
            int localY = ChunkData.worldToLocalTile(worldRow);
            return chunk.getTile(localX, localY);
        }
        return null;
    }

    public boolean isValid(int worldRow, int worldCol) {
        return true;
    }

    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }
    public LightManager getLightManager() { return lightManager; }
    public ChunkManager getChunkManager() { return chunkManager; }

    public void setTileElevation(int worldRow, int worldCol, int elevation) {
        int chunkX = worldToChunkCoord(worldCol);
        int chunkY = worldToChunkCoord(worldRow);
        ChunkData chunkData = chunkManager.getLoadedChunk(chunkX, chunkY);

        if (chunkData != null) {
            Tile tile = getTile(worldRow, worldCol);
            if (tile != null) {
                int oldElevation = tile.getElevation();
                Tile.TileType oldType = tile.getType();

                int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
                tile.setElevation(clampedElevation);
                tile.setType(chunkManager.determineTileTypeFromElevation(clampedElevation));

                if (tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() == Tile.TileType.WATER) {
                    tile.setTreeType(Tile.TreeVisualType.NONE);
                }

                if (oldElevation != clampedElevation || oldType != tile.getType()) {
                    lightManager.markChunkDirty(chunkX, chunkY);

                    if (tile.hasTorch()) {
                        lightManager.removeLightSource(worldRow, worldCol);
                        lightManager.addLightSource(worldRow, worldCol, (byte) TORCH_LIGHT_LEVEL);
                    }
                    lightManager.enqueueSkyLightRecalculationForChunk(chunkX, chunkY);
                }
            }
        }
    }

    public void toggleTorch(int worldRow, int worldCol) {
        Tile tile = getTile(worldRow, worldCol);
        if (tile != null && tile.getType() != Tile.TileType.WATER) {
            if (tile.hasTorch()) {
                lightManager.removeLightSource(worldRow, worldCol);
            } else {
                lightManager.addLightSource(worldRow, worldCol, (byte) TORCH_LIGHT_LEVEL);
            }
        }
    }

    public boolean isTileOpaque(int worldRow, int worldCol) {
        Tile tile = getTile(worldRow, worldCol);
        if (tile == null) {
            return true;
        }
        return !tile.isTransparentToLight();
    }

    public static int worldToChunkCoord(int tileCoord) {
        return (int) Math.floor((double) tileCoord / CHUNK_SIZE_TILES);
    }
}
