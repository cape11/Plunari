package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import static org.isogame.constants.Constants.*;

public class LightManager {

    private final ChunkManager chunkManager;
    private final Map mapAPI; // Provides getTile access across chunks

    private final Set<ChunkCoordinate> dirtyChunksForRenderer;
    // Queue for chunks that need their skylight fully recalculated
    private final Queue<ChunkCoordinate> skyLightRecalculationQueue;

    // Queues for BFS light propagation (typically for block lights or targeted updates)
    private final Queue<LightNode> blockLightPropagationQueue;
    private final Queue<LightNode> blockLightRemovalQueue;


    public LightManager(Map mapAPI, ChunkManager chunkManager) {
        this.mapAPI = mapAPI;
        this.chunkManager = chunkManager;
        this.dirtyChunksForRenderer = new HashSet<>();
        this.skyLightRecalculationQueue = new LinkedList<>();
        this.blockLightPropagationQueue = new LinkedList<>();
        this.blockLightRemovalQueue = new LinkedList<>();
    }

    // --- Queue Management for Game Loop ---
    public Set<ChunkCoordinate> getDirtyChunksAndClear() {
        Set<ChunkCoordinate> currentDirty = new HashSet<>(dirtyChunksForRenderer);
        dirtyChunksForRenderer.clear();
        return currentDirty;
    }

    public Set<ChunkCoordinate> getSkyLightRecalculationQueueAndClear() {
        Set<ChunkCoordinate> batchToProcess = new HashSet<>();
        ChunkCoordinate current;
        // Drain the queue into a set for processing this tick.
        while ((current = skyLightRecalculationQueue.poll()) != null) {
            batchToProcess.add(current);
        }
        return batchToProcess;
    }

    public boolean isSkyLightQueueEmpty() {
        return skyLightRecalculationQueue.isEmpty();
    }

    public int getSkyLightQueueSize() {
        return skyLightRecalculationQueue.size();
    }

    // --- Core Public Methods ---
    public void markChunkDirty(int chunkX, int chunkY) {
        dirtyChunksForRenderer.add(new ChunkCoordinate(chunkX, chunkY));
    }

    public void enqueueSkyLightRecalculationForChunk(int chunkX, int chunkY) {
        ChunkCoordinate coord = new ChunkCoordinate(chunkX, chunkY);
        if (!skyLightRecalculationQueue.contains(coord)) { // Avoid duplicates in queue
            skyLightRecalculationQueue.offer(coord);
        }
    }

    /**
     * Called when global sky light changes. Queues the specified set of chunks (typically rendered ones)
     * for a full skylight recalculation.
     */
    public void updateGlobalSkyLightForSpecificChunks(byte globalSkyLightLevel, Set<ChunkCoordinate> chunksToUpdate) {
        for (ChunkCoordinate coord : chunksToUpdate) {
            if (chunkManager.getLoadedChunk(coord.chunkX, coord.chunkY) != null) {
                enqueueSkyLightRecalculationForChunk(coord.chunkX, coord.chunkY);
            }
        }
    }

