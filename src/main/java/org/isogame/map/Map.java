package org.isogame.map;

import org.isogame.constants.Constants;
import org.isogame.entity.*;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
import org.isogame.savegame.EntitySaveData;
import org.isogame.savegame.MapSaveData;
import org.isogame.savegame.TileSaveData;
import org.isogame.tile.Tile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import static org.isogame.constants.Constants.*;

public class Map {
    private final HashMap<LightManager.ChunkCoordinate, Tile[][]> loadedChunkTiles;
    private final HashMap<LightManager.ChunkCoordinate, Boolean> chunkModificationStatus;
    private final SimplexNoise noiseGenerator;
    private final Random random = new Random();
    private int characterSpawnRow;
    private int characterSpawnCol;
    private final LightManager lightManager;
    private final long worldSeed;
    private final List<Entity> entities;

    public static final int MAX_ANIMALS = 25;

    public Map(long seed) {
        this.worldSeed = seed;
        this.noiseGenerator = new SimplexNoise((int) this.worldSeed);
        this.loadedChunkTiles = new HashMap<>();
        this.chunkModificationStatus = new HashMap<>();
        this.lightManager = new LightManager(this);
        this.entities = new ArrayList<>();
        findSuitableCharacterPositionInChunk(0, 0);
    }

    // --- NEW --- This method contains the core logic for connecting walls.
    /**
     * Updates the connection bitmask for a single wall tile and marks the chunk for redraw.
     * @param r The global row of the wall tile to update.
     * @param c The global column of the wall tile to update.
     */
    public void updateWallConnections(int r, int c) {
        Tile targetTile = getTile(r, c);
        if (targetTile == null) {
            return; // Can't update a null tile
        }

        if (targetTile.getType() != Tile.TileType.WOOD_WALL) {
            if (targetTile.getWallConnectionBitmask() != 0) {
                targetTile.setWallConnectionBitmask(0); // Not a wall, so no connections.
                lightManager.markChunkDirty(r, c);
            }
            return;
        }

        int bitmask = 0;
        Tile neighbor;

        // Bit 1: Connection North
        neighbor = getTile(r - 1, c);
        if (neighbor != null && neighbor.getType() == Tile.TileType.WOOD_WALL) bitmask |= 1;

        // Bit 2: Connection East
        neighbor = getTile(r, c + 1);
        if (neighbor != null && neighbor.getType() == Tile.TileType.WOOD_WALL) bitmask |= 2;

        // Bit 4: Connection South
        neighbor = getTile(r + 1, c);
        if (neighbor != null && neighbor.getType() == Tile.TileType.WOOD_WALL) bitmask |= 4;

        // Bit 8: Connection West
        neighbor = getTile(r, c - 1);
        if (neighbor != null && neighbor.getType() == Tile.TileType.WOOD_WALL) bitmask |= 8;

        if (targetTile.getWallConnectionBitmask() != bitmask) {
            targetTile.setWallConnectionBitmask(bitmask);
            lightManager.markChunkDirty(r, c); // Mark chunk as dirty to force a re-render.
        }
    }

    // --- NEW --- A helper to update a tile AND all its neighbors.
    /**
     * Updates a central tile and tells its four cardinal neighbors to update as well.
     * This ensures the entire structure connects properly when a change is made.
     * @param r The global row of the tile that changed.
     * @param c The global column of the tile that changed.
     */
    public void updateSurroundingWalls(int r, int c) {
        updateWallConnections(r, c);       // Update the tile itself
        updateWallConnections(r - 1, c); // North
        updateWallConnections(r + 1, c); // South
        updateWallConnections(r, c - 1); // West
        updateWallConnections(r, c + 1); // East
    }


