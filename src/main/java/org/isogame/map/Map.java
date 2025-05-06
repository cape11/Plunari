package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Random;
import static org.isogame.constants.Constants.*;

public class Map {

    private final Tile[][] tiles;
    private final int width;
    private final int height;
    private SimplexNoise noiseGenerator;
    private final Random random = new Random();
    private int characterSpawnRow;
    private int characterSpawnCol;

    public Map(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];
        // Initialize with default tiles (e.g., water) before generation
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                tiles[r][c] = new Tile(Tile.TileType.WATER, 0);
            }
        }
        generateMap(); // Generate map on creation
    }

    public void generateMap() {
        System.out.println("Generating map...");
        this.noiseGenerator = new SimplexNoise(random.nextInt(1000)); // New seed each time

        int[][] rawElevations = new int[height][width];

        // Generate raw elevation data
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                double noiseValue = calculateNoise(row, col);
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * ALTURA_MAXIMA);
                rawElevations[row][col] = elevation;
            }
        }

        // Smooth terrain
        smoothTerrain(rawElevations, 3);

        // Assign Tile types based on final elevation
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int elevation = rawElevations[row][col];
                Tile.TileType type;
                if (elevation < NIVEL_MAR) {
                    type = Tile.TileType.WATER;
                } else if (elevation < NIVEL_ARENA) {
                    type = Tile.TileType.SAND;
                } else if (elevation < NIVEL_ROCA) { // Use constants for thresholds
                    type = Tile.TileType.GRASS;
                } else if (elevation < NIVEL_NIEVE) {
                    type = Tile.TileType.ROCK;
                } else {
                    type = Tile.TileType.SNOW;
                }
                tiles[row][col] = new Tile(type, elevation);
            }
        }

        findSuitableCharacterPosition();
        System.out.println("Map generated successfully. Spawn: (" + characterSpawnRow + ", " + characterSpawnCol + ")");
    }

    private double calculateNoise(double x, double y) {
        // Base terrain using octaves
        double base = noiseGenerator.octaveNoise(x * NOISE_SCALE, y * NOISE_SCALE, 6, 0.5);

        // Add some variance/features (example)
        double mountains = noiseGenerator.noise(x * NOISE_SCALE * 0.5, y * NOISE_SCALE * 0.5) * 0.3;
        double roughness = noiseGenerator.noise(x * NOISE_SCALE * 2.0, y * NOISE_SCALE * 2.0) * 0.1;

        double combined = base + mountains + roughness;

        // Clamp to [-1, 1]
        return Math.max(-1.0, Math.min(1.0, combined));
    }

    private void smoothTerrain(int[][] elevations, int passes) {
        int[][] tempAlturas = new int[height][width];

        for (int pass = 0; pass < passes; pass++) {
            // Copy current heights
            for (int row = 0; row < height; row++) {
                System.arraycopy(elevations[row], 0, tempAlturas[row], 0, width);
            }

            // Smooth each non-edge tile
            for (int row = 1; row < height - 1; row++) {
                for (int col = 1; col < width - 1; col++) {
                    // Basic box blur (average of 3x3 neighbourhood)
                    int sum = 0;
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            sum += elevations[row + i][col + j];
                        }
                    }
                    tempAlturas[row][col] = sum / 9;
                }
            }

            // Update to smoothed heights
            for (int row = 1; row < height - 1; row++) {
                System.arraycopy(tempAlturas[row], 1, elevations[row], 1, width - 2);
            }
        }
    }

    private void findSuitableCharacterPosition() {
        int centerRow = height / 2;
        int centerCol = width / 2;

        // Start from the center and spiral outward looking for land
        for (int radius = 0; radius < Math.max(width, height) / 2; radius++) {
            for (int i = -radius; i <= radius; i++) {
                for (int j = -radius; j <= radius; j++) {
                    // Only check the perimeter of the current square
                    if (Math.abs(i) == radius || Math.abs(j) == radius) {
                        int row = centerRow + i;
                        int col = centerCol + j;

                        // Check if valid position within bounds
                        if (isValid(row, col)) {
                            // Check if not water
                            if (tiles[row][col].getType() != Tile.TileType.WATER) {
                                characterSpawnRow = row;
                                characterSpawnCol = col;
                                System.out.println("Found suitable spawn at (" + row + ", " + col + ")");
                                return; // Found a good spot
                            }
                        }
                    }
                }
            }
        }

        // Fallback if somehow no land was found (shouldn't happen with reasonable generation)
        characterSpawnRow = centerRow;
        characterSpawnCol = centerCol;
        System.out.println("Warning: No suitable land found, spawning at center (" + centerRow + ", " + centerCol + ")");
    }

    public Tile getTile(int row, int col) {
        if (isValid(row, col)) {
            return tiles[row][col];
        }
        return null; // Or return a default void tile
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCharacterSpawnRow() {
        return characterSpawnRow;
    }

    public int getCharacterSpawnCol() {
        return characterSpawnCol;
    }

    // Allow external modification of tile elevation (e.g., by player action)
    public void setTileElevation(int row, int col, int elevation) {
        if (isValid(row, col)) {
            Tile tile = tiles[row][col];
            tile.setElevation(elevation);
            // Update tile type based on new elevation
            Tile.TileType type;
            if (elevation < NIVEL_MAR) {
                type = Tile.TileType.WATER;
            } else if (elevation < NIVEL_ARENA) {
                type = Tile.TileType.SAND;
            } else if (elevation < NIVEL_ROCA) {
                type = Tile.TileType.GRASS;
            } else if (elevation < NIVEL_NIEVE) {
                type = Tile.TileType.ROCK;
            } else {
                type = Tile.TileType.SNOW;
            }
            tile.setType(type);
        }
    }
}