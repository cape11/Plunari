// src/main/java/org/isogame/world/World.java
package org.isogame.world;

import org.isogame.constants.Constants;
import org.isogame.entity.*;
import org.isogame.game.EntityManager;
import org.isogame.game.Game;
import org.isogame.game.PlacementManager;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.savegame.GameSaveState;
import org.isogame.savegame.MapSaveData;
import org.isogame.savegame.PlayerSaveData;
import org.isogame.tile.Tile;

import java.util.*;

import static org.isogame.constants.Constants.*;

public class World {

    private final Game game;
    private final Map map;
    private final PlayerModel player;
    private final EntityManager entityManager;
    private final LightManager lightManager;
    private final PlacementManager placementManager;

    private double pseudoTimeOfDay;
    private byte lastGlobalSkyLightTargetSetInLM;
    private final Set<LightManager.ChunkCoordinate> currentlyActiveLogicalChunks = new HashSet<>();

    private double spawnTimer = 0.0;
    private final Random spawnRandom = new Random();
    private static final double SPAWN_CYCLE_TIME = 5.0;
    private static final int SPAWN_RADIUS = 32;

    private final Queue<LightManager.ChunkCoordinate> chunkRenderUpdateQueue = new LinkedList<>();
    private final Queue<LightManager.ChunkCoordinate> globalSkyRefreshNeededQueue = new LinkedList<>();
    private static final int MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME = 2;
    private static final int CHUNKS_TO_REFRESH_SKY_PER_FRAME = 4;

    public World(Game game, long seed) {
        this.game = game;
        this.entityManager = new EntityManager();
        this.map = new Map(seed);
        this.player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        this.entityManager.addEntity(player);
        this.lightManager = this.map.getLightManager();
        this.placementManager = new PlacementManager(game, this.map, this.player);
        this.pseudoTimeOfDay = 0.0005;
        byte initialSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        this.lightManager.setCurrentGlobalSkyLightTarget(initialSkyLight);
        this.lastGlobalSkyLightTargetSetInLM = initialSkyLight;
        initializeWorldState();
    }

    public World(Game game, GameSaveState saveState) {
        this.game = game;
        this.entityManager = new EntityManager();
        this.map = new Map(saveState.mapData.worldSeed);
        this.map.loadState(saveState.mapData);
        this.player = new PlayerModel(this.map.getCharacterSpawnRow(), this.map.getCharacterSpawnCol());
        this.player.loadState(saveState.playerData);
        this.entityManager.loadState(saveState);
        this.entityManager.addEntity(player);
        this.lightManager = this.map.getLightManager();
        this.placementManager = new PlacementManager(game, this.map, this.player);
        this.pseudoTimeOfDay = saveState.pseudoTimeOfDay;
        byte initialSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        this.lightManager.setCurrentGlobalSkyLightTarget(initialSkyLight);
        this.lastGlobalSkyLightTargetSetInLM = initialSkyLight;
        initializeWorldState();
        restoreTorchLightSources(saveState);
    }

    public void update(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;
        updateActiveChunksAroundPlayer();
        updateSkyLightBasedOnTimeOfDay();
        handleDynamicSpawning(deltaTime);
        processSkyRefreshQueue();
        entityManager.update(deltaTime, this.game);
        lightManager.processLightQueuesIncrementally();
        queueDirtyChunksForRenderUpdate();
        processChunkRenderUpdateQueue();
    }

    public void populateSaveData(GameSaveState saveState) {
        saveState.pseudoTimeOfDay = this.pseudoTimeOfDay;
        saveState.playerData = new PlayerSaveData();
        player.populateSaveData(saveState.playerData);
        saveState.mapData = new MapSaveData();
        map.populateSaveData(saveState.mapData);
        entityManager.populateSaveData(saveState);
    }

    private void initializeWorldState() {
        this.chunkRenderUpdateQueue.clear();
        this.globalSkyRefreshNeededQueue.clear();
        this.currentlyActiveLogicalChunks.clear();
        updateActiveChunksAroundPlayer();
        performIntensiveInitialLightProcessing();
    }

