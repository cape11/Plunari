package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import static org.isogame.constants.Constants.*;

public class LightManager {

    private final Map map;
    private final Set<ChunkCoordinate> dirtyChunks;

    private final Queue<LightNode> skyLightPropagationQueue;
    private final Queue<LightNode> skyLightRemovalQueue;
    private final Queue<LightNode> blockLightPropagationQueue;
    private final Queue<LightNode> blockLightRemovalQueue;

    public static final int BATCH_LIGHT_UPDATE_BUDGET = 10000; // Larger budget for specific batch updates
    private static final int MAX_LIGHT_UPDATES_PER_QUEUE_PER_FRAME = 4000; // Default budget

    private static final int MAX_ELEVATION_STEP_FOR_SKYLIGHT = 1;
    private static final int MAX_ELEVATION_STEP_FOR_BLOCKLIGHT = 2;

    // This represents the actual current value the world should eventually reflect.
    // Game queries this for initializing new chunks or for refresh operations.
    private byte currentGlobalSkyLightTarget = SKY_LIGHT_DAY;


    public static class ChunkCoordinate {
        public final int chunkX, chunkY;
        public ChunkCoordinate(int chunkX, int chunkY) { this.chunkX = chunkX; this.chunkY = chunkY; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; ChunkCoordinate cc = (ChunkCoordinate) o; return chunkX == cc.chunkX && chunkY == cc.chunkY; }
        @Override public int hashCode() { return 31 * chunkX + chunkY; }
        @Override public String toString() { return "ChunkCoord(" + chunkX + "," + chunkY + ")"; }
    }

    public LightManager(Map map) {
        this.map = map;
        this.dirtyChunks = new HashSet<>();
        this.skyLightPropagationQueue = new LinkedList<>();
        this.skyLightRemovalQueue = new LinkedList<>();
        this.blockLightPropagationQueue = new LinkedList<>();
        this.blockLightRemovalQueue = new LinkedList<>();
    }

    public Set<ChunkCoordinate> getDirtyChunksAndClear() {
        Set<ChunkCoordinate> currentDirty = new HashSet<>(dirtyChunks);
        dirtyChunks.clear();
        return currentDirty;
    }

    public void markChunkDirty(int r_map_coord, int c_map_coord) {
        if (map.isValid(r_map_coord, c_map_coord)) {
            dirtyChunks.add(new ChunkCoordinate(c_map_coord / CHUNK_SIZE_TILES, r_map_coord / CHUNK_SIZE_TILES));
        }
    }

    public void setCurrentGlobalSkyLightTarget(byte value) {
        this.currentGlobalSkyLightTarget = value;
    }

    public byte getCurrentGlobalSkyLightTarget() {
        return this.currentGlobalSkyLightTarget;
    }