    public boolean placeBlock(int globalR, int globalC, Item itemToPlace) {
        Tile targetTile = getTile(globalR, globalC);
        System.out.println("[Map.placeBlock] Received call to place '" + itemToPlace.getItemId() + "'.");

        if (targetTile == null || itemToPlace == null) {
            return false;
        }

        // --- MODIFIED --- This is where we hook in the new logic.
        if (itemToPlace.getItemId().equals("wood_wall")) {
            if (targetTile.getType() == Tile.TileType.AIR || targetTile.getType() == Tile.TileType.GRASS || targetTile.getType() == Tile.TileType.DIRT || targetTile.getType() == Tile.TileType.SAND) {
                targetTile.setType(Tile.TileType.WOOD_WALL);
                targetTile.setElevation(targetTile.getElevation() + 1); // Make walls slightly higher
                updateSurroundingWalls(globalR, globalC); // Update the new wall and its neighbors
                markChunkAsModified(Math.floorDiv(globalC, CHUNK_SIZE_TILES), Math.floorDiv(globalR, CHUNK_SIZE_TILES));
                return true;
            }
            return false; // Can't place a wall here.
        }
        // --- End of modification ---

        // The rest of the original placeBlock logic...
        if (itemToPlace.type != Item.ItemType.RESOURCE) {
            return false;
        }
        int currentElevation = targetTile.getElevation();
        if (currentElevation >= ALTURA_MAXIMA && targetTile.getType() != Tile.TileType.AIR) {
            return false;
        }
        int newElevation = currentElevation + 1;

        Tile.TileType newType;
        if (itemToPlace.equals(ItemRegistry.getItem("dirt"))) {
            newType = determineTileTypeFromElevation(newElevation);
            if (targetTile.getType() == Tile.TileType.WATER) {
                newElevation = NIVEL_MAR;
                newType = determineTileTypeFromElevation(newElevation);
            }
        } else if (itemToPlace.equals(ItemRegistry.getItem("stone"))) {
            newType = Tile.TileType.ROCK;
        } else if (itemToPlace.equals(ItemRegistry.getItem("sand"))) {
            newType = Tile.TileType.SAND;
        } else {
            return false;
        }
        newElevation = Math.min(newElevation, ALTURA_MAXIMA);

        targetTile.setElevation(newElevation);
        targetTile.setType(newType);
        targetTile.setTreeType(Tile.TreeVisualType.NONE);
        targetTile.setLooseRockType(Tile.LooseRockType.NONE);
        markChunkAsModified(Math.floorDiv(globalC, CHUNK_SIZE_TILES), Math.floorDiv(globalR, CHUNK_SIZE_TILES));

        queueLightUpdateForArea(globalR, globalC, 2, this.lightManager);
        if (targetTile.hasTorch()) {
            lightManager.removeLightSource(globalR, globalC);
        }
        return true;
    }

    // ... (The rest of Map.java remains the same as your latest version) ...
    // Make sure to include all other methods from your file, like getTile, getOrGenerateChunkTiles, etc.
    // The code I'm omitting here is unchanged. Just add the new methods and the modification to placeBlock.
    public List<Entity> getEntities() {
        return this.entities;
    }

    public int getAnimalCount(){
        int count = 0;
        for (Entity e : entities){
            if (e instanceof Animal){
                count++;
            }
        }
        return count;
    }

    public Tile[][] getOrGenerateChunkTiles(int chunkX, int chunkY) {
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkX, chunkY);
        if (loadedChunkTiles.containsKey(coord)) {
            return loadedChunkTiles.get(coord);
        }

        Tile[][] chunkTiles = new Tile[CHUNK_SIZE_TILES][CHUNK_SIZE_TILES];
        int globalStartX = chunkX * CHUNK_SIZE_TILES;
        int globalStartY = chunkY * CHUNK_SIZE_TILES;

