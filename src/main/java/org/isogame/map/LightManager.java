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
        dirtyChunks.add(new ChunkCoordinate(c / CHUNK_SIZE_TILES, r / CHUNK_SIZE_TILES));
    }

    public void updateGlobalSkyLight(byte globalSkyLight) {
        System.out.println("LightManager: Updating global sky light to " + globalSkyLight + " (Aggressive Seeding)");

        for (int r = 0; r < map.getHeight(); r++) {
            for (int c = 0; c < map.getWidth(); c++) {
                Tile tile = map.getTile(r, c);
                if (tile != null) {
                    if (tile.getSkyLightLevel() > 0) {
                        markChunkDirty(r, c);
                    }
                    tile.setSkyLightLevel((byte) 0);
                }
            }
        }
        skyLightQueue.clear();
        skyLightRemovalQueue.clear();

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
        System.out.println("LightManager: Global sky light update initiated. Queue size: " + skyLightQueue.size());
    }

    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = map.getTile(r, c);
        if (tile != null) {
            tile.setHasTorch(true);
            if (lightLevel > tile.getBlockLightLevel()) {
                tile.setBlockLightLevel(lightLevel);
                blockLightQueue.add(new LightNode(r, c, lightLevel));
                markChunkDirty(r, c);
            }
        }
    }

    public void removeLightSource(int r, int c) {
        Tile tile = map.getTile(r, c);
        if (tile != null && tile.hasTorch()) {
            byte oldLight = tile.getBlockLightLevel();
            tile.setHasTorch(false);
            tile.setBlockLightLevel((byte) 0);
            blockLightRemovalQueue.add(new LightNode(r, c, oldLight));
            markChunkDirty(r, c);
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
            LightNode current = queue.poll(); // Poll at the start of the loop
            if (current == null) break; // Should not happen if !queue.isEmpty() but defensive

            if (type == LightType.BLOCK_REMOVAL) {
                // Pass the single polled node to an adapted incremental removal function
                processSingleBlockLightRemovalNode(current);
            } else if (type == LightType.SKY_REMOVAL) {
                processSingleSkyLightRemovalNode(current);
            } else { // BLOCK or SKY propagation
                propagateLight(current, type);
            }
            processedNodes++;
        }
    }

    private void processSingleBlockLightRemovalNode(LightNode removedNode) {
        int r = removedNode.r;
        int c = removedNode.c;
        byte originalRemovedLightLevel = removedNode.lightLevel; // The light level this source originally provided

        Tile tile = map.getTile(r, c);
        if (tile == null) return;

        // If this tile was the source and is no longer, or its light was reduced
        if (!tile.hasTorch() && tile.getBlockLightLevel() < originalRemovedLightLevel) {
            tile.setBlockLightLevel((byte) 0); // Remove its light contribution
            markChunkDirty(r, c);
        } else if (tile.hasTorch() && tile.getBlockLightLevel() < originalRemovedLightLevel) {
            // A torch whose intensity was reduced - this case is less common with simple toggle
            // For now, assume torches are either full strength or 0.
        }


        // Check neighbors that might have been lit by this node
        Queue<LightNode> toRecheck = new LinkedList<>();
        toRecheck.add(new LightNode(r,c,originalRemovedLightLevel)); // Start recheck from the source of removal

        Set<LightNode> visitedForRemovalWave = new HashSet<>();
        visitedForRemovalWave.add(new LightNode(r,c,(byte)0)); // Add by coord only

        while(!toRecheck.isEmpty()){
            LightNode currentCheckNode = toRecheck.poll();
            int cr = currentCheckNode.r;
            int cc = currentCheckNode.c;
            byte lightLevelFromThisPath = currentCheckNode.lightLevel;

            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    if (Math.abs(dr) + Math.abs(dc) > 1) continue;

                    int nr = cr + dr;
                    int nc = cc + dc;

                    if (!map.isValid(nr, nc)) continue;
                    Tile neighbor = map.getTile(nr, nc);
                    if (neighbor == null || neighbor.getBlockLightLevel() == 0) continue;

                    byte expectedLightFromCurrent = (byte) Math.max(0, lightLevelFromThisPath - LIGHT_PROPAGATION_COST - getOpacityCost(neighbor));

                    if (neighbor.getBlockLightLevel() <= expectedLightFromCurrent) {
                        // This neighbor was lit by the path we are removing or an equally strong one through it.
                        // Set its light to 0 and add it to the removal propagation.
                        byte oldNeighborLight = neighbor.getBlockLightLevel();
                        neighbor.setBlockLightLevel((byte) 0);
                        markChunkDirty(nr, nc);
                        LightNode neighborRemovalNode = new LightNode(nr,nc,oldNeighborLight);
                        if(!visitedForRemovalWave.contains(neighborRemovalNode)){
                            toRecheck.add(neighborRemovalNode);
                            visitedForRemovalWave.add(neighborRemovalNode);
                        }
                    } else {
                        // Neighbor is lit by another stronger path or is a source.
                        // Add it to the main blockLightQueue to ensure its light re-propagates correctly.
                        blockLightQueue.add(new LightNode(nr, nc, neighbor.getBlockLightLevel()));
                    }
                }
            }
        }
    }

    private void processSingleSkyLightRemovalNode(LightNode removedNode) {
        // Similar to block light removal, but for sky light.
        // Given current updateGlobalSkyLight, this might be less critical as it does a full reset.
        // However, for future targeted sky light changes (e.g. building a roof), this would be needed.
        int r = removedNode.r;
        int c = removedNode.c;
        byte originalRemovedLightLevel = removedNode.lightLevel;

        Tile tile = map.getTile(r,c);
        if(tile == null || tile.getSkyLightLevel() >= originalRemovedLightLevel) return;

        tile.setSkyLightLevel((byte)0);
        markChunkDirty(r,c);

        Queue<LightNode> toRecheck = new LinkedList<>();
        toRecheck.add(new LightNode(r,c,originalRemovedLightLevel));
        Set<LightNode> visitedForRemovalWave = new HashSet<>();
        visitedForRemovalWave.add(new LightNode(r,c,(byte)0));

        while(!toRecheck.isEmpty()){
            LightNode currentCheckNode = toRecheck.poll();
            // ... (propagation logic similar to block removal, but using skyLight fields and SKY propagation rules)
        }
    }


    private void propagateLight(LightNode sourceNode, LightType type) {
        Queue<LightNode> queue = new LinkedList<>();
        queue.add(sourceNode);
        Set<LightNode> visitedInThisWave = new HashSet<>();
        visitedInThisWave.add(sourceNode);

        while(!queue.isEmpty()){
            LightNode current = queue.poll();
            int r = current.r;
            int c = current.c;
            byte currentLightLevel = current.lightLevel;

            int[] dr_options = {-1, 1, 0, 0};
            int[] dc_options = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int dr = dr_options[i];
                int dc = dc_options[i];
                int nr = r + dr;
                int nc = c + dc;

                if (!map.isValid(nr, nc)) continue;

                Tile neighborTile = map.getTile(nr, nc);
                if (neighborTile == null) continue;

                int opacityCost = getOpacityCost(neighborTile);
                int propagationCost = LIGHT_PROPAGATION_COST;

                if (type == LightType.SKY) {
                    if (dr < 0) { // Moving upwards
                        propagationCost = MAX_LIGHT_LEVEL;
                    } else { // Sideways or Downwards for sky light
                        propagationCost = LIGHT_PROPAGATION_COST;
                    }
                }

                byte lightToNeighbor = (byte) (currentLightLevel - propagationCost - opacityCost);

                if (lightToNeighbor <= 0) continue;

                byte existingNeighborLight;
                if (type == LightType.BLOCK) {
                    existingNeighborLight = neighborTile.getBlockLightLevel();
                } else {
                    existingNeighborLight = neighborTile.getSkyLightLevel();
                }

                if (lightToNeighbor > existingNeighborLight) {
                    if (type == LightType.BLOCK) {
                        neighborTile.setBlockLightLevel(lightToNeighbor);
                    } else {
                        neighborTile.setSkyLightLevel(lightToNeighbor);
                    }
                    markChunkDirty(nr, nc);

                    LightNode neighborNodeForQueue = new LightNode(nr, nc, lightToNeighbor);
                    if (!visitedInThisWave.contains(neighborNodeForQueue)) {
                        queue.add(neighborNodeForQueue);
                        visitedInThisWave.add(neighborNodeForQueue);
                    }
                }
            }
        }
    }

    private int getOpacityCost(Tile tileBeingEntered) {
        if (tileBeingEntered == null) {
            return MAX_LIGHT_LEVEL;
        }
        if (tileBeingEntered.getType() == Tile.TileType.WATER) {
            return 1;
        }
        if (!tileBeingEntered.isTransparentToLight()) {
            return 0;
        }
        return 0;
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
    }
}
