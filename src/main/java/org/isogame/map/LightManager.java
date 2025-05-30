package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import static org.isogame.constants.Constants.*;

public class LightManager {

    private final Map map;
    private final Set<ChunkCoordinate> dirtyChunks;

    private final Queue<LightNode> blockLightQueue;
    private final Queue<LightNode> blockLightRemovalQueue;
    private final Queue<LightNode> skyLightQueue;
    private final Queue<LightNode> skyLightRemovalQueue;

    private static final int MAX_NODES_PER_FRAME_PROPAGATION = 500;
    private static final int MAX_NODES_PER_FRAME_REMOVAL = 200;

    public LightManager(Map map) {
        this.map = map;
        this.dirtyChunks = new HashSet<>();
        this.blockLightQueue = new LinkedList<>();
        this.blockLightRemovalQueue = new LinkedList<>();
        this.skyLightQueue = new LinkedList<>();
        this.skyLightRemovalQueue = new LinkedList<>();
    }

    public Set<ChunkCoordinate> getDirtyChunksAndClear() {
        Set<ChunkCoordinate> currentDirty = new HashSet<>(dirtyChunks);
        dirtyChunks.clear();
        return currentDirty;
    }

    public void markChunkDirty(int r, int c) {
        if (map.isValid(r,c)) { // Added a check for validity before calculating chunk coords
            dirtyChunks.add(new ChunkCoordinate(c / CHUNK_SIZE_TILES, r / CHUNK_SIZE_TILES));
        }
    }
    // Helper method to mark a chunk dirty by its grid coordinates directly
    public void markChunkDirtyByChunkCoords(int chunkX, int chunkY) {
        dirtyChunks.add(new ChunkCoordinate(chunkX, chunkY));
    }


