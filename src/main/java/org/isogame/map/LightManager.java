package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.Collections;
import static org.isogame.constants.Constants.*;

public class LightManager {

    private final ChunkManager chunkManager;
    private final Map mapAPI;

    private final Queue<ChunkCoordinate> skyLightRecalculationQueue;
    private final Queue<LightNode> blockLightPropagationQueue;
    private final Queue<LightNode> blockLightRemovalQueue;

    private final Set<ChunkCoordinate> dirtyChunksForRenderer;
    private final Set<ChunkCoordinate> chunksProcessedThisLightingTick;

    public LightManager(Map mapAPI, ChunkManager chunkManager) {
        this.mapAPI = mapAPI;
        this.chunkManager = chunkManager;
        this.dirtyChunksForRenderer = new HashSet<>();
        this.skyLightRecalculationQueue = new LinkedList<>();
        this.blockLightPropagationQueue = new LinkedList<>();
        this.blockLightRemovalQueue = new LinkedList<>();
        this.chunksProcessedThisLightingTick = new HashSet<>();
    }

    public void resetTickProcessedSet() {
        chunksProcessedThisLightingTick.clear();
    }

    public boolean wasChunkProcessedThisTick(ChunkCoordinate coord) {
        return chunksProcessedThisLightingTick.contains(coord);
    }

    public Set<ChunkCoordinate> getSkyLightRecalculationQueueAsSetAndClear() {
        Set<ChunkCoordinate> batchToProcess = new HashSet<>();
        ChunkCoordinate current;
        while ((current = skyLightRecalculationQueue.poll()) != null) {
            batchToProcess.add(current);
        }
        return batchToProcess;
    }

    public void enqueueSkyLightRecalculationForChunks(Set<ChunkCoordinate> coords) {
        for (ChunkCoordinate coord : coords) {
            enqueueSkyLightRecalculationForChunk(coord.chunkX, coord.chunkY);
        }
    }

    public boolean isSkyLightQueueEmpty() {
        return skyLightRecalculationQueue.isEmpty();
    }

    public int getSkyLightQueueSize() {
        return skyLightRecalculationQueue.size();
    }

    public Set<ChunkCoordinate> getDirtyChunksForRendererAndClear() {
        Set<ChunkCoordinate> currentDirty = new HashSet<>(dirtyChunksForRenderer);
        dirtyChunksForRenderer.clear();
        return currentDirty;
    }

