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
        // Initialize with default tiles before generation
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                // Default to water or a placeholder, will be overwritten by generateMap
                tiles[r][c] = new Tile(Tile.TileType.WATER, 0);
            }
        }
        generateMap(); // Generate map on creation
    }

    public void generateMap() {
        System.out.println("Generating map...");
        this.noiseGenerator = new SimplexNoise(random.nextInt(1000));

        int[][] rawElevations = new int[height][width];

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                double noiseValue = calculateNoise(row, col); // Combined noise
                // Scale noise from [-1, 1] to [0, ALTURA_MAXIMA]
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * ALTURA_MAXIMA);
                rawElevations[row][col] = Math.max(0, Math.min(ALTURA_MAXIMA, elevation)); // Clamp elevation
            }
        }

        smoothTerrain(rawElevations, 2); // Apply 2 passes of smoothing (adjust as needed)

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int elevation = rawElevations[row][col];
                Tile.TileType type;
                // Determine tile type based on FINAL elevation and constants
                if (elevation < NIVEL_MAR) {
                    type = Tile.TileType.WATER;
                } else if (elevation < NIVEL_ARENA) {
                    type = Tile.TileType.SAND;
                } else if (elevation < NIVEL_ROCA) { // This is effectively the GRASS band
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
        // Experiment with these values for different terrain feels
        // For more plains and beaches, you want larger features and less extreme roughness.
        double baseFrequency = NOISE_SCALE * 0.8; // Controls overall feature size (smaller = larger features)
        double mountainFrequency = NOISE_SCALE * 0.3;
        double roughnessFrequency = NOISE_SCALE * 1.5;

        double base = noiseGenerator.octaveNoise(x * baseFrequency, y * baseFrequency, 6, 0.5);
        double mountains = noiseGenerator.noise(x * mountainFrequency, y * mountainFrequency) * 0.35; // Amplitude for mountains
        double roughness = noiseGenerator.noise(x * roughnessFrequency, y * roughnessFrequency) * 0.05; // Lower amplitude for roughness for flatter plains

        // To create more defined land vs. water, you could apply a shaping function
        // For example, make values closer to sea level more common.
        // Or use a separate noise map for continent definition.
        // For now, simple combination:
        double combined = base + mountains + roughness;

        // You can try to "flatten" certain ranges for plains
        // For example, if 'combined' is in a mid-range, reduce its variance.
        // This is more advanced; for now, we rely on the elevation thresholds.

        return Math.max(-1.0, Math.min(1.0, combined)); // Clamp to [-1, 1]
    }

    private void smoothTerrain(int[][] elevations, int passes) {
        if (passes <= 0) return;

        int[][] tempAlturas = new int[height][width];
        for (int pass = 0; pass < passes; pass++) {
            for (int row = 0; row < height; row++) {
                System.arraycopy(elevations[row], 0, tempAlturas[row], 0, width);
            }

            for (int row = 1; row < height - 1; row++) {
                for (int col = 1; col < width - 1; col++) {
                    int sum = 0;
                    // 3x3 average (box blur)
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            sum += elevations[row + i][col + j];
                        }
                    }
                    tempAlturas[row][col] = sum / 9;
                }
            }
            // Copy smoothed data back to elevations
            for (int row = 1; row < height - 1; row++) {
                System.arraycopy(tempAlturas[row], 1, elevations[row], 1, width - 2);
            }
        }
    }

    private void findSuitableCharacterPosition() {
        int centerRow = height / 2;
        int centerCol = width / 2;

        for (int radius = 0; radius < Math.max(width, height) / 2; radius++) {
            for (int i = -radius; i <= radius; i++) {
                for (int j = -radius; j <= radius; j++) {
                    if (Math.abs(i) == radius || Math.abs(j) == radius) {
                        int row = centerRow + i;
                        int col = centerCol + j;
                        if (isValid(row, col)) {
                            if (tiles[row][col].getType() != Tile.TileType.WATER && tiles[row][col].getElevation() >= NIVEL_MAR) {
                                characterSpawnRow = row;
                                characterSpawnCol = col;
                                return;
                            }
                        }
                    }
                }
            }
        }
        characterSpawnRow = centerRow; // Fallback
        characterSpawnCol = centerCol;
    }

    public Tile getTile(int row, int col) {
        if (isValid(row, col)) {
            return tiles[row][col];
        }
        return null;
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }

    public void setTileElevation(int row, int col, int elevation) {
        if (isValid(row, col)) {
            Tile tile = tiles[row][col];
            int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
            tile.setElevation(clampedElevation);

            Tile.TileType newType;
            if (clampedElevation < NIVEL_MAR) {
                newType = Tile.TileType.WATER;
            } else if (clampedElevation < NIVEL_ARENA) {
                newType = Tile.TileType.SAND;
            } else if (clampedElevation < NIVEL_ROCA) {
                newType = Tile.TileType.GRASS;
            } else if (clampedElevation < NIVEL_NIEVE) {
                newType = Tile.TileType.ROCK;
            } else {
                newType = Tile.TileType.SNOW;
            }
            tile.setType(newType);
        }
    }
}