    /**
     * Refreshes sky light for a single chunk based on a target global sky light value.
     * This is intended to be called incrementally by the Game loop.
     * @param chunkCoord The chunk to refresh.
     * @param targetGlobalSkyValue The global sky light value this chunk should adapt to.
     */
    public void refreshSkyLightForSingleChunk(ChunkCoordinate chunkCoord, byte targetGlobalSkyValue) {
        // System.out.println("LightManager: Refreshing sky light for chunk " + chunkCoord + " to target " + targetGlobalSkyValue);
        int startR = chunkCoord.chunkY * CHUNK_SIZE_TILES;
        int startC = chunkCoord.chunkX * CHUNK_SIZE_TILES;
        int endR = Math.min(startR + CHUNK_SIZE_TILES, map.getHeight());
        int endC = Math.min(startC + CHUNK_SIZE_TILES, map.getWidth());

        // Pass 1: Adjust existing skylight, queue removals if light was brighter
        for (int r = startR; r < endR; r++) {
            for (int c = startC; c < endC; c++) {
                Tile tile = map.getTile(r, c);
                if (tile != null) {
                    byte oldSkyLight = tile.getSkyLightLevel();
                    if (oldSkyLight > targetGlobalSkyValue) { // Was brighter than new target
                        tile.setSkyLightLevel((byte) 0); // Tentatively set to 0, propagation will fix if exposed to target
                        skyLightRemovalQueue.add(new LightNode(r, c, oldSkyLight));
                        markChunkDirty(r, c);
                    } else if (oldSkyLight < targetGlobalSkyValue) { // Was darker
                        tile.setSkyLightLevel((byte) 0); // Also set to 0, will be re-evaluated by seeding pass
                        // No removal needed, but might need propagation if it becomes exposed to target
                    }
                    // If oldSkyLight == targetGlobalSkyValue, it might still need propagation if a neighbor changed.
                }
            }
        }

        // Pass 2: Seed new skylight on exposed surface tiles
        for (int r = startR; r < endR; r++) {
            for (int c = startC; c < endC; c++) {
                Tile currentSurfaceTile = map.getTile(r, c);
                if (currentSurfaceTile == null || currentSurfaceTile.getType() == Tile.TileType.WATER) {
                    if(currentSurfaceTile != null) currentSurfaceTile.setSkyLightLevel((byte)0);
                    continue;
                }
                boolean isExposed = isSurfaceTileExposedToSky(r, c, currentSurfaceTile.getElevation());
                if (isExposed) {
                    // If exposed and its current light (might be 0 from Pass 1) is less than the target
                    if (currentSurfaceTile.getSkyLightLevel() < targetGlobalSkyValue) {
                        currentSurfaceTile.setSkyLightLevel(targetGlobalSkyValue);
                        skyLightPropagationQueue.add(new LightNode(r, c, targetGlobalSkyValue));
                        markChunkDirty(r, c);
                    }
                }
                // If not exposed, its light level should be 0 unless propagated from neighbors.
                // If Pass 1 set it to 0, it stays 0 until propagation.
            }
        }
    }


    public void initializeSkylightForChunk(ChunkCoordinate chunkCoord) {
        // This method is for when a chunk is first loaded. It uses the currentGlobalSkyLightTarget.
        // System.out.println("LightManager: Initializing skylight for new chunk " + chunkCoord.toString() + " with global target " + this.currentGlobalSkyLightTarget);
        int startR = chunkCoord.chunkY * CHUNK_SIZE_TILES;
        int startC = chunkCoord.chunkX * CHUNK_SIZE_TILES;
        int endR = Math.min(startR + CHUNK_SIZE_TILES, map.getHeight());
        int endC = Math.min(startC + CHUNK_SIZE_TILES, map.getWidth());

        for (int r = startR; r < endR; r++) {
            for (int c = startC; c < endC; c++) {
                Tile tile = map.getTile(r, c);
                if (tile == null) continue;
                if (tile.getType() == Tile.TileType.WATER) {
                    tile.setSkyLightLevel((byte) 0);
                    continue;
                }
                boolean isExposed = isSurfaceTileExposedToSky(r, c, tile.getElevation());
                if (isExposed) {
                    if (tile.getSkyLightLevel() < this.currentGlobalSkyLightTarget) {
                        tile.setSkyLightLevel(this.currentGlobalSkyLightTarget);
                        skyLightPropagationQueue.add(new LightNode(r, c, this.currentGlobalSkyLightTarget));
                        markChunkDirty(r, c);
                    }
                } else {
                    if (tile.getSkyLightLevel() > 0) {
                        skyLightRemovalQueue.add(new LightNode(r,c, tile.getSkyLightLevel()));
                        tile.setSkyLightLevel((byte) 0);
                        markChunkDirty(r,c);
                    }
                }
            }
        }
    }