    public void updateGlobalSkyLight(byte globalSkyLight) {
        System.out.println("LightManager: Updating global sky light to " + globalSkyLight + " (Aggressive Seeding)");

        // Clear existing sky light and mark affected chunks as dirty
        for (int r = 0; r < map.getHeight(); r++) {
            for (int c = 0; c < map.getWidth(); c++) {
                Tile tile = map.getTile(r, c);
                if (tile != null) {
                    if (tile.getSkyLightLevel() > 0) { // If it had sky light, it needs update
                        markChunkDirty(r, c);
                    }
                    tile.setSkyLightLevel((byte) 0); // Reset sky light
                }
            }
        }
        // Clear queues to prevent processing outdated light nodes
        skyLightQueue.clear();
        skyLightRemovalQueue.clear(); // Important if a previous update was interrupted

        for (int r = 0; r < map.getHeight(); r++) {
            for (int c = 0; c < map.getWidth(); c++) {
                Tile tile = map.getTile(r, c);
                if (tile != null && tile.getType() != Tile.TileType.WATER && tile.getElevation() >= NIVEL_MAR) {
                    if (tile.getSkyLightLevel() < globalSkyLight) {
                        tile.setSkyLightLevel(globalSkyLight);
                        skyLightQueue.add(new LightNode(r, c, globalSkyLight));
                        markChunkDirty(r, c);
                    }
                }
            }
        }
        System.out.println("LightManager: Global sky light update initiated. SkyLightQueue size: " + skyLightQueue.size());
    }


    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = map.getTile(r, c);
        if (tile != null) {
            tile.setHasTorch(true);
            if (lightLevel > tile.getBlockLightLevel()) {
                tile.setBlockLightLevel(lightLevel);
                blockLightQueue.add(new LightNode(r, c, lightLevel));
                markChunkDirty(r,c);
            }
        }
    }

    public void removeLightSource(int r, int c) {
        Tile tile = map.getTile(r, c);
        if (tile != null && tile.hasTorch()) {
            byte oldLight = tile.getBlockLightLevel();
            tile.setHasTorch(false);
            blockLightRemovalQueue.add(new LightNode(r, c, oldLight));
            markChunkDirty(r,c);
        }
    }


    public void processLightQueuesIncrementally() {
        processQueue(blockLightRemovalQueue, LightType.BLOCK_REMOVAL, MAX_NODES_PER_FRAME_REMOVAL);
        processQueue(skyLightRemovalQueue, LightType.SKY_REMOVAL, MAX_NODES_PER_FRAME_REMOVAL);
        processQueue(blockLightQueue, LightType.BLOCK, MAX_NODES_PER_FRAME_PROPAGATION);
        processQueue(skyLightQueue, LightType.SKY, MAX_NODES_PER_FRAME_PROPAGATION);
    }

    private void processQueue(Queue<LightNode> queue, LightType type, int budget) {
        int processedNodes = 0;
        while (!queue.isEmpty() && processedNodes < budget) {
            LightNode current = queue.poll();
            if (current == null) break;

            switch (type) {
                case BLOCK_REMOVAL:
                    processSingleBlockLightRemovalNode(current);
                    break;
                case SKY_REMOVAL:
                    processSingleSkyLightRemovalNode(current);
                    break;
                case BLOCK:
                case SKY:
                    propagateLight(current, type);
                    break;
            }
            processedNodes++;
        }
    }

    private void processSingleBlockLightRemovalNode(LightNode removedNode) {
        int r = removedNode.r;
        int c = removedNode.c;
        byte originalLightLevelOfSource = removedNode.lightLevel;

        Tile sourceTile = map.getTile(r, c);
        if (sourceTile != null) {
            if (!sourceTile.hasTorch() || sourceTile.getBlockLightLevel() < originalLightLevelOfSource) {
                sourceTile.setBlockLightLevel((byte) 0);
                markChunkDirty(r, c);
            } else if (sourceTile.hasTorch() && sourceTile.getBlockLightLevel() >= originalLightLevelOfSource) {
                blockLightQueue.add(new LightNode(r, c, sourceTile.getBlockLightLevel()));
                return;
            }
        }

        Queue<LightNode> removalPropagationQueue = new LinkedList<>();
        removalPropagationQueue.add(new LightNode(r, c, originalLightLevelOfSource));

        Set<LightNode> visitedInThisRemovalWave = new HashSet<>();
        visitedInThisRemovalWave.add(new LightNode(r,c,(byte)0));

        while(!removalPropagationQueue.isEmpty()){
            LightNode currentPropNode = removalPropagationQueue.poll();
            int cr = currentPropNode.r;
            int cc = currentPropNode.c;
            byte lightLevelRemovedFromThisPath = currentPropNode.lightLevel;

            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    if (Math.abs(dr) + Math.abs(dc) > 1) continue; // Cardinal only

                    int nr = cr + dr;
                    int nc = cc + dc;

                    if (!map.isValid(nr, nc)) continue;
                    Tile neighbor = map.getTile(nr, nc);
                    if (neighbor == null) continue;

                    byte currentNeighborLight = neighbor.getBlockLightLevel();
                    if (currentNeighborLight == 0) continue;

                    byte potentialLightFromThisPath = (byte) Math.max(0, lightLevelRemovedFromThisPath - LIGHT_PROPAGATION_COST - getOpacityCost(neighbor));

                    if (currentNeighborLight <= potentialLightFromThisPath) {
                        neighbor.setBlockLightLevel((byte) 0);
                        markChunkDirty(nr, nc);
                        LightNode neighborRemovalPropNode = new LightNode(nr,nc,currentNeighborLight);
                        if(!visitedInThisRemovalWave.contains(new LightNode(nr,nc,(byte)0))){
                            removalPropagationQueue.add(neighborRemovalPropNode);
                            visitedInThisRemovalWave.add(new LightNode(nr,nc,(byte)0));
                        }
                    } else {
                        blockLightQueue.add(new LightNode(nr, nc, currentNeighborLight));
                    }
                }
            }
        }
    }


    private void processSingleSkyLightRemovalNode(LightNode removedNode) {
        int r = removedNode.r;
        int c = removedNode.c;
        byte originalSkyLightLevel = removedNode.lightLevel;

        Tile tile = map.getTile(r,c);
        if(tile == null) return;

        if(tile.getSkyLightLevel() >= originalSkyLightLevel) {
            tile.setSkyLightLevel((byte)0);
            markChunkDirty(r,c);

            Queue<LightNode> skyRemovalPropagationQueue = new LinkedList<>();
            skyRemovalPropagationQueue.add(new LightNode(r,c,originalSkyLightLevel));
            Set<LightNode> visitedInSkyRemovalWave = new HashSet<>();
            visitedInSkyRemovalWave.add(new LightNode(r,c,(byte)0));

            while(!skyRemovalPropagationQueue.isEmpty()){
                LightNode currentPropNode = skyRemovalPropagationQueue.poll();
                int cr = currentPropNode.r;
                int cc = currentPropNode.c;
                byte lightLevelRemovedFromThisPath = currentPropNode.lightLevel;

                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        if (Math.abs(dr) + Math.abs(dc) > 1) continue; // Cardinal only

                        int nr = cr + dr;
                        int nc = cc + dc;

                        if (!map.isValid(nr, nc)) continue;
                        Tile neighbor = map.getTile(nr, nc);
                        if (neighbor == null) continue;

                        byte currentNeighborSkyLight = neighbor.getSkyLightLevel();
                        if (currentNeighborSkyLight == 0) continue;

                        int propagationCost = LIGHT_PROPAGATION_COST;
                        if (dr > 0 && map.getTile(cr, cc) != null && map.getTile(cr, cc).isTransparentToLight()) {
                            propagationCost = 0; // Sky light passes down through transparent blocks easier
                        } else if (dr < 0) { // Sky light doesn't go up
                            propagationCost = MAX_LIGHT_LEVEL;
                        }


                        byte potentialLightFromThisPath = (byte) Math.max(0, lightLevelRemovedFromThisPath - propagationCost - getOpacityCost(neighbor));

                        if (currentNeighborSkyLight <= potentialLightFromThisPath) {
                            neighbor.setSkyLightLevel((byte) 0);
                            markChunkDirty(nr, nc);
                            LightNode neighborRemovalNode = new LightNode(nr, nc, currentNeighborSkyLight);
                            if (!visitedInSkyRemovalWave.contains(new LightNode(nr, nc, (byte) 0))) {
                                skyRemovalPropagationQueue.add(neighborRemovalNode);
                                visitedInSkyRemovalWave.add(new LightNode(nr, nc, (byte) 0));
                            }
                        } else {
                            skyLightQueue.add(new LightNode(nr, nc, currentNeighborSkyLight)); // Re-propagate existing light
                        }
                    }
                }
            }
        }
    }


    private void propagateLight(LightNode sourceNode, LightType type) {
        Queue<LightNode> propagationQueue = new LinkedList<>();
        propagationQueue.add(sourceNode);
        Set<LightNode> visitedInThisWave = new HashSet<>();
        visitedInThisWave.add(new LightNode(sourceNode.r, sourceNode.c, (byte)0));

        while(!propagationQueue.isEmpty()){
            LightNode current = propagationQueue.poll();
            int r = current.r;
            int c = current.c;
            byte currentLightLevelAtCurrentNode = current.lightLevel;

            int[] dr_options = {-1, 1, 0, 0};
            int[] dc_options = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int nr = r + dr_options[i];
                int nc = c + dc_options[i];

                if (!map.isValid(nr, nc)) continue;

                Tile neighborTile = map.getTile(nr, nc);
                if (neighborTile == null) continue;

                int opacityCost = getOpacityCost(neighborTile);
                int propagationStepCost = LIGHT_PROPAGATION_COST;

                if (type == LightType.SKY) {
                    Tile currentTile = map.getTile(r,c); // The tile light is coming FROM
                    if (dr_options[i] > 0) { // Moving downwards
                        if (currentTile != null && currentTile.isTransparentToLight()){
                            propagationStepCost = 0;
                        } else {
                            propagationStepCost = MAX_LIGHT_LEVEL;
                        }
                    } else if (dr_options[i] == 0) { // Moving sideways
                        propagationStepCost = LIGHT_PROPAGATION_COST;
                    } else { // Moving upwards (dr_options[i] < 0)
                        propagationStepCost = MAX_LIGHT_LEVEL; // Sky light doesn't propagate upwards
                    }
                }

                byte lightReachingNeighbor = (byte) Math.max(0, currentLightLevelAtCurrentNode - propagationStepCost - opacityCost);

                if (lightReachingNeighbor <= 0) continue;

                byte existingNeighborLight;
                boolean updateNeighbor = false;

                if (type == LightType.BLOCK) {
                    existingNeighborLight = neighborTile.getBlockLightLevel();
                    if (lightReachingNeighbor > existingNeighborLight) {
                        neighborTile.setBlockLightLevel(lightReachingNeighbor);
                        updateNeighbor = true;
                    }
                } else { // LightType.SKY
                    existingNeighborLight = neighborTile.getSkyLightLevel();
                    if (lightReachingNeighbor > existingNeighborLight) {
                        neighborTile.setSkyLightLevel(lightReachingNeighbor);
                        updateNeighbor = true;
                    }
                }

                if (updateNeighbor) {
                    markChunkDirty(nr, nc);
                    LightNode neighborNodeForQueue = new LightNode(nr, nc, lightReachingNeighbor);
                    if (!visitedInThisWave.contains(new LightNode(nr,nc,(byte)0))) {
                        propagationQueue.add(neighborNodeForQueue);
                        visitedInThisWave.add(new LightNode(nr,nc,(byte)0));
                    }
                }
            }
        }
    }


    private int getOpacityCost(Tile tileBeingEntered) {
        if (tileBeingEntered == null) return MAX_LIGHT_LEVEL;
        if (tileBeingEntered.getType() == Tile.TileType.WATER) {
            return 2;
        }
        if (!tileBeingEntered.isTransparentToLight()) {
            return MAX_LIGHT_LEVEL;
        }
        return 0;
    }

    public boolean isQueueEmpty() {
        return blockLightQueue.isEmpty() &&
                blockLightRemovalQueue.isEmpty() &&
                skyLightQueue.isEmpty() &&
                skyLightRemovalQueue.isEmpty();
    }

    public int getSkyQueueSize() {
        return skyLightQueue.size();
    }

    public int getSkyRemovalQueueSize() {
        return skyLightRemovalQueue.size();
    }

    public int getBlockQueueSize() {
        return blockLightQueue.size();
    }

    public int getBlockRemovalQueueSize() {
        return blockLightRemovalQueue.size();
    }

    private static class LightNode {
        final int r, c;
        final byte lightLevel;

        LightNode(int r, int c, byte lightLevel) {
            this.r = r;
            this.c = c;
            this.lightLevel = lightLevel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LightNode lightNode = (LightNode) o;
            return r == lightNode.r && c == lightNode.c;
        }

        @Override
        public int hashCode() {
            return 31 * r + c;
        }
    }

    private enum LightType { SKY, BLOCK, BLOCK_REMOVAL, SKY_REMOVAL }

    public static class ChunkCoordinate {
        public final int chunkX, chunkY;

        public ChunkCoordinate(int chunkX, int chunkY) {
            this.chunkX = chunkX;
            this.chunkY = chunkY;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoordinate that = (ChunkCoordinate) o;
            return chunkX == that.chunkX && chunkY == that.chunkY;
        }

        @Override
        public int hashCode() {
            return 31 * chunkX + chunkY;
        }

        @Override
        public String toString() {
            return "ChunkCoord(" + chunkX + "," + chunkY + ")";
        }
    }
}