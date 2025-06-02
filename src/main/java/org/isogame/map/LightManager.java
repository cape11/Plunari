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

    // SkyLightPropagationQueue will be minimally used by this simplified sky light model,
    // primarily for a very localized, intentional downward spill if implemented, or if Map.queueLightUpdateForArea uses it.
    private final Queue<LightNode> skyLightPropagationQueue;
    private final Queue<LightNode> skyLightRemovalQueue; // Still needed for transitions to dark or occlusion by new blocks
    private final Queue<LightNode> blockLightPropagationQueue; // For torches, etc.
    private final Queue<LightNode> blockLightRemovalQueue;

    public static final int BATCH_LIGHT_UPDATE_BUDGET = 10000;
    private static final int MAX_LIGHT_UPDATES_PER_QUEUE_PER_FRAME = 4000;

    // Not strictly needed for propagation if sky light doesn't propagate much, but block light still uses its own.
    private static final int MAX_ELEVATION_STEP_FOR_SKYLIGHT_PROPAGATION = 0; // Sky light primarily doesn't "climb" via propagation
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
        public final int r, c;
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
     * Refreshes sky light for a single chunk based on the Core Idea:
     * Exposed surface tiles get targetGlobalSkyValue directly. Occluded get minimal ambient.
     * No general horizontal sky light propagation is queued from this method.
     * Limited downward spill for cliffs can be added here or rely on renderer.
     */
    public void refreshSkyLightForSingleChunk(ChunkCoordinate chunkCoord, byte targetGlobalSkyValue) {
        int startR = chunkCoord.chunkY * CHUNK_SIZE_TILES;
        int startC = chunkCoord.chunkX * CHUNK_SIZE_TILES;
        int endR = Math.min(startR + CHUNK_SIZE_TILES, map.getHeight());
        int endC = Math.min(startC + CHUNK_SIZE_TILES, map.getWidth());

        for (int r = startR; r < endR; r++) {
            for (int c = startC; c < endC; c++) {
                Tile tile = map.getTile(r, c);
                if (tile == null) continue;

                byte oldSkyLight = tile.getSkyLightLevel();
                byte newSkyLightValue;
                boolean changed = false;

                if (tile.getType() == Tile.TileType.AIR) {
                    newSkyLightValue = targetGlobalSkyValue; // AIR is always fully lit by sky
                } else if (tile.getType() == Tile.TileType.WATER) {
                    newSkyLightValue = (byte)0; // Water surface isn't "lit" by sky in this model, it reflects
                } else {
                    // For a pure heightmap (no blocks above another in same r,c column unless it's AIR),
                    // any non-AIR, non-WATER tile *is* the surface tile.
                    // Thus, it's considered sky-exposed by default according to the core idea.
                    // If you later add a concept of "roof" tiles, you'd check that here.
                    // For now, all ground surface tiles get direct sky light.
                    newSkyLightValue = targetGlobalSkyValue;
                }

                if (oldSkyLight != newSkyLightValue) {
                    tile.setSkyLightLevel(newSkyLightValue);
                    changed = true;
                    if (newSkyLightValue < oldSkyLight) {
                        // If light decreased (e.g. nightfall, or was occluded by a new roof block if that existed),
                        // queue removal of OLD brighter light to correctly darken affected neighbors (primarily for block light interactions).
                        skyLightRemovalQueue.add(new LightNode(r, c, oldSkyLight));
                    }
                    // No addition to skyLightPropagationQueue for horizontal spread from this direct application.
                    // If a very limited "downward spill" for cliffs is desired from data, it could be added here:
                    // e.g., check if this tile is a cliff edge and queue its immediate lower neighbor.
                }
                if (changed) {
                    markChunkDirty(r, c);
                }
            }
        }
    }

    public void initializeSkylightForChunk(ChunkCoordinate chunkCoord) {
        refreshSkyLightForSingleChunk(chunkCoord, this.currentGlobalSkyLightTarget);
    }

    /**
     * This method's interpretation changes with the "Core Idea".
     * For a pure heightmap where only the top-most non-AIR tile at (r,c) exists,
     * this tile is *always* considered exposed to the sky unless a specific "roof" mechanic is added.
     * The old neighbor-checking logic for occlusion is less relevant for *direct* sky light
     * if we assume all surface tiles are sky-facing.
     * However, it can still be used by propagation logic if we want minimal sky spill.
     */
    public boolean isSurfaceTileExposedToSky(int r, int c, int elevation) {
        Tile tile = map.getTile(r,c);
        if(tile == null || tile.getType() == Tile.TileType.AIR || tile.getType() == Tile.TileType.WATER) {
            return true; // AIR is always exposed, WATER isn't lit by sky this way but doesn't block
        }
        // For a simple heightmap, any solid surface tile is considered "exposed"
        // unless you have a more complex system (e.g. specific roof tiles).
        // The old 8-neighbor check is effectively bypassed for direct sky application.
        return true; // All non-AIR, non-WATER surface tiles get direct sky light.
    }

    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = map.getTile(r, c);
        if (tile != null && tile.isSolidOpaqueBlock() && tile.getType() != Tile.TileType.WATER) {
            tile.setHasTorch(true);
            if (lightLevel > tile.getBlockLightLevel()) {
                tile.setBlockLightLevel(lightLevel);
                blockLightPropagationQueue.add(new LightNode(r, c, lightLevel));
                markChunkDirty(r,c);
            } else if (!blockLightPropagationQueue.contains(new LightNode(r,c,tile.getBlockLightLevel())) && tile.getBlockLightLevel() > 0){
                blockLightPropagationQueue.add(new LightNode(r, c, tile.getBlockLightLevel()));
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
        // Process removal queues first to ensure darkness spreads before new light
        processQueue(skyLightRemovalQueue, LightProcessingStep.SKY_REMOVAL, budget);
        processQueue(blockLightRemovalQueue, LightProcessingStep.BLOCK_REMOVAL, budget);
        // SkyLightPropagationQueue will be very sparsely used with the new direct sky light model.
        // It might only contain nodes for explicit downward spill if we add that, or if Map.queueLightUpdateForArea adds to it.
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
            Tile tile = map.getTile(current.r, current.c);
            if (tile == null) continue;

            switch (stepType) {
                case SKY_PROPAGATION:
                    // Only propagate if source still has at least the light level that was queued.
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
        if (tileBeingEntered == null) return MAX_LIGHT_LEVEL + 1;
        if (tileBeingEntered.isTransparentToSkyLight()) return 0; // AIR allows light through freely
        if (tileBeingEntered.getType() == Tile.TileType.WATER) return 3;
        if (tileBeingEntered.isSolidOpaqueBlock()) return 1;
        return 1;
    }

    /**
     * Sky light propagation is now EXTREMELY limited under the new model.
     * This method will primarily handle an explicit "downward spill" for cliffs IF nodes are added to its queue for this purpose.
     * General horizontal sky light spread is no longer intended.
     */
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

            // Sky light only "falls" down, does not climb or spread far horizontally via propagation.
            if (elevationDifference < 0) { // Propagating downwards
                int stepCost = 0; // Sky light falls with no cost.
                int opacityOfNeighborSurface = getHorizontalPassOpacity(neighborSurfaceTile);
                byte lightReachingNeighbor = (byte) Math.max(0, propagatedLightStrength - stepCost - opacityOfNeighborSurface);

                if (lightReachingNeighbor > neighborSurfaceTile.getSkyLightLevel()) {
                    neighborSurfaceTile.setSkyLightLevel(lightReachingNeighbor);
                    // Only continue propagating downwards if there's still significant light
                    if (lightReachingNeighbor > 1) { // Threshold to continue spill
                        skyLightPropagationQueue.add(new LightNode(nr, nc, lightReachingNeighbor));
                    }
                    markChunkDirty(nr, nc);
                }
            }
            // No else for horizontal or upward propagation of sky light in this simplified model.
        }
    }

    private void processSingleBlockPropagationStep_Heightmap(LightNode currentQueuedNode) {
        // This method remains the same as it handles local block lights.
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
        // This method needs to ensure that if a tile's light is removed, and it *should*
        // be lit by the currentGlobalSkyLightTarget (because it's exposed), it gets reset to that.
        int r = nodeBeingRemoved.r; int c = nodeBeingRemoved.c;
        byte originalLightLevelOfSourceThatIsBeingRemoved = nodeBeingRemoved.lightLevel;
        Tile sourceSurfaceTile = map.getTile(r,c);
        if (sourceSurfaceTile == null) return;

        // The source tile itself: if it's exposed, its light should be targetGlobalSkyLightTarget.
        // If its current light is being removed and was > target, set to target.
        // This is handled by refreshSkyLightForSingleChunk. This removal step is about neighbors.

        int[] dr = {-1, 1, 0, 0}; int[] dc = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int nr = r + dr[i]; int nc = c + dc[i];
            if (!map.isValid(nr, nc)) continue;
            Tile neighborSurfaceTile = map.getTile(nr, nc);
            if (neighborSurfaceTile == null || neighborSurfaceTile.getType() == Tile.TileType.WATER) continue;

            byte currentNeighborSkyLight = neighborSurfaceTile.getSkyLightLevel();
            if (currentNeighborSkyLight == 0) continue; // Already dark

            // Determine how much light this neighbor *could* have gotten from the source's old light
            int elevationDifference = neighborSurfaceTile.getElevation() - sourceSurfaceTile.getElevation();
            int stepCost = LIGHT_PROPAGATION_COST; // Default
            if (elevationDifference < 0) stepCost = 0; // Light falling path

            // Can only remove light that could have come via a valid path
            if (elevationDifference > 0 && elevationDifference > MAX_ELEVATION_STEP_FOR_SKYLIGHT_PROPAGATION) continue;


            int opacityOfNeighborSurface = getHorizontalPassOpacity(neighborSurfaceTile);
            byte lightThatCouldHaveComeFromRemovedSourcePath = (byte) Math.max(0, originalLightLevelOfSourceThatIsBeingRemoved - stepCost - opacityOfNeighborSurface);

            if (currentNeighborSkyLight <= lightThatCouldHaveComeFromRemovedSourcePath) {
                // This neighbor's light was likely dependent on the source.
                // Set it to the minimum ambient if not exposed, or re-evaluate if exposed.
                byte newBaseLight = SKY_LIGHT_NIGHT_MINIMUM;
                if (isSurfaceTileExposedToSky(nr, nc, neighborSurfaceTile.getElevation())) {
                    newBaseLight = this.currentGlobalSkyLightTarget;
                }

                if (currentNeighborSkyLight > newBaseLight) { // If it was brighter than its new base
                    neighborSurfaceTile.setSkyLightLevel(newBaseLight);
                    skyLightRemovalQueue.add(new LightNode(nr, nc, currentNeighborSkyLight)); // Queue removal of its old light
                    markChunkDirty(nr, nc);
                    if (newBaseLight > 0 && newBaseLight == this.currentGlobalSkyLightTarget) {
                        // If it was set to the global target and is exposed, it might need to spill
                        skyLightPropagationQueue.add(new LightNode(nr, nc, newBaseLight));
                    }
                }
            }
            // If currentNeighborSkyLight > lightThatCouldHaveComeFromRemovedSourcePath, it means it has other, stronger light sources.
            // Its light level should be maintained by those other sources' propagation. No explicit re-queue here needed
            // as the refresh mechanism should handle ensuring all exposed tiles are at target.
        }
    }

    private void processSingleBlockRemovalStep_Heightmap(LightNode nodeBeingRemoved) {
        // ... (This method remains largely the same as previous correct version)
        int r = nodeBeingRemoved.r; int c = nodeBeingRemoved.c;
        byte originalLightLevelOfSource = nodeBeingRemoved.lightLevel;
        Tile sourceTile = map.getTile(r,c);
        if (sourceTile == null) return;

        if (sourceTile.hasTorch() && sourceTile.getBlockLightLevel() >= originalLightLevelOfSource) {
            if (!blockLightPropagationQueue.contains(new LightNode(r,c,sourceTile.getBlockLightLevel()))) {
                blockLightPropagationQueue.add(new LightNode(r, c, sourceTile.getBlockLightLevel()));
            }
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
            if (elevationDifference > MAX_ELEVATION_STEP_FOR_BLOCKLIGHT_PROPAGATION) continue;
            int opacityCostOfNeighbor = getHorizontalPassOpacity(neighbor);
            byte lightThatCameFromRemovedSourcePath = (byte) Math.max(0, originalLightLevelOfSource - LIGHT_PROPAGATION_COST - opacityCostOfNeighbor);

            if (currentNeighborBlockLight <= lightThatCameFromRemovedSourcePath) {
                neighbor.setBlockLightLevel((byte) 0);
                blockLightRemovalQueue.add(new LightNode(nr, nc, currentNeighborBlockLight));
                markChunkDirty(nr, nc);
            } else {
                if (!blockLightPropagationQueue.contains(new LightNode(nr,nc,currentNeighborBlockLight))) {
                    blockLightPropagationQueue.add(new LightNode(nr, nc, currentNeighborBlockLight));
                }
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

    // Getters for Map.java to directly add to queues for border conditions or specific events
    public Queue<LightNode> getSkyLightPropagationQueue_Direct() { return skyLightPropagationQueue; }
    public Queue<LightNode> getSkyLightRemovalQueue_Direct() { return skyLightRemovalQueue; }
    public Queue<LightNode> getBlockLightPropagationQueue_Direct() { return blockLightPropagationQueue; }
}