    private void updateActiveChunksAroundPlayer() {
        List<LightManager.ChunkCoordinate> desiredCoords = getDesiredActiveChunkCoordinates();
        Set<LightManager.ChunkCoordinate> desiredSet = new HashSet<>(desiredCoords);
        currentlyActiveLogicalChunks.removeIf(currentActiveCoord -> {
            if (!desiredSet.contains(currentActiveCoord)) {
                entityManager.unloadEntitiesInChunk(currentActiveCoord);
                game.getRenderer().unloadChunkGraphics(currentActiveCoord.chunkX, currentActiveCoord.chunkY);
                map.unloadChunkData(currentActiveCoord.chunkX, currentActiveCoord.chunkY);
                globalSkyRefreshNeededQueue.remove(currentActiveCoord);
                return true;
            }
            return false;
        });
        for (LightManager.ChunkCoordinate newCoord : desiredCoords) {
            if (currentlyActiveLogicalChunks.add(newCoord)) {
                map.getOrGenerateChunkTiles(newCoord.chunkX, newCoord.chunkY);
                game.getRenderer().ensureChunkGraphicsLoaded(newCoord.chunkX, newCoord.chunkY);
                lightManager.initializeSkylightForChunk(newCoord);
                globalSkyRefreshNeededQueue.offer(newCoord);
                propagateLightToNewChunkBorders(newCoord);
            }
        }
    }

    private List<LightManager.ChunkCoordinate> getDesiredActiveChunkCoordinates() {
        List<LightManager.ChunkCoordinate> desiredActive = new ArrayList<>();
        int playerChunkX = Math.floorDiv(player.getTileCol(), CHUNK_SIZE_TILES);
        int playerChunkY = Math.floorDiv(player.getTileRow(), CHUNK_SIZE_TILES);
        int renderDist = game.getCurrentRenderDistanceChunks();
        for (int dy = -renderDist; dy <= renderDist; dy++) {
            for (int dx = -renderDist; dx <= renderDist; dx++) {
                desiredActive.add(new LightManager.ChunkCoordinate(playerChunkX + dx, playerChunkY + dy));
            }
        }
        return desiredActive;
    }

