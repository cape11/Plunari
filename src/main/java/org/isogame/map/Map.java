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

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                tiles[r][c] = new Tile(Tile.TileType.WATER, 0);
            }
        }
        generateMap();
    }

    public void generateMap() {
        System.out.println("DEBUG: Map.generateMap() CALLED.");
        this.noiseGenerator = new SimplexNoise(random.nextInt(10000));

        int[][] rawElevations = new int[height][width];

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                double noiseValue = calculateCombinedNoise((double)c, (double)r);
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * (ALTURA_MAXIMA + 1)) -1;
                rawElevations[r][c] = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
            }
        }

        smoothTerrain(rawElevations, 1);

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int elevation = rawElevations[r][c];
                Tile.TileType type = determineTileTypeFromElevation(elevation);

                Tile currentTile = tiles[r][c];
                currentTile.setElevation(elevation);
                currentTile.setType(type);

                // Corrected tree placement logic
                if (type == Tile.TileType.GRASS && elevation >= NIVEL_ARENA && elevation < NIVEL_ROCA) {
                    if (random.nextFloat() < 0.08) { // Chance to place a tree
                        if (random.nextBoolean()) {
                            currentTile.setTreeType(Tile.TreeVisualType.APPLE_TREE_FRUITING);
                        } else {
                            currentTile.setTreeType(Tile.TreeVisualType.PINE_TREE_SMALL);
                        }
                    } else {
                        // Failed the random chance for a tree on this valid grass tile
                        currentTile.setTreeType(Tile.TreeVisualType.NONE);
                    }
                } else {
                    // Not a grass tile suitable for trees, or wrong elevation
                    currentTile.setTreeType(Tile.TreeVisualType.NONE);
                }
            }
        }
        findSuitableCharacterPosition();
        System.out.println("DEBUG: Map generation finished. New spawn: (" + characterSpawnRow + ", " + characterSpawnCol + ")");
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

    private Tile.TileType determineTileTypeFromElevation(int elevation) {
        if (elevation < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevation < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevation < NIVEL_ROCA) return Tile.TileType.GRASS;
        if (elevation < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }

    private void smoothTerrain(int[][] elevations, int passes) {
        if (passes <= 0) return;
        int currentHeight = elevations.length;
        int currentWidth = elevations[0].length;
        int[][] tempAlturas = new int[currentHeight][currentWidth];

        for (int pass = 0; pass < passes; pass++) {
            for (int r = 0; r < currentHeight; r++) {
                System.arraycopy(elevations[r], 0, tempAlturas[r], 0, currentWidth);
            }
            for (int r = 0; r < currentHeight; r++) {
                for (int c = 0; c < currentWidth; c++) {
                    int sum = 0;
                    int count = 0;
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            int nr = r + i;
                            int nc = c + j;
                            if (nr >= 0 && nr < currentHeight && nc >= 0 && nc < currentWidth) {
                                sum += tempAlturas[nr][nc];
                                count++;
                            }
                        }
                    }
                    if (count > 0) {
                        elevations[r][c] = sum / count;
                    }
                }
            }
        }
    }

    private void findSuitableCharacterPosition() {
        int centerRow = height / 2;
        int centerCol = width / 2;
        for (int radius = 0; radius < Math.max(width, height) / 2; radius++) {
            for (int rOffset = -radius; rOffset <= radius; rOffset++) {
                for (int cOffset = -radius; cOffset <= radius; cOffset++) {
                    if (Math.abs(rOffset) == radius || Math.abs(cOffset) == radius) {
                        int r = centerRow + rOffset;
                        int c = centerCol + cOffset;
                        if (isValid(r, c) && tiles[r][c].getType() != Tile.TileType.WATER && tiles[r][c].getElevation() >= NIVEL_MAR) {
                            characterSpawnRow = r;
                            characterSpawnCol = c;
                            return;
                        }
                    }
                }
            }
        }
        characterSpawnRow = centerRow;
        characterSpawnCol = centerCol;
    }

    public Tile getTile(int row, int col) {
        if (isValid(row, col)) return tiles[row][col];
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
            tile.setType(determineTileTypeFromElevation(clampedElevation));
            // When elevation is changed by player action, clear the tree.
            // Natural tree placement only happens during generateMap().
            if (tile.getTreeType() != Tile.TreeVisualType.NONE) {
                tile.setTreeType(Tile.TreeVisualType.NONE);
            }
        }
    }
}