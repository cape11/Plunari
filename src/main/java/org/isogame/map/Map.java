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

                // --- ANIMAL SPAWNING LOGIC HAS BEEN REMOVED FROM THIS METHOD ---

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

    /**
     * Retrieves a tile from global map coordinates.
     * Handles loading or generating the chunk if it's not already in memory.
     *
     * @param globalR Global row coordinate of the tile.
     * @param globalC Global column coordinate of the tile.
     * @return The Tile at the given coordinates, or null if coordinates are somehow invalid (should be rare).
     */
    public Tile getTile(int globalR, int globalC) {
        int chunkX = Math.floorDiv(globalC, CHUNK_SIZE_TILES);
        int chunkY = Math.floorDiv(globalR, CHUNK_SIZE_TILES);

        Tile[][] chunkSpecificTiles = getOrGenerateChunkTiles(chunkX, chunkY);

        int localX = globalC % CHUNK_SIZE_TILES;
        localX = (localX < 0) ? localX + CHUNK_SIZE_TILES : localX; // Ensure positive modulo result
        int localY = globalR % CHUNK_SIZE_TILES;
        localY = (localY < 0) ? localY + CHUNK_SIZE_TILES : localY; // Ensure positive modulo result


        if (localX >= 0 && localX < CHUNK_SIZE_TILES && localY >= 0 && localY < CHUNK_SIZE_TILES) {
            return chunkSpecificTiles[localY][localX];
        } else {
            // This should ideally not happen with correct floorDiv and modulo logic.
            System.err.println("Map.getTile: Calculated invalid local coordinates (" + localY + "," + localX + ") for global (" + globalR + "," + globalC + ") in chunk (" + chunkY + "," + chunkX + ")");
            return null; // Or a default "void" tile
        }
    }

    /**
     * Marks a chunk as modified (e.g., by player interaction).
     * This is a flag for the saving system (Phase 2).
     * @param chunkX The X coordinate of the chunk (in chunk units).
     * @param chunkY The Y coordinate of the chunk (in chunk units).
     */
    public void markChunkAsModified(int chunkX, int chunkY) {
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkX, chunkY);
        // Ensure the chunk data is loaded before trying to mark it modified.
        // Typically, this would be called after an action on an already loaded chunk.
        if (!loadedChunkTiles.containsKey(coord)) {
            // This might happen if a modification occurs near a chunk border affecting an unloaded chunk.
            // getOrGenerateChunkTiles(chunkX, chunkY); // Load it if not already.
            // However, modifications should ideally only happen to loaded, active chunks.
            System.err.println("Map.markChunkAsModified: Attempting to mark a non-loaded chunk " + coord + ". This might indicate an issue.");
        }
        chunkModificationStatus.put(coord, true);
    }

    /**
     * Unloads chunk tile data from memory.
     * In a future phase, this would also trigger saving the chunk to disk if it was modified.
     * @param chunkX The X coordinate of the chunk to unload.
     * @param chunkY The Y coordinate of the chunk to unload.
     */
    public void unloadChunkData(int chunkX, int chunkY) {
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkX, chunkY);
        if (loadedChunkTiles.containsKey(coord)) {
            if (chunkModificationStatus.getOrDefault(coord, false)) {
                System.out.println("Map: Chunk " + coord + " was modified and is being unloaded. (Disk saving not yet implemented in this method).");
                // TODO: Phase 2 - Call saveChunkToDisk(coord, loadedChunkTiles.get(coord));
            }
            loadedChunkTiles.remove(coord);
            chunkModificationStatus.remove(coord);
            // System.out.println("Unloaded tile data for chunk: (" + chunkX + ", " + chunkY + ")");
        }
    }

    /**
     * Noise generation function, using global coordinates.
     */
    private double calculateCombinedNoise(double globalX, double globalY) {
        // Parameters for different noise layers (can be tuned)
        double baseFrequency = NOISE_SCALE * 0.05;    // Overall landscape shape
        double mountainFrequency = NOISE_SCALE * 0.2; // For larger features like mountains/valleys
        double roughnessFrequency = NOISE_SCALE * 0.8;  // For smaller surface details

        // Weights for each noise layer
        double baseWeight = 1.0;
        double mountainWeight = 0.40; // Mountains are less dominant but significant
        double roughnessWeight = 0.15; // Roughness adds fine detail

        // Calculate each noise component
        double baseNoise = noiseGenerator.octaveNoise(globalX * baseFrequency, globalY * baseFrequency, 4, 0.6);
        double mountainNoise = noiseGenerator.noise(globalX * mountainFrequency, globalY * mountainFrequency); // Single octave for broader features
        double roughnessNoise = noiseGenerator.noise(globalX * roughnessFrequency, globalY * roughnessFrequency);

        // Combine noise layers
        double combined = (baseNoise * baseWeight) + (mountainNoise * mountainWeight) + (roughnessNoise * roughnessWeight);

        // Normalize (approximate, as weights change the range)
        // A more robust normalization might be needed if weights vary significantly
        double normalizationFactor = baseWeight + mountainWeight + roughnessWeight;
        combined /= normalizationFactor; // Normalize to roughly -1 to 1 range

        return Math.max(-1.0, Math.min(1.0, combined)); // Clamp to ensure it's within -1 to 1
    }

    /**
     * Smooths the terrain elevation data for a single chunk.
     * @param elevations The 2D array of raw elevation data for the chunk.
     */
    private void smoothChunkTerrain(int[][] elevations) { // Default to 1 pass
        smoothChunkTerrain(elevations, 1);
    }

    private void smoothChunkTerrain(int[][] elevations, int passes) {
        if (passes <= 0) return;
        int chunkHeight = elevations.length;    // Should be CHUNK_SIZE_TILES
        int chunkWidth = elevations[0].length; // Should be CHUNK_SIZE_TILES
        int[][] tempElevations = new int[chunkHeight][chunkWidth];

        for (int pass = 0; pass < passes; pass++) {
            // Copy current elevations to temp for reading
            for (int r = 0; r < chunkHeight; r++) {
                System.arraycopy(elevations[r], 0, tempElevations[r], 0, chunkWidth);
            }

            // Apply smoothing based on neighbors
            for (int r = 0; r < chunkHeight; r++) {
                for (int c = 0; c < chunkWidth; c++) {
                    int sum = 0;
                    int count = 0;
                    for (int dr = -1; dr <= 1; dr++) {
                        for (int dc = -1; dc <= 1; dc++) {
                            int nr = r + dr;
                            int nc = c + dc;
                            // Check bounds within the current chunk's tempElevations
                            if (nr >= 0 && nr < chunkHeight && nc >= 0 && nc < chunkWidth) {
                                sum += tempElevations[nr][nc];
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        elevations[r][c] = sum / count; // Average elevation
                    }
                }
            }
        }
    }

    /**
     * Finds a suitable character spawn position within a specified chunk.
     * Prioritizes non-water tiles above sea level.
     * @param chunkX The X coordinate of the chunk to spawn in.
     * @param chunkY The Y coordinate of the chunk to spawn in.
     */
    private void findSuitableCharacterPositionInChunk(int chunkX, int chunkY) {
        Tile[][] chunkTiles = getOrGenerateChunkTiles(chunkX, chunkY); // Ensure this chunk is generated

        int centerLocalRow = CHUNK_SIZE_TILES / 2;
        int centerLocalCol = CHUNK_SIZE_TILES / 2;

        // Search outwards from the center of the chunk
        for (int radius = 0; radius < CHUNK_SIZE_TILES / 2; radius++) {
            for (int rOffset = -radius; rOffset <= radius; rOffset++) {
                for (int cOffset = -radius; cOffset <= radius; cOffset++) {
                    // Check only the perimeter of the current search radius
                    if (Math.abs(rOffset) == radius || Math.abs(cOffset) == radius) {
                        int lr = centerLocalRow + rOffset; // local row in chunk
                        int lc = centerLocalCol + cOffset; // local col in chunk

                        // Ensure local coordinates are within chunk bounds
                        if (lr >= 0 && lr < CHUNK_SIZE_TILES && lc >= 0 && lc < CHUNK_SIZE_TILES) {
                            Tile tile = chunkTiles[lr][lc];
                            if (tile.getType() != Tile.TileType.WATER && tile.getElevation() >= NIVEL_MAR) {
                                // Convert local chunk coordinates to global map coordinates
                                this.characterSpawnRow = chunkY * CHUNK_SIZE_TILES + lr;
                                this.characterSpawnCol = chunkX * CHUNK_SIZE_TILES + lc;
                                return; // Found a suitable spot
                            }
                        }
                    }
                }
            }
        }
        // Fallback: if no ideal spot is found, spawn at the center of the chunk (global coords)
        this.characterSpawnRow = chunkY * CHUNK_SIZE_TILES + centerLocalRow;
        this.characterSpawnCol = chunkX * CHUNK_SIZE_TILES + centerLocalCol;
    }

    /**
     * Populates save data.
     * THIS IS A PLACEHOLDER. For a true infinite world, this needs to save
     * world metadata (seed) and potentially a list of modified chunk files,
     * or manage saving individual chunk files elsewhere.
     */
    public void populateSaveData(MapSaveData saveData) {
        System.out.println("Map.populateSaveData: Saving seed and modified chunks...");
        saveData.worldSeed = this.worldSeed;
        saveData.playerSpawnR = this.characterSpawnRow;
        saveData.playerSpawnC = this.characterSpawnCol;
        saveData.explicitlySavedChunks = new ArrayList<>();

        // In a full system, you'd iterate through `chunkModificationStatus`
        // and for each modified chunk, serialize its `loadedChunkTiles.get(coord)`
        // into a format suitable for `saveData` or individual files.
        // For now, let's imagine it saves a few loaded & modified chunks.
        saveData.explicitlySavedChunks = new ArrayList<>();
        int numSaved = 0;
        for (java.util.Map.Entry<LightManager.ChunkCoordinate, Boolean> entry : chunkModificationStatus.entrySet()) {
            if (entry.getValue() && loadedChunkTiles.containsKey(entry.getKey())) { // If modified and loaded
                MapSaveData.ChunkDiskData cdd = new MapSaveData.ChunkDiskData(entry.getKey().chunkX, entry.getKey().chunkY);
                cdd.tiles = convertChunkTilesToSaveFormat(loadedChunkTiles.get(entry.getKey()));
                saveData.explicitlySavedChunks.add(cdd);
                numSaved++;
                if (numSaved > 50) break; // Limit for this placeholder
            }
        }
        System.out.println("Map.populateSaveData: Would save " + saveData.explicitlySavedChunks.size() + " modified/loaded chunks (placeholder).");
        saveData.entities.clear();
        for (Entity entity : this.entities) {
            // This now skips the Player AND any subclasses of Projectile
            if (!(entity instanceof PlayerModel) && !(entity instanceof Projectile)) {
                EntitySaveData entityData = new EntitySaveData();
                entity.populateSaveData(entityData);
                saveData.entities.add(entityData);
            }
        }
        System.out.println("Map.populateSaveData: Saved " + saveData.entities.size() + " non-player entities.");

        for (Entity entity : this.entities) {
            if (!(entity instanceof PlayerModel)) { // Don't save the player here
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


    /**
     * Loads map state.
     * THIS IS A PLACEHOLDER. For a true infinite world, this would load
     * world metadata (seed) and then chunks would be loaded on demand from individual files.
     */
    public boolean loadState(MapSaveData mapData) {
        System.out.println("Map.loadState: Placeholder for infinite world. Loading seed and pre-loading some chunks.");
        if (mapData == null) return false;

        // Critical: Re-initialize noise generator with the loaded seed!
        // this.worldSeed = mapData.worldSeed; // This is already done in the constructor if mapData.worldSeed is passed.
        // Ensure the current Map instance was created with mapData.worldSeed
        // this.noiseGenerator = new SimplexNoise((int) mapData.worldSeed); // Re-init with loaded seed

        this.characterSpawnRow = mapData.playerSpawnR; // Load spawn point
        this.characterSpawnCol = mapData.playerSpawnC; // FIX: Correct variable name

        loadedChunkTiles.clear();
        chunkModificationStatus.clear();

        if (mapData.explicitlySavedChunks != null) {
            for (MapSaveData.ChunkDiskData cdd : mapData.explicitlySavedChunks) {
                LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(cdd.chunkX, cdd.chunkY);
                Tile[][] tiles = convertSaveFormatToChunkTiles(cdd.tiles);
                if (tiles != null) {
                    loadedChunkTiles.put(coord, tiles);
                    chunkModificationStatus.put(coord, true); // Assume saved chunks were modified or important
                }
            }
            System.out.println("Map.loadState: Pre-loaded " + mapData.explicitlySavedChunks.size() + " chunks from save data.");
        }

        // --- NEW: Load all non-player entities ---
        this.entities.clear();
        if (mapData.entities != null) {
            for (EntitySaveData entityData : mapData.entities) {
                // This safety check prevents crashes from corrupted save data
                if (entityData == null || entityData.entityType == null) {
                    System.err.println("Skipping a null or typeless entity in the save file.");
                    continue;
                }

                Entity newEntity = null;
                switch (entityData.entityType) {
                    case "COW":
                        newEntity = new Cow(entityData.mapRow, entityData.mapCol);
                        break;
                    case "SLIME":
                        newEntity = new Slime(entityData.mapRow, entityData.mapCol);
                        break;
                }

                if (newEntity != null) {
                    newEntity.health = entityData.health;
                    this.entities.add(newEntity);
                }
            }
            System.out.println("Map.loadState: Loaded " + this.entities.size() + " non-player entities.");
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
            System.err.println("Error converting saved chunk data to Tile[][]: " + e.getMessage());
            return null; // Or handle more gracefully
        }
        return chunkTiles;
    }


    // --- Utility methods that now operate with global coordinates ---

    public Tile.TileType determineTileTypeFromElevation(int elevation) {
        if (elevation < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevation < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevation < NIVEL_ROCA) return Tile.TileType.GRASS;
        // Adjusted rock layer to be a bit more prominent before snow
        if (elevation < NIVEL_ROCA + (NIVEL_NIEVE - NIVEL_ROCA) / 2) return Tile.TileType.ROCK;
        if (elevation < NIVEL_NIEVE) return Tile.TileType.ROCK; // More rock
        return Tile.TileType.SNOW;
    }

    public boolean placeBlock(int globalR, int globalC, Item itemToPlace) {
        Tile targetTile = getTile(globalR, globalC); // This now handles on-demand chunk loading/generation
        if (targetTile == null) {
            System.err.println("Map.placeBlock: Failed to get or generate tile at " + globalR + "," + globalC);
            return false;
        }
        if (itemToPlace == null || itemToPlace.type != Item.ItemType.RESOURCE) {
            return false;
        }

        int currentElevation = targetTile.getElevation();
        // Allow placing blocks up to ALTURA_MAXIMA.
        // If placing on a tile already at ALTURA_MAXIMA, it's not allowed unless it's replacing AIR.
        // This logic might need refinement based on desired game mechanics.
        if (currentElevation >= ALTURA_MAXIMA && targetTile.getType() != Tile.TileType.AIR) {
            // System.out.println("Cannot place block: Target tile is at max elevation.");
            return false;
        }
        int newElevation = currentElevation + 1; // Typically, placing a block increases elevation by 1.

        Tile.TileType newType;
        if (itemToPlace.equals(ItemRegistry.getItem("dirt"))) {
            newType = determineTileTypeFromElevation(newElevation);
            if (targetTile.getType() == Tile.TileType.WATER) { // Filling water
                newElevation = NIVEL_MAR; // Bring up to standard land level
                newType = determineTileTypeFromElevation(newElevation); // Recalculate type for this new elevation
            }
        } else if (itemToPlace.equals(ItemRegistry.getItem("stone"))) {
            newType = Tile.TileType.ROCK;
        } else if (itemToPlace.equals(ItemRegistry.getItem("sand"))) {
            newType = Tile.TileType.SAND;
        } else {
            // System.out.println("Cannot place item: " + itemToPlace.getDisplayName() + " is not a placeable block type.");
            return false;
        }
        // Ensure new elevation doesn't exceed max height after adjustments (e.g. for water filling)
        newElevation = Math.min(newElevation, ALTURA_MAXIMA);


        targetTile.setElevation(newElevation);
        targetTile.setType(newType);
        targetTile.setTreeType(Tile.TreeVisualType.NONE); // Placing a block removes any tree
        targetTile.setLooseRockType(Tile.LooseRockType.NONE); // Placing a block removes any loose rock
        int chunkX = Math.floorDiv(globalC, CHUNK_SIZE_TILES);
        int chunkY = Math.floorDiv(globalR, CHUNK_SIZE_TILES);
        markChunkAsModified(chunkX, chunkY);

        queueLightUpdateForArea(globalR, globalC, 2, this.lightManager);
        if (targetTile.hasTorch()) { // If a torch was on the tile we built upon (e.g. if it was AIR)
            lightManager.removeLightSource(globalR, globalC); // This will also set tile.hasTorch to false
        }
        return true;
    }


    public void setTileElevation(int globalR, int globalC, int newElevation) {
        Tile tile = getTile(globalR, globalC); // This handles chunk loading/generation
        if (tile == null) return;

        int oldElevation = tile.getElevation();
        Tile.TileType oldType = tile.getType();
        boolean oldTorchState = tile.hasTorch();

        int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, newElevation));
        tile.setElevation(clampedElevation);
        tile.setType(determineTileTypeFromElevation(clampedElevation));

        if (clampedElevation != oldElevation && tile.getTreeType() != Tile.TreeVisualType.NONE) {
            tile.setTreeType(Tile.TreeVisualType.NONE); // Elevation change removes trees
        }
        if (clampedElevation != oldElevation && tile.getLooseRockType() != Tile.LooseRockType.NONE) {
            tile.setLooseRockType(Tile.LooseRockType.NONE); // Elevation change removes loose rocks
        }

        if (oldElevation != clampedElevation || oldType != tile.getType() || (oldTorchState != tile.hasTorch() && !oldTorchState) ) { // Check if torch was added or removed
            int chunkX = Math.floorDiv(globalC, CHUNK_SIZE_TILES);
            int chunkY = Math.floorDiv(globalR, CHUNK_SIZE_TILES);
            markChunkAsModified(chunkX, chunkY);

            queueLightUpdateForArea(globalR, globalC, 2, this.lightManager);

            // If torch state changed or elevation changed where a torch was
            if (oldTorchState && (!tile.hasTorch() || oldElevation != clampedElevation)) {
                lightManager.removeLightSource(globalR, globalC); // removeLightSource handles tile.setHasTorch(false)
            }
            // If a torch is now present (either newly placed or was already there and elevation didn't destroy it)
            // and it wasn't there before in this state, or its light needs re-assertion.
            // The addLightSource in queueLightUpdateForArea should handle re-asserting existing torches.
        }
    }

    public void queueLightUpdateForArea(int globalRow, int globalCol, int radius, LightManager lm) {
        if (lm == null) return;
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                int nr = globalRow + dr;
                int nc = globalCol + dc;

                Tile t = getTile(nr, nc); // Ensures chunk data exists for this tile
                if (t != null) {
                    // Queue sky light updates
                    if (lm.isSurfaceTileExposedToSky(nr, nc, t.getElevation())) {
                        // If exposed, it should try to get the current global target and propagate from there.
                        lm.getSkyLightPropagationQueue_Direct().add(new LightManager.LightNode(nr, nc, lm.getCurrentGlobalSkyLightTarget()));
                    } else if (t.getSkyLightLevel() > 0) {
                        // If not exposed but had sky light, it needs to be removed.
                        lm.getSkyLightRemovalQueue_Direct().add(new LightManager.LightNode(nr, nc, t.getSkyLightLevel()));
                    }

                    // Queue block light updates (if any block light was present)
                    if (t.getBlockLightLevel() > 0) {
                        lm.getBlockLightPropagationQueue_Direct().add(new LightManager.LightNode(nr, nc, t.getBlockLightLevel()));
                    }

                    // If the tile itself is a torch, ensure its source is (re)added to propagation.
                    if (t.hasTorch()) {
                        lm.addLightSource(nr, nc, (byte)TORCH_LIGHT_LEVEL); // addLightSource handles setting block light on tile
                    }
                }
                // Mark the CHUNK containing (nr, nc) as dirty for rendering.
                // LightManager's markChunkDirty can take global tile coords.
                lm.markChunkDirty(nr, nc);
            }
        }
    }

    public void toggleTorch(int globalR, int globalC) {
        Tile tile = getTile(globalR, globalC); // This handles chunk loading/generation
        if (tile != null && tile.getType() != Tile.TileType.WATER && tile.isSolidOpaqueBlock()) {
            if (tile.hasTorch()) {
                lightManager.removeLightSource(globalR, globalC); // This method in LightManager should set tile.setHasTorch(false)
            } else {
                lightManager.addLightSource(globalR, globalC, (byte) TORCH_LIGHT_LEVEL); // This method in LightManager should set tile.setHasTorch(true)
            }
            // Mark chunk as modified
            int chunkX = Math.floorDiv(globalC, CHUNK_SIZE_TILES);
            int chunkY = Math.floorDiv(globalR, CHUNK_SIZE_TILES);
            markChunkAsModified(chunkX, chunkY);

            queueLightUpdateForArea(globalR, globalC, 2, lightManager); // Update lighting in the area
        }
    }

    public void propagateLightAcrossChunkBorder(LightManager.ChunkCoordinate sourceChunkCoord, LightManager.ChunkCoordinate targetChunkCoord, LightManager lm) {
        if (lm == null || sourceChunkCoord.equals(targetChunkCoord)) return;

        int sourceStartGlobalX = sourceChunkCoord.chunkX * CHUNK_SIZE_TILES;
        int sourceStartGlobalY = sourceChunkCoord.chunkY * CHUNK_SIZE_TILES;

        // Iterate over tiles that are on the border of sourceChunkCoord facing targetChunkCoord
        // For each of these border tiles in the source chunk, queue them for light propagation.
        // The light propagation logic itself (in LightManager) will then attempt to spread light
        // into the adjacent tiles, which might be in the targetChunkCoord.

        if (targetChunkCoord.chunkX > sourceChunkCoord.chunkX) { // Target is East of Source, check East border of Source
            int borderX = sourceStartGlobalX + CHUNK_SIZE_TILES - 1; // Global X of the easternmost column of source chunk
            for (int y_local = 0; y_local < CHUNK_SIZE_TILES; y_local++) {
                queueBorderTileForLightPropagation(sourceStartGlobalY + y_local, borderX, lm);
            }
        } else if (targetChunkCoord.chunkX < sourceChunkCoord.chunkX) { // Target is West of Source, check West border of Source
            int borderX = sourceStartGlobalX; // Global X of the westernmost column of source chunk
            for (int y_local = 0; y_local < CHUNK_SIZE_TILES; y_local++) {
                queueBorderTileForLightPropagation(sourceStartGlobalY + y_local, borderX, lm);
            }
        }

        if (targetChunkCoord.chunkY > sourceChunkCoord.chunkY) { // Target is South of Source, check South border of Source
            int borderY = sourceStartGlobalY + CHUNK_SIZE_TILES - 1; // Global Y of the southernmost row of source chunk
            for (int x_local = 0; x_local < CHUNK_SIZE_TILES; x_local++) {
                queueBorderTileForLightPropagation(borderY, sourceStartGlobalX + x_local, lm);
            }
        } else if (targetChunkCoord.chunkY < sourceChunkCoord.chunkY) { // Target is North of Source, check North border of Source
            int borderY = sourceStartGlobalY; // Global Y of the northernmost row of source chunk
            for (int x_local = 0; x_local < CHUNK_SIZE_TILES; x_local++) {
                queueBorderTileForLightPropagation(borderY, sourceStartGlobalX + x_local, lm);
            }
        }
    }

    /**
     * Helper to queue a specific border tile for light propagation.
     * Ensures the tile (and its chunk) is loaded before queueing.
     */
    private void queueBorderTileForLightPropagation(int globalR, int globalC, LightManager lm) {
        Tile tile = getTile(globalR, globalC); // Ensures chunk is loaded/generated
        if (tile != null) {
            // If sky light is present, add to sky propagation queue
            if (tile.getSkyLightLevel() > 0) {
                // Basic check to avoid redundant queueing, more sophisticated checks can be added
                // LightManager.LightNode node = new LightManager.LightNode(globalR, globalC, tile.getSkyLightLevel());
                // if (!lm.getSkyLightPropagationQueue_Direct().contains(node)) { // Contains check can be slow for large queues
                lm.getSkyLightPropagationQueue_Direct().add(new LightManager.LightNode(globalR, globalC, tile.getSkyLightLevel()));
                // }
            }
            // If block light is present, add to block propagation queue
            if (tile.getBlockLightLevel() > 0) {
                // LightManager.LightNode node = new LightManager.LightNode(globalR, globalC, tile.getBlockLightLevel());
                // if (!lm.getBlockLightPropagationQueue_Direct().contains(node)) {
                lm.getBlockLightPropagationQueue_Direct().add(new LightManager.LightNode(globalR, globalC, tile.getBlockLightLevel()));
                // }
            }
            // If the tile itself is a torch, re-assert its light source
            if (tile.hasTorch()) {
                lm.addLightSource(globalR, globalC, (byte)TORCH_LIGHT_LEVEL);
            }
        }
    }

    // Getters
    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }
    public LightManager getLightManager() { return lightManager; }
    public long getWorldSeed() { return worldSeed; }

    // These are deprecated as the map is no longer of a fixed, predefined size.
    @Deprecated public int getWidth() { throw new UnsupportedOperationException("getWidth() is not supported for an infinite map."); }
    @Deprecated public int getHeight() { throw new UnsupportedOperationException("getHeight() is not supported for an infinite map."); }
}