        for (int y = 0; y < CHUNK_SIZE_TILES; y++) {
            for (int x = 0; x < CHUNK_SIZE_TILES; x++) {
                double noiseValue = calculateCombinedNoise(globalStartX + x, globalStartY + y);
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * (ALTURA_MAXIMA + 1)) - 1;
                elevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));

                Tile.TileType type = determineTileTypeFromElevation(elevation);
                chunkTiles[y][x] = new Tile(type, elevation);

                if (type == Tile.TileType.GRASS && elevation >= NIVEL_ARENA && elevation < NIVEL_ROCA) {
                    if (random.nextFloat() < 0.08) {
                        chunkTiles[y][x].setTreeType(random.nextBoolean() ? Tile.TreeVisualType.APPLE_TREE_FRUITING : Tile.TreeVisualType.PINE_TREE_SMALL);
                    }
                }
                if (chunkTiles[y][x].getTreeType() == Tile.TreeVisualType.NONE &&
                        (type == Tile.TileType.GRASS || type == Tile.TileType.DIRT || type == Tile.TileType.ROCK || type == Tile.TileType.SAND) &&
                        elevation >= NIVEL_MAR && random.nextFloat() < 0.03) {
                    int rockTypeCount = Tile.LooseRockType.values().length - 1;
                    int randomRockIndex = random.nextInt(rockTypeCount) + 1;
                    chunkTiles[y][x].setLooseRockType(Tile.LooseRockType.values()[randomRockIndex]);
                }
            }
        }
        loadedChunkTiles.put(coord, chunkTiles);
        chunkModificationStatus.put(coord, false);
        return chunkTiles;
    }
    public Tile getTile(int globalR, int globalC) {
        int chunkX = Math.floorDiv(globalC, CHUNK_SIZE_TILES);
        int chunkY = Math.floorDiv(globalR, CHUNK_SIZE_TILES);

        Tile[][] chunkSpecificTiles = getOrGenerateChunkTiles(chunkX, chunkY);

        int localX = globalC % CHUNK_SIZE_TILES;
        localX = (localX < 0) ? localX + CHUNK_SIZE_TILES : localX;
        int localY = globalR % CHUNK_SIZE_TILES;
        localY = (localY < 0) ? localY + CHUNK_SIZE_TILES : localY;

        if (localX >= 0 && localX < CHUNK_SIZE_TILES && localY >= 0 && localY < CHUNK_SIZE_TILES) {
            return chunkSpecificTiles[localY][localX];
        } else {
            System.err.println("Map.getTile: Calculated invalid local coordinates (" + localY + "," + localX + ") for global (" + globalR + "," + globalC + ") in chunk (" + chunkY + "," + chunkX + ")");
            return null;
        }
    }

    public void markChunkAsModified(int chunkX, int chunkY) {
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkX, chunkY);
        if (!loadedChunkTiles.containsKey(coord)) {
            System.err.println("Map.markChunkAsModified: Attempting to mark a non-loaded chunk " + coord + ". This might indicate an issue.");
        }
        chunkModificationStatus.put(coord, true);
    }
    public void unloadChunkData(int chunkX, int chunkY) {
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkX, chunkY);
        if (loadedChunkTiles.containsKey(coord)) {
            if (chunkModificationStatus.getOrDefault(coord, false)) {
                System.out.println("Map: Chunk " + coord + " was modified and is being unloaded. (Disk saving not yet implemented in this method).");
            }
            loadedChunkTiles.remove(coord);
            chunkModificationStatus.remove(coord);
        }
    }
    private double calculateCombinedNoise(double globalX, double globalY) {
        double baseFrequency = NOISE_SCALE * 0.05;
        double mountainFrequency = NOISE_SCALE * 0.2;
        double roughnessFrequency = NOISE_SCALE * 0.8;
        double baseWeight = 1.0;
        double mountainWeight = 0.40;
        double roughnessWeight = 0.15;
        double baseNoise = noiseGenerator.octaveNoise(globalX * baseFrequency, globalY * baseFrequency, 4, 0.6);
        double mountainNoise = noiseGenerator.noise(globalX * mountainFrequency, globalY * mountainFrequency);
        double roughnessNoise = noiseGenerator.noise(globalX * roughnessFrequency, globalY * roughnessFrequency);
        double combined = (baseNoise * baseWeight) + (mountainNoise * mountainWeight) + (roughnessNoise * roughnessWeight);
        double normalizationFactor = baseWeight + mountainWeight + roughnessWeight;
        combined /= normalizationFactor;
        return Math.max(-1.0, Math.min(1.0, combined));
    }
    private void findSuitableCharacterPositionInChunk(int chunkX, int chunkY) {
        Tile[][] chunkTiles = getOrGenerateChunkTiles(chunkX, chunkY);
        int centerLocalRow = CHUNK_SIZE_TILES / 2;
        int centerLocalCol = CHUNK_SIZE_TILES / 2;
        for (int radius = 0; radius < CHUNK_SIZE_TILES / 2; radius++) {
            for (int rOffset = -radius; rOffset <= radius; rOffset++) {
                for (int cOffset = -radius; cOffset <= radius; cOffset++) {
                    if (Math.abs(rOffset) == radius || Math.abs(cOffset) == radius) {
                        int lr = centerLocalRow + rOffset;
                        int lc = centerLocalCol + cOffset;
                        if (lr >= 0 && lr < CHUNK_SIZE_TILES && lc >= 0 && lc < CHUNK_SIZE_TILES) {
                            Tile tile = chunkTiles[lr][lc];
                            if (tile.getType() != Tile.TileType.WATER && tile.getElevation() >= NIVEL_MAR) {
                                this.characterSpawnRow = chunkY * CHUNK_SIZE_TILES + lr;
                                this.characterSpawnCol = chunkX * CHUNK_SIZE_TILES + lc;
                                return;
                            }
                        }
                    }
                }
            }
        }
        this.characterSpawnRow = chunkY * CHUNK_SIZE_TILES + centerLocalRow;
        this.characterSpawnCol = chunkX * CHUNK_SIZE_TILES + centerLocalCol;
    }
    public void populateSaveData(MapSaveData saveData) {
        System.out.println("Map.populateSaveData: Saving seed and modified chunks...");
        saveData.worldSeed = this.worldSeed;
        saveData.playerSpawnR = this.characterSpawnRow;
        saveData.playerSpawnC = this.characterSpawnCol;
        saveData.explicitlySavedChunks = new ArrayList<>();
        int numSaved = 0;
        for (java.util.Map.Entry<LightManager.ChunkCoordinate, Boolean> entry : chunkModificationStatus.entrySet()) {
            if (entry.getValue() && loadedChunkTiles.containsKey(entry.getKey())) {
                MapSaveData.ChunkDiskData cdd = new MapSaveData.ChunkDiskData(entry.getKey().chunkX, entry.getKey().chunkY);
                cdd.tiles = convertChunkTilesToSaveFormat(loadedChunkTiles.get(entry.getKey()));
                saveData.explicitlySavedChunks.add(cdd);
                numSaved++;
                if (numSaved > 50) break;
            }
        }
        System.out.println("Map.populateSaveData: Would save " + saveData.explicitlySavedChunks.size() + " modified/loaded chunks (placeholder).");
        saveData.entities.clear();
        for (Entity entity : this.entities) {
            if (!(entity instanceof PlayerModel) && !(entity instanceof Projectile)) {
                EntitySaveData entityData = new EntitySaveData();
                entity.populateSaveData(entityData);
                saveData.entities.add(entityData);
            }
        }
        System.out.println("Map.populateSaveData: Saved " + saveData.entities.size() + " non-player entities.");
    }
    private List<List<TileSaveData>> convertChunkTilesToSaveFormat(Tile[][] chunkTiles) {
        List<List<TileSaveData>> savedRows = new ArrayList<>();
        for (int y = 0; y < CHUNK_SIZE_TILES; y++) {
            List<TileSaveData> savedCols = new ArrayList<>();
            for (int x = 0; x < CHUNK_SIZE_TILES; x++) {
                Tile tile = chunkTiles[y][x];
                TileSaveData tsd = new TileSaveData();
                tsd.typeOrdinal = tile.getType().ordinal();
                tsd.elevation = tile.getElevation();
                tsd.hasTorch = tile.hasTorch();
                tsd.skyLightLevel = tile.getSkyLightLevel();
                tsd.blockLightLevel = tile.getBlockLightLevel();
                tsd.treeTypeOrdinal = tile.getTreeType().ordinal();
                tsd.looseRockTypeOrdinal = tile.getLooseRockType().ordinal();
                savedCols.add(tsd);
            }
            savedRows.add(savedCols);
        }
        return savedRows;
    }
    public boolean loadState(MapSaveData mapData) {
        if (mapData == null) return false;
        this.characterSpawnRow = mapData.playerSpawnR;
        this.characterSpawnCol = mapData.playerSpawnC;
        loadedChunkTiles.clear();
        chunkModificationStatus.clear();
        if (mapData.explicitlySavedChunks != null) {
            for (MapSaveData.ChunkDiskData cdd : mapData.explicitlySavedChunks) {
                LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(cdd.chunkX, cdd.chunkY);
                Tile[][] tiles = convertSaveFormatToChunkTiles(cdd.tiles);
                if (tiles != null) {
                    loadedChunkTiles.put(coord, tiles);
                    chunkModificationStatus.put(coord, true);
                }
            }
        }
        this.entities.clear();
        if (mapData.entities != null) {
            for (EntitySaveData entityData : mapData.entities) {
                if (entityData == null || entityData.entityType == null) {
                    continue;
                }
                Entity newEntity = null;
                switch (entityData.entityType) {
                    case "COW": newEntity = new Cow(entityData.mapRow, entityData.mapCol); break;
                    case "SLIME": newEntity = new Slime(entityData.mapRow, entityData.mapCol); break;
                }
                if (newEntity != null) {
                    newEntity.health = entityData.health;
                    this.entities.add(newEntity);
                }
            }
        }
        return true;
    }
    private Tile[][] convertSaveFormatToChunkTiles(List<List<TileSaveData>> savedRows) {
        if (savedRows == null || savedRows.size() != CHUNK_SIZE_TILES) return null;
        Tile[][] chunkTiles = new Tile[CHUNK_SIZE_TILES][CHUNK_SIZE_TILES];
        try {
            for (int y = 0; y < CHUNK_SIZE_TILES; y++) {
                List<TileSaveData> savedCols = savedRows.get(y);
                if (savedCols == null || savedCols.size() != CHUNK_SIZE_TILES) return null;
                for (int x = 0; x < CHUNK_SIZE_TILES; x++) {
                    TileSaveData tsd = savedCols.get(x);
                    Tile tile = new Tile(Tile.TileType.values()[tsd.typeOrdinal], tsd.elevation);
                    tile.setHasTorch(tsd.hasTorch);
                    tile.setSkyLightLevel(tsd.skyLightLevel);
                    tile.setBlockLightLevel(tsd.blockLightLevel);
                    tile.setTreeType(Tile.TreeVisualType.values()[tsd.treeTypeOrdinal]);
                    tile.setLooseRockType(Tile.LooseRockType.values()[tsd.looseRockTypeOrdinal]);
                    chunkTiles[y][x] = tile;
                }
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            return null;
        }
        return chunkTiles;
    }
    public Tile.TileType determineTileTypeFromElevation(int elevation) {
        if (elevation < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevation < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevation < NIVEL_ROCA) return Tile.TileType.GRASS;
        if (elevation < NIVEL_ROCA + (NIVEL_NIEVE - NIVEL_ROCA) / 2) return Tile.TileType.ROCK;
        if (elevation < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }
    public void setTileElevation(int globalR, int globalC, int newElevation) {
        Tile tile = getTile(globalR, globalC);
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
        if (clampedElevation != oldElevation && tile.getLooseRockType() != Tile.LooseRockType.NONE) {
            tile.setLooseRockType(Tile.LooseRockType.NONE);
        }
        if (oldElevation != clampedElevation || oldType != tile.getType() || (oldTorchState != tile.hasTorch() && !oldTorchState) ) {
            int chunkX = Math.floorDiv(globalC, CHUNK_SIZE_TILES);
            int chunkY = Math.floorDiv(globalR, CHUNK_SIZE_TILES);
            markChunkAsModified(chunkX, chunkY);
            queueLightUpdateForArea(globalR, globalC, 2, this.lightManager);
            if (oldTorchState && (!tile.hasTorch() || oldElevation != clampedElevation)) {
                lightManager.removeLightSource(globalR, globalC);
            }
        }
    }
    public void queueLightUpdateForArea(int globalRow, int globalCol, int radius, LightManager lm) {
        if (lm == null) return;
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                int nr = globalRow + dr;
                int nc = globalCol + dc;
                Tile t = getTile(nr, nc);
                if (t != null) {
                    if (lm.isSurfaceTileExposedToSky(nr, nc, t.getElevation())) {
                        lm.getSkyLightPropagationQueue_Direct().add(new LightManager.LightNode(nr, nc, lm.getCurrentGlobalSkyLightTarget()));
                    } else if (t.getSkyLightLevel() > 0) {
                        lm.getSkyLightRemovalQueue_Direct().add(new LightManager.LightNode(nr, nc, t.getSkyLightLevel()));
                    }
                    if (t.getBlockLightLevel() > 0) {
                        lm.getBlockLightPropagationQueue_Direct().add(new LightManager.LightNode(nr, nc, t.getBlockLightLevel()));
                    }
                    if (t.hasTorch()) {
                        lm.addLightSource(nr, nc, (byte)TORCH_LIGHT_LEVEL);
                    }
                }
                lm.markChunkDirty(nr, nc);
            }
        }
    }
    public void toggleTorch(int globalR, int globalC) {
        Tile tile = getTile(globalR, globalC);
        if (tile != null && tile.getType() != Tile.TileType.WATER && tile.isSolidOpaqueBlock()) {
            if (tile.hasTorch()) {
                lightManager.removeLightSource(globalR, globalC);
            } else {
                lightManager.addLightSource(globalR, globalC, (byte) TORCH_LIGHT_LEVEL);
            }
            int chunkX = Math.floorDiv(globalC, CHUNK_SIZE_TILES);
            int chunkY = Math.floorDiv(globalR, CHUNK_SIZE_TILES);
            markChunkAsModified(chunkX, chunkY);
            queueLightUpdateForArea(globalR, globalC, 2, lightManager);
        }
    }
    public void propagateLightAcrossChunkBorder(LightManager.ChunkCoordinate sourceChunkCoord, LightManager.ChunkCoordinate targetChunkCoord, LightManager lm) {
        if (lm == null || sourceChunkCoord.equals(targetChunkCoord)) return;
        int sourceStartGlobalX = sourceChunkCoord.chunkX * CHUNK_SIZE_TILES;
        int sourceStartGlobalY = sourceChunkCoord.chunkY * CHUNK_SIZE_TILES;
        if (targetChunkCoord.chunkX > sourceChunkCoord.chunkX) {
            int borderX = sourceStartGlobalX + CHUNK_SIZE_TILES - 1;
            for (int y_local = 0; y_local < CHUNK_SIZE_TILES; y_local++) {
                queueBorderTileForLightPropagation(sourceStartGlobalY + y_local, borderX, lm);
            }
        } else if (targetChunkCoord.chunkX < sourceChunkCoord.chunkX) {
            int borderX = sourceStartGlobalX;
            for (int y_local = 0; y_local < CHUNK_SIZE_TILES; y_local++) {
                queueBorderTileForLightPropagation(sourceStartGlobalY + y_local, borderX, lm);
            }
        }
        if (targetChunkCoord.chunkY > sourceChunkCoord.chunkY) {
            int borderY = sourceStartGlobalY + CHUNK_SIZE_TILES - 1;
            for (int x_local = 0; x_local < CHUNK_SIZE_TILES; x_local++) {
                queueBorderTileForLightPropagation(borderY, sourceStartGlobalX + x_local, lm);
            }
        } else if (targetChunkCoord.chunkY < sourceChunkCoord.chunkY) {
            int borderY = sourceStartGlobalY;
            for (int x_local = 0; x_local < CHUNK_SIZE_TILES; x_local++) {
                queueBorderTileForLightPropagation(borderY, sourceStartGlobalX + x_local, lm);
            }
        }
    }
    private void queueBorderTileForLightPropagation(int globalR, int globalC, LightManager lm) {
        Tile tile = getTile(globalR, globalC);
        if (tile != null) {
            if (tile.getSkyLightLevel() > 0) {
                lm.getSkyLightPropagationQueue_Direct().add(new LightManager.LightNode(globalR, globalC, tile.getSkyLightLevel()));
            }
            if (tile.getBlockLightLevel() > 0) {
                lm.getBlockLightPropagationQueue_Direct().add(new LightManager.LightNode(globalR, globalC, tile.getBlockLightLevel()));
            }
            if (tile.hasTorch()) {
                lm.addLightSource(globalR, globalC, (byte)TORCH_LIGHT_LEVEL);
            }
        }
    }
    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }
    public LightManager getLightManager() { return lightManager; }
    public long getWorldSeed() { return worldSeed; }
    @Deprecated public int getWidth() { throw new UnsupportedOperationException("getWidth() is not supported for an infinite map."); }
    @Deprecated public int getHeight() { throw new UnsupportedOperationException("getHeight() is not supported for an infinite map."); }
}