    private void markChunkAndNeighborsDirtyForRenderer(int chunkX, int chunkY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                ChunkCoordinate coord = new ChunkCoordinate(chunkX + dx, chunkY + dy);
                if (chunkManager.getLoadedChunk(coord.chunkX, coord.chunkY) != null) {
                    dirtyChunksForRenderer.add(coord);
                }
            }
        }
    }

    public void enqueueSkyLightRecalculationForChunk(int chunkX, int chunkY) {
        ChunkCoordinate coord = new ChunkCoordinate(chunkX, chunkY);
        if (!skyLightRecalculationQueue.contains(coord)) {
            skyLightRecalculationQueue.offer(coord);
        }
    }

    public void updateGlobalSkyLightForAllLoadedChunks() {
        Set<ChunkCoordinate> allLoaded = chunkManager.getLoadedChunkCoordinates();
        if (!allLoaded.isEmpty()) {
            enqueueSkyLightRecalculationForChunks(allLoaded);
        }
    }

    public Set<ChunkCoordinate> initializeSkylightForChunk(int chunkX, int chunkY, byte globalSkyLightLevel) {
        ChunkCoordinate currentChunkCoord = new ChunkCoordinate(chunkX, chunkY);
        chunksProcessedThisLightingTick.add(currentChunkCoord);

        ChunkData chunkData = chunkManager.getLoadedChunk(chunkX, chunkY);
        if (chunkData == null) {
            return Collections.emptySet();
        }

        Set<ChunkCoordinate> neighborsPotentiallyAffected = new HashSet<>();
        Queue<LightNode> skyLightBFSQueue = new LinkedList<>(); // For BFS spreading after direct pass

        int worldStartX = chunkX * CHUNK_SIZE_TILES;
        int worldStartY = chunkY * CHUNK_SIZE_TILES;

        // 1. Reset skylight levels in this chunk
        for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
            for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                Tile tile = chunkData.getTile(localX, localY);
                if (tile != null) {
                    tile.setSkyLightLevel((byte) 0);
                }
            }
        }

        // 2. Direct Downward Sky Light Application (Revised for clarity and robustness)
        for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
            byte lightStrengthInColumn = globalSkyLightLevel;
            boolean lightBlockedInThisColumn = false;

            for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) { // Iterate from top (Y=0) downwards
                Tile tile = chunkData.getTile(localX, localY);
                int worldR = worldStartY + localY;
                int worldC = worldStartX + localX;

                if (lightBlockedInThisColumn) { // Light was blocked by an opaque tile higher up in this column
                    if (tile != null) tile.setSkyLightLevel((byte) 0);
                    continue; // No light reaches further down this column directly
                }

                // If lightStrengthInColumn is already 0 (e.g. from transparent attenuation),
                // but not yet "blocked" by an opaque tile.
                if (lightStrengthInColumn <= 0) {
                    if (tile != null) tile.setSkyLightLevel((byte) 0);
                    // Continue, as BFS might still light it from the side if it's air/transparent
                    // Add air blocks to BFS queue even if currentStrength is 0, so they can receive from sides.
                    if (tile == null) {
                        skyLightBFSQueue.add(new LightNode(worldR, worldC, (byte)0, LightType.SKY, true));
                    }
                    continue;
                }


                if (tile == null) { // Air block
                    skyLightBFSQueue.add(new LightNode(worldR, worldC, lightStrengthInColumn, LightType.SKY, true));
                    // Light strength does not diminish passing through air in the direct downward column pass
                } else { // Tile exists
                    tile.setSkyLightLevel(lightStrengthInColumn);
                    skyLightBFSQueue.add(new LightNode(worldR, worldC, lightStrengthInColumn, LightType.SKY, false));

                    if (!tile.isTransparentToLight()) { // Opaque block
                        lightBlockedInThisColumn = true; // Mark as blocked for subsequent tiles in this column
                        // lightStrengthInColumn remains for this tile, but 0 for below.
                    } else { // Transparent block (e.g., water)
                        lightStrengthInColumn -= getOpacityCost(tile, LightType.SKY_DIRECT_DOWN);
                        lightStrengthInColumn = (byte) Math.max(0, lightStrengthInColumn);
                    }
                }
            }
        }

        // 3. Prime from Neighbors (Seed skylight from adjacent chunks' borders for BFS)
        primeSkylightFromNeighbors(chunkX, chunkY, chunkData, skyLightBFSQueue, neighborsPotentiallyAffected);

        // 4. Propagate Sky Light using BFS (spreads from directly lit surfaces/air and primed borders)
        if (!skyLightBFSQueue.isEmpty()) {
            propagateLightBFS(skyLightBFSQueue, LightType.SKY, neighborsPotentiallyAffected);
        }

        // 5. Recalculate block lights after skylight is established
        recalculateBlockLightsInChunk(chunkX, chunkY, neighborsPotentiallyAffected);

        markChunkAndNeighborsDirtyForRenderer(chunkX, chunkY);
        return neighborsPotentiallyAffected;
    }

    private void recalculateBlockLightsInChunk(int chunkX, int chunkY, Set<ChunkCoordinate> neighborsPotentiallyAffected) {
        ChunkData chunkData = chunkManager.getLoadedChunk(chunkX, chunkY);
        if (chunkData == null) return;

        Queue<LightNode> blockLightSourcesInChunk = new LinkedList<>();
        int worldStartX = chunkX * CHUNK_SIZE_TILES;
        int worldStartY = chunkY * CHUNK_SIZE_TILES;

        for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
            for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                Tile tile = chunkData.getTile(localX, localY);
                if (tile != null) {
                    tile.setBlockLightLevel((byte) 0);
                }
            }
        }
        for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
            for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                Tile tile = chunkData.getTile(localX, localY);
                if (tile != null && tile.hasTorch()) {
                    tile.setBlockLightLevel((byte) TORCH_LIGHT_LEVEL);
                    blockLightSourcesInChunk.add(new LightNode(worldStartY + localY, worldStartX + localX, (byte) TORCH_LIGHT_LEVEL, LightType.BLOCK, false));
                }
            }
        }
        if (!blockLightSourcesInChunk.isEmpty()) {
            propagateLightBFS(blockLightSourcesInChunk, LightType.BLOCK, neighborsPotentiallyAffected);
        }
    }

    private void primeSkylightFromNeighbors(int chunkX, int chunkY, ChunkData currentChunkData,
                                            Queue<LightNode> skyLightBFSQueue,
                                            Set<ChunkCoordinate> neighborsPotentiallyAffected) {
        int[] dNeighborChunkX = {0, 0, -1, 1};
        int[] dNeighborChunkY = {-1, 1, 0, 0};

        for (int i = 0; i < 4; i++) {
            int neighborAbsChunkX = chunkX + dNeighborChunkX[i];
            int neighborAbsChunkY = chunkY + dNeighborChunkY[i];
            ChunkCoordinate neighborCoord = new ChunkCoordinate(neighborAbsChunkX, neighborAbsChunkY);
            ChunkData neighborChunkData = chunkManager.getLoadedChunk(neighborAbsChunkX, neighborAbsChunkY);

            if (neighborChunkData == null) continue;

            int currentWorldStartX = chunkX * CHUNK_SIZE_TILES;
            int currentWorldStartY = chunkY * CHUNK_SIZE_TILES;

            for (int k = 0; k < CHUNK_SIZE_TILES; k++) {
                Tile currentBorderTile = null;
                Tile neighborBorderTile = null;
                int currentTileWorldR = 0, currentTileWorldC = 0;

                if (dNeighborChunkX[i] == -1) {
                    currentTileWorldR = currentWorldStartY + k; currentTileWorldC = currentWorldStartX;
                    currentBorderTile = currentChunkData.getTile(0, k);
                    neighborBorderTile = neighborChunkData.getTile(CHUNK_SIZE_TILES - 1, k);
                } else if (dNeighborChunkX[i] == 1) {
                    currentTileWorldR = currentWorldStartY + k; currentTileWorldC = currentWorldStartX + CHUNK_SIZE_TILES - 1;
                    currentBorderTile = currentChunkData.getTile(CHUNK_SIZE_TILES - 1, k);
                    neighborBorderTile = neighborChunkData.getTile(0, k);
                } else if (dNeighborChunkY[i] == -1) {
                    currentTileWorldR = currentWorldStartY; currentTileWorldC = currentWorldStartX + k;
                    currentBorderTile = currentChunkData.getTile(k, 0);
                    neighborBorderTile = neighborChunkData.getTile(k, CHUNK_SIZE_TILES - 1);
                } else if (dNeighborChunkY[i] == 1) {
                    currentTileWorldR = currentWorldStartY + CHUNK_SIZE_TILES - 1; currentTileWorldC = currentWorldStartX + k;
                    currentBorderTile = currentChunkData.getTile(k, CHUNK_SIZE_TILES - 1);
                    neighborBorderTile = neighborChunkData.getTile(k, 0);
                }

                byte neighborLight = (neighborBorderTile != null) ? neighborBorderTile.getSkyLightLevel() : MAX_LIGHT_LEVEL;
                boolean currentIsAir = (currentBorderTile == null);
                byte currentLightOnBorder = currentIsAir ? 0 : currentBorderTile.getSkyLightLevel();

                int costToEnterCurrent = LIGHT_PROPAGATION_COST + getOpacityCost(currentBorderTile, LightType.SKY_BFS_HORIZONTAL);
                byte potentialLightFromNeighbor = (byte) Math.max(0, neighborLight - costToEnterCurrent);

                if (potentialLightFromNeighbor > currentLightOnBorder) {
                    if (!currentIsAir) {
                        currentBorderTile.setSkyLightLevel(potentialLightFromNeighbor);
                    }
                    skyLightBFSQueue.add(new LightNode(currentTileWorldR, currentTileWorldC, potentialLightFromNeighbor, LightType.SKY, currentIsAir));
                    neighborsPotentiallyAffected.add(neighborCoord);
                }
            }
        }
    }

    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = mapAPI.getTile(r, c);
        if (tile != null) {
            tile.setHasTorch(true);
            tile.setBlockLightLevel(lightLevel);

            Queue<LightNode> q = new LinkedList<>();
            q.add(new LightNode(r, c, lightLevel, LightType.BLOCK, false));
            Set<ChunkCoordinate> affectedNeighbors = new HashSet<>();
            propagateLightBFS(q, LightType.BLOCK, affectedNeighbors);

            markChunkAndNeighborsDirtyForRenderer(Map.worldToChunkCoord(c), Map.worldToChunkCoord(r));
            enqueueSkyLightRecalculationForChunk(Map.worldToChunkCoord(c), Map.worldToChunkCoord(r));
            enqueueSkyLightRecalculationForChunks(affectedNeighbors);
        }
    }

    public void removeLightSource(int r, int c) {
        Tile tile = mapAPI.getTile(r, c);
        if (tile != null && tile.hasTorch()) {
            byte oldLightLevel = tile.getBlockLightLevel();
            tile.setHasTorch(false);
            tile.setBlockLightLevel((byte) 0);

            Queue<LightNode> removalQ = new LinkedList<>();
            removalQ.add(new LightNode(r, c, oldLightLevel, LightType.BLOCK, false));
            Set<LightNode> sourcesToRePropagate = new HashSet<>();
            processBlockLightRemovalBFS(removalQ, sourcesToRePropagate);

            if (!sourcesToRePropagate.isEmpty()) {
                Queue<LightNode> repropagateQ = new LinkedList<>();
                for (LightNode node : sourcesToRePropagate) {
                    Tile reTile = mapAPI.getTile(node.r, node.c);
                    if (reTile != null) {
                        byte lightToRepropagate = reTile.hasTorch() ? (byte)TORCH_LIGHT_LEVEL : reTile.getBlockLightLevel();
                        if (lightToRepropagate > 0) {
                            repropagateQ.add(new LightNode(node.r, node.c, lightToRepropagate, LightType.BLOCK, false));
                        }
                    }
                }
                if (!repropagateQ.isEmpty()) {
                    propagateLightBFS(repropagateQ, LightType.BLOCK, new HashSet<>());
                }
            }
            markChunkAndNeighborsDirtyForRenderer(Map.worldToChunkCoord(c), Map.worldToChunkCoord(r));
            int chunkX = Map.worldToChunkCoord(c);
            int chunkY = Map.worldToChunkCoord(r);
            enqueueSkyLightRecalculationForChunk(chunkX, chunkY);
            for(int dx = -1; dx <=1; dx++) {
                for(int dy = -1; dy <=1; dy++) {
                    if(dx == 0 && dy == 0) continue;
                    if(chunkManager.getLoadedChunk(chunkX+dx, chunkY+dy) != null) {
                        enqueueSkyLightRecalculationForChunk(chunkX+dx, chunkY+dy);
                    }
                }
            }
        }
    }

    private void processBlockLightRemovalBFS(Queue<LightNode> removalQueue, Set<LightNode> sourcesToRePropagate) {
        Queue<LightNode> currentWave = new LinkedList<>(removalQueue);
        Set<ChunkCoordinate> chunksToMarkDirty = new HashSet<>();

        while (!currentWave.isEmpty()) {
            LightNode removedNode = currentWave.poll();
            chunksToMarkDirty.add(new ChunkCoordinate(Map.worldToChunkCoord(removedNode.c), Map.worldToChunkCoord(removedNode.r)));

            int[] dRow = {-1, 1, 0, 0};
            int[] dCol = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int nr = removedNode.r + dRow[i];
                int nc = removedNode.c + dCol[i];

                Tile neighborTile = mapAPI.getTile(nr, nc);
                if (neighborTile == null) continue;

                byte neighborCurrentBlockLight = neighborTile.getBlockLightLevel();
                if (neighborCurrentBlockLight == 0) continue;

                int costToEnterNeighbor = LIGHT_PROPAGATION_COST + getOpacityCost(neighborTile, LightType.BLOCK);
                byte lightFromRemovedPath = (byte) Math.max(0, removedNode.lightLevel - costToEnterNeighbor);

                if (neighborCurrentBlockLight <= lightFromRemovedPath) {
                    neighborTile.setBlockLightLevel((byte) 0);
                    currentWave.add(new LightNode(nr, nc, neighborCurrentBlockLight, LightType.BLOCK, false));
                    chunksToMarkDirty.add(new ChunkCoordinate(Map.worldToChunkCoord(nc), Map.worldToChunkCoord(nr)));
                } else {
                    sourcesToRePropagate.add(new LightNode(nr, nc, neighborCurrentBlockLight, LightType.BLOCK, false));
                }
            }
        }
        for(ChunkCoordinate coord : chunksToMarkDirty) markChunkAndNeighborsDirtyForRenderer(coord.chunkX, coord.chunkY);
    }

    private void propagateLightBFS(Queue<LightNode> queue, LightType type, Set<ChunkCoordinate> neighborsPotentiallyAffectedOutput) {
        Set<ChunkCoordinate> chunksAffectedThisPropagation = new HashSet<>();

        while (!queue.isEmpty()) {
            LightNode current = queue.poll();
            chunksAffectedThisPropagation.add(new ChunkCoordinate(Map.worldToChunkCoord(current.c), Map.worldToChunkCoord(current.r)));

            if (current.lightLevel <= LIGHT_PROPAGATION_COST && current.lightLevel > 0) {
            } else if (current.lightLevel <= 0) {
                continue;
            }

            int[] dRow = {-1, 1, 0, 0};
            int[] dCol = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int nr = current.r + dRow[i];
                int nc = current.c + dCol[i];

                Tile neighborTile = mapAPI.getTile(nr, nc);
                boolean neighborIsAir = (neighborTile == null);

                LightType effectiveTypeForOpacity = (type == LightType.SKY) ? LightType.SKY_BFS_HORIZONTAL : LightType.BLOCK;
                int opacityCost = getOpacityCost(neighborTile, effectiveTypeForOpacity);

                byte lightReachingNeighbor = (byte) Math.max(0, current.lightLevel - (LIGHT_PROPAGATION_COST + opacityCost));
                if (lightReachingNeighbor <= 0) continue;

                byte existingNeighborLight = 0;
                if (!neighborIsAir) {
                    existingNeighborLight = (type == LightType.BLOCK) ? neighborTile.getBlockLightLevel() : neighborTile.getSkyLightLevel();
                }

                if (lightReachingNeighbor > existingNeighborLight) {
                    if (!neighborIsAir) {
                        if (type == LightType.BLOCK) neighborTile.setBlockLightLevel(lightReachingNeighbor);
                        else neighborTile.setSkyLightLevel(lightReachingNeighbor);
                    }
                    queue.add(new LightNode(nr, nc, lightReachingNeighbor, type, neighborIsAir));

                    ChunkCoordinate currentChunkCoordinate = new ChunkCoordinate(Map.worldToChunkCoord(current.c), Map.worldToChunkCoord(current.r));
                    ChunkCoordinate neighborChunkCoordinate = new ChunkCoordinate(Map.worldToChunkCoord(nc), Map.worldToChunkCoord(nr));
                    chunksAffectedThisPropagation.add(neighborChunkCoordinate);

                    if (!currentChunkCoordinate.equals(neighborChunkCoordinate)) {
                        neighborsPotentiallyAffectedOutput.add(neighborChunkCoordinate);
                    }
                }
            }
        }
        for(ChunkCoordinate coord : chunksAffectedThisPropagation) markChunkAndNeighborsDirtyForRenderer(coord.chunkX, coord.chunkY);
    }

    private enum LightType { SKY, BLOCK, SKY_DIRECT_DOWN, SKY_BFS_HORIZONTAL }

    private int getOpacityCost(Tile tileBeingEntered, LightType type) {
        if (tileBeingEntered == null) return 0;

        switch (type) {
            case SKY_DIRECT_DOWN:
                if (tileBeingEntered.getType() == Tile.TileType.WATER) return 1;
                return tileBeingEntered.isTransparentToLight() ? 1 : (MAX_LIGHT_LEVEL + 1);

            case SKY_BFS_HORIZONTAL:
                if (tileBeingEntered.getType() == Tile.TileType.WATER) return 2;
                return MAX_LIGHT_LEVEL + 1;

            case BLOCK:
                if (tileBeingEntered.getType() == Tile.TileType.WATER) return 3;
                if (tileBeingEntered.getType() == Tile.TileType.GRASS ||
                        tileBeingEntered.getType() == Tile.TileType.SAND ||
                        tileBeingEntered.getType() == Tile.TileType.ROCK ||
                        tileBeingEntered.getType() == Tile.TileType.SNOW) {
                    return 1;
                }
                return tileBeingEntered.isTransparentToLight() ? 1 : (MAX_LIGHT_LEVEL + 1);

            default: return MAX_LIGHT_LEVEL + 1;
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
        final boolean isAir;

        LightNode(int r, int c, byte lightLevel, LightType type, boolean isAir) {
            this.r = r; this.c = c; this.lightLevel = lightLevel; this.type = type; this.isAir = isAir;
        }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; LightNode n = (LightNode) o; return r == n.r && c == n.c && type == n.type && lightLevel == n.lightLevel && isAir == n.isAir; }
        @Override public int hashCode() { return Objects.hash(r, c, type, lightLevel, isAir); }
        @Override public String toString() { return "LightNode ("+r+","+c+") L:"+lightLevel+" T:"+type+ (isAir ? " (Air)" : ""); }
    }
}
