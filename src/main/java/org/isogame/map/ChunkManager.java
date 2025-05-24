package org.isogame.map;

import org.isogame.tile.Tile;
import org.isogame.map.SimplexNoise;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet; // Added for getLoadedChunkCoordinates
import static org.isogame.constants.Constants.*;

/**
 * Manages loading, generating, and accessing chunk data.
 */
public class ChunkManager {

    private final Map<ChunkCoordinate, ChunkData> loadedChunks = new HashMap<>();
    private final SimplexNoise noiseGenerator;
    private final Random random = new Random();

    public ChunkManager() {
        // Consider a fixed seed for testing or a user-provided seed for world generation
        this.noiseGenerator = new SimplexNoise(12345); // Example fixed seed
    }

    /**
     * Gets a chunk. If not loaded, it generates it and stores it.
     * @param chunkX grid X of the chunk
     * @param chunkY grid Y of the chunk
     * @return The ChunkData
     */
    public ChunkData getChunk(int chunkX, int chunkY) {
        ChunkCoordinate coord = new ChunkCoordinate(chunkX, chunkY);
        // This computeIfAbsent ensures thread-safety for generation if you ever go multi-threaded for chunk loading
        return loadedChunks.computeIfAbsent(coord, k -> generateChunk(k.chunkX, k.chunkY));
    }

    /**
     * Returns a chunk only if it's already loaded, otherwise null.
     * @param chunkX grid X of the chunk
     * @param chunkY grid Y of the chunk
     * @return The ChunkData if loaded, else null.
     */
    public ChunkData getLoadedChunk(int chunkX, int chunkY) {
        return loadedChunks.get(new ChunkCoordinate(chunkX, chunkY));
    }

    public void unloadChunk(int chunkX, int chunkY) {
        ChunkCoordinate coord = new ChunkCoordinate(chunkX, chunkY);
        ChunkData removedChunk = loadedChunks.remove(coord);
        if (removedChunk != null) {
            // System.out.println("Unloaded chunk: " + chunkX + "," + chunkY);
        }
    }

    private ChunkData generateChunk(int chunkX, int chunkY) {
        // System.out.println("Generating chunk: " + chunkX + "," + chunkY);
        ChunkData chunkData = new ChunkData(chunkX, chunkY);
        int worldStartX = chunkX * CHUNK_SIZE_TILES;
        int worldStartY = chunkY * CHUNK_SIZE_TILES;

        for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
            for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                int worldX = worldStartX + localX;
                int worldY = worldStartY + localY;

                double noiseValue = calculateCombinedNoise((double) worldX, (double) worldY);
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * (ALTURA_MAXIMA + 1)) - 1;
                elevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));

                Tile.TileType type = determineTileTypeFromElevation(elevation);
                Tile tile = new Tile(type, elevation);
                tile.setSkyLightLevel((byte) 0); // Initial state, lighting system will update
                tile.setBlockLightLevel((byte) 0);
                tile.setHasTorch(false);

                if (type == Tile.TileType.GRASS && elevation >= NIVEL_ARENA && elevation < NIVEL_ROCA) {
                    if (random.nextFloat() < 0.02) { // Tree chance
                        tile.setTreeType(random.nextBoolean() ? Tile.TreeVisualType.APPLE_TREE_FRUITING : Tile.TreeVisualType.PINE_TREE_SMALL);
                    }
                }
                chunkData.setTile(localX, localY, tile);
            }
        }
        return chunkData;
    }

    private double calculateCombinedNoise(double x, double y) {
        double baseFrequency = NOISE_SCALE * 0.05;
        double mountainFrequency = NOISE_SCALE * 0.2;
        double roughnessFrequency = NOISE_SCALE * 0.8;
        double baseNoise = noiseGenerator.octaveNoise(x * baseFrequency, y * baseFrequency, 4, 0.6);
        double mountainNoise = noiseGenerator.noise(x * mountainFrequency, y * mountainFrequency) * 0.40;
        double roughnessNoise = noiseGenerator.noise(x * roughnessFrequency, y * roughnessFrequency) * 0.15;
        double combined = baseNoise + mountainNoise + roughnessNoise;
        return Math.max(-1.0, Math.min(1.0, combined));
    }

    public Tile.TileType determineTileTypeFromElevation(int elevation) {
        if (elevation < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevation < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevation < NIVEL_ROCA) return Tile.TileType.GRASS;
        if (elevation < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }

    public Set<ChunkCoordinate> getLoadedChunkCoordinates() {
        return new HashSet<>(loadedChunks.keySet()); // Return a copy to avoid concurrent modification
    }

    public int getLoadedChunksCount() {
        return loadedChunks.size();
    }
}
