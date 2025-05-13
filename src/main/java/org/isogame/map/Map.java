package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Random;
import static org.isogame.constants.Constants.*;

/**
 * Manages the game map, including its generation, tiles, and character spawn.
 */
public class Map {

    private final Tile[][] tiles; // 2D array storing all tiles in the map
    private final int width;      // Width of the map in number of tiles
    private final int height;     // Height of the map in number of tiles
    private SimplexNoise noiseGenerator; // For procedural generation
    private final Random random = new Random(); // For random elements like tree placement and noise seed
    private int characterSpawnRow; // Calculated row for player to spawn
    private int characterSpawnCol; // Calculated column for player to spawn

    /**
     * Constructs a new Map with the given dimensions and generates its terrain.
     * @param width The width of the map in tiles.
     * @param height The height of the map in tiles.
     */
    public Map(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];

        // Initialize tiles array with a default (e.g., water), will be overwritten by generateMap
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                tiles[r][c] = new Tile(Tile.TileType.WATER, 0);
            }
        }
        generateMap(); // Generate the actual map content
    }

    /**
     * Generates the map terrain, including elevations, tile types, and tree placement.
     * This method is called when a new Map object is created or can be called to regenerate.
     */
    public void generateMap() {
        System.out.println("Generating map...");
        this.noiseGenerator = new SimplexNoise(random.nextInt(1000)); // New seed for varied maps

        int[][] rawElevations = new int[height][width];

        // 1. Generate raw elevation data using noise
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                double noiseValue = calculateCombinedNoise(r, c); // Get combined noise value
                // Scale noise from [-1, 1] range to [0, ALTURA_MAXIMA] range
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * ALTURA_MAXIMA);
                // Clamp elevation to be within defined min/max
                rawElevations[r][c] = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
            }
        }

        // 2. Smooth the raw elevation data
        smoothTerrain(rawElevations, 2); // Adjust number of passes for more/less smoothing

        // 3. Assign Tile objects with types and place trees based on final elevations
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int elevation = rawElevations[r][c];
                Tile.TileType type = determineTileTypeFromElevation(elevation);
                tiles[r][c] = new Tile(type, elevation); // Create new Tile object

                // Place trees based on tile type and other conditions
                if (type == Tile.TileType.GRASS && elevation >= NIVEL_ARENA) { // e.g., only on grass above sand
                    if (random.nextFloat() < 0.05) { // 5% chance for a tree on this grass tile
                        // Randomly pick between your chosen tree types
                        // This assumes you have at least APPLE_TREE_FRUITING and PINE_TREE_SMALL in TreeVisualType enum
                        if (random.nextBoolean()) {
                            tiles[r][c].setTreeType(Tile.TreeVisualType.APPLE_TREE_FRUITING);
                        } else {
                            tiles[r][c].setTreeType(Tile.TreeVisualType.PINE_TREE_SMALL);
                        }
                        // Example for 3 types:
                        // float pick = random.nextFloat();
                        // if (pick < 0.33f) tiles[r][c].setTreeType(Tile.TreeVisualType.TYPE_1);
                        // else if (pick < 0.66f) tiles[r][c].setTreeType(Tile.TreeVisualType.TYPE_2);
                        // else tiles[r][c].setTreeType(Tile.TreeVisualType.TYPE_3);
                    }
                }
            }
        }

        // 4. Find a suitable spawn position for the character
        findSuitableCharacterPosition();
        System.out.println("Map generated successfully. Spawn: (" + characterSpawnRow + ", " + characterSpawnCol + ")");
    }

    /**
     * Calculates a combined noise value for a given map coordinate.
     * This is where you can layer different noise frequencies and amplitudes
     * to create varied terrain features like base landmass, mountains, and roughness.
     * @param x The x-coordinate (often row) for noise calculation.
     * @param y The y-coordinate (often col) for noise calculation.
     * @return A noise value, typically clamped between -1.0 and 1.0.
     */
    private double calculateCombinedNoise(double x, double y) {
        // --- Experiment with these values for desired terrain ---
        // NOISE_SCALE is from Constants.java
        double baseFrequency = NOISE_SCALE * 0.8; // Controls overall feature size (smaller value = larger features)
        double mountainFrequency = NOISE_SCALE * 0.1; // For larger, sparser features like mountain ranges
        double roughnessFrequency = NOISE_SCALE * 0.4; // For smaller, detailed features

        // Base landmass shape
        double baseNoise = noiseGenerator.octaveNoise(x * baseFrequency, y * baseFrequency, 6, 0.5); // 6 octaves, 0.5 persistence

        // Larger features like mountains/hills
        double mountainNoise = noiseGenerator.noise(x * mountainFrequency, y * mountainFrequency) * 0.35; // Adjust amplitude (0.35 here)

        // Finer details, roughness
        double roughnessNoise = noiseGenerator.noise(x * roughnessFrequency, y * roughnessFrequency) * 0.05; // Lower amplitude for subtle roughness

        double combined = baseNoise + mountainNoise + roughnessNoise;

        // Optional: Apply shaping functions here if desired (e.g., Math.pow for sharper peaks, or functions to create flatter plains)
        // Example: combined = Math.pow(combined, 1.2); // Would make lower areas lower and higher areas higher (needs careful tuning)

        return Math.max(-1.0, Math.min(1.0, combined)); // Clamp result to [-1, 1]
    }

    /**
     * Determines the TileType based on an elevation value and predefined thresholds.
     * @param elevation The elevation of the tile.
     * @return The corresponding TileType.
     */
    private Tile.TileType determineTileTypeFromElevation(int elevation) {
        if (elevation < NIVEL_MAR) {
            return Tile.TileType.WATER;
        } else if (elevation < NIVEL_ARENA) {
            return Tile.TileType.SAND;
        } else if (elevation < NIVEL_ROCA) { // This band is typically GRASS
            return Tile.TileType.GRASS;
        } else if (elevation < NIVEL_NIEVE) {
            return Tile.TileType.ROCK;
        } else {
            return Tile.TileType.SNOW;
        }
    }

    /**
     * Smooths the terrain elevations using a simple averaging filter.
     * @param elevations The 2D array of raw elevation data to smooth.
     * @param passes The number of smoothing passes to apply.
     */
    private void smoothTerrain(int[][] elevations, int passes) {
        if (passes <= 0) return;

        int currentHeight = elevations.length;
        int currentWidth = elevations[0].length;
        int[][] tempAlturas = new int[currentHeight][currentWidth];

        for (int pass = 0; pass < passes; pass++) {
            // Copy current elevations to temp array for reading
            for (int r = 0; r < currentHeight; r++) {
                System.arraycopy(elevations[r], 0, tempAlturas[r], 0, currentWidth);
            }

            // Apply smoothing, reading from tempAlturas and writing to elevations
            for (int r = 1; r < currentHeight - 1; r++) {
                for (int c = 1; c < currentWidth - 1; c++) {
                    int sum = 0;
                    // 3x3 average (box blur)
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            sum += tempAlturas[r + i][c + j]; // Read from the copied array
                        }
                    }
                    elevations[r][c] = sum / 9; // Write to the original array
                }
            }
        }
    }

    /**
     * Finds a suitable non-water tile for character spawning, typically near the map center.
     * Sets the characterSpawnRow and characterSpawnCol fields.
     */
    private void findSuitableCharacterPosition() {
        int centerRow = height / 2;
        int centerCol = width / 2;

        // Search outwards from the center
        for (int radius = 0; radius < Math.max(width, height) / 2; radius++) {
            for (int rOffset = -radius; rOffset <= radius; rOffset++) {
                for (int cOffset = -radius; cOffset <= radius; cOffset++) {
                    // Only check the perimeter of the current search square
                    if (Math.abs(rOffset) == radius || Math.abs(cOffset) == radius) {
                        int r = centerRow + rOffset;
                        int c = centerCol + cOffset;
                        if (isValid(r, c)) {
                            // tiles[r][c] should already be initialized from generateMap's main loop
                            if (tiles[r][c].getType() != Tile.TileType.WATER && tiles[r][c].getElevation() >= NIVEL_MAR) {
                                characterSpawnRow = r;
                                characterSpawnCol = c;
                                System.out.println("Spawn found at: (" + r + ", " + c + ")");
                                return;
                            }
                        }
                    }
                }
            }
        }
        // Fallback if no suitable land found (should be rare with reasonable generation)
        characterSpawnRow = centerRow;
        characterSpawnCol = centerCol;
        System.out.println("Warning: No suitable non-water spawn found near center. Spawning at map center.");
    }

    /**
     * Retrieves the Tile object at the specified map coordinates.
     * @param row The row index.
     * @param col The column index.
     * @return The Tile object, or null if coordinates are out of bounds.
     */
    public Tile getTile(int row, int col) {
        if (isValid(row, col)) {
            return tiles[row][col];
        }
        return null;
    }

    /**
     * Checks if the given map coordinates are within the map boundaries.
     * @param row The row index.
     * @param col The column index.
     * @return true if the coordinates are valid, false otherwise.
     */
    public boolean isValid(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    // --- Getters ---
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }

    /**
     * Sets the elevation of a specific tile and updates its TileType accordingly.
     * Does NOT handle tree placement/removal; that's part of initial generation or specific game mechanics.
     * @param row The row index of the tile.
     * @param col The column index of the tile.
     * @param elevation The new elevation value.
     */
    public void setTileElevation(int row, int col, int elevation) {
        if (isValid(row, col)) {
            Tile tile = tiles[row][col]; // Get existing tile
            int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation)); // Clamp to valid range
            tile.setElevation(clampedElevation);
            tile.setType(determineTileTypeFromElevation(clampedElevation)); // Update type based on new elevation

            // IMPORTANT: Removing tree placement logic from here.
            // Tree placement should be in generateMap() or a specific player action.
            // Changing elevation generally shouldn't spontaneously grow/remove trees
            // unless that's a specific desired mechanic.
            // If elevation change implies tree removal (e.g., digging up a tree tile):
            // if (tile.getTreeType() != Tile.TreeVisualType.NONE && newType != Tile.TileType.GRASS) {
            //     tile.setTreeType(Tile.TreeVisualType.NONE); // Example: remove tree if no longer grass
            // }
        }
    }
}