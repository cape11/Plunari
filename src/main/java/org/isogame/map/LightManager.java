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
        System.out.println("LightManager: Updating global sky light to " + globalSkyLight + " (Using Aggressive Seeding)");

        // 1. Reset all sky light on all tiles.
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

        // 2. AGGRESSIVE SEEDING:
        // Seed all non-water tiles at or above sea level with globalSkyLight.
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

        processSkyLightQueue();
        System.out.println("LightManager: Global sky light update complete (Aggressive Seeding). Dirty chunks: " + dirtyChunks.size());
    }

    public void addLightSource(int r, int c, byte lightLevel) {
        Tile tile = map.getTile(r, c);
        if (tile != null) {
            tile.setHasTorch(true);
            if (lightLevel > tile.getBlockLightLevel()) {
                tile.setBlockLightLevel(lightLevel);
                blockLightQueue.add(new LightNode(r, c, lightLevel));
                markChunkDirty(r, c);
                processBlockLightQueue();
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
            processBlockLightRemovalQueue();
        }
    }

    private void processBlockLightQueue() {
        while (!blockLightQueue.isEmpty()) {
            LightNode current = blockLightQueue.poll();
            propagateLight(current, LightType.BLOCK);
        }
    }

    private void processSkyLightQueue() {
        while (!skyLightQueue.isEmpty()) {
            LightNode current = skyLightQueue.poll();
            propagateLight(current, LightType.SKY);
        }
    }

    private void processBlockLightRemovalQueue() {
        Set<LightNode> reLightSources = new HashSet<>();

        while (!blockLightRemovalQueue.isEmpty()) {
            LightNode current = blockLightRemovalQueue.poll();
            int r = current.r;
            int c = current.c;
            byte removedLight = current.lightLevel;

            Tile tile = map.getTile(r, c);
            if (tile == null) continue;

            if (tile.getBlockLightLevel() <= removedLight && !tile.hasTorch()) {
                tile.setBlockLightLevel((byte)0);
                markChunkDirty(r,c);
            } else if (tile.getBlockLightLevel() > 0) {
                reLightSources.add(new LightNode(r, c, tile.getBlockLightLevel()));
            }

            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    if (Math.abs(dr) + Math.abs(dc) > 1) continue;

                    int nr = r + dr;
                    int nc = c + dc;

                    if (!map.isValid(nr, nc)) continue;
                    Tile neighbor = map.getTile(nr, nc);
                    if (neighbor == null) continue;

                    byte neighborLight = neighbor.getBlockLightLevel();
                    byte lightFromCurrent = (byte)Math.max(0, removedLight - LIGHT_PROPAGATION_COST - getOpacityCost(neighbor));

                    if (neighborLight > 0 && neighborLight <= lightFromCurrent) {
                        blockLightRemovalQueue.add(new LightNode(nr, nc, neighborLight));
                        neighbor.setBlockLightLevel((byte)0);
                        markChunkDirty(nr,nc);
                    } else if (neighborLight > 0) {
                        reLightSources.add(new LightNode(nr, nc, neighborLight));
                    }
                }
            }
        }
        for (LightNode source : reLightSources) {
            Tile tile = map.getTile(source.r, source.c);
            if(tile != null && tile.getBlockLightLevel() > 0) {
                blockLightQueue.add(new LightNode(source.r, source.c, tile.getBlockLightLevel()));
            }
        }
        if (!reLightSources.isEmpty()) {
            processBlockLightQueue();
        }
    }

    private void processSkyLightRemovalQueue() {
        while(!skyLightRemovalQueue.isEmpty()){
            skyLightRemovalQueue.poll();
        }
    }

    private void propagateLight(LightNode sourceNode, LightType type) {
        Queue<LightNode> queue = new LinkedList<>();
        queue.add(sourceNode);
        // visitedInThisWave now stores LightNode objects, which include lightLevel.
        // However, for the purpose of not re-adding the same COORDINATE to the queue
        // multiple times in one wave if it's already processed or scheduled,
        // a Set<CoordinateXY> might be simpler if LightNode's equals/hashCode are complex.
        // For now, LightNode.equals uses r,c only, so it acts like a coordinate set.
        Set<LightNode> visitedInThisWave = new HashSet<>();
        visitedInThisWave.add(sourceNode); // Add source node (r,c) to visited set

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
                    if (dr < 0) {
                        propagationCost = MAX_LIGHT_LEVEL;
                    } else {
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
                    // Update the light on the tile
                    if (type == LightType.BLOCK) {
                        neighborTile.setBlockLightLevel(lightToNeighbor);
                    } else {
                        neighborTile.setSkyLightLevel(lightToNeighbor);
                    }
                    markChunkDirty(nr, nc);

                    // Create a node representing the neighbor *with its new light level*
                    LightNode neighborNodeForQueue = new LightNode(nr, nc, lightToNeighbor);

                    // Add to queue for further propagation if this coordinate hasn't been
                    // a source in this wave yet. The light level in neighborNodeForQueue
                    // is the new, higher light level.
                    if (!visitedInThisWave.contains(neighborNodeForQueue)) { // contains() uses r,c from LightNode.equals()
                        queue.add(neighborNodeForQueue);
                        visitedInThisWave.add(neighborNodeForQueue); // Mark (r,c) as visited for this wave
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
            // For visitedInThisWave set, only coordinates matter to prevent re-processing same tile as a source in one wave
            return r == lightNode.r && c == lightNode.c;
        }

        @Override
        public int hashCode() {
            // Hashcode based on coordinates for set behavior
            return 31 * r + c;
        }
    }

    private enum LightType {
        SKY, BLOCK
    }

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
