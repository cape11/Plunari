package org.isogame.map;

import org.isogame.constants.Constants;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
import org.isogame.tile.Tile;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import org.isogame.savegame.MapSaveData;
import org.isogame.savegame.TileSaveData;

import static org.isogame.constants.Constants.*;

public class Map {

    private final Tile[][] tiles;
    private final int width;
    private final int height;
    private SimplexNoise noiseGenerator;
    private final Random random = new Random();
    private int characterSpawnRow;
    private int characterSpawnCol;

    private final LightManager lightManager;

    public Map(int initialWidth, int initialHeight) {
        this.width = initialWidth;
        this.height = initialHeight;
        this.tiles = new Tile[height][width];
        this.lightManager = new LightManager(this);

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                tiles[r][c] = new Tile(Tile.TileType.WATER, 0);
            }
        }
        generateMap();
    }

    public void generateMap() {
        // System.out.println("Map: Generating initial map terrain ("+width+"x"+height+")...");
        this.noiseGenerator = new SimplexNoise(random.nextInt(10000));
        int[][] rawElevations = new int[height][width];

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                double noiseValue = calculateCombinedNoise((double) c, (double) r);
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * (ALTURA_MAXIMA + 1)) - 1;
                rawElevations[r][c] = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
            }
        }
        smoothTerrain(rawElevations, 1);

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int elevation = rawElevations[r][c];
                Tile.TileType type = determineTileTypeFromElevation(elevation);
                Tile currentTile = tiles[r][c];
                currentTile.setElevation(elevation);
                currentTile.setType(type);
                currentTile.setSkyLightLevel((byte) 0);
                currentTile.setBlockLightLevel((byte) 0);
                currentTile.setHasTorch(false);
                currentTile.setTreeType(Tile.TreeVisualType.NONE);

                if (type == Tile.TileType.GRASS && elevation >= NIVEL_ARENA && elevation < NIVEL_ROCA) {
                    if (random.nextFloat() < 0.08) {
                        currentTile.setTreeType(random.nextBoolean() ? Tile.TreeVisualType.APPLE_TREE_FRUITING : Tile.TreeVisualType.PINE_TREE_SMALL);
                    }
                }
            }
        }
        findSuitableCharacterPosition();
        // System.out.println("Map: Terrain generation finished. Spawn: (" + characterSpawnRow + ", " + characterSpawnCol + ")");
    }


    private double calculateCombinedNoise(double x, double y) {
        double baseFrequency = NOISE_SCALE * 0.05;
        double mountainFrequency = NOISE_SCALE * 0.2;
        double roughnessFrequency = NOISE_SCALE * 0.8;
        double baseNoise = noiseGenerator.octaveNoise(x * baseFrequency, y * baseFrequency, 4, 0.6);
        double mountainNoise = noiseGenerator.noise(x * mountainFrequency, y * mountainFrequency) * 0.40;
        double roughnessNoise = noiseGenerator.noise(x * roughnessFrequency, y * roughnessFrequency) * 0.15;
        double combined = baseNoise + mountainNoise + roughnessNoise;
        return Math.max(-1.0, Math.min(1.0, combined));
    }

    public boolean placeBlock(int r, int c, Item itemToPlace) {
        if (!isValid(r, c) || itemToPlace == null || itemToPlace.getType() != Item.ItemType.RESOURCE) {
            return false;
        }
        Tile targetTile = getTile(r, c);
        if (targetTile == null) return false;

        int currentElevation = targetTile.getElevation();
        int newElevation = currentElevation + 1;
        if (newElevation > ALTURA_MAXIMA) return false;

        Tile.TileType newType;
        if (itemToPlace.equals(ItemRegistry.DIRT)) {
            newType = determineTileTypeFromElevation(newElevation);
            if (targetTile.getType() == Tile.TileType.WATER) {
                newElevation = NIVEL_MAR;
                newType = (newElevation < NIVEL_ARENA) ? Tile.TileType.SAND : Tile.TileType.GRASS;
            }
        } else if (itemToPlace.equals(ItemRegistry.STONE)) {
            newType = Tile.TileType.ROCK;
        } else if (itemToPlace.equals(ItemRegistry.SAND)) {
            newType = Tile.TileType.SAND;
        } else {
            return false;
        }

        targetTile.setElevation(newElevation);
        targetTile.setType(newType);
        targetTile.setTreeType(Tile.TreeVisualType.NONE);

        queueLightUpdateForArea(r,c,1,this.lightManager); // Update light for 3x3 area

        if(targetTile.hasTorch()){ // If placing on a tile that had a torch (unlikely for block placement)
            lightManager.removeLightSource(r,c);
            targetTile.setHasTorch(false);
        }
        return true;
    }

    public Tile.TileType determineTileTypeFromElevation(int elevation) {
        if (elevation < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevation < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevation < NIVEL_ROCA) return Tile.TileType.GRASS;
        if (elevation < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }

    public void populateSaveData(MapSaveData saveData) {
        saveData.width = this.width;
        saveData.height = this.height;
        saveData.tiles = new ArrayList<>();
        for (int r = 0; r < this.height; r++) {
            List<TileSaveData> rowList = new ArrayList<>();
            for (int c = 0; c < this.width; c++) {
                Tile tile = getTile(r, c);
                TileSaveData tileData = new TileSaveData();
                if (tile != null) {
                    tileData.typeOrdinal = tile.getType().ordinal();
                    tileData.elevation = tile.getElevation();
                    tileData.hasTorch = tile.hasTorch();
                    tileData.skyLightLevel = tile.getSkyLightLevel();
                    tileData.blockLightLevel = tile.getBlockLightLevel();
                    tileData.treeTypeOrdinal = tile.getTreeType().ordinal();
                } else {
                    tileData.typeOrdinal = Tile.TileType.AIR.ordinal();
                    tileData.elevation = 0;
                    tileData.treeTypeOrdinal = Tile.TreeVisualType.NONE.ordinal();
                }
                rowList.add(tileData);
            }
            saveData.tiles.add(rowList);
        }
    }


    public boolean loadState(MapSaveData mapData) {
        if (mapData == null || mapData.width != this.width || mapData.height != this.height) {
            System.err.println("Map loadState: Invalid map data or dimension mismatch. Expected " +
                    this.width + "x" + this.height + ", got " +
                    (mapData != null ? mapData.width : "null") + "x" + (mapData != null ? mapData.height : "null"));
            return false;
        }
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                if (mapData.tiles == null || r >= mapData.tiles.size() || mapData.tiles.get(r) == null || c >= mapData.tiles.get(r).size()) {
                    if (this.tiles[r][c] == null) this.tiles[r][c] = new Tile(Tile.TileType.WATER, 0);
                    else {
                        this.tiles[r][c].setType(Tile.TileType.WATER); this.tiles[r][c].setElevation(0);
                        this.tiles[r][c].setHasTorch(false); this.tiles[r][c].setSkyLightLevel((byte)0);
                        this.tiles[r][c].setBlockLightLevel((byte)0); this.tiles[r][c].setTreeType(Tile.TreeVisualType.NONE);
                    }
                    continue;
                }
                TileSaveData savedTile = mapData.tiles.get(r).get(c);
                Tile currentTile = this.tiles[r][c];
                if (savedTile != null) {
                    currentTile.setType(Tile.TileType.values()[savedTile.typeOrdinal]);
                    currentTile.setElevation(savedTile.elevation);
                    currentTile.setHasTorch(savedTile.hasTorch);
                    currentTile.setSkyLightLevel(savedTile.skyLightLevel);
                    currentTile.setBlockLightLevel(savedTile.blockLightLevel);
                    currentTile.setTreeType(Tile.TreeVisualType.values()[savedTile.treeTypeOrdinal]);
                } else {
                    currentTile.setType(Tile.TileType.WATER); currentTile.setElevation(0); currentTile.setHasTorch(false);
                    currentTile.setSkyLightLevel((byte)0); currentTile.setBlockLightLevel((byte)0); currentTile.setTreeType(Tile.TreeVisualType.NONE);
                }
            }
        }
        return true;
    }

    private void smoothTerrain(int[][] elevations, int passes) {
        if (passes <= 0) return;
        int currentHeight = elevations.length;
        int currentWidth = elevations[0].length;
        int[][] tempAlturas = new int[currentHeight][currentWidth];
        for (int pass = 0; pass < passes; pass++) {
            for (int r = 0; r < currentHeight; r++) { System.arraycopy(elevations[r], 0, tempAlturas[r], 0, currentWidth); }
            for (int r = 0; r < currentHeight; r++) {
                for (int c = 0; c < currentWidth; c++) {
                    int sum = 0; int count = 0;
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            int nr = r + dr; int nc = c + dc;
                            if (nr >= 0 && nr < currentHeight && nc >= 0 && nc < currentWidth) {
                                sum += tempAlturas[nr][nc]; count++;
                            }
                        }
                    }
                    if (count > 0) elevations[r][c] = sum / count;
                }
            }
        }
    }

    private void findSuitableCharacterPosition() {
        int centerRow = height / 2; int centerCol = width / 2;
        for (int radius = 0; radius < Math.max(width, height) / 2; radius++) {
            for (int rOffset = -radius; rOffset <= radius; rOffset++) {
                for (int cOffset = -radius; cOffset <= radius; cOffset++) {
                    if (Math.abs(rOffset) == radius || Math.abs(cOffset) == radius) {
                        int r = centerRow + rOffset; int c = centerCol + cOffset;
                        if (isValid(r, c) && tiles[r][c].getType() != Tile.TileType.WATER && tiles[r][c].getElevation() >= NIVEL_MAR) {
                            characterSpawnRow = r; characterSpawnCol = c; return;
                        }
                    }
                }
            }
        }
        characterSpawnRow = centerRow; characterSpawnCol = centerCol;
    }

    public Tile getTile(int r, int c) {
        if (isValid(r, c)) return tiles[r][c];
        return null;
    }

    public boolean isValid(int r, int c) {
        return r >= 0 && r < height && c >= 0 && c < width;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }
    public LightManager getLightManager() { return lightManager; }

    public void setTileElevation(int r, int c, int newElevation) {
        if (!isValid(r, c)) return;
        Tile tile = getTile(r, c);
        if (tile == null) return;

        int oldElevation = tile.getElevation();
        Tile.TileType oldType = tile.getType();
        boolean oldTorchState = tile.hasTorch();

        int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, newElevation));
        tile.setElevation(clampedElevation);
        tile.setType(determineTileTypeFromElevation(clampedElevation));

        if (clampedElevation != oldElevation && tile.getTreeType() != Tile.TreeVisualType.NONE) {
            tile.setTreeType(Tile.TreeVisualType.NONE);
        }

        if (oldElevation != clampedElevation || oldType != tile.getType() || oldTorchState != tile.hasTorch()) {
            queueLightUpdateForArea(r, c, 1, this.lightManager); // Update 3x3 area around the change

            if (oldTorchState && (!tile.hasTorch() || oldElevation != clampedElevation)) {
                lightManager.removeLightSource(r, c);
            }
            if (tile.hasTorch()) {
                lightManager.addLightSource(r, c, (byte) TORCH_LIGHT_LEVEL);
            }
        }
    }

    /**
     * Queues light updates for a square area centered at (row, col) with a given radius.
     * @param row Center row
     * @param col Center column
     * @param radius Radius of the square area (0 means only the center tile, 1 means 3x3, etc.)
     * @param lm The LightManager instance
     */
    public void queueLightUpdateForArea(int row, int col, int radius, LightManager lm) {
        if (lm == null) return;
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                int nr = row + dr;
                int nc = col + dc;
                if (isValid(nr, nc)) {
                    Tile t = getTile(nr, nc);
                    if (t != null) {
                        // Re-queue existing light for propagation
                        if (t.getSkyLightLevel() > 0) lm.getSkyLightPropagationQueue().add(new LightManager.LightNode(nr, nc, t.getSkyLightLevel()));
                        else if (lm.isSurfaceTileExposedToSky(nr, nc, t.getElevation())) { // If 0 but exposed, try to seed with global
                            lm.getSkyLightPropagationQueue().add(new LightManager.LightNode(nr, nc, lm.getCurrentGlobalSkyLightTarget())); // NEW CALL
                        }
                        if (t.getBlockLightLevel() > 0) lm.getBlockLightPropagationQueue().add(new LightManager.LightNode(nr, nc, t.getBlockLightLevel()));

                        // If it had a torch, ensure it's processed
                        if (t.hasTorch()) lm.addLightSource(nr, nc, (byte)TORCH_LIGHT_LEVEL);
                    }
                    lm.markChunkDirty(nr, nc); // Mark chunk dirty for rendering
                }
            }
        }
    }


    public void toggleTorch(int r, int c) {
        if (!isValid(r, c)) return;
        Tile tile = getTile(r,c);
        if (tile != null && tile.getType() != Tile.TileType.WATER && tile.isSolidOpaqueBlock()) {
            if (tile.hasTorch()) {
                lightManager.removeLightSource(r, c);
            } else {
                lightManager.addLightSource(r, c, (byte) TORCH_LIGHT_LEVEL);
            }
        }
    }

    /**
     * Helper method to propagate light from one chunk (sourceChunk) to an adjacent chunk (targetChunk).
     * It iterates over the border tiles and queues light propagation.
     */
    public void propagateLightAcrossChunkBorder(LightManager.ChunkCoordinate sourceChunkCoord, LightManager.ChunkCoordinate targetChunkCoord, LightManager lm) {
        if (lm == null) return;

        int sourceStartX = sourceChunkCoord.chunkX * CHUNK_SIZE_TILES;
        int sourceStartY = sourceChunkCoord.chunkY * CHUNK_SIZE_TILES;
        // Determine border tiles based on relative positions of chunks
        // Example: if targetChunk is to the right of sourceChunk (targetChunk.chunkX == sourceChunk.chunkX + 1)
        // Then iterate the rightmost column of sourceChunk and add those tiles to light queues.

        // This is a simplified approach: just re-queue all tiles on the border of the source chunk
        // that are adjacent to the target chunk for light propagation.
        // A more precise way would be to check light levels and opacity.

        int startR, endR, startC, endC;

        if (targetChunkCoord.chunkX > sourceChunkCoord.chunkX) { // Target is to the East
            startC = sourceStartX + CHUNK_SIZE_TILES - 1; endC = startC + 1;
            startR = sourceStartY; endR = sourceStartY + CHUNK_SIZE_TILES;
        } else if (targetChunkCoord.chunkX < sourceChunkCoord.chunkX) { // Target is to the West
            startC = sourceStartX; endC = startC + 1;
            startR = sourceStartY; endR = sourceStartY + CHUNK_SIZE_TILES;
        } else if (targetChunkCoord.chunkY > sourceChunkCoord.chunkY) { // Target is to the South
            startR = sourceStartY + CHUNK_SIZE_TILES - 1; endR = startR + 1;
            startC = sourceStartX; endC = sourceStartX + CHUNK_SIZE_TILES;
        } else if (targetChunkCoord.chunkY < sourceChunkCoord.chunkY) { // Target is to the North
            startR = sourceStartY; endR = startR + 1;
            startC = sourceStartX; endC = sourceStartX + CHUNK_SIZE_TILES;
        } else {
            return; // Not adjacent or same chunk
        }

        for (int r = Math.max(0, startR); r < Math.min(this.height, endR); r++) {
            for (int c = Math.max(0, startC); c < Math.min(this.width, endC); c++) {
                if (isValid(r, c)) {
                    Tile tile = getTile(r, c);
                    if (tile != null) {
                        if (tile.getSkyLightLevel() > 0) {
                            lm.getSkyLightPropagationQueue().add(new LightManager.LightNode(r, c, tile.getSkyLightLevel()));
                        }
                        if (tile.getBlockLightLevel() > 0) {
                            lm.getBlockLightPropagationQueue().add(new LightManager.LightNode(r, c, tile.getBlockLightLevel()));
                        }
                    }
                }
            }
        }
    }
}