    private void updateSkyLightBasedOnTimeOfDay() {
        byte currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        boolean significantChange = Math.abs(currentGlobalSkyLightActual - lastGlobalSkyLightTargetSetInLM) >= SKY_LIGHT_UPDATE_THRESHOLD;
        boolean boundaryReached = (currentGlobalSkyLightActual == SKY_LIGHT_DAY && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_DAY) ||
                (currentGlobalSkyLightActual == SKY_LIGHT_NIGHT_MINIMUM && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_NIGHT_MINIMUM);
        if (significantChange || boundaryReached) {
            lightManager.setCurrentGlobalSkyLightTarget(currentGlobalSkyLightActual);
            globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks);
            lastGlobalSkyLightTargetSetInLM = currentGlobalSkyLightActual;
        }
    }

    private void handleDynamicSpawning(double deltaTime) {
        spawnTimer += deltaTime;
        if (spawnTimer >= SPAWN_CYCLE_TIME) {
            spawnTimer = 0;
            if (entityManager.getEntityCount() >= MAX_ANIMALS + 1) return;
            int spawnTryC = player.getTileCol() + spawnRandom.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS;
            int spawnTryR = player.getTileRow() + spawnRandom.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS;
            int chunkX = Math.floorDiv(spawnTryC, CHUNK_SIZE_TILES);
            int chunkY = Math.floorDiv(spawnTryR, CHUNK_SIZE_TILES);
            if (currentlyActiveLogicalChunks.contains(new LightManager.ChunkCoordinate(chunkX, chunkY))) {
                Tile targetTile = map.getTile(spawnTryR, spawnTryC);
                if (targetTile != null && targetTile.getType() != Tile.TileType.WATER && !targetTile.isSolidOpaqueBlock()) {
                    Entity newEntity = spawnRandom.nextBoolean() ? new Slime(spawnTryR + 0.5f, spawnTryC + 0.5f) : new Cow(spawnTryR + 0.5f, spawnTryC + 0.5f);
                    entityManager.addEntity(newEntity);
                }
            }
        }
    }

    private void performIntensiveInitialLightProcessing() {
        int initialPasses = Math.max(15, currentlyActiveLogicalChunks.size());
        globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks);
        for (int i = 0; i < initialPasses; i++) {
            processSkyRefreshQueue();
            lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET * 2);
            queueDirtyChunksForRenderUpdate();
            processChunkRenderUpdateQueue();
            if (!lightManager.isAnyLightQueueNotEmpty() && globalSkyRefreshNeededQueue.isEmpty()) break;
        }
        lightManager.processAllQueuesToCompletion();
        queueDirtyChunksForRenderUpdate();
    }

    private void processSkyRefreshQueue() {
        int refreshedThisFrame = 0;
        while (refreshedThisFrame < CHUNKS_TO_REFRESH_SKY_PER_FRAME && !globalSkyRefreshNeededQueue.isEmpty()) {
            LightManager.ChunkCoordinate coordToRefresh = globalSkyRefreshNeededQueue.poll();
            if (currentlyActiveLogicalChunks.contains(coordToRefresh)) {
                lightManager.refreshSkyLightForSingleChunk(coordToRefresh, lightManager.getCurrentGlobalSkyLightTarget());
            }
            refreshedThisFrame++;
        }
    }

    private void queueDirtyChunksForRenderUpdate() {
        Set<LightManager.ChunkCoordinate> dirtyFromLighting = lightManager.getDirtyChunksAndClear();
        for (LightManager.ChunkCoordinate dirtyCoord : dirtyFromLighting) {
            if (currentlyActiveLogicalChunks.contains(dirtyCoord) && !chunkRenderUpdateQueue.contains(dirtyCoord)) {
                chunkRenderUpdateQueue.offer(dirtyCoord);
            }
        }
    }

    private void processChunkRenderUpdateQueue() {
        int updatedThisFrame = 0;
        while (updatedThisFrame < MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME && !chunkRenderUpdateQueue.isEmpty()) {
            LightManager.ChunkCoordinate coordToUpdate = chunkRenderUpdateQueue.poll();
            if (currentlyActiveLogicalChunks.contains(coordToUpdate)) {
                game.getRenderer().updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                updatedThisFrame++;
            }
        }
    }

    // *** NEW METHOD ***
    public void queueAllChunksForRenderUpdate() {
        this.chunkRenderUpdateQueue.addAll(this.currentlyActiveLogicalChunks);
    }

    private void propagateLightToNewChunkBorders(LightManager.ChunkCoordinate newCoord) {
        int[] dNeighborsX = {0, 0, 1, -1};
        int[] dNeighborsY = {1, -1, 0, 0};
        for (int i = 0; i < 4; i++) {
            LightManager.ChunkCoordinate neighborCoord = new LightManager.ChunkCoordinate(newCoord.chunkX + dNeighborsX[i], newCoord.chunkY + dNeighborsY[i]);
            if (currentlyActiveLogicalChunks.contains(neighborCoord)) {
                map.propagateLightAcrossChunkBorder(newCoord, neighborCoord, lightManager);
                map.propagateLightAcrossChunkBorder(neighborCoord, newCoord, lightManager);
            }
        }
    }

    private void restoreTorchLightSources(GameSaveState saveState) {
        if (saveState.mapData.explicitlySavedChunks == null) return;
        for (MapSaveData.ChunkDiskData cdd : saveState.mapData.explicitlySavedChunks) {
            LightManager.ChunkCoordinate activeChunk = new LightManager.ChunkCoordinate(cdd.chunkX, cdd.chunkY);
            if (currentlyActiveLogicalChunks.contains(activeChunk)) {
                Tile[][] tiles = map.getOrGenerateChunkTiles(activeChunk.chunkX, activeChunk.chunkY);
                if (tiles == null) continue;
                for (int r_local = 0; r_local < CHUNK_SIZE_TILES; r_local++) {
                    for (int c_local = 0; c_local < CHUNK_SIZE_TILES; c_local++) {
                        if (tiles[r_local][c_local].hasTorch()) {
                            int globalR = activeChunk.chunkY * CHUNK_SIZE_TILES + r_local;
                            int globalC = activeChunk.chunkX * CHUNK_SIZE_TILES + c_local;
                            lightManager.addLightSource(globalR, globalC, (byte) TORCH_LIGHT_LEVEL);
                        }
                    }
                }
            }
        }
        lightManager.processAllQueuesToCompletion();
    }

    private byte calculateSkyLightForTime(double time) {
        float phase;
        if (time < 0.40) return SKY_LIGHT_DAY;
        else if (time < 0.60) {
            phase = (float) ((time - 0.40) / 0.20);
            return (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT_MINIMUM));
        } else if (time < 0.90) return SKY_LIGHT_NIGHT_MINIMUM;
        else {
            phase = (float) ((time - 0.90) / 0.10);
            return (byte) (SKY_LIGHT_NIGHT_MINIMUM + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT_MINIMUM));
        }
    }

    public void requestTileRenderUpdate(int r, int c) {
        if (this.lightManager != null) {
            this.lightManager.markChunkDirty(r, c);
        }
    }

    public Map getMap() { return map; }
    public PlayerModel getPlayer() { return player; }
    public EntityManager getEntityManager() { return entityManager; }
    public LightManager getLightManager() { return lightManager; }
    public PlacementManager getPlacementManager() { return placementManager; }
    public double getPseudoTimeOfDay() { return pseudoTimeOfDay; }
    public Queue<LightManager.ChunkCoordinate> getChunkRenderUpdateQueue() { return chunkRenderUpdateQueue; }
}