    /**
     * Initializes or recalculates all lighting (sky and then block) for a given chunk.
     * This is the main entry point for lighting a chunk from scratch or after a major change.
     */
    public void initializeLightingForNewChunk(int chunkX, int chunkY, byte globalSkyLightLevel) {
        ChunkData chunkData = chunkManager.getLoadedChunk(chunkX, chunkY);
        if (chunkData == null) {
            return;
        }

        int worldStartX = chunkX * CHUNK_SIZE_TILES;
        int worldStartY = chunkY * CHUNK_SIZE_TILES;

        Queue<LightNode> skyLightSourcesForBFS = new LinkedList<>();

        // 1. Reset sky light levels in this chunk to 0
        for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
            for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                Tile tile = chunkData.getTile(localX, localY);
                if (tile != null) {
                    tile.setSkyLightLevel((byte) 0);
                }
            }
        }

        // 2. Sky Light Seeding (Downward Pass for this chunk)
        for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
            int worldX = worldStartX + localX;
            byte currentColumnSkyPotential = globalSkyLightLevel;

            for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
                int worldY = worldStartY + localY;
                Tile tile = chunkData.getTile(localX, localY);

                if (tile == null) {
                    if (currentColumnSkyPotential > 0) {
                        skyLightSourcesForBFS.add(new LightNode(worldY, worldX, currentColumnSkyPotential, LightType.SKY));
                    }
                } else {
                    if (currentColumnSkyPotential > 0) {
                        tile.setSkyLightLevel(currentColumnSkyPotential);
                        skyLightSourcesForBFS.add(new LightNode(worldY, worldX, currentColumnSkyPotential, LightType.SKY));
                    } else {
                        tile.setSkyLightLevel((byte) 0);
                    }
                    if (!tile.isTransparentToLight()) {
                        currentColumnSkyPotential = 0;
                    }
                }
            }
        }

        // --- Enhanced Cross-Chunk Seeding ---
        int[] dNeighborChunkX = {0, 0, -1, 1};
        int[] dNeighborChunkY = {-1, 1, 0, 0};

        for (int i = 0; i < 4; i++) {
            int neighborAbsChunkX = chunkX + dNeighborChunkX[i];
            int neighborAbsChunkY = chunkY + dNeighborChunkY[i];

            ChunkData neighborChunkData = chunkManager.getLoadedChunk(neighborAbsChunkX, neighborAbsChunkY);
            if (neighborChunkData == null) {
                continue;
            }

            if (dNeighborChunkX[i] == -1) {
                for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
                    Tile currentBorderTile = chunkData.getTile(0, localY);
                    Tile neighborBorderTile = neighborChunkData.getTile(CHUNK_SIZE_TILES - 1, localY);
                    primeTileFromNeighbor(currentBorderTile, neighborBorderTile,
                            worldStartX, worldStartY + localY,
                            skyLightSourcesForBFS);
                }
            } else if (dNeighborChunkX[i] == 1) {
                for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
                    Tile currentBorderTile = chunkData.getTile(CHUNK_SIZE_TILES - 1, localY);
                    Tile neighborBorderTile = neighborChunkData.getTile(0, localY);
                    primeTileFromNeighbor(currentBorderTile, neighborBorderTile,
                            worldStartX + CHUNK_SIZE_TILES - 1, worldStartY + localY,
                            skyLightSourcesForBFS);
                }
            } else if (dNeighborChunkY[i] == -1) {
                for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                    Tile currentBorderTile = chunkData.getTile(localX, 0);
                    Tile neighborBorderTile = neighborChunkData.getTile(localX, CHUNK_SIZE_TILES - 1);
                    primeTileFromNeighbor(currentBorderTile, neighborBorderTile,
                            worldStartX + localX, worldStartY,
                            skyLightSourcesForBFS);
                }
            } else if (dNeighborChunkY[i] == 1) {
                for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                    Tile currentBorderTile = chunkData.getTile(localX, CHUNK_SIZE_TILES - 1);
                    Tile neighborBorderTile = neighborChunkData.getTile(localX, 0);
                    primeTileFromNeighbor(currentBorderTile, neighborBorderTile,
                            worldStartX + localX, worldStartY + CHUNK_SIZE_TILES - 1,
                            skyLightSourcesForBFS);
                }
            }
        }

        // 3. Propagate Sky Light (BFS)
        while(!skyLightSourcesForBFS.isEmpty()){
            propagateLight(skyLightSourcesForBFS.poll());
        }

        // 4. Re-evaluate block light sources
        for (int y = 0; y < CHUNK_SIZE_TILES; y++) {
            for (int x = 0; x < CHUNK_SIZE_TILES; x++) {
                Tile tile = chunkData.getTile(x,y);
                if (tile != null && tile.hasTorch()) {
                    addLightSource(worldStartY + y, worldStartX + x, (byte)TORCH_LIGHT_LEVEL);
                }
            }
        }
        markChunkDirty(chunkX, chunkY);
    }

    private void primeTileFromNeighbor(Tile currentBorderTile, Tile neighborBorderTile,
                                       int currentTileWorldCol, int currentTileWorldRow,
                                       Queue<LightNode> skyLightSourcesForBFS) {
        if (currentBorderTile == null || neighborBorderTile == null) {
            return;
        }

        byte neighborSkyLight = neighborBorderTile.getSkyLightLevel();

        // Cost for light to propagate one step from neighbor to current tile's location.
        // The currentBorderTile's own material opacity is not considered here for light *arriving* at its surface.
        int costToReachCurrentTileLocation = LIGHT_PROPAGATION_COST;

        if (neighborSkyLight > costToReachCurrentTileLocation) {
            byte potentialLightFromNeighbor = (byte) (neighborSkyLight - costToReachCurrentTileLocation);
            potentialLightFromNeighbor = (byte) Math.max(0, potentialLightFromNeighbor);

            if (potentialLightFromNeighbor > currentBorderTile.getSkyLightLevel()) {
                currentBorderTile.setSkyLightLevel(potentialLightFromNeighbor);
                skyLightSourcesForBFS.add(new LightNode(currentTileWorldRow, currentTileWorldCol, potentialLightFromNeighbor, LightType.SKY));
            }
        }
    }

    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = mapAPI.getTile(r, c);
        if (tile != null) {
            tile.setHasTorch(true);
            if (lightLevel > tile.getBlockLightLevel()) {
                tile.setBlockLightLevel(lightLevel);
                blockLightPropagationQueue.add(new LightNode(r, c, lightLevel, LightType.BLOCK));
                processBlockLightPropagationQueue();
                markChunkDirty(Map.worldToChunkCoord(c), Map.worldToChunkCoord(r));
                markNeighborChunksDirtyForBlockLight(r,c);
            }
        }
    }

    public void removeLightSource(int r, int c) {
        Tile tile = mapAPI.getTile(r, c);
        if (tile != null && tile.hasTorch()) {
            byte oldLightLevel = tile.getBlockLightLevel();
            tile.setHasTorch(false);
            tile.setBlockLightLevel((byte) 0);

            if (oldLightLevel > 0) {
                blockLightRemovalQueue.add(new LightNode(r, c, oldLightLevel, LightType.BLOCK));
                processBlockLightRemovalQueue();
                markChunkDirty(Map.worldToChunkCoord(c), Map.worldToChunkCoord(r));
                markNeighborChunksDirtyForBlockLight(r,c);
            }
        }
    }

    private void markNeighborChunksDirtyForBlockLight(int worldR, int worldC) {
        int centerChunkX = Map.worldToChunkCoord(worldC);
        int centerChunkY = Map.worldToChunkCoord(worldR);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (chunkManager.getLoadedChunk(centerChunkX + dx, centerChunkY + dy) != null) {
                    markChunkDirty(centerChunkX + dx, centerChunkY + dy);
                }
            }
        }
    }

    private void processBlockLightPropagationQueue() {
        while (!blockLightPropagationQueue.isEmpty()) {
            propagateLight(blockLightPropagationQueue.poll());
        }
    }

    private void processBlockLightRemovalQueue() {
        Set<LightNode> sourcesToRePropagate = new HashSet<>();
        Queue<LightNode> currentRemovalWave = new LinkedList<>(blockLightRemovalQueue);
        blockLightRemovalQueue.clear();

        Set<ChunkCoordinate> affectedChunksForReprop = new HashSet<>();

        while (!currentRemovalWave.isEmpty()) {
            LightNode removedNode = currentRemovalWave.poll();
            int r = removedNode.r;
            int c = removedNode.c;
            byte lightLevelThatWasAtRemovedNode = removedNode.lightLevel;

            markChunkDirty(Map.worldToChunkCoord(c), Map.worldToChunkCoord(r));

            int[] dRow = {-1, 1, 0, 0};
            int[] dCol = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int nr = r + dRow[i];
                int nc = c + dCol[i];

                Tile neighbor = mapAPI.getTile(nr, nc);
                if (neighbor == null) continue;

                byte neighborCurrentBlockLight = neighbor.getBlockLightLevel();
                if (neighborCurrentBlockLight == 0) continue;

                int opacityCostToEnterNeighbor = getOpacityCost(neighbor, LightType.BLOCK);
                byte lightFromRemovedSourcePath = (byte) Math.max(0, lightLevelThatWasAtRemovedNode - LIGHT_PROPAGATION_COST - opacityCostToEnterNeighbor);

                if (neighborCurrentBlockLight <= lightFromRemovedSourcePath) {
                    neighbor.setBlockLightLevel((byte) 0);
                    currentRemovalWave.add(new LightNode(nr, nc, neighborCurrentBlockLight, LightType.BLOCK));
                    markChunkDirty(Map.worldToChunkCoord(nc), Map.worldToChunkCoord(nr));
                } else {
                    sourcesToRePropagate.add(new LightNode(nr, nc, neighborCurrentBlockLight, LightType.BLOCK));
                    affectedChunksForReprop.add(new ChunkCoordinate(Map.worldToChunkCoord(nc),Map.worldToChunkCoord(nr)));
                }
            }
        }

        for (LightNode source : sourcesToRePropagate) {
            Tile tile = mapAPI.getTile(source.r, source.c);
            if(tile != null && tile.getBlockLightLevel() > 0) {
                this.blockLightPropagationQueue.add(new LightNode(source.r, source.c, tile.getBlockLightLevel(), LightType.BLOCK));
            } else if (tile != null && tile.hasTorch()){
                this.blockLightPropagationQueue.add(new LightNode(source.r, source.c, (byte)TORCH_LIGHT_LEVEL, LightType.BLOCK));
                tile.setBlockLightLevel((byte)TORCH_LIGHT_LEVEL);
            }
        }

        if (!this.blockLightPropagationQueue.isEmpty()) {
            processBlockLightPropagationQueue();
        }
        for(ChunkCoordinate coord : affectedChunksForReprop){
            markChunkDirty(coord.chunkX, coord.chunkY);
        }
    }

    private void propagateLight(LightNode sourceNode) {
        Queue<LightNode> queue = new LinkedList<>();
        queue.add(sourceNode);

        Set<ChunkCoordinate> affectedChunks = new HashSet<>();
        affectedChunks.add(new ChunkCoordinate(Map.worldToChunkCoord(sourceNode.c), Map.worldToChunkCoord(sourceNode.r)));

        while(!queue.isEmpty()){
            LightNode current = queue.poll();
            int r = current.r;
            int c = current.c;
            byte currentLightLevelOnCoord = current.lightLevel;
            LightType type = current.type;

            if (currentLightLevelOnCoord <= 0) continue;

            int[] dr_options = {-1, 1, 0, 0};
            int[] dc_options = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int nr = r + dr_options[i];
                int nc = c + dc_options[i];

                Tile neighborTile = mapAPI.getTile(nr, nc);

                int opacityCostToEnterNeighbor = getOpacityCost(neighborTile, type);
                int propagationCostThisStep = LIGHT_PROPAGATION_COST;

                if (type == LightType.SKY) {
                    Tile tilePropagatingFrom = mapAPI.getTile(r,c);
                    if (dr_options[i] > 0 && (tilePropagatingFrom == null || tilePropagatingFrom.isTransparentToLight())) {
                        propagationCostThisStep = 0;
                    }
                    if (dr_options[i] < 0 && tilePropagatingFrom != null && !tilePropagatingFrom.isTransparentToLight()) {
                        propagationCostThisStep = MAX_LIGHT_LEVEL + 1;
                    }
                }

                byte lightReachingNeighbor = (byte) (currentLightLevelOnCoord - propagationCostThisStep - opacityCostToEnterNeighbor);
                lightReachingNeighbor = (byte) Math.max(0, lightReachingNeighbor);

                if (lightReachingNeighbor <= 0) continue;

                if (neighborTile != null) {
                    byte existingNeighborLight;
                    if (type == LightType.BLOCK) {
                        existingNeighborLight = neighborTile.getBlockLightLevel();
                    } else {
                        existingNeighborLight = neighborTile.getSkyLightLevel();
                    }

                    if (lightReachingNeighbor > existingNeighborLight) {
                        if (type == LightType.BLOCK) {
                            neighborTile.setBlockLightLevel(lightReachingNeighbor);
                        } else {
                            neighborTile.setSkyLightLevel(lightReachingNeighbor);
                        }
                        affectedChunks.add(new ChunkCoordinate(Map.worldToChunkCoord(nc), Map.worldToChunkCoord(nr)));
                        queue.add(new LightNode(nr, nc, lightReachingNeighbor, type));
                    }
                }
            }
        }
        for(ChunkCoordinate coord : affectedChunks){
            markChunkDirty(coord.chunkX, coord.chunkY);
        }
    }

    private int getOpacityCost(Tile tileBeingEntered, LightType type) {
        if (tileBeingEntered == null) {
            return 0;
        }

        if (type == LightType.SKY) {
            switch (tileBeingEntered.getType()) {
                case WATER:
                    return 1;
                case GRASS:
                case SAND:
                case ROCK:
                case SNOW:
                    // For skylight spreading onto the surface of these solid blocks,
                    // the cost should be minimal (or zero) beyond standard propagation.
                    // Their opaqueness is for light *passing through* them.
                    return 0;
                default:
                    return MAX_LIGHT_LEVEL + 1;
            }
        } else {
            switch (tileBeingEntered.getType()) {
                case WATER: return 3;
                case GRASS:
                case SAND:
                case ROCK:
                case SNOW:
                    return 1;
                default:
                    return tileBeingEntered.isTransparentToLight() ? 0 : (MAX_LIGHT_LEVEL + 1);
            }
        }
    }

    public void onChunkUnloaded(int chunkX, int chunkY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                ChunkCoordinate neighborCoord = new ChunkCoordinate(chunkX + dx, chunkY + dy);
                if (chunkManager.getLoadedChunk(neighborCoord.chunkX, neighborCoord.chunkY) != null) {
                    enqueueSkyLightRecalculationForChunk(neighborCoord.chunkX, neighborCoord.chunkY);
                }
            }
        }
    }

    private static class LightNode {
        final int r, c;
        final byte lightLevel;
        final LightType type;

        LightNode(int r, int c, byte lightLevel, LightType type) {
            this.r = r; this.c = c; this.lightLevel = lightLevel; this.type = type;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LightNode n = (LightNode) o;
            return r == n.r && c == n.c && type == n.type && lightLevel == n.lightLevel;
        }
        @Override
        public int hashCode() {
            return Objects.hash(r, c, type, lightLevel);
        }
        @Override
        public String toString() {
            return "LightNode ("+r+","+c+") L:"+lightLevel+" T:"+type;
        }
    }

    private enum LightType { SKY, BLOCK }
}
