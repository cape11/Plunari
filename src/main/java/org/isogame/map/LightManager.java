package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
// List import is not strictly needed by LightManager itself with this approach,
// but Game.java might pass List<ChunkCoordinate>
import static org.isogame.constants.Constants.*;

public class LightManager {

    private final Map map;
    private final Set<ChunkCoordinate> dirtyChunks; // Chunks needing geometry rebuild

    private final Queue<LightNode> skyLightPropagationQueue;
    private final Queue<LightNode> skyLightRemovalQueue;
    private final Queue<LightNode> blockLightPropagationQueue;
    private final Queue<LightNode> blockLightRemovalQueue;

    public static final int BATCH_LIGHT_UPDATE_BUDGET = 10000; // Max total light updates across all queues per frame
    private static final int MAX_LIGHT_UPDATES_PER_QUEUE_PER_FRAME = 4000; // Max updates for a single queue type

    private static final int MAX_ELEVATION_STEP_FOR_SKYLIGHT_PROPAGATION = 1;
    private static final int MAX_ELEVATION_STEP_FOR_BLOCKLIGHT_PROPAGATION = 2;

    private byte currentGlobalSkyLightTarget = SKY_LIGHT_DAY;


    public static class ChunkCoordinate {
        public final int chunkX, chunkY;
        public ChunkCoordinate(int chunkX, int chunkY) { this.chunkX = chunkX; this.chunkY = chunkY; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; ChunkCoordinate cc = (ChunkCoordinate) o; return chunkX == cc.chunkX && chunkY == cc.chunkY; }
        @Override public int hashCode() { return 31 * chunkX + chunkY; }
        @Override public String toString() { return "ChunkCoord(" + chunkX + "," + chunkY + ")"; }
    }

    public static class LightNode {
        public final int r, c; // Global tile coordinates
        public final byte lightLevel;
        public LightNode(int r, int c, byte lightLevel) { this.r = r; this.c = c; this.lightLevel = lightLevel; }
        @Override public boolean equals(Object o) { if (this == o) return true; if (o == null || getClass() != o.getClass()) return false; LightNode ln = (LightNode) o; return r == ln.r && c == ln.c && lightLevel == ln.lightLevel; }
        @Override public int hashCode() { int result = r; result = 31 * result + c; result = 31 * result + (int) lightLevel; return result; }
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
        // No need for map.isValid() as floorDiv will work with any integer.
        // The responsibility of whether these coordinates are "sensible" is higher up.
        dirtyChunks.add(new ChunkCoordinate(Math.floorDiv(c_map_coord, CHUNK_SIZE_TILES), Math.floorDiv(r_map_coord, CHUNK_SIZE_TILES)));
    }

    public void setCurrentGlobalSkyLightTarget(byte value) {
        this.currentGlobalSkyLightTarget = value;
    }

    public byte getCurrentGlobalSkyLightTarget() {
        return this.currentGlobalSkyLightTarget;
    }

    public void refreshSkyLightForSingleChunk(ChunkCoordinate chunkCoord, byte targetGlobalSkyValue) {
        int startR_global = chunkCoord.chunkY * CHUNK_SIZE_TILES;
        int startC_global = chunkCoord.chunkX * CHUNK_SIZE_TILES;
        // Iterate CHUNK_SIZE_TILES times, as this method operates on one chunk.
        // endR and endC will be startR/C + CHUNK_SIZE_TILES.
        // map.getTile() will handle generating the tiles if they are new.

        for (int r_offset = 0; r_offset < CHUNK_SIZE_TILES; r_offset++) {
            for (int c_offset = 0; c_offset < CHUNK_SIZE_TILES; c_offset++) {
                int globalR = startR_global + r_offset;
                int globalC = startC_global + c_offset;
                Tile tile = map.getTile(globalR, globalC); // This ensures tile data is available
                if (tile == null) continue; // Should not happen if map.getTile is robust

                byte oldSkyLight = tile.getSkyLightLevel();
                byte newSkyLightValue;
                boolean changed = false;

                if (tile.getType() == Tile.TileType.AIR) {
                    newSkyLightValue = targetGlobalSkyValue;
                } else if (tile.getType() == Tile.TileType.WATER) {
                    newSkyLightValue = (byte)0; // Water surface doesn't emit sky light itself
                } else {
                    // Assuming all non-AIR, non-WATER surface tiles get direct sky light
                    // unless a more complex occlusion system (like roofs) is in place.
                    newSkyLightValue = targetGlobalSkyValue;
                }

                if (oldSkyLight != newSkyLightValue) {
                    tile.setSkyLightLevel(newSkyLightValue);
                    changed = true;
                    if (newSkyLightValue < oldSkyLight) {
                        skyLightRemovalQueue.add(new LightNode(globalR, globalC, oldSkyLight));
                    }
                }
                if (changed) {
                    markChunkDirty(globalR, globalC);
                }
            }
        }
    }