    public boolean isSurfaceTileExposedToSky(int r, int c, int elevation) {
        int[] dRows = {-1, 1, 0, 0, -1, -1, 1, 1};
        int[] dCols = {0, 0, -1, 1, -1, 1, -1, 1};
        int occlusionThreshold = 1;
        for (int i = 0; i < 8; i++) {
            int nr = r + dRows[i]; int nc = c + dCols[i];
            if (map.isValid(nr, nc)) {
                Tile neighbor = map.getTile(nr, nc);
                if (neighbor != null && neighbor.isSolidOpaqueBlock()) {
                    if (neighbor.getElevation() > elevation + occlusionThreshold) return false;
                }
            }
        }
        return true;
    }

    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = map.getTile(r, c);
        if (tile != null && tile.isSolidOpaqueBlock() && tile.getType() != Tile.TileType.WATER) {
            tile.setHasTorch(true);
            if (lightLevel > tile.getBlockLightLevel()) {
                tile.setBlockLightLevel(lightLevel);
                blockLightPropagationQueue.add(new LightNode(r, c, lightLevel));
                markChunkDirty(r,c);
            }
        }
    }

    public void removeLightSource(int r, int c) {
        Tile tile = map.getTile(r, c);
        if (tile != null && tile.hasTorch()) {
            byte oldLight = tile.getBlockLightLevel();
            tile.setHasTorch(false);
            if (oldLight > 0) {
                blockLightRemovalQueue.add(new LightNode(r, c, oldLight));
                markChunkDirty(r,c);
            }
        }
    }

    public void processLightQueuesIncrementally(int budget) {
        processQueue(blockLightRemovalQueue, LightProcessingStep.BLOCK_REMOVAL, budget);
        processQueue(skyLightRemovalQueue, LightProcessingStep.SKY_REMOVAL, budget);
        processQueue(blockLightPropagationQueue, LightProcessingStep.BLOCK_PROPAGATION, budget);
        processQueue(skyLightPropagationQueue, LightProcessingStep.SKY_PROPAGATION, budget);
    }

    public void processLightQueuesIncrementally() {
        processLightQueuesIncrementally(MAX_LIGHT_UPDATES_PER_QUEUE_PER_FRAME);
    }

    private enum LightProcessingStep { SKY_PROPAGATION, BLOCK_PROPAGATION, SKY_REMOVAL, BLOCK_REMOVAL }

    private void processQueue(Queue<LightNode> queue, LightProcessingStep stepType, int budget) {
        int processedCount = 0;
        while (!queue.isEmpty() && processedCount < budget) {
            LightNode current = queue.poll();
            if (current == null) continue;
            Tile tile = map.getTile(current.r, current.c);
            if (tile == null) continue;

            switch (stepType) {
                case SKY_PROPAGATION:
                    if (tile.getSkyLightLevel() == current.lightLevel && current.lightLevel > 0) {
                        processSingleSkyPropagationStep_Heightmap(current);
                    }
                    break;
                case BLOCK_PROPAGATION:
                    if (tile.getBlockLightLevel() == current.lightLevel && current.lightLevel > 0) {
                        processSingleBlockPropagationStep_Heightmap(current);
                    }
                    break;
                case SKY_REMOVAL: processSingleSkyRemovalStep_Heightmap(current); break;
                case BLOCK_REMOVAL: processSingleBlockRemovalStep_Heightmap(current); break;
            }
            processedCount++;
        }
    }

    public int getHorizontalPassOpacity(Tile tileBeingEntered) {
        if (tileBeingEntered == null) return MAX_LIGHT_LEVEL + 1;
        if (tileBeingEntered.getType() == Tile.TileType.WATER) return 3;
        if (tileBeingEntered.isSolidOpaqueBlock()) return 1;
        if (tileBeingEntered.isTransparentToSkyLight()) return 0;
        return 1;
    }

    private void processSingleSkyPropagationStep_Heightmap(LightNode currentQueuedNode) {
        Tile sourceSurfaceTile = map.getTile(currentQueuedNode.r, currentQueuedNode.c);
        if (sourceSurfaceTile == null || sourceSurfaceTile.getSkyLightLevel() == 0) return;
        byte propagatedLightStrength = sourceSurfaceTile.getSkyLightLevel();

        int[] dr = {-1, 1, 0, 0}; int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = currentQueuedNode.r + dr[i]; int nc = currentQueuedNode.c + dc[i];
            if (!map.isValid(nr, nc)) continue;
            Tile neighborSurfaceTile = map.getTile(nr, nc);
            if (neighborSurfaceTile == null || neighborSurfaceTile.getType() == Tile.TileType.WATER) continue;

            int elevationDifference = neighborSurfaceTile.getElevation() - sourceSurfaceTile.getElevation();
            if (elevationDifference > MAX_ELEVATION_STEP_FOR_SKYLIGHT && elevationDifference > 0) continue; // Can't climb too high

            int stepCost = LIGHT_PROPAGATION_COST;
            if (elevationDifference < 0) { // Propagating downwards
                stepCost = 0;
            }

            int opacityOfNeighborSurface = getHorizontalPassOpacity(neighborSurfaceTile);
            byte lightReachingNeighbor = (byte) Math.max(0, propagatedLightStrength - stepCost - opacityOfNeighborSurface);

            if (lightReachingNeighbor > neighborSurfaceTile.getSkyLightLevel()) {
                neighborSurfaceTile.setSkyLightLevel(lightReachingNeighbor);
                skyLightPropagationQueue.add(new LightNode(nr, nc, lightReachingNeighbor));
                markChunkDirty(nr, nc);
            }
        }
    }

    private void processSingleBlockPropagationStep_Heightmap(LightNode currentQueuedNode) {
        Tile sourceSurfaceTile = map.getTile(currentQueuedNode.r, currentQueuedNode.c);
        if (sourceSurfaceTile == null || sourceSurfaceTile.getBlockLightLevel() == 0) return;
        byte propagatedLightStrength = sourceSurfaceTile.getBlockLightLevel();

        int[] dr = {-1, 1, 0, 0}; int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = currentQueuedNode.r + dr[i]; int nc = currentQueuedNode.c + dc[i];
            if (!map.isValid(nr, nc)) continue;
            Tile neighborSurfaceTile = map.getTile(nr, nc);
            if (neighborSurfaceTile == null || neighborSurfaceTile.getType() == Tile.TileType.AIR) continue;

            int elevationDifference = Math.abs(neighborSurfaceTile.getElevation() - sourceSurfaceTile.getElevation());
            if (elevationDifference > MAX_ELEVATION_STEP_FOR_BLOCKLIGHT) continue;

            int opacityOfNeighborSurface = getHorizontalPassOpacity(neighborSurfaceTile);
            byte lightReachingNeighbor = (byte) Math.max(0, propagatedLightStrength - LIGHT_PROPAGATION_COST - opacityOfNeighborSurface);

            if (lightReachingNeighbor > neighborSurfaceTile.getBlockLightLevel()) {
                neighborSurfaceTile.setBlockLightLevel(lightReachingNeighbor);
                blockLightPropagationQueue.add(new LightNode(nr, nc, lightReachingNeighbor));
                markChunkDirty(nr, nc);
            }
        }
    }

    private void processSingleSkyRemovalStep_Heightmap(LightNode nodeBeingRemoved) {
        int r = nodeBeingRemoved.r; int c = nodeBeingRemoved.c;
        byte originalLightLevelOfSourceThatIsBeingRemoved = nodeBeingRemoved.lightLevel;
        Tile sourceSurfaceTile = map.getTile(r,c);
        if (sourceSurfaceTile == null) return;

        int[] dr = {-1, 1, 0, 0}; int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = r + dr[i]; int nc = c + dc[i];
            if (!map.isValid(nr, nc)) continue;
            Tile neighborSurfaceTile = map.getTile(nr, nc);
            if (neighborSurfaceTile == null || neighborSurfaceTile.getType() == Tile.TileType.WATER) continue;

            byte currentNeighborSkyLight = neighborSurfaceTile.getSkyLightLevel();
            if (currentNeighborSkyLight == 0) continue;

            int elevationDifference = Math.abs(neighborSurfaceTile.getElevation() - sourceSurfaceTile.getElevation());
            // Allow removal propagation even up slightly steeper slopes than propagation, if necessary
            if (elevationDifference > MAX_ELEVATION_STEP_FOR_SKYLIGHT + 1) continue;

            int stepCost = LIGHT_PROPAGATION_COST;
            if (neighborSurfaceTile.getElevation() < sourceSurfaceTile.getElevation() ) {
                stepCost = 0;
            }
            int opacityOfNeighborSurface = getHorizontalPassOpacity(neighborSurfaceTile);
            byte lightThatCameFromRemovedSourcePath = (byte) Math.max(0, originalLightLevelOfSourceThatIsBeingRemoved - stepCost - opacityOfNeighborSurface);

            if (currentNeighborSkyLight <= lightThatCameFromRemovedSourcePath) {
                neighborSurfaceTile.setSkyLightLevel((byte) 0);
                skyLightRemovalQueue.add(new LightNode(nr, nc, currentNeighborSkyLight));
                markChunkDirty(nr, nc);
                // If this neighbor is now dark but should be lit by current global sky light
                if (isSurfaceTileExposedToSky(nr, nc, neighborSurfaceTile.getElevation())) {
                    if (this.currentGlobalSkyLightTarget > 0 && neighborSurfaceTile.getSkyLightLevel() < this.currentGlobalSkyLightTarget) {
                        neighborSurfaceTile.setSkyLightLevel(this.currentGlobalSkyLightTarget);
                        skyLightPropagationQueue.add(new LightNode(nr, nc, this.currentGlobalSkyLightTarget));
                    }
                }
            } else {
                skyLightPropagationQueue.add(new LightNode(nr, nc, currentNeighborSkyLight));
            }
        }
    }

    private void processSingleBlockRemovalStep_Heightmap(LightNode nodeBeingRemoved) {
        int r = nodeBeingRemoved.r; int c = nodeBeingRemoved.c;
        byte originalLightLevelOfSource = nodeBeingRemoved.lightLevel;
        Tile sourceTile = map.getTile(r,c);
        if (sourceTile == null) return;

        if (sourceTile.hasTorch() && sourceTile.getBlockLightLevel() >= originalLightLevelOfSource) {
            blockLightPropagationQueue.add(new LightNode(r, c, sourceTile.getBlockLightLevel()));
            return;
        }
        if (sourceTile.getBlockLightLevel() <= originalLightLevelOfSource) {
            sourceTile.setBlockLightLevel((byte)0);
        }
        markChunkDirty(r,c);

        int[] dr = {-1, 1, 0, 0}; int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = r + dr[i]; int nc = c + dc[i];
            if (!map.isValid(nr, nc)) continue;
            Tile neighbor = map.getTile(nr, nc);
            if (neighbor == null || neighbor.getType() == Tile.TileType.AIR) continue;

            byte currentNeighborBlockLight = neighbor.getBlockLightLevel();
            if (currentNeighborBlockLight == 0) continue;

            int elevationDifference = Math.abs(neighbor.getElevation() - sourceTile.getElevation());
            if (elevationDifference > MAX_ELEVATION_STEP_FOR_BLOCKLIGHT) continue;
            int opacityCostOfNeighbor = getHorizontalPassOpacity(neighbor);
            byte lightThatCameFromRemovedSourcePath = (byte) Math.max(0, originalLightLevelOfSource - LIGHT_PROPAGATION_COST - opacityCostOfNeighbor);

            if (currentNeighborBlockLight <= lightThatCameFromRemovedSourcePath) {
                neighbor.setBlockLightLevel((byte) 0);
                blockLightRemovalQueue.add(new LightNode(nr, nc, currentNeighborBlockLight));
                markChunkDirty(nr, nc);
            } else {
                blockLightPropagationQueue.add(new LightNode(nr, nc, currentNeighborBlockLight));
            }
        }
    }

    public boolean isAnyLightQueueNotEmpty() {
        return !blockLightPropagationQueue.isEmpty() || !blockLightRemovalQueue.isEmpty() ||
                !skyLightPropagationQueue.isEmpty() || !skyLightRemovalQueue.isEmpty();
    }

    static class LightNode {
        final int r, c;
        final byte lightLevel;
        LightNode(int r, int c, byte lightLevel) { this.r = r; this.c = c; this.lightLevel = lightLevel; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; LightNode ln = (LightNode) o; return r == ln.r && c == ln.c && lightLevel == ln.lightLevel; }
        @Override public int hashCode() { int result = r; result = 31 * result + c; result = 31 * result + (int) lightLevel; return result; }
    }

    public int getSkyLightPropagationQueueSize() { return skyLightPropagationQueue.size(); }
    public int getSkyLightRemovalQueueSize() { return skyLightRemovalQueue.size(); }
    public int getBlockLightPropagationQueueSize() { return blockLightPropagationQueue.size(); }
    public int getBlockLightRemovalQueueSize() { return blockLightRemovalQueue.size(); }
    public Queue<LightNode> getSkyLightPropagationQueue() { return skyLightPropagationQueue; } // Keep these if Map uses them directly
    public Queue<LightNode> getBlockLightPropagationQueue() { return blockLightPropagationQueue; }
}