    public void initializeSkylightForChunk(ChunkCoordinate chunkCoord) {
        refreshSkyLightForSingleChunk(chunkCoord, this.currentGlobalSkyLightTarget);
    }

    public boolean isSurfaceTileExposedToSky(int r, int c, int elevation) {
        Tile tile = map.getTile(r,c); // Ensures tile data is available
        if(tile == null || tile.getType() == Tile.TileType.AIR || tile.getType() == Tile.TileType.WATER) {
            return true;
        }
        // For a simple heightmap, any solid surface tile is "exposed".
        return true;
    }

    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = map.getTile(r, c); // Ensures tile data is available
        if (tile != null && tile.isSolidOpaqueBlock() && tile.getType() != Tile.TileType.WATER) {
            tile.setHasTorch(true); // Set torch status on the tile
            if (lightLevel > tile.getBlockLightLevel()) {
                tile.setBlockLightLevel(lightLevel);
                blockLightPropagationQueue.add(new LightNode(r, c, lightLevel));
                markChunkDirty(r,c);
            } else if (!blockLightPropagationQueue.contains(new LightNode(r,c,tile.getBlockLightLevel())) && tile.getBlockLightLevel() > 0){
                // If the tile already has a block light (maybe from a previous source that was removed but light lingered)
                // and we are adding a torch that's not brighter, ensure existing light still propagates.
                blockLightPropagationQueue.add(new LightNode(r, c, tile.getBlockLightLevel()));
            }
        }
    }

    public void removeLightSource(int r, int c) {
        Tile tile = map.getTile(r, c); // Ensures tile data is available
        if (tile != null && tile.hasTorch()) {
            byte oldLight = tile.getBlockLightLevel();
            tile.setHasTorch(false); // Update torch status on the tile
            // The actual block light level on the tile will be reduced by the removal queue processing.
            // Queue removal of its current light level.
            if (oldLight > 0) { // If it was actually emitting light
                blockLightRemovalQueue.add(new LightNode(r, c, oldLight));
                // Don't setBlockLightLevel(0) here directly, let removal queue handle it
                // to correctly update neighbors.
                markChunkDirty(r,c);
            }
        }
    }

    public void processLightQueuesIncrementally(int budget) {
        processQueue(skyLightRemovalQueue, LightProcessingStep.SKY_REMOVAL, budget);
        processQueue(blockLightRemovalQueue, LightProcessingStep.BLOCK_REMOVAL, budget);
        processQueue(skyLightPropagationQueue, LightProcessingStep.SKY_PROPAGATION, budget);
        processQueue(blockLightPropagationQueue, LightProcessingStep.BLOCK_PROPAGATION, budget);
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
            Tile tile = map.getTile(current.r, current.c); // Ensures tile data is available
            if (tile == null) continue;

            switch (stepType) {
                case SKY_PROPAGATION:
                    if (tile.getSkyLightLevel() >= current.lightLevel && current.lightLevel > 0) {
                        processSingleSkyPropagationStep_Heightmap(new LightNode(current.r, current.c, tile.getSkyLightLevel()));
                    }
                    break;
                case BLOCK_PROPAGATION:
                    if (tile.getBlockLightLevel() >= current.lightLevel && current.lightLevel > 0) {
                        processSingleBlockPropagationStep_Heightmap(new LightNode(current.r, current.c, tile.getBlockLightLevel()));
                    }
                    break;
                case SKY_REMOVAL: processSingleSkyRemovalStep_Heightmap(current); break;
                case BLOCK_REMOVAL: processSingleBlockRemovalStep_Heightmap(current); break;
            }
            processedCount++;
        }
    }

    public int getHorizontalPassOpacity(Tile tileBeingEntered) {
        if (tileBeingEntered == null) return MAX_LIGHT_LEVEL + 1; // Effectively blocks all light
        if (tileBeingEntered.isTransparentToSkyLight()) return 0;
        if (tileBeingEntered.getType() == Tile.TileType.WATER) return 3; // Water offers some resistance
        if (tileBeingEntered.isSolidOpaqueBlock()) return 1; // Standard cost for opaque blocks
        return 1; // Default
    }

    // In LightManager.java, inside the processSingleSkyPropagationStep_Heightmap method

    private void processSingleSkyPropagationStep_Heightmap(LightNode currentQueuedNode) {
        Tile sourceSurfaceTile = map.getTile(currentQueuedNode.r, currentQueuedNode.c);
        if (sourceSurfaceTile == null || sourceSurfaceTile.getSkyLightLevel() == 0) return;
        byte propagatedLightStrength = sourceSurfaceTile.getSkyLightLevel();

        int[] dr = {-1, 1, 0, 0}; int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = currentQueuedNode.r + dr[i]; int nc = currentQueuedNode.c + dc[i];
            Tile neighborSurfaceTile = map.getTile(nr, nc);
            if (neighborSurfaceTile == null || neighborSurfaceTile.getType() == Tile.TileType.WATER) continue;

            int elevationDifference = neighborSurfaceTile.getElevation() - sourceSurfaceTile.getElevation();

            // --- THIS IS THE CORRECTED LOGIC ---
            // Allow light to spread to neighbors of same-height or slightly higher/lower.
            if (elevationDifference <= MAX_ELEVATION_STEP_FOR_SKYLIGHT_PROPAGATION) {

                // Light spreading horizontally or upwards costs 1 light level.
                // Spreading downwards is free (cost = 0).
                int stepCost = (elevationDifference < 0) ? 0 : 1;

                int opacityOfNeighborSurface = getHorizontalPassOpacity(neighborSurfaceTile);
                byte lightReachingNeighbor = (byte) Math.max(0, propagatedLightStrength - stepCost - opacityOfNeighborSurface);

                if (lightReachingNeighbor > neighborSurfaceTile.getSkyLightLevel()) {
                    neighborSurfaceTile.setSkyLightLevel(lightReachingNeighbor);
                    if (lightReachingNeighbor > 1) {
                        skyLightPropagationQueue.add(new LightNode(nr, nc, lightReachingNeighbor));
                    }
                    markChunkDirty(nr, nc);
                }
            }
            // --- END OF CORRECTION ---
        }
    }


    // In LightManager.java, add this new method anywhere inside the class.

    /**
     * Processes all light queues until they are completely empty.
     * This is an intensive operation and should be used sparingly, right after
     * a world modification, to ensure lighting is visually correct in the same frame.
     */
    public void processAllQueuesToCompletion() {
        int safetyCounter = 0;
        // The safety counter prevents an infinite loop in case of an undiscovered bug in the lighting logic
        while (isAnyLightQueueNotEmpty() && safetyCounter < 30) {
            // Use a large budget to clear queues faster in a single pass
            processLightQueuesIncrementally(BATCH_LIGHT_UPDATE_BUDGET * 5);
            safetyCounter++;
        }
        if (safetyCounter >= 30) {
            System.err.println("LightManager.processAllQueuesToCompletion: Hit safety limit. Possible infinite light loop detected.");
        }
    }

    private void processSingleBlockPropagationStep_Heightmap(LightNode currentQueuedNode) {
        Tile sourceSurfaceTile = map.getTile(currentQueuedNode.r, currentQueuedNode.c);
        if (sourceSurfaceTile == null || sourceSurfaceTile.getBlockLightLevel() == 0) return;
        byte propagatedLightStrength = sourceSurfaceTile.getBlockLightLevel();

        int[] dr = {-1, 1, 0, 0}; int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = currentQueuedNode.r + dr[i]; int nc = currentQueuedNode.c + dc[i];
            // No map.isValid check needed
            Tile neighborSurfaceTile = map.getTile(nr, nc);
            if (neighborSurfaceTile == null || neighborSurfaceTile.getType() == Tile.TileType.AIR) continue;

            int elevationDifference = Math.abs(neighborSurfaceTile.getElevation() - sourceSurfaceTile.getElevation());
            if (elevationDifference > MAX_ELEVATION_STEP_FOR_BLOCKLIGHT_PROPAGATION) continue;

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
            // No map.isValid needed
            Tile neighborSurfaceTile = map.getTile(nr, nc);
            if (neighborSurfaceTile == null || neighborSurfaceTile.getType() == Tile.TileType.WATER) continue;

            byte currentNeighborSkyLight = neighborSurfaceTile.getSkyLightLevel();
            if (currentNeighborSkyLight == 0) continue;

            int elevationDifference = neighborSurfaceTile.getElevation() - sourceSurfaceTile.getElevation();
            int stepCost = LIGHT_PROPAGATION_COST;
            if (elevationDifference < 0) stepCost = 0;

            if (elevationDifference > 0 && elevationDifference > MAX_ELEVATION_STEP_FOR_SKYLIGHT_PROPAGATION) continue;

            int opacityOfNeighborSurface = getHorizontalPassOpacity(neighborSurfaceTile);
            byte lightThatCouldHaveComeFromRemovedSourcePath = (byte) Math.max(0, originalLightLevelOfSourceThatIsBeingRemoved - stepCost - opacityOfNeighborSurface);

            if (currentNeighborSkyLight <= lightThatCouldHaveComeFromRemovedSourcePath) {
                byte newBaseLight = SKY_LIGHT_NIGHT_MINIMUM;
                if (isSurfaceTileExposedToSky(nr, nc, neighborSurfaceTile.getElevation())) {
                    newBaseLight = this.currentGlobalSkyLightTarget;
                }

                if (currentNeighborSkyLight > newBaseLight) {
                    neighborSurfaceTile.setSkyLightLevel(newBaseLight);
                    skyLightRemovalQueue.add(new LightNode(nr, nc, currentNeighborSkyLight));
                    markChunkDirty(nr, nc);
                    if (newBaseLight > 0 && newBaseLight == this.currentGlobalSkyLightTarget) {
                        skyLightPropagationQueue.add(new LightNode(nr, nc, newBaseLight));
                    }
                }
            }
        }
    }

    // In LightManager.java

    // In LightManager.java

    private void processSingleBlockRemovalStep_Heightmap(LightNode nodeBeingRemoved) {
        int r = nodeBeingRemoved.r;
        int c = nodeBeingRemoved.c;
        byte originalLightLevelOfSource = nodeBeingRemoved.lightLevel;

        // The core idea is that we only need to update neighbors whose light level
        // was dependent on the light source we are now removing.

        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = r + dr[i];
            int nc = c + dc[i];
            Tile neighbor = map.getTile(nr, nc);

            // Skip neighbors that have no light to remove.
            if (neighbor == null || neighbor.getBlockLightLevel() == 0) continue;

            byte currentNeighborBlockLight = neighbor.getBlockLightLevel();

            // Check if the neighbor has its own independent light source (a torch).
            // If it does, we don't need to process removal for it, but we should
            // re-queue it for propagation to ensure its light "heals" the new darkness.
            if (neighbor.hasTorch()) {
                blockLightPropagationQueue.add(new LightNode(nr, nc, currentNeighborBlockLight));
                continue;
            }

            // Calculate how much light the neighbor could have possibly received from our source.
            byte lightThatCameFromRemovedSourcePath = (byte) Math.max(0, originalLightLevelOfSource - LIGHT_PROPAGATION_COST - getHorizontalPassOpacity(neighbor));

            // CRITICAL FIX: Only queue a neighbor for removal if its current light level
            // is less than or equal to the light it received from our source. This means
            // it was dependent on our source and now needs to go dark.
            // We REMOVE the "else" block that was aggressively re-propagating other light,
            // as that was the source of the queue explosion.
            if (currentNeighborBlockLight <= lightThatCameFromRemovedSourcePath) {
                // This neighbor's light was dependent on the source we are removing.
                // Set its light to 0 and queue it for further removal to its own neighbors.
                neighbor.setBlockLightLevel((byte) 0);
                blockLightRemovalQueue.add(new LightNode(nr, nc, currentNeighborBlockLight)); // Queue removal of its old light
                markChunkDirty(nr, nc);
            }
        }
    }


    public boolean isAnyLightQueueNotEmpty() {
        return !blockLightPropagationQueue.isEmpty() || !blockLightRemovalQueue.isEmpty() ||
                !skyLightPropagationQueue.isEmpty() || !skyLightRemovalQueue.isEmpty();
    }

    public int getSkyLightPropagationQueueSize() { return skyLightPropagationQueue.size(); }
    public int getSkyLightRemovalQueueSize() { return skyLightRemovalQueue.size(); }
    public int getBlockLightPropagationQueueSize() { return blockLightPropagationQueue.size(); }
    public int getBlockLightRemovalQueueSize() { return blockLightRemovalQueue.size(); }
    public Queue<LightNode> getBlockLightRemovalQueue_Direct() { return blockLightRemovalQueue; }



    public Queue<LightNode> getSkyLightPropagationQueue_Direct() { return skyLightPropagationQueue; }
    public Queue<LightNode> getSkyLightRemovalQueue_Direct() { return skyLightRemovalQueue; }
    public Queue<LightNode> getBlockLightPropagationQueue_Direct() { return blockLightPropagationQueue; }
}